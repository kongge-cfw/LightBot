-- LightBot 2.1 全量 DDL（目标态建表）
-- Snapshot date: 2026-07-28
--
-- 说明：
-- 1. 本文件只含结构：建库、扩展、CREATE TABLE / INDEX / COMMENT / 触发器
-- 2. 按模块分段注释；建表顺序按逻辑依赖（被引用方在前）；无遗留 ALTER
-- 3. 预制数据见同目录 insert-sql.sql
--
-- 全新安装（仓库根目录依次执行）：
--   psql -v ON_ERROR_STOP=1 -U postgres -h localhost -f sql/2026-07-28-init.sql
--   psql -v ON_ERROR_STOP=1 -U postgres -h localhost -d lightbot -f sql/insert-sql.sql
--
-- 后续结构变更请新增 sql/YYYY-MM-DD-NNN.sql，不要改已发布增量。

CREATE DATABASE lightbot ENCODING 'UTF8';
\c lightbot;

CREATE EXTENSION IF NOT EXISTS vector;

-- ============================================================
-- 模块：平台与账号
-- ============================================================

-- ----------------------------------------
-- 表：users
-- ----------------------------------------
CREATE TABLE users (
    id              BIGINT          NOT NULL,
    username        VARCHAR(64)     NOT NULL,
    email           VARCHAR(128)    DEFAULT '',
    password        VARCHAR(256)    NOT NULL,
    nickname        VARCHAR(64),
    avatar          VARCHAR(512),
    phone           VARCHAR(20),
    role            VARCHAR(20)     NOT NULL DEFAULT 'user',
    status          VARCHAR(20)     NOT NULL DEFAULT 'active',
    last_login_at   TIMESTAMP,
    config          JSONB           DEFAULT '{}',
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_user_username ON users (username) WHERE deleted = 0;
CREATE INDEX idx_user_status ON users (status);
CREATE INDEX idx_user_create_time ON users (create_time);
COMMENT ON TABLE users IS '用户表';

-- ----------------------------------------
-- 表：system_config
-- ----------------------------------------
CREATE TABLE system_config (
    config_key   VARCHAR(64)    NOT NULL,
    config_value TEXT,
    description  VARCHAR(255),
    create_time  TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (config_key)
);
COMMENT ON TABLE system_config IS '系统配置表';
COMMENT ON COLUMN system_config.config_key IS '配置键，如 default_ai_provider';
COMMENT ON COLUMN system_config.config_value IS '配置值，JSON格式';
COMMENT ON COLUMN system_config.description IS '配置描述';

-- ----------------------------------------
-- 表：task
-- ----------------------------------------
CREATE TABLE task (
    id               BIGINT        NOT NULL,
    name             VARCHAR(256)  NOT NULL,
    type             VARCHAR(50)   NOT NULL,
    status           VARCHAR(20)   NOT NULL DEFAULT 'pending',
    progress         SMALLINT      NOT NULL DEFAULT 0,
    message          VARCHAR(512),
    payload          TEXT,
    result           TEXT,
    error            TEXT,
    cancel_requested SMALLINT      NOT NULL DEFAULT 0,
    user_id          BIGINT        NOT NULL,
    ref_id           BIGINT,
    attempts         SMALLINT      NOT NULL DEFAULT 0,
    max_attempts     SMALLINT      NOT NULL DEFAULT 3,
    next_retry_at    TIMESTAMP,
    stream_id        VARCHAR(40),
    dead_letter      SMALLINT      NOT NULL DEFAULT 0,
    create_time      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at       TIMESTAMP,
    completed_at     TIMESTAMP,
    deleted          SMALLINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_task_user_id ON task (user_id);
CREATE INDEX idx_task_status ON task (status);
CREATE INDEX idx_task_type ON task (type);
CREATE INDEX idx_task_next_retry ON task (next_retry_at)
    WHERE next_retry_at IS NOT NULL AND deleted = 0;
CREATE INDEX idx_task_dead_letter ON task (dead_letter)
    WHERE dead_letter = 1 AND deleted = 0;
COMMENT ON TABLE task IS '任务队列表';
COMMENT ON COLUMN task.attempts IS '已尝试次数（含首次执行）';
COMMENT ON COLUMN task.max_attempts IS '最大尝试次数（含首次执行）';
COMMENT ON COLUMN task.next_retry_at IS '下次重试时间，NULL 表示无延迟重试';
COMMENT ON COLUMN task.stream_id IS 'Redis Stream 消息 ID';
COMMENT ON COLUMN task.dead_letter IS '是否已转入死信 Stream：0=否，1=是';

-- ========================================
-- API Key 管理表
-- ========================================

-- ----------------------------------------
-- 表：api_key
-- ----------------------------------------
CREATE TABLE api_key (
    id              BIGINT          NOT NULL,
    user_id         BIGINT          NOT NULL,
    name            VARCHAR(64)     NOT NULL,
    key_prefix      VARCHAR(20)     NOT NULL,
    key_hash        VARCHAR(64)     NOT NULL,
    permissions     VARCHAR(32)     NOT NULL DEFAULT 'chat',
    is_enabled      SMALLINT        NOT NULL DEFAULT 1,
    last_used_at    TIMESTAMP       NULL,
    expires_at      TIMESTAMP       NULL,
    agent_ids       JSONB           DEFAULT NULL,
    rate_limit      INT             NOT NULL DEFAULT 60,
    daily_quota     INT             NOT NULL DEFAULT 100000,
    used_tokens     BIGINT          NOT NULL DEFAULT 0,
    quota_reset_at  DATE            DEFAULT NULL,
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_api_key_user_id ON api_key (user_id);
CREATE UNIQUE INDEX uk_api_key_hash ON api_key (key_hash);
COMMENT ON TABLE api_key IS 'API Key管理表';
COMMENT ON COLUMN api_key.agent_ids IS '绑定的Agent ID列表，null表示全部';
COMMENT ON COLUMN api_key.rate_limit IS '每分钟调用上限，默认60';
COMMENT ON COLUMN api_key.daily_quota IS '每日Token配额，默认100000';
COMMENT ON COLUMN api_key.used_tokens IS '当日已用Token数';
COMMENT ON COLUMN api_key.quota_reset_at IS '配额重置日期（每日重置时比较）';

-- LightBot schema：模型与 Prompt
-- model_provider / model / prompt* / llm_trace

-- ============================================================
-- 模块：模型与 Prompt
-- ============================================================

-- ----------------------------------------
-- 表：model_provider
-- ----------------------------------------
CREATE TABLE model_provider (
    id              BIGINT          NOT NULL,
    name            VARCHAR(64)     NOT NULL,
    type            VARCHAR(32)     NOT NULL,
    api_key         VARCHAR(512),
    base_url        VARCHAR(256),
    config          JSONB           DEFAULT '{}',
    status          VARCHAR(20)     NOT NULL DEFAULT 'active',
    models_endpoint VARCHAR(512),
    headers_json    JSONB           DEFAULT '{}',
    extra_json      JSONB           DEFAULT '{}',
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_model_provider_type ON model_provider (type);
CREATE INDEX idx_model_provider_status ON model_provider (status);
COMMENT ON TABLE model_provider IS '模型提供商表';
COMMENT ON COLUMN model_provider.models_endpoint IS '模型列表获取地址（为空时使用默认地址）';
COMMENT ON COLUMN model_provider.headers_json IS '额外请求头（JSON格式）';
COMMENT ON COLUMN model_provider.extra_json IS '扩展配置（JSON格式）';

-- ----------------------------------------
-- 表：model
-- ----------------------------------------
CREATE TABLE model (
    id              BIGINT          NOT NULL,
    provider_id     BIGINT          NOT NULL,
    model_id        VARCHAR(128)    NOT NULL,
    name            VARCHAR(128)    NOT NULL,
    type            VARCHAR(20)     NOT NULL DEFAULT 'llm',
    status          VARCHAR(20)     NOT NULL DEFAULT 'active',
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_model_provider_id ON model (provider_id);
CREATE INDEX idx_model_type ON model (type);
COMMENT ON TABLE model IS '模型表';

-- ----------------------------------------
-- 表：prompt
-- ----------------------------------------
CREATE TABLE prompt (
    id              BIGINT          NOT NULL,
    prompt_key      VARCHAR(128)    NOT NULL,
    description     VARCHAR(512),
    latest_version  VARCHAR(32),
    tags            VARCHAR(512),
    user_id         BIGINT,
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_prompt_key ON prompt (prompt_key) WHERE deleted = 0;
CREATE INDEX idx_prompt_user_id ON prompt (user_id);
COMMENT ON TABLE prompt IS 'Prompt定义表';

-- ----------------------------------------
-- 表：prompt_version
-- ----------------------------------------
CREATE TABLE prompt_version (
    id              BIGINT          NOT NULL,
    prompt_key      VARCHAR(128)    NOT NULL,
    version         VARCHAR(32)     NOT NULL,
    version_desc    VARCHAR(512),
    template        TEXT            NOT NULL,
    variables       JSONB           DEFAULT '{}',
    model_config    JSONB           DEFAULT '{}',
    tool_config     JSONB           DEFAULT '{}',
    status          VARCHAR(20)     NOT NULL DEFAULT 'pre',
    user_id         BIGINT,
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_prompt_version ON prompt_version (prompt_key, version) WHERE deleted = 0;
CREATE INDEX idx_prompt_version_key ON prompt_version (prompt_key);
COMMENT ON TABLE prompt_version IS 'Prompt版本表';
COMMENT ON COLUMN prompt_version.tool_config IS '工具配置（JSON格式，存储Prompt关联的工具列表）';

-- ----------------------------------------
-- 表：prompt_build_template
-- ----------------------------------------
CREATE TABLE prompt_build_template (
    id                      BIGINT          NOT NULL,
    prompt_template_key     VARCHAR(128)    NOT NULL,
    tags                    VARCHAR(256),
    template_desc           VARCHAR(512),
    template                TEXT            NOT NULL,
    variables               VARCHAR(1024),
    model_config            JSONB           DEFAULT '{}',
    tool_config             JSONB           DEFAULT '{}',
    create_time             TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time             TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted                 SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_prompt_build_template_key ON prompt_build_template (prompt_template_key) WHERE deleted = 0;
COMMENT ON TABLE prompt_build_template IS 'Prompt构建模板表';
COMMENT ON COLUMN prompt_build_template.tool_config IS '工具配置（JSON格式）';

-- ============================================================
-- 模块：工具与扩展
-- ============================================================

-- ----------------------------------------
-- 表：tool
-- ----------------------------------------
CREATE TABLE tool (
    id              BIGINT          NOT NULL,
    user_id         BIGINT,
    name            VARCHAR(64)     NOT NULL,
    display_name    VARCHAR(128),
    description     TEXT,
    tool_type       VARCHAR(32)     NOT NULL DEFAULT 'builtin',
    input_schema    JSONB           NOT NULL DEFAULT '{}',
    output_schema   JSONB           DEFAULT '{}',
    config          JSONB           DEFAULT '{}',
    endpoint_url    VARCHAR(512),
    auth_type       VARCHAR(32),
    auth_config     JSONB           DEFAULT '{}',
    status          VARCHAR(20)     NOT NULL DEFAULT 'active',
    tags            JSONB           NOT NULL DEFAULT '[]',
    icon            VARCHAR(64),
    output_example  JSONB           DEFAULT '{}',
    rate_limit_enabled BOOLEAN      NOT NULL DEFAULT FALSE,
    rate_limit_config JSONB,
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_tool_name ON tool (name) WHERE deleted = 0;
CREATE INDEX idx_tool_type ON tool (tool_type);
CREATE INDEX idx_tool_status ON tool (status);
CREATE INDEX idx_tool_tags ON tool USING GIN (tags);
COMMENT ON TABLE tool IS 'Tool表';
COMMENT ON COLUMN tool.tags IS '工具标签（JSONB数组）';
COMMENT ON COLUMN tool.icon IS '图标标识（Ant Design 图标组件名）';
COMMENT ON COLUMN tool.output_example IS '输出示例 JSON';
COMMENT ON COLUMN tool.rate_limit_enabled IS '是否启用限流';
COMMENT ON COLUMN tool.rate_limit_config IS '限流配置 JSON：{"limit":10,"window":"MINUTE|HOUR|DAY"}';

-- ----------------------------------------
-- 表：mcp_server
-- ----------------------------------------
CREATE TABLE mcp_server (
    id              BIGINT          NOT NULL,
    name            VARCHAR(128)    NOT NULL,
    description     VARCHAR(512),
    install_type    VARCHAR(20)     NOT NULL,
    deploy_config   JSONB,
    detail_config   JSONB,
    host            VARCHAR(256),
    status          VARCHAR(20)     NOT NULL DEFAULT 'active',
    user_id         BIGINT,
    transport       VARCHAR(20)     NOT NULL DEFAULT 'sse',
    headers         JSONB,
    disabled_tools  JSONB,
    icon            VARCHAR(64),
    is_builtin      SMALLINT        NOT NULL DEFAULT 0,
    last_sync_time  TIMESTAMP,
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_mcp_server_user_id ON mcp_server (user_id);
CREATE INDEX idx_mcp_server_is_builtin ON mcp_server (is_builtin);
COMMENT ON TABLE mcp_server IS 'MCP Server表';
COMMENT ON COLUMN mcp_server.transport IS '传输类型: sse, stdio, streamable_http';
COMMENT ON COLUMN mcp_server.headers IS 'HTTP请求头(JSONB)，用于SSE/Streamable HTTP认证';
COMMENT ON COLUMN mcp_server.disabled_tools IS '禁用的工具名列表(JSONB数组)';
COMMENT ON COLUMN mcp_server.icon IS '图标标识（Ant Design 图标组件名）';
COMMENT ON COLUMN mcp_server.is_builtin IS '是否平台内置：1=是，0=否';
COMMENT ON COLUMN mcp_server.last_sync_time IS '最后一次工具列表同步时间';

-- LightBot schema：Agent 与会话

-- ----------------------------------------
-- 表：skill
-- ----------------------------------------
CREATE TABLE skill (
    id              BIGINT          NOT NULL,
    agent_id        BIGINT,
    name            VARCHAR(128)    NOT NULL,
    description     TEXT,
    prompt_template TEXT,
    config          JSONB           DEFAULT '{}',
    sort_order      INT             NOT NULL DEFAULT 0,
    status          VARCHAR(20)     NOT NULL DEFAULT 'active',
    slug            VARCHAR(128),
    display_name    VARCHAR(128),
    tool_ids        JSONB           NOT NULL DEFAULT '[]',
    mcp_server_ids  JSONB           NOT NULL DEFAULT '[]',
    model_id        BIGINT,
    scope           VARCHAR(20)     NOT NULL DEFAULT 'global',
    is_builtin      SMALLINT        NOT NULL DEFAULT 0,
    content_hash    VARCHAR(128),
    object_prefix   VARCHAR(256),
    version         VARCHAR(64)     DEFAULT '1.0.0',
    skill_dependencies JSONB        DEFAULT '[]',
    source_type     VARCHAR(20)     DEFAULT 'builtin',
    icon            VARCHAR(64),
    user_id         BIGINT,
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_skill_agent_id ON skill (agent_id);
CREATE UNIQUE INDEX uk_skill_slug ON skill (slug) WHERE slug IS NOT NULL AND deleted = 0;
CREATE INDEX idx_skill_is_builtin ON skill (is_builtin);
CREATE INDEX idx_skill_scope ON skill (scope);
CREATE INDEX idx_skill_source_type ON skill (source_type);
COMMENT ON TABLE skill IS 'Skill表';
COMMENT ON COLUMN skill.slug IS '全局唯一标识（英文-小写-短横线），全局 Skill 必填';
COMMENT ON COLUMN skill.display_name IS '显示名称（中文）';
COMMENT ON COLUMN skill.tool_ids IS '依赖的 Tool ID 列表（JSON 数组，字符串形式）';
COMMENT ON COLUMN skill.mcp_server_ids IS '依赖的 MCP Server ID 列表（JSON 数组，字符串形式）';
COMMENT ON COLUMN skill.model_id IS '可选的模型覆盖（保留字段，当前未启用）';
COMMENT ON COLUMN skill.scope IS '作用域：global=全局可复用；agent=旧的按 Agent 私有（兼容）';
COMMENT ON COLUMN skill.is_builtin IS '是否内置：1=是（不可编辑/删除），0=否';
COMMENT ON COLUMN skill.content_hash IS '内置 Skill 内容 hash，用于检测代码版本变化';
COMMENT ON COLUMN skill.object_prefix IS 'MinIO 路径前缀，如 skills/{slug}/';
COMMENT ON COLUMN skill.version IS '语义版本号';
COMMENT ON COLUMN skill.skill_dependencies IS '依赖其他 Skill 的 slug 列表';
COMMENT ON COLUMN skill.source_type IS '来源类型: builtin/upload/remote';
COMMENT ON COLUMN skill.icon IS '图标标识（Ant Design 图标组件名）';
COMMENT ON COLUMN skill.user_id IS '创建者ID（global skill 可为空）';

-- ----------------------------------------
-- 表：tool_calls
-- ----------------------------------------
CREATE TABLE tool_calls (
    id              BIGINT          NOT NULL,
    message_id      BIGINT,
    tool_name       VARCHAR(100)    NOT NULL,
    tool_input      JSONB,
    tool_output     TEXT,
    status          VARCHAR(20)     NOT NULL DEFAULT 'pending',
    error_message   TEXT,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX ix_tool_calls_message_id ON tool_calls (message_id);
COMMENT ON TABLE tool_calls IS '工具调用记录表';
COMMENT ON COLUMN tool_calls.message_id IS '关联消息ID';
COMMENT ON COLUMN tool_calls.tool_name IS '工具名称';
COMMENT ON COLUMN tool_calls.tool_input IS '工具输入参数';
COMMENT ON COLUMN tool_calls.tool_output IS '工具执行结果';
COMMENT ON COLUMN tool_calls.status IS '状态: pending/success/error';
COMMENT ON COLUMN tool_calls.error_message IS '错误信息';

CREATE INDEX idx_tool_calls_created_at ON tool_calls (created_at DESC);

-- ============================================================
-- 模块：Agent 与会话
-- ============================================================

-- ----------------------------------------
-- 表：agent
-- ----------------------------------------
CREATE TABLE agent (
    id              BIGINT          NOT NULL,
    user_id         BIGINT          NOT NULL,
    name            VARCHAR(128)    NOT NULL,
    description     TEXT,
    system_prompt   TEXT,
    avatar          VARCHAR(512),
    icon            VARCHAR(32),
    agent_type      VARCHAR(32)     NOT NULL DEFAULT 'chat',
    config          JSONB           DEFAULT '{}',
    status          VARCHAR(20)     NOT NULL DEFAULT 'draft',
    publish_time    TIMESTAMP,
    version         INT             NOT NULL DEFAULT 1,
    welcome_message TEXT,
    recommended_questions JSONB,
    is_default      BOOLEAN         NOT NULL DEFAULT FALSE,
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_agent_user_id ON agent (user_id);
CREATE INDEX idx_agent_status ON agent (status);
CREATE INDEX idx_agent_create_time ON agent (create_time);
COMMENT ON TABLE agent IS 'Agent表';
COMMENT ON COLUMN agent.icon IS 'Agent图标（emoji或图标标识）';
COMMENT ON COLUMN agent.welcome_message IS '欢迎语';
COMMENT ON COLUMN agent.recommended_questions IS '推荐问题列表';

-- ----------------------------------------
-- 表：agent_version
-- ----------------------------------------
CREATE TABLE agent_version (
    id              BIGINT          NOT NULL,
    agent_id        BIGINT          NOT NULL,
    user_id         BIGINT          NOT NULL,
    version         INT             NOT NULL DEFAULT 0,
    status          VARCHAR(32)     NOT NULL DEFAULT 'draft',
    config          JSONB           NOT NULL DEFAULT '{}',
    node_count      INT             NOT NULL DEFAULT 0,
    edge_count      INT             NOT NULL DEFAULT 0,
    description     VARCHAR(512),
    publish_time    TIMESTAMP,
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_agent_version_agent_id ON agent_version (agent_id);
CREATE INDEX idx_agent_version_agent_status ON agent_version (agent_id, status);
CREATE UNIQUE INDEX uk_agent_version_agent_pub ON agent_version (agent_id, version)
    WHERE status = 'published' AND deleted = 0;
COMMENT ON TABLE agent_version IS 'Agent版本配置表（草稿与发布历史）';
COMMENT ON COLUMN agent_version.version IS '发布版本号，草稿行为0';
COMMENT ON COLUMN agent_version.status IS 'draft=当前草稿 published=已发布历史版本';
COMMENT ON COLUMN agent_version.config IS '版本快照JSON（workflow图或对话配置）';

-- ----------------------------------------
-- 表：subagent
-- ----------------------------------------
CREATE TABLE subagent (
    id                      BIGINT          NOT NULL,
    name                    VARCHAR(128)    NOT NULL,
    display_name            VARCHAR(128)    NOT NULL,
    description             TEXT            NOT NULL,
    system_prompt           TEXT            NOT NULL,
    tool_ids                JSONB           NOT NULL DEFAULT '[]',
    model_id                BIGINT,
    llm_model               VARCHAR(128),
    connect_timeout_seconds INTEGER         NOT NULL DEFAULT 10,
    read_timeout_seconds    INTEGER         NOT NULL DEFAULT 60,
    model_retry_times       INTEGER         NOT NULL DEFAULT 1,
    enabled                 SMALLINT        NOT NULL DEFAULT 1,
    is_builtin              SMALLINT        NOT NULL DEFAULT 0,
    icon                    VARCHAR(64),
    user_id                 BIGINT,
    create_time             TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time             TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted                 SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_subagent_name_alive ON subagent (name) WHERE deleted = 0;
CREATE INDEX idx_subagent_enabled ON subagent (enabled);
CREATE INDEX idx_subagent_is_builtin ON subagent (is_builtin);
COMMENT ON TABLE subagent IS '子智能体配置表';
COMMENT ON COLUMN subagent.name IS '唯一标识（英文）';
COMMENT ON COLUMN subagent.display_name IS '显示名称（中文）';
COMMENT ON COLUMN subagent.description IS '子智能体描述';
COMMENT ON COLUMN subagent.system_prompt IS '系统提示词';
COMMENT ON COLUMN subagent.tool_ids IS '绑定工具ID列表（JSON数组）';
COMMENT ON COLUMN subagent.model_id IS '可选的 Provider ID 覆盖，null 表示继承主 Agent';
COMMENT ON COLUMN subagent.llm_model IS '可选的模型名称覆盖（如 gpt-4o），与 model_id 配合使用';
COMMENT ON COLUMN subagent.connect_timeout_seconds IS 'SubAgent 模型连接超时（秒），默认 10';
COMMENT ON COLUMN subagent.read_timeout_seconds IS '流式 token 间隔超时（秒），默认 60';
COMMENT ON COLUMN subagent.model_retry_times IS 'SubAgent 模型调用失败重试次数，默认 1（即最多再试 1 次）';
COMMENT ON COLUMN subagent.enabled IS '是否启用';
COMMENT ON COLUMN subagent.is_builtin IS '是否内置';
COMMENT ON COLUMN subagent.icon IS '图标标识（Ant Design 图标组件名）';
COMMENT ON COLUMN subagent.user_id IS '创建者ID';

-- ============================================================
-- 模块：知识库与图谱
-- ============================================================

-- ----------------------------------------
-- 表：knowledge
-- ----------------------------------------
CREATE TABLE knowledge (
    id              BIGINT          NOT NULL,
    user_id         BIGINT          NOT NULL,
    name            VARCHAR(128)    NOT NULL,
    description     TEXT,
    embedding_model VARCHAR(64)     NOT NULL DEFAULT 'text-embedding-3-small',
    type            VARCHAR(32)     NOT NULL DEFAULT 'pg',
    config          JSONB           DEFAULT '{}',
    query_params    JSONB           DEFAULT '{}',
    document_count  INT             NOT NULL DEFAULT 0,
    chunk_count     INT             NOT NULL DEFAULT 0,
    total_tokens    BIGINT          NOT NULL DEFAULT 0,
    status          VARCHAR(20)     NOT NULL DEFAULT 'active',
    mindmap_data    JSONB,
    example_questions JSONB         DEFAULT '[]',
    graph_enabled   BOOLEAN         NOT NULL DEFAULT FALSE,
    node_count      INTEGER         NOT NULL DEFAULT 0,
    edge_count      INTEGER         NOT NULL DEFAULT 0,
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_knowledge_user_id ON knowledge (user_id);
CREATE INDEX idx_knowledge_status ON knowledge (status);
CREATE INDEX idx_knowledge_type ON knowledge (type);
COMMENT ON TABLE knowledge IS '知识库表';
COMMENT ON COLUMN knowledge.type IS '知识库类型：pg / milvus';
COMMENT ON COLUMN knowledge.query_params IS '检索配置（JSONB）';
COMMENT ON COLUMN knowledge.mindmap_data IS '思维导图数据（JSON格式树状结构）';
COMMENT ON COLUMN knowledge.example_questions IS '示例问题列表（JSON数组）';
COMMENT ON COLUMN knowledge.graph_enabled IS '是否启用知识图谱';
COMMENT ON COLUMN knowledge.node_count IS '图谱节点数';
COMMENT ON COLUMN knowledge.edge_count IS '图谱边数';

-- ----------------------------------------
-- 表：knowledge_member
-- ----------------------------------------
CREATE TABLE knowledge_member (
    id              BIGINT          NOT NULL,
    knowledge_id    BIGINT          NOT NULL,
    user_id         BIGINT          NOT NULL,
    role            VARCHAR(20)     NOT NULL DEFAULT 'viewer',
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_knowledge_member ON knowledge_member (knowledge_id, user_id);
CREATE INDEX idx_knowledge_member_user_id ON knowledge_member (user_id);
COMMENT ON TABLE knowledge_member IS '知识库成员表';

-- ----------------------------------------
-- 表：document
-- ----------------------------------------
CREATE TABLE document (
    id              BIGINT          NOT NULL,
    knowledge_id    BIGINT          NOT NULL,
    user_id         BIGINT          NOT NULL,
    name            VARCHAR(256)    NOT NULL,
    file_path       VARCHAR(512),
    file_type       VARCHAR(32),
    file_size       BIGINT,
    file_hash       VARCHAR(64),
    chunk_count     INT             NOT NULL DEFAULT 0,
    token_count     BIGINT          NOT NULL DEFAULT 0,
    status          VARCHAR(20)     NOT NULL DEFAULT 'uploaded',
    error_message   TEXT,
    metadata        JSONB           DEFAULT '{}',
    markdown_path   VARCHAR(512),
    embedding_json  JSONB,
    duplicate_rate  DOUBLE PRECISION,
    duplicate_details JSONB,
    version         INTEGER         NOT NULL DEFAULT 1,
    last_edit_time  TIMESTAMP,
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_document_knowledge_id ON document (knowledge_id);
CREATE INDEX idx_document_user_id ON document (user_id);
CREATE INDEX idx_document_status ON document (status);
CREATE INDEX idx_document_file_hash ON document (file_hash);
COMMENT ON TABLE document IS '文档表';
COMMENT ON COLUMN document.markdown_path IS 'Markdown文件存储路径';
COMMENT ON COLUMN document.embedding_json IS '入库配置（chunkStrategy/chunkSize/chunkOverlap/chunkDelimiter）';
COMMENT ON COLUMN document.duplicate_rate IS '内容重复率（与知识库已有文档的最高相似度）';
COMMENT ON COLUMN document.duplicate_details IS '重复文档详情（top3，含文档名和相似度）';
COMMENT ON COLUMN document.version IS '文档内容版本号，每次编辑递增';
COMMENT ON COLUMN document.last_edit_time IS '最后一次在线编辑时间';

-- ----------------------------------------
-- 表：chunk
-- ----------------------------------------
CREATE TABLE chunk (
    id              BIGINT          NOT NULL,
    document_id     BIGINT          NOT NULL,
    knowledge_id    BIGINT          NOT NULL,
    content         TEXT            NOT NULL,
    chunk_index     INT             NOT NULL,
    token_count     INT             NOT NULL DEFAULT 0,
    metadata        JSONB           DEFAULT '{}',
    status          VARCHAR(20)     NOT NULL DEFAULT 'chunked',
    content_tsv     tsvector,
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX idx_chunk_document_id ON chunk (document_id);
CREATE INDEX idx_chunk_knowledge_id ON chunk (knowledge_id);
CREATE INDEX idx_chunk_status ON chunk (status);
CREATE INDEX idx_chunk_content_tsv ON chunk USING GIN(content_tsv);
COMMENT ON TABLE chunk IS '文档分块表';
COMMENT ON COLUMN chunk.status IS '向量化状态: chunked/vectorizing/vectorized/failed';

-- content_tsv 自动维护触发器
CREATE OR REPLACE FUNCTION update_chunk_content_tsv() RETURNS TRIGGER AS $$
BEGIN
    NEW.content_tsv := to_tsvector('simple', NEW.content);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_chunk_content_tsv
    BEFORE INSERT OR UPDATE OF content ON chunk
    FOR EACH ROW EXECUTE FUNCTION update_chunk_content_tsv();

-- ----------------------------------------
-- 表：qa_pair
-- ----------------------------------------
CREATE TABLE qa_pair (
    id              BIGINT          NOT NULL,
    knowledge_id    BIGINT          NOT NULL,
    question        TEXT            NOT NULL,
    answer          TEXT            NOT NULL,
    source          VARCHAR(20)     NOT NULL DEFAULT 'manual',
    status          VARCHAR(20)     NOT NULL DEFAULT 'pending',
    token_count     INTEGER         NOT NULL DEFAULT 0,
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_qa_pair_knowledge_id ON qa_pair (knowledge_id);
CREATE INDEX idx_qa_pair_status ON qa_pair (status);
COMMENT ON TABLE qa_pair IS '知识库问答对';
COMMENT ON COLUMN qa_pair.source IS '来源：manual-手动创建、import-批量导入、ai-AI生成';
COMMENT ON COLUMN qa_pair.status IS '状态：pending-待向量化、vectorizing-向量化中、active-生效、failed-失败';

-- ----------------------------------------
-- 表：embedding
-- ----------------------------------------
CREATE TABLE embedding (
    id              BIGINT          NOT NULL,
    chunk_id        BIGINT,
    qa_pair_id      BIGINT,
    model_name      VARCHAR(64)     NOT NULL,
    dimension       INT             NOT NULL DEFAULT 1536,
    vector          vector(1536)    NOT NULL,
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_embedding_chunk_id ON embedding (chunk_id) WHERE chunk_id IS NOT NULL;
CREATE UNIQUE INDEX uk_embedding_qa_pair_id ON embedding (qa_pair_id) WHERE qa_pair_id IS NOT NULL;
CREATE INDEX idx_embedding_vector_hnsw ON embedding
    USING hnsw (vector vector_cosine_ops)
    WITH (m = 16, ef_construction = 200);
COMMENT ON TABLE embedding IS '向量表';
COMMENT ON COLUMN embedding.qa_pair_id IS '关联问答对ID，与chunk_id互斥';

-- ----------------------------------------
-- 表：graph_extraction_task
-- ----------------------------------------
CREATE TABLE graph_extraction_task (
    id              BIGINT          NOT NULL,
    knowledge_id    BIGINT          NOT NULL,
    document_id     BIGINT,
    status          VARCHAR(20)     NOT NULL DEFAULT 'pending',
    source          VARCHAR(20)     NOT NULL DEFAULT 'auto',
    entity_count    INTEGER         NOT NULL DEFAULT 0,
    relation_count  INTEGER         NOT NULL DEFAULT 0,
    error_message   TEXT,
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_graph_task_knowledge_id ON graph_extraction_task (knowledge_id);
CREATE INDEX idx_graph_task_status ON graph_extraction_task (status);
COMMENT ON TABLE graph_extraction_task IS '图谱抽取任务';
COMMENT ON COLUMN graph_extraction_task.source IS '来源：auto-自动抽取、import-手动导入';
COMMENT ON COLUMN graph_extraction_task.status IS '状态：pending-待处理、running-执行中、completed-已完成、failed-失败';

-- ----------------------------------------
-- 表：knowledge_graph
-- ----------------------------------------
CREATE TABLE knowledge_graph (
    id              BIGINT          NOT NULL,
    knowledge_id    BIGINT          NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'pending',
    node_count      INTEGER         NOT NULL DEFAULT 0,
    edge_count      INTEGER         NOT NULL DEFAULT 0,
    task_id         BIGINT,
    error_message   TEXT,
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_knowledge_graph_knowledge_id ON knowledge_graph (knowledge_id) WHERE deleted = 0;
COMMENT ON TABLE knowledge_graph IS '知识图谱（知识库级别）';
COMMENT ON COLUMN knowledge_graph.status IS '状态：pending-待处理、running-执行中、completed-已完成、failed-失败';
COMMENT ON COLUMN knowledge_graph.task_id IS '当前正在运行的异步任务ID';

-- ----------------------------------------
-- 表：graph_document
-- ----------------------------------------
CREATE TABLE graph_document (
    id              BIGINT          NOT NULL,
    graph_id        BIGINT          NOT NULL,
    document_id     BIGINT          NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'pending',
    entity_count    INTEGER         NOT NULL DEFAULT 0,
    relation_count  INTEGER         NOT NULL DEFAULT 0,
    error_message   TEXT,
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_graph_document_graph_id ON graph_document (graph_id);
CREATE UNIQUE INDEX uk_graph_document_graph_doc ON graph_document (graph_id, document_id) WHERE deleted = 0;
COMMENT ON TABLE graph_document IS '图谱文档关联（记录每个文档的图谱抽取状态）';
COMMENT ON COLUMN graph_document.status IS '状态：pending-待处理、running-执行中、completed-已完成、failed-失败';

-- ----------------------------------------
-- 表：document_version
-- ----------------------------------------
CREATE TABLE document_version (
    id              BIGINT          NOT NULL,
    document_id     BIGINT          NOT NULL,
    version         INTEGER         NOT NULL,
    content_hash    VARCHAR(64),
    storage_path    VARCHAR(512)    NOT NULL,
    created_by      BIGINT,
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX idx_doc_version_doc ON document_version (document_id, version DESC);
COMMENT ON TABLE document_version IS '文档版本历史';

-- LightBot schema：工具与扩展
-- tool / skill / tool_calls / mcp_server

-- ============================================================
-- 模块：Agent 与会话
-- ============================================================

-- ----------------------------------------
-- 表：chat_session
-- ----------------------------------------
CREATE TABLE chat_session (
    id              BIGINT          NOT NULL,
    agent_id        BIGINT,
    user_id         BIGINT          NOT NULL,
    title           VARCHAR(256),
    status          VARCHAR(20)     NOT NULL DEFAULT 'active',
    context         JSONB           DEFAULT '{}',
    message_count   INT             NOT NULL DEFAULT 0,
    total_tokens    BIGINT          NOT NULL DEFAULT 0,
    last_message_at TIMESTAMP,
    pinned          BOOLEAN         NOT NULL DEFAULT FALSE,
    attachments     JSONB,
    agent_version_id BIGINT,
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_chat_session_agent_id ON chat_session (agent_id);
CREATE INDEX idx_chat_session_user_id ON chat_session (user_id);
CREATE INDEX idx_chat_session_status ON chat_session (status);
CREATE INDEX idx_chat_session_last_message ON chat_session (last_message_at DESC);
CREATE INDEX idx_chat_session_pinned ON chat_session (user_id, pinned DESC, last_message_at DESC);
COMMENT ON TABLE chat_session IS '对话会话表';
COMMENT ON COLUMN chat_session.attachments IS '会话附件索引 JSON 数组（source: user_upload|ai_image|ai_sandbox|ai_deliver）';
COMMENT ON COLUMN chat_session.agent_version_id IS '最近使用的Agent版本快照ID（agent_version.id），null=未指定';

CREATE INDEX idx_chat_session_user_agent ON chat_session (user_id, agent_id);

-- ----------------------------------------
-- 表：message
-- ----------------------------------------
CREATE TABLE message (
    id              BIGINT          NOT NULL,
    session_id      BIGINT          NOT NULL,
    role            VARCHAR(20)     NOT NULL,
    content         TEXT,
    content_type    VARCHAR(20)     NOT NULL DEFAULT 'text',
    message_type    VARCHAR(32)     NOT NULL DEFAULT 'text',
    token_count     INT             NOT NULL DEFAULT 0,
    metadata        JSONB           DEFAULT '{}',
    tool_events     JSONB           DEFAULT '[]',
    parent_id       BIGINT,
    reply_to_message_id BIGINT,
    starred         BOOLEAN         NOT NULL DEFAULT FALSE,
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX idx_message_session_id ON message (session_id);
CREATE INDEX idx_message_create_time ON message (session_id, create_time);
CREATE INDEX idx_message_role ON message (session_id, role);
CREATE INDEX idx_message_metadata_gin ON message USING GIN (metadata jsonb_path_ops);
CREATE INDEX idx_message_rag_refs ON message USING GIN ((metadata -> 'ragReferences') jsonb_path_ops);
CREATE INDEX idx_message_metadata_request_id
    ON message ((metadata ->> 'requestId'))
    WHERE metadata ? 'requestId';
CREATE INDEX idx_message_starred ON message (starred) WHERE starred = TRUE;
COMMENT ON TABLE message IS '消息表';
COMMENT ON COLUMN message.message_type IS '消息类型：text-文本, multimodal_image-多模态图片';
COMMENT ON COLUMN message.tool_events IS '工具事件流（tool_call、tool_result、subagent_* 等），与 metadata 解耦存储';
COMMENT ON COLUMN message.reply_to_message_id IS '引用回复的消息ID';
COMMENT ON COLUMN message.starred IS '是否收藏';

-- ========================================
-- 消息反馈表
-- ========================================

-- ----------------------------------------
-- 表：message_feedback
-- ----------------------------------------
CREATE TABLE message_feedback (
    id              BIGINT          NOT NULL,
    message_id      BIGINT          NOT NULL,
    user_id         BIGINT          NOT NULL,
    rating          VARCHAR(10)     NOT NULL,
    reason          TEXT,
    agent_id        BIGINT,
    agent_version   INT,
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

-- 唯一索引：每人每条消息只能有一条反馈
CREATE UNIQUE INDEX uk_message_feedback_user_message ON message_feedback (user_id, message_id);
CREATE INDEX idx_message_feedback_message_id ON message_feedback (message_id);
CREATE INDEX idx_message_feedback_agent_id ON message_feedback (agent_id);
CREATE INDEX idx_message_feedback_msg_rating ON message_feedback (message_id, rating);

COMMENT ON TABLE message_feedback IS '消息反馈表';
COMMENT ON COLUMN message_feedback.id IS '主键ID';
COMMENT ON COLUMN message_feedback.message_id IS '消息ID';
COMMENT ON COLUMN message_feedback.user_id IS '用户ID';
COMMENT ON COLUMN message_feedback.rating IS '评分：like/dislike';
COMMENT ON COLUMN message_feedback.reason IS '反馈原因（dislike时可选填写）';
COMMENT ON COLUMN message_feedback.agent_id IS '所属Agent ID（反馈提交时快照）';
COMMENT ON COLUMN message_feedback.agent_version IS 'Agent版本号（反馈提交时快照，0=草稿）';
COMMENT ON COLUMN message_feedback.create_time IS '创建时间';

-- ----------------------------------------
-- 表：user_memory
-- ----------------------------------------
CREATE TABLE user_memory (
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
CREATE INDEX idx_user_memory_user_status ON user_memory (user_id, status);
CREATE INDEX idx_user_memory_agent ON user_memory (agent_id);
CREATE INDEX idx_user_memory_type ON user_memory (memory_type);
CREATE INDEX idx_user_memory_vector_hnsw ON user_memory
    USING hnsw (embedding_vector vector_cosine_ops)
    WHERE embedding_vector IS NOT NULL AND deleted = 0;
COMMENT ON TABLE user_memory IS '用户长期记忆表';
COMMENT ON COLUMN user_memory.memory_type IS '记忆类型：preference/profile/project_fact/instruction';
COMMENT ON COLUMN user_memory.status IS '状态：active/disabled/archived';
COMMENT ON COLUMN user_memory.embedding_vector IS '记忆语义向量，用于长期记忆语义检索';

-- ========================================
-- SubAgent 运行记录表
-- ========================================

-- ----------------------------------------
-- 表：subagent_run
-- ----------------------------------------
CREATE TABLE subagent_run (
    id                  BIGINT          NOT NULL,
    thread_id           VARCHAR(100)    NOT NULL,
    parent_thread_id    VARCHAR(100)    NOT NULL,
    subagent_name       VARCHAR(100)    NOT NULL,
    task                TEXT            NOT NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'pending',
    request_id          VARCHAR(64)     NOT NULL,
    batch_id            VARCHAR(80),
    parent_request_id   VARCHAR(100),
    parent_session_id   BIGINT,
    mode                VARCHAR(20)     NOT NULL DEFAULT 'sync',
    cancel_requested    SMALLINT        NOT NULL DEFAULT 0,
    reply               TEXT,
    tool_call_count     INTEGER         DEFAULT 0,
    start_time          TIMESTAMP,
    end_time            TIMESTAMP,
    error_message       TEXT,
    create_time         TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX idx_subagent_run_request_id ON subagent_run(request_id);
CREATE INDEX idx_subagent_run_thread_id ON subagent_run(thread_id);
CREATE INDEX idx_subagent_run_batch_id ON subagent_run(batch_id);
CREATE INDEX idx_subagent_run_parent_request ON subagent_run(parent_request_id);
CREATE INDEX idx_subagent_run_status ON subagent_run(status);
COMMENT ON TABLE subagent_run IS 'SubAgent 运行记录表';
COMMENT ON COLUMN subagent_run.batch_id IS 'SubAgent 委派批次ID';
COMMENT ON COLUMN subagent_run.parent_request_id IS '父 Agent 请求ID';
COMMENT ON COLUMN subagent_run.parent_session_id IS '父 Agent 会话ID';
COMMENT ON COLUMN subagent_run.mode IS '委派模式：sync/parallel';
COMMENT ON COLUMN subagent_run.cancel_requested IS '是否请求取消：0否 1是';

-- ----------------------------------------
-- 表：subagent_task_batch
-- ----------------------------------------
CREATE TABLE subagent_task_batch (
    id                  BIGINT          NOT NULL,
    batch_id            VARCHAR(80)     NOT NULL,
    parent_request_id   VARCHAR(100),
    parent_thread_id    VARCHAR(100),
    parent_session_id   BIGINT,
    mode                VARCHAR(20)     NOT NULL,
    aggregation         VARCHAR(32)     NOT NULL DEFAULT 'return_all',
    status              VARCHAR(20)     NOT NULL DEFAULT 'pending',
    total_count         INTEGER         NOT NULL DEFAULT 0,
    completed_count     INTEGER         NOT NULL DEFAULT 0,
    failed_count        INTEGER         NOT NULL DEFAULT 0,
    cancelled_count     INTEGER         NOT NULL DEFAULT 0,
    cancel_requested    SMALLINT        NOT NULL DEFAULT 0,
    create_time         TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_subagent_task_batch_batch_id ON subagent_task_batch(batch_id);
CREATE INDEX idx_subagent_task_batch_parent_request ON subagent_task_batch(parent_request_id);
CREATE INDEX idx_subagent_task_batch_status ON subagent_task_batch(status);
COMMENT ON TABLE subagent_task_batch IS 'SubAgent 委派批次表';
COMMENT ON COLUMN subagent_task_batch.batch_id IS '批次ID';
COMMENT ON COLUMN subagent_task_batch.status IS '批次状态：pending/running/completed/failed/cancelled';
COMMENT ON COLUMN subagent_task_batch.cancel_requested IS '是否请求取消：0否 1是';

-- ----------------------------------------
-- 表：subagent_task_event
-- ----------------------------------------
CREATE TABLE subagent_task_event (
    id          BIGINT          NOT NULL,
    task_id     VARCHAR(100)    NOT NULL,
    batch_id    VARCHAR(80),
    event_type  VARCHAR(64)     NOT NULL,
    payload     TEXT            NOT NULL DEFAULT '{}',
    create_time TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX idx_subagent_task_event_task_cursor ON subagent_task_event(task_id, id);
CREATE INDEX idx_subagent_task_event_batch_id ON subagent_task_event(batch_id);
COMMENT ON TABLE subagent_task_event IS 'SubAgent任务运行事件表，支持游标增量读取';

-- LightBot schema：Workflow

-- ============================================================
-- 模块：模型与 Prompt
-- ============================================================

-- ----------------------------------------
-- 表：llm_trace
-- ----------------------------------------
CREATE TABLE llm_trace (
    id              BIGINT          NOT NULL,
    request_id      VARCHAR(64)     NOT NULL,
    session_id      BIGINT,
    user_id         BIGINT,
    agent_id        BIGINT,
    agent_name      VARCHAR(128),
    model           VARCHAR(128),
    status          VARCHAR(20)     NOT NULL DEFAULT 'running',
    input_tokens    INT             NOT NULL DEFAULT 0,
    output_tokens   INT             NOT NULL DEFAULT 0,
    total_tokens    INT             NOT NULL DEFAULT 0,
    tool_call_count INT             NOT NULL DEFAULT 0,
    total_duration_ms BIGINT        NOT NULL DEFAULT 0,
    spans           JSONB           DEFAULT '[]',
    error_message   TEXT,
    reply_content   TEXT,
    display_content TEXT,
    trace_source    VARCHAR(32),
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX idx_llm_trace_request_id ON llm_trace (request_id);
CREATE INDEX idx_llm_trace_session_id ON llm_trace (session_id);
CREATE INDEX idx_llm_trace_user_id ON llm_trace (user_id);
CREATE INDEX idx_llm_trace_create_time ON llm_trace (create_time);
CREATE INDEX idx_llm_trace_status ON llm_trace (status);
CREATE INDEX idx_llm_trace_trace_source ON llm_trace (trace_source);
COMMENT ON TABLE llm_trace IS 'LLM调用链追踪表';
COMMENT ON COLUMN llm_trace.request_id IS '请求ID（唯一标识一次AI对话）';
COMMENT ON COLUMN llm_trace.session_id IS '会话ID';
COMMENT ON COLUMN llm_trace.user_id IS '用户ID';
COMMENT ON COLUMN llm_trace.agent_id IS 'AgentID';
COMMENT ON COLUMN llm_trace.agent_name IS 'Agent名称';
COMMENT ON COLUMN llm_trace.model IS '模型标识';
COMMENT ON COLUMN llm_trace.status IS '状态: running/completed/failed';
COMMENT ON COLUMN llm_trace.input_tokens IS '输入Token数';
COMMENT ON COLUMN llm_trace.output_tokens IS '输出Token数';
COMMENT ON COLUMN llm_trace.total_tokens IS '总Token数';
COMMENT ON COLUMN llm_trace.tool_call_count IS '工具调用次数';
COMMENT ON COLUMN llm_trace.total_duration_ms IS '总耗时（毫秒）';
COMMENT ON COLUMN llm_trace.spans IS '调用链Span列表（JSONB）';
COMMENT ON COLUMN llm_trace.error_message IS '错误信息';
COMMENT ON COLUMN llm_trace.reply_content IS 'AI完整回复内容（模型原始输出，含深度思考标签，不做删改）';
COMMENT ON COLUMN llm_trace.display_content IS '最终展示内容（用户对话页可见正文，已剥离思考标签）';
COMMENT ON COLUMN llm_trace.trace_source IS '来源：chat=用户对话；辅助 LLM 调用不写入';

CREATE INDEX idx_llm_trace_source_time ON llm_trace (trace_source, create_time DESC);
CREATE INDEX idx_llm_trace_agent_source ON llm_trace (agent_id, trace_source);

-- LightBot schema：知识库与图谱

-- ============================================================
-- 模块：Workflow
-- ============================================================

-- ----------------------------------------
-- 表：workflow_test_run
-- ----------------------------------------
CREATE TABLE workflow_test_run (
    id              BIGINT          NOT NULL,
    run_id          VARCHAR(64)     NOT NULL,
    agent_id        BIGINT          NOT NULL,
    user_id         BIGINT          NOT NULL,
    test_mode       VARCHAR(20)     NOT NULL DEFAULT 'generation',
    used_draft      SMALLINT        NOT NULL DEFAULT 1,
    status          VARCHAR(20)     NOT NULL DEFAULT 'running',
    user_input      TEXT,
    output          TEXT,
    node_events     JSONB           NOT NULL DEFAULT '[]',
    variables       JSONB,
    workflow_graph  JSONB,
    error_info      TEXT,
    duration_ms     BIGINT,
    start_time      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    end_time        TIMESTAMP,
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_workflow_test_run_run_id ON workflow_test_run (run_id);
CREATE INDEX idx_workflow_test_run_agent_time ON workflow_test_run (agent_id, start_time DESC);
COMMENT ON TABLE workflow_test_run IS '工作流编排页测试运行记录';

-- LightBot schema：评测

-- ============================================================
-- 模块：评测
-- ============================================================

-- ----------------------------------------
-- 表：eval_dataset
-- ----------------------------------------
CREATE TABLE eval_dataset (
    id              BIGINT          NOT NULL,
    name            VARCHAR(128)    NOT NULL,
    description     VARCHAR(512),
    columns_config  JSONB           DEFAULT '[]',
    user_id         BIGINT,
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_eval_dataset_user_id ON eval_dataset (user_id);
COMMENT ON TABLE eval_dataset IS '评测集表';

-- ----------------------------------------
-- 表：eval_dataset_version
-- ----------------------------------------
CREATE TABLE eval_dataset_version (
    id              BIGINT          NOT NULL,
    dataset_id      BIGINT          NOT NULL,
    version         VARCHAR(32)     NOT NULL,
    data_count      INT             NOT NULL DEFAULT 0,
    status          VARCHAR(20)     NOT NULL DEFAULT 'draft',
    dataset_items   JSONB           DEFAULT '[]',
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_eval_dataset_version ON eval_dataset_version (dataset_id, version) WHERE deleted = 0;
CREATE INDEX idx_eval_dataset_version_dataset_id ON eval_dataset_version (dataset_id);
COMMENT ON TABLE eval_dataset_version IS '评测集版本表';

-- ----------------------------------------
-- 表：eval_dataset_item
-- ----------------------------------------
CREATE TABLE eval_dataset_item (
    id              BIGINT          NOT NULL,
    dataset_id      BIGINT          NOT NULL,
    data_content    JSONB           DEFAULT '{}',
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_eval_dataset_item_dataset_id ON eval_dataset_item (dataset_id);
COMMENT ON TABLE eval_dataset_item IS '评测数据项表';

-- ----------------------------------------
-- 表：eval_evaluator
-- ----------------------------------------
CREATE TABLE eval_evaluator (
    id              BIGINT          NOT NULL,
    name            VARCHAR(128)    NOT NULL,
    description     VARCHAR(512),
    tags            VARCHAR(200),
    user_id         BIGINT,
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_eval_evaluator_user_id ON eval_evaluator (user_id);
COMMENT ON TABLE eval_evaluator IS '评估器表';
COMMENT ON COLUMN eval_evaluator.tags IS '标签，逗号分隔';

-- ----------------------------------------
-- 表：eval_evaluator_version
-- ----------------------------------------
CREATE TABLE eval_evaluator_version (
    id              BIGINT          NOT NULL,
    evaluator_id    BIGINT          NOT NULL,
    version         VARCHAR(32)     NOT NULL,
    model_config    JSONB           DEFAULT '{}',
    prompt          TEXT,
    variables       JSONB           DEFAULT '{}',
    status          VARCHAR(20)     NOT NULL DEFAULT 'draft',
    create_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_eval_evaluator_version ON eval_evaluator_version (evaluator_id, version) WHERE deleted = 0;
CREATE INDEX idx_eval_evaluator_version_evaluator_id ON eval_evaluator_version (evaluator_id);
COMMENT ON TABLE eval_evaluator_version IS '评估器版本表';

-- ----------------------------------------
-- 表：eval_evaluator_template
-- ----------------------------------------
CREATE TABLE eval_evaluator_template (
    id                      BIGINT          NOT NULL,
    evaluator_template_key  VARCHAR(128)    NOT NULL,
    template_desc           VARCHAR(512),
    template                TEXT            NOT NULL,
    variables               VARCHAR(1024),
    model_config            JSONB           DEFAULT '{}',
    create_time             TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time             TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted                 SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_eval_evaluator_template_key ON eval_evaluator_template (evaluator_template_key) WHERE deleted = 0;
COMMENT ON TABLE eval_evaluator_template IS '评估器模板表';

-- ----------------------------------------
-- 表：eval_experiment
-- ----------------------------------------
CREATE TABLE eval_experiment (
    id                          BIGINT          NOT NULL,
    name                        VARCHAR(128)    NOT NULL,
    description                 VARCHAR(512),
    dataset_id                  BIGINT,
    dataset_version_id          BIGINT,
    dataset_version             VARCHAR(32),
    evaluation_object_config    JSONB           DEFAULT '{}',
    evaluator_config            JSONB           DEFAULT '[]',
    status                      VARCHAR(20)     NOT NULL DEFAULT 'draft',
    progress                    INT             NOT NULL DEFAULT 0,
    complete_time               TIMESTAMP,
    user_id                     BIGINT,
    task_id                     BIGINT,
    create_time                 TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time                 TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted                     SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_eval_experiment_user_id ON eval_experiment (user_id);
CREATE INDEX idx_eval_experiment_status ON eval_experiment (status);
COMMENT ON TABLE eval_experiment IS '评测实验表';

-- ----------------------------------------
-- 表：eval_experiment_result
-- ----------------------------------------
CREATE TABLE eval_experiment_result (
    id                      BIGINT          NOT NULL,
    experiment_id           BIGINT          NOT NULL,
    input                   TEXT,
    actual_output           TEXT,
    reference_output        TEXT,
    score                   DECIMAL(3,2),
    reason                  TEXT,
    evaluator_version_id    BIGINT,
    evaluator_name          VARCHAR(128),
    evaluation_time         TIMESTAMP,
    create_time             TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time             TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted                 SMALLINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_eval_experiment_result_experiment_id ON eval_experiment_result (experiment_id);
CREATE INDEX idx_eval_experiment_result_evaluator ON eval_experiment_result (evaluator_version_id);
COMMENT ON TABLE eval_experiment_result IS '实验结果表';

-- ----------------------------------------
-- 表：eval_rag_benchmark
-- ----------------------------------------
CREATE TABLE eval_rag_benchmark (
    id              BIGINT        NOT NULL,
    knowledge_id    BIGINT        NOT NULL,
    name            VARCHAR(128)  NOT NULL,
    description     VARCHAR(512),
    question_count  INT           NOT NULL DEFAULT 0,
    status          VARCHAR(20)   NOT NULL DEFAULT 'ready',
    create_time     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_eval_rag_benchmark_knowledge_id ON eval_rag_benchmark (knowledge_id);
COMMENT ON TABLE eval_rag_benchmark IS 'RAG 评估基准表';
COMMENT ON COLUMN eval_rag_benchmark.status IS '状态：generating-生成中, ready-就绪';

-- ----------------------------------------
-- 表：eval_rag_benchmark_item
-- ----------------------------------------
CREATE TABLE eval_rag_benchmark_item (
    id              BIGINT        NOT NULL,
    benchmark_id    BIGINT        NOT NULL,
    query           VARCHAR(2000) NOT NULL,
    gold_chunk_ids  VARCHAR(2000),
    gold_answer     TEXT,
    sort_order      INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_eval_rag_benchmark_item_benchmark_id ON eval_rag_benchmark_item (benchmark_id);
COMMENT ON TABLE eval_rag_benchmark_item IS 'RAG 评估基准题目表';

-- ----------------------------------------
-- 表：eval_rag_result
-- ----------------------------------------
CREATE TABLE eval_rag_result (
    id              BIGINT        NOT NULL,
    knowledge_id    BIGINT        NOT NULL,
    benchmark_id    BIGINT        NOT NULL,
    benchmark_name  VARCHAR(128),
    status          VARCHAR(20)   NOT NULL DEFAULT 'RUNNING',
    overall_score   DOUBLE PRECISION,
    retrieval_json  TEXT,
    answer_json     TEXT,
    config_json     TEXT,
    duration_ms     BIGINT,
    analysis        TEXT,
    error           TEXT,
    create_time     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_eval_rag_result_knowledge_id ON eval_rag_result (knowledge_id);
COMMENT ON TABLE eval_rag_result IS 'RAG 评估结果表';
COMMENT ON COLUMN eval_rag_result.analysis IS 'AI评估分析报告';

-- ----------------------------------------
-- 表：eval_rag_result_detail
-- ----------------------------------------
CREATE TABLE eval_rag_result_detail (
    id                  BIGINT        NOT NULL,
    result_id           BIGINT        NOT NULL,
    query               VARCHAR(2000) NOT NULL,
    gold_chunk_ids      VARCHAR(2000),
    gold_answer         TEXT,
    generated_answer    TEXT,
    retrieved_chunk_ids VARCHAR(2000),
    retrieval_scores    TEXT,
    answer_score        DOUBLE PRECISION,
    answer_reasoning    TEXT,
    sort_order          INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_eval_rag_result_detail_result_id ON eval_rag_result_detail (result_id);
COMMENT ON COLUMN eval_rag_result_detail.answer_reasoning IS 'RAG 评估结果详情表';
