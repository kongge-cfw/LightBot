<template>
  <div class="subagent-call-block" :class="{ 'is-retrying': isActivelyRetrying, 'is-failed': !!error }">
    <button type="button" class="subagent-header" @click="toggle($event)">
      <RobotOutlined class="subagent-icon" />
      <span class="subagent-title">委派 SubAgent：<strong>{{ subagentTitle }}</strong></span>
      <span v-if="errorRetry" class="subagent-header-badge retry">
        <LoadingOutlined spin class="subagent-step-icon" />
        重试 {{ errorRetry.attempt }}/{{ errorRetry.maxRetries }}
      </span>
      <span v-else-if="error" class="subagent-header-badge error">
        <CloseCircleOutlined class="subagent-step-icon" />
        {{ errorLabel || '执行失败' }}
      </span>
      <LoadingOutlined v-else-if="!isDone && !hasResult" class="subagent-spinner" spin />
      <RightOutlined :class="{ expanded: expanded }" class="subagent-toggle" />
    </button>

    <div v-if="errorRetry" class="subagent-status-banner retry">
      <LoadingOutlined spin class="subagent-step-icon" />
      <span class="subagent-status-text">{{ errorRetry.message || 'SubAgent 连接异常，正在重试' }}</span>
      <span class="subagent-retry-count">{{ errorRetry.attempt }}/{{ errorRetry.maxRetries }}</span>
    </div>
    <div v-else-if="error" class="subagent-status-banner error">
      <CloseCircleOutlined class="subagent-step-icon error" />
      <span class="subagent-status-text">{{ error.message }}</span>
      <span v-if="errorLabel" class="subagent-error-code">{{ errorLabel }}</span>
      <span v-else-if="error.code" class="subagent-error-code">{{ error.code }}</span>
    </div>

    <div v-show="expanded" ref="bodyRef" class="subagent-body">
      <div v-if="event.task" class="subagent-section">
        <div class="subagent-section-header">
          <span class="subagent-label">任务</span>
        </div>
        <div class="subagent-task-box">{{ event.task }}</div>
      </div>

      <ToolCallsGroupComponent
        v-if="toolEvents.length"
        class="subagent-tools"
        :tool-events="toolEvents"
        :is-done="toolsDone"
        :default-expanded="true"
        :message-index="-1"
        @heightChange="onToolHeightChange"
      />

      <div v-if="showModelOutput" class="subagent-section">
        <div class="subagent-section-header">
          <span class="subagent-label">模型输出</span>
          <div class="subagent-section-actions">
            <button type="button" class="subagent-action-btn" @click.stop="openRawModal">
              <FileTextOutlined />
              <span>查看原文</span>
            </button>
          </div>
        </div>
        <div class="subagent-markdown">
          <MarkdownPreview :content="modelOutput" :finalized="isDone" :image-preview="false" />
        </div>
      </div>

      <div v-if="resultReply" class="subagent-section">
        <div class="subagent-section-header">
          <span class="subagent-label">执行结果</span>
          <div v-if="hasResultJson" class="subagent-section-actions">
            <a-tooltip title="查看返回 JSON">
              <button type="button" class="subagent-action-btn icon-only" @click.stop="openJsonModal">
                <CodeOutlined />
              </button>
            </a-tooltip>
          </div>
        </div>
        <div class="subagent-markdown">
          <MarkdownPreview :content="resultReply" :finalized="true" :image-preview="false" />
        </div>
      </div>
    </div>

    <!-- 模型输出原文 -->
    <a-modal
      v-model:open="rawModalOpen"
      :footer="null"
      width="680px"
      :body-style="{ maxHeight: '70vh', overflow: 'auto' }"
      destroy-on-close
    >
      <template #title>
        <span>SubAgent 模型输出原文</span>
        <a-tooltip title="复制">
          <button class="subagent-modal-btn" @click="copyText(modelOutput, 'raw')">
            <CheckOutlined v-if="rawCopied" style="color:#16a34a;" />
            <CopyOutlined v-else />
          </button>
        </a-tooltip>
      </template>
      <pre class="subagent-modal-pre">{{ modelOutput }}</pre>
    </a-modal>

    <!-- 返回 JSON -->
    <a-modal
      v-model:open="jsonModalOpen"
      :footer="null"
      width="680px"
      :body-style="{ maxHeight: '70vh', overflow: 'auto' }"
      destroy-on-close
    >
      <template #title>
        <span>SubAgent 返回 JSON</span>
        <a-tooltip title="复制">
          <button class="subagent-modal-btn" @click="copyText(resultRawJson, 'json')">
            <CheckOutlined v-if="jsonCopied" style="color:#16a34a;" />
            <CopyOutlined v-else />
          </button>
        </a-tooltip>
      </template>
      <pre class="subagent-modal-pre">{{ resultRawJson }}</pre>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import { message } from 'ant-design-vue'
import {
  RobotOutlined, LoadingOutlined, RightOutlined, CloseCircleOutlined,
  FileTextOutlined, CodeOutlined, CopyOutlined, CheckOutlined,
} from '@ant-design/icons-vue'
import ToolCallsGroupComponent from '../ToolCallsGroupComponent.vue'
import MarkdownPreview from '../MarkdownPreview.vue'
import {
  findSubagentError,
  findSubagentErrorRetry,
  mapSubagentToolsToStandardEvents,
  formatSubagentErrorLabel,
  findSubagentResultReply,
  findSubagentResultRawJson,
  hasSubagentResultJson,
  mergeSubagentModelOutput,
} from './subagentEventUtils.js'

const props = defineProps({
  event: { type: Object, required: true },
  events: { type: Array, default: () => [] },
  allEvents: { type: Array, default: null },
  eventIndex: { type: Number, default: 0 },
  isDone: { type: Boolean, default: true },
  defaultExpanded: { type: Boolean, default: true },
})

const emit = defineEmits(['heightChange'])

const expanded = ref(props.defaultExpanded)
const bodyRef = ref(null)
let userToggled = false

const rawModalOpen = ref(false)
const jsonModalOpen = ref(false)
const rawCopied = ref(false)
const jsonCopied = ref(false)

const scopedEvents = computed(() => props.allEvents || props.events || [])

const subagentTitle = computed(() => props.event.displayName || props.event.subagentName)
const error = computed(() => findSubagentError(scopedEvents.value, props.event))
const errorLabel = computed(() => formatSubagentErrorLabel(error.value?.code))
const errorRetry = computed(() => {
  if (error.value || resultReply.value) return null
  if (props.isDone) return null
  return findSubagentErrorRetry(scopedEvents.value, props.event)
})
const isActivelyRetrying = computed(() => !!errorRetry.value && !props.isDone)
const hasResult = computed(() => !!resultReply.value || !!error.value)
const toolEvents = computed(() => mapSubagentToolsToStandardEvents(scopedEvents.value, props.event))
const toolsDone = computed(() => props.isDone || !!resultReply.value || !!error.value)

const modelOutput = computed(() => mergeSubagentModelOutput(scopedEvents.value, props.event))
const resultReply = computed(() => findSubagentResultReply(scopedEvents.value, props.event))
const showModelOutput = computed(() => {
  if (!modelOutput.value) return false
  // 完成后有可读结果时，避免与「执行结果」重复展示流式 token
  if (resultReply.value && props.isDone) return false
  return true
})
const resultRawJson = computed(() => findSubagentResultRawJson(scopedEvents.value, props.event))
const hasResultJson = computed(() => hasSubagentResultJson(scopedEvents.value, props.event))

watch(() => props.defaultExpanded, (val) => {
  if (!userToggled) expanded.value = val
  if (val && !userToggled) scrollBodyToBottom()
}, { immediate: true })

watch([errorRetry, error, toolEvents, modelOutput, resultReply], () => {
  if (errorRetry.value || error.value || toolEvents.value.length || modelOutput.value || resultReply.value) {
    expanded.value = true
    nextTick(() => emit('heightChange'))
  }
})

function scrollBodyToBottom() {
  nextTick(() => {
    const el = bodyRef.value
    if (el) el.scrollTop = el.scrollHeight
    emit('heightChange')
  })
}

function onToolHeightChange(event) {
  scrollBodyToBottom()
  emit('heightChange', event)
}

watch(expanded, (val) => {
  if (val) scrollBodyToBottom()
})

watch([modelOutput, resultReply], () => {
  if (expanded.value) scrollBodyToBottom()
})

function toggle(event) {
  userToggled = true
  expanded.value = !expanded.value
  if (expanded.value) scrollBodyToBottom()
  else nextTick(() => emit('heightChange', event))
}

function openRawModal() {
  rawModalOpen.value = true
}

function openJsonModal() {
  jsonModalOpen.value = true
}

async function copyText(text, kind) {
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
    if (kind === 'raw') {
      rawCopied.value = true
      setTimeout(() => { rawCopied.value = false }, 2000)
    } else {
      jsonCopied.value = true
      setTimeout(() => { jsonCopied.value = false }, 2000)
    }
    message.success('已复制')
  } catch {
    message.error('复制失败')
  }
}
</script>

<style scoped>
.subagent-call-block {
  border-radius: 10px;
  border: 1px solid #fcd34d;
  background: var(--color-warn-bg);
  overflow: hidden;
  margin-top: 4px;
}
.subagent-call-block.is-retrying {
  border-color: rgba(245, 158, 11, 0.45);
  animation: errorFadeIn 0.3s ease, retryPulse 1.6s ease-in-out infinite;
}
.subagent-call-block.is-failed {
  border-color: rgba(239, 68, 68, 0.35);
  background: rgba(254, 242, 242, 0.7);
}
.subagent-header {
  appearance: none;
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  border: none;
  background: transparent;
  cursor: pointer;
  text-align: left;
  font-size: 14px;
  color: var(--color-text-dark);
}
.subagent-icon { font-size: 16px; color: #d97706; flex-shrink: 0; }
.subagent-title { flex: 1; min-width: 0; line-height: 1.5; }
.subagent-title strong { font-weight: 600; color: #92400e; }
.subagent-spinner { color: var(--color-mute); font-size: 14px; }
.subagent-header-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  padding: 3px 10px;
  border-radius: 999px;
  flex-shrink: 0;
}
.subagent-header-badge.retry {
  color: #b45309;
  background: rgba(251, 191, 36, 0.2);
}
.subagent-header-badge.error {
  color: #dc2626;
  background: rgba(239, 68, 68, 0.12);
}
.subagent-toggle {
  font-size: 11px;
  color: var(--color-mute);
  transition: transform 0.2s;
  flex-shrink: 0;
}
.subagent-toggle.expanded { transform: rotate(90deg); }
.subagent-status-banner {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin: 0 16px 12px;
  padding: 10px 12px;
  border-radius: 8px;
  font-size: 13px;
  line-height: 1.6;
}
.subagent-status-banner.retry {
  background: rgba(251, 191, 36, 0.18);
  color: #b45309;
  animation: errorFadeIn 0.3s ease, retryPulse 1.6s ease-in-out infinite;
}
.subagent-status-banner.error {
  background: rgba(239, 68, 68, 0.1);
  color: #dc2626;
}
.subagent-status-text {
  flex: 1;
  word-break: break-word;
}
.subagent-body {
  padding: 4px 16px 16px 16px;
  font-size: 14px;
  color: var(--color-body);
  max-height: 520px;
  overflow-y: auto;
}
.subagent-section {
  margin-top: 14px;
}
.subagent-section:first-child {
  margin-top: 4px;
}
.subagent-section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}
.subagent-label {
  font-weight: 600;
  font-size: 13px;
  color: var(--color-mute);
}
.subagent-section-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}
.subagent-action-btn {
  appearance: none;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border: 1px solid rgba(217, 119, 6, 0.25);
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.65);
  color: #b45309;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s ease;
}
.subagent-action-btn:hover {
  background: rgba(255, 255, 255, 0.95);
  border-color: rgba(217, 119, 6, 0.45);
}
.subagent-action-btn.icon-only {
  padding: 4px 8px;
  font-size: 14px;
}
.subagent-task-box {
  padding: 10px 12px;
  background: rgba(255, 255, 255, 0.75);
  border-radius: 8px;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 13px;
  line-height: 1.65;
  color: var(--color-body);
}
.subagent-markdown {
  padding: 10px 12px;
  background: rgba(255, 255, 255, 0.82);
  border-radius: 8px;
  font-size: 14px;
  line-height: 1.7;
}
.subagent-markdown :deep(.markdown-preview) {
  font-size: 14px;
  line-height: 1.7;
}
.subagent-tools {
  margin-top: 10px;
}
.subagent-tools :deep(.tool-calls-group) {
  margin-top: 0;
  background: rgba(255, 255, 255, 0.82);
  font-size: 14px;
}
.subagent-step-icon { font-size: 12px; }
.subagent-step-icon.error { color: #ef4444; }
.subagent-error-code {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 4px;
  background: rgba(0, 0, 0, 0.06);
  color: var(--color-mute);
  flex-shrink: 0;
}
.subagent-retry-count {
  font-size: 12px;
  color: var(--color-mute);
  flex-shrink: 0;
  font-family: var(--font-mono);
}
.subagent-modal-pre {
  margin: 0;
  padding: 0;
  background: none;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: 'Menlo', 'Monaco', 'Consolas', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.7;
  color: var(--color-text-code);
}
.subagent-modal-btn {
  appearance: none;
  border: none;
  background: none;
  color: var(--gray-400);
  font-size: 14px;
  cursor: pointer;
  padding: 2px 6px;
  margin-left: 8px;
  border-radius: 4px;
  vertical-align: middle;
  transition: all 0.15s;
}
.subagent-modal-btn:hover {
  color: var(--main-600);
  background: var(--gray-100);
}
@keyframes errorFadeIn {
  from { opacity: 0; transform: translateY(4px); }
  to { opacity: 1; transform: translateY(0); }
}
@keyframes retryPulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(245, 158, 11, 0); }
  50% { box-shadow: 0 0 0 3px rgba(245, 158, 11, 0.12); }
}
</style>
