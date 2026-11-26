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

const clamp = (x) => Math.max(0, Math.min(1, x))
const mix = (a, b, t) => a + (b - a) * t
const mixColor = (a, b, t) => a.map((channel, i) => mix(channel, b[i], t))
const rgb = (color) => color.map(Math.round).join(',')

// Display-referred daylight levels. The sky is still visible below the horizon;
// the direct beam and its colour have their own altitude response.
const skyStops = [
  [-12, 0, [8, 12, 28]],
  [-6, 0.1, [56, 76, 125]],
  [0, 0.28, [174, 151, 156]],
  [8, 0.54, [202, 205, 217]],
  [30, 0.82, [214, 230, 248]],
  [80, 1, [220, 237, 255]],
]
const beamStops = [
  [0, [255, 146, 77]],
  [6, [255, 179, 108]],
  [15, [255, 208, 153]],
  [40, [255, 232, 198]],
  [70, [255, 245, 224]],
]

function interpolateStops(stops, altitude, interpolate) {
  const end = stops.findIndex(([angle]) => angle >= altitude)
  if (end === -1) return interpolate(stops.at(-1), stops.at(-1), 0)
  if (end === 0) return interpolate(stops[0], stops[0], 0)
  const from = stops[end - 1]
  const to = stops[end]
  return interpolate(from, to, (altitude - from[0]) / (to[0] - from[0]))
}

// Transmission, diffuse daylight, source softness and sky colour in the
// absence of measured cloud cover / visibility. These are unitless UI levels.
const weatherProfiles = {
  clear: { direct: 1, diffuse: 1, softness: 0, color: [217, 231, 248] },
  cloud: { direct: 0.78, diffuse: 0.89, softness: 0.25, color: [215, 225, 238] },
  overcast: { direct: 0.04, diffuse: 0.64, softness: 0.65, color: [194, 204, 218] },
  fog: { direct: 0.1, diffuse: 0.67, softness: 0.85, color: [225, 224, 214] },
  rain: { direct: 0, diffuse: 0.54, softness: 0.72, color: [169, 197, 217] },
  snow: { direct: 0, diffuse: 0.84, softness: 0.72, color: [235, 246, 255] },
  thunder: { direct: 0, diffuse: 0.35, softness: 1, color: [136, 155, 181] },
}

function measurement(value, max) {
  if ((typeof value !== 'number' && typeof value !== 'string') || String(value).trim() === '') return null
  const n = Number(value)
  return Number.isFinite(n) && n >= 0 && n <= max ? n : null
}

export function weatherOptics(mode, weather) {
  const profile = weatherProfiles[mode] ?? weatherProfiles.clear
  const cover = measurement(weather?.nowFull?.cloud, 100)
  const visibility = measurement(weather?.nowFull?.vis, Infinity)
  const level = measurement(weather?.precipLevel, 6)
  const wet = mode === 'rain' || mode === 'snow'
  let { direct, diffuse, softness } = profile
  if (cover !== null && mode !== 'thunder') {
    const cloud = cover / 100
    const clearFraction = 1 - cloud ** 2
    // Broken cloud may leave a sunbreak in light rain. Dense rain/snow and
    // a fully covered sky retain diffuse illumination only.
    if (wet) direct = 0.38 * clearFraction * clamp(1 - (level ?? 2) / 3)
    else if (mode === 'fog') direct *= clearFraction
    else direct = clearFraction
    diffuse = clamp(diffuse + 0.14 * (0.5 - cloud))
    softness = clamp(softness + 0.45 * (cloud - 0.5))
  }
  if (mode === 'fog' && visibility !== null) {
    const clarity = clamp(visibility / 8)
    direct = 0.48 * clarity * (cover === null ? 1 : 1 - (cover / 100) ** 2)
    diffuse = 0.48 + 0.32 * clarity
    softness = 1 - 0.6 * clarity
  }
  if (wet && level !== null) diffuse *= 1 - 0.06 * level
  return { direct, diffuse, softness, color: profile.color }
}

const roomReflectance = 0.14

export function skyLighting(scene, optics, passingCloud = 1) {
  const direct = optics.direct * passingCloud
  // Local floor irradiance and total flux through the vertical window have
  // different incidence factors. Only the latter supplies reflected room light.
  const reflected = roomReflectance * scene.windowFlux * direct
  const diffuse = scene.skyLevel * optics.diffuse
  const natural = clamp(diffuse + reflected)
  const color = mixColor(scene.skyColor, optics.color, optics.softness * scene.skyLevel)
  const reflectedColor = mixColor(color, scene.beamColor, reflected)
  // Fix luminance before applying sky chroma. In particular a cool storm tint
  // must not darken a shaded surface as the morning becomes brighter.
  const tint = reflectedColor.map((channel) => Math.round(channel * 0.5))
  const tintLuma = (0.2126 * tint[0] + 0.7152 * tint[1] + 0.0722 * tint[2]) / 255
  const roomTransmission = 0.33 + 0.67 * natural
  return {
    direct: scene.lightOpacity * direct,
    natural,
    // Use the mean weather transmission for metering. A passing cloud moves
    // the beam without repeatedly waking the desk lamp.
    metered: clamp(diffuse + roomReflectance * scene.windowFlux * optics.direct),
    softness: optics.softness,
    tint: { background: 'rgb(' + rgb(tint) + ')', opacity: clamp((1 - roomTransmission) / (1 - tintLuma)) },
    diffuseColor: 'rgb(' + rgb(reflectedColor) + ')',
    diffuseOpacity: diffuse * (0.04 + 0.1 * optics.softness) + reflected * 0.16,
  }
}

// The canvas already shades blocked pixels by shade * direct. Supply the rest
// underneath so a blocked point keeps the same exposure as direct light fades.
export function ambientShadowOpacity(shade, direct) {
  return direct >= 1 ? 0 : (shade * (1 - direct)) / (1 - shade * direct)
}

export const AUTO_LAMP_ON_BELOW = 0.34
export const AUTO_LAMP_OFF_ABOVE = 0.45

export function lampFromDaylight(level) {
  const t = clamp((AUTO_LAMP_OFF_ABOVE - level) / 0.34)
  return t * t * (3 - 2 * t)
}

export function getSunScene(sunInfo, slotIndex, sample) {
  const slots = sunInfo?.slots || []
  const idx = Math.max(0, Math.min(slots.length - 1, slotIndex || 0))
  const slot = sample ?? slots[idx] ?? { altitude: -30, azimuth: 0, time: '00:00' }
  const alt = slot.altitude
  const az = slot.azimuth
  // -0.833 degrees is the apparent sunrise/sunset threshold used by SolarUtil.
  // The actual solar altitude also distinguishes polar day from polar night.
  const isNight = alt < -0.833
  const sunrise = parseTimeToMinutes(sunInfo?.sunrise)
  const sunset = parseTimeToMinutes(sunInfo?.sunset)
  const minute = parseTimeToMinutes(slot.time) ?? 0
  let dayProgress = minute / 1440
  if (sunrise != null && sunset != null && sunrise !== sunset) {
    const end = sunset < sunrise ? sunset + 1440 : sunset
    const clock = sunset < sunrise && minute <= sunset ? minute + 1440 : minute
    dayProgress = clamp((clock - sunrise) / (end - sunrise))
  }

  const windowAngle = 90 - Math.abs(az - 180)
  const hasDirectLight = alt > 0 && alt < 90 && windowAngle > 0
  const lightRotation = hasDirectLight ? az - 180 : az <= 90 ? -90 : 90
  const sourceX = Math.max(7.5, Math.min(92.5, ((az - 90) / 180) * 100))
  const sky = interpolateStops(skyStops, alt, (a, b, t) => ({ level: mix(a[1], b[1], t), color: mixColor(a[2], b[2], t) }))
  const beamColor = interpolateStops(beamStops, alt, (a, b, t) => mixColor(a[1], b[1], t))
  const beam = rgb(beamColor)
  const rgba = (alpha) => (hasDirectLight ? 'rgba(' + beam + ',' + alpha + ')' : 'transparent')
  const palette = { bloom: rgba(0.7), core: rgba(0.94), mid: rgba(0.52), ambient: rgba(0.08) }

  const rad = Math.PI / 180
  const sine = Math.max(0, Math.sin(alt * rad))
  // Approximate atmospheric extinction near the horizon. Tone-map the
  // floor's local intensity for display; total window flux stays linear.
  const transmission = sine / (0.12 + 0.88 * sine)
  const incidence = hasDirectLight ? Math.cos(alt * rad) * Math.sin(windowAngle * rad) : 0
  const windowFlux = transmission * incidence
  // Fade a grazing beam over the last few degrees to avoid aliased slivers.
  const lightOpacity = hasDirectLight ? Math.sqrt(transmission * sine) * clamp(incidence / 0.08) : 0
  const shadowIntensity = 0.7 - 0.4 * sky.level
  const lampOpacity = lampFromDaylight(clamp(sky.level + roomReflectance * windowFlux))
  const shadowColor = 'rgb(0,0,0)'

  return {
    source: { x: sourceX + '%', y: '-15%' },
    rotation: lightRotation,
    palette,
    rays: makeRays(hasDirectLight ? 1 : 0),
    skyLevel: sky.level,
    skyColor: sky.color,
    beamColor,
    windowFlux,
    shadowIntensity,
    shadowColor,
    lightOpacity,
    lampOpacity,
    altitude: alt,
    noonAltitude: slots.length ? slots.reduce((peak, slot) => (slot?.altitude > peak ? slot.altitude : peak), -90) : alt,
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
const clockFormatters = new Map()
export function solarClock(timezone, now = new Date()) {
  if (timezone) {
    try {
      let formatter = clockFormatters.get(timezone)
      if (!formatter) {
        formatter = new Intl.DateTimeFormat('en-GB', {
          timeZone: timezone,
          year: 'numeric',
          month: '2-digit',
          day: '2-digit',
          hour: '2-digit',
          minute: '2-digit',
          hourCycle: 'h23',
        })
        clockFormatters.set(timezone, formatter)
      }
      const parts = formatter.formatToParts(now)
      const { year, month, day, hour, minute } = Object.fromEntries(parts.map(({ type, value }) => [type, value]))
      const minutes = Number(hour) * 60 + Number(minute)
      return { date: `${year}-${month}-${day}`, minutes, slot: Math.floor(minutes / 5) }
    } catch (error) {
      if (!(error instanceof RangeError)) throw error
    }
  }
  const minutes = now.getHours() * 60 + now.getMinutes()
  return { date: todayStr(now), minutes, slot: Math.floor(minutes / 5) }
}

export function currentSlotIndex(timezone) {
  return solarClock(timezone).slot
}
