package com.lightbot.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.businesspage.BusinessPageDefinition;
import com.lightbot.common.BizException;
import com.lightbot.dto.BusinessPageKeyConfigUpdateDTO;
import com.lightbot.dto.BusinessPageUpsertDTO;
import com.lightbot.entity.ApiKey;
import com.lightbot.entity.BusinessPage;
import com.lightbot.enums.ErrorCode;
import com.lightbot.mapper.BusinessPageMapper;
import com.lightbot.service.ApiKeyService;
import com.lightbot.service.BusinessPageService;
import com.lightbot.vo.BusinessPageKeyConfigVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 业务办理页注册表：仅以 DB 为准，由开发者在能力中心注册。
 *
 * @author finch
 * @since 2026-08-04
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessPageServiceImpl extends ServiceImpl<BusinessPageMapper, BusinessPage>
        implements BusinessPageService {

    private final ApiKeyService apiKeyService;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<BusinessPageDefinition> resolveEnabled(String pageType) {
        if (pageType == null || pageType.isBlank()) {
            return Optional.empty();
        }
        String key = pageType.trim();
        BusinessPage db = getOne(new LambdaQueryWrapper<BusinessPage>().eq(BusinessPage::getPageType, key), false);
        if (db == null || !Integer.valueOf(1).equals(db.getEnabled())) {
            return Optional.empty();
        }
        return Optional.of(toDefinition(db));
    }

    @Override
    public Collection<BusinessPageDefinition> listEnabledDefinitions() {
        List<BusinessPage> rows = list(new LambdaQueryWrapper<BusinessPage>()
                .eq(BusinessPage::getEnabled, 1)
                .orderByAsc(BusinessPage::getPageType));
        return rows.stream().map(this::toDefinition).toList();
    }

    @Override
    public List<BusinessPage> listAllForAdmin() {
        return list(new LambdaQueryWrapper<BusinessPage>().orderByAsc(BusinessPage::getPageType));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BusinessPage upsert(BusinessPageUpsertDTO dto) {
        // 1. 校验：pageHtml（推荐）/ pageUrl / formSchema 至少一种
        String pageType = dto.getPageType().trim();
        String pageHtml = dto.getPageHtml() == null ? null : dto.getPageHtml().trim();
        String pageUrl = dto.getPageUrl() == null ? null : dto.getPageUrl().trim();
        boolean hasForm = dto.getFormSchema() != null && !dto.getFormSchema().isEmpty();
        if ((pageHtml == null || pageHtml.isBlank())
                && (pageUrl == null || pageUrl.isBlank())
                && !hasForm) {
            throw new BizException("请填写 H5 页面 HTML（推荐），或外链 pageUrl，或 formSchema 兜底");
        }
        if (pageHtml != null && pageHtml.length() > 512 * 1024) {
            throw new BizException("H5 HTML 过大，请控制在 512KB 以内");
        }
        if (pageUrl != null && !pageUrl.isBlank()
                && !pageUrl.startsWith("http://") && !pageUrl.startsWith("https://")) {
            throw new BizException("pageUrl 须为 http(s) 地址");
        }
        BusinessPage existing = getOne(new LambdaQueryWrapper<BusinessPage>().eq(BusinessPage::getPageType, pageType), false);
        // 2. 写入（开发者注册，一律非平台内置）
        BusinessPage entity = existing != null ? existing : new BusinessPage();
        entity.setPageType(pageType);
        entity.setDisplayName(dto.getDisplayName().trim());
        entity.setDescription(dto.getDescription());
        entity.setDefaultTitle(dto.getDefaultTitle() != null && !dto.getDefaultTitle().isBlank()
                ? dto.getDefaultTitle().trim() : dto.getDisplayName().trim());
        entity.setPageHtml(pageHtml == null || pageHtml.isBlank() ? null : pageHtml);
        entity.setPageUrl(pageUrl == null || pageUrl.isBlank() ? null : pageUrl);
        // 默认仅允许对话内嵌；开发者可显式传入 drawer
        entity.setAllowedModes(writeJson(dto.getAllowedModes() != null ? dto.getAllowedModes() : List.of("inline")));
        entity.setAllowedActions(writeJson(dto.getAllowedActions() != null ? dto.getAllowedActions() : List.of("submit", "cancel")));
        entity.setAllowedPropKeys(writeJson(dto.getAllowedPropKeys() != null ? dto.getAllowedPropKeys() : List.of()));
        entity.setAllowedOptionKeys(writeJson(dto.getAllowedOptionKeys() != null ? dto.getAllowedOptionKeys() : List.of()));
        entity.setDefaultProps(writeJson(dto.getDefaultProps() != null ? dto.getDefaultProps() : Map.of()));
        entity.setFormSchema(dto.getFormSchema() == null ? null : writeJson(dto.getFormSchema()));
        entity.setEnabled(dto.getEnabled() == null || Boolean.TRUE.equals(dto.getEnabled()) ? 1 : 0);
        entity.setBuiltin(0);
        if (existing == null) {
            save(entity);
        } else {
            updateById(entity);
        }
        return getById(entity.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setEnabled(Long id, boolean enabled) {
        BusinessPage page = getById(id);
        if (page == null) {
            throw new BizException("业务页不存在");
        }
        page.setEnabled(enabled ? 1 : 0);
        updateById(page);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCustom(Long id) {
        BusinessPage page = getById(id);
        if (page == null) {
            throw new BizException("业务页不存在");
        }
        removeById(id);
    }

    @Override
    public String catalogForToolDescription() {
        String catalog = listEnabledDefinitions().stream()
                .map(d -> d.pageType() + "=" + d.displayName())
                .collect(Collectors.joining("；"));
        return catalog.isBlank() ? "（尚未在能力中心注册任何业务页）" : catalog;
    }

    @Override
    public Set<String> resolveAllowedPageTypes(Long apiKeyId, List<String> agentAllowed) {
        Set<String> enabled = listEnabledDefinitions().stream()
                .map(BusinessPageDefinition::pageType)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> fromKey = resolveKeyAllowed(apiKeyId, enabled);
        if (agentAllowed == null) {
            return fromKey;
        }
        Set<String> agentSet = agentAllowed.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        // 空数组：Agent 禁止全部
        if (agentSet.isEmpty()) {
            return Set.of();
        }
        Set<String> intersect = new LinkedHashSet<>();
        for (String type : fromKey) {
            if (agentSet.contains(type)) {
                intersect.add(type);
            }
        }
        return intersect;
    }

    @Override
    public boolean isPageTypeAllowed(String pageType, Long apiKeyId, List<String> agentAllowed) {
        if (pageType == null || pageType.isBlank()) {
            return false;
        }
        return resolveAllowedPageTypes(apiKeyId, agentAllowed).contains(pageType.trim());
    }

    @Override
    public BusinessPageKeyConfigVO getKeyConfig(Long apiKeyId) {
        ApiKey apiKey = requireApiKey(apiKeyId);
        Map<String, Object> cfg = parseKeyConfig(apiKey.getBusinessPageConfig());
        BusinessPageKeyConfigVO vo = new BusinessPageKeyConfigVO();
        vo.setApiKeyId(apiKeyId);
        boolean inherit = !Boolean.FALSE.equals(cfg.get("inherit"));
        vo.setInherit(inherit);
        vo.setAllowedPageTypes(asStringList(cfg.get("allowedPageTypes")));
        vo.setEffectivePageTypes(new ArrayList<>(resolveAllowedPageTypes(apiKeyId, null)));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BusinessPageKeyConfigVO updateKeyConfig(Long apiKeyId, BusinessPageKeyConfigUpdateDTO dto) {
        ApiKey apiKey = requireApiKey(apiKeyId);
        if (dto == null || !Boolean.FALSE.equals(dto.getInherit())) {
            apiKey.setBusinessPageConfig(null);
            apiKeyService.updateById(apiKey);
            return getKeyConfig(apiKeyId);
        }
        Map<String, Object> override = new LinkedHashMap<>();
        override.put("inherit", false);
        List<String> types = dto.getAllowedPageTypes() != null ? dto.getAllowedPageTypes() : List.of();
        override.put("allowedPageTypes", types.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .distinct()
                .toList());
        apiKey.setBusinessPageConfig(writeJson(override));
        apiKeyService.updateById(apiKey);
        return getKeyConfig(apiKeyId);
    }

    private Set<String> resolveKeyAllowed(Long apiKeyId, Set<String> enabled) {
        if (apiKeyId == null) {
            return enabled;
        }
        ApiKey apiKey = apiKeyService.getById(apiKeyId);
        if (apiKey == null) {
            return enabled;
        }
        Map<String, Object> cfg = parseKeyConfig(apiKey.getBusinessPageConfig());
        if (!Boolean.FALSE.equals(cfg.get("inherit"))) {
            return enabled;
        }
        List<String> allowed = asStringList(cfg.get("allowedPageTypes"));
        Set<String> result = new LinkedHashSet<>();
        for (String type : allowed) {
            if (enabled.contains(type)) {
                result.add(type);
            }
        }
        return result;
    }

    private ApiKey requireApiKey(Long apiKeyId) {
        if (apiKeyId == null) {
            throw new BizException(ErrorCode.API_KEY_NOT_FOUND);
        }
        ApiKey apiKey = apiKeyService.getById(apiKeyId);
        if (apiKey == null) {
            throw new BizException(ErrorCode.API_KEY_NOT_FOUND);
        }
        return apiKey;
    }

    private BusinessPageDefinition toDefinition(BusinessPage row) {
        return BusinessPageDefinition.of(
                row.getPageType(),
                row.getDisplayName(),
                row.getDescription(),
                row.getDefaultTitle(),
                row.getPageHtml(),
                row.getPageUrl(),
                asStringList(readJson(row.getAllowedModes())),
                asStringList(readJson(row.getAllowedActions())),
                asStringList(readJson(row.getAllowedPropKeys())),
                asStringList(readJson(row.getAllowedOptionKeys())),
                asObjectMap(readJson(row.getDefaultProps())),
                asObjectMap(readJson(row.getFormSchema())),
                false
        );
    }

    private Object readJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return null;
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception e) {
            throw new BizException("JSON 序列化失败");
        }
    }

    private Map<String, Object> parseKeyConfig(String json) {
        if (json == null || json.isBlank()) {
            return Map.of("inherit", true);
        }
        try {
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<>() {});
            return map != null ? map : Map.of("inherit", true);
        } catch (Exception e) {
            return Map.of("inherit", true);
        }
    }

    private List<String> asStringList(Object raw) {
        if (raw instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object item : list) {
                if (item != null && !String.valueOf(item).isBlank()) {
                    out.add(String.valueOf(item).trim());
                }
            }
            return out;
        }
        return List.of();
    }

    private Map<String, Object> asObjectMap(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            map.forEach((k, v) -> {
                if (k != null) {
                    out.put(String.valueOf(k), v);
                }
            });
            return out;
        }
        return Map.of();
    }
}
