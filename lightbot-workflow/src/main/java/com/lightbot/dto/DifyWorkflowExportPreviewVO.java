package com.lightbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/** LightBot 工作流导出 Dify YAML 的预检结果。 */
@Data
@Schema(description = "Dify工作流导出预检结果")
public class DifyWorkflowExportPreviewVO {

    @Schema(description = "源工作流SHA-256摘要")
    private String sourceDigest;

    @Schema(description = "节点数量")
    private Integer nodeCount;

    @Schema(description = "边数量")
    private Integer edgeCount;

    @Schema(description = "可导出节点数量")
    private Integer exportableCount;

    @Schema(description = "转换问题")
    private List<DifyWorkflowIssueVO> issues;
}
