package com.lightbot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 图谱关系手动维护请求。
 *
 * @author finch
 * @since 2026-07-26
 */
@Data
public class GraphEdgeRequestDTO {

    @NotBlank(message = "头实体名称不能为空")
    @Size(max = 50, message = "实体名称不超过50字")
    private String headName;

    @NotBlank(message = "关系类型不能为空")
    @Size(max = 50, message = "关系类型不超过50字")
    private String relationType;

    @NotBlank(message = "尾实体名称不能为空")
    @Size(max = 50, message = "实体名称不超过50字")
    private String tailName;

    @Size(max = 200, message = "关系描述不超过200字")
    private String description;
}
