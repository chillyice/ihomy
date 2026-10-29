// 理财储蓄计算内核(纯函数):到期日、计息天数、预期/已计提利息、到期本息、派生状态、
// 实际收益与实际年化、持仓概览。
//
// 口径(与后端 FinanceService、docs/需求设计说明书.md §4.5.10 一致,不可各写一套):
// - 本金不计支出:持仓本金与收益分开,收益只在赎回时勾选入账;
// - 年化入参是「百分数」(2.9 = 2.9%),金额为「元」,输出统一四舍五入到 2 位;
// - 天数「算头不算尾」;按月折算用 termMonths(缺省按起止日倒推整月);
// - 年化为空 = 浮动收益(基金等),预期/应得返回 null,界面显示「浮动收益」而不是 NaN。
import { addMonths, daysBetween, monthDiff, todayStr } from './date'
import { round2, toCents, toYuan } from './money'

/** 产品类型(与 sys_dict_item 的 finance_type 一一对应,顺序即下拉顺序) */
export const FINANCE_TYPES = ['DEPOSIT', 'LARGE_DEPOSIT', 'BOND', 'BANK_WMP', 'FUND', 'INSURANCE', 'OTHER']

/** 计息方式(与 finance_interest_mode 一一对应) */
export const INTEREST_MODES = ['BY_MONTH', 'BY_DAY', 'COMPOUND']

/** 状态(与 finance_status 一一对应;MATURED 由到期日派生,库内一般不主动写) */
export const FINANCE_STATUSES = ['HOLDING', 'MATURED', 'REDEEMED', 'CANCELLED']

/** 按日折算可选的计息基准天数 */
export const DAY_COUNTS = [365, 360]

/** 风险等级(选填) */
export const RISK_LEVELS = ['R1', 'R2', 'R3', 'R4', 'R5']

/** 字典文案(走 i18n book.finance.type.* / status.* / mode.*;缺键回落枚举,不显示空白) */
function label(prefix, value, t) {
  if (!value) return ''
  const key = `book.finance.${prefix}.${value}`
  const msg = t(key)
  return msg === key ? value : msg
}

export const financeTypeLabel = (type, t) => label('type', type, t)
export const financeStatusLabel = (status, t) => label('status', status, t)
export const interestModeLabel = (mode, t) => label('mode', mode, t)

/** 数字归一:非法值 → null(区分「0」与「没填」) */
function num(value) {
  if (value === null || value === undefined || value === '') return null
  const n = Number(value)
  return Number.isFinite(n) ? n : null
}

/** 计息基准天数:只认 360,其余按 365(与后端 DictConst.financeDayCount 同口径) */
export function dayCountOf(p) {
  return Number(p?.dayCount) === 360 ? 360 : 365
}

/** 到期日:显式 endDate 优先,否则起息日 + 期限(月);两者皆空 = 活期 */
export function endDateOf(p) {
  if (!p) return null
  if (p.endDate) return String(p.endDate)
  const months = num(p.termMonths)
  if (!p.startDate || months === null || months <= 0) return null
  return addMonths(p.startDate, months)
}

/** 计息总天数(算头不算尾);没有到期日 → null */
export function totalDays(p) {
  const end = endDateOf(p)
  if (!end) return null
  return Math.max(0, daysBetween(p.startDate, end))
}

/** 已持有天数:到期后钉在总天数,未起息为 0 */
export function heldDays(p, today = new Date()) {
  if (!p?.startDate) return 0
  const now = todayStr(today)
  const end = endDateOf(p)
  if (!end) return Math.max(0, daysBetween(p.startDate, now))
  const total = Math.max(0, daysBetween(p.startDate, end))
  const until = daysBetween(p.startDate, now < end ? now : end)
  return Math.min(total, Math.max(0, until))
}

/** 进度百分比 0~100(当日到期 = 100) */
export function progressPct(p, today = new Date()) {
  const end = endDateOf(p)
  if (!end) return 0
  const total = Math.max(0, daysBetween(p.startDate, end))
  if (total <= 0) return 100
  return Math.min(100, Math.max(0, (heldDays(p, today) / total) * 100))
}

/** 剩余天数:负数 = 已过期,0 = 今天到期;活期 → null */
export function remainingDays(p, today = new Date()) {
  const end = endDateOf(p)
  if (!end) return null
  return daysBetween(todayStr(today), end)
}

/** 年化百分数(小数形式);没填 → null(浮动收益) */
function rateOf(p) {
  const rate = num(p?.annualRate)
  return rate === null ? null : rate / 100
}

/**
 * 到期应得利息(元):
 * 按月折算 = 本金 × 年化 × 月数 / 12;按日折算 = 本金 × 年化 × 总天数 / 基准天数;
 * 复利 = 本金 × ((1 + 年化)^(总天数/基准天数) − 1)。年化为空 → null。
 */
export function expectedInterest(p) {
  const principal = num(p?.principal) || 0
  const r = rateOf(p)
  if (r === null || principal <= 0) return null
  const mode = p.interestMode || 'BY_MONTH'
  if (mode === 'BY_MONTH') {
    const end = endDateOf(p)
    const months = num(p.termMonths) ?? (end ? monthDiff(p.startDate, end) : 0)
    if (months <= 0) return round2(0)
    return round2(principal * r * (months / 12))
  }
  const total = totalDays(p)
  if (total === null) return null
  const base = dayCountOf(p)
  if (mode === 'COMPOUND') return round2(principal * (Math.pow(1 + r, total / base) - 1))
  return round2(principal * r * (total / base))
}

/**
 * 已计提利息(元):按月折算按已持有天数占期限比例摊,按日折算/复利按已持有天数算。
 * 年化为空 → null;按月折算且当天到期(totalDays = 0)→ null(没有可摊的期限)。
 */
export function accruedInterest(p, today = new Date()) {
  const principal = num(p?.principal) || 0
  const r = rateOf(p)
  if (r === null || principal <= 0) return null
  const mode = p.interestMode || 'BY_MONTH'
  const held = heldDays(p, today)
  if (mode === 'BY_MONTH') {
    const total = totalDays(p)
    const expected = expectedInterest(p)
    if (expected === null || !total) return null
    return round2((expected * held) / total)
  }
  const base = dayCountOf(p)
  if (mode === 'COMPOUND') return round2(principal * (Math.pow(1 + r, held / base) - 1))
  return round2(principal * r * (held / base))
}

/** 到期本息合计(元);浮动收益 → null(只能看实际收益) */
export function maturityAmount(p) {
  const principal = num(p?.principal)
  const expected = expectedInterest(p)
  if (principal === null || expected === null) return null
  return round2(principal + expected)
}

/** 派生状态:已赎回/已作废原样;其余按到期日判「已到期·待赎回」 */
export function derivedStatus(p, today = new Date()) {
  const s = String(p?.status || '').toUpperCase()
  if (s === 'REDEEMED' || s === 'CANCELLED') return s
  const end = endDateOf(p)
  if (end && end < todayStr(today)) return 'MATURED'
  return 'HOLDING'
}

/** 已实现收益(元):优先取落库值,否则用「到账金额 − 本金」;都没填 → null */
export function actualInterest(p) {
  const stored = num(p?.actualInterest)
  if (stored !== null) return round2(stored)
  const redeemed = num(p?.redeemAmount)
  const principal = num(p?.principal)
  if (redeemed === null || principal === null) return null
  return round2(redeemed - principal)
}

/** 实际收益率(小数,0.029 = 2.9%);算不出 → null */
export function actualRate(p) {
  const interest = actualInterest(p)
  const principal = num(p?.principal)
  if (interest === null || !principal || principal <= 0) return null
  return interest / principal
}

/**
 * 实际年化(%):按「赎回日(未填则到期日)」算真实持有天数折年,基准天数取 dayCount。
 * 持有天数为 0 或算不出 → null(不产生 Infinity)。
 */
export function actualAnnualized(p) {
  const rate = actualRate(p)
  if (rate === null) return null
  const end = p.redeemDate || endDateOf(p)
  if (!p.startDate || !end) return null
  const days = daysBetween(p.startDate, end)
  if (days <= 0) return null
  return round2((rate * dayCountOf(p) * 100) / days)
}

/**
 * 加权年化(%):只统计「持仓中(含已到期待赎回)且填了年化」的产品,
 * Σ(本金 × 年化) / Σ 本金;没有可统计的本金 → null。
 */
export function weightedRate(list) {
  let principal = 0
  let weighted = 0
  for (const p of list || []) {
    if (!p) continue
    const status = derivedStatus(p)
    if (status !== 'HOLDING' && status !== 'MATURED') continue
    const amount = num(p.principal) || 0
    const rate = num(p.annualRate)
    if (amount <= 0 || rate === null) continue
    principal += amount
    weighted += amount * rate
  }
  if (principal <= 0) return null
  return Math.round((weighted / principal) * 10000) / 10000
}

/**
 * 持仓概览:
 * - holdingPrincipal 持仓本金(持有中 + 已到期待赎回)
 * - accrued 已计提收益 / expected 到期预期收益(浮动收益产品不计入,避免 NaN)
 * - realized 已实现收益(已赎回的合计,含亏损)
 * - weightedRate 加权年化(%)、holdingCount 持仓笔数、dueSoonCount 30 天内到期笔数
 * - byType 按类型占比(本金口径,合计为 1;无持仓时为 0)
 */
export function summarizePortfolio(list, today = new Date()) {
  const rows = Array.isArray(list) ? list : []
  let holdingPrincipal = 0
  let accrued = 0
  let expected = 0
  let realized = 0
  let holdingCount = 0
  let dueSoonCount = 0
  const byType = new Map()

  for (const p of rows) {
    if (!p) continue
    const status = derivedStatus(p, today)
    const principal = num(p.principal) || 0
    if (status === 'REDEEMED') {
      realized += toCents(actualInterest(p))
      continue
    }
    if (status !== 'HOLDING' && status !== 'MATURED') continue
    holdingCount++
    holdingPrincipal += toCents(principal)
    const acc = accruedInterest(p, today)
    if (acc !== null) accrued += toCents(acc)
    const exp = expectedInterest(p)
    if (exp !== null) expected += toCents(exp)
    const left = remainingDays(p, today)
    if (left !== null && left >= 0 && left <= 30) dueSoonCount++
    const type = p.productType || 'OTHER'
    byType.set(type, (byType.get(type) || 0) + toCents(principal))
  }

  const totalCents = holdingPrincipal
  return {
    holdingPrincipal: toYuan(holdingPrincipal),
    holdingCount,
    accrued: toYuan(accrued),
    expected: toYuan(expected),
    realized: toYuan(realized),
    weightedRate: weightedRate(rows),
    dueSoonCount,
    byType: [...byType.entries()]
      .map(([type, cents]) => ({
        type,
        principal: toYuan(cents),
        ratio: totalCents > 0 ? Math.round((cents / totalCents) * 10000) / 10000 : 0,
      }))
      .sort((a, b) => b.principal - a.principal),
  }
}

/** 未来 N 天内到期的持仓(按到期日升序),供到期日历横排 */
export function upcomingMaturities(list, today = new Date(), windowDays = 90) {
  const now = todayStr(today)
  return (Array.isArray(list) ? list : [])
    .map((p) => ({ product: p, end: endDateOf(p), left: remainingDays(p, today) }))
    .filter((x) => {
      if (!x.end || x.left === null) return false
      const status = derivedStatus(x.product, today)
      return (status === 'HOLDING' || status === 'MATURED') && x.left >= 0 && x.left <= windowDays
    })
    .sort((a, b) => a.end.localeCompare(b.end))
    .map((x) => ({
      id: x.product.id,
      name: x.product.name,
      productType: x.product.productType,
      endDate: x.end,
      daysLeft: x.left,
      principal: num(x.product.principal) || 0,
      overdue: x.end < now,
    }))
}
