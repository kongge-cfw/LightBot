<template>
  <a-config-provider :locale="zhCN" :theme="themeConfig">
    <ErrorBoundary verbose>
      <router-view v-slot="{ Component, route: r }">
        <transition name="route-fade" mode="out-in">
          <component :is="Component" :key="r.matched[0]?.path || r.path" />
        </transition>
      </router-view>
    </ErrorBoundary>
  </a-config-provider>
</template>

<script setup>
import { onMounted, onUnmounted } from 'vue'
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import { useTheme } from './composables/useTheme'
import { handleDebugLabShortcut } from './utils/chat/debug/debugLabShortcut'
import ErrorBoundary from './components/ErrorBoundary.vue'

const { isDark, themeConfig } = useTheme()

function onGlobalKeydown(e) {
  handleDebugLabShortcut(e)
}

onMounted(() => {
  window.addEventListener('keydown', onGlobalKeydown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', onGlobalKeydown)
})
</script>

<style>
/* 全局 CSS 变量（含暗色主题）已抽出至 src/styles/variables.css，由 main.js 优先加载 */

/* 全局基础样式 */
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

/* 禁用浏览器拼写检查波浪线 */
::spelling-error {
  text-decoration: none;
}

/* 锁死文档外壳：滚动只允许发生在页面内部指定的滚动容器里，body 永不滚动，
   避免可滚动页残留的 body 级滚动条让固定页也能小幅滚动 */
html,
body,
#app {
  height: 100%;
}

body {
  font-family: var(--font-sans);
  color: var(--color-ink);
  background: var(--color-canvas-soft);
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  overflow: hidden;
}

/* Ant Design 主题覆盖 - Vercel 风格 */
.ant-btn-primary {
  background: var(--color-primary) !important;
  border-color: var(--color-primary) !important;
  border-radius: var(--radius-pill) !important;
  font-weight: 500;
  box-shadow: none !important;
}

.ant-btn-primary:hover {
  background: #27272a !important;
  border-color: #27272a !important;
}

[data-theme="dark"] .ant-btn-primary {
  border-color: rgba(255, 255, 255, 0.15) !important;
}
[data-theme="dark"] .ant-btn-primary:hover {
  background: #3f3f46 !important;
  border-color: rgba(255, 255, 255, 0.25) !important;
}

.ant-btn-primary:disabled,
.ant-btn-primary.ant-btn-disabled {
  background: var(--color-canvas-soft-2) !important;
  border-color: var(--color-hairline) !important;
  color: var(--dark-25) !important;
  box-shadow: none !important;
  text-shadow: none !important;
  cursor: not-allowed !important;
}

[data-theme="dark"] .ant-btn-primary:disabled,
[data-theme="dark"] .ant-btn-primary.ant-btn-disabled {
  background: rgba(255, 255, 255, 0.08) !important;
  border-color: rgba(255, 255, 255, 0.15) !important;
  color: rgba(255, 255, 255, 0.35) !important;
}

.ant-btn-default {
  border-radius: var(--radius-pill) !important;
  font-weight: 500;
}

.ant-input,
.ant-input-affix-wrapper,
.ant-select-selector,
.ant-picker {
  border-radius: var(--radius-sm) !important;
}

.ant-modal .ant-modal-content {
  border-radius: var(--radius-lg) !important;
  box-shadow: var(--shadow-5) !important;
}

.ant-modal .ant-modal-header {
  border-radius: var(--radius-lg) var(--radius-lg) 0 0 !important;
}

.ant-message .ant-message-notice-content {
  border-radius: var(--radius-md) !important;
  box-shadow: var(--shadow-4) !important;
}

/* 表格表头禁止换行 */
.ant-table-thead th,
.ant-table-thead .ant-table-cell {
  white-space: nowrap !important;
}

/* 滚动条样式 */
::-webkit-scrollbar {
  width: 6px;
}

::-webkit-scrollbar-track {
  background: transparent;
}

::-webkit-scrollbar-thumb {
  background: #d4d4d8;
  border-radius: 3px;
}

::-webkit-scrollbar-thumb:hover {
  background: #a1a1aa;
}

[data-theme="dark"] ::-webkit-scrollbar-thumb {
  background: #3f3f46;
}

[data-theme="dark"] ::-webkit-scrollbar-thumb:hover {
  background: #52525b;
}

/* 弹窗遮罩层禁止外层滚动，内容仅在 modal body 内滚动 */
.ant-modal-wrap {
  overflow: hidden !important;
}

/* 垂直滚动区域：内容与滚动条保持统一间距 */
.scroll-area-y,
.modal-scroll-body,
.tab-content,
.guide,
.log-table-body,
.detail-scroll-body,
.dialog-scroll-body,
.fetch-model-list,
.model-list {
  scrollbar-gutter: stable;
}

.scroll-area-y,
.modal-scroll-body,
.tab-content,
.guide,
.log-table-body,
.fetch-model-list,
.model-list {
  padding-right: var(--scroll-content-gap);
}

/* 弹窗 / 抽屉内可滚动区域 */
.ant-modal-body .scroll-area-y,
.ant-modal-body .modal-scroll-body,
.ant-modal-body .dialog-scroll-body,
.ant-drawer-body .scroll-area-y,
.ant-drawer-body .modal-scroll-body {
  padding-right: var(--scroll-content-gap);
}

/* ===== 深色模式：自定义组件适配 ===== */
/* Ant Design 组件由 darkAlgorithm 自动处理，仅保留自定义组件覆盖 */

[data-theme="dark"] .tool-calls-group,
[data-theme="dark"] .workflow-nodes-group {
  background: var(--color-bg-elevated) !important;
  border-color: var(--color-hairline) !important;
}

[data-theme="dark"] .search-docs-result *,
[data-theme="dark"] .web-search-result *,
[data-theme="dark"] .sandbox-file-result * {
  border-color: var(--color-hairline) !important;
}

/* 顶层路由切换：淡入淡出，仅作用于 Landing/Login/MainLayout 等顶层路由 */
.route-fade-enter-active,
.route-fade-leave-active {
  transition: opacity 0.32s cubic-bezier(0.22, 1, 0.36, 1), transform 0.32s cubic-bezier(0.22, 1, 0.36, 1);
}
.route-fade-enter-from {
  opacity: 0;
  transform: translateY(6px) scale(0.992);
}
.route-fade-leave-to {
  opacity: 0;
  transform: translateY(-4px) scale(0.992);
}
</style>
