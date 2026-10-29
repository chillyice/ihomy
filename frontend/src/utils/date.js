// 日期工具:全站只按年月日算,不受时区/时分秒影响('YYYY-MM-DD' 字符串为主)。
// 贷款(utils/loan.js)与理财(utils/finance.js)共用;loan.js 仍再导出 parseDate/daysBetween,
// 历史调用方无需改动。

/** 'YYYY-MM-DD' 或 Date → { y, m, d };解析失败返回 null */
export function parseDate(value) {
  if (value instanceof Date) {
    return Number.isNaN(value.getTime()) ? null : { y: value.getFullYear(), m: value.getMonth() + 1, d: value.getDate() }
  }
  const m = /^(\d{4})-(\d{1,2})-(\d{1,2})/.exec(String(value || ''))
  return m ? { y: Number(m[1]), m: Number(m[2]), d: Number(m[3]) } : null
}

/** 两位补零 */
const pad = (n) => String(n).padStart(2, '0')

/** {y,m,d} → 'YYYY-MM-DD';非法返回 null */
function fmt({ y, m, d }) {
  return `${y}-${pad(m)}-${pad(d)}`
}

/** Date / 日期字符串 → 'YYYY-MM-DD';解析失败返回 '' */
export function toDateStr(value) {
  const d = parseDate(value)
  return d ? fmt(d) : ''
}

/** 今天(或指定时刻)的 'YYYY-MM-DD' */
export function todayStr(today = new Date()) {
  return toDateStr(today)
}

/** 某年某月的天数(处理闰年) */
function daysInMonth(year, month) {
  return new Date(Date.UTC(year, month, 0)).getUTCDate()
}

/**
 * 两个日期之间的天数(算头不算尾)—— 放款日 → 首期还款日、起息日 → 到期日都按它算。
 * 只按年月日算,不受时区/时分秒影响;解析失败返回 0。
 */
export function daysBetween(from, to) {
  const a = parseDate(from)
  const b = parseDate(to)
  if (!a || !b) return 0
  return Math.round((Date.UTC(b.y, b.m - 1, b.d) - Date.UTC(a.y, a.m - 1, a.d)) / 86400000)
}

/**
 * 日期加月数(与 Java LocalDate.plusMonths 一致:月末溢出时收拢到当月最后一天,
 * 2024-01-31 + 1 月 = 2024-02-29)。解析失败返回 null。
 */
export function addMonths(value, months) {
  const d = parseDate(value)
  if (!d) return null
  const total = d.y * 12 + (d.m - 1) + Math.round(Number(months) || 0)
  const y = Math.floor(total / 12)
  const m = (((total % 12) + 12) % 12) + 1
  return fmt({ y, m, d: Math.min(d.d, daysInMonth(y, m)) })
}

/**
 * 两个日期之间的整月数(不足一个月不计):用于「按月折算」但没有填期限时倒推月数。
 * 返回 >= 0;解析失败返回 0。
 */
export function monthDiff(from, to) {
  const a = parseDate(from)
  const b = parseDate(to)
  if (!a || !b) return 0
  let months = (b.y - a.y) * 12 + (b.m - a.m)
  if (b.d < a.d) months -= 1
  return Math.max(0, months)
}
