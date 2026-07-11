package com.lightbot.service.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * SSE 断线重连缓存的特征测试。
 */
class SseEventBufferTest {

    @Test
    void test_getReconnectData_withOtherUser_shouldHideRequest() {
        SseEventBuffer eventBuffer = new SseEventBuffer();
        eventBuffer.bufferEvent("request-1", 1, "first", 100L);

        SseEventBuffer.ReconnectResult result = eventBuffer.getReconnectData("request-1", null, 200L);

        assertEquals(SseEventBuffer.ReconnectResult.Status.NOT_FOUND, result.status());
        assertEquals(0, result.events().size());
    }

    @Test
    void test_getReconnectData_withCompletedRequest_shouldReturnOnlyMissedEvents() {
        SseEventBuffer eventBuffer = new SseEventBuffer();
        eventBuffer.bufferEvent("request-2", 1, "first", 100L);
        eventBuffer.bufferEvent("request-2", 2, "second", 100L);
        eventBuffer.markCompleted("request-2");

        SseEventBuffer.ReconnectResult result = eventBuffer.getReconnectData("request-2", 1, 100L);

        assertEquals(SseEventBuffer.ReconnectResult.Status.COMPLETED, result.status());
        assertEquals(1, result.events().size());
        assertEquals(2, result.events().get(0).id());
        assertEquals("second", result.events().get(0).data());
    }

    @Test
    void test_getReconnectData_withDeliveredCompletedRequest_shouldNotReplay() {
        SseEventBuffer eventBuffer = new SseEventBuffer();
        eventBuffer.bufferEvent("request-3", 1, "first", 100L);
        eventBuffer.markCompleted("request-3");

        SseEventBuffer.ReconnectResult result = eventBuffer.getReconnectData("request-3", 1, 100L);

        assertEquals(SseEventBuffer.ReconnectResult.Status.ALREADY_DELIVERED, result.status());
        assertEquals(0, result.events().size());
    }
}
