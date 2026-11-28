// 小院慢生活:展示侧纯函数。
// 只做「按服务端状态算显示」,不做任何收益模拟——产出/经验/稀有掉落一律以 /state 为准,
// 客户端重算收益会和服务端引擎漂移(见 docs/踩坑速查.md「放置游戏服务端权威」)。
// 数值口径与后端 HomesteadEngine / HomesteadConst 对齐:整数毫秒 + 基点(10000 = 100%)。

export const SKILL_GATHER = 'GATHER'
export const SKILL_PROCESS = 'PROCESS'

/** 服务端常量镜像:速度基点(与 HomesteadConst 一致,只在显示层用) */
export const SPEED_BASE_BP = 10000
export const LEVEL_STEP_BP = 100
export const TOOL_STEP_BP = 800

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

/** 速度倍率(基点):10000 + (等级-1)×100 + 工具阶×800 */
export function speedBp(level, toolTier) {
  const lv = Math.max(1, Number(level) || 1)
  const tier = Math.max(0, Number(toolTier) || 0)
  return SPEED_BASE_BP + (lv - 1) * LEVEL_STEP_BP + tier * TOOL_STEP_BP
}

/** 单次动作耗时(毫秒):ceil(baseMs × 10000 / speedBp),至少 1ms(与后端同口径) */
export function msPerItem(action, level, toolTier) {
  if (!action) return 0
  const baseMs = Math.max(1, Number(action.seconds) || 1) * 1000
  return Math.max(1, Math.ceil((baseMs * SPEED_BASE_BP) / speedBp(level, toolTier)))
}

/**
 * 进度环:锚在服务端给的 progressMs 上按本地流逝插值。
 * elapsedMs 是从拿到 /state 到此刻的时长;返回比例、距下次产出的剩余毫秒与「已经够几次」。
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

/** 背包/材料够不够看(action.inputs 全部满足) */
export function hasMaterials(action, inventory) {
  if (!action?.inputs?.length) return true
  return action.inputs.every((need) => (inventory?.[need.item] || 0) >= need.qty)
}

/**
 * 动作状态:LOCKED 等级不够 / MATERIALS 材料不足 / READY 可开工。
 * 与后端校验同一顺序(先看等级再看材料),前端只用来禁用按钮与提示。
 */
export function actionState(action, skillLevel, inventory) {
  if (!action) return 'LOCKED'
  if ((Number(skillLevel) || 0) < (Number(action.level) || 1)) return 'LOCKED'
  if (!hasMaterials(action, inventory)) return 'MATERIALS'
  return 'READY'
}

/** 工具升阶花费是否够;返回缺多少铜板 / 缺哪些材料 */
export function toolUpgradeCheck(tool, tier, coins, inventory) {
  const step = tool?.steps?.[tier]
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
