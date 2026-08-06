package com.lightbot.workflow.dify;

import com.lightbot.dto.DifyWorkflowImportPreviewVO;
import com.lightbot.dto.DifyWorkflowExportResult;
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
    private final DifyWorkflowExporter exporter = new DifyWorkflowExporter();

    @Test
    void test_previewSampleWorkflows_shouldNotContainBlocker() throws IOException {
        for (String fileName : List.of("01-simple-start-end.yml", "02-llm-workflow.yml", "03-condition-route.yml",
                "04-compatibility-node-families.yml")) {
            DifyWorkflowImportPreviewVO preview = importer.preview(Files.readString(resolveSample(fileName)));
            assertNotNull(preview.getGraph());
            assertFalse(preview.getIssues().stream().anyMatch(issue -> "BLOCKER".equals(issue.getSeverity())),
                    () -> fileName + " should not contain import blockers");
        }
    }

    @Test
    void test_previewAndExport_supportedDifyNodeFamilies_shouldKeepRoundTripAvailable() {
        DifyWorkflowImportPreviewVO preview = importer.preview("""
                version: 0.3.0
                kind: app
                app:
                  name: compatibility-sample
                  mode: workflow
                workflow:
                  graph:
                    nodes:
                      - id: start
                        type: custom
                        position: {x: 0, y: 0}
                        data: {type: start, title: Start}
                      - id: code
                        type: custom
                        position: {x: 200, y: 0}
                        data: {type: code, title: Code, code_language: javascript, code: "function main() { return {result: 'ok'}; }", variables: [], outputs: []}
                      - id: http
                        type: custom
                        position: {x: 400, y: 0}
                        data: {type: http-request, title: HTTP, method: get, url: "https://example.com"}
                      - id: retrieval
                        type: custom
                        position: {x: 600, y: 0}
                        data: {type: knowledge-retrieval, title: Retrieval, query_variable_selector: [sys, query]}
                      - id: tool
                        type: custom
                        position: {x: 800, y: 0}
                        data: {type: tool, title: Tool, provider_id: demo, tool_name: search, api_key: should-not-export, tool_parameters: {query: [sys, query]}}
                      - id: assigner
                        type: custom
                        position: {x: 1000, y: 0}
                        data: {type: assigner, title: Assigner, items: []}
                      - id: template
                        type: custom
                        position: {x: 1200, y: 0}
                        data: {type: template-transform, title: Template, template: "{{#sys.query#}}"}
                      - id: extractor
                        type: custom
                        position: {x: 1400, y: 0}
                        data: {type: parameter-extractor, title: Extractor, query: [sys, query], parameters: []}
                      - id: iteration
                        type: custom
                        position: {x: 1600, y: 0}
                        data: {type: iteration, title: Iteration, iterator_selector: [sys, query]}
                      - id: answer
                        type: custom
                        position: {x: 1800, y: 0}
                        data: {type: answer, title: Answer, answer: "done"}
                      - id: agent
                        type: custom
                        position: {x: 2000, y: 0}
                        data: {type: agent, title: Agent, app_id: demo-agent}
                      - id: end
                        type: custom
                        position: {x: 2200, y: 0}
                        data: {type: end, title: End}
                    edges:
                      - {id: e1, type: custom, source: start, target: code}
                      - {id: e2, type: custom, source: code, target: http}
                      - {id: e3, type: custom, source: http, target: retrieval}
                      - {id: e4, type: custom, source: retrieval, target: tool}
                      - {id: e5, type: custom, source: tool, target: assigner}
                      - {id: e6, type: custom, source: assigner, target: template}
                      - {id: e7, type: custom, source: template, target: extractor}
                      - {id: e8, type: custom, source: extractor, target: iteration}
                      - {id: e9, type: custom, source: iteration, target: answer}
                      - {id: e10, type: custom, source: answer, target: agent}
                      - {id: e11, type: custom, source: agent, target: end}
                """);

        assertFalse(preview.getIssues().stream().anyMatch(issue -> "BLOCKER".equals(issue.getSeverity())));
        DifyWorkflowExportResult result = exporter.export(preview.getGraph(), "compatibility-sample");
        assertNotNull(result.getContent());
        assertFalse(result.getContent().contains("should-not-export"));
        assertFalse(result.getPreview().getIssues().stream().anyMatch(issue -> "BLOCKER".equals(issue.getSeverity())));
        assertFalse(importer.preview(result.getContent()).getIssues().stream()
                .anyMatch(issue -> "BLOCKER".equals(issue.getSeverity())));
    }

    private Path resolveSample(String fileName) {
        Path fromModule = Path.of("..", "docs", "design", "examples", "dify-workflows", fileName);
        if (Files.exists(fromModule)) {
            return fromModule;
        }
        return Path.of("docs", "design", "examples", "dify-workflows", fileName);
    }
}
