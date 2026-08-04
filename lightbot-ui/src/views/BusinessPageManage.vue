<template>
  <div class="page">
    <div class="page-intro">
      <strong>主路径：直接编写 H5 HTML。</strong>
      对话内用 iframe <code>srcdoc</code> 嵌套渲染；接口与样式写在 HTML 内，通过
      <code>postMessage</code> 回传提交/取消。
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
            <span class="card-desc">{{ truncateText(p.description || (p.pageHtml ? '已配置 H5 HTML' : '暂无描述'), 50) }}</span>
          </div>
        </EntityCard>

        <LbEmptyState
          v-if="filteredList.length === 0 && !loading"
          :icon="AppstoreOutlined"
          :title="searchText ? '没有匹配的业务页' : '还没有业务页，点击右上角编写 H5'"
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
      <a-alert
        type="info"
        show-icon
        class="modal-alert"
        message="在下方直接编写完整 H5（含 HTML/CSS/JS）。对话中以 iframe 嵌套展示，无需填写外链。"
      />
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
        <a-form-item label="描述">
          <a-input v-model:value="form.description" placeholder="给建设者 / LLM 的用途说明" />
        </a-form-item>
        <a-form-item label="H5 页面" required>
          <a-textarea
            v-model:value="form.pageHtml"
            :rows="16"
            class="html-editor"
            placeholder="完整 HTML 文档…"
          />
          <div class="form-hint">
            提交：<code>parent.postMessage({source:'lightbot-business-page', type:'submit', values:{...}}, '*')</code>
            ；取消：<code>type:'cancel'</code>。收到父页 <code>type=init</code> 可填充 props。
            <a-button type="link" size="small" @click="resetHtmlTemplate">恢复示例模板</a-button>
          </div>
        </a-form-item>
        <a-collapse ghost>
          <a-collapse-panel key="adv" header="高级（可选兜底）">
            <a-form-item label="外链 URL">
              <a-input v-model:value="form.pageUrl" placeholder="无 HTML 时可用 https://..." />
            </a-form-item>
            <a-form-item label="formSchema">
              <a-textarea v-model:value="form.formSchemaText" :rows="4" placeholder="无 H5 时的通用表单兜底 JSON" />
            </a-form-item>
          </a-collapse-panel>
        </a-collapse>
        <a-form-item label="启用">
          <a-switch v-model:checked="form.enabled" />
        </a-form-item>
      </a-form>
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
  MoreOutlined,
} from '@ant-design/icons-vue'
import { Modal, message } from 'ant-design-vue'
import EntityCard from '../components/EntityCard.vue'
import LbEmptyState from '../components/common/LbEmptyState.vue'
import {
  deleteBusinessPage,
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

const form = reactive({
  id: null,
  pageType: '',
  displayName: '',
  defaultTitle: '',
  description: '',
  pageHtml: DEFAULT_BUSINESS_PAGE_HTML,
  pageUrl: '',
  formSchemaText: '',
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

function hasFormSchema(record) {
  if (!record?.formSchema) return false
  try {
    const schema = typeof record.formSchema === 'string' ? JSON.parse(record.formSchema) : record.formSchema
    return Array.isArray(schema?.fields) && schema.fields.length > 0
  } catch {
    return false
  }
}

function rendererLabel(record) {
  if (isRegisteredBusinessPage(record.pageType)) return '宿主已注入组件'
  if (record.pageHtml) return 'H5 HTML'
  if (record.pageUrl) return 'H5 外链'
  if (hasFormSchema(record)) return '通用表单兜底'
  return '仅元数据'
}

function rendererTagClass(record) {
  if (isRegisteredBusinessPage(record.pageType)) return 'tag-host'
  if (record.pageHtml || record.pageUrl) return 'tag-h5'
  if (hasFormSchema(record)) return 'tag-generic'
  return 'tag-meta'
}

function resetHtmlTemplate() {
  form.pageHtml = DEFAULT_BUSINESS_PAGE_HTML
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
    Object.assign(form, {
      id: record.id,
      pageType: record.pageType,
      displayName: record.displayName,
      defaultTitle: record.defaultTitle,
      description: record.description || '',
      pageHtml: record.pageHtml || DEFAULT_BUSINESS_PAGE_HTML,
      pageUrl: record.pageUrl || '',
      formSchemaText: normalizeFormSchemaText(record.formSchema),
      enabled: record.enabled === 1,
    })
  } else {
    Object.assign(form, {
      id: null,
      pageType: '',
      displayName: '',
      defaultTitle: '',
      description: '',
      pageHtml: DEFAULT_BUSINESS_PAGE_HTML,
      pageUrl: '',
      formSchemaText: '',
      enabled: true,
    })
  }
  dialogVisible.value = true
}

function normalizeFormSchemaText(raw) {
  if (!raw) return ''
  if (typeof raw === 'string') {
    try {
      return JSON.stringify(JSON.parse(raw), null, 2)
    } catch {
      return raw
    }
  }
  try {
    return JSON.stringify(raw, null, 2)
  } catch {
    return ''
  }
}

async function handleSave() {
  if (!form.pageType?.trim() || !form.displayName?.trim()) {
    message.warning('pageType 与展示名称不能为空')
    return
  }
  const pageHtml = form.pageHtml?.trim() || ''
  const pageUrl = form.pageUrl?.trim() || ''
  let formSchema = null
  if (form.formSchemaText?.trim()) {
    try {
      formSchema = JSON.parse(form.formSchemaText)
    } catch {
      message.error('formSchema 不是合法 JSON')
      return
    }
  }
  if (!pageHtml && !pageUrl && !formSchema?.fields?.length) {
    message.warning('请编写 H5 HTML（推荐）')
    return
  }
  if (pageUrl && !/^https?:\/\//i.test(pageUrl)) {
    message.warning('外链须以 http:// 或 https:// 开头')
    return
  }
  saving.value = true
  try {
    const propKeys = Array.isArray(formSchema?.fields)
      ? formSchema.fields.map((f) => f.key).filter(Boolean)
      : []
    await upsertBusinessPage({
      pageType: form.pageType.trim(),
      displayName: form.displayName.trim(),
      defaultTitle: form.defaultTitle || form.displayName,
      description: form.description,
      pageHtml: pageHtml || null,
      pageUrl: pageUrl || null,
      formSchema,
      enabled: form.enabled,
      allowedModes: ['inline'],
      allowedActions: ['submit', 'cancel'],
      allowedPropKeys: propKeys,
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
.tag-generic { background: #eff6ff; color: #1d4ed8; }
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
.modal-alert { margin-bottom: 16px; }
.html-editor {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  line-height: 1.45;
}
.form-hint {
  margin-top: 6px;
  font-size: 12px;
  color: #737373;
  line-height: 1.5;
}
.form-hint code {
  font-size: 11px;
  padding: 0 4px;
  border-radius: 4px;
  background: #f4f4f5;
}
</style>
