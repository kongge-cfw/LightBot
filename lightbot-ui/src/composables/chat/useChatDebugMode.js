import { ref } from 'vue'
import { message } from 'ant-design-vue'

/** 进入/退出 Debug 模式的快捷键：Ctrl+Alt+D（D = Debug，避免与浏览器 Ctrl+Shift+D 冲突） */
export const CHAT_DEBUG_SHORTCUT = 'Ctrl+Alt+D'

const DEBUG_MODE_STORAGE_KEY = 'lightbot-chat-debug-mode'

function readStoredDebugMode() {
  try {
    return sessionStorage.getItem(DEBUG_MODE_STORAGE_KEY) === '1'
  } catch {
    return false
  }
}

function persistDebugMode(enabled) {
  try {
    sessionStorage.setItem(DEBUG_MODE_STORAGE_KEY, enabled ? '1' : '0')
  } catch {
    // ignore
  }
}

/**
 * Chat 页 Debug 模式：快捷键切换，无需 URL 参数
 */
export function useChatDebugMode() {
  const debugMode = ref(readStoredDebugMode())
  const debugPanelOpen = ref(false)

  function toggleDebugMode() {
    debugMode.value = !debugMode.value
    persistDebugMode(debugMode.value)
    if (!debugMode.value) {
      debugPanelOpen.value = false
    }
    message.info(
      debugMode.value
        ? `已进入 Debug 模式（${CHAT_DEBUG_SHORTCUT} 退出）`
        : '已退出 Debug 模式',
    )
  }

  /** @returns {boolean} 是否已处理该按键 */
  function handleDebugShortcut(e) {
    if (!e.ctrlKey || !e.altKey) return false
    if (e.key !== 'D' && e.key !== 'd') return false
    e.preventDefault()
    toggleDebugMode()
    return true
  }

  return {
    debugMode,
    debugPanelOpen,
    handleDebugShortcut,
  }
}
