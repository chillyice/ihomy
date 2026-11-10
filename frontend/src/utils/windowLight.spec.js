import { describe, it, expect } from 'vitest'
import { getSunScene, lampFromShadow, LAMP_SHADOW_ON, LAMP_SHADOW_OFF } from './windowLight'

// 造一天:6:00 日出、18:00 日落,高度角/方位角随时隙近似推进
function makeDay() {
  const slots = Array.from({ length: 288 }, (_, i) => {
    const mins = i * 5
    const h = Math.floor(mins / 60)
    const m = mins % 60
    const dayP = (mins - 360) / (1080 - 360)
    const isDay = mins >= 360 && mins <= 1080
    return {
      time: `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`,
      altitude: isDay ? Math.sin(dayP * Math.PI) * 60 : -20,
      azimuth: isDay ? 90 + dayP * 180 : 270,
    }
  })
  return { sunrise: '06:00', sunset: '18:00', slots }
}

describe('lampFromShadow', () => {
  it('低于关灯阈值不亮、高于开灯阈值全亮', () => {
    expect(lampFromShadow(0.3)).toBe(0) // 正午
    expect(lampFromShadow(LAMP_SHADOW_OFF - 0.01)).toBe(0)
    expect(lampFromShadow(0.7)).toBe(1) // 夜间
    expect(lampFromShadow(LAMP_SHADOW_ON + 0.01)).toBe(1)
  })

  it('两阈值之间连续过渡(中点约 0.5)', () => {
    const mid = lampFromShadow((LAMP_SHADOW_ON + LAMP_SHADOW_OFF) / 2)
    expect(mid).toBeCloseTo(0.5, 5)
  })

  it('随阴影强度单调不减', () => {
    let prev = -1
    for (let v = 0; v <= 1.0001; v += 0.01) {
      const cur = lampFromShadow(v)
      expect(cur).toBeGreaterThanOrEqual(prev)
      prev = cur
    }
  })
})

describe('getSunScene 台灯时机', () => {
  const day = makeDay()

  it('正午关灯、深夜开灯', () => {
    expect(getSunScene(day, 144).lampOpacity).toBe(0) // 12:00
    expect(getSunScene(day, 12).lampOpacity).toBe(1) // 01:00
    expect(getSunScene(day, 276).lampOpacity).toBe(1) // 23:00
  })

  it('傍晚/清晨逐时隙渐变,不出现整块跳变', () => {
    let maxStep = 0
    let prev = getSunScene(day, 0).lampOpacity
    for (let i = 1; i < 288; i += 1) {
      const cur = getSunScene(day, i).lampOpacity
      maxStep = Math.max(maxStep, Math.abs(cur - prev))
      prev = cur
    }
    // 旧版按方位角 0/1 切换会一步跨 1;渐变后单个 5 分钟时隙的变化远小于此
    expect(maxStep).toBeLessThan(0.2)
  })

  it('日落后保持全亮(夜间阴影强度封顶 0.7)', () => {
    expect(getSunScene(day, 216).lampOpacity).toBe(1) // 18:00 日落
    expect(getSunScene(day, 222).lampOpacity).toBe(1) // 18:30
  })
})
