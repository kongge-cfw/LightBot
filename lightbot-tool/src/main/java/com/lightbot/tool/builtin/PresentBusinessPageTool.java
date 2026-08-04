package com.lightbot.tool.builtin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.businesspage.BusinessPageDefinition;
import com.lightbot.constant.ToolResultPrefixes;
import com.lightbot.service.BusinessPageService;
import com.lightbot.tool.ToolEventEmitter;
import com.lightbot.tool.annotation.SystemTool;
import com.lightbot.tool.annotation.ToolParamMeta;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 内置工具 — 在对话中呈现已由开发者注册的业务办理页。
 *
 * @author finch
 * @since 2026-08-04
 */
@Slf4j
@Component("presentBusinessPageTool")
@RequiredArgsConstructor
@SystemTool(
        displayName = "业务办理页",
        icon = "AppstoreOutlined",
        description = "在对话中呈现已在能力中心注册的业务办理页（H5 HTML / 外链）",
        tags = {"交互", "业务页"},
        outputExample = "{\"success\":true,\"pageType\":\"leave_request\",\"title\":\"请假申请\",\"mode\":\"inline\",\"wait_for_user\":true,\"props\":{\"days\":1},\"pageHtml\":\"<!DOCTYPE html>...\",\"actions\":[\"submit\",\"cancel\"]}",
        outputSchema = "{\"type\":\"object\",\"properties\":{\"success\":{\"type\":\"boolean\"},\"pageType\":{\"type\":\"string\"},\"wait_for_user\":{\"type\":\"boolean\"},\"props\":{\"type\":\"object\"},\"pageHtml\":{\"type\":\"string\"},\"pageUrl\":{\"type\":\"string\"}}}"
)
public class PresentBusinessPageTool {

    public static final String TOOL_NAME = "present_business_page";

    private final BusinessPageService businessPageService;
    private final ObjectMapper objectMapper;

    @Tool(name = TOOL_NAME,
            description = "当用户要办理具体业务时，在对话气泡内呈现已注册的业务办理页。"
                    + "【强制】pageType 只能使用工具描述末尾「可用列表」或参数 enum 中的值，必须精确复制，禁止自造同义名（例如不要用 utility_payment 代替 utility_bill_pay）。"
                    + "不要编造页面结构或 HTML。"
                    + "展示模式默认且优先使用 inline（嵌在对话消息内）；除非用户明确要求侧栏/抽屉，否则不要传 drawer。"
                    + "props 传 JSON 对象字符串（字段需在注册白名单内）；options 可传少量文案定制。"
                    + "【强制】调用成功后不要再输出操作指南、步骤列表或复述页面字段；页面已在对话中展示，等待用户在页面内提交/取消即可。"
                    + "若无匹配业务页，直接告知用户暂不支持，不要猜测 pageType。")
    public String presentBusinessPage(
            @ToolParam(description = "必须从可用列表/enum 中精确选择的 pageType，禁止自造别名")
            @ToolParamMeta(example = "utility_bill_pay") String pageType,
            @ToolParam(description = "页面标题，可选", required = false)
            @ToolParamMeta(example = "请假申请", required = false) String title,
            @ToolParam(description = "业务数据 JSON 对象字符串（预填到 H5 页的 props）", required = false)
            @ToolParamMeta(example = "{\"days\":1,\"reason\":\"事假\"}", required = false) String props,
            @ToolParam(description = "展示模式：默认 inline（对话内嵌）。仅当用户明确要求侧栏时用 drawer", required = false)
            @ToolParamMeta(example = "inline", required = false) String mode,
            @ToolParam(description = "允许操作，逗号分隔或 JSON 数组", required = false)
            @ToolParamMeta(example = "submit,cancel", required = false) String actions,
            @ToolParam(description = "文案/显隐定制 JSON", required = false)
            @ToolParamMeta(example = "{\"primaryButtonText\":\"提交申请\",\"hint\":\"请填写请假信息\"}", required = false) String options,
            ToolContext toolContext) {

        log.info("[Tool:present_business_page] pageType={}, mode={}", pageType, mode);

        String catalog = resolveSessionCatalog(toolContext);
        BusinessPageDefinition definition = businessPageService.resolveEnabled(pageType).orElse(null);
        if (definition == null) {
            return ToolResultPrefixes.failureJson(
                    "未知或已禁用 pageType: " + pageType
                            + "。禁止使用未注册别名；请从下列列表精确选择：" + catalog);
        }

        @SuppressWarnings("unchecked")
        Collection<String> allowed = toolContext != null && toolContext.getContext() != null
                ? (Collection<String>) toolContext.getContext().get("allowedBusinessPages")
                : null;
        if (allowed != null && !allowed.contains(definition.pageType())) {
            return ToolResultPrefixes.failureJson(
                    "当前会话无权呈现业务页: " + definition.pageType()
                            + "。允许：" + (allowed.isEmpty() ? "（无）" : String.join(",", allowed)));
        }

        Map<String, Object> mergedProps = mergeAllowedMap(definition.defaultProps(), parseObject(props), definition.allowedPropKeys());
        Map<String, Object> mergedOptions = mergeAllowedMap(Map.of(), parseObject(options), definition.allowedOptionKeys());
        String resolvedMode = resolveMode(mode, definition);
        List<String> resolvedActions = resolveActions(actions, definition);
        String resolvedTitle = (title != null && !title.isBlank()) ? title.trim() : definition.defaultTitle();

        ToolEventEmitter.emit("正在打开业务办理页: " + definition.displayName());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("pageType", definition.pageType());
        result.put("displayName", definition.displayName());
        result.put("title", resolvedTitle);
        result.put("mode", resolvedMode);
        result.put("props", mergedProps);
        result.put("actions", resolvedActions);
        result.put("options", mergedOptions);
        if (definition.pageHtml() != null && !definition.pageHtml().isBlank()) {
            result.put("pageHtml", definition.pageHtml());
            result.put("renderHint", "h5");
        } else if (definition.pageUrl() != null && !definition.pageUrl().isBlank()) {
            result.put("pageUrl", definition.pageUrl());
            result.put("renderHint", "h5");
        } else {
            result.put("renderHint", "fallback");
        }
        // 等待用户在页面提交/取消（对话回灌 + Workflow HITL）
        result.put("wait_for_user", true);
        result.put("break_loop", true);
        result.put("schemaVersion", 1);

        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            return ToolResultPrefixes.failureJson("序列化失败: " + e.getMessage());
        }
    }

    /**
     * 优先使用会话白名单 catalog，否则回落全量启用列表。
     */
    @SuppressWarnings("unchecked")
    private String resolveSessionCatalog(ToolContext toolContext) {
        Collection<String> allowed = toolContext != null && toolContext.getContext() != null
                ? (Collection<String>) toolContext.getContext().get("allowedBusinessPages")
                : null;
        if (allowed != null) {
            if (allowed.isEmpty()) {
                return "（无）";
            }
            return allowed.stream()
                    .filter(s -> s != null && !s.isBlank())
                    .map(String::trim)
                    .map(type -> businessPageService.resolveEnabled(type)
                            .map(d -> d.pageType() + "=" + d.displayName())
                            .orElse(type))
                    .collect(java.util.stream.Collectors.joining("；"));
        }
        return businessPageService.catalogForToolDescription();
    }

    private Map<String, Object> parseObject(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Object raw = objectMapper.readValue(json.trim(), Object.class);
            if (raw instanceof Map<?, ?> map) {
                Map<String, Object> out = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    if (e.getKey() != null) {
                        out.put(String.valueOf(e.getKey()), e.getValue());
                    }
                }
                return out;
            }
        } catch (Exception e) {
            log.warn("[Tool:present_business_page] JSON 解析失败: {}", e.getMessage());
        }
        return Map.of();
    }

    private Map<String, Object> mergeAllowedMap(
            Map<String, Object> defaults,
            Map<String, Object> incoming,
            Set<String> allowedKeys) {
        Map<String, Object> merged = new LinkedHashMap<>(defaults != null ? defaults : Map.of());
        if (incoming == null || incoming.isEmpty()) {
            return merged;
        }
        // 未配置白名单时放行全部（H5 页常见）
        if (allowedKeys == null || allowedKeys.isEmpty()) {
            for (Map.Entry<String, Object> e : incoming.entrySet()) {
                if (e.getValue() != null) {
                    merged.put(e.getKey(), e.getValue());
                }
            }
            return merged;
        }
        for (Map.Entry<String, Object> e : incoming.entrySet()) {
            if (allowedKeys.contains(e.getKey()) && e.getValue() != null) {
                merged.put(e.getKey(), e.getValue());
            }
        }
        return merged;
    }

    private String resolveMode(String mode, BusinessPageDefinition definition) {
        // 产品默认：对话内嵌；未传或非法值一律回落 inline（若注册表允许）
        String candidate = mode == null || mode.isBlank() ? "inline" : mode.trim().toLowerCase();
        if (!"inline".equals(candidate) && !"drawer".equals(candidate)) {
            candidate = "inline";
        }
        if (definition.allowedModes().contains(candidate)) {
            return candidate;
        }
        if (definition.allowedModes().contains("inline")) {
            return "inline";
        }
        return definition.allowedModes().iterator().next();
    }

    private List<String> resolveActions(String actions, BusinessPageDefinition definition) {
        Set<String> requested = new LinkedHashSet<>();
        if (actions != null && !actions.isBlank()) {
            String trimmed = actions.trim();
            if (trimmed.startsWith("[")) {
                try {
                    List<String> list = objectMapper.readValue(trimmed, new TypeReference<List<String>>() {});
                    if (list != null) {
                        for (String item : list) {
                            if (item != null && !item.isBlank()) {
                                requested.add(item.trim());
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            }
            if (requested.isEmpty()) {
                for (String part : trimmed.split(",")) {
                    if (part != null && !part.isBlank()) {
                        requested.add(part.trim());
                    }
                }
            }
        }
        List<String> resolved = new ArrayList<>();
        if (requested.isEmpty()) {
            resolved.addAll(definition.allowedActions());
            return resolved;
        }
        for (String action : requested) {
            if (definition.allowedActions().contains(action)) {
                resolved.add(action);
            }
        }
        return resolved.isEmpty() ? new ArrayList<>(definition.allowedActions()) : resolved;
    }
}
