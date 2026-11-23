import { describe, expect, it } from 'vitest'
import zhCN from '@/i18n/zh-CN'
import en from '@/i18n/en'
import { NAV_PATHS } from '@/utils/navModules'
import { MODULE_DEFAULT_WIDGET, WIDGETS, WIDGET_MODULE, siblingsOf, widgetById } from './registry'

// 注册表是「加首页组件/加样式」的唯一入口,这几条约束保证新条目不会悄悄破坏布局或文案
const ALLOWED_SPANS = [1, 2, 4, 6, 8, 12]
const ALLOWED_ROWS = [1, 2, 3, 4, 6]
// weather 是 navModules 里的虚拟入口,不在 NAV_PATHS;daily 没有独立模块
const VIRTUAL_MODULES = ['weather']

const get = (obj, key) => key.split('.').reduce((o, part) => (o == null ? o : o[part]), obj)

describe('暖居首页组件注册表', () => {
  it('组件 id 唯一,且默认样式的 id 不带冒号', () => {
    const ids = WIDGETS.map((w) => w.id)
    expect(new Set(ids).size).toBe(ids.length)
    for (const w of WIDGETS) {
      if (!MODULE_DEFAULT_WIDGET[w.module]) continue
      const isDefault = MODULE_DEFAULT_WIDGET[w.module] === w.id
      expect(w.id.includes(':')).toBe(!isDefault)
    }
  })

  it('默认尺寸落在可吸附的列宽/行高档位里', () => {
    for (const w of WIDGETS) {
      expect(ALLOWED_SPANS, `${w.id} span`).toContain(w.span)
      expect(ALLOWED_ROWS, `${w.id} row`).toContain(w.row)
    }
  })

  it('module 要么是真实导航模块,要么是虚拟/无模块', () => {
    for (const w of WIDGETS) {
      if (w.module == null || VIRTUAL_MODULES.includes(w.module)) continue
      expect(Object.keys(NAV_PATHS), `${w.id} module`).toContain(w.module)
    }
  })

  it('同一功能的多种样式共享 module,且都有默认样式兜底', () => {
    for (const w of WIDGETS) {
      if (!w.module) continue
      const def = widgetById(MODULE_DEFAULT_WIDGET[w.module])
      expect(def, `${w.id} 缺少默认样式`).toBeTruthy()
      expect(def.module).toBe(w.module)
      expect(WIDGET_MODULE[w.id]).toBe(w.module)
    }
  })

  it('多样式功能每条都带样式名,单样式功能不需要', () => {
    for (const w of WIDGETS) {
      const multi = w.module ? WIDGETS.filter((x) => x.module === w.module).length > 1 : false
      if (multi) expect(w.styleKey, `${w.id} 缺 styleKey`).toBeTruthy()
    }
  })

  it('带 component 的条目必须有 props 映射(统一渲染出口靠它传数据)', () => {
    for (const w of WIDGETS.filter((x) => x.component)) {
      expect(typeof w.props, `${w.id} 缺 props`).toBe('function')
    }
  })

  it('文案键在中文与英文里都存在', () => {
    const missing = []
    for (const w of WIDGETS) {
      for (const key of [w.labelKey, w.styleKey].filter(Boolean)) {
        if (get(zhCN, key) === undefined) missing.push(`zh-CN 缺 ${key}`)
        if (get(en, key) === undefined) missing.push(`en 缺 ${key}`)
      }
    }
    expect(missing).toEqual([])
  })

  it('siblingsOf 只返回同功能的其它样式', () => {
    expect(siblingsOf('photos').map((w) => w.id)).toEqual(['photos:carousel'])
    expect(siblingsOf('photos:carousel').map((w) => w.id)).toEqual(['photos'])
    expect(siblingsOf('daily')).toEqual([])
    expect(siblingsOf('nope')).toEqual([])
  })
})
