# LightBot 数据库迁移说明

LightBot 使用 PostgreSQL 15 和 `pgvector`。SQL 文件按发布时间排序，必须按文件名升序执行。

## 全新部署（v2.1.0）

`2026-07-12-init.sql` 是完整快照（含企业版 `chat_session.source` / `api_key_id`）；全新数据库只执行该文件。

```bash
psql -U postgres -h localhost -f sql/2026-07-12-init.sql
```

脚本会创建 `lightbot` 数据库、启用 `vector` 扩展并切换到该数据库。执行账号需要拥有建库和创建扩展的权限。

## 已有库升级（增量）

不要重跑初始化脚本。连接已有的 `lightbot` 数据库后，按文件名升序执行尚未应用的增量脚本，例如：

```bash
psql -U postgres -h localhost -d lightbot -f sql/2026-07-14-001.sql
psql -U postgres -h localhost -d lightbot -f sql/2026-07-18-001.sql
psql -U postgres -h localhost -d lightbot -f sql/2026-07-18-002.sql
psql -U postgres -h localhost -d lightbot -f sql/2026-07-20-001.sql
psql -U postgres -h localhost -d lightbot -f sql/2026-07-20-002.sql
psql -U postgres -h localhost -d lightbot -f sql/2026-07-21-001.sql
# 企业版会话来源（已有库必跑；init 快照已内含，全新部署无需重复）
psql -U postgres -h localhost -d lightbot -f sql/2026-07-30-001.sql
psql -U postgres -h localhost -d lightbot -f sql/2026-07-30-002.sql
```

## 增量迁移内容

| 脚本 | 变更 |
| --- | --- |
| `2026-07-14-001.sql` | MCP Server 内置标记及历史数据回填 |
| `2026-07-18-001.sql` | Redis Stream 任务重试、延迟队列、死信和 SubAgent 流式超时语义 |
| `2026-07-18-002.sql` | 将消息工具事件从 `metadata` 拆分到 `tool_events` |
| `2026-07-20-001.sql` | 增加 RAG 引用反馈聚合所需的 JSONB GIN 索引 |
| `2026-07-20-002.sql` | 优化消息查询索引、反馈索引和 SubAgent 软删除后的名称唯一性 |
| `2026-07-21-001.sql` | 新增工具维度限流配置 |
| `2026-07-30-001.sql` | `chat_session` 增加 `source` / `api_key_id`（平台调试 / API 集成 / 自动化分离） |
| `2026-07-30-002.sql` | 智能问数：`ask_dataset` / `ask_relation` 语义层表 |

## 发布约定

- 新迁移放在本目录，命名为 `YYYY-MM-DD-NNN.sql`。
- 全新部署快照与增量脚本必须保持一致；发布时同步更新初始化脚本、本文档和 [CHANGELOG](../CHANGELOG.md)。
- 生产执行前先备份数据库，并在预发布环境验证迁移耗时、索引创建和回滚方案。
