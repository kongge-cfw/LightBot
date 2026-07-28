# LightBot 数据库与迁移

> 数据库：PostgreSQL 15 + pgvector。本文档状态更新于 **2026-07-28**。

## 目录策略

`sql/` 同时承担两类职责：

| 类型 | 文件 | 使用方式 |
| --- | --- | --- |
| 全新安装基线 | `2026-07-28-init.sql` | 仅用于创建空的 2.1 数据库；已合并截至 2026-07-21 的完整结构。 |
| 存量升级迁移 | `YYYY-MM-DD-NNN.sql` | 仅用于已有数据库；严格按文件名升序执行。 |
| 模块索引 | [module-index.md](module-index.md) | 按平台、AI、知识库、工具、Agent、工作流和评测定位表。 |

已并入基线的历史增量脚本（原 `2026-07-14-001` ～ `2026-07-21-001`）已从仓库移除，避免与当前基线重复维护。已部署且执行过这些脚本的环境无需再补跑；全新安装只执行基线文件。

## 全新部署（2.1）

在 PostgreSQL 服务端执行，命令会创建 `lightbot` 数据库：

```bash
psql -v ON_ERROR_STOP=1 -U postgres -h localhost -f sql/2026-07-28-init.sql
```

前置条件：执行账号具备 `CREATE DATABASE`、`CREATE EXTENSION` 权限，且 PostgreSQL 已安装 pgvector。全新安装只执行该基线文件，**不要**对空库再执行其它历史增量脚本。

## 已有环境升级

不要重跑全量基线。先备份，再连接已有的 `lightbot` 数据库，按文件名升序执行仓库中尚未执行的 `YYYY-MM-DD-NNN.sql`：

```bash
pg_dump -Fc -U postgres -h localhost -d lightbot -f lightbot-before-upgrade.dump

# 示例：执行尚未应用的新迁移（以仓库实际文件为准）
# psql -v ON_ERROR_STOP=1 -U postgres -h localhost -d lightbot -f sql/YYYY-MM-DD-NNN.sql
```

迁移失败时立即停止，不要跳过失败脚本继续执行。根据备份和失败脚本的影响范围恢复后，在预发布环境重新验证。当前迁移不提供通用自动回滚脚本。

## 新增迁移规范

1. 新脚本直接放在 `sql/` 根目录，命名为 `YYYY-MM-DD-NNN.sql`，同日序号递增。
2. 每个脚本开头写清楚变更目的、影响表、是否需要数据回填和回滚注意事项。
3. 使用 `IF EXISTS` / `IF NOT EXISTS` 前先评估幂等性，不能用它掩盖错误的执行顺序。
4. 有结构变更时同步更新基线快照、本文档、[模块索引](module-index.md)和发布说明。
5. 禁止修改已发布的历史增量迁移；确有修复时新增一个迁移文件。结构稳定后可将已合并增量从仓库移除，并刷新基线文件名与文档。

## 数据库文档

- [模块索引](module-index.md)：按业务模块查看表归属。
- [部署指南](../docs/deployment.md)：生产迁移、备份与发布顺序。
- [历史数据库设计](../docs/database/schema.md)：早期设计说明，仅供背景参考；实际表结构以基线 SQL 和增量迁移为准。
