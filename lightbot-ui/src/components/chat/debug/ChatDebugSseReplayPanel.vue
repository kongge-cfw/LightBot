<template>
  <div class="debug-sse-panel">
    <div class="debug-panel-toolbar">
      <a-button type="primary" @click="replay">回放 SSE</a-button>
      <a-select v-model:value="selectedSample" style="width: 190px" @change="loadSample">
        <a-select-option v-for="sample in samples" :key="sample.id" :value="sample.id">{{ sample.label }}</a-select-option>
      </a-select>
      <a-button @click="loadSample">加载所选样例</a-button>
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

const SSE_SAMPLES = [
  {
    id: 'tool',
    label: '工具调用 + 正文',
    content: `data:[STATUS]{"type":"reasoning_content","content":"正在分析..."}
data:[STATUS]{"type":"tool_call","toolName":"query_knowledge","displayName":"知识库检索","args":"{\\"query\\":\\"LightBot\\"}","contentOffset":0}
data:[STATUS]{"type":"tool_result","toolName":"query_knowledge","displayName":"知识库检索","contentOffset":0,"result":"{\\"total\\":1,\\"results\\":[{\\"content\\":\\"LightBot 是 AI Agent 平台\\"}]}"}
data:根据知识库，
data:LightBot 支持 RAG 与工作流。`,
  },
  {
    id: 'subagent',
    label: 'SubAgent 批次流式',
    content: `data:[STATUS]{"type":"subagent_batch_start","batch_id":"debug-batch-001","mode":"parallel","aggregation":"return_all","delegationIndex":0,"contentOffset":0,"tasks":[{"task_index":0,"task_id":"debug-task-001","subagent_name":"research-agent","task":"用 Markdown 总结 LightBot 模块"}]}
data:[STATUS]{"type":"subagent_task_start","batch_id":"debug-batch-001","task_id":"debug-task-001","task_index":0,"subagentName":"research-agent","status":"running","contentOffset":0,"delegationIndex":0}
data:[STATUS]{"type":"subagent_token","batch_id":"debug-batch-001","task_id":"debug-task-001","task_index":0,"subagentName":"research-agent","content":"## 核心模块\\n\\n- Agent\\n- RAG\\n- Workflow","contentOffset":0,"delegationIndex":0}
data:[STATUS]{"type":"subagent_task_done","batch_id":"debug-batch-001","task_id":"debug-task-001","task_index":0,"subagentName":"research-agent","status":"completed","contentOffset":0,"delegationIndex":0,"result":{"status":"completed","reply":"## 核心模块\\n\\n- Agent\\n- RAG\\n- Workflow"}}
data:[STATUS]{"type":"subagent_batch_done","batch_id":"debug-batch-001","status":"completed","contentOffset":0,"delegationIndex":0}`,
  },
  {
    id: 'skill',
    label: 'Skill 启用',
    content: `data:[STATUS]{"type":"skill_active","contentOffset":0,"skills":[{"slug":"code-review","name":"code-review","displayName":"代码审查","description":"审查代码质量","builtin":true}]}
data:已启用代码审查 Skill。`,
  },
  {
    id: 'workflow',
    label: '工作流节点',
    content: `data:[STATUS]{"type":"workflow_node_start","nodeId":"debug-node-1","nodeType":"llm","nodeLabel":"需求分析","contentOffset":0}
data:[STATUS]{"type":"workflow_node_complete","nodeId":"debug-node-1","nodeType":"llm","nodeLabel":"需求分析","message":"执行完成","success":true,"contentOffset":0}
data:[STATUS]{"type":"workflow_complete","contentOffset":0}`,
  },
]

const samples = SSE_SAMPLES
const selectedSample = ref('tool')

function loadSample() {
  sseLog.value = SSE_SAMPLES.find(sample => sample.id === selectedSample.value)?.content || ''
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
