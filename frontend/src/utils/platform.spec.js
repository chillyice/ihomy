import { describe, it, expect } from 'vitest'
import { isIOSUA } from './platform'

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
