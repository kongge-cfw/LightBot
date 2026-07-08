<template>
  <div class="chat-debug-lab">
    <header class="debug-lab-header">
      <div class="debug-lab-title">
        <BugOutlined class="debug-lab-icon" />
        <h1>Chat Debug Lab</h1>
        <span class="debug-lab-sub">与 AI 回复使用相同渲染组件</span>
      </div>
      <div class="debug-lab-actions">
        <a-select
          v-if="activeTab === 'composer'"
          v-model:value="selectedPresetId"
          placeholder="加载预设"
          style="width: 200px"
          allow-clear
          :options="presetOptions"
          @change="applyPreset"
        />
        <a-button @click="handleImportFromStorage">读取导入数据</a-button>
        <a-button @click="goBackChat">返回对话</a-button>
      </div>
    </header>

    <div class="debug-lab-body">
      <div class="debug-lab-editor">
        <a-tabs v-model:activeKey="activeTab" class="debug-lab-tabs">
          <a-tab-pane key="composer" tab="消息组合">
            <ChatDebugComposerPanel
              ref="composerRef"
              v-model="composerJson"
              @parse="parseComposer"
            />
          </a-tab-pane>
          <a-tab-pane key="tool" tab="工具渲染">
            <ChatDebugToolPanel ref="toolRef" @parse="parseTool" />
          </a-tab-pane>
          <a-tab-pane key="markdown" tab="Markdown">
            <ChatDebugMarkdownPanel
              ref="markdownRef"
              v-model="markdownSource"
              @parse="parseMarkdown"
            />
          </a-tab-pane>
        </a-tabs>
      </div>

      <ChatDebugPreviewShell class="debug-lab-preview">
        <template v-if="activeTab === 'composer' && previewMsg">
          <ChatMessageRow
            :msg="previewMsg"
            :index="0"
            :loading="false"
            :streaming="false"
            :get-att-thumb-url="noopThumb"
            :messages="[previewMsg]"
            :messages-length="1"
            :refs-section-expanded="true"
            :is-ref-expanded="() => false"
          />
        </template>
        <template v-else-if="activeTab === 'tool' && previewToolEvent">
          <div class="debug-tool-preview-wrap">
            <ToolCallRenderer :event="previewToolEvent" :message-index="0" />
          </div>
        </template>
        <template v-else-if="activeTab === 'markdown' && markdownParsed !== null">
          <div class="debug-md-preview-wrap">
            <MarkdownPreview :content="markdownParsed" :finalized="true" />
          </div>
        </template>
        <div v-else class="debug-preview-empty">点击「解析预览」查看渲染效果</div>
      </ChatDebugPreviewShell>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { BugOutlined } from '@ant-design/icons-vue'
import ChatDebugPreviewShell from '@/components/chat/debug/ChatDebugPreviewShell.vue'
import ChatDebugComposerPanel from '@/components/chat/debug/ChatDebugComposerPanel.vue'
import ChatDebugToolPanel from '@/components/chat/debug/ChatDebugToolPanel.vue'
import ChatDebugMarkdownPanel from '@/components/chat/debug/ChatDebugMarkdownPanel.vue'
import ChatMessageRow from '@/components/chat/message/ChatMessageRow.vue'
import ToolCallRenderer from '@/components/ToolCallRenderer.vue'
import MarkdownPreview from '@/components/MarkdownPreview.vue'
import {
  apiMessageToEditorJson,
  buildPreviewMessage,
  createDefaultApiMessage,
} from '@/utils/chat/debug/debugMessageBuilder'
import { DEBUG_PRESETS, getPresetById } from '@/utils/chat/debug/debugPresets'
import { peekDebugImportPayload } from '@/utils/chat/debug/debugImportStorage'
import { useChatDebugImport } from '@/composables/chat/useChatDebugImport'

const route = useRoute()
const router = useRouter()
const { readImportPayload } = useChatDebugImport()

const activeTab = ref(route.query.tab || 'composer')
const composerRef = ref(null)
const toolRef = ref(null)
const markdownRef = ref(null)

const composerJson = ref(apiMessageToEditorJson(createDefaultApiMessage()))
const previewMsg = ref(null)
const previewToolEvent = ref(null)
const markdownSource = ref('')
const markdownParsed = ref(null)

const presetOptions = DEBUG_PRESETS.map((p) => ({ value: p.id, label: p.label }))
const selectedPresetId = ref(undefined)

function noopThumb() {
  return ''
}

function parseComposer() {
  const apiMsg = composerRef.value?.validateAndGetMessage?.()
  if (!apiMsg) return
  previewMsg.value = buildPreviewMessage(apiMsg)
  message.success('消息已解析')
}

function parseTool() {
  const event = toolRef.value?.validateAndGetEvent?.()
  if (!event) return
  previewToolEvent.value = event
  message.success('工具结果已解析')
}

function parseMarkdown() {
  markdownParsed.value = markdownSource.value
  message.success('Markdown 已解析')
}

function applyPreset(presetId) {
  if (!presetId) return
  const msg = getPresetById(presetId)
  if (!msg) return
  composerJson.value = apiMessageToEditorJson(msg)
  parseComposer()
}

function importPayload(payload) {
  if (!payload) return
  const apiMsg = {
    role: payload.role || 'assistant',
    content: payload.content ?? '',
    metadata: payload.metadata ?? {},
  }
  composerJson.value = apiMessageToEditorJson(apiMsg)
  activeTab.value = 'composer'
  parseComposer()
  message.success('已导入消息')
}

function handleImportFromStorage() {
  const payload = peekDebugImportPayload() || readImportPayload(route)
  if (!payload) {
    message.info('暂无待导入数据（可在 Chat 页 Debug 模式下使用「发送到 Debug Lab」）')
    return
  }
  importPayload(payload)
}

function goBackChat() {
  router.push({ name: 'Chat' })
}

onMounted(() => {
  const imported = readImportPayload(route)
  if (imported) {
    importPayload(imported)
    return
  }
  parseComposer()
})

watch(() => route.query.tab, (tab) => {
  if (tab) activeTab.value = tab
})
</script>

<style scoped>
.chat-debug-lab {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: var(--color-canvas);
}

.debug-lab-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 24px;
  border-bottom: 1px solid var(--color-hairline);
  background: var(--color-canvas-soft);
  flex-shrink: 0;
}

.debug-lab-title {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.debug-lab-title h1 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--color-ink);
}

.debug-lab-icon {
  font-size: 20px;
  color: #d97706;
}

.debug-lab-sub {
  font-size: 12px;
  color: var(--gray-500);
}

.debug-lab-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.debug-lab-body {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(360px, 42%) 1fr;
  gap: 16px;
  padding: 16px 24px 24px;
}

.debug-lab-editor {
  min-height: 0;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--gray-100);
  border-radius: 8px;
  padding: 0 12px 12px;
  background: var(--color-canvas-soft);
}

.debug-lab-tabs {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.debug-lab-tabs :deep(.ant-tabs-content) {
  flex: 1;
  min-height: 0;
}

.debug-lab-tabs :deep(.ant-tabs-tabpane) {
  height: 100%;
}

.debug-lab-preview {
  min-height: 0;
}

.debug-preview-empty {
  padding: 48px 24px;
  text-align: center;
  color: var(--gray-400);
  font-size: 14px;
}

.debug-tool-preview-wrap,
.debug-md-preview-wrap {
  max-width: 800px;
  margin: 0 auto;
  padding: 0 32px;
}

@media (max-width: 960px) {
  .debug-lab-body {
    grid-template-columns: 1fr;
  }
}
</style>
