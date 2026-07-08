<template>
  <a-layout class="debug-lab-root">
    <!-- 顶部：Logo + 横向 Nav + 模块级操作（预设与 composer 同排） -->
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
        <a-select
          v-if="activeModule === 'composer'"
          v-model:value="selectedPresetId"
          placeholder="加载预设"
          allow-clear
          :options="presetOptions"
          class="debug-preset-select"
          @change="applyPreset"
        />
        <a-button size="small" @click="handleImportFromStorage">读取导入</a-button>
        <a-button size="small" @click="goBackChat">返回对话</a-button>
      </div>
    </a-layout-header>

    <a-layout-content class="debug-body-wrap">
      <a-layout class="debug-inner-layout">
        <!-- 左侧 Sider：当前模块操作区（非 Tab 栏） -->
        <a-layout-sider width="220" class="debug-sider" theme="light">
          <div class="debug-sider-title">操作</div>
          <div class="debug-sider-actions">
            <template v-if="activeModule === 'composer'">
              <a-button type="primary" block @click="parseComposer">解析预览</a-button>
              <a-button block @click="composerRef?.handleFormat?.()">格式化 JSON</a-button>
              <a-button block @click="composerRef?.handleReset?.()">重置</a-button>
            </template>
            <template v-else-if="activeModule === 'tool'">
              <a-button type="primary" block @click="parseTool">解析预览</a-button>
              <a-button block @click="toolRef?.loadSample?.()">加载样例</a-button>
              <a-button block @click="toolRef?.loadErrorSample?.()">错误样例</a-button>
            </template>
            <template v-else-if="activeModule === 'markdown'">
              <a-button type="primary" block @click="parseMarkdown">解析预览</a-button>
              <a-button block @click="markdownRef?.handleClear?.()">清空</a-button>
            </template>
            <template v-else>
              <a-alert type="info" message="该模块规划中，见方案文档 Phase 3+" show-icon />
            </template>
          </div>
          <div class="debug-sider-hint">
            预览组件与 Chat 页完全一致，不单独实现渲染逻辑；数据仅来自前端 Mock / 导入 / 预设。
          </div>
        </a-layout-sider>

        <!-- 主内容：编辑区 + 预览区 -->
        <a-layout-content class="debug-main-content">
          <div v-if="activeModule === 'composer'" class="debug-split">
            <div class="debug-editor-pane">
              <ChatDebugComposerPanel
                ref="composerRef"
                v-model="composerJson"
                :show-toolbar="false"
                @parse="parseComposer"
              />
            </div>
            <ChatDebugPreviewShell class="debug-preview-pane">
              <ChatMessageRow
                v-if="previewMsg"
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
              <div v-else class="debug-preview-empty">点击「解析预览」查看渲染效果</div>
            </ChatDebugPreviewShell>
          </div>

          <div v-else-if="activeModule === 'tool'" class="debug-split">
            <div class="debug-editor-pane">
              <ChatDebugToolPanel ref="toolRef" :show-toolbar="false" @parse="parseTool" />
            </div>
            <ChatDebugPreviewShell class="debug-preview-pane">
              <div v-if="previewToolEvent" class="debug-tool-preview-wrap">
                <ToolCallRenderer :event="previewToolEvent" :message-index="0" />
              </div>
              <div v-else class="debug-preview-empty">点击「解析预览」查看工具 UI</div>
            </ChatDebugPreviewShell>
          </div>

          <div v-else-if="activeModule === 'markdown'" class="debug-split">
            <div class="debug-editor-pane">
              <ChatDebugMarkdownPanel
                ref="markdownRef"
                v-model="markdownSource"
                :show-toolbar="false"
                @parse="parseMarkdown"
              />
            </div>
            <ChatDebugPreviewShell class="debug-preview-pane">
              <div v-if="markdownParsed !== null" class="debug-md-preview-wrap">
                <MarkdownPreview :content="markdownParsed" :finalized="true" />
              </div>
              <div v-else class="debug-preview-empty">点击「解析预览」查看 Markdown</div>
            </ChatDebugPreviewShell>
          </div>

          <div v-else class="debug-coming-soon">
            <a-empty description="该调试模块尚未开放，请参考 docs/Chat-Debug-Lab方案.md" />
          </div>
        </a-layout-content>
      </a-layout>
    </a-layout-content>

    <a-layout-footer class="debug-footer">
      Debug Lab · 纯前端 Mock · 与生产渲染同源 · 无需登录
    </a-layout-footer>
  </a-layout>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
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
import { getDebugLabNavMenuItems } from '@/constants/debugLabNav'

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

function onNavClick({ key }) {
  const item = navMenuItems.find((i) => i.key === key)
  if (item?.disabled) {
    message.info('该模块尚未开放')
    return
  }
  activeModule.value = key
  router.replace({ query: { ...route.query, tab: key } })
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
  activeModule.value = 'composer'
  router.replace({ query: { ...route.query, tab: 'composer' } })
  parseComposer()
}

function importPayload(payload) {
  if (!payload) return
  composerJson.value = apiMessageToEditorJson({
    role: payload.role || 'assistant',
    content: payload.content ?? '',
    metadata: payload.metadata ?? {},
  })
  activeModule.value = 'composer'
  parseComposer()
  message.success('已导入消息')
}

function handleImportFromStorage() {
  const payload = peekDebugImportPayload() || readImportPayload(route)
  if (!payload) {
    message.info('暂无待导入数据（Chat 页 Debug 模式下可「发送到 Debug Lab」）')
    return
  }
  importPayload(payload)
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
    return
  }
  if (activeModule.value === 'composer') {
    parseComposer()
  }
})

watch(() => route.query.tab, (tab) => {
  if (tab && navMenuItems.some((i) => i.key === tab && !i.disabled)) {
    activeModule.value = tab
  }
})
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

.debug-preset-select {
  width: 180px;
}

.debug-body-wrap {
  padding: 16px 24px 0;
  flex: 1;
}

.debug-inner-layout {
  background: var(--color-canvas);
  border: 1px solid var(--gray-100);
  border-radius: 8px;
  min-height: calc(100vh - 56px - 52px - 32px);
  overflow: hidden;
}

.debug-sider {
  border-right: 1px solid var(--gray-100);
  padding: 16px 12px;
  background: var(--color-canvas-soft) !important;
}

.debug-sider-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--gray-700);
  margin-bottom: 12px;
}

.debug-sider-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.debug-sider-hint {
  margin-top: 16px;
  font-size: 11px;
  line-height: 1.5;
  color: var(--gray-500);
}

.debug-main-content {
  padding: 16px;
  min-height: 0;
  background: var(--color-canvas);
}

.debug-split {
  display: grid;
  grid-template-columns: minmax(320px, 1fr) minmax(360px, 1fr);
  gap: 16px;
  height: calc(100vh - 56px - 52px - 64px);
  min-height: 480px;
}

.debug-editor-pane {
  min-height: 0;
  overflow: auto;
  border: 1px solid var(--gray-100);
  border-radius: 8px;
  padding: 12px;
  background: var(--color-canvas-soft);
}

.debug-preview-pane {
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
  padding: 0 16px;
}

.debug-coming-soon {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 400px;
}

.debug-footer {
  text-align: center;
  padding: 12px;
  color: var(--gray-500);
  font-size: 12px;
  background: transparent;
}

@media (max-width: 960px) {
  .debug-split {
    grid-template-columns: 1fr;
    height: auto;
  }

  .debug-header {
    flex-wrap: wrap;
    height: auto;
    padding: 8px 16px;
  }

  .debug-header-nav {
    order: 3;
    width: 100%;
  }
}
</style>
