package com.lightbot.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 工具事件持久化压缩：合并 SubAgent 流式 token，提取可读 replyText。
 */
@Slf4j
public final class ToolEventCompactUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ToolEventCompactUtil() {
    }

    /**
     * 压缩 toolEvents 后再写入 message metadata，减少 SubAgent token 碎片占用。
     *
     * @param events 原始事件列表
     * @return 压缩后的事件列表
     */
    public static List<Map<String, Object>> compactForPersistence(List<Map<String, Object>> events) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        Map<String, Object> pendingToken = null;

        for (Map<String, Object> evt : events) {
            if (evt == null) {
                continue;
            }
            String type = stringVal(evt.get("type"));
            if ("subagent_token".equals(type)) {
                if (pendingToken != null && sameSubagentScope(pendingToken, evt)) {
                    appendTokenContent(pendingToken, evt);
                } else {
                    flushToken(result, pendingToken);
                    pendingToken = new LinkedHashMap<>(evt);
                }
                continue;
            }
            flushToken(result, pendingToken);
            pendingToken = null;
            if ("subagent_result".equals(type)) {
                result.add(enrichSubagentResult(new LinkedHashMap<>(evt)));
            } else {
                result.add(evt);
            }
        }
        flushToken(result, pendingToken);
        return result;
    }

    /**
     * 从 SubAgent 委派工具返回 JSON 中提取 reply 文本
     *
     * @param resultJson 工具返回 JSON
     * @return reply 文本，无法解析时返回 null
     */
    public static String extractSubagentReplyText(String resultJson) {
        if (resultJson == null || resultJson.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> map = MAPPER.readValue(resultJson, new TypeReference<>() {});
            Object reply = map.get("reply");
            if (reply != null) {
                String text = reply.toString();
                return text.isBlank() ? null : text;
            }
        } catch (Exception ignored) {
            // 非 JSON：若不像工具 JSON，直接作为展示文本
            if (!resultJson.trim().startsWith("{")) {
                return resultJson;
            }
        }
        return null;
    }

    private static void flushToken(List<Map<String, Object>> result, Map<String, Object> pending) {
        if (pending == null) {
            return;
        }
        String content = stringVal(pending.get("content"));
        if (!content.isEmpty()) {
            result.add(pending);
        }
    }

    private static void appendTokenContent(Map<String, Object> target, Map<String, Object> delta) {
        String existing = stringVal(target.get("content"));
        String addition = stringVal(delta.get("content"));
        target.put("content", existing + addition);
    }

    private static boolean sameSubagentScope(Map<String, Object> a, Map<String, Object> b) {
        return Objects.equals(a.get("subagentName"), b.get("subagentName"))
                && Objects.equals(a.get("contentOffset"), b.get("contentOffset"));
    }

    private static Map<String, Object> enrichSubagentResult(Map<String, Object> evt) {
        if (!evt.containsKey("replyText")) {
            String reply = extractSubagentReplyText(stringVal(evt.get("result")));
            if (reply != null && !reply.isBlank()) {
                evt.put("replyText", reply);
            }
        }
        return evt;
    }

    private static String stringVal(Object v) {
        return v != null ? v.toString() : "";
    }
}
