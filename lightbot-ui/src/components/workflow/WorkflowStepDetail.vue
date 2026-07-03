<template>
  <div class="wf-step-detail">
    <div v-if="step.status === 'failed'" class="wf-detail-msg fail">{{ step.message || '执行失败' }}</div>
    <div v-else-if="step.status === 'suspended' && step.nodeType !== 'confirm'" class="wf-detail-msg suspended">
      {{ step.message || '等待处理' }}
    </div>

    <component :is="detailRenderer" :step="step" />

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
import { formatKv, hasKvData } from './workflowStepUtils.js'
import { getWorkflowNodeDetailRenderer } from './workflowNodeDetailRegistry.js'
import './workflowStepDetailShared.css'

const props = defineProps({
  step: { type: Object, required: true },
})

const detailRenderer = computed(() => getWorkflowNodeDetailRenderer(props.step?.nodeType))
</script>
