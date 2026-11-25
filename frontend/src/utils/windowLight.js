// 光线与天气:基于太阳位置(高度角/方位角/日出日落)生成光影参数
// 被 useSunLight.js 调用,提供全页面共享的光影状态
import { todayStr } from './date'

// Screen axes for a south-facing window: +x to the right, +y into the room.
export function floorShadowDirection(azimuth, realWeight = 1) {
  const bearing = ((180 - azimuth) * Math.PI) / 180
  const sideways = Math.sin(bearing)
  const inward = Math.cos(bearing)
  if (realWeight === 1 || inward <= Number.EPSILON) return { x: sideways, y: inward }
  // Scale along the ray to the calibrated screen depth, preserving its bearing.
  const depth = 1 - realWeight + realWeight * inward
  return { x: (sideways / inward) * depth, y: depth }
}

// Horizontal ground projection of a unit-height vertical object in direct sunlight.
export function groundShadowPerHeight(altitude) {
  return altitude > 0 && altitude < 90 ? 1 / Math.tan((altitude * Math.PI) / 180) : 0
}

function parseTimeToMinutes(timeStr) {
  if (!timeStr) return null
  const parts = String(timeStr).split(':').map(Number)
  if (parts.length < 2 || isNaN(parts[0]) || isNaN(parts[1])) return null
  return parts[0] * 60 + parts[1]
}

// Each stop describes the multiply tint on the page, independent of the window's bearing.
const nightTint = { at: 0, rgb: [8, 12, 28], opacity: 0.7 }
const clearSky = [
  nightTint,
  { at: 0.1, rgb: [255, 200, 80], opacity: 0.7 },
  { at: 0.3, rgb: [255, 250, 230], opacity: 0.1 },
  { at: 0.5, rgb: [255, 250, 230], opacity: 0 },
  { at: 0.7, rgb: [255, 250, 230], opacity: 0 },
  { at: 0.9, rgb: [255, 140, 50], opacity: 0.7 },
  { ...nightTint, at: 1 },
]
const graySky = [nightTint, { ...nightTint, at: 0.1, opacity: 0 }, { ...nightTint, at: 0.9, opacity: 0 }, { ...nightTint, at: 1 }]

function tintTransmission([r, g, b], opacity) {
  return 1 - opacity * (1 - (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255)
}
const nightTransmission = tintTransmission(nightTint.rgb, nightTint.opacity)

export function getSkyTint(scene, weather) {
  const stops = weather === 'clear' ? clearSky : graySky
  const progress = scene.isNight ? 0 : scene.dayProgress
  const end = stops.findIndex((stop, i) => i > 0 && progress <= stop.at)
  const from = stops[end - 1]
  const to = stops[end]
  const t = (progress - from.at) / (to.at - from.at)
  const rgb = from.rgb.map((channel, i) => channel + (to.rgb[i] - channel) * t)
  const opacity = from.opacity + (to.opacity - from.opacity) * t
  // Only daylight above the existing night tint can be blocked by the window.
  const daylightShare = Math.max(0, 1 - nightTransmission / tintTransmission(rgb, opacity))
  return { background: `rgb(${rgb.join(',')})`, opacity, daylightShare }
}

// The canvas already shades blocked pixels by shade * direct. Supply the rest
// underneath so a blocked point keeps the same exposure as direct light fades.
export function ambientShadowOpacity(shade, direct) {
  return direct >= 1 ? 0 : (shade * (1 - direct)) / (1 - shade * direct)
}

// 台灯自动开关阈值(以窗口阴影强度为口径:正午 0.3 → 日出日落 0.7 → 夜间 0.7):
// 阴影强度 ≥ LAMP_SHADOW_ON 认为「暗到该开灯」,≤ LAMP_SHADOW_OFF 认为「亮到该关灯」。
// 两阈值之间做 smoothstep 过渡(而非 0/1 跳变),傍晚渐亮、清晨渐灭,避免视觉突跳;
// 纯函数、不带历史状态,光照测试台按任意时隙跳着看也能得到同一条曲线。
export const LAMP_SHADOW_ON = 0.64
export const LAMP_SHADOW_OFF = 0.54

export function lampFromShadow(shadowIntensity) {
  const span = LAMP_SHADOW_ON - LAMP_SHADOW_OFF
  const t = Math.max(0, Math.min(1, ((shadowIntensity ?? 0) - LAMP_SHADOW_OFF) / span))
  return t * t * (3 - 2 * t)
}

export function getSunScene(sunInfo, slotIndex) {
  const slots = sunInfo?.slots || []
  const idx = Math.max(0, Math.min(287, slotIndex || 0))
  const slot = slots[idx] || { altitude: -30, azimuth: 0, time: '12:00' }

  const alt = slot.altitude
  const az = slot.azimuth
  const time = slot.time

  const sunriseMin = parseTimeToMinutes(sunInfo?.sunrise)
  const sunsetMin = parseTimeToMinutes(sunInfo?.sunset)
  const currentMin = parseTimeToMinutes(time)

  // 日昼进度:0=日出,1=日落
  // 夜间策略:日落后 hold 在日落位置(+90°),凌晨2点 reset 到日出位置(-90°)
  // 2点没人看,跳变无感;日落后阴影停留在日落位置,像太阳冻在日落
  const TWO_AM = 2 * 60
  let dayProgress = 0.5
  let isNight = true
  if (sunriseMin != null && sunsetMin != null && currentMin != null && sunsetMin > sunriseMin) {
    if (currentMin >= sunriseMin && currentMin <= sunsetMin) {
      isNight = false
      dayProgress = (currentMin - sunriseMin) / (sunsetMin - sunriseMin)
    } else if (currentMin >= sunsetMin || currentMin < TWO_AM) {
      isNight = true
      dayProgress = 1
    } else {
      isNight = true
      dayProgress = 0
    }
  }

  // 窗角:太阳直射窗户的角度(90°=正对窗户,0°=平行窗户无直射)
  const windowAngle = 90 - Math.abs(az - 180)
  // 是否有直射光:白天 + 窗角>0 + 太阳在地平线上
  const hasDirectLight = !isNight && windowAngle > 0 && alt > 0

  // 旋转:基于方位角驱动,az=90°开始(-90°)→ az=180°正中(0°)→ az=270°结束(+90°)
  // 窗角≤0(az<90 或 az>270)时 hold 在对应端点,无直射光不旋转
  const lightRotation = hasDirectLight ? az - 180 : az <= 90 ? -90 : 90

  // 光源水平位置:由方位角驱动,夜间 hold 在日出位置(左侧 7.5%)
  const sourceX = isNight ? 7.5 : Math.max(7.5, Math.min(92.5, ((az - 90) / 180) * 100))

  // 阴影强度:夜间 0.7,正午 0.3,日出日落 0.7(不超过 70%)
  const shadowIntensity = isNight ? 0.7 : 0.7 - Math.sin(dayProgress * Math.PI) * 0.4
  // 阴影颜色:夜间深蓝黑,日间纯黑
  const shadowColor = isNight ? 'rgb(8,12,28)' : 'rgb(0,0,0)'

  // 光柱颜色:夜间全透明(不发光),日间基于高度角
  let palette, rayBaseOpacity
  if (!hasDirectLight) {
    palette = {
      bloom: 'transparent',
      core: 'transparent',
      mid: 'transparent',
      ambient: 'transparent',
    }
    rayBaseOpacity = 0
  } else if (alt < 6) {
    palette = {
      bloom: 'rgba(255, 175, 90, 0.85)',
      core: 'rgba(255, 160, 70, 0.95)',
      mid: 'rgba(255, 130, 50, 0.6)',
      ambient: 'rgba(255, 165, 80, 0.12)',
    }
    rayBaseOpacity = 1.3
  } else if (alt < 15) {
    palette = {
      bloom: 'rgba(255, 200, 130, 0.78)',
      core: 'rgba(255, 190, 110, 0.92)',
      mid: 'rgba(255, 170, 85, 0.52)',
      ambient: 'rgba(255, 195, 115, 0.09)',
    }
    rayBaseOpacity = 1.15
  } else if (alt < 60) {
    palette = {
      bloom: 'rgba(255, 230, 180, 0.68)',
      core: 'rgba(255, 222, 160, 0.9)',
      mid: 'rgba(255, 210, 140, 0.48)',
      ambient: 'rgba(255, 225, 170, 0.06)',
    }
    rayBaseOpacity = 1.05
  } else {
    palette = {
      bloom: 'rgba(255, 242, 210, 0.62)',
      core: 'rgba(255, 238, 195, 0.9)',
      mid: 'rgba(255, 228, 170, 0.42)',
      ambient: 'rgba(255, 240, 200, 0.05)',
    }
    rayBaseOpacity = 0.95
  }

  // 光柱不透明度:无直射光时 0,有直射光时正弦过渡(正午最强)
  const rayOpacity = hasDirectLight ? Math.sin(dayProgress * Math.PI) * rayBaseOpacity : 0

  // 低角度的长影仍需可辨；按当天太阳高度与南窗入射角渐显，地平线和窗侧都自然淡出。
  const windowIncidence = Math.sin((Math.max(0, windowAngle) * Math.PI) / 180)
  const lightOpacity = hasDirectLight ? Math.sqrt(Math.sin((alt * Math.PI) / 180) * windowIncidence) : 0

  // 台灯:由阴影强度驱动(见 lampFromShadow),不再按方位角一刀切
  const lampOpacity = lampFromShadow(shadowIntensity)

  return {
    source: { x: sourceX + '%', y: '-15%' },
    rotation: lightRotation,
    palette,
    rays: makeRays(rayOpacity),
    shadowIntensity,
    shadowColor,
    lightOpacity,
    lampOpacity,
    altitude: alt,
    noonAltitude: slots.reduce((peak, sample) => (sample?.altitude > peak ? sample.altitude : peak), alt),
    azimuth: az,
    isNight,
    dayProgress,
    windowAngle,
    hasDirectLight,
  }
}

// 7 条羽毛状光束
function makeRays(opacityScale) {
  const base = [
    { width: 90, offset: -210, opacity: 0.45, blur: 45 },
    { width: 60, offset: -120, opacity: 0.6, blur: 35 },
    { width: 110, offset: -30, opacity: 0.5, blur: 55 },
    { width: 50, offset: 60, opacity: 0.65, blur: 30 },
    { width: 80, offset: 150, opacity: 0.4, blur: 50 },
    { width: 45, offset: 240, opacity: 0.35, blur: 40 },
    { width: 130, offset: 0, opacity: 0.25, blur: 80 },
  ]
  return base.map((r) => ({ ...r, opacity: r.opacity * opacityScale }))
}

// The backend labels each five-minute slot in the sun-info location's time zone.
export function solarClock(timezone, now = new Date()) {
  if (timezone) {
    try {
      const parts = new Intl.DateTimeFormat('en-GB', {
        timeZone: timezone,
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        hourCycle: 'h23',
      }).formatToParts(now)
      const { year, month, day, hour, minute } = Object.fromEntries(parts.map(({ type, value }) => [type, value]))
      return { date: `${year}-${month}-${day}`, slot: Math.floor((Number(hour) * 60 + Number(minute)) / 5) }
    } catch (error) {
      if (!(error instanceof RangeError)) throw error
    }
  }
  return { date: todayStr(now), slot: Math.floor((now.getHours() * 60 + now.getMinutes()) / 5) }
}

export function currentSlotIndex(timezone) {
  return solarClock(timezone).slot
}
