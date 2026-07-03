<template>
  <div class="subagent-call-block">
    <button type="button" class="subagent-header" @click="toggle($event)">
      <RobotOutlined class="subagent-icon" />
      <span class="subagent-title">委派 SubAgent：<strong>{{ event.displayName || event.subagentName }}</strong></span>
      <LoadingOutlined v-if="!isDone && !hasResult" class="subagent-spinner" />
      <RightOutlined :class="{ expanded: expanded }" class="subagent-toggle" />
    </button>
    <div v-show="expanded" class="subagent-body">
      <div v-if="event.task" class="subagent-section">
        <span class="subagent-label">任务</span>
        <pre class="subagent-pre">{{ event.task }}</pre>
      </div>
      <div v-if="steps.length" class="subagent-section">
        <span class="subagent-label">执行过程</span>
        <div v-for="(step, si) in steps" :key="si" class="subagent-step">
          <span v-if="step.type === 'subagent_tool_call'" class="subagent-step-call">
            <CodeOutlined class="subagent-step-icon" /> 调用工具: <strong>{{ step.toolName }}</strong>
          </span>
          <span v-else-if="step.type === 'subagent_tool_result'" class="subagent-step-result">
            <CheckCircleOutlined class="subagent-step-icon success" /> 工具结果
            <span v-if="step.content" class="subagent-step-preview">{{ step.content }}</span>
          </span>
          <span v-else-if="step.type === 'subagent_token'" class="subagent-step-token">
            {{ step.content }}
          </span>
        </div>
      </div>
      <div v-if="errorRetry" class="subagent-error-retry">
        <LoadingOutlined spin class="subagent-step-icon" />
        <span>{{ errorRetry.message }}</span>
        <span class="subagent-retry-count">{{ errorRetry.attempt }}/{{ errorRetry.maxRetries }}</span>
      </div>
      <div v-if="error" class="subagent-error">
        <CloseCircleOutlined class="subagent-step-icon error" />
        <span>{{ error.message }}</span>
        <span v-if="error.code" class="subagent-error-code">{{ error.code }}</span>
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
  RobotOutlined, LoadingOutlined, RightOutlined,
  CodeOutlined, CheckCircleOutlined, CloseCircleOutlined,
} from '@ant-design/icons-vue'
import {
  collectSubagentSteps,
  findSubagentResult,
  findSubagentError,
  findSubagentErrorRetry,
} from './subagentEventUtils.js'

const props = defineProps({
  event: { type: Object, required: true },
  events: { type: Array, default: () => [] },
  eventIndex: { type: Number, default: 0 },
  isDone: { type: Boolean, default: true },
  defaultExpanded: { type: Boolean, default: true },
})

const emit = defineEmits(['heightChange'])

const expanded = ref(props.defaultExpanded)
let userToggled = false

const steps = computed(() => collectSubagentSteps(props.events, props.event))
const result = computed(() => findSubagentResult(props.events, props.event))
const error = computed(() => findSubagentError(props.events, props.event))
const errorRetry = computed(() => findSubagentErrorRetry(props.events, props.event))
const hasResult = computed(() => !!result.value || !!error.value)

watch(() => props.defaultExpanded, (val) => {
  if (!userToggled) expanded.value = val
}, { immediate: true })

function toggle(event) {
  userToggled = true
  expanded.value = !expanded.value
  nextTick(() => emit('heightChange', event))
}
</script>

<style scoped>
.subagent-call-block {
  border-radius: 8px;
  border: 1px solid #fcd34d;
  background: var(--color-warn-bg);
  overflow: hidden;
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
.subagent-title { flex: 1; }
.subagent-spinner { color: var(--color-mute); }
.subagent-toggle {
  font-size: 10px;
  color: var(--color-mute);
  transition: transform 0.2s;
}
.subagent-toggle.expanded { transform: rotate(90deg); }
.subagent-body {
  padding: 0 12px 10px 34px;
  font-size: 12px;
  color: var(--color-body);
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
.subagent-step { padding: 3px 0; font-size: 12px; line-height: 1.5; }
.subagent-step-call { color: #7c3aed; display: flex; align-items: center; gap: 4px; }
.subagent-step-result { color: #059669; display: flex; align-items: center; gap: 4px; }
.subagent-step-icon { font-size: 11px; }
.subagent-step-icon.success { color: #10b981; }
.subagent-step-icon.error { color: #ef4444; }
.subagent-step-preview {
  color: var(--color-mute);
  font-size: 11px;
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.subagent-step-token {
  color: var(--color-mute);
  font-size: 12px;
  line-height: 1.6;
  word-break: break-word;
}
.subagent-error-retry,
.subagent-error {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin-top: 6px;
  padding: 6px 8px;
  border-radius: 6px;
  font-size: 12px;
}
.subagent-error-retry {
  background: rgba(251, 191, 36, 0.15);
  color: #b45309;
}
.subagent-error {
  background: rgba(239, 68, 68, 0.1);
  color: #dc2626;
}
.subagent-error-code {
  font-size: 10px;
  padding: 0 4px;
  border-radius: 3px;
  background: rgba(0, 0, 0, 0.06);
  color: var(--color-mute);
}
.subagent-retry-count {
  font-size: 11px;
  color: var(--color-mute);
  margin-left: auto;
}
</style>
