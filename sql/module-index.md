# LightBot SQL 模块索引

> 适用于 2.1 基线快照，更新于 **2026-07-27**。实际字段、约束和索引以 `2026-07-12-init.sql` 与其后的增量迁移为准。

## 使用方式

- 查找某个业务域的数据表：按下表定位模块。
- 排查字段或索引变更：查看“关联迁移”。
- 新增表：先确认模块归属，再在新的日期迁移中创建；全新安装基线同步补齐。

## 模块与表

| 模块 | 主要表 | 关联迁移 | 说明 |
| --- | --- | --- | --- |
| 平台与账号 | `users`、`system_config`、`task`、`api_key` | `2026-07-18-001` | 用户、系统配置、任务中心、API Key 与任务重试/死信状态。 |
| 模型与 Prompt | `model_provider`、`model`、`prompt`、`prompt_version`、`prompt_build_template`、`llm_trace` | — | 模型提供商、模型、提示词版本和 LLM 调用追踪。 |
| 知识库与图谱 | `knowledge`、`knowledge_member`、`document`、`document_version`、`chunk`、`embedding`、`qa_pair`、`graph_extraction_task`、`knowledge_graph`、`graph_document` | `2026-07-20-001`、`2026-07-20-002` | 文档、分块、向量、问答对和知识图谱。Dify Dataset 只读连接保存于 `knowledge.config`，不新增独立凭证表。 |
| 工具与扩展 | `tool`、`tool_calls`、`skill`、`mcp_server` | `2026-07-14-001`、`2026-07-21-001` | 内置/API 工具、调用记录、Skill、MCP Server 与工具限流。 |
| Agent 与会话 | `agent`、`agent_version`、`chat_session`、`message`、`message_feedback`、`user_memory`、`subagent`、`subagent_run`、`subagent_task_batch`、`subagent_task_event` | `2026-07-18-001`、`2026-07-18-002`、`2026-07-20-001`、`2026-07-20-002` | Agent 版本、会话消息、反馈、长期记忆和 SubAgent 运行状态。 |
| Workflow | `workflow_test_run`，以及 `agent_version.config` 中的工作流快照 | — | 工作流图由 Agent 版本配置保存，测试/回放记录保存于 `workflow_test_run`。 |
| 评测 | `eval_dataset`、`eval_dataset_version`、`eval_dataset_item`、`eval_evaluator`、`eval_evaluator_version`、`eval_evaluator_template`、`eval_experiment`、`eval_experiment_result`、`eval_rag_benchmark`、`eval_rag_benchmark_item`、`eval_rag_result`、`eval_rag_result_detail` | — | 评测集、评估器、实验与 RAG Benchmark。 |

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
2. 增量迁移按发布日期保留；跨模块迁移在“关联迁移”中同时列出。
3. 基线快照的文件名是其基线起点日期，不代表当前文档日期；其内容已经合并截至 2026-07-21 的结构变更。
