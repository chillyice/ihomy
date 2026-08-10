// 主题系统:明暗模式 + 主题色,偏好存 localStorage('ihomy-theme'),默认跟随系统暗色偏好
// 实现方式:html.dark 类切换暗色(配合 element-plus dark css-vars),CSS 变量控制主题色

export const THEMES = [
  { key: 'ocean', label: '海蓝', primary: '#1F3A5F', accent: '#2E74B5' },
  { key: 'emerald', label: '森林', primary: '#14532D', accent: '#16A34A' },
  { key: 'sunset', label: '日暮', primary: '#7C2D12', accent: '#EA580C' },
  { key: 'violet', label: '暮紫', primary: '#312E81', accent: '#7C3AED' },
  { key: 'rose', label: '樱粉', primary: '#881337', accent: '#E11D48' },
]

export const THEME_STORAGE_KEY = 'ihomy-theme'

export const DEFAULT_THEME = { dark: false, theme: 'ocean' }

export function loadTheme() {
  try {
    const raw = localStorage.getItem(THEME_STORAGE_KEY)
    if (!raw) return DEFAULT_THEME
    return { ...DEFAULT_THEME, ...JSON.parse(raw) }
  } catch {
    return DEFAULT_THEME
  }
}

// 应用主题:切换 html.dark 类 + 设置主/辅色 CSS 变量,并持久化
export function applyTheme(theme) {
  const t = { ...DEFAULT_THEME, ...theme }
  const preset = THEMES.find((x) => x.key === t.theme) || THEMES[0]
  const root = document.documentElement
  root.classList.toggle('dark', !!t.dark)
  root.style.setProperty('--color-primary', preset.primary)
  root.style.setProperty('--color-accent', preset.accent)
  // 浏览器地址栏/状态栏颜色跟随主题
  const meta = document.querySelector('meta[name="theme-color"]')
  if (meta) meta.setAttribute('content', t.dark ? '#14161a' : preset.primary)
  localStorage.setItem(THEME_STORAGE_KEY, JSON.stringify(t))
  return t
}

// 初始化:跟随系统暗色偏好的跟随逻辑在这里完成(dark 未显式设置时取系统值)
export function initTheme() {
  const t = loadTheme()
  if (t.dark === undefined) {
    t.dark = window.matchMedia('(prefers-color-scheme: dark)').matches
  }
  return applyTheme(t)
}