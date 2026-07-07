package com.lightbot.util;

/**
 * 模型调用异常分类工具
 * <p>将底层模型/网络异常翻译为用户友好的中文提示与错误码，
 * 供对话、Prompt 调试等多处复用，避免各处重复维护错误映射。</p>
 *
 * @author finch
 * @since 2026-07-07
 */
public final class ModelErrorClassifier {

    private static final int MAX_MESSAGE_LENGTH = 100;

    private ModelErrorClassifier() {
    }

    /**
     * 将异常分类为用户友好的错误提示
     *
     * @param e 异常
     * @return 用户友好的中文错误提示
     */
    public static String classifyMessage(Throwable e) {
        if (e == null) {
            return "模型调用异常：未知错误";
        }
        String msg = e.getMessage();
        if (msg == null) {
            msg = e.getClass().getSimpleName();
        }

        // 网络超时
        if (msg.contains("timeout") || msg.contains("timed out") || msg.contains("Timeout")) {
            return "模型响应超时，请稍后重试";
        }
        // 限流
        if (msg.contains("429") || msg.contains("rate") || msg.contains("Rate")) {
            return "模型请求被限流，请稍后重试";
        }
        // 认证失败
        if (msg.contains("401") || msg.contains("403") || msg.contains("Unauthorized") || msg.contains("Forbidden")) {
            return "模型认证失败，请检查 API Key 配置";
        }
        // Token 超限
        if (msg.contains("token") && (msg.contains("limit") || msg.contains("exceed") || msg.contains("maximum"))) {
            return "上下文长度超限，请缩短对话后重试";
        }
        // 内容审核
        if (msg.contains("content_filter") || msg.contains("safety") || msg.contains("blocked")) {
            return "内容触发安全策略，请调整输入后重试";
        }
        return "模型调用异常：" + (msg.length() > MAX_MESSAGE_LENGTH ? msg.substring(0, MAX_MESSAGE_LENGTH) + "..." : msg);
    }

    /**
     * 将异常分类为错误码
     *
     * @param e 异常
     * @return 错误码（TIMEOUT/RATE_LIMITED/AUTH_ERROR/TOKEN_LIMIT/CONTENT_FILTER/LLM_ERROR/UNKNOWN）
     */
    public static String classifyCode(Throwable e) {
        if (e == null) {
            return "UNKNOWN";
        }
        String msg = e.getMessage();
        if (msg == null) {
            return "UNKNOWN";
        }
        if (msg.contains("timeout") || msg.contains("timed out") || msg.contains("Timeout")) {
            return "TIMEOUT";
        }
        if (msg.contains("429") || msg.contains("rate") || msg.contains("Rate")) {
            return "RATE_LIMITED";
        }
        if (msg.contains("401") || msg.contains("403")) {
            return "AUTH_ERROR";
        }
        if (msg.contains("token") && (msg.contains("limit") || msg.contains("exceed"))) {
            return "TOKEN_LIMIT";
        }
        if (msg.contains("content_filter") || msg.contains("safety")) {
            return "CONTENT_FILTER";
        }
        return "LLM_ERROR";
    }
}
