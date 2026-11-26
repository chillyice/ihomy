/**
 * 小面包房(放置挂机)内核:面团 → 面包 → 金币 → 升级产线,纯函数、不改入参。
 *
 * 自动产线按固定顺序推进:先产面团,再用面团烤面包(1:1),最后把面包按单价卖掉。
 * 任一段库存不足,该段就按库存封顶 —— 所以离线补算必须按小步长模拟,不能按「秒产出 × 秒数」直接加。
 *
 * 手动操作是「现烤现卖」:玩家亲手出货比批量卖给批发商贵,直接得金币(基础 1 金币/次)。
 * 这样点击在「卖不动」的早期也能立即换到钱,不至于囤一堆面团却买不起第一台机器。
 *
 * 净值(家庭榜口径)= 金币 + 面团折价 + 面包按当前单价折算;升级花费会扣金币,所以净值会回落。
 */

/** 存档版本:结构变更时 +1,旧档按版本直接丢弃 */
export const SAVE_VERSION = 1

/** 存档键(与其它小游戏同族,ihomy:games:<key>:<用途>:v1) */
export const STATE_KEY = 'ihomy:games:bakery:state:v1'

/** 面团折算成净值的单价(半价,避免囤原料就刷榜) */
export const DOUGH_VALUE = 0.5

/** 面包基础单价,每级配方 +PRICE_STEP */
export const BASE_PRICE = 2
export const PRICE_STEP = 1

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

/** 资源与金币上限:防长期离线把数值推到 Infinity(到顶后不再增长) */
const MAX_AMOUNT = 1e15
/** 单步模拟步长(秒):离线 8 小时 ≈ 2.9 万步,开销可忽略又保证产线耦合正确 */
const STEP_SECONDS = 1

/**
 * 六条升级线。amount 是每级增量:
 * CLICK 手动出售金币 / DOUGH 自动面团/秒 / BAKE 烤面包/秒 / SELL 卖出/秒 / PRICE 单价 / MULT 全局倍率。
 */
export const UPGRADE_DEFS = [
  { key: 'hand', base: 20, growth: 1.4, kind: 'CLICK', amount: 0.5 },
  { key: 'mixer', base: 40, growth: 1.18, kind: 'DOUGH', amount: 0.8 },
  { key: 'oven', base: 90, growth: 1.2, kind: 'BAKE', amount: 0.5 },
  { key: 'clerk', base: 150, growth: 1.22, kind: 'SELL', amount: 0.5 },
  { key: 'recipe', base: 500, growth: 1.45, kind: 'PRICE', amount: PRICE_STEP },
  { key: 'fame', base: 2000, growth: 1.7, kind: 'MULT', amount: 0.12 },
]

export const UPGRADE_KEYS = UPGRADE_DEFS.map((d) => d.key)

const DEF_BY_KEY = Object.fromEntries(UPGRADE_DEFS.map((d) => [d.key, d]))

const clampAmount = (value) => Math.max(0, Math.min(MAX_AMOUNT, value))

const emptyUpgrades = () => Object.fromEntries(UPGRADE_KEYS.map((k) => [k, 0]))

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
    bread: 0,
    coins: 0,
    runCoins: 0,
    upgrades: { ...emptyUpgrades(), ...STARTER_LEVELS },
    stores: 0,
    lastTick: now,
  }
}

export const levelOf = (state, key) => {
  const value = Number(state && state.upgrades && state.upgrades[key])
  return Number.isFinite(value) && value > 0 ? Math.floor(value) : 0
}

/** 全局倍率:口碑 + 分店永久加成 */
export function multiplier(state) {
  return (1 + 0.12 * levelOf(state, 'fame')) * (1 + STORE_BONUS * (Number(state && state.stores) || 0))
}

/** 单次「现烤现卖」的金币 */
export function clickPower(state) {
  return (CLICK_BASE + 0.5 * levelOf(state, 'hand')) * multiplier(state)
}

/** 面包当前单价 */
export function pricePerBread(state) {
  return BASE_PRICE + PRICE_STEP * levelOf(state, 'recipe')
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
 * 稳态金币/秒:产线最窄的一环决定吞吐(产面团 → 烤 → 卖)。
 * 只用于界面展示;真实结算由 tick 按库存逐步推进。
 */
export function coinsPerSecond(state) {
  const throughput = Math.min(doughPerSecond(state), bakePerSecond(state), sellPerSecond(state))
  return throughput * pricePerBread(state)
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

/** 现烤现卖一次:直接得金币,并计入本轮营业额 */
export function tapSell(state) {
  const earned = clickPower(state)
  return {
    ...state,
    coins: clampAmount(state.coins + earned),
    runCoins: clampAmount(state.runCoins + earned),
  }
}

/** 当前净资产:榜单与卡片统一口径 */
export function netWorth(state) {
  const price = pricePerBread(state)
  return Math.floor(state.coins + state.dough * DOUGH_VALUE + state.bread * price)
}

/** 下一家分店门槛(按本轮累计卖出金币判定) */
export function prestigeRequirement(state) {
  return Math.floor(PRESTIGE_BASE * Math.pow(PRESTIGE_FACTOR, Number(state && state.stores) || 0))
}

export function canPrestige(state) {
  return state.runCoins >= prestigeRequirement(state)
}

/**
 * 开分店:清空资源与升级,分店数 +1(永久倍率随之提高)。
 * 门槛按本轮累计卖出金币算,所以净资产会因为「重置」暂时掉下来,属预期。
 */
export function prestige(state, now = Date.now()) {
  if (!canPrestige(state)) return { ok: false, state }
  const next = {
    ...createState(now),
    stores: (Number(state.stores) || 0) + 1,
  }
  return { ok: true, state: next }
}

/**
 * 推进 seconds 秒(可小数):按 ≤1 秒的步长模拟产线耦合,返回新档。
 * 纯函数,不修改入参;非法秒数按 0 处理。
 */
export function tick(state, seconds) {
  const total = Number(seconds)
  if (!Number.isFinite(total) || total <= 0) return state
  const working = {
    ...state,
    upgrades: { ...state.upgrades },
  }
  const doughRate = doughPerSecond(state)
  const bakeRate = bakePerSecond(state)
  const sellRate = sellPerSecond(state)
  const price = pricePerBread(state)
  let remaining = total
  while (remaining > 1e-9) {
    const dt = Math.min(STEP_SECONDS, remaining)
    remaining -= dt
    working.dough = clampAmount(working.dough + doughRate * dt)
    const baked = Math.min(bakeRate * dt, working.dough)
    working.dough -= baked
    working.bread = clampAmount(working.bread + baked)
    const sold = Math.min(sellRate * dt, working.bread)
    working.bread -= sold
    const earned = sold * price
    working.coins = clampAmount(working.coins + earned)
    working.runCoins = clampAmount(working.runCoins + earned)
  }
  return working
}

/**
 * 从 lastTick 补到 now:不足 2 分钟静默全额补,更长按夜班效率(50%)并最多补 8 小时。
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
  const next = tick(state, base * efficiency)
  return { state: { ...next, lastTick: now }, elapsed: base, efficiency, capped, rollback: false }
}

/** 压缩数字展示:1.2K / 1.2万,按语言走 Intl 紧凑格式 */
export function formatCompact(value, locale = 'zh-CN') {
  const num = Number(value) || 0
  try {
    return new Intl.NumberFormat(locale, { notation: 'compact', maximumFractionDigits: 1 }).format(num)
  } catch {
    return String(Math.floor(num))
  }
}

/** 存档序列化:保留 3 位小数,避免长浮点把存档撑大 */
export function serializeState(state) {
  const round = (n) => Math.round((Number(n) || 0) * 1000) / 1000
  return JSON.stringify({
    version: SAVE_VERSION,
    dough: round(state.dough),
    bread: round(state.bread),
    coins: round(state.coins),
    runCoins: round(state.runCoins),
    upgrades: Object.fromEntries(UPGRADE_KEYS.map((k) => [k, levelOf(state, k)])),
    stores: Number(state.stores) || 0,
    lastTick: Number(state.lastTick) || 0,
  })
}

const isAmount = (value) => typeof value === 'number' && Number.isFinite(value) && value >= 0 && value <= MAX_AMOUNT
const isLevel = (value) => Number.isInteger(value) && value >= 0 && value <= 1_000_000

/** 存档校验:结构/版本/数值范围任一不符即视为坏档(调用方按无档处理) */
export function isValidBakeryState(value) {
  if (!value || typeof value !== 'object') return false
  if (value.version !== SAVE_VERSION) return false
  if (!isAmount(value.dough) || !isAmount(value.bread) || !isAmount(value.coins) || !isAmount(value.runCoins)) return false
  if (!Number.isInteger(value.stores) || value.stores < 0 || value.stores > 1_000_000) return false
  if (!Number.isFinite(value.lastTick) || value.lastTick < 0) return false
  const upgrades = value.upgrades
  if (!upgrades || typeof upgrades !== 'object') return false
  return UPGRADE_KEYS.every((key) => isLevel(upgrades[key] === undefined ? 0 : upgrades[key]))
}

/** 解析存档:坏档返回 null,不抛异常 */
export function parseState(raw) {
  if (!raw) return null
  try {
    const parsed = JSON.parse(raw)
    if (!isValidBakeryState(parsed)) return null
    return {
      ...createState(parsed.lastTick),
      dough: parsed.dough,
      bread: parsed.bread,
      coins: parsed.coins,
      runCoins: parsed.runCoins,
      upgrades: Object.fromEntries(UPGRADE_KEYS.map((k) => [k, parsed.upgrades[k] === undefined ? 0 : parsed.upgrades[k]])),
      stores: parsed.stores,
      lastTick: parsed.lastTick,
    }
  } catch {
    return null
  }
}
