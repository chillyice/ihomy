// 备份策略展示的纯函数:周期/保留/模块文案由这里统一拼装,页面只渲染。
// 时间与周期在后端校验(cron 的唯一事实来源是 sys_backup_policy),这里只负责本地化展示。

const pad = (n) => String(n).padStart(2, '0')

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

/** 每周/每月下拉:周一=1 … 周日=7;每月 1–28 */
export function scheduleDays(type) {
  if (type === 'WEEKLY') return [1, 2, 3, 4, 5, 6, 7]
  if (type === 'MONTHLY') return Array.from({ length: 28 }, (_, i) => i + 1)
  return []
}

/** 策略周期摘要;缺字段时回退到「每天」 */
export function scheduleText(policy, t) {
  const time = String(policy?.scheduleTime || '03:30')
  const type = policy?.scheduleType || 'DAILY'
  if (type === 'WEEKLY') {
    const day = Number(policy?.scheduleDay) || 7
    return t('ops.backup.everyWeekAt', { day: t(`ops.backup.weekday.${day}`), time })
  }
  if (type === 'MONTHLY') {
    const day = Number(policy?.scheduleDay) || 1
    return t('ops.backup.everyMonthAt', { day, time })
  }
  return t('ops.backup.everyDayAt', { time })
}

/** 保留天数摘要;留空 = 不自动清理 */
export function retentionText(days, t) {
  const n = Number(days)
  if (!Number.isFinite(n) || n <= 0) return t('ops.backup.keepForever')
  return t('ops.backup.keepDays', { n })
}

/** 模块码 → 本地化名称;未知码原样显示,避免静默丢失 */
export function moduleText(code, t) {
  const key = `ops.backup.module.${String(code || '').toLowerCase()}`
  const text = t(key)
  return text === key ? String(code || '') : text
}

/** 时间显示补齐两位(策略列表/表单) */
export function timeText(time) {
  const m = /^(\d{1,2}):(\d{2})$/.exec(String(time || ''))
  if (!m) return String(time || '—')
  return `${pad(Number(m[1]))}:${m[2]}`
}
