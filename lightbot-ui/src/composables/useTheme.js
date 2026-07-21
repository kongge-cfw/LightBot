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
        inkBarColor: '#171717',
        itemSelectedColor: '#171717',
        itemHoverColor: '#27272a',
        itemActiveColor: '#171717',
        titleFontSize: 14,
      },
      Table: {
        headerBg: 'transparent',
        headerSplitColor: 'transparent',
        rowHoverBg: 'rgba(0,0,0,0.02)',
      },
      Card: {
        borderRadiusLG: 12,
      },
      Input: { borderRadius: 6 },
      InputNumber: { borderRadius: 6 },
      Select: { borderRadius: 6 },
      DatePicker: { borderRadius: 6 },
      Pagination: {
        itemActiveBg: '#171717',
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
