<template>
  <div class="ubp-card" :class="{ 'is-done': done }">
    <div class="ubp-header">
      <ThunderboltOutlined class="ubp-icon" />
      <div>
        <div class="ubp-title">{{ title }}</div>
        <div class="ubp-sub">{{ hint }}</div>
      </div>
    </div>

    <template v-if="!done">
      <div class="ubp-field">
        <label>缴费类型</label>
        <a-radio-group v-model:value="billType" :disabled="submitting" button-style="solid">
          <a-radio-button value="electricity">电费</a-radio-button>
          <a-radio-button value="water">水费</a-radio-button>
          <a-radio-button value="gas">燃气</a-radio-button>
        </a-radio-group>
      </div>
      <div class="ubp-field">
        <label>户号</label>
        <a-input v-model:value="accountNo" placeholder="请输入缴费户号" :disabled="submitting" allow-clear />
      </div>
      <div class="ubp-field">
        <label>地址（可选）</label>
        <a-input v-model:value="address" placeholder="服务地址" :disabled="submitting" allow-clear />
      </div>
      <div class="ubp-field">
        <label>缴费金额（元）</label>
        <div class="ubp-amounts">
          <button
            v-for="amt in suggestedAmounts"
            :key="amt"
            type="button"
            class="ubp-amt"
            :class="{ active: Number(amount) === Number(amt) }"
            :disabled="submitting"
            @click="amount = amt"
          >¥{{ amt }}</button>
        </div>
        <a-input-number v-model:value="amount" :min="1" :max="20000" style="width: 100%" :disabled="submitting" />
      </div>
      <div class="ubp-actions">
        <a-button v-if="canCancel" :disabled="submitting" @click="emitCancel">{{ cancelText }}</a-button>
        <a-button v-if="canSubmit" type="primary" :loading="submitting" :disabled="!canSubmitNow" @click="emitSubmit">
          {{ primaryText }}
        </a-button>
      </div>
      <div v-if="errorMsg" class="ubp-error">{{ errorMsg }}</div>
    </template>
    <div v-else class="ubp-done">
      <CheckCircleFilled />
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ThunderboltOutlined, CheckCircleFilled } from '@ant-design/icons-vue'
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
const title = computed(() => props.payload?.title || '水电燃气缴费')
const hint = computed(() => options.value.hint || '固化模板')
const primaryText = computed(() => options.value.primaryButtonText || '确认缴费')
const cancelText = computed(() => options.value.cancelButtonText || '取消')
const canSubmit = computed(() => actions.value.includes('submit'))
const canCancel = computed(() => actions.value.includes('cancel'))
const suggestedAmounts = computed(() => {
  const list = pageProps.value.suggestedAmounts
  if (!Array.isArray(list) || !list.length) return [100, 200, 500]
  return list.map(Number).filter((n) => Number.isFinite(n) && n > 0).slice(0, 6)
})

const billType = ref('electricity')
const accountNo = ref('')
const address = ref('')
const amount = ref(100)
const errorMsg = ref('')
const { submitting, done, submitResult, cancelResult } = useBusinessPageSubmit(props, emit)

watch(() => pageProps.value, (p) => {
  if (p?.billType) billType.value = String(p.billType)
  if (p?.accountNo) accountNo.value = String(p.accountNo)
  if (p?.address) address.value = String(p.address)
  if (p?.amount) amount.value = Number(p.amount)
  else if (suggestedAmounts.value[0]) amount.value = suggestedAmounts.value[0]
}, { immediate: true, deep: true })

const canSubmitNow = computed(() => String(accountNo.value || '').trim().length >= 4 && Number(amount.value) > 0)

async function emitSubmit() {
  errorMsg.value = ''
  if (!canSubmitNow.value) {
    errorMsg.value = '请填写户号与金额'
    return
  }
  await submitResult({
    action: 'submit',
    pageType: props.payload?.pageType,
    billType: billType.value,
    accountNo: String(accountNo.value).trim(),
    address: String(address.value || '').trim(),
    amount: Number(amount.value),
  })
}

async function emitCancel() {
  await cancelResult({ action: 'cancel', pageType: props.payload?.pageType })
}
</script>

<style scoped>
.ubp-card {
  border: 1px solid var(--border-color, #e5e7eb);
  border-radius: 14px;
  padding: 16px;
  max-width: 420px;
  background: linear-gradient(180deg, #f7faf7 0%, #fff 50%);
}
.ubp-header { display: flex; gap: 10px; margin-bottom: 14px; }
.ubp-icon { font-size: 22px; color: #389e0d; margin-top: 2px; }
.ubp-title { font-weight: 600; font-size: 16px; }
.ubp-sub { font-size: 12px; color: #6b7280; margin-top: 2px; }
.ubp-field { margin-bottom: 12px; }
.ubp-field label { display: block; margin-bottom: 6px; font-size: 13px; color: #4b5563; }
.ubp-amounts { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 8px; }
.ubp-amt {
  border: 1px solid #d9d9d9; background: #fff; border-radius: 8px; padding: 6px 12px; cursor: pointer;
}
.ubp-amt.active { border-color: #389e0d; background: #f6ffed; color: #389e0d; font-weight: 600; }
.ubp-actions { display: flex; justify-content: flex-end; gap: 8px; }
.ubp-error { margin-top: 8px; color: #cf1322; font-size: 12px; }
.ubp-done { text-align: center; color: #389e0d; display: flex; flex-direction: column; gap: 8px; align-items: center; padding: 12px 0; }
</style>
