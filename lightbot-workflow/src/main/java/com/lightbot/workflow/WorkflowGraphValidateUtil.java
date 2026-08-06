package com.lightbot.workflow;

import com.lightbot.enums.NodeType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工作流图结构校验工具（与前端 workflowGraphValidate.js 语义对齐）
 */
public final class WorkflowGraphValidateUtil {

    private static final int MAX_NODES = 500;
    private static final int MAX_EDGES = 1_000;

    private static final Set<String> BRANCH_NODE_TYPES = Set.of(
            NodeType.CONDITION.getCode(),
            NodeType.CLASSIFIER.getCode()
    );

    private static final String MULTI_OUT_EDGE_SUFFIX =
            "：当前引擎仅会沿第一条出边继续执行，不会并行跑多条分支。"
                    + "请删除多余连线，或改用「条件分支 / 意图分类」节点。";

    private WorkflowGraphValidateUtil() {
    }

    /**
     * 校验非分支节点是否存在多条出边
     *
     * @param nodes 画布节点
     * @param edges 画布连线
     * @return 错误信息列表
     */
    public static List<String> validateMultiOutgoingEdges(List<Map<String, Object>> nodes,
                                                        List<Map<String, Object>> edges) {
        List<String> errors = new ArrayList<>();
        if (edges == null || edges.isEmpty()) {
            return errors;
        }

        Map<String, String> nodeTypes = new HashMap<>();
        Map<String, String> nodeLabels = new HashMap<>();
        if (nodes != null) {
            for (Map<String, Object> node : nodes) {
                if (node == null || node.get("id") == null) {
                    continue;
                }
                String id = node.get("id").toString();
                String type = node.get("type") != null ? node.get("type").toString() : "";
                nodeTypes.put(id, type);
                nodeLabels.put(id, resolveNodeLabel(node, type));
            }
        }

        Map<String, Integer> outCounts = new HashMap<>();
        for (Map<String, Object> edge : edges) {
            if (edge == null || edge.get("source") == null) {
                continue;
            }
            String sourceId = edge.get("source").toString();
            outCounts.merge(sourceId, 1, Integer::sum);
        }

        for (Map.Entry<String, Integer> entry : outCounts.entrySet()) {
            if (entry.getValue() <= 1) {
                continue;
            }
            String nodeId = entry.getKey();
            String type = nodeTypes.getOrDefault(nodeId, "");
            if (isMultiOutEdgeAllowed(type)) {
                continue;
            }
            String label = nodeLabels.getOrDefault(nodeId, nodeId);
            errors.add(String.format("「%s」有 %d 条出边%s", label, entry.getValue(), MULTI_OUT_EDGE_SUFFIX));
        }
        return errors;
    }

    /**
     * 校验可保存的工作流图载荷，不要求草稿已配置完全部业务节点。
     *
     * @param nodes 画布节点
     * @param edges 画布连线
     * @return 错误信息列表
     */
    @SuppressWarnings("unchecked")
    public static List<String> validatePayload(List<Map<String, Object>> nodes, List<Map<String, Object>> edges) {
        List<String> errors = new ArrayList<>();
        if (nodes == null || nodes.isEmpty()) {
            errors.add("工作流节点为空");
            return errors;
        }
        if (nodes.size() > MAX_NODES) {
            errors.add("工作流节点数量不能超过" + MAX_NODES);
        }
        if (edges != null && edges.size() > MAX_EDGES) {
            errors.add("工作流连线数量不能超过" + MAX_EDGES);
        }

        Set<String> nodeIds = new java.util.HashSet<>();
        for (Map<String, Object> node : nodes) {
            if (node == null || node.get("id") == null || node.get("id").toString().isBlank()) {
                errors.add("工作流节点缺少ID");
                continue;
            }
            String id = node.get("id").toString();
            if (!nodeIds.add(id)) {
                errors.add("工作流节点ID重复: " + id);
            }
            if (id.length() > 100) {
                errors.add("工作流节点ID不超过100字");
            }
            Object dataObj = node.get("data");
            if (dataObj instanceof Map<?, ?> data) {
                validateFieldLength(errors, data, "label", 50, "节点名称");
                validateFieldLength(errors, data, "url", 2048, "HTTP地址");
                validateFieldLength(errors, data, "code", 32000, "代码内容");
                validateFieldLength(errors, data, "systemPrompt", 5000, "系统提示词");
                validateFieldLength(errors, data, "promptTemplate", 5000, "提示词模板");
            }
        }
        if (edges != null) {
            for (Map<String, Object> edge : edges) {
                if (edge == null || edge.get("source") == null || edge.get("target") == null) {
                    errors.add("工作流连线缺少起点或终点");
                    continue;
                }
                if (!nodeIds.contains(edge.get("source").toString()) || !nodeIds.contains(edge.get("target").toString())) {
                    errors.add("工作流连线引用了不存在的节点");
                }
            }
        }
        return errors;
    }

    private static void validateFieldLength(List<String> errors, Map<?, ?> data, String key, int max, String name) {
        Object value = data.get(key);
        if (value != null && value.toString().length() > max) {
            errors.add(name + "不超过" + max + "字");
        }
    }

    private static boolean isMultiOutEdgeAllowed(String type) {
        if (type == null || type.isBlank() || NodeType.END.getCode().equals(type)) {
            return true;
        }
        if (NodeType.LOOP.getCode().equals(type) || NodeType.BATCH.getCode().equals(type)) {
            return true;
        }
        return BRANCH_NODE_TYPES.contains(type);
    }

    @SuppressWarnings("unchecked")
    private static String resolveNodeLabel(Map<String, Object> node, String type) {
        Object dataObj = node.get("data");
        if (dataObj instanceof Map<?, ?> data) {
            Object label = data.get("label");
            if (label != null && !label.toString().isBlank()) {
                return label.toString().trim();
            }
        }
        try {
            return NodeType.fromValue(type).getDesc();
        } catch (IllegalArgumentException ignored) {
            return node.get("id") != null ? node.get("id").toString() : "节点";
        }
    }
}
