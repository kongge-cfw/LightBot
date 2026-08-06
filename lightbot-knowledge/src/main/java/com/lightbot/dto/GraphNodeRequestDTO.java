package com.lightbot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 图谱节点手动维护请求。
 *
 * @author finch
 * @since 2026-07-26
 */
@Data
public class GraphNodeRequestDTO {

    @NotBlank(message = "节点名称不能为空")
    @Size(max = 50, message = "节点名称不超过50字")
    private String name;

    @Size(max = 50, message = "实体类型不超过50字")
    private String entityType = "其他";

    @Size(max = 200, message = "节点描述不超过200字")
    private String description;

    private Long providerId;
}
