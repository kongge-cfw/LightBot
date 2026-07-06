<script setup>
import { ref, reactive, onMounted, onUnmounted, nextTick, watch, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { SendOutlined, CopyOutlined, CheckOutlined, RobotOutlined, FileTextOutlined, RightOutlined, LinkOutlined, PauseCircleOutlined, LoadingOutlined, CheckCircleOutlined, BulbOutlined, WarningOutlined, PaperClipOutlined, AudioOutlined, CloseOutlined, PlayCircleOutlined, EyeOutlined, SoundOutlined, ReloadOutlined, NumberOutlined, TagOutlined, DeleteOutlined, QuestionCircleOutlined, CodeOutlined, EditOutlined, CommentOutlined, ThunderboltOutlined, LikeOutlined, DislikeOutlined, LikeFilled, DislikeFilled, StarOutlined, StarFilled, CloseCircleOutlined, FolderOpenOutlined } from '@ant-design/icons-vue'
import { useUserStore } from '../stores/user'
import { formatTime } from '../utils/format'
import MarkdownPreview from '../components/MarkdownPreview.vue'
import ToolCallsGroupComponent from '../components/ToolCallsGroupComponent.vue'
import WorkflowNodesGroupComponent from '../components/WorkflowNodesGroupComponent.vue'
import WorkflowConfirmForm from '../components/WorkflowConfirmForm.vue'
import { isWorkflowAwaitingConfirm } from '../components/workflow/workflowStepUtils.js'
import { useChatSendGate } from '../composables/chat/useChatSendGate.js'
import { createChatWorkflowStreamHandlers } from '../composables/chat/useChatWorkflowStream.js'
import {
  getTopSkillEvents,
  getCapabilityEventsForOffset,
  getInlineCapabilityEvents,
  getPureToolEvents,
  getToolEventsForOffset,
  isToolBlockDone,
  splitContentByOffsets,
  isSegmentFinalized,
} from '../composables/chat/useChatEventPartition.js'
import { createChatCapabilityStreamHandlers, registerToolBlockOffset } from '../composables/chat/useChatCapabilityStream.js'
import {
  useChatScroll,
  useChatHistory,
  useChatStream,
  clearErrorRetry,
  useChatMessageActions,
  useChatSessionChrome,
  useChatFeedback,
  useChatRagRefs,
  useChatMessageModel,
  useChatInputHint,
} from '../composables/chat/index.js'
import { AgentCapabilityPanel } from '../components/capabilities/index.js'
import ChatAttachmentPreview from '../components/ChatAttachmentPreview.vue'
import ChatAttachmentTile from '../components/ChatAttachmentTile.vue'
import SessionFileTree from '../components/SessionFileTree.vue'
import SessionFilePreviewModal from '../components/SessionFilePreviewModal.vue'
import VoiceMicVisualizer from '../components/VoiceMicVisualizer.vue'
import ChatMentionInput from '../components/ChatMentionInput.vue'
import MentionTextRenderer from '../components/MentionTextRenderer.vue'
import { agentAvatarGradient } from '../utils/bindingTheme'
import { useChatAgents } from '../composables/useChatAgents'
import { useChatAttachments } from '../composables/useChatAttachments'
import { useVoiceIO } from '../composables/useVoiceIO'
import { useAskUser } from '../composables/useAskUser'
import { useStreamSmoother } from '../composables/useStreamSmoother'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const input = ref('')
const inputHistory = ref([])
const historyIndex = ref(-1)
const loading = ref(false)
const streaming = ref(false)
const messages = ref([])
const sessionTokenCount = ref(0)
const messagesRef = ref(null)
const inputRef = ref(null)
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

const editingMessageId = ref(null)
const editContent = ref('')
const editInputRef = ref(null)
const replyTo = reactive({ active: false, messageId: null, content: '', role: '' })

const sessionId = computed(() => route.params.sessionId || null)

function autoResize() {
  // ChatMentionInput 使用 contenteditable，由 CSS 控制高度（min-height/max-height + overflow-y），
  // 不再需要 JS 手动调整。保留空函数避免破坏 useVoiceIO 等调用方。
}

const {
  agents, selectedAgentId, currentAgent, chatCapabilities,
  selectedConfigVersion, selectedAgentVersionId, configVersionOptions,
  showFileUploadBtn, showVoiceInputBtn, showTtsBtn,
  fileAcceptTypes, fileUploadHint,
  currentWelcomeMessage, currentRecommendedQuestions,
  handleAgentSelect, loadChatCapabilities, onConfigVersionChange,
  loadAgentConfigVersions, loadCurrentAgent, loadAgents, agentVersionLabel,
} = useChatAgents({
  sessionId, loading, pendingAttachments, voiceListening, stopVoiceInput: () => { voiceListening.value = false },
})

function agentIconStyle(agentType) {
  return { background: agentAvatarGradient(agentType) }
}

const {
  getAttThumbUrl, openAttachmentPreview,
  triggerFileUpload, onFileSelected, removeAttachment,
} = useChatAttachments({
  selectedAgentId, sessionId, selectedConfigVersion, chatCapabilities, pendingAttachments,
  fileInputRef, uploading, attachmentPreviewOpen, attachmentPreviewAtt,
})

const {
  parseMessage, enrichMessagesAttachments, getMsgMentions, getMsgAttachments,
  shouldRenderMentions, getMsgRagRefs, getRagQaQuestion,
  isBackendErrorMessage, applyStreamDoneMetadata, formatElapsed,
  getReplyToInfo, hasReplyTarget, isMessageEditing,
} = useChatMessageModel({ editingMessageId, messages, sessionTokenCount })

let scrollAfterExpandFn = () => {}
const {
  expandedRefsMap, refsSectionExpandedMap,
  isReferenceExpanded, toggleReference, isRefsSectionExpanded, toggleRefsSection, toggleReasoningExpand,
} = useChatRagRefs({ messages, scrollAfterExpand: (...a) => scrollAfterExpandFn(...a) })

const {
  isNearBottom, userScrolledUp, virtualizer,
  handleScroll, onCapabilityHeightChange,
  scrollToBottom, scrollReasoningToBottom, forceScrollToBottom, scrollAfterExpand,
  createScrollHandler,
} = useChatScroll({ messages, messagesRef, streaming, getMsgRagRefs, refsSectionExpandedMap })
scrollAfterExpandFn = scrollAfterExpand

const {
  sessionTitle, titleEditing, titleEditValue, titleInputRef,
  fileDrawerOpen, fileDrawerLoading, fileDrawerLoadedOnce, fileStats, fileTreeRefreshTick,
  sessionFilePreviewOpen, sessionFilePreviewTarget, sessionFileTreeRef,
  startTitleEdit, confirmTitleEdit, cancelTitleEdit, tooltipPopupContainer,
  openFileDrawer, onFileDrawerOpened, refreshSessionFiles, loadSessionFiles, onFileTreeRefreshed,
  resolveAttachmentAsSessionFile, onAttachmentPreview, openSessionFilePreviewModal, openSessionFilePreview,
  pollSessionTitle, cleanupPollTitleTimer,
} = useChatSessionChrome({ sessionId, openAttachmentPreview, attachmentPreviewOpen, attachmentPreviewAtt })

const {
  getMessageFeedbackType, handleMessageFeedback,
  showDislikeModal, submitDislikeReason, skipDislikeReason,
  loadBatchFeedbacks, dislikeModalVisible, dislikeReason,
} = useChatFeedback()

const {
  inputHintQuestions, showInputQuestionCarousel, showInputDisclaimer, questionRotateIndex, applyRecommendedQuestion,
} = useChatInputHint({ messages, currentRecommendedQuestions, selectedAgentId, input, inputRef })

const {
  toggleVoiceInput, stopVoiceInput,
  speakMessage, messagePlainText, cleanup: voiceCleanup,
} = useVoiceIO({
  input, inputRef, chatCapabilities, autoResize,
  voiceListening, speakingMsgKey,
})

const {
  askUserModal, isAskUserUnanswered, showAskUserModal,
} = useAskUser({ messages })

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

const { canSend, workflowConfirmBlocked } = useChatSendGate({
  loading,
  input,
  pendingAttachments,
  messages,
})

const {
  handleChatWorkflowStreamEvent,
  submitWorkflowConfirm,
  abandonWorkflowConfirm,
  applyToolMetadata,
} = createChatWorkflowStreamHandlers({
  loading,
  streaming,
  currentStatus,
  hasStreamContent,
  streamSmoother,
  scrollToBottom,
  clearErrorRetry,
  getCurrentStreamingMsg: () => currentStreamingMsg,
  setCurrentStreamingMsg: (msg) => { currentStreamingMsg = msg },
  getAbortController: () => abortController.value,
  setAbortController: (ctrl) => { abortController.value = ctrl },
  getSelectedAgentId: () => selectedAgentId.value,
})

const { handleChatCapabilityStreamEvent } = createChatCapabilityStreamHandlers({
  currentStatus,
  hasStreamContent,
  scrollToBottom,
  messages,
  messagesRef,
})

const hasStreamingAssistantMessage = computed(() =>
  messages.value.some(m => m.role === 'assistant' && m._streaming)
)

let runChatStreamFn = async () => {}
const {
  highlightMessageId, rawModal, metadataModal,
  canRegenerate, isLastUserMessage,
  startEdit, cancelEdit, startReply, cancelReply,
  toggleStarMessage, scrollToMessage, submitEdit,
  copyMessage, copyRequestId, copyMessageId,
  openRawModal, openMetadataModal, copyRawContent, copyMetadata,
  handleDeleteMessage, cleanupEditClickOutside,
} = useChatMessageActions({
  sessionId,
  messages,
  loading,
  streaming,
  virtualizer,
  isNearBottom,
  userScrolledUp,
  scrollToBottom,
  runChatStream: (...args) => runChatStreamFn(...args),
  getReplyToInfo,
  hasReplyTarget,
  isMessageEditing,
  editingMessageId,
  editContent,
  editInputRef,
  replyTo,
  inputRef,
})

const {
  submitAskUserResponse, runChatStream, sendMessage, regenerateReply, stopGenerating,
} = useChatStream({
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
  getCurrentStreamingMsg: () => currentStreamingMsg,
  setCurrentStreamingMsg: (msg) => { currentStreamingMsg = msg },
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
})
runChatStreamFn = runChatStream

const {
  hasMoreMessages, loadingOlder, initialLoadDone, switchingSession,
  loadHistory, loadOlderMessages, onStreamingEnded,
} = useChatHistory({
  sessionId,
  messages,
  streaming,
  virtualizer,
  messagesRef,
  isNearBottom,
  userScrolledUp,
  forceScrollToBottom,
  selectedAgentId,
  currentAgent,
  loadAgentConfigVersions,
  loadCurrentAgent,
  loadBatchFeedbacks,
  parseMessage,
  enrichMessagesAttachments,
  isAskUserUnanswered,
  showAskUserModal,
  cancelReply,
  loadingHistory,
  lastReplyElapsed,
  sessionTokenCount,
  sessionTitle,
  input,
  inputHistory,
  historyIndex,
  pendingAttachments,
  fileDrawerLoadedOnce,
  sessionFilePreviewTarget,
  sessionFilePreviewOpen,
  fileStats,
})

const scrollHandler = createScrollHandler({ loadOlderMessages, hasMoreMessages, loadingOlder })

const userInitial = computed(() => {
  const name = userStore.user?.username || userStore.user?.nickname || 'U'
  return name[0].toUpperCase()
})

function formatTokenCount(tokens) {
  if (!tokens) return '0'
  if (tokens >= 10000) return (tokens / 10000).toFixed(1) + '万'
  return tokens.toLocaleString()
}

function handleChatKeydown(e) {
  if (e.ctrlKey && e.code === 'Slash') {
    e.preventDefault()
    inputRef.value?.focus()
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

function handleEditKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    submitEdit()
  } else if (e.key === 'Escape') {
    cancelEdit()
  }
}

function goToKnowledge(knowledgeId, documentId) {
  const query = documentId ? { docId: String(documentId) } : {}
  router.push({ path: `/app/knowledge/${knowledgeId}`, query })
}

onUnmounted(() => {
  abortActiveStreamOnUnload()
  voiceCleanup()
  cleanupPollTitleTimer()
  const container = messagesRef.value
  if (container) {
    container.removeEventListener('scroll', scrollHandler)
  }
  document.removeEventListener('keydown', handleChatKeydown)
  window.removeEventListener('beforeunload', abortActiveStreamOnUnload)
  cleanupEditClickOutside()
})

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

  const container = messagesRef.value
  if (container) {
    container.addEventListener('scroll', scrollHandler)
  }
  document.addEventListener('keydown', handleChatKeydown)
  window.addEventListener('beforeunload', abortActiveStreamOnUnload)
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
  loadHistory()
})

watch(streaming, (isStreaming) => {
  onStreamingEnded(isStreaming)
})

watch(selectedAgentId, (newId) => {
  if (newId && !sessionId.value) {
    loadCurrentAgent(newId)
  } else if (!newId) {
    currentAgent.value = null
  }
})

watch(sessionId, (newVal, oldVal) => {
  if (!newVal && oldVal) {
    loadAgents()
  }
})
</script>
