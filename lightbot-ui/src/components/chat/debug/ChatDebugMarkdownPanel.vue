<template>
  <div class="debug-markdown-panel">
    <div class="debug-panel-toolbar">
      <a-button type="primary" @click="$emit('parse')">解析预览</a-button>
      <a-button @click="handleClear">清空</a-button>
    </div>
    <div class="debug-editor-label">Markdown 源码</div>
    <a-textarea
      v-model:value="localSource"
      :rows="22"
      class="debug-md-textarea"
      placeholder="在此输入 Markdown..."
      @change="onEdit"
    />
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  modelValue: { type: String, default: '' },
})

const emit = defineEmits(['update:modelValue', 'parse'])

const DEFAULT_MD = `# Markdown 测试

普通文本 **加粗** *斜体*

- 列表 A
- 列表 B

列表后的普通段落不应缩进。`

const localSource = ref(props.modelValue || DEFAULT_MD)

watch(() => props.modelValue, (val) => {
  if (val != null && val !== localSource.value) {
    localSource.value = val
  }
})

function onEdit() {
  emit('update:modelValue', localSource.value)
}

function handleClear() {
  localSource.value = ''
  emit('update:modelValue', '')
}

defineExpose({
  getSource: () => localSource.value,
})
</script>

<style scoped>
.debug-markdown-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.debug-panel-toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.debug-editor-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--gray-700);
  margin-bottom: 8px;
}

.debug-md-textarea {
  flex: 1;
  font-family: 'Menlo', 'Monaco', 'Consolas', monospace;
  font-size: 13px;
  line-height: 1.6;
}
</style>
