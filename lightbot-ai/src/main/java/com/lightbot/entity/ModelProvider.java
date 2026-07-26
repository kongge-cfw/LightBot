package com.lightbot.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lightbot.enums.CommonStatus;
import com.lightbot.enums.ModelProviderType;
import com.lightbot.handler.JsonbTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.time.LocalDateTime;

/**
 * 模型提供商表
 *
 * @author finch
 * @since 2026-05-19
 */
@Data
@TableName("model_provider")
@Schema(description = "模型提供商表")
public class ModelProvider {

    @TableId(type = IdType.ASSIGN_ID)
    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("name")
    @Size(max = 50, message = "提供商名称不超过50字")
    @Schema(description = "提供商名称")
    private String name;

    @TableField("type")
    @Schema(description = "类型")
    private ModelProviderType type;

    @TableField("api_key")
    @Size(max = 2048, message = "API Key不超过2048字")
    @Schema(description = "API密钥")
    private String apiKey;

    @TableField("base_url")
    @Size(max = 512, message = "基础地址不超过512字")
    @Schema(description = "API基础地址")
    private String baseUrl;

    @TableField("models_endpoint")
    @Size(max = 512, message = "模型列表地址不超过512字")
    @Schema(description = "模型列表获取地址（为空时使用默认地址）")
    private String modelsEndpoint;

    @TableField(value = "headers_json", typeHandler = JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    @Size(max = 8000, message = "额外请求头不超过8000字")
    @Schema(description = "额外请求头（JSON格式）")
    private String headersJson;

    @TableField(value = "extra_json", typeHandler = JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    @Size(max = 8000, message = "扩展配置不超过8000字")
    @Schema(description = "扩展配置（JSON格式）")
    private String extraJson;

    @TableField(value = "config", typeHandler = JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
    @Size(max = 8000, message = "模型参数配置不超过8000字")
    @Schema(description = "模型参数配置")
    private String config;

    @TableField("status")
    @Schema(description = "状态")
    private CommonStatus status;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @TableField("deleted")
    @TableLogic
    @Schema(description = "逻辑删除标记")
    private Integer deleted;
}
