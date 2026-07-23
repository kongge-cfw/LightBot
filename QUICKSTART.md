# LightBot 快速启动指南

> 适用于 LightBot v2.1.0。

## 1. 环境要求

| 组件 | 版本/说明 | 是否必需 |
| --- | --- | --- |
| JDK | 17 | 是 |
| Maven | 3.9+ | 是 |
| Node.js + pnpm | Node.js 20+、pnpm 9+ | 前端必需 |
| PostgreSQL | 15+，需安装 `pgvector` | 是 |
| Redis | 7+ | 是 |
| 模型 API Key | OpenAI 或 DashScope 兼容配置 | 是 |
| MinIO | 文件、附件和产物存储 | 建议 |
| Neo4j | 知识图谱 | 可选 |
| Milvus | 向量检索 | 可选，未部署时请关闭配置 |

仓库当前未提供完整的 Docker Compose 编排文件。可使用现有容器平台或本地安装的中间件；以下连接参数需与 `lightbot-server/src/main/resources/application.yml` 保持一致。

## 2. 初始化 PostgreSQL

创建 v2.1 数据库快照：

```bash
psql -U postgres -h localhost -f sql/2026-07-12-init.sql
```

该脚本会创建 `lightbot` 数据库并执行 `CREATE EXTENSION vector`。执行账号需要具备建库与创建扩展权限。

从 2.0 升级时不要重跑快照；请按 [SQL 迁移说明](sql/README.md) 的顺序执行 6 个增量脚本。

验证连接：

```bash
psql -U postgres -h localhost -d lightbot -c "\\dt"
```

## 3. 配置后端

复制并按环境调整配置。开发环境可直接修改 `lightbot-server/src/main/resources/application.yml`，生产环境建议通过环境变量或外部配置注入敏感信息。

最小配置项：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/lightbot
    username: postgres
    password: postgres
  data:
    redis:
      host: localhost
      port: 6379
      password: 123456
      database: 9

lightbot:
  milvus:
    enabled: false  # 未部署 Milvus 时关闭；RAG 向量检索需设为 true
```

配置至少一个模型密钥：

```bash
# PowerShell
$env:OPENAI_API_KEY = "sk-xxx"
$env:DASHSCOPE_API_KEY = "sk-xxx"
```

首次进入系统后，在“模型管理”中配置提供商、模型及默认模型。

## 4. 启动后端

在仓库根目录执行多模块构建，确保依赖边界和父子版本一致：

```bash
mvn clean install -DskipTests
```

启动服务：

```bash
cd lightbot-server
mvn spring-boot:run
```

服务默认监听 `http://localhost:8081`，Swagger UI 地址为 `http://localhost:8081/swagger-ui.html`。

也可以打包后启动：

```bash
cd lightbot-server
mvn package -DskipTests
java -jar target/lightbot-server-2.1.0.jar
```

## 5. 启动前端

```bash
cd lightbot-ui
pnpm install
pnpm dev
```

默认访问地址为 `http://localhost:5173`。前端开发代理应指向后端的 `8081` 端口。

## 6. 首次使用

1. 访问前端页面并完成管理员初始化或注册。
2. 在模型管理中配置可用模型并设置默认模型。
3. 创建 Agent，或从内置 Agent/工作流模板开始。
4. 创建会话，选择 Agent 后发起流式对话。
5. 需要 RAG 时，再接入 MinIO、Milvus 和 Neo4j 并启用对应能力。

## 常见问题

### `extension "vector" is not available`

PostgreSQL 未安装 pgvector。请安装与 PostgreSQL 主版本兼容的 pgvector 后重新执行初始化脚本。

### 后端启动时无法连接 Redis

检查 `spring.data.redis` 的 host、port、password、database 是否与 Redis 实例一致。任务队列、缓存和会话相关能力都依赖 Redis。

### 未部署 Milvus 如何启动

将 `lightbot.milvus.enabled` 设为 `false`。此时基础会话可用，但向量检索相关能力不可用。

### 数据库已存在

不要对已有生产库重跑 `2026-07-12-init.sql`。从 2.0 升级请严格按 [sql/README.md](sql/README.md) 执行增量迁移，并先完成备份和预发布验证。

## 后续阅读

- [README.md](README.md)：功能、架构和 API 概览
- [ROADMAP.md](ROADMAP.md)：版本规划和已完成能力
- [sql/README.md](sql/README.md)：数据库快照与迁移顺序
- [CHANGELOG.md](CHANGELOG.md)：版本变更记录
