import { ref, computed, watch } from 'vue'
import { theme } from 'ant-design-vue'

const saved = localStorage.getItem('lightbot-theme')
const isDark = ref(saved === 'dark' || (!saved && window.matchMedia('(prefers-color-scheme: dark)').matches))

export function useTheme() {
  const themeConfig = computed(() => ({
    algorithm: isDark.value ? theme.darkAlgorithm : theme.defaultAlgorithm,
    token: {
      colorPrimary: '#171717',
      colorLink: '#0070f3',
      colorLinkHover: '#0761d1',
      borderRadius: 8,
      borderRadiusLG: 12,
      borderRadiusSM: 6,
      fontFamily: 'var(--font-sans)',
      fontSize: 14,
      controlHeight: 32,
      wireframe: false,
      ...(isDark.value
        ? {
            colorBgContainer: '#1a1a1a',
            colorBgElevated: '#222222',
            colorBgLayout: '#111111',
            colorBorder: '#333333',
            colorText: '#e4e4e7',
            colorTextSecondary: '#a1a1aa',
          }
        : {}),
    },
    components: {
      Button: {
        borderRadius: 100,
        fontWeight: 500,
        primaryShadow: 'none',
        defaultShadow: 'none',
      },
      Modal: {
        borderRadiusLG: 12,
      },
      Tabs: {
        // 浅色模式用近黑高亮，深色模式切到浅色保证对比度
        inkBarColor: isDark.value ? '#e4e4e7' : '#171717',
        itemSelectedColor: isDark.value ? '#e4e4e7' : '#171717',
        itemHoverColor: isDark.value ? '#a1a1aa' : '#27272a',
        itemActiveColor: isDark.value ? '#e4e4e7' : '#171717',
        titleFontSize: 14,
      },
      Table: {
        headerBg: 'transparent',
        headerSplitColor: 'transparent',
        // 浅色用黑色 2% 半透，深色用白色 4% 半透
        rowHoverBg: isDark.value ? 'rgba(255,255,255,0.04)' : 'rgba(0,0,0,0.02)',
      },
      Card: {
        borderRadiusLG: 12,
      },
      Input: { borderRadius: 6 },
      InputNumber: { borderRadius: 6 },
      Select: { borderRadius: 6 },
      DatePicker: { borderRadius: 6 },
      Pagination: {
        // 浅色激活态用近黑底，深色用中灰底；文字始终为反色
        itemActiveBg: isDark.value ? '#3f3f46' : '#171717',
        itemActiveColor: isDark.value ? '#e4e4e7' : '#ffffff',
        itemActiveColorDisabled: 'rgba(255,255,255,0.35)',
      },
      Tooltip: {
        borderRadius: 6,
        fontSize: 12,
      },
      Notification: {
        borderRadiusLG: 8,
      },
      Drawer: {
        borderRadiusLG: 12,
      },
    },
  }))

  function toggleTheme() {
    isDark.value = !isDark.value
  }

  watch(isDark, (dark) => {
    document.documentElement.setAttribute('data-theme', dark ? 'dark' : 'light')
    localStorage.setItem('lightbot-theme', dark ? 'dark' : 'light')
  }, { immediate: true })

  return { isDark, themeConfig, toggleTheme }
}
