<template>
  <div class="debug-sse-panel">
    <div class="debug-panel-toolbar">
      <a-button type="primary" @click="replay">回放 SSE</a-button>
      <a-button @click="loadSample">加载样例</a-button>
      <a-button @click="clearAll">清空</a-button>
    </div>
    <div class="debug-sse-grid">
      <div class="debug-sse-col">
        <div class="debug-editor-label">SSE 原始日志</div>
        <a-textarea v-model:value="sseLog" :rows="14" class="debug-json-textarea" placeholder="粘贴 data: 行..." />
      </div>
      <div class="debug-sse-col">
        <div class="debug-editor-label">基础消息 JSON（可选，回放会合并到其上）</div>
        <a-textarea v-model:value="baseJson" :rows="14" class="debug-json-textarea" />
      </div>
    </div>
    <a-alert v-if="parseError" type="error" :message="parseError" show-icon closable class="debug-parse-error" @close="parseError = ''" />
    <div class="debug-sse-hint">支持 data:[STATUS]{...}、data:[METADATA]{...} 与普通 data: 正文 chunk。</div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { message } from 'ant-design-vue'
import {
  apiMessageToEditorJson,
  createDefaultApiMessage,
  editorJsonToApiMessage,
} from '@/utils/chat/debug/debugMessageBuilder'
import { parseSseDebugLog, buildApiMessageFromSse } from '@/utils/chat/debug/debugSseReplay'

const emit = defineEmits(['preview'])

const sseLog = ref('')
const baseJson = ref(apiMessageToEditorJson(createDefaultApiMessage()))
const parseError = ref('')

const SAMPLE_SSE = `data:[STATUS]{"type":"reasoning_content","content":"正在分析..."}
data:[STATUS]{"type":"tool_call","toolName":"query_knowledge","displayName":"知识库检索","args":"{\\"query\\":\\"LightBot\\"}","contentOffset":0}
data:[STATUS]{"type":"tool_result","toolName":"query_knowledge","displayName":"知识库检索","contentOffset":0,"result":"{\\"total\\":1,\\"results\\":[{\\"content\\":\\"LightBot 是 AI Agent 平台\\"}]}"}
data:根据知识库，
data:LightBot 支持 RAG 与工作流。`

function loadSample() {
  sseLog.value = SAMPLE_SSE
  baseJson.value = apiMessageToEditorJson(createDefaultApiMessage())
}

function clearAll() {
  sseLog.value = ''
  emit('preview', null)
}

function replay() {
  parseError.value = ''
  try {
    const base = editorJsonToApiMessage(baseJson.value)
    const parsed = parseSseDebugLog(sseLog.value)
    const merged = buildApiMessageFromSse(base, parsed)
    emit('preview', merged)
    message.success(`已解析 ${parsed.statusEvents.length} 个 STATUS 事件，${parsed.chunks.length} 个正文 chunk`)
  } catch (e) {
    parseError.value = e.message || '回放失败'
    emit('preview', null)
  }
}
</script>

<style scoped>
.debug-sse-panel {
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

.debug-sse-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  flex: 1;
  min-height: 0;
}

.debug-sse-col {
  display: flex;
  flex-direction: column;
  min-height: 0;
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

.debug-sse-hint {
  margin-top: 8px;
  font-size: 12px;
  color: var(--gray-500);
}

@media (max-width: 960px) {
  .debug-sse-grid {
    grid-template-columns: 1fr;
  }
}
</style>
