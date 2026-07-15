package com.lightbot.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.entity.LlmTrace;
import com.lightbot.entity.Message;
import com.lightbot.service.ChatAttachmentService;
import com.lightbot.service.LlmTraceService;
import com.lightbot.service.MessageService;
import com.lightbot.subagent.service.SubAgentTaskService;
import com.lightbot.vo.ResearchTaskProjectionVO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 请求级协作状态投影测试。
 *
 * @author finch
 * @since 2026-07-15
 */
class ResearchTaskProjectionServiceImplTest {

    @Test
    void test_getProjection_withRequestScopedState_shouldExposeUsageAndCurrentTaskOnly() {
        MessageService messageService = mock(MessageService.class);
        ChatAttachmentService attachmentService = mock(ChatAttachmentService.class);
        SubAgentTaskService taskService = mock(SubAgentTaskService.class);
        LlmTraceService traceService = mock(LlmTraceService.class);
        ResearchTaskProjectionServiceImpl service = new ResearchTaskProjectionServiceImpl(
                messageService, attachmentService, taskService, traceService, new ObjectMapper());

        String requestId = "request-current";
        Message assistant = new Message();
        assistant.setId(100L);
        assistant.setCreateTime(LocalDateTime.of(2026, 7, 15, 12, 0));
        assistant.setMetadata("""
                {"toolEvents":[
                  {"type":"tool_result","toolName":"write_todos","result":"{\\"success\\":true,\\"todos\\":[{\\"id\\":\\"scope\\",\\"content\\":\\"分析当前请求\\",\\"status\\":\\"in_progress\\"}]}"},
                  {"type":"tool_result","toolName":"present_artifacts","result":"{\\"artifacts\\":[{\\"name\\":\\"report.md\\",\\"path\\":\\"outputs/report.md\\"}]}"}
                ]}
                """);
        LlmTrace trace = new LlmTrace();
        trace.setInputTokens(120);
        trace.setOutputTokens(80);
        trace.setTotalTokens(200);
        trace.setStatus("completed");
        trace.setCreateTime(LocalDateTime.of(2026, 7, 15, 12, 1));

        when(messageService.listAssistantByRequestId(1L, requestId)).thenReturn(List.of(assistant));
        when(messageService.getUserByRequestId(1L, requestId)).thenReturn(null);
        when(taskService.listRuntimeSummaries(1L, requestId, 100)).thenReturn(List.of(
                Map.of("task_id", "task-current", "status", "running", "update_time", "2026-07-15T12:02:00")));
        when(traceService.findLatestByRequestId(requestId)).thenReturn(trace);

        ResearchTaskProjectionVO projection = service.getProjection(1L, requestId);

        assertEquals(requestId, projection.getParentRequestId());
        assertEquals(1, projection.getTodos().size());
        assertEquals("分析当前请求", projection.getTodos().get(0).get("content"));
        assertEquals(1, projection.getArtifacts().size());
        assertEquals("task-current", projection.getSubagents().get(0).get("task_id"));
        assertTrue((Boolean) projection.getUsage().get("available"));
        assertEquals(200L, projection.getUsage().get("totalTokens"));
        assertEquals("running", projection.getStatus());
    }

    @Test
    void test_getProjection_withoutTrace_shouldNotInventUsage() {
        MessageService messageService = mock(MessageService.class);
        ChatAttachmentService attachmentService = mock(ChatAttachmentService.class);
        SubAgentTaskService taskService = mock(SubAgentTaskService.class);
        LlmTraceService traceService = mock(LlmTraceService.class);
        ResearchTaskProjectionServiceImpl service = new ResearchTaskProjectionServiceImpl(
                messageService, attachmentService, taskService, traceService, new ObjectMapper());

        when(messageService.listAssistantByRequestId(2L, "request-empty")).thenReturn(List.of());
        when(messageService.getUserByRequestId(2L, "request-empty")).thenReturn(null);
        when(taskService.listRuntimeSummaries(2L, "request-empty", 100)).thenReturn(List.of());
        when(traceService.findLatestByRequestId("request-empty")).thenReturn(null);

        ResearchTaskProjectionVO projection = service.getProjection(2L, "request-empty");

        assertFalse((Boolean) projection.getUsage().get("available"));
        assertEquals("idle", projection.getStatus());
    }
}
