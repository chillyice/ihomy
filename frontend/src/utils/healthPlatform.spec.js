import { describe, expect, it } from 'vitest'
import { HEALTH_ENTRY, healthEntryKind, isAndroidUA } from './healthPlatform'

// 设置页按入口形态只展示本系统的方法:iOS 看不到 Health Connect,安卓看不到快捷指令。
const UA = {
  iphone: 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15',
  ipad: 'Mozilla/5.0 (iPad; CPU OS 17_0 like Mac OS X) AppleWebKit/605.1.15',
  android: 'Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36',
  mac: 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15',
  windows: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0 Safari/537.36',
}

describe('isAndroidUA', () => {
  it('只认 Android,不把 iOS / 桌面当安卓', () => {
    expect(isAndroidUA(UA.android)).toBe(true)
    expect(isAndroidUA(UA.iphone)).toBe(false)
    expect(isAndroidUA(UA.windows)).toBe(false)
    expect(isAndroidUA('')).toBe(false)
  })
})

describe('healthEntryKind', () => {
  it('原生壳按 nativePlatform 分支', () => {
    expect(healthEntryKind({ isNative: true, nativePlatform: 'android' })).toBe(HEALTH_ENTRY.ANDROID_APP)
    expect(healthEntryKind({ isNative: true, nativePlatform: 'ios' })).toBe(HEALTH_ENTRY.IOS)
    expect(healthEntryKind({ isNative: true, nativePlatform: 'web' })).toBe(HEALTH_ENTRY.OTHER)
  })

  it('浏览器:安卓给 android-web,iOS(含 iPadOS 伪装 Mac)给 ios', () => {
    expect(healthEntryKind({ ua: UA.android })).toBe(HEALTH_ENTRY.ANDROID_WEB)
    expect(healthEntryKind({ ua: UA.iphone })).toBe(HEALTH_ENTRY.IOS)
    expect(healthEntryKind({ ua: UA.ipad })).toBe(HEALTH_ENTRY.IOS)
    expect(healthEntryKind({ ua: UA.mac, maxTouchPoints: 5 })).toBe(HEALTH_ENTRY.IOS)
  })

  it('桌面浏览器不展示连接段(other)', () => {
    expect(healthEntryKind({ ua: UA.windows })).toBe(HEALTH_ENTRY.OTHER)
    expect(healthEntryKind({ ua: UA.mac, maxTouchPoints: 0 })).toBe(HEALTH_ENTRY.OTHER)
    expect(healthEntryKind()).toBe(HEALTH_ENTRY.OTHER)
  })
})
