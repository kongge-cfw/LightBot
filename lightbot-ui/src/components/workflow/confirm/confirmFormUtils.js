/** 人工确认表单字段工具（节点配置 / 测试运行 / 对话共用） */

export const CONFIRM_FIELD_TYPES = [
  { value: 'info', label: '展示信息' },
  { value: 'text', label: '文本' },
  { value: 'textarea', label: '多行文本' },
  { value: 'number', label: '数字' },
  { value: 'radio', label: '单选' },
  { value: 'select', label: '下拉' },
]

export function normalizeConfirmOptions(options) {
  if (!Array.isArray(options)) return []
  return options
    .map(opt => {
      if (typeof opt === 'string') return { label: opt, value: opt }
      return { label: opt.label ?? opt.value, value: opt.value ?? opt.label }
    })
    .filter(opt => opt.value != null && String(opt.value).trim() !== '')
}

/** 确保 field.options 为数组（单选/下拉编辑用） */
export function ensureConfirmOptionsArray(field) {
  if (!field) return []
  if (!Array.isArray(field.options)) {
    field.options = []
  }
  return field.options
}

export function getConfirmOptionText(option) {
  if (typeof option === 'string') return option
  if (option && typeof option === 'object') {
    return option.label ?? option.value ?? ''
  }
  return ''
}

export function setConfirmOptionAt(field, index, text) {
  const list = ensureConfirmOptionsArray(field)
  list[index] = text ?? ''
}

export function addConfirmOption(field) {
  ensureConfirmOptionsArray(field).push('')
}

export function removeConfirmOptionAt(field, index) {
  ensureConfirmOptionsArray(field).splice(index, 1)
}

/** 保存前过滤空选项 */
export function sanitizeConfirmOptions(field) {
  if (!field || !Array.isArray(field.options)) return
  field.options = field.options
    .map(opt => getConfirmOptionText(opt).trim())
    .filter(Boolean)
}

/** info 类型展示文案：优先 label，其次 defaultValue */
export function getConfirmInfoText(field) {
  if (!field) return ''
  return field.label || field.defaultValue || field.key || ''
}

export function buildConfirmSubmittedEntries(formFields, submittedData) {
  const fields = Array.isArray(formFields) ? formFields : []
  const data = submittedData || {}
  return fields
    .filter(f => f?.type !== 'info' && f?.key)
    .map(f => ({
      key: f.key,
      label: f.label || f.key,
      value: data[f.key] != null ? String(data[f.key]) : '—',
    }))
}
