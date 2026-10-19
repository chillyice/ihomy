import { describe, expect, it } from 'vitest'
import { CATEGORY_IDS, convert, convertAll, formatNumber, run } from './unitTool'

describe('长度 / 面积 / 体积', () => {
  it('公制与英制', () => {
    expect(convert(1, 'length', 'km', 'm')).toBe(1000)
    expect(convert(1, 'length', 'mi', 'km')).toBeCloseTo(1.609344, 6)
    expect(convert(1, 'length', 'ft', 'cm')).toBeCloseTo(30.48, 6)
    expect(convert(1, 'area', 'ha', 'm2')).toBe(10000)
    expect(convert(1, 'volume', 'l', 'ml')).toBe(1000)
    expect(convert(1, 'volume', 'gal', 'l')).toBeCloseTo(3.785412, 5)
  })
})

describe('重量 / 速度 / 压力', () => {
  it('常见换算', () => {
    expect(convert(1, 'weight', 'jin', 'kg')).toBe(0.5)
    expect(convert(1, 'weight', 'lb', 'kg')).toBeCloseTo(0.453592, 6)
    expect(convert(36, 'speed', 'kmh', 'mps')).toBeCloseTo(10, 6)
    expect(convert(1, 'pressure', 'atm', 'kpa')).toBeCloseTo(101.325, 6)
    expect(convert(1, 'pressure', 'bar', 'psi')).toBeCloseTo(14.5038, 3)
  })
})

describe('温度', () => {
  it('偏移公式', () => {
    expect(convert(0, 'temperature', 'c', 'f')).toBe(32)
    expect(convert(100, 'temperature', 'c', 'f')).toBe(212)
    expect(convert(0, 'temperature', 'c', 'k')).toBeCloseTo(273.15, 6)
    expect(convert(32, 'temperature', 'f', 'c')).toBe(0)
    expect(convert(300, 'temperature', 'k', 'c')).toBeCloseTo(26.85, 6)
  })
})

describe('数据量 / 时间', () => {
  it('十进制与二进制', () => {
    expect(convert(1, 'data', 'gb', 'mb')).toBe(1000)
    expect(convert(1, 'data', 'gib', 'mib')).toBe(1024)
    expect(convert(8, 'data', 'bit', 'b')).toBe(1)
    expect(convert(2, 'time', 'h', 'min')).toBe(120)
    expect(convert(1, 'time', 'day', 's')).toBe(86400)
  })
})

describe('格式化与边界', () => {
  it('有效数字与整数', () => {
    expect(formatNumber(1000)).toBe('1000')
    expect(formatNumber(1 / 3)).toBe('0.333333')
    expect(formatNumber(0)).toBe('0')
    expect(formatNumber(1.609344)).toBe('1.60934')
  })

  it('非法数字与未知单位报错', () => {
    expect(() => convert('abc', 'length', 'm', 'km')).toThrow('invalidNumber')
    expect(() => convert(1, 'length', 'm', 'lightyear')).toThrow('unknownUnit')
    expect(() => convert(1, 'nope', 'm', 'km')).toThrow('unknownUnit')
  })

  it('类别清单包含温度', () => {
    expect(CATEGORY_IDS).toContain('temperature')
    expect(CATEGORY_IDS).toContain('data')
    expect(convertAll(1, 'length', 'm')).toHaveLength(9)
  })
})

describe('run', () => {
  it('输出结果与全量对照', () => {
    const result = run('1', { category: 'length', from: 'km', to: 'm' }, { locale: 'zh' })
    expect(result.ok).toBe(true)
    expect(result.output).toBe('1 km = 1000 m')
    expect(result.data.all).toHaveLength(9)
  })

  it('温度全量对照', () => {
    const result = run(0, { category: 'temperature', from: 'c', to: 'f' }, { locale: 'en' })
    expect(result.ok).toBe(true)
    expect(result.data.all.map((item) => item.unit)).toEqual(['c', 'f', 'k'])
  })

  it('缺省参数回退到基准单位', () => {
    const result = run('2', { category: 'weight' })
    expect(result.ok).toBe(true)
    expect(result.output).toBe('2 kg = 2 kg')
  })

  it('非法输入给本地化错误', () => {
    const result = run('abc', {}, { locale: 'en' })
    expect(result.ok).toBe(false)
    expect(result.error.message).toBe('Enter a number')
  })
})
