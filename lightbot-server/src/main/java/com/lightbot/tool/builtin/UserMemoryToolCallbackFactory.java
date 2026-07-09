package com.lightbot.tool.builtin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.dto.UserMemoryVO;
import com.lightbot.entity.UserMemory;
import com.lightbot.enums.UserMemoryStatus;
import com.lightbot.service.UserMemoryService;
import com.lightbot.service.chat.ChatContext;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 用户长期记忆工具回调工厂
 *
 * @author finch
 * @since 2026-07-09
 */
@Component
@RequiredArgsConstructor
public class UserMemoryToolCallbackFactory {

    public static final String SAVE_TOOL_NAME = "memory_save";
    public static final String SEARCH_TOOL_NAME = "memory_search";
    public static final String DELETE_TOOL_NAME = "memory_delete";

    private final UserMemoryService userMemoryService;
    private final ObjectMapper objectMapper;

    /**
     * 构建长期记忆工具回调列表
     *
     * @return 工具回调列表
     */
    public List<ToolCallback> buildCallbacks() {
        return List.of(new SaveMemoryCallback(), new SearchMemoryCallback(), new DeleteMemoryCallback());
    }

    private class SaveMemoryCallback extends BaseMemoryCallback {

        SaveMemoryCallback() {
            super(DefaultToolDefinition.builder()
                    .name(SAVE_TOOL_NAME)
                    .description("""
                            Save a stable user long-term memory when the user explicitly asks you to remember a preference, profile fact, project fact, or long-term instruction.
                            Do not save passwords, secrets, API keys, tokens, private contact data, or temporary facts.
                            """)
                    .inputSchema("""
                            {
                              "type": "object",
                              "properties": {
                                "content": {"type": "string", "description": "The concise memory content to save."},
                                "memoryType": {"type": "string", "enum": ["preference", "profile", "project_fact", "instruction"], "description": "Memory category."},
                                "keywords": {"type": "array", "items": {"type": "string"}, "description": "Optional search keywords."},
                                "confidence": {"type": "number", "minimum": 0, "maximum": 1, "description": "Confidence of the memory."}
                              },
                              "required": ["content"]
                            }
                            """)
                    .build());
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            try {
                Map<String, Object> args = parseArgs(toolInput);
                Long userId = resolveUserId(toolContext);
                if (userId == null) {
                    return failure("缺少用户上下文，无法保存长期记忆");
                }
                String content = str(args.get("content"));
                if (content == null || content.isBlank()) {
                    return failure("缺少 content 参数");
                }
                UserMemoryVO saved = userMemoryService.saveFromTool(
                        userId,
                        resolveAgentId(toolContext),
                        resolveSessionId(toolContext),
                        resolveUserMessageId(toolContext),
                        str(args.get("memoryType")),
                        content,
                        stringList(args.get("keywords")),
                        decimal(args.get("confidence")));
                return objectMapper.writeValueAsString(Map.of("success", true, "memory", saved));
            } catch (Exception e) {
                return failure(e.getMessage());
            }
        }
    }

    private class SearchMemoryCallback extends BaseMemoryCallback {

        SearchMemoryCallback() {
            super(DefaultToolDefinition.builder()
                    .name(SEARCH_TOOL_NAME)
                    .description("Search the current user's enabled long-term memories for relevant preferences or background facts.")
                    .inputSchema("""
                            {
                              "type": "object",
                              "properties": {
                                "query": {"type": "string", "description": "Search query."},
                                "limit": {"type": "integer", "minimum": 1, "maximum": 10, "description": "Maximum memories to return."}
                              },
                              "required": ["query"]
                            }
                            """)
                    .build());
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            try {
                Map<String, Object> args = parseArgs(toolInput);
                Long userId = resolveUserId(toolContext);
                if (userId == null) {
                    return failure("缺少用户上下文，无法查询长期记忆");
                }
                String query = str(args.get("query"));
                int limit = intVal(args.get("limit"), 5, 1, 10);
                List<UserMemoryVO> memories = userMemoryService.searchForPrompt(userId, resolveAgentId(toolContext), query, limit)
                        .stream()
                        .map(UserMemoryVO::from)
                        .toList();
                return objectMapper.writeValueAsString(Map.of("success", true, "memories", memories));
            } catch (Exception e) {
                return failure(e.getMessage());
            }
        }
    }

    private class DeleteMemoryCallback extends BaseMemoryCallback {

        DeleteMemoryCallback() {
            super(DefaultToolDefinition.builder()
                    .name(DELETE_TOOL_NAME)
                    .description("Disable or archive one of the current user's long-term memories when the user asks you to forget it.")
                    .inputSchema("""
                            {
                              "type": "object",
                              "properties": {
                                "memoryId": {"type": "string", "description": "Memory id to disable."}
                              },
                              "required": ["memoryId"]
                            }
                            """)
                    .build());
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            try {
                Map<String, Object> args = parseArgs(toolInput);
                Long userId = resolveUserId(toolContext);
                Long memoryId = longVal(args.get("memoryId"));
                if (userId == null || memoryId == null) {
                    return failure("缺少用户上下文或 memoryId 参数");
                }
                UserMemory memory = userMemoryService.getById(memoryId);
                if (memory == null || !userId.equals(memory.getUserId())) {
                    return failure("未找到可删除的长期记忆");
                }
                memory.setStatus(UserMemoryStatus.DISABLED);
                userMemoryService.updateById(memory);
                return objectMapper.writeValueAsString(Map.of("success", true, "memoryId", String.valueOf(memoryId)));
            } catch (Exception e) {
                return failure(e.getMessage());
            }
        }
    }

    private abstract class BaseMemoryCallback implements ToolCallback {

        private final ToolDefinition definition;

        BaseMemoryCallback(ToolDefinition definition) {
            this.definition = definition;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return definition;
        }

        @Override
        public ToolMetadata getToolMetadata() {
            return ToolMetadata.builder().returnDirect(false).build();
        }

        @Override
        public String call(String toolInput) {
            return call(toolInput, null);
        }
    }

    private Map<String, Object> parseArgs(String toolInput) throws Exception {
        if (toolInput == null || toolInput.isBlank()) {
            return Map.of();
        }
        return objectMapper.readValue(toolInput, new TypeReference<>() {});
    }

    private String failure(String message) {
        try {
            return objectMapper.writeValueAsString(Map.of("success", false, "error", message != null ? message : "unknown"));
        } catch (Exception e) {
            return "{\"success\":false}";
        }
    }

    private Long resolveUserId(ToolContext toolContext) {
        Object value = contextValue(toolContext, "userId");
        if (value == null) {
            ChatContext chatContext = resolveChatContext(toolContext);
            return chatContext != null ? chatContext.getUserId() : null;
        }
        return longVal(value);
    }

    private Long resolveAgentId(ToolContext toolContext) {
        Object value = contextValue(toolContext, "agentId");
        return longVal(value);
    }

    private Long resolveSessionId(ToolContext toolContext) {
        Object value = contextValue(toolContext, "sessionId");
        return longVal(value);
    }

    private Long resolveUserMessageId(ToolContext toolContext) {
        ChatContext chatContext = resolveChatContext(toolContext);
        return chatContext != null ? chatContext.getUserMessageId() : null;
    }

    private ChatContext resolveChatContext(ToolContext toolContext) {
        Object value = contextValue(toolContext, "chatContext");
        return value instanceof ChatContext chatContext ? chatContext : null;
    }

    private Object contextValue(ToolContext toolContext, String key) {
        return toolContext != null && toolContext.getContext() != null ? toolContext.getContext().get(key) : null;
    }

    private String str(Object value) {
        return value != null ? value.toString() : null;
    }

    private Long longVal(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private int intVal(Object value, int defaultValue, int min, int max) {
        int parsed = defaultValue;
        if (value instanceof Number n) {
            parsed = n.intValue();
        } else if (value != null && !value.toString().isBlank()) {
            try {
                parsed = Integer.parseInt(value.toString());
            } catch (Exception ignored) {
            }
        }
        return Math.max(min, Math.min(max, parsed));
    }

    private BigDecimal decimal(Object value) {
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> raw)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : raw) {
            if (item != null && !item.toString().isBlank()) {
                result.add(item.toString());
            }
        }
        return result;
    }
}
