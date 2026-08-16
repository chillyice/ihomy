// 主题系统:仅浅色/深色两种模式,偏好存 localStorage('ihomy-theme'),默认跟随太阳位置自动切换
// 实现方式:html.dark 类切换暗色(配合 element-plus dark css-vars)

export const THEME_STORAGE_KEY = 'ihomy-theme'

export const DEFAULT_THEME = { dark: false, autoMode: true }

export function loadTheme() {
  try {
    const raw = localStorage.getItem(THEME_STORAGE_KEY)
    if (!raw) return DEFAULT_THEME
    return { ...DEFAULT_THEME, ...JSON.parse(raw) }
  } catch {
    return DEFAULT_THEME
  }
}

// 应用主题:切换 html.dark 类,并持久化
export function applyTheme(theme) {
  const t = { ...DEFAULT_THEME, ...theme }
  const root = document.documentElement
  root.classList.toggle('dark', !!t.dark)
  // 浏览器地址栏/状态栏颜色跟随主题
  const meta = document.querySelector('meta[name="theme-color"]')
  if (meta) meta.setAttribute('content', t.dark ? '#0F1A2E' : '#EDE4D3')
  localStorage.setItem(THEME_STORAGE_KEY, JSON.stringify(t))
  return t
}

// 初始化:跟随系统暗色偏好的跟随逻辑在这里完成(dark 未显式设置时取系统值)
export function initTheme() {
  const t = loadTheme()
  if (t.autoMode && t.dark === undefined) {
    t.dark = window.matchMedia('(prefers-color-scheme: dark)').matches
  }
  return applyTheme(t)
}

// 自动模式:根据太阳位置(夜间)切换明暗,仅当 autoMode=true 时生效
export function applyAutoTheme(isNight) {
  const t = loadTheme()
  if (!t.autoMode) return null
  const newDark = !!isNight
  if (t.dark === newDark) return t
  return applyTheme({ ...t, dark: newDark })
}
