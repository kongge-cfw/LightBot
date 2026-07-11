package com.lightbot.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM 消息上下文准备：估算字符数、裁剪工具轮次、规范化空内容。
 * <p>SubAgent 与主 Chat 工具循环共用，避免工具结果撑爆模型输入上限。</p>
 */
@Slf4j
public final class ChatMessageContextUtil {

    /** 主 Chat 默认工具上下文字符上限 */
    public static final int DEFAULT_MAX_TOOL_CONTEXT_CHARS = 60_000;
    /** DashScope 输入上限 202745，留安全余量 */
    public static final int DASHSCOPE_SAFE_INPUT_CHARS = 180_000;
    /** 单条工具结果最大字符数 */
    public static final int MAX_SINGLE_TOOL_RESULT_CHARS = 50_000;
    /** 保留最近 N 轮完整工具调用 */
    public static final int DEFAULT_TOOL_ROUNDS_TO_KEEP = 2;

    private static final String EMPTY_TASK_PLACEHOLDER = "（无任务描述）";
    private static final String EMPTY_SYSTEM_PLACEHOLDER = "You are a helpful assistant.";
    private static final String TOOL_CALL_PLACEHOLDER = " ";
    private static final String EMPTY_TOOL_RESULT = "{}";

    private ChatMessageContextUtil() {
    }

    /**
     * 估算单条消息占用的字符数（含 ToolResponseMessage 的 responseData）
     *
     * @param msg Spring AI Message
     * @return 字符数
     */
    public static int estimateMessageChars(Message msg) {
        if (msg == null) {
            return 0;
        }
        if (msg instanceof ToolResponseMessage trm) {
            int total = 0;
            for (ToolResponseMessage.ToolResponse tr : trm.getResponses()) {
                if (tr.responseData() != null) {
                    total += tr.responseData().length();
                }
            }
            return total;
        }
        String text = msg.getText();
        return text != null ? text.length() : 0;
    }

    /**
     * 估算消息列表总字符数
     *
     * @param messages 消息列表
     * @return 总字符数
     */
    public static int estimateTotalChars(List<Message> messages) {
        int total = 0;
        for (Message msg : messages) {
            total += estimateMessageChars(msg);
        }
        return total;
    }

    /**
     * 规范化消息内容，避免 DashScope 等提供商因空 content 报 InvalidParameter
     *
     * @param messages 消息列表（原地替换）
     */
    public static void normalizeMessagesForLlm(List<Message> messages) {
        for (int i = 0; i < messages.size(); i++) {
            Message msg = messages.get(i);
            if (msg instanceof SystemMessage sm) {
                String text = sm.getText();
                if (text == null || text.isBlank()) {
                    messages.set(i, new SystemMessage(EMPTY_SYSTEM_PLACEHOLDER));
                }
            } else if (msg instanceof UserMessage um) {
                String text = um.getText();
                if (text == null || text.isBlank()) {
                    messages.set(i, new UserMessage(EMPTY_TASK_PLACEHOLDER));
                }
            } else if (msg instanceof AssistantMessage am) {
                String text = am.getText();
                if ((text == null || text.isBlank()) && am.hasToolCalls()) {
                    messages.set(i, AssistantMessage.builder()
                            .content(TOOL_CALL_PLACEHOLDER)
                            .toolCalls(am.getToolCalls())
                            .build());
                }
            } else if (msg instanceof ToolResponseMessage trm) {
                List<ToolResponseMessage.ToolResponse> normalized = new ArrayList<>();
                for (ToolResponseMessage.ToolResponse tr : trm.getResponses()) {
                    normalized.add(new ToolResponseMessage.ToolResponse(
                            tr.id(), tr.name(), ensureNonEmptyToolResult(tr.responseData())));
                }
                messages.set(i, ToolResponseMessage.builder().responses(normalized).build());
            }
        }
    }

    /**
     * 工具结果不得为空，否则部分模型 API 会拒绝
     *
     * @param result 工具返回
     * @return 非空字符串
     */
    public static String ensureNonEmptyToolResult(String result) {
        if (result == null || result.isBlank()) {
            return EMPTY_TOOL_RESULT;
        }
        return result;
    }

    /**
     * 截断单条工具结果
     *
     * @param result 工具返回
     * @param maxLen 最大长度
     * @return 截断后的结果
     */
    public static String capToolResult(String result, int maxLen) {
        String safe = ensureNonEmptyToolResult(result);
        if (safe.length() <= maxLen) {
            return safe;
        }
        return safe.substring(0, maxLen) + "\n...(工具结果已截断，共 " + safe.length() + " 字符)";
    }

    /**
     * 使用默认阈值裁剪工具调用上下文
     *
     * @param messages 消息列表（原地修改）
     */
    public static void trimToolCallContext(List<Message> messages) {
        trimToolCallContext(messages, DEFAULT_MAX_TOOL_CONTEXT_CHARS, DEFAULT_TOOL_ROUNDS_TO_KEEP);
    }

    /**
     * 工具调用上下文裁剪：超过阈值时压缩早期工具轮次为摘要 SystemMessage
     *
     * @param messages      消息列表（原地修改）
     * @param maxChars      字符上限
     * @param roundsToKeep  保留最近完整工具轮次数
     */
    public static void trimToolCallContext(List<Message> messages, int maxChars, int roundsToKeep) {
        int totalChars = estimateTotalChars(messages);
        if (totalChars <= maxChars) {
            return;
        }

        List<int[]> rounds = new ArrayList<>();
        for (int i = 0; i < messages.size() - 1; i++) {
            Message cur = messages.get(i);
            if (cur instanceof AssistantMessage am && am.hasToolCalls()) {
                Message next = messages.get(i + 1);
                if (next instanceof ToolResponseMessage) {
                    rounds.add(new int[]{i, i + 1});
                    i++;
                }
            }
        }

        if (rounds.size() <= roundsToKeep) {
            // 轮次不多但总字符仍超限：截断每条 ToolResponse 的 responseData
            shrinkToolResponsePayloads(messages, maxChars);
            return;
        }

        int compressUpTo = rounds.size() - roundsToKeep;
        int removeStart = rounds.get(0)[0];
        int removeEnd = rounds.get(compressUpTo - 1)[1];

        int toolCount = 0;
        for (int r = 0; r < compressUpTo; r++) {
            AssistantMessage am = (AssistantMessage) messages.get(rounds.get(r)[0]);
            toolCount += am.getToolCalls().size();
        }

        String summary = "[已省略第 1-" + compressUpTo + " 轮工具调用详情，共执行 "
                + toolCount + " 个工具，上下文已压缩]";
        List<Message> trimmed = new ArrayList<>(messages);
        for (int i = removeEnd; i >= removeStart; i--) {
            trimmed.remove(i);
        }
        trimmed.add(removeStart, new SystemMessage(summary));
        messages.clear();
        messages.addAll(trimmed);

        log.info("[ContextTrim] 压缩了 {} 轮工具调用（{} 个工具），消息字符 {} → ~{}",
                compressUpTo, toolCount, totalChars, estimateTotalChars(messages));

        if (estimateTotalChars(messages) > maxChars) {
            shrinkToolResponsePayloads(messages, maxChars);
        }
    }

    private static void shrinkToolResponsePayloads(List<Message> messages, int maxChars) {
        int total = estimateTotalChars(messages);
        if (total <= maxChars) {
            return;
        }
        // 从最早的 ToolResponse 开始截断，直到低于上限
        for (int i = 0; i < messages.size() && estimateTotalChars(messages) > maxChars; i++) {
            if (!(messages.get(i) instanceof ToolResponseMessage trm)) {
                continue;
            }
            List<ToolResponseMessage.ToolResponse> shrunk = new ArrayList<>();
            for (ToolResponseMessage.ToolResponse tr : trm.getResponses()) {
                int budget = Math.max(512, maxChars / Math.max(1, trm.getResponses().size()));
                shrunk.add(new ToolResponseMessage.ToolResponse(
                        tr.id(), tr.name(), capToolResult(tr.responseData(), budget)));
            }
            messages.set(i, ToolResponseMessage.builder().responses(shrunk).build());
        }
        log.info("[ContextTrim] 截断工具结果后字符数: {}", estimateTotalChars(messages));
    }
}
