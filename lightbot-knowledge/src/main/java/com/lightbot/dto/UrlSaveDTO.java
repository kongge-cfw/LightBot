package com.lightbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.lightbot.validation.JsonFormat;
import lombok.Data;

/**
 * 确认保存已预览的 URL 网页内容
 */
@Data
@Schema(description = "URL网页内容保存请求")
public class UrlSaveDTO {

    @NotBlank(message = "URL 不能为空")
    @Size(max = 2048, message = "URL不超过2048字")
    @Schema(description = "来源 URL")
    private String url;

    @NotBlank(message = "正文内容不能为空")
    @Size(max = 5 * 1024 * 1024, message = "正文内容不能超过5MiB")
    @Schema(description = "正文内容")
    private String content;

    @Schema(description = "网页标题")
    @Size(max = 255, message = "网页标题不超过255字")
    private String title;

    @Schema(description = "同步配置 JSON: {headers:{...}, syncInterval:'daily'|'weekly'|'manual'}")
    @JsonFormat(message = "同步配置必须为合法JSON格式")
    @Size(max = 8000, message = "同步配置不超过8000字")
    private String syncConfig;
}
