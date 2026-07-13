import { ref, nextTick } from 'vue'
import { message } from 'ant-design-vue'
import { useRouter } from 'vue-router'
import { chatStream, stopChatStream } from '../../api/chat'
import { createSession, getSessionMessages } from '../../api/chatSession'
import { validatePendingAttachmentMix } from '../../utils/chatAttachment'
import { enrichVideoThumbnails } from '../../utils/videoThumbnail'
import { safeJsonParse } from '../../utils/request'
import { getToolBlockOffsets, markToolBlockDone } from './useChatEventPartition.js'
import { registerToolBlockOffset } from './useChatCapabilityStream.js'
import { normalizeAssistantMessageErrors, hasMessageErrorState, resolveDeleteAssistantMessageId } from '../../utils/chat/messageErrorState.js'

export function clearErrorRetry(msg) {
  if (msg?._errorRetry) {
    msg._errorRetry = null
  }
}

/**
 * SSE 流式对话总线：发送、重生成、中止、Ask User 回复
 * @param {object} deps
 */
export function useChatStream(deps) {
  const {
    sessionId,
    messages,
    messagesRef,
    loading,
    streaming,
    hasStreamContent,
    currentStatus,
    reconnecting,
    lastReplyElapsed,
    abortController,
    userStoppedStream,
    toolEvents,
    isNearBottom,
    userScrolledUp,
    skipNextWatch,
    input,
    inputHistory,
    historyIndex,
    pendingAttachments,
    inputRef,
    replyTo,
    canSend,
    workflowConfirmBlocked,
    selectedAgentId,
    selectedConfigVersion,
    selectedAgentVersionId,
    sessionTokenCount,
    streamSmoother,
    getCurrentStreamingMsg,
    setCurrentStreamingMsg,
    scrollToBottom,
    scrollReasoningToBottom,
    applyStreamDoneMetadata,
    loadBatchFeedbacks,
    pollSessionTitle,
    isAskUserUnanswered,
    showAskUserModal,
    askUserModal,
    cancelReply,
    autoResize,
    canRegenerate,
    getMsgMentions,
    handleChatWorkflowStreamEvent,
    handleChatCapabilityStreamEvent,
    applyToolMetadata,
  } = deps

  let sendStartTime = 0

  async function submitAskUserResponse(answer) {
    if (!answer?.trim()) return
    askUserModal.visible = false
    const text = answer.trim()
    messages.value.push({ role: 'user', content: text, _attachments: [] })
    isNearBottom.value = true
    userScrolledUp.value = false
    scrollToBottom()
    await runChatStream({ message: text, attachments: [], regenerate: false })
  }

  /** 流被用户终止后，仅在本次请求确实落库时同步消息 ID（禁止借用历史助手消息 ID） */
  async function syncAbortedAssistantMessageId(sid, assistantMsg, sendStartMs) {
    if (!sid || !assistantMsg || assistantMsg._persisted) return
    // 终止/错误/敏感拦截等未通过 DONE 落库的消息，不应绑定会话里其它助手消息的 ID
    if (assistantMsg._terminated || assistantMsg._error || hasMessageErrorState(assistantMsg)) {
      return
    }
    for (let attempt = 0; attempt < 4; attempt++) {
      await new Promise(r => setTimeout(r, 250 * (attempt + 1)))
      if (assistantMsg._persisted) return
      try {
        const res = await getSessionMessages(sid, { pageNum: 1, pageSize: 5 })
        const records = res.data?.records || []
        const latestAssistant = records.find(m => {
          const role = String(m.role?.code || m.role || '').toLowerCase()
          return role === 'assistant'
        })
        if (!latestAssistant?.id) continue
        const createdAt = latestAssistant.createTime ? new Date(latestAssistant.createTime).getTime() : 0
        if (sendStartMs && createdAt > 0 && createdAt < sendStartMs - 3000) {
          continue
        }
        assistantMsg._id = String(latestAssistant.id)
        assistantMsg._persisted = true
        assistantMsg._createTime = latestAssistant.createTime || assistantMsg._createTime
        await loadBatchFeedbacks([assistantMsg])
        return
      } catch {
        // 忽略，继续重试
      }
    }
  }

  function finalizeAbortedStream(assistantMsg, pushed) {
    if (assistantMsg?._terminated) {
      streamSmoother.stop()
      setCurrentStreamingMsg(null)
      reconnecting.value = false
      loading.value = false
      streaming.value = false
      hasStreamContent.value = false
      currentStatus.value = ''
      abortController.value = null
      userStoppedStream.value = false
      return
    }

    streamSmoother.stop()
    setCurrentStreamingMsg(null)
    reconnecting.value = false

    let targetMsg = assistantMsg
    if (!pushed) {
      targetMsg = {
        role: 'assistant',
        content: '*AI 输出已终止*',
        _streaming: false,
        _toolsDone: true,
        _toolEvents: [],
        _workflowEvents: [],
        _toolBlockOffsets: [],
        _toolBlocksDone: [],
        _toolExpanded: false,
        _reasoningContent: '',
        _reasoningExpanded: true,
        _reasoningDone: true,
        _terminated: true,
        _persisted: false,
      }
      messages.value.push(targetMsg)
    } else if (assistantMsg) {
      if (!assistantMsg._toolBlockOffsets?.length) {
        assistantMsg._toolBlockOffsets = getToolBlockOffsets(assistantMsg)
      }
      if (!assistantMsg.content?.includes('AI 输出已终止')) {
        assistantMsg.content = (assistantMsg.content || '') + (assistantMsg.content ? '\n\n' : '') + '*AI 输出已终止*'
      }
      assistantMsg._streaming = false
      assistantMsg._toolsDone = true
      assistantMsg._toolExpanded = false
      assistantMsg._terminated = true
      targetMsg = assistantMsg
    }

    if (!targetMsg._persisted) {
      targetMsg._id = null
      targetMsg._persisted = false
    }

    normalizeAssistantMessageErrors(targetMsg)

    loading.value = false
    streaming.value = false
    hasStreamContent.value = false
    currentStatus.value = ''
    lastReplyElapsed.value = Date.now() - sendStartTime
    abortController.value = null
    userStoppedStream.value = false
    // 后端可能在流中断后仍落库并生成标题，继续轮询
    pollSessionTitle(sessionId.value)
    syncAbortedAssistantMessageId(sessionId.value, targetMsg, sendStartTime)

    if (isNearBottom.value) {
      nextTick(() => {
        const el = messagesRef.value
        if (el) el.scrollTop = el.scrollHeight
      })
    }
  }

  const router = useRouter()

  async function runChatStream({ message: msgText, attachments, mentions, regenerate, editMessageId: editMsgId, replyToMessageId: replyMsgId, deleteAssistantMessageId }) {
    loading.value = true
    streaming.value = true
    hasStreamContent.value = false
    lastReplyElapsed.value = null
    currentStatus.value = '正在思考...'
    toolEvents.value = []
    sendStartTime = Date.now()

    let assistantMsg = null
    let pushed = false
    let pendingRequestId = null
    abortController.value = new AbortController()

    const attachRequestId = (msg) => {
      if (msg && pendingRequestId) {
        msg._requestId = pendingRequestId
      }
    }

    const ensureAssistantMsg = (toolExpanded = false) => {
      if (pushed) return assistantMsg
      messages.value.push({
        role: 'assistant',
        content: '',
        _streaming: true,
        _toolsDone: false,
        _toolEvents: [],
        _workflowEvents: [],
        _toolBlockOffsets: [],
        _toolBlocksDone: [],
        _toolExpanded: toolExpanded,
        _reasoningContent: '',
        _reasoningExpanded: true,
        _reasoningDone: false,
        _persisted: false,
      })
      assistantMsg = messages.value[messages.value.length - 1]
      setCurrentStreamingMsg(assistantMsg)
      attachRequestId(assistantMsg)
      pushed = true
      hasStreamContent.value = true
      streamSmoother.start()
      return assistantMsg
    }

    try {
      let sid = sessionId.value
      const currentAgentId = selectedAgentId.value

      if (!sid) {
        const res = await createSession(currentAgentId || undefined)
        sid = res.data.id
        skipNextWatch.value = true
        router.replace(`/app/chat/${sid}`)
      }

      const chatPayload = {
        message: msgText || undefined,
        sessionId: sid,
        agentId: currentAgentId || undefined,
        configVersion: selectedConfigVersion.value ?? 0,
        agentVersionId: selectedAgentVersionId.value || undefined,
        regenerate: regenerate || undefined,
        deleteAssistantMessageId: deleteAssistantMessageId || undefined,
        editMessageId: editMsgId || undefined,
        replyToMessageId: replyMsgId || undefined,
        mentions: mentions?.length ? mentions.map(m => ({
          type: m.type,
          resourceId: String(m.resourceId),
          name: m.name,
          token: m.token,
        })) : undefined,
        attachments: attachments?.length ? attachments.map(a => ({
          id: a.id,
          type: a.type,
          mimeType: a.mimeType,
          objectKey: a.objectKey,
          previewUrl: a.previewUrl,
          fileName: a.fileName,
        })) : undefined,
      }
      await chatStream(
        chatPayload,
        {
          onRequestId: (requestId) => {
            if (requestId) {
              pendingRequestId = requestId
              attachRequestId(assistantMsg)
            }
          },
          // onChunk: 正文 chunk（深度思考由后端 reasoning_content 事件推送）
          onChunk: (chunk) => {
            if (reconnecting.value) reconnecting.value = false
            if (!pushed) {
              messages.value.push({ role: 'assistant', content: '', _streaming: true, _toolsDone: false, _toolEvents: [], _workflowEvents: [], _toolBlockOffsets: [], _toolBlocksDone: [], _toolExpanded: false, _reasoningContent: '', _reasoningExpanded: true, _reasoningDone: false, _persisted: false })
              assistantMsg = messages.value[messages.value.length - 1]
              setCurrentStreamingMsg(assistantMsg)
              attachRequestId(assistantMsg)
              pushed = true
              hasStreamContent.value = true
              streamSmoother.start()
            }
            streamSmoother.push(chunk)
          },
          // onStatus: 状态消息
          onStatus: (status) => {
            currentStatus.value = status
            scrollToBottom()
          },
          // onToolEvent: 工具调用/结果/状态事件
          onToolEvent: (event) => {
            ensureAssistantMsg(event.type === 'tool_call' || event.type === 'tool_result')
            if (event.type === 'tool_complete') {
              const offset = event.contentOffset ?? assistantMsg._currentToolOffset
              markToolBlockDone(assistantMsg, offset)
              return
            }
            if (event.type === 'reasoning_content') {
              if (!pushed) {
                messages.value.push({ role: 'assistant', content: '', _streaming: true, _toolsDone: false, _toolEvents: [], _workflowEvents: [], _toolBlockOffsets: [], _toolBlocksDone: [], _toolExpanded: false, _reasoningContent: '', _reasoningExpanded: true, _reasoningDone: false, _persisted: false })
                assistantMsg = messages.value[messages.value.length - 1]
                setCurrentStreamingMsg(assistantMsg)
                attachRequestId(assistantMsg)
                pushed = true
                hasStreamContent.value = true
                streamSmoother.start()
              }
              clearErrorRetry(assistantMsg)
              assistantMsg._reasoningContent = (assistantMsg._reasoningContent || '') + event.content
              assistantMsg._reasoningDone = false
              scrollToBottom()
              scrollReasoningToBottom()
              return
            }
            // 敏感词拦截事件：标记消息为拦截状态
            if (event.type === 'sensitive_block') {
              assistantMsg._sensitiveBlock = event.scope || 'ai_output'
              assistantMsg.content = event.message || assistantMsg.content
              assistantMsg._streaming = false
              assistantMsg._toolsDone = true
              loading.value = false
              streaming.value = false
              hasStreamContent.value = false
              currentStatus.value = ''
              lastReplyElapsed.value = Date.now() - sendStartTime
              abortController.value = null
              return
            }
            // 1.3 模型调用重试事件：保留流式状态，只更新专门提示块
            if (event.type === 'error_retry') {
              assistantMsg._errorRetry = {
                message: event.message || 'AI连接异常，正在重试中',
                code: event.code || 'LLM_ERROR',
                attempt: event.attempt || 1,
                maxRetries: event.maxRetries || event.attempt || 1,
              }
              currentStatus.value = assistantMsg._errorRetry.message
              scrollToBottom()
              return
            }
            // 1.3 结构化错误事件：LLM 调用中断、工具异常等
            if (event.type === 'error') {
              assistantMsg._error = {
                message: event.message || '未知错误',
                code: event.code || 'UNKNOWN',
              }
              assistantMsg._errorRetry = null
              assistantMsg._persisted = false
              assistantMsg._id = null
              normalizeAssistantMessageErrors(assistantMsg)
              assistantMsg._streaming = false
              assistantMsg._toolsDone = true
              loading.value = false
              streaming.value = false
              hasStreamContent.value = false
              currentStatus.value = ''
              lastReplyElapsed.value = Date.now() - sendStartTime
              abortController.value = null
              scrollToBottom()
              return
            }
            const isWorkflowStreamEvent = event.type === 'workflow_llm_chunk'
              || event.type === 'workflow_node_start'
              || event.type === 'workflow_node_complete'
              || event.type === 'workflow_node_retry'
              || event.type === 'workflow_node_failure'
              || event.type === 'workflow_complete'
              || event.type === 'workflow_confirm_required'
              || event.type === 'workflow_suspended'
            if (isWorkflowStreamEvent) {
              ensureAssistantMsg()
              handleChatWorkflowStreamEvent(assistantMsg, event)
              return
            }

            streamSmoother.flush()

            ensureAssistantMsg()
            if (handleChatCapabilityStreamEvent(assistantMsg, event)) {
              return
            }

            const offset = event.contentOffset ?? assistantMsg.content.length
            if (event.contentOffset == null) {
              event.contentOffset = offset
            }
            if (event.type === 'tool_call') {
              assistantMsg._toolExpanded = true
              assistantMsg._currentToolOffset = offset
              registerToolBlockOffset(assistantMsg, offset)
            } else if (assistantMsg._currentToolOffset == null || assistantMsg._currentToolOffset < 0) {
              assistantMsg._currentToolOffset = offset
              registerToolBlockOffset(assistantMsg, offset)
            }

            assistantMsg._toolEvents.push(event)
            toolEvents.value.push(event)
            if (event.type === 'tool_status' && event.message) {
              currentStatus.value = event.message
            }
            scrollToBottom()
          },
          // onMetadata: metadata消息（含工具事件与 offset，每轮工具调用后更新）
          onMetadata: (metadataStr) => {
            if (!assistantMsg) return
            applyToolMetadata(assistantMsg, safeJsonParse(metadataStr))
          },
          // onDone: 完成
          onDone: (meta) => {
            // 用户主动停止时仍合并 [DONE] 中的消息 ID（后端可能已完成落库）
            if (userStoppedStream.value) {
              if (assistantMsg) {
                applyStreamDoneMetadata(assistantMsg, meta)
                if (assistantMsg._id) loadBatchFeedbacks([assistantMsg])
              }
              return
            }
            streamSmoother.stop()
            setCurrentStreamingMsg(null)
            reconnecting.value = false
            if (assistantMsg) {
              assistantMsg._reasoningDone = true
              assistantMsg._streaming = false
              assistantMsg._toolsDone = true
              assistantMsg._toolExpanded = false
              applyStreamDoneMetadata(assistantMsg, meta)
              applyToolMetadata(assistantMsg, typeof assistantMsg.metadata === 'object' ? assistantMsg.metadata : null)
              assistantMsg._toolBlockOffsets = getToolBlockOffsets(assistantMsg)
              if (assistantMsg._workflowConfirmPending) {
                loading.value = false
                streaming.value = false
              }
              if (assistantMsg._id) {
                loadBatchFeedbacks([assistantMsg])
              }
            }
            loading.value = false
            streaming.value = false
            hasStreamContent.value = false
            if (!assistantMsg?._workflowConfirmPending) {
              currentStatus.value = ''
            }
            lastReplyElapsed.value = Date.now() - sendStartTime
            abortController.value = null
            // 轮询等待标题生成完成
            pollSessionTitle(sid)
            // 流式结束后滚动到底部（延迟等待渲染完成）
            if (isNearBottom.value) {
              setTimeout(() => {
                const el = messagesRef.value
                if (el) el.scrollTop = el.scrollHeight
              }, 300)
            }
            // 流式结束后自动弹出 ask_user 弹窗
            nextTick(() => {
              const lastIdx = messages.value.length - 1
              if (lastIdx >= 0 && messages.value[lastIdx].role === 'assistant' && isAskUserUnanswered(lastIdx)) {
                showAskUserModal(lastIdx)
              }
            })
          },
        },
        abortController.value?.signal,
        { maxRetries: 3, retryDelay: 2000, onReconnecting: () => {
          reconnecting.value = true
          currentStatus.value = '正在重连...'
        }}
      )
      if (userStoppedStream.value) {
        finalizeAbortedStream(assistantMsg, pushed)
        return
      }
    } catch (e) {
      reconnecting.value = false
      if (e.name === 'AbortError') {
        finalizeAbortedStream(assistantMsg, pushed)
        return
      }
      streamSmoother.stop()
      setCurrentStreamingMsg(null)
      if (!assistantMsg) {
        messages.value.push({ role: 'assistant', content: '', _streaming: false, _toolsDone: true, _toolEvents: [], _workflowEvents: [], _toolBlockOffsets: [], _toolBlocksDone: [], _toolExpanded: false, _reasoningContent: '', _reasoningExpanded: true, _reasoningDone: true, _persisted: false })
        assistantMsg = messages.value[messages.value.length - 1]
      }
      assistantMsg._error = {
        message: 'AI 大模型调用失败，请检查模型配置是否正确。\n\n错误详情：' + (e.message || '未知错误'),
        code: 'REQUEST_ERROR',
      }
      assistantMsg._persisted = false
      assistantMsg._id = null
      normalizeAssistantMessageErrors(assistantMsg)
      assistantMsg._streaming = false
      assistantMsg._toolsDone = true
      loading.value = false
      streaming.value = false
      hasStreamContent.value = false
      currentStatus.value = ''
      abortController.value = null
      pollSessionTitle(sessionId.value)
    }
  }

  async function sendMessage() {
    const text = input.value.trim()
    const attachments = [...pendingAttachments.value]
    if ((!text && attachments.length === 0) || !canSend.value) {
      if (workflowConfirmBlocked.value && (text || attachments.length)) {
        message.warning('请先完成工作流人工确认表单，再发送新消息')
      }
      return
    }
    const mixCheck = validatePendingAttachmentMix(attachments)
    if (!mixCheck.ok) {
      message.warning(mixCheck.message)
      return
    }

    // 从 ChatMentionInput 提取结构化 mentions（在重置 input 之前）
    const mentionInputComp = inputRef.value
    const sentMentions = mentionInputComp?.getMentions?.() || undefined

    const displayContent = text || (attachments.length ? '[附件]' : '')
    const sentAttachments = attachments.map(a => ({ ...a }))
    await enrichVideoThumbnails(sentAttachments)

    const userMsg = { role: 'user', content: displayContent, _attachments: sentAttachments }
    // 携带 mention 快照（用于前端 chip 渲染，后端 message.metadata 也会持久化一份）
    if (sentMentions?.length) {
      userMsg._mentions = sentMentions
    }
    // 携带引用回复信息（用于前端渲染引用摘要）
    const currentReplyToId = replyTo.active ? replyTo.messageId : null
    const currentReplyToContent = replyTo.active ? replyTo.content : ''
    const currentReplyToRole = replyTo.active ? replyTo.role : ''
    if (currentReplyToId) {
      userMsg._replyToMessageId = currentReplyToId
      userMsg._replyToContent = currentReplyToContent
      userMsg._replyToRole = currentReplyToRole
    }
    messages.value.push(userMsg)
    // 记录输入历史（去重：与上一条相同则不重复记录）
    if (text && (inputHistory.value.length === 0 || inputHistory.value[inputHistory.value.length - 1] !== text)) {
      inputHistory.value.push(text)
    }
    historyIndex.value = -1
    input.value = ''
    // 清空 ChatMentionInput 中的 chip（input 清空会触发 watch 重置 innerHTML，mentions 也需手动清）
    mentionInputComp?.clear?.()
    pendingAttachments.value = []
    cancelReply()
    autoResize()
    isNearBottom.value = true
    userScrolledUp.value = false
    scrollToBottom()

    await runChatStream({
      message: text,
      attachments: sentAttachments,
      mentions: sentMentions,
      regenerate: false,
      replyToMessageId: currentReplyToId,
    })
  }

  async function regenerateReply(assistantIndex) {
    if (loading.value || !canRegenerate(assistantIndex)) return
    let userIdx = assistantIndex - 1
    while (userIdx >= 0 && messages.value[userIdx].role !== 'user') {
      userIdx--
    }
    if (userIdx < 0) return
    const userMsg = messages.value[userIdx]
    const assistantMsg = messages.value[assistantIndex]
    const deleteId = resolveDeleteAssistantMessageId(assistantMsg)
    // 仅移除当前助手消息；未落库时不传 deleteAssistantMessageId，避免误删历史成功回复
    if (assistantIndex === messages.value.length - 1) {
      messages.value.pop()
    } else {
      messages.value.splice(assistantIndex, 1)
    }
    isNearBottom.value = true
    userScrolledUp.value = false
    scrollToBottom()
    await runChatStream({
      message: userMsg.content === '[附件]' ? '' : (userMsg.content || ''),
      attachments: userMsg._attachments || [],
      mentions: getMsgMentions(userMsg).length ? getMsgMentions(userMsg) : undefined,
      regenerate: true,
      deleteAssistantMessageId: deleteId,
    })
  }

  function stopGenerating() {
    // 通知后端停止：置中断标记使 in-flight LLM 立即停，并连带取消子任务（fire-and-forget）
    const requestId = getCurrentStreamingMsg()?._requestId
    if (requestId) {
      stopChatStream(requestId).catch(() => {})
    }
    if (abortController.value) {
      userStoppedStream.value = true
      abortController.value.abort()
      abortController.value = null
    }
    // 兜底：AbortError 未及时触发时仍结束流式状态
    setTimeout(() => {
      if (!streaming.value) return
      const msg = getCurrentStreamingMsg()
        || [...messages.value].reverse().find(m => m.role === 'assistant' && m._streaming)
      if (msg) finalizeAbortedStream(msg, messages.value.includes(msg))
    }, 120)
  }

  return {
    clearErrorRetry,
    submitAskUserResponse,
    syncAbortedAssistantMessageId,
    finalizeAbortedStream,
    runChatStream,
    sendMessage,
    regenerateReply,
    stopGenerating,
  }
}
