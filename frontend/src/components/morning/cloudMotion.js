const COMPASS = [
  '北',
  '北东北',
  '东北',
  '东东北',
  '东',
  '东东南',
  '东南',
  '南东南',
  '南',
  '南西南',
  '西南',
  '西西南',
  '西',
  '西西北',
  '西北',
  '北西北',
]
const mix = (a, b, t) => a + (b - a) * t
const snap = (n) => (Math.abs(n) < 1e-8 ? 0 : n)
const rand = (min, max) => mix(min, max, Math.random())

export function morningWindVector(degrees, direction) {
  const numeric = Number.parseFloat(degrees)
  const name = String(direction || '').replace(/\s+|风$/g, '')
  const exact = COMPASS.indexOf(name)
  const cardinal = COMPASS.indexOf(name.charAt(0))
  const angle = Number.isFinite(numeric) ? numeric : (exact >= 0 ? exact : cardinal >= 0 ? cardinal : 12) * 22.5
  const radians = (angle * Math.PI) / 180
  // 风向是来向。屏幕的纵轴向下，北风把近处云推向画面深处。
  return { x: snap(-Math.sin(radians)), y: snap(Math.cos(radians)) }
}

export function sampleCloudFlight(heightBand, speedBias = 1, initialIndex = null) {
  // 开屏留几朵已经在路上，其余分批入场；后续每朵独立等下一次风。
  const inFlight = initialIndex === 0 || (initialIndex > 1 && Math.random() < 0.68)
  return {
    x: rand(3, 97),
    y: rand(...heightBand),
    nearY: rand(-16, -5),
    speed: speedBias * rand(0.85, 1.22),
    phase: inFlight ? rand(initialIndex === 0 ? 0.35 : 0.21, 0.77) : 0,
    delay: inFlight ? 0 : rand(1.5, initialIndex === null ? 22 : 14),
  }
}

export function cloudMotionStyle(wind, level, rest, flight) {
  const sideways = Math.abs(wind.x)
  const depth = Math.abs(wind.y)
  const low = wind.y >= 0
  // 每朵云有独立航道，远处聚向中间，但不挤在一个消失点上。
  const { x, y, nearY, phase, speed, delay } = flight
  const farX = mix(50, x, 0.78)
  const farY = mix(24, 42, Math.min(1, Math.max(0, (y - 4) / 58)))
  const fromX = mix(mix(x, low ? x : farX, depth), wind.x > 0 ? -14 : 114, sideways)
  const toX = mix(mix(x, low ? farX : x, depth), wind.x > 0 ? 114 : -14, sideways)
  const fromY = mix(y, low ? nearY : farY, depth)
  const toY = mix(y, low ? farY : nearY, depth)
  const duration = Math.max(24, 210 * 0.8 ** level) * speed

  return {
    '--track-rest-x': rest.x + 'vw',
    '--track-rest-y': rest.y + 'vh',
    '--track-from-x': fromX + 'vw',
    '--track-from-y': fromY + 'vh',
    '--track-to-x': toX + 'vw',
    '--track-to-y': toY + 'vh',
    '--track-from-scale': mix(1, low ? 1.3 : 0.48, depth),
    '--track-to-scale': mix(1, low ? 0.48 : 1.3, depth),
    animationDuration: duration + 's',
    animationDelay: delay - phase * duration + 's',
  }
}
