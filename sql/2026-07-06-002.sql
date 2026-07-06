-- SubAgent 增加连接超时与响应超时（对齐工作流节点）
ALTER TABLE subagent ADD COLUMN IF NOT EXISTS connect_timeout_seconds INTEGER NOT NULL DEFAULT 10;
ALTER TABLE subagent ADD COLUMN IF NOT EXISTS read_timeout_seconds INTEGER NOT NULL DEFAULT 45;
COMMENT ON COLUMN subagent.connect_timeout_seconds IS 'SubAgent 模型连接超时（秒），默认 10';
COMMENT ON COLUMN subagent.read_timeout_seconds IS 'SubAgent 整体响应超时（秒），默认 45';

-- 将旧 timeout_seconds 迁移为 read_timeout_seconds（若仍为默认值）
UPDATE subagent
SET read_timeout_seconds = timeout_seconds
WHERE timeout_seconds IS NOT NULL AND timeout_seconds <> 60 AND read_timeout_seconds = 45;
