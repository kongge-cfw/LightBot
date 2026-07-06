import { processSseLines } from './chat'

const WORKFLOW_SSE_EVENT_TYPES = new Set([
  'workflow_node_start',
  'workflow_node_complete',
  'workflow_node_retry',
  'workflow_node_failure',
  'workflow_complete',
  'workflow_llm_chunk',
  'workflow_confirm_required',
  'workflow_suspended',
  'error',
])

/**
 * 读取工作流调试 SSE 流（格式与 Chat 一致：[STATUS] + JSON、[DONE] + 最终结果）
 *
 * @param {string} url 请求地址
 * @param {Object} payload 请求体
 * @param {{ onEvent?: Function, onDone?: Function, signal?: AbortSignal }} callbacks
 */
export async function readWorkflowTestSseStream(url, payload, { onEvent, onDone, signal } = {}) {
  const token = localStorage.getItem('token')
  const response = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: token || '',
    },
    body: JSON.stringify(payload),
    signal,
  })

  if (!response.ok) {
    let message = `流式请求失败: ${response.status}`
    try {
      const errBody = await response.json()
      message = errBody?.message || message
    } catch {
      // ignore
    }
    throw new Error(message)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let doneFired = false

  const fireDone = (meta) => {
    if (!doneFired) {
      doneFired = true
      onDone?.(meta)
    }
  }

  const handleToolEvent = (parsed) => {
    if (!WORKFLOW_SSE_EVENT_TYPES.has(parsed?.type)) return
    if (parsed.type === 'error') {
      onEvent?.(parsed)
      throw new Error(parsed.message || '执行失败')
    }
    onEvent?.(parsed)
  }

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) {
        if (buffer.trim()) {
          processSseLines(buffer, { onToolEvent: handleToolEvent, onDone: fireDone })
        }
        fireDone()
        break
      }
      buffer += decoder.decode(value, { stream: true })
      const lastNewline = buffer.lastIndexOf('\n')
      if (lastNewline === -1) continue
      const complete = buffer.substring(0, lastNewline)
      buffer = buffer.substring(lastNewline + 1)
      processSseLines(complete, { onToolEvent: handleToolEvent, onDone: fireDone })
    }
  } catch (err) {
    if (err.name === 'AbortError') throw err
    throw err
  }
}

export function testWorkflowStream(agentId, payload, callbacks) {
  return readWorkflowTestSseStream(`/api/agents/${agentId}/workflow/test/stream`, payload, callbacks)
}

export function resumeWorkflowStream(agentId, payload, callbacks) {
  return readWorkflowTestSseStream(`/api/agents/${agentId}/workflow/resume/stream`, payload, callbacks)
}
