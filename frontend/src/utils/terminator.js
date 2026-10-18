// 晨昏线几何(纯计算,零依赖):只做「由太阳直射点推高度角」与「经纬度↔像素」两件事,
// 天文算法(NOAA/Meeus)的唯一来源在后端 SolarUtil,随 /public/sun-info 的 subsolar 一起下发。
// 前端若再移植一份算法就会出现两份事实,故这里只做球面三角公式的几何展开。

const RAD = Math.PI / 180

// 地图视图裁剪:经度全览 [-180,180],纬度裁掉两极 [-60,84](无居民区且正弦投影下严重拉伸)
export const MAP_LNG_MIN = -180
export const MAP_LNG_MAX = 180
export const MAP_LAT_MIN = -60
export const MAP_LAT_MAX = 84
export const MAP_W = 720
export const MAP_H = 288

// 晨昏阶段阈值(度):与后端 SolarUtil 的 SUN_H0_* 一致,别单方面改
export const STAGE_ORDER = ['day', 'civil', 'nautical', 'astronomical', 'night']

const TWILIGHT_KEYS = [
  'astronomicalDawn',
  'nauticalDawn',
  'civilDawn',
  'sunrise',
  'solarNoon',
  'sunset',
  'civilDusk',
  'nauticalDusk',
  'astronomicalDusk',
]

/** 由太阳直射点推任意点的太阳高度角(度)。直射点处为 90°。 */
export function solarAltitude(lat, lng, subsolarLat, subsolarLng) {
  const sinA =
    Math.sin(lat * RAD) * Math.sin(subsolarLat * RAD) +
    Math.cos(lat * RAD) * Math.cos(subsolarLat * RAD) * Math.cos((lng - subsolarLng) * RAD)
  return Math.asin(Math.max(-1, Math.min(1, sinA))) / RAD
}

/**
 * 高度角 + 方位角(度,正北 0° 顺时针),与后端 SolarUtil.sunPosition 同一套球面公式。
 * 时角 H = 与直射经度的经差(直射经线即真太阳时正午所在经线,故 H 精确)。
 * 只做几何,不做天文——赤纬与直射经度均由后端 subsolar 下发。
 */
export function solarPosition(lat, lng, subsolar) {
  const latR = lat * RAD
  const decR = subsolar.lat * RAD
  const hR = (lng - subsolar.lng) * RAD
  const sinAlt = clamp1(Math.sin(latR) * Math.sin(decR) + Math.cos(latR) * Math.cos(decR) * Math.cos(hR))
  const alt = Math.asin(sinAlt)
  const cosAlt = Math.cos(alt)
  if (Math.abs(cosAlt) < 1e-10) return { altitude: alt / RAD, azimuth: 0 }
  const y = -Math.sin(hR)
  const x = (Math.sin(decR) - Math.sin(latR) * sinAlt) / (Math.cos(latR) * cosAlt)
  const az = Math.atan2(y, x) / RAD
  return { altitude: alt / RAD, azimuth: az < 0 ? az + 360 : az }
}

function clamp1(v) {
  return Math.max(-1, Math.min(1, v))
}

/** 晨昏阶段:day / civil / nautical / astronomical / night */
export function twilightStage(altitude) {
  if (altitude > -0.833) return 'day'
  if (altitude > -6) return 'civil'
  if (altitude > -12) return 'nautical'
  if (altitude > -18) return 'astronomical'
  return 'night'
}

/** 'HH:mm' → 当日分钟数;空串/非法返回 null(极昼极夜的事件不存在) */
export function parseHm(s) {
  const m = /^(\d{1,2}):(\d{2})$/.exec(s || '')
  if (!m) return null
  return Number(m[1]) * 60 + Number(m[2])
}

/**
 * 当前时刻之后最近的晨昏/日月事件,返回 {key, time, minutes};当日无后续事件返回 null。
 * astroTimes 即 /public/sun-info 的 9 个时刻(空串表示当日不发生,直接跳过)。
 */
export function nextTwilightEvent(astroTimes, nowMinutes) {
  if (!astroTimes) return null
  for (const key of TWILIGHT_KEYS) {
    const minutes = parseHm(astroTimes[key])
    if (minutes != null && minutes > nowMinutes) {
      return { key, time: astroTimes[key], minutes }
    }
  }
  return null
}

/** 当日已过的分钟数(当地),0~1440 */
export function nowLocalMinutes(date = new Date()) {
  return date.getHours() * 60 + date.getMinutes()
}

/** 直射点漂移:赤纬按天变(可忽略),经度 +0.25°/min,归一化到 [-180,180] */
export function driftSubsolar(subsolar, elapsedMinutes) {
  if (!subsolar) return null
  const lng = normLng(subsolar.lng + 0.25 * elapsedMinutes)
  return { lat: subsolar.lat, lng }
}

function normLng(lng) {
  let v = ((lng % 360) + 360) % 360
  return v > 180 ? v - 360 : v
}

/** 经纬度 → 地图像素坐标(等距圆柱,视图裁剪见 MAP_* 常量) */
export function projectToMap(lng, lat, width = MAP_W, height = MAP_H) {
  return {
    x: ((lng - MAP_LNG_MIN) / (MAP_LNG_MAX - MAP_LNG_MIN)) * width,
    y: ((MAP_LAT_MAX - lat) / (MAP_LAT_MAX - MAP_LAT_MIN)) * height,
  }
}

/** 地图像素坐标 → 经纬度(unprojectFromMap ∘ projectToMap = 恒等) */
export function unprojectFromMap(x, y, width = MAP_W, height = MAP_H) {
  return {
    lng: MAP_LNG_MIN + (x / width) * (MAP_LNG_MAX - MAP_LNG_MIN),
    lat: MAP_LAT_MAX - (y / height) * (MAP_LAT_MAX - MAP_LAT_MIN),
  }
}
