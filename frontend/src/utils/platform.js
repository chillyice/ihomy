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

/** Android 官方平板线:`resources` 的 smallestScreenWidthDp ≥ 600(WebView 里 1 CSS px ≈ 1dp) */
export const TABLET_MIN_SHORT_SIDE = 600
/** 窄窗口线:与 main.css 的移动端断点保持一致 */
export const NARROW_MAX_WIDTH = 768
/** 宽屏线:与 main.css 的宽屏段 `@media (min-width: 900px)` 保持一致(≥900 才考虑双栏) */
export const WIDE_MIN_WIDTH = 900

/**
 * 视口形态判定(纯函数,方便测试):手机 / 平板 / 窄窗口。
 *
 * - `tablet`:iPad(含 iPadOS 13+ 伪装 Mac 的情况)或 Android 且屏幕短边 ≥ 600dp;
 * - `phone`:非平板的 iPhone / Android(短边过线的 Android 已归入平板);
 * - `narrow`:窗口宽度 ≤ 768px(桌面浏览器拉窄、平板分屏都算);
 * - `mobile`:**是否用移动端布局** = 手机 or 窄窗口 —— 平板按桌面端布局处理。
 *
 * 不用 UA 里的 `Mobile` 标记单独判平板:部分国产平板 UA 也带 Mobile;屏幕短边是系统口径。
 */
export function classifyViewport({ ua = '', maxTouchPoints = 0, screenWidth = 0, screenHeight = 0, innerWidth = 0 } = {}) {
  const agent = String(ua || '')
  const isIPhone = /iPhone|iPod/i.test(agent)
  // iPhone 的 UA 也含「like Mac OS X」,必须先排除 iPhone/iPod 再按「Mac + 多点触摸」认 iPadOS
  const isIPad = /iPad/i.test(agent) || (!isIPhone && /Mac/i.test(agent) && maxTouchPoints > 1)
  const isAndroid = /Android/i.test(agent)
  const shortSide = Math.min(screenWidth || innerWidth || 0, screenHeight || innerWidth || 0)
  const tablet = isIPad || (isAndroid && shortSide >= TABLET_MIN_SHORT_SIDE)
  const phone = !tablet && (isIPhone || isAndroid)
  const narrow = (innerWidth || 0) <= NARROW_MAX_WIDTH
  return { phone, tablet, narrow, mobile: phone || narrow }
}

/**
 * 宽屏判定(纯函数):窗口宽 ≥900 且屏幕短边 ≥600dp。
 *
 * 两个条件都要满足:
 * - 只看宽度会让「手机横屏」(如 873×393)也进双栏,短边一项把手机排除;
 * - 平板分屏把窗口压窄到 <900 时第一项不成立,自动退回单栏。
 *
 * 与 `classifyViewport` 分开:那个函数的返回值被 8 处 `toEqual` 全量断言,不加字段改语义。
 */
export function isWideViewport({ innerWidth = 0, screenWidth = 0, screenHeight = 0 } = {}) {
  const shortSide = Math.min(screenWidth || innerWidth || 0, screenHeight || innerWidth || 0)
  return (innerWidth || 0) >= WIDE_MIN_WIDTH && shortSide >= TABLET_MIN_SHORT_SIDE
}
