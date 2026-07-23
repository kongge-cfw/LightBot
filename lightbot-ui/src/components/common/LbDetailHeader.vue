<template>
  <!-- 面包屑模式：顶栏 + 标题区 两行布局 -->
  <div v-if="breadcrumb && breadcrumb.length" class="lb-detail-header lb-detail-header--stack">
    <div class="lb-detail-header__top-bar">
      <LbBreadcrumb :items="breadcrumb" :show-arrow="showBack" @back="emit('back')" />
      <div v-if="$slots.extra" class="lb-detail-header__extra">
        <slot name="extra" />
      </div>
    </div>
    <div class="lb-detail-header__title-section">
      <div v-if="icon" class="lb-detail-header__icon" :class="`lb-detail-header__icon--${iconBg}`">
        <component :is="icon" />
      </div>
      <div class="lb-detail-header__title-text">
        <h2 class="lb-detail-header__title">{{ title }}</h2>
        <div v-if="$slots.tags || tagsList.length" class="lb-detail-header__tags">
          <slot name="tags">
            <a-tag v-for="t in tagsList" :key="t" color="blue">{{ t }}</a-tag>
          </slot>
        </div>
      </div>
    </div>
  </div>

  <!-- 兼容模式：单行布局（原 showBack + title + tags + extra） -->
  <div v-else class="lb-detail-header">
    <div class="lb-detail-header__main">
      <button v-if="showBack" type="button" class="lb-detail-header__back" @click="emit('back')">
        <ArrowLeftOutlined /> 返回
      </button>
      <h2 class="lb-detail-header__title">{{ title }}</h2>
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
 * 支持两种模式：
 * 1. 面包屑模式（推荐）：传入 breadcrumb 数组，渲染「顶栏（面包屑 + 操作）+ 标题区（图标盒 + h2 + 元信息）」两行布局，
 *    参考 Linear / Notion / Dify。解决「返回按钮放哪都丑」问题——箭头融入面包屑导航路径。
 * 2. 兼容模式：不传 breadcrumb 但 showBack=true，退化为原「返回按钮 + 标题 + tags + extra」单行布局。
 */
import { ArrowLeftOutlined } from '@ant-design/icons-vue'
import { computed } from 'vue'
import LbBreadcrumb from './LbBreadcrumb.vue'

const props = defineProps({
  title: { type: String, required: true },
  tags: { type: [String, Array], default: '' },
  showBack: { type: Boolean, default: true },
  breadcrumb: {
    type: Array,
    default: undefined,
  },
  icon: { type: [Object, Function], default: undefined },
  iconBg: {
    type: String,
    default: 'default',
    validator: (v) => ['default', 'tool', 'knowledge', 'mcp', 'skill', 'subagent'].includes(v),
  },
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
