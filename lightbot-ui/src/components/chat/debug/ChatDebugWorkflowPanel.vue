<template>
  <div class="debug-workflow-panel">
    <div class="debug-panel-toolbar">
      <a-select
        v-model:value="selectedSample"
        :options="sampleOptions"
        style="width: 200px"
        @change="loadSample"
      />
      <a-button type="primary" @click="$emit('parse')">解析预览</a-button>
      <a-button @click="handleFormat">格式化 JSON</a-button>
    </div>

    <div class="debug-workflow-builder">
      <div class="debug-editor-label">组合节点（多选后追加到 workflowEvents）</div>
      <a-select
        v-model:value="selectedNodeTypes"
        mode="multiple"
        :options="nodeTypeOptions"
        placeholder="选择节点类型组合样式"
        style="width: 100%"
        :max-tag-count="4"
      />
      <a-button size="small" style="margin-top: 8px" @click="appendCombinedNodes">追加组合节点</a-button>
      <a-button size="small" style="margin-top: 8px; margin-left: 8px" @click="replaceWithCombinedNodes">替换为组合节点</a-button>
    </div>

    <div class="debug-editor-label">工作流消息 JSON（content + metadata.workflowEvents）</div>
    <a-textarea
      v-model:value="localJson"
      :rows="18"
      class="debug-json-textarea"
      @change="onEdit"
    />
    <a-alert
      v-if="parseError"
      type="error"
      :message="parseError"
      show-icon
      closable
      class="debug-parse-error"
      @close="parseError = ''"
    />
    <div class="debug-workflow-hint">
      展开预览区节点可查看 outputs、message、durationMs 等返回信息；单节点样例用于逐个验证样式。
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { message } from 'ant-design-vue'
import { apiMessageToEditorJson, editorJsonToApiMessage } from '@/utils/chat/debug/debugMessageBuilder'
import {
  getWorkflowSampleMessage,
  getWorkflowSampleOptions,
  buildWorkflowMessageFromEvents,
  combineWorkflowNodeSamples,
  WORKFLOW_NODE_TYPE_OPTIONS,
  WORKFLOW_DEBUG_SAMPLES,
} from '@/utils/chat/debug/debugWorkflowSamples'

const emit = defineEmits(['parse', 'update:modelValue'])

const sampleOptions = getWorkflowSampleOptions()
const nodeTypeOptions = WORKFLOW_NODE_TYPE_OPTIONS
const selectedSample = ref(sampleOptions[0]?.value || 'full-pipeline')
const selectedNodeTypes = ref(['start', 'llm', 'retrieval', 'end'])
const localJson = ref('')
const parseError = ref('')

function loadSample(id = selectedSample.value) {
  const msg = getWorkflowSampleMessage(id)
  if (!msg) return
  localJson.value = apiMessageToEditorJson(msg)
  emit('update:modelValue', localJson.value)
}

function onEdit() {
  emit('update:modelValue', localJson.value)
}

function handleFormat() {
  parseError.value = ''
  try {
    const msg = editorJsonToApiMessage(localJson.value)
    localJson.value = apiMessageToEditorJson(msg)
    emit('update:modelValue', localJson.value)
  } catch (e) {
    parseError.value = e.message || 'JSON 格式错误'
  }
}

function getCurrentWorkflowEvents() {
  const msg = editorJsonToApiMessage(localJson.value)
  return msg.metadata?.workflowEvents || []
}

function applyWorkflowEvents(events, content) {
  localJson.value = buildWorkflowMessageFromEvents(events, content)
  emit('update:modelValue', localJson.value)
}

function appendCombinedNodes() {
  if (!selectedNodeTypes.value?.length) {
    message.warning('请至少选择一个节点类型')
    return
  }
  try {
    const existing = getCurrentWorkflowEvents()
    const combined = combineWorkflowNodeSamples(selectedNodeTypes.value)
    applyWorkflowEvents([...existing, ...combined])
    message.success('已追加组合节点')
  } catch (e) {
    parseError.value = e.message || '组合失败'
  }
}

function replaceWithCombinedNodes() {
  if (!selectedNodeTypes.value?.length) {
    message.warning('请至少选择一个节点类型')
    return
  }
  try {
    const combined = combineWorkflowNodeSamples(selectedNodeTypes.value)
    applyWorkflowEvents(combined)
    message.success('已替换为组合节点')
  } catch (e) {
    parseError.value = e.message || '组合失败'
  }
}

function validateAndGetMessage() {
  parseError.value = ''
  try {
    return editorJsonToApiMessage(localJson.value)
  } catch (e) {
    parseError.value = e.message || 'JSON 格式错误'
    return null
  }
}

loadSample(WORKFLOW_DEBUG_SAMPLES[0]?.id)

defineExpose({ validateAndGetMessage, loadSample, handleFormat })
</script>

<style scoped>
.debug-workflow-panel {
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

.debug-workflow-builder {
  margin-bottom: 12px;
  padding: 10px;
  border: 1px dashed var(--gray-200);
  border-radius: 6px;
  background: var(--color-canvas);
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

.debug-parse-error {
  margin-top: 8px;
}

.debug-workflow-hint {
  margin-top: 8px;
  font-size: 12px;
  color: var(--gray-500);
  line-height: 1.5;
}
</style>
