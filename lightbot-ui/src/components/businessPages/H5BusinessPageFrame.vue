<template>
  <div class="h5-frame" :class="{ 'is-done': done }">
    <div v-if="!done" class="h5-shell">
      <div class="h5-title">{{ title }}</div>
      <p v-if="hint" class="h5-hint">{{ hint }}</p>
      <iframe
        v-if="pageHtml"
        ref="iframeRef"
        class="h5-iframe"
        :srcdoc="pageHtml"
        :title="title"
        sandbox="allow-scripts allow-forms allow-same-origin allow-modals"
        referrerpolicy="no-referrer"
        @load="onIframeLoad"
      />
      <iframe
        v-else-if="pageUrl"
        ref="iframeRef"
        class="h5-iframe"
        :src="iframeSrc"
        :title="title"
        sandbox="allow-scripts allow-forms allow-same-origin allow-popups allow-popups-to-escape-sandbox"
        referrerpolicy="no-referrer"
        @load="onIframeLoad"
      />
      <div v-if="loadError" class="h5-error">{{ loadError }}</div>
    </div>
    <div v-else class="h5-done">
      <div class="h5-title">{{ title }}</div>
      <pre v-if="submittedJson" class="h5-summary">{{ submittedJson }}</pre>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useBusinessPageSubmit } from './useBusinessPageSubmit'

const MSG_SOURCE = 'lightbot-business-page'

const props = defineProps({
  payload: { type: Object, required: true },
  messageIndex: { type: Number, default: -1 },
  workflowMode: { type: Boolean, default: false },
})
const emit = defineEmits(['workflow-submit', 'workflow-cancel'])

const iframeRef = ref(null)
const loadError = ref('')
const { submitting, done, submittedResult, submitResult, cancelResult } = useBusinessPageSubmit(props, emit)
const submittedJson = computed(() => {
  const values = submittedResult.value?.values
  if (!values || typeof values !== 'object') return ''
  try {
    return JSON.stringify(values, null, 2)
  } catch {
    return ''
  }
})

const title = computed(() => props.payload?.title || props.payload?.displayName || '业务办理')
const hint = computed(() => props.payload?.options?.hint || 'H5 业务页')
const pageHtml = computed(() => String(props.payload?.pageHtml || '').trim())
const pageUrl = computed(() => String(props.payload?.pageUrl || '').trim())

const initPayload = computed(() => ({
  pageType: props.payload?.pageType,
  title: title.value,
  props: props.payload?.props || {},
  options: props.payload?.options || {},
  actions: props.payload?.actions || ['submit', 'cancel'],
}))

const iframeSrc = computed(() => {
  if (!pageUrl.value) return ''
  try {
    const url = new URL(pageUrl.value)
    url.searchParams.set('pageType', props.payload?.pageType || '')
    url.searchParams.set('mode', props.payload?.mode || 'inline')
    url.searchParams.set('payload', btoa(unescape(encodeURIComponent(JSON.stringify(initPayload.value)))))
    return url.toString()
  } catch {
    return pageUrl.value
  }
})

function postInit() {
  const win = iframeRef.value?.contentWindow
  if (!win || (!pageHtml.value && !pageUrl.value)) return
  try {
    win.postMessage({
      source: MSG_SOURCE,
      type: 'init',
      payload: initPayload.value,
    }, '*')
  } catch {
    loadError.value = '无法向 H5 页发送初始化消息'
  }
}

function onIframeLoad() {
  loadError.value = ''
  postInit()
}

async function onMessage(event) {
  const data = event?.data
  if (!data || data.source !== MSG_SOURCE || submitting.value || done.value) return
  if (iframeRef.value?.contentWindow && event.source !== iframeRef.value.contentWindow) return

  if (data.type === 'ready') {
    postInit()
    return
  }
  if (data.type === 'resize' && typeof data.height === 'number' && iframeRef.value) {
    const h = Math.min(Math.max(data.height, 240), 720)
    iframeRef.value.style.height = `${h}px`
    return
  }
  if (data.type === 'submit') {
    await submitResult({
      action: 'submit',
      pageType: props.payload?.pageType,
      values: data.values || data.result || {},
      ...(data.extra && typeof data.extra === 'object' ? { extra: data.extra } : {}),
    })
    return
  }
  if (data.type === 'cancel') {
    await cancelResult({
      action: 'cancel',
      pageType: props.payload?.pageType,
    })
  }
}

onMounted(() => {
  window.addEventListener('message', onMessage)
  if (!pageHtml.value && !pageUrl.value) {
    loadError.value = '未配置 H5 页面内容（pageHtml）'
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('message', onMessage)
})
</script>

<style scoped>
.h5-frame {
  width: 100%;
  max-width: 480px;
  border: 1px solid var(--border-color, #e5e7eb);
  border-radius: 14px;
  background: #fff;
  overflow: hidden;
}
.h5-shell { padding: 12px 12px 10px; }
.h5-title { font-weight: 600; font-size: 15px; margin-bottom: 4px; }
.h5-hint { margin: 0 0 10px; font-size: 12px; color: #6b7280; }
.h5-iframe {
  width: 100%;
  height: 420px;
  border: 0;
  border-radius: 10px;
  background: #f8fafc;
}
.h5-error {
  margin-top: 8px;
  font-size: 12px;
  color: #cf1322;
}
.h5-done {
  padding: 12px;
  font-size: 13px;
}
.h5-done .h5-title { margin-bottom: 10px; }
.h5-summary {
  margin: 0;
  padding: 10px 12px;
  border-radius: 8px;
  background: #f8fafc;
  font-size: 12px;
  line-height: 1.45;
  white-space: pre-wrap;
  word-break: break-word;
  text-align: left;
  color: #171717;
}
</style>
