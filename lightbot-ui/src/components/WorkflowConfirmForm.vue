<template>
  <div v-if="confirmForm" class="workflow-confirm-form" :class="{ resolved: readonly }">
    <a-alert
      :type="readonly ? 'success' : 'warning'"
      show-icon
      :message="readonly ? '已提交' : '等待您的选择'"
      :description="confirmForm.message || '请查看以下信息并做出选择'"
      class="confirm-alert"
    />
    <div v-if="readonly && submittedEntries.length" class="submitted-summary">
      <div v-for="row in submittedEntries" :key="row.key" class="submitted-row">
        <span class="submitted-label">{{ row.label }}</span>
        <span class="submitted-value">{{ row.value }}</span>
      </div>
    </div>
    <a-form v-else layout="vertical" class="confirm-fields">
      <template v-for="field in formFields" :key="field.key">
        <div v-if="field.type === 'info'" class="info-field">
          {{ field.label || field.defaultValue || field.key }}
        </div>
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
            <a-radio v-for="opt in normalizeOptions(field.options)" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </a-radio>
          </a-radio-group>
          <a-select
            v-else-if="field.type === 'select'"
            v-model:value="formValues[field.key]"
            :placeholder="`请选择${field.label || field.key}`"
            :options="normalizeOptions(field.options)"
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
</template>

<script setup>
import { reactive, watch, computed } from 'vue'

const props = defineProps({
  confirmForm: { type: Object, default: null },
  submitting: { type: Boolean, default: false },
  /** 已提交只读回显 */
  readonly: { type: Boolean, default: false },
  submittedData: { type: Object, default: null },
})

const emit = defineEmits(['submit'])

const formValues = reactive({})

const formFields = computed(() => {
  const fields = props.confirmForm?.formFields
  return Array.isArray(fields) ? fields.filter(f => f?.key || f?.type === 'info') : []
})

const submittedEntries = computed(() => {
  const data = props.submittedData || {}
  return formFields.value
    .filter(f => f.type !== 'info' && f.key)
    .map(f => ({
      key: f.key,
      label: f.label || f.key,
      value: data[f.key] != null ? String(data[f.key]) : '—',
    }))
})

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

function normalizeOptions(options) {
  if (!Array.isArray(options)) return []
  return options.map(opt => {
    if (typeof opt === 'string') return { label: opt, value: opt }
    return { label: opt.label ?? opt.value, value: opt.value ?? opt.label }
  })
}

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
  padding: 12px;
  border: 1px solid #fed7aa;
  border-radius: 8px;
  background: #fff7ed;
}
.workflow-confirm-form.resolved {
  border-color: #bbf7d0;
  background: #f0fdf4;
}
.confirm-alert { margin-bottom: 12px; }
.confirm-fields { margin-top: 4px; }
.info-field {
  margin-bottom: 10px;
  padding: 8px 10px;
  border-radius: 6px;
  background: #fff;
  border: 1px dashed #fdba74;
  font-size: 13px;
  line-height: 1.55;
  color: var(--color-text-dark);
}
.confirm-radio-group { display: flex; flex-direction: column; gap: 6px; }
.submitted-summary { display: flex; flex-direction: column; gap: 6px; }
.submitted-row {
  display: flex; gap: 8px; font-size: 13px;
  padding: 6px 8px; background: #fff; border-radius: 6px;
}
.submitted-label { color: var(--color-mute); min-width: 72px; }
.submitted-value { color: var(--color-text-dark); font-weight: 500; }
</style>
