-- ============================================================================
-- v3.1 查询效率优化（backend-optimization-v3.1.md 2.1 系列）
-- 覆盖：KnowledgeAdvisor GIN 索引 + rate 复合索引、message.requestId 部分索引、
--       SubAgent.name 改部分唯一索引（让 soft-delete 后同名可重建）
-- ============================================================================

-- 1. message.metadata->'ragReferences' 的 GIN 表达式索引（v3.1 2.1.1-A）
--    加速 KnowledgeAdvisor 4 条 SQL 的 @> 包含查询，命中后从全表扫降到 << 1% 子集
CREATE INDEX IF NOT EXISTS idx_message_rag_refs
    ON message USING GIN ((metadata -> 'ragReferences') jsonb_path_ops);

-- 2. message_feedback(message_id, rating) 复合索引（v3.1 2.1.1-B）
--    加速 lowRatedChunks 中 COUNT(DISTINCT mf.id) FILTER (WHERE mf.rating = 'dislike')
CREATE INDEX IF NOT EXISTS idx_message_feedback_msg_rating
    ON message_feedback (message_id, rating);

-- 3. message.metadata ->> 'requestId' 部分表达式索引（v3.1 2.1.2）
--    仅对带 requestId 的消息（assistant 流式响应）建索引，体积小命中率高
CREATE INDEX IF NOT EXISTS idx_message_metadata_request_id
    ON message ((metadata ->> 'requestId'))
    WHERE metadata ? 'requestId';

-- 4. SubAgent.name 改部分唯一索引（v3.1 2.1.5）
--    原 subagent.name 是全表 UNIQUE，soft-delete 后同名不可重建，业务"删除-重建"被卡
--    步骤：① 删全表唯一约束 ② 删冗余 idx_subagent_name ③ 建部分唯一索引（仅未删除行）
--    其他同类资源已核查：users.username / tool.name / skill.slug / prompt.prompt_key
--    均已是部分唯一索引（WHERE deleted = 0），无需改动；mcp_server.name / agent.name
--    无唯一约束（业务上允许重名，按 ID 区分），不动
ALTER TABLE subagent DROP CONSTRAINT IF EXISTS subagent_name_key;
DROP INDEX IF EXISTS idx_subagent_name;
CREATE UNIQUE INDEX IF NOT EXISTS uk_subagent_name_alive
    ON subagent (name)
    WHERE deleted = 0;
