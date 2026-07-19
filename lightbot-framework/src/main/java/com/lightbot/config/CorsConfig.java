package com.lightbot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 跨域配置：origin 走白名单，禁止使用通配符 + allowCredentials 组合。
 *
 * @author finch
 * @since 2026-07-19
 */
@Configuration
public class CorsConfig {

    /**
     * CORS 配置属性绑定
     */
    @Component
    @ConfigurationProperties(prefix = "lightbot.cors")
    public static class CorsProperties {
        /**
         * 允许的前端源（白名单），多个用逗号分隔
         */
        private List<String> allowedOrigins = Collections.singletonList("http://localhost:5173");
        /**
         * 允许的 HTTP 方法
         */
        private String allowedMethods = "GET,POST,PUT,DELETE,OPTIONS";
        /**
         * 是否允许携带凭证
         */
        private boolean allowCredentials = true;
        /**
         * 预检请求缓存时间（秒）
         */
        private long maxAge = 3600L;

        public List<String> getAllowedOrigins() { return allowedOrigins; }
        public void setAllowedOrigins(List<String> allowedOrigins) { this.allowedOrigins = allowedOrigins; }
        public String getAllowedMethods() { return allowedMethods; }
        public void setAllowedMethods(String allowedMethods) { this.allowedMethods = allowedMethods; }
        public boolean isAllowCredentials() { return allowCredentials; }
        public void setAllowCredentials(boolean allowCredentials) { this.allowCredentials = allowCredentials; }
        public long getMaxAge() { return maxAge; }
        public void setMaxAge(long maxAge) { this.maxAge = maxAge; }
    }

    /**
     * origin 白名单（仅 @Value 兜底，实际值由 CorsProperties 提供）
     */
    @Value("${lightbot.cors.allowed-origins:http://localhost:5173,http://localhost:3000}")
    private String allowedOriginsCsv;

    @Bean
    public CorsFilter corsFilter(CorsProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        // 白名单 origin，禁止 "*"（与 allowCredentials=true 不兼容且存在安全风险）
        List<String> origins = parseOrigins(properties.getAllowedOrigins());
        for (String origin : origins) {
            config.addAllowedOrigin(origin.trim());
        }
        // JSESSIONID / Authorization 等凭证必须配合具体 origin，不能用 *
        if (properties.isAllowCredentials()) {
            config.setAllowCredentials(true);
        }
        // 方法白名单
        for (String method : properties.getAllowedMethods().split(",")) {
            config.addAllowedMethod(method.trim());
        }
        // 头白名单：仅放行常见请求头，避免 X-* 自定义头穿透
        config.addAllowedHeader("Authorization");
        config.addAllowedHeader("Content-Type");
        config.addAllowedHeader("Accept");
        config.addAllowedHeader("X-Requested-With");
        config.addAllowedHeader("X-Trace-Id");
        config.addAllowedHeader("X-Request-Id");
        // 暴露给前端 JS 可读的响应头
        config.addExposedHeader("X-Trace-Id");
        config.addExposedHeader("X-Request-Id");
        config.setMaxAge(properties.getMaxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }

    /**
     * 兼容 YAML List 形式和 CSV 字符串形式两种写法
     */
    private List<String> parseOrigins(List<String> fromProps) {
        if (fromProps != null && !fromProps.isEmpty()) {
            return fromProps;
        }
        if (allowedOriginsCsv == null || allowedOriginsCsv.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(allowedOriginsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
