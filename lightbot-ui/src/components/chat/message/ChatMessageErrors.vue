<template>
  <!-- 工作流节点最终失败（专用错误块） -->
  <div v-if="msg._workflowError" class="error-block workflow-error-block">
    <div class="error-block-header">
      <CloseCircleOutlined class="error-block-icon" />
      <span class="error-block-title">工作流执行失败</span>
      <span v-if="msg._workflowError.reason" class="error-block-code">
        {{ workflowFailureReasonLabel(msg._workflowError.reason) }}
      </span>
    </div>
    <div class="workflow-error-node">节点「{{ msg._workflowError.nodeLabel }}」</div>
    <div class="error-block-message">{{ msg._workflowError.message }}</div>
  </div>
  <!-- 工作流节点重试/超时提示 -->
  <div v-if="msg._workflowNodeRetry" class="error-retry-block workflow-resilience-block" :class="{ 'is-failure': msg._workflowNodeRetry.kind === 'failure' }">
    <div class="error-retry-header">
      <LoadingOutlined v-if="msg._workflowNodeRetry.kind === 'retry' && msg._streaming" class="error-retry-icon" spin />
      <WarningOutlined v-else-if="msg._workflowNodeRetry.kind === 'retry'" class="error-retry-icon" />
      <CloseCircleOutlined v-else class="error-retry-icon failure-icon" />
      <span class="error-retry-title">{{ workflowResilienceTitle(msg._workflowNodeRetry) }}</span>
      <span v-if="msg._workflowNodeRetry.attempt != null" class="error-retry-count">
        {{ msg._workflowNodeRetry.attempt }}/{{ workflowRetryTotal(msg._workflowNodeRetry) }}
      </span>
    </div>
    <div class="error-retry-message">{{ msg._workflowNodeRetry.message }}</div>
  </div>
  <!-- 1.3 模型重试提示 -->
  <div v-if="msg._errorRetry" class="error-retry-block">
    <div class="error-retry-header">
      <LoadingOutlined v-if="msg._streaming" class="error-retry-icon" spin />
      <WarningOutlined v-else class="error-retry-icon" />
      <span class="error-retry-title">AI 连接异常，正在重试</span>
      <span class="error-retry-count">{{ msg._errorRetry.attempt }}/{{ msg._errorRetry.maxRetries }}</span>
    </div>
    <div class="error-retry-message">{{ msg._errorRetry.message }}</div>
  </div>
  <!-- 1.3 结构化错误事件：LLM 调用中断、工具异常等 -->
  <div v-if="msg._error" class="error-block">
    <div class="error-block-header">
      <CloseCircleOutlined class="error-block-icon" />
      <span class="error-block-title">AI 调用异常</span>
      <span class="error-block-code">{{ msg._error.code }}</span>
    </div>
    <div class="error-block-message">{{ msg._error.message }}</div>
  </div>
</template>

<script setup>
import { LoadingOutlined, WarningOutlined, CloseCircleOutlined } from '@ant-design/icons-vue'
import { workflowFailureReasonLabel } from '../../workflow/workflowStepUtils.js'

defineProps({
  msg: { type: Object, required: true },
})

function workflowResilienceTitle(item) {
  if (item.kind === 'failure') return '工作流节点失败'
  const map = {
    connect_timeout: '节点连接超时',
    read_timeout: '节点响应超时',
    execution_error: '节点执行异常',
  }
  return map[item.reason] || '工作流节点重试中'
}

function workflowRetryTotal(item) {
  if (item.maxAttempts == null) return item.attempt || '—'
  return Math.max(1, item.maxAttempts - 1)
}
</script>

<style scoped>
.error-retry-block {
  background: rgba(245, 158, 11, 0.08);
  border: 1px solid rgba(245, 158, 11, 0.24);
  border-radius: 10px;
  padding: 12px 14px;
  margin-bottom: 10px;
  font-size: 14px;
  line-height: 1.6;
  animation: fadeIn 0.3s ease;
}
.error-retry-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
.error-retry-icon {
  color: #d97706;
  font-size: 15px;
  flex-shrink: 0;
}
.error-retry-title {
  font-weight: 600;
  color: #92400e;
}
.error-retry-count {
  margin-left: auto;
  font-size: 12px;
  color: #92400e;
  background: rgba(245, 158, 11, 0.14);
  padding: 1px 8px;
  border-radius: 4px;
  font-family: var(--font-mono);
}
.error-retry-message {
  color: #92400e;
}
.workflow-resilience-block.is-failure {
  background: var(--color-error-bg);
  border-color: var(--color-error-soft);
}
.workflow-resilience-block.is-failure .error-retry-title,
.workflow-resilience-block.is-failure .error-retry-message {
  color: #991b1b;
}
.workflow-resilience-block.is-failure .error-retry-count {
  color: #991b1b;
  background: rgba(239, 68, 68, 0.1);
}
.failure-icon { color: #ef4444 !important; }
.error-block {
  background: var(--color-error-bg);
  border: 1px solid var(--color-error-soft);
  border-radius: 10px;
  padding: 14px 16px;
  font-size: 14px;
  line-height: 1.6;
  animation: fadeIn 0.3s ease;
}
.error-block-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.error-block-icon {
  color: #ef4444;
  font-size: 16px;
  flex-shrink: 0;
}
.error-block-title {
  font-weight: 600;
  color: #991b1b;
}
.error-block-code {
  margin-left: auto;
  font-size: 12px;
  color: #991b1b;
  background: rgba(239, 68, 68, 0.1);
  padding: 1px 8px;
  border-radius: 4px;
  font-family: var(--font-mono);
}
.error-block-message {
  color: #991b1b;
}
.workflow-error-block .workflow-error-node {
  font-size: 13px;
  color: #7f1d1d;
  margin-bottom: 4px;
  font-weight: 500;
}
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(4px); }
  to { opacity: 1; transform: translateY(0); }
}
</style>
