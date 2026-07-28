-- LightBot 预制数据（INSERT）
--
-- 说明：默认 Agent、系统配置、Prompt/评测模板等目标态数据。
-- 在执行完带日期的 init DDL 并连接到 lightbot 后执行本文件。
--
--   psql -v ON_ERROR_STOP=1 -U postgres -h localhost -d lightbot -f sql/insert-sql.sql
--

-- ============================================================
-- 模块：Agent 与会话 / 平台与账号
-- ============================================================

-- ----------------------------------------
-- 数据：默认 Agent（agent.id=1）
-- ----------------------------------------
INSERT INTO agent (id, user_id, name, description, agent_type, system_prompt, welcome_message, status, is_default, version, create_time, update_time, deleted)
VALUES (
    1, 1, 'LightBot 助手', '默认AI助手', 'chat',
    '你是 LightBot 智能助手，请用中文回答用户问题。回答应简洁准确，遇到不确定的信息请如实告知。',
    '## 你好，我是 LightBot
有什么可以帮你的？',
    'published', FALSE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
) ON CONFLICT (id) DO NOTHING;

-- ----------------------------------------
-- 数据：系统默认模型配置（system_config）
-- ----------------------------------------
INSERT INTO system_config (config_key, config_value, description) VALUES
('default_ai_provider', '{"providerId": null, "modelId": null}', '默认AI模型配置（生成提示词、推荐问题等功能使用）'),
('default_chat_model', '{"providerId": null, "modelId": null}', '默认对话模型配置'),
('default_embedding_model', '{"providerId": null, "modelId": null}', '默认向量模型配置（知识库文档嵌入等场景使用）'),
('default_tts_model', '{"providerId": null, "modelId": null}', '默认TTS模型配置（语音合成等场景使用）'),
('default_rerank_model', '{"providerId": null, "modelId": null}', '默认重排模型配置（知识库检索精排等场景使用）')
ON CONFLICT (config_key) DO NOTHING;

-- ----------------------------------------
-- 数据：Landing 页面配置（system_config.landing_config）
-- ----------------------------------------
INSERT INTO system_config (config_key, config_value, description) VALUES (
  'landing_config',
  '{
    "title": "LightBot",
    "subtitle": "AI Native 智能体平台",
    "subtitles": [
      "AI Native 智能体平台",
      "一站式 RAG 知识库引擎",
      "可视化 Workflow 编排",
      "MCP 协议生态集成",
      "全链路评测与可观测"
    ],
    "description": "构建智能体、知识库、工作流与工具集成的统一平台。从 Prompt 工程到 RAG 检索增强，从 Workflow 编排到 MCP 工具生态，LightBot 为 AI 应用开发提供全栈能力支撑。",
    "features": [
      {"icon": "Agent", "title": "智能体", "desc": "多模型驱动的自主推理 Agent，支持工具调用、记忆管理和多轮对话"},
      {"icon": "SubAgent", "title": "子智能体", "desc": "多 Agent 协作编排，支持任务分解与子智能体调度"},
      {"icon": "Knowledge", "title": "知识库", "desc": "向量检索 + 图谱融合的 RAG 引擎，精准召回知识增强生成"},
      {"icon": "Workflow", "title": "工作流", "desc": "可视化 DAG 编排，支持条件分支、并行执行和人工审批节点"},
      {"icon": "Mcp", "title": "MCP 协议", "desc": "标准 Model Context Protocol 集成，即插即用外部工具生态"},
      {"icon": "Tool", "title": "工具系统", "desc": "HTTP/函数/脚本多类型工具，统一 Schema 定义与安全沙箱执行"},
      {"icon": "Skill", "title": "技能市场", "desc": "可复用的 Prompt + Tool 组合，一键发布到技能市场共享"},
      {"icon": "Prompt", "title": "Prompt 工程", "desc": "模板化提示词管理，支持版本控制与 A/B 测试优化"},
      {"icon": "Eval", "title": "评测中心", "desc": "数据集管理、自动评估、实验对比，量化 Agent 质量持续优化"},
      {"icon": "Observability", "title": "可观测性", "desc": "全链路 Trace 追踪、Token 消耗统计、工具调用日志实时监控"}
    ],
    "github": "https://github.com/finch04/LightBot",
    "copyright": "© 2026 LightBot. All Rights Reserved."
  }',
  'Landing 页面配置（标题、描述、功能列表等）'
)
ON CONFLICT (config_key) DO NOTHING;

-- ============================================================
-- 模块：模型与 Prompt
-- ============================================================

-- ----------------------------------------
-- 数据：Prompt 构建模板（prompt_build_template，16 条）
-- ----------------------------------------
INSERT INTO prompt_build_template (id, prompt_template_key, tags, template_desc, template, variables, model_config, tool_config) VALUES
(10001, 'general_assistant', '通用,助手', '通用AI助手模板', '你是一个专业的AI助手。请根据用户的问题提供准确、有帮助的回答。

角色：{{role}}
任务：{{task}}

用户输入：{{user_input}}', 'role,task,user_input', '{"temperature": 0.7}', '{}'),
(10002, 'code_reviewer', '代码,审查', '代码审查模板', '你是一个资深的代码审查专家。请对以下代码进行审查，指出问题并给出改进建议。

审查语言：{{language}}
审查重点：{{focus}}

代码：
{{code}}', 'language,focus,code', '{"temperature": 0.3}', '{}'),
(10003, 'translator', '翻译', '翻译专家模板', '你是一个专业的翻译专家，精通多种语言。请将以下文本翻译成目标语言。

源语言：{{source_lang}}
目标语言：{{target_lang}}

原文：{{text}}', 'source_lang,target_lang,text', '{"temperature": 0.3}', '{}'),
(10004, 'conversational_ai', 'chat,dialogue', '对话式AI模板', '你是一个{{role}}，具有以下特点：
{{personality}}

在与用户对话时，请遵循以下原则：
1. {{principle_1}}
2. {{principle_2}}
3. {{principle_3}}

用户：{{user_input}}

请回复：', 'role,personality,principle_1,principle_2,principle_3,user_input', '{"temperature": 0.7, "maxTokens": 2000}', '{}'),
(10005, 'social_media_promotion', 'social,promotion', '社交媒体推销文案生成模板', '你是一个擅长撰写社交媒体文案的 AI 助手，请根据提供的产品信息生成一条适合发布在{{platform}}平台的推广文案。

要求：
1. 使用轻松、亲切的口吻，像朋友分享好物；
2. 结尾添加相关话题标签，如 #好物推荐；

产品信息：
{{product_info}}', 'platform,product_info', '{"temperature": 0.8, "maxTokens": 500}', '{}'),
(10006, 'product_promotion', 'goods,promotion', '商品推广Prompt模板', '请为以下商品写一段推广文案：

商品名称：{{product_name}}
商品特点：{{features}}
目标人群：{{target_audience}}

要求：
1. 突出商品卖点
2. 语言简洁有力
3. 吸引目标人群购买', 'product_name,features,target_audience', '{"temperature": 0.7, "maxTokens": 300}', '{}'),
(10007, 'task_executor', 'task,execution', '任务执行模板', '你是一个专业的{{domain}}专家，请完成以下任务：

## 任务描述
{{task_description}}

## 输入信息
{{input_data}}

## 输出要求
{{output_requirements}}

## 约束条件
{{constraints}}

请按要求完成任务：', 'domain,task_description,input_data,output_requirements,constraints', '{"temperature": 0.3, "maxTokens": 3000}', '{}'),
(10008, 'analysis_report', 'analysis,report', '分析报告模板', '请对以下{{analysis_subject}}进行深入分析：

## 分析对象
{{subject_details}}

## 分析维度
{{analysis_dimensions}}

## 参考标准
{{reference_standards}}

## 报告结构
1. 摘要
2. 详细分析
3. 关键发现
4. 结论和建议

请生成完整的分析报告：', 'analysis_subject,subject_details,analysis_dimensions,reference_standards', '{"temperature": 0.4, "maxTokens": 4000}', '{}'),
(10009, 'creative_generator', 'creative,generation', '创意生成模板', '请为{{project_type}}项目生成创意方案：

## 项目背景
{{background}}

## 目标群体
{{target_audience}}

## 核心需求
{{core_requirements}}

## 创意约束
{{creative_constraints}}

## 输出要求
- 提供3-5个不同的创意方向
- 每个方向包含核心概念和执行要点
- 评估可行性和预期效果

请开始生成创意：', 'project_type,background,target_audience,core_requirements,creative_constraints', '{"temperature": 0.9, "maxTokens": 3000}', '{}'),
(10010, 'problem_solver', 'problem,solution', '问题解决模板', '作为{{expert_role}}，请帮助解决以下问题：

## 问题描述
{{problem_description}}

## 现状分析
{{current_situation}}

## 已尝试方案
{{attempted_solutions}}

## 限制条件
{{limitations}}

## 解决方案要求
1. 分析问题根因
2. 提供多个可选方案
3. 评估方案的可行性和风险
4. 推荐最优方案和实施步骤

请提供解决方案：', 'expert_role,problem_description,current_situation,attempted_solutions,limitations', '{"temperature": 0.5, "maxTokens": 3500}', '{}'),
(10011, 'teaching_assistant', 'education,teaching', '教学辅导模板', '你是一位经验丰富的{{subject}}老师，请为学生提供学习指导：

## 学生信息
- 学习水平：{{student_level}}
- 学习目标：{{learning_goal}}

## 教学内容
{{teaching_content}}

## 学生问题
{{student_question}}

## 教学要求
1. 用简单易懂的语言解释
2. 提供具体的例子
3. 给出练习建议
4. 鼓励学生思考

请开始教学：', 'subject,student_level,learning_goal,teaching_content,student_question', '{"temperature": 0.6, "maxTokens": 2500}', '{}'),
(10012, 'content_writer', '写作,内容创作', '内容创作专家模板，适用于文章、博客、营销文案等场景', '你是一位资深的内容创作专家，擅长撰写各类文体。请根据以下要求创作内容。

内容类型：{{content_type}}
目标受众：{{target_audience}}
风格要求：{{style}}
主题：{{topic}}

请直接输出内容：', 'content_type,target_audience,style,topic', '{"temperature": 0.8}', '{}'),
(10013, 'data_analyst', '数据分析', '数据分析专家模板，适用于数据解读、报表分析、趋势预测等场景', '你是一位专业的数据分析师。请根据以下数据和问题进行分析。

数据描述：{{data_description}}
分析目标：{{analysis_goal}}
数据样本：
{{data_sample}}

请提供分析结论和建议：', 'data_description,analysis_goal,data_sample', '{"temperature": 0.3}', '{}'),
(10014, 'customer_service', '客服', '智能客服模板，适用于售前咨询、售后支持、投诉处理等场景', '你是一位专业的客服代表，态度友好、耐心细致。请根据以下信息回复客户。

客服角色：{{service_role}}
客户问题：{{customer_issue}}
产品信息：{{product_info}}
回复语言：{{reply_lang}}

请给出专业回复：', 'service_role,customer_issue,product_info,reply_lang', '{"temperature": 0.5}', '{}'),
(10015, 'summarizer', '摘要,总结', '文本摘要专家模板，适用于长文摘要、会议纪要、报告精简等场景', '你是一位文本摘要专家。请对以下内容进行精准概括。

摘要类型：{{summary_type}}
摘要长度：{{summary_length}}
原文：
{{original_text}}

请输出摘要：', 'summary_type,summary_length,original_text', '{"temperature": 0.3}', '{}'),
(10016, 'email_composer', '邮件', '邮件撰写专家模板，适用于商务邮件、工作汇报、客户沟通等场景', '你是一位专业的邮件撰写助手。请根据以下信息撰写邮件。

邮件场景：{{scenario}}
收件人：{{recipient}}
邮件目的：{{purpose}}
关键要点：{{key_points}}
语气：{{tone}}

请输出完整邮件（含主题行）：', 'scenario,recipient,purpose,key_points,tone', '{"temperature": 0.6}', '{}');

-- ============================================================
-- 模块：评测
-- ============================================================

-- ----------------------------------------
-- 数据：评估器模板（eval_evaluator_template，3 条）
-- ----------------------------------------
INSERT INTO eval_evaluator_template (id, evaluator_template_key, template_desc, template, variables, model_config) VALUES
(10001, 'text_similarity', '文本相似度评估', '请评估以下两个文本的相似度，分数范围为0-1，保留两位小数。

文本1：{{reference_output}}

文本2：{{actual_output}}

相似度分数：', 'reference_output,actual_output', '{"temperature": 0.1}'),
(10002, 'code_quality', '代码质量评估', '请评估以下代码的质量，从可读性、效率和最佳实践三个方面进行分析，并给出0-1的总分，保留两位小数。

代码：
{{code}}

评估报告：', 'code', '{"temperature": 0.2}'),
(10003, 'sentiment_analysis', '情感分析评估', '请分析以下文本的情感倾向，输出-1到1之间的情感分数，其中-1表示非常负面，0表示中性，1表示非常正面，保留两位小数。

文本：{{text}}

情感分数：', 'text', '{"temperature": 0.1}');

