package com.lightbot.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.Result;
import com.lightbot.entity.ApiKey;
import com.lightbot.enums.ErrorCode;
import com.lightbot.service.ApiKeyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * API Key 认证拦截器
 * <p>在 Sa-Token 拦截器之前执行，识别 lbkey_ 前缀的 Bearer Token 并走 API Key 认证</p>
 * <p>企业 API Key 仅允许对话入口（POST /api/chat、POST /api/chat/stream），不可访问控制台资产接口</p>
 *
 * @author finch
 * @since 2026-06-25
 */
@Component
@RequiredArgsConstructor
public class ApiKeyAuthInterceptor implements HandlerInterceptor {

    public static final String ATTR_API_KEY_USER_ID = "apiKeyUserId";
    public static final String ATTR_API_KEY_ENTITY = "apiKeyEntity";

    private final ApiKeyService apiKeyService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return true;
        }

        String token = auth.substring(7).trim();
        if (!token.startsWith("lbkey_")) {
            return true;
        }

        // 1. 企业 API Key 仅放行对话接口，避免被当作全站登录态
        if (!isChatApiPath(request)) {
            writeError(response, HttpServletResponse.SC_FORBIDDEN,
                    ErrorCode.API_KEY_PATH_FORBIDDEN.getCode(),
                    ErrorCode.API_KEY_PATH_FORBIDDEN.getMessage());
            return false;
        }

        // 2. API Key 认证
        ApiKey apiKey = apiKeyService.authenticateWithDetails(token);
        if (apiKey == null) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    ErrorCode.API_KEY_INVALID.getCode(), "API Key无效或已过期");
            return false;
        }

        // 3. 请求频率限制
        if (!apiKeyService.checkRateLimit(apiKey.getId(), apiKey.getRateLimit())) {
            writeError(response, HttpStatus.TOO_MANY_REQUESTS.value(),
                    10005, "请求过于频繁，限制 " + apiKey.getRateLimit() + " 次/分钟");
            return false;
        }

        // 4. Agent 作用域在 InitMiddleware 按请求体 agentId 校验（query 参数不可靠）
        request.setAttribute(ATTR_API_KEY_USER_ID, com.lightbot.constant.EnterpriseActors.API_KEY);
        request.setAttribute(ATTR_API_KEY_ENTITY, apiKey);
        return true;
    }

    /**
     * 企业 API Key 允许的对话相关入口（不含会话管理/资产 CRUD）
     */
    private boolean isChatApiPath(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String uri = request.getRequestURI();
        return "/api/chat".equals(uri)
                || "/api/chat/stream".equals(uri)
                || "/api/chat/stream/stop".equals(uri)
                || "/api/chat/reconnect".equals(uri);
    }

    private void writeError(HttpServletResponse response, int status, int code, String message) throws Exception {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(code, message)));
    }
}
