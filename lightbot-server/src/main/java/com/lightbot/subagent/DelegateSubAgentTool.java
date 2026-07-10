package com.lightbot.subagent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.subagent.service.SubAgentTaskService;
import com.lightbot.subagent.spi.SubAgentDefinition;
import com.lightbot.subagent.spi.SubAgentDefinitionResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SubAgent 对外工具门面；编排、查询和取消逻辑均委托给 {@link SubAgentTaskService}。
 *
 * @author finch
 * @since 2026-07-10
 */
@Component
@RequiredArgsConstructor
public class DelegateSubAgentTool {

    public static final String TOOL_NAME = "delegate_to_subagent";
    public static final String RESULT_TOOL_NAME = "get_subagent_task_result";
    public static final String CANCEL_TOOL_NAME = "cancel_subagent_task";

    private final ObjectMapper objectMapper;
    private final SubAgentDefinitionResolver definitionResolver;
    private final SubAgentTaskService subAgentTaskService;

    /** 为兼容旧调用返回第一个委派工具。 */
    public ToolCallback buildCallback(List<Long> boundSubAgentIds) {
        List<ToolCallback> callbacks = buildCallbacks(boundSubAgentIds);
        return callbacks.isEmpty() ? null : callbacks.get(0);
    }

    /** 构造当前 Agent 的统一委派门面及其后台控制工具。 */
    public List<ToolCallback> buildCallbacks(List<Long> boundSubAgentIds) {
        Map<String, SubAgentDefinition> definitions = definitionResolver.resolve(boundSubAgentIds);
        if (definitions.isEmpty()) return List.of();
        return List.of(
                new TaskCallback(delegateDefinition(definitions.values()), boundSubAgentIds, Operation.DELEGATE),
                new TaskCallback(resultDefinition(), boundSubAgentIds, Operation.QUERY),
                new TaskCallback(cancelDefinition(), boundSubAgentIds, Operation.CANCEL));
    }

    private ToolDefinition delegateDefinition(Iterable<SubAgentDefinition> definitions) {
        List<SubAgentDefinition> items = java.util.stream.StreamSupport.stream(definitions.spliterator(), false).toList();
        String names = items.stream().map(SubAgentDefinition::name).map(this::json).collect(Collectors.joining(", "));
        String catalog = items.stream().map(item -> "- " + item.name() + "（" + item.displayName() + "）")
                .collect(Collectors.joining("\\n"));
        return DefaultToolDefinition.builder().name(TOOL_NAME).description("""
                将自包含任务委派给一个或多个 SubAgent。多任务使用 tasks，parallel 并行等待，
                background 立即返回批次 ID；查询与取消会更新同一个委派批次，而非创建新工具结果。
                可用 SubAgent：
                """ + catalog).inputSchema("""
                {"type":"object","properties":{
                  "mode":{"type":"string","enum":["sync","parallel","background"]},
                  "subagent_name":{"type":"string","enum":[%s]},"task":{"type":"string"},"thread_id":{"type":"string"},
                  "tasks":{"type":"array","items":{"type":"object","properties":{"subagent_name":{"type":"string","enum":[%s]},"task":{"type":"string"},"thread_id":{"type":"string"}},"required":["subagent_name","task"]}},
                  "max_concurrency":{"type":"integer","minimum":1,"maximum":5},
                  "aggregation":{"type":"string","enum":["return_all","summarize"]}
                }}
                """.formatted(names, names)).build();
    }

    private ToolDefinition resultDefinition() {
        return DefaultToolDefinition.builder().name(RESULT_TOOL_NAME)
                .description("查询已创建的 SubAgent 后台任务或批次结果。")
                .inputSchema("{" + "\"type\":\"object\",\"properties\":{\"task_id\":{\"type\":\"string\"},\"task_ids\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},\"batch_id\":{\"type\":\"string\"}}}")
                .build();
    }

    private ToolDefinition cancelDefinition() {
        return DefaultToolDefinition.builder().name(CANCEL_TOOL_NAME)
                .description("请求取消当前会话创建的 SubAgent 任务或批次。")
                .inputSchema("{" + "\"type\":\"object\",\"properties\":{\"task_id\":{\"type\":\"string\"},\"batch_id\":{\"type\":\"string\"}}}")
                .build();
    }

    private String json(String value) {
        try { return objectMapper.writeValueAsString(value); } catch (Exception ignored) { return "\"\""; }
    }

    private enum Operation { DELEGATE, QUERY, CANCEL }

    @RequiredArgsConstructor
    private class TaskCallback implements ToolCallback {
        private final ToolDefinition definition;
        private final List<Long> boundSubAgentIds;
        private final Operation operation;

        @Override public ToolDefinition getToolDefinition() { return definition; }
        @Override public ToolMetadata getToolMetadata() { return ToolMetadata.builder().returnDirect(false).build(); }
        @Override public String call(String toolInput) { return call(toolInput, null); }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            return switch (operation) {
                case DELEGATE -> subAgentTaskService.delegate(toolInput, toolContext, boundSubAgentIds);
                case QUERY -> subAgentTaskService.query(toolInput, toolContext);
                case CANCEL -> subAgentTaskService.cancel(toolInput, toolContext);
            };
        }
    }
}
