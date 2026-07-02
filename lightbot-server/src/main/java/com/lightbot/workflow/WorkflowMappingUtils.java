package com.lightbot.workflow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 工作流节点参数映射：{{变量}} 模板解析与 input/outputMappings 读写
 */
public final class WorkflowMappingUtils {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{([^}]+)}}");

    private WorkflowMappingUtils() {
    }

    /**
     * 从 nodeData 读取 inputMappings，按模板解析为工具/API 入参
     */
    public static Map<String, Object> buildInputArgs(Map<String, Object> nodeData, Map<String, Object> variables) {
        Map<String, Object> args = new LinkedHashMap<>();
        List<Map<String, Object>> mappings = readMappingList(nodeData.get("inputMappings"));
        if (mappings == null || mappings.isEmpty()) {
            mappings = readMappingList(nodeData.get("input_mappings"));
        }
        if (mappings != null) {
            for (Map<String, Object> row : mappings) {
                String key = WorkflowNodeDataUtils.parseString(row.get("key"));
                if (key == null) {
                    continue;
                }
                args.put(key, resolveTemplateValue(row.get("value"), variables));
            }
        }
        return args;
    }

    /**
     * 将 sourceVars 按 outputMappings 写入工作流变量名
     */
    public static Map<String, Object> applyOutputMappings(Map<String, Object> nodeData,
                                                          Map<String, Object> sourceVars,
                                                          String fallbackKey,
                                                          Object fallbackValue) {
        Map<String, Object> outputs = new LinkedHashMap<>();
        List<Map<String, Object>> mappings = readMappingList(nodeData.get("outputMappings"));
        if (mappings == null || mappings.isEmpty()) {
            mappings = readMappingList(nodeData.get("output_mappings"));
        }
        if (mappings != null && !mappings.isEmpty()) {
            for (Map<String, Object> row : mappings) {
                String key = WorkflowNodeDataUtils.parseString(row.get("key"));
                if (key == null) {
                    continue;
                }
                Object rawValue = row.get("value");
                if (rawValue == null || String.valueOf(rawValue).isBlank()) {
                    rawValue = "{{" + fallbackKey + "}}";
                }
                outputs.put(key, resolveTemplateValue(rawValue, sourceVars));
            }
            return outputs;
        }
        if (fallbackKey != null) {
            Object value = sourceVars.get(fallbackKey);
            if (value == null) {
                value = fallbackValue;
            }
            outputs.put(fallbackKey, value);
        }
        return outputs;
    }

    /**
     * 解析 {{var}} 或含多个 {{var}} 的模板字符串
     */
    public static Object resolveTemplateValue(Object rawValue, Map<String, Object> variables) {
        if (!(rawValue instanceof String text)) {
            return rawValue;
        }
        Map<String, Object> vars = variables != null ? variables : Map.of();
        Matcher matcher = VAR_PATTERN.matcher(text.trim());
        if (matcher.matches()) {
            String varName = matcher.group(1).trim();
            return vars.get(varName);
        }
        StringBuffer sb = new StringBuffer();
        matcher.reset();
        while (matcher.find()) {
            String varName = matcher.group(1).trim();
            Object varValue = vars.get(varName);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(varValue == null ? "" : String.valueOf(varValue)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> readMappingList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return null;
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                result.add(new LinkedHashMap<>((Map<String, Object>) map));
            }
        }
        return result;
    }
}
