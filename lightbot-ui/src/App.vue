<template>
  <a-config-provider :locale="zhCN" :theme="themeConfig" :modal="modalDefaults">
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

// antd Modal 全局默认值：强制遮罩点击不关闭（项目规范），新代码无需逐个 :mask-closable="false"
const modalDefaults = {
  maskClosable: false,
  centered: false,
}

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

/* Ant Design 主题 token 已在 useTheme.js 的 themeConfig 中统一配置
   （colorPrimary / borderRadius / Button.borderRadius 等）；
   此处仅保留 token 无法覆盖的兜底规则，避免视觉退化 */

/* Button disabled 兜底：darkAlgorithm 派生的 disabled 灰度偏亮，回归原项目视觉 */
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

[data-theme="dark"] .ant-btn-primary {
  border-color: rgba(255, 255, 255, 0.15) !important;
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

/* Notification 全局定制：项目级通知统一圆角与阴影（token Notification.borderRadiusLG 兜底） */
.ant-notification-notice {
  border-radius: var(--radius-md) !important;
  box-shadow: var(--shadow-4) !important;
}
.ant-notification-notice-message {
  font-weight: 600;
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

/* ===== 深色模式：antd 组件全局兜底 =====
   说明：antd 4.x 的部分 alias token（如 Tabs.itemSelectedColor / Pagination.itemActiveBg）
   在 darkAlgorithm 下会被重新派生（基于 colorPrimary: #171717 → 约 #404040），与深色
   背景对比度不足。useTheme.js 已显式注入按 isDark 切换的 token 值，但部分场景被
   darkAlgorithm 覆盖，故在此处加全局 CSS 兜底保证可见性。 */

/* Tabs 选中态文字、hover 态、下划线：深色模式下用浅色保证可见 */
[data-theme="dark"] .ant-tabs-tab.ant-tabs-tab-active .ant-tabs-tab-btn {
  color: #e4e4e7 !important;
}
[data-theme="dark"] .ant-tabs-tab:hover .ant-tabs-tab-btn {
  color: #d4d4d8 !important;
}
[data-theme="dark"] .ant-tabs-ink-bar {
  background: #e4e4e7 !important;
}
[data-theme="dark"] .ant-tabs-nav::before {
  border-bottom-color: var(--color-hairline) !important;
}

/* Pagination 激活态：深色模式下用中灰底 + 浅色文字 */
[data-theme="dark"] .ant-pagination-item-active {
  background: #3f3f46 !important;
  border-color: #3f3f46 !important;
}
[data-theme="dark"] .ant-pagination-item-active a {
  color: #e4e4e7 !important;
}

/* Table hover 行：darkAlgorithm 派生的 hover 色常偏暗，强制轻量高亮 */
[data-theme="dark"] .ant-table-tbody > tr.ant-table-row:hover > td {
  background: rgba(255, 255, 255, 0.04) !important;
}

/* Menu 选中态：darkAlgorithm 派生色与背景对比度不足 */
[data-theme="dark"] .ant-menu-item-selected {
  color: #e4e4e7 !important;
}
[data-theme="dark"] .ant-menu-item-active {
  background-color: rgba(255, 255, 255, 0.06) !important;
}

/* Drawer / Modal 在深色模式下边框、阴影加强 */
[data-theme="dark"] .ant-modal-content,
[data-theme="dark"] .ant-drawer-content {
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.5) !important;
}

/* Card 边框：darkAlgorithm 默认边框偏亮，对齐项目 hairline */
[data-theme="dark"] .ant-card {
  border-color: var(--color-hairline) !important;
}

/* Tag 默认色：深色模式下 default tag 文字对比度不足 */
[data-theme="dark"] .ant-tag {
  color: #d4d4d8 !important;
  border-color: var(--color-hairline) !important;
  background: rgba(255, 255, 255, 0.04) !important;
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
