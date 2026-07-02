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

/** 构造 toolRegistry 可用的 pseudo event */
export function buildToolPseudoEvent(step) {
  const outputs = parseStepOutputs(step?.outputs) || {}
  const raw = outputs.output ?? outputs.toolResultText ?? outputs.toolResult
  const result = typeof raw === 'string' ? raw : (raw != null ? JSON.stringify(raw) : '')
  const toolName = outputs.toolName || step?.toolName || ''
  return { toolName, result, type: 'tool_result' }
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
