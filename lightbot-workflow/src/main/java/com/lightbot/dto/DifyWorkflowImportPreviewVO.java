package com.lightbot.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/** Dify YAML 导入预检结果。 */
@Data
@Schema(description = "Dify工作流导入预检结果")
public class DifyWorkflowImportPreviewVO {

    @Schema(description = "源文件SHA-256摘要")
    private String sourceDigest;

    @Schema(description = "Dify应用名称")
    private String appName;

    @Schema(description = "节点数量")
    private Integer nodeCount;

    @Schema(description = "边数量")
    private Integer edgeCount;

    @Schema(description = "转换后工作流图")
    private WorkflowGraphDTO graph;

    @Schema(description = "转换问题")
    private List<DifyWorkflowIssueVO> issues;
}
