-- 实体图标 code 字段（前端 Ant Design 图标组件名，非 SVG 内容）
ALTER TABLE tool       ADD COLUMN IF NOT EXISTS icon VARCHAR(64);
ALTER TABLE skill      ADD COLUMN IF NOT EXISTS icon VARCHAR(64);
ALTER TABLE subagent   ADD COLUMN IF NOT EXISTS icon VARCHAR(64);
ALTER TABLE mcp_server ADD COLUMN IF NOT EXISTS icon VARCHAR(64);

COMMENT ON COLUMN tool.icon       IS '图标标识（Ant Design 图标组件名）';
COMMENT ON COLUMN skill.icon      IS '图标标识（Ant Design 图标组件名）';
COMMENT ON COLUMN subagent.icon   IS '图标标识（Ant Design 图标组件名）';
COMMENT ON COLUMN mcp_server.icon IS '图标标识（Ant Design 图标组件名）';
