import { describe, it, expect } from 'vitest'
import { addToTime, diffTimes, formatTime, nextCronRuns, parseCron, parseTimeInput, run } from './timeTool'

const local = (year, month, day, hour = 0, minute = 0, second = 0) => new Date(year, month - 1, day, hour, minute, second, 0).getTime()

describe('时间输入解析', () => {
  it('自动识别秒与毫秒', () => {
    expect(parseTimeInput('1700000000')).toMatchObject({ ms: 1700000000000, numeric: true, unit: 's' })
    expect(parseTimeInput('1700000000000')).toMatchObject({ ms: 1700000000000, numeric: true, unit: 'ms' })
    expect(parseTimeInput('1700000000', 'ms')).toMatchObject({ ms: 1700000000, unit: 'ms' })
    expect(parseTimeInput('1700000000000', 's')).toMatchObject({ ms: 1700000000000000, unit: 's' })
  })

  it('解析日期字符串', () => {
    expect(parseTimeInput('2026-10-19T12:00:00Z')).toMatchObject({ ms: Date.UTC(2026, 9, 19, 12), numeric: false })
  })

  it('空值与非法值返回 null', () => {
    expect(parseTimeInput('')).toBeNull()
    expect(parseTimeInput('not a time')).toBeNull()
  })
})

describe('时区格式化', () => {
  it('UTC 与东八区', () => {
    const utc = formatTime(0, 'UTC', 'zh')
    expect(utc.iso).toBe('1970-01-01T00:00:00.000Z')
    expect(utc.zone).toBe('UTC')
    expect(utc.zoned).toContain('1970')

    const shanghai = formatTime(0, 'Asia/Shanghai', 'zh')
    expect(shanghai.offset).toContain('+08:00')
    expect(shanghai.zoned).toContain('1970')
  })

  it('非法时区抛出 RangeError', () => {
    expect(() => formatTime(0, 'Mars/Olympus', 'zh')).toThrow()
  })
})

describe('日期差', () => {
  it('拆分天/时/分/秒', () => {
    const start = Date.UTC(2026, 0, 1, 0, 0, 0)
    const end = start + 86_400_000 + 3_600_000 * 2 + 60_000 * 3 + 4000
    expect(diffTimes(start, end)).toMatchObject({ sign: 1, days: 1, hours: 2, minutes: 3, seconds: 4 })
    expect(diffTimes(end, start).sign).toBe(-1)
  })
})

describe('日期加减', () => {
  it('日/时/分', () => {
    const base = Date.UTC(2026, 0, 1, 12, 0, 0)
    expect(addToTime(base, 1, 'day')).toBe(base + 86_400_000)
    expect(addToTime(base, -2, 'hour')).toBe(base - 7_200_000)
    expect(addToTime(base, 30, 'minute')).toBe(base + 1_800_000)
    expect(addToTime(base, 1, 'week')).toBe(base + 7 * 86_400_000)
  })

  it('月份夹到目标月最后一天', () => {
    expect(addToTime(Date.UTC(2024, 0, 31), 1, 'month')).toBe(Date.UTC(2024, 1, 29))
    expect(addToTime(Date.UTC(2023, 0, 31), 1, 'month')).toBe(Date.UTC(2023, 1, 28))
    expect(addToTime(Date.UTC(2024, 1, 29), 1, 'year')).toBe(Date.UTC(2025, 1, 28))
  })

  it('非法单位与数量抛错', () => {
    expect(() => addToTime(0, 1, 'fortnight')).toThrow('invalidUnit')
    expect(() => addToTime(0, 1.5, 'day')).toThrow('invalidAmount')
  })
})

describe('Cron 解析', () => {
  it('5 段表达式展开', () => {
    const parsed = parseCron('*/15 9-18 * * MON-FRI')
    expect(parsed.ok).toBe(true)
    expect([...parsed.fields.minute]).toEqual([0, 15, 30, 45])
    expect([...parsed.fields.hour].slice(0, 3)).toEqual([9, 10, 11])
    expect([...parsed.fields.dayOfWeek]).toEqual([1, 2, 3, 4, 5])
    expect(parsed.hasSeconds).toBe(false)
  })

  it('6 段表达式带秒', () => {
    const parsed = parseCron('*/30 * * * * *')
    expect(parsed.ok).toBe(true)
    expect(parsed.hasSeconds).toBe(true)
    expect([...parsed.fields.second]).toEqual([0, 30])
  })

  it('字段越界与段数错误', () => {
    expect(parseCron('60 * * * *')).toMatchObject({ ok: false, error: { field: 'minute' } })
    expect(parseCron('* * * *')).toMatchObject({ ok: false, error: { field: 'count' } })
    expect(parseCron('* * * FOO *')).toMatchObject({ ok: false, error: { field: 'month' } })
  })
})

describe('Cron 下次执行时间', () => {
  it('每 15 分钟', () => {
    const from = local(2026, 1, 1, 10, 7, 0)
    const state = nextCronRuns('*/15 * * * *', from, 5)
    expect(state.ok).toBe(true)
    expect(state.runs).toHaveLength(5)
    for (const ms of state.runs) {
      const date = new Date(ms)
      expect(date.getMinutes() % 15).toBe(0)
      expect(date.getSeconds()).toBe(0)
    }
    for (let i = 1; i < state.runs.length; i += 1) {
      expect(state.runs[i] - state.runs[i - 1]).toBe(15 * 60_000)
    }
    expect(new Date(state.runs[0]).getHours()).toBe(10)
    expect(new Date(state.runs[0]).getMinutes()).toBe(15)
  })

  it('工作日早上 9 点', () => {
    const from = local(2026, 1, 2, 10, 0, 0) // 2026-01-02 是周五
    const state = nextCronRuns('0 9 * * 1-5', from, 5)
    expect(state.runs).toHaveLength(5)
    for (const ms of state.runs) {
      const date = new Date(ms)
      expect(date.getHours()).toBe(9)
      expect(date.getMinutes()).toBe(0)
      expect(date.getDay()).toBeGreaterThanOrEqual(1)
      expect(date.getDay()).toBeLessThanOrEqual(5)
    }
  })

  it('每月 1 日', () => {
    const state = nextCronRuns('0 0 1 * *', local(2026, 1, 15), 3)
    expect(state.runs).toHaveLength(3)
    for (const ms of state.runs) expect(new Date(ms).getDate()).toBe(1)
  })

  it('6 段按秒推进', () => {
    const from = local(2026, 1, 1, 10, 0, 1)
    const state = nextCronRuns('*/30 * * * * *', from, 3)
    expect(state.runs).toHaveLength(3)
    expect(new Date(state.runs[0]).getSeconds()).toBe(30)
    expect(state.runs[1] - state.runs[0]).toBe(30_000)
  })

  it('日与周同时受限时按 OR 语义', () => {
    // 每月 1 日或每周一
    const state = nextCronRuns('0 0 1 * 1', local(2026, 1, 2), 6)
    expect(state.runs.length).toBeGreaterThan(0)
    for (const ms of state.runs) {
      const date = new Date(ms)
      expect(date.getDate() === 1 || date.getDay() === 1).toBe(true)
    }
  })
})

describe('run 统一入口', () => {
  it('convert:自动识别秒并按时区展示', () => {
    const result = run('1700000000', { mode: 'convert', timeZone: 'Asia/Shanghai' }, { locale: 'zh' })
    expect(result.ok).toBe(true)
    expect(result.data.iso).toBe('2023-11-14T22:13:20.000Z')
    expect(result.data.zone).toBe('Asia/Shanghai')
    expect(result.data.unit).toBe('s')
  })

  it('diff:两行输入', () => {
    const result = run('2026-01-01T00:00:00Z\n2026-01-02T02:03:04Z', { mode: 'diff', timeZone: 'UTC' }, { locale: 'zh' })
    expect(result.ok).toBe(true)
    expect(result.data).toMatchObject({ days: 1, hours: 2, minutes: 3, seconds: 4, sign: 1 })
  })

  it('add:按日历加一个月', () => {
    const result = run('2024-01-31T00:00:00Z', { mode: 'add', amount: 1, unit: 'month', timeZone: 'UTC' }, { locale: 'zh' })
    expect(result.data.iso).toBe('2024-02-29T00:00:00.000Z')
  })

  it('cron:返回未来执行时间', () => {
    const from = local(2026, 1, 1, 10, 7, 0)
    const result = run('*/15 * * * *', { mode: 'cron', from, count: 3, timeZone: 'UTC' }, { locale: 'zh' })
    expect(result.ok).toBe(true)
    expect(result.data.next).toHaveLength(3)
    expect(result.data.next[0].zoned).toContain(':15')
  })

  it('错误路径本地化', () => {
    expect(run('', { mode: 'convert' }, { locale: 'zh' })).toMatchObject({ ok: false, error: { code: 'empty' } })
    expect(run('x', { mode: 'convert' }, { locale: 'en' }).error.message).toMatch(/recognizable/i)
    expect(run('* * *', { mode: 'cron' }, { locale: 'zh' })).toMatchObject({ ok: false, error: { code: 'invalidCron' } })
    expect(run('0', { mode: 'convert', timeZone: 'Bad/Zone' }, { locale: 'zh' })).toMatchObject({
      ok: false,
      error: { code: 'invalidTimeZone' },
    })
  })
})
