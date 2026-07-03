<template>
  <div v-if="confirmForm" class="workflow-confirm-form" :class="{ resolved: readonly, collapsed: collapsible && !expanded }">
    <button
      v-if="collapsible"
      type="button"
      class="confirm-form-toggle"
      :aria-expanded="expanded"
      @click="expanded = !expanded"
    >
      <span class="confirm-form-toggle-left">
        <WarningOutlined v-if="!readonly" class="confirm-form-toggle-icon pending" />
        <CheckCircleOutlined v-else class="confirm-form-toggle-icon done" />
        <span class="confirm-form-toggle-title">{{ readonly ? '人工确认已提交' : '等待人工确认' }}</span>
        <span v-if="!expanded && !readonly" class="confirm-form-toggle-hint">点击展开填写</span>
      </span>
      <RightOutlined class="confirm-form-expand-icon" :class="{ expanded }" />
    </button>

    <div v-show="!collapsible || expanded" class="confirm-form-body">
      <a-alert
        v-if="!collapsible"
        :type="readonly ? 'success' : 'warning'"
        show-icon
        :message="readonly ? '已提交' : '等待人工确认'"
        :description="confirmForm.message || '请查看以下信息并做出选择'"
        class="confirm-alert"
      />
      <div v-else-if="confirmForm.message" class="confirm-form-message">
        {{ confirmForm.message }}
      </div>
      <WorkflowConfirmSubmittedSummary
        v-if="readonly && submittedEntries.length"
        :form-fields="formFields"
        :submitted-data="submittedData"
      />
      <a-form v-else layout="vertical" class="confirm-fields">
        <template v-for="field in formFields" :key="field.key || field.label">
          <WorkflowConfirmInfoBlock v-if="field.type === 'info'" :field="field" />
          <a-form-item
            v-else
            :label="field.label || field.key"
            :required="field.required"
          >
            <a-radio-group
              v-if="field.type === 'radio'"
              v-model:value="formValues[field.key]"
              class="confirm-radio-group"
            >
              <a-radio v-for="opt in normalizeConfirmOptions(field.options)" :key="opt.value" :value="opt.value">
                {{ opt.label }}
              </a-radio>
            </a-radio-group>
            <a-select
              v-else-if="field.type === 'select'"
              v-model:value="formValues[field.key]"
              :placeholder="`请选择${field.label || field.key}`"
              :options="normalizeConfirmOptions(field.options)"
              style="width: 100%"
            />
            <a-textarea
              v-else-if="field.type === 'textarea'"
              v-model:value="formValues[field.key]"
              :rows="3"
              :placeholder="field.label || field.key"
            />
            <a-input-number
              v-else-if="field.type === 'number'"
              v-model:value="formValues[field.key]"
              style="width: 100%"
            />
            <a-input
              v-else
              v-model:value="formValues[field.key]"
              :placeholder="field.label || field.key"
            />
          </a-form-item>
        </template>
        <a-form-item>
          <a-button type="primary" :loading="submitting" @click="handleSubmit">
            确认并继续
          </a-button>
        </a-form-item>
      </a-form>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, watch, computed } from 'vue'
import { CheckCircleOutlined, RightOutlined, WarningOutlined } from '@ant-design/icons-vue'
import WorkflowConfirmInfoBlock from './workflow/confirm/WorkflowConfirmInfoBlock.vue'
import WorkflowConfirmSubmittedSummary from './workflow/confirm/WorkflowConfirmSubmittedSummary.vue'
import { normalizeConfirmOptions, buildConfirmSubmittedEntries } from './workflow/confirm/confirmFormUtils.js'

const props = defineProps({
  confirmForm: { type: Object, default: null },
  submitting: { type: Boolean, default: false },
  /** 已提交只读回显 */
  readonly: { type: Boolean, default: false },
  submittedData: { type: Object, default: null },
  /** 是否支持展开/收起 */
  collapsible: { type: Boolean, default: true },
  /** 初始是否展开（待确认默认展开） */
  defaultExpanded: { type: Boolean, default: true },
})

const emit = defineEmits(['submit'])

const expanded = ref(props.defaultExpanded)

watch(
  () => props.confirmForm,
  () => {
    expanded.value = props.defaultExpanded
  }
)

const formValues = reactive({})

const formFields = computed(() => {
  const fields = props.confirmForm?.formFields
  return Array.isArray(fields) ? fields.filter(f => f?.key || f?.type === 'info') : []
})

const submittedEntries = computed(() =>
  buildConfirmSubmittedEntries(formFields.value, props.submittedData)
)

watch(
  () => props.confirmForm,
  (form) => {
    Object.keys(formValues).forEach(k => delete formValues[k])
    if (!form?.formFields) return
    for (const field of form.formFields) {
      if (field?.type === 'info') continue
      if (!field?.key) continue
      formValues[field.key] = field.defaultValue ?? (field.type === 'number' ? null : '')
    }
  },
  { immediate: true, deep: true }
)

function handleSubmit() {
  for (const field of formFields.value) {
    if (field.type === 'info' || !field.required) continue
    const val = formValues[field.key]
    if (val == null || String(val).trim() === '') {
      return
    }
  }
  emit('submit', { ...formValues })
}
</script>

<style scoped>
.workflow-confirm-form {
  margin-top: 12px;
  border: 1px solid var(--color-warning-soft);
  border-radius: 8px;
  background: var(--color-warn-bg);
  overflow: hidden;
}
.workflow-confirm-form.resolved {
  border-color: var(--green-200);
  background: var(--green-50);
}
.confirm-form-toggle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 10px 12px;
  border: none;
  background: transparent;
  cursor: pointer;
  text-align: left;
  color: inherit;
}
.confirm-form-toggle-left {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex: 1;
}
.confirm-form-toggle-icon.pending {
  color: var(--color-warning-deep, #b45309);
}
.confirm-form-toggle-icon.done {
  color: var(--green-600, #16a34a);
}
.confirm-form-toggle-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-dark);
}
.confirm-form-toggle-hint {
  font-size: 12px;
  color: var(--color-mute);
  margin-left: 4px;
}
.confirm-form-expand-icon {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--color-mute);
  transition: transform 0.2s ease;
}
.confirm-form-expand-icon.expanded {
  transform: rotate(90deg);
}
.confirm-form-body {
  padding: 0 12px 12px;
}
.confirm-form-message {
  margin-bottom: 10px;
  padding: 8px 10px;
  border-radius: 6px;
  background: var(--color-canvas, #fff);
  border: 1px solid var(--color-warning-soft);
  font-size: 13px;
  line-height: 1.5;
  color: var(--color-text-secondary);
  white-space: pre-wrap;
}
.confirm-alert { margin-bottom: 12px; }
.confirm-fields { margin-top: 4px; }
.confirm-radio-group { display: flex; flex-direction: column; gap: 6px; }
.workflow-confirm-form.collapsed .confirm-form-toggle {
  border-bottom: none;
}
</style>
