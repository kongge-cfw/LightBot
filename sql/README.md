# LightBot 数据库迁移说明

LightBot 使用 PostgreSQL 15 和 `pgvector`。SQL 文件按发布时间排序，必须按文件名升序执行。

## 全新部署（v2.1.0）

`2026-07-12-init.sql` 是 2.1 的完整快照，已合并截至 2026-07-21 的表结构、索引和数据修正；全新数据库只执行该文件。

```bash
psql -U postgres -h localhost -f sql/2026-07-12-init.sql
```

脚本会创建 `lightbot` 数据库、启用 `vector` 扩展并切换到该数据库。执行账号需要拥有建库和创建扩展的权限。

## 从 v2.0.0 升级到 v2.1.0

不要重跑初始化脚本。连接已有的 `lightbot` 数据库后，依次执行以下增量迁移：

```bash
psql -U postgres -h localhost -d lightbot -f sql/2026-07-14-001.sql
psql -U postgres -h localhost -d lightbot -f sql/2026-07-18-001.sql
psql -U postgres -h localhost -d lightbot -f sql/2026-07-18-002.sql
psql -U postgres -h localhost -d lightbot -f sql/2026-07-20-001.sql
psql -U postgres -h localhost -d lightbot -f sql/2026-07-20-002.sql
psql -U postgres -h localhost -d lightbot -f sql/2026-07-21-001.sql
```

## v2.1.0 迁移内容

| 脚本 | 变更 |
| --- | --- |
| `2026-07-14-001.sql` | MCP Server 内置标记及历史数据回填 |
| `2026-07-18-001.sql` | Redis Stream 任务重试、延迟队列、死信和 SubAgent 流式超时语义 |
| `2026-07-18-002.sql` | 将消息工具事件从 `metadata` 拆分到 `tool_events` |
| `2026-07-20-001.sql` | 增加 RAG 引用反馈聚合所需的 JSONB GIN 索引 |
| `2026-07-20-002.sql` | 优化消息查询索引、反馈索引和 SubAgent 软删除后的名称唯一性 |
| `2026-07-21-001.sql` | 新增工具维度限流配置 |

## 发布约定

- 新迁移放在本目录，命名为 `YYYY-MM-DD-NNN.sql`。
- 全新部署快照与增量脚本必须保持一致；发布时同步更新初始化脚本、本文档和 [CHANGELOG](../CHANGELOG.md)。
- 生产执行前先备份数据库，并在预发布环境验证迁移耗时、索引创建和回滚方案。
