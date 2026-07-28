# LightBot SQL 模块索引

> 适用于 2.1 基线快照，更新于 **2026-07-28**。实际字段、约束和索引以 `2026-07-28-init.sql` 与其后的增量迁移为准。

## 使用方式

- 查找某个业务域的数据表：按下表定位模块。
- 排查字段或索引：以基线 SQL 中对应 `CREATE TABLE` / 索引定义为准。
- 新增表：先确认模块归属，再在新的日期迁移中创建；全新安装基线同步补齐。

## 模块与表

| 模块 | 主要表 | 说明 |
| --- | --- | --- |
| 平台与账号 | `users`、`system_config`、`task`、`api_key` | 用户、系统配置、任务中心（含重试/死信）、API Key 配额与限流。 |
| 模型与 Prompt | `model_provider`、`model`、`prompt`、`prompt_version`、`prompt_build_template`、`llm_trace` | 模型提供商、模型、提示词版本和 LLM 调用追踪。 |
| 知识库与图谱 | `knowledge`、`knowledge_member`、`document`、`document_version`、`chunk`、`embedding`、`qa_pair`、`graph_extraction_task`、`knowledge_graph`、`graph_document` | 文档、分块、向量、问答对和知识图谱。Dify Dataset 只读连接保存于 `knowledge.config`，不新增独立凭证表。 |
| 工具与扩展 | `tool`、`tool_calls`、`skill`、`mcp_server` | 内置/API 工具、调用记录、Skill、MCP Server（含内置标识）与工具限流。 |
| Agent 与会话 | `agent`、`agent_version`、`chat_session`、`message`、`message_feedback`、`user_memory`、`subagent`、`subagent_run`、`subagent_task_batch`、`subagent_task_event` | Agent 版本、会话消息（含 `tool_events`/收藏）、反馈、长期记忆和 SubAgent 运行状态。 |
| Workflow | `workflow_test_run`，以及 `agent_version.config` 中的工作流快照 | 工作流图由 Agent 版本配置保存，测试/回放记录保存于 `workflow_test_run`。 |
| 评测 | `eval_dataset`、`eval_dataset_version`、`eval_dataset_item`、`eval_evaluator`、`eval_evaluator_version`、`eval_evaluator_template`、`eval_experiment`、`eval_experiment_result`、`eval_rag_benchmark`、`eval_rag_benchmark_item`、`eval_rag_result`、`eval_rag_result_detail` | 评测集、评估器、实验与 RAG Benchmark。 |

## 关键跨模块关系

```text
users
 ├── agent ── agent_version ── workflow_test_run
 │     └── chat_session ── message ── message_feedback
 ├── knowledge ── document ── chunk ── embedding
 ├── tool / skill / mcp_server
 └── eval_dataset / eval_evaluator / eval_experiment
```

`task` 为平台任务中心，承载文档处理、图谱抽取、评测等异步任务；`llm_trace` 和 `tool_calls` 分别记录模型与工具维度的运行轨迹。

## Dify 相关存储边界

| 功能 | 存储位置 | 原因 |
| --- | --- | --- |
| Dify Dataset 只读知识库 | `knowledge.config` | 与知识库生命周期一致，Dify Base URL、Dataset ID 与加密 API Key 随该知识库配置保存。 |
| Dify 工作流导入/导出 | `agent_version.config` | 导入后转换为 LightBot 工作流图，随 Agent 版本保存；导出时从当前工作流图生成 Dify YAML。 |
| 加密密钥 | 环境变量 `LIGHTBOT_DIFY_ENCRYPTION_KEY` | 不入库、不入 Git；用于加解密 `knowledge.config` 中的 Dify API Key。 |

## 维护原则

1. 此索引不替代 SQL 脚本，不复制 DDL。
2. 基线快照文件名表示当前快照日期；内容已合并此前增量结构变更。
3. 后续结构变更以新的 `YYYY-MM-DD-NNN.sql` 发布，并在合适时机重新折叠进基线。
