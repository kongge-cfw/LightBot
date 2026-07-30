package com.lightbot.controller;

import com.lightbot.common.Result;
import com.lightbot.enums.TaskStatus;
import com.lightbot.service.TaskService;
import com.lightbot.service.port.TaskCountNotifier;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 任务事件 SSE 推送，实时通知前端运行中任务数量变化
 * <p>企业版：推送全企业任务计数，所有在线建设者共享同一视图</p>
 *
 * @author finch
 * @since 2026-05-21
 */
@Slf4j
@Tag(name = "任务事件", description = "任务状态实时推送")
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskEventController implements TaskCountNotifier {

    private final TaskService taskService;

    private static final AtomicLong EMITTER_SEQ = new AtomicLong();
    /** 所有在线建设者的 SSE 连接 */
    private static final Map<Long, SseEmitter> EMITTERS = new ConcurrentHashMap<>();

    @Operation(summary = "任务计数实时推送（SSE）")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        // 需登录；计数为企业维度，不按用户隔离
        cn.dev33.satoken.stp.StpUtil.checkLogin();
        long emitterId = EMITTER_SEQ.incrementAndGet();
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        EMITTERS.put(emitterId, emitter);
        emitter.onCompletion(() -> EMITTERS.remove(emitterId, emitter));
        emitter.onTimeout(() -> EMITTERS.remove(emitterId, emitter));
        emitter.onError(e -> EMITTERS.remove(emitterId, emitter));
        sendCount(emitter);
        return emitter;
    }

    @Operation(summary = "任务计数查询（HTTP 兜底）", description = "SSE 断线或首次进入任务中心时拉取一次纠正徽标")
    @GetMapping("/count")
    public Result<Map<String, Long>> count() {
        return Result.ok(buildCounts());
    }

    @Override
    public void notifyUser(Long userId) {
        notifyAllUsers();
    }

    @Override
    public void notifyAllUsers() {
        for (Map.Entry<Long, SseEmitter> entry : EMITTERS.entrySet()) {
            sendCount(entry.getValue());
        }
    }

    private void sendCount(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().name("count").data(buildCounts()));
        } catch (IOException | IllegalStateException e) {
            EMITTERS.values().removeIf(e2 -> e2 == emitter);
        } catch (Exception e) {
            log.warn("[TaskEvent] 推送失败", e);
        }
    }

    /**
     * 汇总企业各未完结状态任务数
     */
    private Map<String, Long> buildCounts() {
        long pending = taskService.countByStatus(null, TaskStatus.PENDING.getCode());
        long running = taskService.countByStatus(null, TaskStatus.RUNNING.getCode());
        long pendingRetry = taskService.countByStatus(null, TaskStatus.PENDING_RETRY.getCode());
        long active = pending + running + pendingRetry;
        return Map.of(
                "active", active,
                "pending", pending,
                "running", running,
                "pendingRetry", pendingRetry);
    }
}
