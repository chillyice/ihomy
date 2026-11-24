import { describe, expect, it } from 'vitest'
import { durationText, formatBytes, moduleText, retentionText, scheduleDays, scheduleText, timeText } from './backupRules'

const fakeT = (key, params = {}) => `${key}|${Object.values(params).join(',')}`

describe('备份策略展示', () => {
  it('按周期拼装摘要', () => {
    expect(scheduleText({ scheduleType: 'DAILY', scheduleTime: '03:30' }, fakeT)).toBe('ops.backup.everyDayAt|03:30')
    expect(scheduleText({ scheduleType: 'WEEKLY', scheduleTime: '04:30', scheduleDay: 1 }, fakeT)).toBe(
      'ops.backup.everyWeekAt|ops.backup.weekday.1|,04:30',
    )
    expect(scheduleText({ scheduleType: 'MONTHLY', scheduleTime: '02:00', scheduleDay: 28 }, fakeT)).toBe(
      'ops.backup.everyMonthAt|28,02:00',
    )
  })

  it('缺字段回退到每天/周日/1 号', () => {
    expect(scheduleText({}, fakeT)).toBe('ops.backup.everyDayAt|03:30')
    expect(scheduleText({ scheduleType: 'WEEKLY' }, fakeT)).toBe('ops.backup.everyWeekAt|ops.backup.weekday.7|,03:30')
    expect(scheduleText({ scheduleType: 'MONTHLY' }, fakeT)).toBe('ops.backup.everyMonthAt|1,03:30')
  })

  it('保留天数留空 = 不自动清理', () => {
    expect(retentionText(null, fakeT)).toBe('ops.backup.keepForever|')
    expect(retentionText('', fakeT)).toBe('ops.backup.keepForever|')
    expect(retentionText(7, fakeT)).toBe('ops.backup.keepDays|7')
  })

  it('模块码本地化,未知码原样返回', () => {
    expect(moduleText('CONTENT', fakeT)).toBe('ops.backup.module.content|')
    const originalT = (key) => key
    expect(moduleText('UNKNOWN', originalT)).toBe('UNKNOWN')
  })

  it('周期下拉项', () => {
    expect(scheduleDays('WEEKLY')).toEqual([1, 2, 3, 4, 5, 6, 7])
    expect(scheduleDays('MONTHLY')).toHaveLength(28)
    expect(scheduleDays('DAILY')).toEqual([])
  })

  it('时间补齐两位', () => {
    expect(timeText('3:05')).toBe('03:05')
    expect(timeText('23:59')).toBe('23:59')
    expect(timeText('')).toBe('—')
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
    expect(durationText(0, fakeT)).toBe('—')
    expect(durationText(1500, fakeT)).toBe('ops.backup.durSecond|2')
    expect(durationText(83000, fakeT)).toBe('ops.backup.durMinute|1,23')
    expect(durationText(7500000, fakeT)).toBe('ops.backup.durHour|2,5')
  })
})
