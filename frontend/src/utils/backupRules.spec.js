import { describe, expect, it } from 'vitest'
import { dailyCron, durationText, formatBytes, parseDailyCron, parseWeeklyCron, retentionPlan, weeklyCron } from './backupRules'

describe('备份策略时间换算', () => {
  it('每天 cron 往返', () => {
    expect(dailyCron(2, 30)).toBe('0 30 2 * * *')
    expect(parseDailyCron('0 30 2 * * *')).toEqual({ hour: 2, minute: 30, text: '02:30' })
    expect(parseDailyCron('0 5 23 * * *')).toEqual({ hour: 23, minute: 5, text: '23:05' })
  })

  it('每周 cron 往返', () => {
    expect(weeklyCron(0, 4, 30)).toBe('0 30 4 * * 0')
    expect(parseWeeklyCron('0 30 4 * * 0')).toEqual({ dow: 0, hour: 4, minute: 30, text: '04:30' })
  })

  it('非标准 cron 不强行解析', () => {
    expect(parseDailyCron('0 0 2,14 * * *')).toBeNull()
    expect(parseWeeklyCron('0 0 4 * * 1-5')).toBeNull()
    expect(parseDailyCron('0 99 2 * * *')).toBeNull()
  })
})

describe('体积与耗时展示', () => {
  it('体积单位', () => {
    expect(formatBytes(0)).toBe('0 B')
    expect(formatBytes(512)).toBe('512 B')
    expect(formatBytes(2048)).toBe('2.0 KB')
    expect(formatBytes(5 * 1024 * 1024)).toBe('5.0 MB')
    expect(formatBytes(2 * 1024 * 1024 * 1024)).toBe('2.00 GB')
  })

  it('耗时:按量级选单位,单位文案交给 i18n', () => {
    const fakeT = (key, params) => `${key}|${Object.values(params).join(',')}`
    expect(durationText(0, fakeT)).toBe('—')
    expect(durationText(1500, fakeT)).toBe('ops.backup.durSecond|2')
    expect(durationText(83000, fakeT)).toBe('ops.backup.durMinute|1,23')
    expect(durationText(7500000, fakeT)).toBe('ops.backup.durHour|2,5')
  })
})

describe('保留策略摘要', () => {
  it('缺字段返回 0,不抛错', () => {
    expect(retentionPlan({})).toEqual({
      basic: { daily: 0, weekly: 0, monthly: 0 },
      content: { daily: 0, weekly: 0, monthly: 0 },
      full: { weekly: 0, monthly: 0 },
      family: { days: 0, keep: 0 },
    })
  })
})
