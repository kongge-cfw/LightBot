package com.lightbot.workflow.dify;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.lightbot.dto.DifyWorkflowImportPreviewVO;
import com.lightbot.dto.DifyWorkflowIssueVO;
import com.lightbot.dto.WorkflowGraphDTO;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 将受限 Dify Workflow YAML 转换为 LightBot 工作流草稿。 */
@Component
public class DifyWorkflowImporter {

    private static final int MAX_YAML_SIZE = 2 * 1024 * 1024;
    private static final int MAX_NODE_COUNT = 100;
    private static final int MAX_DEPTH = 64;

    private final ObjectMapper yamlMapper = new ObjectMapper(YAMLFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(MAX_DEPTH).build())
            .build());

    /**
     * 预检并转换 Dify YAML，不执行持久化。
     *
     * @param yamlContent Dify DSL 内容
     * @return 预检结果
     */
    public DifyWorkflowImportPreviewVO preview(String yamlContent) {
        List<DifyWorkflowIssueVO> issues = new ArrayList<>();
        DifyWorkflowImportPreviewVO preview = new DifyWorkflowImportPreviewVO();
        preview.setSourceDigest(sha256(yamlContent));
        preview.setIssues(issues);

        if (yamlContent == null || yamlContent.isBlank()) {
            addIssue(issues, "BLOCKER", "EMPTY_DSL", null, "Dify YAML 不能为空");
            return preview;
        }
        if (yamlContent.getBytes(StandardCharsets.UTF_8).length > MAX_YAML_SIZE) {
            addIssue(issues, "BLOCKER", "FILE_TOO_LARGE", null, "Dify YAML 不能超过 2 MiB");
            return preview;
        }

        Map<String, Object> root = readRoot(yamlContent, issues);
        if (root == null) {
            return preview;
        }
        Map<String, Object> app = mapValue(root.get("app"));
        if (!"workflow".equals(stringValue(app.get("mode")))) {
            addIssue(issues, "BLOCKER", "UNSUPPORTED_APP_MODE", null, "仅支持 Dify Workflow 类型应用");
            return preview;
        }
        preview.setAppName(stringValue(app.get("name")));

        Map<String, Object> workflow = mapValue(root.get("workflow"));
        Map<String, Object> graph = mapValue(workflow.get("graph"));
        List<Map<String, Object>> difyNodes = mapList(graph.get("nodes"));
        List<Map<String, Object>> difyEdges = mapList(graph.get("edges"));
        preview.setNodeCount(difyNodes.size());
        preview.setEdgeCount(difyEdges.size());
        if (difyNodes.isEmpty()) {
            addIssue(issues, "BLOCKER", "EMPTY_GRAPH", null, "Dify Workflow 未包含节点");
            return preview;
        }
        if (difyNodes.size() > MAX_NODE_COUNT) {
            addIssue(issues, "BLOCKER", "TOO_MANY_NODES", null, "Dify Workflow 节点数不能超过 100");
            return preview;
        }

        List<Map<String, Object>> nodes = new ArrayList<>();
        Set<String> nodeIds = new HashSet<>();
        for (Map<String, Object> difyNode : difyNodes) {
            Map<String, Object> node = mapNode(difyNode, issues);
            if (node == null) {
                continue;
            }
            String nodeId = stringValue(node.get("id"));
            if (!nodeIds.add(nodeId)) {
                addIssue(issues, "BLOCKER", "DUPLICATE_NODE_ID", nodeId, "存在重复节点 ID");
                continue;
            }
            nodes.add(node);
        }

        List<Map<String, Object>> edges = mapEdges(difyEdges, nodeIds, issues);
        validateGraph(nodes, edges, issues);
        WorkflowGraphDTO workflowGraph = new WorkflowGraphDTO();
        workflowGraph.setNodes(nodes);
        workflowGraph.setEdges(edges);
        workflowGraph.setGlobalConfig(mapGlobalConfig(workflow));
        preview.setGraph(workflowGraph);
        return preview;
    }

    private Map<String, Object> readRoot(String yamlContent, List<DifyWorkflowIssueVO> issues) {
        try {
            Object parsed = yamlMapper.readValue(yamlContent, Object.class);
            if (!(parsed instanceof Map<?, ?> map)) {
                addIssue(issues, "BLOCKER", "INVALID_DSL", null, "Dify YAML 根节点必须是对象");
                return null;
            }
            return castMap(map);
        } catch (JsonProcessingException e) {
            addIssue(issues, "BLOCKER", "INVALID_YAML", null, "Dify YAML 格式错误或包含不支持的标签");
            return null;
        }
    }

    private Map<String, Object> mapNode(Map<String, Object> difyNode, List<DifyWorkflowIssueVO> issues) {
        String id = stringValue(difyNode.get("id"));
        Map<String, Object> sourceData = mapValue(difyNode.get("data"));
        String difyType = stringValue(sourceData.get("type"));
        if (id.isBlank() || difyType.isBlank()) {
            addIssue(issues, "BLOCKER", "INVALID_NODE", id, "节点缺少 id 或 data.type");
            return null;
        }
        if ("note".equals(difyType)) {
            addIssue(issues, "WARNING", "IGNORED_NOTE", id, "画布注释不会导入到执行工作流");
            return null;
        }
        String lightBotType = switch (difyType) {
            case "start", "end", "llm" -> difyType;
            case "if-else" -> "condition";
            case "knowledge-retrieval" -> "retrieval";
            case "question-classifier" -> "classifier";
            case "code", "document-extractor" -> "script";
            case "http-request" -> "api";
            case "tool" -> "tool";
            case "iteration" -> "loop";
            case "assigner" -> "variable";
            case "template-transform", "variable-aggregator", "list-operator" -> "variable_handle";
            case "parameter-extractor" -> "parameter_extractor";
            case "answer" -> "output";
            case "agent" -> "app_component";
            case "mcp" -> "mcp";
            default -> "script";
        };

        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", id);
        node.put("type", lightBotType);
        node.put("position", normalizePosition(mapValue(difyNode.get("position"))));
        node.put("data", mapNodeData(lightBotType, difyType, sourceData, issues, id));
        if (!isKnownDifyType(difyType)) {
            addIssue(issues, "REPAIR_REQUIRED", "PASSTHROUGH_NODE", id,
                    "LightBot 暂无等价运行时节点，已作为脚本占位导入；再次导出会保留原始 Dify 节点配置");
        }
        return node;
    }

    private Map<String, Object> mapNodeData(String type, String difyType, Map<String, Object> sourceData,
                                             List<DifyWorkflowIssueVO> issues, String nodeId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("label", defaultValue(stringValue(sourceData.get("title")), nodeTitle(type)));
        data.put("difySourceType", difyType);
        data.put("difySourceData", sanitizeDifyData(sourceData, issues, nodeId));
        switch (type) {
            case "llm" -> mapLlmData(sourceData, data, issues, nodeId);
            case "condition" -> mapConditionData(sourceData, data);
            case "retrieval" -> {
                data.put("knowledgeId", null);
                data.put("knowledgeName", "");
                data.put("inputVariable", selectorToVariable(sourceData.get("query_variable_selector")));
                data.put("topK", numberValue(sourceData.get("retrieval_mode"), 5));
                data.put("threshold", 0.5D);
                addIssue(issues, "REPAIR_REQUIRED", "KNOWLEDGE_BINDING_REQUIRED", nodeId,
                        "请在 LightBot 中重新选择知识库");
            }
            case "classifier" -> mapClassifierData(sourceData, data, issues, nodeId);
            case "script" -> mapScriptData(sourceData, data);
            case "api" -> mapApiData(sourceData, data, issues, nodeId);
            case "tool" -> mapToolData(sourceData, data, issues, nodeId);
            case "loop" -> mapLoopData(sourceData, data, issues, nodeId);
            case "variable" -> mapVariableData(sourceData, data);
            case "variable_handle" -> mapVariableHandleData(sourceData, data);
            case "parameter_extractor" -> mapParameterExtractorData(sourceData, data, issues, nodeId);
            case "output" -> data.put("output", defaultValue(stringValue(sourceData.get("answer")), "{{input}}"));
            case "app_component" -> mapAppComponentData(sourceData, data, issues, nodeId);
            case "mcp" -> mapMcpData(sourceData, data, issues, nodeId);
            case "end" -> data.put("textTemplate", "{{output}}");
            default -> { }
        }
        return data;
    }

    private void mapScriptData(Map<String, Object> sourceData, Map<String, Object> data) {
        data.put("scriptLanguage", defaultValue(stringValue(sourceData.get("code_language")), "javascript"));
        data.put("scriptContent", stringValue(sourceData.get("code")));
        data.put("inputParams", mapInputParameters(sourceData.get("variables")));
        data.put("outputParams", mapOutputParameters(sourceData.get("outputs")));
    }

    private void mapApiData(Map<String, Object> sourceData, Map<String, Object> data,
                            List<DifyWorkflowIssueVO> issues, String nodeId) {
        data.put("url", stringValue(sourceData.get("url")));
        data.put("method", defaultValue(stringValue(sourceData.get("method")), "GET").toUpperCase());
        data.put("headers", "{}");
        data.put("body", "{}");
        addIssue(issues, "REPAIR_REQUIRED", "HTTP_AUTH_REBIND_REQUIRED", nodeId,
                "HTTP 节点已导入；认证信息不会迁移，请在 LightBot 中重新配置请求头或认证");
    }

    private void mapToolData(Map<String, Object> sourceData, Map<String, Object> data,
                             List<DifyWorkflowIssueVO> issues, String nodeId) {
        data.put("toolId", null);
        data.put("toolName", defaultValue(stringValue(sourceData.get("tool_name")), stringValue(sourceData.get("title"))));
        data.put("inputMappings", mapInputParameters(sourceData.get("tool_parameters")));
        data.put("outputMappings", List.of(Map.of("key", "toolResult", "value", "{{output}}")));
        addIssue(issues, "REPAIR_REQUIRED", "TOOL_BINDING_REQUIRED", nodeId,
                "工具节点已导入；请在 LightBot 中重新选择本地工具或 MCP 工具");
    }

    private void mapLoopData(Map<String, Object> sourceData, Map<String, Object> data,
                             List<DifyWorkflowIssueVO> issues, String nodeId) {
        String iterator = selectorToVariable(sourceData.get("iterator_selector"));
        data.put("iterator_type", "byArray");
        data.put("arrayVariable", iterator);
        data.put("input_params", List.of(Map.of("key", "item", "type", "Object", "value_from", "refer", "value", iterator)));
        data.put("output_params", List.of(Map.of("key", "result", "type", "Object")));
        data.put("count_limit", numberValue(sourceData.get("max_count"), 100));
        addIssue(issues, "REPAIR_REQUIRED", "ITERATION_LAYOUT_REVIEW_REQUIRED", nodeId,
                "迭代节点已导入；请检查 LightBot 画布中的循环体和变量引用");
    }

    private void mapVariableData(Map<String, Object> sourceData, Map<String, Object> data) {
        List<Map<String, Object>> items = mapList(sourceData.get("items"));
        if (!items.isEmpty()) {
            Map<String, Object> item = items.get(0);
            data.put("variableName", selectorToName(item.get("variable_selector")));
            data.put("variableValue", defaultValue(stringValue(item.get("input")), selectorToVariable(item.get("input_variable_selector"))));
            return;
        }
        data.put("variableName", stringValue(sourceData.get("variable_name")));
        data.put("variableValue", stringValue(sourceData.get("value")));
    }

    private void mapVariableHandleData(Map<String, Object> sourceData, Map<String, Object> data) {
        data.put("handleType", "template-transform".equals(stringValue(sourceData.get("type"))) ? "template" : "group");
        data.put("templateContent", defaultValue(stringValue(sourceData.get("template")), "{{input}}"));
        data.put("groupStrategy", "firstNotNull");
        data.put("groups", List.of(Map.of("variables", List.of())));
    }

    private void mapParameterExtractorData(Map<String, Object> sourceData, Map<String, Object> data,
                                           List<DifyWorkflowIssueVO> issues, String nodeId) {
        Map<String, Object> model = mapValue(sourceData.get("model"));
        data.put("providerId", null);
        data.put("providerName", stringValue(model.get("provider")));
        data.put("modelId", null);
        data.put("modelName", stringValue(model.get("name")));
        data.put("inputVariable", selectorToVariable(sourceData.get("query")));
        data.put("instruction", stringValue(sourceData.get("instruction")));
        data.put("extractParams", mapOutputParameters(sourceData.get("parameters")));
        addIssue(issues, "REPAIR_REQUIRED", "MODEL_BINDING_REQUIRED", nodeId,
                "参数提取节点已导入；请在 LightBot 中重新选择模型");
    }

    private void mapAppComponentData(Map<String, Object> sourceData, Map<String, Object> data,
                                     List<DifyWorkflowIssueVO> issues, String nodeId) {
        data.put("componentCode", stringValue(sourceData.get("app_id")));
        data.put("componentName", defaultValue(stringValue(sourceData.get("title")), "Dify Agent"));
        data.put("componentType", "workflow");
        data.put("inputMappings", List.of(Map.of("key", "query", "value", "{{query}}")));
        data.put("outputMappings", List.of(Map.of("key", "result", "value", "{{result}}")));
        addIssue(issues, "REPAIR_REQUIRED", "APP_COMPONENT_BINDING_REQUIRED", nodeId,
                "Agent 节点已导入；请在 LightBot 中重新绑定子工作流或 Agent");
    }

    private void mapMcpData(Map<String, Object> sourceData, Map<String, Object> data,
                            List<DifyWorkflowIssueVO> issues, String nodeId) {
        data.put("mcpServerId", null);
        data.put("mcpServerName", stringValue(sourceData.get("provider_id")));
        data.put("toolName", stringValue(sourceData.get("tool_name")));
        data.put("inputParams", "{}");
        addIssue(issues, "REPAIR_REQUIRED", "MCP_BINDING_REQUIRED", nodeId,
                "MCP 节点已导入；请在 LightBot 中重新选择 MCP Server 和工具");
    }

    private void mapLlmData(Map<String, Object> sourceData, Map<String, Object> data,
                            List<DifyWorkflowIssueVO> issues, String nodeId) {
        Map<String, Object> model = mapValue(sourceData.get("model"));
        data.put("providerId", null);
        data.put("modelId", null);
        data.put("providerName", stringValue(model.get("provider")));
        data.put("modelName", stringValue(model.get("name")));
        data.put("temperature", numberValue(mapValue(model.get("completion_params")).get("temperature"), 0.7D));
        data.put("enableStreaming", true);
        data.put("sysPrompt", promptText(sourceData, "system"));
        data.put("promptTemplate", defaultValue(promptText(sourceData, "user"), "{{query}}"));
        addIssue(issues, "REPAIR_REQUIRED", "MODEL_BINDING_REQUIRED", nodeId,
                "请在 LightBot 中重新选择模型提供商和模型");
    }

    private void mapConditionData(Map<String, Object> sourceData, Map<String, Object> data) {
        List<Map<String, Object>> groups = new ArrayList<>();
        int index = 0;
        for (Map<String, Object> sourceCase : mapList(sourceData.get("cases"))) {
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("label", index == 0 ? "如果" : "否则如果");
            group.put("relation", defaultValue(stringValue(sourceCase.get("logical_operator")), "and"));
            group.put("sourceHandle", "out_" + (char) ('a' + index));
            List<Map<String, Object>> rules = new ArrayList<>();
            for (Map<String, Object> condition : mapList(sourceCase.get("conditions"))) {
                Map<String, Object> rule = new LinkedHashMap<>();
                rule.put("variable", selectorToVariable(condition.get("variable_selector")));
                rule.put("operator", defaultValue(stringValue(condition.get("comparison_operator")), "contains"));
                rule.put("value", stringValue(condition.get("value")));
                rules.add(rule);
            }
            group.put("rules", rules);
            groups.add(group);
            index++;
        }
        data.put("conditionGroups", groups);
        data.put("branches", List.of());
    }

    private void mapClassifierData(Map<String, Object> sourceData, Map<String, Object> data,
                                   List<DifyWorkflowIssueVO> issues, String nodeId) {
        List<Map<String, Object>> conditions = new ArrayList<>();
        for (Map<String, Object> clazz : mapList(sourceData.get("classes"))) {
            Map<String, Object> condition = new LinkedHashMap<>();
            condition.put("id", stringValue(clazz.get("id")));
            condition.put("subject", defaultValue(stringValue(clazz.get("name")), stringValue(clazz.get("id"))));
            conditions.add(condition);
        }
        data.put("conditions", conditions);
        data.put("inputVariable", selectorToVariable(sourceData.get("query_variable_selector")));
        data.put("providerId", null);
        data.put("modelId", null);
        data.put("mode_switch", "efficient");
        addIssue(issues, "REPAIR_REQUIRED", "MODEL_BINDING_REQUIRED", nodeId,
                "请在 LightBot 中重新选择分类模型");
    }

    private List<Map<String, Object>> mapEdges(List<Map<String, Object>> sourceEdges, Set<String> nodeIds,
                                                List<DifyWorkflowIssueVO> issues) {
        List<Map<String, Object>> edges = new ArrayList<>();
        int index = 0;
        for (Map<String, Object> source : sourceEdges) {
            String sourceId = stringValue(source.get("source"));
            String targetId = stringValue(source.get("target"));
            if (!nodeIds.contains(sourceId) || !nodeIds.contains(targetId)) {
                addIssue(issues, "BLOCKER", "INVALID_EDGE", null, "连线引用了未导入的节点");
                continue;
            }
            Map<String, Object> edge = new LinkedHashMap<>();
            edge.put("id", defaultValue(stringValue(source.get("id")), "dify-edge-" + index));
            edge.put("source", sourceId);
            edge.put("target", targetId);
            Map<String, Object> data = mapValue(source.get("data"));
            edge.put("sourceHandle", defaultValue(stringValue(source.get("sourceHandle")),
                    defaultValue(stringValue(data.get("sourceHandle")), "out")));
            edge.put("targetHandle", defaultValue(stringValue(source.get("targetHandle")), "in"));
            edges.add(edge);
            index++;
        }
        return edges;
    }

    private Map<String, Object> mapGlobalConfig(Map<String, Object> workflow) {
        List<Map<String, Object>> parameters = new ArrayList<>();
        for (Map<String, Object> variable : mapList(workflow.get("conversation_variables"))) {
            Map<String, Object> parameter = new LinkedHashMap<>();
            parameter.put("key", selectorName(variable));
            parameter.put("default_value", stringValue(variable.get("value")));
            parameters.add(parameter);
        }
        Map<String, Object> variableConfig = new LinkedHashMap<>();
        variableConfig.put("conversation_params", parameters);
        Map<String, Object> historyConfig = new LinkedHashMap<>();
        historyConfig.put("history_switch", true);
        historyConfig.put("history_max_round", 5);
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("variable_config", variableConfig);
        config.put("history_config", historyConfig);
        return config;
    }

    private void validateGraph(List<Map<String, Object>> nodes, List<Map<String, Object>> edges,
                               List<DifyWorkflowIssueVO> issues) {
        long startCount = nodes.stream().filter(node -> "start".equals(node.get("type"))).count();
        long endCount = nodes.stream().filter(node -> "end".equals(node.get("type"))).count();
        if (startCount != 1) {
            addIssue(issues, "BLOCKER", "INVALID_START_NODE", null, "工作流必须且只能有一个开始节点");
        }
        if (endCount == 0) {
            addIssue(issues, "BLOCKER", "MISSING_END_NODE", null, "工作流至少需要一个结束节点");
        }
        if (hasCycle(nodes, edges)) {
            addIssue(issues, "BLOCKER", "CYCLE_DETECTED", null, "Dify Workflow 不能包含环路");
        }
    }

    private boolean hasCycle(List<Map<String, Object>> nodes, List<Map<String, Object>> edges) {
        Map<String, List<String>> adjacency = new HashMap<>();
        for (Map<String, Object> node : nodes) {
            adjacency.put(stringValue(node.get("id")), new ArrayList<>());
        }
        for (Map<String, Object> edge : edges) {
            adjacency.get(stringValue(edge.get("source"))).add(stringValue(edge.get("target")));
        }
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (String nodeId : adjacency.keySet()) {
            if (hasCycle(nodeId, adjacency, visiting, visited)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasCycle(String nodeId, Map<String, List<String>> adjacency, Set<String> visiting, Set<String> visited) {
        if (visiting.contains(nodeId)) {
            return true;
        }
        if (!visited.add(nodeId)) {
            return false;
        }
        visiting.add(nodeId);
        for (String next : adjacency.getOrDefault(nodeId, List.of())) {
            if (hasCycle(next, adjacency, visiting, visited)) {
                return true;
            }
        }
        visiting.remove(nodeId);
        return false;
    }

    private String promptText(Map<String, Object> sourceData, String role) {
        for (Map<String, Object> prompt : mapList(sourceData.get("prompt_template"))) {
            if (role.equalsIgnoreCase(stringValue(prompt.get("role")))) {
                return stringValue(prompt.get("text"));
            }
        }
        return "";
    }

    private String selectorToVariable(Object selector) {
        if (selector instanceof List<?> list && !list.isEmpty()) {
            String last = stringValue(list.get(list.size() - 1));
            return "{{" + defaultValue(last, "query") + "}}";
        }
        return "{{query}}";
    }

    private String selectorToName(Object selector) {
        if (selector instanceof List<?> list && !list.isEmpty()) {
            return stringValue(list.get(list.size() - 1));
        }
        return "";
    }

    private List<Map<String, Object>> mapInputParameters(Object source) {
        List<Map<String, Object>> parameters = new ArrayList<>();
        if (source instanceof Map<?, ?> map) {
            castMap(map).forEach((key, value) -> parameters.add(Map.of("key", key,
                    "value", value instanceof List<?> ? selectorToVariable(value) : stringValue(value))));
            return parameters;
        }
        for (Map<String, Object> parameter : mapList(source)) {
            String key = defaultValue(stringValue(parameter.get("variable")), stringValue(parameter.get("name")));
            if (key.isBlank()) {
                continue;
            }
            Object value = parameter.containsKey("value") ? parameter.get("value")
                    : parameter.get("value_selector");
            parameters.add(Map.of("key", key, "value", value instanceof List<?> ? selectorToVariable(value) : stringValue(value)));
        }
        return parameters;
    }

    private List<Map<String, Object>> mapOutputParameters(Object source) {
        List<Map<String, Object>> parameters = new ArrayList<>();
        if (source instanceof Map<?, ?> map) {
            castMap(map).forEach((key, value) -> parameters.add(Map.of("key", key,
                    "type", defaultValue(stringValue(value), "String"))));
            return parameters;
        }
        for (Map<String, Object> parameter : mapList(source)) {
            String key = defaultValue(stringValue(parameter.get("name")), stringValue(parameter.get("key")));
            if (key.isBlank()) {
                continue;
            }
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("key", key);
            output.put("type", defaultValue(stringValue(parameter.get("type")), "String"));
            output.put("required", Boolean.TRUE.equals(parameter.get("required")));
            output.put("desc", defaultValue(stringValue(parameter.get("description")), stringValue(parameter.get("desc"))));
            parameters.add(output);
        }
        return parameters;
    }

    private Map<String, Object> sanitizeDifyData(Map<String, Object> sourceData,
                                                  List<DifyWorkflowIssueVO> issues, String nodeId) {
        boolean[] removed = {false};
        @SuppressWarnings("unchecked")
        Map<String, Object> sanitized = (Map<String, Object>) sanitizeDifyValue(sourceData, "", removed);
        if (removed[0]) {
            addIssue(issues, "REPAIR_REQUIRED", "SENSITIVE_CONFIG_REMOVED", nodeId,
                    "导入时已移除 Dify 节点中的凭证或密钥，导出后请在目标 Dify 环境重新配置");
        }
        return sanitized;
    }

    private Object sanitizeDifyValue(Object value, String key, boolean[] removed) {
        if (isSensitiveKey(key)) {
            removed[0] = true;
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            castMap(map).forEach((childKey, childValue) -> {
                if (isSensitiveKey(childKey)) {
                    removed[0] = true;
                    return;
                }
                result.put(childKey, sanitizeDifyValue(childValue, childKey, removed));
            });
            return result;
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>();
            for (Object item : list) {
                result.add(sanitizeDifyValue(item, key, removed));
            }
            return result;
        }
        return value;
    }

    private boolean isSensitiveKey(String key) {
        String normalized = key == null ? "" : key.toLowerCase().replaceAll("[^a-z]", "");
        return normalized.contains("apikey") || normalized.contains("secret") || normalized.contains("token")
                || normalized.contains("password") || normalized.contains("credential")
                || normalized.contains("authorization");
    }

    private boolean isKnownDifyType(String type) {
        return Set.of("start", "end", "llm", "if-else", "knowledge-retrieval", "question-classifier",
                "code", "document-extractor", "http-request", "tool", "iteration", "assigner",
                "template-transform", "variable-aggregator", "list-operator", "parameter-extractor",
                "answer", "agent", "mcp").contains(type);
    }

    private String selectorName(Map<String, Object> variable) {
        Object selector = variable.get("selector");
        if (selector instanceof List<?> list && !list.isEmpty()) {
            return stringValue(list.get(list.size() - 1));
        }
        return stringValue(variable.get("name"));
    }

    private Map<String, Object> normalizePosition(Map<String, Object> position) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("x", numberValue(position.get("x"), 0));
        result.put("y", numberValue(position.get("y"), 0));
        return result;
    }

    private String nodeTitle(String type) {
        return switch (type) {
            case "start" -> "开始";
            case "end" -> "结束";
            case "llm" -> "大模型";
            case "condition" -> "条件判断";
            case "retrieval" -> "知识检索";
            case "classifier" -> "意图分类";
            default -> type;
        };
    }

    private List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                result.add(castMap(map));
            }
        }
        return result;
    }

    private Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> map ? castMap(map) : Map.of();
    }

    private Map<String, Object> castMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private Integer numberValue(Object value, int defaultValue) {
        return value instanceof Number number ? number.intValue() : defaultValue;
    }

    private Double numberValue(Object value, double defaultValue) {
        return value instanceof Number number ? number.doubleValue() : defaultValue;
    }

    private String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private void addIssue(List<DifyWorkflowIssueVO> issues, String severity, String code,
                          String nodeId, String message) {
        DifyWorkflowIssueVO issue = new DifyWorkflowIssueVO();
        issue.setSeverity(severity);
        issue.setCode(code);
        issue.setNodeId(nodeId);
        issue.setMessage(message);
        issues.add(issue);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder("sha256:");
            for (byte item : digest) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }
}
