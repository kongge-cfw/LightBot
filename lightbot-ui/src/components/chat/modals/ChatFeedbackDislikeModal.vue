<template>
  <!-- Dislike 原因弹窗 -->
  <a-modal
    :open="open"
    title="反馈原因（可选）"
    @ok="$emit('submit')"
    @cancel="$emit('skip')"
    okText="提交"
    cancelText="跳过"
    :maskClosable="false"
    :closable="false"
    :keyboard="false"
    width="420px"
    @update:open="$emit('update:open', $event)"
  >
    <div style="padding: 4px 0;">
      <a-textarea
        :value="reason"
        placeholder="请描述问题所在，帮助我们改进回答质量..."
        :auto-size="{ minRows: 3, maxRows: 6 }"
        @update:value="$emit('update:reason', $event)"
      />
    </div>
  </a-modal>
</template>

<script setup>
defineProps({
  open: { type: Boolean, default: false },
  reason: { type: String, default: '' },
})

defineEmits(['update:open', 'update:reason', 'submit', 'skip'])
</script>
