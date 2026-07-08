import { TOOL_RENDERERS, getToolDisplayName } from '@/components/toolRegistry'

/** 全部已注册工具名 */
export const REGISTERED_TOOL_NAMES = Object.keys(TOOL_RENDERERS)

/** 未注册工具（测试 BaseToolCall 兜底） */
export const UNREGISTERED_TOOL_NAME = '__unregistered__'

function sample(toolName, resultObj, extra = {}) {
  return {
    type: 'tool_result',
    toolName,
    displayName: getToolDisplayName(toolName),
    result: typeof resultObj === 'string' ? resultObj : JSON.stringify(resultObj),
    contentOffset: 0,
    ...extra,
  }
}

export const TOOL_DEBUG_SAMPLES = {
  query_knowledge: sample('query_knowledge', {
    total: 2,
    results: [
      {
        result_type: 'document',
        document_name: '产品手册.pdf',
        content: 'LightBot 是一个轻量级 Java AI Agent 平台，支持 RAG、工作流与多工具调用。',
        score: 0.92,
        knowledge_name: '产品文档',
      },
      {
        result_type: 'qa_pair',
        question: '如何创建 Agent？',
        answer: '在 Agent 管理页点击新建，配置系统提示词与模型即可。',
        score: 0.88,
      },
    ],
  }),

  find_in_document: sample('find_in_document', {
    mode: 'open',
    document_name: 'API 指南.md',
    total_lines: 120,
    start_line: 10,
    end_line: 15,
    content: '## 认证\n\n所有 API 请求需携带 Authorization 头。',
  }),

  search_documents: sample('search_documents', {
    total: 2,
    documents: [
      { document_name: '部署指南.pdf', knowledge_name: '运维文档', match_count: 3 },
      { document_name: 'FAQ.md', knowledge_name: '产品文档', match_count: 1 },
    ],
  }),

  list_knowledge_bases: sample('list_knowledge_bases', {
    knowledge_bases: [
      { id: '1001', name: '产品文档', document_count: 12 },
      { id: '1002', name: '技术规范', document_count: 8 },
    ],
  }),

  get_mindmap: sample('get_mindmap', {
    knowledge_name: '产品架构',
    mindmap: {
      name: 'LightBot 架构',
      children: [
        { name: 'lightbot-server', children: [{ name: 'Agent' }, { name: 'RAG' }] },
        { name: 'lightbot-ui', children: [{ name: 'Chat' }, { name: 'Workflow' }] },
      ],
    },
  }),

  open_kb_document: sample('open_kb_document', {
    document_name: 'README.md',
    content: '# LightBot\n\n轻量级 Java AI Agent 平台。',
    total_chars: 256,
  }),

  web_search: sample('web_search', {
    answer: 'Spring AI 是 Spring 生态下的 AI 应用框架。',
    results: [
      { title: 'Spring AI 官方文档', url: 'https://spring.io/projects/spring-ai', snippet: 'Spring AI 提供统一的 ChatClient API...' },
    ],
  }),

  calculator: sample('calculator', { expression: '123 * 456', result: 56088 }),

  pg_list_tables: sample('pg_list_tables', {
    tables: ['users', 'agent', 'knowledge', 'chat_session', 'message'],
  }),

  pg_describe_table: sample('pg_describe_table', {
    table: 'agent',
    columns: [
      { name: 'id', type: 'bigint', nullable: false },
      { name: 'name', type: 'varchar(128)', nullable: false },
    ],
  }),

  pg_query: sample('pg_query', {
    columns: ['id', 'name', 'status'],
    rows: [['1', '客服助手', 'published'], ['2', '代码助手', 'draft']],
    row_count: 2,
  }),

  ask_user: sample('ask_user', {
    question: '请选择您要查询的时间范围',
    options: ['最近 7 天', '最近 30 天', '自定义'],
    wait_for_user: true,
  }),

  image_generation: sample('image_generation', {
    url: 'https://picsum.photos/512/512',
    prompt: 'A minimalist robot assistant icon',
  }),

  read_skill: sample('read_skill', {
    slug: 'code-review',
    name: '代码审查',
    content: '# 代码审查 Skill\n\n审查代码时关注安全性与可读性。',
  }),

  list_skill_files: sample('list_skill_files', {
    slug: 'code-review',
    files: ['SKILL.md', 'examples/review-checklist.md'],
  }),

  execute_code: sample('execute_code', {
    success: true,
    language: 'python',
    output: 'Hello, LightBot!\n',
    elapsedMs: 128,
  }),

  sandbox_read_file: sample('sandbox_read_file', {
    path: '/workspace/output.txt',
    content: 'Line 1: analysis result\nLine 2: done',
    size: 48,
  }),

  sandbox_list_files: sample('sandbox_list_files', {
    path: '/workspace',
    files: [
      { name: 'input.csv', size: 1024, is_dir: false },
      { name: 'output', size: 0, is_dir: true },
    ],
  }),

  sandbox_write_file: sample('sandbox_write_file', {
    path: '/workspace/result.json',
    bytes_written: 256,
    success: true,
  }),

  ocr_parse_file: sample('ocr_parse_file', {
    file_name: 'scan.pdf',
    pages: 2,
    text: '第一页 OCR 识别文本...\n\n第二页 OCR 识别文本...',
  }),

  present_artifacts: sample('present_artifacts', {
    files: [
      { name: 'report.pdf', path: 'session/files/report.pdf', size: 204800, mime_type: 'application/pdf' },
    ],
  }),

  install_skill: sample('install_skill', {
    success: true,
    message: '成功安装 1 个技能',
    installed: [{ slug: 'pdf-summary', name: 'PDF 摘要', source: 'remote' }],
    errors: [],
  }),

  [UNREGISTERED_TOOL_NAME]: sample('custom_unknown_tool', {
    message: '未注册工具示例输出',
    data: { foo: 'bar' },
  }),
}

export function getToolSelectOptions() {
  const registered = REGISTERED_TOOL_NAMES.map((name) => ({
    value: name,
    label: `${getToolDisplayName(name)} (${name})`,
  }))
  registered.push({
    value: UNREGISTERED_TOOL_NAME,
    label: '未注册工具 (BaseToolCall 兜底)',
  })
  return registered
}

export function getToolSampleEvent(toolName) {
  if (toolName === UNREGISTERED_TOOL_NAME) {
    return { ...TOOL_DEBUG_SAMPLES[UNREGISTERED_TOOL_NAME] }
  }
  const s = TOOL_DEBUG_SAMPLES[toolName]
  if (s) return { ...s }
  return sample(toolName, { message: 'empty sample' })
}

export function getToolSampleResultJson(toolName) {
  const event = getToolSampleEvent(toolName)
  try {
    return JSON.stringify(JSON.parse(event.result), null, 2)
  } catch {
    return event.result || '{}'
  }
}

export function getToolErrorSampleResult() {
  return JSON.stringify({ _error: true, message: '工具执行失败：模拟错误' }, null, 2)
}
