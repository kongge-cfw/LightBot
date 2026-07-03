import { ref, watch, nextTick } from 'vue'
import { useVirtualizer } from '@tanstack/vue-virtual'

/**
 * 虚拟滚动与消息列表滚动策略
 * @param {object} deps
 * @param {import('vue').Ref<Array>} deps.messages
 * @param {import('vue').Ref<HTMLElement|null>} deps.messagesRef
 * @param {import('vue').Ref<boolean>} deps.streaming
 * @param {(msg: object) => Array} deps.getMsgRagRefs
 * @param {import('vue').Ref<Map>} deps.refsSectionExpandedMap
 */
export function useChatScroll({ messages, messagesRef, streaming, getMsgRagRefs, refsSectionExpandedMap }) {
  const isNearBottom = ref(true)
  /** 用户主动上划，暂停流式自动滚动 */
  const userScrolledUp = ref(false)

  const virtualizer = useVirtualizer({
    count: messages.value.length,
    getScrollElement: () => messagesRef.value,
    estimateSize: (index) => {
      const msg = messages.value[index]
      if (!msg) return 80
      if (msg.role === 'user') return msg._replyToMessageId ? 90 : 60
      const len = msg.content?.length || 0
      let size = Math.max(80, Math.min(600, Math.ceil(len / 40) * 22 + 60))
      // 工具事件额外占高：每个 tool_call/tool_result 约 32px，tool_status 约 24px
      const toolCount = (msg._toolEvents || []).filter(e => e.type === 'tool_call' || e.type === 'tool_result').length
      const statusCount = (msg._toolEvents || []).filter(e => e.type === 'tool_status').length
      if (toolCount > 0 || statusCount > 0) {
        size += 60 + toolCount * 32 + statusCount * 24
      }
      // 参考文献额外占高（默认展开，记录的为收起状态）
      const refCount = getMsgRagRefs(msg).length
      if (refCount > 0 && !refsSectionExpandedMap.value.has(index)) size += 40 + refCount * 36
      else if (refCount > 0) size += 32
      return Math.min(size, 2000)
    },
    overscan: 5,
  })

  watch(() => messages.value.length, (newLen) => {
    virtualizer.value.setOptions({
      ...virtualizer.value.options,
      count: newLen,
    })
  })

  function handleScroll() {
    const el = messagesRef.value
    if (!el) return
    const threshold = 150
    const nearBottom = el.scrollHeight - el.scrollTop - el.clientHeight < threshold
    isNearBottom.value = nearBottom
    // 流式输出期间：上划暂停自动滚动，回到底部恢复
    if (streaming.value) {
      userScrolledUp.value = !nearBottom
    }
  }

  function onCapabilityHeightChange(evt) {
    const rowEl = evt?.target?.closest?.('[data-index]')
    if (!rowEl) return
    const container = messagesRef.value
    if (container) container.style.overflowAnchor = 'none'
    virtualizer.value.measureElement(rowEl)
    nextTick(() => { if (container) container.style.overflowAnchor = '' })
  }

  function scrollToBottom() {
    if (!isNearBottom.value || userScrolledUp.value) return
    const el = messagesRef.value
    if (!el) return
    nextTick(() => {
      el.scrollTop = el.scrollHeight
    })
  }

  /** 流式输出期间，自动滚动深度思考面板到底部 */
  function scrollReasoningToBottom() {
    if (userScrolledUp.value) return
    nextTick(() => {
      const panels = messagesRef.value?.querySelectorAll('.reasoning-content')
      if (panels?.length) {
        const last = panels[panels.length - 1]
        last.scrollTop = last.scrollHeight
      }
    })
  }

  /** 强制滚动到底部（切换会话后使用，不检查 isNearBottom）
   *  多次延迟滚动，确保工具面板、参考文献等延迟渲染内容撑开后仍能定位到底部
   */
  function forceScrollToBottom() {
    const el = messagesRef.value
    if (!el) return
    const doScroll = () => { el.scrollTop = el.scrollHeight }
    nextTick(() => {
      doScroll()
      requestAnimationFrame(doScroll)
      setTimeout(doScroll, 100)
      setTimeout(doScroll, 300)
      setTimeout(doScroll, 600)
    })
  }

  /**
   * 展开/折叠内容后，将展开的区域滚动到可视区域内
   * @param {number} msgIndex - 消息在列表中的索引
   * @param {HTMLElement} [expandEl] - 展开的元素（可选，用于定位）
   */
  function scrollAfterExpand(msgIndex, expandEl) {
    nextTick(() => {
      requestAnimationFrame(() => {
        const container = messagesRef.value
        if (!container) return
        // 优先用展开元素定位，否则用消息元素
        const target = expandEl || container.querySelector(`[data-index="${msgIndex}"]`)
        if (!target) return
        const containerRect = container.getBoundingClientRect()
        const targetRect = target.getBoundingClientRect()
        // 目标元素底部超出可视区域，滚动使其可见
        if (targetRect.bottom > containerRect.bottom) {
          container.scrollTop += targetRect.bottom - containerRect.bottom + 16
        }
      })
    })
  }

  /**
   * 滚动事件处理器：距离检测 + 触顶加载更早消息
   * @param {object} opts
   * @param {() => Promise<void>} opts.loadOlderMessages
   * @param {import('vue').Ref<boolean>} opts.hasMoreMessages
   * @param {import('vue').Ref<boolean>} opts.loadingOlder
   */
  function createScrollHandler({ loadOlderMessages, hasMoreMessages, loadingOlder }) {
    return () => {
      handleScroll()
      const container = messagesRef.value
      if (container && container.scrollTop < 50 && hasMoreMessages.value && !loadingOlder.value && !streaming.value) {
        loadOlderMessages()
      }
    }
  }

  return {
    isNearBottom,
    userScrolledUp,
    virtualizer,
    handleScroll,
    onCapabilityHeightChange,
    scrollToBottom,
    scrollReasoningToBottom,
    forceScrollToBottom,
    scrollAfterExpand,
    createScrollHandler,
  }
}
