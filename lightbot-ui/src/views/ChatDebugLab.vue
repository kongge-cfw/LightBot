<template>
  <a-layout class="debug-lab-root">
    <a-layout-header class="debug-header">
      <div class="debug-header-brand">
        <BugOutlined class="debug-brand-icon" />
        <span class="debug-brand-title">Chat Debug Lab</span>
      </div>
      <a-menu
        v-model:selectedKeys="selectedNavKeys"
        mode="horizontal"
        :items="navMenuItems"
        class="debug-header-nav"
        @click="onNavClick"
      />
      <div class="debug-header-extra">
        <a-button size="small" @click="handleImportFromStorage">读取导入</a-button>
        <a-button size="small" @click="goBackChat">返回对话</a-button>
      </div>
    </a-layout-header>

    <a-layout-content class="debug-body-wrap">
      <!-- 注册表：全宽 -->
      <div v-if="activeModule === 'registry'" class="debug-module debug-module-full">
        <ChatDebugRegistryPanel />
      </div>

      <!-- 对比：双预览 -->
      <div v-else-if="activeModule === 'compare'" class="debug-module">
        <DebugSplitPane v-model="splitRatio">
          <template #editor>
            <div class="debug-editor-pane">
              <ChatDebugComparePanel @preview="onComparePreview" />
            </div>
          </template>
          <template #preview>
            <div class="debug-compare-preview">
              <div class="debug-compare-preview-col">
                <div class="debug-compare-preview-label">预览 A</div>
                <ChatDebugMessagePreview :msg="comparePreviewLeft" empty-text="点击「解析两侧」" />
              </div>
              <div class="debug-compare-preview-col">
                <div class="debug-compare-preview-label">预览 B</div>
                <ChatDebugMessagePreview :msg="comparePreviewRight" empty-text="点击「解析两侧」" />
              </div>
            </div>
          </template>
        </DebugSplitPane>
      </div>

      <!-- 其余模块：编辑 + 预览 -->
      <div v-else class="debug-module">
        <DebugSplitPane v-model="splitRatio">
          <template #editor>
            <div class="debug-editor-pane">
              <template v-if="activeModule === 'composer'">
                <ChatDebugUiStateBar v-model="composerUiState" />
                <ChatDebugComposerPanel
                  ref="composerRef"
                  v-model="composerJson"
                  @parse="parseComposer"
                >
                  <template #toolbar-extra>
                    <a-select
                      v-model:value="selectedPresetId"
                      placeholder="加载预设"
                      allow-clear
                      :options="presetOptions"
                      class="debug-preset-select"
                      @change="applyPreset"
                    />
                    <a-button @click="exportComposerFixture">导出 Fixture</a-button>
                    <a-button @click="triggerFixtureImport">导入 Fixture</a-button>
                  </template>
                </ChatDebugComposerPanel>
              </template>

              <ChatDebugToolPanel v-else-if="activeModule === 'tool'" ref="toolRef" @parse="parseTool" />

              <ChatDebugMarkdownPanel
                v-else-if="activeModule === 'markdown'"
                ref="markdownRef"
                v-model="markdownSource"
                @parse="parseMarkdown"
              />

              <ChatDebugCapabilityPanel
                v-else-if="activeModule === 'capability'"
                ref="capabilityRef"
                @parse="parseCapability"
              />

              <ChatDebugWorkflowPanel
                v-else-if="activeModule === 'workflow'"
                ref="workflowRef"
                @parse="parseWorkflow"
              />

              <ChatDebugStreamPanel
                v-else-if="activeModule === 'stream'"
                ref="streamRef"
                @preview="onStreamPreview"
              />

              <ChatDebugSseReplayPanel
                v-else-if="activeModule === 'sse'"
                @preview="onSsePreview"
              />

              <ChatDebugThemePanel
                v-else-if="activeModule === 'theme'"
                ref="themeRef"
                @parse="parseTheme"
              />
            </div>
          </template>
          <template #preview>
            <template v-if="activeModule === 'tool'">
              <ChatDebugPreviewShell class="debug-preview-pane">
                <div v-if="previewToolEvent" class="debug-tool-preview-wrap">
                  <ToolCallRenderer :event="previewToolEvent" :message-index="0" />
                </div>
                <div v-else class="debug-preview-empty">点击「解析预览」查看工具 UI</div>
              </ChatDebugPreviewShell>
            </template>
            <template v-else-if="activeModule === 'markdown'">
              <ChatDebugPreviewShell class="debug-preview-pane">
                <div v-if="markdownParsed !== null" class="debug-md-preview-wrap">
                  <MarkdownPreview :content="markdownParsed" :finalized="true" />
                </div>
                <div v-else class="debug-preview-empty">点击「解析预览」查看 Markdown</div>
              </ChatDebugPreviewShell>
            </template>
            <ChatDebugMessagePreview
              v-else
              :msg="previewMsg"
              :refs-section-expanded="composerUiState.refsSectionExpanded"
              :empty-text="previewEmptyText"
            />
          </template>
        </DebugSplitPane>
      </div>
    </a-layout-content>

    <input
      ref="fixtureInputRef"
      type="file"
      accept="application/json,.json"
      class="debug-fixture-input"
      @change="onFixtureFileChange"
    />

    <a-layout-footer class="debug-footer">
      Debug Lab · 纯前端 Mock · 与生产渲染同源 · 无需登录
    </a-layout-footer>
  </a-layout>
</template>

<script setup>
import { ref, computed, onMounted, watch, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { BugOutlined } from '@ant-design/icons-vue'
import DebugSplitPane from '@/components/chat/debug/DebugSplitPane.vue'
import ChatDebugMessagePreview from '@/components/chat/debug/ChatDebugMessagePreview.vue'
import ChatDebugPreviewShell from '@/components/chat/debug/ChatDebugPreviewShell.vue'
import ChatDebugUiStateBar from '@/components/chat/debug/ChatDebugUiStateBar.vue'
import ChatDebugComposerPanel from '@/components/chat/debug/ChatDebugComposerPanel.vue'
import ChatDebugToolPanel from '@/components/chat/debug/ChatDebugToolPanel.vue'
import ChatDebugMarkdownPanel from '@/components/chat/debug/ChatDebugMarkdownPanel.vue'
import ChatDebugCapabilityPanel from '@/components/chat/debug/ChatDebugCapabilityPanel.vue'
import ChatDebugWorkflowPanel from '@/components/chat/debug/ChatDebugWorkflowPanel.vue'
import ChatDebugRegistryPanel from '@/components/chat/debug/ChatDebugRegistryPanel.vue'
import ChatDebugStreamPanel from '@/components/chat/debug/ChatDebugStreamPanel.vue'
import ChatDebugComparePanel from '@/components/chat/debug/ChatDebugComparePanel.vue'
import ChatDebugSseReplayPanel from '@/components/chat/debug/ChatDebugSseReplayPanel.vue'
import ChatDebugThemePanel from '@/components/chat/debug/ChatDebugThemePanel.vue'
import ToolCallRenderer from '@/components/ToolCallRenderer.vue'
import MarkdownPreview from '@/components/MarkdownPreview.vue'
import {
  apiMessageToEditorJson,
  buildPreviewMessage,
  createDefaultApiMessage,
  editorJsonToApiMessage,
} from '@/utils/chat/debug/debugMessageBuilder'
import { DEBUG_PRESETS, getPresetById } from '@/utils/chat/debug/debugPresets'
import { peekDebugImportPayload } from '@/utils/chat/debug/debugImportStorage'
import { useChatDebugImport } from '@/composables/chat/useChatDebugImport'
import { getDebugLabNavMenuItems } from '@/constants/debugLabNav'
import { DEFAULT_DEBUG_UI_STATE } from '@/utils/chat/debug/debugUiState'
import { buildDebugFixture, downloadDebugFixture, readDebugFixtureFile } from '@/utils/chat/debug/debugFixture'

const route = useRoute()
const router = useRouter()
const { readImportPayload } = useChatDebugImport()

const navMenuItems = getDebugLabNavMenuItems()
const activeModule = ref(route.query.tab || 'composer')
const selectedNavKeys = computed({
  get: () => [activeModule.value],
  set: (keys) => {
    if (keys?.[0]) activeModule.value = keys[0]
  },
})

const splitRatio = ref(0.36)
const composerRef = ref(null)
const toolRef = ref(null)
const markdownRef = ref(null)
const capabilityRef = ref(null)
const workflowRef = ref(null)
const streamRef = ref(null)
const themeRef = ref(null)
const fixtureInputRef = ref(null)

const composerJson = ref(apiMessageToEditorJson(createDefaultApiMessage()))
const composerUiState = ref({ ...DEFAULT_DEBUG_UI_STATE })
const previewMsg = ref(null)
const previewToolEvent = ref(null)
const markdownSource = ref('')
const markdownParsed = ref(null)
const comparePreviewLeft = ref(null)
const comparePreviewRight = ref(null)

const presetOptions = DEBUG_PRESETS.map((p) => ({ value: p.id, label: p.label }))
const selectedPresetId = ref(undefined)

const previewEmptyText = computed(() => {
  if (activeModule.value === 'tool') return '点击「解析预览」查看工具 UI'
  if (activeModule.value === 'markdown') return '点击「解析预览」查看 Markdown'
  if (activeModule.value === 'stream') return '点击「开始流式模拟」'
  if (activeModule.value === 'sse') return '点击「回放 SSE」'
  return '点击「解析预览」查看渲染效果'
})

function onNavClick({ key }) {
  activeModule.value = key
  router.replace({ query: { ...route.query, tab: key } })
  if (key !== 'stream') {
    streamRef.value?.stopSimulation?.()
  }
}

function buildMsgPreview(apiMsg, uiState = composerUiState.value) {
  return buildPreviewMessage(apiMsg, uiState)
}

function parseComposer() {
  const apiMsg = composerRef.value?.validateAndGetMessage?.()
  if (!apiMsg) return
  previewMsg.value = buildMsgPreview(apiMsg)
  message.success('消息已解析')
}

function parseTool() {
  const event = toolRef.value?.validateAndGetEvent?.()
  if (!event) return
  previewToolEvent.value = event
  previewMsg.value = null
  message.success('工具结果已解析')
}

function parseMarkdown() {
  markdownParsed.value = markdownSource.value
  previewMsg.value = null
  message.success('Markdown 已解析')
}

function parseCapability() {
  const apiMsg = capabilityRef.value?.validateAndGetMessage?.()
  if (!apiMsg) return
  previewMsg.value = buildMsgPreview(apiMsg)
  message.success('能力块消息已解析')
}

function parseWorkflow() {
  const apiMsg = workflowRef.value?.validateAndGetMessage?.()
  if (!apiMsg) return
  previewMsg.value = buildMsgPreview(apiMsg)
  message.success('工作流消息已解析')
}

function parseTheme() {
  const apiMsg = themeRef.value?.validateAndGetMessage?.()
  if (!apiMsg) return
  previewMsg.value = buildMsgPreview(apiMsg)
  message.success('主题样例已解析')
}

function onStreamPreview(msg) {
  previewMsg.value = msg
}

function onSsePreview(apiMsg) {
  if (!apiMsg) {
    previewMsg.value = null
    return
  }
  previewMsg.value = buildMsgPreview(apiMsg)
}

function onComparePreview(payload) {
  if (!payload) {
    comparePreviewLeft.value = null
    comparePreviewRight.value = null
    return
  }
  comparePreviewLeft.value = buildMsgPreview(payload.left)
  comparePreviewRight.value = buildMsgPreview(payload.right)
  message.success('两侧消息已解析')
}

function applyPreset(presetId) {
  if (!presetId) return
  const msg = getPresetById(presetId)
  if (!msg) return
  composerJson.value = apiMessageToEditorJson(msg)
  previewMsg.value = null
  activeModule.value = 'composer'
  router.replace({ query: { ...route.query, tab: 'composer' } })
  message.success('预设已加载，点击「解析预览」查看效果')
}

function importPayload(payload) {
  if (!payload) return
  composerJson.value = apiMessageToEditorJson({
    role: payload.role || 'assistant',
    content: payload.content ?? '',
    metadata: payload.metadata ?? {},
  })
  if (payload.uiState) {
    composerUiState.value = { ...DEFAULT_DEBUG_UI_STATE, ...payload.uiState }
  }
  previewMsg.value = null
  activeModule.value = 'composer'
  message.success('已导入消息，点击「解析预览」查看效果')
}

function handleImportFromStorage() {
  const payload = peekDebugImportPayload() || readImportPayload(route)
  if (!payload) {
    message.info('暂无待导入数据（Chat 页 Debug 模式下可「发送到 Debug Lab」）')
    return
  }
  importPayload(payload)
}

function exportComposerFixture() {
  try {
    const apiMsg = editorJsonToApiMessage(composerJson.value)
    const fixture = buildDebugFixture({
      message: apiMsg,
      uiState: composerUiState.value,
      label: 'composer-fixture',
    })
    downloadDebugFixture(fixture)
    message.success('Fixture 已导出')
  } catch (e) {
    message.error(e.message || '导出失败')
  }
}

function triggerFixtureImport() {
  fixtureInputRef.value?.click()
}

async function onFixtureFileChange(e) {
  const file = e.target.files?.[0]
  e.target.value = ''
  if (!file) return
  try {
    const fixture = await readDebugFixtureFile(file)
    if (fixture?.message) {
      importPayload({ ...fixture.message, uiState: fixture.uiState })
    } else if (fixture?.role || fixture?.content != null) {
      importPayload(fixture)
    } else {
      message.error('Fixture 格式无效')
    }
  } catch (err) {
    message.error(err.message || '导入失败')
  }
}

function goBackChat() {
  if (localStorage.getItem('token')) {
    router.push({ name: 'Chat' })
  } else {
    router.push({ path: '/' })
  }
}

onMounted(() => {
  const imported = readImportPayload(route)
  if (imported) {
    importPayload(imported)
  }
})

onBeforeUnmount(() => {
  streamRef.value?.stopSimulation?.()
})

watch(() => route.query.tab, (tab) => {
  if (tab && navMenuItems.some((i) => i.key === tab)) {
    activeModule.value = tab
  }
})

watch(activeModule, (mod) => {
  previewMsg.value = null
  previewToolEvent.value = null
  markdownParsed.value = null
  comparePreviewLeft.value = null
  comparePreviewRight.value = null
})

watch(composerUiState, () => {
  if (activeModule.value === 'composer' && previewMsg.value) {
    const apiMsg = composerRef.value?.validateAndGetMessage?.()
    if (apiMsg) {
      previewMsg.value = buildMsgPreview(apiMsg)
    }
  }
}, { deep: true })
</script>

<style scoped>
.debug-lab-root {
  min-height: 100vh;
  background: var(--color-canvas);
}

.debug-header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 0 24px;
  height: 56px;
  line-height: 56px;
  background: #001529;
}

.debug-header-brand {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  color: #fff;
}

.debug-brand-icon {
  font-size: 20px;
  color: #faad14;
}

.debug-brand-title {
  font-size: 16px;
  font-weight: 600;
  white-space: nowrap;
}

.debug-header-nav {
  flex: 1;
  min-width: 0;
  background: transparent;
  border-bottom: none;
  line-height: 54px;
}

.debug-header-nav :deep(.ant-menu-item) {
  color: rgba(255, 255, 255, 0.75);
}

.debug-header-nav :deep(.ant-menu-item-selected) {
  color: #fff !important;
}

.debug-header-extra {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.debug-body-wrap {
  padding: 16px 24px 0;
  flex: 1;
}

.debug-module {
  border: 1px solid var(--gray-100);
  border-radius: 8px;
  overflow: hidden;
  background: var(--color-canvas);
}

.debug-module-full {
  padding: 16px;
  min-height: calc(100vh - 56px - 52px - 32px);
}

.debug-editor-pane {
  height: 100%;
  min-height: 0;
  overflow: auto;
  padding: 12px;
  background: var(--color-canvas-soft);
}

.debug-preset-select {
  width: 168px;
}

.debug-compare-preview {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  height: 100%;
  padding: 12px;
  min-height: 0;
}

.debug-compare-preview-col {
  display: flex;
  flex-direction: column;
  min-height: 0;
  min-width: 0;
}

.debug-compare-preview-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--gray-700);
  margin-bottom: 8px;
}

.debug-preview-empty {
  padding: 48px 24px;
  text-align: center;
  color: var(--gray-400);
  font-size: 14px;
}

.debug-tool-preview-wrap,
.debug-md-preview-wrap {
  width: 100%;
  padding: 0 20px;
}

.debug-preview-pane {
  height: 100%;
}

.debug-fixture-input {
  display: none;
}

.debug-footer {
  text-align: center;
  padding: 12px;
  color: var(--gray-500);
  font-size: 12px;
  background: transparent;
}

@media (max-width: 960px) {
  .debug-header {
    flex-wrap: wrap;
    height: auto;
    padding: 8px 16px;
  }

  .debug-header-nav {
    order: 3;
    width: 100%;
  }

  .debug-compare-preview {
    grid-template-columns: 1fr;
  }
}
</style>
