-- ============================================================================
-- 知识库 Advisor：反馈聚合 + 休眠分块检测
-- 为 message.metadata 添加 GIN 索引以加速 jsonb_array_elements 检索
-- ============================================================================

-- 1. message.metadata 的 GIN 索引：支撑 ragReferences 数组展开 + 反馈聚合查询
CREATE INDEX IF NOT EXISTS idx_message_metadata_gin ON message USING GIN (metadata jsonb_path_ops);

-- 2. chunk.knowledge_id 已有索引（idx_chunk_knowledge_id）— 此处不重复创建
