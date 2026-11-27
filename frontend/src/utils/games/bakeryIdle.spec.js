import { describe, it, expect } from 'vitest'
import {
  ACHIEVEMENT_BONUS,
  ACHIEVEMENT_DEFS,
  ACHIEVEMENT_KEYS,
  CLICK_BASE,
  OFFLINE_CAP_SECONDS,
  OFFLINE_EFFICIENCY,
  ORDER_GAP_SECONDS,
  ORDER_REWARD_BONUS,
  ORDER_SECONDS,
  PRESTIGE_BASE,
  PRODUCT_DEFS,
  PRODUCT_KEYS,
  SAVE_VERSION,
  STORE_BONUS,
  UPGRADE_KEYS,
  achievementProgress,
  bakePerSecond,
  buyUpgrade,
  canPrestige,
  catchUp,
  clickPower,
  coinsPerSecond,
  createState,
  doughPerSecond,
  formatCompact,
  fulfillOrder,
  isValidBakeryState,
  itemRates,
  levelOf,
  multiplier,
  netWorth,
  orderReputation,
  parseState,
  prestige,
  prestigeRequirement,
  priceOf,
  productDef,
  rollOrder,
  selectProduct,
  sellPerSecond,
  serializeState,
  tapSell,
  tick,
  unlockProduct,
  upgradeCost,
} from './bakeryIdle'

const withLevels = (levels, patch = {}) => {
  const state = createState(0)
  return { ...state, ...patch, upgrades: { ...state.upgrades, ...levels } }
}

/** 造一个已解锁指定品种、带指定库存的档 */
const withProducts = (keys, patch = {}) => {
  const state = createState(0)
  const stock = Object.fromEntries(PRODUCT_KEYS.map((k) => [k, 0]))
  for (const [k, v] of Object.entries(patch.stock || {})) stock[k] = v
  return {
    ...state,
    ...patch,
    stock,
    unlocked: ['white', ...keys.filter((k) => k !== 'white')],
    product: patch.product || 'white',
    lastTick: patch.lastTick ?? 0,
  }
}

describe('bakeryIdle 开局与基础数值', () => {
  it('新档带一条基础产线,否则新玩家换不到第一笔金币', () => {
    const state = createState(1000)
    expect(state.version).toBe(SAVE_VERSION)
    expect(state.dough).toBe(0)
    expect(state.coins).toBe(0)
    expect(state.product).toBe('white')
    expect(state.unlocked).toEqual(['white'])
    expect(state.achievements).toEqual([])
    expect(state.order).toBeNull()
    expect(state.counters.baked).toBe(0)
    expect(state.lastTick).toBe(1000)
    expect(PRODUCT_KEYS.every((k) => state.stock[k] === 0)).toBe(true)
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

  it('点击一次即刻入账,并计入本轮营业额与累计计数', () => {
    const next = tapSell(createState(0))
    expect(next.coins).toBe(CLICK_BASE)
    expect(next.runCoins).toBe(CLICK_BASE)
    expect(next.counters.taps).toBe(1)
    expect(next.counters.coinsEarned).toBe(CLICK_BASE)
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
    expect(priceOf(state)).toBe(2)
    expect(priceOf(withLevels({ recipe: 3 }))).toBeCloseTo(2 * (1 + 0.08 * 3), 6)
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

describe('bakeryIdle 面包品种', () => {
  it('五种面包的价格、耗料、烤制时间逐级提高', () => {
    expect(PRODUCT_DEFS).toHaveLength(5)
    for (let i = 1; i < PRODUCT_DEFS.length; i += 1) {
      const prev = PRODUCT_DEFS[i - 1]
      const cur = PRODUCT_DEFS[i]
      expect(cur.basePrice).toBeGreaterThan(prev.basePrice)
      expect(cur.doughPer).toBeGreaterThan(prev.doughPer)
      expect(cur.bakeSeconds).toBeGreaterThan(prev.bakeSeconds)
      expect(cur.unlockCost).toBeGreaterThan(prev.unlockCost)
    }
  })

  it('没解锁不能切换生产,解锁后自动切过去', () => {
    const state = withLevels({}, { coins: PRODUCT_DEFS[1].unlockCost })
    expect(selectProduct(state, 'croissant')).toBe(state)

    const result = unlockProduct(state, 'croissant')
    expect(result.ok).toBe(true)
    expect(result.state.coins).toBe(0)
    expect(result.state.unlocked).toContain('croissant')
    expect(result.state.product).toBe('croissant')
    expect(unlockProduct(result.state, 'croissant').ok).toBe(false)
  })

  it('金币不够时解锁失败且原档不动', () => {
    const state = withLevels({}, { coins: PRODUCT_DEFS[1].unlockCost - 1 })
    const result = unlockProduct(state, 'croissant')
    expect(result.ok).toBe(false)
    expect(result.state).toBe(state)
  })

  it('单价按配方百分比加成,所有品种一起涨', () => {
    const state = withProducts(['croissant'], { upgrades: { ...createState(0).upgrades, recipe: 5 } })
    expect(priceOf(state, 'white')).toBeCloseTo(2 * 1.4, 6)
    expect(priceOf(state, 'croissant')).toBeCloseTo(6 * 1.4, 6)
    expect(priceOf(state, 'wedding')).toBeCloseTo(150 * 1.4, 6)
  })

  it('有效速率按当前品种的耗料与占炉时间折算', () => {
    const white = withLevels({ mixer: 2, oven: 2, clerk: 2 })
    expect(itemRates(white).bake).toBeCloseTo(bakePerSecond(white), 6)

    const cake = selectProduct(withProducts(['cake']), 'cake')
    const rates = itemRates(cake)
    expect(rates.bake).toBeCloseTo(bakePerSecond(cake) / productDef('cake').bakeSeconds, 6)
    expect(rates.items).toBeCloseTo(Math.min(doughPerSecond(cake) / 5, rates.bake, sellPerSecond(cake)), 6)
  })
})

describe('bakeryIdle 产线结算', () => {
  it('没有烤炉时不产面包,面团只会堆着', () => {
    const state = withLevels({ oven: 0 })
    const next = tick(state, 10)
    expect(next.dough).toBeCloseTo(doughPerSecond(state) * 10, 6)
    expect(next.stock.white).toBe(0)
    expect(next.coins).toBe(0)
  })

  it('烤炉能力不足时按面团库存封顶,不会凭空产面包', () => {
    const state = withLevels({ oven: 10, clerk: 0 }, { dough: 0.1 })
    const next = tick(state, 10)
    expect(next.dough).toBeCloseTo(0, 9)
    expect(next.stock.white).toBeLessThanOrEqual(0.1 + doughPerSecond(state) * 10 + 1e-6)
  })

  it('卖出的面包换金币,并按单价计入营业额', () => {
    // 断掉揉面机与烤炉以隔离卖出这一段,否则边烤边卖会让库存看不出变化
    const state = withLevels(
      { mixer: 0, oven: 0, clerk: 1, recipe: 2 },
      { stock: { white: 100, croissant: 0, toast: 0, cake: 0, wedding: 0 } },
    )
    const next = tick(state, 10)
    const sold = Math.min(sellPerSecond(state) * 10, 100)
    expect(next.stock.white).toBeCloseTo(100 - sold, 6)
    expect(next.coins).toBeCloseTo(sold * (2 * (1 + 0.08 * 2)), 6)
    expect(next.runCoins).toBeCloseTo(sold * (2 * (1 + 0.08 * 2)), 6)
  })

  it('只卖当前品种,其它品种库存原样留着', () => {
    const state = withProducts([], {
      stock: { croissant: 50 },
      upgrades: { ...createState(0).upgrades, mixer: 0, oven: 0, clerk: 1 },
    })
    const next = tick(state, 10)
    expect(next.stock.croissant).toBe(50)
    expect(next.stock.white).toBe(0)
    expect(next.coins).toBe(0)
  })

  it('烘烤按每份耗料扣面团', () => {
    const state = withProducts(['toast'], {
      product: 'toast',
      stock: { toast: 0 },
      upgrades: { ...createState(0).upgrades, mixer: 0, oven: 10, clerk: 0 },
      dough: 100,
    })
    const next = tick(state, 1)
    const baked = Math.min(bakePerSecond(state) / productDef('toast').bakeSeconds, 100 / 3)
    expect(next.stock.toast).toBeCloseTo(baked, 6)
    expect(next.dough).toBeCloseTo(100 - baked * 3, 6)
  })

  it('刚出炉的面包下一拍才上架,库存不会恒为 0', () => {
    // 开局烘烤与售卖速度相同:同一拍先烤后卖会把面包瞬间清空,数字看着像没在生产
    const state = createState(0)
    const first = tick(state, 1)
    expect(first.stock.white).toBeGreaterThan(0)
    expect(first.coins).toBe(0)
    const second = tick(first, 1)
    expect(second.stock.white).toBeGreaterThan(0)
    expect(second.coins).toBeGreaterThan(0)
  })

  it('有同品种未交付订单时预留现货,攒够订单量即可交付', () => {
    const state = rollOrder(createState(0), 0.2, 0.9)
    const mid = tick(state, 10)
    expect(mid.stock.white).toBeGreaterThan(1)
    expect(mid.stock.white).toBeLessThan(state.order.qty)

    const full = tick(state, ORDER_SECONDS - 2)
    expect(full.stock.white).toBeGreaterThanOrEqual(state.order.qty)
    expect(fulfillOrder(full).ok).toBe(true)
  })

  it('稳态每秒金币取产线最窄的一环', () => {
    const state = withLevels({ mixer: 1, oven: 5, clerk: 5 })
    expect(coinsPerSecond(state)).toBeCloseTo(itemRates(state).items * priceOf(state), 6)
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

  it('离线期间订单暂停,回来原样接着做', () => {
    const base = withLevels({})
    const state = { ...base, order: { product: 'white', qty: 6, seconds: 30, reward: 26 }, orderGap: 0 }
    const result = catchUp(state, 3600 * 1000)
    expect(result.state.order).toEqual(state.order)
    expect(result.state.orderGap).toBe(0)
  })
})

describe('bakeryIdle 顾客订单', () => {
  const ready = () => {
    const state = withProducts(['croissant'])
    return { ...state, counters: { ...state.counters, ordersDone: 0 } }
  }

  it('一半概率要当前品种,否则在已解锁品种里挑', () => {
    const state = ready()
    expect(rollOrder(state, 0.1, 0).order.product).toBe('white')
    expect(rollOrder(state, 0.9, 0).order.product).toBe('croissant')
  })

  it('数量按品种档位在区间内取值', () => {
    const state = ready()
    expect(rollOrder(state, 0.1, 0).order.qty).toBe(6)
    expect(rollOrder(state, 0.1, 0.999999).order.qty).toBe(14)
    const cake = selectProduct(withProducts(['cake']), 'cake')
    expect(rollOrder(cake, 0.1, 0).order.qty).toBe(2)
    expect(rollOrder(cake, 0.1, 0.999999).order.qty).toBe(6)
  })

  it('奖励 = 数量 × 单价 × 倍数 × 连单信誉', () => {
    const state = ready()
    const order = rollOrder(state, 0.1, 0).order
    expect(order.seconds).toBe(ORDER_SECONDS)
    expect(order.reward).toBe(Math.round(order.qty * priceOf(state, 'white') * ORDER_REWARD_BONUS * 1))
  })

  it('已有订单时不会顶掉', () => {
    const state = rollOrder(ready(), 0.1, 0)
    expect(rollOrder(state, 0.9, 0)).toBe(state)
  })

  it('倒计时走完算错过,隔一段时间再来下一位', () => {
    const state = { ...withLevels({}), order: { product: 'white', qty: 6, seconds: 2, reward: 26 }, orderGap: 0 }
    const next = tick(state, 2)
    expect(next.order).toBeNull()
    expect(next.orderGap).toBe(ORDER_GAP_SECONDS)
  })

  it('库存不足不能交付,也不会扣东西', () => {
    const state = { ...withProducts([]), order: { product: 'white', qty: 6, seconds: 30, reward: 26 } }
    const result = fulfillOrder(state)
    expect(result.ok).toBe(false)
    expect(result.state).toBe(state)
  })

  it('交付成功换金币、扣库存并计入营业额与累计单数', () => {
    const state = withProducts([], {
      stock: { white: 10 },
      coins: 5,
      runCoins: 5,
      order: { product: 'white', qty: 6, seconds: 30, reward: 30 },
    })
    const result = fulfillOrder(state)
    expect(result.ok).toBe(true)
    expect(result.reward).toBe(30)
    expect(result.state.coins).toBe(35)
    expect(result.state.runCoins).toBe(35)
    expect(result.state.stock.white).toBe(4)
    expect(result.state.counters.ordersDone).toBe(1)
    expect(result.state.counters.coinsEarned).toBe(30)
    expect(result.state.order).toBeNull()
    expect(result.state.orderGap).toBe(ORDER_GAP_SECONDS)
  })

  it('连单信誉抬高订单奖励,封顶 +100%', () => {
    const state = withProducts([])
    const fresh = orderReputation(state)
    const veteran = orderReputation({ ...state, counters: { ...state.counters, ordersDone: 100 } })
    expect(fresh).toBe(1)
    expect(veteran).toBeCloseTo(2, 6)
    const low = { ...state, counters: { ...state.counters, ordersDone: 2 } }
    const order = rollOrder(low, 0.1, 0).order
    expect(order.reward).toBe(Math.round(order.qty * priceOf(low, 'white') * ORDER_REWARD_BONUS * orderReputation(low)))
  })

  it('完成 10 单解锁「熟客」成就', () => {
    const state = withProducts([], {
      stock: { white: 10 },
      counters: { ...createState(0).counters, ordersDone: 9 },
      order: { product: 'white', qty: 6, seconds: 30, reward: 30 },
    })
    const result = fulfillOrder(state)
    expect(result.state.achievements).toContain('orders_10')
  })
})

describe('bakeryIdle 成就', () => {
  it('累计烤够就自动解锁,并永久提升全产线', () => {
    const state = withLevels({})
    expect(state.achievements).toEqual([])
    const next = tick(state, 3)
    expect(next.achievements).toContain('first_bake')
    expect(multiplier(next) / multiplier(state)).toBeCloseTo(1 + ACHIEVEMENT_BONUS, 6)
  })

  it('每个成就都只加一次', () => {
    const state = tick(withLevels({}), 3)
    const again = tick(state, 3)
    expect(again.achievements.filter((k) => k === 'first_bake')).toHaveLength(1)
  })

  it('累计点击 100 次解锁「快手」', () => {
    let state = withLevels({})
    for (let i = 0; i < 100; i += 1) state = tapSell(state)
    expect(state.achievements).toContain('tap_100')
    expect(state.counters.taps).toBe(100)
  })

  it('累计卖出 100 万金币解锁「百万流水」', () => {
    const state = withProducts([], {
      counters: { ...createState(0).counters, coinsEarned: 999999 },
      stock: { white: 10 },
      order: { product: 'white', qty: 4, seconds: 30, reward: 10 },
    })
    expect(fulfillOrder(state).state.achievements).toContain('coins_1m')
  })

  it('开出分店解锁分店成就', () => {
    const state = withProducts([], { runCoins: PRESTIGE_BASE })
    const next = prestige(state, 1000).state
    expect(next.stores).toBe(1)
    expect(next.achievements).toContain('store_1')
  })

  it('解锁全部品种解锁「满汉全席」', () => {
    let state = withProducts([], { coins: 1e9 })
    for (const def of PRODUCT_DEFS) {
      if (def.unlockCost > 0) state = unlockProduct(state, def.key).state
    }
    expect(state.unlocked).toHaveLength(PRODUCT_DEFS.length)
    expect(state.achievements).toContain('menu_full')
  })

  it('进度按累计口径给出当前值', () => {
    const def = ACHIEVEMENT_DEFS.find((d) => d.key === 'baker_100')
    const state = withProducts([], { counters: { ...createState(0).counters, baked: 42.5 } })
    expect(achievementProgress(state, def)).toBe(42.5)
    const menu = ACHIEVEMENT_DEFS.find((d) => d.key === 'menu_full')
    expect(achievementProgress(withProducts(['croissant', 'toast']), menu)).toBe(3)
  })

  it('成就键都在定义清单里', () => {
    const state = tick(withLevels({}), 3)
    expect(state.achievements.every((k) => ACHIEVEMENT_KEYS.includes(k))).toBe(true)
  })
})

describe('bakeryIdle 净资产与开分店', () => {
  it('净资产 = 金币 + 面团折价 + 各品种库存按各自单价', () => {
    const state = withProducts(['croissant'], {
      coins: 100,
      dough: 10,
      stock: { white: 6, croissant: 3 },
      upgrades: { ...createState(0).upgrades, recipe: 4 },
    })
    // 面团 10 × 0.5 + 白面包 6 × 2×1.32 + 牛角包 3 × 6×1.32
    expect(netWorth(state)).toBe(100 + 5 + Math.floor(6 * (2 * 1.32) + 3 * (6 * 1.32)))
  })

  it('门槛按本轮营业额 2 万起步,每开一家 ×4', () => {
    expect(prestigeRequirement(createState(0))).toBe(PRESTIGE_BASE)
    expect(prestigeRequirement({ ...createState(0), stores: 2 })).toBe(PRESTIGE_BASE * 16)
  })

  it('开分店重置产线与库存,但保留菜单、成就与累计计数', () => {
    let state = withLevels({ mixer: 10, hand: 5 }, { coins: PRESTIGE_BASE, runCoins: PRESTIGE_BASE })
    state = unlockProduct({ ...state, coins: 1e6 }, 'croissant').state
    state = { ...state, counters: { ...state.counters, baked: 123 } }
    expect(canPrestige(state)).toBe(true)

    const opened = prestige(state, 5000)
    expect(opened.ok).toBe(true)
    expect(opened.state.stores).toBe(1)
    expect(opened.state.coins).toBe(0)
    expect(opened.state.runCoins).toBe(0)
    expect(opened.state.stock.croissant).toBe(0)
    expect(opened.state.product).toBe('white')
    expect(opened.state.unlocked).toContain('croissant')
    expect(opened.state.counters.baked).toBe(123)
    expect(levelOf(opened.state, 'hand')).toBe(0)
    expect(levelOf(opened.state, 'mixer')).toBe(1)
    expect(opened.state.lastTick).toBe(5000)
    // 分店加成 × 本轮顺带解锁的成就(第一炉、烤满 100 份、开分店)
    expect(opened.state.achievements).toEqual(expect.arrayContaining(['first_bake', 'baker_100', 'store_1']))
    expect(multiplier(opened.state)).toBeCloseTo((1 + STORE_BONUS) * (1 + ACHIEVEMENT_BONUS * opened.state.achievements.length), 6)
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
  it('v2 序列化后可以原样解析回来', () => {
    const state = withProducts(['croissant'], {
      dough: 12.3456,
      coins: 900.1234,
      runCoins: 5000.5,
      stock: { white: 1.5, croissant: 2.25 },
      stores: 1,
      lastTick: 123456789,
      achievements: ['first_bake'],
      counters: { baked: 42.5, taps: 7, coinsEarned: 1234.5, ordersDone: 3 },
      order: { product: 'croissant', qty: 4, seconds: 30.5, reward: 66 },
      orderGap: 10,
      upgrades: { ...createState(0).upgrades, mixer: 3, recipe: 2 },
    })
    const parsed = parseState(serializeState(state))
    expect(parsed).not.toBeNull()
    expect(parsed.version).toBe(SAVE_VERSION)
    expect(parsed.dough).toBe(12.346)
    expect(parsed.coins).toBe(900.123)
    expect(parsed.stock.white).toBe(1.5)
    expect(parsed.stock.croissant).toBe(2.25)
    expect(parsed.product).toBe('white')
    expect(parsed.unlocked).toEqual(['white', 'croissant'])
    expect(parsed.stores).toBe(1)
    expect(parsed.achievements).toEqual(['first_bake'])
    expect(parsed.counters.baked).toBe(42.5)
    expect(parsed.order).toEqual({ product: 'croissant', qty: 4, seconds: 30.5, reward: 66 })
    expect(parsed.orderGap).toBe(10)
    expect(levelOf(parsed, 'mixer')).toBe(3)
    expect(parsed.lastTick).toBe(123456789)
  })

  it('坏档一律按无档处理', () => {
    expect(parseState(null)).toBeNull()
    expect(parseState('not json')).toBeNull()
    expect(parseState(JSON.stringify({ ...createState(0), version: 99 }))).toBeNull()
    expect(parseState(JSON.stringify({ ...createState(0), coins: -1 }))).toBeNull()
    expect(parseState(JSON.stringify({ ...createState(0), dough: Number.POSITIVE_INFINITY }))).toBeNull()
    expect(parseState(JSON.stringify({ ...createState(0), stores: 1.5 }))).toBeNull()
    expect(parseState(JSON.stringify({ ...createState(0), upgrades: { hand: 1.5 } }))).toBeNull()
    expect(parseState(JSON.stringify({ ...createState(0), unlocked: [] }))).toBeNull()
    expect(parseState(JSON.stringify({ ...createState(0), unlocked: ['white', 'donut'] }))).toBeNull()
    expect(parseState(JSON.stringify({ ...createState(0), product: 'cake' }))).toBeNull()
    expect(parseState(JSON.stringify({ ...createState(0), achievements: ['nope'] }))).toBeNull()
    expect(parseState(JSON.stringify({ ...createState(0), stock: { white: -1 } }))).toBeNull()
    expect(parseState(JSON.stringify({ ...createState(0), order: { product: 'white', qty: 0, seconds: 30, reward: 10 } }))).toBeNull()
  })

  it('v1 旧档迁移成 v2:面包库存进白面包,金币/升级/分店原样保留', () => {
    const v1 = {
      version: 1,
      dough: 3,
      bread: 7.5,
      coins: 100,
      runCoins: 20000,
      upgrades: { hand: 2, mixer: 4, oven: 3, clerk: 1, recipe: 0, fame: 0 },
      stores: 1,
      lastTick: 777,
    }
    const parsed = parseState(JSON.stringify(v1))
    expect(parsed).not.toBeNull()
    expect(parsed.version).toBe(SAVE_VERSION)
    expect(parsed.stock.white).toBe(7.5)
    expect(parsed.stock.croissant).toBe(0)
    expect(parsed.product).toBe('white')
    expect(parsed.unlocked).toEqual(['white'])
    expect(parsed.coins).toBe(100)
    expect(parsed.runCoins).toBe(20000)
    expect(parsed.stores).toBe(1)
    expect(levelOf(parsed, 'mixer')).toBe(4)
    expect(parsed.lastTick).toBe(777)
    expect(parsed.counters.baked).toBe(0)
    expect(parsed.achievements).toEqual([])
  })

  it('结构不全的 v1 档按无档处理', () => {
    expect(parseState(JSON.stringify({ version: 1, coins: 1 }))).toBeNull()
    expect(
      parseState(JSON.stringify({ version: 1, dough: 0, bread: -1, coins: 0, runCoins: 0, upgrades: {}, stores: 0, lastTick: 0 })),
    ).toBeNull()
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

  it('升级菜单后产线确实变快(不是只有价格数字变大)', () => {
    const white = withLevels({ mixer: 5, oven: 5, clerk: 5 })
    const wedding = selectProduct(withProducts(['croissant', 'toast', 'cake', 'wedding']), 'wedding')
    const leveled = { ...wedding, upgrades: { ...createState(0).upgrades, mixer: 5, oven: 5, clerk: 5 } }
    expect(coinsPerSecond(leveled)).toBeGreaterThan(coinsPerSecond(white))
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
