package com.lightbot.subagent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.entity.SubAgent;
import com.lightbot.entity.SubAgentRun;
import com.lightbot.entity.SubAgentTaskBatch;
import com.lightbot.mapper.SubAgentRunMapper;
import com.lightbot.mapper.SubAgentTaskBatchMapper;
import com.lightbot.service.SubAgentService;
import com.lightbot.service.chat.ChatContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * SubAgent 委派工具。
 *
 * <p>兼容旧的 {@code subagent_name + task} 单任务参数，同时支持批量、并行、后台执行、
 * 批次查询和取消。</p>
 *
 * @author finch
 * @since 2026-05-28
 */
@Slf4j
@Component
public class DelegateSubAgentTool {

    public static final String TOOL_NAME = "delegate_to_subagent";
    public static final String RESULT_TOOL_NAME = "get_subagent_task_result";
    public static final String CANCEL_TOOL_NAME = "cancel_subagent_task";

    private static final int MAX_TASKS_PER_CALL = 5;
    private static final int DEFAULT_MAX_CONCURRENCY = 3;

    private final SubAgentService subAgentService;
    private final SubAgentRuntime subAgentRuntime;
    private final SubAgentRunMapper subAgentRunMapper;
    private final SubAgentTaskBatchMapper subAgentTaskBatchMapper;
    private final ObjectMapper objectMapper;
    private final Executor lightBotExecutor;

    public DelegateSubAgentTool(SubAgentService subAgentService,
                                SubAgentRuntime subAgentRuntime,
                                SubAgentRunMapper subAgentRunMapper,
                                SubAgentTaskBatchMapper subAgentTaskBatchMapper,
                                ObjectMapper objectMapper,
                                @Qualifier("lightBotExecutor") Executor lightBotExecutor) {
        this.subAgentService = subAgentService;
        this.subAgentRuntime = subAgentRuntime;
        this.subAgentRunMapper = subAgentRunMapper;
        this.subAgentTaskBatchMapper = subAgentTaskBatchMapper;
        this.objectMapper = objectMapper;
        this.lightBotExecutor = lightBotExecutor;
    }

    /**
     * 为给定的一组 SubAgent ID 构造动态工具回调。
     *
     * @param boundSubAgentIds 当前 Agent 绑定的 SubAgent ID 列表
     * @return 委派工具，无可用 SubAgent 时返回 null
     */
    public ToolCallback buildCallback(List<Long> boundSubAgentIds) {
        List<ToolCallback> callbacks = buildCallbacks(boundSubAgentIds);
        return callbacks.isEmpty() ? null : callbacks.get(0);
    }

    /**
     * 为给定的一组 SubAgent ID 构造委派、结果查询和取消工具。
     *
     * @param boundSubAgentIds 当前 Agent 绑定的 SubAgent ID 列表
     * @return 工具回调列表
     */
    public List<ToolCallback> buildCallbacks(List<Long> boundSubAgentIds) {
        if (boundSubAgentIds == null || boundSubAgentIds.isEmpty()) {
            return List.of();
        }
        List<SubAgent> subs = subAgentService.listByIds(boundSubAgentIds).stream()
                .filter(s -> s != null && Integer.valueOf(1).equals(s.getEnabled()))
                .toList();
        if (subs.isEmpty()) {
            return List.of();
        }

        Map<String, SubAgent> byName = new LinkedHashMap<>();
        for (SubAgent sa : subs) {
            byName.put(sa.getName(), sa);
        }
        return List.of(
                new DelegateCallback(buildDelegateDefinition(subs), byName, subAgentRuntime,
                        subAgentRunMapper, subAgentTaskBatchMapper, objectMapper, lightBotExecutor),
                new ResultCallback(buildResultDefinition(), subAgentRunMapper, subAgentTaskBatchMapper, objectMapper),
                new CancelCallback(buildCancelDefinition(), subAgentRunMapper, subAgentTaskBatchMapper, objectMapper)
        );
    }

    private ToolDefinition buildDelegateDefinition(List<SubAgent> subs) {
        String catalog = subs.stream()
                .map(s -> "- " + s.getName() + "（" + displayName(s) + "）: "
                        + (s.getDescription() != null ? s.getDescription() : ""))
                .collect(Collectors.joining("\n"));
        String description = """
                把一个或多个子任务委派给专门的子智能体执行，并返回结构化结果。
                可委派的子智能体清单（必须严格使用 name 字段，不能编造名称）：
                """ + catalog + """

                使用规则：
                1. 简单单任务可继续使用 subagent_name + task，等价于 mode=sync。
                2. 多任务请使用 tasks 数组；mode=parallel 时会并行执行多个 SubAgent。
                3. mode=background 会立即返回 batch_id/task_id，稍后用 get_subagent_task_result 查询结果。
                4. task 必须完整、自包含（背景 + 目标 + 期望产物），子智能体看不到主对话历史。
                5. 主 Agent 需基于返回结果继续对话、汇总或说明失败原因。
                """;

        String namesEnum = subs.stream()
                .map(SubAgent::getName)
                .map(this::quoteJson)
                .collect(Collectors.joining(", "));
        String inputSchema = """
                {
                  "type": "object",
                  "properties": {
                    "mode": {
                      "type": "string",
                      "enum": ["sync", "parallel", "background"],
                      "description": "执行模式：sync 单/多任务串行等待；parallel 多任务并行等待；background 后台执行并返回 batch_id/task_id"
                    },
                    "subagent_name": {"type": "string", "enum": [%s], "description": "兼容旧参数：目标子智能体 name"},
                    "task": {"type": "string", "description": "兼容旧参数：完整的子任务描述"},
                    "thread_id": {"type": "string", "description": "兼容旧参数：子代理线程 ID，传入时续跑已有会话"},
                    "tasks": {
                      "type": "array",
                      "description": "多任务委派列表；存在时优先使用该字段",
                      "items": {
                        "type": "object",
                        "properties": {
                          "subagent_name": {"type": "string", "enum": [%s]},
                          "task": {"type": "string"},
                          "thread_id": {"type": "string"}
                        },
                        "required": ["subagent_name", "task"]
                      }
                    },
                    "max_concurrency": {"type": "integer", "description": "parallel/background 模式最大并发，1-5"},
                    "aggregation": {
                      "type": "string",
                      "enum": ["return_all", "summarize"],
                      "description": "结果聚合偏好；当前工具返回结构化 results，主 Agent 负责最终汇总"
                    }
                  }
                }
                """.formatted(namesEnum, namesEnum);

        return DefaultToolDefinition.builder()
                .name(TOOL_NAME)
                .description(description)
                .inputSchema(inputSchema)
                .build();
    }

    private ToolDefinition buildResultDefinition() {
        String inputSchema = """
                {
                  "type": "object",
                  "properties": {
                    "task_id": {"type": "string", "description": "单个任务 ID"},
                    "task_ids": {"type": "array", "items": {"type": "string"}, "description": "多个任务 ID"},
                    "batch_id": {"type": "string", "description": "委派批次 ID"}
                  }
                }
                """;
        return DefaultToolDefinition.builder()
                .name(RESULT_TOOL_NAME)
                .description("查询 SubAgent 后台任务或批次结果。可传 task_id、task_ids 或 batch_id。")
                .inputSchema(inputSchema)
                .build();
    }

    private ToolDefinition buildCancelDefinition() {
        String inputSchema = """
                {
                  "type": "object",
                  "properties": {
                    "task_id": {"type": "string", "description": "要取消的单个任务 ID"},
                    "batch_id": {"type": "string", "description": "要取消的委派批次 ID"}
                  }
                }
                """;
        return DefaultToolDefinition.builder()
                .name(CANCEL_TOOL_NAME)
                .description("取消后台 SubAgent 任务。可按 task_id 或 batch_id 请求取消；运行中的任务会在下一次检查点停止。")
                .inputSchema(inputSchema)
                .build();
    }

    private String quoteJson(String value) {
        try {
            return objectMapper.writeValueAsString(value != null ? value : "");
        } catch (Exception e) {
            return "\"\"";
        }
    }

    private static String displayName(SubAgent subAgent) {
        return subAgent.getDisplayName() != null && !subAgent.getDisplayName().isBlank()
                ? subAgent.getDisplayName() : subAgent.getName();
    }

    private record DelegateTask(String subagentName, String task, String threadId) {}

    private record ParsedInput(String mode, List<DelegateTask> tasks, int maxConcurrency, String aggregation) {}

    private record ToolRuntimeContext(String requestId, String parentThreadId, Long parentSessionId, ChatContext chatContext) {}

    private static class DelegateCallback implements ToolCallback {
        private final ToolDefinition definition;
        private final Map<String, SubAgent> byName;
        private final SubAgentRuntime runtime;
        private final SubAgentRunMapper subAgentRunMapper;
        private final SubAgentTaskBatchMapper subAgentTaskBatchMapper;
        private final ObjectMapper objectMapper;
        private final Executor executor;

        DelegateCallback(ToolDefinition definition,
                         Map<String, SubAgent> byName,
                         SubAgentRuntime runtime,
                         SubAgentRunMapper subAgentRunMapper,
                         SubAgentTaskBatchMapper subAgentTaskBatchMapper,
                         ObjectMapper objectMapper,
                         Executor executor) {
            this.definition = definition;
            this.byName = byName;
            this.runtime = runtime;
            this.subAgentRunMapper = subAgentRunMapper;
            this.subAgentTaskBatchMapper = subAgentTaskBatchMapper;
            this.objectMapper = objectMapper;
            this.executor = executor;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return definition;
        }

        @Override
        public ToolMetadata getToolMetadata() {
            return ToolMetadata.builder().returnDirect(false).build();
        }

        @Override
        public String call(String toolInput) {
            return call(toolInput, null);
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            ParsedInput input;
            try {
                input = parseInput(toolInput);
            } catch (Exception e) {
                return failure("参数解析失败: " + e.getMessage());
            }
            String validationError = validateInput(input);
            if (validationError != null) {
                return failure(validationError);
            }

            ToolRuntimeContext runtimeContext = resolveRuntimeContext(toolContext);
            String batchId = scopedBatchId(runtimeContext.requestId(), input);
            ensureBatch(batchId, input, runtimeContext);
            ensureTaskRows(batchId, input, runtimeContext);

            if ("background".equals(input.mode())) {
                return runBackground(batchId, input, runtimeContext);
            }
            if ("parallel".equals(input.mode()) && input.tasks().size() > 1) {
                return runParallel(batchId, input, runtimeContext);
            }
            return runSequential(batchId, input, runtimeContext);
        }

        private ParsedInput parseInput(String toolInput) throws Exception {
            Map<String, Object> args = objectMapper.readValue(
                    toolInput != null && !toolInput.isBlank() ? toolInput : "{}", new TypeReference<>() {});
            String mode = asString(args.get("mode"));
            if (mode == null || mode.isBlank()) {
                mode = "sync";
            }
            mode = mode.trim().toLowerCase();
            String aggregation = asString(args.get("aggregation"));
            if (aggregation == null || aggregation.isBlank()) {
                aggregation = "return_all";
            }

            int maxConcurrency = DEFAULT_MAX_CONCURRENCY;
            Object maxConcurrencyObj = args.get("max_concurrency");
            if (maxConcurrencyObj instanceof Number n) {
                maxConcurrency = n.intValue();
            } else if (maxConcurrencyObj != null) {
                try {
                    maxConcurrency = Integer.parseInt(maxConcurrencyObj.toString());
                } catch (Exception ignored) {
                    maxConcurrency = DEFAULT_MAX_CONCURRENCY;
                }
            }
            maxConcurrency = Math.max(1, Math.min(MAX_TASKS_PER_CALL, maxConcurrency));

            return new ParsedInput(mode, parseTasks(args), maxConcurrency, aggregation);
        }

        @SuppressWarnings("unchecked")
        private List<DelegateTask> parseTasks(Map<String, Object> args) {
            List<DelegateTask> tasks = new ArrayList<>();
            Object taskListObj = args.get("tasks");
            if (taskListObj instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> raw) {
                        Map<String, Object> map = (Map<String, Object>) raw;
                        tasks.add(new DelegateTask(
                                asString(map.get("subagent_name")),
                                asString(map.get("task")),
                                asString(map.get("thread_id"))));
                    }
                }
            }
            if (tasks.isEmpty()) {
                tasks.add(new DelegateTask(
                        asString(args.get("subagent_name")),
                        asString(args.get("task")),
                        asString(args.get("thread_id"))));
            }
            return tasks.size() > MAX_TASKS_PER_CALL ? tasks.subList(0, MAX_TASKS_PER_CALL) : tasks;
        }

        private String validateInput(ParsedInput input) {
            if (!List.of("sync", "parallel", "background").contains(input.mode())) {
                return "mode 仅支持 sync、parallel、background";
            }
            if (input.tasks().isEmpty()) {
                return "缺少 tasks 或 subagent_name/task 参数";
            }
            for (DelegateTask task : input.tasks()) {
                if (task.subagentName() == null || task.subagentName().isBlank()) {
                    return "缺少 subagent_name 参数";
                }
                if (task.task() == null || task.task().isBlank()) {
                    return "缺少 task 参数";
                }
                if (!byName.containsKey(task.subagentName())) {
                    return "未在当前 Agent 绑定列表中找到子智能体: " + task.subagentName() + "，可选: " + byName.keySet();
                }
            }
            return null;
        }

        private String runSequential(String batchId, ParsedInput input, ToolRuntimeContext runtimeContext) {
            markBatchRunning(batchId);
            List<Map<String, Object>> results = new ArrayList<>();
            for (int i = 0; i < input.tasks().size(); i++) {
                results.add(runOne(batchId, input.tasks().get(i), runtimeContext, i));
                refreshBatchStats(batchId);
            }
            return success(batchId, input.mode(), input.aggregation(), false, results);
        }

        private String runParallel(String batchId, ParsedInput input, ToolRuntimeContext runtimeContext) {
            markBatchRunning(batchId);
            List<Map<String, Object>> results = new ArrayList<>();
            for (int start = 0; start < input.tasks().size(); start += input.maxConcurrency()) {
                int end = Math.min(input.tasks().size(), start + input.maxConcurrency());
                List<CompletableFuture<Map<String, Object>>> futures = new ArrayList<>();
                for (int i = start; i < end; i++) {
                    DelegateTask task = input.tasks().get(i);
                    int taskIndex = i;
                    futures.add(CompletableFuture.supplyAsync(
                            () -> runOne(batchId, task, runtimeContext, taskIndex), executor));
                }
                for (CompletableFuture<Map<String, Object>> future : futures) {
                    results.add(future.join());
                }
                refreshBatchStats(batchId);
            }
            return success(batchId, input.mode(), input.aggregation(), false, results);
        }

        private String runBackground(String batchId, ParsedInput input, ToolRuntimeContext runtimeContext) {
            markBatchRunning(batchId);
            List<Map<String, Object>> results = new ArrayList<>();
            ToolRuntimeContext backgroundContext = new ToolRuntimeContext(
                    runtimeContext.requestId(),
                    runtimeContext.parentThreadId(),
                    runtimeContext.parentSessionId(),
                    backgroundChatContext(runtimeContext.chatContext()));
            for (int i = 0; i < input.tasks().size(); i++) {
                DelegateTask task = input.tasks().get(i);
                int taskIndex = i;
                String taskId = scopedRequestId(runtimeContext.requestId(), task, taskIndex);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("subagent_name", task.subagentName());
                item.put("task_id", taskId);
                item.put("batch_id", batchId);
                item.put("status", "submitted");
                results.add(item);
                executor.execute(() -> {
                    runOne(batchId, task, backgroundContext, taskIndex);
                    refreshBatchStats(batchId);
                });
            }
            return success(batchId, input.mode(), input.aggregation(), true, results);
        }

        private ChatContext backgroundChatContext(ChatContext source) {
            if (source == null) {
                return null;
            }
            ChatContext ctx = new ChatContext();
            ctx.setProviderId(source.getProviderId());
            ctx.setConfigMap(source.getConfigMap());
            return ctx;
        }

        private Map<String, Object> runOne(String batchId, DelegateTask task,
                                           ToolRuntimeContext runtimeContext, int taskIndex) {
            SubAgent target = byName.get(task.subagentName());
            String taskId = scopedRequestId(runtimeContext.requestId(), task, taskIndex);
            try {
                SubAgentRuntime.SubAgentResult result = runtime.run(
                        target, task.task(), taskId, task.threadId(), runtimeContext.parentThreadId(), runtimeContext.chatContext());
                SubAgentRun latest = subAgentRunMapper.selectByRequestId(taskId);
                String status = latest != null ? latest.getStatus() : "completed";
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("subagent_name", task.subagentName());
                out.put("display_name", displayName(target));
                out.put("task_id", taskId);
                out.put("batch_id", batchId);
                out.put("status", status);
                out.put("reply", result.reply());
                out.put("thread_id", result.threadId());
                out.put("continued", result.continued());
                return out;
            } catch (Exception e) {
                log.warn("[SubAgent] 委派任务失败: subagent={}, error={}", task.subagentName(), e.getMessage());
                SubAgentRun run = subAgentRunMapper.selectByRequestId(taskId);
                if (run != null) {
                    run.setStatus("failed");
                    run.setErrorMessage(e.getMessage());
                    run.setEndTime(LocalDateTime.now());
                    subAgentRunMapper.updateById(run);
                }
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("subagent_name", task.subagentName());
                out.put("display_name", target != null ? displayName(target) : task.subagentName());
                out.put("task_id", taskId);
                out.put("batch_id", batchId);
                out.put("status", "failed");
                out.put("error", e.getMessage());
                return out;
            }
        }

        private void ensureBatch(String batchId, ParsedInput input, ToolRuntimeContext runtimeContext) {
            SubAgentTaskBatch existing = subAgentTaskBatchMapper.selectByBatchId(batchId);
            if (existing != null) {
                return;
            }
            SubAgentTaskBatch batch = new SubAgentTaskBatch();
            batch.setBatchId(batchId);
            batch.setParentRequestId(runtimeContext.requestId());
            batch.setParentThreadId(runtimeContext.parentThreadId());
            batch.setParentSessionId(runtimeContext.parentSessionId());
            batch.setMode(input.mode());
            batch.setAggregation(input.aggregation());
            batch.setStatus("pending");
            batch.setTotalCount(input.tasks().size());
            batch.setCompletedCount(0);
            batch.setFailedCount(0);
            batch.setCancelledCount(0);
            batch.setCancelRequested(0);
            subAgentTaskBatchMapper.insert(batch);
        }

        private void ensureTaskRows(String batchId, ParsedInput input, ToolRuntimeContext runtimeContext) {
            for (int i = 0; i < input.tasks().size(); i++) {
                DelegateTask task = input.tasks().get(i);
                String taskId = scopedRequestId(runtimeContext.requestId(), task, i);
                if (subAgentRunMapper.selectByRequestId(taskId) != null) {
                    continue;
                }
                SubAgentRun run = new SubAgentRun();
                run.setBatchId(batchId);
                run.setParentRequestId(runtimeContext.requestId());
                run.setParentThreadId(runtimeContext.parentThreadId() != null ? runtimeContext.parentThreadId() : "");
                run.setParentSessionId(runtimeContext.parentSessionId());
                run.setSubagentName(task.subagentName());
                run.setTask(task.task());
                run.setStatus("pending");
                run.setRequestId(taskId);
                run.setMode(input.mode());
                run.setCancelRequested(0);
                run.setToolCallCount(0);
                run.setThreadId(resolveThreadId(task, runtimeContext, taskId));
                subAgentRunMapper.insert(run);
            }
        }

        private String resolveThreadId(DelegateTask task, ToolRuntimeContext runtimeContext, String taskId) {
            if (task.threadId() != null && !task.threadId().isBlank()) {
                return task.threadId();
            }
            if (runtimeContext.parentThreadId() != null && !runtimeContext.parentThreadId().isBlank()) {
                return SubAgentThreadManager.makeChildThreadId(runtimeContext.parentThreadId(), task.subagentName(), taskId);
            }
            return "subagent_" + System.currentTimeMillis() + "_" + taskId.substring(Math.max(0, taskId.length() - 8));
        }

        private void markBatchRunning(String batchId) {
            SubAgentTaskBatch batch = subAgentTaskBatchMapper.selectByBatchId(batchId);
            if (batch == null || "completed".equals(batch.getStatus())
                    || "failed".equals(batch.getStatus()) || "cancelled".equals(batch.getStatus())) {
                return;
            }
            batch.setStatus("running");
            subAgentTaskBatchMapper.updateById(batch);
        }

        private void refreshBatchStats(String batchId) {
            SubAgentTaskBatch batch = subAgentTaskBatchMapper.selectByBatchId(batchId);
            if (batch == null) {
                return;
            }
            List<SubAgentRun> runs = subAgentRunMapper.selectByBatchId(batchId);
            int completed = 0;
            int failed = 0;
            int cancelled = 0;
            int running = 0;
            for (SubAgentRun run : runs) {
                if ("completed".equals(run.getStatus())) completed++;
                else if ("failed".equals(run.getStatus())) failed++;
                else if ("cancelled".equals(run.getStatus())) cancelled++;
                else if ("running".equals(run.getStatus())) running++;
            }
            batch.setCompletedCount(completed);
            batch.setFailedCount(failed);
            batch.setCancelledCount(cancelled);
            if (cancelled == runs.size() && !runs.isEmpty()) {
                batch.setStatus("cancelled");
            } else if (completed + failed + cancelled >= runs.size() && !runs.isEmpty()) {
                batch.setStatus(failed > 0 ? "failed" : "completed");
            } else if (running > 0) {
                batch.setStatus("running");
            }
            subAgentTaskBatchMapper.updateById(batch);
        }

        private ToolRuntimeContext resolveRuntimeContext(ToolContext toolContext) {
            String requestId = null;
            String parentThreadId = null;
            Long parentSessionId = null;
            ChatContext chatContext = null;
            if (toolContext != null && toolContext.getContext() != null) {
                Object rid = toolContext.getContext().get("requestId");
                if (rid != null) requestId = rid.toString();
                Object ptid = toolContext.getContext().get("parentThreadId");
                if (ptid != null) parentThreadId = ptid.toString();
                Object sid = toolContext.getContext().get("sessionId");
                if (sid != null) {
                    try {
                        parentSessionId = Long.parseLong(sid.toString());
                    } catch (Exception ignored) {
                        parentSessionId = null;
                    }
                }
                Object cctx = toolContext.getContext().get("chatContext");
                if (cctx instanceof ChatContext cc) chatContext = cc;
            }
            if (requestId == null || requestId.isBlank()) {
                requestId = "subagent_" + System.currentTimeMillis();
            }
            return new ToolRuntimeContext(requestId, parentThreadId, parentSessionId, chatContext);
        }

        private String scopedBatchId(String parentRequestId, ParsedInput input) {
            String raw = (parentRequestId != null ? parentRequestId : "")
                    + ":" + input.mode()
                    + ":" + input.aggregation()
                    + ":" + input.tasks().stream()
                            .map(t -> t.subagentName() + "|" + t.task() + "|" + t.threadId())
                            .collect(Collectors.joining(";"));
            return "subagent_batch_" + DigestUtils.md5DigestAsHex(raw.getBytes(StandardCharsets.UTF_8));
        }

        private String scopedRequestId(String parentRequestId, DelegateTask task, int taskIndex) {
            String raw = (parentRequestId != null ? parentRequestId : "")
                    + ":" + taskIndex
                    + ":" + (task.subagentName() != null ? task.subagentName() : "")
                    + ":" + (task.task() != null ? task.task() : "")
                    + ":" + (task.threadId() != null ? task.threadId() : "");
            return "subagent_task_" + DigestUtils.md5DigestAsHex(raw.getBytes(StandardCharsets.UTF_8));
        }

        private String success(String batchId, String mode, String aggregation,
                               boolean background, List<Map<String, Object>> results) {
            try {
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("batch_id", batchId);
                out.put("mode", mode);
                out.put("aggregation", aggregation);
                out.put("background", background);
                out.put("results", results);
                if (results.size() == 1) {
                    Map<String, Object> first = results.get(0);
                    out.put("reply", first.get("reply"));
                    out.put("thread_id", first.get("thread_id"));
                    out.put("task_id", first.get("task_id"));
                    out.put("continued", first.get("continued"));
                }
                return objectMapper.writeValueAsString(out);
            } catch (Exception e) {
                return results.toString();
            }
        }

        private String failure(String message) {
            try {
                return objectMapper.writeValueAsString(Map.of("status", "failed", "error", message));
            } catch (Exception e) {
                return message;
            }
        }

        private static String asString(Object value) {
            return value != null ? value.toString() : null;
        }
    }

    private static class ResultCallback implements ToolCallback {
        private final ToolDefinition definition;
        private final SubAgentRunMapper subAgentRunMapper;
        private final SubAgentTaskBatchMapper subAgentTaskBatchMapper;
        private final ObjectMapper objectMapper;

        ResultCallback(ToolDefinition definition,
                       SubAgentRunMapper subAgentRunMapper,
                       SubAgentTaskBatchMapper subAgentTaskBatchMapper,
                       ObjectMapper objectMapper) {
            this.definition = definition;
            this.subAgentRunMapper = subAgentRunMapper;
            this.subAgentTaskBatchMapper = subAgentTaskBatchMapper;
            this.objectMapper = objectMapper;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return definition;
        }

        @Override
        public ToolMetadata getToolMetadata() {
            return ToolMetadata.builder().returnDirect(false).build();
        }

        @Override
        public String call(String toolInput) {
            return call(toolInput, null);
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            try {
                Map<String, Object> args = objectMapper.readValue(
                        toolInput != null && !toolInput.isBlank() ? toolInput : "{}", new TypeReference<>() {});
                String batchId = asString(args.get("batch_id"));
                if (batchId != null && !batchId.isBlank()) {
                    return objectMapper.writeValueAsString(queryBatch(batchId));
                }
                List<String> taskIds = parseTaskIds(args);
                if (taskIds.isEmpty()) {
                    return objectMapper.writeValueAsString(Map.of("status", "failed", "error", "缺少 task_id、task_ids 或 batch_id 参数"));
                }
                List<Map<String, Object>> results = new ArrayList<>();
                for (String taskId : taskIds) {
                    results.add(queryTask(taskId));
                }
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("results", results);
                if (results.size() == 1) {
                    out.putAll(results.get(0));
                }
                return objectMapper.writeValueAsString(out);
            } catch (Exception e) {
                return "{\"status\":\"failed\",\"error\":\"" + e.getMessage() + "\"}";
            }
        }

        private Map<String, Object> queryBatch(String batchId) {
            SubAgentTaskBatch batch = subAgentTaskBatchMapper.selectByBatchId(batchId);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("batch_id", batchId);
            if (batch == null) {
                out.put("status", "pending");
                out.put("results", List.of());
                return out;
            }
            out.put("status", batch.getStatus());
            out.put("mode", batch.getMode());
            out.put("aggregation", batch.getAggregation());
            out.put("total_count", batch.getTotalCount());
            out.put("completed_count", batch.getCompletedCount());
            out.put("failed_count", batch.getFailedCount());
            out.put("cancelled_count", batch.getCancelledCount());
            out.put("cancel_requested", Integer.valueOf(1).equals(batch.getCancelRequested()));
            out.put("results", subAgentRunMapper.selectByBatchId(batchId).stream().map(this::mapTask).toList());
            return out;
        }

        private List<String> parseTaskIds(Map<String, Object> args) {
            List<String> taskIds = new ArrayList<>();
            Object taskId = args.get("task_id");
            if (taskId != null && !taskId.toString().isBlank()) {
                taskIds.add(taskId.toString());
            }
            Object taskIdsObj = args.get("task_ids");
            if (taskIdsObj instanceof List<?> list) {
                for (Object item : list) {
                    if (item != null && !item.toString().isBlank()) {
                        taskIds.add(item.toString());
                    }
                }
            }
            return taskIds;
        }

        private Map<String, Object> queryTask(String taskId) {
            SubAgentRun run = subAgentRunMapper.selectByRequestId(taskId);
            if (run == null) {
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("task_id", taskId);
                out.put("status", "pending");
                return out;
            }
            return mapTask(run);
        }

        private Map<String, Object> mapTask(SubAgentRun run) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("task_id", run.getRequestId());
            out.put("batch_id", run.getBatchId());
            out.put("status", run.getStatus());
            out.put("subagent_name", run.getSubagentName());
            out.put("thread_id", run.getThreadId());
            out.put("mode", run.getMode());
            out.put("reply", run.getReply());
            out.put("error", run.getErrorMessage());
            out.put("cancel_requested", Integer.valueOf(1).equals(run.getCancelRequested()));
            out.put("start_time", run.getStartTime());
            out.put("end_time", run.getEndTime());
            return out;
        }

        private static String asString(Object value) {
            return value != null ? value.toString() : null;
        }
    }

    private static class CancelCallback implements ToolCallback {
        private final ToolDefinition definition;
        private final SubAgentRunMapper subAgentRunMapper;
        private final SubAgentTaskBatchMapper subAgentTaskBatchMapper;
        private final ObjectMapper objectMapper;

        CancelCallback(ToolDefinition definition,
                       SubAgentRunMapper subAgentRunMapper,
                       SubAgentTaskBatchMapper subAgentTaskBatchMapper,
                       ObjectMapper objectMapper) {
            this.definition = definition;
            this.subAgentRunMapper = subAgentRunMapper;
            this.subAgentTaskBatchMapper = subAgentTaskBatchMapper;
            this.objectMapper = objectMapper;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return definition;
        }

        @Override
        public ToolMetadata getToolMetadata() {
            return ToolMetadata.builder().returnDirect(false).build();
        }

        @Override
        public String call(String toolInput) {
            return call(toolInput, null);
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            try {
                Map<String, Object> args = objectMapper.readValue(
                        toolInput != null && !toolInput.isBlank() ? toolInput : "{}", new TypeReference<>() {});
                String taskId = asString(args.get("task_id"));
                String batchId = asString(args.get("batch_id"));
                int affected = 0;
                if (taskId != null && !taskId.isBlank()) {
                    affected += subAgentRunMapper.requestCancelByRequestId(taskId);
                }
                if (batchId != null && !batchId.isBlank()) {
                    affected += subAgentRunMapper.requestCancelByBatchId(batchId);
                    subAgentTaskBatchMapper.requestCancelByBatchId(batchId);
                }
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("status", affected > 0 ? "cancel_requested" : "not_found");
                out.put("affected", affected);
                out.put("task_id", taskId);
                out.put("batch_id", batchId);
                return objectMapper.writeValueAsString(out);
            } catch (Exception e) {
                return "{\"status\":\"failed\",\"error\":\"" + e.getMessage() + "\"}";
            }
        }

        private static String asString(Object value) {
            return value != null ? value.toString() : null;
        }
    }
}
