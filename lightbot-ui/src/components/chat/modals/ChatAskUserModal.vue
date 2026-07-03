<template>
  <!-- Ask User 弹窗 -->
  <a-modal
    :open="open"
    title="AI 向您提问"
    :footer="null"
    :maskClosable="false"
    width="520px"
    @update:open="$emit('update:open', $event)"
  >
    <div style="padding:8px 0;">
      <div style="display:flex;align-items:flex-start;gap:10px;padding:14px 16px;background:#dbeafe;border:1px solid #93c5fd;border-radius:10px;font-size:14px;line-height:1.7;color:#1e40af;margin-bottom:16px;">
        <QuestionCircleOutlined style="color:#2563eb;font-size:18px;margin-top:2px;flex-shrink:0;" />
        <span style="font-weight:500;">{{ question }}</span>
      </div>
      <div v-if="options.length > 0" style="display:flex;flex-direction:column;gap:8px;margin-bottom:16px;">
        <button v-for="(opt, i) in options" :key="i" @click="$emit('submit', opt)"
          style="display:flex;align-items:center;gap:10px;padding:12px 16px;background:#fff;border:1px solid #d4d4d8;border-radius:10px;font-size:14px;color:var(--color-primary);cursor:pointer;transition:all 0.15s;text-align:left;width:100%;"
          onmouseover="this.style.borderColor='#0070f3';this.style.background='#f0f7ff'"
          onmouseout="this.style.borderColor='#d4d4d8';this.style.background='#fff'">
          <span style="display:inline-flex;align-items:center;justify-content:center;min-width:26px;height:26px;background:#3b82f6;color:#fff;border-radius:50%;font-size:12px;font-weight:600;flex-shrink:0;">{{ i + 1 }}</span>
          <span style="flex:1;line-height:1.5;">{{ opt }}</span>
        </button>
      </div>
      <div style="display:flex;gap:8px;align-items:flex-end;">
        <a-textarea
          :value="freeText"
          :placeholder="options.length > 0 ? '或者输入自定义回答...' : '请输入您的回答...'"
          :auto-size="{ minRows: 2, maxRows: 4 }"
          @update:value="$emit('update:freeText', $event)"
          @keydown.enter.ctrl="$emit('submit', freeText)"
          style="flex:1;"
        />
        <a-button type="primary" :disabled="!freeText.trim()" @click="$emit('submit', freeText)">
          发送
        </a-button>
      </div>
      <div style="text-align:right;margin-top:6px;font-size:11px;color:#a1a1aa;">Ctrl+Enter 发送</div>
    </div>
  </a-modal>
</template>

<script setup>
import { QuestionCircleOutlined } from '@ant-design/icons-vue'

defineProps({
  open: { type: Boolean, default: false },
  question: { type: String, default: '' },
  options: { type: Array, default: () => [] },
  freeText: { type: String, default: '' },
})

defineEmits(['update:open', 'update:freeText', 'submit'])
</script>
