/**
 * Chat 能力类 SSE 事件处理（Skill / SubAgent，从 Chat.vue 解耦）
 */
import { nextTick } from 'vue'
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
    messages,
    messagesRef,
  } = deps

  function scrollToCapabilityBlock(assistantMsg, selector) {
    if (!assistantMsg) {
      scrollToBottom()
      return
    }
    nextTick(() => {
      requestAnimationFrame(() => {
        const idx = messages?.value?.indexOf(assistantMsg) ?? -1
        const container = messagesRef?.value
        if (idx < 0 || !container) {
          scrollToBottom()
          return
        }
        const row = container.querySelector(`[data-index="${idx}"]`)
        const block = row?.querySelector(selector)
        if (!block) {
          scrollToBottom()
          return
        }
        const containerRect = container.getBoundingClientRect()
        const blockRect = block.getBoundingClientRect()
        if (blockRect.bottom > containerRect.bottom - 16) {
          container.scrollTop += blockRect.bottom - containerRect.bottom + 16
        } else if (blockRect.top < containerRect.top + 16) {
          container.scrollTop += blockRect.top - containerRect.top - 16
        }
      })
    })
  }

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
      const content = assistantMsg.content || ''
      const splitEnd = Math.min(Math.max(0, offset), content.length)
      if (!event.contentPrefixAnchor && splitEnd > 0) {
        event.contentPrefixAnchor = content.substring(0, splitEnd)
      }
      assistantMsg._toolExpanded = true
      assistantMsg._currentToolOffset = offset
      registerToolBlockOffset(assistantMsg, offset)
      currentStatus.value = formatSubagentCallStatus(event)
      scrollToCapabilityBlock(assistantMsg, '.subagent-call-block')
    } else if (event.type === 'subagent_tool_call') {
      currentStatus.value = formatSubagentToolCallStatus(event)
      scrollToCapabilityBlock(assistantMsg, '.subagent-call-block')
    } else if (event.type === 'subagent_token') {
      currentStatus.value = 'SubAgent 输出中...'
      scrollToBottom()
    } else if (event.type === 'subagent_error_retry') {
      assistantMsg._toolExpanded = true
      registerToolBlockOffset(assistantMsg, offset)
      currentStatus.value = event.message || `SubAgent 重试 ${event.attempt}/${event.maxRetries}`
      scrollToCapabilityBlock(assistantMsg, '.subagent-call-block')
    } else if (event.type === 'subagent_error') {
      assistantMsg._toolExpanded = true
      registerToolBlockOffset(assistantMsg, offset)
      currentStatus.value = event.message || 'SubAgent 执行失败'
      scrollToCapabilityBlock(assistantMsg, '.subagent-call-block')
    } else {
      hasStreamContent.value = true
      scrollToBottom()
      return true
    }

    hasStreamContent.value = true
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
