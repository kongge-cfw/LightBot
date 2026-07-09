-- 用户长期记忆：保存用户偏好、长期事实和可选语义向量
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS user_memory (
    id                  BIGINT          NOT NULL,
    user_id             BIGINT          NOT NULL,
    agent_id            BIGINT,
    session_id          BIGINT,
    memory_type         VARCHAR(32)     NOT NULL,
    content             TEXT            NOT NULL,
    keywords            JSONB           NOT NULL DEFAULT '[]'::jsonb,
    source_message_id   BIGINT,
    confidence          NUMERIC(5,4)    NOT NULL DEFAULT 1.0000,
    status              VARCHAR(32)     NOT NULL DEFAULT 'active',
    embedding_vector    vector(1536),
    last_used_at        TIMESTAMP,
    create_time         TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_user_memory_user_status ON user_memory (user_id, status);
CREATE INDEX IF NOT EXISTS idx_user_memory_agent ON user_memory (agent_id);
CREATE INDEX IF NOT EXISTS idx_user_memory_type ON user_memory (memory_type);
CREATE INDEX IF NOT EXISTS idx_user_memory_vector_hnsw ON user_memory
    USING hnsw (embedding_vector vector_cosine_ops)
    WHERE embedding_vector IS NOT NULL AND deleted = 0;

COMMENT ON TABLE user_memory IS '用户长期记忆表';
COMMENT ON COLUMN user_memory.memory_type IS '记忆类型：preference/profile/project_fact/instruction';
COMMENT ON COLUMN user_memory.status IS '状态：active/disabled/archived';
COMMENT ON COLUMN user_memory.embedding_vector IS '记忆语义向量，用于长期记忆语义检索';
