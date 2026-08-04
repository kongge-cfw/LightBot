import { inject, ref } from 'vue'
import { message } from 'ant-design-vue'

/**
 * 业务页提交/取消：对话回灌 或 Workflow HITL
 */
export function useBusinessPageSubmit(props, emit) {
  const submitBusinessPageResult = inject('submitBusinessPageResult', null)
  const submitting = ref(false)
  const done = ref(false)
  /** 提交成功后保留的结果，供卡片回显 */
  const submittedResult = ref(null)

  async function submitResult(result) {
    submitting.value = true
    try {
      if (props.workflowMode) {
        emit('workflow-submit', result)
        done.value = true
        submittedResult.value = result
        return
      }
      if (typeof submitBusinessPageResult === 'function' && props.messageIndex >= 0) {
        await submitBusinessPageResult({
          messageIndex: props.messageIndex,
          result,
          status: 'submitted',
        })
        done.value = true
        submittedResult.value = result
        return
      }
      // 无回灌能力时本地演示
      await new Promise((r) => setTimeout(r, 400))
      done.value = true
      submittedResult.value = result
      message.success('演示提交成功')
    } finally {
      submitting.value = false
    }
  }

  async function cancelResult(result) {
    submitting.value = true
    try {
      if (props.workflowMode) {
        emit('workflow-cancel', result || { action: 'cancel' })
        done.value = true
        return
      }
      if (typeof submitBusinessPageResult === 'function' && props.messageIndex >= 0) {
        await submitBusinessPageResult({
          messageIndex: props.messageIndex,
          result: result || { action: 'cancel' },
          status: 'cancelled',
        })
        done.value = true
        return
      }
      done.value = true
      message.info('已取消')
    } finally {
      submitting.value = false
    }
  }

  return { submitting, done, submittedResult, submitResult, cancelResult }
}
