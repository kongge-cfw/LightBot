<template>
  <div class="wf-step-detail">
    <div v-if="step.status === 'failed'" class="wf-detail-msg fail">{{ step.message || '执行失败' }}</div>
    <div v-else-if="step.status === 'suspended' && step.nodeType !== 'confirm'" class="wf-detail-msg suspended">{{ step.message || '等待处理' }}</div>

    <!-- 开始：用户输入预览 -->
    <template v-if="step.nodeType === 'start'">
      <div v-if="userInputText" class="wf-detail-pill start">用户输入 · {{ userInputText.length }} 字</div>
      <div v-if="userInputText" class="wf-detail-pre muted">{{ truncateText(userInputText, 400) }}</div>
      <div v-else class="wf-detail-hint">流程已启动</div>
      <div v-if="userInputText" class="wf-detail-hint">完整内容已在上方用户消息中展示</div>
    </template>

    <!-- 结束：结果预览 -->
    <template v-else-if="step.nodeType === 'end'">
      <div class="wf-detail-pill end">流程结束</div>
      <div v-if="endResultText" class="wf-detail-pre">{{ truncateText(endResultText, 600) }}</div>
      <div v-else class="wf-detail-hint">无独立结果字段，请查看回复正文</div>
    </template>

    <!-- 流程输入 -->
    <template v-else-if="step.nodeType === 'input'">
      <div v-if="assignmentRows.length" class="wf-param-table">
        <div class="wf-detail-kv-title">写入变量</div>
        <div v-for="row in assignmentRows" :key="row.key" class="wf-param-row">
          <span class="wf-param-key">{{ row.key }}</span>
          <span class="wf-param-value">{{ row.value }}</span>
        </div>
      </div>
      <div v-else class="wf-detail-hint">未写入新变量</div>
    </template>

    <!-- 流程输出 -->
    <template v-else-if="step.nodeType === 'output'">
      <div class="wf-detail-pill output">已合并至回复</div>
      <div v-if="outputText" class="wf-detail-meta">模板渲染 {{ outputText.length }} 字</div>
      <div class="wf-detail-hint">正文已在消息区展示，此处不重复全文</div>
    </template>

    <!-- 变量赋值 -->
    <template v-else-if="step.nodeType === 'variable'">
      <div v-if="assignmentRows.length" class="wf-param-table variable">
        <div class="wf-detail-kv-title">变量赋值</div>
        <div v-for="row in assignmentRows" :key="row.key" class="wf-param-row">
          <span class="wf-param-key">{{ row.key }}</span>
          <span class="wf-param-value mono">{{ row.value }}</span>
        </div>
      </div>
    </template>

    <!-- 变量处理 -->
    <template v-else-if="step.nodeType === 'variable_handle'">
      <div v-if="hasKvData(variableHandleOutputs)" class="wf-detail-kv variable-handle">
        <div class="wf-detail-kv-title">处理结果</div>
        <pre>{{ formatKv(variableHandleOutputs) }}</pre>
      </div>
      <div v-else class="wf-detail-hint">无输出变量</div>
    </template>

    <!-- LLM -->
    <template v-else-if="step.nodeType === 'llm'">
      <div v-if="tokenSummary" class="wf-detail-pill">{{ tokenSummary }}</div>
      <div v-if="step.traceData?.llmMessages?.length" class="wf-detail-kv">
        <div class="wf-detail-kv-title">Prompt 快照</div>
        <pre>{{ formatMessages(step.traceData.llmMessages) }}</pre>
      </div>
      <div v-else class="wf-detail-hint">正文已在回复中流式展示</div>
    </template>

    <!-- 知识检索 -->
    <template v-else-if="step.nodeType === 'retrieval'">
      <div v-if="retrievalChunks.length" class="wf-chunk-list">
        <div v-for="(c, i) in retrievalChunks" :key="i" class="wf-chunk-card">
          <div class="wf-chunk-head">
            <span class="wf-chunk-idx">#{{ i + 1 }}</span>
            <span v-if="c.score != null" class="wf-chunk-score">{{ formatScore(c.score) }}</span>
          </div>
          <div class="wf-chunk-text">{{ truncateText(c.content, 280) }}</div>
        </div>
      </div>
      <div v-else-if="outputs.retrievalResult" class="wf-detail-pre">{{ truncateText(outputs.retrievalResult, 600) }}</div>
      <div v-else class="wf-detail-hint">未命中相关内容</div>
    </template>

    <!-- 工具：复用 Chat toolRegistry 渲染 -->
    <template v-else-if="step.nodeType === 'tool'">
      <div v-if="workflowToolRenderable" class="wf-tool-registry-result">
        <ToolCallRenderer :event="workflowToolEvent" :message-index="-1" />
      </div>
      <div v-else-if="toolRawResult" class="wf-detail-pre">{{ truncateText(toolRawResult, 800) }}</div>
      <div v-else class="wf-detail-hint">无工具返回结果</div>
    </template>

    <!-- MCP -->
    <template v-else-if="step.nodeType === 'mcp'">
      <ToolCallRenderer v-if="mcpPseudoEvent.toolName" :event="mcpPseudoEvent" />
      <div v-else-if="toolRawResult" class="wf-detail-pre">{{ truncateText(toolRawResult, 800) }}</div>
    </template>

    <!-- 条件 / 分类 -->
    <template v-else-if="step.nodeType === 'condition'">
      <div class="wf-detail-pill condition">命中分支：{{ outputs.matchedGroupLabel || handleLabel(outputs.matchedHandle) || '—' }}</div>
      <div v-if="step.nextNodeId" class="wf-detail-meta">下一节点：{{ step.nextNodeId }}</div>
    </template>
    <template v-else-if="step.nodeType === 'classifier'">
      <div class="wf-detail-pill intent">意图：{{ outputs.subject || '—' }}</div>
      <div v-if="outputs.thought" class="wf-detail-pre thought">{{ outputs.thought }}</div>
      <div v-if="tokenSummary" class="wf-detail-meta">{{ tokenSummary }}</div>
    </template>

    <!-- API -->
    <template v-else-if="step.nodeType === 'api'">
      <div class="wf-detail-pill api">HTTP {{ outputs.statusCode ?? '—' }}</div>
      <div v-if="outputs.body" class="wf-detail-pre">{{ truncateText(outputs.body, 800) }}</div>
    </template>

    <!-- 脚本 -->
    <template v-else-if="step.nodeType === 'script'">
      <div v-if="hasKvData(filteredOutputs)" class="wf-detail-kv script">
        <div class="wf-detail-kv-title">脚本出参</div>
        <pre>{{ formatKv(filteredOutputs) }}</pre>
      </div>
      <div v-else class="wf-detail-hint">脚本无返回字段</div>
    </template>

    <!-- 参数提取 -->
    <template v-else-if="step.nodeType === 'parameter_extractor'">
      <div v-if="extractRows.length" class="wf-param-table extractor">
        <div class="wf-detail-kv-title">提取参数</div>
        <div v-for="row in extractRows" :key="row.key" class="wf-param-row">
          <span class="wf-param-key">{{ row.key }}</span>
          <span class="wf-param-value mono">{{ row.value }}</span>
        </div>
      </div>
      <details v-if="outputs.extractRaw" class="wf-raw-fold">
        <summary>原始 JSON</summary>
        <pre>{{ truncateText(outputs.extractRaw, 1200) }}</pre>
      </details>
    </template>

    <!-- 子工作流 / 容器 -->
    <template v-else-if="step.nodeType === 'app_component'">
      <div v-if="outputs.componentName" class="wf-detail-pill app">子流程 · {{ outputs.componentName }}</div>
      <div v-else-if="outputs.result" class="wf-detail-pre">{{ truncateText(outputs.result, 600) }}</div>
      <div v-if="step.children?.length" class="wf-detail-hint">展开上方子节点查看 {{ step.children.length }} 个内部步骤</div>
    </template>

    <!-- 用户交互 confirm -->
    <template v-else-if="step.nodeType === 'confirm'">
      <div v-if="step.status === 'suspended'" class="wf-detail-msg suspended">请在下方表单中完成选择</div>
      <div v-else-if="hasKvData(filteredOutputs)" class="wf-detail-kv confirm">
        <div class="wf-detail-kv-title">用户提交</div>
        <pre>{{ formatKv(filteredOutputs) }}</pre>
      </div>
    </template>

    <!-- 循环 / 批处理容器 -->
    <template v-else-if="step.nodeType === 'loop' || step.nodeType === 'batch'">
      <div class="wf-detail-pill">{{ step.nodeType === 'loop' ? '循环容器' : '批处理容器' }}</div>
      <div v-if="step.children?.length" class="wf-detail-meta">共 {{ step.children.length }} 个内部步骤</div>
      <div v-else class="wf-detail-hint">无子步骤记录</div>
    </template>

    <!-- 通用兜底 -->
    <template v-else>
      <div v-if="step.message && step.status === 'done'" class="wf-detail-msg">{{ step.message }}</div>
      <div v-if="showGenericDetail" class="wf-detail-pre">{{ truncateText(step.detail, 800) }}</div>
      <div v-if="hasKvData(filteredOutputs)" class="wf-detail-kv">
        <div class="wf-detail-kv-title">出参</div>
        <pre>{{ formatKv(filteredOutputs) }}</pre>
      </div>
    </template>

    <div v-if="hasKvData(step.input) && !['start', 'input'].includes(step.nodeType)" class="wf-detail-kv muted">
      <div class="wf-detail-kv-title">入参</div>
      <pre>{{ formatKv(step.input) }}</pre>
    </div>
    <div v-if="step.nextNodeId && !['condition', 'classifier', 'end'].includes(step.nodeType)" class="wf-detail-meta">
      下一节点：{{ step.nextNodeId }}
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import ToolCallRenderer from '../ToolCallRenderer.vue'
import { HIDE_DETAIL_BODY_TYPES } from './workflowNodeRegistry.js'
import {
  parseStepOutputs, formatKv, hasKvData, truncateText,
  buildWorkflowToolEvent, buildToolPseudoEvent, canRenderWorkflowTool,
  extractUserInputText, buildAssignmentRows, listOutputKeys, previewValue,
} from './workflowStepUtils.js'

const props = defineProps({
  step: { type: Object, required: true },
})

const outputs = computed(() => parseStepOutputs(props.step?.outputs) || {})

const filteredOutputs = computed(() => {
  const o = { ...outputs.value }
  delete o.llmOutput
  delete o.toolResultText
  if (props.step?.nodeType === 'parameter_extractor') delete o.extractRaw
  return o
})

const userInputText = computed(() => extractUserInputText(props.step))

const endResultText = computed(() => {
  const r = outputs.value.result
  return r != null ? String(r) : ''
})

const outputText = computed(() => {
  const o = outputs.value.output
  return o != null ? String(o) : ''
})

const assignmentRows = computed(() => buildAssignmentRows(outputs.value))

const variableHandleOutputs = computed(() => filteredOutputs.value)

const extractRows = computed(() => {
  const keys = listOutputKeys(outputs.value)
  return keys.map(key => ({ key, value: previewValue(outputs.value[key], 160) }))
})

const tokenSummary = computed(() => {
  const td = props.step?.traceData
  if (!td) return ''
  const inTok = td.inputTokens
  const outTok = td.outputTokens
  if (inTok == null && outTok == null) return ''
  return `Token ${inTok ?? 0} → ${outTok ?? 0}`
})

const retrievalChunks = computed(() => {
  const chunks = outputs.value.retrievalChunks
  return Array.isArray(chunks) ? chunks : []
})

const workflowToolEvent = computed(() => buildWorkflowToolEvent(props.step))
const workflowToolRenderable = computed(() => canRenderWorkflowTool(workflowToolEvent.value))
const mcpPseudoEvent = computed(() => buildToolPseudoEvent(props.step))
const toolRawResult = computed(() => {
  const raw = outputs.value.output ?? outputs.value.mcpResult ?? outputs.value.toolResultText
  return raw != null ? String(raw) : ''
})

const showGenericDetail = computed(() => {
  return props.step?.detail
    && String(props.step.detail).trim()
    && !HIDE_DETAIL_BODY_TYPES.has(props.step?.nodeType)
})

function handleLabel(handle) {
  const map = { out_a: '分支 A', out_b: '分支 B', out_c: '否则' }
  return map[handle] || handle
}

function formatScore(score) {
  const n = Number(score)
  if (Number.isNaN(n)) return ''
  return n <= 1 ? `${Math.round(n * 100)}%` : String(n)
}

function formatMessages(msgs) {
  if (!Array.isArray(msgs)) return ''
  return msgs.map(m => `[${m.role}] ${m.content}`).join('\n\n')
}
</script>

<style scoped>
.wf-step-detail { font-size: 12px; }
.wf-detail-msg { margin-top: 4px; color: var(--color-mute); }
.wf-detail-msg.fail { color: #dc2626; }
.wf-detail-msg.suspended { color: #f97316; font-weight: 500; }
.wf-detail-hint { color: var(--color-mute); font-style: italic; margin-top: 4px; }
.wf-detail-pill {
  display: inline-block; margin-top: 4px; padding: 2px 8px; border-radius: 999px;
  background: #faf5ff; color: #6d28d9; font-size: 11px;
}
.wf-detail-pill.start { background: #f0fdf4; color: #15803d; }
.wf-detail-pill.end { background: #fef2f2; color: #b91c1c; }
.wf-detail-pill.output { background: #ecfeff; color: #0e7490; }
.wf-detail-pill.condition { background: #fffbeb; color: #b45309; }
.wf-detail-pill.api { background: #f0f9ff; color: #0369a1; }
.wf-detail-pill.app { background: #eff6ff; color: #1d4ed8; }
.wf-detail-pill.intent { background: #fff7ed; color: #c2410c; }
.wf-detail-pre {
  margin-top: 6px; padding: 8px 10px; background: var(--color-canvas-soft);
  border: 1px solid var(--color-border-slate); border-radius: 6px;
  white-space: pre-wrap; word-break: break-word; max-height: 220px; overflow: auto;
  line-height: 1.55;
}
.wf-detail-pre.muted { background: var(--color-canvas); color: var(--color-mute); }
.wf-detail-pre.thought { max-height: 120px; }
.wf-detail-kv {
  margin-top: 6px; padding: 8px; border-radius: 6px;
  border: 1px solid #ede9fe; background: #faf5ff;
}
.wf-detail-kv.variable-handle { border-color: #fbcfe8; background: #fdf2f8; }
.wf-detail-kv.variable-handle .wf-detail-kv-title { color: #be185d; }
.wf-detail-kv.script { border-color: #e2e8f0; background: #f8fafc; }
.wf-detail-kv.script .wf-detail-kv-title { color: #475569; }
.wf-detail-kv.confirm { border-color: #fed7aa; background: #fff7ed; }
.wf-detail-kv.confirm .wf-detail-kv-title { color: #c2410c; }
.wf-detail-kv.muted { border-color: var(--color-border-slate); background: var(--color-canvas-soft); }
.wf-detail-kv-title { margin-bottom: 4px; font-weight: 600; color: #6d28d9; font-size: 11px; }
.wf-detail-kv pre { margin: 0; white-space: pre-wrap; word-break: break-word; font-family: ui-monospace, monospace; line-height: 1.45; }
.wf-detail-meta { margin-top: 6px; font-size: 11px; color: var(--color-mute); font-family: ui-monospace, monospace; }
.wf-param-table {
  margin-top: 6px; border: 1px solid var(--color-border-slate); border-radius: 6px; overflow: hidden;
}
.wf-param-table.variable { border-color: #fbcfe8; }
.wf-param-table.extractor { border-color: #fecdd3; }
.wf-param-row {
  display: flex; gap: 8px; padding: 6px 10px; border-top: 1px solid var(--color-border-slate);
  font-size: 12px; line-height: 1.45;
}
.wf-param-row:first-of-type { border-top: none; }
.wf-param-key {
  flex-shrink: 0; min-width: 72px; font-weight: 600; color: #0d9488; font-family: ui-monospace, monospace;
}
.wf-param-table.variable .wf-param-key { color: #db2777; }
.wf-param-table.extractor .wf-param-key { color: #e11d48; }
.wf-param-value { flex: 1; word-break: break-word; color: var(--color-text-dark); }
.wf-param-value.mono { font-family: ui-monospace, monospace; font-size: 11px; }
.wf-raw-fold { margin-top: 8px; font-size: 11px; color: var(--color-mute); }
.wf-raw-fold pre {
  margin-top: 4px; padding: 8px; background: var(--color-canvas-soft); border-radius: 6px;
  max-height: 160px; overflow: auto; white-space: pre-wrap;
}
.wf-chunk-list { display: flex; flex-direction: column; gap: 6px; margin-top: 6px; }
.wf-chunk-card { padding: 8px; border: 1px solid #e9d5ff; border-radius: 6px; background: #fff; }
.wf-chunk-head { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
.wf-chunk-idx { font-size: 11px; color: #7c3aed; font-weight: 600; }
.wf-chunk-score { font-size: 10px; color: var(--color-mute); }
.wf-chunk-text { font-size: 12px; line-height: 1.5; color: var(--color-text-dark); }
.wf-tool-registry-result {
  margin-top: 4px;
  padding: 4px 0;
  max-width: 100%;
  overflow: hidden;
}
.wf-tool-registry-result :deep(.web-search-result),
.wf-tool-registry-result :deep(.deliver-file-result),
.wf-tool-registry-result :deep(.query-knowledge-result) {
  max-width: 100%;
}
</style>
