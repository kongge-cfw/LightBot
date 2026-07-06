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
        <span class="subagent-label">任务</span>
        <pre class="subagent-pre">{{ event.task }}</pre>
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
      <div v-if="tokenSteps.length" class="subagent-section">
        <span class="subagent-label">模型输出</span>
        <div v-for="(step, si) in tokenSteps" :key="si" class="subagent-token-stream">
          {{ step.content }}
        </div>
      </div>
      <div v-if="result" class="subagent-section">
        <span class="subagent-label">执行结果</span>
        <pre class="subagent-pre">{{ result }}</pre>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import {
  RobotOutlined, LoadingOutlined, RightOutlined, CloseCircleOutlined,
} from '@ant-design/icons-vue'
import ToolCallsGroupComponent from '../ToolCallsGroupComponent.vue'
import {
  collectSubagentSteps,
  groupSubagentSteps,
  findSubagentResult,
  findSubagentError,
  findSubagentErrorRetry,
  mapSubagentToolsToStandardEvents,
  formatSubagentErrorLabel,
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

const scopedEvents = computed(() => props.allEvents || props.events || [])

const subagentTitle = computed(() => props.event.displayName || props.event.subagentName)
const result = computed(() => findSubagentResult(scopedEvents.value, props.event))
const error = computed(() => findSubagentError(scopedEvents.value, props.event))
const errorLabel = computed(() => formatSubagentErrorLabel(error.value?.code))
const errorRetry = computed(() => {
  if (error.value || result.value) return null
  if (props.isDone) return null
  return findSubagentErrorRetry(scopedEvents.value, props.event)
})
const isActivelyRetrying = computed(() => !!errorRetry.value && !props.isDone)
const hasResult = computed(() => !!result.value || !!error.value)
const toolEvents = computed(() => mapSubagentToolsToStandardEvents(scopedEvents.value, props.event))
const toolsDone = computed(() => props.isDone || !!result.value || !!error.value)

const tokenSteps = computed(() => {
  const raw = collectSubagentSteps(scopedEvents.value, props.event)
  const grouped = groupSubagentSteps(raw)
  return grouped.filter(s => s.type === 'subagent_token_stream')
})

watch(() => props.defaultExpanded, (val) => {
  if (!userToggled) expanded.value = val
  if (val && !userToggled) scrollBodyToBottom()
}, { immediate: true })

watch([errorRetry, error, toolEvents], () => {
  if (errorRetry.value || error.value || toolEvents.value.length) {
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

watch(tokenSteps, () => {
  if (expanded.value) scrollBodyToBottom()
}, { deep: true })

function toggle(event) {
  userToggled = true
  expanded.value = !expanded.value
  if (expanded.value) scrollBodyToBottom()
  else nextTick(() => emit('heightChange', event))
}
</script>

<style scoped>
.subagent-call-block {
  border-radius: 8px;
  border: 1px solid #fcd34d;
  background: var(--color-warn-bg);
  overflow: hidden;
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
  gap: 8px;
  padding: 10px 12px;
  border: none;
  background: transparent;
  cursor: pointer;
  text-align: left;
  font-size: 13px;
  color: var(--color-text-dark);
}
.subagent-icon { font-size: 14px; color: #d97706; }
.subagent-title { flex: 1; min-width: 0; }
.subagent-spinner { color: var(--color-mute); }
.subagent-header-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  padding: 2px 8px;
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
  font-size: 10px;
  color: var(--color-mute);
  transition: transform 0.2s;
  flex-shrink: 0;
}
.subagent-toggle.expanded { transform: rotate(90deg); }
.subagent-status-banner {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin: 0 12px 10px;
  padding: 8px 10px;
  border-radius: 8px;
  font-size: 12px;
  line-height: 1.5;
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
  padding: 0 12px 10px 34px;
  font-size: 12px;
  color: var(--color-body);
  max-height: 420px;
  overflow-y: auto;
}
.subagent-section { margin-top: 6px; }
.subagent-label { font-weight: 500; color: var(--color-mute); }
.subagent-pre {
  margin: 4px 0 0;
  padding: 8px;
  background: rgba(255, 255, 255, 0.7);
  border-radius: 6px;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 12px;
  max-height: 200px;
  overflow: auto;
}
.subagent-tools {
  margin-top: 8px;
}
.subagent-tools :deep(.tool-calls-group) {
  margin-top: 0;
  background: rgba(255, 255, 255, 0.75);
}
.subagent-step-icon { font-size: 11px; }
.subagent-step-icon.error { color: #ef4444; }
.subagent-token-stream {
  display: block;
  color: var(--color-body);
  font-size: 12px;
  line-height: 1.6;
  word-break: break-word;
  white-space: pre-wrap;
  padding: 6px 8px;
  background: rgba(255, 255, 255, 0.7);
  border-radius: 6px;
  margin-top: 4px;
}
.subagent-error-code {
  font-size: 10px;
  padding: 0 4px;
  border-radius: 3px;
  background: rgba(0, 0, 0, 0.06);
  color: var(--color-mute);
  flex-shrink: 0;
}
.subagent-retry-count {
  font-size: 11px;
  color: var(--color-mute);
  flex-shrink: 0;
  font-family: var(--font-mono);
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
