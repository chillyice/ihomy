// 健康设置页的入口形态(纯函数,便于单测):安卓壳 / 安卓浏览器 / iOS / 其他。
// 设置页按这个分支决定「连接」段展示哪种方法:iOS 与安卓互不展示对方的方法;
// 桌面浏览器(含平板)不展示连接段 —— 那边既没有 Health Connect 也生成不了快捷指令。
import { isIOSUA } from './platform'

export const HEALTH_ENTRY = {
  ANDROID_APP: 'android-app',
  ANDROID_WEB: 'android-web',
  IOS: 'ios',
  OTHER: 'other',
}

/** Android UA(不含 iOS);Capacitor 壳内 WebView 的 UA 也带 Android,由 isNative 分支先拦掉 */
export function isAndroidUA(ua = '') {
  return /Android/i.test(String(ua || ''))
}

/**
 * @param {{isNative?:boolean, nativePlatform?:string, ua?:string, maxTouchPoints?:number}} env
 * @return {'android-app'|'android-web'|'ios'|'other'}
 */
export function healthEntryKind({ isNative = false, nativePlatform = 'web', ua = '', maxTouchPoints = 0 } = {}) {
  if (isNative) {
    if (nativePlatform === 'android') return HEALTH_ENTRY.ANDROID_APP
    if (nativePlatform === 'ios') return HEALTH_ENTRY.IOS
    return HEALTH_ENTRY.OTHER
  }
  if (isAndroidUA(ua)) return HEALTH_ENTRY.ANDROID_WEB
  if (isIOSUA(ua, maxTouchPoints)) return HEALTH_ENTRY.IOS
  return HEALTH_ENTRY.OTHER
}
