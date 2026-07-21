-- ============================================================================
-- v3.1 工具维度限流（backend-optimization-v3.1.md 2.6.3）
-- 新增 2 列：rate_limit_enabled（开关）+ rate_limit_config（JSONB：limit/window）
-- 内置/API 工具均可在 UI 配置；按 (userId, toolName) 维度 Redis 计数限流
-- ============================================================================

ALTER TABLE tool ADD COLUMN IF NOT EXISTS rate_limit_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE tool ADD COLUMN IF NOT EXISTS rate_limit_config JSONB;

COMMENT ON COLUMN tool.rate_limit_enabled IS '是否启用限流';
COMMENT ON COLUMN tool.rate_limit_config IS '限流配置 JSON：{"limit":10,"window":"MINUTE|HOUR|DAY"}';
