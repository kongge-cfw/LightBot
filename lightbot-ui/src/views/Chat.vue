<template>
  <div class="chat-container">
    <ChatTopBar
      v-if="sessionId"
      :session-title="sessionTitle"
      :title-editing="titleEditing"
      :title-edit-value="titleEditValue"
      :show-file-drawer="!!sessionId"
      :show-subagent-drawer="!!sessionId"
      :subagent-running-count="runningSubagentCount"
      :title-editable="!!sessionId"
      @start-title-edit="startTitleEdit"
      @confirm-title-edit="confirmTitleEdit"
      @cancel-title-edit="cancelTitleEdit"
      @update:title-edit-value="titleEditValue = $event"
      @open-file-drawer="openFileDrawer"
      @open-subagent-drawer="subagentRuntimeOpen = true"
    />

    <div ref="messagesRef" class="chat-messages">
      <ChatWelcomeState
        v-if="!sessionId && messages.length === 0 && !loadingHistory"
        :welcome-message="currentWelcomeMessage"
        :recommended-questions="currentRecommendedQuestions"
        :selected-agent-id="selectedAgentId"
        :agents-length="agents.length"
        @select-question="applyRecommendedQuestion"
      />

      <div v-if="loadingHistory" class="history-loading">
        <LoadingOutlined spin class="history-loading-icon" />
      </div>
      <div
        v-else
        class="virtual-list-container"
        :style="{ height: virtualizer.getTotalSize() + 'px', position: 'relative' }"
      >
        <div
          v-for="virtualRow in virtualizer.getVirtualItems()"
          :key="virtualRow.key"
          :data-index="virtualRow.index"
          :ref="el => { if (el) virtualizer.measureElement(el) }"
          :style="{
            position: 'absolute',
            top: 0,
            left: 0,
            width: '100%',
            transform: `translateY(${virtualRow.start}px)`,
          }"
        >
          <ChatMessageRow
            :ref="el => setMessageRowComponentRef(virtualRow.index, el)"
            :msg="messages[virtualRow.index]"
            :index="virtualRow.index"
            :loading="loading"
            :streaming="streaming"
            :editing-message-id="editingMessageId"
            :edit-content="editContent"
            :highlight-message-id="highlightMessageId"
            :feedback-type="getMessageFeedbackType(messages[virtualRow.index])"
            :can-regenerate="canRegenerate(virtualRow.index)"
            :is-last-user-message="isLastUserMessage(virtualRow.index)"
            :speaking-msg-key="speakingMsgKey"
            :show-tts-btn="showTtsBtn"
            :last-reply-elapsed="lastReplyElapsed"
            :selected-agent-id="selectedAgentId"
            :selected-agent-version-id="selectedAgentVersionId"
            :get-att-thumb-url="getAttThumbUrl"
            :messages="messages"
            :messages-length="messages.length"
            :refs-section-expanded="isRefsSectionExpanded(messages[virtualRow.index])"
            :is-ref-expanded="(refIndex) => isReferenceExpanded(messages[virtualRow.index], refIndex)"
            @update:edit-content="editContent = $event"
            @cancel-edit="cancelEdit"
            @submit-edit="submitEdit"
            @scroll-to-reply="scrollToMessage"
            @preview-attachment="onAttachmentPreview"
            @height-change="onCapabilityHeightChange"
            @copy="copyMessage(messages[virtualRow.index])"
            @regenerate="regenerateReply(virtualRow.index)"
            @edit="startEdit(virtualRow.index)"
            @reply="startReply(virtualRow.index)"
            @delete="handleDeleteMessage(virtualRow.index)"
            @star="toggleStarMessage(virtualRow.index)"
            @feedback-like="handleMessageFeedback(messages[virtualRow.index], 'like')"
            @show-dislike="showDislikeModal(messages[virtualRow.index])"
            @speak="speakMessage(messages[virtualRow.index], virtualRow.index)"
            @view-raw="openRawModal(virtualRow.index)"
            @copy-request-id="copyRequestId(messages[virtualRow.index])"
            @copy-message-id="copyMessageId(messages[virtualRow.index])"
            @workflow-confirm-submit="formData => submitWorkflowConfirm(messages[virtualRow.index], formData)"
            @workflow-confirm-abandon="() => abandonWorkflowConfirm(messages[virtualRow.index])"
            @rag-toggle="onRagToggle(virtualRow.index, $event)"
            @reasoning-toggle="toggleReasoningExpand(virtualRow.index)"
            @go-knowledge="({ knowledgeId, documentId }) => goToKnowledge(knowledgeId, documentId)"
          />
        </div>
      </div>

      <ChatStreamingPlaceholder v-if="loading && !streaming" :status-text="currentStatus" :status-badges="streamingSubagentBadges" />
      <ChatStreamingPlaceholder
        v-if="loading && streaming && !hasStreamContent && !hasStreamingAssistantMessage"
        :status-text="currentStatus"
        :status-badges="streamingSubagentBadges"
      />
    </div>

    <ChatInputArea
      ref="chatInputAreaRef"
      v-model:input="input"
      :loading="loading"
      :can-send="canSend"
      :workflow-confirm-blocked="workflowConfirmBlocked"
      :ask-user-blocked="askUserModal.visible"
      :switching-session="inputMaskLoading"
      :agents="agents"
      :selected-agent-id="selectedAgentId"
      :selected-agent-version-id="selectedAgentVersionId"
      :current-agent="currentAgent"
      :config-version-options="configVersionOptions"
      :selected-config-version="selectedConfigVersion"
      :session-token-count="sessionTokenCount"
      :show-file-upload-btn="showFileUploadBtn"
      :file-upload-hint="fileUploadHint"
      :image-upload-hint="imageUploadHint"
      :file-accept-types="fileAcceptTypes"
      :image-accept-types="imageAcceptTypes"
      :document-accept-types="documentAcceptTypes"
      :uploading="uploading"
      :show-voice-input-btn="showVoiceInputBtn"
      :voice-listening="voiceListening"
      :reply-to="replyTo"
      :pending-attachments="pendingAttachments"
      :get-att-thumb-url="getAttThumbUrl"
      :show-input-disclaimer="showInputDisclaimer"
      :input-hint-questions="inputHintQuestions"
      :question-rotate-index="questionRotateIndex"
      @send="sendMessage"
      @stop="stopGenerating"
      @agent-select="handleAgentSelect"
      @config-version-change="onConfigVersionChange"
      @file-selected="onFileSelected"
      @remove-attachment="removeAttachment"
      @attachment-preview="onAttachmentPreview"
      @toggle-voice="toggleVoiceInput"
      @cancel-reply="cancelReply"
      @apply-question="applyRecommendedQuestion"
    />

    <ChatAttachmentPreview
      v-model:open="attachmentPreviewOpen"
      :attachment="attachmentPreviewAtt"
    />

    <ChatRawContentModal
      v-model:open="rawModal.visible"
      :title="rawModal.title"
      :content="rawModal.content"
      :copied="rawModal.copied"
      :metadata="rawModal.metadata"
      v-model:metadata-open="metadataModal.visible"
      :metadata-json="metadataModal.json"
      :metadata-copied="metadataModal.copied"
      @copy-raw="copyRawContent"
      @copy-metadata="copyMetadata"
      @open-metadata="openMetadataModal"
    />

    <ChatAskUserModal
      v-model:open="askUserModal.visible"
      :questions="askUserModal.questions"
      @submit="submitAskUserResponse"
    />

    <ChatFeedbackDislikeModal
      v-model:open="dislikeModalVisible"
      v-model:reason="dislikeReason"
      @submit="submitDislikeReason"
      @skip="skipDislikeReason"
      @close="closeDislikeModal"
    />

    <ChatSessionFilesDrawer
      v-model:open="fileDrawerOpen"
      :session-id="sessionId"
      :file-stats="fileStats"
      :file-drawer-loading="fileDrawerLoading"
      :file-tree-refresh-tick="fileTreeRefreshTick"
      :tooltip-popup-container="tooltipPopupContainer"
      @after-open-change="onFileDrawerOpened"
      @refresh="refreshSessionFiles"
      @preview="openSessionFilePreviewModal"
      @refreshed="onFileTreeRefreshed"
    />

    <ChatSubAgentRuntimeDrawer
      v-model:open="subagentRuntimeOpen"
      :session-id="sessionId"
      :live-events="liveSubagentEvents"
    />

    <SessionFilePreviewModal
      v-model:open="sessionFilePreviewOpen"
      :session-id="sessionId"
      :file="sessionFilePreviewTarget"
    />


  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { LoadingOutlined } from '@ant-design/icons-vue'
import ChatAttachmentPreview from '../components/ChatAttachmentPreview.vue'
import SessionFilePreviewModal from '../components/SessionFilePreviewModal.vue'
import ChatTopBar from '../components/chat/ChatTopBar.vue'
import ChatWelcomeState from '../components/chat/ChatWelcomeState.vue'
import ChatMessageRow from '../components/chat/message/ChatMessageRow.vue'
import ChatInputArea from '../components/chat/input/ChatInputArea.vue'
import ChatRawContentModal from '../components/chat/modals/ChatRawContentModal.vue'
import ChatAskUserModal from '../components/chat/modals/ChatAskUserModal.vue'
import ChatFeedbackDislikeModal from '../components/chat/modals/ChatFeedbackDislikeModal.vue'
import ChatSessionFilesDrawer from '../components/chat/session/ChatSessionFilesDrawer.vue'
import ChatSubAgentRuntimeDrawer from '../components/chat/session/ChatSubAgentRuntimeDrawer.vue'
import ChatStreamingPlaceholder from '../components/chat/session/ChatStreamingPlaceholder.vue'
import { buildSubagentLiveStatusBadges } from '../components/capabilities/subagentEventUtils.js'
import { useChatAgents } from '../composables/useChatAgents'
import { useChatAttachments } from '../composables/useChatAttachments'
import { useVoiceIO } from '../composables/useVoiceIO'
import { useAskUser } from '../composables/useAskUser'
import { useStreamSmoother } from '../composables/useStreamSmoother'
import {
  useChatSendGate,
  createChatWorkflowStreamHandlers,
  createChatCapabilityStreamHandlers,
  useChatMessageModel,
  useChatFeedback,
  useChatRagRefs,
  useChatInputHint,
  useChatScroll,
  useChatHistory,
  useChatStream,
  useChatMessageActions,
  useChatSessionChrome,
  getMsgRagRefs,
  clearErrorRetry,
} from '../composables/chat'

const route = useRoute()
const router = useRouter()

const sessionId = computed(() => route.params.sessionId || null)

const input = ref('')
const inputHistory = ref([])
const historyIndex = ref(-1)
const loading = ref(false)
const streaming = ref(false)
const messages = ref([])
const sessionTokenCount = ref(0)
const messagesRef = ref(null)
const inputRef = ref(null)
const chatInputAreaRef = ref(null)
const skipNextWatch = ref(false)
const loadingHistory = ref(false)
const currentStatus = ref('')
const reconnecting = ref(false)
const lastReplyElapsed = ref(null)
const hasStreamContent = ref(false)
const userStoppedStream = ref(false)
const abortController = ref(null)
const toolEvents = ref([])
const pendingAttachments = ref([])
const fileInputRef = ref(null)
const uploading = ref(false)
const attachmentPreviewOpen = ref(false)
const attachmentPreviewAtt = ref(null)
const voiceListening = ref(false)
const speakingMsgKey = ref(null)
const subagentRuntimeOpen = ref(false)
const liveSubagentEvents = ref([])

const runningSubagentCount = computed(() => {
  const taskStates = new Map()
  for (const event of liveSubagentEvents.value) {
    if (!event?.task_id) continue
    if (event.type === 'subagent_task_start') taskStates.set(event.task_id, 'running')
    if (event.type === 'subagent_task_done') taskStates.set(event.task_id, event.status || 'completed')
    if (event.type === 'subagent_error') taskStates.set(event.task_id, 'failed')
  }
  return [...taskStates.values()].filter(status => status === 'running').length
})

const streamingSubagentBadges = computed(() => {
  if (!loading.value && !streaming.value) return []
  return buildSubagentLiveStatusBadges(liveSubagentEvents.value)
})

function autoResize() {}

const {
  agents, agentsLoading, selectedAgentId, currentAgent, chatCapabilities,
  selectedConfigVersion, selectedAgentVersionId, configVersionOptions,
  showFileUploadBtn, showVoiceInputBtn, showTtsBtn,
  fileAcceptTypes, imageAcceptTypes, documentAcceptTypes, fileUploadHint, imageUploadHint,
  currentWelcomeMessage, currentRecommendedQuestions,
  handleAgentSelect, onConfigVersionChange,
  loadAgentConfigVersions, loadCurrentAgent, loadAgents,
} = useChatAgents({
  sessionId, loading, pendingAttachments, voiceListening,
  stopVoiceInput: () => { voiceListening.value = false },
})

watch(chatInputAreaRef, (area) => {
  if (area?.inputRef) inputRef.value = area.inputRef
}, { flush: 'post' })

const {
  getAttThumbUrl, openAttachmentPreview, onFileSelected, removeAttachment,
} = useChatAttachments({
  selectedAgentId, sessionId, chatCapabilities, pendingAttachments,
  fileInputRef, uploading, attachmentPreviewOpen, attachmentPreviewAtt,
})

const { toggleVoiceInput, stopVoiceInput, speakMessage, cleanup: voiceCleanup } = useVoiceIO({
  input, inputRef, chatCapabilities, autoResize, voiceListening, speakingMsgKey,
})

const {
  sessionTitle, titleEditing, titleEditValue,
  fileDrawerOpen, fileDrawerLoading, fileTreeRefreshTick, fileStats,
  fileDrawerLoadedOnce,
  sessionFilePreviewOpen, sessionFilePreviewTarget,
  startTitleEdit, confirmTitleEdit, cancelTitleEdit, tooltipPopupContainer,
  openFileDrawer, onFileDrawerOpened, refreshSessionFiles, onFileTreeRefreshed,
  onAttachmentPreview, openSessionFilePreviewModal, pollSessionTitle, cleanupPollTitleTimer,
} = useChatSessionChrome({
  sessionId,
  openAttachmentPreview,
  attachmentPreviewOpen,
  attachmentPreviewAtt,
})

const {
  getMessageFeedbackType, handleMessageFeedback, showDislikeModal,
  submitDislikeReason, skipDislikeReason, closeDislikeModal, loadBatchFeedbacks,
  dislikeModalVisible, dislikeReason,
} = useChatFeedback()

const editingMessageId = ref(null)
const editContent = ref('')

const {
  enrichMessagesAttachments, parseMessage, applyStreamDoneMetadata, getMsgMentions,
} = useChatMessageModel({ editingMessageId, messages, sessionTokenCount })

function scrollAfterExpandBridge(msgIndex, expandEl) {
  scrollAfterExpandBridge.impl?.(msgIndex, expandEl)
}

const {
  expandedRefsMap, refsSectionExpandedMap,
  isReferenceExpanded, toggleReference,
  isRefsSectionExpanded, toggleRefsSection, toggleReasoningExpand,
} = useChatRagRefs({ messages, scrollAfterExpand: scrollAfterExpandBridge })

const {
  isNearBottom, userScrolledUp, virtualizer,
  onCapabilityHeightChange, scrollToBottom, scrollReasoningToBottom,
  forceScrollToBottom, scrollAfterExpand, createScrollHandler,
} = useChatScroll({
  messages, messagesRef, streaming, getMsgRagRefs, refsSectionExpandedMap,
})

scrollAfterExpandBridge.impl = scrollAfterExpand

const { askUserModal, isAskUserUnanswered, showAskUserModal, recordAskUserAnswers } = useAskUser({ messages })

let currentStreamingMsg = null
const streamSmoother = useStreamSmoother({
  onFlush: (text) => {
    if (currentStreamingMsg) {
      clearErrorRetry(currentStreamingMsg)
      currentStreamingMsg.content += text
      scrollToBottom()
    }
  },
})

const {
  handleChatWorkflowStreamEvent,
  submitWorkflowConfirm,
  abandonWorkflowConfirm,
  applyToolMetadata,
} = createChatWorkflowStreamHandlers({
  loading, streaming, currentStatus, hasStreamContent, streamSmoother,
  scrollToBottom, clearErrorRetry,
  getCurrentStreamingMsg: () => currentStreamingMsg,
  setCurrentStreamingMsg: (msg) => { currentStreamingMsg = msg },
  getAbortController: () => abortController.value,
  setAbortController: (ctrl) => { abortController.value = ctrl },
  getSelectedAgentId: () => selectedAgentId.value,
})

const { handleChatCapabilityStreamEvent } = createChatCapabilityStreamHandlers({
  currentStatus, hasStreamContent, scrollToBottom, messages, messagesRef,
  onSubagentEvent: (event) => {
    liveSubagentEvents.value.push({ ...event })
    if (liveSubagentEvents.value.length > 600) {
      liveSubagentEvents.value.splice(0, liveSubagentEvents.value.length - 600)
    }
  },
})

const { canSend, workflowConfirmBlocked } = useChatSendGate({
  loading, input, pendingAttachments, messages,
})

const {
  inputHintQuestions, showInputDisclaimer, questionRotateIndex, applyRecommendedQuestion,
} = useChatInputHint({ messages, currentRecommendedQuestions, selectedAgentId, input, inputRef })

const messageRowComponentRefs = new Map()

function setMessageRowComponentRef(index, el) {
  if (el) messageRowComponentRefs.set(index, el)
  else messageRowComponentRefs.delete(index)
}

function resolveEditInputRefFn() {
  const eid = editingMessageId.value
  if (!eid) return null
  let idx = messages.value.findIndex(m => m._id === eid)
  if (idx < 0 && String(eid).startsWith('local-')) {
    const parsed = parseInt(String(eid).replace('local-', ''), 10)
    if (!Number.isNaN(parsed) && parsed < messages.value.length) idx = parsed
  }
  if (idx < 0) return null
  return messageRowComponentRefs.get(idx)?.getEditMentionInput?.() ?? null
}

const streamHolder = {}

const {
  replyTo, highlightMessageId, rawModal, metadataModal,
  canRegenerate, isLastUserMessage, startEdit, cancelEdit, startReply, cancelReply,
  toggleStarMessage, scrollToMessage, submitEdit,
  copyMessage, copyRequestId, copyMessageId,
  openRawModal, openMetadataModal, copyRawContent, copyMetadata,
  handleDeleteMessage, cleanupEditClickOutside,
} = useChatMessageActions({
  sessionId, messages, loading, streaming, virtualizer,
  isNearBottom, userScrolledUp, scrollToBottom,
  runChatStream: (opts) => streamHolder.runChatStream(opts),
  inputRef, editingMessageId, editContent, resolveEditInputRefFn,
})

const {
  submitAskUserResponse, runChatStream,
  sendMessage, regenerateReply, stopGenerating,
} = useChatStream({
  sessionId, messages, messagesRef, loading, streaming, hasStreamContent,
  currentStatus, reconnecting, lastReplyElapsed, abortController, userStoppedStream,
  toolEvents, isNearBottom, userScrolledUp, skipNextWatch, input, inputHistory,
  historyIndex, pendingAttachments, inputRef, replyTo, canSend, workflowConfirmBlocked,
  selectedAgentId, selectedConfigVersion, selectedAgentVersionId, sessionTokenCount,
  streamSmoother,
  getCurrentStreamingMsg: () => currentStreamingMsg,
  setCurrentStreamingMsg: (msg) => { currentStreamingMsg = msg },
  scrollToBottom, scrollReasoningToBottom, applyStreamDoneMetadata, loadBatchFeedbacks,
  pollSessionTitle, isAskUserUnanswered, showAskUserModal, askUserModal, recordAskUserAnswers, cancelReply,
  autoResize, canRegenerate, getMsgMentions,
  handleChatWorkflowStreamEvent, handleChatCapabilityStreamEvent, applyToolMetadata,
})

streamHolder.runChatStream = runChatStream

const {
  hasMoreMessages, loadingOlder, switchingSession,
  loadHistory, loadOlderMessages, onStreamingEnded,
} = useChatHistory({
  sessionId, messages, streaming, virtualizer, messagesRef,
  isNearBottom, userScrolledUp, forceScrollToBottom,
  selectedAgentId, currentAgent, loadAgentConfigVersions, loadCurrentAgent,
  loadBatchFeedbacks, parseMessage, enrichMessagesAttachments,
  isAskUserUnanswered, showAskUserModal, cancelReply,
  loadingHistory, lastReplyElapsed, sessionTokenCount, sessionTitle,
  input, inputHistory, historyIndex, pendingAttachments,
  fileDrawerLoadedOnce,
  sessionFilePreviewTarget, sessionFilePreviewOpen, fileStats,
  onSessionMissing: () => router.replace({ path: '/app/chat' }),
})

const hasStreamingAssistantMessage = computed(() =>
  messages.value.some(m => m.role === 'assistant' && m._streaming)
)

/** 输入框加载遮罩：首次加载 Agent 列表 或 切换会话期间 */
const inputMaskLoading = computed(() => agentsLoading.value || switchingSession.value)

function onRagToggle(index, payload) {
  const msg = messages.value[index]
  if (!msg) return
  if (payload?.kind === 'section') toggleRefsSection(msg)
  else if (payload?.kind === 'item') toggleReference(msg, payload.refIndex)
}

function goToKnowledge(knowledgeId, documentId) {
  const query = documentId ? { docId: String(documentId) } : {}
  router.push({ path: `/app/knowledge/${knowledgeId}`, query })
}

function handleChatKeydown(e) {
  if (e.ctrlKey && e.code === 'Slash') {
    e.preventDefault()
    chatInputAreaRef.value?.focusInput?.()
    return
  }
  if (e.key === 'Escape' && streaming.value) {
    e.preventDefault()
    stopGenerating()
  }
}

function abortActiveStreamOnUnload() {
  if (streaming.value && abortController.value) {
    abortController.value.abort()
    abortController.value = null
  }
}

const scrollHandler = createScrollHandler({ loadOlderMessages, hasMoreMessages, loadingOlder })

onMounted(async () => {
  const queryAgentId = route.query.agentId
  loadHistory()
  await loadAgents(queryAgentId || undefined)
  if (queryAgentId) {
    await loadCurrentAgent(queryAgentId)
    await loadAgentConfigVersions(queryAgentId)
    router.replace({ path: '/app/chat' })
  } else if (selectedAgentId.value) {
    await loadAgentConfigVersions(selectedAgentId.value)
  }
  if (messagesRef.value) {
    messagesRef.value.addEventListener('scroll', scrollHandler)
  }
  document.addEventListener('keydown', handleChatKeydown)
  window.addEventListener('beforeunload', abortActiveStreamOnUnload)
})

onUnmounted(() => {
  abortActiveStreamOnUnload()
  voiceCleanup()
  cleanupPollTitleTimer()
  cleanupEditClickOutside()
  if (messagesRef.value) {
    messagesRef.value.removeEventListener('scroll', scrollHandler)
  }
  document.removeEventListener('keydown', handleChatKeydown)
  window.removeEventListener('beforeunload', abortActiveStreamOnUnload)
})

watch(() => route.params.sessionId, () => {
  if (skipNextWatch.value) {
    skipNextWatch.value = false
    return
  }
  if (streaming.value && abortController.value) {
    userStoppedStream.value = true
    abortController.value.abort()
    abortController.value = null
  }
  expandedRefsMap.value = new Map()
  refsSectionExpandedMap.value = new Map()
  liveSubagentEvents.value = []
  messageRowComponentRefs.clear()
  loadHistory()
})

watch(streaming, onStreamingEnded)

watch(selectedAgentId, (newId) => {
  if (newId && !sessionId.value) {
    loadCurrentAgent(newId)
  } else if (!newId) {
    currentAgent.value = null
  }
})

watch(sessionId, (newVal, oldVal) => {
  if (!newVal && oldVal) loadAgents()
})
</script>

<style scoped>
.chat-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--color-canvas);
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 24px 0;
  /* 禁用滚动锚定：能力块展开时浏览器会为保持锚点不动而调整 scrollTop，
     导致块在视口上半时向上展开、下半时向下展开。关闭后统一从上往下展开 */
  overflow-anchor: none;
}

.chat-messages::-webkit-scrollbar {
  width: 6px;
}

.chat-messages::-webkit-scrollbar-thumb {
  background: #d4d4d8;
  border-radius: 3px;
}

.history-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 80px 0;
}

.history-loading-icon {
  font-size: 24px;
  color: var(--color-mute);
}

.virtual-list-container {
  width: 100%;
  max-width: 800px;
  margin: 0 auto;
}
</style>

<style>
.chat-upload-tooltip .chat-upload-hint {
  display: block;
  white-space: pre-line;
  line-height: 1.5;
}
</style>
