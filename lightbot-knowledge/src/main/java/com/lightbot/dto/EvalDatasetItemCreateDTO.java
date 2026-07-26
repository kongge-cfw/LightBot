package com.lightbot.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 评测数据项创建请求
 *
 * @author finch
 * @since 2026-05-27
 */
@Data
public class EvalDatasetItemCreateDTO {

    private Long datasetId;

    private Long datasetVersionId;

    @Size(max = 2000, message = "数据内容不超过2000字")
    private String dataContent;

    @Size(max = 500, message = "元数据不超过500字")
    private String metadata;

    @Size(max = 1000, message = "批量数据最多1000条")
    private List<@Size(max = 2000, message = "单条数据内容不超过2000字") String> dataContents;
}
