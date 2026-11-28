import { describe, expect, it } from 'vitest'
import {
  actionState,
  elapsedText,
  formatCompact,
  hasMaterials,
  msPerItem,
  pickName,
  progressOf,
  speedBp,
  toolUpgradeCheck,
  xpRatio,
} from './homestead'

// 动作样例与 content/homestead/actions.json 同形(只取算耗时用得到的字段)
const pickTwig = { code: 'PICK_TWIG', level: 1, seconds: 3, inputs: [], yields: [{ item: 'PINE_TWIG', qty: 1 }] }
const cookRice = {
  code: 'COOK_RICE',
  level: 1,
  seconds: 6,
  inputs: [{ item: 'PINE_TWIG', qty: 2 }],
  yields: [{ item: 'RICE_MEAL', qty: 1 }],
}
const fellCamphor = { code: 'FELL_CAMPHOR', level: 35, seconds: 12, inputs: [], yields: [] }

describe('homestead display helpers', () => {
  it('speed follows level and tool tier', () => {
    expect(speedBp(1, 0)).toBe(10000)
    expect(speedBp(2, 0)).toBe(10100)
    expect(speedBp(1, 5)).toBe(14000)
    expect(speedBp(50, 5)).toBe(18900)
  })

  it('msPerItem matches backend integer rounding', () => {
    // 与 HomesteadEngineTest 里的期望值一一对应
    expect(msPerItem(pickTwig, 1, 0)).toBe(3000)
    expect(msPerItem(pickTwig, 2, 0)).toBe(2971)
    expect(msPerItem(pickTwig, 1, 5)).toBe(2143)
  })

  it('progress interpolates from server anchor', () => {
    const state = { player: { actionCode: 'PICK_TWIG', progressMs: 1000 }, msPerItem: 3000 }
    expect(progressOf(state, 0)).toEqual({ ratio: 1000 / 3000, remainingMs: 2000, due: 0 })
    // 刚好够一次 → 新一圈从头开始
    expect(progressOf(state, 2000)).toEqual({ ratio: 0, remainingMs: 3000, due: 1 })
    expect(progressOf(state, 3500)).toEqual({ ratio: 0.5, remainingMs: 1500, due: 1 })
    expect(progressOf(state, 5000).due).toBe(2)
  })

  it('idle player has no progress ring', () => {
    expect(progressOf({ player: { actionCode: null, progressMs: 0 }, msPerItem: 0 }, 9999)).toEqual({
      ratio: 0,
      remainingMs: 0,
      due: 0,
    })
  })

  it('action state prefers level gate over materials', () => {
    const inventory = { PINE_TWIG: 1 }
    expect(actionState(fellCamphor, 1, inventory)).toBe('LOCKED')
    expect(actionState(cookRice, 1, inventory)).toBe('MATERIALS')
    expect(actionState(cookRice, 1, { PINE_TWIG: 2 })).toBe('READY')
    expect(actionState(pickTwig, 1, {})).toBe('READY')
    expect(actionState(null, 50, {})).toBe('LOCKED')
  })

  it('gather actions never need materials', () => {
    expect(hasMaterials(pickTwig, {})).toBe(true)
    expect(hasMaterials(cookRice, {})).toBe(false)
  })

  it('tool upgrade reports what is missing', () => {
    const tool = { code: 'AXE', steps: [{ coins: 30, materials: [{ item: 'DRY_WOOD', qty: 4 }] }] }
    const poor = toolUpgradeCheck(tool, 0, 10, { DRY_WOOD: 1 })
    expect(poor.ok).toBe(false)
    expect(poor.missingCoins).toBe(20)
    expect(poor.missingItems).toEqual([{ item: 'DRY_WOOD', qty: 4 }])
    expect(toolUpgradeCheck(tool, 0, 30, { DRY_WOOD: 4 }).ok).toBe(true)
    expect(toolUpgradeCheck(tool, 1, 9999, {}).maxed).toBe(true)
  })

  it('xp ratio clamps and max level reads full', () => {
    expect(xpRatio({ xp: 25, xpToNext: 50 })).toBe(0.5)
    expect(xpRatio({ xp: 80, xpToNext: 50 })).toBe(1)
    expect(xpRatio({ xp: 0, xpToNext: 0 })).toBe(1)
  })

  it('names fall back to chinese without english', () => {
    expect(pickName({ name: '拾柴', nameEn: 'Woodcutting' }, 'en-US')).toBe('Woodcutting')
    expect(pickName({ name: '拾柴' }, 'en')).toBe('拾柴')
    expect(pickName({ name: '拾柴' }, 'zh-CN')).toBe('拾柴')
    expect(pickName(null, 'en')).toBe('')
  })

  it('elapsed text rounds to minutes and hours', () => {
    expect(elapsedText(30_000)).toEqual({ unit: 'minute', value: 1 })
    expect(elapsedText(90 * 60_000)).toEqual({ unit: 'hourMinute', value: 1, rest: 30 })
    expect(elapsedText(2 * 3600_000)).toEqual({ unit: 'hour', value: 2, rest: 0 })
  })

  it('compact numbers survive locale differences', () => {
    expect(formatCompact(0)).toBe('0')
    expect(formatCompact(1000)).toMatch(/1/)
  })
})
