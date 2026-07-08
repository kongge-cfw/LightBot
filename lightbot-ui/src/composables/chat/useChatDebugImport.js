import { CHAT_DEBUG_IMPORT_KEY, saveDebugImportPayload, consumeDebugImportPayload } from '@/utils/chat/debug/debugImportStorage'

export { CHAT_DEBUG_IMPORT_KEY }

/**
 * 将 Chat 消息保存并跳转到 Debug Lab
 * @param {import('vue-router').Router} router
 * @param {object} msg Chat 页消息对象
 * @param {{ tab?: string }} options
 */
export function sendMessageToDebugLab(router, msg, { tab = 'composer' } = {}) {
  if (!msg) return
  let metadata = msg.metadata
  if (typeof metadata === 'string') {
    try {
      metadata = JSON.parse(metadata)
    } catch {
      metadata = {}
    }
  }
  saveDebugImportPayload({
    content: msg.content ?? '',
    metadata: {
      toolEvents: metadata?.toolEvents ?? msg._toolEvents ?? [],
      workflowEvents: metadata?.workflowEvents ?? msg._workflowEvents ?? [],
      reasoningContent: metadata?.reasoningContent ?? msg._reasoningContent ?? '',
      ragReferences: metadata?.ragReferences ?? [],
      requestId: metadata?.requestId ?? msg._requestId ?? '',
      error: metadata?.error,
      workflowError: metadata?.workflowError,
    },
    role: msg.role || 'assistant',
  })
  router.push({ name: 'ChatDebugLab', query: { tab } })
}

/**
 * Debug Lab 页读取导入数据（sessionStorage 或 route state）
 */
export function useChatDebugImport() {
  function readImportPayload(route) {
    const fromState = route?.state?.import
    if (fromState?.content != null) {
      return fromState
    }
    return consumeDebugImportPayload()
  }

  return { readImportPayload }
}
