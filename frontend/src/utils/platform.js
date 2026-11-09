// 设备平台判定:iOS / iPadOS 专用分支(如「全屏下禁止键入」)使用。
// iPadOS 13+ 的 Safari 把 UA 伪装成 Macintosh,只能再用触摸点数与真 Mac 区分。
export function isIOSUA(ua = '', maxTouchPoints = 0) {
  const agent = String(ua || '')
  if (/iPad|iPhone|iPod/i.test(agent)) return true
  return /Mac/i.test(agent) && maxTouchPoints > 1
}

export function isIOS() {
  if (typeof navigator === 'undefined') return false
  return isIOSUA(navigator.userAgent, navigator.maxTouchPoints)
}

/**
 * 是否运行在 Capacitor 原生壳内。
 * 只读注入的全局桥(window.Capacitor),不 import @capacitor/core —— 保证 Web 首屏零原生运行时开销。
 */
export function isNativeApp() {
  if (typeof window === 'undefined') return false
  const cap = window.Capacitor
  if (!cap) return false
  if (typeof cap.isNativePlatform === 'function') return cap.isNativePlatform()
  return cap.platform === 'android' || cap.platform === 'ios'
}

/** 原生平台名(android / ios / web) */
export function nativePlatform() {
  if (typeof window === 'undefined') return 'web'
  const cap = window.Capacitor
  if (cap && typeof cap.getPlatform === 'function') return cap.getPlatform()
  return cap?.platform || 'web'
}

/** 是否 Android 壳(调用 Android 专有行为前先判;iOS 未实现) */
export function isAndroidApp() {
  return isNativeApp() && nativePlatform() === 'android'
}

/** Web 端版本号(构建时由 VERSION 注入);原生壳内请用 native/version.js 的 getAppVersion */
export function getWebVersion() {
  return typeof __APP_VERSION__ === 'undefined' ? '' : __APP_VERSION__
}
