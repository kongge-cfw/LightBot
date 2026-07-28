<template>
  <div class="dashboard">
    <div v-if="loading" class="dashboard-skeleton">
      <div class="stats-overview">
        <div class="stat-card" v-for="i in 4" :key="i">
          <div class="sk-block sk-icon"></div>
          <div class="sk-info">
            <div class="sk-block sk-line sk-value"></div>
            <div class="sk-block sk-line sk-label"></div>
          </div>
        </div>
      </div>
      <div class="panel panel-trend">
        <div class="sk-block sk-line sk-title"></div>
        <div class="sk-block sk-chart"></div>
      </div>
      <div class="dashboard-grid">
        <div class="panel" v-for="i in 3" :key="i">
          <div class="sk-block sk-line sk-title"></div>
          <div class="sk-block sk-chart-sm"></div>
        </div>
      </div>
    </div>

    <template v-else>
      <!-- KPI -->
      <div class="stats-overview">
        <LbStatCard :icon="RobotOutlined" accent="blue" :value="basic.agentCount ?? '-'" label="Agent" />
        <LbStatCard :icon="DatabaseOutlined" accent="green" :value="basic.knowledgeCount ?? '-'" label="知识库" />
        <LbStatCard :icon="MessageOutlined" accent="purple" :value="basic.sessionCount ?? '-'" label="对话会话" />
        <LbStatCard :icon="FileTextOutlined" accent="orange" :value="basic.messageCount ?? '-'" label="消息总数" />
      </div>

      <!-- 调用趋势：整行，占视觉重心 -->
      <div class="panel panel-trend">
        <div class="panel-header panel-header--trend">
          <h3>
            调用趋势
            <span class="trend-metric-hint">每日消息 / 新建会话</span>
          </h3>
          <div class="trend-controls">
            <div class="trend-quick-btns">
              <button
                v-for="d in trendQuickDays"
                :key="d"
                type="button"
                class="trend-quick-btn"
                :class="{ active: trendMode === 'days' && trendDays === d }"
                :disabled="trendLoading"
                @click="selectTrendDays(d)"
              >近{{ d }}天</button>
            </div>
            <a-range-picker
              v-model:value="trendDateRange"
              size="small"
              :allow-clear="true"
              :disabled="trendLoading"
              format="YYYY-MM-DD"
              :disabled-date="disabledTrendDate"
              @change="onTrendRangeChange"
            />
          </div>
        </div>
        <a-spin :spinning="trendLoading">
          <div ref="trendChartRef" class="chart-box chart-box--trend"></div>
          <div class="chat-summary">
            <span>区间会话: <b>{{ chatStats.totalSessions ?? '-' }}</b></span>
            <span>区间消息: <b>{{ chatStats.totalMessages ?? '-' }}</b></span>
            <span v-if="chatStats.trendStartDate && chatStats.trendEndDate" class="trend-range-info">
              {{ chatStats.trendStartDate }} ~ {{ chatStats.trendEndDate }}
            </span>
          </div>
        </a-spin>
      </div>

      <!-- 三列卡片区 -->
      <div class="dashboard-grid">
        <div class="panel">
          <div class="panel-header">
            <h3>Agent 概况</h3>
            <span class="panel-tip">{{ agentStats.total ?? 0 }} 个</span>
          </div>
          <div ref="agentChartRef" class="chart-box chart-box--donut"></div>
          <div class="recent-list" v-if="agentStats.recent?.length">
            <div class="recent-title">最近创建 Agent</div>
            <div v-for="a in agentStats.recent" :key="a.id" class="recent-item">
              <span class="recent-name">{{ a.name }}</span>
              <span :class="['recent-tag', a.status]">{{ recentStatusLabel(a) }}</span>
            </div>
          </div>
        </div>

        <div class="panel">
          <div class="panel-header">
            <h3>知识库概况</h3>
            <span class="panel-tip">{{ knowledgeStats.totalKnowledge ?? 0 }} 个</span>
          </div>
          <div class="knowledge-metrics">
            <div class="metric-row">
              <span class="metric-label">文档总数</span>
              <span class="metric-value">{{ knowledgeStats.totalDocuments ?? '-' }}</span>
            </div>
            <div class="metric-row">
              <span class="metric-label">分块总数</span>
              <span class="metric-value">{{ knowledgeStats.totalChunks ?? '-' }}</span>
            </div>
          </div>
          <div ref="knowledgeChartRef" class="chart-box chart-box--donut"></div>
          <div class="recent-list" v-if="knowledgeStats.recentDocuments?.length">
            <div class="recent-title">最近文档</div>
            <div v-for="doc in knowledgeStats.recentDocuments" :key="doc.id" class="recent-item">
              <span class="recent-name" :title="doc.name">{{ doc.name }}</span>
            </div>
          </div>
        </div>

        <div class="panel">
          <div class="panel-header">
            <h3>模型资源</h3>
          </div>
          <div class="model-metrics">
            <div class="model-metric-card">
              <div class="model-metric-value">{{ basic.providerCount ?? '-' }}</div>
              <div class="model-metric-label">模型提供商</div>
            </div>
            <div class="model-metric-card">
              <div class="model-metric-value">{{ basic.modelCount ?? '-' }}</div>
              <div class="model-metric-label">模型数量</div>
            </div>
          </div>
          <div class="model-metrics">
            <div class="model-metric-card">
              <div class="model-metric-value">{{ basic.documentCount ?? '-' }}</div>
              <div class="model-metric-label">文档总数</div>
            </div>
            <div class="model-metric-card">
              <div class="model-metric-value">{{ basic.chunkCount ?? '-' }}</div>
              <div class="model-metric-label">向量分块</div>
            </div>
          </div>
          <div ref="resourceChartRef" class="chart-box chart-box--bar"></div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { message } from 'ant-design-vue'
import { RobotOutlined, DatabaseOutlined, MessageOutlined, FileTextOutlined } from '@ant-design/icons-vue'
import * as echarts from 'echarts/core'
import { LineChart, BarChart, PieChart } from 'echarts/charts'
import {
  GridComponent,
  TooltipComponent,
  LegendComponent,
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { getDashboardBasic, getDashboardAgents, getDashboardKnowledge, getDashboardChat } from '../api/dashboard'
import LbStatCard from '../components/common/LbStatCard.vue'
import { getChartColors, baseTooltip, axisStyle } from '../utils/chartTheme'

echarts.use([LineChart, BarChart, PieChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

const basic = ref({})
const agentStats = ref({})
const knowledgeStats = ref({})
const chatStats = ref({})
const loading = ref(false)
const trendLoading = ref(false)

const trendQuickDays = [7, 14, 30]
const trendDays = ref(7)
const trendMode = ref('days')
const trendDateRange = ref(null)

const trendChartRef = ref(null)
const agentChartRef = ref(null)
const knowledgeChartRef = ref(null)
const resourceChartRef = ref(null)

let trendChart = null
let agentChart = null
let knowledgeChart = null
let resourceChart = null

const messageTrend = computed(() => chatStats.value.messagesPerDay || [])
const sessionTrend = computed(() => chatStats.value.sessionsPerDay || [])

function statusLabel(s) {
  const item = agentStats.value.statusList?.find(x => x.code === s)
  return item?.label || s || '草稿'
}

function recentStatusLabel(item) {
  return item?.statusLabel || statusLabel(item?.status)
}

function buildChatParams() {
  if (trendMode.value === 'range' && trendDateRange.value?.length === 2) {
    return {
      startDate: trendDateRange.value[0].format('YYYY-MM-DD'),
      endDate: trendDateRange.value[1].format('YYYY-MM-DD'),
    }
  }
  return { days: trendDays.value }
}

function ensureCharts() {
  if (trendChartRef.value && !trendChart) trendChart = echarts.init(trendChartRef.value)
  if (agentChartRef.value && !agentChart) agentChart = echarts.init(agentChartRef.value)
  if (knowledgeChartRef.value && !knowledgeChart) knowledgeChart = echarts.init(knowledgeChartRef.value)
  if (resourceChartRef.value && !resourceChart) resourceChart = echarts.init(resourceChartRef.value)
}

function renderTrendChart() {
  if (!trendChart) return
  const c = getChartColors()
  const axis = axisStyle()
  const dates = messageTrend.value.map(d => {
    const parts = String(d.date || '').split('-')
    return parts.length >= 3 ? `${parts[1]}/${parts[2]}` : d.date
  })
  const messages = messageTrend.value.map(d => Number(d.count) || 0)
  const sessions = sessionTrend.value.map(d => Number(d.count) || 0)
  trendChart.setOption({
    color: [c.primary, c.success],
    tooltip: { ...baseTooltip(), trigger: 'axis' },
    legend: {
      data: ['消息', '新建会话'],
      top: 0,
      textStyle: { color: c.mute, fontSize: 12 },
    },
    grid: { left: 40, right: 16, top: 36, bottom: 28 },
    xAxis: {
      type: 'category',
      data: dates,
      boundaryGap: false,
      ...axis,
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      ...axis,
    },
    series: [
      {
        name: '消息',
        type: 'line',
        smooth: true,
        showSymbol: messages.length <= 14,
        areaStyle: { opacity: 0.12 },
        data: messages,
      },
      {
        name: '新建会话',
        type: 'line',
        smooth: true,
        showSymbol: sessions.length <= 14,
        areaStyle: { opacity: 0.08 },
        data: sessions,
      },
    ],
  }, true)
}

function renderDonut(chart, list, emptyText) {
  if (!chart) return
  const c = getChartColors()
  const data = (list || [])
    .map(item => ({ name: item.label || item.code, value: Number(item.count) || 0 }))
    .filter(d => d.value > 0)
  chart.setOption({
    color: c.palette,
    tooltip: { ...baseTooltip(), trigger: 'item' },
    legend: {
      type: 'scroll',
      orient: 'vertical',
      right: 0,
      top: 'middle',
      textStyle: { color: c.mute, fontSize: 11 },
    },
    series: [{
      type: 'pie',
      radius: ['48%', '72%'],
      center: ['36%', '50%'],
      avoidLabelOverlap: true,
      itemStyle: { borderRadius: 4, borderColor: c.canvas, borderWidth: 2 },
      label: { show: false },
      data: data.length ? data : [{ name: emptyText, value: 1, itemStyle: { color: c.hairline } }],
    }],
  }, true)
}

function renderAgentChart() {
  renderDonut(agentChart, agentStats.value.statusList, '暂无 Agent')
}

function renderKnowledgeChart() {
  renderDonut(knowledgeChart, knowledgeStats.value.documentStatusList, '暂无文档')
}

function renderResourceChart() {
  if (!resourceChart) return
  const c = getChartColors()
  const axis = axisStyle()
  const items = [
    { name: '提供商', value: Number(basic.value.providerCount) || 0 },
    { name: '模型', value: Number(basic.value.modelCount) || 0 },
    { name: '文档', value: Number(basic.value.documentCount) || 0 },
    { name: '分块', value: Number(basic.value.chunkCount) || 0 },
  ]
  resourceChart.setOption({
    color: [c.purple],
    tooltip: { ...baseTooltip(), trigger: 'axis' },
    grid: { left: 48, right: 16, top: 12, bottom: 28 },
    xAxis: {
      type: 'category',
      data: items.map(i => i.name),
      ...axis,
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      ...axis,
    },
    series: [{
      type: 'bar',
      barWidth: 28,
      itemStyle: { borderRadius: [6, 6, 0, 0] },
      data: items.map(i => i.value),
    }],
  }, true)
}

function renderAllCharts() {
  ensureCharts()
  renderTrendChart()
  renderAgentChart()
  renderKnowledgeChart()
  renderResourceChart()
}

function resizeCharts() {
  trendChart?.resize()
  agentChart?.resize()
  knowledgeChart?.resize()
  resourceChart?.resize()
}

async function loadChatStats() {
  trendLoading.value = true
  try {
    const c = await getDashboardChat(buildChatParams()).catch(() => ({ data: {} }))
    chatStats.value = c.data || {}
    await nextTick()
    ensureCharts()
    renderTrendChart()
    // spin 遮罩变化后图表尺寸可能变化，补一次 resize
    nextTick(() => trendChart?.resize())
  } finally {
    trendLoading.value = false
  }
}

function selectTrendDays(d) {
  trendMode.value = 'days'
  trendDays.value = d
  trendDateRange.value = null
  loadChatStats()
}

function onTrendRangeChange(dates) {
  if (!dates || dates.length !== 2) {
    trendMode.value = 'days'
    trendDays.value = 7
    loadChatStats()
    return
  }
  const days = dates[1].diff(dates[0], 'day') + 1
  if (days > 90) {
    message.warning('最多支持查询90天（3个月）的数据')
    trendDateRange.value = null
    trendMode.value = 'days'
    trendDays.value = 7
    loadChatStats()
    return
  }
  trendMode.value = 'range'
  loadChatStats()
}

function disabledTrendDate(current) {
  if (!current) return false
  const now = new Date()
  const threeMonthsAgo = new Date(now.getFullYear(), now.getMonth() - 3, now.getDate(), 0, 0, 0)
  const todayEnd = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 23, 59, 59)
  return current.valueOf() < threeMonthsAgo.getTime() || current.valueOf() > todayEnd.getTime()
}

async function loadAll() {
  loading.value = true
  try {
    const [b, a, k] = await Promise.all([
      getDashboardBasic().catch(() => ({ data: {} })),
      getDashboardAgents().catch(() => ({ data: {} })),
      getDashboardKnowledge().catch(() => ({ data: {} })),
    ])
    basic.value = b.data || {}
    agentStats.value = a.data || {}
    knowledgeStats.value = k.data || {}
    await loadChatStats()
    await nextTick()
    renderAllCharts()
  } finally {
    loading.value = false
    await nextTick()
    renderAllCharts()
    resizeCharts()
  }
}

watch([agentStats, knowledgeStats, basic], () => {
  if (!loading.value) {
    nextTick(() => {
      ensureCharts()
      renderAgentChart()
      renderKnowledgeChart()
      renderResourceChart()
    })
  }
})

onMounted(() => {
  loadAll()
  window.addEventListener('resize', resizeCharts)
})

onUnmounted(() => {
  window.removeEventListener('resize', resizeCharts)
  trendChart?.dispose()
  agentChart?.dispose()
  knowledgeChart?.dispose()
  resourceChart?.dispose()
  trendChart = null
  agentChart = null
  knowledgeChart = null
  resourceChart = null
})
</script>

<style scoped>
.dashboard {
  height: 100%;
  overflow-x: hidden;
  overflow-y: auto;
  padding: 24px 32px 40px;
  background: var(--color-canvas-soft);
  display: flex;
  flex-direction: column;
  gap: 16px;
  box-sizing: border-box;
}

.dashboard-skeleton {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.sk-block {
  background: linear-gradient(
    90deg,
    var(--color-canvas-soft-2) 0%,
    var(--color-hairline-strong) 50%,
    var(--color-canvas-soft-2) 100%
  );
  background-size: 200% 100%;
  animation: sk-shimmer 2.4s ease-in-out infinite;
  border-radius: 6px;
}
@keyframes sk-shimmer {
  0% { background-position: 150% 0; }
  100% { background-position: -50% 0; }
}
.sk-info { flex: 1; display: flex; flex-direction: column; gap: 8px; }
.sk-icon { width: 48px; height: 48px; border-radius: 12px; flex-shrink: 0; }
.sk-line { height: 14px; border-radius: 4px; }
.sk-value { width: 80px; height: 24px; }
.sk-label { width: 48px; height: 12px; }
.sk-title { width: 120px; height: 18px; margin-bottom: 16px; }
.sk-chart { height: 260px; border-radius: 8px; }
.sk-chart-sm { height: 180px; border-radius: 8px; }

.stats-overview {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}
.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
  border-radius: 12px;
  padding: 20px;
}

.panel {
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
  border-radius: 12px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.panel-trend {
  min-height: 360px;
}
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  gap: 12px;
}
.panel-header h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--color-ink);
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.panel-tip {
  font-size: 12px;
  color: var(--color-mute);
}
.trend-metric-hint {
  font-size: 12px;
  font-weight: 400;
  color: var(--color-mute);
}
.panel-header--trend {
  flex-wrap: wrap;
}
.trend-controls {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.trend-quick-btns {
  display: flex;
  gap: 6px;
}
.trend-quick-btn {
  border: 1px solid var(--color-hairline);
  background: var(--color-canvas);
  color: var(--color-mute);
  border-radius: 6px;
  padding: 2px 10px;
  font-size: 12px;
  cursor: pointer;
}
.trend-quick-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.trend-chart-wrap {
  position: relative;
  min-height: 280px;
}
.trend-quick-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.chart-box {
  width: 100%;
  min-height: 180px;
}
.chart-box--trend {
  height: 280px;
  min-height: 280px;
}
.chart-box--donut {
  height: 200px;
  min-height: 200px;
}
.chart-box--bar {
  height: 180px;
  min-height: 180px;
  margin-top: 8px;
}

.chat-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-top: 8px;
  font-size: 13px;
  color: var(--color-mute);
}
.chat-summary b {
  color: var(--color-ink);
  font-weight: 600;
}
.trend-range-info {
  margin-left: auto;
  font-size: 12px;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.knowledge-metrics,
.model-metrics {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-bottom: 8px;
}
.metric-row {
  display: flex;
  justify-content: space-between;
  padding: 8px 10px;
  background: var(--color-canvas-soft);
  border-radius: 8px;
  font-size: 13px;
}
.metric-label { color: var(--color-mute); }
.metric-value { color: var(--color-ink); font-weight: 600; }
.model-metric-card {
  background: var(--color-canvas-soft);
  border-radius: 8px;
  padding: 12px;
  text-align: center;
}
.model-metric-value {
  font-size: 22px;
  font-weight: 700;
  color: var(--color-ink);
  line-height: 1.2;
}
.model-metric-label {
  margin-top: 4px;
  font-size: 12px;
  color: var(--color-mute);
}

.recent-list { margin-top: 8px; }
.recent-title {
  font-size: 12px;
  font-weight: 500;
  color: var(--color-mute);
  margin-bottom: 8px;
}
.recent-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 6px 0;
  border-bottom: 1px solid var(--color-hairline);
}
.recent-item:last-child { border-bottom: none; }
.recent-name {
  font-size: 13px;
  color: var(--color-ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.recent-tag {
  flex-shrink: 0;
  font-size: 11px;
  padding: 1px 8px;
  border-radius: 999px;
  background: var(--color-canvas-soft-2);
  color: var(--color-mute);
}
.recent-tag.draft { background: var(--color-canvas-soft-2); color: var(--color-mute); }
.recent-tag.published { background: var(--color-success-bg); color: #16a34a; }
.recent-tag.published_editing { background: var(--color-info-bg); color: #2563eb; }
.recent-tag.archived { background: var(--color-warn-bg-deep); color: #d97706; }

@media (max-width: 1200px) {
  .dashboard-grid { grid-template-columns: 1fr 1fr; }
  .stats-overview { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 768px) {
  .dashboard { padding: 16px; }
  .dashboard-grid,
  .stats-overview { grid-template-columns: 1fr; }
  .chart-box--trend { height: 240px; min-height: 240px; }
}
</style>
