<template>
  <div
    class="markdown-preview"
    :class="{ 'no-image-preview': !imagePreview }"
    ref="containerRef"
    @click="onContainerClick"
    @mouseover="onContainerMouseOver"
    @mouseleave="onContainerMouseLeave"
  >
    <div v-html="renderedHtml"></div>
    <span v-if="!finalized" class="typing-cursor">|</span>
  </div>
  <a-tooltip
    v-model:open="copyTooltipOpen"
    :title="copyTooltipTitle"
    placement="top"
    :get-popup-container="tooltipPopupContainer"
  >
    <span class="code-copy-tooltip-anchor" :style="copyTooltipStyle" />
  </a-tooltip>
  <ChatMediaPreview v-if="imagePreview" v-model:open="previewOpen" :src="previewSrc" media-type="image" />
  <MermaidPreviewModal v-model:open="mermaidPreviewOpen" :source="mermaidPreviewSource" />
</template>

<script setup>
import { watch, shallowRef, ref, nextTick } from 'vue'
import { message } from 'ant-design-vue'
import { renderMarkdown, CODE_COPY_BTN_ICON, CODE_COPIED_BTN_ICON } from '@/utils/markdown_preview'
import { renderMermaidDiagrams, resetMermaidTheme } from '@/utils/mermaidRender'
import { useTheme } from '@/composables/useTheme'
import ChatMediaPreview from '@/components/ChatMediaPreview.vue'
import MermaidPreviewModal from '@/components/MermaidPreviewModal.vue'
import 'katex/dist/katex.min.css'

const props = defineProps({
  content: { type: String, default: '' },
  /** false=流式进行中（补全未闭合标记）；true=对话结束后的终态渲染 */
  finalized: { type: Boolean, default: true },
  /** 是否启用图片点击预览（仅 AI 对话场景需要） */
  imagePreview: { type: Boolean, default: true },
  /** 是否剥离 YAML frontmatter（SKILL.md 等文件需要） */
  stripFrontmatter: { type: Boolean, default: false },
})

const { isDark } = useTheme()
const renderedHtml = shallowRef('')
const containerRef = ref(null)

// ── 图片 / Mermaid 预览 ──
const previewOpen = ref(false)
const previewSrc = ref('')
const mermaidPreviewOpen = ref(false)
const mermaidPreviewSource = ref('')

const copyTooltipOpen = ref(false)
const copyTooltipTitle = ref('复制')
const copyTooltipStyle = ref({
  position: 'fixed',
  left: '-9999px',
  top: '-9999px',
  width: '1px',
  height: '1px',
  pointerEvents: 'none',
})

function tooltipPopupContainer() {
  return document.body
}

function showCopyTooltip(btn) {
  const rect = btn.getBoundingClientRect()
  copyTooltipTitle.value = btn.classList.contains('is-copied') ? '已复制' : '复制'
  copyTooltipStyle.value = {
    position: 'fixed',
    left: `${rect.left + rect.width / 2}px`,
    top: `${rect.top}px`,
    width: '1px',
    height: '1px',
    pointerEvents: 'none',
  }
  copyTooltipOpen.value = true
}

function onContainerMouseOver(e) {
  const btn = e.target.closest?.('.code-copy-btn')
  if (btn && containerRef.value?.contains(btn)) {
    showCopyTooltip(btn)
  }
}

function onContainerMouseLeave(e) {
  if (!containerRef.value?.contains(e.relatedTarget)) {
    copyTooltipOpen.value = false
  }
}

async function copyCodeText(text, btn) {
  if (!text) return
  try {
    await navigator.clipboard.writeText(text)
    btn.innerHTML = CODE_COPIED_BTN_ICON
    btn.classList.add('is-copied')
    showCopyTooltip(btn)
    setTimeout(() => {
      btn.innerHTML = CODE_COPY_BTN_ICON
      btn.classList.remove('is-copied')
      if (copyTooltipOpen.value) {
        copyTooltipTitle.value = '复制'
      }
    }, 1500)
  } catch {
    message.error('复制失败')
  }
}

function onContainerClick(e) {
  const copyBtn = e.target.closest('.code-copy-btn')
  if (copyBtn && containerRef.value?.contains(copyBtn)) {
    e.preventDefault()
    e.stopPropagation()
    const wrap = copyBtn.closest('.code-block-wrap')
    const codeEl = wrap?.querySelector('pre code') || wrap?.querySelector('pre')
    copyCodeText(codeEl?.textContent || '', copyBtn)
    return
  }

  const mermaidEl = e.target.closest('.mermaid-diagram-clickable')
  if (mermaidEl && containerRef.value?.contains(mermaidEl)) {
    e.preventDefault()
    mermaidPreviewSource.value = mermaidEl.dataset.mermaidSource || ''
    mermaidPreviewOpen.value = true
    return
  }

  // 拦截 markdown 内的链接点击：外部链接新窗口打开，相对链接阻止跳转
  const anchor = e.target.closest('a')
  if (anchor && containerRef.value?.contains(anchor)) {
    e.preventDefault()
    const href = anchor.getAttribute('href')
    if (href && (href.startsWith('http://') || href.startsWith('https://'))) {
      window.open(href, '_blank', 'noopener')
    }
    return
  }

  if (!props.imagePreview) return
  const img = e.target.closest('img')
  if (!img || !containerRef.value?.contains(img)) return
  if (img.src) {
    previewSrc.value = img.src
    previewOpen.value = true
  }
}

/** 剥离 YAML frontmatter（--- 包裹的头部元数据） */
function stripFrontmatter(text) {
  if (!text) return text
  return text.replace(/^---\n[\s\S]*?\n---\n?/, '')
}

watch(
  () => [props.content, props.finalized, isDark.value],
  async ([val, finalized], _, onCleanup) => {
    let expired = false
    onCleanup(() => { expired = true })

    if (!val) {
      renderedHtml.value = ''
      return
    }

    const textToRender = props.stripFrontmatter ? stripFrontmatter(val) : val
    const theme = isDark.value ? 'github-dark' : 'github-light'
    const html = await renderMarkdown(textToRender, { streaming: !finalized, theme })
    if (expired) return
    renderedHtml.value = html

    if (finalized && html.includes('class="mermaid"')) {
      await nextTick()
      if (expired) return
      resetMermaidTheme()
      await renderMermaidDiagrams(containerRef.value, isDark.value)
    }
  },
  { immediate: true }
)
</script>

<style lang="less">
.markdown-preview {
  max-width: 100%;
  color: var(--gray-1000);
  font-family:
    -apple-system, BlinkMacSystemFont, 'Noto Sans SC', 'PingFang SC', 'Microsoft YaHei',
    'Hiragino Sans GB', 'Source Han Sans CN', sans-serif;
  font-size: 15px;
  line-height: 1.75;
  word-break: break-word;

  h1, h2 { font-size: 1.25rem; margin: 1rem 0 0.5rem; }
  h3, h4 { font-size: 1.1rem; margin: 0.8rem 0 0.4rem; }
  h5, h6 { font-size: 1rem; margin: 0.6rem 0 0.3rem; }

  p { margin: 0.4rem 0; }
  p:last-child { margin-bottom: 0; }

  strong { font-weight: 600; }

  ul, ol { padding-left: 1.625rem; margin: 0.4rem 0; }
  ul:not(.contains-task-list) { list-style: disc; }
  ol { list-style: decimal; }
  li { margin: 0.15rem 0; }
  li > p, ol > p, ul > p { margin: 0.25rem 0; }

  ul.contains-task-list,
  ul.task-list {
    list-style: none;
    padding-left: 0;
  }
  .task-list-item {
    display: flex;
    align-items: flex-start;
    gap: 8px;
    list-style: none;
  }
  .task-list-item input[type="checkbox"] {
    appearance: none;
    -webkit-appearance: none;
    width: 16px;
    height: 16px;
    margin: 4px 0 0;
    border: 2px solid var(--gray-300);
    border-radius: 4px;
    background: var(--color-canvas);
    flex-shrink: 0;
    cursor: default;
    position: relative;
  }
  .task-list-item input[type="checkbox"]:checked {
    background: #16a34a;
    border-color: #16a34a;
  }
  .task-list-item input[type="checkbox"]:checked::after {
    content: '';
    position: absolute;
    left: 4px;
    top: 1px;
    width: 4px;
    height: 8px;
    border: solid #fff;
    border-width: 0 2px 2px 0;
    transform: rotate(45deg);
  }

  details.md-details {
    margin: 0.8rem 0;
    border: 1px solid var(--gray-100);
    border-radius: 8px;
    background: var(--color-canvas-soft);
    overflow: hidden;
  }
  details.md-details > summary {
    cursor: pointer;
    padding: 10px 14px;
    font-weight: 600;
    color: var(--gray-800);
    list-style: none;
    user-select: none;
  }
  details.md-details > summary::-webkit-details-marker { display: none; }
  details.md-details > summary::before {
    content: '▸';
    display: inline-block;
    margin-right: 8px;
    transition: transform 0.15s;
    color: var(--gray-500);
  }
  details.md-details[open] > summary::before {
    transform: rotate(90deg);
  }
  .md-details-body {
    padding: 0 14px 14px;
    border-top: 1px solid var(--gray-100);
  }
  .md-details-body > :first-child { margin-top: 12px; }
  .md-details-body ul:not(.contains-task-list) { list-style: disc; }
  .md-details-body ol { list-style: decimal; }

  .code-block-wrap {
    margin: 0.6rem 0;
    border: 1px solid var(--gray-100);
    border-radius: 8px;
    overflow: hidden;
    background: var(--gray-25);
  }
  .code-block-toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    padding: 6px 10px;
    border-bottom: 1px solid var(--gray-100);
    background: var(--color-canvas-soft);
  }
  .code-block-lang {
    font-size: 12px;
    color: var(--gray-600);
    font-family: 'Menlo', 'Monaco', 'Consolas', monospace;
    text-transform: lowercase;
  }
  .code-copy-btn {
    border: none;
    background: transparent;
    color: var(--gray-600);
    cursor: pointer;
    width: 28px;
    height: 28px;
    padding: 0;
    border-radius: 6px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    font-size: 14px;
    line-height: 1;
  }
  .code-copy-btn:hover { background: var(--gray-25); color: var(--main-700); }
  .code-copy-btn.is-copied { color: #16a34a; }
  .code-block-wrap > pre {
    margin: 0;
    border: none;
    border-radius: 0;
  }

  a { color: var(--main-700); text-decoration: none; }
  a:hover { text-decoration: underline; }

  hr {
    height: 0;
    margin: 1rem 0;
    border: 0;
    border-top: 1px solid var(--gray-200);
  }
  hr.md-hr-asterisk {
    border-top-style: dashed;
    border-top-color: var(--gray-300);
  }
  hr.md-hr-underscore {
    border-top-width: 3px;
    border-top-style: double;
  }

  blockquote {
    margin: 0.6rem 0;
    padding: 0.25rem 0 0.25rem 1rem;
    border-left: 3px solid var(--gray-200);
    color: var(--gray-700);
  }

  code {
    font-family: 'Menlo', 'Monaco', 'Consolas', 'Courier New', monospace;
    font-size: 13px;
    line-height: 1.5;
  }

  :not(pre) > code {
    padding: 1px 5px;
    border-radius: 4px;
    background-color: var(--gray-25);
  }

  pre {
    margin: 0.6rem 0;
    padding: 12px 14px;
    border: 1px solid var(--gray-100);
    border-radius: 8px;
    overflow: auto;
    font-size: 13px;
    line-height: 1.5;
    background: var(--gray-25);

    code {
      padding: 0;
      background: none;
      border-radius: 0;
    }
  }

  table {
    width: 100%;
    border-collapse: collapse;
    margin: 0.8rem 0;
    font-size: 14px;
    display: table;
    border: 1px solid var(--gray-100);
    border-radius: 8px;
    overflow: hidden;
  }

  th, td {
    padding: 8px 12px;
    text-align: left;
    border: 1px solid var(--gray-100);
  }

  th {
    background-color: var(--gray-25);
    color: var(--gray-800);
    font-weight: 600;
  }

  td { color: var(--gray-800); }

  tbody tr:hover { background-color: var(--gray-25); }

  img {
    display: block;
    max-width: 360px;
    max-height: 260px;
    height: auto;
    border-radius: 6px;
    cursor: pointer;
    object-fit: contain;
    transition: transform 0.2s;
    margin: 0.5rem auto;
  }
  img:hover {
    transform: scale(1.02);
  }

  .mermaid-diagram {
    margin: 0.8rem 0;
    padding: 12px;
    border: 1px solid var(--gray-100);
    border-radius: 8px;
    background: var(--color-canvas);
    overflow-x: auto;
    text-align: center;
  }
  .mermaid-diagram-clickable {
    cursor: zoom-in;
  }
  .mermaid-diagram-clickable:hover {
    border-color: var(--main-700);
    box-shadow: 0 0 0 1px rgba(37, 99, 235, 0.12);
  }
  .mermaid-diagram svg {
    max-width: 100%;
    height: auto;
  }
  pre.mermaid {
    margin: 0.8rem 0;
    padding: 12px 14px;
    border: 1px solid var(--gray-100);
    border-radius: 8px;
    background: var(--gray-25);
    font-family: 'Menlo', 'Monaco', 'Consolas', monospace;
    font-size: 13px;
    line-height: 1.5;
    overflow: auto;
    white-space: pre;
  }
  pre.mermaid.mermaid-render-error {
    border-color: #fca5a5;
    color: #b91c1c;
  }

  /* Shiki 代码高亮块 */
  .code-block-wrap pre.shiki,
  pre.shiki {
    margin: 0.6rem 0;
    padding: 12px 14px;
    border: 1px solid var(--gray-100);
    border-radius: 8px;
    overflow: auto;
    font-size: 13px;
    line-height: 1.5;
  }
  .code-block-wrap pre.shiki {
    margin: 0;
    border: none;
    border-radius: 0;
  }
  pre.shiki code {
    padding: 0;
    background: none;
    border-radius: 0;
    font-family: 'Menlo', 'Monaco', 'Consolas', monospace;
  }
}
.no-image-preview img {
  cursor: default;
}
.no-image-preview img:hover {
  transform: none;
}

/* 10.2 打字光标：流式输出时在末尾闪烁 */
.typing-cursor {
  display: inline;
  animation: typing-cursor-blink 0.8s step-end infinite;
  color: var(--gray-600);
  font-weight: 400;
  margin-left: 1px;
}
@keyframes typing-cursor-blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

.code-copy-tooltip-anchor {
  position: fixed;
  z-index: -1;
  pointer-events: none;
}
</style>
