import { describe, it, expect } from 'vitest'
import { buildMonthGrid, monthRange, shiftMonth, weekdayKey, todayString, isDateString, addDays, monthKey } from './scheduleCalendar'

describe('buildMonthGrid 网格边界', () => {
  it('恒为 6 行 × 7 列', () => {
    for (let m = 1; m <= 12; m++) expect(buildMonthGrid(2026, m)).toHaveLength(42)
  })

  it('2026 年 2 月(28 天、月首周日):6 格前补 + 8 格后补', () => {
    const grid = buildMonthGrid(2026, 2)
    expect(grid[0].date).toBe('2026-01-26')
    expect(grid[5].date).toBe('2026-01-31')
    expect(grid[6].date).toBe('2026-02-01')
    expect(grid[6].inMonth).toBe(true)
    expect(grid[33].date).toBe('2026-02-28')
    expect(grid[34].date).toBe('2026-03-01')
    expect(grid[41].date).toBe('2026-03-08')
    expect(grid.filter((c) => c.inMonth)).toHaveLength(28)
  })

  it('2024 年 2 月(闰年 29 天)', () => {
    const grid = buildMonthGrid(2024, 2)
    expect(grid.filter((c) => c.inMonth)).toHaveLength(29)
    expect(grid.find((c) => c.date === '2024-02-29').inMonth).toBe(true)
    expect(grid[0].date).toBe('2024-01-29')
  })

  it('6 行月份:2026 年 8 月(周六起,31 天)也铺满 42 格', () => {
    const grid = buildMonthGrid(2026, 8)
    expect(grid).toHaveLength(42)
    // 8 月 1 日为周六 → 周一开头时前面补 5 格(7-27..7-31)
    expect(grid[0].date).toBe('2026-07-27')
    expect(grid.filter((c) => c.inMonth)).toHaveLength(31)
    expect(grid[41].inMonth).toBe(false)
  })

  it('weekStart=0 时以周日开头', () => {
    const grid = buildMonthGrid(2026, 2, 0)
    expect(grid[0].date).toBe('2026-02-01')
    expect(grid[27].date).toBe('2026-02-28')
    expect(grid[28].date).toBe('2026-03-01')
  })

  it('跨年边界:12 月与 1 月的补格落在相邻年份', () => {
    const dec = buildMonthGrid(2026, 12)
    expect(dec[0].date.startsWith('2026-11')).toBe(true)
    const jan = buildMonthGrid(2027, 1)
    expect(jan[0].date.startsWith('2026-12')).toBe(true)
    expect(jan[41].date.startsWith('2027-02')).toBe(true)
  })
})

describe('monthRange / shiftMonth / monthKey', () => {
  it('区间取首末格日期', () => {
    expect(monthRange(2026, 2)).toEqual({ from: '2026-01-26', to: '2026-03-08' })
  })

  it('翻月跨年', () => {
    expect(shiftMonth(2026, 12, 1)).toEqual({ year: 2027, month: 1 })
    expect(shiftMonth(2026, 1, -1)).toEqual({ year: 2025, month: 12 })
    expect(shiftMonth(2026, 6, 0)).toEqual({ year: 2026, month: 6 })
  })

  it('月份串补零', () => {
    expect(monthKey(2026, 2)).toBe('2026-02')
    expect(monthKey(2026, 12)).toBe('2026-12')
  })
})

describe('weekdayKey / todayString / isDateString / addDays', () => {
  it('周几按周一开头', () => {
    expect(weekdayKey('2026-10-30')).toBe('fri')
    expect(weekdayKey('2026-11-01')).toBe('sun')
    expect(weekdayKey('2026-11-02')).toBe('mon')
    expect(weekdayKey('')).toBe('')
  })

  it('今天用本地日期并补零', () => {
    expect(todayString(new Date(2026, 0, 5))).toBe('2026-01-05')
  })

  it('日期串校验挡住非法值', () => {
    expect(isDateString('2026-10-30')).toBe(true)
    expect(isDateString('2026-2-3')).toBe(false)
    expect(isDateString('2026-02-30')).toBe(false)
    expect(isDateString('abc')).toBe(false)
    expect(isDateString(null)).toBe(false)
  })

  it('加减天数跨月跨年', () => {
    expect(addDays('2026-10-31', 1)).toBe('2026-11-01')
    expect(addDays('2026-01-01', -1)).toBe('2025-12-31')
    expect(addDays('2024-02-28', 1)).toBe('2024-02-29')
  })
})
