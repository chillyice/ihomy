// 系统日历单向导出(仅原生壳):把服务端算好的提醒/纪念日/还款日写进本机「ihomy」日历。
// 目的不是替代 App 内提醒,而是兜底——鸿蒙/国产系统杀后台时,系统日历的闹钟仍会响。
// 日期时刻全部来自 /schedule/occurrences(客户端零历法),本模块只做形状转换与幂等调用。
import { registerPlugin } from '@capacitor/core'
import { scheduleApi } from '@/api'
import { useUserStore } from '@/stores/user'
import i18n from '@/i18n'

const IhomyCalendar = registerPlugin('IhomyCalendar')

const LAST_KEY = 'ihomy:calendar:lastExport'
/** 导出窗口(服务端上限 92 天) */
export const EXPORT_DAYS = 90
/** 进日历的 kind:提醒/纪念日/计划/还款日/理财到期;食材到期不进(太碎,日历会被淹没) */
const EXPORT_KINDS = new Set(['REMINDER', 'ANNIVERSARY', 'PLAN', 'LOAN', 'FINANCE'])
const KIND_LABEL_KEY = {
  REMINDER: 'appSettings.kindReminder',
  ANNIVERSARY: 'appSettings.kindAnniversary',
  PLAN: 'appSettings.kindPlan',
  LOAN: 'appSettings.kindLoan',
  FINANCE: 'appSettings.kindFinance',
}

function dateStr(d) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

/** 上次导出记录(本地缓存,只用于展示;导出本身幂等,不依赖它) */
export function readLastExport() {
  try {
    const raw = localStorage.getItem(LAST_KEY)
    const v = raw ? JSON.parse(raw) : null
    return v && typeof v.at === 'number' ? v : null
  } catch {
    return null
  }
}

/** 日历导出状态:权限 + 日历内事件数 + 上次导出记录 */
export async function calendarStatus() {
  const perm = await IhomyCalendar.checkPermission()
  const granted = perm && perm.state === 'granted'
  let cal = { calendarId: null, count: 0 }
  if (granted) {
    cal = await IhomyCalendar.status().catch(() => cal)
  }
  return {
    granted,
    calendarId: cal && cal.calendarId ? cal.calendarId : null,
    count: (cal && cal.count) || 0,
    last: readLastExport(),
  }
}

/** 请求日历权限;已被永久拒绝时返回 false,由调用方给系统设置指路 */
export async function requestCalendarPermission() {
  const r = await IhomyCalendar.requestPermission().catch(() => null)
  return !!r && r.state === 'granted'
}

function buildDescription(item) {
  const t = i18n.global.t
  const parts = [t('appSettings.calendarEventSource')]
  const labelKey = KIND_LABEL_KEY[item.kind]
  if (labelKey) parts.push(t(labelKey))
  if (item.repeat && item.repeat !== 'NONE') parts.push(t('appSettings.calendarRepeating'))
  return parts.join(' · ')
}

/** 同一接口的 link 变成自定义 scheme,日历事件点开可直接进 App 对应页 */
function deepLinkOf(item) {
  const link = item.link || (item.kind === 'ANNIVERSARY' ? '/anniversary' : `/plan?date=${item.date}`)
  return `ihomy://${String(link).replace(/^\/+/, '')}`
}

/** occurrences → 原生导出入参(按 key 去重,全部字段原样取自服务端) */
export function buildExportItems(items, timeZone) {
  const out = []
  const seen = new Set()
  for (const it of items || []) {
    if (!it || !it.key || it.done) continue
    if (!EXPORT_KINDS.has(it.kind)) continue
    if (seen.has(it.key)) continue
    seen.add(it.key)
    out.push({
      key: it.key,
      title: it.title || '',
      description: buildDescription(it),
      date: it.date,
      time: it.time || null,
      timeZone: it.timeZone || timeZone || '',
      link: deepLinkOf(it),
    })
  }
  return out
}

/**
 * 导出未来 N 天到系统日历(全量覆盖本 App 专属日历,幂等)。
 * 返回 {inserted, removed, skipped, days};权限被拒抛 Error('permission')。
 */
export async function exportToCalendar({ days = EXPORT_DAYS } = {}) {
  const userStore = useUserStore()
  if (!userStore.isLoggedIn) throw new Error('guest')

  const status = await calendarStatus()
  if (!status.granted) {
    const ok = await requestCalendarPermission()
    if (!ok) throw new Error('permission')
  }

  const from = new Date()
  const to = new Date()
  to.setDate(to.getDate() + days - 1)
  const res = await scheduleApi.occurrences(dateStr(from), dateStr(to))
  const items = buildExportItems((res && res.items) || [], res && res.timeZone)

  const out = await IhomyCalendar.export({
    calendarName: i18n.global.t('appSettings.calendarName'),
    items,
  })
  localStorage.setItem(LAST_KEY, JSON.stringify({ at: Date.now(), inserted: out.inserted || 0, days, truncated: !!(res && res.truncated) }))
  return { ...out, days, truncated: !!(res && res.truncated) }
}

/** 清空本 App 日历里的事件(日历本身保留) */
export async function clearCalendar() {
  const out = await IhomyCalendar.clear().catch((e) => {
    if (String(e && e.message).includes('PERMISSION_DENIED')) throw new Error('permission')
    throw e
  })
  localStorage.removeItem(LAST_KEY)
  return out
}
