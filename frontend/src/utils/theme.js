import { ref, computed } from 'vue'

// ============================================================
//  主题（深色 / 浅色）统一管理
//  通过 <html class="dark"> 切换，所有配色集中在 styles/index.scss 的 CSS 变量里，
//  组件无需感知主题，图表等 Canvas 内容通过订阅 isDark 重建配色。
// ============================================================

const STORAGE_KEY = 'labwise_theme'

/** 首次进入跟随系统偏好，之后以用户选择为准 */
function resolveInitial() {
  const saved = localStorage.getItem(STORAGE_KEY)
  if (saved === 'dark' || saved === 'light') return saved
  return window.matchMedia?.('(prefers-color-scheme: dark)')?.matches ? 'dark' : 'light'
}

export const theme = ref(resolveInitial())
export const isDark = computed(() => theme.value === 'dark')

function apply(value) {
  const root = document.documentElement
  root.classList.toggle('dark', value === 'dark')
  root.setAttribute('data-theme', value)
  // 供原生控件（滚动条、表单控件）跟随主题
  root.style.colorScheme = value === 'dark' ? 'dark' : 'light'
}

// 模块加载即应用一次，避免首屏白闪
apply(theme.value)

export function toggleTheme() {
  theme.value = theme.value === 'dark' ? 'light' : 'dark'
  localStorage.setItem(STORAGE_KEY, theme.value)
  apply(theme.value)
}

export function useTheme() {
  return { theme, isDark, toggleTheme }
}
