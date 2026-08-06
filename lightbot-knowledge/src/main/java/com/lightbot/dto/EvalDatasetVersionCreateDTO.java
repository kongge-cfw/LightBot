package com.lightbot.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 评测集版本创建请求
 *
 * @author finch
 * @since 2026-05-27
 */
@Data
public class EvalDatasetVersionCreateDTO {

    private Long datasetId;

    @Size(max = 32, message = "版本号不超过32字")
    private String version;
}
