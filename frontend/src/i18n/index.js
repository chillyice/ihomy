// 国际化入口:语言偏好 localStorage('ihomy-lang'),默认跟随浏览器语言。
// 中文是默认语言、随首屏加载;英文按需动态加载(减少首屏体积),切到英文前先补文案再切换。
// 新增功能文案必须同步补 zh-CN.js 与 en.js 两个语言文件。
import { createI18n } from 'vue-i18n'
import zhCN from './zh-CN'

export const SUPPORTED_LOCALES = ['zh-CN', 'en']

const LOCALE_LOADERS = {
  en: () => import('./en'),
}
const loadedLocales = new Set(['zh-CN'])

function detectLocale() {
  const saved = typeof localStorage !== 'undefined' ? localStorage.getItem('ihomy-lang') : null
  if (saved && SUPPORTED_LOCALES.includes(saved)) return saved
  const nav = (typeof navigator !== 'undefined' && navigator.language ? navigator.language : 'zh-CN').toLowerCase()
  return nav.startsWith('zh') ? 'zh-CN' : 'en'
}

const i18n = createI18n({
  legacy: false,
  globalInjection: true,
  locale: detectLocale(),
  fallbackLocale: 'zh-CN',
  messages: { 'zh-CN': zhCN },
})

/** 确保该语言的文案已就绪(中文恒可用;英文首次调用时拉取对应 chunk)。 */
export async function ensureLocale(locale) {
  if (!SUPPORTED_LOCALES.includes(locale) || loadedLocales.has(locale)) return
  const loader = LOCALE_LOADERS[locale]
  if (!loader) return
  const mod = await loader()
  i18n.global.setLocaleMessage(locale, mod.default)
  loadedLocales.add(locale)
}

export async function applyLocale(locale) {
  localStorage.setItem('ihomy-lang', locale)
  try {
    await ensureLocale(locale)
  } finally {
    // 文案加载失败也切过去:fallbackLocale(zh-CN)仍能显示中文,不至于卡在旧语言
    i18n.global.locale.value = locale
    document.documentElement.lang = locale
  }
}

if (typeof document !== 'undefined') document.documentElement.lang = i18n.global.locale.value

export default i18n
