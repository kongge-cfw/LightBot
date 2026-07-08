<template>
  <div class="debug-ui-state-bar">
    <span class="debug-ui-state-label">UI 态</span>
    <a-checkbox
      :checked="localState.streaming"
      @change="(e) => patch({ streaming: e.target.checked })"
    >
      streaming
    </a-checkbox>
    <a-checkbox
      :checked="localState.reasoningExpanded"
      @change="(e) => patch({ reasoningExpanded: e.target.checked })"
    >
      reasoningExpanded
    </a-checkbox>
    <a-checkbox
      :checked="localState.reasoningDone"
      :disabled="localState.streaming"
      @change="(e) => patch({ reasoningDone: e.target.checked })"
    >
      reasoningDone
    </a-checkbox>
    <a-checkbox
      :checked="localState.toolsDone"
      :disabled="localState.streaming"
      @change="(e) => patch({ toolsDone: e.target.checked })"
    >
      toolsDone
    </a-checkbox>
    <a-checkbox
      :checked="localState.toolExpanded"
      @change="(e) => patch({ toolExpanded: e.target.checked })"
    >
      toolExpanded
    </a-checkbox>
    <a-checkbox
      :checked="localState.refsSectionExpanded"
      @change="(e) => patch({ refsSectionExpanded: e.target.checked })"
    >
      refsExpanded
    </a-checkbox>
  </div>
</template>

<script setup>
import { reactive, watch } from 'vue'
import { DEFAULT_DEBUG_UI_STATE, normalizeDebugUiState } from '@/utils/chat/debug/debugUiState'

const props = defineProps({
  modelValue: { type: Object, default: () => ({ ...DEFAULT_DEBUG_UI_STATE }) },
})

const emit = defineEmits(['update:modelValue'])

const localState = reactive(normalizeDebugUiState(props.modelValue))

watch(() => props.modelValue, (val) => {
  Object.assign(localState, normalizeDebugUiState(val))
}, { deep: true })

function patch(partial) {
  Object.assign(localState, partial)
  if (partial.streaming === true) {
    localState.toolsDone = false
    localState.reasoningDone = false
  }
  emit('update:modelValue', { ...localState })
}
</script>

<style scoped>
.debug-ui-state-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 12px;
  padding: 8px 10px;
  margin-bottom: 12px;
  border: 1px dashed var(--gray-200);
  border-radius: 6px;
  background: var(--color-canvas);
}

.debug-ui-state-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--gray-600);
  margin-right: 4px;
}
</style>
