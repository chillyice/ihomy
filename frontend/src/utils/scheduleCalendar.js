// 月历网格纯函数:恒输出 6×7=42 格(含前后月补格),供 ScheduleCalendar 渲染。
// 不引任何日历/农历库:农历、节气、休班全部来自后端 /public/calendar。
// month 用 1-12(与接口、i18n 一致);weekStart 0=周日 / 1=周一(默认,中文习惯)。
// 全部按 UTC 计算,避免本地时区/夏令时让补格跨日。

const pad2 = (n) => String(n).padStart(2, '0')

export const ymd = (year, month, day) => `${year}-${pad2(month)}-${pad2(day)}`

/** 'YYYY-MM'(/public/calendar 与 query 的月份口径) */
export const monthKey = (year, month) => `${year}-${pad2(month)}`

/**
 * 生成 6×7 月历格。
 * @param {number} year
 * @param {number} month 1-12
 * @param {number} weekStart 0=周日 / 1=周一
 * @returns {Array<{date:string, day:number, inMonth:boolean, weekday:number}>}
 */
export function buildMonthGrid(year, month, weekStart = 1) {
  const first = new Date(Date.UTC(year, month - 1, 1))
  const offset = (first.getUTCDay() - weekStart + 7) % 7
  const start = Date.UTC(year, month - 1, 1 - offset)
  const cells = []
  for (let i = 0; i < 42; i++) {
    const d = new Date(start + i * 86400000)
    const y = d.getUTCFullYear()
    const m = d.getUTCMonth() + 1
    cells.push({
      date: ymd(y, m, d.getUTCDate()),
      day: d.getUTCDate(),
      inMonth: y === year && m === month,
      weekday: d.getUTCDay(),
    })
  }
  return cells
}

/** 月历区间(含补格):/schedule/events 的 from/to,42 天在 62 天上限内 */
export function monthRange(year, month, weekStart = 1) {
  const grid = buildMonthGrid(year, month, weekStart)
  return { from: grid[0].date, to: grid[grid.length - 1].date }
}

/** 前后翻月,自动跨年 */
export function shiftMonth(year, month, delta) {
  const d = new Date(Date.UTC(year, month - 1 + delta, 1))
  return { year: d.getUTCFullYear(), month: d.getUTCMonth() + 1 }
}

/** 周几表头(周一开头),值为 i18n 键后缀 schedule.weekday.<key> */
export const WEEKDAY_KEYS = ['mon', 'tue', 'wed', 'thu', 'fri', 'sat', 'sun']

/** 日期 → 周几键(按周一开头;纯字符串解析,不受时区影响) */
export function weekdayKey(dateStr) {
  const [y, m, d] = String(dateStr).split('-').map(Number)
  if (!y || !m || !d) return ''
  const jsDay = new Date(Date.UTC(y, m - 1, d)).getUTCDay()
  return WEEKDAY_KEYS[(jsDay + 6) % 7]
}

/** 本地今天(YYYY-MM-DD),不用 toISOString:那是 UTC,东八区凌晨会差一天 */
export function todayString(now = new Date()) {
  return ymd(now.getFullYear(), now.getMonth() + 1, now.getDate())
}

/** 是否合法 YYYY-MM-DD(旧链接参数校验) */
export function isDateString(value) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(String(value || ''))) return false
  const [y, m, d] = String(value).split('-').map(Number)
  const probe = new Date(Date.UTC(y, m - 1, d))
  return probe.getUTCFullYear() === y && probe.getUTCMonth() + 1 === m && probe.getUTCDate() === d
}

/** 日期加减天数(用于「每年」锚定日的下一年顺延) */
export function addDays(dateStr, days) {
  const [y, m, d] = String(dateStr).split('-').map(Number)
  const t = new Date(Date.UTC(y, m - 1, d) + days * 86400000)
  return ymd(t.getUTCFullYear(), t.getUTCMonth() + 1, t.getUTCDate())
}
