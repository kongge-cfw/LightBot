-- SubAgent 增加独立超时与重试配置
ALTER TABLE subagent ADD COLUMN IF NOT EXISTS timeout_seconds INTEGER NOT NULL DEFAULT 60;
ALTER TABLE subagent ADD COLUMN IF NOT EXISTS model_retry_times INTEGER NOT NULL DEFAULT 1;
COMMENT ON COLUMN subagent.timeout_seconds IS 'SubAgent 执行超时（秒），默认 60';
COMMENT ON COLUMN subagent.model_retry_times IS 'SubAgent 模型调用失败重试次数，默认 1（即最多再试 1 次）';

-- 内置 SubAgent 同步默认值
UPDATE subagent SET timeout_seconds = 60, model_retry_times = 1
WHERE is_builtin = 1 AND (timeout_seconds IS NULL OR model_retry_times IS NULL);
