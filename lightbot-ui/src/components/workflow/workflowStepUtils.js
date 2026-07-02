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

/** 从 events / metadata 恢复挂起确认态 */
export function resolveWorkflowConfirmPending(workflowEvents, metadata) {
  if (metadata?.workflowConfirmForm && metadata?.workflowRunId) {
    return {
      runId: String(metadata.workflowRunId),
      confirmForm: metadata.workflowConfirmForm,
    }
  }
  if (!metadata?.workflowSuspended || !Array.isArray(workflowEvents)) return null
  for (let i = workflowEvents.length - 1; i >= 0; i--) {
    const ev = workflowEvents[i]
    if (ev?.type === 'workflow_confirm_required' && ev.confirmForm) {
      return {
        runId: ev.runId != null ? String(ev.runId) : metadata?.workflowRunId,
        confirmForm: ev.confirmForm,
      }
    }
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
