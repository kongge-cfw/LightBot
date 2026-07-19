<template>
  <slot v-if="!error" />
  <div v-else class="error-boundary">
    <div class="error-card">
      <div class="error-icon">!</div>
      <h3 class="error-title">页面出现异常</h3>
      <p class="error-desc">{{ friendlyMessage }}</p>
      <pre v-if="errorDetail" class="error-detail">{{ errorDetail }}</pre>
      <div class="error-actions">
        <button class="btn-primary" @click="handleRetry">重试</button>
        <button class="btn-secondary" @click="handleReload">刷新页面</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onErrorCaptured } from 'vue'
import { captureException } from '../utils/errorReport'

const props = defineProps({
  /** 友好的错误提示文案，覆盖默认 */
  message: { type: String, default: '' },
  /** 是否在控制台展示完整错误（开发态 true） */
  verbose: { type: Boolean, default: true },
})

const emit = defineEmits(['error'])

const error = ref(null)

const friendlyMessage = computed(() => props.message || '当前组件渲染失败，可尝试重试或刷新页面')
const errorDetail = computed(() => {
  if (!props.verbose || !error.value) return ''
  const err = error.value
  return `${err.message || ''}\n${err.stack || ''}`.slice(0, 800)
})

onErrorCaptured((err, instance, info) => {
  error.value = err
  // 上报到监控 + 触发上游 hook（如局部错误需要全局通知）
  captureException(err, {
    source: 'errorCaptured',
    info,
    componentTag: instance?.$options?.__name || instance?.$options?.name,
  })
  emit('error', err, info)
  // 返回 false 阻止错误继续向上冒泡（外层 ErrorBoundary 不会重复捕获）
  return false
})

function handleRetry() {
  error.value = null
}

function handleReload() {
  location.reload()
}
</script>

<style scoped>
.error-boundary {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 320px;
  padding: 32px;
}

.error-card {
  max-width: 540px;
  padding: 32px 28px;
  background: var(--color-bg-container, #fff);
  border: 1px solid var(--color-hairline, #ebebeb);
  border-radius: var(--radius-lg, 12px);
  text-align: center;
  box-shadow: var(--shadow-2);
}

.error-icon {
  width: 48px;
  height: 48px;
  margin: 0 auto 16px;
  border-radius: 50%;
  background: var(--color-error-soft, #f7d4d6);
  color: var(--color-error, #ee0000);
  font-size: 28px;
  font-weight: 700;
  line-height: 48px;
}

.error-title {
  margin: 0 0 8px;
  font-size: 18px;
  color: var(--color-ink, #171717);
}

.error-desc {
  margin: 0 0 16px;
  color: var(--color-body, #4d4d4d);
  font-size: 14px;
}

.error-detail {
  margin: 0 0 16px;
  padding: 12px;
  background: var(--color-canvas-soft-2, #f5f5f5);
  border-radius: var(--radius-sm, 6px);
  font-family: var(--font-mono, monospace);
  font-size: 12px;
  color: var(--color-body, #4d4d4d);
  text-align: left;
  max-height: 200px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}

.error-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
}

.btn-primary,
.btn-secondary {
  padding: 6px 16px;
  border-radius: var(--radius-pill, 100px);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  border: none;
}

.btn-primary {
  background: var(--color-primary, #171717);
  color: #fff;
}

.btn-primary:hover {
  background: #27272a;
}

.btn-secondary {
  background: transparent;
  color: var(--color-ink, #171717);
  border: 1px solid var(--color-hairline, #ebebeb);
}

.btn-secondary:hover {
  border-color: var(--color-hairline-strong, #a1a1a1);
}
</style>
