package com.lightbot.controller;

import com.lightbot.dto.ApiKeyCreateDTO;
import com.lightbot.entity.ApiKey;
import com.lightbot.service.ApiKeyService;
import com.lightbot.service.UserService;
import com.lightbot.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 企业 API Key 管理接口（仅管理员）
 *
 * @author finch
 * @since 2026-06-25
 */
@Tag(name = "企业 API Key 管理")
@RestController
@RequestMapping("/api/api-keys")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyService apiKeyService;
    private final UserService userService;

    @GetMapping
    @Operation(summary = "查询企业 API Key 列表")
    public Result<List<ApiKey>> list() {
        userService.checkAdmin();
        return Result.ok(apiKeyService.listAll());
    }

    @PostMapping
    @Operation(summary = "创建企业 API Key")
    public Result<Map<String, Object>> create(@Valid @RequestBody ApiKeyCreateDTO request) {
        userService.checkAdmin();
        // userId 记为创建人（管理员）审计字段，不作为访问隔离维度
        long userId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong();
        return Result.ok(apiKeyService.createApiKey(userId, request.getName(), request.getPermissions(),
                request.getExpiresAt(), request.getAgentIds(), request.getRateLimit(), request.getDailyQuota()));
    }

    @PatchMapping("/{id}/toggle")
    @Operation(summary = "启用/禁用企业 API Key")
    public Result<Void> toggle(@PathVariable Long id) {
        userService.checkAdmin();
        long userId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong();
        apiKeyService.toggleEnabled(id, userId);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除企业 API Key")
    public Result<Void> delete(@PathVariable Long id) {
        userService.checkAdmin();
        long userId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong();
        apiKeyService.deleteApiKey(id, userId);
        return Result.ok();
    }

    @PostMapping("/{id}/regenerate")
    @Operation(summary = "重新生成企业 API Key")
    public Result<Map<String, Object>> regenerate(@PathVariable Long id) {
        userService.checkAdmin();
        long userId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong();
        return Result.ok(apiKeyService.regenerateApiKey(id, userId));
    }
}
