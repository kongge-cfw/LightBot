<template>
  <a-modal
    :open="open"
    title="Debug 控制台"
    width="480px"
    :footer="null"
    @update:open="$emit('update:open', $event)"
  >
    <div class="debug-panel-desc">
      开发调试工具。按 {{ debugShortcut }} 可退出 Debug 模式。
    </div>
    <div class="debug-actions">
      <button type="button" class="debug-action-btn debug-action-primary" @click="openDebugLab">
        <ExperimentOutlined class="debug-action-icon" />
        <span class="debug-action-text">
          <strong>打开 Chat Debug Lab</strong>
          <small>全屏调试：消息组合、工具渲染、Markdown 测试</small>
        </span>
      </button>
    </div>
  </a-modal>
</template>

<script setup>
import { ExperimentOutlined } from '@ant-design/icons-vue'
import { useRouter } from 'vue-router'
import { CHAT_DEBUG_SHORTCUT } from '@/composables/chat'

defineProps({
  open: { type: Boolean, default: false },
})

const emit = defineEmits(['update:open'])

const router = useRouter()
const debugShortcut = CHAT_DEBUG_SHORTCUT

function openDebugLab() {
  emit('update:open', false)
  router.push({ name: 'ChatDebugLab', query: { tab: 'composer' } })
}
</script>

<style scoped>
.debug-panel-desc {
  font-size: 13px;
  color: var(--gray-600);
  margin-bottom: 16px;
}

.debug-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.debug-action-btn {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  width: 100%;
  padding: 12px 14px;
  border: 1px solid var(--gray-100);
  border-radius: 8px;
  background: var(--color-canvas-soft);
  cursor: pointer;
  text-align: left;
  transition: border-color 0.15s, background 0.15s;
}

.debug-action-btn:hover {
  border-color: var(--main-700);
  background: var(--color-canvas);
}

.debug-action-primary {
  border-color: rgba(217, 119, 6, 0.35);
  background: rgba(245, 158, 11, 0.08);
}

.debug-action-icon {
  font-size: 20px;
  color: var(--main-700);
  margin-top: 2px;
}

.debug-action-text {
  display: flex;
  flex-direction: column;
  gap: 4px;
  color: var(--gray-800);
}

.debug-action-text strong {
  font-size: 14px;
}

.debug-action-text small {
  font-size: 12px;
  color: var(--gray-500);
  font-weight: 400;
  line-height: 1.5;
}
</style>
