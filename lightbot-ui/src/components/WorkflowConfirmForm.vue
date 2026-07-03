<template>
  <div v-if="confirmForm" class="workflow-confirm-form" :class="{ resolved: readonly }">
    <a-alert
      :type="readonly ? 'success' : 'warning'"
      show-icon
      :message="readonly ? '已提交' : '等待人工确认'"
      :description="confirmForm.message || '请查看以下信息并做出选择'"
      class="confirm-alert"
    />
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
</template>

<script setup>
import { reactive, watch, computed } from 'vue'
import WorkflowConfirmInfoBlock from './workflow/confirm/WorkflowConfirmInfoBlock.vue'
import WorkflowConfirmSubmittedSummary from './workflow/confirm/WorkflowConfirmSubmittedSummary.vue'
import { normalizeConfirmOptions, buildConfirmSubmittedEntries } from './workflow/confirm/confirmFormUtils.js'

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
  padding: 12px;
  border: 1px solid var(--color-warning-soft);
  border-radius: 8px;
  background: var(--color-warn-bg);
}
.workflow-confirm-form.resolved {
  border-color: var(--green-200);
  background: var(--green-50);
}
.confirm-alert { margin-bottom: 12px; }
.confirm-fields { margin-top: 4px; }
.confirm-radio-group { display: flex; flex-direction: column; gap: 6px; }
</style>
