package com.lightbot.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Dify YAML 导出内容。 */
@Getter
@AllArgsConstructor
public class DifyWorkflowExportResult {

    private final String fileName;

    private final String content;

    private final DifyWorkflowExportPreviewVO preview;
}
