package com.lightbot.subagent.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightbot.service.chat.ChatContext;
import com.lightbot.subagent.service.SubAgentTaskEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** 统一发布 SubAgent 批次与任务生命周期事件。 */
@Component
@RequiredArgsConstructor
public class SubAgentEventPublisher {

    private final ObjectMapper objectMapper;
    private final SubAgentTaskEventService taskEventService;

    /** 发布批次或任务事件到当前对话 SSE。 */
    public void publish(ChatContext context, String type, Map<String, Object> payload) {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("type", type);
            event.putAll(payload);
            taskEventService.record(String.valueOf(payload.getOrDefault("task_id", "")),
                    String.valueOf(payload.getOrDefault("batch_id", "")), type, event);
            if (context == null) {
                return;
            }
            if (context.getToolEventsList() != null) {
                synchronized (context.getToolEventsList()) {
                    context.getToolEventsList().add(new LinkedHashMap<>(event));
                }
            }
            context.emitRealtimeStatus(objectMapper.writeValueAsString(event));
        } catch (Exception ignored) {
            // SSE 观测失败不能影响主 Agent 工具调用。
        }
    }
}
