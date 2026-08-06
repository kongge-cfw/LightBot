package com.lightbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 图谱三元组 DTO（用于 LLM 抽取结果和手动导入）
 *
 * @author finch
 * @since 2026-05-29
 */
@Data
@Schema(description = "图谱三元组")
public class GraphTripleDTO {

    @Schema(description = "头实体名称")
    @NotBlank(message = "头实体名称不能为空")
    @Size(max = 50, message = "实体名称不超过50字")
    private String head;

    @Schema(description = "头实体类型")
    @Size(max = 50, message = "实体类型不超过50字")
    private String headType;

    @Schema(description = "头实体描述")
    @Size(max = 200, message = "实体描述不超过200字")
    private String headDesc;

    @Schema(description = "关系类型")
    @NotBlank(message = "关系类型不能为空")
    @Size(max = 50, message = "关系类型不超过50字")
    private String relation;

    @Schema(description = "关系描述")
    @Size(max = 200, message = "关系描述不超过200字")
    private String relationDesc;

    @Schema(description = "尾实体名称")
    @NotBlank(message = "尾实体名称不能为空")
    @Size(max = 50, message = "实体名称不超过50字")
    private String tail;

    @Schema(description = "尾实体类型")
    @Size(max = 50, message = "实体类型不超过50字")
    private String tailType;

    @Schema(description = "尾实体描述")
    @Size(max = 200, message = "实体描述不超过200字")
    private String tailDesc;
}
