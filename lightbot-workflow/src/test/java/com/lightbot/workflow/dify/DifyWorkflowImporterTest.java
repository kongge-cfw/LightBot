package com.lightbot.workflow.dify;

import com.lightbot.dto.DifyWorkflowImportPreviewVO;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Dify YAML 示例导入测试。 */
class DifyWorkflowImporterTest {

    private final DifyWorkflowImporter importer = new DifyWorkflowImporter();

    @Test
    void test_previewSampleWorkflows_shouldNotContainBlocker() throws IOException {
        for (String fileName : List.of("01-simple-start-end.yml", "02-llm-workflow.yml", "03-condition-route.yml")) {
            DifyWorkflowImportPreviewVO preview = importer.preview(Files.readString(resolveSample(fileName)));
            assertNotNull(preview.getGraph());
            assertFalse(preview.getIssues().stream().anyMatch(issue -> "BLOCKER".equals(issue.getSeverity())),
                    () -> fileName + " should not contain import blockers");
        }
    }

    private Path resolveSample(String fileName) {
        Path fromModule = Path.of("..", "docs", "design", "examples", "dify-workflows", fileName);
        if (Files.exists(fromModule)) {
            return fromModule;
        }
        return Path.of("docs", "design", "examples", "dify-workflows", fileName);
    }
}
