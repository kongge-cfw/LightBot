# LightBot 数据库与迁移

> 数据库：PostgreSQL 15 + pgvector。本文档状态更新于 **2026-07-27**。

## 目录策略

`sql/` 同时承担两类职责：

| 类型 | 文件 | 使用方式 |
| --- | --- | --- |
| 全新安装基线 | `2026-07-12-init.sql` | 仅用于创建空的 2.1 数据库；包含截至 2026-07-21 的完整结构。 |
| 存量升级迁移 | `YYYY-MM-DD-NNN.sql` | 仅用于已有数据库；严格按文件名升序执行。 |
| 模块索引 | [module-index.md](module-index.md) | 按平台、AI、知识库、工具、Agent、工作流和评测定位表与迁移。 |

历史增量迁移不会因为“脚本变多”而被合并或改名。它们已经是部署环境的执行记录；重写历史会造成环境之间无法判断是否已执行 DDL，甚至重复创建索引或丢失数据。为降低检索成本，本次以模块索引统一维护表和变更归属。

## 全新部署（2.1）

在 PostgreSQL 服务端执行，命令会创建 `lightbot` 数据库：

```bash
psql -v ON_ERROR_STOP=1 -U postgres -h localhost -f sql/2026-07-12-init.sql
```

前置条件：执行账号具备 `CREATE DATABASE`、`CREATE EXTENSION` 权限，且 PostgreSQL 已安装 pgvector。全新安装只执行该基线文件，**不要**再执行下方增量脚本。

## 从旧库升级至 2.1

先备份，再连接已有的 `lightbot` 数据库按顺序执行：

```bash
pg_dump -Fc -U postgres -h localhost -d lightbot -f lightbot-before-2.1.dump

psql -v ON_ERROR_STOP=1 -U postgres -h localhost -d lightbot -f sql/2026-07-14-001.sql
psql -v ON_ERROR_STOP=1 -U postgres -h localhost -d lightbot -f sql/2026-07-18-001.sql
psql -v ON_ERROR_STOP=1 -U postgres -h localhost -d lightbot -f sql/2026-07-18-002.sql
psql -v ON_ERROR_STOP=1 -U postgres -h localhost -d lightbot -f sql/2026-07-20-001.sql
psql -v ON_ERROR_STOP=1 -U postgres -h localhost -d lightbot -f sql/2026-07-20-002.sql
psql -v ON_ERROR_STOP=1 -U postgres -h localhost -d lightbot -f sql/2026-07-21-001.sql
```

迁移失败时立即停止，不要跳过失败脚本继续执行。根据备份和失败脚本的影响范围恢复后，在预发布环境重新验证。当前迁移不提供通用自动回滚脚本。

## 2.1 增量迁移清单

| 脚本 | 模块 | 变更摘要 |
| --- | --- | --- |
| `2026-07-14-001.sql` | Tool / MCP | 为 `mcp_server` 新增平台内置标识并回填历史数据。 |
| `2026-07-18-001.sql` | Platform / Agent | 为 `task` 增加 Redis Stream 重试、延迟和死信字段；调整内置 SubAgent 的流式超时默认值。 |
| `2026-07-18-002.sql` | Agent | 将消息工具事件拆分至 `message.tool_events`。 |
| `2026-07-20-001.sql` | Knowledge | 为 `message.metadata` 增加 GIN 索引，支撑 RAG 引用与反馈查询。 |
| `2026-07-20-002.sql` | Knowledge / Agent | 增加 RAG、反馈、请求 ID 索引；将 SubAgent 名称唯一性改为未删除记录的部分唯一索引。 |
| `2026-07-21-001.sql` | Tool | 为 `tool` 新增工具维度限流开关与 JSONB 配置。 |

## 新增迁移规范

1. 新脚本直接放在 `sql/` 根目录，命名为 `YYYY-MM-DD-NNN.sql`，同日序号递增。
2. 每个脚本开头写清楚变更目的、影响表、是否需要数据回填和回滚注意事项。
3. 使用 `IF EXISTS` / `IF NOT EXISTS` 前先评估幂等性，不能用它掩盖错误的执行顺序。
4. 有结构变更时同步更新基线快照、本文档、[模块索引](module-index.md)和发布说明。
5. 禁止修改已发布的历史增量迁移；确有修复时新增一个迁移文件。

## 数据库文档

- [模块索引](module-index.md)：按业务模块查看表与迁移归属。
- [部署指南](../docs/deployment.md)：生产迁移、备份与发布顺序。
- [历史数据库设计](../docs/database/schema.md)：早期设计说明，仅供背景参考；实际表结构以基线 SQL 和增量迁移为准。
