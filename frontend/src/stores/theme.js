import { defineStore } from 'pinia'
import { loadTheme, applyTheme, THEMES, THEME_IDS } from '@/theme'

// 主题系统唯一数据源:theme(暖居/光尘)× mode(晨/暮)+ autoMode(日出日落自动切晨暮)
export const useThemeStore = defineStore('theme', {
  state: () => loadTheme(),
  getters: {
    themes: () => THEME_IDS.map((id) => ({ id, ...THEMES[id] })),
    isDusk: (s) => s.mode === 'dusk',
  },
  actions: {
    _apply() {
      applyTheme({ theme: this.theme, mode: this.mode, autoMode: this.autoMode })
    },
    setTheme(theme) {
      if (!THEMES[theme]) return
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
