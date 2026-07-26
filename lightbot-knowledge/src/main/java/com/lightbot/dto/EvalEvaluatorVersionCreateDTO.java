package com.lightbot.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 评估器版本创建请求
 *
 * @author finch
 * @since 2026-05-27
 */
@Data
public class EvalEvaluatorVersionCreateDTO {

    private Long evaluatorId;

    @Size(max = 32, message = "版本号不超过32字")
    private String version;

    @Size(max = 5000, message = "评估提示词不超过5000字")
    private String prompt;

    @Size(max = 8000, message = "变量定义不超过8000字")
    private String variables;

    @Size(max = 8000, message = "模型配置不超过8000字")
    private String modelConfig;
}
