<template>
  <section class="subagent-batch-block">
    <button class="batch-header" type="button" @click="expanded = !expanded">
      <RobotOutlined class="batch-icon" />
      <span class="batch-title">SubAgent 批次委派</span>
      <span class="batch-summary">{{ modeLabel }} · {{ tasks.length }} 个任务</span>
      <span class="batch-status" :class="statusClass">{{ statusLabel }}</span>
      <RightOutlined class="batch-toggle" :class="{ expanded }" />
    </button>

    <div v-if="expanded" class="batch-body">
      <div class="batch-meta">
        <span v-if="batchId">批次：{{ batchId }}</span>
        <span>聚合：{{ aggregationLabel }}</span>
        <span v-if="event.mode === 'parallel'">并发上限由 Agent 调度</span>
      </div>
      <div class="task-tabs" role="tablist">
        <button v-for="(task, index) in tasks" :key="task.task_id || index" type="button"
          class="task-tab" :class="{ active: activeIndex === index, [statusClassOf(task.status)]: true }"
          @click="activeIndex = index">
          {{ index + 1 }}. {{ task.display_name || task.subagent_name || task.subagentName || 'SubAgent' }}
        </button>
      </div>
      <article v-if="activeTask" class="task-detail">
        <div class="task-detail-head">
          <strong>{{ activeTask.display_name || activeTask.subagent_name || activeTask.subagentName || 'SubAgent' }}</strong>
          <span class="task-status" :class="statusClassOf(activeTask.status)">{{ statusText(activeTask.status) }}</span>
        </div>
        <p v-if="activeTask.task" class="task-prompt">{{ activeTask.task }}</p>
        <p v-if="activeTask.task_id" class="task-id">任务：{{ activeTask.task_id }}</p>
        <pre v-if="activeTask.output" class="task-output">{{ activeTask.output }}</pre>
        <p v-else-if="activeTask.reply" class="task-output">{{ activeTask.reply }}</p>
        <p v-if="activeTask.error" class="task-error">{{ activeTask.error }}</p>
        <div v-if="activeTask.tools.length" class="task-tools">
          <span v-for="tool in activeTask.tools" :key="tool.key">调用 {{ tool.toolDisplayName || tool.toolName || '工具' }}</span>
        </div>
      </article>
    </div>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { RightOutlined, RobotOutlined } from '@ant-design/icons-vue'

const props = defineProps({
  event: { type: Object, required: true },
  events: { type: Array, default: () => [] },
  allEvents: { type: Array, default: () => [] },
  defaultExpanded: { type: Boolean, default: true },
})

const expanded = ref(props.defaultExpanded)
const activeIndex = ref(0)
const batchId = computed(() => props.event.batch_id || props.event.batchId || '')
const scopedEvents = computed(() => (props.events || props.allEvents || []).filter(item =>
  !batchId.value || item.batch_id === batchId.value || item.batchId === batchId.value))

const tasks = computed(() => {
  const index = new Map()
  for (const task of props.event.tasks || []) {
    index.set(task.task_id, { ...task, status: task.status || 'pending', tools: [] })
  }
  for (const item of scopedEvents.value) {
    const result = item.result && typeof item.result === 'object' ? item.result : null
    const resultItems = item.results || result?.results
    if (Array.isArray(resultItems)) for (const value of resultItems) mergeTask(index, value)
    if (result?.task_id || item.type === 'subagent_task_done') mergeTask(index, result || item)
    if (item.task_id && (item.type === 'subagent_token' || item.type === 'subagent_tool_call' || item.type === 'subagent_tool_result')) {
      const task = index.get(item.task_id) || { task_id: item.task_id, subagent_name: item.subagentName, status: 'running', tools: [] }
      if (item.type === 'subagent_token') task.output = (task.output || '') + (item.content || '')
      else if (item.type === 'subagent_tool_call') task.tools.push({ ...item, key: `${item.toolName}-${task.tools.length}` })
      index.set(item.task_id, task)
    }
  }
  return [...index.values()]
})

const batchStatus = computed(() => {
  const update = [...scopedEvents.value].reverse().find(item => item.type === 'subagent_batch_update' || item.type === 'subagent_batch_done')
  if (update?.status) return update.status
  if (tasks.value.some(task => task.status === 'failed')) return 'failed'
  if (tasks.value.length && tasks.value.every(task => task.status === 'completed')) return 'completed'
  return props.event.mode === 'background' ? 'submitted' : 'running'
})
const activeTask = computed(() => tasks.value[activeIndex.value] || null)
const modeLabel = computed(() => ({ sync: '同步', parallel: '并行', background: '后台' })[props.event.mode] || '同步')
const aggregationLabel = computed(() => props.event.aggregation === 'summarize' ? '汇总结果' : '返回全部结果')
const statusLabel = computed(() => statusText(batchStatus.value))
const statusClass = computed(() => statusClassOf(batchStatus.value))

watch(tasks, value => { if (activeIndex.value >= value.length) activeIndex.value = 0 }, { deep: true })

function mergeTask(index, value) {
  if (!value?.task_id) return
  const current = index.get(value.task_id) || { task_id: value.task_id, tools: [] }
  index.set(value.task_id, { ...current, ...value, tools: current.tools || [] })
}
function statusText(status) {
  return ({ pending: '等待中', submitted: '已提交', running: '执行中', completed: '已完成', failed: '失败', cancelled: '已取消', cancel_requested: '取消中' })[status] || '处理中'
}
function statusClassOf(status) {
  if (status === 'completed') return 'success'
  if (status === 'failed') return 'error'
  if (status === 'cancelled' || status === 'cancel_requested') return 'warn'
  return 'running'
}
</script>

<style scoped>
.subagent-batch-block { border: 1px solid #f6d998; border-radius: 10px; overflow: hidden; background: var(--color-canvas); }
.batch-header { width: 100%; display: flex; align-items: center; gap: 8px; border: 0; padding: 12px 14px; color: var(--color-ink); background: linear-gradient(90deg, #fff9eb, var(--color-canvas)); cursor: pointer; text-align: left; }
.batch-icon { color: #d48806; font-size: 16px; }.batch-title { font-weight: 650; }.batch-summary, .batch-meta, .task-id { color: var(--color-mute); font-size: 12px; }.batch-summary { flex: 1; }
.batch-status, .task-status { padding: 3px 8px; border-radius: 999px; font-size: 12px; white-space: nowrap; }.running { background: #eff6ff; color: #2563eb; }.success { background: #f0fdf4; color: #15803d; }.error { background: #fef2f2; color: #b91c1c; }.warn { background: #fffbeb; color: #b45309; }
.batch-toggle { transition: transform .2s; }.batch-toggle.expanded { transform: rotate(90deg); }.batch-body { padding: 12px 14px 14px; }.batch-meta { display: flex; flex-wrap: wrap; gap: 10px; margin-bottom: 10px; }
.task-tabs { display: flex; gap: 6px; overflow-x: auto; padding-bottom: 8px; }.task-tab { border: 1px solid var(--color-hairline); border-radius: 6px; padding: 6px 9px; background: var(--color-canvas-soft); color: var(--color-body); font-size: 12px; cursor: pointer; white-space: nowrap; }.task-tab.active { border-color: #d48806; color: #9a6700; background: #fff9eb; }
.task-detail { border-top: 1px solid var(--color-hairline); padding-top: 11px; }.task-detail-head { display: flex; justify-content: space-between; gap: 12px; }.task-prompt, .task-output, .task-error { white-space: pre-wrap; word-break: break-word; line-height: 1.6; font-size: 13px; }.task-prompt { color: var(--color-body); }.task-output { margin: 10px 0 0; color: var(--color-ink); font-family: inherit; }.task-error { color: #b91c1c; }.task-tools { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 10px; }.task-tools span { font-size: 12px; color: var(--color-mute); background: var(--color-canvas-soft); padding: 3px 6px; border-radius: 4px; }
</style>
