package com.lightbot.task;

import com.lightbot.enums.TaskType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 重试策略测试：指数退避计算 + 边界 + TaskType 覆盖解析。
 *
 * @author finch
 * @since 2026-07-18
 */
class RetryPolicyTest {

    @Test
    void test_computeDelay_withDefaultPolicy_shouldExponentiallyBackoff() {
        RetryPolicy policy = new RetryPolicy();
        // 默认：base=5000ms, multiplier=2.0, max=600000ms
        // attempt=0 → 5000 * 2^0 = 5000
        assertEquals(5_000L, policy.computeDelay(0));
        // attempt=1 → 5000 * 2^1 = 10000
        assertEquals(10_000L, policy.computeDelay(1));
        // attempt=2 → 5000 * 2^2 = 20000
        assertEquals(20_000L, policy.computeDelay(2));
        // attempt=3 → 5000 * 2^3 = 40000
        assertEquals(40_000L, policy.computeDelay(3));
    }

    @Test
    void test_computeDelay_whenExceedsMax_shouldCapToMaxMs() {
        RetryPolicy policy = new RetryPolicy();
        // attempt=100 → 5000 * 2^100 远超 600000，应被夹到 600000
        assertEquals(600_000L, policy.computeDelay(100));
    }

    @Test
    void test_computeDelay_withCustomMultiplier_shouldComputeCorrectly() {
        RetryPolicy policy = new RetryPolicy();
        policy.setBackoffBaseMs(1_000L);
        policy.setBackoffMultiplier(3.0d);
        policy.setBackoffMaxMs(100_000L);
        // attempt=0 → 1000 * 3^0 = 1000
        assertEquals(1_000L, policy.computeDelay(0));
        // attempt=2 → 1000 * 3^2 = 9000
        assertEquals(9_000L, policy.computeDelay(2));
        // attempt=5 → 1000 * 3^5 = 243000 > 100000，夹到 100000
        assertEquals(100_000L, policy.computeDelay(5));
    }

    @Test
    void test_computeDelay_withMultiplierOne_shouldReturnConstantBase() {
        // multiplier=1 退化为固定间隔（非指数退避）
        RetryPolicy policy = new RetryPolicy();
        policy.setBackoffBaseMs(2_000L);
        policy.setBackoffMultiplier(1.0d);
        assertEquals(2_000L, policy.computeDelay(0));
        assertEquals(2_000L, policy.computeDelay(10));
    }

    @Test
    void test_computeDelay_whenMaxMsSmallerThanBase_shouldAlwaysReturnMax() {
        // 边界：maxMs < base，任何 attempt 都应被夹到 maxMs
        RetryPolicy policy = new RetryPolicy();
        policy.setBackoffBaseMs(10_000L);
        policy.setBackoffMaxMs(500L);
        assertEquals(500L, policy.computeDelay(0));
        assertEquals(500L, policy.computeDelay(5));
    }

    @Test
    void test_resolvePolicy_withNullType_shouldReturnDefault() {
        RetryPolicyProperties props = new RetryPolicyProperties();
        // 不调 validate() 也能用（defaultPolicy 字段已有默认实例）
        RetryPolicy resolved = props.resolve(null);
        assertNotNull(resolved);
        assertSame(props.getDefaultPolicy(), resolved);
    }

    @Test
    void test_resolvePolicy_withUnconfiguredType_shouldReturnDefault() {
        RetryPolicyProperties props = new RetryPolicyProperties();
        RetryPolicy resolved = props.resolve(TaskType.DOCUMENT_UPLOAD);
        assertNotNull(resolved);
        assertSame(props.getDefaultPolicy(), resolved);
    }

    @Test
    void test_resolvePolicy_withOverride_shouldReturnOverride() {
        RetryPolicyProperties props = new RetryPolicyProperties();
        RetryPolicy heavy = new RetryPolicy();
        heavy.setMaxAttempts(2);
        heavy.setBackoffBaseMs(30_000L);
        props.getOverrides().put(TaskType.GRAPH_EXTRACTION, heavy);

        RetryPolicy resolved = props.resolve(TaskType.GRAPH_EXTRACTION);
        assertSame(heavy, resolved);
        assertEquals(2, resolved.getMaxAttempts());
        assertEquals(30_000L, resolved.getBackoffBaseMs());
    }

    @Test
    void test_resolvePolicy_withDifferentOverride_shouldResolveIndependently() {
        // 同一个 properties 对象对两个 TaskType 应返回各自策略
        RetryPolicyProperties props = new RetryPolicyProperties();

        RetryPolicy graphPolicy = new RetryPolicy();
        graphPolicy.setMaxAttempts(2);
        RetryPolicy qaPolicy = new RetryPolicy();
        qaPolicy.setMaxAttempts(5);

        props.getOverrides().put(TaskType.GRAPH_EXTRACTION, graphPolicy);
        props.getOverrides().put(TaskType.QA_PAIR_GENERATE, qaPolicy);

        assertEquals(2, props.resolve(TaskType.GRAPH_EXTRACTION).getMaxAttempts());
        assertEquals(5, props.resolve(TaskType.QA_PAIR_GENERATE).getMaxAttempts());
        // 其他类型仍走默认
        assertEquals(props.getDefaultPolicy().getMaxAttempts(),
                props.resolve(TaskType.DOCUMENT_UPLOAD).getMaxAttempts());
    }
}
