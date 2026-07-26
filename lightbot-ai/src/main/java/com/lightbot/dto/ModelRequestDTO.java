package com.lightbot.dto;

import com.lightbot.enums.ModelType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 模型请求DTO
 *
 * @author finch
 * @since 2026-05-20
 */
@Data
public class ModelRequestDTO {

    private Long id;

    @NotNull(message = "提供商ID不能为空")
    private Long providerId;

    @NotBlank(message = "模型标识不能为空")
    @Pattern(regexp = "^[\\w.:-]+$", message = "模型标识只能包含字母、数字、下划线、点、冒号和连字符，不能包含竖线等特殊符号")
    @Size(max = 100, message = "模型标识不超过100字")
    private String modelId;

    @NotBlank(message = "模型名称不能为空")
    @Size(max = 100, message = "模型名称不超过100字")
    private String name;

    @NotNull(message = "模型类型不能为空")
    private ModelType type;
}
