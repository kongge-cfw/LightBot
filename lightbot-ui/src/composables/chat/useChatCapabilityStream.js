/**
 * Chat 能力类 SSE 事件处理（Skill / SubAgent，从 Chat.vue 解耦）
 */
import { SKILL_ACTIVE_EVENT_TYPE, formatSkillActiveStatus } from '../../components/skills/skillRegistry.js'
import {
  isSubagentEvent,
  formatSubagentCallStatus,
  formatSubagentToolCallStatus,
} from '../../components/capabilities/subagentRegistry.js'

export { SKILL_ACTIVE_EVENT_TYPE }
export { isSubagentEvent }

/**
 * @param {object} deps Chat 运行时依赖
 */
export function createChatCapabilityStreamHandlers(deps) {
  const {
    currentStatus,
    hasStreamContent,
    scrollToBottom,
    registerToolBlockOffset,
  } = deps

  /**
   * 处理 Skill / SubAgent SSE 事件
   * @returns {boolean} 是否已消费该事件
   */
  function handleChatCapabilityStreamEvent(assistantMsg, event) {
    if (!assistantMsg || !event?.type) return false

    if (event.type === SKILL_ACTIVE_EVENT_TYPE) {
      if (!assistantMsg._toolEvents) assistantMsg._toolEvents = []
      assistantMsg._toolEvents.push(event)
      hasStreamContent.value = true
      currentStatus.value = formatSkillActiveStatus(event.skills)
      scrollToBottom()
      return true
    }

    if (!isSubagentEvent(event)) return false

    const offset = event.contentOffset ?? assistantMsg.content?.length ?? 0
    if (event.contentOffset == null) {
      event.contentOffset = offset
    }

    if (!assistantMsg._toolEvents) assistantMsg._toolEvents = []
    assistantMsg._toolEvents.push(event)

    if (event.type === 'subagent_call') {
      assistantMsg._toolExpanded = true
      assistantMsg._currentToolOffset = offset
      registerToolBlockOffset(assistantMsg, offset)
      currentStatus.value = formatSubagentCallStatus(event)
    } else if (event.type === 'subagent_tool_call') {
      currentStatus.value = formatSubagentToolCallStatus(event)
    } else if (event.type === 'subagent_token') {
      currentStatus.value = 'SubAgent 输出中...'
    } else if (event.type === 'subagent_error_retry') {
      currentStatus.value = event.message || `SubAgent 重试 ${event.attempt}/${event.maxRetries}`
    } else if (event.type === 'subagent_error') {
      currentStatus.value = event.message || 'SubAgent 执行失败'
    }

    hasStreamContent.value = true
    scrollToBottom()
    return true
  }

  return { handleChatCapabilityStreamEvent }
}

/** 注册工具块 offset（供 Chat 复用） */
export function registerToolBlockOffset(msg, offset) {
  if (offset == null || offset < 0) return
  if (!msg._toolBlockOffsets) msg._toolBlockOffsets = []
  if (!msg._toolBlockOffsets.some(o => o == offset)) {
    msg._toolBlockOffsets.push(offset)
    msg._toolBlockOffsets.sort((a, b) => a - b)
  }
}
