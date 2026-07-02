/** 工作流步骤展示通用工具 */

export function parseStepOutputs(outputs) {
  if (!outputs) return null
  if (typeof outputs === 'string') {
    try { return JSON.parse(outputs) } catch { return null }
  }
  return typeof outputs === 'object' ? outputs : null
}

export function formatKv(value) {
  if (value == null) return ''
  try { return JSON.stringify(value, null, 2) } catch { return String(value) }
}

export function hasKvData(value) {
  return value && typeof value === 'object' && Object.keys(value).length > 0
}

export function truncateText(text, max = 120) {
  if (text == null) return ''
  const s = String(text)
  return s.length <= max ? s : `${s.slice(0, max)}…`
}

/** 预览任意值为可读字符串 */
export function previewValue(value, max = 80) {
  if (value == null) return '—'
  if (typeof value === 'object') return truncateText(formatKv(value), max)
  return truncateText(String(value), max)
}

/** 统计 outputs 中有效键（排除内部字段） */
export function listOutputKeys(outputs, exclude = []) {
  if (!outputs || typeof outputs !== 'object') return []
  const skip = new Set([...exclude, 'llmOutput', 'toolResultText', 'extractRaw', '_is_completed', '_reason'])
  return Object.keys(outputs).filter(k => !skip.has(k))
}

/** 从步骤中提取用户输入预览 */
export function extractUserInputText(step) {
  const outputs = parseStepOutputs(step?.outputs) || {}
  const fromOutput = outputs.input ?? outputs.query
  if (fromOutput != null && String(fromOutput).trim()) return String(fromOutput)
  const fromInput = step?.input?.userInput
  if (fromInput != null && String(fromInput).trim()) return String(fromInput)
  return ''
}

/** 构建变量赋值展示行 */
export function buildAssignmentRows(outputs, excludeKeys = []) {
  const keys = listOutputKeys(outputs, excludeKeys)
  return keys.map(key => ({
    key,
    label: key,
    value: previewValue(outputs[key], 200),
  }))
}

export function isWorkflowAwaitingConfirm(workflowEvents) {
  return !!findUnresolvedConfirmEvent(workflowEvents)
}

/** 从 events / metadata 恢复挂起确认态 */
export function resolveWorkflowConfirmPending(workflowEvents, metadata) {
  if (metadata?.workflowConfirmResolved === true) {
    return null
  }
  if (metadata?.workflowSuspended === false) {
    return null
  }
  if (Array.isArray(workflowEvents)) {
    const hasComplete = workflowEvents.some(e => e?.type === 'workflow_complete')
    const pendingEvent = findUnresolvedConfirmEvent(workflowEvents)
    const confirmSubmitted = workflowEvents.some(e =>
      e?.type === 'workflow_node_complete'
      && e.nodeType === 'confirm'
      && !e.suspended
      && e.outputs
      && Object.keys(e.outputs).length > 0
    )
    if (confirmSubmitted && !pendingEvent) {
      return null
    }
    if (hasComplete && !pendingEvent) {
      return null
    }
    if (!pendingEvent) {
      return null
    }
    if (metadata?.workflowConfirmForm && metadata?.workflowRunId) {
      return {
        runId: String(metadata.workflowRunId),
        confirmForm: metadata.workflowConfirmForm,
      }
    }
    return {
      runId: pendingEvent.runId != null ? String(pendingEvent.runId) : metadata?.workflowRunId,
      confirmForm: pendingEvent.confirmForm,
    }
  }
  if (metadata?.workflowConfirmForm && metadata?.workflowRunId && metadata?.workflowSuspended) {
    return {
      runId: String(metadata.workflowRunId),
      confirmForm: metadata.workflowConfirmForm,
    }
  }
  return null
}

/** 提取已提交的 confirm 数据（用于只读回显） */
export function resolveWorkflowConfirmSubmitted(workflowEvents) {
  if (!Array.isArray(workflowEvents)) return null
  for (let i = workflowEvents.length - 1; i >= 0; i--) {
    const ev = workflowEvents[i]
    if (ev?.type === 'workflow_confirm_required' && ev.resolved && ev.submittedData) {
      return ev.submittedData
    }
  }
  for (let i = workflowEvents.length - 1; i >= 0; i--) {
    const ev = workflowEvents[i]
    if (ev?.type === 'workflow_node_complete' && ev.outputs && ev.nodeType === 'confirm') {
      return ev.outputs
    }
  }
  return null
}

function findUnresolvedConfirmEvent(workflowEvents) {
  for (let i = workflowEvents.length - 1; i >= 0; i--) {
    const ev = workflowEvents[i]
    if (ev?.type !== 'workflow_confirm_required') continue
    if (ev.resolved || ev.submittedData) continue
    if (ev.confirmForm) return ev
  }
  return null
}

/**
 * 从工作流 tool 节点 outputs 提取与 Chat tool_result 一致的结果字符串
 */
export function extractWorkflowToolResult(outputs) {
  if (!outputs || typeof outputs !== 'object') return ''

  const text = outputs.toolResultText
  if (typeof text === 'string' && text.trim()) {
    return text
  }

  const toolResult = outputs.toolResult
  if (toolResult != null) {
    if (typeof toolResult === 'string' && toolResult.trim()) {
      return toolResult
    }
    try {
      return JSON.stringify(toolResult)
    } catch {
      return String(toolResult)
    }
  }

  const output = outputs.output
  if (typeof output === 'string' && output.trim()) {
    return output
  }
  if (output != null && typeof output === 'object') {
    try {
      return JSON.stringify(output)
    } catch {
      return String(output)
    }
  }

  return ''
}

/**
 * 构造 toolRegistry / ToolCallRenderer 可用的 pseudo event（对齐对话 Agent tool_result）
 */
export function buildWorkflowToolEvent(step) {
  const outputs = parseStepOutputs(step?.outputs) || {}
  const toolName = String(outputs.toolName || step?.toolName || '').trim()
  const result = extractWorkflowToolResult(outputs)
  return {
    toolName,
    result,
    type: 'tool_result',
  }
}

/** @deprecated 使用 buildWorkflowToolEvent */
export function buildToolPseudoEvent(step) {
  return buildWorkflowToolEvent(step)
}

/** 工作流 tool 节点是否可交给 ToolCallRenderer 渲染 */
export function canRenderWorkflowTool(event) {
  return !!(event?.result && String(event.result).trim())
}

export function extractLegacyResultText(step) {
  const outputs = parseStepOutputs(step?.outputs)
  if (outputs && Object.keys(outputs).length > 0) {
    const preferKeys = ['result', 'output', 'text', 'answer', 'retrievalResult']
    for (const k of preferKeys) {
      const v = outputs[k]
      if (v != null && String(v).trim()) return String(v)
    }
    for (const v of Object.values(outputs)) {
      if (v != null && String(v).trim() && typeof v !== 'object') return String(v)
    }
  }
  if (step?.detail && String(step.detail).trim()) {
    return String(step.detail)
  }
  return ''
}

/** 合并 resume 返回的全量 events（去重，以 resume 结果为准） */
export function mergeWorkflowEvents(existing, incoming) {
  if (!incoming?.length) return existing || []
  return incoming
}
