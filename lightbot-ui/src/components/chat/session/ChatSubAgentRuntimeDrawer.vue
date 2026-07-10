<template>
  <a-drawer
    :open="open"
    title="SubAgent 运行态"
    placement="right"
    width="380"
    @update:open="$emit('update:open', $event)"
  >
    <template #extra>
      <a-button size="small" :loading="loading" @click="loadSummaries">刷新</a-button>
    </template>

    <div v-if="runs.length" class="runtime-list">
      <button v-for="run in runs" :key="run.task_id" type="button" class="runtime-item" @click="openTask(run)">
        <span class="runtime-status" :class="`is-${run.status}`"></span>
        <span class="runtime-main">
          <span class="runtime-title">{{ run.subagent_name || 'SubAgent' }}</span>
          <span class="runtime-task">{{ run.task || '未提供任务描述' }}</span>
          <span class="runtime-progress">{{ run.progress_summary || statusLabel(run.status) }}</span>
        </span>
        <span class="runtime-status-label">{{ statusLabel(run.status) }}</span>
      </button>
    </div>
    <a-empty v-else-if="!loading" description="当前会话暂无 SubAgent 任务" />

    <a-modal v-model:open="detailOpen" :title="detailTitle" width="760px" :footer="null" destroy-on-close>
      <a-spin :spinning="detailLoading">
        <div v-if="selectedTask" class="task-detail-summary">
          <span :class="['task-detail-status', `is-${selectedTask.status}`]">{{ statusLabel(selectedTask.status) }}</span>
          <span>{{ selectedTask.progress_summary || statusLabel(selectedTask.status) }}</span>
        </div>
        <div class="detail-section">
          <div class="detail-section-title">运行事件</div>
          <a-timeline v-if="events.length" class="event-timeline">
            <a-timeline-item v-for="event in events" :key="event.cursor" :color="eventColor(event.type)">
              <div class="event-title">{{ eventLabel(event.type) }}</div>
              <div class="event-time">{{ event.create_time || '' }}</div>
              <div v-if="eventText(event)" class="event-text">{{ eventText(event) }}</div>
            </a-timeline-item>
          </a-timeline>
          <a-empty v-else description="暂无运行事件" :image="aEmptyImage" />
        </div>
        <div class="detail-section">
          <div class="detail-section-title">独立子线程</div>
          <div v-if="threadMessages.length" class="thread-messages">
            <div v-for="(item, index) in threadMessages" :key="index" class="thread-message" :class="`role-${item.type}`">
              <span class="thread-role">{{ roleLabel(item.type) }}</span>
              <pre class="thread-content">{{ threadContent(item) }}</pre>
            </div>
          </div>
          <a-empty v-else :description="threadAvailable ? '子线程暂未产生可展示消息' : '子线程历史暂不可用'" :image="aEmptyImage" />
        </div>
      </a-spin>
    </a-modal>
  </a-drawer>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { Empty } from 'ant-design-vue'
import {
  getSubAgentRun,
  getSubAgentRunEvents,
  getSubAgentRunThread,
  getSubAgentRuntimeSummaries,
} from '@/api/subagent'

const props = defineProps({
  open: { type: Boolean, default: false },
  sessionId: { type: [String, Number], default: null },
})

defineEmits(['update:open'])

const aEmptyImage = Empty.PRESENTED_IMAGE_SIMPLE
const runs = ref([])
const loading = ref(false)
const detailOpen = ref(false)
const detailLoading = ref(false)
const selectedTask = ref(null)
const events = ref([])
const eventCursor = ref('')
const threadMessages = ref([])
const threadAvailable = ref(false)
let refreshTimer = null

const detailTitle = computed(() => selectedTask.value
  ? `${selectedTask.value.subagent_name || 'SubAgent'} · 子线程详情`
  : 'SubAgent 子线程详情')

async function loadSummaries() {
  if (!props.sessionId) return
  loading.value = true
  try {
    const res = await getSubAgentRuntimeSummaries(props.sessionId)
    runs.value = res.data || []
  } catch {
    // 侧栏轮询失败时保留上一份摘要，避免干扰主对话。
  } finally {
    loading.value = false
  }
}

async function openTask(run) {
  selectedTask.value = run
  events.value = []
  eventCursor.value = ''
  threadMessages.value = []
  detailOpen.value = true
  detailLoading.value = true
  try {
    const [taskRes, eventRes, threadRes] = await Promise.all([
      getSubAgentRun(run.task_id, props.sessionId),
      getSubAgentRunEvents(run.task_id, props.sessionId),
      getSubAgentRunThread(run.task_id, props.sessionId),
    ])
    selectedTask.value = taskRes.data || run
    applyEvents(eventRes.data)
    threadMessages.value = threadRes.data?.messages || []
    threadAvailable.value = !!threadRes.data?.available
  } finally {
    detailLoading.value = false
  }
}

function applyEvents(payload) {
  const incoming = payload?.events || []
  const known = new Set(events.value.map(item => item.cursor))
  events.value.push(...incoming.filter(item => !known.has(item.cursor)))
  if (payload?.next_cursor) eventCursor.value = payload.next_cursor
}

async function refreshActiveTask() {
  if (!props.open || !detailOpen.value || !selectedTask.value?.task_id || !props.sessionId) return
  try {
    const [eventRes, taskRes, threadRes] = await Promise.all([
      getSubAgentRunEvents(selectedTask.value.task_id, props.sessionId, eventCursor.value),
      getSubAgentRun(selectedTask.value.task_id, props.sessionId),
      getSubAgentRunThread(selectedTask.value.task_id, props.sessionId),
    ])
    applyEvents(eventRes.data)
    selectedTask.value = taskRes.data || selectedTask.value
    threadMessages.value = threadRes.data?.messages || threadMessages.value
    threadAvailable.value = !!threadRes.data?.available
  } catch {
    // 轮询失败不关闭用户正在查看的详情。
  }
}

function startPolling() {
  stopPolling()
  if (!props.open || !props.sessionId) return
  refreshTimer = setInterval(() => {
    loadSummaries()
    refreshActiveTask()
  }, 4000)
}

function stopPolling() {
  if (refreshTimer) clearInterval(refreshTimer)
  refreshTimer = null
}

function statusLabel(status) {
  return ({ pending: '待执行', running: '运行中', completed: '已完成', failed: '失败', cancelled: '已取消', cancel_requested: '取消中' })[status] || '未知'
}

function eventLabel(type) {
  return ({ subagent_task_start: '任务开始', subagent_tool_call: '调用工具', subagent_tool_result: '工具完成', subagent_token: '生成输出', subagent_task_done: '任务完成', subagent_error: '执行异常', subagent_error_retry: '准备重试' })[type] || type
}

function eventColor(type) {
  if (type === 'subagent_error') return 'red'
  if (type === 'subagent_task_done') return 'green'
  if (type === 'subagent_tool_call' || type === 'subagent_token') return 'blue'
  return 'gray'
}

function eventText(event) {
  try {
    const payload = typeof event.payload === 'string' ? JSON.parse(event.payload) : event.payload
    return payload?.toolDisplayName || payload?.toolName || payload?.message || payload?.status || ''
  } catch {
    return ''
  }
}

function roleLabel(type) {
  return ({ system: '系统', user: '用户', assistant: 'SubAgent', tool_response: '工具' })[type] || type
}

function threadContent(item) {
  if (item.content) return item.content
  if (item.responses) return JSON.stringify(item.responses, null, 2)
  if (item.toolCalls) return JSON.stringify(item.toolCalls, null, 2)
  return ''
}

watch(() => [props.open, props.sessionId], ([open]) => {
  if (open) {
    loadSummaries()
    startPolling()
  } else {
    stopPolling()
  }
}, { immediate: true })

watch(detailOpen, (open) => {
  if (!open) {
    selectedTask.value = null
    events.value = []
    eventCursor.value = ''
  }
})

onBeforeUnmount(stopPolling)
</script>

<style scoped>
.runtime-list { display: flex; flex-direction: column; gap: 8px; }
.runtime-item { display: flex; gap: 10px; width: 100%; padding: 10px; border: 1px solid var(--color-hairline); border-radius: 8px; background: var(--color-canvas); text-align: left; cursor: pointer; }
.runtime-item:hover { background: var(--color-canvas-soft); border-color: var(--color-hairline-strong); }
.runtime-status { width: 8px; height: 8px; margin-top: 6px; border-radius: 50%; flex: 0 0 auto; background: var(--color-mute); }
.is-running { background: var(--color-link); }.is-pending { background: var(--color-warning); }.is-completed { background: var(--color-success); }.is-failed { background: var(--color-error); }.is-cancelled { background: var(--color-mute); }
.runtime-main { display: flex; min-width: 0; flex: 1; flex-direction: column; gap: 3px; }.runtime-title { color: var(--color-ink); font-size: 14px; font-weight: 600; }.runtime-task, .runtime-progress { overflow: hidden; color: var(--color-body); font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }.runtime-progress { color: var(--color-mute); }.runtime-status-label { color: var(--color-mute); font-size: 12px; white-space: nowrap; }
.task-detail-summary { display: flex; align-items: center; gap: 8px; margin-bottom: 16px; color: var(--color-body); }.task-detail-status { padding: 2px 8px; border-radius: 999px; background: var(--color-canvas-soft-2); font-size: 12px; }.detail-section { margin-top: 18px; }.detail-section-title { margin-bottom: 8px; color: var(--color-ink); font-weight: 600; }.event-timeline { padding-top: 6px; }.event-title { color: var(--color-ink); font-size: 13px; font-weight: 600; }.event-time, .event-text { margin-top: 2px; color: var(--color-mute); font-size: 12px; }.thread-messages { display: flex; max-height: 360px; flex-direction: column; gap: 8px; overflow-y: auto; }.thread-message { padding: 8px 10px; border-radius: 8px; background: var(--color-canvas-soft); }.thread-role { color: var(--color-mute); font-size: 12px; font-weight: 600; }.thread-content { margin: 5px 0 0; color: var(--color-body); font-family: inherit; font-size: 12px; line-height: 1.6; white-space: pre-wrap; word-break: break-word; }
</style>
