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

export function cloudMotionStyle(wind, level, anchor, phase, speed) {
  const sideways = Math.abs(wind.x)
  const depth = Math.abs(wind.y)
  const low = wind.y >= 0
  // 每朵云有独立航道，远处聚向中间，但不挤在一个消失点上。
  const farX = mix(50, anchor.x, 0.78)
  const farY = mix(24, 42, Math.min(1, Math.max(0, (anchor.y - 4) / 58)))
  const fromX = mix(mix(anchor.x, low ? anchor.x : farX, depth), wind.x > 0 ? -14 : 114, sideways)
  const toX = mix(mix(anchor.x, low ? farX : anchor.x, depth), wind.x > 0 ? 114 : -14, sideways)
  const fromY = mix(anchor.y, low ? -10 : farY, depth)
  const toY = mix(anchor.y, low ? farY : -10, depth)
  const duration = Math.max(24, 210 * 0.8 ** level) * speed

  return {
    '--track-rest-x': anchor.x + 'vw',
    '--track-rest-y': anchor.y + 'vh',
    '--track-from-x': fromX + 'vw',
    '--track-from-y': fromY + 'vh',
    '--track-to-x': toX + 'vw',
    '--track-to-y': toY + 'vh',
    '--track-from-scale': mix(1, low ? 1.3 : 0.48, depth),
    '--track-to-scale': mix(1, low ? 0.48 : 1.3, depth),
    animationDuration: duration + 's',
    animationDelay: -phase * duration + 's',
  }
}
