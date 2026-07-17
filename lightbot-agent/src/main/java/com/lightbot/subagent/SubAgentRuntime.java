package com.lightbot.subagent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.constant.ToolResultPrefixes;
import com.lightbot.entity.SubAgent;
import com.lightbot.entity.SubAgentRun;
import com.lightbot.entity.Tool;
import com.lightbot.mapper.SubAgentRunMapper;
import com.lightbot.model.ModelFactory;
import com.lightbot.model.DashScopeModelSupport;
import com.lightbot.model.ProviderResolver;
import com.lightbot.entity.ModelProvider;
import com.lightbot.enums.ModelProviderType;
import com.lightbot.service.ModelProviderService;
import com.lightbot.service.ToolService;
import com.lightbot.service.chat.ChatContext;
import com.lightbot.service.chat.ToolEventGenerator;
import com.lightbot.subagent.spi.SubAgentDefinition;
import com.lightbot.subagent.spi.SubAgentExecutor;
import com.lightbot.subagent.service.SubAgentTaskEventService;
import com.lightbot.util.ChatMessageContextUtil;
import com.lightbot.util.TextNormalizeUtil;
import com.lightbot.util.ToolArgsSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SubAgent 执行器（流式工具循环）
 * <p>对标 Yuxi 的 task 工具内部 invoke：构造独立的 system_prompt + 子任务，
 * 解析 SubAgent.tools（按 name 查表）形成自己的工具集，
 * 走一轮流式工具调用循环，最终返回 assistant 文本给主 Agent。</p>
 *
 * @author finch
 * @since 2026-05-28
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubAgentRuntime implements SubAgentExecutor {

    private final ModelProviderService modelProviderService;
    private static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 10;
    private static final int DEFAULT_READ_TIMEOUT_SECONDS = 60;
    /** 流式输出期间两个 chunk 之间的最大间隔（秒），超过则视为"响应停滞" */
    private static final int DEFAULT_TOKEN_INTERVAL_TIMEOUT_SECONDS = 30;
    private static final int DEFAULT_MODEL_RETRY_TIMES = 1;
    private static final int MAX_LOOP_DEPTH = 6;

    private final ModelFactory modelFactory;
    private final ToolService toolService;
    private final ProviderResolver providerResolver;
    private final ObjectMapper objectMapper;
    private final SubAgentRunMapper subAgentRunMapper;
    private final SubAgentThreadManager threadManager;
    private final ToolEventGenerator toolEventGenerator;
    private final SubAgentTaskEventService taskEventService;
    private final SubAgentPermissionPolicy permissionPolicy;
    private final ToolArgsSanitizer toolArgsSanitizer;

    /**
     * 子代理执行结果
     *
     * @param reply     最终回复文本
     * @param threadId  子代理线程 ID
     * @param continued 是否为续跑（true=加载了历史消息）
     */
    public record SubAgentResult(String reply, String threadId, boolean continued) {}

    @Override
    public ExecutionResult execute(SubAgentDefinition definition, String task, String taskId,
                                   String threadId, String parentThreadId, ChatContext chatContext) {
        SubAgentResult result = run(definition.source(), task, taskId, threadId, parentThreadId, chatContext);
        return new ExecutionResult(result.reply(), result.threadId(), result.continued());
    }

    /**
     * 同步执行一个 SubAgent，返回最终回答文本。
     * <p>模型解析：SubAgent 未配置独立 Provider 时，继承主 Agent 的 providerId + configMap（含版本快照中的 modelId/参数）。</p>
     *
     * @param subAgent         要委派的子智能体
     * @param taskDescription  主 Agent 给的任务描述
     * @param requestId        请求 ID（透传到工具上下文，用于幂等检查）
     * @param threadId         子代理线程 ID（null 表示新建，非 null 表示续跑）
     * @param parentThreadId   父 Agent 线程 ID（用于生成确定性 threadId）
     * @param chatContext      对话上下文（继承主 Agent 模型配置 + 推送流式事件，可为 null）
     */
    public SubAgentResult run(SubAgent subAgent, String taskDescription,
                              String requestId, String threadId, String parentThreadId,
                              ChatContext chatContext) {
        if (subAgent == null) {
            return new SubAgentResult("SubAgent 不存在", null, false);
        }

        // 1. 幂等性检查：同一 requestId 已完成则直接返回；未完成则复用已有任务记录
        SubAgentRun run = null;
        if (requestId != null && !requestId.isBlank()) {
            SubAgentRun existing = subAgentRunMapper.selectByRequestId(requestId);
            if (existing != null && isTerminal(existing.getStatus())) {
                log.info("[SubAgent] 幂等命中: requestId=[{}], status=[{}]", requestId, existing.getStatus());
                return new SubAgentResult(
                        existing.getReply() != null ? existing.getReply() : "",
                        existing.getThreadId(),
                        true);
            }
            if (existing != null) {
                if (isCancelRequested(existing)) {
                    markCancelled(existing, "SubAgent task cancelled before start");
                    return new SubAgentResult("SubAgent 任务已取消", existing.getThreadId(), false);
                }
                run = existing;
                if (threadId == null || threadId.isBlank()) {
                    threadId = existing.getThreadId();
                }
            }
        }

        // 2. 确定 threadId
        boolean continued = false;
        if (threadId == null || threadId.isBlank()) {
            threadId = parentThreadId != null
                    ? SubAgentThreadManager.makeChildThreadId(parentThreadId, subAgent.getName(), requestId)
                    : "subagent_" + System.currentTimeMillis();
        }

        long start = System.currentTimeMillis();
        int connectTimeoutSeconds = resolveConnectTimeoutSeconds(subAgent);
        int readTimeoutSeconds = resolveReadTimeoutSeconds(subAgent, chatContext);
        int modelRetryTimes = resolveModelRetryTimes(subAgent);
        log.info("[SubAgent] 委派开始: name={}, threadId={}, taskLen={}, connect={}s, tokenInterval={}s, retry={}",
                subAgent.getName(), threadId, taskDescription != null ? taskDescription.length() : 0,
                connectTimeoutSeconds, readTimeoutSeconds, modelRetryTimes);

        // 3. 创建或复用运行记录。后台任务会先写入 pending 记录，运行时只更新状态。
        if (run == null) {
            run = new SubAgentRun();
        }
        run.setThreadId(threadId);
        run.setParentThreadId(parentThreadId != null ? parentThreadId : "");
        run.setSubagentName(subAgent.getName());
        run.setTask(taskDescription);
        run.setStatus("running");
        run.setRequestId(requestId != null ? requestId : threadId);
        run.setStartTime(LocalDateTime.now());
        run.setToolCallCount(0);
        if (run.getId() == null) {
            run.setCancelRequested(0);
            subAgentRunMapper.insert(run);
        } else {
            subAgentRunMapper.updateById(run);
        }

        try {
            // 4. 解析子 Agent 的工具集合（按 ID 查 tool 表）
            List<String> toolIdStrings = parseToolIds(subAgent.getToolIds());
            List<Long> toolIds = toolIdStrings.stream().map(Long::parseLong).toList();
            List<Tool> boundTools = toolIds.isEmpty() ? List.of() : toolService.listByIds(toolIds);
            List<Long> executableToolIds = permissionPolicy.filterExecutableToolIds(subAgent, boundTools);
            List<ToolCallback> toolCallbacks = executableToolIds.isEmpty()
                    ? List.of()
                    : toolService.resolveToolCallbacksByIds(executableToolIds);
            Map<String, ToolCallback> toolMap = new HashMap<>();
            Map<String, String> toolDisplayNameMap = new HashMap<>();
            for (ToolCallback cb : toolCallbacks) {
                toolMap.put(cb.getToolDefinition().name(), cb);
            }
            if (!executableToolIds.isEmpty()) {
                for (Tool tool : boundTools) {
                    if (!executableToolIds.contains(tool.getId())) {
                        continue;
                    }
                    if (tool != null && tool.getName() != null) {
                        toolDisplayNameMap.put(tool.getName(),
                                tool.getDisplayName() != null && !tool.getDisplayName().isBlank()
                                        ? tool.getDisplayName() : tool.getName());
                    }
                }
            }

            // 5. 准备模型：独立配置优先，否则继承主 Agent（含版本快照 configMap）
            ResolvedModel resolved = resolveModel(subAgent, chatContext);
            ChatModel chatModel = modelFactory.getChatModel(resolved.providerId());
            log.info("[SubAgent] 模型: name={}, providerId={}, modelId={}, inherit={}",
                    subAgent.getName(), resolved.providerId(),
                    resolved.configMap().get("modelId"),
                    subAgent.getModelId() == null);

            // 6. 构造消息：续跑加载历史，否则新建
            List<Message> messages;
            if (threadManager.threadExists(threadId)) {
                messages = new ArrayList<>(threadManager.loadMessages(threadId));
                if (!messages.isEmpty() && messages.get(0) instanceof SystemMessage) {
                    messages.set(0, new SystemMessage(subAgent.getSystemPrompt() != null ? subAgent.getSystemPrompt() : ""));
                }
                messages.add(new UserMessage(taskDescription != null ? taskDescription : ""));
                continued = true;
            } else {
                messages = new ArrayList<>();
                messages.add(new SystemMessage(subAgent.getSystemPrompt() != null ? subAgent.getSystemPrompt() : ""));
                messages.add(new UserMessage(taskDescription != null ? taskDescription : ""));
            }

            // 7. 构造 ChatOptions（继承主 Agent 的 modelId/temperature 等 + 注入子工具集）
            ToolCallingChatOptions options = buildSubAgentChatOptions(
                    resolved.providerId(), resolved.configMap(), toolCallbacks, subAgent, requestId);

            // 8. 流式工具循环：直至模型返回不含 tool_call 的纯文本，或达到深度上限
            // 超时语义：首字超时（connectTimeoutSeconds）+ token 间隔超时（resolveTokenIntervalTimeoutSeconds），
            // 流式输出期间不做总时长判定——长输出不会再被误判为"响应超时"
            String reply = "";
            int toolCallCount = 0;
            for (int depth = 0; depth < MAX_LOOP_DEPTH; depth++) {
                if (chatContext != null && chatContext.isAborted()) {
                    markCancelled(run, "SubAgent execution cancelled by client");
                    return new SubAgentResult("", threadId, continued);
                }
                if (isCancelRequested(run)) {
                    markCancelled(run, "SubAgent task cancelled");
                    emitSubAgentError(chatContext, subAgent, "SubAgent 任务已取消", "CANCELLED");
                    return new SubAgentResult("", threadId, continued);
                }
                StringBuilder replyBuilder = new StringBuilder();
                AssistantMessage assistant;
                try {
                    prepareMessagesForLlm(messages);
                    assistant = streamLlmWithRetry(
                            chatModel, new Prompt(new ArrayList<>(messages), options),
                            subAgent, chatContext, modelRetryTimes, replyBuilder, depth,
                            connectTimeoutSeconds, readTimeoutSeconds);
                } catch (Exception e) {
                    String errorMsg = classifyErrorMessage(e);
                    log.error("[SubAgent] 模型调用失败: name={}, depth={}, error={}",
                            subAgent.getName(), depth, e.getMessage(), e);
                    emitSubAgentError(chatContext, subAgent, errorMsg, classifyErrorCode(e));
                    markFailed(run, errorMsg, start);
                    return new SubAgentResult(errorMsg, threadId, false);
                }

                if (assistant == null) {
                    break;
                }
                if (!assistant.hasToolCalls()) {
                    reply = replyBuilder.length() > 0 ? replyBuilder.toString()
                            : (assistant.getText() != null ? assistant.getText() : "");
                    break;
                }

                // 8.2 模型要求调用工具：逐个执行后回填
                messages.add(assistant);
                List<ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();
                for (AssistantMessage.ToolCall tc : assistant.getToolCalls()) {
                    emitSubAgentToolCall(chatContext, subAgent, tc, toolDisplayNameMap);

                    String result;
                    ToolCallback cb = toolMap.get(tc.name());
                    if (cb == null) {
                        result = ToolResultPrefixes.failureJson(ToolResultPrefixes.NOT_FOUND + ": " + tc.name());
                    } else {
                        try {
                            String rawArgs = tc.arguments() != null ? tc.arguments() : "{}";
                            String callArgs = rawArgs;
                            String repaired = toolArgsSanitizer.tryRepairTruncatedWriteArgs(tc.name(), rawArgs);
                            if (repaired != null) {
                                callArgs = repaired.replaceAll(",\\s*\"_repairedFromTruncation\"\\s*:\\s*true", "")
                                        .replaceAll("\"_repairedFromTruncation\"\\s*:\\s*true\\s*,?", "");
                            } else {
                                callArgs = toolArgsSanitizer.forChatCall(rawArgs);
                            }
                            result = cb.call(callArgs,
                                    new ToolContext(Map.of(
                                            "subAgentId", subAgent.getId(),
                                            "subAgentName", subAgent.getName(),
                                            "requestId", requestId != null ? requestId : "")));
                        } catch (Exception e) {
                            log.warn("[SubAgent] 工具执行异常: subAgent={}, tool={}, error={}",
                                    subAgent.getName(), tc.name(), e.getMessage());
                            result = ToolResultPrefixes.failureJson(ToolResultPrefixes.FAILURE + ": " + e.getMessage());
                        }
                    }

                    emitSubAgentToolResult(chatContext, subAgent, tc, result, toolDisplayNameMap);

                    result = ChatMessageContextUtil.capToolResult(result, ChatMessageContextUtil.MAX_SINGLE_TOOL_RESULT_CHARS);
                    toolResponses.add(new ToolResponseMessage.ToolResponse(tc.id(), tc.name(), result));
                    toolCallCount++;
                }
                messages.add(ToolResponseMessage.builder().responses(toolResponses).build());
            }

            // 9. 保存消息历史（续跑用）
            threadManager.saveMessages(threadId, messages);

            // 10. 更新运行记录为完成
            String finalReply = reply.isBlank()
                    ? "（SubAgent " + subAgent.getName() + " 未返回有效内容）"
                    : TextNormalizeUtil.sanitizeForAiMessage(reply, 0);
            long cost = System.currentTimeMillis() - start;
            run.setReply(finalReply);
            run.setStatus("completed");
            run.setToolCallCount(toolCallCount);
            run.setEndTime(LocalDateTime.now());
            subAgentRunMapper.updateById(run);
            log.info("[SubAgent] 委派完成: name={}, 耗时={}ms, replyLen={}", subAgent.getName(), cost, reply.length());
            return new SubAgentResult(finalReply, threadId, continued);

        } catch (Exception e) {
            String errorMsg = "SubAgent 执行失败: " + e.getMessage();
            emitSubAgentError(chatContext, subAgent, errorMsg, "UNKNOWN");
            markFailed(run, errorMsg, start);
            return new SubAgentResult(errorMsg, threadId, false);
        }
    }

    /**
     * LLM 调用前规范化并裁剪消息，避免空 content 或工具结果撑爆 DashScope 输入上限
     */
    private void prepareMessagesForLlm(List<Message> messages) {
        ChatMessageContextUtil.normalizeMessagesForLlm(messages);
        ChatMessageContextUtil.trimToolCallContext(
                messages,
                ChatMessageContextUtil.DASHSCOPE_SAFE_INPUT_CHARS,
                ChatMessageContextUtil.DEFAULT_TOOL_ROUNDS_TO_KEEP);
    }

    /** 解析后的模型配置：Provider ID + 模型参数字典 */
    private record ResolvedModel(Long providerId, Map<String, Object> configMap) {}

    /**
     * 模型解析：SubAgent 独立 Provider 优先；否则继承主 Agent 的 providerId + configMap（含版本快照）
     */
    private ResolvedModel resolveModel(SubAgent subAgent, ChatContext chatContext) {
        if (subAgent.getModelId() != null) {
            Map<String, Object> cfg = new HashMap<>();
            if (subAgent.getLlmModel() != null && !subAgent.getLlmModel().isBlank()) {
                cfg.put("modelId", subAgent.getLlmModel());
            }
            return new ResolvedModel(subAgent.getModelId(), cfg);
        }
        if (chatContext != null && chatContext.getProviderId() != null) {
            Map<String, Object> cfg = chatContext.getConfigMap() != null
                    ? new HashMap<>(chatContext.getConfigMap()) : new HashMap<>();
            return new ResolvedModel(chatContext.getProviderId(), cfg);
        }
        return new ResolvedModel(providerResolver.resolve(), Map.of());
    }

    /**
     * 构建 SubAgent ChatOptions：继承 modelId/temperature 等，并注入子工具集
     */
    private ToolCallingChatOptions buildSubAgentChatOptions(Long providerId,
                                                             Map<String, Object> configMap,
                                                             List<ToolCallback> toolCallbacks,
                                                             SubAgent subAgent, String requestId) {
        String modelId = configMap != null && configMap.get("modelId") != null
                ? configMap.get("modelId").toString() : null;
        Map<String, Object> toolContext = null;
        if (!toolCallbacks.isEmpty()) {
            toolContext = Map.of(
                    "subAgentId", subAgent.getId(),
                    "subAgentName", subAgent.getName(),
                    "requestId", requestId != null ? requestId : "");
        }

        ModelProvider provider = providerId != null ? modelProviderService.getById(providerId) : null;
        if (provider != null && provider.getType() == ModelProviderType.DASHSCOPE
                && !DashScopeModelSupport.isCompatibleMode(provider.getBaseUrl())) {
            return DashScopeModelSupport.buildNativeChatOptions(
                    modelId, configMap, toolCallbacks, toolContext);
        }

        ToolCallingChatOptions.Builder builder = ToolCallingChatOptions.builder();
        if (modelId != null) {
            builder.model(modelId);
        }
        if (configMap != null) {
            if (configMap.containsKey("temperature")) {
                Object v = configMap.get("temperature");
                builder.temperature(v instanceof Number n ? n.doubleValue() : Double.parseDouble(v.toString()));
            }
            if (configMap.containsKey("topP")) {
                Object v = configMap.get("topP");
                builder.topP(v instanceof Number n ? n.doubleValue() : Double.parseDouble(v.toString()));
            }
            if (configMap.containsKey("maxTokens")) {
                Object v = configMap.get("maxTokens");
                builder.maxTokens(v instanceof Number n ? n.intValue() : Integer.parseInt(v.toString()));
            }
        }
        if (!toolCallbacks.isEmpty()) {
            builder.toolCallbacks(toolCallbacks);
            builder.toolContext(toolContext);
        }
        ToolCallingChatOptions options = builder.build();
        options.setInternalToolExecutionEnabled(false);
        if (provider != null) {
            options = modelFactory.adaptToolCallingOptions(provider, configMap, options);
        }
        return options;
    }

    /**
     * 带重试的流式 LLM 调用（对齐主 Agent streamModelWithRetry 策略）
     */
    private AssistantMessage streamLlmWithRetry(ChatModel chatModel, Prompt prompt, SubAgent subAgent,
                                                 ChatContext chatContext, int retryTimes,
                                                 StringBuilder replyBuilder, int depth,
                                                 int connectTimeoutSeconds, int readTimeoutSeconds) throws Exception {
        Exception lastError = null;
        for (int attempt = 0; attempt <= retryTimes; attempt++) {
            try {
                return streamLlmOnce(chatModel, prompt, subAgent, chatContext, replyBuilder,
                        connectTimeoutSeconds * 1000L, connectTimeoutSeconds, readTimeoutSeconds);
            } catch (Exception e) {
                lastError = e;
                if (attempt < retryTimes) {
                    int retryNo = attempt + 1;
                    long delayMs = (long) Math.pow(2, attempt) * 1000;
                    String reason = classifyFailureReason(e);
                    log.warn("[SubAgent] 模型调用失败，第{}次重试，等待{}ms: name={}, depth={}, reason={}, error={}",
                            retryNo, delayMs, subAgent.getName(), depth, reason, e.getMessage());
                    emitSubAgentErrorRetry(chatContext, subAgent,
                            buildRetryMessage(subAgent, reason, retryNo, retryTimes),
                            reasonToCode(reason), retryNo, retryTimes);
                    Thread.sleep(delayMs);
                }
            }
        }
        throw lastError != null ? lastError : new RuntimeException("SubAgent 模型调用失败");
    }

    /** 单次流式 LLM 调用：首字超时（connectTimeoutSeconds）+ token 间隔超时（tokenIntervalTimeoutSeconds） */
    private AssistantMessage streamLlmOnce(ChatModel chatModel, Prompt prompt, SubAgent subAgent,
                                          ChatContext chatContext, StringBuilder replyBuilder, long remainingMs,
                                          int connectTimeoutSeconds, int readTimeoutSeconds) {
        List<AssistantMessage> lastAssistant = new ArrayList<>();
        java.util.concurrent.atomic.AtomicBoolean completed = new java.util.concurrent.atomic.AtomicBoolean(false);
        java.util.concurrent.atomic.AtomicBoolean firstReceived = new java.util.concurrent.atomic.AtomicBoolean(false);
        StringBuilder streamSnapshot = new StringBuilder();
        int tokenIntervalSec = resolveTokenIntervalTimeoutSeconds(subAgent);

        java.util.function.Consumer<ChatResponse> processChunk = response -> {
            firstReceived.set(true);
            Generation gen = response.getResult();
            if (gen != null && gen.getOutput() != null) {
                AssistantMessage output = gen.getOutput();
                if (lastAssistant.isEmpty()) {
                    lastAssistant.add(output);
                } else {
                    lastAssistant.set(0, output);
                }
                String text = output.getText();
                if (text != null && !text.isEmpty()) {
                    String delta = consumeStreamTextDelta(streamSnapshot, text);
                    if (!delta.isEmpty()) {
                        replyBuilder.append(delta);
                        pushTokenEvent(chatContext, subAgent, delta);
                    }
                }
            }
        };

        Flux<ChatResponse> flux = chatModel.stream(prompt);
        if (chatContext != null) {
            flux = flux.takeUntilOther(Mono.delay(Duration.ofMillis(200))
                    .repeat()
                    .filter(tick -> chatContext.isAborted())
                    .next());
        }
        Flux<ChatResponse> cached = flux.cache();

        long connectWaitMs = Math.min(remainingMs, connectTimeoutSeconds * 1000L);
        try {
            cached.take(1)
                    .doOnNext(processChunk)
                    .blockFirst(Duration.ofMillis(Math.max(1, connectWaitMs)));
        } catch (Exception e) {
            if (!firstReceived.get()) {
                throw new RuntimeException(connectTimeoutMessage(connectTimeoutSeconds));
            }
            throw e instanceof RuntimeException re ? re : new RuntimeException(e);
        }
        if (!firstReceived.get()) {
            throw new RuntimeException(connectTimeoutMessage(connectTimeoutSeconds));
        }

        // 流式阶段：用 Flux.timeout 监督两个 chunk 之间最大间隔，超过则视为"响应停滞"
        // 不再累加 streamingPausedMs 也不做总时长判定——流式输出多久都不算超时，只在停滞时超时
        try {
            cached.skip(1)
                    .doOnNext(processChunk)
                    .doOnComplete(() -> completed.set(true))
                    .blockLast(Duration.ofSeconds(tokenIntervalSec));
        } catch (Exception e) {
            // 超时异常（TimeoutException 或包异常）单独识别，给出"响应停滞"文案
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (msg.contains("timeout") || msg.contains("Timeout") || e instanceof java.util.concurrent.TimeoutException) {
                throw new RuntimeException(stalledTimeoutMessage(tokenIntervalSec));
            }
            throw e instanceof RuntimeException re ? re : new RuntimeException(e);
        }

        if (chatContext != null && chatContext.isAborted()) {
            throw new RuntimeException("SubAgent execution cancelled by client");
        }
        if (!completed.get() && lastAssistant.isEmpty()) {
            throw new RuntimeException(readTimeoutMessage(readTimeoutSeconds));
        }
        return lastAssistant.isEmpty() ? null : lastAssistant.get(0);
    }

    /** 取 token 间隔超时阈值：SubAgent 可通过 readTimeoutSeconds 配置覆盖（最小 10s），否则用默认值 */
    private int resolveTokenIntervalTimeoutSeconds(SubAgent subAgent) {
        if (subAgent != null && subAgent.getReadTimeoutSeconds() != null) {
            return Math.max(10, Math.min(300, subAgent.getReadTimeoutSeconds()));
        }
        return DEFAULT_TOKEN_INTERVAL_TIMEOUT_SECONDS;
    }

    private int resolveConnectTimeoutSeconds(SubAgent subAgent) {
        if (subAgent != null && subAgent.getConnectTimeoutSeconds() != null) {
            return Math.max(1, Math.min(60, subAgent.getConnectTimeoutSeconds()));
        }
        return DEFAULT_CONNECT_TIMEOUT_SECONDS;
    }

    private int resolveReadTimeoutSeconds(SubAgent subAgent, ChatContext chatContext) {
        int configured = DEFAULT_READ_TIMEOUT_SECONDS;
        if (subAgent != null && subAgent.getReadTimeoutSeconds() != null) {
            configured = Math.max(10, Math.min(300, subAgent.getReadTimeoutSeconds()));
        }
        return configured;
    }

    private int resolveModelRetryTimes(SubAgent subAgent) {
        if (subAgent == null || subAgent.getModelRetryTimes() == null) {
            return DEFAULT_MODEL_RETRY_TIMES;
        }
        return Math.max(0, Math.min(10, subAgent.getModelRetryTimes()));
    }

    private String connectTimeoutMessage(int connectTimeoutSeconds) {
        return "SubAgent 连接超时（" + connectTimeoutSeconds + "秒），请检查网络或模型服务";
    }

    private String readTimeoutMessage(int readTimeoutSeconds) {
        return "SubAgent 响应超时（" + readTimeoutSeconds + "秒），请稍后重试";
    }

    /** 流式期间两个 chunk 间隔超时的提示文案：明确"停滞"语义而非"总时长" */
    private String stalledTimeoutMessage(int stalledSeconds) {
        return "SubAgent 响应停滞（" + stalledSeconds + "秒无新内容），请稍后重试";
    }

    private String resolveSubAgentDisplayName(SubAgent subAgent) {
        if (subAgent == null) {
            return "";
        }
        return subAgent.getDisplayName() != null && !subAgent.getDisplayName().isBlank()
                ? subAgent.getDisplayName() : subAgent.getName();
    }

    private String classifyFailureReason(Throwable e) {
        if (e == null) {
            return "execution_error";
        }
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        if (msg.contains("连接超时") || msg.contains("connect timed out") || msg.contains("connection timed out")
                || msg.contains("connect timeout") || msg.contains("连接失败")) {
            return "connect_timeout";
        }
        if (msg.contains("响应超时") || msg.contains("timeout") || msg.contains("timed out")) {
            return "read_timeout";
        }
        return "execution_error";
    }

    private String reasonToCode(String reason) {
        return switch (reason) {
            case "connect_timeout" -> "CONNECT_TIMEOUT";
            case "read_timeout" -> "READ_TIMEOUT";
            default -> "LLM_ERROR";
        };
    }

    private String reasonLabel(String reason) {
        return switch (reason) {
            case "connect_timeout" -> "连接超时";
            case "read_timeout" -> "响应超时";
            default -> "执行失败";
        };
    }

    private String buildRetryMessage(SubAgent subAgent, String reason, int attempt, int maxRetries) {
        return resolveSubAgentDisplayName(subAgent) + "：" + reasonLabel(reason)
                + "，正在重试 " + attempt + "/" + maxRetries;
    }

    /**
     * 从流式 getText() 提取增量（兼容累积全文与纯增量两种 provider 行为）
     */
    private String consumeStreamTextDelta(StringBuilder snapshot, String currentText) {
        if (currentText == null || currentText.isEmpty()) {
            return "";
        }
        String consumed = snapshot.toString();
        if (!consumed.isEmpty()
                && currentText.startsWith(consumed)
                && currentText.length() > consumed.length()) {
            String delta = currentText.substring(consumed.length());
            snapshot.setLength(0);
            snapshot.append(currentText);
            return delta;
        }
        if (currentText.contentEquals(consumed)) {
            return "";
        }
        if (!consumed.isEmpty()
                && currentText.length() <= consumed.length()
                && consumed.startsWith(currentText)) {
            return "";
        }
        if (!consumed.isEmpty()
                && currentText.length() <= consumed.length()
                && consumed.endsWith(currentText)) {
            return "";
        }
        snapshot.append(currentText);
        return currentText;
    }

    private void emitSubAgentError(ChatContext chatContext, SubAgent subAgent, String message, String code) {
        if (chatContext == null) return;
        int offset = chatContext.getSubAgentContentOffset() != null ? chatContext.getSubAgentContentOffset() : 0;
        Integer delegationIndex = chatContext.getSubAgentDelegationIndex();
        String displayName = subAgent.getDisplayName() != null ? subAgent.getDisplayName() : subAgent.getName();
        String json = toolEventGenerator.enrichSubagentJson(
                toolEventGenerator.subagentErrorEvent(subAgent.getName(), displayName, message, code, offset),
                delegationIndex, chatContext.getSubAgentBatchId(), chatContext.getSubAgentTaskId(),
                chatContext.getSubAgentTaskIndex());
        Map<String, Object> evt = new HashMap<>();
        evt.put("type", "subagent_error");
        evt.put("subagentName", subAgent.getName());
        evt.put("displayName", displayName);
        evt.put("message", message);
        evt.put("code", code);
        evt.put("contentOffset", offset);
        if (delegationIndex != null) evt.put("delegationIndex", delegationIndex);
        emitSubAgentStreamEvent(chatContext, evt, json);
    }

    private void emitSubAgentErrorRetry(ChatContext chatContext, SubAgent subAgent, String message,
                                        String code, int attempt, int maxRetries) {
        if (chatContext == null) return;
        int offset = chatContext.getSubAgentContentOffset() != null ? chatContext.getSubAgentContentOffset() : 0;
        Integer delegationIndex = chatContext.getSubAgentDelegationIndex();
        String displayName = subAgent.getDisplayName() != null ? subAgent.getDisplayName() : subAgent.getName();
        String json = toolEventGenerator.enrichSubagentJson(
                toolEventGenerator.subagentErrorRetryEvent(
                        subAgent.getName(), displayName, message, code, attempt, maxRetries, offset),
                delegationIndex, chatContext.getSubAgentBatchId(), chatContext.getSubAgentTaskId(),
                chatContext.getSubAgentTaskIndex());
        Map<String, Object> evt = new HashMap<>();
        evt.put("type", "subagent_error_retry");
        evt.put("subagentName", subAgent.getName());
        evt.put("displayName", displayName);
        evt.put("message", message);
        evt.put("code", code);
        evt.put("attempt", attempt);
        evt.put("maxRetries", maxRetries);
        evt.put("contentOffset", offset);
        if (delegationIndex != null) evt.put("delegationIndex", delegationIndex);
        emitSubAgentStreamEvent(chatContext, evt, json);
    }

    private String classifyErrorMessage(Throwable e) {
        if (e == null) return "SubAgent 执行失败：未知错误";
        String msg = e.getMessage();
        if (msg == null) return "SubAgent 执行失败：" + e.getClass().getSimpleName();
        String reason = classifyFailureReason(e);
        if ("connect_timeout".equals(reason)) {
            return msg.contains("SubAgent") ? msg : connectTimeoutMessage(resolveConnectTimeoutSeconds(null));
        }
        if ("read_timeout".equals(reason)) {
            return msg.contains("SubAgent") ? msg : readTimeoutMessage(resolveReadTimeoutSeconds(null, null));
        }
        if (msg.contains("429") || msg.contains("rate") || msg.contains("Rate")) {
            return "SubAgent 请求被限流，请稍后重试";
        }
        if (msg.contains("401") || msg.contains("403")) {
            return "SubAgent 模型认证失败，请检查 API Key 配置";
        }
        if (msg.contains("input length") || (msg.contains("InvalidParameter") && msg.contains("202745"))) {
            return "SubAgent 上下文过长，请缩小任务范围或减少工具返回数据";
        }
        return "SubAgent 执行失败：" + (msg.length() > 200 ? msg.substring(0, 200) + "..." : msg);
    }

    private String classifyErrorCode(Throwable e) {
        if (e == null) return "UNKNOWN";
        String code = reasonToCode(classifyFailureReason(e));
        if (!"LLM_ERROR".equals(code)) {
            return code;
        }
        String msg = e.getMessage();
        if (msg == null) return "UNKNOWN";
        if (msg.contains("429") || msg.contains("rate") || msg.contains("Rate")) return "RATE_LIMITED";
        if (msg.contains("401") || msg.contains("403")) return "AUTH_ERROR";
        if (msg.contains("token") && (msg.contains("limit") || msg.contains("exceed"))) return "TOKEN_LIMIT";
        return "LLM_ERROR";
    }

    private void pushTokenEvent(ChatContext chatContext, SubAgent subAgent, String delta) {
        if (chatContext == null || delta == null) {
            return;
        }
        int offset = chatContext.getSubAgentContentOffset() != null ? chatContext.getSubAgentContentOffset() : 0;
        Integer delegationIndex = chatContext.getSubAgentDelegationIndex();
        String json = toolEventGenerator.enrichSubagentJson(
                toolEventGenerator.subagentTokenEvent(subAgent.getName(), delta, offset), delegationIndex,
                chatContext.getSubAgentBatchId(), chatContext.getSubAgentTaskId(), chatContext.getSubAgentTaskIndex());
        Map<String, Object> evt = new HashMap<>();
        evt.put("type", "subagent_token");
        evt.put("subagentName", subAgent.getName());
        evt.put("displayName", resolveSubAgentDisplayName(subAgent));
        evt.put("content", delta);
        evt.put("contentOffset", offset);
        if (delegationIndex != null) evt.put("delegationIndex", delegationIndex);
        emitSubAgentStreamEvent(chatContext, evt, json);
    }

    private void emitSubAgentToolCall(ChatContext chatContext, SubAgent subAgent, AssistantMessage.ToolCall tc,
                                      Map<String, String> toolDisplayNameMap) {
        if (chatContext == null || tc == null) {
            return;
        }
        int offset = chatContext.getSubAgentContentOffset() != null ? chatContext.getSubAgentContentOffset() : 0;
        Integer delegationIndex = chatContext.getSubAgentDelegationIndex();
        String toolName = tc.name() != null ? tc.name() : "";
        String toolDisplayName = toolDisplayNameMap.getOrDefault(toolName, toolName);
        String args = tc.arguments() != null ? tc.arguments() : "{}";
        String subDisplayName = resolveSubAgentDisplayName(subAgent);
        String json = toolEventGenerator.enrichSubagentJson(
                toolEventGenerator.subagentToolCallEvent(
                        subAgent.getName(), subDisplayName, toolName, toolDisplayName, args, offset),
                delegationIndex, chatContext.getSubAgentBatchId(), chatContext.getSubAgentTaskId(),
                chatContext.getSubAgentTaskIndex());
        Map<String, Object> evt = new java.util.LinkedHashMap<>();
        evt.put("type", "subagent_tool_call");
        evt.put("subagentName", subAgent.getName());
        evt.put("displayName", subDisplayName);
        evt.put("toolName", toolName);
        evt.put("toolDisplayName", toolDisplayName);
        evt.put("args", args);
        evt.put("contentOffset", offset);
        if (delegationIndex != null) evt.put("delegationIndex", delegationIndex);
        emitSubAgentStreamEvent(chatContext, evt, json);
    }

    private void emitSubAgentToolResult(ChatContext chatContext, SubAgent subAgent, AssistantMessage.ToolCall tc,
                                        String result, Map<String, String> toolDisplayNameMap) {
        if (chatContext == null || tc == null) {
            return;
        }
        int offset = chatContext.getSubAgentContentOffset() != null ? chatContext.getSubAgentContentOffset() : 0;
        Integer delegationIndex = chatContext.getSubAgentDelegationIndex();
        String toolName = tc.name() != null ? tc.name() : "";
        String toolDisplayName = toolDisplayNameMap.getOrDefault(toolName, toolName);
        String subDisplayName = resolveSubAgentDisplayName(subAgent);
        String json = toolEventGenerator.enrichSubagentJson(
                toolEventGenerator.subagentToolResultEvent(
                        subAgent.getName(), subDisplayName, toolName, toolDisplayName, result, offset),
                delegationIndex, chatContext.getSubAgentBatchId(), chatContext.getSubAgentTaskId(),
                chatContext.getSubAgentTaskIndex());
        Map<String, Object> evt = new java.util.LinkedHashMap<>();
        evt.put("type", "subagent_tool_result");
        evt.put("subagentName", subAgent.getName());
        evt.put("displayName", subDisplayName);
        evt.put("toolName", toolName);
        evt.put("toolDisplayName", toolDisplayName);
        evt.put("result", toolEventGenerator.truncateForSse(result));
        evt.put("contentOffset", offset);
        if (delegationIndex != null) evt.put("delegationIndex", delegationIndex);
        emitSubAgentStreamEvent(chatContext, evt, json);
    }

    private void emitSubAgentStreamEvent(ChatContext chatContext, Map<String, Object> evt, String json) {
        evt.put("schema_version", 1);
        if (chatContext.getRequestId() != null && !chatContext.getRequestId().isBlank()) {
            evt.put("parent_request_id", chatContext.getRequestId());
        }
        // SSE 已有任务关联字段；metadata 也必须保留，历史消息才能按任务归属工具步骤。
        if (chatContext.getSubAgentBatchId() != null) {
            evt.put("batch_id", chatContext.getSubAgentBatchId());
        }
        if (chatContext.getSubAgentTaskId() != null) {
            evt.put("task_id", chatContext.getSubAgentTaskId());
        }
        if (chatContext.getSubAgentTaskIndex() != null) {
            evt.put("task_index", chatContext.getSubAgentTaskIndex());
        }
        taskEventService.record(chatContext.getSubAgentTaskId(), chatContext.getSubAgentBatchId(),
                String.valueOf(evt.get("type")), evt);
        if (chatContext.getRealtimeStatusEmitter() != null) {
            if (chatContext.getToolEventsList() != null) {
                synchronized (chatContext.getToolEventsList()) {
                    chatContext.getToolEventsList().add(new java.util.LinkedHashMap<>(evt));
                }
            }
            // 实时 SSE 与持久化事件使用同一契约，避免刷新前后丢失请求归属或版本字段。
            try {
                chatContext.emitRealtimeStatus(objectMapper.writeValueAsString(evt));
            } catch (Exception ignored) {
                chatContext.emitRealtimeStatus(json);
            }
        } else {
            Object payload = evt.get("result") != null ? evt.get("result")
                    : (evt.get("content") != null ? evt.get("content") : evt.get("args"));
            chatContext.pushSubAgentEvent(new ChatContext.SubAgentEvent(
                    evt.get("type").toString().replace("subagent_", ""),
                    (String) evt.get("subagentName"),
                    payload != null ? payload.toString() : "",
                    (Integer) evt.getOrDefault("contentOffset", 0)));
        }
    }

    private void markFailed(SubAgentRun run, String errorMessage, long start) {
        run.setStatus("failed");
        run.setErrorMessage(errorMessage);
        run.setEndTime(LocalDateTime.now());
        subAgentRunMapper.updateById(run);
        log.error("[SubAgent] 委派失败: name={}, 耗时={}ms, error={}",
                run.getSubagentName(), System.currentTimeMillis() - start, errorMessage);
    }

    private void markCancelled(SubAgentRun run, String message) {
        run.setStatus("cancelled");
        run.setErrorMessage(message);
        run.setCancelRequested(1);
        run.setEndTime(LocalDateTime.now());
        subAgentRunMapper.updateById(run);
        log.info("[SubAgent] 委派取消: name={}, requestId={}", run.getSubagentName(), run.getRequestId());
    }

    private boolean isCancelRequested(SubAgentRun run) {
        if (run == null || run.getRequestId() == null) {
            return false;
        }
        SubAgentRun latest = subAgentRunMapper.selectByRequestId(run.getRequestId());
        return latest != null && (Integer.valueOf(1).equals(latest.getCancelRequested())
                || "cancelled".equals(latest.getStatus()));
    }

    private boolean isTerminal(String status) {
        return "completed".equals(status) || "failed".equals(status) || "cancelled".equals(status);
    }

    /** 解析 SubAgent.toolIds JSON 数组 */
    private List<String> parseToolIds(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("[SubAgent] 解析 toolIds JSON 失败: {}", e.getMessage());
            return List.of();
        }
    }
}
