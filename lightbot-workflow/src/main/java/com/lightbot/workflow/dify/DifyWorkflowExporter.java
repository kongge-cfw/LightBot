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

/** 将 LightBot 工作流草稿导出为受限的 Dify Workflow YAML。 */
@Component
public class DifyWorkflowExporter {

    private static final Set<String> SUPPORTED_TYPES = Set.of("start", "end", "llm", "condition", "classifier");

    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    /**
     * 校验当前 LightBot 图是否可导出为 Dify Workflow。
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
            if (!SUPPORTED_TYPES.contains(type)) {
                addIssue(issues, "BLOCKER", "EXPORT_UNSUPPORTED_NODE", nodeId,
                        "暂不支持导出 LightBot 节点：" + type);
                continue;
            }
            exportableCount++;
            Map<String, Object> data = mapValue(node.get("data"));
            if ("llm".equals(type) && stringValue(data.get("modelName")).isBlank()) {
                addIssue(issues, "BLOCKER", "MODEL_MAPPING_REQUIRED", nodeId,
                        "LLM 节点缺少可导出的模型名称");
            }
            if ("classifier".equals(type) && stringValue(data.get("modelName")).isBlank()) {
                addIssue(issues, "BLOCKER", "MODEL_MAPPING_REQUIRED", nodeId,
                        "分类节点缺少可导出的模型名称");
            }
        }
        if (nodes.stream().filter(node -> "start".equals(stringValue(node.get("type")))).count() != 1) {
            addIssue(issues, "BLOCKER", "INVALID_START_NODE", null, "工作流必须且只能有一个开始节点");
        }
        if (nodes.stream().noneMatch(node -> "end".equals(stringValue(node.get("type"))))) {
            addIssue(issues, "BLOCKER", "MISSING_END_NODE", null, "工作流至少需要一个结束节点");
        }
        preview.setExportableCount(exportableCount);
        return preview;
    }

    /**
     * 导出 YAML；存在阻断问题时拒绝生成部分文件。
     *
     * @param graph 工作流草稿
     * @param workflowName 文件及应用名称
     * @return YAML 文件内容
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

    private Map<String, Object> buildWorkflow(WorkflowGraphDTO graph) {
        Map<String, Object> workflow = new LinkedHashMap<>();
        workflow.put("conversation_variables", exportConversationVariables(graph.getGlobalConfig()));
        workflow.put("environment_variables", List.of());
        Map<String, Object> graphMap = new LinkedHashMap<>();
        graphMap.put("nodes", exportNodes(graph.getNodes()));
        graphMap.put("edges", exportEdges(graph.getEdges()));
        graphMap.put("viewport", Map.of("x", 0, "y", 0, "zoom", 1));
        workflow.put("graph", graphMap);
        return workflow;
    }

    private List<Map<String, Object>> exportNodes(List<Map<String, Object>> nodes) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> node : nodes) {
            Map<String, Object> exported = new LinkedHashMap<>();
            exported.put("id", stringValue(node.get("id")));
            exported.put("type", "custom");
            exported.put("position", normalizePosition(mapValue(node.get("position"))));
            exported.put("data", exportNodeData(stringValue(node.get("type")), mapValue(node.get("data"))));
            result.add(exported);
        }
        return result;
    }

    private Map<String, Object> exportNodeData(String type, Map<String, Object> data) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("title", defaultValue(stringValue(data.get("label")), type));
        result.put("desc", "");
        switch (type) {
            case "start", "end" -> result.put("type", type);
            case "llm" -> exportLlmData(data, result);
            case "condition" -> exportConditionData(data, result);
            case "classifier" -> exportClassifierData(data, result);
            default -> throw new IllegalArgumentException("不支持导出的节点类型: " + type);
        }
        return result;
    }

    private void exportLlmData(Map<String, Object> data, Map<String, Object> result) {
        result.put("type", "llm");
        result.put("model", Map.of(
                "provider", defaultValue(stringValue(data.get("providerName")), "openai"),
                "name", stringValue(data.get("modelName")),
                "mode", "chat",
                "completion_params", Map.of("temperature", numberValue(data.get("temperature"), 0.7D))));
        List<Map<String, Object>> prompts = new ArrayList<>();
        String systemPrompt = stringValue(data.get("sysPrompt"));
        if (!systemPrompt.isBlank()) {
            prompts.add(Map.of("role", "system", "text", systemPrompt));
        }
        prompts.add(Map.of("role", "user", "text", defaultValue(stringValue(data.get("promptTemplate")), "{{#sys.query#}}")));
        result.put("prompt_template", prompts);
    }

    private void exportConditionData(Map<String, Object> data, Map<String, Object> result) {
        result.put("type", "if-else");
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
        result.put("type", "question-classifier");
        List<Map<String, Object>> classes = new ArrayList<>();
        for (Map<String, Object> condition : mapList(data.get("conditions"))) {
            classes.add(Map.of(
                    "id", defaultValue(stringValue(condition.get("id")), "class_" + classes.size()),
                    "name", defaultValue(stringValue(condition.get("subject")), "未命名类别")));
        }
        result.put("classes", classes);
        result.put("query_variable_selector", List.of("sys", "query"));
        result.put("model", Map.of(
                "provider", defaultValue(stringValue(data.get("providerName")), "openai"),
                "name", stringValue(data.get("modelName"))));
    }

    private List<Map<String, Object>> exportEdges(List<Map<String, Object>> edges) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (int index = 0; index < edges.size(); index++) {
            Map<String, Object> edge = edges.get(index);
            Map<String, Object> exported = new LinkedHashMap<>();
            exported.put("id", defaultValue(stringValue(edge.get("id")), "lightbot-edge-" + index));
            exported.put("type", "custom");
            exported.put("source", stringValue(edge.get("source")));
            exported.put("target", stringValue(edge.get("target")));
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
            if (key.isBlank()) {
                continue;
            }
            result.add(Map.of(
                    "selector", List.of("conversation", key),
                    "value", stringValue(parameter.get("default_value")),
                    "value_type", "string"));
        }
        return result;
    }

    private List<String> variableSelector(String value) {
        String variable = value.replace("{{", "").replace("}}", "").trim();
        if (variable.isBlank() || "query".equals(variable) || "input".equals(variable)) {
            return List.of("sys", "query");
        }
        return List.of("conversation", variable);
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
            return Map.of();
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
