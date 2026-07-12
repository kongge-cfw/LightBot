package com.lightbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.common.BizException;
import com.lightbot.config.RedisCacheConfig;
import com.lightbot.dto.SubAgentRequestDTO;
import com.lightbot.entity.SubAgent;
import com.lightbot.enums.ErrorCode;
import com.lightbot.mapper.SubAgentMapper;
import com.lightbot.service.SubAgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.lightbot.service.ToolService;

import java.io.Serializable;
import java.util.List;

/**
 * SubAgent 服务实现
 *
 * @author finch
 * @since 2026-05-24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubAgentServiceImpl extends ServiceImpl<SubAgentMapper, SubAgent>
        implements SubAgentService {

    private static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 10;
    private static final int DEFAULT_READ_TIMEOUT_SECONDS = 120;
    private static final int DEFAULT_MODEL_RETRY_TIMES = 1;

    private final ObjectMapper objectMapper;
    private final ToolService toolService;

    @Override
    @Cacheable(value = RedisCacheConfig.CACHE_SUBAGENT, key = "#id", unless = "#result == null")
    public SubAgent getById(Serializable id) {
        return super.getById(id);
    }

    @Override
    @CacheEvict(value = RedisCacheConfig.CACHE_SUBAGENT, key = "#entity.id")
    public boolean updateById(SubAgent entity) {
        return super.updateById(entity);
    }

    @Override
    @CacheEvict(value = RedisCacheConfig.CACHE_SUBAGENT, allEntries = true)
    public SubAgent create(SubAgentRequestDTO request) {
        // 1. 校验名称唯一性
        long count = count(new LambdaQueryWrapper<SubAgent>().eq(SubAgent::getName, request.getName()));
        if (count > 0) {
            throw new BizException(ErrorCode.SUBAGENT_NAME_EXISTS);
        }
        // 2. 构建实体
        SubAgent subAgent = new SubAgent();
        subAgent.setName(request.getName());
        subAgent.setDisplayName(request.getDisplayName());
        subAgent.setDescription(request.getDescription());
        subAgent.setSystemPrompt(request.getSystemPrompt());
        subAgent.setToolIds(toJson(request.getToolIds()));
        subAgent.setModelId(request.resolveProviderId());
        subAgent.setLlmModel(request.getLlmModel());
        subAgent.setConnectTimeoutSeconds(resolveConnectTimeoutSeconds(request.getConnectTimeoutSeconds()));
        subAgent.setReadTimeoutSeconds(resolveReadTimeoutSeconds(request.getReadTimeoutSeconds()));
        subAgent.setModelRetryTimes(resolveModelRetryTimes(request.getModelRetryTimes()));
        subAgent.setEnabled(request.getEnabled() != null ? (request.getEnabled() ? 1 : 0) : 1);
        subAgent.setIsBuiltin(0);
        save(subAgent);
        return subAgent;
    }

    @Override
    @CacheEvict(value = RedisCacheConfig.CACHE_SUBAGENT, key = "#request.id")
    public SubAgent update(SubAgentRequestDTO request) {
        // 1. 校验存在性
        SubAgent subAgent = getById(request.getId());
        if (subAgent == null) {
            throw new BizException(ErrorCode.SUBAGENT_NOT_FOUND);
        }
        // 2. 内置不可编辑
        if (Integer.valueOf(1).equals(subAgent.getIsBuiltin())) {
            throw new BizException("内置 SubAgent 不可编辑");
        }
        // 3. 名称变更时校验唯一性
        if (!subAgent.getName().equals(request.getName())) {
            long count = count(new LambdaQueryWrapper<SubAgent>().eq(SubAgent::getName, request.getName()));
            if (count > 0) {
                throw new BizException(ErrorCode.SUBAGENT_NAME_EXISTS);
            }
        }
        // 4. 清理悬空工具引用后更新字段（tool_ids 走 updateById 以应用 JSONB TypeHandler；model 为 null 时单独清空）
        request.setToolIds(cleanStaleToolIds(request.getToolIds()));
        subAgent.setName(request.getName());
        subAgent.setDisplayName(request.getDisplayName());
        subAgent.setDescription(request.getDescription());
        subAgent.setSystemPrompt(request.getSystemPrompt());
        subAgent.setToolIds(toJson(request.getToolIds()));
        subAgent.setConnectTimeoutSeconds(resolveConnectTimeoutSeconds(request.getConnectTimeoutSeconds()));
        subAgent.setReadTimeoutSeconds(resolveReadTimeoutSeconds(request.getReadTimeoutSeconds()));
        subAgent.setModelRetryTimes(resolveModelRetryTimes(request.getModelRetryTimes()));
        if (request.getEnabled() != null) {
            subAgent.setEnabled(request.getEnabled() ? 1 : 0);
        }
        Long providerId = request.resolveProviderId();
        if (providerId != null) {
            subAgent.setModelId(providerId);
            subAgent.setLlmModel(request.getLlmModel());
            if (!updateById(subAgent)) {
                throw new BizException(ErrorCode.SUBAGENT_NOT_FOUND);
            }
        } else {
            if (!updateById(subAgent)) {
                throw new BizException(ErrorCode.SUBAGENT_NOT_FOUND);
            }
            update(new LambdaUpdateWrapper<SubAgent>()
                    .eq(SubAgent::getId, subAgent.getId())
                    .set(SubAgent::getModelId, null)
                    .set(SubAgent::getLlmModel, null));
        }
        return getById(subAgent.getId());
    }

    @Override
    public Page<SubAgent> listPage(int pageNum, int pageSize, String keyword, Boolean isBuiltin) {
        LambdaQueryWrapper<SubAgent> wrapper = new LambdaQueryWrapper<SubAgent>()
                .orderByDesc(SubAgent::getCreateTime);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(SubAgent::getName, keyword)
                    .or().like(SubAgent::getDisplayName, keyword));
        }
        if (isBuiltin != null) {
            wrapper.eq(SubAgent::getIsBuiltin, isBuiltin ? 1 : 0);
        }
        return baseMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public SubAgent getByName(String name) {
        return baseMapper.selectByName(name);
    }

    @Override
    public List<SubAgent> listEnabled() {
        return list(new LambdaQueryWrapper<SubAgent>()
                .eq(SubAgent::getEnabled, 1)
                .orderByDesc(SubAgent::getCreateTime));
    }

    @Override
    @CacheEvict(value = RedisCacheConfig.CACHE_SUBAGENT, key = "#id")
    public void deleteById(Long id) {
        SubAgent subAgent = getById(id);
        if (subAgent == null) {
            throw new BizException(ErrorCode.SUBAGENT_NOT_FOUND);
        }
        if (Integer.valueOf(1).equals(subAgent.getIsBuiltin())) {
            throw new BizException("内置 SubAgent 不可删除");
        }
        removeById(id);
    }

    @Override
    public void setEnabled(Long id, boolean enabled) {
        SubAgent subAgent = getById(id);
        if (subAgent == null) {
            throw new BizException(ErrorCode.SUBAGENT_NOT_FOUND);
        }
        subAgent.setEnabled(enabled ? 1 : 0);
        updateById(subAgent);
    }

    private String toJson(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<String> cleanStaleToolIds(List<String> toolIds) {
        return toolService.cleanStaleToolIds(toolIds);
    }

    private int resolveConnectTimeoutSeconds(Integer connectTimeoutSeconds) {
        if (connectTimeoutSeconds == null) {
            return DEFAULT_CONNECT_TIMEOUT_SECONDS;
        }
        return Math.max(1, Math.min(60, connectTimeoutSeconds));
    }

    private int resolveReadTimeoutSeconds(Integer readTimeoutSeconds) {
        if (readTimeoutSeconds != null) {
            return Math.max(10, Math.min(300, readTimeoutSeconds));
        }
        return DEFAULT_READ_TIMEOUT_SECONDS;
    }

    private int resolveModelRetryTimes(Integer modelRetryTimes) {
        if (modelRetryTimes == null) {
            return DEFAULT_MODEL_RETRY_TIMES;
        }
        return Math.max(0, Math.min(10, modelRetryTimes));
    }
}
