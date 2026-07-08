/** Debug Lab 顶部导航（精简：核心调试模块） */
export const DEBUG_LAB_NAV_ITEMS = [
  { key: 'composer', label: '消息组合' },
  { key: 'tool', label: '工具渲染' },
  { key: 'markdown', label: 'Markdown' },
  { key: 'capability', label: '能力块' },
  { key: 'workflow', label: '工作流' },
]

export function getDebugLabNavMenuItems() {
  return DEBUG_LAB_NAV_ITEMS.map((item) => ({
    key: item.key,
    label: item.label,
  }))
}
