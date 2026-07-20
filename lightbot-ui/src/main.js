import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { message } from 'ant-design-vue'
import 'ant-design-vue/dist/reset.css'
import './styles/code-block-scrollbar.css'
import './styles/admin-page.css'
import './styles/provider-card.css'
import './styles/ui-utils.css'
import './styles/modal-scroll.css'

import App from './App.vue'
import router from './router'
import { captureException, installGlobalErrorHandlers } from './utils/errorReport'

// 限制全局最多显示3条消息提示，防止堆叠
message.config({ maxCount: 3 })

const app = createApp(App)

// 全局 Vue 错误兜底：组件未捕获异常由这里捕获，避免整页白屏
app.config.errorHandler = (err, instance, info) => {
  captureException(err, {
    source: 'vue.errorHandler',
    info,
    componentTag: instance?.$options?.__name || instance?.$options?.name,
    route: router.currentRoute.value?.fullPath,
  })
  // 用户可见的轻提示（不打断流程，详细信息走 captureException 落盘）
  if (import.meta.env.PROD) {
    message.error('页面发生异常，请重试或刷新')
  }
}

// 全局兜底：捕获 Vue 之外的异常（setTimeout / Promise reject / 资源加载失败）
installGlobalErrorHandlers()

app.use(createPinia())
app.use(router)
// Ant Design Vue 组件通过 unplugin-vue-components 自动按需引入
app.mount('#app')
