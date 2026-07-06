/**
 * SubAgent 事件关联工具（从 events 数组中提取 call 对应的步骤/结果）
 */

function matchSubagentScope(event, call) {
  if (!event || !call) return false
  if (event.subagentName !== call.subagentName) return false
  const eventOffset = event.contentOffset
  const callOffset = call.contentOffset
  if (eventOffset == null || callOffset == null) return eventOffset == callOffset
  return Number(eventOffset) === Number(callOffset)
}

export function collectSubagentSteps(events, call) {
  if (!call || call.type !== 'subagent_call') return []
  return (events || []).filter(
    e => (e.type === 'subagent_tool_call' || e.type === 'subagent_tool_result' || e.type === 'subagent_token')
      && matchSubagentScope(e, call)
  )
}

/**
 * 将连续的 subagent_token 合并为一段流式文本，避免每个 token 独占一行
 * @param {Array} steps collectSubagentSteps 的原始结果
 * @returns {Array} 合并后的步骤列表
 */
export function groupSubagentSteps(steps) {
  const grouped = []
  let tokenBuffer = ''

  for (const step of steps || []) {
    if (step.type === 'subagent_token') {
      tokenBuffer += step.content || ''
      continue
    }
    if (tokenBuffer) {
      grouped.push({ type: 'subagent_token_stream', content: tokenBuffer })
      tokenBuffer = ''
    }
    grouped.push(step)
  }

  if (tokenBuffer) {
    grouped.push({ type: 'subagent_token_stream', content: tokenBuffer })
  }
  return grouped
}

export function findSubagentResult(events, call) {
  if (!call || call.type !== 'subagent_call') return null
  const resultEvt = (events || []).find(
    e => e.type === 'subagent_result' && matchSubagentScope(e, call)
  )
  return resultEvt?.result || null
}

/** 获取 subagent_result 事件对象 */
export function findSubagentResultEvent(events, call) {
  if (!call || call.type !== 'subagent_call') return null
  return (events || []).find(
    e => e.type === 'subagent_result' && matchSubagentScope(e, call)
  ) || null
}

/** 从委派结果 JSON 中解析 reply 展示文本 */
export function parseSubagentReplyText(result) {
  if (!result) return ''
  if (typeof result === 'object' && result.reply != null) {
    return String(result.reply)
  }
  const raw = String(result)
  try {
    const obj = JSON.parse(raw)
    if (obj && typeof obj.reply === 'string') return obj.reply
  } catch {
    // 非 JSON 直接展示
  }
  return raw
}

/** SubAgent 执行结果的用户可读文本 */
export function findSubagentResultReply(events, call) {
  const evt = findSubagentResultEvent(events, call)
  if (!evt) return ''
  if (evt.replyText) return evt.replyText
  return parseSubagentReplyText(evt.result)
}

/** SubAgent 返回的原始 JSON（弹窗展示） */
export function findSubagentResultRawJson(events, call) {
  const evt = findSubagentResultEvent(events, call)
  if (!evt?.result) return ''
  try {
    return JSON.stringify(JSON.parse(evt.result), null, 2)
  } catch {
    return String(evt.result)
  }
}

/** 是否包含可查看的结构化 JSON 返回 */
export function hasSubagentResultJson(events, call) {
  const evt = findSubagentResultEvent(events, call)
  if (!evt?.result) return false
  try {
    const obj = JSON.parse(evt.result)
    return obj != null && typeof obj === 'object'
  } catch {
    return false
  }
}

/** 合并 SubAgent 流式 token 为完整模型输出文本 */
export function mergeSubagentModelOutput(events, call) {
  const steps = groupSubagentSteps(collectSubagentSteps(events, call))
  return steps
    .filter(s => s.type === 'subagent_token_stream')
    .map(s => s.content || '')
    .join('')
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

/**
 * 将 SubAgent 内部工具事件转换为标准 tool_call / tool_result，供 ToolCallsGroupComponent 渲染
 * @param {Array} events 全部工具事件
 * @param {Object} call subagent_call 事件
 * @returns {Array} 标准工具事件列表
 */
export function mapSubagentToolsToStandardEvents(events, call) {
  if (!call || call.type !== 'subagent_call') return []
  const steps = collectSubagentSteps(events, call)
  const mapped = []
  for (const step of steps) {
    if (step.type === 'subagent_tool_call') {
      mapped.push({
        type: 'tool_call',
        toolName: step.toolName,
        displayName: step.toolDisplayName || step.toolName,
        args: step.args || '{}',
      })
    } else if (step.type === 'subagent_tool_result') {
      mapped.push({
        type: 'tool_result',
        toolName: step.toolName,
        displayName: step.toolDisplayName || step.toolName,
        result: step.result ?? step.content ?? '',
      })
    }
  }
  return mapped
}

export function formatSubagentErrorLabel(code) {
  if (code === 'CONNECT_TIMEOUT') return '连接超时'
  if (code === 'READ_TIMEOUT') return '响应超时'
  return null
}
