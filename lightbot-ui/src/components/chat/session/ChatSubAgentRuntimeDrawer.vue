<template>
  <a-drawer
    :open="open"
    title="子智能体状态"
    placement="right"
    width="380"
    @update:open="$emit('update:open', $event)"
  >
    <template #extra>
      <a-tooltip title="刷新">
        <a-button type="text" size="small" class="runtime-refresh" :disabled="loading || summaryRefreshing" @click="refreshSummaries">
          <ReloadOutlined :class="{ 'is-spinning': summaryRefreshing }" />
        </a-button>
      </a-tooltip>
    </template>

    <div v-if="runtimeRuns.length" class="runtime-list">
      <button v-for="run in runtimeRuns" :key="run.task_id" type="button" class="runtime-item" @click="openTask(run)">
        <span class="runtime-status" :class="`is-${run.status}`"></span>
        <span class="runtime-main">
          <span class="runtime-title">{{ displayNameOf(run) }}</span>
          <span class="runtime-task">{{ run.task || '未提供任务描述' }}</span>
          <span class="runtime-progress">{{ run.progress_summary || run.status_label || statusLabel(run.status) }}</span>
        </span>
        <span v-if="canCancel(run)" class="runtime-cancel-slot">
          <a-tooltip :title="isCancelling(run) ? '取消中…' : '停止子智能体'">
            <button
              type="button"
              class="runtime-cancel"
              :disabled="isCancelling(run)"
              @click.stop="cancelRun(run)"
            >
              <a-spin v-if="isCancelling(run)" size="small" />
              <CircleStop v-else :size="16" />
            </button>
          </a-tooltip>
        </span>
        <span v-else class="runtime-status-label" :class="`is-${run.status}`">{{ run.status_label || statusLabel(run.status) }}</span>
      </button>
    </div>
    <a-empty v-else-if="!loading" description="当前会话暂无子智能体任务" />

    <SubAgentTaskDetailModal
      v-model:open="detailOpen"
      :session-id="sessionId"
      :task="selectedTask"
      :live-events="liveEvents"
    />
  </a-drawer>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import { CircleStop } from 'lucide-vue-next'
import SubAgentTaskDetailModal from './SubAgentTaskDetailModal.vue'
import { pickFresher } from '@/utils/subagentRuntime'
import {
  getSubAgentRuntimeSummaries,
  cancelSubAgentTask,
} from '@/api/subagent'

const props = defineProps({
  open: { type: Boolean, default: false },
  sessionId: { type: [String, Number], default: null },
  liveEvents: { type: Array, default: () => [] },
})

defineEmits(['update:open'])

const runs = ref([])
const loading = ref(false)
const summaryRefreshing = ref(false)
const detailOpen = ref(false)
const selectedTask = ref(null)
let refreshTimer = null

const liveTaskStates = computed(() => {
  const states = new Map()
  for (const event of props.liveEvents) {
    if (event?.type === 'subagent_batch_start') {
      for (const task of event.tasks || []) {
        states.set(String(task.task_id), {
          task_id: task.task_id,
          batch_id: event.batch_id,
          subagent_name: task.subagent_name,
          display_name: task.display_name || task.displayName || task.subagent_name,
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
    const taskKey = String(event.task_id)
    const state = states.get(taskKey) || {
      task_id: event.task_id,
      batch_id: event.batch_id,
      subagent_name: event.subagentName,
      display_name: event.display_name || event.displayName || event.subagentName,
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
      state.error = event.message || '任务执行异常'
    } else if (event.type === 'subagent_task_done') {
      state.status = event.status || 'completed'
      state.progress_summary = state.status === 'completed' ? '任务已完成' : '任务执行结束'
      const reply = event.result?.reply
      if (event.result?.error) state.error = event.result.error
      if (reply) state.reply = String(reply)
      if (!state.liveOutput && reply) state.liveOutput = String(reply)
    }
    if (event.display_name || event.displayName) {
      state.display_name = event.display_name || event.displayName
    }
    if (event.task) state.task = event.task
    if (event.status_label) state.status_label = event.status_label
    states.set(taskKey, state)
  }
  return states
})

const runtimeRuns = computed(() => {
  const merged = new Map(runs.value.map(run => [String(run.task_id), { ...run }]))
  for (const [taskId, live] of liveTaskStates.value) {
    merged.set(taskId, pickFresher(merged.get(taskId), live))
  }
  return [...merged.values()].sort((a, b) => Number(b.status === 'running') - Number(a.status === 'running'))
})

async function loadSummaries() {
  if (!props.sessionId) {
    runs.value = []
    return
  }
  loading.value = true
  try {
    // 子智能体状态抽屉是会话级视图，不能被当前一轮协作任务的 taskIds 限制。
    const res = await getSubAgentRuntimeSummaries(props.sessionId, 100)
    runs.value = res.data || []
  } catch {
    // 侧栏轮询失败时保留上一份摘要，避免干扰主对话。
  } finally {
    loading.value = false
  }
}

/** 手动刷新保证图标至少旋转 500ms，避免请求过快时用户没有操作反馈。 */
async function refreshSummaries() {
  if (summaryRefreshing.value) return
  summaryRefreshing.value = true
  const minDelay = new Promise(resolve => setTimeout(resolve, 500))
  try {
    await Promise.all([loadSummaries(), minDelay])
  } finally {
    summaryRefreshing.value = false
  }
}

const cancellingTasks = ref(new Set())

/** 侧栏可停止的状态：仅进行中/待执行/取消中允许点击停止 */
function canCancel(run) {
  return ['pending', 'running', 'cancel_requested'].includes(run?.status)
}

function isCancelling(run) {
  return run?.status === 'cancel_requested' || cancellingTasks.value.has(run?.task_id)
}

/** 手动停止一个子任务：置取消中（乐观更新），依赖轮询收敛到已取消 */
async function cancelRun(run) {
  if (!run?.task_id || !props.sessionId || isCancelling(run)) return
  cancellingTasks.value = new Set(cancellingTasks.value).add(run.task_id)
  try {
    await cancelSubAgentTask(run.task_id, props.sessionId)
    const target = runs.value.find(r => r.task_id === run.task_id)
    if (target) target.status = 'cancel_requested'
  } catch {
    message.error('停止子智能体失败，请稍后重试')
    const next = new Set(cancellingTasks.value)
    next.delete(run.task_id)
    cancellingTasks.value = next
  }
}

/** 打开详情弹窗：加载交给共享组件 SubAgentTaskDetailModal */
function openTask(run) {
  selectedTask.value = run
  detailOpen.value = true
}

function startPolling() {
  stopPolling()
  if (!props.open || !props.sessionId) return
  refreshTimer = setInterval(loadSummaries, 4000)
}

function stopPolling() {
  if (refreshTimer) clearInterval(refreshTimer)
  refreshTimer = null
}

function statusLabel(status) {
  return ({ pending: '待执行', running: '运行中', completed: '已完成', failed: '失败', cancelled: '已取消', timeout: '超时', cancel_requested: '取消中' })[status] || '未知'
}

function displayNameOf(task) {
  return task?.display_name || task?.displayName || task?.subagent_name || task?.subagentName || '子智能体'
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
  if (!open) selectedTask.value = null
})

onBeforeUnmount(stopPolling)
</script>

<style scoped>
.runtime-refresh { display: inline-flex; width: 28px; height: 28px; align-items: center; justify-content: center; border: 0 !important; color: var(--color-body); }
.runtime-refresh:hover { background: var(--color-canvas-soft) !important; color: var(--color-ink) !important; }.runtime-refresh:disabled { cursor: default; opacity: .6; }.is-spinning { animation: runtimeSpin .8s linear infinite; }
.runtime-list { display: flex; flex-direction: column; gap: 8px; }
.runtime-cancel-slot { display: inline-flex; align-items: flex-start; flex: 0 0 auto; }
.runtime-cancel { display: inline-flex; width: 26px; height: 26px; align-items: center; justify-content: center; border: 1px solid var(--color-hairline); border-radius: 6px; background: var(--color-canvas); color: var(--color-error); cursor: pointer; }
.runtime-cancel:hover { background: #fee2e2; border-color: var(--color-error); }
.runtime-cancel:disabled { cursor: default; opacity: .6; }
.runtime-item { display: flex; gap: 10px; width: 100%; padding: 10px; border: 1px solid var(--color-hairline); border-radius: 8px; background: var(--color-canvas); text-align: left; cursor: pointer; }
.runtime-item:hover { background: var(--color-canvas-soft); border-color: var(--color-hairline-strong); }
.runtime-status { width: 8px; height: 8px; margin-top: 6px; border-radius: 50%; flex: 0 0 auto; background: var(--color-mute); }.runtime-status.is-running { background: var(--color-link); box-shadow: 0 0 0 0 color-mix(in srgb, var(--color-link) 55%, transparent); animation: runningPulse 1.5s infinite; }.runtime-status.is-pending { background: var(--color-warning); }.runtime-status.is-completed { background: var(--color-success-500); }.runtime-status.is-failed { background: var(--color-error); }.runtime-status.is-cancelled { background: var(--color-mute); }
.runtime-main { display: flex; min-width: 0; flex: 1; flex-direction: column; gap: 3px; }.runtime-title { color: var(--color-ink); font-size: 14px; font-weight: 600; }.runtime-task, .runtime-progress { overflow: hidden; color: var(--color-body); font-size: 12px; line-height: 1.45; word-break: break-word; white-space: normal; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; }.runtime-progress { color: var(--color-mute); }.runtime-status-label { display: inline-flex; align-items: center; height: 22px; padding: 0 8px; border-radius: 999px; font-size: 12px; white-space: nowrap; }.runtime-status-label.is-running { color: #1d4ed8; background: #dbeafe; animation: runningTagPulse 1.5s ease-in-out infinite; }.runtime-status-label.is-pending { color: #a16207; background: #fef3c7; }.runtime-status-label.is-completed { color: #15803d; background: #dcfce7; }.runtime-status-label.is-failed { color: #b91c1c; background: #fee2e2; }.runtime-status-label.is-cancelled { color: var(--color-body); background: var(--color-canvas-soft-2); }
@keyframes runtimeSpin { to { transform: rotate(360deg); } } @keyframes runningPulse { 70% { box-shadow: 0 0 0 7px transparent; } 100% { box-shadow: 0 0 0 0 transparent; } } @keyframes runningTagPulse { 50% { opacity: .68; } }
</style>
