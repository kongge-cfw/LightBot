package com.lightbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Prompt版本创建请求
 *
 * @author finch
 * @since 2026-05-27
 */
@Data
@Schema(description = "Prompt版本创建请求")
public class PromptVersionCreateDTO {

    @Schema(description = "提示词唯一标识")
    @Size(max = 100, message = "提示词标识不超过100字")
    private String promptKey;

    @Schema(description = "版本号")
    @Size(max = 32, message = "版本号不超过32字")
    private String version;

    @Schema(description = "版本说明")
    @Size(max = 200, message = "版本说明不超过200字")
    private String versionDesc;

    @Schema(description = "模板内容")
    @Size(max = 5000, message = "模板内容不超过5000字")
    private String template;

    @Schema(description = "变量定义")
    @Size(max = 8000, message = "变量定义不超过8000字")
    private String variables;

    @Schema(description = "模型配置")
    @Size(max = 8000, message = "模型配置不超过8000字")
    private String modelConfig;

    @Schema(description = "工具配置")
    @Size(max = 8000, message = "工具配置不超过8000字")
    private String toolConfig;

    @Schema(description = "版本状态：pre（草稿）或 release（正式发布）")
    private String status;
}
