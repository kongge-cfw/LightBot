package com.lightbot.util;

import com.lightbot.constant.ToolResultPrefixes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * 工具入参 JSON Schema 校验装饰器
 * <p>包装原始 {@link ToolCallback}，在 call 前用 {@link ToolInputSchemaValidator} 校验入参，
 * 校验失败时返回结构化错误 JSON 回喂给 LLM 触发重试，避免非法参数进入工具导致 NPE 或脏数据</p>
 *
 * @author finch
 * @since 2026-07-20
 */
@Slf4j
public class ValidatingToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final ToolInputSchemaValidator validator;

    public ValidatingToolCallback(ToolCallback delegate, ToolInputSchemaValidator validator) {
        this.delegate = delegate;
        this.validator = validator;
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
        // 1. 校验入参，非法时直接返回错误 JSON（不抛异常，避免被外层兜底成 500）
        String inputSchema = delegate.getToolDefinition().inputSchema();
        try {
            validator.validate(inputSchema, toolInput);
        } catch (ToolValidationException e) {
            String toolName = delegate.getToolDefinition().name();
            log.warn("[ToolValidator] 工具入参校验失败: tool={}, reason={}", toolName, e.getMessage());
            return ToolResultPrefixes.failureJson(e.getMessage());
        }
        // 2. 校验通过，委托原始回调执行
        return toolContext != null
                ? delegate.call(toolInput, toolContext)
                : delegate.call(toolInput);
    }
}
