-- SubAgent 任务事件流：支持按游标读取最近进度与独立子线程详情关联。
CREATE TABLE IF NOT EXISTS subagent_task_event (
    id          BIGINT          NOT NULL,
    task_id     VARCHAR(100)    NOT NULL,
    batch_id    VARCHAR(80),
    event_type  VARCHAR(64)     NOT NULL,
    payload     TEXT            NOT NULL DEFAULT '{}',
    create_time TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_subagent_task_event_task_cursor
    ON subagent_task_event(task_id, id);
CREATE INDEX IF NOT EXISTS idx_subagent_task_event_batch_id
    ON subagent_task_event(batch_id);

COMMENT ON TABLE subagent_task_event IS 'SubAgent任务运行事件表，支持游标增量读取';
