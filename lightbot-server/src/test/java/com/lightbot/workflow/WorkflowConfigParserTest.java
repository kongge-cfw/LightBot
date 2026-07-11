package com.lightbot.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Agent 工作流配置兼容规则的特征测试。
 */
class WorkflowConfigParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void test_resolveDraftGraph_shouldPreferWorkflowDraftOverLegacyWorkflow() {
        Map<String, Object> draft = Map.of("nodes", List.of(Map.of("id", "draft")));
        Map<String, Object> legacy = Map.of("nodes", List.of(Map.of("id", "legacy")));
        Map<String, Object> config = Map.of(
                WorkflowConfigKeys.WORKFLOW_DRAFT, draft,
                WorkflowConfigKeys.WORKFLOW_LEGACY, legacy);

        assertEquals(draft, WorkflowConfigParser.resolveDraftGraph(config));
    }

    @Test
    void test_resolvePublishedGraph_withoutPublishedVersion_shouldFallbackToDraft() {
        Map<String, Object> draft = Map.of("nodes", List.of(Map.of("id", "draft")));
        Map<String, Object> published = Map.of("nodes", List.of(Map.of("id", "published")));
        Map<String, Object> config = Map.of(
                WorkflowConfigKeys.WORKFLOW_DRAFT, draft,
                WorkflowConfigKeys.WORKFLOW_PUBLISHED, published,
                WorkflowConfigKeys.PUBLISHED_VERSION, 0);

        assertEquals(draft, WorkflowConfigParser.resolvePublishedGraph(config));
    }

    @Test
    void test_resolvePublishedGraph_withPublishedVersionButMissingGraph_shouldReturnNull() {
        Map<String, Object> config = Map.of(WorkflowConfigKeys.PUBLISHED_VERSION, 1);

        assertNull(WorkflowConfigParser.resolvePublishedGraph(config));
    }

    @Test
    void test_parseConfigMap_withInvalidJson_shouldReturnEmptyMap() {
        Map<String, Object> result = WorkflowConfigParser.parseConfigMap("{invalid", objectMapper);

        assertTrue(result.isEmpty());
    }
}
