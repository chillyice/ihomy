import { describe, it, expect } from 'vitest'
import { formatClock } from './gameClock'

describe('小游戏计时显示', () => {
  it('不足一小时显示 m:ss', () => {
    expect(formatClock(0)).toBe('0:00')
    expect(formatClock(9)).toBe('0:09')
    expect(formatClock(65)).toBe('1:05')
    expect(formatClock(3599)).toBe('59:59')
  })

  it('超过一小时显示 h:mm:ss', () => {
    expect(formatClock(3600)).toBe('1:00:00')
    expect(formatClock(7325)).toBe('2:02:05')
  })

  it('非法/负数按 0 处理', () => {
    expect(formatClock(-5)).toBe('0:00')
    expect(formatClock(null)).toBe('0:00')
    expect(formatClock(Number.NaN)).toBe('0:00')
  })
})
