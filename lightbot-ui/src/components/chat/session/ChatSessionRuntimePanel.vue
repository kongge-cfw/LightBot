<template>
  <aside class="runtime-panel" aria-label="会话协作状态">
    <header class="runtime-panel-header">
      <div><strong>协作状态</strong><span>实时同步当前会话</span></div>
      <span class="runtime-live-indicator"><i></i>实时</span>
    </header>

    <nav class="runtime-status-list" aria-label="会话状态分类">
      <a-button type="default" class="runtime-status-button" :class="{ active: activeSection === 'todos' }" block
        :aria-expanded="activeSection === 'todos'" @click="selectSection('todos')">
        <span class="runtime-status-icon is-todo"><CheckSquareOutlined /></span>
        <span class="runtime-status-copy"><strong>待办</strong><small>计划与完成进度</small></span>
        <span class="runtime-status-meta">{{ todos.length ? `${completedTodoCount}/${todos.length} · ${todoProgress}%` : '0' }}</span>
        <RightOutlined class="runtime-status-arrow" :class="{ expanded: activeSection === 'todos' }" />
      </a-button>
      <a-button type="default" class="runtime-status-button" :class="{ active: activeSection === 'files' }" block
        :aria-expanded="activeSection === 'files'" @click="selectSection('files')">
        <span class="runtime-status-icon is-files"><PaperClipOutlined /></span>
        <span class="runtime-status-copy"><strong>附件/文件</strong><small>会话中可预览的文件</small></span>
        <span class="runtime-status-meta">{{ attachments.length }}</span>
        <RightOutlined class="runtime-status-arrow" :class="{ expanded: activeSection === 'files' }" />
      </a-button>
      <a-button type="default" class="runtime-status-button" :class="{ active: activeSection === 'artifacts' }" block
        :aria-expanded="activeSection === 'artifacts'" @click="selectSection('artifacts')">
        <span class="runtime-status-icon is-artifacts"><InboxOutlined /></span>
        <span class="runtime-status-copy"><strong>产物</strong><small>主 Agent 已交付的结果</small></span>
        <span class="runtime-status-meta">{{ artifacts.length }}</span>
        <RightOutlined class="runtime-status-arrow" :class="{ expanded: activeSection === 'artifacts' }" />
      </a-button>
      <a-button type="default" class="runtime-status-button" :class="{ active: activeSection === 'subagents' }" block
        :aria-expanded="activeSection === 'subagents'" @click="selectSection('subagents')">
        <span class="runtime-status-icon is-subagents"><RobotOutlined /></span>
        <span class="runtime-status-copy"><strong>子智能体</strong><small>并行任务与实时输出</small></span>
        <span class="runtime-status-meta">{{ subagentMeta }}</span>
        <RightOutlined class="runtime-status-arrow" :class="{ expanded: activeSection === 'subagents' }" />
      </a-button>
    </nav>

    <transition name="runtime-detail" mode="out-in">
      <section v-if="activeSection" :key="activeSection" class="runtime-detail-panel">
        <a-card size="small" class="runtime-detail-card" :bordered="false">
        <header class="runtime-detail-header">
          <span><component :is="activeIcon" /> {{ activeTitle }}</span>
          <button type="button" class="runtime-detail-close" aria-label="收起状态详情" @click="activeSection = null"><CloseOutlined /></button>
        </header>

        <template v-if="activeSection === 'todos'">
          <div v-if="todos.length" class="todo-list">
            <div v-for="todo in todos" :key="todo.id" class="todo-item" :class="`is-${todo.status}`">
              <LoadingOutlined v-if="todo.status === 'in_progress'" spin />
              <CheckCircleFilled v-else-if="todo.status === 'completed'" />
              <CloseCircleFilled v-else-if="todo.status === 'cancelled'" />
              <BorderOutlined v-else />
              <span>{{ todo.content }}</span>
            </div>
            <div class="todo-progress-track"><span :style="{ width: `${todoProgress}%` }"></span></div>
          </div>
          <a-empty v-else :image="emptyImage" description="暂无待办" class="runtime-empty" />
        </template>

        <template v-else-if="activeSection === 'files'">
          <div v-if="attachments.length" class="file-list">
            <button v-for="file in attachments.slice(0, 4)" :key="file.id || file.objectKey || file.fileName" type="button"
              class="file-item" @click="$emit('open-files')">
              <FileOutlined /><span>{{ file.fileName || file.name || '未命名文件' }}</span>
            </button>
            <button v-if="attachments.length > 4" type="button" class="runtime-more" @click="$emit('open-files')">
              查看全部 {{ attachments.length }} 个文件
            </button>
          </div>
          <a-empty v-else :image="emptyImage" description="暂无附件或文件" class="runtime-empty" />
        </template>

        <template v-else-if="activeSection === 'artifacts'">
          <div v-if="artifacts.length" class="artifact-list">
            <button v-for="artifact in artifacts" :key="artifact.path || artifact.url || artifact.name" type="button"
              class="artifact-card" @click="$emit('open-files')">
              <FileDoneOutlined />
              <span class="artifact-main"><strong>{{ artifact.name || '未命名产物' }}</strong><small>{{ artifact.path || artifact.contentType || '已交付' }}</small></span>
            </button>
          </div>
          <a-empty v-else :image="emptyImage" description="暂无交付产物" class="runtime-empty" />
        </template>

        <template v-else-if="activeSection === 'subagents'">
          <transition-group v-if="runtimeRuns.length" name="subagent-card" tag="div" class="subagent-list">
            <button v-for="run in runtimeRuns" :key="run.task_id" type="button" class="subagent-card" :class="`is-${run.status}`"
              @click="$emit('open-subagent')">
              <span class="subagent-avatar"><RobotOutlined /></span>
              <span class="subagent-main">
                <span class="subagent-name-row"><strong>{{ displayNameOf(run) }}</strong><span class="subagent-status" :class="`is-${run.status}`">{{ statusLabel(run.status) }}</span></span>
                <span class="subagent-task">{{ run.task || '正在准备任务' }}</span>
                <span class="subagent-output">{{ run.liveOutput || run.progress_summary || run.status_label || '等待调度' }}</span>
              </span>
              <LoadingOutlined v-if="isRunning(run.status)" spin class="subagent-spinner" />
            </button>
          </transition-group>
          <a-empty v-else :image="emptyImage" description="暂无子智能体任务" class="runtime-empty" />
        </template>
        </a-card>
      </section>
    </transition>
  </aside>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { Empty } from 'ant-design-vue'
import {
  BorderOutlined, CheckCircleFilled, CheckSquareOutlined, CloseCircleFilled, CloseOutlined,
  FileDoneOutlined, FileOutlined, InboxOutlined, LoadingOutlined, PaperClipOutlined,
  RightOutlined, RobotOutlined,
} from '@ant-design/icons-vue'
import { getSessionAttachments } from '../../../api/chatSession'
import { getSubAgentRuntimeSummaries } from '../../../api/subagent'

const props = defineProps({
  sessionId: { type: [String, Number], default: null },
  messages: { type: Array, default: () => [] },
  liveEvents: { type: Array, default: () => [] },
})

defineEmits(['open-files', 'open-subagent'])

const emptyImage = Empty.PRESENTED_IMAGE_SIMPLE
const activeSection = ref(null)
const attachments = ref([])
const dbRuns = ref([])
let refreshTimer = null
let knownRunningTaskIds = new Set()

const sectionConfig = {
  todos: { title: '待办', icon: CheckSquareOutlined },
  files: { title: '附件/文件', icon: PaperClipOutlined },
  artifacts: { title: '产物', icon: InboxOutlined },
  subagents: { title: '子智能体', icon: RobotOutlined },
}

const activeTitle = computed(() => sectionConfig[activeSection.value]?.title || '')
const activeIcon = computed(() => sectionConfig[activeSection.value]?.icon || InboxOutlined)

function selectSection(key) {
  activeSection.value = activeSection.value === key ? null : key
}

function parseResult(event) {
  if (!event?.result) return null
  if (typeof event.result === 'object') return event.result
  try { return JSON.parse(event.result) } catch { return null }
}

const allToolEvents = computed(() => props.messages.flatMap(message => message?._toolEvents || []))

const todos = computed(() => {
  let snapshot = []
  for (const event of allToolEvents.value) {
    if (event?.type !== 'tool_result' || !['write_todos', 'write_todo'].includes(event.toolName)) continue
    const payload = parseResult(event)
    if (payload?.success && Array.isArray(payload.todos)) snapshot = payload.todos
  }
  return snapshot
})

const completedTodoCount = computed(() => todos.value.filter(todo => todo.status === 'completed').length)
const todoProgress = computed(() => todos.value.length ? Math.round(completedTodoCount.value * 100 / todos.value.length) : 0)

const artifacts = computed(() => {
  const byPath = new Map()
  for (const event of allToolEvents.value) {
    if (event?.type !== 'tool_result' || event.toolName !== 'present_artifacts') continue
    const payload = parseResult(event)
    for (const artifact of payload?.artifacts || []) {
      const key = artifact.path || artifact.url || artifact.name
      if (key) byPath.set(key, artifact)
    }
  }
  return [...byPath.values()]
})

const liveTaskStates = computed(() => {
  const states = new Map()
  for (const event of props.liveEvents) {
    if (event?.type === 'subagent_batch_start') {
      for (const task of event.tasks || []) {
        states.set(task.task_id, {
          task_id: task.task_id, batch_id: event.batch_id, subagent_name: task.subagent_name,
          display_name: task.display_name || task.displayName || task.subagent_name,
          task: task.task, status: 'pending', progress_summary: '等待调度', liveOutput: '',
        })
      }
      continue
    }
    if (!event?.task_id) continue
    const state = states.get(event.task_id) || {
      task_id: event.task_id, batch_id: event.batch_id, subagent_name: event.subagentName,
      display_name: event.display_name || event.displayName || event.subagentName,
      task: event.task || '', status: 'pending', progress_summary: '等待调度', liveOutput: '',
    }
    if (event.type === 'subagent_task_start') {
      state.status = 'running'; state.progress_summary = '正在执行'
    } else if (event.type === 'subagent_token') {
      state.status = 'running'; state.progress_summary = '正在生成输出'
      state.liveOutput = `${state.liveOutput || ''}${event.content || ''}`.slice(-260)
    } else if (event.type === 'subagent_tool_call') {
      state.status = 'running'; state.progress_summary = `正在调用 ${event.toolDisplayName || event.toolName || '工具'}`
    } else if (event.type === 'subagent_tool_result') {
      state.status = 'running'; state.progress_summary = '工具执行完成，继续处理'
    } else if (event.type === 'subagent_error') {
      state.status = 'failed'; state.progress_summary = event.message || '任务执行失败'
    } else if (event.type === 'subagent_task_done') {
      state.status = event.status || 'completed'
      state.progress_summary = state.status === 'completed' ? '任务已完成' : '任务已结束'
      state.liveOutput = event.result?.reply || state.liveOutput
    }
    if (event.task) state.task = event.task
    if (event.display_name || event.displayName) state.display_name = event.display_name || event.displayName
    states.set(event.task_id, state)
  }
  return states
})

const terminalStatuses = new Set(['completed', 'failed', 'cancelled', 'timeout'])
const runtimeRuns = computed(() => {
  const merged = new Map(dbRuns.value.map(run => [run.task_id, { ...run }]))
  for (const [taskId, live] of liveTaskStates.value) {
    const persisted = merged.get(taskId)
    if (persisted && terminalStatuses.has(persisted.status) && !terminalStatuses.has(live.status)) {
      merged.set(taskId, { ...live, ...persisted, liveOutput: live.liveOutput })
    } else {
      merged.set(taskId, { ...persisted, ...live })
    }
  }
  return [...merged.values()].sort((a, b) => Number(isRunning(b.status)) - Number(isRunning(a.status)))
})

const runningSubagentCount = computed(() => runtimeRuns.value.filter(run => isRunning(run.status)).length)
const subagentMeta = computed(() => runtimeRuns.value.length
  ? (runningSubagentCount.value ? `${runningSubagentCount.value}/${runtimeRuns.value.length} 运行中` : runtimeRuns.value.length)
  : '0')

function isRunning(status) { return ['pending', 'running', 'cancel_requested'].includes(status) }
function statusLabel(status) {
  return ({ pending: '待调度', running: '运行中', completed: '已完成', failed: '失败', cancelled: '已取消', timeout: '超时' })[status] || '处理中'
}
function displayNameOf(run) { return run.display_name || run.displayName || run.subagent_name || run.subagentName || '子智能体' }

async function refreshAttachments() {
  if (!props.sessionId) { attachments.value = []; return }
  try {
    const response = await getSessionAttachments(props.sessionId)
    attachments.value = response.data || []
  } catch {
    // 侧栏刷新失败时保留已有数据，不能影响主对话。
  }
}

async function refreshRuns() {
  if (!props.sessionId) { dbRuns.value = []; return }
  try {
    const response = await getSubAgentRuntimeSummaries(props.sessionId, 30)
    dbRuns.value = response.data || []
  } catch {
    // SSE 仍可提供本轮运行态，轮询失败不清空。
  }
}

function startPolling() {
  clearInterval(refreshTimer)
  refreshTimer = setInterval(() => {
    refreshAttachments()
    refreshRuns()
  }, 2000)
}

watch(() => props.sessionId, () => {
  activeSection.value = null
  knownRunningTaskIds = new Set()
  refreshAttachments()
  refreshRuns()
  startPolling()
}, { immediate: true })

watch(runtimeRuns, (runs) => {
  const currentRunningIds = new Set(runs.filter(run => isRunning(run.status)).map(run => run.task_id))
  const hasNewRunningTask = [...currentRunningIds].some(taskId => !knownRunningTaskIds.has(taskId))
  if (!activeSection.value && hasNewRunningTask) activeSection.value = 'subagents'
  knownRunningTaskIds = currentRunningIds
}, { deep: true })

watch(() => artifacts.value.length, refreshAttachments)

onBeforeUnmount(() => clearInterval(refreshTimer))
</script>

<style scoped>
.runtime-panel { width: 336px; min-height: 100%; flex: 0 0 336px; overflow-y: auto; border-left: 1px solid var(--color-hairline); background: var(--color-canvas); box-shadow: -10px 0 28px rgba(15, 23, 42, .04); padding: 20px 16px 28px; }
.runtime-panel-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-bottom: 16px; }.runtime-panel-header > div { display: flex; flex-direction: column; gap: 3px; }.runtime-panel-header strong { color: var(--color-ink); font-size: 15px; line-height: 1.35; }.runtime-panel-header span { color: var(--color-mute); font-size: 12px; }.runtime-live-indicator { display: inline-flex; align-items: center; gap: 5px; min-height: 24px; padding: 0 8px; border-radius: 999px; background: var(--color-canvas-soft-2); color: var(--color-mute); font-size: 11px; }.runtime-live-indicator i { width: 6px; height: 6px; border-radius: 50%; background: var(--color-success); box-shadow: 0 0 0 3px color-mix(in srgb, var(--color-success) 12%, transparent); }
.runtime-status-list { display: flex; flex-direction: column; gap: 8px; }.runtime-status-button { display: flex !important; width: 100%; height: auto !important; align-items: center; min-height: 66px; gap: 10px; border: 1px solid var(--color-hairline) !important; border-radius: var(--radius-md) !important; background: var(--color-canvas) !important; padding: 10px !important; color: var(--color-ink) !important; cursor: pointer; text-align: left; white-space: normal !important; transition: border-color .18s ease, background .18s ease, box-shadow .18s ease, transform .18s ease; }.runtime-status-button:hover { border-color: var(--color-hairline-strong) !important; background: var(--color-canvas-soft) !important; transform: translateY(-1px); }.runtime-status-button.active { border-color: var(--color-link) !important; background: var(--color-link-bg-soft) !important; box-shadow: 0 4px 14px color-mix(in srgb, var(--color-link) 14%, transparent); }.runtime-status-icon { display: grid; width: 34px; height: 34px; place-items: center; flex: 0 0 34px; border-radius: 9px; background: var(--color-canvas-soft-2); color: var(--color-mute); font-size: 16px; }.runtime-status-icon.is-todo { color: #15803d; background: #dcfce7; }.runtime-status-icon.is-files { color: #1d4ed8; background: #dbeafe; }.runtime-status-icon.is-artifacts { color: #9333ea; background: #f3e8ff; }.runtime-status-icon.is-subagents { color: #c2410c; background: #ffedd5; }.runtime-status-icon.is-automation { color: var(--color-link-deep); background: var(--color-link-bg-soft); }.runtime-status-copy { display: flex; min-width: 0; flex: 1; flex-direction: column; gap: 2px; }.runtime-status-copy strong { overflow: hidden; color: var(--color-ink); font-size: 13px; font-weight: 600; line-height: 1.3; text-overflow: ellipsis; white-space: nowrap; }.runtime-status-copy small { overflow: hidden; color: var(--color-mute); font-size: 11px; line-height: 1.35; text-overflow: ellipsis; white-space: nowrap; }.runtime-status-meta { flex: 0 0 auto; color: var(--color-mute); font-size: 11px; font-variant-numeric: tabular-nums; }.runtime-status-arrow { flex: 0 0 auto; color: var(--color-mute); font-size: 11px; transition: transform .2s ease; }.runtime-status-arrow.expanded { color: var(--color-link); transform: rotate(90deg); }.runtime-status-button.active .runtime-status-copy strong, .runtime-status-button.active .runtime-status-meta { color: var(--color-link-deep); }
.runtime-detail-panel { margin-top: 12px; }.runtime-detail-card { border: 1px solid var(--color-hairline) !important; border-radius: var(--radius-lg) !important; background: var(--color-canvas-soft) !important; }.runtime-detail-card :deep(.ant-card-body) { padding: 14px; }.runtime-detail-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; color: var(--color-ink); font-size: 13px; font-weight: 600; }.runtime-detail-header > span { display: inline-flex; align-items: center; gap: 7px; }.runtime-detail-close { display: grid; width: 26px; height: 26px; place-items: center; border: 0; border-radius: 6px; background: var(--color-canvas-soft-2); color: var(--color-mute); cursor: pointer; }.runtime-detail-close:hover { background: var(--color-hairline); color: var(--color-ink); }.runtime-detail-enter-active, .runtime-detail-leave-active { transition: opacity .18s ease, transform .18s ease; }.runtime-detail-enter-from, .runtime-detail-leave-to { opacity: 0; transform: translateY(-5px); }
.runtime-empty { margin: 0; padding: 8px 0 2px; }.runtime-empty :deep(.ant-empty-image) { height: 32px; margin-bottom: 5px; }.runtime-empty :deep(.ant-empty-description) { color: var(--color-mute); font-size: 12px; }.todo-list, .file-list, .artifact-list, .subagent-list { display: flex; flex-direction: column; gap: 8px; }.todo-item { display: flex; align-items: flex-start; gap: 8px; color: var(--color-body); font-size: 13px; line-height: 1.55; }.todo-item > :first-child { margin-top: 3px; color: var(--color-mute); }.todo-item.is-in_progress > :first-child { color: var(--color-link); }.todo-item.is-completed { color: var(--color-mute); text-decoration: line-through; }.todo-item.is-completed > :first-child { color: var(--color-success); }.todo-item.is-cancelled > :first-child { color: var(--color-error); }.todo-progress-track { height: 5px; overflow: hidden; border-radius: 99px; background: var(--color-canvas-soft-3); margin-top: 4px; }.todo-progress-track span { display: block; height: 100%; border-radius: inherit; background: var(--color-link); transition: width .35s ease; }
.file-item, .runtime-more { border: 0; background: transparent; padding: 4px 0; color: var(--color-body); display: flex; gap: 7px; align-items: center; cursor: pointer; min-width: 0; text-align: left; font-size: 13px; }.file-item:hover, .runtime-more:hover { color: var(--color-link); }.file-item span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.file-item svg { color: var(--color-mute); }.runtime-more { color: var(--color-link); font-size: 12px; }
.artifact-card { width: 100%; display: flex; align-items: center; gap: 9px; padding: 10px; border: 1px solid var(--color-hairline); border-radius: 8px; background: var(--color-canvas); color: var(--color-body); text-align: left; cursor: pointer; }.artifact-card:hover { border-color: var(--color-link); }.artifact-card > svg { color: #9333ea; font-size: 17px; }.artifact-main { min-width: 0; display: flex; flex-direction: column; gap: 2px; }.artifact-main strong, .artifact-main small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.artifact-main strong { font-size: 12px; color: var(--color-ink); }.artifact-main small { font-size: 11px; color: var(--color-mute); }
.subagent-card { width: 100%; display: flex; align-items: flex-start; gap: 9px; padding: 10px; border: 1px solid var(--color-hairline); border-radius: 10px; background: var(--color-canvas); cursor: pointer; text-align: left; color: var(--color-body); transition: border-color .2s ease, transform .2s ease; }.subagent-card:hover { border-color: var(--color-link); transform: translateY(-1px); }.subagent-card.is-running { border-color: color-mix(in srgb, var(--color-link) 42%, var(--color-hairline)); box-shadow: 0 0 0 1px color-mix(in srgb, var(--color-link) 8%, transparent); }.subagent-avatar { display: grid; place-items: center; flex: 0 0 28px; height: 28px; border-radius: 8px; background: #ffedd5; color: #c2410c; }.subagent-main { min-width: 0; flex: 1; display: flex; flex-direction: column; gap: 3px; }.subagent-name-row { display: flex; align-items: center; gap: 6px; }.subagent-name-row strong { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 13px; color: var(--color-ink); }.subagent-status { padding: 1px 5px; border-radius: 4px; color: var(--color-mute); background: var(--color-canvas-soft-2); font-size: 10px; flex: 0 0 auto; }.subagent-status.is-running { color: #1d4ed8; background: #dbeafe; }.subagent-status.is-completed { color: #15803d; background: #dcfce7; }.subagent-status.is-failed, .subagent-status.is-timeout { color: #b91c1c; background: #fee2e2; }.subagent-task, .subagent-output { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 11px; }.subagent-task { color: var(--color-body); }.subagent-output { color: var(--color-mute); }.subagent-spinner { color: var(--color-link); margin-top: 4px; }.subagent-card-enter-active { transition: opacity .28s ease, transform .28s ease; }.subagent-card-enter-from { opacity: 0; transform: translateY(10px) scale(.97); }
@media (max-width: 1100px) { .runtime-panel { display: none; } }
</style>
