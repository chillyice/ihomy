import { describe, it, expect } from 'vitest'
import {
  ambientShadowOpacity,
  ambientShadeLayer,
  skyLighting,
  weatherOptics,
  getSunScene,
  lampFromDaylight,
  solarClock,
} from './windowLight'

it('selects the date and five-minute slot on the clock used to build the solar table', () => {
  const instant = new Date('2026-11-25T16:04:59Z')
  expect(solarClock('Asia/Shanghai', instant)).toEqual({ date: '2026-11-26', minutes: 4, slot: 0 })
  expect(solarClock('America/New_York', instant)).toEqual({ date: '2026-11-25', minutes: 664, slot: 132 })
  expect(solarClock('Asia/Shanghai', new Date('2026-11-25T16:05:00Z')).slot).toBe(1)
  expect(solarClock('invalid', instant)).toEqual(solarClock(undefined, instant))
})

// 造一天:6:00 日出、18:00 日落,高度角/方位角随时隙近似推进
function makeDay() {
  const slots = Array.from({ length: 288 }, (_, i) => {
    const mins = i * 5
    const h = Math.floor(mins / 60)
    const m = mins % 60
    const dayP = (mins - 360) / (1080 - 360)
    return {
      time: `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`,
      altitude: Math.sin(dayP * Math.PI) * 60,
      azimuth: (90 + dayP * 180 + 360) % 360,
    }
  })
  return { sunrise: '06:00', sunset: '18:00', slots }
}

it('uses the selected local day’s peak for the frame even between observations', () => {
  const day = makeDay()
  const morning = { ...day.slots[96], altitude: 24 }
  expect(getSunScene(day, 96, morning)).toMatchObject({ altitude: 24, noonAltitude: 60 })
  expect(getSunScene({ ...day, slots: day.slots.map((slot) => ({ ...slot, altitude: slot.altitude / 2 })) }, 144).noonAltitude).toBe(30)
})

it('ramps the lamp continuously from dark indoor conditions to useful daylight', () => {
  expect(lampFromDaylight(0)).toBe(1)
  expect(lampFromDaylight(0.28)).toBeCloseTo(0.5)
  expect(lampFromDaylight(1)).toBe(0)
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

  it('日落时仍有天光，随后逐渐亮灯', () => {
    expect(getSunScene(day, 216).lampOpacity).toBeLessThan(1) // 18:00 日落
    expect(getSunScene(day, 222).lampOpacity).toBe(1) // 18:30
  })
})

it('南窗收到低角度直射光时长影可见，太阳降到地平线或转到窗后时自然光影消失', () => {
  const slots = [
    { time: '06:00', altitude: 0, azimuth: 115 },
    { time: '06:05', altitude: 2, azimuth: 115 },
    { time: '12:00', altitude: 55, azimuth: 180 },
    { time: '17:00', altitude: 2, azimuth: 275 },
    { time: '19:00', altitude: -8, azimuth: 280 },
  ]
  const day = { sunrise: '06:00', sunset: '18:00', slots }
  const opacity = slots.map((_, index) => getSunScene(day, index).lightOpacity)
  expect(opacity[0]).toBe(0)
  expect(opacity[1]).toBeGreaterThan(0)
  expect(opacity[1]).toBeLessThan(opacity[2])
  expect(opacity[2]).toBeGreaterThan(0.8)
  expect(opacity.slice(3)).toEqual([0, 0])
})

it('keeps a fully blocked point at the same shade while the direct beam appears or fades', () => {
  for (const shade of [0, 0.3, 0.7, 1]) {
    for (const direct of [0, 0.01, 0.5, 0.99, 1]) {
      const ambient = ambientShadowOpacity(shade, direct)
      expect((1 - ambient) * (1 - shade * direct)).toBeCloseTo(1 - shade, 10)
    }
  }
})

it('dresses the ambient shade warm without changing how far it darkens the page', () => {
  for (const shade of [0, 0.05, 0.2, 0.55]) {
    const layer = ambientShadeLayer(shade)
    const [r, g, b] = layer.background.slice(4, -1).split(',').map(Number)
    const luma = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255
    // 乘色在 luminance 上必须等价于「黑色 × shade」,明暗节奏才不受影响
    expect(1 - layer.opacity * (1 - luma)).toBeCloseTo(1 - shade, 4)
    expect(layer.opacity).toBeLessThanOrEqual(1)
    // 暖褐:红 > 绿 > 蓝
    expect(r).toBeGreaterThan(g)
    expect(g).toBeGreaterThan(b)
  }
})

function sceneAt(altitude, azimuth = 180, time = '12:00') {
  return getSunScene({ slots: [{ altitude, azimuth, time }], sunrise: '', sunset: '' }, 0)
}

it('keeps polar noon lit without sunrise and sunset, including sky behind the window', () => {
  expect(sceneAt(43).hasDirectLight).toBe(true)
  expect(sceneAt(-21).isNight).toBe(true)
  const north = sceneAt(43, 5)
  expect(north.lightOpacity).toBe(0)
  expect(north.skyLevel).toBeGreaterThan(0.7)
})

it('gradually brightens the sky through twilight while the window has no direct beam', () => {
  const altitudes = [-18, -12, -9, -6, -3, 0]
  const scenes = altitudes.map((alt) => sceneAt(alt))
  expect(scenes.map((scene) => scene.lightOpacity)).toEqual([0, 0, 0, 0, 0, 0])
  expect(scenes[0].skyLevel).toBe(0)
  for (let i = 2; i < scenes.length; i++) expect(scenes[i].skyLevel).toBeGreaterThan(scenes[i - 1].skyLevel)
})

it('keeps direct colour continuous at the old palette boundaries', () => {
  for (const boundary of [6, 15, 60]) {
    const before = sceneAt(boundary - 0.01).beamColor
    const after = sceneAt(boundary + 0.01).beamColor
    expect(Math.max(...after.map((channel, i) => Math.abs(channel - before[i])))).toBeLessThan(0.5)
  }
  expect(sceneAt(3).beamColor[2]).toBeLessThan(sceneAt(70).beamColor[2])
})

it('models local floor strength separately from flux through a vertical window', () => {
  const mid = sceneAt(30)
  const high = sceneAt(80)
  expect(high.lightOpacity).toBeGreaterThan(mid.lightOpacity)
  expect(high.windowFlux).toBeLessThan(mid.windowFlux)
  expect(sceneAt(45, 80).windowFlux).toBe(0)
})

it('retains measured zeroes and uses weather defaults for missing measurements', () => {
  const fog = (vis) => weatherOptics('fog', { nowFull: { vis } })
  expect(fog(null)).toEqual(fog(''))
  expect(fog(0).direct).toBe(0)
  expect(fog(6).direct).toBeGreaterThan(fog(0.1).direct)
  expect(fog(0.1).softness).toBeGreaterThan(fog(6).softness)
  const rain = (cloud, precipLevel) => weatherOptics('rain', { nowFull: { cloud }, precipLevel })
  expect(rain(null, 1).direct).toBe(0)
  expect(rain(0, 1).direct).toBeGreaterThan(0)
  expect(rain(0, 4).direct).toBe(0)
  expect(rain(0, 6).direct).toBe(0)
})

it('retains diffuse daylight under thick cloud and meters the lamp independently of moving clouds', () => {
  const sun = sceneAt(55)
  const cloud = weatherOptics('cloud', { nowFull: { cloud: 50 } })
  expect(skyLighting(sun, cloud, 0.1).direct).toBeLessThan(skyLighting(sun, cloud, 1).direct)
  expect(skyLighting(sun, cloud, 0.1).metered).toBe(skyLighting(sun, cloud, 1).metered)
  const gray = skyLighting(sun, weatherOptics('overcast', { nowFull: { cloud: 100 } }))
  expect(gray.direct).toBe(0)
  expect(gray.diffuseOpacity).toBeGreaterThan(0)
  expect(skyLighting(sceneAt(-18), weatherOptics('snow')).diffuseOpacity).toBe(0)
})
