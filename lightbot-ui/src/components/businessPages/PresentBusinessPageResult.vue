<template>
  <div class="pbp-result">
    <div v-if="isPlainText" class="pbp-plain"><pre>{{ displayText }}</pre></div>
    <div v-else-if="data?._error || data?.success === false" class="pbp-error">
      <CloseCircleOutlined />
      <span>{{ data.message || data.error || '业务页呈现失败' }}</span>
    </div>
    <template v-else>
      <a-drawer
        v-if="isDrawer"
        v-model:open="drawerOpen"
        :title="data.title || data.displayName || '业务办理'"
        placement="right"
        :width="440"
        :mask-closable="false"
        destroy-on-close
      >
        <component
          :is="pageComponent"
          :payload="data"
          :message-index="messageIndex"
          :workflow-mode="workflowMode"
          @workflow-submit="$emit('workflow-submit', $event)"
          @workflow-cancel="$emit('workflow-cancel', $event)"
        />
      </a-drawer>
      <div v-if="isDrawer" class="pbp-drawer-entry">
        <a-button type="primary" @click="drawerOpen = true">打开 {{ data.title || '业务办理页' }}</a-button>
      </div>
      <component
        v-else
        :is="pageComponent"
        :payload="data"
        :message-index="messageIndex"
        :workflow-mode="workflowMode"
        class="pbp-page"
        @workflow-submit="$emit('workflow-submit', $event)"
        @workflow-cancel="$emit('workflow-cancel', $event)"
      />
    </template>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { CloseCircleOutlined } from '@ant-design/icons-vue'
import { resolveBusinessPageComponent } from './businessPageRegistry'

const props = defineProps({
  event: { type: Object, required: true },
  messageIndex: { type: Number, default: -1 },
  workflowMode: { type: Boolean, default: false },
})
defineEmits(['workflow-submit', 'workflow-cancel'])

const rawResult = computed(() => props.event?.result || '')
const data = computed(() => {
  try {
    return JSON.parse(rawResult.value)
  } catch {
    return null
  }
})
const isPlainText = computed(() => !data.value || typeof data.value !== 'object')
const displayText = computed(() => (typeof data.value === 'string' ? data.value : rawResult.value))
const pageComponent = computed(() => resolveBusinessPageComponent(data.value?.pageType, data.value))
const isDrawer = computed(() => String(data.value?.mode || 'inline').toLowerCase() === 'drawer')
const drawerOpen = ref(false)

watch(isDrawer, (v) => {
  if (v) drawerOpen.value = true
}, { immediate: true })
</script>

<style scoped>
.pbp-result { width: 100%; }
.pbp-plain pre { margin: 0; white-space: pre-wrap; word-break: break-word; font-size: 12px; }
.pbp-error {
  display: flex; align-items: center; gap: 8px; padding: 12px 14px;
  border-radius: 10px; border: 1px solid #ffccc7; background: #fff2f0; color: #cf1322; font-size: 13px;
}
.pbp-page { margin-top: 2px; }
.pbp-drawer-entry { margin-top: 8px; }
</style>
