package com.lightbot.workflow.processor;

import com.lightbot.enums.NodeType;
import com.lightbot.workflow.NodeExecutionContext;
import com.lightbot.workflow.NodeExecutionResult;
import com.lightbot.workflow.NodeProcessor;
import com.lightbot.workflow.SubWorkflowExecutionResult;
import com.lightbot.workflow.WorkflowExecutorService;
import com.lightbot.workflow.WorkflowNodeDataUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 应用组件节点：嵌套执行已发布子工作流
 */
@Slf4j
@Component
public class AppComponentNodeProcessor extends AbstractFlowNodeProcessor implements NodeProcessor {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{([^}]+)}}");

    private final WorkflowExecutorService workflowExecutorService;

    public AppComponentNodeProcessor(@Lazy WorkflowExecutorService workflowExecutorService) {
        this.workflowExecutorService = workflowExecutorService;
    }

    @Override
    public NodeType getType() {
        return NodeType.APP_COMPONENT;
    }

    @Override
    @SuppressWarnings("unchecked")
    public NodeExecutionResult execute(NodeExecutionContext context) {
        Map<String, Object> nodeData = context.getCurrentNodeData() != null
                ? context.getCurrentNodeData() : Map.of();

        String componentType = WorkflowNodeDataUtils.parseString(nodeData.get("componentType"));
        if (componentType == null) {
            componentType = "workflow";
        }
        if (!"workflow".equalsIgnoreCase(componentType)) {
            throw new IllegalArgumentException("暂不支持智能体组件嵌套，请选择工作流组件");
        }

        Long targetAgentId = WorkflowNodeDataUtils.parseLongId(nodeData.get("componentCode"));
        if (targetAgentId == null) {
            throw new IllegalArgumentException("请选择已发布的子工作流 Agent");
        }

        Map<String, Object> parentVars = context.getVariables() != null
                ? context.getVariables() : Map.of();
        Map<String, Object> subInputs = buildSubInputs(nodeData, parentVars);
        String userInput = resolveSubUserInput(subInputs, parentVars);

        log.info("[AppComponentNodeProcessor] 调用子工作流: targetAgentId={}, nodeId={}",
                targetAgentId, context.getCurrentNodeId());

        SubWorkflowExecutionResult subResult = workflowExecutorService.executeSubWorkflow(
                context, targetAgentId, subInputs, userInput);

        Map<String, Object> subVars = subResult.getVariables() != null
                ? subResult.getVariables() : Map.of();
        Map<String, Object> outputs = applyOutputMappings(nodeData, subVars, subResult.getOutput());

        String streamContent = Boolean.TRUE.equals(nodeData.get("streamSwitch"))
                ? subResult.getOutput() : null;

        return NodeExecutionResult.builder()
                .nextNodeId(resolveNextNodeId(context))
                .outputs(outputs)
                .streamContent(streamContent)
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildSubInputs(Map<String, Object> nodeData, Map<String, Object> parentVars) {
        Map<String, Object> inputs = new LinkedHashMap<>();
        List<Map<String, Object>> mappings = readMappingList(nodeData.get("inputMappings"));
        if (mappings == null || mappings.isEmpty()) {
            mappings = readMappingList(nodeData.get("input_mappings"));
        }
        if (mappings != null) {
            for (Map<String, Object> row : mappings) {
                String key = WorkflowNodeDataUtils.parseString(row.get("key"));
                if (key == null) {
                    continue;
                }
                Object rawValue = row.get("value");
                inputs.put(key, resolveTemplateValue(rawValue, parentVars));
            }
        }
        if (!inputs.containsKey("query")) {
            Object query = parentVars.get("query");
            if (query == null) {
                query = parentVars.get("input");
            }
            if (query != null) {
                inputs.put("query", query);
            }
        }
        if (!inputs.containsKey("input")) {
            inputs.put("input", inputs.getOrDefault("query", parentVars.get("input")));
        }
        return inputs;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> applyOutputMappings(Map<String, Object> nodeData,
                                                    Map<String, Object> subVars,
                                                    String defaultOutput) {
        Map<String, Object> outputs = new LinkedHashMap<>();
        List<Map<String, Object>> mappings = readMappingList(nodeData.get("outputMappings"));
        if (mappings == null || mappings.isEmpty()) {
            mappings = readMappingList(nodeData.get("output_mappings"));
        }
        if (mappings != null && !mappings.isEmpty()) {
            for (Map<String, Object> row : mappings) {
                String key = WorkflowNodeDataUtils.parseString(row.get("key"));
                if (key == null) {
                    continue;
                }
                Object rawValue = row.get("value");
                if (rawValue == null || String.valueOf(rawValue).isBlank()) {
                    rawValue = "{{result}}";
                }
                outputs.put(key, resolveTemplateValue(rawValue, subVars));
            }
        } else {
            Object result = subVars.get("result");
            if (result == null) {
                result = defaultOutput;
            }
            outputs.put("result", result);
            outputs.put("output", result);
        }
        return outputs;
    }

    private String resolveSubUserInput(Map<String, Object> subInputs, Map<String, Object> parentVars) {
        Object query = subInputs.get("query");
        if (query == null) {
            query = subInputs.get("input");
        }
        if (query == null) {
            query = parentVars.get("query");
        }
        if (query == null) {
            query = parentVars.get("input");
        }
        return query != null ? String.valueOf(query) : "";
    }

    private Object resolveTemplateValue(Object rawValue, Map<String, Object> variables) {
        if (!(rawValue instanceof String text)) {
            return rawValue;
        }
        Matcher matcher = VAR_PATTERN.matcher(text.trim());
        if (matcher.matches()) {
            String varName = matcher.group(1).trim();
            return variables.get(varName);
        }
        StringBuffer sb = new StringBuffer();
        matcher.reset();
        while (matcher.find()) {
            String varName = matcher.group(1).trim();
            Object varValue = variables.get(varName);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(varValue == null ? "" : String.valueOf(varValue)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readMappingList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return null;
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                result.add(new LinkedHashMap<>((Map<String, Object>) map));
            }
        }
        return result;
    }
}
