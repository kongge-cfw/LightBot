-- ========================================
-- message 表 tool_events 字段拆分（Phase 6.5 阶段 1：数据分离）
-- 把 toolEvents 从 metadata 中拆出到独立列：
--   1. 减小 metadata 体积（前端 deep watch 不再扫描工具结果字符串）
--   2. tool_events 可独立索引/查询，为后续按需获取打基础
-- ========================================

ALTER TABLE message ADD COLUMN IF NOT EXISTS tool_events JSONB DEFAULT '[]';

COMMENT ON COLUMN message.tool_events IS '工具事件流（tool_call/tool_result/subagent_* 等），与 metadata 解耦存储';
