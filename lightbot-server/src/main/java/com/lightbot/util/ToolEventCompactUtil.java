package com.lightbot.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 工具事件持久化压缩：合并 SubAgent 流式 token，提取可读 replyText，对齐 contentOffset。
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
        return compactForPersistence(events, null);
    }

    /**
     * 压缩 toolEvents 并根据最终正文重新对齐 contentOffset（入库前调用）。
     *
     * @param events       原始事件列表
     * @param finalContent 最终落库正文
     * @return 压缩后的事件列表
     */
    public static List<Map<String, Object>> compactForPersistence(List<Map<String, Object>> events,
                                                                    String finalContent) {
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
                result.add(new LinkedHashMap<>(evt));
            }
        }
        flushToken(result, pendingToken);
        dedupeSubagentTokens(result);
        if (finalContent != null) {
            realignContentOffsets(result, finalContent);
        } else {
            stripContentPrefixAnchors(result);
        }
        return result;
    }

    /**
     * 提取工具块 offset（仅 tool_call / subagent_call，与前端 getToolBlockOffsets 一致）
     *
     * @param events 事件列表
     * @return 排序去重后的 offset 列表
     */
    public static List<Integer> extractToolBlockOffsets(List<Map<String, Object>> events) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }
        Set<Integer> offsets = new HashSet<>();
        for (Map<String, Object> evt : events) {
            if (evt == null) {
                continue;
            }
            String type = stringVal(evt.get("type"));
            if (!"tool_call".equals(type) && !"subagent_call".equals(type)) {
                continue;
            }
            Object co = evt.get("contentOffset");
            if (co instanceof Number n) {
                offsets.add(n.intValue());
            }
        }
        return offsets.stream().sorted().toList();
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

    private static void realignContentOffsets(List<Map<String, Object>> events, String finalContent) {
        if (events.isEmpty()) {
            return;
        }
        String content = finalContent != null ? finalContent : "";
        Map<Integer, Integer> offsetRemap = new HashMap<>();

        for (Map<String, Object> evt : events) {
            String type = stringVal(evt.get("type"));
            if (!"tool_call".equals(type) && !"subagent_call".equals(type)) {
                continue;
            }
            int oldOffset = toInt(evt.get("contentOffset"), 0);
            String anchor = stringVal(evt.get("contentPrefixAnchor"));
            int newOffset = resolveOffset(anchor, oldOffset, content);
            offsetRemap.put(oldOffset, newOffset);
            evt.put("contentOffset", newOffset);
            // 保留/刷新前缀锚点，供前端历史消息精确切分正文
            if ("subagent_call".equals(type) && newOffset > 0 && newOffset <= content.length()) {
                evt.put("contentPrefixAnchor", content.substring(0, newOffset));
            } else {
                evt.remove("contentPrefixAnchor");
            }
        }

        for (Map<String, Object> evt : events) {
            if (!"subagent_call".equals(stringVal(evt.get("type")))) {
                evt.remove("contentPrefixAnchor");
            }
            Object co = evt.get("contentOffset");
            if (!(co instanceof Number n)) {
                continue;
            }
            Integer mapped = offsetRemap.get(n.intValue());
            if (mapped != null) {
                evt.put("contentOffset", mapped);
            } else if (n.intValue() > content.length()) {
                evt.put("contentOffset", content.length());
            }
        }
    }

    private static int resolveOffset(String anchor, int fallbackOffset, String finalContent) {
        if (anchor != null && !anchor.isEmpty()) {
            int idx = finalContent.indexOf(anchor);
            if (idx >= 0) {
                return idx + anchor.length();
            }
            String sanitizedAnchor = TextNormalizeUtil.sanitizeForDatabase(anchor);
            if (!sanitizedAnchor.equals(anchor)) {
                idx = finalContent.indexOf(sanitizedAnchor);
                if (idx >= 0) {
                    return idx + sanitizedAnchor.length();
                }
            }
            if (finalContent.startsWith(anchor)) {
                return anchor.length();
            }
            if (finalContent.startsWith(sanitizedAnchor)) {
                return sanitizedAnchor.length();
            }
        }
        return Math.min(Math.max(0, fallbackOffset), finalContent.length());
    }

    private static void stripContentPrefixAnchors(List<Map<String, Object>> events) {
        for (Map<String, Object> evt : events) {
            evt.remove("contentPrefixAnchor");
        }
    }

    /** 已有 subagent_result.replyText 时移除同 scope 的 subagent_token，避免重复存储 */
    private static void dedupeSubagentTokens(List<Map<String, Object>> events) {
        Set<String> resultScopes = new HashSet<>();
        for (Map<String, Object> evt : events) {
            if ("subagent_result".equals(stringVal(evt.get("type"))) && evt.get("replyText") != null) {
                resultScopes.add(subagentScopeKey(evt));
            }
        }
        if (resultScopes.isEmpty()) {
            return;
        }
        events.removeIf(evt -> "subagent_token".equals(stringVal(evt.get("type")))
                && resultScopes.contains(subagentScopeKey(evt)));
    }

    private static String subagentScopeKey(Map<String, Object> evt) {
        return stringVal(evt.get("subagentName")) + "@" + stringVal(evt.get("contentOffset"));
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

    private static int toInt(Object value, int defaultValue) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return defaultValue;
    }

    private static String stringVal(Object v) {
        return v != null ? v.toString() : "";
    }
}
