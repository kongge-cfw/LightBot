<template>
  <a-drawer
    :open="open"
    title="SubAgent 运行态"
    placement="right"
    width="380"
    @update:open="$emit('update:open', $event)"
  >
    <template #extra>
      <a-tooltip title="刷新运行态">
        <button type="button" class="runtime-refresh" :disabled="loading" @click="loadSummaries">
          <ReloadOutlined :class="{ 'is-spinning': loading }" />
        </button>
      </a-tooltip>
    </template>

    <div v-if="runtimeRuns.length" class="runtime-list">
      <button v-for="run in runtimeRuns" :key="run.task_id" type="button" class="runtime-item" @click="openTask(run)">
        <span class="runtime-status" :class="`is-${run.status}`"></span>
        <span class="runtime-main">
          <span class="runtime-title">{{ run.subagent_name || 'SubAgent' }}</span>
          <span class="runtime-task">{{ run.task || '未提供任务描述' }}</span>
          <span class="runtime-progress">{{ run.progress_summary || statusLabel(run.status) }}</span>
        </span>
        <span class="runtime-status-label" :class="`is-${run.status}`">{{ statusLabel(run.status) }}</span>
      </button>
    </div>
    <a-empty v-else-if="!loading" description="当前会话暂无 SubAgent 任务" />

    <a-modal v-model:open="detailOpen" :title="detailTitle" width="760px" :footer="null" destroy-on-close>
      <a-spin :spinning="detailLoading">
        <div class="task-detail-scroll">
          <div v-if="selectedDisplayTask" class="task-detail-summary">
            <span :class="['task-detail-status', `is-${selectedDisplayTask.status}`]">{{ statusLabel(selectedDisplayTask.status) }}</span>
            <span :class="['task-detail-progress', `is-${selectedDisplayTask.status}`]">{{ selectedDisplayTask.progress_summary || statusLabel(selectedDisplayTask.status) }}</span>
          </div>
          <div v-if="displayOutput" class="detail-section live-output-section">
            <div class="detail-section-title">{{ isSelectedTaskDone ? '最终输出' : '实时输出' }}</div>
            <MarkdownPreview :content="displayOutput" :finalized="isSelectedTaskDone" :image-preview="false" />
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
        </div>
      </a-spin>
    </a-modal>
  </a-drawer>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { Empty } from 'ant-design-vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import MarkdownPreview from '@/components/MarkdownPreview.vue'
import {
  getSubAgentRun,
  getSubAgentRunEvents,
  getSubAgentRunThread,
  getSubAgentRuntimeSummaries,
} from '@/api/subagent'

const props = defineProps({
  open: { type: Boolean, default: false },
  sessionId: { type: [String, Number], default: null },
  liveEvents: { type: Array, default: () => [] },
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

const liveTaskStates = computed(() => {
  const states = new Map()
  for (const event of props.liveEvents) {
    if (event?.type === 'subagent_batch_start') {
      for (const task of event.tasks || []) {
        states.set(task.task_id, {
          task_id: task.task_id,
          batch_id: event.batch_id,
          subagent_name: task.subagent_name,
          task: task.task,
          status: 'pending',
          progress_summary: '等待调度',
          liveOutput: '',
          reply: '',
        })
      }
      continue
    }
    if (!event?.task_id) continue
    const state = states.get(event.task_id) || {
      task_id: event.task_id,
      batch_id: event.batch_id,
      subagent_name: event.subagentName,
      task: '',
      status: 'pending',
      progress_summary: '等待调度',
      liveOutput: '',
      reply: '',
    }
    if (event.type === 'subagent_task_start') {
      state.status = 'running'
      state.progress_summary = '正在执行'
    } else if (event.type === 'subagent_tool_call') {
      state.progress_summary = `正在调用 ${event.toolDisplayName || event.toolName || '工具'}`
    } else if (event.type === 'subagent_tool_result') {
      state.progress_summary = '工具执行完成，继续处理'
    } else if (event.type === 'subagent_token') {
      state.status = 'running'
      state.progress_summary = '正在生成输出'
      state.liveOutput = `${state.liveOutput || ''}${event.content || ''}`.slice(-8000)
    } else if (event.type === 'subagent_error') {
      state.status = 'failed'
      state.progress_summary = event.message || '任务执行异常'
    } else if (event.type === 'subagent_task_done') {
      state.status = event.status || 'completed'
      state.progress_summary = state.status === 'completed' ? '任务已完成' : '任务执行结束'
      const reply = event.result?.reply
      if (reply) state.reply = String(reply)
      if (!state.liveOutput && reply) state.liveOutput = String(reply)
    }
    states.set(event.task_id, state)
  }
  return states
})

const runtimeRuns = computed(() => {
  const merged = new Map(runs.value.map(run => [run.task_id, { ...run }]))
  for (const [taskId, live] of liveTaskStates.value) {
    merged.set(taskId, { ...(merged.get(taskId) || {}), ...live })
  }
  return [...merged.values()].sort((a, b) => Number(b.status === 'running') - Number(a.status === 'running'))
})

const selectedLiveState = computed(() => liveTaskStates.value.get(selectedTask.value?.task_id) || null)
const selectedDisplayTask = computed(() => selectedTask.value
  ? { ...selectedTask.value, ...(selectedLiveState.value || {}) }
  : null)
const liveOutput = computed(() => selectedLiveState.value?.liveOutput || '')
const isSelectedTaskDone = computed(() => ['completed', 'failed', 'cancelled'].includes(selectedDisplayTask.value?.status))
const displayOutput = computed(() => {
  const task = selectedDisplayTask.value
  if (!task) return ''
  if (isSelectedTaskDone.value && task.reply) return task.reply
  return liveOutput.value || task.reply || ''
})

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
.runtime-refresh { display: inline-flex; width: 28px; height: 28px; align-items: center; justify-content: center; border: 1px solid var(--color-hairline); border-radius: 6px; background: var(--color-canvas); color: var(--color-body); cursor: pointer; }
.runtime-refresh:hover { background: var(--color-canvas-soft); color: var(--color-ink); }.runtime-refresh:disabled { cursor: default; opacity: .6; }.is-spinning { animation: runtimeSpin .8s linear infinite; }
.runtime-list { display: flex; flex-direction: column; gap: 8px; }
.runtime-item { display: flex; gap: 10px; width: 100%; padding: 10px; border: 1px solid var(--color-hairline); border-radius: 8px; background: var(--color-canvas); text-align: left; cursor: pointer; }
.runtime-item:hover { background: var(--color-canvas-soft); border-color: var(--color-hairline-strong); }
.runtime-status { width: 8px; height: 8px; margin-top: 6px; border-radius: 50%; flex: 0 0 auto; background: var(--color-mute); }.runtime-status.is-running { background: var(--color-link); box-shadow: 0 0 0 0 color-mix(in srgb, var(--color-link) 55%, transparent); animation: runningPulse 1.5s infinite; }.runtime-status.is-pending { background: var(--color-warning); }.runtime-status.is-completed { background: var(--color-success-500); }.runtime-status.is-failed { background: var(--color-error); }.runtime-status.is-cancelled { background: var(--color-mute); }
.runtime-main { display: flex; min-width: 0; flex: 1; flex-direction: column; gap: 3px; }.runtime-title { color: var(--color-ink); font-size: 14px; font-weight: 600; }.runtime-task, .runtime-progress { overflow: hidden; color: var(--color-body); font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }.runtime-progress { color: var(--color-mute); }.runtime-status-label, .task-detail-status { display: inline-flex; align-items: center; height: 22px; padding: 0 8px; border-radius: 999px; font-size: 12px; white-space: nowrap; }.runtime-status-label.is-running, .task-detail-status.is-running { color: #1d4ed8; background: #dbeafe; animation: runningTagPulse 1.5s ease-in-out infinite; }.runtime-status-label.is-pending, .task-detail-status.is-pending { color: #a16207; background: #fef3c7; }.runtime-status-label.is-completed, .task-detail-status.is-completed { color: #15803d; background: #dcfce7; }.runtime-status-label.is-failed, .task-detail-status.is-failed { color: #b91c1c; background: #fee2e2; }.runtime-status-label.is-cancelled, .task-detail-status.is-cancelled { color: var(--color-body); background: var(--color-canvas-soft-2); }
.task-detail-scroll { display: flex; max-height: 64vh; flex-direction: column; gap: 18px; overflow-y: auto; padding: 2px 12px 8px 2px; }.task-detail-summary { display: flex; align-items: center; gap: 8px; color: var(--color-body); }.task-detail-progress.is-pending { color: #a16207; }.task-detail-progress.is-completed { color: #15803d; }.task-detail-progress.is-running { color: #1d4ed8; }.detail-section { min-width: 0; }.detail-section-title { margin-bottom: 8px; color: var(--color-ink); font-weight: 600; }.live-output-section { padding: 12px; border: 1px solid var(--color-hairline); border-radius: 8px; background: var(--color-canvas-soft); }.event-timeline { padding-top: 6px; }.event-title { color: var(--color-ink); font-size: 13px; font-weight: 600; }.event-time, .event-text { margin-top: 2px; color: var(--color-mute); font-size: 12px; }.thread-messages { display: flex; max-height: 360px; flex-direction: column; gap: 8px; overflow-y: auto; padding-right: 8px; }.thread-message { padding: 8px 10px; border-radius: 8px; background: var(--color-canvas-soft); }.thread-role { color: var(--color-mute); font-size: 12px; font-weight: 600; }.thread-content { margin: 5px 0 0; color: var(--color-body); font-family: inherit; font-size: 12px; line-height: 1.6; white-space: pre-wrap; word-break: break-word; }
@keyframes runtimeSpin { to { transform: rotate(360deg); } } @keyframes runningPulse { 70% { box-shadow: 0 0 0 7px transparent; } 100% { box-shadow: 0 0 0 0 transparent; } } @keyframes runningTagPulse { 50% { opacity: .68; } }
</style>
