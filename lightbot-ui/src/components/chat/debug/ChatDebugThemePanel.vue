<template>
  <div class="debug-theme-panel">
    <div class="debug-panel-toolbar">
      <a-button type="primary" @click="$emit('parse')">解析预览</a-button>
      <a-button @click="toggleTheme">
        切换{{ isDark ? '浅色' : '深色' }}模式
      </a-button>
      <a-select
        v-model:value="selectedPresetId"
        :options="presetOptions"
        style="width: 180px"
        @change="loadPreset"
      />
    </div>
    <div class="debug-theme-hint">
      当前主题：<strong>{{ isDark ? '深色' : '浅色' }}</strong>。预览使用与 Chat 相同的 CSS 变量与组件，可检查工具块 / Markdown 在两种主题下的样式。
    </div>
    <div class="debug-editor-label">样例消息 JSON</div>
    <a-textarea
      v-model:value="localJson"
      :rows="16"
      class="debug-json-textarea"
    />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useTheme } from '@/composables/useTheme'
import {
  apiMessageToEditorJson,
  editorJsonToApiMessage,
} from '@/utils/chat/debug/debugMessageBuilder'
import { DEBUG_PRESETS, getPresetById } from '@/utils/chat/debug/debugPresets'

const emit = defineEmits(['parse'])

const { isDark, toggleTheme } = useTheme()
const presetOptions = DEBUG_PRESETS.map((p) => ({ value: p.id, label: p.label }))
const selectedPresetId = ref('multi-tools')
const localJson = ref('')

function loadPreset(id = selectedPresetId.value) {
  const msg = getPresetById(id)
  if (!msg) return
  localJson.value = apiMessageToEditorJson(msg)
}

function validateAndGetMessage() {
  try {
    return editorJsonToApiMessage(localJson.value)
  } catch {
    return null
  }
}

loadPreset('multi-tools')

defineExpose({ validateAndGetMessage })
</script>

<style scoped>
.debug-theme-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.debug-panel-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.debug-theme-hint {
  font-size: 13px;
  color: var(--gray-600);
  margin-bottom: 12px;
  line-height: 1.5;
}

.debug-editor-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--gray-700);
  margin-bottom: 8px;
}

.debug-json-textarea {
  flex: 1;
  font-family: 'Menlo', 'Monaco', 'Consolas', monospace;
  font-size: 12px;
  line-height: 1.6;
}
</style>
