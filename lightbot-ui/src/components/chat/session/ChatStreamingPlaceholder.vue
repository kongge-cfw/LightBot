<template>
  <div class="message assistant">
    <div class="message-body">
      <div class="message-content status-content">
        <div class="status-loading">
          <span class="status-spinner"></span>
          <span class="status-text">{{ statusText || '正在思考...' }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  statusText: { type: String, default: '' },
})
</script>

<style scoped>
.message {
  padding: 12px 32px;
  max-width: 800px;
  margin: 0 auto;
  width: 100%;
}
.message-body {
  min-width: 0;
  width: 100%;
}
.message-content {
  font-size: 15px;
  line-height: 1.7;
  color: var(--color-ink);
  word-break: break-word;
}
.status-content {
  display: flex;
  align-items: center;
}
.status-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: var(--color-canvas-soft-2);
  border-radius: 12px;
  animation: fadeIn 0.3s ease;
}
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(4px); }
  to { opacity: 1; transform: translateY(0); }
}
.status-spinner {
  width: 14px;
  height: 14px;
  border: 2px solid var(--color-hairline);
  border-top-color: var(--color-link);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}
.status-text {
  font-size: 13px;
  color: var(--color-body);
}
</style>
