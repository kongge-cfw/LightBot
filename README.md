<p align="center">
  <img src="docs/assets/lightbot-logo-single.png" alt="LightBot" width="180">
</p>

<h1 align="center">LightBot</h1>

<p align="center">
  <strong>轻量级现代化 Java AI Agent 平台</strong>
</p>

<p align="center">
  <em>Lightweight, modern, enterprise-ready Java AI Agent platform</em>
</p>

<p align="center">
  <a href="QUICKSTART.md">快速启动</a> ·
  <a href="权限配置.md">权限与数据隔离</a> ·
  <a href="ROADMAP.md">Roadmap</a> ·
  <a href="CHANGELOG.md">Changelog</a> ·
  <a href="sql/README.md">数据库迁移</a> ·
  <a href="LICENSE">Apache-2.0</a> ·
  <a href="http://localhost:8081/swagger-ui.html">Swagger UI</a>
</p>

---

## v2.1.0 发布概览

v2.1.0 聚焦“模块边界清晰、运行时可治理、数据库可升级”。本版本完成后端 Maven 多模块落地，强化了 Agent/Workflow 运行时和 Redis Stream 任务队列，并将 2.1 数据库快照与存量迁移路径文档化。

| 方向 | v2.1.0 能力 |
| --- | --- |
| 模块化 | common、framework、platform、ai、knowledge、tool、workflow、agent、server 九模块职责分离 |
| Agent 运行时 | SubAgent 批量并行委派、实时运行态、长期记忆、资源提及与多模态附件 |
| Workflow | 人工确认、节点超时/重试、嵌套工作流、参数映射、测试历史和 SSE 回放 |
| 任务治理 | Redis Stream 消费、延迟重试、死信标记、取消与僵尸任务扫描 |
| 平台治理 | 工具维度限流、Token 用量、角色权限、RAG 反馈和查询索引优化 |

完整说明见 [CHANGELOG.md](CHANGELOG.md) 和 [ROADMAP.md](ROADMAP.md)。

## 项目介绍

LightBot 是基于 [Spring AI](https://docs.spring.io/spring-ai/reference/) 的 Java AI Agent 平台，为 Java 开发者提供 Agent 构建、Workflow 编排、RAG、Tool/MCP/Skill、SubAgent 协作、评测和可观测能力。

平台采用“单体部署、模块化边界、渐进式演进”的架构：先以清晰的模块契约解决复杂度，再在有明确容量或独立发布需求时考虑分布式拆分。

## 核心能力

| 能力域 | 已支持能力 |
| --- | --- |
| Agent 与会话 | Agent 版本管理、流式对话、会话管理、消息收藏/反馈/搜索、资源提及、长期记忆、附件与产物管理 |
| Workflow | 可视化 DAG、条件/分类/检索/工具/脚本/人工确认等节点、嵌套工作流、变量引用、节点韧性、调试与回放 |
| Tool 与扩展 | 内置/API 工具、JSON Schema、工具调用记录、工具限流、MCP（stdio/SSE/Streamable HTTP）、Skill、SubAgent |
| RAG 与图谱 | 文档解析、OCR、分块、向量与关键词检索、Rerank、QA Pair、知识图谱、语义搜索和 RAG 评测 |
| 模型与 Prompt | OpenAI 兼容模型、DashScope、DeepSeek、Ollama 等提供商，动态模型路由与 Prompt 版本管理 |
| 运营与可观测 | LLM Trace、工具调用记录、实时日志、Dashboard、Token 用量、任务中心与失败任务治理 |
| 安全与权限 | Sa-Token 认证、角色权限、API Key 作用域/配额、敏感词拦截和工具调用安全校验 |

## 系统架构

```text
Vue 3 + Ant Design Vue
        │ HTTP / SSE
        ▼
lightbot-server                    HTTP 入口、配置、拦截器
        ▼
lightbot-agent                     Agent / Chat 运行时
        ▼
lightbot-workflow                  Workflow DSL、图校验、节点执行
        ▼
lightbot-tool                      Tool / MCP / Skill / SubAgent
        ▼
lightbot-knowledge                 RAG、文档、图谱、评测
        ▼
lightbot-ai                        模型工厂、Prompt、LLM Trace
        ▼
lightbot-platform                  用户、任务、系统配置、API Key、Dashboard
        ▼
lightbot-framework → lightbot-common
```

依赖方向固定为：

```text
common → framework → platform → ai → knowledge → tool → workflow → agent → server
```

下层不依赖上层；跨模块通过接口或 Port 通信，不直接依赖其他模块的实现类。

## 技术栈

| 层级 | 技术 |
| --- | --- |
| 后端 | Java 17、Spring Boot 3.3.6、Spring AI 1.1.8、MyBatis-Plus |
| 前端 | Vue 3、Vite、Ant Design Vue、Vue Flow、Pinia、pnpm |
| 数据 | PostgreSQL 15 + pgvector、Redis 7、Milvus、Neo4j、MinIO |
| 协议与治理 | SSE、MCP、Sa-Token、SpringDoc OpenAPI、Redis Stream |

## 快速启动

环境要求：JDK 17、Maven 3.9+、Node.js 20+、pnpm 9+、PostgreSQL 15 + pgvector、Redis 7，以及至少一个模型 API Key。

```bash
# 1. 创建 2.1 数据库（全新部署）
psql -U postgres -h localhost -f sql/2026-07-12-init.sql

# 2. 设置模型 API Key（PowerShell）
$env:OPENAI_API_KEY = "sk-xxx"

# 3. 构建并启动后端
mvn clean install -DskipTests
cd lightbot-server
mvn spring-boot:run

# 4. 启动前端
cd ../lightbot-ui
pnpm install
pnpm dev
```

前端默认地址：`http://localhost:5173`；后端地址：`http://localhost:8081`；Swagger UI：`http://localhost:8081/swagger-ui.html`。

详细配置、可选中间件开关和常见问题见 [QUICKSTART.md](QUICKSTART.md)。

## 数据库与升级

| 场景 | 执行方式 |
| --- | --- |
| 新部署 v2.1 | 仅执行 `sql/2026-07-12-init.sql`，其中已合并截至 2026-07-21 的最终结构 |
| v2.0 升级至 v2.1 | 按文件名升序执行 6 个增量迁移；不要重跑初始化快照 |
| 后续开发 | 新迁移放在 `sql/`，命名为 `YYYY-MM-DD-NNN.sql`，并同步维护快照与迁移说明 |

迁移顺序、变更内容和生产执行注意事项见 [sql/README.md](sql/README.md)。

## API 概览

服务启动后可以通过 Swagger UI 查看实时 OpenAPI 定义。主要资源前缀如下：

| 资源 | 前缀 |
| --- | --- |
| 认证与用户 | `/api/auth`、`/api/admin`、`/api/user-*` |
| Agent 与会话 | `/api/agents`、`/api/chat`、`/api/chat/sessions` |
| Workflow 与任务 | `/api/agents/{agentId}/workflow`、`/api/tasks` |
| 知识与图谱 | `/api/knowledge`、`/api/graph`、`/api/documents` |
| Tool 与扩展 | `/api/tools`、`/api/mcp-servers`、`/api/skills`、`/api/subagents` |
| 模型与 Prompt | `/api/model-providers`、`/api/models`、`/api/prompts` |
| 评测与观测 | `/api/eval/*`、`/api/observability`、`/api/dashboard` |

## 仓库结构

```text
lightbot/
├── lightbot-common/       # Result、枚举、公共类型
├── lightbot-framework/    # Spring 配置、Redis/MinIO 等技术封装
├── lightbot-platform/     # 用户、任务、系统配置、API Key、Dashboard
├── lightbot-ai/           # 模型工厂、Prompt、LLM Trace
├── lightbot-knowledge/    # RAG、文档、图谱、评测
├── lightbot-tool/         # Tool、MCP、Skill、SubAgent
├── lightbot-workflow/     # Workflow DSL、节点处理器、图校验
├── lightbot-agent/        # Agent/Chat 运行时
├── lightbot-server/       # REST 入口与应用装配
├── lightbot-ui/           # Vue 3 前端
├── sql/                   # 初始化快照与增量迁移
└── docs/                  # 设计、发布与功能文档
```

## 开发与发布

```bash
# 后端构建
mvn clean install -DskipTests

# 前端检查与构建
cd lightbot-ui
pnpm lint:check
pnpm build
```

- 提交遵循 Conventional Commits，例如：`feat(workflow): 新增人工确认节点`。
- SQL 变更使用根目录 `sql/YYYY-MM-DD-NNN.sql`；不要放入 `docs/sql`。
- 发布前执行 [docs/RELEASE.md](docs/RELEASE.md) 的检查清单。

## Roadmap

[ROADMAP.md](ROADMAP.md) 是唯一的版本规划入口：其中列出已完成的 v2.1、v2.2 的可交付规划和 v3.0 的启动条件。

## License

本项目采用 [Apache License 2.0](LICENSE) 开源。

在遵守许可证条款的前提下，你可以使用、修改和分发本项目；修改后分发时须保留许可证和版权声明。`LightBot` 名称及相关标识不因该许可证而自动授予商标使用权。

项目依赖分别遵循其自身许可证。发布二进制包、镜像或衍生版本前，请一并核对并保留第三方组件要求的许可证与声明。
