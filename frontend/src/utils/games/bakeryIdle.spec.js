import { describe, it, expect } from 'vitest'
import {
  BASE_PRICE,
  CLICK_BASE,
  OFFLINE_CAP_SECONDS,
  OFFLINE_EFFICIENCY,
  PRESTIGE_BASE,
  SAVE_VERSION,
  STORE_BONUS,
  UPGRADE_KEYS,
  bakePerSecond,
  buyUpgrade,
  canPrestige,
  catchUp,
  clickPower,
  coinsPerSecond,
  createState,
  doughPerSecond,
  formatCompact,
  isValidBakeryState,
  levelOf,
  multiplier,
  netWorth,
  parseState,
  prestige,
  prestigeRequirement,
  pricePerBread,
  sellPerSecond,
  serializeState,
  tapSell,
  tick,
  upgradeCost,
} from './bakeryIdle'

const withLevels = (levels, patch = {}) => {
  const state = createState(0)
  return { ...state, ...patch, upgrades: { ...state.upgrades, ...levels } }
}

describe('bakeryIdle 开局与基础数值', () => {
  it('新档带一条基础产线,否则新玩家换不到第一笔金币', () => {
    const state = createState(1000)
    expect(state.version).toBe(SAVE_VERSION)
    expect(state.dough).toBe(0)
    expect(state.coins).toBe(0)
    expect(state.lastTick).toBe(1000)
    expect(levelOf(state, 'mixer')).toBe(1)
    expect(levelOf(state, 'oven')).toBe(1)
    expect(levelOf(state, 'clerk')).toBe(1)
    expect(levelOf(state, 'hand')).toBe(0)
    expect(state.stores).toBe(0)
  })

  it('手动出售的基础金币与手速加成', () => {
    const base = createState(0)
    expect(clickPower(base)).toBe(CLICK_BASE)
    expect(clickPower(withLevels({ hand: 4 }))).toBe(CLICK_BASE + 2)
    expect(clickPower(withLevels({ hand: 4, fame: 1 }))).toBeCloseTo((CLICK_BASE + 2) * 1.12, 6)
  })

  it('点击一次即刻入账,并计入本轮营业额', () => {
    const next = tapSell(createState(0))
    expect(next.coins).toBe(CLICK_BASE)
    expect(next.runCoins).toBe(CLICK_BASE)
    // 纯函数:原档不动
    expect(createState(0).coins).toBe(0)
  })

  it('六条产线的速率按等级与倍率计算', () => {
    const state = withLevels({ mixer: 2, oven: 3, clerk: 4, fame: 5 }, { stores: 2 })
    const mult = multiplier(state)
    expect(mult).toBeCloseTo((1 + 0.12 * 5) * (1 + STORE_BONUS * 2), 6)
    expect(doughPerSecond(state)).toBeCloseTo(0.8 * 2 * mult, 6)
    expect(bakePerSecond(state)).toBeCloseTo(0.5 * 3 * mult, 6)
    expect(sellPerSecond(state)).toBeCloseTo(0.5 * 4 * mult, 6)
    expect(pricePerBread(state)).toBe(BASE_PRICE)
    expect(pricePerBread(withLevels({ recipe: 3 }))).toBe(BASE_PRICE + 3)
  })

  it('升级价格按 growth 指数增长,买不起时原档返回', () => {
    const state = createState(0)
    const cost0 = upgradeCost(state, 'mixer')
    const cost1 = upgradeCost(withLevels({ mixer: 5 }), 'mixer')
    expect(cost1).toBeGreaterThan(cost0)

    const poor = { ...state, coins: cost0 - 1 }
    const failed = buyUpgrade(poor, 'mixer')
    expect(failed.bought).toBe(false)
    expect(failed.state).toBe(poor)

    const rich = { ...state, coins: cost0 }
    const ok = buyUpgrade(rich, 'mixer')
    expect(ok.bought).toBe(true)
    expect(ok.state.coins).toBe(0)
    expect(levelOf(ok.state, 'mixer')).toBe(levelOf(state, 'mixer') + 1)
  })
})

describe('bakeryIdle 产线结算', () => {
  it('没有烤炉时不产面包,面团只会堆着', () => {
    const state = withLevels({ oven: 0 })
    const next = tick(state, 10)
    expect(next.dough).toBeCloseTo(doughPerSecond(state) * 10, 6)
    expect(next.bread).toBe(0)
    expect(next.coins).toBe(0)
  })

  it('烤炉能力不足时按面团库存封顶,不会凭空产面包', () => {
    const state = withLevels({ oven: 10, clerk: 0 }, { dough: 0.1 })
    const next = tick(state, 10)
    expect(next.dough).toBeCloseTo(0, 9)
    expect(next.bread).toBeLessThanOrEqual(0.1 + doughPerSecond(state) * 10 + 1e-6)
  })

  it('卖出的面包换金币,并按单价计入营业额', () => {
    // 断掉揉面机以隔离卖出这一段,否则边烤边卖会让库存看不出变化
    const state = withLevels({ mixer: 0, clerk: 1, recipe: 2 }, { bread: 100 })
    const next = tick(state, 10)
    const sold = Math.min(sellPerSecond(state) * 10, 100)
    expect(next.bread).toBeCloseTo(100 - sold, 6)
    expect(next.coins).toBeCloseTo(sold * (BASE_PRICE + 2), 6)
    expect(next.runCoins).toBeCloseTo(sold * (BASE_PRICE + 2), 6)
  })

  it('稳态每秒金币取产线最窄的一环', () => {
    const state = withLevels({ mixer: 1, oven: 5, clerk: 5 })
    const expected = Math.min(doughPerSecond(state), bakePerSecond(state), sellPerSecond(state)) * pricePerBread(state)
    expect(coinsPerSecond(state)).toBeCloseTo(expected, 6)
  })

  it('非法秒数不推进', () => {
    const state = createState(0)
    expect(tick(state, 0)).toBe(state)
    expect(tick(state, -5)).toBe(state)
    expect(tick(state, Number.NaN)).toBe(state)
  })
})

describe('bakeryIdle 离线补算', () => {
  it('不足 2 分钟全额静默补', () => {
    const state = createState(0)
    const result = catchUp(state, 60 * 1000)
    expect(result.elapsed).toBe(60)
    expect(result.efficiency).toBe(1)
    expect(result.rollback).toBe(false)
    expect(result.state.coins).toBeCloseTo(tick(state, 60).coins, 6)
    expect(result.state.lastTick).toBe(60 * 1000)
  })

  it('超过 2 分钟按夜班效率(50%)结算', () => {
    const state = createState(0)
    const result = catchUp(state, 10 * 60 * 1000)
    expect(result.elapsed).toBe(600)
    expect(result.efficiency).toBe(OFFLINE_EFFICIENCY)
    expect(result.state.coins).toBeCloseTo(tick(state, 300).coins, 6)
  })

  it('最多补 8 小时,并标记已封顶', () => {
    const state = createState(0)
    const result = catchUp(state, 30 * 3600 * 1000)
    expect(result.elapsed).toBe(OFFLINE_CAP_SECONDS)
    expect(result.capped).toBe(true)
  })

  it('时钟回拨不白送收益', () => {
    const state = createState(60 * 1000)
    const result = catchUp(state, 10 * 1000)
    expect(result.elapsed).toBe(0)
    expect(result.rollback).toBe(true)
    expect(result.state.coins).toBe(state.coins)
    expect(result.state.lastTick).toBe(10 * 1000)
  })
})

describe('bakeryIdle 净资产与开分店', () => {
  it('净资产 = 金币 + 面团折价 + 面包按当前单价', () => {
    const state = withLevels({ recipe: 4 }, { coins: 100, dough: 10, bread: 6 })
    // 面团 10 × 0.5 + 面包 6 × (2 + 4)
    expect(netWorth(state)).toBe(100 + 5 + 36)
  })

  it('门槛按本轮营业额 2 万起步,每开一家 ×4', () => {
    expect(prestigeRequirement(createState(0))).toBe(PRESTIGE_BASE)
    expect(prestigeRequirement({ ...createState(0), stores: 2 })).toBe(PRESTIGE_BASE * 16)
  })

  it('开分店重置产线并保留永久加成', () => {
    const state = withLevels({ mixer: 10, hand: 5 }, { coins: PRESTIGE_BASE, runCoins: PRESTIGE_BASE })
    expect(canPrestige(state)).toBe(true)

    const opened = prestige(state, 5000)
    expect(opened.ok).toBe(true)
    expect(opened.state.stores).toBe(1)
    expect(opened.state.coins).toBe(0)
    expect(opened.state.runCoins).toBe(0)
    expect(levelOf(opened.state, 'hand')).toBe(0)
    expect(levelOf(opened.state, 'mixer')).toBe(1)
    expect(opened.state.lastTick).toBe(5000)
    expect(multiplier(opened.state)).toBeCloseTo(1 + STORE_BONUS, 6)
  })

  it('不够门槛时开不了分店', () => {
    const state = { ...createState(0), runCoins: PRESTIGE_BASE - 1 }
    expect(canPrestige(state)).toBe(false)
    const result = prestige(state, 1000)
    expect(result.ok).toBe(false)
    expect(result.state).toBe(state)
  })
})

describe('bakeryIdle 存档', () => {
  it('序列化后可以原样解析回来', () => {
    const state = withLevels(
      { mixer: 3, recipe: 2 },
      {
        dough: 12.3456,
        bread: 1.5,
        coins: 900.1234,
        runCoins: 5000.5,
        stores: 1,
        lastTick: 123456789,
      },
    )
    const parsed = parseState(serializeState(state))
    expect(parsed).not.toBeNull()
    expect(parsed.dough).toBe(12.346)
    expect(parsed.bread).toBe(1.5)
    expect(parsed.coins).toBe(900.123)
    expect(parsed.runCoins).toBe(5000.5)
    expect(parsed.stores).toBe(1)
    expect(levelOf(parsed, 'mixer')).toBe(3)
    expect(parsed.lastTick).toBe(123456789)
    expect(UPGRADE_KEYS.every((k) => Number.isInteger(parsed.upgrades[k]))).toBe(true)
  })

  it('坏档一律按无档处理', () => {
    expect(parseState(null)).toBeNull()
    expect(parseState('not json')).toBeNull()
    expect(parseState(JSON.stringify({ ...createState(0), version: 99 }))).toBeNull()
    expect(parseState(JSON.stringify({ ...createState(0), coins: -1 }))).toBeNull()
    expect(parseState(JSON.stringify({ ...createState(0), dough: Number.POSITIVE_INFINITY }))).toBeNull()
    expect(parseState(JSON.stringify({ ...createState(0), stores: 1.5 }))).toBeNull()
    expect(parseState(JSON.stringify({ ...createState(0), upgrades: { hand: 1.5 } }))).toBeNull()
  })

  it('校验器接受新档', () => {
    expect(isValidBakeryState(createState(1000))).toBe(true)
  })
})

describe('bakeryIdle 展示与节奏', () => {
  it('紧凑数字按语言格式化', () => {
    expect(formatCompact(999, 'en-US')).toBe('999')
    expect(formatCompact(1234, 'en-US')).toBe('1.2K')
    expect(formatCompact(12345, 'zh-CN')).toBe('1.2万')
  })

  it('节奏:纯挂机 2 小时内能开第一家分店', () => {
    const first = firstPrestigeSecond((s) => s, 7200)
    expect(first).not.toBeNull()
    expect(first).toBeLessThanOrEqual(7200)
    expect(first).toBeGreaterThan(600)
  })

  it('节奏:每秒点 2 下,30 分钟内能开第一家分店', () => {
    const first = firstPrestigeSecond((s) => tapSell(tapSell(s)), 1800)
    expect(first).not.toBeNull()
    expect(first).toBeLessThanOrEqual(1800)
  })
})

/**
 * 模拟玩家最朴素的策略(每秒结算一次,钱够就买当前最便宜的升级),
 * 返回首次可以开分店的秒数,用来守住数值不会退化成「永远开不出店」。
 */
function firstPrestigeSecond(tapPerSecond, seconds) {
  let state = createState(0)
  for (let second = 1; second <= seconds; second += 1) {
    state = tick(state, 1)
    state = { ...state, lastTick: second * 1000 }
    state = tapPerSecond(state)
    let bought = true
    while (bought) {
      bought = false
      const keys = [...UPGRADE_KEYS].sort((a, b) => upgradeCost(state, a) - upgradeCost(state, b))
      for (const key of keys) {
        if (buyUpgrade(state, key).bought) {
          state = buyUpgrade(state, key).state
          bought = true
          break
        }
      }
    }
    if (canPrestige(state)) return second
  }
  return null
}
