<template>
  <ChatDebugPreviewShell class="debug-message-preview">
    <ChatMessageRow
      v-if="msg"
      :msg="msg"
      :index="0"
      :loading="false"
      :streaming="!!msg._streaming"
      :get-att-thumb-url="noopThumb"
      :messages="[msg]"
      :messages-length="1"
      :refs-section-expanded="localRefsSectionExpanded"
      :is-ref-expanded="isRefExpanded"
      @reasoning-toggle="onReasoningToggle"
      @rag-toggle="onRagToggle"
      @go-knowledge="onGoKnowledge"
    />
    <div v-else class="debug-preview-empty">{{ emptyText }}</div>
  </ChatDebugPreviewShell>
</template>

<script setup>
import { ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import ChatDebugPreviewShell from './ChatDebugPreviewShell.vue'
import ChatMessageRow from '@/components/chat/message/ChatMessageRow.vue'

const props = defineProps({
  msg: { type: Object, default: null },
  refsSectionExpanded: { type: Boolean, default: true },
  emptyText: { type: String, default: '点击「解析预览」查看渲染效果' },
})

const localRefsSectionExpanded = ref(true)
const expandedRefIndices = ref(new Set())

watch(
  () => props.msg,
  () => {
    expandedRefIndices.value = new Set()
    localRefsSectionExpanded.value = props.refsSectionExpanded
  },
  { immediate: true },
)

watch(
  () => props.refsSectionExpanded,
  (val) => {
    localRefsSectionExpanded.value = val
  },
)

function noopThumb() {
  return ''
}

function isRefExpanded(refIndex) {
  return expandedRefIndices.value.has(refIndex)
}

function onReasoningToggle() {
  if (!props.msg) return
  props.msg._reasoningExpanded = !props.msg._reasoningExpanded
}

function onRagToggle(payload) {
  if (payload?.kind === 'section') {
    localRefsSectionExpanded.value = !localRefsSectionExpanded.value
    return
  }
  if (payload?.kind === 'item') {
    const next = new Set(expandedRefIndices.value)
    const idx = payload.refIndex
    if (next.has(idx)) next.delete(idx)
    else next.add(idx)
    expandedRefIndices.value = next
  }
}

function onGoKnowledge({ knowledgeId, documentId }) {
  if (!knowledgeId) return
  const query = documentId ? `?docId=${encodeURIComponent(String(documentId))}` : ''
  const path = `/app/knowledge/${knowledgeId}${query}`
  window.open(path, '_blank', 'noopener,noreferrer')
  message.info('已在新窗口打开知识库详情（Debug 预览）')
}
</script>

<style scoped>
.debug-message-preview {
  height: 100%;
}

.debug-preview-empty {
  padding: 48px 24px;
  text-align: center;
  color: var(--gray-400);
  font-size: 14px;
}
</style>
