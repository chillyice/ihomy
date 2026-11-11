import { describe, it, expect } from 'vitest'
import { classifyViewport, isIOSUA, isWideViewport, TABLET_MIN_SHORT_SIDE, NARROW_MAX_WIDTH, WIDE_MIN_WIDTH } from './platform'

describe('isIOSUA 平台识别', () => {
  it('iPhone / iPod 原生 UA', () => {
    expect(isIOSUA('Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)', 5)).toBe(true)
    expect(isIOSUA('Mozilla/5.0 (iPod touch; CPU iPhone OS 15_0 like Mac OS X)', 5)).toBe(true)
  })

  it('iPad 原生 UA', () => {
    expect(isIOSUA('Mozilla/5.0 (iPad; CPU OS 16_0 like Mac OS X)', 5)).toBe(true)
  })

  it('iPadOS 13+ 伪装成 Mac,靠多点触摸识别', () => {
    expect(isIOSUA('Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)', 5)).toBe(true)
  })

  it('真 Mac(无触摸点)不算 iOS', () => {
    expect(isIOSUA('Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)', 0)).toBe(false)
  })

  it('Android / Windows 不算 iOS', () => {
    expect(isIOSUA('Mozilla/5.0 (Linux; Android 14; Pixel 8)', 5)).toBe(false)
    expect(isIOSUA('Mozilla/5.0 (Windows NT 10.0; Win64; x64)', 0)).toBe(false)
  })
})

const UA_PHONE_ANDROID = 'Mozilla/5.0 (Linux; Android 14; V2227A) AppleWebKit/537.36 Mobile Safari/537.36'
const UA_TABLET_ANDROID = 'Mozilla/5.0 (Linux; Android 12; HUAWEI M6) AppleWebKit/537.36 Safari/537.36'
const UA_IPAD = 'Mozilla/5.0 (iPad; CPU OS 16_0 like Mac OS X) AppleWebKit/605.1.15'
const UA_DESKTOP = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120'

describe('classifyViewport 手机 / 平板 / 窄窗口判定', () => {
  it('常量与 Android 官方平板线一致', () => {
    expect(TABLET_MIN_SHORT_SIDE).toBe(600)
    expect(NARROW_MAX_WIDTH).toBe(768)
  })

  it('手机竖屏:移动端布局', () => {
    const d = classifyViewport({
      ua: UA_PHONE_ANDROID,
      maxTouchPoints: 5,
      screenWidth: 393,
      screenHeight: 873,
      innerWidth: 393,
    })
    expect(d).toEqual({ phone: true, tablet: false, narrow: true, mobile: true })
  })

  it('手机横屏:宽度过窄窗线但仍是手机,继续走移动端布局', () => {
    const d = classifyViewport({
      ua: UA_PHONE_ANDROID,
      maxTouchPoints: 5,
      screenWidth: 873,
      screenHeight: 393,
      innerWidth: 873,
    })
    expect(d).toEqual({ phone: true, tablet: false, narrow: false, mobile: true })
  })

  it('华为平板(短边 ≥600dp,UA 无 Mobile 标记):桌面端布局', () => {
    const d = classifyViewport({
      ua: UA_TABLET_ANDROID,
      maxTouchPoints: 10,
      screenWidth: 1066,
      screenHeight: 666,
      innerWidth: 1066,
    })
    expect(d).toEqual({ phone: false, tablet: true, narrow: false, mobile: false })
  })

  it('国产平板 UA 带 Mobile 标记也不影响,仍按短边判平板', () => {
    const d = classifyViewport({
      ua: 'Mozilla/5.0 (Linux; Android 11; Lenovo TB-J606F) AppleWebKit/537.36 Mobile Safari/537.36',
      maxTouchPoints: 10,
      screenWidth: 800,
      screenHeight: 1280,
      innerWidth: 800,
    })
    expect(d.tablet).toBe(true)
    expect(d.mobile).toBe(false)
  })

  it('iPad:桌面端布局', () => {
    const d = classifyViewport({
      ua: UA_IPAD,
      maxTouchPoints: 5,
      screenWidth: 820,
      screenHeight: 1180,
      innerWidth: 820,
    })
    expect(d).toEqual({ phone: false, tablet: true, narrow: false, mobile: false })
  })

  it('iPhone 的 UA 也含「like Mac OS X」,不能被当成 iPad', () => {
    const d = classifyViewport({
      ua: 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15',
      maxTouchPoints: 5,
      screenWidth: 393,
      screenHeight: 852,
      innerWidth: 393,
    })
    expect(d).toEqual({ phone: true, tablet: false, narrow: true, mobile: true })
  })

  it('iPadOS 13+ 伪装成 Mac(有多点触摸):按平板处理', () => {
    const d = classifyViewport({
      ua: 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15',
      maxTouchPoints: 5,
      screenWidth: 1024,
      screenHeight: 1366,
      innerWidth: 1024,
    })
    expect(d.tablet).toBe(true)
    expect(d.mobile).toBe(false)
  })

  it('平板分屏(窗口 ≤768px):退回移动端布局', () => {
    const d = classifyViewport({
      ua: UA_TABLET_ANDROID,
      maxTouchPoints: 10,
      screenWidth: 1066,
      screenHeight: 666,
      innerWidth: 600,
    })
    expect(d).toEqual({ phone: false, tablet: true, narrow: true, mobile: true })
  })

  it('桌面浏览器:桌面端布局', () => {
    const d = classifyViewport({
      ua: UA_DESKTOP,
      maxTouchPoints: 0,
      screenWidth: 1920,
      screenHeight: 1080,
      innerWidth: 1440,
    })
    expect(d).toEqual({ phone: false, tablet: false, narrow: false, mobile: false })
  })

  it('桌面浏览器拉窄到 ≤768px:移动端布局(与旧行为一致)', () => {
    const d = classifyViewport({
      ua: UA_DESKTOP,
      maxTouchPoints: 0,
      screenWidth: 1920,
      screenHeight: 1080,
      innerWidth: 768,
    })
    expect(d).toEqual({ phone: false, tablet: false, narrow: true, mobile: true })
  })

  it('缺参数(SSR / 老 WebView)不抛错', () => {
    expect(() => classifyViewport()).not.toThrow()
    expect(() => classifyViewport({})).not.toThrow()
  })
})

describe('isWideViewport 宽屏判定', () => {
  it('常量与 main.css 的宽屏段一致', () => {
    expect(WIDE_MIN_WIDTH).toBe(900)
  })

  it('10 寸平板横屏(1024×768):宽屏', () => {
    expect(isWideViewport({ innerWidth: 1024, screenWidth: 1024, screenHeight: 768 })).toBe(true)
  })

  it('手机横屏(873×393):宽度过线但短边不足,不算宽屏', () => {
    expect(isWideViewport({ innerWidth: 873, screenWidth: 873, screenHeight: 393 })).toBe(false)
  })

  it('手机竖屏(393×873):宽度不足,不算宽屏', () => {
    expect(isWideViewport({ innerWidth: 393, screenWidth: 393, screenHeight: 873 })).toBe(false)
  })

  it('900px 边界:刚好算宽屏;899px 回单栏', () => {
    expect(isWideViewport({ innerWidth: 900, screenWidth: 1440, screenHeight: 900 })).toBe(true)
    expect(isWideViewport({ innerWidth: 899, screenWidth: 1440, screenHeight: 900 })).toBe(false)
  })

  it('平板分屏压窄到 875px:自动退出宽屏(短边仍过线)', () => {
    expect(isWideViewport({ innerWidth: 875, screenWidth: 1066, screenHeight: 666 })).toBe(false)
  })

  it('桌面 1440×900:宽屏', () => {
    expect(isWideViewport({ innerWidth: 1440, screenWidth: 1920, screenHeight: 1080 })).toBe(true)
  })

  it('拿不到 screen 尺寸时按窗口宽补:窗口够宽仍算宽屏', () => {
    expect(isWideViewport({ innerWidth: 1280 })).toBe(true)
  })

  it('缺参数(SSR / 老 WebView)不抛错,按非宽屏处理', () => {
    expect(() => isWideViewport()).not.toThrow()
    expect(isWideViewport()).toBe(false)
    expect(isWideViewport({})).toBe(false)
  })
})
