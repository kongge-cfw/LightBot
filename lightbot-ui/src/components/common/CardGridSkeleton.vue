<template>
  <div class="skeleton-grid" aria-busy="true" aria-live="polite">
    <div v-for="i in count" :key="i" class="skeleton-card">
      <div class="skeleton-card-top">
        <div class="skeleton-icon" />
        <div class="skeleton-info">
          <div class="skeleton-line skeleton-line--title" />
          <div class="skeleton-line skeleton-line--tag" />
        </div>
        <div class="skeleton-actions">
          <div class="skeleton-action-dot" />
          <div class="skeleton-action-dot" />
        </div>
      </div>
      <div class="skeleton-body">
        <div class="skeleton-line skeleton-line--desc" />
        <div class="skeleton-line skeleton-line--desc skeleton-line--short" />
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  count: { type: Number, default: 9 },
})
</script>

<style scoped>
.skeleton-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}
.skeleton-card {
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
  border-radius: 12px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.skeleton-card-top {
  display: flex;
  align-items: center;
  gap: 12px;
}

.skeleton-icon,
.skeleton-line,
.skeleton-action-dot {
  background: linear-gradient(
    90deg,
    var(--color-canvas-soft-2) 25%,
    var(--color-canvas-soft-3, #e4e4e7) 37%,
    var(--color-canvas-soft-2) 63%
  );
  background-size: 400% 100%;
  animation: skeleton-shimmer 1.4s ease infinite;
  border-radius: 6px;
}

.skeleton-icon {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  flex-shrink: 0;
}

.skeleton-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.skeleton-line {
  height: 12px;
}

.skeleton-line--title {
  width: 55%;
}

.skeleton-line--tag {
  width: 30%;
  height: 10px;
}

.skeleton-line--desc {
  width: 100%;
}

.skeleton-line--short {
  width: 70%;
}

.skeleton-actions {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
}

.skeleton-action-dot {
  width: 28px;
  height: 28px;
  border-radius: 6px;
}

.skeleton-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

@keyframes skeleton-shimmer {
  0% {
    background-position: 100% 50%;
  }
  100% {
    background-position: 0 50%;
  }
}

@media (prefers-reduced-motion: reduce) {
  .skeleton-icon,
  .skeleton-line,
  .skeleton-action-dot {
    animation: none;
  }
}
</style>
