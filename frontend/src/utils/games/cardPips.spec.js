import { describe, it, expect } from 'vitest'
import { isCourtCard, pipPoints } from './cardPips'

describe('扑克牌点数图案', () => {
  it('A 到 10 的花色图案数量与点数一致', () => {
    for (let rank = 1; rank <= 10; rank++) {
      expect(pipPoints(rank)).toHaveLength(rank)
    }
  })

  it('人头牌不铺点', () => {
    expect(pipPoints(11)).toEqual([])
    expect(pipPoints(12)).toEqual([])
    expect(pipPoints(13)).toEqual([])
  })

  it('人头牌判定覆盖 J/Q/K', () => {
    expect(isCourtCard(11)).toBe(true)
    expect(isCourtCard(12)).toBe(true)
    expect(isCourtCard(13)).toBe(true)
    expect(isCourtCard(10)).toBe(false)
    expect(isCourtCard(1)).toBe(false)
  })

  it('点位始终落在图案区内', () => {
    for (let rank = 1; rank <= 10; rank++) {
      for (const [x, y] of pipPoints(rank)) {
        expect(x).toBeGreaterThanOrEqual(0)
        expect(x).toBeLessThanOrEqual(100)
        expect(y).toBeGreaterThanOrEqual(0)
        expect(y).toBeLessThanOrEqual(100)
      }
    }
  })
})
