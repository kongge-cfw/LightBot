package com.lightbot.util;

import cn.dev33.satoken.stp.StpUtil;
import com.lightbot.constant.ToolResultPrefixes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.Map;

/**
 * 工具维度限流装饰器
 * <p>包装原始 {@link ToolCallback}，在 call 前用 {@link ToolRateLimiter} 按
 * (userId, toolName) 维度判定配额；超限时返回结构化错误 JSON 回喂给 LLM，
 * 让模型感知"该工具已被限流，请改用其他方式"，避免硬抛异常被外层兜底成 500。</p>
 *
 * @author finch
 * @since 2026-07-21
 */
@Slf4j
public class RateLimitedToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final ToolRateLimiter rateLimiter;
    private final String rateLimitConfig;

    public RateLimitedToolCallback(ToolCallback delegate, ToolRateLimiter rateLimiter, String rateLimitConfig) {
        this.delegate = delegate;
        this.rateLimiter = rateLimiter;
        this.rateLimitConfig = rateLimitConfig;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        // 1. 解析当前用户 ID：优先从 ToolContext 取，其次从 SaToken 会话取（系统调用降级为 0）
        Long userId = resolveUserId(toolContext);
        String toolName = delegate.getToolDefinition().name();

        // 2. 限流判定：未开启/配置非法时直接放行
        if (!rateLimiter.tryAcquire(userId, toolName, rateLimitConfig)) {
            String msg = "工具调用已被限流，请稍后重试或改用其他方式";
            log.warn("[ToolRateLimit] 拦截调用: userId={}, tool={}", userId, toolName);
            return ToolResultPrefixes.failureJson(msg);
        }

        // 3. 通过限流，委托原回调执行
        return toolContext != null
                ? delegate.call(toolInput, toolContext)
                : delegate.call(toolInput);
    }

    private Long resolveUserId(ToolContext toolContext) {
        if (toolContext != null) {
            Map<String, Object> ctx = toolContext.getContext();
            Object uid = ctx != null ? ctx.get("userId") : null;
            if (uid instanceof Number n) {
                return n.longValue();
            }
            if (uid instanceof String s && !s.isBlank()) {
                try { return Long.parseLong(s); } catch (NumberFormatException ignored) {}
            }
        }
        try {
            return StpUtil.getLoginIdAsLong();
        } catch (Exception e) {
            // 非会话上下文（定时任务、内部调用）按系统用户处理
            return 0L;
        }
    }
}
