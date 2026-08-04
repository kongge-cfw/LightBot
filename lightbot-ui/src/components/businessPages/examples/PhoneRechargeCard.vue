<template>
  <div class="prc-card" :class="{ 'is-done': done }">
    <div class="prc-header">
      <div class="prc-title-wrap">
        <MobileOutlined class="prc-icon" />
        <div>
          <div class="prc-title">{{ title }}</div>
          <div class="prc-sub">{{ hint }}</div>
        </div>
      </div>
      <a-tag v-if="carrier" color="blue">{{ carrier }}</a-tag>
    </div>

    <template v-if="!done">
      <div class="prc-field">
        <label>手机号码</label>
        <a-input v-model:value="phone" placeholder="请输入11位手机号" maxlength="11" allow-clear :disabled="submitting" />
      </div>
      <div class="prc-field">
        <label>充值金额（元）</label>
        <div class="prc-amounts">
          <button
            v-for="amt in suggestedAmounts"
            :key="amt"
            type="button"
            class="prc-amount-btn"
            :class="{ active: Number(amount) === Number(amt) }"
            :disabled="submitting"
            @click="amount = amt"
          >¥{{ amt }}</button>
        </div>
        <a-input-number
          v-model:value="amount"
          class="prc-amount-input"
          :min="minAmount"
          :max="maxAmount"
          :precision="0"
          :disabled="submitting"
          placeholder="自定义金额"
        />
      </div>
      <div v-if="showInvoice" class="prc-invoice">
        <a-checkbox v-model:checked="needInvoice" :disabled="submitting">需要电子发票</a-checkbox>
      </div>
      <div class="prc-actions">
        <a-button v-if="canCancel" :disabled="submitting" @click="onCancel">{{ cancelButtonText }}</a-button>
        <a-button v-if="canSubmit" type="primary" :loading="submitting" :disabled="!canSubmitNow" @click="onSubmit">
          {{ primaryButtonText }}
        </a-button>
      </div>
      <div v-if="errorMsg" class="prc-error">{{ errorMsg }}</div>
    </template>

    <div v-else class="prc-success">
      <CheckCircleFilled class="prc-success-icon" />
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { MobileOutlined, CheckCircleFilled } from '@ant-design/icons-vue'
import { useBusinessPageSubmit } from '../useBusinessPageSubmit'

const props = defineProps({
  payload: { type: Object, required: true },
  messageIndex: { type: Number, default: -1 },
  workflowMode: { type: Boolean, default: false },
})
const emit = defineEmits(['workflow-submit', 'workflow-cancel'])

const pageProps = computed(() => props.payload?.props || {})
const options = computed(() => props.payload?.options || {})
const actions = computed(() => Array.isArray(props.payload?.actions) ? props.payload.actions : [])
const title = computed(() => props.payload?.title || '手机话费充值')
const hint = computed(() => options.value.hint || '固化模板')
const carrier = computed(() => pageProps.value.carrier || '')
const primaryButtonText = computed(() => options.value.primaryButtonText || '确认充值')
const cancelButtonText = computed(() => options.value.cancelButtonText || '取消')
const showInvoice = computed(() => options.value.showInvoice === true || options.value.showInvoice === 'true')
const canSubmit = computed(() => actions.value.includes('submit'))
const canCancel = computed(() => actions.value.includes('cancel'))
const suggestedAmounts = computed(() => {
  const list = pageProps.value.suggestedAmounts
  if (!Array.isArray(list) || !list.length) return [50, 100, 200]
  return list.map(Number).filter((n) => Number.isFinite(n) && n > 0).slice(0, 6)
})
const minAmount = computed(() => Number(pageProps.value.minAmount) > 0 ? Number(pageProps.value.minAmount) : 10)
const maxAmount = computed(() => Number(pageProps.value.maxAmount) > 0 ? Number(pageProps.value.maxAmount) : 500)

const phone = ref('')
const amount = ref(100)
const needInvoice = ref(false)
const errorMsg = ref('')
const { submitting, done, submitResult, cancelResult } = useBusinessPageSubmit(props, emit)

watch(() => pageProps.value, (p) => {
  if (p?.phone) phone.value = String(p.phone).replace(/\D/g, '').slice(0, 11)
  const first = suggestedAmounts.value[0]
  if (first) amount.value = first
}, { immediate: true, deep: true })

const canSubmitNow = computed(() => /^1\d{10}$/.test(String(phone.value || ''))
  && Number(amount.value) >= minAmount.value
  && Number(amount.value) <= maxAmount.value)

async function onSubmit() {
  errorMsg.value = ''
  if (!canSubmitNow.value) {
    errorMsg.value = '请填写正确的手机号与金额'
    return
  }
  await submitResult({
    action: 'submit',
    pageType: props.payload?.pageType || 'phone_recharge',
    phone: String(phone.value),
    amount: Number(amount.value),
    needInvoice: !!needInvoice.value,
  })
}

async function onCancel() {
  await cancelResult({ action: 'cancel', pageType: props.payload?.pageType || 'phone_recharge' })
}
</script>

<style scoped>
.prc-card {
  border: 1px solid var(--border-color, #e5e7eb);
  border-radius: 14px;
  background: linear-gradient(180deg, #f8fbff 0%, #fff 48%);
  padding: 16px;
  max-width: 420px;
}
.prc-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-bottom: 14px; }
.prc-title-wrap { display: flex; gap: 10px; align-items: flex-start; }
.prc-icon { font-size: 22px; color: #1677ff; margin-top: 2px; }
.prc-title { font-size: 16px; font-weight: 600; }
.prc-sub { margin-top: 2px; font-size: 12px; color: #6b7280; }
.prc-field { margin-bottom: 14px; }
.prc-field label { display: block; margin-bottom: 6px; font-size: 13px; color: #4b5563; }
.prc-amounts { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 8px; }
.prc-amount-btn {
  border: 1px solid #d9d9d9; background: #fff; border-radius: 8px; padding: 6px 14px; cursor: pointer; font-size: 13px;
}
.prc-amount-btn.active { border-color: #1677ff; background: #e6f4ff; color: #1677ff; font-weight: 600; }
.prc-amount-input { width: 100%; }
.prc-invoice { margin-bottom: 14px; }
.prc-actions { display: flex; justify-content: flex-end; gap: 8px; }
.prc-error { margin-top: 10px; font-size: 12px; color: #cf1322; }
.prc-success { text-align: center; padding: 12px 8px 4px; }
.prc-success-icon { font-size: 36px; color: #52c41a; }
.prc-success-title { margin-top: 8px; font-size: 15px; font-weight: 600; }
</style>
