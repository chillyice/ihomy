import { describe, expect, it } from 'vitest'
import {
  actionBonus,
  actionState,
  bpToPercent,
  buffRemainingMs,
  chancePercent,
  costGap,
  countdownParts,
  elapsedText,
  expeditionScore,
  fitsSlot,
  formatCompact,
  hasMaterials,
  msPerItem,
  pickName,
  progressOf,
  sellCoinsWithBonus,
  slotFamily,
  speedBp,
  toolContribution,
  xpRatio,
} from './homestead'

// 动作样例与 content/homestead/actions.json 同形(只取算耗时用得到的字段)
const pickTwig = { code: 'PICK_TWIG', skill: 'WOOD', level: 1, seconds: 3, inputs: [], yields: [{ item: 'PINE_TWIG', qty: 1 }] }
const cookRice = {
  code: 'COOK_RICE',
  skill: 'COOKING',
  level: 1,
  seconds: 6,
  inputs: [{ item: 'PINE_TWIG', qty: 2 }],
  yields: [{ item: 'RICE_MEAL', qty: 1 }],
}
const fellCamphor = { code: 'FELL_CAMPHOR', skill: 'WOOD', level: 35, seconds: 12, inputs: [], yields: [] }
// 70 级工坊动作:要木作坊 Lv.5 才开
const planeBoard = {
  code: 'PLANE_BOARD',
  skill: 'WOOD',
  level: 70,
  seconds: 10,
  inputs: [{ item: 'DRY_WOOD', qty: 2 }],
  yields: [{ item: 'PLANK', qty: 1 }],
  requireBuilding: 'WORKSHOP_WOOD',
  requireBuildingLevel: 5,
}

describe('homestead display helpers', () => {
  it('speed follows level and folded bonus', () => {
    expect(speedBp(1, 0)).toBe(10000)
    expect(speedBp(2, 0)).toBe(10100)
    expect(speedBp(1, 4000)).toBe(14000)
    expect(speedBp(50, 4000)).toBe(18900)
    // 99 级 9800bp + 3000bp(工具/装备/场地折叠)
    expect(speedBp(99, 3000)).toBe(22800)
  })

  it('msPerItem matches backend integer rounding', () => {
    // 与 HomesteadEngineTest 里的期望值一一对应
    expect(msPerItem(pickTwig, 1, 0)).toBe(3000)
    expect(msPerItem(pickTwig, 2, 0)).toBe(2971)
    expect(msPerItem(pickTwig, 1, 4000)).toBe(2143)
    // 加成封顶 30000bp(服务端同口径):3000 × 10000 / 40000 = 750ms
    expect(msPerItem(pickTwig, 1, 30000)).toBe(750)
  })

  it('tool contribution scales with tier and ignores unknown kinds', () => {
    const tool = {
      effects: [
        { kind: 'SPEED', bp: 300 },
        { kind: 'RARE', bp: 100 },
        { kind: 'MYSTERY', bp: 900 },
      ],
    }
    expect(toolContribution(tool, 0)).toEqual({ speed: 0, xp: 0, yield: 0, rare: 0, efficiency: 0 })
    expect(toolContribution(tool, 3)).toEqual({ speed: 900, xp: 0, yield: 0, rare: 300, efficiency: 0 })
    expect(toolContribution(null, 5).speed).toBe(0)
  })

  it('action bonus swaps only the tool share of the folded total', () => {
    // 服务端给的 1000bp 里,当前动作那件工具贡献 400;换成本动作工具贡献 900 的另一个工作
    const stateBonus = { speedBp: 1000, xpBp: 500, yieldBp: 0, rareBp: 0, efficiencyBp: 0 }
    const currentTool = {
      effects: [
        { kind: 'SPEED', bp: 200 },
        { kind: 'XP', bp: 250 },
      ],
    }
    const otherTool = {
      effects: [
        { kind: 'SPEED', bp: 300 },
        { kind: 'XP', bp: 100 },
      ],
    }
    const same = actionBonus(stateBonus, currentTool, 2, currentTool, 2)
    expect(same.speedBp).toBe(1000)
    expect(same.xpBp).toBe(500)
    const other = actionBonus(stateBonus, currentTool, 2, otherTool, 3)
    expect(other.speedBp).toBe(1000 - 400 + 900)
    expect(other.xpBp).toBe(500 - 500 + 300)
    // 没有工具时扣掉当前工具那一份,但不为负
    expect(actionBonus({ speedBp: 100 }, currentTool, 2, null, 0).speedBp).toBe(0)
  })

  it('sell bonus floors like the backend', () => {
    expect(sellCoinsWithBonus(1000, 800)).toBe(1080)
    expect(sellCoinsWithBonus(7, 800)).toBe(7)
    expect(sellCoinsWithBonus(0, 800)).toBe(0)
    expect(sellCoinsWithBonus(1000, 0)).toBe(1000)
    expect(bpToPercent(150)).toBe(1.5)
    expect(bpToPercent(200)).toBe(2)
    // 概率按两位小数:3.2% / 0.05%
    expect(chancePercent(320)).toBe(3.2)
    expect(chancePercent(5)).toBe(0.05)
    expect(chancePercent(5000)).toBe(50)
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

  it('action state walks level → building → materials', () => {
    const inventory = { PINE_TWIG: 1 }
    expect(actionState(fellCamphor, 1, inventory)).toBe('LOCKED')
    expect(actionState(cookRice, 1, inventory)).toBe('MATERIALS')
    expect(actionState(cookRice, 1, { PINE_TWIG: 2 })).toBe('READY')
    expect(actionState(pickTwig, 1, {})).toBe('READY')
    expect(actionState(null, 50, {})).toBe('LOCKED')
    // 70 级工坊动作:等级够但场地没到 5 级是 BUILDING,不是 MATERIALS
    expect(actionState(planeBoard, 70, { DRY_WOOD: 9 }, 4)).toBe('BUILDING')
    expect(actionState(planeBoard, 70, { DRY_WOOD: 9 }, 5)).toBe('READY')
    expect(actionState(planeBoard, 70, {}, 5)).toBe('MATERIALS')
  })

  it('gather actions never need materials', () => {
    expect(hasMaterials(pickTwig, {})).toBe(true)
    expect(hasMaterials(cookRice, {})).toBe(false)
  })

  it('cost gap reports coins and materials for tool/gear/building steps', () => {
    const step = { coins: 30, materials: [{ item: 'DRY_WOOD', qty: 4 }] }
    const poor = costGap(step, 10, { DRY_WOOD: 1 })
    expect(poor.ok).toBe(false)
    expect(poor.maxed).toBe(false)
    expect(poor.missingCoins).toBe(20)
    expect(poor.missingItems).toEqual([{ item: 'DRY_WOOD', qty: 4 }])
    expect(costGap(step, 30, { DRY_WOOD: 4 }).ok).toBe(true)
    // 到顶(没有下一级 step)按 maxed 处理,前端据此显示「已到顶」
    expect(costGap(null, 9999, {})).toEqual({ ok: false, maxed: true, missingCoins: 0, missingItems: [] })
  })

  it('xp ratio clamps and max level reads full', () => {
    expect(xpRatio({ xp: 25, xpToNext: 50 })).toBe(0.5)
    expect(xpRatio({ xp: 80, xpToNext: 50 })).toBe(1)
    expect(xpRatio({ xp: 0, xpToNext: 0 })).toBe(1)
  })

  it('buff countdown anchors on server remainingMs', () => {
    expect(buffRemainingMs({ remainingMs: 90_000 }, 0)).toBe(90_000)
    expect(buffRemainingMs({ remainingMs: 90_000 }, 30_000)).toBe(60_000)
    expect(buffRemainingMs({ remainingMs: 1000 }, 5000)).toBe(0)
    expect(countdownParts(90_000)).toEqual({ minutes: 1, seconds: 30 })
    expect(countdownParts(59_999)).toEqual({ minutes: 0, seconds: 59 })
    expect(countdownParts(-1)).toEqual({ minutes: 0, seconds: 0 })
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

  it('expedition score folds level weight and worn power', () => {
    // 与后端 HomesteadConst.EXPEDITION_LEVEL_WEIGHT 同口径:每级 3 分 + 穿戴战力
    expect(expeditionScore(5, 20)).toBe(35)
    expect(expeditionScore(0, 0)).toBe(0)
    expect(expeditionScore('12', '40')).toBe(76)
    expect(expeditionScore(-3, -1)).toBe(0)
  })

  it('ring slots share one gear family', () => {
    expect(slotFamily('RING_A')).toBe('RING')
    expect(slotFamily('RING_B')).toBe('RING')
    expect(slotFamily('HEAD')).toBe('HEAD')
    expect(slotFamily('')).toBe('')
    // 蓝本挂在家族上,两格戒指都能穿同一件
    expect(fitsSlot('RING_A', 'RING')).toBe(true)
    expect(fitsSlot('RING_B', 'RING')).toBe(true)
    expect(fitsSlot('HEAD', 'RING')).toBe(false)
    expect(fitsSlot('', 'HEAD')).toBe(false)
    expect(fitsSlot('HEAD', '')).toBe(false)
  })
})
