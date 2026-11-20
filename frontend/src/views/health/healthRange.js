// 健康页时间范围(纯函数):预设范围、区间收口、分桶决策、日期网格。
// 与后端窗口上限同口径:趋势/统计 ≤366 天,睡眠会话明细 ≤62 天。
// 日期一律用本地「正午」做基准,避免夏令时/时区把「今天」挪一天。

export const MAX_RANGE_DAYS = 366
export const SESSION_MAX_DAYS = 62
/** 点指标(心率/血氧/体温)跨度超过这个天数就按天聚合,免得几千个点糊成一条 */
export const POINT_BUCKET_DAYS = 2

/** 预设范围:今天 / 7 / 14 / 30 / 90 / 365 天(文案走 health.trend.today / range) */
export const RANGE_PRESETS = [
  { key: 'today', days: 1 },
  { key: 'd7', days: 7 },
  { key: 'd14', days: 14 },
  { key: 'd30', days: 30 },
  { key: 'd90', days: 90 },
  { key: 'd365', days: 365 },
]

const nowNoon = () => {
  const d = new Date()
  d.setHours(12, 0, 0, 0)
  return d
}

const noon = (value) => {
  if (value instanceof Date) {
    const d = new Date(value)
    d.setHours(12, 0, 0, 0)
    return d
  }
  const iso = String(value || '').slice(0, 10)
  const [y, m, d] = iso.split('-').map(Number)
  // 空串/缺年月会解析成 0 或 NaN:直接当「今天」,否则会悄悄落到 1900-01-01
  // (presetRange(days) 不传 today 时必须走这里)
  if (!Number.isFinite(y) || y === 0 || !Number.isFinite(m) || m === 0) return nowNoon()
  return new Date(y, m - 1, Number.isFinite(d) && d > 0 ? d : 1, 12)
}

/** Date / ISO 日期 → YYYY-MM-DD */
export function isoDay(value) {
  const d = noon(value)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

/** 相对某天前后移动 offset 天(默认以今天为准) */
export function shiftDay(offset, base) {
  const d = noon(base)
  d.setDate(d.getDate() + offset)
  return isoDay(d)
}

/** 区间含首尾共几天(from > to 时按交换后算) */
export function spanDays(from, to) {
  const a = noon(from)
  const b = noon(to)
  const diff = Math.round((Math.max(a.getTime(), b.getTime()) - Math.min(a.getTime(), b.getTime())) / 86400000)
  return diff + 1
}

/** 最近 days 天(含今天):days=1 就是今天 */
export function presetRange(days, today) {
  const to = isoDay(today)
  return { from: shiftDay(-(Math.max(1, days) - 1), to), to }
}

/**
 * 自定义区间收口:交换颠倒、未来收口到今天、超 366 天往前截。
 * 缺一端就用另一端兜底;都缺回落近 14 天。clamped=true 时页面提示「已收口」。
 */
export function clampRange(from, to, today) {
  let a = String(from || '').slice(0, 10)
  let b = String(to || '').slice(0, 10)
  const now = isoDay(today)
  if (!a && !b) return { ...presetRange(14, now), clamped: false }
  if (!a) a = b
  if (!b) b = a
  if (a > b) [a, b] = [b, a]
  let clamped = false
  if (b > now) {
    b = now
    clamped = true
  }
  if (a > b) {
    a = b
    clamped = true
  }
  if (spanDays(a, b) > MAX_RANGE_DAYS) {
    a = shiftDay(-(MAX_RANGE_DAYS - 1), b)
    clamped = true
  }
  return { from: a, to: b, clamped }
}

/** 分桶:逐点指标跨度 > 2 天走日聚合,其余(按天/按周数据)本来一天一行 */
export function bucketFor(period, from, to) {
  if (period !== 'POINT') return 'DAY'
  return spanDays(from, to) > POINT_BUCKET_DAYS ? 'DAY' : 'POINT'
}

/** 日期网格(含首尾,YYYY-MM-DD),缺数据的那天由调用方补 null */
export function dateGrid(from, to) {
  const out = []
  const start = isoDay(from)
  const end = isoDay(to)
  if (start > end) return out
  const count = spanDays(start, end)
  for (let i = 0; i < count; i++) out.push(shiftDay(i, start))
  return out
}
