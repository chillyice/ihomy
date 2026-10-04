import { describe, it, expect } from 'vitest'
import { createI18n } from 'vue-i18n'
import zhCN from './zh-CN'
import en from './en'

// 中英文案键结构必须对齐(AGENTS:新增文案须同步两份语言文件)
const walk = (o, prefix, out = {}) => {
  if (Array.isArray(o)) out[prefix] = 'len:' + o.length
  else if (o && typeof o === 'object') Object.keys(o).forEach((k) => walk(o[k], prefix ? `${prefix}.${k}` : k, out))
  else out[prefix] = 'str'
  return out
}

describe('i18n 中英键对齐', () => {
  it('两份语言文件的键与数组长度一致', () => {
    const a = walk(zhCN, '')
    const b = walk(en, '')
    expect(Object.keys(a).filter((k) => !(k in b))).toEqual([])
    expect(Object.keys(b).filter((k) => !(k in a))).toEqual([])
    expect(Object.keys(a).filter((k) => a[k] !== b[k])).toEqual([])
  })
})

// 帮助页用 $tm 渲染文案数组,确认数组拿得到、且两种语言都是文案而不是结构
describe('help 文案数组可渲染', () => {
  const i18n = createI18n({ legacy: false, globalInjection: true, locale: 'zh-CN', messages: { 'zh-CN': zhCN, en } })

  it('roles/start/visible/trouble 四组列表两种语言都是字符串数组', () => {
    for (const group of ['roles', 'start', 'visible', 'trouble']) {
      for (const locale of ['zh-CN', 'en']) {
        i18n.global.locale.value = locale
        const items = i18n.global.tm(`help.${group}.items`)
        expect(Array.isArray(items), `${locale} help.${group}.items`).toBe(true)
        expect(items.length).toBeGreaterThan(0)
        items.forEach((s) => expect(typeof s).toBe('string'))
        expect(items.every((s) => s.trim().length > 0)).toBe(true)
      }
    }
  })
})
