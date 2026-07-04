<template>
  <div>
    <div v-if="isPlainText" style="margin:0;padding:8px 10px;background:#fafafa;border-radius:6px;font-size:12px;line-height:1.5;color:#374151;white-space:pre-wrap;word-break:break-word;">
      <pre style="margin:0;">{{ displayText }}</pre>
    </div>

    <template v-else>
      <!-- 问题 -->
      <div v-if="displayQuestion" style="display:flex;align-items:flex-start;gap:8px;padding:10px 12px;background:#dbeafe;border:1px solid #93c5fd;border-radius:8px;font-size:13px;line-height:1.6;color:#1e40af;margin-bottom:8px;">
        <QuestionCircleOutlined style="color:#2563eb;font-size:15px;margin-top:2px;flex-shrink:0;" />
        <span style="font-weight:500;">{{ displayQuestion }}</span>
      </div>

      <!-- 选项列表（提问阶段展示可选项） -->
      <div v-if="data.options?.length" style="display:flex;flex-direction:column;gap:6px;margin-bottom:8px;">
        <div v-for="(opt, i) in data.options" :key="i"
          :style="optionItemStyle(opt)"
          style="display:flex;align-items:center;gap:8px;padding:8px 12px;border-radius:8px;font-size:12px;color:#1f2937;cursor:default;transition:all 0.2s;">
          <span style="display:inline-flex;align-items:center;justify-content:center;min-width:22px;height:22px;border-radius:50%;font-size:11px;font-weight:600;flex-shrink:0;"
            :style="optionBadgeStyle(opt)">{{ i + 1 }}</span>
          <span style="flex:1;line-height:1.5;">{{ opt }}</span>
          <CheckCircleOutlined v-if="isSelectedOption(opt)" style="color:#16a34a;font-size:14px;flex-shrink:0;" />
        </div>
      </div>

      <!-- 用户回答（工作流 resume 后 / 对话已回复） -->
      <div v-if="userAnswer" style="display:flex;align-items:flex-start;gap:8px;padding:10px 12px;background:#f0fdf4;border:1px solid #86efac;border-radius:8px;font-size:13px;line-height:1.6;color:#166534;margin-bottom:8px;">
        <CheckCircleOutlined style="color:#22c55e;font-size:15px;margin-top:2px;flex-shrink:0;" />
        <div style="min-width:0;">
          <div style="font-size:11px;font-weight:600;margin-bottom:4px;opacity:0.85;">用户回答</div>
          <div style="white-space:pre-wrap;word-break:break-word;">{{ userAnswer }}</div>
        </div>
      </div>

      <!-- 状态提示（无回答内容时） -->
      <div v-else-if="data.is_open_ended || !answered" style="display:flex;align-items:center;gap:6px;padding:8px 12px;border-radius:8px;font-size:12px;background:#fefce8;border:1px solid #fde68a;color:#92400e;">
        <ClockCircleOutlined style="color:#f59e0b;font-size:13px;flex-shrink:0;" />
        <span>{{ isWorkflowMode ? '等待您在下方表单中回答…' : '等待用户回答...' }}</span>
      </div>

      <!-- 对话 Agent：未回答时可弹窗 -->
      <div v-if="!answered && !isWorkflowMode" style="margin-top:8px;">
        <button @click="handleAnswer"
          style="display:inline-flex;align-items:center;gap:6px;padding:8px 18px;background:#0070f3;color:#fff;border:none;border-radius:8px;font-size:13px;font-weight:500;cursor:pointer;transition:background 0.15s;"
          onmouseover="this.style.background='#005bc4'"
          onmouseout="this.style.background='#0070f3'">
          <QuestionCircleOutlined style="font-size:14px;" />
          回答
        </button>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, inject } from 'vue'
import { QuestionCircleOutlined, CheckCircleOutlined, ClockCircleOutlined } from '@ant-design/icons-vue'

const props = defineProps({
  event: { type: Object, required: true },
  messageIndex: { type: Number, default: -1 }
})

const showAskUserModal = inject('showAskUserModal', null)
const isAskUserUnanswered = inject('isAskUserUnanswered', null)

const isWorkflowMode = computed(() => props.messageIndex < 0)

const rawResult = computed(() => props.event.result || '')
const data = computed(() => { try { return JSON.parse(rawResult.value) } catch { return null } })
const isPlainText = computed(() => !data.value || typeof data.value !== 'object')
const displayText = computed(() => typeof data.value === 'string' ? data.value : rawResult.value)

const displayQuestion = computed(() => {
  const q = data.value?.question
  return q != null && String(q).trim() ? String(q).trim() : ''
})

const userAnswer = computed(() => {
  const ans = data.value?.user_answer
  return ans != null && String(ans).trim() ? String(ans).trim() : ''
})

const answered = computed(() => {
  if (userAnswer.value) return true
  if (isWorkflowMode.value) return false
  if (!isAskUserUnanswered || props.messageIndex < 0) return true
  return !isAskUserUnanswered(props.messageIndex)
})

function isSelectedOption(opt) {
  if (!userAnswer.value || !opt) return false
  return String(opt).trim() === userAnswer.value
}

function optionItemStyle(opt) {
  if (isSelectedOption(opt)) {
    return { background: '#f0fdf4', border: '1px solid #86efac' }
  }
  return { background: '#eff6ff', border: '1px solid #93c5fd' }
}

function optionBadgeStyle(opt) {
  if (isSelectedOption(opt)) {
    return { background: '#16a34a', color: '#fff' }
  }
  return { background: '#3b82f6', color: '#fff' }
}

function handleAnswer() {
  if (showAskUserModal && props.messageIndex >= 0) {
    showAskUserModal(props.messageIndex)
  }
}
</script>
