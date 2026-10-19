import { describe, expect, it } from 'vitest'
import {
  contrastRatio,
  cssVariables,
  harmonize,
  hslToRgb,
  mix,
  parseColor,
  readableText,
  rgbToHex,
  rgbToHsl,
  rgbToHsv,
  rgbToLab,
  run,
  wcagReport,
} from './colorTool'

const red = { r: 255, g: 0, b: 0 }

describe('颜色解析', () => {
  it('HEX 三种长度与透明度', () => {
    expect(parseColor('#fff')).toEqual({ r: 255, g: 255, b: 255, a: 1 })
    expect(parseColor('ff0000')).toEqual({ r: 255, g: 0, b: 0, a: 1 })
    expect(parseColor('#00000080')).toEqual({ r: 0, g: 0, b: 0, a: 0.5 })
    expect(parseColor('#0f08')).toMatchObject({ r: 0, g: 255, b: 0, a: 0.53 })
  })

  it('rgb() / hsl() 与百分号', () => {
    expect(parseColor('rgb(255, 128, 0)')).toEqual({ r: 255, g: 128, b: 0, a: 1 })
    expect(parseColor('rgba(0 0 0 / 0.25)')).toEqual({ r: 0, g: 0, b: 0, a: 0.25 })
    expect(parseColor('hsl(120, 100%, 50%)')).toEqual({ r: 0, g: 255, b: 0, a: 1 })
    expect(parseColor('hsl(0 100% 50%)')).toEqual({ ...red, a: 1 })
  })

  it('非法输入返回 null', () => {
    expect(parseColor('nope')).toBe(null)
    expect(parseColor('#12345')).toBe(null)
    expect(parseColor('')).toBe(null)
  })
})

describe('格式转换', () => {
  it('rgb ↔ hex', () => {
    expect(rgbToHex(red)).toBe('#ff0000')
    expect(rgbToHex({ r: 0, g: 0, b: 0, a: 0.5 })).toBe('#00000080')
  })

  it('rgb ↔ hsl', () => {
    expect(rgbToHsl(red)).toEqual({ h: 0, s: 100, l: 50 })
    expect(rgbToHsl({ r: 0, g: 255, b: 0 })).toEqual({ h: 120, s: 100, l: 50 })
    expect(hslToRgb({ h: 240, s: 100, l: 50 })).toEqual({ r: 0, g: 0, b: 255 })
    expect(hslToRgb({ h: -120, s: 100, l: 50 })).toEqual({ r: 0, g: 0, b: 255 })
  })

  it('HSV', () => {
    expect(rgbToHsv(red)).toEqual({ h: 0, s: 100, v: 100 })
    expect(rgbToHsv({ r: 128, g: 128, b: 128 })).toEqual({ h: 0, s: 0, v: 50.2 })
  })

  it('LAB 已知值', () => {
    const white = rgbToLab({ r: 255, g: 255, b: 255 })
    expect(white.l).toBe(100)
    expect(white.a).toBeCloseTo(0, 1)
    expect(white.b).toBeCloseTo(0, 1)
    expect(rgbToLab({ r: 0, g: 0, b: 0 })).toEqual({ l: 0, a: 0, b: 0 })
    const lab = rgbToLab(red)
    expect(lab.l).toBeCloseTo(53.24, 1)
    expect(lab.a).toBeCloseTo(80.09, 1)
    expect(lab.b).toBeCloseTo(67.2, 1)
  })
})

describe('对比度与可读性', () => {
  it('黑白对比度为 21', () => {
    expect(contrastRatio({ r: 0, g: 0, b: 0 }, { r: 255, g: 255, b: 255 })).toBe(21)
  })

  it('WCAG 分级', () => {
    expect(wcagReport(21)).toEqual({ aa: true, aaa: true, aaLarge: true, aaaLarge: true })
    expect(wcagReport(4.54)).toEqual({ aa: true, aaa: false, aaLarge: true, aaaLarge: true })
    expect(wcagReport(2)).toEqual({ aa: false, aaa: false, aaLarge: false, aaaLarge: false })
  })

  it('文字色自动取黑或白', () => {
    expect(readableText({ r: 255, g: 255, b: 255 })).toBe('#000000')
    expect(readableText({ r: 0, g: 0, b: 0 })).toBe('#ffffff')
  })
})

describe('配色与混色', () => {
  it('红色互补为青色', () => {
    const groups = harmonize(red)
    expect(groups.find((group) => group.kind === 'complementary').colors).toEqual(['#00ffff'])
    expect(groups.find((group) => group.kind === 'triadic').colors).toEqual(['#00ff00', '#0000ff'])
  })

  it('混色按权重', () => {
    expect(rgbToHex(mix(red, { r: 0, g: 0, b: 255 }, 0.5))).toBe('#800080')
    expect(rgbToHex(mix(red, { r: 0, g: 0, b: 255 }, 0))).toBe('#ff0000')
  })

  it('CSS 变量片段', () => {
    expect(cssVariables(red)).toContain('--color: #ff0000;')
    expect(cssVariables(red)).toContain('--color-hsl: 0 100% 50%;')
  })
})

describe('run', () => {
  it('输出各格式与对比度', () => {
    const result = run('#ff0000', {}, { locale: 'zh' })
    expect(result.ok).toBe(true)
    expect(result.output).toContain('hsl(0, 100%, 50%)')
    expect(result.stats.contrastWhite).toBeCloseTo(4, 1)
    expect(result.data.harmonize).toHaveLength(5)
  })

  it('非法颜色给可读错误', () => {
    const result = run('nope', {}, { locale: 'en' })
    expect(result.ok).toBe(false)
    expect(result.error.code).toBe('invalidColor')
  })
})
