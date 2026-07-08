import { ref } from 'vue'
import { message } from 'ant-design-vue'

/** 进入/退出 Debug 模式：1.5 秒内连按 D 键 5 次（无需组合键，输入框内不触发） */
export const CHAT_DEBUG_SHORTCUT = '连按 D 键 5 次'
const DEBUG_PRESS_COUNT = 5
const DEBUG_PRESS_WINDOW_MS = 1500

const DEBUG_MODE_STORAGE_KEY = 'lightbot-chat-debug-mode'

/** 模块级状态，App / Chat 共享 */
const pressTimestamps = []
const debugMode = ref(readStoredDebugMode())
const debugPanelOpen = ref(false)

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

function isTypingTarget(target) {
  if (!target) return false
  const tag = target.tagName
  if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return true
  return !!target.isContentEditable
}

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

/**
 * 连按 D 快捷键
 * - Chat 页：切换 Debug 模式
 * - 其他页：跳转 Debug Lab（/debug，无需登录）
 * - 已在 Debug Lab：提示
 *
 * @param {KeyboardEvent} e
 * @param {{ routeName?: string, router?: import('vue-router').Router }} [ctx]
 * @returns {boolean} 是否已处理该按键
 */
export function handleDebugShortcut(e, ctx = {}) {
  if (isTypingTarget(e.target)) return false
  if (e.ctrlKey || e.altKey || e.metaKey) return false
  if (e.key !== 'D' && e.key !== 'd') {
    pressTimestamps.length = 0
    return false
  }

  const now = Date.now()
  while (pressTimestamps.length && now - pressTimestamps[0] > DEBUG_PRESS_WINDOW_MS) {
    pressTimestamps.shift()
  }
  pressTimestamps.push(now)

  if (pressTimestamps.length >= DEBUG_PRESS_COUNT) {
    pressTimestamps.length = 0
    e.preventDefault()

    const routeName = ctx.routeName
    if (routeName === 'Chat' || routeName === 'ChatSession') {
      toggleDebugMode()
    } else if (routeName === 'ChatDebugLab') {
      message.info('已在 Debug Lab')
    } else if (ctx.router) {
      ctx.router.push({ name: 'ChatDebugLab' })
      message.info('已进入 Debug Lab')
    } else {
      toggleDebugMode()
    }
    return true
  }
  return false
}

/**
 * Chat 页 Debug 模式：连按 D 切换，无需 URL 参数
 */
export function useChatDebugMode() {
  return {
    debugMode,
    debugPanelOpen,
    handleDebugShortcut,
  }
}
