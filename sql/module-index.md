# LightBot SQL 模块索引

> 适用于 2.1 基线快照，更新于 **2026-07-28**。实际字段以 `2026-07-28-init.sql` 为准；预制数据见 `insert-sql.sql`。

## 使用方式

- 查找业务域表：按下表定位，或在 init SQL 中搜索 `-- 模块：` / `-- 表：`。
- 新增表：写入带日期的 init SQL 对应模块段（注意依赖顺序），并为存量库补充 `YYYY-MM-DD-NNN.sql`。
- 新增预制数据：写入 `insert-sql.sql`，标注模块与数据用途。

## 模块与表

| 模块 | 主要表 | 说明 |
| --- | --- | --- |
| 平台与账号 | `users`、`system_config`、`task`、`api_key` | 用户、配置、任务中心、API Key。 |
| 模型与 Prompt | `model_provider`、`model`、`prompt`、`prompt_version`、`prompt_build_template`、`llm_trace` | 模型、提示词与调用追踪。 |
| 工具与扩展 | `tool`、`mcp_server`、`skill`、`tool_calls` | 工具、MCP、Skill、调用记录。 |
| Agent 与会话 | `agent`、`agent_version`、`subagent`、`chat_session`、`message`、`message_feedback`、`user_memory`、`subagent_run`、`subagent_task_batch`、`subagent_task_event` | Agent 定义在前；会话/消息/运行表在知识库段之后。 |
| 知识库与图谱 | `knowledge`、`knowledge_member`、`document`、`chunk`、`qa_pair`、`embedding`、`graph_*`、`document_version` | 文档 → 分块/问答对 → 向量。 |
| Workflow | `workflow_test_run` | 工作流测试记录。 |
| 评测 | `eval_*` | 评测集、评估器、实验、RAG Benchmark。 |

## 预制数据（insert-sql.sql）

| 段落 | 内容 |
| --- | --- |
| 默认 Agent | `agent.id=1` |
| 系统配置 | 默认模型键、Landing 配置 |
| Prompt 模板 | `prompt_build_template` |
| 评估器模板 | `eval_evaluator_template` |

## 建表依赖说明

当前基线未声明数据库级 FOREIGN KEY。init SQL 仍按被引用方优先排序：账号/配置 → 模型/工具 → Agent → 知识库 → 会话/消息 → 评测。

## 维护原则

1. 基线只有两个 SQL：`YYYY-MM-DD-init.sql`（DDL）与 `insert-sql.sql`（INSERT）。
2. 存量变更走 `YYYY-MM-DD-NNN.sql`。
