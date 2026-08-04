<template>
  <div class="page">
    <div class="page-intro">
      业务页二选一：<strong>内嵌 HTML</strong> 或 <strong>外链网页</strong>。
      内嵌模式下宿主会静默拦截成功的业务请求，页面无需编写对话框协议；
      外链跨域仍需页面或业务后端主动回传结果。
    </div>
    <a-spin :spinning="loading" style="min-height: 300px; display: block;">
      <div class="provider-grid">
        <EntityCard
          v-for="p in filteredList"
          :key="p.id || p.pageType"
          type="template"
          :name="p.displayName || p.pageType"
          @click="openDialog(p)"
        >
          <template #icon>
            <AppstoreOutlined />
            <span class="status-dot" :class="p.enabled === 1 ? 'status-active' : 'status-disabled'" />
          </template>
          <template #actions>
            <a-tooltip title="删除">
              <button class="btn-icon danger" @click="handleDelete(p)"><DeleteOutlined /></button>
            </a-tooltip>
            <a-dropdown :trigger="['click']">
              <button class="btn-icon" @click.prevent><MoreOutlined /></button>
              <template #overlay>
                <a-menu>
                  <a-menu-item @click="toggleEnabled(p, p.enabled !== 1)">
                    <CheckCircleOutlined v-if="p.enabled === 1" style="color: #16a34a; margin-right: 6px" />
                    <CloseCircleOutlined v-else style="color: #a3a3a3; margin-right: 6px" />
                    {{ p.enabled === 1 ? '禁用' : '启用' }}
                  </a-menu-item>
                  <a-menu-item @click="openDialog(p)">
                    <EditOutlined style="margin-right: 6px" /> 编辑
                  </a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
          </template>
          <div class="card-body">
            <div class="card-tags">
              <span class="tag tag-pagetype">{{ p.pageType }}</span>
              <span class="tag" :class="rendererTagClass(p)">{{ rendererLabel(p) }}</span>
            </div>
            <span class="card-desc">{{ truncateText(p.description || (p.pageHtml ? '已配置内嵌 HTML' : (p.pageUrl ? '已配置外链网页' : '暂无描述')), 50) }}</span>
          </div>
        </EntityCard>

        <LbEmptyState
          v-if="filteredList.length === 0 && !loading"
          :icon="AppstoreOutlined"
          :title="searchText ? '没有匹配的业务页' : '还没有业务页，点击右上角注册'"
        />
      </div>
    </a-spin>

    <a-modal
      v-model:open="dialogVisible"
      :title="form.id ? '编辑业务页' : '注册业务页'"
      :confirm-loading="saving"
      width="820px"
      destroy-on-close
      @ok="handleSave"
    >
      <a-form :label-col="{ flex: '0 0 100px' }">
        <a-form-item label="pageType" required>
          <a-input v-model:value="form.pageType" :disabled="!!form.id" placeholder="如 utility_bill_pay" />
        </a-form-item>
        <a-form-item label="展示名称" required>
          <a-input v-model:value="form.displayName" />
        </a-form-item>
        <a-form-item label="默认标题">
          <a-input v-model:value="form.defaultTitle" />
        </a-form-item>
        <a-form-item>
          <template #label>
            <span class="field-label">
              描述
              <a-tooltip
                :trigger="['hover', 'click']"
                placement="topLeft"
                :overlay-style="{ maxWidth: '320px' }"
                title="给建设者与 LLM 看的用途说明，会出现在工具可用列表的辅助信息中。"
              >
                <QuestionCircleOutlined class="field-help-icon" />
              </a-tooltip>
            </span>
          </template>
          <a-input v-model:value="form.description" placeholder="给建设者 / LLM 的用途说明" />
        </a-form-item>
        <a-form-item required>
          <template #label>
            <span class="field-label">
              呈现方式
              <a-tooltip
                :trigger="['hover', 'click']"
                placement="topLeft"
                :overlay-style="{ maxWidth: '360px' }"
              >
                <template #title>
                  <div class="help-tip-body">
                    <p><b>内嵌 HTML</b>：编写完整页面与业务 fetch，宿主自动感知成功提交并回传对话。</p>
                    <p><b>外链网页</b>：填写已部署地址；跨域无法静默拦截，需页面 postMessage 或业务后端回调。</p>
                  </div>
                </template>
                <QuestionCircleOutlined class="field-help-icon" />
              </a-tooltip>
            </span>
          </template>
          <a-radio-group v-model:value="form.renderMode" option-type="button" button-style="solid">
            <a-radio-button value="embedded">内嵌 HTML</a-radio-button>
            <a-radio-button value="external">外链网页</a-radio-button>
          </a-radio-group>
        </a-form-item>
        <template v-if="form.renderMode === 'embedded'">
          <a-form-item required>
            <template #label>
              <span class="field-label">
                页面内容
                <a-tooltip
                  :trigger="['hover', 'click']"
                  placement="topLeft"
                  :overlay-style="{ maxWidth: '380px' }"
                >
                  <template #title>
                    <div class="help-tip-body">
                      <p>页面只需 <code>fetch</code> 业务接口；成功的 POST/PUT/PATCH 由宿主自动回传。</p>
                      <p>取消按钮文案用「取消」，或 id/class 含 cancel。</p>
                      <p>办结摘要的字段名来自页面 <code>&lt;label&gt;</code>（或 <code>data-label</code>），请与输入框关联（包裹 / <code>for</code> / 相邻），勿依赖平台翻译 field key。</p>
                      <p>演示接口：<code>/__lightbot_bp_demo__</code></p>
                      <p>可选 options：<code>captureUrlIncludes</code> / <code>captureUrlExcludes</code> 收窄匹配。</p>
                    </div>
                  </template>
                  <QuestionCircleOutlined class="field-help-icon" />
                </a-tooltip>
              </span>
            </template>
            <div class="html-editor-toolbar">
              <a-button type="link" size="small" class="template-reset-btn" @click="resetHtmlTemplate">
                恢复示例模板
              </a-button>
              <a-button type="default" size="small" @click="openHtmlFullscreen">
                <FullscreenOutlined /> 最大化
              </a-button>
            </div>
            <a-textarea
              v-model:value="form.pageHtml"
              :rows="16"
              class="html-editor"
              placeholder="完整 HTML 文档（含 CSS/JS 与业务接口调用）…"
            />
          </a-form-item>
        </template>
        <template v-else>
          <a-form-item required>
            <template #label>
              <span class="field-label">
                页面地址
                <a-tooltip
                  :trigger="['hover', 'click']"
                  placement="topLeft"
                  :overlay-style="{ maxWidth: '380px' }"
                >
                  <template #title>
                    <div class="help-tip-body">
                      <p>外链跨域无法注入桥接。</p>
                      <p>可在外链页调用 <code>parent.postMessage({source:'lightbot-business-page', type:'submit', values:{...}}, '*')</code>，或由业务后端回调平台。</p>
                    </div>
                  </template>
                  <QuestionCircleOutlined class="field-help-icon" />
                </a-tooltip>
              </span>
            </template>
            <a-input v-model:value="form.pageUrl" placeholder="https://example.com/business-page" />
          </a-form-item>
        </template>
        <a-form-item label="启用">
          <a-switch v-model:checked="form.enabled" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 页面内容最大化 + AI 辅助 -->
    <a-modal
      v-model:open="htmlFullscreen"
      title="编辑页面内容"
      width="96vw"
      :footer="null"
      destroy-on-close
      wrap-class-name="bp-html-fullscreen-modal"
      @cancel="htmlFullscreen = false"
    >
      <div class="bp-html-max">
        <aside class="bp-ai-panel">
          <div class="bp-ai-title">
            <ThunderboltOutlined />
            AI 辅助生成
          </div>
          <p class="bp-ai-desc">用自然语言描述业务页字段与流程，生成完整 HTML。</p>
          <a-textarea
            v-model:value="aiRequirement"
            :rows="8"
            class="bp-ai-input"
            placeholder="例如：请假申请页，字段含开始日期、结束日期、事由；提交调用业务接口，失败时在页内提示。"
          />
          <a-checkbox v-model:checked="aiBasedOnCurrent">基于当前代码修改</a-checkbox>
          <a-button
            type="primary"
            block
            class="bp-ai-submit"
            :loading="aiGenerating"
            :disabled="!aiRequirement.trim()"
            @click="handleGenerateHtml"
          >
            <ThunderboltOutlined />
            {{ aiGenerating ? '生成中…' : '生成 HTML' }}
          </a-button>
          <a-button block @click="resetHtmlTemplate">恢复示例模板</a-button>
        </aside>
        <div class="bp-html-max-main">
          <a-textarea
            v-model:value="form.pageHtml"
            class="html-editor html-editor--max"
            placeholder="完整 HTML 文档…"
          />
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import {
  AppstoreOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  DeleteOutlined,
  EditOutlined,
  FullscreenOutlined,
  MoreOutlined,
  QuestionCircleOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons-vue'
import { Modal, message } from 'ant-design-vue'
import EntityCard from '../components/EntityCard.vue'
import LbEmptyState from '../components/common/LbEmptyState.vue'
import {
  deleteBusinessPage,
  generateBusinessPageHtml,
  listBusinessPages,
  setBusinessPageEnabled,
  upsertBusinessPage,
} from '../api/businessPage'
import { isRegisteredBusinessPage } from '../components/businessPages/businessPageRegistry'
import { DEFAULT_BUSINESS_PAGE_HTML } from '../utils/businessPageHtmlTemplate'
import { truncateText } from '../utils/format'

defineProps({
  hideHeader: { type: Boolean, default: false },
})

const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const searchText = ref('')
const list = ref([])
const htmlFullscreen = ref(false)
const aiRequirement = ref('')
const aiBasedOnCurrent = ref(true)
const aiGenerating = ref(false)

const form = reactive({
  id: null,
  pageType: '',
  displayName: '',
  defaultTitle: '',
  description: '',
  renderMode: 'embedded',
  pageHtml: DEFAULT_BUSINESS_PAGE_HTML,
  pageUrl: '',
  enabled: true,
})

const filteredList = computed(() => {
  const q = searchText.value.trim().toLowerCase()
  if (!q) return list.value
  return list.value.filter((p) =>
    [p.pageType, p.displayName, p.description]
      .filter(Boolean)
      .some((s) => String(s).toLowerCase().includes(q))
  )
})

function resolveRenderMode(record) {
  const hasHtml = !!String(record?.pageHtml || '').trim()
  const hasUrl = !!String(record?.pageUrl || '').trim()
  if (hasUrl && !hasHtml) return 'external'
  return 'embedded'
}

function rendererLabel(record) {
  if (isRegisteredBusinessPage(record.pageType)) return '宿主已注入组件'
  if (resolveRenderMode(record) === 'external') return '外链网页'
  if (record.pageHtml) return '内嵌 HTML'
  return '未配置页面'
}

function rendererTagClass(record) {
  if (isRegisteredBusinessPage(record.pageType)) return 'tag-host'
  if (record.pageHtml || record.pageUrl) return 'tag-h5'
  return 'tag-meta'
}

function resetHtmlTemplate() {
  form.pageHtml = DEFAULT_BUSINESS_PAGE_HTML
}

function openHtmlFullscreen() {
  if (!aiRequirement.value.trim()) {
    const parts = [
      form.displayName && `页面「${form.displayName}」`,
      form.description,
      '请生成可用的业务办理表单页。',
    ].filter(Boolean)
    aiRequirement.value = parts.join('\n')
  }
  htmlFullscreen.value = true
}

async function handleGenerateHtml() {
  const requirement = aiRequirement.value.trim()
  if (!requirement) {
    message.warning('请填写生成需求')
    return
  }
  aiGenerating.value = true
  try {
    const res = await generateBusinessPageHtml({
      requirement,
      pageType: form.pageType || undefined,
      displayName: form.displayName || undefined,
      description: form.description || undefined,
      basedOnCurrent: aiBasedOnCurrent.value,
      currentHtml: aiBasedOnCurrent.value ? (form.pageHtml || '') : undefined,
    })
    const html = res.data?.html
    if (!html) {
      message.error('未返回 HTML 内容')
      return
    }
    form.pageHtml = html
    message.success('已生成并填入编辑器')
  } catch (e) {
    message.error(e?.message || 'AI 生成失败')
  } finally {
    aiGenerating.value = false
  }
}

async function loadList() {
  loading.value = true
  try {
    const res = await listBusinessPages()
    list.value = res.data || []
  } finally {
    loading.value = false
  }
}

function openDialog(record) {
  if (record) {
    const mode = resolveRenderMode(record)
    Object.assign(form, {
      id: record.id,
      pageType: record.pageType,
      displayName: record.displayName,
      defaultTitle: record.defaultTitle,
      description: record.description || '',
      renderMode: mode,
      pageHtml: record.pageHtml || DEFAULT_BUSINESS_PAGE_HTML,
      pageUrl: record.pageUrl || '',
      enabled: record.enabled === 1,
    })
  } else {
    Object.assign(form, {
      id: null,
      pageType: '',
      displayName: '',
      defaultTitle: '',
      description: '',
      renderMode: 'embedded',
      pageHtml: DEFAULT_BUSINESS_PAGE_HTML,
      pageUrl: '',
      enabled: true,
    })
  }
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.pageType?.trim() || !form.displayName?.trim()) {
    message.warning('pageType 与展示名称不能为空')
    return
  }
  const embedded = form.renderMode === 'embedded'
  const pageHtml = embedded ? (form.pageHtml?.trim() || '') : ''
  const pageUrl = embedded ? '' : (form.pageUrl?.trim() || '')
  if (embedded && !pageHtml) {
    message.warning('请编写内嵌 HTML 页面内容')
    return
  }
  if (!embedded && !pageUrl) {
    message.warning('请填写外链网页地址')
    return
  }
  if (pageUrl && !/^https?:\/\//i.test(pageUrl)) {
    message.warning('外链须以 http:// 或 https:// 开头')
    return
  }
  saving.value = true
  try {
    await upsertBusinessPage({
      pageType: form.pageType.trim(),
      displayName: form.displayName.trim(),
      defaultTitle: form.defaultTitle || form.displayName,
      description: form.description,
      // 二选一：仅保存当前模式对应字段
      pageHtml: pageHtml || null,
      pageUrl: pageUrl || null,
      enabled: form.enabled,
      allowedModes: ['inline'],
      allowedActions: ['submit', 'cancel'],
      allowedPropKeys: [],
      allowedOptionKeys: ['primaryButtonText', 'cancelButtonText', 'hint'],
      defaultProps: {},
    })
    message.success('已保存')
    dialogVisible.value = false
    await loadList()
  } finally {
    saving.value = false
  }
}

async function toggleEnabled(record, enabled) {
  await setBusinessPageEnabled(record.id, enabled)
  record.enabled = enabled ? 1 : 0
  message.success(enabled ? '已启用' : '已禁用')
}

function handleDelete(record) {
  Modal.confirm({
    title: '删除业务页',
    content: `确认删除「${record.displayName || record.pageType}」？`,
    okType: 'danger',
    async onOk() {
      await deleteBusinessPage(record.id)
      message.success('已删除')
      await loadList()
    },
  })
}

function search(text) {
  searchText.value = text || ''
}

function refresh() {
  return loadList()
}

onMounted(loadList)

defineExpose({ openDialog, search, refresh, loading })
</script>

<style scoped>
.page {
  height: auto;
  overflow: visible;
  padding: 0;
  background: transparent;
}
.page-intro {
  margin-bottom: 16px;
  padding: 12px 14px;
  border-radius: 10px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  font-size: 13px;
  color: #475569;
  line-height: 1.6;
}
.page-intro code {
  font-size: 12px;
  padding: 1px 5px;
  border-radius: 4px;
  background: #e2e8f0;
}
.provider-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}
.card-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 4px;
}
.card-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.tag {
  display: inline-flex;
  align-items: center;
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 11px;
  line-height: 18px;
  background: #f4f4f5;
  color: #52525b;
}
.tag-pagetype {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  background: #f1f5f9;
  color: #334155;
}
.tag-host { background: #ecfdf5; color: #047857; }
.tag-h5 { background: #fdf4ff; color: #a21caf; }
.tag-meta { background: #fff7ed; color: #c2410c; }
.card-desc {
  font-size: 13px;
  color: var(--color-mute, #737373);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.status-dot {
  position: absolute;
  right: -2px;
  bottom: -2px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  border: 1.5px solid #fff;
}
.status-active { background: #22c55e; }
.status-disabled { background: #a3a3a3; }
.btn-icon {
  border: none;
  background: transparent;
  cursor: pointer;
  padding: 4px;
  border-radius: 6px;
  color: #737373;
  display: inline-flex;
  align-items: center;
}
.btn-icon:hover { background: #f4f4f5; color: #171717; }
.btn-icon.danger:hover { background: #fef2f2; color: #dc2626; }
.field-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.field-help-icon {
  font-size: 13px;
  color: var(--color-mute, #a3a3a3);
  cursor: help;
}
.field-help-icon:hover {
  color: var(--color-body, #52525b);
}
.html-editor-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}
.html-editor {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  line-height: 1.45;
}
.html-editor--max {
  height: calc(100vh - 180px);
  min-height: 480px;
  resize: none;
}
.template-reset-btn {
  padding-left: 0;
}
.bp-html-max {
  display: grid;
  grid-template-columns: 300px 1fr;
  gap: 16px;
  min-height: 60vh;
}
.bp-ai-panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  background: #fafafa;
}
.bp-ai-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  font-size: 14px;
  color: #171717;
}
.bp-ai-desc {
  margin: 0;
  font-size: 12px;
  color: #737373;
  line-height: 1.5;
}
.bp-ai-input {
  font-size: 13px;
}
.bp-ai-submit {
  margin-top: 4px;
}
.bp-html-max-main {
  min-width: 0;
}
.help-tip-body {
  font-size: 12px;
  line-height: 1.55;
}
.help-tip-body p {
  margin: 0 0 6px;
}
.help-tip-body p:last-child {
  margin-bottom: 0;
}
.help-tip-body code {
  font-size: 11px;
  padding: 0 3px;
  border-radius: 3px;
  background: rgba(255, 255, 255, 0.16);
}
</style>

<style>
.bp-html-fullscreen-modal .ant-modal {
  top: 12px;
  padding-bottom: 0;
  max-width: 96vw;
}
.bp-html-fullscreen-modal .ant-modal-body {
  padding-top: 12px;
}
@media (max-width: 960px) {
  .bp-html-max {
    grid-template-columns: 1fr !important;
  }
  .html-editor--max {
    height: 50vh !important;
    min-height: 280px !important;
  }
}
</style>
