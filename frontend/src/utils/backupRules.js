// 备份策略与展示的纯函数:UI 只负责把时间控件/数字映射到 cron 与保留份数。
// 后端策略是所有校验的唯一事实来源,这里只做「能解析就回显、不能解析就保留原文」的轻量转换。

const DAILY = /^0\s+(\d{1,2})\s+(\d{1,2})\s+\*\s+\*\s+\*$/
const WEEKLY = /^0\s+(\d{1,2})\s+(\d{1,2})\s+\*\s+\*\s+(\d)$/

const pad = (n) => String(n).padStart(2, '0')

/** 每天 HH:mm → Spring 六段 cron */
export function dailyCron(hour, minute) {
  return `0 ${Number(minute) || 0} ${Number(hour) || 0} * * *`
}

/** 每周几 HH:mm(dow 0=周日) → cron */
export function weeklyCron(dow, hour, minute) {
  return `0 ${Number(minute) || 0} ${Number(hour) || 0} * * ${Number(dow) || 0}`
}

/** 解析每天 cron;不是「每天」形状返回 null */
export function parseDailyCron(cron) {
  const m = DAILY.exec(String(cron || '').trim())
  if (!m) return null
  // cron 六段式的字段顺序是 秒 分 时,前面固定 0 秒,所以第一组是分、第二组是时
  const minute = Number(m[1])
  const hour = Number(m[2])
  if (hour > 23 || minute > 59) return null
  return { hour, minute, text: `${pad(hour)}:${pad(minute)}` }
}

/** 解析每周 cron;不是「每周」形状返回 null */
export function parseWeeklyCron(cron) {
  const m = WEEKLY.exec(String(cron || '').trim())
  if (!m) return null
  const minute = Number(m[1])
  const hour = Number(m[2])
  const dow = Number(m[3])
  if (hour > 23 || minute > 59 || dow > 6) return null
  return { dow, hour, minute, text: `${pad(hour)}:${pad(minute)}` }
}

/** 用户可读体积(备份列表/概览) */
export function formatBytes(bytes) {
  const n = Number(bytes)
  if (!Number.isFinite(n) || n <= 0) return '0 B'
  if (n < 1024) return `${Math.round(n)} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  if (n < 1024 * 1024 * 1024) return `${(n / 1024 / 1024).toFixed(1)} MB`
  return `${(n / 1024 / 1024 / 1024).toFixed(2)} GB`
}

/** 毫秒 → 本地化耗时文案(单位由 i18n 提供,英文站点不残留中文) */
export function durationText(ms, t) {
  const n = Number(ms)
  if (!Number.isFinite(n) || n <= 0) return '—'
  const sec = Math.round(n / 1000)
  if (sec < 60) return t('ops.backup.durSecond', { n: sec })
  const min = Math.floor(sec / 60)
  if (min < 60) return t('ops.backup.durMinute', { m: min, s: sec % 60 })
  const hour = Math.floor(min / 60)
  return t('ops.backup.durHour', { h: hour, m: min % 60 })
}

/** 备份文件/数据库大小的策略摘要数据(文案交给 i18n) */
export function retentionPlan(policy) {
  const r = policy?.retention || {}
  return {
    basic: { daily: r.basicDaily ?? 0, weekly: r.basicWeekly ?? 0, monthly: r.basicMonthly ?? 0 },
    content: { daily: r.contentDaily ?? 0, weekly: r.contentWeekly ?? 0, monthly: r.contentMonthly ?? 0 },
    full: { weekly: r.fullWeekly ?? 0, monthly: r.fullMonthly ?? 0 },
    family: { days: r.familyDays ?? 0, keep: r.familyKeep ?? 0 },
  }
}
