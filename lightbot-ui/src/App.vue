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

/* Button hover/active 深色模式兜底：
   colorPrimary=#171717 经 darkAlgorithm 派生的 colorPrimaryHover/colorPrimaryActive 趋近 #000，
   和 colorBgLayout=#111 画布重合导致 primary 按钮 hover/active 时"消失"。
   显式提升 hover/active 亮度到中灰区间，并加强白色描边保证可见性。
   default 按钮 hover 的边框色派生自 colorPrimaryHover（同样趋黑），同步覆盖为浅灰。 */
[data-theme="dark"] .ant-btn-primary:not(.ant-btn-disabled):not(:disabled):hover,
[data-theme="dark"] .ant-btn-primary:not(.ant-btn-disabled):not(:disabled):focus {
  background: #2a2a2a !important;
  border-color: rgba(255, 255, 255, 0.3) !important;
  color: #fff !important;
}
[data-theme="dark"] .ant-btn-primary:not(.ant-btn-disabled):not(:disabled):active {
  background: #3f3f46 !important;
  border-color: rgba(255, 255, 255, 0.35) !important;
  color: #fff !important;
}
[data-theme="dark"] .ant-btn-default:not(.ant-btn-disabled):not(:disabled):hover,
[data-theme="dark"] .ant-btn-default:not(.ant-btn-disabled):not(:disabled):focus {
  background: rgba(255, 255, 255, 0.06) !important;
  border-color: #52525b !important;
  color: #e4e4e7 !important;
}
[data-theme="dark"] .ant-btn-default:not(.ant-btn-disabled):not(:disabled):active {
  background: rgba(255, 255, 255, 0.1) !important;
  border-color: #71717a !important;
  color: #fafafa !important;
}

.ant-input,
.ant-input-affix-wrapper,
.ant-select-selector,
.ant-picker {
  border-radius: var(--radius-sm) !important;
}

/* 输入类控件 hover/focus 状态：antd 默认用 colorPrimary（#171717）派生 controlOutline，
   alpha 约 0.12，浅色背景下 box-shadow 几乎看不见；深色模式下更糊。
   统一覆盖为 link 蓝边 + 蓝色光晕，跨主题一致、视觉反馈清晰。
   覆盖范围：Input / Input.TextArea / InputNumber / Select / DatePicker / Cascader 等。 */
.ant-input:hover:not(:disabled):not(.ant-input-disabled),
.ant-input-affix-wrapper:hover:not(.ant-input-affix-wrapper-disabled),
.ant-input-number:not(.ant-input-number-disabled):hover,
.ant-input-number-affix-wrapper:not(.ant-input-number-affix-wrapper-disabled):hover,
.ant-picker:not(.ant-picker-disabled):hover,
.ant-select:not(.ant-select-disabled):hover .ant-select-selector,
.ant-cascader-picker:not(.ant-cascader-picker-disabled):hover .ant-cascader-input {
  border-color: var(--color-link) !important;
}

.ant-input:focus,
.ant-input-focused,
.ant-input-affix-wrapper-focused,
.ant-input-number-focused,
.ant-input-number-affix-wrapper-focused,
.ant-picker-focused,
.ant-select-focused .ant-select-selector,
.ant-cascader-picker-focused .ant-cascader-input {
  border-color: var(--color-link) !important;
  box-shadow: 0 0 0 2px var(--color-link-bg-soft) !important;
  outline: 0 !important;
}

/* textarea focus 状态：ant-input 类的 textarea 走 .ant-input:focus 路径，但部分场景用 :focus-within */
textarea.ant-input:focus,
.ant-input textarea:focus {
  border-color: var(--color-link) !important;
  box-shadow: 0 0 0 2px var(--color-link-bg-soft) !important;
  outline: 0 !important;
}

.ant-modal .ant-modal-content {
  border-radius: var(--radius-lg) !important;
  box-shadow: var(--shadow-5) !important;
}

.ant-modal .ant-modal-header {
  border-radius: var(--radius-lg) var(--radius-lg) 0 0 !important;
}

/* Message 全局定制：跟随主题的通知样式
   - 浅色模式：白底 + 深字 + hairline 边框
   - 深色模式：深底 + 白字 + 半透边框，避免与 #111 背景重合
   - 图标色降饱和：success/error/warning/info 改用 Tailwind 色阶而非 antd 默认饱和色 */
.ant-message .ant-message-notice-content {
  border-radius: var(--radius-md) !important;
  box-shadow: var(--shadow-4) !important;
  background: var(--color-canvas) !important;
  border: 1px solid var(--color-hairline) !important;
  color: var(--color-ink) !important;
  font-size: 13px !important;
  font-weight: 500 !important;
  padding: 10px 16px !important;
}
.ant-message .ant-message-notice-content .anticon {
  font-size: 15px !important;
  margin-right: 8px !important;
}
.ant-message .ant-message-success .anticon { color: #22c55e !important; }
.ant-message .ant-message-error .anticon { color: #ef4444 !important; }
.ant-message .ant-message-warning .anticon { color: #f59e0b !important; }
.ant-message .ant-message-info .anticon,
.ant-message .ant-message-loading .anticon { color: #3b82f6 !important; }

[data-theme="dark"] .ant-message .ant-message-notice-content {
  background: #18181b !important;
  border-color: rgba(255, 255, 255, 0.08) !important;
  color: #fafafa !important;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.6) !important;
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

/* Pagination size-changer：默认 select 宽度由当前值撑开，pageSize=10 时只容得下"10 条/页"，
   下拉里的"100 条/页"被挤压。强制 min-width 容下最长的可选项，全局 10 处一并解决。 */
.ant-pagination-options .ant-select {
  min-width: 112px;
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

/* Spin 加载动画：antd 默认 dot 色派生自 colorPrimary（本项目 #171717），
   深色模式下近黑色，在 #111 画布上完全不可见；浅色模式下也偏暗不够醒目。
   统一改用 link 蓝色，跨主题一致可见。同时把 tip 文字色绑定到 mute，
   避免 tip 在深色下也是近黑色。 */
.ant-spin-dot-item {
  background: var(--color-link) !important;
}
.ant-spin-text,
.ant-spin .ant-spin-tip {
  color: var(--color-mute) !important;
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

/* Radio/Checkbox hover 兜底：antd 把 hover 文字/边框色派生自 colorPrimary（#171717），
   深色模式下近乎黑色，未选中项 hover 时与深色背景重合看不见。强制改为浅色 + 轻底色反馈。 */
[data-theme="dark"] .ant-radio-button-wrapper:not(.ant-radio-button-wrapper-checked):not(.ant-radio-button-wrapper-disabled):hover,
[data-theme="dark"] .ant-radio-wrapper:not(.ant-radio-wrapper-checked):not(.ant-radio-wrapper-disabled):hover,
[data-theme="dark"] .ant-checkbox-wrapper:not(.ant-checkbox-wrapper-checked):not(.ant-checkbox-wrapper-disabled):hover {
  color: #d4d4d8 !important;
}
[data-theme="dark"] .ant-radio-button-wrapper:not(.ant-radio-button-wrapper-checked):not(.ant-radio-button-wrapper-disabled):hover {
  background-color: rgba(255, 255, 255, 0.06) !important;
}
[data-theme="dark"] .ant-radio-wrapper:not(.ant-radio-wrapper-checked):hover .ant-radio-inner,
[data-theme="dark"] .ant-checkbox-wrapper:not(.ant-checkbox-wrapper-checked):hover .ant-checkbox-inner {
  border-color: #d4d4d8 !important;
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

/* ===== 下拉项选中态统一（浅/深色通用）=====
   说明：antd 4.x 的 Select / Cascader / TreeSelect / 内嵌 Select 的下拉项选中态颜色
   由 colorPrimary 派生，本项目 colorPrimary=#171717 导致选中文字变黑色异常。
   useTheme.js 已在 Select/Cascader token 中显式覆盖，此处加全局 CSS 兜底
   保证所有"派生自 Select 的组件"都生效（如 Pagination showSizeChanger 内嵌的 Select）。
   浅色：淡蓝底 + 蓝字；深色：深蓝底 + 亮蓝字。使用 CSS 变量自动适配双模式。 */
.ant-select-item-option-selected,
.ant-cascader-menu-item-selected,
.ant-tree-select-tree-node-selected {
  background-color: var(--color-info-bg) !important;
  color: var(--color-link) !important;
  font-weight: 500;
}
.ant-select-item-option-selected:hover,
.ant-cascader-menu-item-selected:hover {
  background-color: var(--color-link-bg-soft) !important;
}
.ant-select-item-option-active:not(.ant-select-item-option-selected),
.ant-cascader-menu-item-active:not(.ant-cascader-menu-item-selected) {
  background-color: var(--color-info-bg) !important;
}
/* Select 已选中项在控件内显示的文字色（即折叠态显示的当前值） */
.ant-select-multiple .ant-select-selection-item,
.ant-select-single.ant-select-show-arrow .ant-select-selection-item {
  color: var(--color-ink);
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
