import { describe, it, expect } from 'vitest'
import {
  AUTO_GAP_MS,
  backupBlockedReason,
  backupWindowStart,
  generateDeviceId,
  isValidDeviceId,
  makeBackupKey,
  normalizeCursor,
  pendingSinceMs,
  shouldAutoBackup,
} from './photoBackupWindow'

const DAY = 24 * 3600 * 1000
const NOW = Date.UTC(2026, 10, 10, 12, 0, 0)

describe('photoBackupWindow 首次备份窗口', () => {
  it('默认从 90 天前算起,而不是从纪元或今年年初', () => {
    expect(backupWindowStart(NOW)).toBe(NOW - 90 * DAY)
    expect(backupWindowStart(NOW, 7)).toBe(NOW - 7 * DAY)
  })

  it('异常注入不产生 NaN/负数', () => {
    expect(backupWindowStart(undefined)).toBe(0)
    expect(backupWindowStart(10, 90)).toBe(0)
    expect(backupWindowStart(NOW, undefined)).toBe(NOW - 90 * DAY)
  })
})

describe('photoBackupWindow 游标', () => {
  it('两个键都为正整数才认,缺一个就当没有游标', () => {
    expect(normalizeCursor({ d: 1000, i: 7 })).toEqual({ d: 1000, i: 7 })
    expect(normalizeCursor({ d: 1000 })).toBeNull()
    expect(normalizeCursor({ d: 1000, i: 0 })).toBeNull()
    expect(normalizeCursor({ d: -1, i: 3 })).toBeNull()
    expect(normalizeCursor('1000,7')).toBeNull()
    expect(normalizeCursor(null)).toBeNull()
  })

  it('字符串数字按数字收敛,保留原始游标值', () => {
    expect(normalizeCursor({ d: '1730000000000', i: '42' })).toEqual({ d: 1730000000000, i: 42 })
  })

  it('待备份张数:有游标按游标起,没游标按 90 天起', () => {
    expect(pendingSinceMs({ d: 1730000000000, i: 9 }, NOW)).toBe(1730000000000)
    expect(pendingSinceMs(null, NOW)).toBe(NOW - 90 * DAY)
  })
})

describe('photoBackupWindow 备份相册标识', () => {
  it('合法设备标识加 android: 前缀,与服务端正则一致', () => {
    expect(isValidDeviceId('abc_123-XYZ')).toBe(true)
    expect(makeBackupKey('abc_123-XYZ')).toBe('android:abc_123-XYZ')
  })

  it('空/超长/带冒号的标识不生成键(避免服务端 400)', () => {
    expect(isValidDeviceId('')).toBe(false)
    expect(isValidDeviceId('x'.repeat(57))).toBe(false)
    expect(isValidDeviceId('a:b')).toBe(false)
    expect(makeBackupKey('a:b')).toBe('')
    expect(makeBackupKey(null)).toBe('')
  })

  it('新生成的标识稳定符合服务端形状,不同随机源不会撞成空串', () => {
    const a = generateDeviceId(() => 0.123456789, NOW)
    const b = generateDeviceId(() => 0, NOW)
    expect(isValidDeviceId(a)).toBe(true)
    expect(isValidDeviceId(b)).toBe(true)
    expect(a).not.toBe(b)
    expect(a.length).toBeLessThanOrEqual(56)
  })
})

describe('photoBackupWindow 开关闸门', () => {
  it('仅 Wi-Fi 优先于仅充电报原因', () => {
    expect(backupBlockedReason({ wifiOnly: true, wifi: false, chargeOnly: true, charging: false })).toBe('network')
    expect(backupBlockedReason({ wifiOnly: true, wifi: false })).toBe('network')
  })

  it('仅充电:没充电就不开始,充电后可开始', () => {
    expect(backupBlockedReason({ chargeOnly: true, charging: false })).toBe('charging')
    expect(backupBlockedReason({ chargeOnly: true, charging: true })).toBe('')
  })

  it('开关关掉时任何状态都放行', () => {
    expect(backupBlockedReason({})).toBe('')
    expect(backupBlockedReason({ wifiOnly: false, wifi: false, chargeOnly: false, charging: false })).toBe('')
  })
})

describe('photoBackupWindow 自动触发闸门', () => {
  it('开关关/未登录都不自动跑', () => {
    expect(shouldAutoBackup({ enabled: false, loggedIn: true, now: NOW, lastAutoAt: 0 })).toBe(false)
    expect(shouldAutoBackup({ enabled: true, loggedIn: false, now: NOW, lastAutoAt: 0 })).toBe(false)
  })

  it('距上次触发不到 10 分钟不重复跑,到点后放行', () => {
    expect(shouldAutoBackup({ enabled: true, loggedIn: true, now: NOW, lastAutoAt: NOW - AUTO_GAP_MS + 1 })).toBe(false)
    expect(shouldAutoBackup({ enabled: true, loggedIn: true, now: NOW, lastAutoAt: NOW - AUTO_GAP_MS })).toBe(true)
  })

  it('从未跑过(0)且时间有效时放行', () => {
    expect(shouldAutoBackup({ enabled: true, loggedIn: true, now: NOW, lastAutoAt: 0 })).toBe(true)
  })
})
