package com.lightbot.subagent.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.entity.SubAgentRun;
import com.lightbot.entity.SubAgentTaskBatch;
import com.lightbot.common.BizException;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lightbot.service.chat.ChatContext;
import com.lightbot.subagent.SubAgentThreadManager;
import com.lightbot.subagent.event.SubAgentEventPublisher;
import com.lightbot.subagent.service.SubAgentTaskService;
import com.lightbot.subagent.spi.SubAgentDefinition;
import com.lightbot.subagent.spi.SubAgentDefinitionResolver;
import com.lightbot.subagent.spi.SubAgentExecutor;
import com.lightbot.subagent.spi.SubAgentTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * SubAgent 批次编排实现：创建、调度、查询、取消均在本服务内完成。
 *
 * @author finch
 * @since 2026-07-10
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubAgentTaskServiceImpl implements SubAgentTaskService {

    private static final int MAX_TASKS = 5;
    private static final int DEFAULT_CONCURRENCY = 3;

    private final ObjectMapper objectMapper;
    private final SubAgentDefinitionResolver definitionResolver;
    private final SubAgentExecutor executor;
    private final SubAgentTaskRepository repository;
    private final SubAgentEventPublisher eventPublisher;
    @Qualifier("lightBotExecutor")
    private final Executor lightBotExecutor;

    @Override
    public String delegate(String toolInput, ToolContext toolContext, List<Long> boundSubAgentIds) {
        try {
            DelegationInput input = parseDelegation(toolInput);
            Map<String, SubAgentDefinition> definitions = definitionResolver.resolve(boundSubAgentIds);
            String validationError = validate(input, definitions);
            if (validationError != null) {
                return json(Map.of("status", "failed", "error", validationError));
            }

            RuntimeContext context = runtimeContext(toolContext);
            String batchId = batchId(context.requestId(), input);
            ensureRecords(batchId, input, context);
            publishBatchStart(context, batchId, input);

            if ("background".equals(input.mode())) {
                submitBackground(batchId, input, context, definitions);
                return json(result(batchId, input, true, submitted(batchId, input, context)));
            }
            List<Map<String, Object>> results = "parallel".equals(input.mode()) && input.tasks().size() > 1
                    ? runParallel(batchId, input, context, definitions)
                    : runSequential(batchId, input, context, definitions);
            refreshBatch(batchId);
            publishBatchDone(context, batchId);
            return json(result(batchId, input, false, results));
        } catch (Exception e) {
            log.warn("[SubAgent] 批次委派失败: {}", e.getMessage());
            return json(Map.of("status", "failed", "error", "SubAgent 委派失败: " + e.getMessage()));
        }
    }

    @Override
    public String query(String toolInput, ToolContext toolContext) {
        try {
            Map<String, Object> args = args(toolInput);
            RuntimeContext context = runtimeContext(toolContext);
            String batchId = string(args.get("batch_id"));
            if (batchId != null && !batchId.isBlank()) {
                SubAgentTaskBatch batch = repository.findBatch(batchId);
                if (!ownedBy(batch, context.parentSessionId())) {
                    return denied();
                }
                return json(batchResult(batch));
            }
            List<Map<String, Object>> results = new ArrayList<>();
            for (String taskId : taskIds(args)) {
                SubAgentRun task = repository.findTask(taskId);
                if (!ownedBy(task, context.parentSessionId())) {
                    return denied();
                }
                results.add(taskResult(task));
            }
            if (results.isEmpty()) {
                return json(Map.of("status", "failed", "error", "缺少 task_id、task_ids 或 batch_id 参数"));
            }
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("results", results);
            if (results.size() == 1) output.putAll(results.get(0));
            return json(output);
        } catch (Exception e) {
            return json(Map.of("status", "failed", "error", "查询失败: " + e.getMessage()));
        }
    }

    @Override
    public String cancel(String toolInput, ToolContext toolContext) {
        try {
            Map<String, Object> args = args(toolInput);
            RuntimeContext context = runtimeContext(toolContext);
            String taskId = string(args.get("task_id"));
            String batchId = string(args.get("batch_id"));
            if ((taskId == null || taskId.isBlank()) && (batchId == null || batchId.isBlank())) {
                return json(Map.of("status", "failed", "error", "缺少 task_id 或 batch_id 参数"));
            }
            if (taskId != null && !taskId.isBlank() && !ownedBy(repository.findTask(taskId), context.parentSessionId())) {
                return denied();
            }
            if (batchId != null && !batchId.isBlank() && !ownedBy(repository.findBatch(batchId), context.parentSessionId())) {
                return denied();
            }
            int affected = 0;
            if (taskId != null && !taskId.isBlank()) affected += repository.requestCancelTask(taskId);
            if (batchId != null && !batchId.isBlank()) affected += repository.requestCancelBatch(batchId);
            return json(Map.of("status", affected > 0 ? "cancel_requested" : "not_found", "affected", affected,
                    "task_id", taskId != null ? taskId : "", "batch_id", batchId != null ? batchId : ""));
        } catch (Exception e) {
            return json(Map.of("status", "failed", "error", "取消失败: " + e.getMessage()));
        }
    }

    @Override
    public Page<SubAgentRun> pageRuns(Long sessionId, String batchId, int pageNum, int pageSize) {
        return repository.pageTasks(sessionId, batchId, Math.max(pageNum, 1), Math.min(Math.max(pageSize, 1), 100));
    }

    @Override
    public Map<String, Object> getBatchDetail(String batchId, Long sessionId) {
        SubAgentTaskBatch batch = repository.findBatch(batchId);
        if (!ownedBy(batch, sessionId)) {
            throw new BizException("SubAgent 批次不存在或无权访问");
        }
        return batchResult(batch);
    }

    @Override
    public Map<String, Object> getTaskDetail(String taskId, Long sessionId) {
        SubAgentRun task = repository.findTask(taskId);
        if (!ownedBy(task, sessionId)) {
            throw new BizException("SubAgent 任务不存在或无权访问");
        }
        return taskResult(task);
    }

    @Override
    public Map<String, Object> cancelBatch(String batchId, Long sessionId) {
        SubAgentTaskBatch batch = repository.findBatch(batchId);
        if (!ownedBy(batch, sessionId)) {
            throw new BizException("SubAgent 批次不存在或无权访问");
        }
        int affected = repository.requestCancelBatch(batchId);
        return Map.of("batch_id", batchId, "status", affected > 0 ? "cancel_requested" : "not_found", "affected", affected);
    }

    private List<Map<String, Object>> runSequential(String batchId, DelegationInput input, RuntimeContext context,
                                                      Map<String, SubAgentDefinition> definitions) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (int index = 0; index < input.tasks().size(); index++) {
            results.add(runTask(batchId, input.tasks().get(index), context, definitions, index));
            refreshBatch(batchId);
        }
        return results;
    }

    private List<Map<String, Object>> runParallel(String batchId, DelegationInput input, RuntimeContext context,
                                                    Map<String, SubAgentDefinition> definitions) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (int start = 0; start < input.tasks().size(); start += input.maxConcurrency()) {
            int end = Math.min(input.tasks().size(), start + input.maxConcurrency());
            List<CompletableFuture<Map<String, Object>>> futures = new ArrayList<>();
            for (int index = start; index < end; index++) {
                int taskIndex = index;
                futures.add(CompletableFuture.supplyAsync(
                        () -> runTask(batchId, input.tasks().get(taskIndex), context, definitions, taskIndex), lightBotExecutor));
            }
            for (CompletableFuture<Map<String, Object>> future : futures) results.add(future.join());
            refreshBatch(batchId);
        }
        return results;
    }

    private void submitBackground(String batchId, DelegationInput input, RuntimeContext context,
                                  Map<String, SubAgentDefinition> definitions) {
        for (int index = 0; index < input.tasks().size(); index++) {
            int taskIndex = index;
            lightBotExecutor.execute(() -> {
                runTask(batchId, input.tasks().get(taskIndex), context.background(), definitions, taskIndex);
                refreshBatch(batchId);
            });
        }
    }

    private Map<String, Object> runTask(String batchId, DelegatedTask task, RuntimeContext context,
                                        Map<String, SubAgentDefinition> definitions, int taskIndex) {
        String taskId = taskId(context.requestId(), task, taskIndex);
        SubAgentDefinition definition = definitions.get(task.subagentName());
        publishTask(context, "subagent_task_start", batchId, taskId, task, taskIndex, "running", null);
        try {
            SubAgentExecutor.ExecutionResult result = executor.execute(definition, task.task(), taskId, task.threadId(),
                    context.parentThreadId(), context.taskContext(batchId, taskId, taskIndex));
            SubAgentRun run = repository.findTask(taskId);
            Map<String, Object> output = taskResult(run);
            output.put("reply", result.reply());
            output.put("thread_id", result.threadId());
            output.put("continued", result.continued());
            publishTask(context, "subagent_task_done", batchId, taskId, task, taskIndex,
                    output.get("status").toString(), output);
            return output;
        } catch (Exception e) {
            SubAgentRun run = repository.findTask(taskId);
            if (run != null) {
                run.setStatus("failed");
                run.setErrorMessage(e.getMessage());
                run.setEndTime(LocalDateTime.now());
                repository.saveTask(run);
            }
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("task_id", taskId);
            output.put("batch_id", batchId);
            output.put("subagent_name", task.subagentName());
            output.put("status", "failed");
            output.put("error", e.getMessage());
            publishTask(context, "subagent_task_done", batchId, taskId, task, taskIndex, "failed", output);
            return output;
        }
    }

    private void ensureRecords(String batchId, DelegationInput input, RuntimeContext context) {
        if (repository.findBatch(batchId) == null) {
            SubAgentTaskBatch batch = new SubAgentTaskBatch();
            batch.setBatchId(batchId);
            batch.setParentRequestId(context.requestId());
            batch.setParentThreadId(context.parentThreadId());
            batch.setParentSessionId(context.parentSessionId());
            batch.setMode(input.mode());
            batch.setAggregation(input.aggregation());
            batch.setStatus("pending");
            batch.setTotalCount(input.tasks().size());
            batch.setCompletedCount(0);
            batch.setFailedCount(0);
            batch.setCancelledCount(0);
            batch.setCancelRequested(0);
            repository.saveBatch(batch);
        }
        for (int index = 0; index < input.tasks().size(); index++) {
            DelegatedTask task = input.tasks().get(index);
            String taskId = taskId(context.requestId(), task, index);
            if (repository.findTask(taskId) != null) continue;
            SubAgentRun run = new SubAgentRun();
            run.setBatchId(batchId);
            run.setParentRequestId(context.requestId());
            run.setParentThreadId(context.parentThreadId());
            run.setParentSessionId(context.parentSessionId());
            run.setSubagentName(task.subagentName());
            run.setTask(task.task());
            run.setStatus("pending");
            run.setRequestId(taskId);
            run.setMode(input.mode());
            run.setCancelRequested(0);
            run.setToolCallCount(0);
            run.setThreadId(task.threadId() != null && !task.threadId().isBlank() ? task.threadId()
                    : SubAgentThreadManager.makeChildThreadId(context.parentThreadId(), task.subagentName(), taskId));
            repository.saveTask(run);
        }
    }

    private void refreshBatch(String batchId) {
        SubAgentTaskBatch batch = repository.findBatch(batchId);
        if (batch == null) return;
        List<SubAgentRun> tasks = repository.findTasks(batchId);
        int completed = 0, failed = 0, cancelled = 0, running = 0;
        for (SubAgentRun task : tasks) {
            if ("completed".equals(task.getStatus())) completed++;
            else if ("failed".equals(task.getStatus())) failed++;
            else if ("cancelled".equals(task.getStatus())) cancelled++;
            else if ("running".equals(task.getStatus())) running++;
        }
        batch.setCompletedCount(completed);
        batch.setFailedCount(failed);
        batch.setCancelledCount(cancelled);
        batch.setStatus(cancelled == tasks.size() && !tasks.isEmpty() ? "cancelled"
                : completed + failed + cancelled >= tasks.size() && !tasks.isEmpty() ? (failed > 0 ? "failed" : "completed")
                : running > 0 ? "running" : "pending");
        repository.saveBatch(batch);
    }

    private void publishBatchStart(RuntimeContext context, String batchId, DelegationInput input) {
        List<Map<String, Object>> tasks = new ArrayList<>();
        for (int index = 0; index < input.tasks().size(); index++) {
            DelegatedTask task = input.tasks().get(index);
            tasks.add(Map.of("task_id", taskId(context.requestId(), task, index), "subagent_name", task.subagentName(),
                    "task", task.task(), "task_index", index));
        }
        eventPublisher.publish(context.chatContext(), "subagent_batch_start", Map.of("batch_id", batchId, "mode", input.mode(),
                "aggregation", input.aggregation(), "tasks", tasks, "contentOffset", context.contentOffset(),
                "delegationIndex", context.delegationIndex()));
    }

    private void publishTask(RuntimeContext context, String type, String batchId, String taskId, DelegatedTask task,
                             int taskIndex, String status, Map<String, Object> result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("batch_id", batchId);
        payload.put("task_id", taskId);
        payload.put("task_index", taskIndex);
        payload.put("subagentName", task.subagentName());
        payload.put("status", status);
        payload.put("contentOffset", context.contentOffset());
        payload.put("delegationIndex", context.delegationIndex());
        if (result != null) payload.put("result", result);
        eventPublisher.publish(context.chatContext(), type, payload);
    }

    private void publishBatchDone(RuntimeContext context, String batchId) {
        SubAgentTaskBatch batch = repository.findBatch(batchId);
        eventPublisher.publish(context.chatContext(), "subagent_batch_done", Map.of("batch_id", batchId,
                "status", batch != null ? batch.getStatus() : "completed", "contentOffset", context.contentOffset(),
                "delegationIndex", context.delegationIndex()));
    }

    private Map<String, Object> result(String batchId, DelegationInput input, boolean background, List<Map<String, Object>> results) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("batch_id", batchId);
        output.put("mode", input.mode());
        output.put("aggregation", input.aggregation());
        output.put("background", background);
        output.put("results", results);
        if (results.size() == 1) output.putAll(results.get(0));
        return output;
    }

    private List<Map<String, Object>> submitted(String batchId, DelegationInput input, RuntimeContext context) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (int index = 0; index < input.tasks().size(); index++) {
            DelegatedTask task = input.tasks().get(index);
            result.add(Map.of("batch_id", batchId, "task_id", taskId(context.requestId(), task, index),
                    "subagent_name", task.subagentName(), "status", "submitted"));
        }
        return result;
    }

    private Map<String, Object> batchResult(SubAgentTaskBatch batch) {
        if (batch == null) return Map.of("status", "not_found", "results", List.of());
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("batch_id", batch.getBatchId());
        output.put("status", batch.getStatus());
        output.put("mode", batch.getMode());
        output.put("aggregation", batch.getAggregation());
        output.put("total_count", batch.getTotalCount());
        output.put("completed_count", batch.getCompletedCount());
        output.put("failed_count", batch.getFailedCount());
        output.put("cancelled_count", batch.getCancelledCount());
        output.put("cancel_requested", Integer.valueOf(1).equals(batch.getCancelRequested()));
        output.put("results", repository.findTasks(batch.getBatchId()).stream().map(this::taskResult).toList());
        return output;
    }

    private Map<String, Object> taskResult(SubAgentRun task) {
        if (task == null) return new LinkedHashMap<>(Map.of("status", "not_found"));
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("task_id", task.getRequestId());
        output.put("batch_id", task.getBatchId());
        output.put("status", task.getStatus());
        output.put("subagent_name", task.getSubagentName());
        output.put("thread_id", task.getThreadId());
        output.put("mode", task.getMode());
        output.put("reply", task.getReply());
        output.put("error", task.getErrorMessage());
        output.put("cancel_requested", Integer.valueOf(1).equals(task.getCancelRequested()));
        output.put("start_time", task.getStartTime());
        output.put("end_time", task.getEndTime());
        return output;
    }

    private DelegationInput parseDelegation(String toolInput) throws Exception {
        Map<String, Object> args = args(toolInput);
        String mode = string(args.get("mode"));
        mode = mode == null || mode.isBlank() ? "sync" : mode.trim().toLowerCase();
        String aggregation = string(args.get("aggregation"));
        aggregation = aggregation == null || aggregation.isBlank() ? "return_all" : aggregation;
        int concurrency = number(args.get("max_concurrency"), DEFAULT_CONCURRENCY);
        concurrency = Math.max(1, Math.min(MAX_TASKS, concurrency));
        List<DelegatedTask> tasks = new ArrayList<>();
        if (args.get("tasks") instanceof List<?> rawTasks) {
            for (Object raw : rawTasks) if (raw instanceof Map<?, ?> task) {
                tasks.add(new DelegatedTask(string(task.get("subagent_name")), string(task.get("task")), string(task.get("thread_id"))));
            }
        }
        if (tasks.isEmpty()) tasks.add(new DelegatedTask(string(args.get("subagent_name")), string(args.get("task")), string(args.get("thread_id"))));
        return new DelegationInput(mode, tasks.size() > MAX_TASKS ? tasks.subList(0, MAX_TASKS) : tasks, concurrency, aggregation);
    }

    private String validate(DelegationInput input, Map<String, SubAgentDefinition> definitions) {
        if (!List.of("sync", "parallel", "background").contains(input.mode())) return "mode 仅支持 sync、parallel、background";
        if (!List.of("return_all", "summarize").contains(input.aggregation())) return "aggregation 仅支持 return_all、summarize";
        for (DelegatedTask task : input.tasks()) {
            if (task.subagentName() == null || task.subagentName().isBlank()) return "缺少 subagent_name 参数";
            if (task.task() == null || task.task().isBlank()) return "缺少 task 参数";
            if (!definitions.containsKey(task.subagentName())) return "未在当前 Agent 绑定列表中找到子智能体: " + task.subagentName();
        }
        return null;
    }

    private RuntimeContext runtimeContext(ToolContext toolContext) {
        Map<String, Object> values = toolContext != null && toolContext.getContext() != null ? toolContext.getContext() : Map.of();
        String requestId = string(values.get("requestId"));
        String parentThreadId = string(values.get("parentThreadId"));
        Long sessionId = longValue(values.get("sessionId"));
        ChatContext chatContext = values.get("chatContext") instanceof ChatContext ctx ? ctx : null;
        int offset = chatContext != null && chatContext.getSubAgentContentOffset() != null ? chatContext.getSubAgentContentOffset() : 0;
        Integer delegationIndex = chatContext != null ? chatContext.getSubAgentDelegationIndex() : null;
        return new RuntimeContext(requestId == null || requestId.isBlank() ? "subagent_" + System.currentTimeMillis() : requestId,
                parentThreadId != null ? parentThreadId : "", sessionId, chatContext, offset, delegationIndex);
    }

    private Map<String, Object> args(String input) throws Exception {
        return objectMapper.readValue(input != null && !input.isBlank() ? input : "{}", new TypeReference<>() {});
    }
    private String json(Map<String, Object> value) { try { return objectMapper.writeValueAsString(value); } catch (Exception e) { return "{\"status\":\"failed\"}"; } }
    private String denied() { return json(Map.of("status", "forbidden", "error", "无权访问其他会话的 SubAgent 任务")); }
    private boolean ownedBy(SubAgentTaskBatch batch, Long sessionId) { return batch != null && sessionId != null && sessionId.equals(batch.getParentSessionId()); }
    private boolean ownedBy(SubAgentRun task, Long sessionId) { return task != null && sessionId != null && sessionId.equals(task.getParentSessionId()); }
    private List<String> taskIds(Map<String, Object> args) { List<String> ids = new ArrayList<>(); if (string(args.get("task_id")) != null) ids.add(string(args.get("task_id"))); if (args.get("task_ids") instanceof List<?> list) for (Object item : list) if (string(item) != null) ids.add(string(item)); return ids; }
    private String batchId(String requestId, DelegationInput input) { return "subagent_batch_" + hash(requestId + ":" + input.mode() + ":" + input.aggregation() + ":" + input.tasks()); }
    private String taskId(String requestId, DelegatedTask task, int index) { return "subagent_task_" + hash(requestId + ":" + index + ":" + task); }
    private String hash(String value) { return DigestUtils.md5DigestAsHex(value.getBytes(StandardCharsets.UTF_8)); }
    private String string(Object value) { return value != null && !value.toString().isBlank() ? value.toString() : null; }
    private int number(Object value, int fallback) { try { return value instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(value)); } catch (Exception e) { return fallback; } }
    private Long longValue(Object value) { try { return value != null ? Long.parseLong(value.toString()) : null; } catch (Exception e) { return null; } }

    private record DelegationInput(String mode, List<DelegatedTask> tasks, int maxConcurrency, String aggregation) {}
    private record DelegatedTask(String subagentName, String task, String threadId) {}
    private record RuntimeContext(String requestId, String parentThreadId, Long parentSessionId, ChatContext chatContext,
                                  int contentOffset, Integer delegationIndex) {
        RuntimeContext background() {
            // 后台任务完成后通过查询工具回填，避免污染已结束的当前 SSE。
            return new RuntimeContext(requestId, parentThreadId, parentSessionId, null, contentOffset, delegationIndex);
        }
        ChatContext taskContext(String batchId, String taskId, int taskIndex) {
            if (chatContext == null) return null;
            ChatContext taskContext = new ChatContext();
            taskContext.setProviderId(chatContext.getProviderId());
            taskContext.setConfigMap(chatContext.getConfigMap());
            taskContext.setAborted(chatContext.isAborted());
            taskContext.setRealtimeStatusEmitter(chatContext.getRealtimeStatusEmitter());
            taskContext.setSubAgentContentOffset(contentOffset);
            taskContext.setSubAgentDelegationIndex(delegationIndex);
            taskContext.setSubAgentBatchId(batchId);
            taskContext.setSubAgentTaskId(taskId);
            taskContext.setSubAgentTaskIndex(taskIndex);
            return taskContext;
        }
    }
}
