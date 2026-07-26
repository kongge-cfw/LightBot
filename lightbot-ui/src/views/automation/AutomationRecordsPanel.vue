<template>
  <div class="records-panel">
    <div class="panel-toolbar">
      <a-input
        v-model:value="keyword"
        allow-clear
        placeholder="搜索任务名称 / 指令..."
        style="width: 240px"
      >
        <template #prefix><SearchOutlined /></template>
      </a-input>
      <a-select
        v-model:value="statusFilter"
        allow-clear
        placeholder="执行状态"
        style="width: 140px"
        :options="statusOptions"
      />
      <button type="button" class="lb-btn" @click="refresh">
        <ReloadOutlined /> 刷新
      </button>
    </div>

    <div class="panel-table-wrap">
      <a-table
        :columns="columns"
        :data-source="filteredRecords"
        :pagination="pagination"
        row-key="id"
        size="middle"
        :scroll="{ x: 960 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-badge :status="statusBadge[record.status]" :text="statusLabel[record.status]" />
          </template>
          <template v-else-if="column.key === 'instruction'">
            <a-tooltip :title="record.instruction" placement="topLeft">
              <span class="cell-ellipsis">{{ record.instruction }}</span>
            </a-tooltip>
          </template>
          <template v-else-if="column.key === 'actions'">
            <button type="button" class="btn-link" @click="openDetail(record)">详情</button>
          </template>
        </template>
        <template #emptyText>
          <LbEmptyState
            :icon="ThunderboltOutlined"
            title="暂无执行记录"
            desc="配置定时任务后，每次触发会在此展示结果"
          />
        </template>
      </a-table>
    </div>

    <a-drawer
      v-model:open="detailOpen"
      title="执行详情"
      :width="440"
      destroy-on-close
    >
      <template v-if="detailRecord">
        <a-descriptions :column="1" bordered size="small">
          <a-descriptions-item label="任务名称">{{ detailRecord.taskName }}</a-descriptions-item>
          <a-descriptions-item label="智能体">{{ detailRecord.agentName }}</a-descriptions-item>
          <a-descriptions-item label="文字指令">{{ detailRecord.instruction }}</a-descriptions-item>
          <a-descriptions-item label="触发时间">{{ detailRecord.triggerTime }}</a-descriptions-item>
          <a-descriptions-item label="状态">
            <a-badge :status="statusBadge[detailRecord.status]" :text="statusLabel[detailRecord.status]" />
          </a-descriptions-item>
          <a-descriptions-item label="耗时">{{ detailRecord.duration || '—' }}</a-descriptions-item>
          <a-descriptions-item label="结果摘要">{{ detailRecord.summary || '—' }}</a-descriptions-item>
        </a-descriptions>
      </template>
    </a-drawer>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ReloadOutlined, SearchOutlined, ThunderboltOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import LbEmptyState from '../../components/common/LbEmptyState.vue'

/** 静态示例数据，后续对接真实 API */
const MOCK_RECORDS = [
  {
    id: '1',
    taskName: '每日早报汇总',
    agentName: '运营助手',
    instruction: '汇总昨日运营数据，生成简报',
    triggerTime: '2026-07-26 08:00:00',
    status: 'success',
    duration: '12.4s',
    summary: '已生成简报并发送至指定会话',
  },
  {
    id: '2',
    taskName: '知识库巡检',
    agentName: '知识库助手',
    instruction: '检查知识库文档索引状态，列出异常项',
    triggerTime: '2026-07-26 09:30:00',
    status: 'failed',
    duration: '3.1s',
    summary: '模型调用超时',
  },
  {
    id: '3',
    taskName: '每周周报',
    agentName: '运营助手',
    instruction: '生成本周工作周报草稿',
    triggerTime: '2026-07-25 18:00:00',
    status: 'running',
    duration: '—',
    summary: '正在执行…',
  },
]

const statusLabel = {
  success: '成功',
  failed: '失败',
  running: '执行中',
  pending: '等待中',
}
const statusBadge = {
  success: 'success',
  failed: 'error',
  running: 'processing',
  pending: 'default',
}
const statusOptions = [
  { value: 'success', label: '成功' },
  { value: 'failed', label: '失败' },
  { value: 'running', label: '执行中' },
  { value: 'pending', label: '等待中' },
]

const columns = [
  { title: '任务名称', dataIndex: 'taskName', key: 'taskName', width: 160, ellipsis: true },
  { title: '智能体', dataIndex: 'agentName', key: 'agentName', width: 120, ellipsis: true },
  { title: '文字指令', dataIndex: 'instruction', key: 'instruction', ellipsis: true },
  { title: '触发时间', dataIndex: 'triggerTime', key: 'triggerTime', width: 170 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 100 },
  { title: '耗时', dataIndex: 'duration', key: 'duration', width: 90 },
  { title: '操作', key: 'actions', width: 80, fixed: 'right' },
]

const records = ref([...MOCK_RECORDS])
const keyword = ref('')
const statusFilter = ref(undefined)
const detailOpen = ref(false)
const detailRecord = ref(null)
const pagination = reactive({
  current: 1,
  pageSize: 10,
  showSizeChanger: true,
  showTotal: (t) => `共 ${t} 条`,
})

const filteredRecords = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return records.value.filter((r) => {
    if (statusFilter.value && r.status !== statusFilter.value) return false
    if (!kw) return true
    return [r.taskName, r.instruction, r.agentName].some((v) => String(v || '').toLowerCase().includes(kw))
  })
})

function refresh() {
  message.success('已刷新（静态数据）')
}

function openDetail(record) {
  detailRecord.value = record
  detailOpen.value = true
}
</script>

<style scoped>
.records-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
  gap: 12px;
}
.panel-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}
.panel-table-wrap {
  flex: 1;
  min-height: 0;
  overflow: auto;
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
  border-radius: 12px;
  padding: 4px 8px 8px;
}
.cell-ellipsis {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}
.btn-link {
  border: none;
  background: none;
  padding: 0;
  color: var(--color-link);
  cursor: pointer;
  font-size: 13px;
}
.btn-link:hover {
  opacity: 0.8;
}
</style>
