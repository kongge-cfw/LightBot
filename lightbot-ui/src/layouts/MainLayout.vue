<template>
  <div class="layout" :class="{ 'layout--rail-hidden': !sessionRailVisible, 'layout--rail-collapsed': sidebarCollapsed && sessionRailVisible }">
    <!-- 顶部主导航 -->
    <header v-show="!isWorkflowRoute" class="topbar">
      <div class="topbar-left">
        <a class="brand" href="/" @click.prevent="router.push('/')">
          <img src="/lightbot-logo.png" alt="LightBot" class="brand-logo" />
        </a>
        <nav class="top-nav" aria-label="主导航">
          <router-link
            v-for="item in navItems"
            :key="item.path"
            :to="item.path"
            :class="['top-nav-item', { active: isActive(item.path) }]"
          >
            <component :is="item.icon" class="top-nav-icon" />
            <span>{{ item.label }}</span>
          </router-link>
        </nav>
      </div>
      <div class="topbar-right">
        <a-dropdown
          v-model:open="userDropdownOpen"
          :trigger="['click']"
          :getPopupContainer="getPopupContainer"
          overlayClassName="sidebar-user-dropdown"
          :overlayStyle="{ width: '160px' }"
        >
          <button type="button" class="user-chip">
            <AvatarFrame :frame="userStore.user?.avatarFrame" :size="28">
              <div class="user-avatar">
                <img
                  v-if="userStore.user?.avatar"
                  :src="userStore.user.avatar"
                  alt="avatar"
                  class="user-avatar-img"
                  @error="userStore.user?.avatar && (userStore.user.avatar = '')"
                />
                <span v-else>{{ (userStore.user?.username || userStore.user?.nickname || 'U')[0] }}</span>
              </div>
            </AvatarFrame>
            <span class="user-name">{{ userStore.user?.username || userStore.user?.nickname || '用户' }}</span>
            <LevelTag :level="userStore.user?.level" size="small" />
            <a-badge
              v-if="taskBadgeCount"
              :count="taskBadgeCount"
              :number-style="taskBadgeStyle"
              :class="['sidebar-task-badge-inline', taskBadgePopClass]"
              @click.stop="router.push('/app/tasks')"
            />
            <DownOutlined class="user-chevron" :class="{ open: userDropdownOpen }" />
          </button>
          <template #overlay>
            <a-menu @click="handleCommand">
              <a-menu-item key="user-info" class="menu-user-info" @click="router.push('/app/profile')">
                <div class="user-info-display">
                  <div class="user-info-name-row">
                    <span class="user-info-name">{{ userStore.user?.username || userStore.user?.nickname || '用户' }}</span>
                    <span class="user-info-role">{{ userRoleText }}</span>
                  </div>
                  <div class="user-info-meta">
                    <span class="user-info-id">ID: {{ userStore.user?.id }}</span>
                  </div>
                </div>
              </a-menu-item>
              <a-menu-divider />
              <a-menu-item key="tasks">
                <div class="menu-item-with-badge">
                  <span class="menu-item-content"><CheckSquareOutlined /><span>任务中心</span></span>
                  <a-badge v-if="taskBadgeCount" :count="taskBadgeCount" :number-style="{ fontSize: '10px', boxShadow: 'none', backgroundColor: '#f5222d' }" />
                </div>
              </a-menu-item>
              <a-menu-item key="sessions"><span class="menu-item-content"><MessageOutlined /><span>会话管理</span></span></a-menu-item>
              <a-menu-item v-if="userStore.user?.role === 'admin'" key="settings"><span class="menu-item-content"><SettingOutlined /><span>系统管理</span></span></a-menu-item>
              <a-menu-item v-if="userStore.user?.role === 'admin'" key="model-providers"><span class="menu-item-content"><ApiOutlined /><span>模型管理</span></span></a-menu-item>
              <a-menu-item v-if="userStore.user?.role === 'admin'" key="logs"><span class="menu-item-content"><FileTextOutlined /><span>日志</span></span></a-menu-item>
              <a-menu-divider />
              <a-menu-item key="theme" @click="toggleTheme">
                <span class="menu-item-content">
                  <BulbFilled v-if="isDark" />
                  <BulbOutlined v-else />
                  <span>{{ isDark ? '浅色模式' : '深色模式' }}</span>
                </span>
              </a-menu-item>
              <a-menu-divider />
              <a-menu-item key="logout"><span class="menu-item-content"><LogoutOutlined /><span>退出登录</span></span></a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
      </div>
    </header>

    <div class="layout-body">
      <!-- 左侧会话轨：仅「对话」菜单下展示 -->
      <aside v-if="sessionRailVisible" :class="['session-rail', { collapsed: sidebarCollapsed }]">
        <div class="rail-header">
          <span v-show="!sidebarCollapsed" class="rail-title">最近对话</span>
          <div class="rail-header-actions">
            <a-tooltip v-if="!sidebarCollapsed" title="搜索历史对话" placement="bottom">
              <button type="button" class="rail-icon-btn" @click="openConversationSearch">
                <SearchOutlined />
              </button>
            </a-tooltip>
            <a-tooltip :title="sidebarCollapsed ? '展开会话栏' : '收起会话栏'" placement="right">
              <button type="button" class="rail-icon-btn" @click="toggleSidebar">
                <MenuUnfoldOutlined v-if="sidebarCollapsed" />
                <MenuFoldOutlined v-else />
              </button>
            </a-tooltip>
          </div>
        </div>

        <a-tooltip v-if="sidebarCollapsed" title="新建对话" placement="right">
          <button type="button" class="btn-new-chat-rail" @click="newChat">
            <PlusOutlined />
          </button>
        </a-tooltip>
        <button v-else type="button" class="btn-new-chat-rail btn-new-chat-rail--full" @click="newChat">
          <PlusOutlined />
          <span>新建对话</span>
        </button>

        <div v-show="!sidebarCollapsed" class="session-section">
          <div class="session-list" ref="sessionListRef">
            <TransitionGroup name="lb-list">
              <div
                v-for="s in sessions"
                :key="s.id"
                :class="['session-item', { active: currentSessionId === s.id, 'session-item--pinned': s.pinned }]"
                @click="switchSession(s)"
              >
                <span class="session-title">{{ s.title || '新对话' }}</span>
                <span v-if="s.lastMessageAt" class="session-time">{{ formatRelativeTime(s.lastMessageAt) }}</span>
                <PushpinFilled v-if="s.pinned" class="session-pin-icon" aria-label="已置顶" />
                <a-dropdown :trigger="['click']" placement="bottomRight">
                  <EllipsisOutlined class="session-more" @click.stop />
                  <template #overlay>
                    <a-menu @click="({ key }) => handleSessionMenu(key, s)">
                      <a-menu-item key="pin">{{ s.pinned ? '取消置顶' : '置顶' }}</a-menu-item>
                      <a-menu-item key="rename">重命名</a-menu-item>
                      <a-menu-item key="export">导出</a-menu-item>
                      <a-menu-divider />
                      <a-menu-item key="delete" class="menu-danger">删除</a-menu-item>
                    </a-menu>
                  </template>
                </a-dropdown>
              </div>
            </TransitionGroup>
            <div v-if="sessionLoading" class="session-loading-more">
              <LoadingOutlined spin style="font-size: 12px; color: var(--color-mute)" />
              <span v-if="sessions.length === 0" class="session-loading-text">加载中...</span>
            </div>
            <div v-if="sessionHasMore && !sessionLoading" ref="sessionLoadMoreRef" class="session-load-more-sentinel"></div>
            <div v-if="sessions.length === 0 && !sessionLoading" class="session-empty">暂无对话</div>
          </div>
        </div>
      </aside>

      <main class="main-content">
        <router-view v-slot="{ Component, route: r }">
          <transition name="lb-route" mode="out-in">
            <keep-alive :include="cachedRouteNames">
              <component
                :is="Component"
                :key="r.path.startsWith('/app/chat') ? '/app/chat' : r.path"
              />
            </keep-alive>
          </transition>
        </router-view>
      </main>
    </div>

    <a-modal
      v-model:open="renameVisible"
      title="重命名对话"
      :width="400"
      @ok="confirmRename"
      @cancel="renameVisible = false"
    >
      <a-input
        v-model:value="renameValue"
        placeholder="请输入新名称"
        :maxlength="50"
        @press-enter="confirmRename"
      />
    </a-modal>

    <ConversationSearchModal
      v-model:open="conversationSearchOpen"
      @pick="handleConversationSearchPick"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch, markRaw } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  PlusOutlined,
  DownOutlined,
  EllipsisOutlined,
  PushpinFilled,
  RobotOutlined,
  DatabaseOutlined,
  ToolOutlined,
  DashboardOutlined,
  EyeOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  FileTextOutlined,
  ExperimentOutlined,
  CheckSquareOutlined,
  MessageOutlined,
  SettingOutlined,
  ApiOutlined,
  LogoutOutlined,
  LoadingOutlined,
  BulbOutlined,
  BulbFilled,
  SearchOutlined,
} from '@ant-design/icons-vue'
import { useUserStore } from '../stores/user'
import { useTaskStore } from '../stores/task'
import { useTheme } from '../composables/useTheme'
import { useCountPop } from '../composables/useCountPop'
import { Modal, message } from 'ant-design-vue'
import { getSessions, updateSessionTitle, deleteSession, togglePinSession, exportSession } from '../api/chatSession'
import AvatarFrame from '../components/AvatarFrame.vue'
import LevelTag from '../components/LevelTag.vue'
import ConversationSearchModal from '../components/chat/modals/ConversationSearchModal.vue'
import { sseFetch } from '../utils/sseFetch'
import { formatRelativeTime } from '../utils/format'

const cachedRouteNames = ['AgentDetail', 'KnowledgeDetail', 'McpManage', 'SkillManage']

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const taskStore = useTaskStore()

const userRoleText = computed(() => {
  const role = userStore.user?.role
  if (role === 'admin') return '管理员'
  if (role === 'user') return '普通用户'
  return '未知角色'
})

const sessions = ref([])
const currentSessionId = ref(null)
const sessionListRef = ref(null)
const sessionLoadMoreRef = ref(null)
const sessionPageNum = ref(1)
const sessionHasMore = ref(true)
const sessionLoading = ref(false)
let sessionObserver = null
const renameVisible = ref(false)
const renameValue = ref('')
const renameTarget = ref(null)
const conversationSearchOpen = ref(false)
const userDropdownOpen = ref(false)
const sidebarCollapsed = ref(localStorage.getItem('sidebar-collapsed') === 'true')
let taskSSE = null
let sseRetries = 0
const SSE_BASE_DELAY = 3000

const { isDark, toggleTheme } = useTheme()

const isWorkflowRoute = computed(() => route.path.startsWith('/app/workflow/'))
const isChatRoute = computed(() => route.path.startsWith('/app/chat'))
/** 会话轨仅在「对话」页显示；其他菜单与工作流编辑页均隐藏 */
const sessionRailVisible = computed(() => isChatRoute.value && !isWorkflowRoute.value)

const taskBadgeCount = computed(() => {
  if (taskStore.active <= 0) return 0
  return taskStore.active > 10 ? '10+' : taskStore.active
})

const taskBadgePopClass = useCountPop(() => taskStore.active)
const taskBadgeStyle = { fontSize: '10px', boxShadow: 'none', backgroundColor: '#f5222d' }

const navItems = [
  { path: '/app/chat', label: '对话', icon: markRaw(MessageOutlined) },
  { path: '/app/agents', label: 'Agent', icon: markRaw(RobotOutlined) },
  { path: '/app/knowledge', label: '知识库', icon: markRaw(DatabaseOutlined) },
  { path: '/app/extensions', label: '扩展', icon: markRaw(ToolOutlined) },
  { path: '/app/prompts', label: 'Prompt', icon: markRaw(FileTextOutlined) },
  { path: '/app/eval', label: '评测', icon: markRaw(ExperimentOutlined) },
  { path: '/app/dashboard', label: 'Dashboard', icon: markRaw(DashboardOutlined) },
  { path: '/app/observability', label: '可观测', icon: markRaw(EyeOutlined) },
]

function isActive(path) {
  return route.path.startsWith(path)
}

function toggleSidebar() {
  if (!sessionRailVisible.value) return
  sidebarCollapsed.value = !sidebarCollapsed.value
  localStorage.setItem('sidebar-collapsed', sidebarCollapsed.value)
}

function handleGlobalKeydown(e) {
  if (e.ctrlKey && e.shiftKey && e.code === 'KeyN') {
    e.preventDefault()
    newChat()
    return
  }
  if (e.ctrlKey && e.shiftKey && e.code === 'KeyO') {
    e.preventDefault()
    toggleSidebar()
  }
}

function getPopupContainer() {
  return document.body
}

async function loadSessions(append = false) {
  if (sessionLoading.value) return
  sessionLoading.value = true
  try {
    const res = await getSessions({ pageNum: sessionPageNum.value, pageSize: 10 })
    const records = res.data.records || []
    if (append) {
      sessions.value.push(...records)
    } else {
      sessions.value = records
    }
    sessionHasMore.value = records.length === 10
  } catch (e) {
    // ignore
  } finally {
    sessionLoading.value = false
  }
}

function resetSessions() {
  sessionPageNum.value = 1
  sessionHasMore.value = true
  loadSessions(false)
}

function initSessionObserver() {
  if (sessionObserver) sessionObserver.disconnect()
  sessionObserver = new IntersectionObserver((entries) => {
    if (entries[0].isIntersecting && sessionHasMore.value && !sessionLoading.value) {
      sessionPageNum.value++
      loadSessions(true)
    }
  }, { rootMargin: '50px' })
}

function refreshSessions() {
  resetSessions()
}

function newChat() {
  currentSessionId.value = null
  router.push('/app/chat')
}

function switchSession(session) {
  currentSessionId.value = session.id
  router.push(`/app/chat/${session.id}`)
}

function handleSessionMenu(key, session) {
  if (key === 'pin') {
    handleTogglePin(session)
  } else if (key === 'rename') {
    startRename(session)
  } else if (key === 'export') {
    handleExportSession(session)
  } else if (key === 'delete') {
    handleDeleteSession(session)
  }
}

async function handleExportSession(session) {
  Modal.confirm({
    title: '导出会话',
    content: '选择导出格式',
    okText: 'Markdown',
    cancelText: 'JSON',
    onOk: () => doExportSession(session.id, 'markdown', session.title),
    onCancel: () => doExportSession(session.id, 'json', session.title),
  })
}

async function doExportSession(id, format, title) {
  try {
    const res = await exportSession(id, format)
    const ext = format === 'json' ? 'json' : 'md'
    const blob = new Blob([res], { type: 'application/octet-stream' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${title || 'session'}-${id}.${ext}`
    a.click()
    URL.revokeObjectURL(url)
    message.success('导出成功')
  } catch {
    message.error('导出失败')
  }
}

async function handleTogglePin(session) {
  try {
    await togglePinSession(session.id)
    resetSessions()
  } catch {
    // interceptor已处理错误提示
  }
}

function handleDeleteSession(session) {
  Modal.confirm({
    title: '确定删除对话？',
    content: '删除后，聊天记录将不可恢复。',
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    async onOk() {
      try {
        await deleteSession(session.id)
        sessions.value = sessions.value.filter(s => s.id !== session.id)
        if (currentSessionId.value === session.id) {
          router.push('/app/chat')
        }
      } catch {
        // interceptor已处理错误提示
      }
    },
  })
}

function startRename(session) {
  renameTarget.value = session
  renameValue.value = session.title || ''
  renameVisible.value = true
}

function openConversationSearch() {
  conversationSearchOpen.value = true
}

function handleConversationSearchPick(item) {
  if (!item || !item.sessionId) return
  router.push(`/app/chat/${item.sessionId}`)
}

async function confirmRename() {
  const val = renameValue.value.trim()
  if (!val) return
  if (renameTarget.value) {
    const oldTitle = renameTarget.value.title
    renameTarget.value.title = val
    try {
      await updateSessionTitle(renameTarget.value.id, val)
    } catch {
      renameTarget.value.title = oldTitle
      message.error('重命名失败')
    }
  }
  renameVisible.value = false
}

function handleCommand({ key }) {
  if (key === 'logout') {
    userStore.logout()
    router.push('/login')
  } else if (key === 'tasks') {
    router.push('/app/tasks')
  } else if (key === 'sessions') {
    router.push('/app/sessions')
  } else if (key === 'settings') {
    router.push('/app/settings')
  } else if (key === 'model-providers') {
    router.push('/app/model-providers')
  } else if (key === 'logs') {
    router.push('/app/logs')
  }
}

function connectTaskSSE() {
  if (taskSSE) return
  const token = localStorage.getItem('token') || ''
  if (!token) return

  taskSSE = sseFetch('/api/tasks/stream', {
    token,
    onEvent: (evt) => {
      if (evt.event === 'count') {
        try {
          const counts = JSON.parse(evt.data)
          taskStore.updateCounts(counts)
        } catch {
          taskStore.updateCounts({ active: Number(evt.data) || 0, pending: 0, running: 0 })
        }
      }
    },
    onDone: () => {
      sseRetries = 0
    },
    onError: () => {
      taskSSE = null
      sseRetries++
      const delay = Math.min(SSE_BASE_DELAY * Math.pow(1.5, sseRetries - 1), 30000)
      setTimeout(connectTaskSSE, delay)
    },
    maxRetries: 0,
  })
}

function disconnectTaskSSE() {
  taskSSE?.close?.()
  taskSSE = null
}

onMounted(async () => {
  if (!userStore.user) {
    try {
      await userStore.fetchUser()
    } catch (e) {
      const status = e?.response?.status
      router.push(status === 401 ? '/login' : '/')
      return
    }
  }
  loadSessions()
  initSessionObserver()
  window.addEventListener('session-title-updated', refreshSessions)
  document.addEventListener('keydown', handleGlobalKeydown)
  connectTaskSSE()
})

onUnmounted(() => {
  window.removeEventListener('session-title-updated', refreshSessions)
  document.removeEventListener('keydown', handleGlobalKeydown)
  disconnectTaskSSE()
})

watch(() => route.path, (path) => {
  if (path.startsWith('/app/chat')) {
    const match = path.match(/\/app\/chat\/(\d+)/)
    const newId = match ? match[1] : null
    if (newId && newId !== currentSessionId.value) {
      resetSessions()
    }
    currentSessionId.value = newId
  }
})

watch(sessionLoadMoreRef, (el) => {
  if (sessionObserver && el) {
    sessionObserver.observe(el)
  }
})
</script>

<style scoped>
.layout {
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow: hidden;
  background: var(--color-canvas-soft);
  font-family: var(--font-sans);
}

/* ===== Topbar ===== */
.topbar {
  height: 56px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 0 16px 0 12px;
  background: var(--color-canvas);
  border-bottom: 1px solid var(--color-hairline);
  z-index: 20;
}
.topbar-left,
.topbar-right {
  display: flex;
  align-items: center;
  min-width: 0;
}
.topbar-left {
  flex: 1;
  gap: 8px;
}
.topbar-right {
  gap: 8px;
  flex-shrink: 0;
}

.brand {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  padding: 4px 8px 4px 4px;
  border-radius: 8px;
  transition: background 0.15s;
}
.brand:hover {
  background: var(--color-canvas-soft-2);
}
.brand-logo {
  height: 36px;
  object-fit: contain;
  display: block;
}

.top-nav {
  display: flex;
  align-items: stretch;
  align-self: stretch;
  gap: 0;
  min-width: 0;
  height: 56px;
  margin: 0 0 0 8px;
  overflow-x: auto;
  scrollbar-width: none;
}
.top-nav::-webkit-scrollbar {
  display: none;
}
.top-nav-item {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 100%;
  padding: 0 14px;
  border-radius: 0;
  color: var(--color-mute);
  text-decoration: none;
  font-size: 13px;
  font-weight: 500;
  letter-spacing: -0.01em;
  white-space: nowrap;
  transition: color 0.18s ease;
}
.top-nav-item::after {
  content: '';
  position: absolute;
  left: 14px;
  right: 14px;
  bottom: 0;
  height: 2px;
  background: var(--color-ink);
  border-radius: 2px 2px 0 0;
  transform: scaleX(0);
  transform-origin: center;
  transition: transform 0.2s var(--easing-standard, cubic-bezier(0.2, 0.8, 0.2, 1));
}
.top-nav-icon {
  font-size: 14px;
  opacity: 0.72;
  transition: opacity 0.18s ease;
}
.top-nav-item:hover {
  color: var(--color-ink);
  background: transparent;
}
.top-nav-item:hover .top-nav-icon {
  opacity: 0.9;
}
.top-nav-item.active {
  color: var(--color-ink);
  font-weight: 600;
  background: transparent;
}
.top-nav-item.active .top-nav-icon {
  opacity: 1;
}
.top-nav-item.active::after {
  transform: scaleX(1);
}

.topbar-icon-btn,
.rail-icon-btn {
  width: 36px;
  height: 36px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: var(--color-mute);
  cursor: pointer;
  transition: background 0.15s, color 0.15s, border-color 0.15s;
}
.topbar-icon-btn:hover,
.rail-icon-btn:hover {
  background: var(--color-canvas-soft-2);
  color: var(--color-ink);
}

.user-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 40px;
  padding: 0 10px 0 6px;
  border: 1px solid var(--color-hairline);
  border-radius: 999px;
  background: var(--color-canvas);
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
}
.user-chip:hover {
  border-color: var(--color-hairline-strong);
  background: var(--color-canvas-soft);
}
.user-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--color-link);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 600;
  overflow: hidden;
  flex-shrink: 0;
}
.user-avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.user-name {
  max-width: 96px;
  font-size: 13px;
  font-weight: 500;
  color: var(--color-ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.user-chevron {
  font-size: 10px;
  color: var(--color-mute);
  transition: transform 0.2s ease;
}
.user-chevron.open {
  transform: rotate(180deg);
}
.sidebar-task-badge-inline {
  cursor: pointer;
  flex-shrink: 0;
}
.sidebar-task-badge-inline.lb-count-pop {
  animation: lb-count-pop 0.36s cubic-bezier(0.34, 1.56, 0.64, 1);
}

/* ===== Body ===== */
.layout-body {
  flex: 1;
  min-height: 0;
  display: flex;
  overflow: hidden;
}

/* ===== Session rail ===== */
.session-rail {
  width: 260px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: var(--color-canvas-soft);
  border-right: 1px solid var(--color-hairline);
  overflow: hidden;
  transition: width 0.22s ease;
}
.session-rail.collapsed {
  width: 56px;
}
.session-rail.hidden {
  width: 0 !important;
  min-width: 0;
  border: none;
  pointer-events: none;
}

.rail-header {
  height: 48px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 0 10px 0 14px;
  border-bottom: 1px solid var(--color-hairline);
}
.session-rail.collapsed .rail-header {
  justify-content: center;
  padding: 0;
}
.rail-title {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--color-mute);
}
.rail-header-actions {
  display: inline-flex;
  align-items: center;
  gap: 2px;
}
.session-rail.collapsed .rail-header-actions .rail-icon-btn:not(:last-child) {
  display: none;
}

.btn-new-chat-rail {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin: 10px 10px 8px;
  height: 36px;
  border: 1px solid var(--color-hairline);
  border-radius: 8px;
  background: var(--color-canvas-soft);
  color: var(--color-ink);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
}
.btn-new-chat-rail:hover {
  border-color: var(--color-ink);
  background: var(--color-canvas);
}
.session-rail.collapsed .btn-new-chat-rail {
  margin: 10px 8px;
  padding: 0;
}

.session-section {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 4px 8px 12px;
}
.session-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.session-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  border-radius: 8px;
  border: 1px solid transparent;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
}
.session-item:hover {
  background: var(--color-canvas-soft-2);
}
.session-item.active {
  background: var(--color-canvas-soft-2);
  border-color: var(--color-hairline);
}
.session-item--pinned {
  background: rgba(99, 102, 241, 0.06);
}
.session-item--pinned.active {
  border-color: rgba(99, 102, 241, 0.35);
}
.session-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  color: var(--color-ink);
}
.session-time {
  flex-shrink: 0;
  font-size: 11px;
  color: var(--color-mute);
}
.session-pin-icon {
  flex-shrink: 0;
  font-size: 12px;
  color: #818cf8;
}
.session-more {
  opacity: 0;
  color: var(--color-mute);
  font-size: 14px;
  padding: 2px 4px;
  border-radius: 4px;
  transition: opacity 0.15s;
}
.session-item:hover .session-more {
  opacity: 1;
}
.session-more:hover {
  background: var(--color-canvas-soft-3);
  color: var(--color-ink);
}
.session-empty {
  padding: 24px 12px;
  font-size: 13px;
  color: var(--color-mute);
  text-align: center;
}
.session-loading-more {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px 0;
}
.session-loading-text {
  font-size: 12px;
  color: var(--color-mute);
}
.session-load-more-sentinel {
  height: 1px;
}

.main-content {
  flex: 1;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  background: var(--color-canvas);
}

.menu-item-with-badge {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}
.menu-item-content {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
:deep(.menu-danger) {
  color: var(--color-error) !important;
}
:deep(.menu-danger:hover) {
  background: var(--color-error-soft) !important;
}
:global(.sidebar-user-dropdown .ant-dropdown-menu) {
  min-width: auto;
}
.menu-user-info {
  cursor: default !important;
}
.menu-user-info:hover {
  background: transparent !important;
}
.user-info-display {
  padding: 2px 0;
}
.user-info-name-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 4px;
}
.user-info-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}
.user-info-role {
  flex-shrink: 0;
  padding: 1px 8px;
  font-size: 12px;
  color: var(--color-mute);
  background: var(--color-canvas-soft-2);
  border-radius: 10px;
}
.user-info-meta {
  font-size: 12px;
  color: var(--color-mute);
}

@media (max-width: 960px) {
  .top-nav-item span {
    display: none;
  }
  .top-nav-item {
    padding: 0 10px;
  }
  .user-name {
    display: none;
  }
}
</style>
