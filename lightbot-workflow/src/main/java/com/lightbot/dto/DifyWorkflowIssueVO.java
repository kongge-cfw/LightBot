package com.lightbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** Dify 工作流转换问题。 */
@Data
@Schema(description = "Dify工作流转换问题")
public class DifyWorkflowIssueVO {

    @Schema(description = "严重级别：BLOCKER、REPAIR_REQUIRED、WARNING")
    private String severity;

    @Schema(description = "问题编码")
    private String code;

    @Schema(description = "Dify或LightBot节点ID")
    private String nodeId;

    @Schema(description = "问题说明")
    private String message;
}
