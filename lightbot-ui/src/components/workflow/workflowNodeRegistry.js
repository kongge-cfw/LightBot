/**
 * 工作流节点 Chat/测试 渲染注册表（参考 toolRegistry）
 */
import { truncateText, parseStepOutputs } from './workflowStepUtils.js'
import { getNodeTypeName } from '../../views/workflow/composables/useWorkflowNodeSteps.js'

/** Chat 中弱化或隐藏的内置节点 */
export const HIDDEN_IN_CHAT_TYPES = new Set([
  'loop_start', 'loop_end', 'batch_start', 'batch_end',
])

/** 详情区不重复展示正文的节点（正文在消息区流式输出） */
export const HIDE_DETAIL_BODY_TYPES = new Set(['llm', 'output'])

export function isHiddenInChat(nodeType) {
  return HIDDEN_IN_CHAT_TYPES.has(nodeType)
}

export function isContainerNodeType(nodeType) {
  return nodeType === 'loop' || nodeType === 'batch' || nodeType === 'app_component'
}

export function stepStatusClass(step) {
  if (step?.status === 'running') return 'event-running'
  if (step?.status === 'failed') return 'event-fail'
  if (step?.status === 'suspended') return 'event-suspended'
  if (step?.status === 'done') return 'event-done'
  return 'event-start'
}

export function stepStatusIcon(step) {
  if (step?.status === 'running') return 'running'
  if (step?.status === 'failed') return 'failed'
  if (step?.status === 'suspended') return 'suspended'
  if (step?.status === 'done') return 'done'
  return 'start'
}

const HANDLE_LABELS = {
  out_a: '分支 A',
  out_b: '分支 B',
  out_c: '否则',
}

/** 步骤行右侧摘要文案 */
export function getStepSummary(step) {
  if (!step) return ''
  const type = step.nodeType
  const outputs = parseStepOutputs(step.outputs) || {}

  if (step.status === 'running') return '执行中'
  if (step.status === 'suspended') return '等待确认'
  if (step.status === 'failed') return step.message || '执行失败'

  switch (type) {
    case 'llm': {
      const inTok = step.traceData?.inputTokens
      const outTok = step.traceData?.outputTokens
      if (inTok != null || outTok != null) {
        return `Token ${inTok ?? 0} → ${outTok ?? 0}`
      }
      return '生成完成'
    }
    case 'retrieval': {
      const chunks = outputs.retrievalChunks
      if (Array.isArray(chunks) && chunks.length) return `命中 ${chunks.length} 条片段`
      const text = outputs.retrievalResult || ''
      return text ? `命中 ${text.length} 字` : '未命中'
    }
    case 'tool': {
      const name = outputs.toolDisplayName || outputs.toolName || step.toolName
      return name ? `工具 · ${name}` : '工具执行完成'
    }
    case 'mcp':
      return outputs.toolName ? `MCP · ${outputs.toolName}` : 'MCP 调用完成'
    case 'api':
      return outputs.statusCode != null ? `HTTP ${outputs.statusCode}` : 'HTTP 完成'
    case 'condition': {
      const label = outputs.matchedGroupLabel || HANDLE_LABELS[outputs.matchedHandle] || outputs.matchedHandle
      return label ? `命中 · ${label}` : '路由完成'
    }
    case 'classifier':
      return outputs.subject ? `意图 · ${outputs.subject}` : '分类完成'
    case 'confirm':
      if (step.status === 'suspended') return '等待您的选择'
      if (outputs && Object.keys(outputs).length) {
        const choice = outputs.choice ?? outputs.confirmed
        return choice ? `已选择 · ${choice}` : '已提交'
      }
      return '已提交'
    case 'app_component':
      return outputs.componentName ? `子流程 · ${outputs.componentName}` : '子工作流完成'
    case 'loop':
      return step.children?.length ? `循环 · ${step.children.length} 步` : '循环完成'
    case 'batch':
      return step.children?.length ? `批处理 · ${step.children.length} 步` : '批处理完成'
    case 'parameter_extractor': {
      const keys = Object.keys(outputs).filter(k => !['extractRaw', '_is_completed', '_reason'].includes(k))
      return keys.length ? `提取 ${keys.length} 个参数` : '提取完成'
    }
    case 'script':
      return '脚本执行完成'
    case 'variable':
      return '变量已更新'
    case 'variable_handle':
      return '变量处理完成'
    case 'input':
      return '输入已写入'
    case 'output':
      return '已输出'
    case 'start':
      return '流程开始'
    case 'end':
      return '流程结束'
    default:
      return step.message || '完成'
  }
}

export function hasExpandableStepContent(step) {
  if (!step || isHiddenInChat(step.nodeType)) return false
  if (step.status === 'failed' || step.status === 'suspended') return true
  if (step.message && step.status !== 'done') return true
  const outputs = parseStepOutputs(step.outputs)
  if (outputs && Object.keys(outputs).length) return true
  if (step.traceData && Object.keys(step.traceData).length) return true
  if (step.detail && !HIDE_DETAIL_BODY_TYPES.has(step.nodeType)) return true
  if (step.input && Object.keys(step.input).length) return true
  if (step.isContainer && step.children?.length) return true
  return false
}

export function getNodeTypeLabel(type) {
  return getNodeTypeName(type)
}
