package com.lightbot.workflow.dify;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.lightbot.dto.DifyWorkflowExportPreviewVO;
import com.lightbot.dto.DifyWorkflowExportResult;
import com.lightbot.dto.DifyWorkflowIssueVO;
import com.lightbot.dto.WorkflowGraphDTO;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 将 LightBot 工作流草稿导出为 Dify Workflow YAML。
 *
 * <p>实现策略与 spring-ai-alibaba-admin 的 DSL Adapter 一致：可等价映射的节点转换为 Dify 节点；
 * 从 Dify 导入的节点保留其已脱敏原始结构，以保证没有 LightBot 等价运行时节点时仍可再次导出。</p>
 */
@Component
public class DifyWorkflowExporter {

    private static final Set<String> INTERNAL_TYPES = Set.of("loop_start", "loop_end", "batch_start", "batch_end");

    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    /**
     * 预检当前草稿的 Dify 兼容性。
     *
     * @param graph 工作流草稿
     * @return 兼容性预检结果
     */
    public DifyWorkflowExportPreviewVO preview(WorkflowGraphDTO graph) {
        DifyWorkflowExportPreviewVO preview = new DifyWorkflowExportPreviewVO();
        List<DifyWorkflowIssueVO> issues = new ArrayList<>();
        preview.setIssues(issues);
        List<Map<String, Object>> nodes = graph != null && graph.getNodes() != null ? graph.getNodes() : List.of();
        List<Map<String, Object>> edges = graph != null && graph.getEdges() != null ? graph.getEdges() : List.of();
        preview.setNodeCount(nodes.size());
        preview.setEdgeCount(edges.size());
        preview.setSourceDigest(sha256(graph));

        int exportableCount = 0;
        for (Map<String, Object> node : nodes) {
            String nodeId = stringValue(node.get("id"));
            String type = stringValue(node.get("type"));
            if (INTERNAL_TYPES.contains(type)) {
                addIssue(issues, "WARNING", "SKIPPED_INTERNAL_NODE", nodeId,
                        "循环或批处理容器的内部节点不会单独写入 Dify YAML");
                continue;
            }
            if (nodeId.isBlank() || type.isBlank()) {
                addIssue(issues, "BLOCKER", "INVALID_NODE", nodeId, "工作流存在缺少 id 或 type 的节点");
                continue;
            }
            exportableCount++;
            validateNodeMapping(type, mapValue(node.get("data")), nodeId, issues);
        }
        long startCount = nodes.stream().filter(node -> "start".equals(stringValue(node.get("type")))).count();
        if (startCount != 1) {
            addIssue(issues, "BLOCKER", "INVALID_START_NODE", null, "工作流必须且只能有一个开始节点");
        }
        if (nodes.stream().noneMatch(node -> "end".equals(stringValue(node.get("type"))))) {
            addIssue(issues, "BLOCKER", "MISSING_END_NODE", null, "工作流至少需要一个结束节点");
        }
        preview.setExportableCount(exportableCount);
        return preview;
    }

    /**
     * 导出 Dify YAML；仅在图结构不完整时拒绝生成文件。
     *
     * @param graph 工作流草稿
     * @param workflowName 工作流名称
     * @return Dify YAML 文件内容
     */
    public DifyWorkflowExportResult export(WorkflowGraphDTO graph, String workflowName) {
        DifyWorkflowExportPreviewVO preview = preview(graph);
        if (preview.getIssues().stream().anyMatch(issue -> "BLOCKER".equals(issue.getSeverity()))) {
            return new DifyWorkflowExportResult(null, null, preview);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", "0.3.0");
        root.put("kind", "app");
        root.put("app", Map.of(
                "name", safeName(workflowName),
                "description", "Exported from LightBot",
                "mode", "workflow"));
        root.put("workflow", buildWorkflow(graph));
        try {
            String content = yamlMapper.writeValueAsString(root);
            return new DifyWorkflowExportResult(safeName(workflowName) + ".yml", content, preview);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Dify YAML 序列化失败", e);
        }
    }

    private void validateNodeMapping(String type, Map<String, Object> data, String nodeId,
                                     List<DifyWorkflowIssueVO> issues) {
        if (!stringValue(data.get("difySourceType")).isBlank()) {
            return;
        }
        if (Set.of("llm", "classifier", "parameter_extractor").contains(type)
                && stringValue(data.get("modelName")).isBlank()) {
            addIssue(issues, "REPAIR_REQUIRED", "MODEL_MAPPING_REQUIRED", nodeId,
                    "模型名称为空，已保留待绑定标记；请在 Dify 导入后选择可用模型");
        }
        if (Set.of("retrieval", "tool", "mcp", "app_component").contains(type)) {
            addIssue(issues, "REPAIR_REQUIRED", "EXTERNAL_RESOURCE_REBIND_REQUIRED", nodeId,
                    "外部资源标识无法跨平台迁移，Dify 导入后需要重新绑定资源");
        }
        if (Set.of("loop", "batch", "confirm", "input").contains(type)) {
            addIssue(issues, "REPAIR_REQUIRED", "SEMANTIC_REVIEW_REQUIRED", nodeId,
                    "该节点已生成兼容的 Dify 表达，请在 Dify 画布中复核执行语义");
        }
    }

    private Map<String, Object> buildWorkflow(WorkflowGraphDTO graph) {
        Map<String, Object> workflow = new LinkedHashMap<>();
        workflow.put("conversation_variables", exportConversationVariables(graph.getGlobalConfig()));
        workflow.put("environment_variables", List.of());
        Map<String, Object> graphMap = new LinkedHashMap<>();
        graphMap.put("nodes", exportNodes(graph.getNodes()));
        graphMap.put("edges", exportEdges(graph.getEdges(), graph.getNodes()));
        graphMap.put("viewport", Map.of("x", 0, "y", 0, "zoom", 1));
        workflow.put("graph", graphMap);
        return workflow;
    }

    private List<Map<String, Object>> exportNodes(List<Map<String, Object>> nodes) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> node : nodes) {
            String type = stringValue(node.get("type"));
            if (INTERNAL_TYPES.contains(type)) {
                continue;
            }
            Map<String, Object> exported = new LinkedHashMap<>();
            exported.put("id", stringValue(node.get("id")));
            exported.put("type", "custom");
            exported.put("position", normalizePosition(mapValue(node.get("position"))));
            exported.put("data", exportNodeData(type, mapValue(node.get("data"))));
            result.add(exported);
        }
        return result;
    }

    private Map<String, Object> exportNodeData(String lightBotType, Map<String, Object> data) {
        Map<String, Object> original = mapValue(data.get("difySourceData"));
        String originalType = stringValue(data.get("difySourceType"));
        if (!original.isEmpty() && !originalType.isBlank()) {
            original.put("type", originalType);
            original.put("title", defaultValue(stringValue(data.get("label")), stringValue(original.get("title"))));
            original.put("desc", stringValue(original.get("desc")));
            return sanitize(original);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", mapDifyType(lightBotType));
        result.put("title", defaultValue(stringValue(data.get("label")), lightBotType));
        result.put("desc", "");
        switch (lightBotType) {
            case "start" -> exportStartData(data, result);
            case "end" -> exportEndData(data, result);
            case "llm" -> exportLlmData(data, result);
            case "condition" -> exportConditionData(data, result);
            case "classifier" -> exportClassifierData(data, result);
            case "retrieval" -> exportRetrievalData(data, result);
            case "tool", "mcp" -> exportToolData(data, result);
            case "api" -> exportApiData(data, result);
            case "script", "confirm" -> exportCodeData(data, result, "confirm".equals(lightBotType));
            case "variable" -> exportVariableData(data, result);
            case "variable_handle" -> exportVariableHandleData(data, result);
            case "parameter_extractor" -> exportParameterExtractorData(data, result);
            case "loop", "batch" -> exportIterationData(data, result);
            case "output" -> result.put("answer", defaultValue(stringValue(data.get("output")), "{{#sys.query#}}"));
            case "app_component" -> exportAppComponentData(data, result);
            case "input" -> exportInputData(data, result);
            default -> exportCodeData(data, result, false);
        }
        return sanitize(result);
    }

    private String mapDifyType(String lightBotType) {
        return switch (lightBotType) {
            case "condition" -> "if-else";
            case "classifier" -> "question-classifier";
            case "retrieval" -> "knowledge-retrieval";
            case "api" -> "http-request";
            case "script", "confirm" -> "code";
            case "variable" -> "assigner";
            case "variable_handle" -> "template-transform";
            case "parameter_extractor" -> "parameter-extractor";
            case "loop", "batch" -> "iteration";
            case "output" -> "answer";
            case "app_component" -> "agent";
            case "input" -> "assigner";
            case "mcp" -> "tool";
            default -> lightBotType;
        };
    }

    private void exportStartData(Map<String, Object> data, Map<String, Object> result) {
        List<Map<String, Object>> variables = new ArrayList<>();
        for (Map<String, Object> parameter : mapList(data.get("outputParams"))) {
            String name = stringValue(parameter.get("key"));
            if (!name.isBlank()) {
                variables.add(Map.of("variable", name, "label", name,
                        "type", defaultValue(stringValue(parameter.get("type")), "text-input"), "required", false));
            }
        }
        result.put("variables", variables);
    }

    private void exportEndData(Map<String, Object> data, Map<String, Object> result) {
        String text = defaultValue(stringValue(data.get("textTemplate")), "{{output}}");
        result.put("outputs", List.of(Map.of("variable", "result", "value_selector", variableSelector(text), "type", "string")));
    }

    private void exportLlmData(Map<String, Object> data, Map<String, Object> result) {
        result.put("model", modelConfig(data));
        List<Map<String, Object>> prompts = new ArrayList<>();
        String systemPrompt = stringValue(data.get("sysPrompt"));
        if (!systemPrompt.isBlank()) {
            prompts.add(Map.of("role", "system", "text", systemPrompt));
        }
        prompts.add(Map.of("role", "user", "text", defaultValue(stringValue(data.get("promptTemplate")), "{{#sys.query#}}")));
        result.put("prompt_template", prompts);
    }

    private void exportConditionData(Map<String, Object> data, Map<String, Object> result) {
        List<Map<String, Object>> cases = new ArrayList<>();
        for (Map<String, Object> group : mapList(data.get("conditionGroups"))) {
            List<Map<String, Object>> conditions = new ArrayList<>();
            for (Map<String, Object> rule : mapList(group.get("rules"))) {
                conditions.add(Map.of(
                        "variable_selector", variableSelector(stringValue(rule.get("variable"))),
                        "comparison_operator", defaultValue(stringValue(rule.get("operator")), "contains"),
                        "value", stringValue(rule.get("value"))));
            }
            cases.add(Map.of(
                    "id", defaultValue(stringValue(group.get("sourceHandle")), "case_" + cases.size()),
                    "logical_operator", defaultValue(stringValue(group.get("relation")), "and"),
                    "conditions", conditions));
        }
        result.put("cases", cases);
    }

    private void exportClassifierData(Map<String, Object> data, Map<String, Object> result) {
        List<Map<String, Object>> classes = new ArrayList<>();
        for (Map<String, Object> condition : mapList(data.get("conditions"))) {
            classes.add(Map.of(
                    "id", defaultValue(stringValue(condition.get("id")), "class_" + classes.size()),
                    "name", defaultValue(stringValue(condition.get("subject")), "未命名类别")));
        }
        result.put("classes", classes);
        result.put("query_variable_selector", variableSelector(stringValue(data.get("inputVariable"))));
        result.put("model", modelConfig(data));
        result.put("instruction", stringValue(data.get("instruction")));
    }

    private void exportRetrievalData(Map<String, Object> data, Map<String, Object> result) {
        result.put("query_variable_selector", variableSelector(stringValue(data.get("inputVariable"))));
        result.put("dataset_ids", List.of());
        result.put("retrieval_mode", "multiple");
        result.put("multiple_retrieval_config", Map.of(
                "top_k", numberValue(data.get("topK"), 5D).intValue(),
                "score_threshold", numberValue(data.get("threshold"), 0.5D)));
    }

    private void exportToolData(Map<String, Object> data, Map<String, Object> result) {
        result.put("provider_id", defaultValue(stringValue(data.get("mcpServerName")), "lightbot"));
        result.put("tool_name", defaultValue(stringValue(data.get("toolName")), "lightbot_tool"));
        Map<String, Object> parameters = new LinkedHashMap<>();
        for (Map<String, Object> mapping : mapList(data.get("inputMappings"))) {
            String key = stringValue(mapping.get("key"));
            if (!key.isBlank()) {
                parameters.put(key, variableSelector(stringValue(mapping.get("value"))));
            }
        }
        result.put("tool_parameters", parameters);
    }

    private void exportApiData(Map<String, Object> data, Map<String, Object> result) {
        result.put("url", stringValue(data.get("url")));
        result.put("method", defaultValue(stringValue(data.get("method")), "GET").toLowerCase());
        result.put("headers", parseJsonObject(data.get("headers")));
        result.put("body", parseJsonObject(data.get("body")));
    }

    private void exportCodeData(Map<String, Object> data, Map<String, Object> result, boolean confirmationPlaceholder) {
        result.put("code_language", defaultValue(stringValue(data.get("scriptLanguage")), "javascript"));
        String content = stringValue(data.get("scriptContent"));
        if (confirmationPlaceholder) {
            content = "// LightBot 人工确认节点：请在 Dify 中按业务语义配置人工审核。\nreturn { approved: true };";
        }
        result.put("code", defaultValue(content, "function main() { return { result: '' }; }"));
        result.put("variables", exportCodeVariables(data.get("inputParams")));
        result.put("outputs", exportCodeOutputs(data.get("outputParams")));
    }

    private void exportVariableData(Map<String, Object> data, Map<String, Object> result) {
        String name = defaultValue(stringValue(data.get("variableName")), "lightbot_variable");
        String value = stringValue(data.get("variableValue"));
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("variable_selector", List.of("conversation", name));
        item.put("operation", "over-write");
        if (value.startsWith("{{") && value.endsWith("}}")) {
            item.put("input_variable_selector", variableSelector(value));
        } else {
            item.put("input", value);
        }
        result.put("items", List.of(item));
    }

    private void exportVariableHandleData(Map<String, Object> data, Map<String, Object> result) {
        String handleType = defaultValue(stringValue(data.get("handleType")), "template");
        if ("group".equals(handleType)) {
            result.put("type", "variable-aggregator");
            result.put("variables", List.of());
            result.put("output_type", "string");
            return;
        }
        result.put("template", defaultValue(stringValue(data.get("templateContent")), "{{#sys.query#}}"));
    }

    private void exportParameterExtractorData(Map<String, Object> data, Map<String, Object> result) {
        result.put("model", modelConfig(data));
        result.put("query", variableSelector(stringValue(data.get("inputVariable"))));
        result.put("instruction", stringValue(data.get("instruction")));
        List<Map<String, Object>> parameters = new ArrayList<>();
        for (Map<String, Object> parameter : mapList(data.get("extractParams"))) {
            String name = stringValue(parameter.get("key"));
            if (!name.isBlank()) {
                parameters.add(Map.of("name", name, "type", defaultValue(stringValue(parameter.get("type")), "string"),
                        "required", Boolean.TRUE.equals(parameter.get("required")),
                        "description", defaultValue(stringValue(parameter.get("desc")), name)));
            }
        }
        result.put("parameters", parameters);
    }

    private void exportIterationData(Map<String, Object> data, Map<String, Object> result) {
        result.put("iterator_selector", variableSelector(defaultValue(stringValue(data.get("arrayVariable")), "{{input}}")));
        result.put("output_selector", List.of("iteration", "result"));
        result.put("parallel_mode", "batch".equals(stringValue(data.get("type"))));
        result.put("max_count", numberValue(data.get("count_limit"), 100D).intValue());
    }

    private void exportAppComponentData(Map<String, Object> data, Map<String, Object> result) {
        result.put("app_id", stringValue(data.get("componentCode")));
        result.put("instruction", "LightBot 子工作流引用；请在 Dify 中重新选择 Agent 或应用。");
    }

    private void exportInputData(Map<String, Object> data, Map<String, Object> result) {
        result.put("items", List.of());
        result.put("title", defaultValue(stringValue(data.get("label")), "流程输入"));
    }

    private Map<String, Object> modelConfig(Map<String, Object> data) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("temperature", numberValue(data.get("temperature"), 0.7D));
        return Map.of(
                "provider", defaultValue(stringValue(data.get("providerName")), "openai"),
                "name", defaultValue(stringValue(data.get("modelName")), "__LIGHTBOT_MODEL_REBIND__"),
                "mode", "chat",
                "completion_params", params);
    }

    private List<Map<String, Object>> exportCodeVariables(Object source) {
        List<Map<String, Object>> variables = new ArrayList<>();
        for (Map<String, Object> parameter : mapList(source)) {
            String key = stringValue(parameter.get("key"));
            if (!key.isBlank()) {
                variables.add(Map.of("variable", key, "value_selector", variableSelector(stringValue(parameter.get("value")))));
            }
        }
        return variables;
    }

    private List<Map<String, Object>> exportCodeOutputs(Object source) {
        List<Map<String, Object>> outputs = new ArrayList<>();
        for (Map<String, Object> parameter : mapList(source)) {
            String key = stringValue(parameter.get("key"));
            if (!key.isBlank()) {
                outputs.add(Map.of("variable", key, "type", defaultValue(stringValue(parameter.get("type")), "string")));
            }
        }
        return outputs;
    }

    private List<Map<String, Object>> exportEdges(List<Map<String, Object>> edges, List<Map<String, Object>> nodes) {
        Set<String> exportedNodeIds = new java.util.HashSet<>();
        for (Map<String, Object> node : nodes) {
            if (!INTERNAL_TYPES.contains(stringValue(node.get("type")))) {
                exportedNodeIds.add(stringValue(node.get("id")));
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (int index = 0; index < edges.size(); index++) {
            Map<String, Object> edge = edges.get(index);
            String source = stringValue(edge.get("source"));
            String target = stringValue(edge.get("target"));
            if (!exportedNodeIds.contains(source) || !exportedNodeIds.contains(target)) {
                continue;
            }
            Map<String, Object> exported = new LinkedHashMap<>();
            exported.put("id", defaultValue(stringValue(edge.get("id")), "lightbot-edge-" + index));
            exported.put("type", "custom");
            exported.put("source", source);
            exported.put("target", target);
            exported.put("sourceHandle", defaultValue(stringValue(edge.get("sourceHandle")), "out"));
            exported.put("targetHandle", "in");
            result.add(exported);
        }
        return result;
    }

    private List<Map<String, Object>> exportConversationVariables(Map<String, Object> globalConfig) {
        Map<String, Object> variableConfig = mapValue(globalConfig != null ? globalConfig.get("variable_config") : null);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> parameter : mapList(variableConfig.get("conversation_params"))) {
            String key = stringValue(parameter.get("key"));
            if (!key.isBlank()) {
                result.add(Map.of("selector", List.of("conversation", key),
                        "value", stringValue(parameter.get("default_value")), "value_type", "string"));
            }
        }
        return result;
    }

    private List<String> variableSelector(String value) {
        String variable = value == null ? "" : value.replace("{{", "").replace("}}", "").trim();
        if (variable.isBlank() || "query".equals(variable) || "input".equals(variable)) {
            return List.of("sys", "query");
        }
        if (variable.startsWith("#") && variable.endsWith("#")) {
            variable = variable.substring(1, variable.length() - 1);
        }
        if (variable.contains(".")) {
            return List.of(variable.split("\\."));
        }
        return List.of("conversation", variable);
    }

    private Map<String, Object> parseJsonObject(Object source) {
        if (!(source instanceof String value) || value.isBlank()) {
            return Map.of();
        }
        try {
            return sanitize(mapValue(jsonMapper.readValue(value, Object.class)));
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    private Map<String, Object> normalizePosition(Map<String, Object> position) {
        return Map.of("x", numberValue(position.get("x"), 0D), "y", numberValue(position.get("y"), 0D));
    }

    private String safeName(String workflowName) {
        String normalized = workflowName == null ? "" : workflowName.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return normalized.isBlank() ? "lightbot-workflow" : normalized;
    }

    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            result.add(mapValue(item));
        }
        return result;
    }

    private Map<String, Object> sanitize(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!isSensitiveKey(key)) {
                result.put(key, sanitizeValue(value, key));
            }
        });
        return result;
    }

    private Object sanitizeValue(Object value, String key) {
        if (isSensitiveKey(key)) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            return sanitize(mapValue(map));
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>();
            for (Object item : list) {
                result.add(sanitizeValue(item, key));
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

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private Double numberValue(Object value, double fallback) {
        return value instanceof Number number ? number.doubleValue() : fallback;
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

    private String sha256(WorkflowGraphDTO graph) {
        try {
            String source = jsonMapper.writeValueAsString(graph);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder("sha256:");
            for (byte item : digest) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("无法生成工作流摘要", e);
        }
    }
}
