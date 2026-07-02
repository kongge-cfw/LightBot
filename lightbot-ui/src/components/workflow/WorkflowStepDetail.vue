<template>
  <div class="wf-step-detail">
    <div v-if="step.status === 'failed'" class="wf-detail-msg fail">{{ step.message || '执行失败' }}</div>
    <div v-else-if="step.status === 'suspended'" class="wf-detail-msg suspended">{{ step.message || '等待人工确认' }}</div>

    <!-- LLM -->
    <template v-if="step.nodeType === 'llm'">
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

    <!-- 工具 / MCP -->
    <template v-else-if="step.nodeType === 'tool' || step.nodeType === 'mcp'">
      <ToolCallRenderer v-if="toolPseudoEvent.toolName" :event="toolPseudoEvent" />
      <div v-else-if="toolRawResult" class="wf-detail-pre">{{ truncateText(toolRawResult, 800) }}</div>
    </template>

    <!-- 条件 / 分类 -->
    <template v-else-if="step.nodeType === 'condition'">
      <div class="wf-detail-pill">命中分支：{{ outputs.matchedGroupLabel || handleLabel(outputs.matchedHandle) || '—' }}</div>
      <div v-if="step.nextNodeId" class="wf-detail-meta">下一节点：{{ step.nextNodeId }}</div>
    </template>
    <template v-else-if="step.nodeType === 'classifier'">
      <div class="wf-detail-pill intent">意图：{{ outputs.subject || '—' }}</div>
      <div v-if="outputs.thought" class="wf-detail-pre thought">{{ outputs.thought }}</div>
      <div v-if="tokenSummary" class="wf-detail-meta">{{ tokenSummary }}</div>
    </template>

    <!-- API -->
    <template v-else-if="step.nodeType === 'api'">
      <div class="wf-detail-pill">HTTP {{ outputs.statusCode ?? '—' }}</div>
      <div v-if="outputs.body" class="wf-detail-pre">{{ truncateText(outputs.body, 800) }}</div>
    </template>

    <!-- 脚本 / 参数提取 / 变量 -->
    <template v-else-if="step.nodeType === 'script' || step.nodeType === 'parameter_extractor' || step.nodeType === 'variable' || step.nodeType === 'variable_handle' || step.nodeType === 'input'">
      <div v-if="hasKvData(filteredOutputs)" class="wf-detail-kv">
        <div class="wf-detail-kv-title">出参</div>
        <pre>{{ formatKv(filteredOutputs) }}</pre>
      </div>
    </template>

    <!-- 子工作流 / 容器：子步骤由外层 WorkflowStepRow 递归渲染 -->
    <template v-else-if="step.nodeType === 'app_component'">
      <div v-if="outputs.componentName" class="wf-detail-pill">子流程 · {{ outputs.componentName }}</div>
      <div v-else-if="outputs.result" class="wf-detail-pre">{{ truncateText(outputs.result, 600) }}</div>
      <div v-if="step.children?.length" class="wf-detail-hint">展开上方子节点查看 {{ step.children.length }} 个内部步骤</div>
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

    <div v-if="hasKvData(step.input)" class="wf-detail-kv muted">
      <div class="wf-detail-kv-title">入参</div>
      <pre>{{ formatKv(step.input) }}</pre>
    </div>
    <div v-if="step.nextNodeId && !['condition', 'classifier'].includes(step.nodeType)" class="wf-detail-meta">
      下一节点：{{ step.nextNodeId }}
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import ToolCallRenderer from '../ToolCallRenderer.vue'
import { HIDE_DETAIL_BODY_TYPES } from './workflowNodeRegistry.js'
import {
  parseStepOutputs, formatKv, hasKvData, truncateText, buildToolPseudoEvent,
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

const toolPseudoEvent = computed(() => buildToolPseudoEvent(props.step))
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
.wf-detail-pill.intent { background: #fff7ed; color: #c2410c; }
.wf-detail-pre {
  margin-top: 6px; padding: 8px 10px; background: var(--color-canvas-soft);
  border: 1px solid var(--color-border-slate); border-radius: 6px;
  white-space: pre-wrap; word-break: break-word; max-height: 220px; overflow: auto;
  line-height: 1.55;
}
.wf-detail-pre.thought { max-height: 120px; }
.wf-detail-kv {
  margin-top: 6px; padding: 8px; border-radius: 6px;
  border: 1px solid #ede9fe; background: #faf5ff;
}
.wf-detail-kv.muted { border-color: var(--color-border-slate); background: var(--color-canvas-soft); }
.wf-detail-kv-title { margin-bottom: 4px; font-weight: 600; color: #6d28d9; font-size: 11px; }
.wf-detail-kv pre { margin: 0; white-space: pre-wrap; word-break: break-word; font-family: ui-monospace, monospace; line-height: 1.45; }
.wf-detail-meta { margin-top: 6px; font-size: 11px; color: var(--color-mute); font-family: ui-monospace, monospace; }
.wf-chunk-list { display: flex; flex-direction: column; gap: 6px; margin-top: 6px; }
.wf-chunk-card { padding: 8px; border: 1px solid #e9d5ff; border-radius: 6px; background: #fff; }
.wf-chunk-head { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
.wf-chunk-idx { font-size: 11px; color: #7c3aed; font-weight: 600; }
.wf-chunk-score { font-size: 10px; color: var(--color-mute); }
.wf-chunk-text { font-size: 12px; line-height: 1.5; color: var(--color-text-dark); }
.wf-sub-steps {
  margin-top: 6px; margin-left: 4px; padding-left: 10px;
  border-left: 2px solid #e9d5ff; display: flex; flex-direction: column; gap: 4px;
}
</style>
