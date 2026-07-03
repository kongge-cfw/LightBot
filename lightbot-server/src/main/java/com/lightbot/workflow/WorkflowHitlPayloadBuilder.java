package com.lightbot.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.tool.builtin.AskUserTool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流 HITL 挂起载荷构建：将 ask_user 等工具结果转为与 confirm 节点一致的 confirmForm 结构
 */
public final class WorkflowHitlPayloadBuilder {

    public static final String HITL_TYPE_ASK_USER = "ask_user";
    public static final String ANSWER_FIELD_KEY = "answer";

    private WorkflowHitlPayloadBuilder() {
    }

    /**
     * 判断工具结果是否要求等待用户输入（ask_user）
     * <p>以 JSON 中 {@code wait_for_user} 为准（仅 ask_user 会产出该标记），
     * 兼容节点 data 中 toolName 误存为 displayName 的历史配置。</p>
     */
    public static boolean isAskUserWaitForUser(String toolName, String rawResult, ObjectMapper objectMapper) {
        return hasWaitForUserFlag(rawResult, objectMapper);
    }

    /**
     * 解析工具 JSON 结果中的 wait_for_user 标记
     */
    public static boolean hasWaitForUserFlag(String rawResult, ObjectMapper objectMapper) {
        if (rawResult == null || rawResult.isBlank()) {
            return false;
        }
        try {
            JsonNode node = objectMapper.readTree(rawResult.trim());
            return node.path("wait_for_user").asBoolean(false);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 将 ask_user 工具 JSON 转为 workflow_confirm_required.confirmForm
     */
    public static Map<String, Object> fromAskUserTool(String nodeId, String rawResult, ObjectMapper objectMapper) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("nodeId", nodeId);
        payload.put("hitlType", HITL_TYPE_ASK_USER);
        payload.put("toolName", AskUserTool.TOOL_NAME);

        String question = "请回答以下问题";
        boolean openEnded = true;
        List<String> options = List.of();

        try {
            JsonNode node = objectMapper.readTree(rawResult.trim());
            if (node.has("question") && !node.get("question").isNull()) {
                question = node.get("question").asText(question);
            }
            openEnded = node.path("is_open_ended").asBoolean(true);
            if (node.has("options") && node.get("options").isArray()) {
                List<String> list = new ArrayList<>();
                for (JsonNode opt : node.get("options")) {
                    if (opt != null && !opt.isNull()) {
                        String text = opt.asText("").trim();
                        if (!text.isEmpty()) {
                            list.add(text);
                        }
                    }
                }
                options = list;
                if (!list.isEmpty()) {
                    openEnded = false;
                }
            }
        } catch (Exception ignored) {
        }

        payload.put("message", question);
        payload.put("formFields", buildAskUserFormFields(openEnded, options));
        return payload;
    }

    private static List<Map<String, Object>> buildAskUserFormFields(boolean openEnded, List<String> options) {
        List<Map<String, Object>> fields = new ArrayList<>();
        if (!openEnded && options != null && !options.isEmpty()) {
            Map<String, Object> choice = new HashMap<>();
            choice.put("key", ANSWER_FIELD_KEY);
            choice.put("label", "请选择");
            choice.put("type", "radio");
            choice.put("required", true);
            choice.put("options", options);
            fields.add(choice);
        } else {
            Map<String, Object> text = new HashMap<>();
            text.put("key", ANSWER_FIELD_KEY);
            text.put("label", "您的回答");
            text.put("type", "textarea");
            text.put("required", true);
            fields.add(text);
        }
        return fields;
    }
}
