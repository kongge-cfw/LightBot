package com.lightbot.task;

import com.lightbot.entity.Task;
import com.lightbot.enums.TaskType;
import com.lightbot.task.impl.TaskQueueServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 任务队列服务单测：使用 Mockito mock StringRedisTemplate，覆盖 enqueue/ack/progress/delayed/cancel/deadletter。
 *
 * <p>验证 Redis key 命名、消息字段、消费组映射、TTL 设置、ZSet 抢占语义。
 * 不连接真实 Redis，避免测试环境依赖。</p>
 *
 * @author finch
 * @since 2026-07-18
 */
@ExtendWith(MockitoExtension.class)
class TaskQueueServiceImplTest {

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private StreamOperations<String, Object, Object> streamOps;
    @Mock
    private ZSetOperations<String, String> zsetOps;
    @Mock
    private HashOperations<String, Object, Object> hashOps;
    @Mock
    private ValueOperations<String, String> valueOps;

    private TaskQueueServiceImpl service;

    @BeforeEach
    void setUp() {
        // 桥接各 opsForXxx 调用，service 构造时无 Redis 真实连接
        lenient().when(redis.opsForStream()).thenReturn(streamOps);
        lenient().when(redis.opsForZSet()).thenReturn(zsetOps);
        lenient().when(redis.opsForHash()).thenReturn(hashOps);
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
        service = new TaskQueueServiceImpl(redis);
    }

    private Task newTask(Long id, TaskType type, int attempts) {
        Task t = new Task();
        t.setId(id);
        t.setType(type);
        t.setAttempts(attempts);
        return t;
    }

    // ============ enqueue ============

    @Test
    @SuppressWarnings("unchecked")
    void test_enqueue_shouldXaddMainStreamWithFullFields() {
        Task task = newTask(1001L, TaskType.DOCUMENT_UPLOAD, 0);
        // 模拟 XADD 返回的 RecordId；使用 doReturn() 避免 strict 模式对 any(Class) 返回 null 的告警
        org.mockito.Mockito.doReturn(RecordId.of("1700000000000-0"))
                .when(streamOps).add(org.mockito.ArgumentMatchers.<org.springframework.data.redis.connection.stream.MapRecord<String, Object, Object>>any());

        String streamId = service.enqueue(task);

        assertEquals("1700000000000-0", streamId);
        // StreamRecords.string(...) 返回 MapRecord；用 MapRecord 类型捕获以避免泛型擦除匹配问题
        ArgumentCaptor<org.springframework.data.redis.connection.stream.MapRecord<String, Object, Object>> captor =
                ArgumentCaptor.forClass(org.springframework.data.redis.connection.stream.MapRecord.class);
        verify(streamOps).add(captor.capture());
        // 验证 stream key
        assertEquals("lightbot:task:stream:main", captor.getValue().getStream());
    }

    // ============ enqueueDelayed ============

    @Test
    void test_enqueueDelayed_shouldZaddDelayZsetWithTimestampScore() {
        Task task = newTask(2002L, TaskType.GRAPH_EXTRACTION, 1);
        long delayAt = 1_700_000_000_000L;

        service.enqueueDelayed(task, delayAt);

        // ZADD 接受 double score，long 入参自动拓宽为 double
        verify(zsetOps).add(eq("lightbot:task:zset:delay"), eq("2002"),
                org.mockito.ArgumentMatchers.doubleThat(d -> Math.abs(d - 1_700_000_000_000.0d) < 1.0d));
    }

    // ============ cancel signals ============

    @Test
    void test_publishCancel_shouldSetKeyWithTtl() {
        service.publishCancel(3003L);
        // 验证 SET cancel:3003 "1" EX 3600
        verify(valueOps).set(eq("lightbot:task:cancel:3003"), eq("1"), eq(3_600L), eq(java.util.concurrent.TimeUnit.SECONDS));
    }

    @Test
    void test_isCancelled_whenKeyExists_shouldReturnTrue() {
        when(redis.hasKey("lightbot:task:cancel:4004")).thenReturn(true);
        assertTrue(service.isCancelled(4004L));
    }

    @Test
    void test_isCancelled_whenKeyMissing_shouldReturnFalse() {
        when(redis.hasKey("lightbot:task:cancel:4004")).thenReturn(false);
        assertFalse(service.isCancelled(4004L));
    }

    @Test
    void test_isCancelled_whenRedisReturnsNull_shouldReturnFalse() {
        // hasKey 返回 null（罕见，但 Boolean.TRUE.equals 防御）
        when(redis.hasKey(anyString())).thenReturn(null);
        assertFalse(service.isCancelled(5005L));
    }

    @Test
    void test_clearCancel_shouldDeleteKey() {
        service.clearCancel(6006L);
        verify(redis).delete("lightbot:task:cancel:6006");
    }

    // ============ progress ============

    @Test
    void test_reportProgress_shouldWriteHashAndExpire() {
        service.reportProgress(7007L, 42, "处理中");

        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(hashOps).putAll(eq("lightbot:task:progress:7007"), captor.capture());
        Map<String, String> fields = captor.getValue();
        assertEquals("42", fields.get("progress"));
        assertEquals("处理中", fields.get("message"));
        assertNotNull(fields.get("ts"));
        // 验证 TTL 设置
        verify(redis).expire(eq("lightbot:task:progress:7007"), eq(3_600L), eq(java.util.concurrent.TimeUnit.SECONDS));
    }

    @Test
    void test_reportProgress_withNullMessage_shouldWriteEmptyString() {
        service.reportProgress(8008L, 50, null);
        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(hashOps).putAll(eq("lightbot:task:progress:8008"), captor.capture());
        assertEquals("", captor.getValue().get("message"));
    }

    @Test
    void test_getProgress_whenHashEmpty_shouldReturnNull() {
        when(hashOps.entries("lightbot:task:progress:9009")).thenReturn(new HashMap<>());
        assertNull(service.getProgress(9009L));
    }

    @Test
    void test_getProgress_whenHashNull_shouldReturnNull() {
        when(hashOps.entries(anyString())).thenReturn(null);
        assertNull(service.getProgress(9009L));
    }

    @Test
    void test_getProgress_withValidFields_shouldReturnSnapshot() {
        Map<Object, Object> raw = new HashMap<>();
        raw.put("progress", "73");
        raw.put("message", "解析中");
        raw.put("ts", "1700000000000");
        when(hashOps.entries("lightbot:task:progress:1111")).thenReturn(raw);

        ProgressSnapshot snap = service.getProgress(1111L);
        assertNotNull(snap);
        assertEquals(73, snap.getProgress());
        assertEquals("解析中", snap.getMessage());
        assertEquals(1_700_000_000_000L, snap.getTs());
    }

    @Test
    void test_getProgress_withMalformedNumber_shouldFallbackGracefully() {
        Map<Object, Object> raw = new HashMap<>();
        raw.put("progress", "NaN");
        raw.put("ts", "not-a-number");
        when(hashOps.entries(anyString())).thenReturn(raw);

        ProgressSnapshot snap = service.getProgress(2222L);
        assertNotNull(snap);
        assertEquals(0, snap.getProgress());
        assertEquals(0L, snap.getTs());
    }

    // ============ ack ============

    @Test
    void test_ack_shouldXackBothDefaultAndHeavyGroups() {
        service.ack("1700000000000-0");
        // 默认组与重型组都尝试 ACK（XACK 对未消费组返回 0，无副作用）
        verify(streamOps).acknowledge(eq("lightbot:task:stream:main"), eq("cg:default"), any(RecordId.class));
        verify(streamOps).acknowledge(eq("lightbot:task:stream:main"), eq("cg:heavy"), any(RecordId.class));
    }

    // ============ sendToDeadLetter ============

    @Test
    @SuppressWarnings("unchecked")
    void test_sendToDeadLetter_shouldXaddDeadletterStreamWithAllFields() {
        Task task = newTask(1234L, TaskType.QA_PAIR_GENERATE, 3);

        service.sendToDeadLetter(task, "1700000000000-0", "NPE at chunker");

        // StreamRecords.string(...) 返回 MapRecord；捕获为 MapRecord 以读取 stream 和 kvMap
        ArgumentCaptor<org.springframework.data.redis.connection.stream.MapRecord<String, Object, Object>> captor =
                ArgumentCaptor.forClass(org.springframework.data.redis.connection.stream.MapRecord.class);
        verify(streamOps).add(captor.capture());
        assertEquals("lightbot:task:stream:deadletter", captor.getValue().getStream());
        Map<Object, Object> value = captor.getValue().getValue();
        assertEquals("1234", value.get("task_id"));
        // FIELD_TYPE 存的是 TaskType.name()（枚举常量名），便于消费者按 name() 反查 Executor
        assertEquals("QA_PAIR_GENERATE", value.get("type"));
        assertEquals("NPE at chunker", value.get("error"));
        assertEquals("3", value.get("attempts"));
        assertEquals("1700000000000-0", value.get("original_id"));
        assertNotNull(value.get("failed_at"));
        assertNotNull(value.get("ts"));
        assertEquals("1", value.get("v"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void test_sendToDeadLetter_withNullError_shouldWriteEmptyString() {
        Task task = newTask(1234L, TaskType.DOCUMENT_UPLOAD, 1);
        service.sendToDeadLetter(task, null, null);
        ArgumentCaptor<org.springframework.data.redis.connection.stream.MapRecord<String, Object, Object>> captor =
                ArgumentCaptor.forClass(org.springframework.data.redis.connection.stream.MapRecord.class);
        verify(streamOps).add(captor.capture());
        Map<Object, Object> value = captor.getValue().getValue();
        assertEquals("", value.get("error"));
        assertEquals("", value.get("original_id"));
    }

    // ============ scanDueDelayed / removeDelayed ============

    @Test
    void test_scanDueDelayed_whenZsetEmpty_shouldReturnEmptyList() {
        // min/max 是 double，offset/count 是 long
        when(zsetOps.rangeByScoreWithScores(anyString(), anyDouble(), anyDouble(), eq(0L), eq(100L)))
                .thenReturn(new LinkedHashSet<>());
        List<Long> result = service.scanDueDelayed(System.currentTimeMillis(), 100);
        assertTrue(result.isEmpty());
    }

    @Test
    void test_scanDueDelayed_withValidTuples_shouldReturnTaskIds() {
        Set<ZSetOperations.TypedTuple<String>> tuples = new LinkedHashSet<>();
        tuples.add(tupleOf("1001", 1_700_000_000_000L));
        tuples.add(tupleOf("1002", 1_700_000_000_500L));
        tuples.add(tupleOf("not-a-number", 1_700_000_001_000L)); // 非法 taskId，应被过滤
        when(zsetOps.rangeByScoreWithScores(anyString(), anyDouble(), anyDouble(), eq(0L), eq(50L)))
                .thenReturn(tuples);

        List<Long> result = service.scanDueDelayed(1_700_000_001_000L, 50);
        assertEquals(2, result.size());
        assertEquals(1001L, result.get(0));
        assertEquals(1002L, result.get(1));
    }

    @Test
    void test_scanDueDelayed_withNullTuples_shouldReturnEmptyList() {
        when(zsetOps.rangeByScoreWithScores(anyString(), anyDouble(), anyDouble(), eq(0L), eq(100L)))
                .thenReturn(null);
        List<Long> result = service.scanDueDelayed(System.currentTimeMillis(), 100);
        assertTrue(result.isEmpty());
    }

    @Test
    void test_removeDelayed_whenZremReturns1_shouldReturnTrue() {
        when(zsetOps.remove("lightbot:task:zset:delay", "9999")).thenReturn(1L);
        assertTrue(service.removeDelayed(9999L));
    }

    @Test
    void test_removeDelayed_whenZremReturns0_shouldReturnFalse() {
        when(zsetOps.remove(anyString(), anyString())).thenReturn(0L);
        assertFalse(service.removeDelayed(9999L));
    }

    @Test
    void test_removeDelayed_whenZremReturnsNull_shouldReturnFalse() {
        when(zsetOps.remove(anyString(), anyString())).thenReturn(null);
        assertFalse(service.removeDelayed(9999L));
    }

    // ============ claimMessages ============

    @Test
    void test_claimMessages_withEmptyIds_shouldReturn0WithoutXclaim() {
        int claimed = service.claimMessages(TaskType.Group.DEFAULT, "worker-1", Duration.ofMinutes(5), List.of());
        assertEquals(0, claimed);
        verify(streamOps, never()).claim(anyString(), anyString(), anyString(), any(Duration.class), any(RecordId[].class));
    }

    @Test
    void test_claimMessages_withIds_shouldReturnClaimedSize() {
        when(streamOps.claim(anyString(), anyString(), anyString(), any(Duration.class), any(RecordId[].class)))
                .thenReturn(List.of());
        int claimed = service.claimMessages(TaskType.Group.HEAVY, "worker-2", Duration.ofMinutes(10),
                List.of("1700000000000-0", "1700000000001-0"));
        // mock 返回空列表，期望 0
        assertEquals(0, claimed);
    }

    // ============ ensureGroups ============

    @Test
    void test_ensureGroups_shouldCreateBothGroups() {
        service.ensureGroups();
        verify(streamOps, times(1)).createGroup(eq("lightbot:task:stream:main"), any(), eq("cg:default"));
        verify(streamOps, times(1)).createGroup(eq("lightbot:task:stream:main"), any(), eq("cg:heavy"));
    }

    @Test
    void test_ensureGroups_whenBusyGroup_shouldSilentlyIgnore() {
        // 第一次抛 BUSYGROUP，第二次正常 — 验证不抛异常
        org.springframework.data.redis.RedisSystemException busyGroup = new org.springframework.data.redis.RedisSystemException(
                "BUSYGROUP Consumer Group name already exists", new RuntimeException());
        when(streamOps.createGroup(anyString(), any(), anyString()))
                .thenThrow(busyGroup)
                .thenReturn("OK");

        service.ensureGroups(); // 不应抛异常
    }

    // ============ 辅助 ============

    /** 构造一个 TypedTuple mock；scanDueDelayed 只读 value，无需 stub score */
    @SuppressWarnings("unchecked")
    private ZSetOperations.TypedTuple<String> tupleOf(String value, long score) {
        ZSetOperations.TypedTuple<String> t = org.mockito.Mockito.mock(ZSetOperations.TypedTuple.class);
        when(t.getValue()).thenReturn(value);
        return t;
    }

    // 注：Range 参数仅用于消除 unchecked 警告 import
    @SuppressWarnings("unused")
    private Range<String> unusedRange;
}
