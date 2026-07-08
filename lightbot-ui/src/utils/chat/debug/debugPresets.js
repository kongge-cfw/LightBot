import { getToolDisplayName } from '@/components/toolRegistry'

/** @typedef {{ id: string, label: string, message: object }} DebugPreset */

/** @type {DebugPreset[]} */
export const DEBUG_PRESETS = [
  {
    id: 'plain-markdown',
    label: '纯 Markdown',
    message: {
      role: 'assistant',
      content: `# Markdown 测试

这是一段**加粗**与*斜体*文本。

- 列表项 A
- 列表项 B

说明：列表后的普通段落不应被缩进。`,
      metadata: { toolEvents: [], workflowEvents: [], reasoningContent: '', ragReferences: [] },
    },
  },
  {
    id: 'reasoning',
    label: '深度思考 + 正文',
    message: {
      role: 'assistant',
      content: '根据分析，建议采用方案 B，因为它在性能与可维护性之间取得了较好平衡。',
      metadata: {
        reasoningContent: '首先对比方案 A 与 B 的复杂度...\n其次评估团队熟悉度...\n结论：方案 B 更合适。',
        toolEvents: [],
        workflowEvents: [],
        ragReferences: [],
      },
    },
  },
  {
    id: 'query-knowledge',
    label: '知识库检索 + 正文',
    message: {
      role: 'assistant',
      content: '根据知识库检索结果，LightBot 支持 RAG 与工作流编排。',
      metadata: {
        toolEvents: [
          {
            type: 'tool_call',
            toolName: 'query_knowledge',
            displayName: getToolDisplayName('query_knowledge'),
            args: JSON.stringify({ query: 'LightBot 功能' }),
            contentOffset: 0,
          },
          {
            type: 'tool_result',
            toolName: 'query_knowledge',
            displayName: getToolDisplayName('query_knowledge'),
            contentOffset: 0,
            result: JSON.stringify({
              total: 1,
              results: [{
                result_type: 'document',
                document_name: '产品介绍.md',
                content: 'LightBot 是轻量级 Java AI Agent 平台。',
                score: 0.95,
              }],
            }),
          },
        ],
        workflowEvents: [],
        reasoningContent: '',
        ragReferences: [],
      },
    },
  },
  {
    id: 'skill-active',
    label: 'Skill 启用',
    message: {
      role: 'assistant',
      content: '已启用代码审查 Skill，开始分析您的代码。',
      metadata: {
        toolEvents: [{
          type: 'skill_active',
          skills: [
            { slug: 'code-review', name: 'code-review', displayName: '代码审查', description: '审查代码质量', builtin: true },
          ],
        }],
        workflowEvents: [],
        reasoningContent: '',
        ragReferences: [],
      },
    },
  },
  {
    id: 'rag-references',
    label: '带参考文献',
    message: {
      role: 'assistant',
      content: '根据知识库内容，Agent 可通过绑定知识库实现 RAG 增强。',
      metadata: {
        toolEvents: [],
        workflowEvents: [],
        reasoningContent: '',
        ragReferences: [
          {
            documentName: 'RAG 指南.pdf',
            contentPreview: 'RAG（检索增强生成）通过向量检索为模型提供上下文...',
            score: 0.91,
            sourceType: 'document',
            knowledgeId: '1001',
            documentId: '2001',
          },
          {
            documentName: '问答：如何配置知识库？',
            contentPreview: '问题：如何配置知识库？\n答案：在知识库详情页上传文档并等待入库完成。',
            score: 0.87,
            sourceType: 'qa_pair',
            question: '如何配置知识库？',
            knowledgeId: '1001',
            documentId: '2002',
          },
        ],
      },
    },
  },
  {
    id: 'tool-error',
    label: '工具错误',
    message: {
      role: 'assistant',
      content: '抱歉，工具调用失败，请稍后重试。',
      metadata: {
        toolEvents: [
          {
            type: 'tool_call',
            toolName: 'web_search',
            displayName: getToolDisplayName('web_search'),
            args: JSON.stringify({ query: 'test' }),
            contentOffset: 0,
          },
          {
            type: 'tool_result',
            toolName: 'web_search',
            displayName: getToolDisplayName('web_search'),
            contentOffset: 0,
            result: JSON.stringify({ _error: true, message: '网络搜索服务暂时不可用' }),
          },
        ],
        workflowEvents: [],
        reasoningContent: '',
        ragReferences: [],
      },
    },
  },
  {
    id: 'multi-tools',
    label: '多工具组合',
    message: {
      role: 'assistant',
      content: '计算完成。123 × 456 = 56088。同时为您检索了相关知识。',
      metadata: {
        toolEvents: [
          {
            type: 'tool_call',
            toolName: 'calculator',
            displayName: getToolDisplayName('calculator'),
            args: JSON.stringify({ expression: '123*456' }),
            contentOffset: 0,
          },
          {
            type: 'tool_result',
            toolName: 'calculator',
            displayName: getToolDisplayName('calculator'),
            contentOffset: 0,
            result: JSON.stringify({ expression: '123*456', result: 56088 }),
          },
          {
            type: 'tool_call',
            toolName: 'query_knowledge',
            displayName: getToolDisplayName('query_knowledge'),
            args: JSON.stringify({ query: '计算' }),
            contentOffset: 18,
          },
          {
            type: 'tool_result',
            toolName: 'query_knowledge',
            displayName: getToolDisplayName('query_knowledge'),
            contentOffset: 18,
            result: JSON.stringify({
              total: 1,
              results: [{ result_type: 'document', document_name: '数学手册', content: '乘法运算...', score: 0.8 }],
            }),
          },
        ],
        workflowEvents: [],
        reasoningContent: '',
        ragReferences: [],
      },
    },
  },
]

export function getPresetById(id) {
  const preset = DEBUG_PRESETS.find((p) => p.id === id)
  if (!preset) return null
  return JSON.parse(JSON.stringify(preset.message))
}
