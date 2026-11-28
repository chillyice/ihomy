// 小院慢生活:展示侧纯函数。
// 只做「按服务端状态算显示」,不做任何收益模拟——产出/经验/稀有掉落一律以 /state 为准,
// 客户端重算收益会和服务端引擎漂移(见 docs/踩坑速查.md「放置游戏服务端权威」)。
// 数值口径与后端 HomesteadEngine / HomesteadConst 对齐:整数毫秒 + 基点(10000 = 100%)。

export const SKILL_GATHER = 'GATHER'
export const SKILL_PROCESS = 'PROCESS'

/** 服务端常量镜像(与 HomesteadConst 一致,只在显示层用) */
export const SPEED_BASE_BP = 10000
export const LEVEL_STEP_BP = 100

/** 压缩数字(铜板/指数都能到很长的位数) */
export function formatCompact(value, locale = 'zh-CN') {
  const n = Number(value) || 0
  try {
    return new Intl.NumberFormat(locale, { notation: 'compact', maximumFractionDigits: 1 }).format(n)
  } catch {
    return String(Math.floor(n))
  }
}

/** 内容表里每条都有 name / nameEn,按当前语言取,缺英文名时回退中文 */
export function pickName(def, locale) {
  if (!def) return ''
  const en = typeof locale === 'string' && locale.toLowerCase().startsWith('en')
  return (en && def.nameEn) || def.name || ''
}

/** 内容表里的 desc / descEn 同口径 */
export function pickDesc(def, locale) {
  if (!def) return ''
  const en = typeof locale === 'string' && locale.toLowerCase().startsWith('en')
  return (en && def.descEn) || def.desc || ''
}

/** 基点转百分比文案用数值:150 → 1.5,200 → 2 */
export function bpToPercent(bp) {
  return Math.round((Math.max(0, Number(bp) || 0) / 100) * 10) / 10
}

/** 概率基点转百分比:320 → 3.2,5 → 0.05 */
export function chancePercent(bp) {
  return Math.round((Math.max(0, Number(bp) || 0) / 100) * 100) / 100
}

/** 速度倍率(基点):10000 + (等级-1)×100 + 全部加成(工具/装备/场地/buff 已由服务端折叠) */
export function speedBp(level, bonusBp = 0) {
  const lv = Math.max(1, Number(level) || 1)
  return SPEED_BASE_BP + (lv - 1) * LEVEL_STEP_BP + Math.max(0, Number(bonusBp) || 0)
}

/** 单次动作耗时(毫秒):ceil(baseMs × 10000 / speedBp),至少 1ms(与后端同口径) */
export function msPerItem(action, level, bonusBp = 0) {
  if (!action) return 0
  const baseMs = Math.max(1, Number(action.seconds) || 1) * 1000
  return Math.max(1, Math.ceil((baseMs * SPEED_BASE_BP) / speedBp(level, bonusBp)))
}

/**
 * 一件工具按阶数给出的加成(效果 × 当前阶):只用于展示其它动作的预估耗时/经验,
 * 真正的记账仍在服务端 HomesteadBonus,这里不参与任何收益计算。
 */
export function toolContribution(tool, tier) {
  const out = { speed: 0, xp: 0, yield: 0, rare: 0, efficiency: 0 }
  const level = Math.max(0, Number(tier) || 0)
  if (!tool || level <= 0) return out
  for (const effect of tool.effects || []) {
    const key = String(effect?.kind || '').toLowerCase()
    if (key in out) out[key] += Math.max(0, Number(effect.bp) || 0) * level
  }
  return out
}

/**
 * 某一动作吃到的加成:/state 的 bonus 是「当前动作」折叠好的总量,含它那件工具;
 * 换成别的工作时把当前工具那一份换掉(装备/场地/buff 视作共用)。纯展示,不做入账。
 */
export function actionBonus(stateBonus, currentTool, currentTier, tool, tier) {
  const base = stateBonus || {}
  const mine = toolContribution(currentTool, currentTier)
  const own = toolContribution(tool, tier)
  const fold = (key) => Math.max(0, (Number(base[key]) || 0) - mine[foldKey(key)] + own[foldKey(key)])
  return {
    speedBp: fold('speedBp'),
    xpBp: fold('xpBp'),
    yieldBp: fold('yieldBp'),
    rareBp: fold('rareBp'),
    efficiencyBp: fold('efficiencyBp'),
  }
}

/** bonus 视图的键(speedBp)与工具贡献的键(speed)对应关系 */
function foldKey(key) {
  return String(key || '').replace(/Bp$/, '')
}

/** 卖价含加成:向下取整(与后端 HomesteadBonus.sellCoins 同口径) */
export function sellCoinsWithBonus(price, sellBonusBp) {
  const base = Number(price) || 0
  if (base <= 0) return 0
  return Math.floor((base * (SPEED_BASE_BP + Math.max(0, Number(sellBonusBp) || 0))) / SPEED_BASE_BP)
}

/**
 * 进度环:锚在服务端给的 progressMs 上按本地流逝插值。
 * elapsedMs 是从拿到 /state 到此刻的时长;返回比例、距下次产出的剩余毫秒与「已经够几次」。
 * 只用于数字与到点对账,进度条的连续动画走 CSS(不每帧写 ref)。
 */
export function progressOf(state, elapsedMs) {
  const perItem = Number(state?.msPerItem) || 0
  const progress = Math.max(0, Number(state?.player?.progressMs) || 0)
  if (!state?.player?.actionCode || perItem <= 0) {
    return { ratio: 0, remainingMs: 0, due: 0 }
  }
  const raw = progress + Math.max(0, Number(elapsedMs) || 0)
  const due = Math.floor(raw / perItem)
  const rest = raw - due * perItem
  return { ratio: Math.min(1, rest / perItem), remainingMs: perItem - rest, due }
}

/** 材料够不够看(action.inputs 全部满足) */
export function hasMaterials(action, inventory) {
  if (!action?.inputs?.length) return true
  return action.inputs.every((need) => (inventory?.[need.item] || 0) >= need.qty)
}

/**
 * 动作状态:LOCKED 等级不够 / BUILDING 场地没升到 / MATERIALS 材料不足 / READY 可开工。
 * 与后端校验同一顺序(先等级、再场地、再材料),前端只用来禁用按钮与提示。
 */
export function actionState(action, skillLevel, inventory, buildingLevel = 0) {
  if (!action) return 'LOCKED'
  if ((Number(skillLevel) || 0) < (Number(action.level) || 1)) return 'LOCKED'
  if (action.requireBuilding && (Number(buildingLevel) || 0) < (Number(action.requireBuildingLevel) || 1)) {
    return 'BUILDING'
  }
  if (!hasMaterials(action, inventory)) return 'MATERIALS'
  return 'READY'
}

/**
 * 花费缺口:铜板与材料各差多少。工具/装备/场地三处共用(都带 {coins, materials})。
 * 返回 ok 表示当场就能付得起。
 */
export function costGap(step, coins, inventory) {
  if (!step) return { ok: false, maxed: true, missingCoins: 0, missingItems: [] }
  const missingCoins = Math.max(0, (Number(step.coins) || 0) - (Number(coins) || 0))
  const missingItems = (step.materials || []).filter((need) => (inventory?.[need.item] || 0) < need.qty)
  return { ok: missingCoins === 0 && missingItems.length === 0, maxed: false, missingCoins, missingItems }
}

/** 技能经验条比例(满级视为 1) */
export function xpRatio(skill) {
  const need = Number(skill?.xpToNext) || 0
  if (need <= 0) return 1
  return Math.min(1, Math.max(0, (Number(skill?.xp) || 0) / need))
}

/** 离线时长文案(秒级足够,按分钟/小时取整) */
export function elapsedText(elapsedMs) {
  const minutes = Math.floor((Number(elapsedMs) || 0) / 60000)
  if (minutes < 60) return { unit: 'minute', value: Math.max(1, minutes) }
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return { unit: rest > 0 ? 'hourMinute' : 'hour', value: hours, rest }
}

/** 料理 buff 剩余毫秒:以服务端 remainingMs 为锚,本地只补流逝 */
export function buffRemainingMs(buff, elapsedMs) {
  return Math.max(0, (Number(buff?.remainingMs) || 0) - Math.max(0, Number(elapsedMs) || 0))
}

/** 倒计时文案拆成「分:秒」两段(最后一分钟按秒走) */
export function countdownParts(ms) {
  const total = Math.max(0, Math.floor((Number(ms) || 0) / 1000))
  return { minutes: Math.floor(total / 60), seconds: total % 60 }
}
