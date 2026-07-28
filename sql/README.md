# LightBot 数据库与迁移

> 数据库：PostgreSQL 15 + pgvector。本文档状态更新于 **2026-07-28**。

## 目录策略

```text
sql/
├── 2026-07-28-init.sql   # 全量 DDL：建库 + 扩展 + 全部建表
├── insert-sql.sql         # 预制数据：全部 INSERT
└── YYYY-MM-DD-NNN.sql    # 存量升级增量（按日期升序，有则执行）
```

基线固定为 **2 个 SQL 文件**：带日期的 DDL，以及 `insert-sql.sql`。

| 类型 | 文件 | 使用方式 |
| --- | --- | --- |
| 全量 DDL | `2026-07-28-init.sql` | 创建空库结构；按模块注释、按依赖排序；无遗留 ALTER。 |
| 预制数据 | `insert-sql.sql` | 默认 Agent、系统配置、Prompt/评测模板。 |
| 存量升级 | `YYYY-MM-DD-NNN.sql` | 已有库按文件名升序执行；不要重跑 init。 |
| 模块索引 | [module-index.md](module-index.md) | 按业务域定位表归属。 |

## 全新部署（2.1）

在仓库根目录依次执行：

```bash
psql -v ON_ERROR_STOP=1 -U postgres -h localhost -f sql/2026-07-28-init.sql
psql -v ON_ERROR_STOP=1 -U postgres -h localhost -d lightbot -f sql/insert-sql.sql
```

前置条件：执行账号具备 `CREATE DATABASE`、`CREATE EXTENSION` 权限，且 PostgreSQL 已安装 pgvector。

## 已有环境升级

不要重跑全量 init。先备份，再按文件名升序执行尚未应用的 `YYYY-MM-DD-NNN.sql`。

## 基线维护约定

1. **一次建表**：字段/索引变更合并进 `YYYY-MM-DD-init.sql` 对应表的 `CREATE TABLE`。
2. **建表次序**：按逻辑依赖排列（被引用方在前）。
3. **种子目标态**：预制数据只写最终 `INSERT`，统一放在 `insert-sql.sql`。
4. **增量不可变**：已发布的 `YYYY-MM-DD-NNN.sql` 禁止改写。

## 数据库文档

- [模块索引](module-index.md)
- [部署指南](../docs/deployment.md)
- [历史数据库设计](../docs/database/schema.md)（背景参考；以带日期的 init SQL 为准）
