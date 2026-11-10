// 相册自动备份的纯口径:窗口起点、游标、备份相册标识、自动触发闸门。
// 单测锁这里的规则,真机只验最终手感(见 docs/daily/2026-11-10.md 的 B1 小节)。
export const DEFAULT_DAYS = 90
/** 回到前台后多久内不重复自动备份 */
export const AUTO_GAP_MS = 10 * 60 * 1000
export const BACKUP_KEY_PREFIX = 'android:'
/** 与服务端 AlbumService.BACKUP_KEY / AlbumBackupDTO 同口径 */
const DEVICE_ID_RE = /^[A-Za-z0-9_-]{1,56}$/
const DAY_MS = 24 * 3600 * 1000

/** 首次备份的窗口起点(毫秒);「全部备份」传 full=true 时不用这个值 */
export function backupWindowStart(now, days = DEFAULT_DAYS) {
  const base = Number(now)
  const span = Number(days)
  return Math.max(0, (Number.isFinite(base) ? base : 0) - (Number.isFinite(span) ? span : DEFAULT_DAYS) * DAY_MS)
}

export function isValidDeviceId(id) {
  return typeof id === 'string' && DEVICE_ID_RE.test(id)
}

/** 设备标识 → 服务端备份相册键;非法标识返回空串,调用方应先修好标识再调 */
export function makeBackupKey(deviceId) {
  return isValidDeviceId(deviceId) ? BACKUP_KEY_PREFIX + deviceId : ''
}

/** 生成设备标识;random/now 注入只为单测可复现,生产用默认参数 */
export function generateDeviceId(random = Math.random, now = Date.now()) {
  const rnd = Number(random())
  const seed = (Number.isFinite(rnd) ? rnd : 0).toString(36).slice(2, 10)
  const stamp = (Number.isFinite(Number(now)) ? Number(now) : 0).toString(36)
  return (seed + stamp).slice(0, 56)
}

/** 游标必须两个键都为正整数才算数(否则视为没游标,从窗口起点重扫) */
export function normalizeCursor(raw) {
  if (!raw || typeof raw !== 'object') return null
  const d = Number(raw.d)
  const i = Number(raw.i)
  if (!Number.isFinite(d) || d <= 0) return null
  if (!Number.isFinite(i) || i <= 0) return null
  return { d, i }
}

/** 待备份张数的统计起点:有游标从游标起,否则从「近 90 天」起 */
export function pendingSinceMs(cursor, now, days = DEFAULT_DAYS) {
  return cursor ? cursor.d : backupWindowStart(now, days)
}

/**
 * 「仅 Wi-Fi / 仅充电」闸门:返回不能开始的原因(与界面 phase 同名),空串=可以开始。
 * 手动点「立即备份」也走这里——开关语义是「只在满足条件时备份」,不是只管自动。
 */
export function backupBlockedReason({ wifiOnly, wifi, chargeOnly, charging } = {}) {
  if (wifiOnly && !wifi) return 'network'
  if (chargeOnly && !charging) return 'charging'
  return ''
}

/** 回前台/登录恢复时是否该自动跑一次(开关开、已登录、距上次触发够久) */
export function shouldAutoBackup({ enabled, loggedIn, now, lastAutoAt, gapMs = AUTO_GAP_MS } = {}) {
  if (!enabled || !loggedIn) return false
  const n = Number(now)
  const last = Number(lastAutoAt)
  return (Number.isFinite(n) ? n : 0) - (Number.isFinite(last) ? last : 0) >= gapMs
}
