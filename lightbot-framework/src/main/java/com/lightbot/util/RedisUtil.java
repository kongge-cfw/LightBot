package com.lightbot.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Redis 工具类
 *
 * @author finch
 * @since 2026-05-21
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisUtil {

    private final StringRedisTemplate stringRedisTemplate;

    private static final String CANCEL_SIGNAL_PREFIX = "lightbot:task:cancel:";

    // ==================== 任务取消信号 ====================
    // 已迁移到 TaskQueueService.publishCancel / isCancelled / clearCancel。
    // 以下方法保留 @Deprecated 作为过渡，便于既有 Executor 平滑迁移；新代码一律走 TaskQueueService。

    /**
     * 设置任务取消信号（Redis key，比DB标记更快检测）
     *
     * @param taskId 任务ID
     * @deprecated 请改用 {@link com.lightbot.task.TaskQueueService#publishCancel}
     */
    @Deprecated
    public void setCancelSignal(Long taskId) {
        stringRedisTemplate.opsForValue().set(CANCEL_SIGNAL_PREFIX + taskId, "1", 1, TimeUnit.HOURS);
        log.debug("[Redis] 设置取消信号, taskId={}", taskId);
    }

    /**
     * 检查任务是否有取消信号
     *
     * @param taskId 任务ID
     * @return true=已请求取消
     * @deprecated 请改用 {@link com.lightbot.task.TaskQueueService#isCancelled}
     */
    @Deprecated
    public boolean hasCancelSignal(Long taskId) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(CANCEL_SIGNAL_PREFIX + taskId));
    }

    /**
     * 清除任务取消信号
     *
     * @param taskId 任务ID
     * @deprecated 请改用 {@link com.lightbot.task.TaskQueueService#clearCancel}
     */
    @Deprecated
    public void clearCancelSignal(Long taskId) {
        stringRedisTemplate.delete(CANCEL_SIGNAL_PREFIX + taskId);
    }

    // ==================== 通用缓存 ====================

    /**
     * 设置缓存（带过期时间）
     *
     * @param key     缓存key
     * @param value   缓存值
     * @param timeout 过期时间（秒）
     */
    public void set(String key, String value, long timeout) {
        stringRedisTemplate.opsForValue().set(key, value, timeout, TimeUnit.SECONDS);
    }

    /**
     * 设置缓存（永不过期）
     */
    public void set(String key, String value) {
        stringRedisTemplate.opsForValue().set(key, value);
    }

    /**
     * 获取缓存
     *
     * @return 缓存值，不存在返回null
     */
    public String get(String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }

    /**
     * 删除缓存
     */
    public void delete(String key) {
        stringRedisTemplate.delete(key);
    }

    /**
     * 判断缓存是否存在
     */
    public boolean exists(String key) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
    }

    /**
     * 自增（key 不存在时自动从 0 开始）
     *
     * @return 自增后的值
     */
    public long increment(String key) {
        Long val = stringRedisTemplate.opsForValue().increment(key);
        return val != null ? val : 1L;
    }
}

