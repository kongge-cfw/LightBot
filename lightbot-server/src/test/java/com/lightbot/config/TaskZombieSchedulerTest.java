package com.lightbot.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.lightbot.entity.Document;
import com.lightbot.entity.Task;
import com.lightbot.enums.DocumentStatus;
import com.lightbot.enums.TaskStatus;
import com.lightbot.enums.TaskType;
import com.lightbot.service.DocumentService;
import com.lightbot.service.TaskService;
import com.lightbot.task.StaleMessage;
import com.lightbot.task.TaskQueueService;
import com.lightbot.task.TaskZombieProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 僵尸任务扫描器单测：覆盖 RUNNING 孤儿判定、PEL 在场跳过、CAS 状态推进、Document 回滚。
 *
 * <p>核心场景：</p>
 * <ul>
 *   <li>无候选任务：直接返回</li>
 *   <li>streamId 仍在 PEL：跳过（worker 可能还在跑）</li>
 *   <li>streamId 不在 PEL：CAS markFailed + clearCancel</li>
 *   <li>DocumentUpload 任务：额外回滚 Document 状态</li>
 *   <li>Document 已在终态：仅 markFailed，不重复回滚</li>
 *   <li>非文档任务：仅 markFailed，不触发 Document 查询</li>
 *   <li>按 TaskType 区分阈值：长任务 GRAPH_EXTRACTION 未到专属阈值时跳过</li>
 *   <li>PEL 查询失败：保守跳过整轮扫描</li>
 *   <li>CAS 失败（状态已变更）：不触发后续回滚</li>
 * </ul>
 *
 * @author finch
 * @since 2026-07-18
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TaskZombieSchedulerTest {

    @Mock
    private TaskService taskService;
    @Mock
    private TaskQueueService taskQueueService;
    @Mock
    private DocumentService documentService;
    @Mock
    private LambdaUpdateChainWrapper<Task> updateChain;

    private TaskZombieProperties properties;
    private TaskZombieScheduler scheduler;

    @BeforeEach
    void setUp() {
        properties = new TaskZombieProperties();
        properties.setIntervalSeconds(60L);
        properties.setDefaultTimeoutMinutes(10L);
        properties.setBatchSize(100);
        scheduler = new TaskZombieScheduler(taskService, taskQueueService, documentService, properties);

        // 默认空 PEL：除非测试显式覆盖，否则视为没有消息在 PEL 中
        lenient().when(taskQueueService.scanStale(any(), any(Duration.class), anyInt()))
                .thenReturn(new ArrayList<>());
    }

    private Task newRunningTask(Long id, TaskType type, String streamId, int ageMinutes) {
        Task t = new Task();
        t.setId(id);
        t.setType(type);
        t.setStatus(TaskStatus.RUNNING);
        t.setStreamId(streamId);
        t.setUpdateTime(LocalDateTime.now().minusMinutes(ageMinutes));
        return t;
    }

    /**
     * mock taskService.list(wrapper)，scheduler 用 LambdaQueryWrapper 查候选任务
     */
    private void mockListReturns(List<Task> result) {
        lenient().when(taskService.list(any(LambdaQueryWrapper.class))).thenReturn(result);
    }

    /**
     * mock CAS 链：taskService.lambdaUpdate().eq(...).set(...).update() 返回指定布尔
     */
    private void mockCasReturns(boolean success) {
        lenient().when(taskService.lambdaUpdate()).thenReturn(updateChain);
        lenient().when(updateChain.eq(any(), any())).thenReturn(updateChain);
        lenient().when(updateChain.set(any(), any())).thenReturn(updateChain);
        // set(column, null) 用 nullable 匹配，避免 raw null 与 matcher 混用
        lenient().when(updateChain.set(any(), nullable(Object.class))).thenReturn(updateChain);
        lenient().when(updateChain.update()).thenReturn(success);
    }

    /**
     * PEL 返回指定 streamId 集合
     */
    private void mockPelStreamIds(String... streamIds) {
        List<StaleMessage> msgs = new ArrayList<>();
        for (String id : streamIds) {
            msgs.add(new StaleMessage(id, "any-consumer", 1000L, 1));
        }
        lenient().when(taskQueueService.scanStale(any(), any(Duration.class), anyInt())).thenReturn(msgs);
    }

    // ============ 场景1：无候选任务 ============

    @Test
    void test_scan_whenNoRunningTasks_shouldDoNothing() {
        mockListReturns(List.of());

        scheduler.scan();

        verify(taskQueueService, never()).clearCancel(anyLong());
        verify(documentService, never()).getById(anyLong());
        verify(taskService, never()).lambdaUpdate();
    }

    // ============ 场景2：streamId 仍在 PEL，跳过 ============

    @Test
    void test_scan_whenStreamIdInPel_shouldSkipTask() {
        mockPelStreamIds("s-live-1");
        Task t = newRunningTask(1001L, TaskType.DOCUMENT_UPLOAD, "s-live-1", 30);
        mockListReturns(List.of(t));

        scheduler.scan();

        // 不应触发 CAS / 回滚 / clearCancel
        verify(taskService, never()).lambdaUpdate();
        verify(documentService, never()).getById(anyLong());
        verify(taskQueueService, never()).clearCancel(anyLong());
    }

    // ============ 场景3：streamId 不在 PEL，强制失败 ============

    @Test
    void test_scan_whenStreamIdNotInPel_shouldCasMarkFailedAndClearCancel() {
        mockPelStreamIds("s-other");
        Task t = newRunningTask(2002L, TaskType.EXPERIMENT_RUN, "s-zombie-2002", 30);
        mockListReturns(List.of(t));
        mockCasReturns(true);

        scheduler.scan();

        // 验证 CAS 推进 + 死信标记 + 清理取消信号
        verify(updateChain).update();
        verify(taskService).markDeadLetter(eq(2002L), anyString());
        verify(taskQueueService).clearCancel(2002L);
        // 非文档类任务不触发 Document 查询
        verify(documentService, never()).getById(anyLong());
    }

    // ============ 场景4：CAS 失败（worker 抢先推进状态） ============

    @Test
    void test_scan_whenCasFails_shouldSkipRollback() {
        mockPelStreamIds("s-other");
        Task t = newRunningTask(3003L, TaskType.DOCUMENT_UPLOAD, "s-zombie-3003", 30);
        t.setRefId(55L);
        mockListReturns(List.of(t));
        mockCasReturns(false);

        scheduler.scan();

        // CAS 失败：不应触发 markDeadLetter / Document 回滚 / clearCancel
        verify(taskService, never()).markDeadLetter(anyLong(), anyString());
        verify(documentService, never()).getById(anyLong());
        verify(taskQueueService, never()).clearCancel(anyLong());
    }

    // ============ 场景5：DocumentUpload + Document 中间态 → 联动回滚 ============

    @Test
    void test_scan_whenDocumentUploadAndDocIntermediate_shouldRollbackDocument() {
        mockPelStreamIds("s-other");
        Task t = newRunningTask(4004L, TaskType.DOCUMENT_UPLOAD, "s-zombie-4004", 30);
        t.setRefId(55L);
        mockListReturns(List.of(t));
        mockCasReturns(true);

        // Document 处于中间态（PROCESSING），应被回滚
        Document doc = new Document();
        doc.setId(55L);
        doc.setStatus(DocumentStatus.PROCESSING);
        when(documentService.getById(55L)).thenReturn(doc);

        scheduler.scan();

        verify(documentService).updateById(any(Document.class));
    }

    // ============ 场景6：DocumentUpload + Document 已终态 → 不重复回滚 ============

    @Test
    void test_scan_whenDocumentAlreadyTerminal_shouldNotUpdateDocument() {
        mockPelStreamIds("s-other");
        Task t = newRunningTask(5005L, TaskType.DOCUMENT_INGEST, "s-zombie-5005", 30);
        t.setRefId(66L);
        mockListReturns(List.of(t));
        mockCasReturns(true);

        // Document 已 COMPLETED，不应再次写
        Document doc = new Document();
        doc.setId(66L);
        doc.setStatus(DocumentStatus.COMPLETED);
        when(documentService.getById(66L)).thenReturn(doc);

        scheduler.scan();

        verify(documentService, never()).updateById(any(Document.class));
    }

    // ============ 场景7：DocumentUpload + Document 不存在 → 安全跳过 ============

    @Test
    void test_scan_whenDocumentMissing_shouldSkipDocRollbackGracefully() {
        mockPelStreamIds("s-other");
        Task t = newRunningTask(6006L, TaskType.DOCUMENT_INGEST, "s-zombie-6006", 30);
        t.setRefId(77L);
        mockListReturns(List.of(t));
        mockCasReturns(true);
        when(documentService.getById(77L)).thenReturn(null);

        scheduler.scan();

        // 任务仍被 markFailed，只是 Document 跳过
        verify(updateChain).update();
        verify(documentService, never()).updateById(any(Document.class));
    }

    // ============ 场景8：长任务专属阈值（GRAPH_EXTRACTION 60min）未到，跳过 ============

    @Test
    void test_scan_whenLongTaskUnderTypeSpecificTimeout_shouldSkip() {
        // GRAPH_EXTRACTION 配 60min 阈值；任务只过了 30min → 跳过
        properties.getOverrides().put(TaskType.GRAPH_EXTRACTION, 60L);

        mockPelStreamIds("s-other");
        Task t = newRunningTask(7007L, TaskType.GRAPH_EXTRACTION, "s-zombie-7007", 30);
        mockListReturns(List.of(t));

        scheduler.scan();

        verify(taskService, never()).lambdaUpdate();
        verify(taskQueueService, never()).clearCancel(anyLong());
    }

    // ============ 场景9：长任务超过专属阈值，正常失败 ============

    @Test
    void test_scan_whenLongTaskExceedsTypeSpecificTimeout_shouldFail() {
        properties.getOverrides().put(TaskType.GRAPH_EXTRACTION, 60L);

        mockPelStreamIds("s-other");
        Task t = newRunningTask(8008L, TaskType.GRAPH_EXTRACTION, "s-zombie-8008", 90);
        mockListReturns(List.of(t));
        mockCasReturns(true);

        scheduler.scan();

        verify(updateChain).update();
        verify(taskQueueService).clearCancel(8008L);
    }

    // ============ 场景10：streamId 为空 → 按孤儿处理 ============

    @Test
    void test_scan_whenStreamIdIsNull_shouldTreatAsOrphanAndFail() {
        mockPelStreamIds("s-other");
        Task t = newRunningTask(9009L, TaskType.EXPERIMENT_RUN, null, 30);
        mockListReturns(List.of(t));
        mockCasReturns(true);

        scheduler.scan();

        // streamId=null 视为孤儿（理论不应发生，兜底处理避免漏网）
        verify(updateChain).update();
        verify(taskQueueService).clearCancel(9009L);
    }

    // ============ 场景11：PEL 查询异常 → 本轮保守跳过 ============

    @Test
    void test_scan_whenPelQueryThrows_shouldSkipEntireRound() {
        when(taskQueueService.scanStale(any(), any(Duration.class), anyInt()))
                .thenThrow(new RuntimeException("Redis down"));

        scheduler.scan();

        // 整轮跳过：不应查 DB / 不应 CAS
        verify(taskService, never()).list(any(LambdaQueryWrapper.class));
        verify(taskService, never()).lambdaUpdate();
    }

    // ============ 场景12：多任务混合结果，分别处理 ============

    @Test
    void test_scan_whenMultipleTasksMixed_shouldProcessEachIndependently() {
        mockPelStreamIds("s-in-pel");

        // task1：在 PEL，跳过
        Task t1 = newRunningTask(110L, TaskType.EXPERIMENT_RUN, "s-in-pel", 30);
        // task2：不在 PEL，强制失败
        Task t2 = newRunningTask(111L, TaskType.EXPERIMENT_RUN, "s-zombie-111", 30);
        // task3：GRAPH_EXTRACTION 30min（专属 60min 未到），跳过
        Task t3 = newRunningTask(112L, TaskType.GRAPH_EXTRACTION, "s-zombie-112", 30);
        properties.getOverrides().put(TaskType.GRAPH_EXTRACTION, 60L);

        mockListReturns(List.of(t1, t2, t3));
        mockCasReturns(true);

        scheduler.scan();

        // 只有 t2 触发 clearCancel
        verify(taskQueueService).clearCancel(111L);
        verify(taskQueueService, never()).clearCancel(110L);
        verify(taskQueueService, never()).clearCancel(112L);
    }
}
