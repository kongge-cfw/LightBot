-- 删除废弃字段 timeout_seconds（数据已迁移至 read_timeout_seconds）
UPDATE subagent
SET read_timeout_seconds = timeout_seconds
WHERE timeout_seconds IS NOT NULL
  AND (read_timeout_seconds IS NULL OR (read_timeout_seconds = 45 AND timeout_seconds <> 45));

ALTER TABLE subagent DROP COLUMN IF EXISTS timeout_seconds;
