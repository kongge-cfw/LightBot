package com.lightbot.tool.builtin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.tool.annotation.SystemTool;
import com.lightbot.tool.annotation.ToolParamMeta;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内置工具 - 更新当前会话待办快照。
 * <p>工具本身不维护第二份会话状态；返回结果会由对话工具事件持久化，前端据此恢复待办。</p>
 *
 * @author finch
 * @since 2026-07-14
 */
@Component("writeTodosTool")
@RequiredArgsConstructor
@SystemTool(displayName = "更新待办", icon = "CheckSquareOutlined",
        description = "更新当前任务的完整待办清单。适用于多步骤任务、并行子智能体编排和阶段进度同步。每次调用必须传入完整待办快照。",
        tags = {"协作", "待办"},
        outputExample = "{\"success\":true,\"todos\":[{\"id\":\"research\",\"content\":\"调研资料\",\"status\":\"completed\"}]}",
        outputSchema = "{\"type\":\"object\",\"properties\":{\"success\":{\"type\":\"boolean\"},\"todos\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"string\"},\"content\":{\"type\":\"string\"},\"status\":{\"type\":\"string\",\"enum\":[\"pending\",\"in_progress\",\"completed\",\"cancelled\"]}}}}}}")
public class WriteTodosTool {

    private static final List<String> SUPPORTED_STATUSES = List.of("pending", "in_progress", "completed", "cancelled");

    private final ObjectMapper objectMapper;

    /**
     * 返回经校验的完整待办快照。
     *
     * @param todos 待办列表，每项必须包含 id、content、status
     * @return 待办快照 JSON
     */
    @Tool(name = "write_todos", description = "写入当前任务的完整待办清单。每项包含稳定 id、内容 content 和状态 status（pending/in_progress/completed/cancelled）。每次调用都传完整清单，不要只传增量。")
    public String writeTodos(
            @ToolParam(description = "完整待办列表。每项必须有 id、content、status；status 只能是 pending、in_progress、completed、cancelled。")
            @ToolParamMeta(example = "[{\"id\":\"research\",\"content\":\"调研资料\",\"status\":\"in_progress\"}]")
            List<Map<String, Object>> todos) {
        if (todos == null || todos.isEmpty()) {
            return toJson(Map.of("success", false, "error", "待办列表不能为空", "todos", List.of()));
        }

        // 1. 校验并规范化，保证前端无需猜测字段或状态。
        List<Map<String, String>> normalized = new java.util.ArrayList<>();
        for (Map<String, Object> todo : todos) {
            String id = value(todo, "id");
            String content = value(todo, "content");
            String status = value(todo, "status");
            if (id.isBlank() || content.isBlank() || !SUPPORTED_STATUSES.contains(status)) {
                return toJson(Map.of("success", false,
                        "error", "每个待办必须包含 id、content，且 status 为 pending/in_progress/completed/cancelled",
                        "todos", List.of()));
            }
            Map<String, String> item = new LinkedHashMap<>();
            item.put("id", id);
            item.put("content", content);
            item.put("status", status);
            normalized.add(item);
        }

        // 2. 结果由 ChatService 的工具事件持久化，作为会话待办的唯一历史来源。
        return toJson(Map.of("success", true, "todos", normalized));
    }

    private String value(Map<String, Object> source, String key) {
        Object value = source != null ? source.get(key) : null;
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String toJson(Map<String, ?> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{\"success\":false,\"error\":\"待办结果序列化失败\",\"todos\":[]}";
        }
    }
}
