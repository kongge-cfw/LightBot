-- ========================================
-- 任务队列 Redis Stream 重构（Phase 1）
-- 1. task 表增加重试 / 死信 / Stream 关联字段
-- 2. 增加重试与死信索引
-- ========================================

-- 已尝试次数（默认 0；每次执行 +1）
ALTER TABLE task ADD COLUMN IF NOT EXISTS attempts SMALLINT NOT NULL DEFAULT 0;

-- 最大重试次数（默认 3，按 TaskType 由 RetryPolicyProperties 覆盖）
ALTER TABLE task ADD COLUMN IF NOT EXISTS max_attempts SMALLINT NOT NULL DEFAULT 3;

-- 下次重试时间：score 写入 Redis ZSet lightbot:task:zset:delay，DB 字段冗余便于运维查询
ALTER TABLE task ADD COLUMN IF NOT EXISTS next_retry_at TIMESTAMP;

-- 主 Stream 消息 ID（XADD 返回，便于 PEL/XCLAIM 追溯）
ALTER TABLE task ADD COLUMN IF NOT EXISTS stream_id VARCHAR(40);

-- 是否已转入死信 Stream：0否 1是
ALTER TABLE task ADD COLUMN IF NOT EXISTS dead_letter SMALLINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN task.attempts IS '已尝试次数（含首次执行）';
COMMENT ON COLUMN task.max_attempts IS '最大重试次数（含首次执行）';
COMMENT ON COLUMN task.next_retry_at IS '下次重试时间（延迟队列 score 来源；NULL 表示无延迟）';
COMMENT ON COLUMN task.stream_id IS '主 Stream 消息 ID（XADD 返回）';
COMMENT ON COLUMN task.dead_letter IS '是否已转入死信 Stream：0否 1是';

-- 待重试任务索引（扫描延迟到期）
CREATE INDEX IF NOT EXISTS idx_task_next_retry
    ON task (next_retry_at)
    WHERE next_retry_at IS NOT NULL AND deleted = 0;

-- 死信任务索引（管理后台查询）
CREATE INDEX IF NOT EXISTS idx_task_dead_letter
    ON task (dead_letter)
    WHERE dead_letter = 1 AND deleted = 0;

-- ========================================
-- SubAgent 流式超时语义重构
-- read_timeout_seconds 默认值 45 → 60，语义改为"流式 token 间隔超时"
-- （长输出不超时，只在停滞时触发）
-- 同时把内置 SubAgent 的现有数据更新为 60
-- ========================================

ALTER TABLE subagent ALTER COLUMN read_timeout_seconds SET DEFAULT 60;
UPDATE subagent SET read_timeout_seconds = 60 WHERE is_builtin = 1 AND read_timeout_seconds = 30;
