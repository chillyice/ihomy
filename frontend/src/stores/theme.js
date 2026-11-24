import { defineStore } from 'pinia'
import { loadTheme, applyTheme, windowMarkForMode, THEMES, SELECTABLE_THEME_IDS } from '@/theme'

// 主题系统唯一数据源:theme(暖居/光尘)× mode(晨/暮)+ autoMode(日出日落自动切晨暮)

// 独立产品页(壁纸)有自己的主题偏好:挂载时关掉这里,主题变更只落到该页自己的偏好键,
// 不覆盖站点的 ihomy-theme。默认开启,页面卸载后恢复。
let persistPref = true
export function setThemePersist(enabled) {
  persistPref = enabled !== false
}

export const useThemeStore = defineStore('theme', {
  state: () => loadTheme(),
  getters: {
    themes: () => SELECTABLE_THEME_IDS.map((id) => ({ id, ...THEMES[id] })),
    isDusk: (s) => s.mode === 'dusk',
    windowMark: (s) => windowMarkForMode(s.mode),
  },
  actions: {
    _apply(opts) {
      applyTheme({ theme: this.theme, mode: this.mode, autoMode: this.autoMode }, { persist: persistPref, ...opts })
    },
    // 一次落一整份偏好(独立产品页按自己的偏好初始化时用:依次 setTheme/setMode 会触发多次扫光)
    setPreference(pref, opts) {
      if (pref && SELECTABLE_THEME_IDS.includes(pref.theme)) this.theme = pref.theme
      if (pref && (pref.mode === 'dawn' || pref.mode === 'dusk')) this.mode = pref.mode
      if (pref && typeof pref.autoMode === 'boolean') this.autoMode = pref.autoMode
      this._apply(opts)
    },
    setTheme(theme) {
      if (!SELECTABLE_THEME_IDS.includes(theme)) return
      this.theme = theme
      this._apply()
    },
    setMode(mode) {
      if (mode !== 'dawn' && mode !== 'dusk') return
      this.mode = mode
      this.autoMode = false
      this._apply()
    },
    toggleMode() {
      this.setMode(this.isDusk ? 'dawn' : 'dusk')
    },
    setAutoMode(autoMode) {
      this.autoMode = autoMode
      this._apply()
    },
    // 日出日落自动切晨暮:仅 autoMode 时生效,只切 mode 不切 theme
    applyAuto(isNight) {
      if (!this.autoMode) return
      const mode = isNight ? 'dusk' : 'dawn'
      if (this.mode === mode) return
      this.mode = mode
      this._apply()
    },
  },
})
