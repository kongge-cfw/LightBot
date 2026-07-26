<template>
  <div class="data-center-page" :class="{ 'data-center-page--designer': designerOpen }">
    <LbPageTabsHeader
      v-show="!designerOpen"
      title="数据中心"
      :tabs="tabs"
      :active-key="activeTab"
      @update:active-key="onTabChange"
    />
    <div class="tab-content">
      <DataPoolPanel v-if="activeTab === 'pool' && !designerOpen" />
      <DataModelPanel
        v-if="activeTab === 'model' || designerOpen"
        @update:designer-open="designerOpen = $event"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import LbPageTabsHeader from '../components/common/LbPageTabsHeader.vue'
import DataPoolPanel from './data-center/DataPoolPanel.vue'
import DataModelPanel from './data-center/DataModelPanel.vue'

const VALID_TABS = ['pool', 'model']
const tabs = [
  { key: 'pool', label: '数据池' },
  { key: 'model', label: '数据模型' },
]

const route = useRoute()
const router = useRouter()
const activeTab = ref(VALID_TABS.includes(route.query.tab) ? route.query.tab : 'pool')
const designerOpen = ref(false)

function onTabChange(key) {
  activeTab.value = key
  router.replace({ query: { ...route.query, tab: key } })
}

watch(
  () => route.query.tab,
  (tab) => {
    if (VALID_TABS.includes(tab) && tab !== activeTab.value) {
      activeTab.value = tab
    }
  },
)
</script>

<style scoped>
.data-center-page {
  height: 100%;
  overflow: hidden;
  background: var(--color-canvas-soft);
  padding: 28px 32px;
  display: flex;
  flex-direction: column;
}
/* 配置表单时铺满整页内容区（覆盖标题与 Tab） */
.data-center-page--designer {
  padding: 20px 24px;
}
.tab-content {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.tab-content > * {
  flex: 1;
  min-height: 0;
}
</style>
