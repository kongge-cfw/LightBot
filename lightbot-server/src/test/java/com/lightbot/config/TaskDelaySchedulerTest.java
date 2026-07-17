package com.lightbot.config;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.lightbot.entity.Task;
import com.lightbot.enums.TaskStatus;
import com.lightbot.enums.TaskType;
import com.lightbot.service.TaskService;
import com.lightbot.task.TaskQueueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 延迟队列扫描调度器单测：覆盖 ZSet 扫描、ZREM 互斥、状态校验、XADD 重投、异常兜底放回。
 *
 * <p>核心场景：</p>
 * <ul>
 *   <li>无到期任务：直接返回，不触发后续操作</li>
 *   <li>ZREM 抢占失败：跳过该任务（多实例互斥）</li>
 *   <li>任务已取消：跳过重投</li>
 *   <li>正常重投：XADD + 状态置回 PENDING</li>
 *   <li>重投异常：兜底放回延迟队列，避免任务丢失</li>
 * </ul>
 *
 * @author finch
 * @since 2026-07-18
 */
@ExtendWith(MockitoExtension.class)
class TaskDelaySchedulerTest {

    @Mock
    private TaskQueueService taskQueueService;
    @Mock
    private TaskService taskService;
    @Mock
    private LambdaUpdateChainWrapper<Task> chainWrapper;

    private TaskDelayScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new TaskDelayScheduler(taskQueueService, taskService);
        // batch-size 默认 100，interval-seconds 默认 1
        ReflectionTestUtils.setField(scheduler, "batchSize", 100);
        ReflectionTestUtils.setField(scheduler, "intervalSeconds", 1L);
    }

    private Task newTask(Long id, TaskStatus status, int attempts) {
        Task t = new Task();
        t.setId(id);
        t.setStatus(status);
        t.setAttempts(attempts);
        t.setType(TaskType.DOCUMENT_UPLOAD);
        return t;
    }

    @Test
    void test_scanDue_whenNoDueTasks_shouldDoNothing() {
        when(taskQueueService.scanDueDelayed(anyLong(), eq(100))).thenReturn(List.of());

        scheduler.scanDue();

        // 队列空：不应有任何 ZREM/getById/enqueue 调用
        verify(taskQueueService, never()).removeDelayed(anyLong());
        verify(taskService, never()).getById(anyLong());
        verify(taskQueueService, never()).enqueue(any());
    }

    @Test
    void test_scanDue_whenZremFails_shouldSkipTask() {
        // 模拟另一个实例先抢到 ZREM
        when(taskQueueService.scanDueDelayed(anyLong(), eq(100)))
                .thenReturn(List.of(1001L));
        when(taskQueueService.removeDelayed(1001L)).thenReturn(false);

        scheduler.scanDue();

        verify(taskQueueService, never()).enqueue(any());
        verify(taskService, never()).getById(1001L);
    }

    @Test
    void test_scanDue_whenTaskNotFound_shouldLogAndContinue() {
        when(taskQueueService.scanDueDelayed(anyLong(), eq(100)))
                .thenReturn(List.of(2002L));
        when(taskQueueService.removeDelayed(2002L)).thenReturn(true);
        when(taskService.getById(2002L)).thenReturn(null);

        scheduler.scanDue();

        // 任务不存在，不应 XADD
        verify(taskQueueService, never()).enqueue(any());
    }

    @Test
    void test_scanDue_whenStatusNotPendingRetry_shouldSkip() {
        // 任务被手动取消或已完结，状态已变
        Task cancelled = newTask(3003L, TaskStatus.CANCELLED, 1);
        when(taskQueueService.scanDueDelayed(anyLong(), eq(100)))
                .thenReturn(List.of(3003L));
        when(taskQueueService.removeDelayed(3003L)).thenReturn(true);
        when(taskService.getById(3003L)).thenReturn(cancelled);

        scheduler.scanDue();

        verify(taskQueueService, never()).enqueue(any());
    }

    @Test
    void test_scanDue_whenPendingRetry_shouldReenqueueAndResetStatus() {
        Task due = newTask(4004L, TaskStatus.PENDING_RETRY, 2);
        when(taskQueueService.scanDueDelayed(anyLong(), eq(100)))
                .thenReturn(List.of(4004L));
        when(taskQueueService.removeDelayed(4004L)).thenReturn(true);
        when(taskService.getById(4004L)).thenReturn(due);
        when(taskQueueService.enqueue(due)).thenReturn("1700000000000-0");

        // mock chainWrapper 链式调用：set(column, val) 第二个参数用 nullable 匹配 null
        when(taskService.lambdaUpdate()).thenReturn(chainWrapper);
        when(chainWrapper.eq(any(), any())).thenReturn(chainWrapper);
        when(chainWrapper.set(any(), any())).thenReturn(chainWrapper);
        when(chainWrapper.set(any(), nullable(Object.class))).thenReturn(chainWrapper);
        when(chainWrapper.update()).thenReturn(true);

        scheduler.scanDue();

        // 验证 XADD 重投
        verify(taskQueueService).enqueue(due);
        // 验证状态更新（chain 调用完成）
        verify(taskService).lambdaUpdate();
        verify(chainWrapper).update();
    }

    @Test
    void test_scanDue_whenEnqueueThrows_shouldFallbackToDelayed() {
        // 重投过程抛异常：兜底把任务放回延迟队列 5 秒后再试
        Task due = newTask(5005L, TaskStatus.PENDING_RETRY, 1);
        when(taskQueueService.scanDueDelayed(anyLong(), eq(100)))
                .thenReturn(List.of(5005L));
        when(taskQueueService.removeDelayed(5005L)).thenReturn(true);
        when(taskService.getById(5005L)).thenReturn(due);
        when(taskQueueService.enqueue(due)).thenThrow(new RuntimeException("Redis down"));

        scheduler.scanDue();

        // 验证兜底放回延迟队列（now+5000ms）
        verify(taskQueueService).enqueueDelayed(any(Task.class), anyLong());
    }

    @Test
    void test_scanDue_whenMultipleTasksAndMixedResults_shouldProcessAll() {
        // task1 抢占失败；task2 状态变更；task3 正常重投
        Task task2 = newTask(6002L, TaskStatus.SUCCESS, 1);
        Task task3 = newTask(6003L, TaskStatus.PENDING_RETRY, 1);

        when(taskQueueService.scanDueDelayed(anyLong(), eq(100)))
                .thenReturn(List.of(6001L, 6002L, 6003L));
        when(taskQueueService.removeDelayed(6001L)).thenReturn(false);
        when(taskQueueService.removeDelayed(6002L)).thenReturn(true);
        when(taskQueueService.removeDelayed(6003L)).thenReturn(true);
        when(taskService.getById(6002L)).thenReturn(task2);
        when(taskService.getById(6003L)).thenReturn(task3);
        when(taskQueueService.enqueue(task3)).thenReturn("1700000000003-0");

        // mock 链：仅 task3 会触发
        when(taskService.lambdaUpdate()).thenReturn(chainWrapper);
        lenient().when(chainWrapper.eq(any(), any())).thenReturn(chainWrapper);
        lenient().when(chainWrapper.set(any(), any())).thenReturn(chainWrapper);
        lenient().when(chainWrapper.set(any(), nullable(Object.class))).thenReturn(chainWrapper);
        lenient().when(chainWrapper.update()).thenReturn(true);

        scheduler.scanDue();

        // 只 task3 触发 enqueue
        verify(taskQueueService).enqueue(task3);
    }
}
