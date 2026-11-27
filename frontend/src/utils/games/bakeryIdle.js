/**
 * 小面包房(放置挂机)内核:面团 → 面包 → 金币 → 升级产线,纯函数、不改入参。
 *
 * 自动产线按固定顺序推进:先产面团,再用面团烤当前品种的面包,最后把面包按单价卖掉。
 * 任一段库存不足,该段就按库存封顶 —— 所以离线补算必须按小步长模拟,不能按「秒产出 × 秒数」直接加。
 *
 * 卖的是「货架上这一拍之前烤好的」,刚出炉的下一拍才上架:开局烘烤与售卖速度相同,
 * 若同一拍里先烤后卖,面包会被瞬间卖空,库存永远显示 0(看起来像根本没在生产)。
 * 同时有同品种未交付订单时只卖超出订单量的部分,否则现货永远凑不够交付。
 *
 * 手动操作是「现烤现卖」:玩家亲手出货比批量卖给批发商贵,直接得金币(基础 1 金币/次)。
 * 这样点击在「卖不动」的早期也能立即换到钱,不至于囤一堆面团却买不起第一台机器。
 *
 * 菜单:五种面包一次性解锁,只有当前选中的品种会被烤制;越高级耗面团更多、烤得更慢、单价更高,
 * 所以「换品种」本质是把瓶颈在揉面 / 烘烤 / 出售之间挪一挪。
 * 顾客订单:定时上门要某个品种的现货,限时交付换约 2.2 倍单价;超时只是错过,没有惩罚。
 * 成就:一辈子累计型目标(烤了多少份、开过几家分店…),每个永久 +2% 全产线,开分店不会清掉。
 *
 * 净值(家庭榜口径)= 金币 + 面团折价 + 所有品种库存按当前单价折算;升级/解锁花费会扣金币,所以净值会回落。
 */

/** 存档版本:结构变更时 +1;v1(只有单一「面包」库存)会迁移成 v2,其余旧档丢弃 */
export const SAVE_VERSION = 2

/** 存档键(与其它小游戏同族,ihomy:games:<key>:<用途>:v1;后缀是用途版本,不是存档结构版本) */
export const STATE_KEY = 'ihomy:games:bakery:state:v1'

/** 面团折算成净值的单价(半价,避免囤原料就刷榜) */
export const DOUGH_VALUE = 0.5

/** 配方升级:每级让所有品种单价 +8% */
export const RECIPE_STEP = 0.08

/** 手动出售的基础金币 */
export const CLICK_BASE = 1

/** 开分店门槛:首家 2 万累计营业额,每多一家 ×4 */
export const PRESTIGE_BASE = 20000
export const PRESTIGE_FACTOR = 4
/** 每家分店的永久全局加成 */
export const STORE_BONUS = 0.15

/** 离线规则:≥2 分钟按夜班效率结算并弹窗,最多补 8 小时 */
export const OFFLINE_POPUP_SECONDS = 120
export const OFFLINE_EFFICIENCY = 0.5
export const OFFLINE_CAP_SECONDS = 8 * 3600

/** 顾客订单:基础奖励倍数、限时、下一位顾客的间隔、连单信誉 */
export const ORDER_REWARD_BONUS = 2.2
export const ORDER_SECONDS = 45
export const ORDER_GAP_SECONDS = 25
export const ORDER_REPUTATION_STEP = 0.03
export const ORDER_REPUTATION_CAP = 1

/** 每个成就给全部产线的永久加成 */
export const ACHIEVEMENT_BONUS = 0.02

/** 资源与金币上限:防长期离线把数值推到 Infinity(到顶后不再增长) */
const MAX_AMOUNT = 1e15
/** 单步模拟步长(秒):离线 8 小时 ≈ 2.9 万步,开销可忽略又保证产线耦合正确 */
const STEP_SECONDS = 1

/**
 * 五种面包。unlockCost 为一次性解锁价;doughPer = 每份耗面团;bakeSeconds = 每份占炉时间。
 * 价格与耗料同步抬高,让升级顺序在「揉面 / 烘烤 / 出售」之间真的会摇摆。
 */
export const PRODUCT_DEFS = [
  { key: 'white', emoji: '🥖', basePrice: 2, doughPer: 1, bakeSeconds: 1, unlockCost: 0 },
  { key: 'croissant', emoji: '🥐', basePrice: 6, doughPer: 2, bakeSeconds: 1.6, unlockCost: 1500 },
  { key: 'toast', emoji: '🍞', basePrice: 16, doughPer: 3, bakeSeconds: 2.2, unlockCost: 18000 },
  { key: 'cake', emoji: '🍰', basePrice: 48, doughPer: 5, bakeSeconds: 3, unlockCost: 250000 },
  { key: 'wedding', emoji: '🎂', basePrice: 150, doughPer: 8, bakeSeconds: 4.5, unlockCost: 3000000 },
]

export const PRODUCT_KEYS = PRODUCT_DEFS.map((d) => d.key)

const PRODUCT_BY_KEY = Object.fromEntries(PRODUCT_DEFS.map((d) => [d.key, d]))

/** 订单数量区间:越贵的品种一次要得越少,单笔总价值保持在同一档 */
const ORDER_QTY = {
  white: [6, 14],
  croissant: [4, 10],
  toast: [3, 8],
  cake: [2, 6],
  wedding: [2, 4],
}

/**
 * 六条升级线。amount 是每级增量:
 * CLICK 手动出售金币 / DOUGH 自动面团/秒 / BAKE 烤面包/秒 / SELL 卖出/秒 / PRICE 单价百分比 / MULT 全局倍率。
 */
export const UPGRADE_DEFS = [
  { key: 'hand', base: 20, growth: 1.4, kind: 'CLICK', amount: 0.5 },
  { key: 'mixer', base: 40, growth: 1.18, kind: 'DOUGH', amount: 0.8 },
  { key: 'oven', base: 90, growth: 1.2, kind: 'BAKE', amount: 0.5 },
  { key: 'clerk', base: 150, growth: 1.22, kind: 'SELL', amount: 0.5 },
  { key: 'recipe', base: 500, growth: 1.45, kind: 'PRICE', amount: RECIPE_STEP },
  { key: 'fame', base: 2000, growth: 1.7, kind: 'MULT', amount: 0.12 },
]

export const UPGRADE_KEYS = UPGRADE_DEFS.map((d) => d.key)

const DEF_BY_KEY = Object.fromEntries(UPGRADE_DEFS.map((d) => [d.key, d]))

/** 成就:metric 指向累计计数器(或 stores / unlocked),达到 target 即永久解锁 */
export const ACHIEVEMENT_DEFS = [
  { key: 'first_bake', metric: 'baked', target: 1 },
  { key: 'baker_100', metric: 'baked', target: 100 },
  { key: 'baker_1000', metric: 'baked', target: 1000 },
  { key: 'baker_10000', metric: 'baked', target: 10000 },
  { key: 'tap_100', metric: 'taps', target: 100 },
  { key: 'tap_1000', metric: 'taps', target: 1000 },
  { key: 'coins_10k', metric: 'coinsEarned', target: 10000 },
  { key: 'coins_1m', metric: 'coinsEarned', target: 1000000 },
  { key: 'store_1', metric: 'stores', target: 1 },
  { key: 'store_3', metric: 'stores', target: 3 },
  { key: 'orders_10', metric: 'ordersDone', target: 10 },
  { key: 'menu_full', metric: 'unlocked', target: PRODUCT_DEFS.length },
]

export const ACHIEVEMENT_KEYS = ACHIEVEMENT_DEFS.map((d) => d.key)

/** 累计计数器(开分店也不清零,供成就使用) */
const COUNTER_KEYS = ['baked', 'taps', 'coinsEarned', 'ordersDone']

const num = (value) => (Number.isFinite(Number(value)) ? Number(value) : 0)

const clampAmount = (value) => Math.max(0, Math.min(MAX_AMOUNT, num(value)))

const emptyUpgrades = () => Object.fromEntries(UPGRADE_KEYS.map((k) => [k, 0]))
const emptyStock = () => Object.fromEntries(PRODUCT_KEYS.map((k) => [k, 0]))
const emptyCounters = () => Object.fromEntries(COUNTER_KEYS.map((k) => [k, 0]))

/**
 * 开局赠送的一条基础产线:没有它就只能囤面团换不成金币,会把新玩家卡死。
 * 后续所有产能都在这条线上靠升级放大。
 */
const STARTER_LEVELS = { mixer: 1, oven: 1, clerk: 1 }

/** 新档:lastTick 由调用方给(默认此刻),测试可注入固定时间 */
export function createState(now = Date.now()) {
  return {
    version: SAVE_VERSION,
    dough: 0,
    coins: 0,
    runCoins: 0,
    stock: emptyStock(),
    product: 'white',
    unlocked: ['white'],
    upgrades: { ...emptyUpgrades(), ...STARTER_LEVELS },
    stores: 0,
    lastTick: now,
    achievements: [],
    counters: emptyCounters(),
    order: null,
    /** 没有订单时,还要多少「营业秒」才来下一位顾客 */
    orderGap: 5,
  }
}

export const levelOf = (state, key) => {
  const value = Number(state && state.upgrades && state.upgrades[key])
  return Number.isFinite(value) && value > 0 ? Math.floor(value) : 0
}

export const productDef = (key) => PRODUCT_BY_KEY[key] || PRODUCT_BY_KEY.white

export const stockOf = (state, key = state && state.product) => clampAmount(state && state.stock ? state.stock[key] : 0)

export const isUnlocked = (state, key) => Array.isArray(state && state.unlocked) && state.unlocked.includes(key)

const achievementsOf = (state) => (Array.isArray(state && state.achievements) ? state.achievements : [])

/** 全局倍率:口碑 × 分店永久加成 × 成就永久加成 */
export function multiplier(state) {
  const stores = Number(state && state.stores) || 0
  return (1 + 0.12 * levelOf(state, 'fame')) * (1 + STORE_BONUS * stores) * (1 + ACHIEVEMENT_BONUS * achievementsOf(state).length)
}

/** 单次「现烤现卖」的金币 */
export function clickPower(state) {
  return (CLICK_BASE + 0.5 * levelOf(state, 'hand')) * multiplier(state)
}

/** 某个品种的当前单价(配方按百分比加成,所有品种一起涨) */
export function priceOf(state, key = state && state.product) {
  return productDef(key).basePrice * (1 + RECIPE_STEP * levelOf(state, 'recipe'))
}

export function doughPerSecond(state) {
  return 0.8 * levelOf(state, 'mixer') * multiplier(state)
}

export function bakePerSecond(state) {
  return 0.5 * levelOf(state, 'oven') * multiplier(state)
}

export function sellPerSecond(state) {
  return 0.5 * levelOf(state, 'clerk') * multiplier(state)
}

/**
 * 当前品种的三段有效速率(份/秒):揉面速度按每份耗料折算,烘烤速度按每份占炉时间折算。
 * 界面展示与稳态估值共用,避免两处各写一套公式。
 */
export function itemRates(state) {
  const def = productDef(state && state.product)
  const dough = doughPerSecond(state)
  const bake = bakePerSecond(state) / def.bakeSeconds
  const sell = sellPerSecond(state)
  return {
    dough,
    bake,
    sell,
    items: Math.min(dough / def.doughPer, bake, sell),
  }
}

/** 稳态金币/秒:按当前品种,产线最窄的一环决定吞吐。只用于界面展示,真实结算由 tick 逐步推进。 */
export function coinsPerSecond(state) {
  return itemRates(state).items * priceOf(state)
}

/** 第 level+1 级的价格 */
export function upgradeCost(state, key) {
  const def = DEF_BY_KEY[key]
  if (!def) return Infinity
  return Math.ceil(def.base * Math.pow(def.growth, levelOf(state, key)))
}

export function canBuy(state, key) {
  return state.coins >= upgradeCost(state, key)
}

/** 买一级:买不起返回原档与 bought=false */
export function buyUpgrade(state, key) {
  const cost = upgradeCost(state, key)
  if (!Number.isFinite(cost) || !(state.coins >= cost)) {
    return { bought: false, cost, state }
  }
  const next = {
    ...state,
    coins: clampAmount(state.coins - cost),
    upgrades: { ...state.upgrades, [key]: levelOf(state, key) + 1 },
  }
  return { bought: true, cost, state: next }
}

/** 现烤现卖一次:直接得金币,并计入本轮营业额与一辈子累计 */
export function tapSell(state) {
  const earned = clickPower(state)
  const next = {
    ...state,
    coins: clampAmount(state.coins + earned),
    runCoins: clampAmount(state.runCoins + earned),
    counters: {
      ...state.counters,
      taps: num(state.counters && state.counters.taps) + 1,
      coinsEarned: clampAmount(num(state.counters && state.counters.coinsEarned) + earned),
    },
  }
  return applyAchievements(next)
}

/** 解锁某个品种(一次性花费);解锁后自动切过去生产 */
export function unlockProduct(state, key) {
  const def = PRODUCT_BY_KEY[key]
  if (!def || isUnlocked(state, key)) {
    return { ok: false, cost: def ? def.unlockCost : 0, state }
  }
  const cost = def.unlockCost
  if (!(num(state.coins) >= cost)) {
    return { ok: false, cost, state }
  }
  const next = {
    ...state,
    coins: clampAmount(state.coins - cost),
    unlocked: [...(state.unlocked || []), key],
    product: key,
  }
  return { ok: true, cost, state: applyAchievements(next) }
}

/** 切换在烤的品种:没解锁或本来就在烤它,原档返回 */
export function selectProduct(state, key) {
  if (!isUnlocked(state, key) || state.product === key) return state
  return { ...state, product: key }
}

/** 当前净资产:榜单与卡片统一口径(所有品种库存都按各自单价折算) */
export function netWorth(state) {
  let stockValue = 0
  for (const key of PRODUCT_KEYS) stockValue += stockOf(state, key) * priceOf(state, key)
  return Math.floor(num(state.coins) + num(state.dough) * DOUGH_VALUE + stockValue)
}

/** 下一家分店门槛(按本轮累计卖出金币判定) */
export function prestigeRequirement(state) {
  return Math.floor(PRESTIGE_BASE * Math.pow(PRESTIGE_FACTOR, Number(state && state.stores) || 0))
}

export function canPrestige(state) {
  return state.runCoins >= prestigeRequirement(state)
}

/**
 * 开分店:清空资源、库存与升级,分店数 +1(永久倍率随之提高)。
 * 菜单解锁、成就与累计计数器永久保留 —— 不然刚花百万解锁的品种被重置会让人再也不想开分店。
 */
export function prestige(state, now = Date.now()) {
  if (!canPrestige(state)) return { ok: false, state }
  const next = {
    ...createState(now),
    stores: (Number(state.stores) || 0) + 1,
    unlocked: [...(state.unlocked || ['white'])],
    achievements: [...achievementsOf(state)],
    counters: { ...emptyCounters(), ...(state.counters || {}) },
    order: null,
    orderGap: ORDER_GAP_SECONDS,
  }
  return { ok: true, state: applyAchievements(next) }
}

/**
 * 推进 seconds 秒(可小数):按 ≤1 秒的步长模拟产线耦合,返回新档。
 * 纯函数,不修改入参;非法秒数按 0 处理。
 * orderClock=false 用于离线补算:顾客只在你真正看着店铺时才上门。
 */
export function tick(state, seconds, { orderClock = true } = {}) {
  const total = Number(seconds)
  if (!Number.isFinite(total) || total <= 0) return state
  const def = productDef(state.product)
  const working = {
    ...state,
    stock: { ...emptyStock(), ...(state.stock || {}) },
    counters: { ...emptyCounters(), ...(state.counters || {}) },
    upgrades: { ...state.upgrades },
  }
  const doughRate = doughPerSecond(state)
  const bakeRate = bakePerSecond(state) / def.bakeSeconds
  const sellRate = sellPerSecond(state)
  const price = priceOf(state)
  let order = state.order ? { ...state.order } : null
  let orderGap = num(state.orderGap)
  let remaining = total
  while (remaining > 1e-9) {
    const dt = Math.min(STEP_SECONDS, remaining)
    remaining -= dt

    working.dough = clampAmount(working.dough + doughRate * dt)

    // 先卖掉货架上已有的库存(上一拍出炉的):同一拍里刚烤好的不参与售卖,
    // 否则开局「烤 0.5 份/秒 + 卖 0.5 份/秒」会把面包瞬间卖光,库存恒为 0。
    // 有同品种未交付订单时把订单量留在货架上,不然现货永远凑不够交付。
    const shelved = clampAmount(working.stock[def.key])
    const reserved = order && order.product === def.key ? num(order.qty) : 0
    const sold = Math.min(sellRate * dt, Math.max(0, shelved - reserved))
    working.stock[def.key] = clampAmount(shelved - sold)
    const earned = sold * price
    working.coins = clampAmount(working.coins + earned)
    working.runCoins = clampAmount(working.runCoins + earned)
    working.counters.coinsEarned = clampAmount(working.counters.coinsEarned + earned)

    // 烘烤同时受炉子与面团库存限制(每份耗 doughPer 份面团),烤好的下一拍上架
    const baked = Math.min(bakeRate * dt, working.dough / def.doughPer)
    working.dough = clampAmount(working.dough - baked * def.doughPer)
    working.stock[def.key] = clampAmount(working.stock[def.key] + baked)
    working.counters.baked = clampAmount(working.counters.baked + baked)

    if (orderClock) {
      if (order) {
        order.seconds -= dt
        if (order.seconds <= 1e-9) {
          order = null
          orderGap = ORDER_GAP_SECONDS
        }
      } else if (orderGap > 0) {
        orderGap = Math.max(0, orderGap - dt)
      }
    }
  }
  working.order = order
  working.orderGap = orderGap
  return applyAchievements(working)
}

/**
 * 从 lastTick 补到 now:不足 2 分钟静默全额补,更长按夜班效率(50%)并最多补 8 小时。
 * 离线期间订单暂停(不走倒计时),回来时原样接着做。
 * 时钟回拨(now < lastTick)按 0 处理并把 lastTick 拉回此刻,避免时间倒退白送收益。
 */
export function catchUp(state, now = Date.now()) {
  const lastTick = Number(state && state.lastTick)
  if (!Number.isFinite(lastTick) || lastTick < 0 || now < lastTick) {
    return { state: { ...state, lastTick: now }, elapsed: 0, efficiency: 1, capped: false, rollback: true }
  }
  const elapsed = Math.floor((now - lastTick) / 1000)
  const capped = elapsed > OFFLINE_CAP_SECONDS
  const base = Math.min(elapsed, OFFLINE_CAP_SECONDS)
  const efficiency = base >= OFFLINE_POPUP_SECONDS ? OFFLINE_EFFICIENCY : 1
  const next = tick(state, base * efficiency, { orderClock: false })
  return {
    state: { ...next, lastTick: now, order: state.order ? { ...state.order } : null, orderGap: num(state.orderGap) },
    elapsed: base,
    efficiency,
    capped,
    rollback: false,
  }
}

/** 连单信誉:每完成一笔订单提高 3% 订单奖励,封顶 +100% */
export function orderReputation(state) {
  const done = num(state.counters && state.counters.ordersDone)
  return 1 + Math.min(ORDER_REPUTATION_CAP, done * ORDER_REPUTATION_STEP)
}

/**
 * 生成一位顾客的订单:一半概率要当前品种,否则在已解锁品种里随机;
 * 数量按品种档位取区间。randPick / randQty 由调用方给(0~1),便于测试固定结果。
 */
export function rollOrder(state, randPick = 0.5, randQty = 0.5) {
  const unlocked = (state.unlocked || []).filter((key) => PRODUCT_BY_KEY[key])
  if (!unlocked.length || state.order) return state
  const current = PRODUCT_BY_KEY[state.product] ? state.product : unlocked[0]
  let key = current
  if (randPick >= 0.5 && unlocked.length > 1) {
    const index = Math.min(unlocked.length - 1, Math.floor(((randPick - 0.5) / 0.5) * unlocked.length))
    key = unlocked[index]
  }
  const [min, max] = ORDER_QTY[key] || [4, 10]
  const ratio = Math.max(0, Math.min(0.999999, num(randQty)))
  const qty = min + Math.floor(ratio * (max - min + 1))
  const reward = Math.round(qty * priceOf(state, key) * ORDER_REWARD_BONUS * orderReputation(state))
  return {
    ...state,
    order: { product: key, qty, seconds: ORDER_SECONDS, reward },
    orderGap: 0,
  }
}

/** 交付订单:库存不够返回 ok=false(不消耗任何东西);交付后计入营业额与累计单数 */
export function fulfillOrder(state) {
  const order = state.order
  if (!order) return { ok: false, reward: 0, state }
  const stock = stockOf(state, order.product)
  if (stock < order.qty) return { ok: false, reward: 0, state }
  const reward = num(order.reward)
  const next = {
    ...state,
    coins: clampAmount(num(state.coins) + reward),
    runCoins: clampAmount(num(state.runCoins) + reward),
    stock: { ...emptyStock(), ...(state.stock || {}), [order.product]: clampAmount(stock - order.qty) },
    counters: {
      ...emptyCounters(),
      ...(state.counters || {}),
      ordersDone: num(state.counters && state.counters.ordersDone) + 1,
      coinsEarned: clampAmount(num(state.counters && state.counters.coinsEarned) + reward),
    },
    order: null,
    orderGap: ORDER_GAP_SECONDS,
  }
  return { ok: true, reward, state: applyAchievements(next) }
}

/** 成就进度(累计口径;stores / unlocked 从当前档读) */
export function achievementProgress(state, def) {
  if (!def) return 0
  if (def.metric === 'stores') return num(state && state.stores)
  if (def.metric === 'unlocked') return Array.isArray(state && state.unlocked) ? state.unlocked.length : 0
  return num(state && state.counters && state.counters[def.metric])
}

/** 把所有已达标的成就补进存档:纯函数,没有新成就时原档返回 */
function applyAchievements(state) {
  const owned = achievementsOf(state)
  let next = state
  for (const def of ACHIEVEMENT_DEFS) {
    if (owned.includes(def.key)) continue
    if (achievementProgress(next, def) < def.target) continue
    if (next === state) next = { ...state, achievements: [...owned] }
    next.achievements.push(def.key)
  }
  return next
}

/** 压缩数字展示:1.2K / 1.2万,按语言走 Intl 紧凑格式 */
export function formatCompact(value, locale = 'zh-CN') {
  const value2 = Number(value) || 0
  try {
    return new Intl.NumberFormat(locale, { notation: 'compact', maximumFractionDigits: 1 }).format(value2)
  } catch {
    return String(Math.floor(value2))
  }
}

/** 存档序列化:保留 3 位小数,避免长浮点把存档撑大 */
export function serializeState(state) {
  const round = (n) => Math.round((Number(n) || 0) * 1000) / 1000
  return JSON.stringify({
    version: SAVE_VERSION,
    dough: round(state.dough),
    coins: round(state.coins),
    runCoins: round(state.runCoins),
    stock: Object.fromEntries(PRODUCT_KEYS.map((k) => [k, round(stockOf(state, k))])),
    product: productDef(state.product).key,
    unlocked: (state.unlocked || ['white']).filter((k) => PRODUCT_BY_KEY[k]),
    upgrades: Object.fromEntries(UPGRADE_KEYS.map((k) => [k, levelOf(state, k)])),
    stores: Number(state.stores) || 0,
    lastTick: Number(state.lastTick) || 0,
    achievements: achievementsOf(state).filter((k) => ACHIEVEMENT_KEYS.includes(k)),
    counters: Object.fromEntries(COUNTER_KEYS.map((k) => [k, round(state.counters && state.counters[k])])),
    order: state.order
      ? {
          product: productDef(state.order.product).key,
          qty: Math.max(0, Math.floor(num(state.order.qty))),
          seconds: round(state.order.seconds),
          reward: round(state.order.reward),
        }
      : null,
    orderGap: round(state.orderGap),
  })
}

const isAmount = (value) => typeof value === 'number' && Number.isFinite(value) && value >= 0 && value <= MAX_AMOUNT
const isLevel = (value) => Number.isInteger(value) && value >= 0 && value <= 1_000_000

/** 存档校验:结构/版本/数值范围任一不符即视为坏档(调用方按无档处理) */
export function isValidBakeryState(value) {
  if (!value || typeof value !== 'object') return false
  if (value.version !== SAVE_VERSION) return false
  if (!isAmount(value.dough) || !isAmount(value.coins) || !isAmount(value.runCoins)) return false
  if (!Number.isInteger(value.stores) || value.stores < 0 || value.stores > 1_000_000) return false
  if (!Number.isFinite(value.lastTick) || value.lastTick < 0) return false
  const upgrades = value.upgrades
  if (!upgrades || typeof upgrades !== 'object') return false
  if (!UPGRADE_KEYS.every((key) => isLevel(upgrades[key] === undefined ? 0 : upgrades[key]))) return false
  const unlocked = value.unlocked
  if (!Array.isArray(unlocked) || !unlocked.length) return false
  if (!unlocked.every((key) => PRODUCT_BY_KEY[key])) return false
  if (!PRODUCT_BY_KEY[value.product] || !unlocked.includes(value.product)) return false
  const stock = value.stock
  if (!stock || typeof stock !== 'object') return false
  if (!PRODUCT_KEYS.every((key) => isAmount(stock[key] === undefined ? 0 : stock[key]))) return false
  const achievements = value.achievements
  if (!Array.isArray(achievements) || !achievements.every((key) => ACHIEVEMENT_KEYS.includes(key))) return false
  const counters = value.counters
  if (!counters || typeof counters !== 'object') return false
  if (!COUNTER_KEYS.every((key) => isAmount(counters[key] === undefined ? 0 : counters[key]))) return false
  if (!isAmount(value.orderGap)) return false
  const order = value.order
  if (order !== null) {
    if (!order || typeof order !== 'object') return false
    if (!PRODUCT_BY_KEY[order.product]) return false
    if (!Number.isInteger(order.qty) || order.qty <= 0 || order.qty > 1_000_000) return false
    if (!isAmount(order.seconds) || order.seconds > 1_000_000) return false
    if (!isAmount(order.reward)) return false
  }
  return true
}

/** v1 旧档(只有单一 bread 库存)校验:字段齐、范围合理才迁移 */
function isV1State(value) {
  if (!value || value.version !== 1) return false
  if (!isAmount(value.dough) || !isAmount(value.bread) || !isAmount(value.coins) || !isAmount(value.runCoins)) return false
  if (!Number.isInteger(value.stores) || value.stores < 0 || value.stores > 1_000_000) return false
  if (!Number.isFinite(value.lastTick) || value.lastTick < 0) return false
  const upgrades = value.upgrades
  if (!upgrades || typeof upgrades !== 'object') return false
  return UPGRADE_KEYS.every((key) => isLevel(upgrades[key] === undefined ? 0 : upgrades[key]))
}

/** v1 → v2:旧「面包」库存原样放进白面包,升级/金币/分店全保留(菜单只解锁白面包) */
function migrateV1(value) {
  const stock = emptyStock()
  stock.white = value.bread
  return {
    ...createState(value.lastTick),
    dough: value.dough,
    coins: value.coins,
    runCoins: value.runCoins,
    stock,
    upgrades: { ...emptyUpgrades(), ...STARTER_LEVELS, ...Object.fromEntries(UPGRADE_KEYS.map((k) => [k, levelOf(value, k)])) },
    stores: value.stores,
    lastTick: value.lastTick,
  }
}

/** 把合法 v2 档补齐缺省键(未知字段丢弃) */
function normalizeState(parsed) {
  return {
    version: SAVE_VERSION,
    dough: num(parsed.dough),
    coins: num(parsed.coins),
    runCoins: num(parsed.runCoins),
    stock: Object.fromEntries(PRODUCT_KEYS.map((k) => [k, num(parsed.stock && parsed.stock[k])])),
    product: parsed.product,
    unlocked: [...parsed.unlocked],
    upgrades: Object.fromEntries(UPGRADE_KEYS.map((k) => [k, levelOf(parsed, k)])),
    stores: parsed.stores,
    lastTick: parsed.lastTick,
    achievements: [...parsed.achievements],
    counters: Object.fromEntries(COUNTER_KEYS.map((k) => [k, num(parsed.counters && parsed.counters[k])])),
    order: parsed.order
      ? {
          product: parsed.order.product,
          qty: parsed.order.qty,
          seconds: num(parsed.order.seconds),
          reward: num(parsed.order.reward),
        }
      : null,
    orderGap: num(parsed.orderGap),
  }
}

/** 解析存档:坏档返回 null,不抛异常;v1 档自动迁移 */
export function parseState(raw) {
  if (!raw) return null
  try {
    const parsed = JSON.parse(raw)
    if (parsed && parsed.version === 1) return isV1State(parsed) ? migrateV1(parsed) : null
    if (!isValidBakeryState(parsed)) return null
    return normalizeState(parsed)
  } catch {
    return null
  }
}
