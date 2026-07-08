/** Debug Lab 顶部导航模块（可扩展，纯前端调试项） */
export const DEBUG_LAB_NAV_ITEMS = [
  { key: 'composer', label: '消息组合' },
  { key: 'tool', label: '工具渲染' },
  { key: 'markdown', label: 'Markdown' },
  { key: 'registry', label: '注册表', disabled: true, title: 'Phase 3' },
  { key: 'capability', label: '能力块', disabled: true, title: 'Phase 3' },
  { key: 'workflow', label: '工作流', disabled: true, title: 'Phase 3' },
  { key: 'theme', label: '主题样式', disabled: true, title: '规划中' },
]

export function getDebugLabNavMenuItems() {
  return DEBUG_LAB_NAV_ITEMS.map((item) => ({
    key: item.key,
    label: item.label,
    disabled: !!item.disabled,
    title: item.title,
  }))
}
