<template>
  <div class="form-designer">
    <aside class="field-panel">
      <div class="field-panel__title">拖拽添加字段</div>
      <div
        v-for="item in FIELD_TYPES"
        :key="item.type"
        class="field-item"
        draggable="true"
        @dragstart="onDragStart($event, item)"
      >
        {{ item.label }}
      </div>
    </aside>

    <div
      class="canvas"
      :class="{ 'canvas--drag-over': dragOver }"
      @dragover.prevent="dragOver = true"
      @dragleave="dragOver = false"
      @drop.prevent="onDrop"
    >
      <div v-if="customFields.length === 0" class="canvas__hint">
        从左侧拖拽字段到此处；底部系统字段固定且不可修改
      </div>
      <div class="canvas__list">
        <div
          v-for="(field, idx) in fields"
          :key="field._id"
          :class="['field-row', field.system ? 'field-row--system' : 'field-row--custom']"
        >
          <span class="field-row__index">{{ idx + 1 }}.</span>

          <template v-if="field.system">
            <span class="type-badge type-badge--system">系统</span>
            <span class="type-badge">日期时间</span>
            <span class="field-row__label-text">{{ field.label }}</span>
            <span class="field-row__hint">精确到时分秒 · 自动写入 · 不可调整</span>
            <LockOutlined class="field-row__lock" />
          </template>

          <template v-else>
            <div class="field-row__actions">
              <button
                type="button"
                class="btn-link"
                :disabled="!canMoveUp(idx)"
                @click="moveField(idx, -1)"
              >上移</button>
              <button
                type="button"
                class="btn-link"
                :disabled="!canMoveDown(idx)"
                @click="moveField(idx, 1)"
              >下移</button>
            </div>
            <span class="type-badge">{{ getTypeLabel(field.type) }}</span>
            <a-input
              :value="field.label"
              placeholder="字段名称（中文/字母/数字/下划线，≤20）"
              :maxlength="20"
              show-count
              class="field-row__label"
              @update:value="(v) => (field.label = filterLabel(v ?? ''))"
            />
            <a-input
              v-model:value="field.description"
              placeholder="字段描述（供大模型分析 / DDL 备注）"
              :maxlength="100"
              show-count
              class="field-row__description"
              allow-clear
            />
            <a-checkbox v-model:checked="field.required" class="field-row__required">必填</a-checkbox>
            <div class="field-row__extra">
              <template v-if="['select', 'radio', 'checkbox'].includes(field.type)">
                <button type="button" class="lb-btn lb-btn--sm" @click="openOptionsDialog(field)">
                  {{ field.options.length ? `已配置 ${field.options.length} 项` : '配置选项' }}
                </button>
              </template>
              <template v-else-if="field.type === 'number'">
                <a-input-number v-model:value="field.props.min" placeholder="最小值" class="field-row__num" />
                <a-input-number v-model:value="field.props.max" placeholder="最大值" class="field-row__num" />
              </template>
              <template v-else-if="field.type === 'upload'">
                <span class="field-row__hint">最多</span>
                <a-input-number v-model:value="field.props.limit" :min="1" :max="50" class="field-row__num" />
                <span class="field-row__hint">个文件</span>
              </template>
            </div>
            <button type="button" class="btn-link btn-link--danger field-row__delete" @click="removeField(idx)">删除</button>
          </template>
        </div>
      </div>
    </div>

    <a-modal
      v-model:open="optionsDialog.visible"
      title="配置选项"
      :width="480"
      destroy-on-close
      @ok="confirmOptions"
      @cancel="optionsDialog.visible = false"
    >
      <div class="options-list">
        <div v-for="(_, optionIndex) in optionsDialog.list" :key="optionIndex" class="options-list__row">
          <a-input v-model:value="optionsDialog.list[optionIndex]" placeholder="请输入选项内容" />
          <button type="button" class="btn-link btn-link--danger options-list__remove" @click="removeOption(optionIndex)">删除</button>
        </div>
      </div>
      <button type="button" class="lb-btn options-list__add" @click="addOption">新增选项</button>
    </a-modal>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { LockOutlined } from '@ant-design/icons-vue'

const FIELD_TYPES = [
  { type: 'input', label: '单行文本' },
  { type: 'textarea', label: '多行文本' },
  { type: 'number', label: '数字' },
  { type: 'date', label: '日期' },
  { type: 'datetime', label: '日期时间' },
  { type: 'select', label: '下拉选择' },
  { type: 'radio', label: '单选' },
  { type: 'checkbox', label: '多选' },
  { type: 'upload', label: '附件' },
]

/** 系统固定字段：始终存在、精确到秒、不可调整 */
const SYSTEM_FIELD_DEFS = [
  {
    key: 'createTime',
    label: '创建时间',
    type: 'datetime',
    required: true,
    system: true,
    props: { precision: 'second', format: 'YYYY-MM-DD HH:mm:ss', readonly: true },
  },
  {
    key: 'updateTime',
    label: '更新时间',
    type: 'datetime',
    required: true,
    system: true,
    props: { precision: 'second', format: 'YYYY-MM-DD HH:mm:ss', readonly: true },
  },
]

const SYSTEM_KEYS = new Set(SYSTEM_FIELD_DEFS.map((f) => f.key))

const props = defineProps({
  modelValue: { type: Object, default: () => ({ fields: [] }) },
})

const dragOver = ref(false)
const fields = ref([])
let dragType = null
let idSeed = 0

const optionsDialog = reactive({
  visible: false,
  field: null,
  list: [],
})

const customFields = computed(() => fields.value.filter((f) => !f.system))

const LABEL_REG = /[\u4e00-\u9fa5a-zA-Z0-9_]/g

function filterLabel(value) {
  if (value == null || typeof value !== 'string') return ''
  return value.match(LABEL_REG)?.join('')?.slice(0, 20) ?? ''
}

function getTypeLabel(type) {
  return FIELD_TYPES.find((item) => item.type === type)?.label || type || '-'
}

function genId() {
  idSeed += 1
  return `f_${idSeed}_${Date.now()}`
}

function buildSystemFields() {
  return SYSTEM_FIELD_DEFS.map((def) => ({
    _id: `sys_${def.key}`,
    _key: def.key,
    label: def.label,
    type: def.type,
    required: def.required,
    system: true,
    options: [],
    props: { ...def.props },
  }))
}

function ensureSystemFields(list) {
  const custom = list.filter((f) => !f.system && !SYSTEM_KEYS.has(f._key))
  return [...custom, ...buildSystemFields()]
}

function onDragStart(event, item) {
  dragType = item
  event.dataTransfer.effectAllowed = 'copy'
  event.dataTransfer.setData('text/plain', item.type)
}

function onDrop() {
  dragOver.value = false
  if (!dragType) return
  const hasOptions = ['select', 'radio', 'checkbox'].includes(dragType.type)
  let initProps = {}
  if (dragType.type === 'number') initProps = { min: undefined, max: undefined }
  if (dragType.type === 'upload') initProps = { limit: 10 }
  const custom = fields.value.filter((f) => !f.system)
  custom.push({
    _id: genId(),
    _key: '',
    label: dragType.label,
    description: '',
    type: dragType.type,
    required: false,
    system: false,
    options: hasOptions ? [] : [],
    props: initProps,
  })
  fields.value = ensureSystemFields(custom)
  dragType = null
}

function removeField(index) {
  const field = fields.value[index]
  if (!field || field.system) return
  const next = fields.value.filter((_, i) => i !== index)
  fields.value = ensureSystemFields(next)
}

function canMoveUp(index) {
  const field = fields.value[index]
  if (!field || field.system) return false
  return index > 0 && !fields.value[index - 1]?.system
}

function canMoveDown(index) {
  const field = fields.value[index]
  if (!field || field.system) return false
  const next = fields.value[index + 1]
  return next && !next.system
}

function moveField(index, delta) {
  if (delta < 0 && !canMoveUp(index)) return
  if (delta > 0 && !canMoveDown(index)) return
  const next = index + delta
  const list = [...fields.value]
  const current = list[index]
  list[index] = list[next]
  list[next] = current
  fields.value = ensureSystemFields(list)
}

function openOptionsDialog(field) {
  if (field.system) return
  optionsDialog.field = field
  const options = field.options || []
  optionsDialog.list = options.length
    ? options.map((item) => String(item.label ?? item.value ?? ''))
    : ['']
  optionsDialog.visible = true
}

function addOption() {
  optionsDialog.list.push('')
}

function removeOption(index) {
  optionsDialog.list.splice(index, 1)
}

function confirmOptions() {
  if (!optionsDialog.field || optionsDialog.field.system) return
  optionsDialog.field.options = optionsDialog.list
    .map((item) => String(item || '').trim())
    .filter(Boolean)
    .map((text) => ({ value: text, label: text }))
  optionsDialog.visible = false
}

function resolveFieldKey(field, index) {
  if (field._key && String(field._key).trim()) return String(field._key).trim()
  const label = (field.label || '').trim()
  if (/^[a-zA-Z_][a-zA-Z0-9_]*$/.test(label)) return label
  return `field_${index + 1}`
}

function schemaFromFields() {
  return fields.value.map((field, index) => {
    const result = {
      key: resolveFieldKey(field, index),
      label: field.label || '未命名',
      type: field.type,
      required: !!field.required,
    }
    if (field.system) {
      result.system = true
      result.props = {
        precision: 'second',
        format: 'YYYY-MM-DD HH:mm:ss',
        readonly: true,
      }
      return result
    }
    const desc = String(field.description || '').trim()
    if (desc) {
      result.description = desc.slice(0, 100)
    }
    if (field.props && Object.keys(field.props).length) {
      result.props = { ...field.props }
    }
    if (field.type === 'upload' && (!result.props || result.props.limit == null)) {
      result.props = { ...(result.props || {}), limit: 10 }
    }
    if (['select', 'radio', 'checkbox'].includes(field.type) && Array.isArray(field.options) && field.options.length) {
      result.props = {
        ...(result.props || {}),
        options: field.options.map((item) => ({ value: item.value, label: item.label ?? item.value })),
      }
    }
    return result
  })
}

function mapRawField(field, index) {
  const options = Array.isArray(field.props?.options)
    ? field.props.options.map((item) => ({ value: item.value ?? '', label: item.label ?? item.value ?? '' }))
    : []
  let fieldProps = {}
  if (field.type === 'number' && field.props) fieldProps = { min: field.props.min, max: field.props.max }
  if (field.type === 'upload' && field.props) fieldProps = { limit: field.props.limit ?? 10 }
  if (field.type === 'datetime' && field.props) fieldProps = { ...field.props }
  return {
    _id: field.key || `field_${index}`,
    _key: field.key || '',
    label: field.label || '',
    description: field.description || '',
    type: field.type || 'input',
    required: !!field.required,
    system: !!field.system || SYSTEM_KEYS.has(field.key),
    options,
    props: fieldProps,
  }
}

watch(
  () => props.modelValue,
  (value) => {
    const raw = (value?.fields || []).filter((f) => !SYSTEM_KEYS.has(f.key) && !f.system)
    const mapped = raw.map((field, index) => mapRawField(field, index))
    fields.value = ensureSystemFields(mapped)
  },
  { immediate: true, deep: true },
)

defineExpose({
  getSchema() {
    return { fields: schemaFromFields() }
  },
  /** 供「索引与检索」读取当前字段快照（含系统字段） */
  getFields() {
    return schemaFromFields()
  },
})
</script>

<style scoped>
.form-designer {
  display: flex;
  gap: 16px;
  min-height: 0;
  height: 100%;
  flex: 1;
}
.field-panel {
  width: 168px;
  flex-shrink: 0;
  align-self: stretch;
  border: 1px solid var(--color-hairline);
  border-radius: 16px;
  padding: 12px;
  background: var(--color-canvas-soft);
  overflow-y: auto;
}
.field-panel__title {
  margin-bottom: 10px;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-mute);
  letter-spacing: 0.04em;
  text-transform: uppercase;
}
.field-item {
  margin-bottom: 8px;
  padding: 8px 12px;
  border: 1px solid var(--color-hairline);
  border-radius: 8px;
  background: var(--color-canvas);
  font-size: 13px;
  color: var(--color-ink);
  cursor: grab;
  transition: border-color 0.15s ease, background-color 0.15s ease, transform 0.15s ease;
}
.field-item:hover {
  border-color: var(--color-ink);
  background: var(--color-canvas-soft-2);
}
.field-item:active {
  cursor: grabbing;
  transform: scale(0.98);
}
.canvas {
  flex: 1;
  min-width: 0;
  min-height: 0;
  align-self: stretch;
  border: 1px solid var(--color-hairline);
  border-radius: 16px;
  padding: 16px;
  background: var(--color-canvas);
  overflow: auto;
  transition: border-color 0.15s ease, background-color 0.15s ease;
}
.canvas--drag-over {
  border-style: dashed;
  border-color: var(--color-link);
  background: var(--color-info-bg);
}
.canvas__hint {
  margin-bottom: 12px;
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--color-canvas-soft);
  color: var(--color-mute);
  font-size: 13px;
}
.canvas__list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.field-row {
  align-items: center;
  gap: 8px;
  padding: 12px;
  border: 1px solid var(--color-hairline);
  border-radius: 12px;
  background: var(--color-canvas-soft);
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}
.field-row:hover {
  border-color: var(--color-hairline-strong);
}
/* 自定义字段：固定列宽，保证名称/描述输入框跨行对齐 */
.field-row--custom {
  display: grid;
  grid-template-columns:
    28px
    72px
    88px
    minmax(160px, 200px)
    minmax(180px, 1fr)
    56px
    minmax(0, 220px)
    40px;
}
.field-row--system {
  display: flex;
  flex-wrap: wrap;
  background: var(--color-canvas-soft-2);
  border-style: dashed;
  opacity: 0.95;
}
.field-row__index {
  color: var(--color-mute);
  font-size: 13px;
  width: 28px;
  text-align: right;
}
.field-row__actions {
  display: flex;
  gap: 2px;
  width: 72px;
  flex-shrink: 0;
}
.field-row__label {
  width: 100% !important;
  min-width: 0;
}
.field-row__label :deep(.ant-input) {
  width: 100%;
}
.field-row__description {
  width: 100% !important;
  min-width: 0;
}
.field-row__description :deep(.ant-input) {
  width: 100%;
}
.field-row__required {
  white-space: nowrap;
  justify-self: start;
}
.field-row__extra {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex-wrap: wrap;
}
.field-row__num {
  width: 100px !important;
}
.field-row__label-text {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-ink);
  min-width: 72px;
}
.field-row__hint {
  font-size: 12px;
  color: var(--color-mute);
  white-space: nowrap;
}
.field-row__lock {
  margin-left: auto;
  color: var(--color-mute);
  font-size: 14px;
}
.field-row__delete {
  justify-self: end;
  white-space: nowrap;
}
.type-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 88px;
  box-sizing: border-box;
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--color-canvas-soft-2);
  color: var(--color-body);
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.type-badge--system {
  background: var(--color-canvas-soft-3);
  color: var(--color-mute);
}
.btn-link {
  border: none;
  background: transparent;
  color: var(--color-link);
  font-size: 13px;
  cursor: pointer;
  padding: 0 4px;
}
.btn-link:disabled {
  color: var(--color-mute);
  cursor: not-allowed;
  opacity: 0.5;
}
.btn-link--danger {
  color: var(--color-error);
}
.options-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 260px;
  overflow-y: auto;
}
.options-list__row {
  display: flex;
  gap: 8px;
  align-items: center;
}
.options-list__row :deep(.ant-input) {
  flex: 1;
  min-width: 0;
}
.options-list__remove {
  flex-shrink: 0;
  white-space: nowrap;
}
.options-list__add {
  width: 100%;
  margin-top: 12px;
}
</style>
