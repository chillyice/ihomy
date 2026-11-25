import { resolveMorningWeather } from './morningWeather'

// Coordinates across the fixed jambs and central mullion of the window.
export const WINDOW_WIDTH_RATIO = 0.88
export const CASEMENTS = [
  { hinge: 0.012, latch: 0.5 - 0.036 },
  { hinge: 1 - 0.012, latch: 0.5 + 0.036 },
]

export function weatherClosesWindow(weather) {
  if (!weather) return false
  const mode = resolveMorningWeather(weather)
  if (mode === 'thunder' || ((mode === 'rain' || mode === 'snow') && Number(weather.precipLevel) >= 3)) return true
  // Some severe conditions fall into the provider's generic light-precipitation bucket.
  if (/大雨|暴雨|强阵雨|极端降雨|大雪|暴雪|冻雨|冰雹|沙尘暴|台风/.test(weather.text ?? weather.nowFull?.text ?? '')) return true
  const scales = String(weather.nowFull?.windScale ?? weather.windScale ?? '').match(/\d+(?:\.\d+)?/g) ?? []
  return scales.some((scale) => Number(scale) >= 6) || Number.parseFloat(weather.nowFull?.windSpeed ?? weather.windSpeed) >= 39
}

export function chooseWindowSash(random = Math.random) {
  if (random() < 0.45) return null
  const { hinge, latch } = CASEMENTS[random() < 0.5 ? 0 : 1]
  const angle = ((30 + random() * 45) * Math.PI) / 180
  const span = latch - hinge
  return {
    hinge,
    latch,
    tipX: hinge + span * Math.cos(angle),
    depth: Math.abs(span) * Math.sin(angle),
  }
}
