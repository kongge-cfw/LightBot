package com.lightbot.workflow.processor;

import com.lightbot.enums.NodeType;
import com.lightbot.workflow.NodeExecutionContext;
import com.lightbot.workflow.NodeExecutionResult;
import com.lightbot.workflow.NodeProcessor;
import com.lightbot.workflow.SubWorkflowExecutionResult;
import com.lightbot.workflow.WorkflowExecutorService;
import com.lightbot.workflow.WorkflowMappingUtils;
import com.lightbot.workflow.WorkflowNodeDataUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 应用组件节点：嵌套执行已发布子工作流
 */
@Slf4j
@Component
public class AppComponentNodeProcessor extends AbstractFlowNodeProcessor implements NodeProcessor {

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
        Map<String, Object> inputs = WorkflowMappingUtils.buildInputArgs(nodeData, parentVars);
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
        Map<String, Object> outputs = WorkflowMappingUtils.applyOutputMappings(
                nodeData, subVars, "result", defaultOutput);
        if (!outputs.containsKey("output")) {
            Object result = outputs.get("result");
            if (result == null) {
                result = subVars.get("result");
            }
            if (result == null) {
                result = defaultOutput;
            }
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
}
