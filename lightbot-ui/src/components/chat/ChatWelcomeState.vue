<template>
  <div class="empty-state">
    <img src="/lightbot-logo-single.png" alt="LightBot" class="empty-logo" />
    <div class="welcome-content"><MarkdownPreview :content="welcomeMessage" /></div>
    <!-- 推荐问题：全部展示 -->
    <div v-if="recommendedQuestions.length > 0" class="recommended-questions">
      <button
        v-for="(q, qi) in recommendedQuestions"
        :key="qi"
        class="btn-question"
        @click="$emit('select-question', q)"
      >
        {{ q }}
      </button>
    </div>
    <!-- 无默认Agent提示 -->
    <div v-if="!selectedAgentId && agentsLength > 0" class="no-default-hint">
      没有默认Agent，<router-link to="/app/agents">去创建</router-link>
    </div>
  </div>
</template>

<script setup>
import MarkdownPreview from '../MarkdownPreview.vue'

defineProps({
  welcomeMessage: { type: String, default: '' },
  recommendedQuestions: { type: Array, default: () => [] },
  selectedAgentId: { type: [String, Number], default: null },
  agentsLength: { type: Number, default: 0 },
})

defineEmits(['select-question'])
</script>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  padding: 40px;
}
.empty-logo {
  height: 64px;
  margin-bottom: 24px;
  object-fit: contain;
}
.welcome-content {
  text-align: center;
  max-width: 600px;
  margin-bottom: 24px;
  font-size: 15px;
  line-height: 1.7;
  color: var(--color-ink);
}
.welcome-content :deep(h1),
.welcome-content :deep(h2) {
  font-size: 24px;
  font-weight: 600;
  color: var(--color-ink);
  margin-bottom: 8px;
}
.welcome-content :deep(p) {
  margin: 0 0 8px;
  color: var(--color-mute);
}
.recommended-questions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
  max-width: 600px;
}
.btn-question {
  padding: 8px 16px;
  background: var(--color-canvas);
  border: 1px solid var(--color-hairline);
  border-radius: 100px;
  font-size: 13px;
  color: var(--color-body);
  cursor: pointer;
  transition: all 0.15s;
}
.btn-question:hover {
  border-color: var(--color-link);
  color: var(--color-link);
  background: var(--color-link-bg-soft);
}
.no-default-hint {
  font-size: 13px;
  color: var(--color-mute);
  margin-top: 8px;
}
.no-default-hint a {
  color: var(--color-link);
  text-decoration: none;
}
.no-default-hint a:hover {
  text-decoration: underline;
}
</style>
