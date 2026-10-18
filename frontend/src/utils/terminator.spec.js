import { describe, it, expect } from 'vitest'
import {
  MAP_H,
  MAP_LAT_MAX,
  MAP_LAT_MIN,
  MAP_W,
  driftSubsolar,
  nextTwilightEvent,
  parseHm,
  projectToMap,
  solarAltitude,
  solarPosition,
  twilightStage,
  unprojectFromMap,
} from './terminator'

describe('由直射点推太阳高度角', () => {
  it('直射点为天顶,镜像点为天底', () => {
    // 赤纬 -9.4°(10 月下旬)、直射经度 116.5°
    expect(solarAltitude(-9.4, 116.5, -9.4, 116.5)).toBeCloseTo(90, 5)
    expect(solarAltitude(9.4, -63.5, -9.4, 116.5)).toBeCloseTo(-90, 5)
  })

  it('与后端 SolarUtil.sunPosition 对齐(北京 39.9N 夏至正午 73.54°)', () => {
    // 直射经度也取 116.4(北京本地太阳正午)时,正午高度角 = 90 −(39.9 − 23.44)
    expect(solarAltitude(39.9, 116.4, 23.44, 116.4)).toBeCloseTo(73.54, 1)
    expect(solarAltitude(39.9, 116.4, -23.44, 116.4)).toBeCloseTo(26.66, 1)
  })

  it('经差 90° 且赤纬 0 时高度角为 0', () => {
    expect(solarAltitude(0, 90, 0, 0)).toBeCloseTo(0, 6)
    expect(solarAltitude(0, -90, 0, 0)).toBeCloseTo(0, 6)
  })

  it('跨 ±180 经度归一化后结果一致', () => {
    // 经度 200° 与 -160° 是同一条经线,取差值时必须等价
    expect(solarAltitude(30, 200, -5, -170)).toBeCloseTo(solarAltitude(30, -160, -5, -170), 6)
  })

  it('solarPosition 与后端 SolarUtil 一致:直射点天顶方位退化,正午太阳在正南', () => {
    const ss = { lat: 23.44, lng: 116.4 }
    expect(solarPosition(23.44, 116.4, ss).altitude).toBeCloseTo(90, 4)
    // 北京正午:太阳在正南(180°),高度角 90 −(39.9 − 23.44)
    const noon = solarPosition(39.9, 116.4, ss)
    expect(noon.altitude).toBeCloseTo(73.54, 1)
    expect(noon.azimuth).toBeCloseTo(180, 1)
    // 上午太阳在东半天空(0~180),下午在西半天空(180~360),正午在正南
    const morning = solarPosition(39.9, 40.4, ss).azimuth
    const evening = solarPosition(39.9, 196.4, ss).azimuth
    expect(morning).toBeGreaterThan(0)
    expect(morning).toBeLessThan(180)
    expect(evening).toBeGreaterThan(180)
    expect(evening).toBeLessThan(360)
    // 方位角恒在 [0,360)
    for (let lng = -180; lng < 180; lng += 17) {
      const p = solarPosition(20, lng, { lat: -12, lng: 0 })
      expect(p.azimuth).toBeGreaterThanOrEqual(0)
      expect(p.azimuth).toBeLessThan(360)
    }
  })
})

describe('晨昏阶段阈值', () => {
  it('边界值归属下一档(> 而非 >=)', () => {
    expect(twilightStage(90)).toBe('day')
    expect(twilightStage(-0.8)).toBe('day')
    expect(twilightStage(-0.833)).toBe('civil')
    expect(twilightStage(-5.999)).toBe('civil')
    expect(twilightStage(-6)).toBe('nautical')
    expect(twilightStage(-12)).toBe('astronomical')
    expect(twilightStage(-17.999)).toBe('astronomical')
    expect(twilightStage(-18)).toBe('night')
    expect(twilightStage(-18.001)).toBe('night')
    expect(twilightStage(-90)).toBe('night')
  })

  // TerminatorMap 画色带时逐像素比对的是 sin 阈值(不做 asin),必须与 twilightStage 分到同一档
  it('Canvas 色带的 sin 阈值分档与 twilightStage 一致', () => {
    const T = [-0.833, -6, -12, -18].map((v) => Math.sin((v * Math.PI) / 180))
    const bandStage = (alt) => {
      const s = Math.sin((alt * Math.PI) / 180)
      const lv = s > T[0] ? -1 : s > T[1] ? 0 : s > T[2] ? 1 : s > T[3] ? 2 : 3
      return ['day', 'civil', 'nautical', 'astronomical', 'night'][lv + 1]
    }
    // 0.25 步长(二进制可精确表示):逐步比对,任一档错位都会被抓到
    for (let i = -360; i <= 360; i++) {
      const alt = i / 4
      expect(bandStage(alt)).toBe(twilightStage(alt))
    }
    // 四个边界:恰在阈值上与 twilightStage 同档;再往下挪一丁点仍是同一档(阈值是 > 而非 >=)
    for (const b of [-0.833, -6, -12, -18]) {
      expect(bandStage(b)).toBe(twilightStage(b))
      expect(bandStage(b - 1e-9)).toBe(bandStage(b))
      // 越过往上一档:高度角降低时档位只允许变深,不会跳回去
      expect(['day', 'civil', 'nautical', 'astronomical', 'night'].indexOf(bandStage(b + 1e-9))).toBe(
        ['day', 'civil', 'nautical', 'astronomical', 'night'].indexOf(bandStage(b)) - 1,
      )
    }
  })
})

describe('当日下一个晨昏事件', () => {
  const times = {
    astronomicalDawn: '04:45',
    nauticalDawn: '05:16',
    civilDawn: '05:46',
    sunrise: '06:12',
    solarNoon: '11:59',
    sunset: '17:48',
    civilDusk: '18:14',
    nauticalDusk: '18:44',
    astronomicalDusk: '19:15',
  }

  it('返回当前分钟之后最近的一个时刻', () => {
    expect(nextTwilightEvent(times, 6 * 60).key).toBe('sunrise')
    expect(nextTwilightEvent(times, 12 * 60).key).toBe('sunset')
    expect(nextTwilightEvent(times, 19 * 60).key).toBe('astronomicalDusk')
    expect(nextTwilightEvent(times, 0).key).toBe('astronomicalDawn')
    expect(nextTwilightEvent(times, 4 * 60 + 46).time).toBe('05:16')
  })

  it('当日无后续事件返回 null(夜间末尾/极昼),空串事件被跳过', () => {
    expect(nextTwilightEvent(times, 23 * 60)).toBeNull()
    // 极昼:日出日落与太阳正午当日都不发生 → 剩下的晨昏始末仍照常返回
    expect(nextTwilightEvent({ ...times, sunrise: '', sunset: '', solarNoon: '' }, 0).key).toBe('astronomicalDawn')
    // 全部为空 → 无任何事件
    expect(nextTwilightEvent({ sunrise: '', sunset: '', solarNoon: '', civilDawn: '', civilDusk: '' }, 0)).toBeNull()
    expect(nextTwilightEvent(null, 600)).toBeNull()
  })

  it('容忍空串与缺字段,不产出 NaN', () => {
    expect(parseHm('')).toBeNull()
    expect(parseHm(undefined)).toBeNull()
    expect(parseHm('18:30')).toBe(1110)
    expect(parseHm('07:05')).toBe(425)
  })
})

describe('直射点漂移', () => {
  it('经度按 0.25°/min 前进并归一化', () => {
    expect(driftSubsolar({ lat: 10, lng: 0 }, 60).lng).toBeCloseTo(15, 6)
    expect(driftSubsolar({ lat: 10, lng: 0 }, 60).lat).toBe(10)
    expect(driftSubsolar({ lat: 10, lng: 170 }, 60).lng).toBeCloseTo(-175, 6)
    expect(driftSubsolar({ lat: 10, lng: -170 }, 60).lng).toBeCloseTo(-155, 6)
    expect(driftSubsolar(null, 60)).toBeNull()
  })
})

describe('等距圆柱投影', () => {
  it('视图四角落在画布边界', () => {
    const tl = projectToMap(-180, MAP_LAT_MAX)
    expect(tl.x).toBeCloseTo(0, 6)
    expect(tl.y).toBeCloseTo(0, 6)
    const br = projectToMap(180, MAP_LAT_MIN)
    expect(br.x).toBeCloseTo(MAP_W, 6)
    expect(br.y).toBeCloseTo(MAP_H, 6)
    // 北京落在画布中部偏右上
    const bj = projectToMap(116.4, 39.9)
    expect(bj.x).toBeCloseTo(((116.4 + 180) / 360) * MAP_W, 6)
    expect(bj.y).toBeCloseTo(((MAP_LAT_MAX - 39.9) / 144) * MAP_H, 6)
  })

  it('投影/反投影往返一致', () => {
    for (const [lng, lat] of [
      [0, 0],
      [116.4, 39.9],
      [-73.9, 40.7],
      [151.2, -33.9],
    ]) {
      const p = projectToMap(lng, lat)
      const back = unprojectFromMap(p.x, p.y)
      expect(back.lng).toBeCloseTo(lng, 6)
      expect(back.lat).toBeCloseTo(lat, 6)
    }
  })

  it('支持自定义画布尺寸', () => {
    const p = projectToMap(0, 12, 1440, 576)
    expect(p.x).toBeCloseTo(720, 6)
    expect(p.y).toBeCloseTo(288, 6)
  })
})
