-- SubAgent 后台任务与批次状态：支持批量委派、后台查询、取消和观测

ALTER TABLE subagent_run ADD COLUMN IF NOT EXISTS batch_id VARCHAR(80);
ALTER TABLE subagent_run ADD COLUMN IF NOT EXISTS parent_request_id VARCHAR(100);
ALTER TABLE subagent_run ADD COLUMN IF NOT EXISTS parent_session_id BIGINT;
ALTER TABLE subagent_run ADD COLUMN IF NOT EXISTS mode VARCHAR(20) NOT NULL DEFAULT 'sync';
ALTER TABLE subagent_run ADD COLUMN IF NOT EXISTS cancel_requested SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE subagent_run ADD COLUMN IF NOT EXISTS update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_subagent_run_batch_id ON subagent_run(batch_id);
CREATE INDEX IF NOT EXISTS idx_subagent_run_parent_request ON subagent_run(parent_request_id);
CREATE INDEX IF NOT EXISTS idx_subagent_run_status ON subagent_run(status);

COMMENT ON COLUMN subagent_run.batch_id IS 'SubAgent 委派批次ID';
COMMENT ON COLUMN subagent_run.parent_request_id IS '父 Agent 请求ID';
COMMENT ON COLUMN subagent_run.parent_session_id IS '父 Agent 会话ID';
COMMENT ON COLUMN subagent_run.mode IS '委派模式：sync/parallel/background';
COMMENT ON COLUMN subagent_run.cancel_requested IS '是否请求取消：0否 1是';

CREATE TABLE IF NOT EXISTS subagent_task_batch (
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

CREATE UNIQUE INDEX IF NOT EXISTS uk_subagent_task_batch_batch_id ON subagent_task_batch(batch_id);
CREATE INDEX IF NOT EXISTS idx_subagent_task_batch_parent_request ON subagent_task_batch(parent_request_id);
CREATE INDEX IF NOT EXISTS idx_subagent_task_batch_status ON subagent_task_batch(status);

COMMENT ON TABLE subagent_task_batch IS 'SubAgent 委派批次表';
COMMENT ON COLUMN subagent_task_batch.batch_id IS '批次ID';
COMMENT ON COLUMN subagent_task_batch.status IS '批次状态：pending/running/completed/failed/cancelled';
COMMENT ON COLUMN subagent_task_batch.cancel_requested IS '是否请求取消：0否 1是';
