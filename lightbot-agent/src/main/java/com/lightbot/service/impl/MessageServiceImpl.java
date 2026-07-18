package com.lightbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.BizException;
import com.lightbot.entity.Message;
import com.lightbot.enums.ErrorCode;
import com.lightbot.entity.MessageFeedback;
import com.lightbot.mapper.MessageFeedbackMapper;
import com.lightbot.mapper.MessageMapper;
import com.lightbot.mapper.ToolCallMapper;
import com.lightbot.dto.ChatAttachmentDTO;
import com.lightbot.service.ChatAttachmentParsedService;
import com.lightbot.service.MessageService;
import com.lightbot.util.MinioUtil;
import com.lightbot.util.SessionStoragePath;
import com.lightbot.vo.ConversationSearchResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 消息服务实现类
 *
 * @author finch
 * @since 2026-05-19
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageServiceImpl extends ServiceImpl<MessageMapper, Message>
        implements MessageService {

    private final MinioUtil minioUtil;
    private final ObjectMapper objectMapper;
    private final ToolCallMapper toolCallMapper;
    private final MessageFeedbackMapper messageFeedbackMapper;
    private final ChatAttachmentParsedService chatAttachmentParsedService;

    @Override
    public Page<Message> listBySessionIdPage(Long sessionId, int pageNum, int pageSize) {
        Page<Message> page = baseMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getSessionId, sessionId)
                        .orderByDesc(Message::getCreateTime));
        // 1. 历史列表场景剥离 tool_result.result 正文，仅保留长度提示，
        //    前端展开「查看结果」时调 getToolResultDetail 按需拉取完整 JSON
        for (Message msg : page.getRecords()) {
            msg.setToolEvents(compactToolEventsForList(msg.getToolEvents()));
        }
        return page;
    }

    /**
     * 历史列表 toolEvents 瘦身：剥离所有 type=tool_result 事件的 result 正文，仅保留长度提示。
     * 前端展开时按 messageId+index 调 getToolResultDetail 按需拉取完整内容
     */
    private String compactToolEventsForList(String toolEventsJson) {
        if (toolEventsJson == null || toolEventsJson.isBlank()) {
            return toolEventsJson;
        }
        try {
            JsonNode root = objectMapper.readTree(toolEventsJson);
            if (!root.isArray() || root.isEmpty()) {
                return toolEventsJson;
            }
            boolean changed = false;
            List<JsonNode> output = new ArrayList<>();
            for (JsonNode evt : root) {
                if (!"tool_result".equals(evt.path("type").asText())) {
                    output.add(evt);
                    continue;
                }
                JsonNode resultNode = evt.get("result");
                String result = resultNode != null ? resultNode.asText("") : "";
                // 1. 删除 result 正文，仅保留长度供前端显示大小提示
                com.fasterxml.jackson.databind.node.ObjectNode mutable = evt.deepCopy();
                mutable.remove("result");
                if (!result.isEmpty()) {
                    mutable.put("resultTotalLength", result.length());
                }
                output.add(mutable);
                changed = true;
            }
            if (!changed) {
                return toolEventsJson;
            }
            return objectMapper.writeValueAsString(output);
        } catch (Exception e) {
            log.warn("[Message] 瘦身 toolEvents 失败：{}", e.getMessage());
            return toolEventsJson;
        }
    }

    @Override
    public String getToolResultDetail(Long toolCallId) {
        // 直接按主键查 tool_calls 表：toolCallId 在工具调用时预生成，同时写入 tool_events 事件和 tool_calls 主键
        if (toolCallId == null || toolCallId <= 0) {
            return null;
        }
        com.lightbot.entity.ToolCall call = toolCallMapper.selectById(toolCallId);
        return call != null ? call.getToolOutput() : null;
    }

    @Override
    public List<Message> listBySessionId(Long sessionId) {
        return list(new LambdaQueryWrapper<Message>()
                .eq(Message::getSessionId, sessionId)
                .orderByAsc(Message::getCreateTime));
    }

    @Override
    public List<Message> listAssistantByRequestId(Long sessionId, String requestId) {
        if (sessionId == null || requestId == null || requestId.isBlank()) {
            return List.of();
        }
        return baseMapper.selectAssistantByRequestId(sessionId, requestId);
    }

    @Override
    public Message getUserByRequestId(Long sessionId, String requestId) {
        if (sessionId == null || requestId == null || requestId.isBlank()) {
            return null;
        }
        return baseMapper.selectUserByRequestId(sessionId, requestId);
    }

    @Override
    public Message getPreviousUserMessage(Long sessionId, Long beforeMessageId) {
        if (sessionId == null || beforeMessageId == null) {
            return null;
        }
        return baseMapper.selectPreviousUserMessage(sessionId, beforeMessageId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteBySessionId(Long sessionId) {
        // 1. 加载会话下所有消息，清理关联的 MinIO 资源
        List<Message> messages = listBySessionId(sessionId);
        cleanupMinioResources(messages);
        // 2. 批量删除关联的 ToolCall 记录
        List<Long> messageIds = messages.stream().map(Message::getId).toList();
        if (!messageIds.isEmpty()) {
            toolCallMapper.deleteByMessageIds(messageIds);
            // 3. 删除关联的反馈记录
            messageFeedbackMapper.delete(new LambdaQueryWrapper<MessageFeedback>()
                    .in(MessageFeedback::getMessageId, messageIds));
        }
        // 4. 删除消息
        remove(new LambdaQueryWrapper<Message>().eq(Message::getSessionId, sessionId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMessage(Long messageId, Long sessionId) {
        // 1. 加载消息，清理关联的 MinIO 资源
        Message message = getOne(new LambdaQueryWrapper<Message>()
                .eq(Message::getId, messageId)
                .eq(Message::getSessionId, sessionId));
        if (message != null) {
            cleanupMinioResources(List.of(message));
        }
        // 2. 删除关联的 ToolCall 记录
        toolCallMapper.deleteByMessageIds(List.of(messageId));
        // 3. 删除关联的反馈记录
        messageFeedbackMapper.delete(new LambdaQueryWrapper<MessageFeedback>()
                .eq(MessageFeedback::getMessageId, messageId));
        // 4. 删除消息
        remove(new LambdaQueryWrapper<Message>()
                .eq(Message::getId, messageId)
                .eq(Message::getSessionId, sessionId));
    }

    @Override
    public Page<Message> searchBySessionId(Long sessionId, String keyword, int pageNum, int pageSize) {
        return baseMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getSessionId, sessionId)
                        .like(Message::getContent, keyword)
                        .orderByDesc(Message::getCreateTime));
    }

    @Override
    public List<ConversationSearchResultVO> searchConversations(Long userId, String keyword, int limit) {
        // 1. 关键词归一化：trim 后为空直接返回空列表（避免 ILIKE '%%' 全表扫描）
        String normalized = keyword == null ? "" : keyword.trim();
        if (normalized.isEmpty() || userId == null) {
            return List.of();
        }
        // 2. 关键词长度限制：避免超长关键字拖慢 ILIKE
        String boundedKeyword = normalized.length() > 64 ? normalized.substring(0, 64) : normalized;
        // 3. 查询数量兜底
        int boundedLimit = Math.max(1, Math.min(limit, 50));
        // 4. 查询并裁剪 snippet 到 200 字符
        List<ConversationSearchResultVO> raw = baseMapper.searchConversationsByContent(userId, boundedKeyword, boundedLimit);
        for (ConversationSearchResultVO item : raw) {
            item.setSnippet(truncateSnippet(item.getSnippet(), boundedKeyword, 200));
        }
        return raw;
    }

    /**
     * 裁剪 snippet：保留关键字上下文窗口，最多 maxLen 字符
     */
    private String truncateSnippet(String content, String keyword, int maxLen) {
        if (content == null) {
            return "";
        }
        if (content.length() <= maxLen) {
            return content;
        }
        // 1. 大小写不敏感定位关键字位置，命中则截取上下文窗口
        int idx = content.toLowerCase().indexOf(keyword.toLowerCase());
        if (idx < 0) {
            return content.substring(0, maxLen) + "…";
        }
        // 2. 上下文窗口：关键字前 60 字符 + 关键字 + 剩余到 maxLen
        int start = Math.max(0, idx - 60);
        int end = Math.min(content.length(), start + maxLen);
        String snippet = content.substring(start, end);
        String prefix = start > 0 ? "…" : "";
        String suffix = end < content.length() ? "…" : "";
        return prefix + snippet + suffix;
    }

    @Override
    public void toggleStar(Long messageId) {
        Message msg = getById(messageId);
        if (msg == null) {
            throw new BizException(ErrorCode.MESSAGE_NOT_FOUND);
        }
        msg.setStarred(!Boolean.TRUE.equals(msg.getStarred()));
        updateById(msg);
    }

    @Override
    public Page<Message> listStarred(int pageNum, int pageSize) {
        return page(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getStarred, true)
                        .orderByDesc(Message::getCreateTime));
    }

    /**
     * 清理消息关联的 MinIO 资源
     * <p>包含两类：AI 生图生成的图片、用户上传的附件</p>
     */
    private void cleanupMinioResources(List<Message> messages) {
        for (Message msg : messages) {
            List<String> imagePaths = extractImageFilePaths(msg.getMetadata());
            for (String path : imagePaths) {
                safeDeleteObject(path);
            }
            for (ChatAttachmentDTO att : parseAttachmentsFromMetadata(msg.getMetadata())) {
                deleteMessageAttachment(msg.getSessionId(), att);
            }
        }
    }

    /**
     * 删除消息关联附件的存储对象（MinIO + 文档解析缓存）。
     * 不依赖 ChatAttachmentService，避免与 ChatSessionService 形成循环依赖。
     */
    private void deleteMessageAttachment(Long sessionId, ChatAttachmentDTO attachment) {
        if (attachment == null || attachment.getObjectKey() == null || attachment.getObjectKey().isBlank()) {
            return;
        }
        safeDeleteObject(attachment.getObjectKey());
        if ("document".equals(attachment.getType())) {
            Long agentId = SessionStoragePath.extractAgentIdFromTempObjectKey(attachment.getObjectKey());
            chatAttachmentParsedService.deleteParsed(attachment, sessionId, agentId);
        }
    }

    private void safeDeleteObject(String objectKey) {
        try {
            minioUtil.delete(objectKey);
            log.info("[Message] 清理MinIO资源: path={}", objectKey);
        } catch (Exception e) {
            log.warn("[Message] 清理MinIO资源失败: path={}, error={}", objectKey, e.getMessage());
        }
    }

    /**
     * 从消息 metadata 中提取 image_generation 工具生成的图片 file_path 列表
     */
    private List<String> extractImageFilePaths(String metadata) {
        List<String> paths = new ArrayList<>();
        if (metadata == null || metadata.isBlank()) {
            return paths;
        }
        try {
            JsonNode root = objectMapper.readTree(metadata);
            JsonNode toolEvents = root.get("toolEvents");
            if (toolEvents == null || !toolEvents.isArray()) {
                return paths;
            }
            for (JsonNode event : toolEvents) {
                if (!"tool_result".equals(event.path("type").asText())) {
                    continue;
                }
                if (!"image_generation".equals(event.path("toolName").asText())) {
                    continue;
                }
                String result = event.path("result").asText(null);
                if (result == null || result.isBlank()) {
                    continue;
                }
                try {
                    JsonNode resultNode = objectMapper.readTree(result);
                    String filePath = resultNode.path("file_path").asText(null);
                    if (filePath != null && !filePath.isBlank()) {
                        paths.add(filePath);
                    }
                } catch (Exception ignored) {
                    // result 不是合法 JSON，跳过
                }
            }
        } catch (Exception e) {
            log.warn("[Message] 解析metadata失败: {}", e.getMessage());
        }
        return paths;
    }

    /**
     * 从消息 metadata 中提取用户上传附件列表
     */
    private List<ChatAttachmentDTO> parseAttachmentsFromMetadata(String metadata) {
        List<ChatAttachmentDTO> result = new ArrayList<>();
        if (metadata == null || metadata.isBlank()) {
            return result;
        }
        try {
            JsonNode root = objectMapper.readTree(metadata);
            JsonNode attachments = root.get("attachments");
            if (attachments == null || !attachments.isArray()) {
                return result;
            }
            for (JsonNode att : attachments) {
                result.add(objectMapper.convertValue(att, ChatAttachmentDTO.class));
            }
        } catch (Exception e) {
            log.warn("[Message] 解析附件metadata失败: {}", e.getMessage());
        }
        return result;
    }
}
