<template>
  <div class="extensions-page">
    <div class="page-header">
      <div>
        <h1 class="page-title">扩展管理</h1>
        <p class="page-desc">管理 MCP 服务、Skill 技能、工具和 SubAgents</p>
      </div>
    </div>
    <div class="tab-toolbar">
      <a-tabs v-model:activeKey="activeTab" class="ext-tabs">
        <a-tab-pane key="mcp" tab="MCP Server" />
        <a-tab-pane key="skills" tab="Skill" />
        <a-tab-pane key="tools" tab="工具" />
        <a-tab-pane key="subagents" tab="SubAgents" />
      </a-tabs>
      <div class="toolbar-actions">
        <!-- 工具Tab时显示类型筛选 -->
        <a-select
          v-if="activeTab === 'tools'"
          v-model:value="toolTypeFilter"
          placeholder="工具类型"
          style="width: 120px"
        >
          <a-select-option value="all">全部</a-select-option>
          <a-select-option value="builtin">内置</a-select-option>
          <a-select-option value="knowledge">知识库</a-select-option>
          <a-select-option value="api">API调用</a-select-option>
        </a-select>
        <a-input
          v-model:value="searchText"
          :placeholder="searchPlaceholder"
          allow-clear
          style="width: 220px"
          @change="handleSearch"
        >
          <template #prefix>
            <LoadingOutlined v-if="currentLoading" />
            <SearchOutlined v-else />
          </template>
        </a-input>
        <button class="lb-btn" @click="handleRefresh">
          <ReloadOutlined :spin="currentLoading" /> 刷新
        </button>
        <button v-if="activeTab === 'skills'" class="lb-btn lb-btn--accent lb-btn--accent--mcp" @click="skillRef?.openImportModal()">
          <UploadOutlined /> ZIP 导入
        </button>
        <button v-if="activeTab === 'skills'" class="lb-btn lb-btn--accent lb-btn--accent--default" @click="skillRef?.openRemoteInstallModal()">
          <CloudDownloadOutlined /> 远程安装
        </button>
        <!-- 自动注入工具：工具Tab专属，放最右区域紧邻主按钮（与 Skill tab 强调色按钮位置一致）；
             tooltip 改 bottomLeft，避免按钮贴右边界时 tooltip 溢出屏幕 -->
        <DynamicToolDrawer v-if="activeTab === 'tools'" placement="bottomLeft" :show-status="false" :show-count="false" />
        <button class="lb-btn lb-btn--primary" @click="handleAdd">
          <PlusOutlined /> {{ addBtnText }}
        </button>
      </div>
    </div>
    <div class="tab-content scroll-area-y">
      <!-- v-if 懒加载：未激活的 tab 不挂载子组件，避免首次进入并发所有子组件的 onMounted 请求 -->
      <McpManage v-if="activeTab === 'mcp'" ref="mcpRef" hide-header />
      <SkillManage v-if="activeTab === 'skills'" ref="skillRef" hide-header />
      <ToolManage v-if="activeTab === 'tools'" ref="toolRef" hide-header />
      <SubAgentManage v-if="activeTab === 'subagents'" ref="subAgentRef" hide-header />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { PlusOutlined, SearchOutlined, UploadOutlined, CloudDownloadOutlined, ReloadOutlined, LoadingOutlined } from '@ant-design/icons-vue'
import McpManage from './McpManage.vue'
import SkillManage from './SkillManage.vue'
import ToolManage from './ToolManage.vue'
import SubAgentManage from './SubAgentManage.vue'
import DynamicToolDrawer from '../components/DynamicToolDrawer.vue'

const route = useRoute()
const router = useRouter()
const activeTab = ref(route.query.tab || 'mcp')
const searchText = ref('')
const toolTypeFilter = ref('all')
const mcpRef = ref(null)
const skillRef = ref(null)
const toolRef = ref(null)
const subAgentRef = ref(null)

const addBtnText = computed(() => {
  const map = { mcp: '新增 MCP Server', skills: '新增 Skill', tools: '新增工具', subagents: '新增 SubAgent' }
  return map[activeTab.value] || '新增'
})

const searchPlaceholder = computed(() => {
  const map = { mcp: '搜索 MCP Server...', skills: '搜索 Skill...', tools: '搜索工具...', subagents: '搜索 SubAgent...' }
  return map[activeTab.value] || '搜索...'
})

// 当前激活 tab 子组件的 loading 状态：刷新按钮 spin 与搜索框 loading 共用，让用户看到"操作已生效"
const currentLoading = computed(() => {
  const target = activeTab.value === 'mcp' ? mcpRef.value
    : activeTab.value === 'skills' ? skillRef.value
    : activeTab.value === 'tools' ? toolRef.value
    : subAgentRef.value
  return !!target?.loading
})

function handleAdd() {
  const target = activeTab.value === 'mcp' ? mcpRef.value
    : activeTab.value === 'skills' ? skillRef.value
    : activeTab.value === 'tools' ? toolRef.value
    : subAgentRef.value
  target?.openDialog()
}

function handleRefresh() {
  searchText.value = ''
  toolTypeFilter.value = 'all'
  const target = activeTab.value === 'mcp' ? mcpRef.value
    : activeTab.value === 'skills' ? skillRef.value
    : activeTab.value === 'tools' ? toolRef.value
    : subAgentRef.value
  target?.refresh()
}

function handleSearch() {
  const target = activeTab.value === 'mcp' ? mcpRef.value
    : activeTab.value === 'skills' ? skillRef.value
    : activeTab.value === 'tools' ? toolRef.value
    : subAgentRef.value
  // 传递搜索文本和工具类型（仅工具Tab）
  if (activeTab.value === 'tools') {
    const type = toolTypeFilter.value === 'all' ? undefined : toolTypeFilter.value
    target?.search(searchText.value, type)
  } else {
    target?.search(searchText.value)
  }
}

// 工具类型筛选变化时触发搜索
watch(toolTypeFilter, () => {
  if (activeTab.value === 'tools') {
    handleSearch()
  }
})

// 切换Tab时清空搜索、同步URL、刷新数据
watch(activeTab, (tab) => {
  router.replace({ query: { ...route.query, tab } })
  searchText.value = ''
  toolTypeFilter.value = 'all'
  const target = tab === 'mcp' ? mcpRef.value
    : tab === 'skills' ? skillRef.value
    : tab === 'tools' ? toolRef.value
    : subAgentRef.value
  target?.refresh()
})
</script>

<style scoped>
.extensions-page {
  height: 100vh;
  overflow: hidden;
  background: var(--color-canvas-soft);
  padding: 32px;
  display: flex;
  flex-direction: column;
}
.page-header {
  margin-bottom: 20px;
  flex-shrink: 0;
}
.page-title {
  font-size: 24px;
  font-weight: 600;
  color: var(--color-ink);
  margin: 0 0 4px;
}
.page-desc {
  font-size: 14px;
  color: var(--color-mute);
  margin: 0;
}
.tab-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  flex-shrink: 0;
}
.ext-tabs {
  flex: 1;
}
.ext-tabs :deep(.ant-tabs-nav) {
  margin-bottom: 0;
}
.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}
.tab-content {
  flex: 1;
  min-height: 0;
}
.tab-content :deep(.page) {
  height: auto;
  overflow: visible;
  padding: 0;
  background: transparent;
}
</style>
