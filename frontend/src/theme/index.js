// 主题系统:两轴矩阵——主题(theme,装修风格)× 晨暮(mode,时间光照档)
// theme ∈ 'warm'(暖居,现有配色)| 'guangchen'(光尘,新旗舰);mode ∈ 'dawn'(晨/浅)| 'dusk'(暮/深)
// 应用方式:html.theme-{id} + html.dark(=dusk,驱动 Element Plus 暗色 css-vars)
// 持久化 key 沿用 'ihomy-theme',做旧 {dark, autoMode} → {theme, mode, autoMode} 迁移

export const THEME_STORAGE_KEY = 'ihomy-theme'

export const THEMES = {
  warm: {
    id: 'warm',
    label: { zh: '暖居', en: 'Warm Dwelling' },
    meta: { dawn: '#EDE4D3', dusk: '#0F1A2E' },
  },
  guangchen: {
    id: 'guangchen',
    label: { zh: '光尘', en: 'Light & Dust' },
    meta: { dawn: '#F1E7D6', dusk: '#241A12' },
  },
}

export const THEME_IDS = Object.keys(THEMES)

export const DEFAULT_THEME = { theme: 'warm', mode: 'dawn', autoMode: true }

// 旧格式迁移:{dark, autoMode} → {theme, mode, autoMode}
function migrate(raw) {
  if (raw && (raw.theme || raw.mode)) return raw
  return {
    theme: 'warm',
    mode: raw?.dark ? 'dusk' : 'dawn',
    autoMode: raw?.autoMode ?? true,
  }
}

export function loadTheme() {
  try {
    const raw = localStorage.getItem(THEME_STORAGE_KEY)
    if (!raw) return { ...DEFAULT_THEME }
    const m = migrate(JSON.parse(raw))
    if (!THEMES[m.theme]) m.theme = DEFAULT_THEME.theme
    if (m.mode !== 'dawn' && m.mode !== 'dusk') m.mode = DEFAULT_THEME.mode
    return { ...DEFAULT_THEME, ...m }
  } catch {
    return { ...DEFAULT_THEME }
  }
}

export function applyTheme(state) {
  const t = { ...DEFAULT_THEME, ...state }
  const root = document.documentElement
  root.classList.remove(...THEME_IDS.map((id) => 'theme-' + id))
  root.classList.add('theme-' + t.theme)
  root.classList.toggle('dark', t.mode === 'dusk')
  const meta = document.querySelector('meta[name="theme-color"]')
  if (meta) meta.setAttribute('content', THEMES[t.theme].meta[t.mode])
  localStorage.setItem(THEME_STORAGE_KEY, JSON.stringify(t))
  return t
}

export function initTheme() {
  return applyTheme(loadTheme())
}
