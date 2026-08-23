// 国际化入口:语言偏好 localStorage('ihomy-lang'),默认跟随浏览器语言。
// 新增功能文案必须同步补 zh-CN.js 与 en.js 两个语言文件。
import { createI18n } from 'vue-i18n'
import zhCN from './zh-CN'
import en from './en'

export const SUPPORTED_LOCALES = ['zh-CN', 'en']

function detectLocale() {
  const saved = localStorage.getItem('ihomy-lang')
  if (saved && SUPPORTED_LOCALES.includes(saved)) return saved
  const nav = (navigator.language || 'zh-CN').toLowerCase()
  return nav.startsWith('zh') ? 'zh-CN' : 'en'
}

export function applyLocale(locale) {
  localStorage.setItem('ihomy-lang', locale)
  i18n.global.locale.value = locale
  document.documentElement.lang = locale
}

const i18n = createI18n({
  legacy: false,
  globalInjection: true,
  locale: detectLocale(),
  fallbackLocale: 'zh-CN',
  messages: { 'zh-CN': zhCN, en },
})

document.documentElement.lang = i18n.global.locale.value

export default i18n