/**
 * Dashboard 图表主题色：从 CSS 变量读取，兼容亮/暗色。
 */
function cssVar(name, fallback) {
  if (typeof window === 'undefined') return fallback
  const v = getComputedStyle(document.documentElement).getPropertyValue(name)
  return v && v.trim() ? v.trim() : fallback
}

export function getChartColors() {
  return {
    ink: cssVar('--color-ink', '#1f2937'),
    mute: cssVar('--color-mute', '#6b7280'),
    hairline: cssVar('--color-hairline', '#e5e7eb'),
    canvas: cssVar('--color-canvas', '#ffffff'),
    primary: cssVar('--color-accent', '#2563eb') || '#2563eb',
    success: '#16a34a',
    warning: '#d97706',
    danger: '#dc2626',
    purple: '#7c3aed',
    cyan: '#0891b2',
    palette: ['#2563eb', '#16a34a', '#d97706', '#7c3aed', '#0891b2', '#dc2626', '#64748b', '#db2777'],
  }
}

export function baseTooltip() {
  const c = getChartColors()
  return {
    backgroundColor: c.canvas,
    borderColor: c.hairline,
    borderWidth: 1,
    textStyle: { color: c.ink, fontSize: 12 },
  }
}

export function axisStyle() {
  const c = getChartColors()
  return {
    axisLine: { lineStyle: { color: c.hairline } },
    axisLabel: { color: c.mute, fontSize: 11 },
    splitLine: { lineStyle: { color: c.hairline, type: 'dashed' } },
  }
}
