// 触觉反馈统一出口:全站只从这里取「震一下」,同类 80ms 内合并,总开关存本机。
// 语义只有五类(与 HapticsPlugin 一一对应),禁止给滚动/hover/每次普通点击加震动。
import { isAndroidApp } from '@/utils/platform'

export const HAPTICS_KEY = 'ihomy:haptics:v1'
const THROTTLE_MS = 80
const KINDS = new Set(['tap', 'select', 'success', 'warning', 'error'])

let lastKind = ''
let lastAt = 0

export function hapticsEnabled() {
  try {
    return localStorage.getItem(HAPTICS_KEY) !== '0'
  } catch (e) {
    return true
  }
}

export function setHapticsEnabled(on) {
  try {
    localStorage.setItem(HAPTICS_KEY, on ? '1' : '0')
  } catch (e) {}
}

/**
 * 触发一次触觉反馈。
 * @param {'tap'|'select'|'success'|'warning'|'error'} kind
 */
export function haptic(kind = 'tap') {
  if (!KINDS.has(kind) || !hapticsEnabled() || !isAndroidApp()) return
  const now = Date.now()
  if (kind === lastKind && now - lastAt < THROTTLE_MS) return
  lastKind = kind
  lastAt = now
  import('@/native/haptics').then((m) => m.play(kind)).catch(() => {})
}
