package com.lightbot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.lightbot.enums.AuthType;
import com.lightbot.enums.CommonStatus;
import com.lightbot.enums.ToolType;
import com.lightbot.validation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Tool 请求DTO
 *
 * @author finch
 * @since 2026-05-20
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ToolRequestDTO {

    private Long id;

    @NotBlank(message = "工具标识不能为空")
    @Size(max = 50, message = "工具标识不超过50字")
    private String name;

    @Size(max = 50, message = "显示名称不超过50字")
    private String displayName;

    /** 图标标识（Ant Design 图标组件名，如 GlobalOutlined） */
    @Size(max = 64, message = "图标标识不超过64字")
    private String icon;

    @Size(max = 200, message = "工具描述不超过200字")
    private String description;

    @NotNull(message = "工具类型不能为空")
    private ToolType toolType;

    @JsonFormat(message = "输入Schema必须为合法JSON格式")
    @Size(max = 16000, message = "输入Schema不超过16000字")
    private String inputSchema;

    @JsonFormat(message = "输出Schema必须为合法JSON格式")
    @Size(max = 16000, message = "输出Schema不超过16000字")
    private String outputSchema;

    @JsonFormat(message = "输出示例必须为合法JSON格式")
    @Size(max = 8000, message = "输出示例不超过8000字")
    private String outputExample;

    @JsonFormat(message = "工具配置必须为合法JSON格式")
    @Size(max = 8000, message = "工具配置不超过8000字")
    private String config;

    @Size(max = 2048, message = "端点地址不超过2048字")
    private String endpointUrl;

    private AuthType authType;

    @JsonFormat(message = "认证配置必须为合法JSON格式")
    @Size(max = 8000, message = "认证配置不超过8000字")
    private String authConfig;

    /** 工具标签（JSON数组字符串） */
    @JsonFormat(message = "标签必须为合法JSON格式")
    @Size(max = 200, message = "标签不超过200字")
    private String tags;

    /** 是否启用限流：true 时按 rateLimitConfig 在 (userId, toolName) 维度限流 */
    private Boolean rateLimitEnabled;

    /** 限流配置 JSON：{"limit":10,"window":"MINUTE|HOUR|DAY"} */
    @JsonFormat(message = "限流配置必须为合法JSON格式")
    @Size(max = 8000, message = "限流配置不超过8000字")
    private String rateLimitConfig;

    private CommonStatus status;
}
