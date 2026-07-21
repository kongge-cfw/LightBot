<template>
  <div class="lb-stat-card">
    <div class="lb-stat-card__icon" :style="iconStyle">
      <component :is="icon" v-if="icon" />
    </div>
    <div class="lb-stat-card__info">
      <div class="lb-stat-card__value">{{ displayValue }}</div>
      <div class="lb-stat-card__label">{{ label }}</div>
    </div>
  </div>
</template>

<script setup>
/**
 * 统计卡片
 * 统一 DashboardView.vue / Observability.vue 中 .stat-card 的重复写法。
 * 渐变色通过 accent prop 预设，也可通过 gradient prop 自定义。
 */
import { computed } from 'vue'

const props = defineProps({
  icon: { type: [Object, Function, String], default: null },
  label: { type: String, required: true },
  value: { type: [String, Number], default: '-' },
  accent: {
    type: String,
    default: 'blue',
    validator: (v) => ['blue', 'purple', 'teal', 'orange', 'green', 'red', 'custom'].includes(v),
  },
  gradient: { type: String, default: '' },
})

const ACCENT_PRESETS = {
  blue: 'linear-gradient(135deg, #1890ff, #096dd9)',
  purple: 'linear-gradient(135deg, #722ed1, #531dab)',
  teal: 'linear-gradient(135deg, #13c2c2, #08979c)',
  orange: 'linear-gradient(135deg, #fa8c16, #d46b08)',
  green: 'linear-gradient(135deg, #52c41a, #389e0d)',
  red: 'linear-gradient(135deg, #f5222d, #cf1322)',
}

const iconStyle = computed(() => ({
  background: props.gradient || ACCENT_PRESETS[props.accent] || ACCENT_PRESETS.blue,
}))

const displayValue = computed(() => (props.value == null || props.value === '' ? '-' : props.value))
</script>

<style scoped>
.lb-stat-card__info {
  min-width: 0;
}
</style>
