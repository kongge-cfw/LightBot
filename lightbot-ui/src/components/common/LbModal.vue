<template>
  <a-modal
    v-model:open="openProxy"
    :title="title"
    :width="width"
    :mask-closable="maskClosable"
    :destroy-on-close="destroyOnClose"
    :centered="centered"
    :footer="footer"
    :closable="closable"
    :wrap-class-name="wrapClassName"
    @cancel="onCancel"
    @ok="onOk"
  >
    <template v-if="$slots.title" #title>
      <slot name="title" />
    </template>
    <div :class="['lb-modal-body', { 'lb-modal-body--scroll': scrollable }]">
      <slot />
    </div>
    <template v-if="footer !== null" #footer>
      <slot name="footer">
        <LbDialogFooter
          :loading="loading"
          :confirm-text="confirmText"
          :cancel-text="cancelText"
          :loading-text="loadingText"
          :hide-cancel="hideCancel"
          :hide-confirm="hideConfirm"
          :confirm-disabled="confirmDisabled"
          @cancel="onCancel"
          @confirm="onConfirm"
        />
      </slot>
    </template>
  </a-modal>
</template>

<script setup>
import { computed } from 'vue'
import LbDialogFooter from './LbDialogFooter.vue'

const props = defineProps({
  open: { type: Boolean, default: false },
  title: { type: [String, Object], default: undefined },
  width: { type: [Number, String], default: 560 },
  maskClosable: { type: Boolean, default: false },
  destroyOnClose: { type: Boolean, default: false },
  centered: { type: Boolean, default: false },
  closable: { type: Boolean, default: true },
  scrollable: { type: Boolean, default: true },
  footer: { type: [String, Object], default: 'default' },
  wrapClassName: { type: String, default: '' },
  loading: { type: Boolean, default: false },
  confirmText: { type: String, default: '确定' },
  cancelText: { type: String, default: '取消' },
  loadingText: { type: String, default: '提交中...' },
  hideCancel: { type: Boolean, default: false },
  hideConfirm: { type: Boolean, default: false },
  confirmDisabled: { type: Boolean, default: false },
})

const emit = defineEmits(['update:open', 'cancel', 'ok', 'confirm'])

const openProxy = computed({
  get: () => props.open,
  set: (v) => emit('update:open', v),
})

function onCancel(e) {
  emit('cancel', e)
}

function onOk(e) {
  emit('ok', e)
}

function onConfirm() {
  emit('confirm')
}
</script>

<style scoped>
.lb-modal-body {
  width: 100%;
}
.lb-modal-body--scroll {
  max-height: 60vh;
  overflow-y: auto;
  padding-right: var(--scroll-content-gap, 12px);
  scrollbar-gutter: stable;
}
.lb-modal-body--scroll::-webkit-scrollbar {
  width: 5px;
}
.lb-modal-body--scroll::-webkit-scrollbar-thumb {
  background: #d4d4d8;
  border-radius: 3px;
}
.lb-modal-body--scroll::-webkit-scrollbar-track {
  background: transparent;
}
</style>
