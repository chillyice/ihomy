import { describe, expect, it } from 'vitest'
import { MAX_RANGE_DAYS, POINT_BUCKET_DAYS, bucketFor, clampRange, dateGrid, isoDay, presetRange, shiftDay, spanDays } from './healthRange'

// 时间范围守门:范围算错会出现「历史数据看不到」或「请求超上限被后端截断」,所以纯函数全部钉住。
const TODAY = '2026-11-20'

describe('healthRange 预设与网格', () => {
  it('今天 = 含今天共 1 天;近 7 天含今天往前推 6 天', () => {
    expect(presetRange(1, TODAY)).toEqual({ from: TODAY, to: TODAY })
    expect(presetRange(7, TODAY)).toEqual({ from: '2026-11-14', to: TODAY })
    expect(presetRange(365, TODAY)).toEqual({ from: '2025-11-21', to: TODAY })
  })

  it('spanDays 含首尾,颠倒也给正数', () => {
    expect(spanDays('2026-11-14', TODAY)).toBe(7)
    expect(spanDays(TODAY, TODAY)).toBe(1)
    expect(spanDays(TODAY, '2026-11-14')).toBe(7)
  })

  it('dateGrid 按天补齐,首尾都在', () => {
    const grid = dateGrid('2026-11-14', TODAY)
    expect(grid).toHaveLength(7)
    expect(grid[0]).toBe('2026-11-14')
    expect(grid[6]).toBe(TODAY)
    expect(dateGrid(TODAY, '2026-11-14')).toEqual([])
  })

  it('isoDay / shiftDay 不受夏令时影响(用正午做基准)', () => {
    expect(isoDay('2026-11-20T23:30:00')).toBe('2026-11-20')
    expect(shiftDay(1, TODAY)).toBe('2026-11-21')
    expect(shiftDay(-6, TODAY)).toBe('2026-11-14')
  })

  // 页面初始化时 makeRange 会先于任何日期参数算默认范围:空值绝不能落到 1900 年,
  // 否则默认范围变成 1899-12-03~1900-01-01,整个趋势图都是空的(踩过)
  it('不传 today 时以真实今天收尾,空日期回落今天而不是 1900', () => {
    const real = isoDay(new Date())
    const range = presetRange(7)
    expect(range.to).toBe(real)
    expect(spanDays(range.from, range.to)).toBe(7)
    expect(isoDay('')).toBe(real)
    expect(isoDay(undefined)).toBe(real)
    expect(shiftDay(0, '')).toBe(real)
  })
})

describe('healthRange 自定义区间收口', () => {
  it('颠倒的起止自动交换(静默修正,不提示「已收口」)', () => {
    expect(clampRange(TODAY, '2026-11-14', TODAY)).toEqual({ from: '2026-11-14', to: TODAY, clamped: false })
  })

  it('未来日期收口到今天', () => {
    const out = clampRange('2026-11-01', '2026-12-31', TODAY)
    expect(out).toEqual({ from: '2026-11-01', to: TODAY, clamped: true })
  })

  it('超过 366 天从结束日往前截', () => {
    const out = clampRange('2024-01-01', TODAY, TODAY)
    expect(out.clamped).toBe(true)
    expect(spanDays(out.from, out.to)).toBe(MAX_RANGE_DAYS)
    expect(out.to).toBe(TODAY)
  })

  it('缺一端用另一端兜底,都缺回落近 14 天', () => {
    expect(clampRange('2026-11-01', '', TODAY)).toEqual({ from: '2026-11-01', to: '2026-11-01', clamped: false })
    expect(clampRange('', TODAY, TODAY)).toEqual({ from: TODAY, to: TODAY, clamped: false })
    expect(clampRange('', '', TODAY)).toEqual({ from: '2026-11-07', to: TODAY, clamped: false })
  })
})

describe('healthRange 分桶', () => {
  it('逐点指标跨度 ≤2 天保留逐点,>2 天转日聚合', () => {
    expect(bucketFor('POINT', TODAY, TODAY)).toBe('POINT')
    expect(bucketFor('POINT', '2026-11-19', TODAY)).toBe('POINT')
    expect(bucketFor('POINT', '2026-11-18', TODAY)).toBe('DAY')
    expect(bucketFor('POINT', '2026-11-17', TODAY)).toBe('DAY')
    expect(POINT_BUCKET_DAYS).toBe(2)
  })

  it('按天/按周指标本来就是一天一行,恒 DAY', () => {
    expect(bucketFor('DAY', TODAY, TODAY)).toBe('DAY')
    expect(bucketFor('SESSION', TODAY, TODAY)).toBe('DAY')
  })
})
