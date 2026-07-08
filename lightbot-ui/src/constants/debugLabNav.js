/** Debug Lab 顶部导航模块 */
export const DEBUG_LAB_NAV_ITEMS = [
  { key: 'composer', label: '消息组合' },
  { key: 'tool', label: '工具渲染' },
  { key: 'markdown', label: 'Markdown' },
  { key: 'capability', label: '能力块' },
  { key: 'workflow', label: '工作流' },
  { key: 'registry', label: '注册表' },
  { key: 'stream', label: '流式模拟' },
  { key: 'compare', label: '对比' },
  { key: 'sse', label: 'SSE 回放' },
  { key: 'theme', label: '主题样式' },
]

export function getDebugLabNavMenuItems() {
  return DEBUG_LAB_NAV_ITEMS.map((item) => ({
    key: item.key,
    label: item.label,
    disabled: !!item.disabled,
    title: item.title,
  }))
}
