package com.lightbot.dto;

import com.lightbot.enums.ModelProviderType;
import com.lightbot.validation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 模型提供商请求DTO
 *
 * @author finch
 * @since 2026-05-19
 */
@Data
public class ModelProviderDTO {

    private Long id;

    @NotNull(message = "名称不能为空")
    @Size(max = 50, message = "名称不超过50字")
    private String name;

    @NotNull(message = "类型不能为空")
    private ModelProviderType type;

    @Size(max = 2048, message = "API Key不超过2048字")
    private String apiKey;

    @Size(max = 512, message = "Base URL不超过512字")
    private String baseUrl;

    /** 默认模型ID */
    @Size(max = 100, message = "默认模型ID不超过100字")
    private String defaultModelId;

    /** 模型列表获取地址（为空时使用默认地址） */
    @Size(max = 512, message = "模型列表地址不超过512字")
    private String modelsEndpoint;

    /** 额外请求头（JSON格式） */
    @JsonFormat(message = "额外请求头必须为合法JSON格式")
    @Size(max = 8000, message = "额外请求头不超过8000字")
    private String headersJson;

    /** 扩展配置（JSON格式） */
    @JsonFormat(message = "扩展配置必须为合法JSON格式")
    @Size(max = 8000, message = "扩展配置不超过8000字")
    private String extraJson;

    /** 模型参数配置 */
    @JsonFormat(message = "模型参数配置必须为合法JSON格式")
    @Size(max = 8000, message = "模型参数配置不超过8000字")
    private String config;
}
