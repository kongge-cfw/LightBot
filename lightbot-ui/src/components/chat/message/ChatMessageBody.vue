<template>
  <div class="message-body-inner" :class="{ 'user-message-body': msg?.role === 'user' }">
    <!-- 用户附件 -->
    <div
      v-if="msg?.role === 'user' && getMsgAttachments(msg).length && !msg._sensitiveBlock"
      class="user-message-attachments"
    >
      <ChatAttachmentTile
        v-for="(att, ai) in getMsgAttachments(msg)"
        :key="att.id || ai"
        :att="att"
        :thumb-url="getAttThumbUrl(att)"
        @preview="$emit('preview-attachment', $event)"
      />
    </div>
    <!-- 深度思考面板 -->
    <ChatReasoningPanel :msg="msg" @reasoning-toggle="$emit('reasoning-toggle')" />
    <!-- Skill 启用 -->
    <div v-if="getTopSkillEvents(msg).length > 0 && !hasErrors" class="capability-block-inline">
      <AgentCapabilityPanel
        :events="getTopSkillEvents(msg)"
        :is-done="!msg._streaming || msg._toolsDone"
        :default-expanded="true"
        @heightChange="$emit('height-change', $event)"
      />
    </div>
    <!-- 工作流节点执行（步骤面板） -->
    <div v-if="msg?._workflowEvents?.length > 0 && !hasErrors" class="workflow-block-inline">
      <WorkflowNodesGroupComponent
        :workflow-events="msg._workflowEvents"
        :is-done="!msg._streaming && !msg._workflowConfirmPending && !isWorkflowAwaitingConfirm(msg._workflowEvents)"
        :default-expanded="!!msg._streaming || !!msg._workflowConfirmPending || isWorkflowAwaitingConfirm(msg._workflowEvents)"
        :is-streaming="!!msg._streaming"
      />
    </div>
    <!-- 有工具事件：按 offset 分段渲染，工具块在对应位置插入，后续文本在其下方 -->
    <template v-if="!hasErrors && msg._toolEvents?.length > 0">
      <template v-for="(segment, si) in splitContentByOffsets(msg)" :key="si">
        <div v-if="segment.type === 'text'" class="message-content">
          <MentionTextRenderer
            v-if="shouldRenderMentions(msg, segment.text)"
            :content="segment.text"
            :mentions="getMsgMentions(msg)"
            :finalized="isSegmentFinalized(msg, segment, si)"
          />
          <MarkdownPreview v-else :content="segment.text" :finalized="isSegmentFinalized(msg, segment, si)" />
        </div>
        <div v-else-if="segment.type === 'tool'" class="tool-block-inline">
          <AgentCapabilityPanel
            v-if="getCapabilityEventsForOffset(msg, segment.offset).length > 0"
            :events="getCapabilityEventsForOffset(msg, segment.offset)"
            :all-events="msg._toolEvents || []"
            :is-done="isToolBlockDone(msg, segment.offset)"
            :default-expanded="true"
            @heightChange="$emit('height-change', $event)"
          />
          <ToolCallsGroupComponent
            v-if="getPureToolEvents(getToolEventsForOffset(msg, segment.offset)).length > 0"
            :tool-events="getPureToolEvents(getToolEventsForOffset(msg, segment.offset))"
            :is-done="isToolBlockDone(msg, segment.offset)"
            :default-expanded="true"
            :message-index="index"
            @heightChange="$emit('height-change', $event)"
          />
        </div>
      </template>
    </template>
    <!-- 无工具事件：正常渲染 -->
    <template v-else-if="!hasErrors">
      <div v-if="msg.content && msg.content !== '[附件]'" class="message-content">
        <MentionTextRenderer
          v-if="shouldRenderMentions(msg, msg.content)"
          :content="msg.content"
          :mentions="getMsgMentions(msg)"
          :finalized="!msg._streaming"
        />
        <MarkdownPreview v-else :content="msg.content" :finalized="!msg._streaming" />
      </div>
    </template>
    <!-- 人工确认：仅待提交时展示，支持展开/收起 -->
    <WorkflowConfirmForm
      v-if="msg._workflowConfirmPending?.confirmForm && !hasErrors"
      class="workflow-confirm-inline"
      :confirm-form="msg._workflowConfirmPending.confirmForm"
      :submitting="loading"
      :abandoning="loading"
      :default-expanded="true"
      @submit="formData => $emit('workflow-confirm-submit', formData)"
      @abandon="$emit('workflow-confirm-abandon')"
    />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import MarkdownPreview from '../../MarkdownPreview.vue'
import ToolCallsGroupComponent from '../../ToolCallsGroupComponent.vue'
import WorkflowNodesGroupComponent from '../../WorkflowNodesGroupComponent.vue'
import WorkflowConfirmForm from '../../WorkflowConfirmForm.vue'
import { isWorkflowAwaitingConfirm } from '../../workflow/workflowStepUtils.js'
import { AgentCapabilityPanel } from '../../capabilities/index.js'
import ChatAttachmentTile from '../../ChatAttachmentTile.vue'
import MentionTextRenderer from '../../MentionTextRenderer.vue'
import ChatReasoningPanel from './ChatReasoningPanel.vue'
import {
  getTopSkillEvents,
  getCapabilityEventsForOffset,
  getPureToolEvents,
  getToolEventsForOffset,
  isToolBlockDone,
  splitContentByOffsets,
  isSegmentFinalized,
} from '../../../composables/chat/useChatEventPartition.js'
import {
  getMsgAttachments,
  getMsgMentions,
  shouldRenderMentions,
} from '../../../composables/chat/useChatMessageModel.js'
import { hasMessageErrorState } from '../../../utils/chat/messageErrorState.js'

const props = defineProps({
  msg: { type: Object, required: true },
  index: { type: Number, required: true },
  loading: { type: Boolean, default: false },
  selectedAgentId: { type: [String, Number], default: null },
  selectedAgentVersionId: { type: [String, Number], default: null },
  getAttThumbUrl: { type: Function, required: true },
})

const hasErrors = computed(() => hasMessageErrorState(props.msg) && !props.msg?._terminated)

defineEmits([
  'preview-attachment',
  'height-change',
  'workflow-confirm-submit',
  'workflow-confirm-abandon',
  'reasoning-toggle',
])
</script>

<style scoped>
.message-body-inner {
  position: relative;
  width: 100%;
}
.message-body-inner.user-message-body {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
}
.user-message-attachments {
  display: flex;
  flex-direction: row-reverse;
  flex-wrap: wrap-reverse;
  justify-content: flex-end;
  align-items: flex-end;
  gap: 8px;
  max-width: 80%;
}
.message-content {
  font-size: 15px;
  line-height: 1.7;
  color: var(--color-ink);
  word-break: break-word;
}
.message-content :deep(h1),
.message-content :deep(h2),
.message-content :deep(h3),
.message-content :deep(h4),
.message-content :deep(h5),
.message-content :deep(h6) {
  margin: 16px 0 8px;
  font-weight: 600;
  line-height: 1.4;
  color: var(--color-ink);
}
.message-content :deep(h1) { font-size: 1.5em; }
.message-content :deep(h2) { font-size: 1.3em; }
.message-content :deep(h3) { font-size: 1.15em; }
.message-content :deep(h4),
.message-content :deep(h5),
.message-content :deep(h6) { font-size: 1em; }
.message-content :deep(p) {
  margin: 0 0 12px;
}
.message-content :deep(code) {
  background: var(--color-canvas-soft-2);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 13px;
  font-family: 'Geist Mono', 'Menlo', monospace;
}
.message-content :deep(pre) {
  background: #171717;
  border-radius: 8px;
  padding: 16px;
  overflow-x: auto;
  margin: 12px 0;
}
.message-content :deep(pre)::-webkit-scrollbar { height: 4px; }
.message-content :deep(pre)::-webkit-scrollbar-track { background: transparent; }
.message-content :deep(pre)::-webkit-scrollbar-thumb { background: rgba(255, 255, 255, 0.15); border-radius: 2px; }
.message-content :deep(pre)::-webkit-scrollbar-thumb:hover { background: rgba(255, 255, 255, 0.25); }
.message-content :deep(pre code) {
  background: transparent !important;
  color: #e4e4e7;
  padding: 0;
  font-size: 13px;
  line-height: 1.6;
}
.message-content :deep(ul),
.message-content :deep(ol) {
  padding-left: 20px;
  margin: 8px 0;
}
.message-content :deep(li > p),
.message-content :deep(ol > p),
.message-content :deep(ul > p) {
  margin: 2px 0;
}
.message-content :deep(li) {
  margin: 2px 0;
}
.message-content :deep(blockquote) {
  border-left: 3px solid var(--color-link);
  padding-left: 12px;
  margin: 12px 0;
  color: var(--color-body);
}
.message-content :deep(table) {
  border-collapse: collapse;
  margin: 12px 0;
  width: 100%;
}
.message-content :deep(th),
.message-content :deep(td) {
  border: 1px solid var(--color-hairline);
  padding: 8px 12px;
  text-align: left;
  font-size: 14px;
}
.message-content :deep(th) {
  background: var(--color-canvas-soft-2);
}
.tool-block-inline {
  margin: 8px 0;
  width: 100%;
}
.capability-block-inline {
  margin-top: 8px;
  width: 100%;
}
.workflow-block-inline {
  margin: 8px 0;
  width: 100%;
}
.workflow-confirm-inline {
  margin: 10px 0 4px;
  width: 100%;
}
</style>

<style>
.message.user .message-content-wrapper.user-message-stack .message-body-inner .message-content {
  display: inline-block;
  background: var(--color-canvas-soft-2);
  padding: 10px 16px;
  border-radius: 12px 12px 2px 12px;
  text-align: left;
  max-width: 80%;
}
.message.user .message-content-wrapper.user-message-stack .message-body-inner .message-content .markdown-preview p {
  margin: 0;
}
.message.user .message-content-wrapper.user-message-stack .message-body-inner .message-content .markdown-preview {
  line-height: 1.5;
}
[data-theme="dark"] .message.user .message-content-wrapper.user-message-stack .message-body-inner .message-content {
  background: #27272a;
}
[data-theme="dark"] .message.user .message-content-wrapper.user-message-stack .message-body-inner .message-content .mention-chip-knowledge {
  background: rgba(16, 185, 129, 0.28);
  color: #6ee7b7;
  box-shadow: inset 0 0 0 1px rgba(110, 231, 183, 0.28);
}
[data-theme="dark"] .message.user .message-content-wrapper.user-message-stack .message-body-inner .message-content .mention-chip-subagent {
  background: rgba(245, 158, 11, 0.28);
  color: #fcd34d;
  box-shadow: inset 0 0 0 1px rgba(252, 211, 77, 0.28);
}
[data-theme="dark"] .message.user .message-content-wrapper.user-message-stack .message-body-inner .message-content .mention-chip-skill {
  background: rgba(168, 85, 247, 0.28);
  color: #d8b4fe;
  box-shadow: inset 0 0 0 1px rgba(216, 180, 254, 0.28);
}
[data-theme="dark"] .message.user .message-content-wrapper.user-message-stack .message-body-inner .message-content .mention-chip-tool {
  background: rgba(59, 130, 246, 0.28);
  color: #93c5fd;
  box-shadow: inset 0 0 0 1px rgba(147, 197, 253, 0.28);
}
[data-theme="dark"] .message.user .message-content-wrapper.user-message-stack .message-body-inner .message-content .mention-chip-invalid {
  background: rgba(239, 68, 68, 0.24);
  color: #fca5a5;
  box-shadow: inset 0 0 0 1px rgba(252, 165, 165, 0.28);
}
[data-theme="dark"] .message-body-inner .message-content code {
  background: #27272a;
}
</style>
