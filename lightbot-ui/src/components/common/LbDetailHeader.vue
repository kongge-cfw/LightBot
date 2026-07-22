<template>
  <div class="lb-detail-header">
    <div class="lb-detail-header__main">
      <button v-if="showBack" type="button" class="lb-detail-header__back" @click="emit('back')">
        <ArrowLeftOutlined /> 返回
      </button>
      <h1 class="lb-detail-header__title">{{ title }}</h1>
      <p class="lb-detail-header__desc" :class="{ 'lb-detail-header__desc--empty': !desc }">{{ desc || '暂无描述' }}</p>
      <div v-if="$slots.tags || tagsList.length" class="lb-detail-header__tags">
        <slot name="tags">
          <a-tag v-for="t in tagsList" :key="t" color="blue">{{ t }}</a-tag>
        </slot>
      </div>
    </div>
    <div v-if="$slots.extra" class="lb-detail-header__extra">
      <slot name="extra" />
    </div>
  </div>
</template>

<script setup>
/**
 * 详情页头部
 * 统一项目中 6+ 处 .btn-back + .page-title + .page-desc + .header-actions 的重复写法。
 * 三种返回写法（.btn-back / <a-button type="text"> / .btn-outline-sm）统一为本组件 showBack + @back。
 */
import { ArrowLeftOutlined } from '@ant-design/icons-vue'
import { computed } from 'vue'

const props = defineProps({
  title: { type: String, required: true },
  desc: { type: String, default: '' },
  tags: { type: [String, Array], default: '' },
  showBack: { type: Boolean, default: true },
})

const emit = defineEmits(['back'])

const tagsList = computed(() => {
  if (!props.tags) return []
  if (Array.isArray(props.tags)) return props.tags
  return String(props.tags)
    .split(/[,，]/)
    .map((t) => t.trim())
    .filter(Boolean)
})
</script>
