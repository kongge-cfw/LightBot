/**
 * SubAgent 事件关联工具（从 events 数组中提取 call 对应的步骤/结果）
 */

function matchSubagentScope(event, call) {
  return event.subagentName === call.subagentName
    && event.contentOffset == call.contentOffset
}

export function collectSubagentSteps(events, call) {
  if (!call || call.type !== 'subagent_call') return []
  return (events || []).filter(
    e => (e.type === 'subagent_tool_call' || e.type === 'subagent_tool_result' || e.type === 'subagent_token')
      && matchSubagentScope(e, call)
  )
}

export function findSubagentResult(events, call) {
  if (!call || call.type !== 'subagent_call') return null
  const resultEvt = (events || []).find(
    e => e.type === 'subagent_result' && matchSubagentScope(e, call)
  )
  return resultEvt?.result || null
}

export function findSubagentError(events, call) {
  if (!call || call.type !== 'subagent_call') return null
  return (events || []).find(
    e => e.type === 'subagent_error' && matchSubagentScope(e, call)
  ) || null
}

export function findSubagentErrorRetry(events, call) {
  if (!call || call.type !== 'subagent_call') return null
  const retries = (events || []).filter(
    e => e.type === 'subagent_error_retry' && matchSubagentScope(e, call)
  )
  return retries.length ? retries[retries.length - 1] : null
}
