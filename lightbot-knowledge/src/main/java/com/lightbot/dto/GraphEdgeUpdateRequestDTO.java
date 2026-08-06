package com.lightbot.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 图谱关系更新请求。
 *
 * @author finch
 * @since 2026-07-26
 */
@Data
public class GraphEdgeUpdateRequestDTO {

    @Size(max = 50, message = "关系类型不超过50字")
    private String relationType;

    @Size(max = 200, message = "关系描述不超过200字")
    private String description;
}
