package com.lightbot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.lightbot.enums.McpInstallType;
import com.lightbot.enums.McpTransportType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.lightbot.validation.JsonFormat;
import lombok.Data;

/**
 * MCP Server 请求DTO
 *
 * @author finch
 * @since 2026-05-20
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class McpServerRequestDTO {

    private Long id;

    @NotNull(message = "名称不能为空")
    @Size(max = 50, message = "服务名称不超过50字")
    private String name;

    @Size(max = 200, message = "服务描述不超过200字")
    private String description;

    /** 图标标识（Ant Design 图标组件名，如 ApiOutlined） */
    @Size(max = 64, message = "图标标识不超过64字")
    private String icon;

    @NotNull(message = "安装类型不能为空")
    private McpInstallType installType;

    @JsonFormat(message = "部署配置必须为合法JSON格式")
    @Size(max = 8000, message = "部署配置不超过8000字")
    private String deployConfig;

    @JsonFormat(message = "详细配置必须为合法JSON格式")
    @Size(max = 8000, message = "详细配置不超过8000字")
    private String detailConfig;

    @Size(max = 2048, message = "服务地址不超过2048字")
    private String host;

    @NotNull(message = "传输类型不能为空")
    private McpTransportType transport;

    @JsonFormat(message = "请求头必须为合法JSON格式")
    @Size(max = 8000, message = "请求头不超过8000字")
    private String headers;

    @JsonFormat(message = "禁用工具必须为合法JSON格式")
    @Size(max = 8000, message = "禁用工具不超过8000字")
    private String disabledTools;
}
