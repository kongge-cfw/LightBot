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
      :refs-section-expanded="refsSectionExpanded"
      :is-ref-expanded="() => false"
    />
    <div v-else class="debug-preview-empty">{{ emptyText }}</div>
  </ChatDebugPreviewShell>
</template>

<script setup>
import ChatDebugPreviewShell from './ChatDebugPreviewShell.vue'
import ChatMessageRow from '@/components/chat/message/ChatMessageRow.vue'

defineProps({
  msg: { type: Object, default: null },
  refsSectionExpanded: { type: Boolean, default: true },
  emptyText: { type: String, default: '点击「解析预览」查看渲染效果' },
})

function noopThumb() {
  return ''
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
