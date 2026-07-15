package com.lightbot.skill;

import java.util.List;

/**
 * 内置 Skill 定义清单
 * <p>对标 Yuxi 项目中的 SKILL.md：每个 Skill 是一条「编排指令」，
 * 由 prompt_template 指导主 Agent 何时、如何使用所依赖的 Tool。</p>
 *
 * <p>新增内置 Skill 时仅需在 {@link #list()} 中追加一条 Definition，
 * {@link BuiltInSkillRegistrar} 启动时会按 content_hash 同步到数据库。</p>
 *
 * @author finch
 * @since 2026-05-28
 */
public final class BuiltInSkillDefinitions {

    private BuiltInSkillDefinitions() {}

    public record Definition(
            String slug,
            String name,
            String displayName,
            String icon,
            String description,
            List<String> toolNames,
            List<String> skillDependencies,
            String promptTemplate,
            int sortOrder
    ) {}

    private static final List<Definition> DEFS = List.of(
            new Definition(
                    "deep-research",
                    "deep-research",
                    "深度研究",
                    "ExperimentOutlined",
                    "对话题进行多轮联网检索、任务拆解与结构化整理，适合调研报告、行业分析等需要事实证据的问题。",
                    List.of("web_search", "ask_user", "ocr_parse_file", "sandbox_read_file", "sandbox_write_file", "sandbox_append_file"),
                    List.of(),
                    """
                    ### 技能：深度研究（deep-research）
                    **触发条件**：用户问题需要外部最新信息、行业调研、对比分析、事实核查，且预期得到带证据的结构化结论时启用本技能。简单事实问答或不需要外部证据的问题不要为了使用本技能而展开调研。

                    **本技能只使用 LightBot 当前实际可用的工具**：
                    - `write_todos` 与 `present_artifacts` 为主 Agent 自动注入的协作工具，不占 Agent 的手动工具配额。前者只记录完整待办快照；后者只登记已存在于 `outputs/` 目录的最终文件。
                    - `ask_user` 用 `questions` 一次提出 1~5 个独立问题；每项使用 `questionId`、`question`、`options`、`multiSelect`、`allowOther`。调用后系统会等待本会话用户回答，不能继续调用任何工具。
                    - `web_search` 是联网检索工具，参数为 `query` 和 `maxResults`；结果包含标题、URL 与摘要。只有它实际出现在可用工具中时才能调用。
                    - `delegate_to_subagent` 仅在当前 Agent 已绑定子智能体后才会出现；用一次调用中的 `tasks` 承载互不依赖的问题，`mode="parallel"` 表示并行。
                    - `query_knowledge` 仅在当前 Agent 已绑定知识库后由系统自动注入；它用于检索绑定知识库，不能把未命中的常识伪装成知识库结论。
                    - 遇到本轮上传的 PDF/图片且需要提取文字时，使用 `ocr_parse_file`；它返回 `ocr/...` 解析文件路径，再用 `sandbox_read_file` 读取所需片段。不要臆造附件内容。
                    - 仅在用户明确要求可下载文件时，用 `sandbox_write_file` / `sandbox_append_file` 写入 `outputs/` 下的 Markdown/文本交付物，再用 `present_artifacts` 登记。中间文件写普通工作区路径，不得交付 Skill 文件。
                    - 长文必须分片：单次 `content` 勿超约 3500 字符；先 `sandbox_write_file` 写开头，再多次 `sandbox_append_file` 追加，禁止整篇塞进一次 tool call（会被截断失败）。
                    - 禁止调用或提及 Yuxi 的 `task`、`ask_user_question`、`tavily_search`、`read_file`、`write_file`、`query_kb` 等名称；也不要猜测任何未出现在当前工具列表中的 MCP 工具名。

                    **一、先判断是否需要澄清**：
                    - 仅当研究目标、对象范围、时间范围、地区/市场、交付形式或评价标准的缺失会实质改变结论时，才调用 `ask_user`。
                    - 对调研场景一次优先提出 1~3 个彼此独立的问题；优先提供 2~5 个明确选项并允许补充。传 `questions` 数组，不要退回到旧的单题 `question`/`options` 参数；不要把本可合理假定的细节抛回给用户。
                    - 调用 `ask_user` 后必须停止后续检索、待办更新和子智能体委派，等待用户回答；用户回答后将其视为本轮调研约束继续执行。
                    - 若范围已足够明确，直接开始调研；对采用的默认范围在最终报告开头简要说明，不要额外追问。

                    **二、计划与待办**：
                    - 对需要两步以上检索、比较、核验或交付的任务，先调用 `write_todos` 写入 2~8 个可验收的完整待办快照；不要只写笼统的“开始调研”。
                    - 待办应覆盖：范围/问题拆解、证据检索、关键事实或冲突核验、综合与交付。每项使用稳定 id、可读 content 和状态；同一时刻至少一个且通常只有一个主待办为 `in_progress`。
                    - 每次阶段完成、开始下一阶段或任务失败/取消时，都用**完整快照**更新 `write_todos`；只有结论已被核验或已纳入最终汇总时才标记 `completed`。

                    **三、执行与协作**：
                    1. 将问题拆分为互相独立、可验收的研究子问题，例如背景、关键参与者、最新进展、风险/争议和对比维度。
                    2. 若当前 Agent 已绑定合适的子智能体，优先用一次 `delegate_to_subagent` 将互不依赖的子问题放入 `tasks`，指定 `mode="parallel"`；每个任务必须写明研究目标、必要上下文、期望输出、证据/引用要求。不要重复委派同一问题，也不要让子智能体做最终总编。
                    3. 对关键数字、核心结论或来源冲突，使用第二个独立来源交叉核验；有合适的核验子智能体时可单独委派。没有可靠证据时明确标注“待核实”，不要补造结论。
                    4. 若 `query_knowledge` 可用，先检索已绑定知识库；再按需用 `web_search` 补充公开最新信息。单次 `maxResults` 控制在 5 以内，同一检索意图最多重写一次关键词。收到子任务结果后，更新待办，再由主 Agent 综合。
                    5. 用户上传 PDF/图片时，先用 `ocr_parse_file` 生成 `ocr/...` 文本，再用 `sandbox_read_file` 按需阅读；OCR 或检索失败时记录待办并说明证据缺口，不能把文件名当作文件内容。

                    **四、交付**：
                    - 最终报告先给 1~2 句结论摘要，再按小节说明发现、依据、风险/不确定性和建议；不要机械拼接子智能体原文。
                    - 每一条来自检索的事实、数据、时间或链接，必须在句末以「（参考：站点名或 URL）」标注出处；无出处的事实不得写入结论。
                    - 存在失败、未完成任务或证据冲突时必须如实说明影响；只有用户要求文件交付时，主 Agent 才先用 `sandbox_write_file`（必要时 `sandbox_append_file` 分段）写入例如 `outputs/reports/research-report.md`，确认成功后再使用 `present_artifacts` 登记并告知用户。

                    **停止准则**：当所有待办均进入终态，且各子问题已获得可信来源、或已明确记录无法核验的原因时，停止检索并综合作答，避免无意义的反复搜索。

                    **禁止**：未调用工具就给出含具体数据、时间或链接的结论；编造或臆测来源；在 `ask_user` 等待期间继续调用其他工具。
                    """,
                    10
            ),
            new Definition(
                    "knowledge-grounded-qa",
                    "knowledge-grounded-qa",
                    "知识库严谨问答",
                    "BookOutlined",
                    "强制基于已绑定的知识库回答问题，适合企业内部文档、产品手册等场景，杜绝凭空发挥。",
                    List.of("query_knowledge", "list_knowledge_bases", "search_documents", "open_kb_document", "find_in_document", "get_mindmap"),
                    List.of(),
                    """
                    ### 技能：知识库严谨问答（knowledge-grounded-qa）
                    **前提与触发**：仅当当前 Agent 已绑定知识库、且问题涉及产品、制度、规范、手册或内部资料时使用。`query_knowledge`、`list_knowledge_bases`、`search_documents`、`open_kb_document`、`find_in_document`、`get_mindmap` 均是 LightBot 工具；只调用实际出现在当前可用工具列表中的工具。

                    **工具选择与执行顺序**：
                    1. 常规事实问答先调用 `query_knowledge(question)`，问题应保留对象、动作、版本或时间范围；不要先列举知识库再进行无意义检索。
                    2. 用户询问“有哪些知识库”、需要在多个库间选择时，调用 `list_knowledge_bases`；只用返回的 ID 继续调用 `get_mindmap`，不能猜测 ID。
                    3. 当检索结果给出了 `document_id` 而片段不足以支撑流程、条款或关键数字时，调用 `open_kb_document(documentId)` 补足原文上下文；用户点名某个文件而向量检索未命中时，先用 `search_documents(keyword)`，再用返回的文档 ID 打开原文。
                    4. 对长文内的精确字段、章节、条款或关键词，调用 `find_in_document` 定位；仅在用户询问目录或知识结构时调用 `get_mindmap`，不把思维导图当作事实证据。
                    5. 回答只概括工具返回的内容，每项关键事实标注「（参考：文档名）」；检索无结果时明确说“绑定知识库未找到依据”，可给出带明确边界的通用建议，但不得伪装为知识库结论。

                    **禁止**：使用 Yuxi 的 `list_kbs`、`query_kb`、`open_document` 或臆造文档 ID；把未命中的常识包装成知识库结论；大段复制原文；将任何绑定库之外的资料当作内部规范。
                    """,
                    20
            ),
            new Definition(
                    "calculator-precise",
                    "calculator-precise",
                    "精确数值计算",
                    "CalculatorOutlined",
                    "遇到加减乘除等数值运算时强制调用计算器工具，避免大模型口算偏差。",
                    List.of("calculator"),
                    List.of(),
                    """
                    ### 技能：精确数值计算（calculator-precise）
                    **触发条件**：用户明确要求加、减、乘、除、比例、金额或单位换算的精确计算时使用；纯事实问答、估算讨论或没有运算的数字描述不调用。

                    **LightBot 工具约束**：`calculator` 一次只接受 `a`、`b` 和 `operation`，其中 `operation` 只能是 `add`、`subtract`、`multiply`、`divide`。

                    **执行规则**：
                    1. 先识别单位和计算口径；百分比先转换为小数（如 15% 为 0.15），再用 `calculator` 运算。单位不能相加时先说明无法直接计算。
                    2. 多步表达式必须按依赖顺序多次调用，并将上一步实际 `result` 用作下一步输入；不可臆算中间值。
                    3. 除法前确保除数不为 0；工具报错时说明失败原因，不用模型口算替代。不要把工具不支持的开方、幂、三角函数伪装为已精确计算。
                    4. 最终先给结果及单位，再用简洁公式说明输入与中间值；没有明确的舍入规则时保留工具返回精度，不制造伪精度。
                    """,
                    30
            ),
            new Definition(
                    "image-create",
                    "image-create",
                    "图片创作",
                    "PictureOutlined",
                    "面向需要生成插画、海报、示意图等场景，基于明确的画面要求调用 LightBot 图像生成工具。",
                    List.of("image_generation", "ask_user"),
                    List.of(),
                    """
                    ### 技能：图片创作（image-create）
                    **触发条件**：用户明确要求生成插画、海报、示意图、封面或创意图像时启用。当前 LightBot 的 `image_generation` 仅支持文生图，参数是 `prompt` 与可选的 `negativePrompt`；不支持参考图编辑、尺寸选择或批量生成。

                    **执行流程**：
                    1. 若主体、用途、风格或画面中必须出现/避免的元素缺失且会实质影响成图，一次调用 `ask_user` 提出 1~3 个独立问题后等待回答；否则采用合理默认值并简短说明，不为可推断细节追问。
                    2. 将确认后的意图整理成简洁、具体的英文 `prompt`（主体、环境、构图、风格、光线/材质）；只在确有需要时填写 `negativePrompt`，不要加入与需求冲突的通用负面词。
                    3. 调用 `image_generation` 后，直接使用返回的 `image_url` 呈现图片。该工具会把图片保存到 LightBot 的会话输出区并返回真实 URL，不能再编造临时 URL，也不需要为图片调用 `present_artifacts`。
                    4. 若用户要求编辑既有图片、精确尺寸、透明背景或多张变体，先明确当前工具能力边界，再让用户选择改为新的文生图描述或使用已绑定的其他工具；不假装完成不支持的操作。

                    **禁止**：未调用工具即声称图片已生成；照搬 Yuxi 的沙盒脚本/临时 URL 流程；杜撰图片链接或模型参数。
                    """,
                    40
            ),
            new Definition(
                    "db-introspect",
                    "db-introspect",
                    "数据库探查",
                    "DatabaseOutlined",
                    "面向开发/数据分析场景，安全地查询 PostgreSQL 元信息与样本数据，禁止任何写操作。",
                    List.of("pg_list_tables", "pg_describe_table", "pg_query"),
                    List.of(),
                    """
                    ### 技能：数据库探查（db-introspect）
                    **触发条件**：用户需要查看 LightBot PostgreSQL 的公开业务结构、只读样例或汇总数据时启用。只能使用 `pg_list_tables`、`pg_describe_table`、`pg_query`，不得调用 Yuxi 的 MySQL、终端或未绑定的图表工具。

                    **安全边界**：
                    - `pg_query` 只允许只读 `SELECT`、`SHOW`、`EXPLAIN` 或 `WITH` 查询；严禁 INSERT、UPDATE、DELETE、DDL、事务控制、系统 schema、危险函数和绕过限制的注释。
                    - 每条数据查询显式写 `LIMIT`，且不超过 50；先选择需要的列，避免 `SELECT *` 和大字段全量回显。
                    - 不回显连接串、账号、密钥、令牌或不必要的个人敏感数据；遇到这些字段应聚合、脱敏或拒绝展示。

                    **执行流程**：
                    1. 表或字段未知时，先调用 `pg_list_tables`，再仅对目标表调用 `pg_describe_table(tableName)`；只使用工具实际返回的表名与列名。
                    2. 依据已确认的 schema 组装只读 `pg_query`，对汇总或样本查询分别说明统计口径、筛选条件与 LIMIT。
                    3. 将结果用简洁自然语言和 Markdown 表格解释，标明行数、是否截断及其局限；不要把冗长 SQL 或原始敏感值直接抛给用户。
                    4. 用户需要图表时，只有当前 Agent 已实际绑定并暴露对应 MCP 图表工具才可调用其真实工具名；否则提供 Markdown 表格或建议用户绑定图表 MCP。
                    """,
                    50
            )
    );

    public static List<Definition> list() {
        return DEFS;
    }
}
