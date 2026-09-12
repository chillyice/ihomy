// 太阳位置(NOAA 算法,从后端 common/SolarUtil.java 忠实移植,与后端数值保持一致)。
// 仅用于光影实验台(/tools/light-lab)的客户端实时计算,时间统一按 Asia/Shanghai(UTC+8,无夏令时)。
// 说明:NOAA 太阳高度角/方位角与海拔无关(后端同)。

const RAD = Math.PI / 180
const DEG = 180 / Math.PI
const TZ_OFFSET_HOURS = 8 // 中国标准时间 UTC+8

function julianDay(y, m, d, hour) {
  if (m <= 2) { y--; m += 12 }
  const a = Math.floor(y / 100)
  const b = 2 - a + Math.floor(a / 4)
  return Math.floor(365.25 * (y + 4716)) + Math.floor(30.6001 * (m + 1)) + d + hour / 24.0 + b - 1524.5
}
function julianCentury(jd) {
  return (jd - 2451545.0) / 36525.0
}
function sunGeomMeanLong(T) {
  const l = 280.46646 + T * (36000.76983 + T * 0.0003032)
  return ((l % 360) + 360) % 360
}
function sunGeomMeanAnom(T) {
  return 357.52911 + T * (35999.05029 - 0.0001537 * T)
}
function earthEccentricity(T) {
  return 0.016708634 - T * (0.000042037 + 0.0000001267 * T)
}
function sunEqCenter(T) {
  const m = sunGeomMeanAnom(T) * RAD
  return Math.sin(m) * (1.914602 - T * (0.004817 + 0.000014 * T))
    + Math.sin(2 * m) * (0.019993 - 0.000101 * T)
    + Math.sin(3 * m) * 0.000289
}
function sunTrueLong(T) {
  return sunGeomMeanLong(T) + sunEqCenter(T)
}
function sunApparentLong(T) {
  const omega = 125.04 - 1934.136 * T
  return sunTrueLong(T) - 0.00569 - 0.00478 * Math.sin(omega * RAD)
}
function meanObliquity(T) {
  const sec = 21.448 - T * (46.815 + T * (0.00059 - T * 0.001813))
  return 23.0 + (26.0 + sec / 60.0) / 60.0
}
function obliquityCorrection(T) {
  const omega = 125.04 - 1934.136 * T
  return meanObliquity(T) + 0.00256 * Math.cos(omega * RAD)
}
function sunDeclination(T) {
  const e = obliquityCorrection(T) * RAD
  const lambda = sunApparentLong(T) * RAD
  return Math.asin(Math.sin(e) * Math.sin(lambda)) * DEG
}
function eqOfTime(T) {
  const e0 = obliquityCorrection(T)
  const l0 = sunGeomMeanLong(T)
  const m = sunGeomMeanAnom(T)
  const e = earthEccentricity(T)
  const y = Math.tan((e0 / 2) * RAD) ** 2
  const sin2l0 = Math.sin(2 * l0 * RAD)
  const sinm = Math.sin(m * RAD)
  const cos2l0 = Math.cos(2 * l0 * RAD)
  const sin4l0 = Math.sin(4 * l0 * RAD)
  const sin2m = Math.sin(2 * m * RAD)
  const Etime = y * sin2l0 - 2 * e * sinm + 4 * e * y * sinm * cos2l0
    - 0.5 * y * y * sin4l0 - 1.25 * e * e * sin2m
  return Etime * DEG * 4.0 // minutes
}
function sunAltAz(lat, dec, haDeg) {
  const latR = lat * RAD
  const decR = dec * RAD
  const haR = haDeg * RAD
  let sinAlt = Math.sin(latR) * Math.sin(decR) + Math.cos(latR) * Math.cos(decR) * Math.cos(haR)
  sinAlt = Math.max(-1, Math.min(1, sinAlt))
  const alt = Math.asin(sinAlt)
  const cosAlt = Math.cos(alt)
  if (Math.abs(cosAlt) < 1e-10) {
    return { altitude: alt * DEG, azimuth: 0 }
  }
  const y = -Math.sin(haR)
  const x = (Math.sin(decR) - Math.sin(latR) * sinAlt) / (Math.cos(latR) * cosAlt)
  let az = Math.atan2(y, x) * DEG
  if (az < 0) az += 360
  return { altitude: alt * DEG, azimuth: az }
}

/**
 * 计算指定日期、指定时刻(当地时间分钟)的太阳高度角与方位角。
 * @param {number} lat 纬度
 * @param {number} lng 经度
 * @param {string} dateStr 'YYYY-MM-DD'
 * @param {number} minutesOfDay 当地时间分钟(0~1440)
 * @returns {{ time: string, altitude: number, azimuth: number }}
 */
export function sunPosition(lat, lng, dateStr, minutesOfDay) {
  const [y, m, d] = dateStr.split('-').map(Number)
  const localMs = Date.UTC(y, m - 1, d, 0, 0) + minutesOfDay * 60000
  const utcMs = localMs - TZ_OFFSET_HOURS * 3600000
  const ud = new Date(utcMs)

  const hour = ud.getUTCHours() + ud.getUTCMinutes() / 60 + ud.getUTCSeconds() / 3600
  const jd = julianDay(ud.getUTCFullYear(), ud.getUTCMonth() + 1, ud.getUTCDate(), hour)
  const T = julianCentury(jd)
  const dec = sunDeclination(T)
  const eot = eqOfTime(T)
  const solarNoonMin = 720 - 4 * lng - eot
  const utcMin = ud.getUTCHours() * 60 + ud.getUTCMinutes()
  let ha = (utcMin - solarNoonMin) / 4
  while (ha > 180) ha -= 360
  while (ha < -180) ha += 360

  const { altitude, azimuth } = sunAltAz(lat, dec, ha)

  const hh = Math.floor(minutesOfDay / 60) % 24
  const mm = Math.floor(minutesOfDay % 60)
  return {
    time: `${String(hh).padStart(2, '0')}:${String(mm).padStart(2, '0')}`,
    altitude: Math.round(altitude * 10) / 10,
    azimuth: Math.round(azimuth * 10) / 10,
  }
}
