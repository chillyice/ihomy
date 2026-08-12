// 光线与天气:纯前端按当前时辰生成窗外景色 + 室内光影参数,无外部 API
// 后续接天气 API 后,condition 字段由后端返回,此处只负责光影映射

// 时辰场景
export function getWindowLight() {
  const h = new Date().getHours()
  if (h >= 6 && h < 9) {
    return { gradient: 'linear-gradient(180deg, #F5E6C8 0%, #E8D8B8 60%, #D4C0A0 100%)', scene: 'dawn' }
  }
  if (h >= 9 && h < 16) {
    return { gradient: 'linear-gradient(180deg, #E8E4D0 0%, #D8D0B8 60%, #C9BD9C 100%)', scene: 'noon' }
  }
  if (h >= 16 && h < 19) {
    return { gradient: 'linear-gradient(180deg, #D4A574 0%, #C08850 50%, #A8483A 100%)', scene: 'dusk' }
  }
  if (h >= 19 && h < 22) {
    return { gradient: 'linear-gradient(180deg, #4A3A4E 0%, #3A2E38 50%, #2A2018 100%)', scene: 'evening' }
  }
  return { gradient: 'linear-gradient(180deg, #2A2038 0%, #221A28 50%, #1A1410 100%)', scene: 'night' }
}

// 整体光影叠加层(覆盖整张房间图):色调 + 压暗
export function getRoomOverlay() {
  const h = new Date().getHours()
  if (h >= 6 && h < 9) {
    // 晨光:暖白,从左上方洒入
    return {
      bg: 'linear-gradient(135deg, rgba(255,235,190,0.25) 0%, transparent 50%)',
      blend: 'soft-light',
      brightness: 1.05,
    }
  }
  if (h >= 9 && h < 16) {
    // 正午:明亮,几乎不叠色
    return {
      bg: 'rgba(255,250,235,0.08)',
      blend: 'overlay',
      brightness: 1.0,
    }
  }
  if (h >= 16 && h < 19) {
    // 黄昏:暖橙,从右上方洒入
    return {
      bg: 'linear-gradient(225deg, rgba(212,140,80,0.35) 0%, rgba(168,72,58,0.2) 40%, transparent 70%)',
      blend: 'overlay',
      brightness: 0.95,
    }
  }
  if (h >= 19 && h < 22) {
    // 傍晚:深紫压暗
    return {
      bg: 'linear-gradient(180deg, rgba(40,30,50,0.4) 0%, rgba(30,20,25,0.5) 100%)',
      blend: 'multiply',
      brightness: 0.8,
    }
  }
  // 夜晚:深压暗 + 室内灯暖光
  return {
    bg: 'radial-gradient(circle at 30% 65%, rgba(232,200,150,0.15) 0%, transparent 40%), rgba(15,12,20,0.55)',
    blend: 'multiply',
    brightness: 0.7,
  }
}

// 体积光场景:基于太阳高度角(altitude)和方位角(azimuth)驱动
// sunInfo: { sunrise, sunset, solarNoon, slots: [{time, altitude, azimuth}, ...] }
// slotIndex: 0-95(每 15 分钟一个时隙)
export function getSunScene(sunInfo, slotIndex) {
  const slots = sunInfo?.slots || []
  const idx = Math.max(0, Math.min(95, slotIndex || 0))
  const slot = slots[idx] || { altitude: -30, azimuth: 0 }
  const moonPhase = sunInfo?.moonPhase ?? 0.5

  const alt = slot.altitude // -90 ~ 90
  const az = slot.azimuth // 0 ~ 360 (North=0, East=90, South=180, West=270)

  // 方位角偏离正南的度数(归一化到 -180~180)
  let azDev = az - 180
  while (azDev > 180) azDev -= 360
  while (azDev < -180) azDev += 360

  // 阴影参数(日间才有,夜晚按月相)
  // 竖直阴影旋转:太阳在东(azDev<0)→ 阴影偏左(负旋转);太阳在西(azDev>0)→ 阴影偏右(正旋转)
  // 阴影与光线对向(光线从太阳方向射来,阴影朝同向延伸,旋转取反使阴影远离太阳)
  const shadowVRotation = Math.max(-45, Math.min(45, azDev * 0.5))

  // 横向阴影 top 位置:太阳越高 → 阴影越靠近顶部(窗户);太阳越低 → 阴影越远
  // alt=90(正天顶): top≈8%; alt=0(地平线): top≈80%; alt<0: 不显示
  const shadowHTop = Math.max(5, Math.min(85, 80 - alt * 0.8))

  // 窗户外框阴影(墙面投影):宽度占页面比例
  const frameWidth = 55 // %
  // 外框阴影延伸长度:太阳低 → 长投影;太阳高 → 短投影
  const frameHeight = Math.max(30, Math.min(400, 300 - alt * 2.5))
  // 外框 skew:跟随方位角
  const frameSkew = Math.max(-30, Math.min(30, -azDev * 0.3))

  // 太阳在地平线以下:夜间模式(按月相决定月光强度)
  if (alt < -6) {
    const moonBrightness = moonPhase < 0.5 ? moonPhase * 2 : (1 - moonPhase) * 2 // 0~1
    const moonGlow = 0.08 + moonBrightness * 0.15
    const moonRay = 0.1 + moonBrightness * 0.2
    const moonShadow = 0.05 + moonBrightness * 0.08
    return {
      source: { x: '50%', y: '-2%' },
      rotation: 0,
      palette: {
        bloom: `rgba(180, 200, 235, ${moonGlow})`,
        core: `rgba(170, 195, 230, ${moonRay})`,
        mid: `rgba(150, 180, 215, ${moonRay * 0.5})`,
        ambient: `rgba(25, 30, 50, 0.08)`,
        shadow: `rgba(10, 12, 25, ${moonShadow})`,
      },
      rays: makeRays(moonBrightness * 0.35),
      shadowVRotation: 0,
      shadowHTop: 80,
      shadowOpacity: moonBrightness * 0.15,
      shadowVisible: moonBrightness > 0.3,
      frameWidth, frameHeight: 50, frameSkew: 0,
      frameOpacity: moonBrightness * 0.1,
      altitude: alt,
      azimuth: az,
    }
  }

  // 光源水平位置:方位角 90(东)= 左侧 5%,180(南)= 中 50%,270(西)= 右 95%
  const sourceX = Math.max(3, Math.min(97, ((az - 90) / 180) * 100))

  // 光柱旋转:正南(180°)= 0°(垂直),东(90°)= 左上斜射向右下,西(270°)= 右上斜射向左下
  // transform-origin 默认 center,正旋转=顺时针=顶部转右;取反使光源在左时光柱指向右下
  const rotation = Math.max(-55, Math.min(55, (az - 180) * 0.55))

  // 高度角 → 颜色/强度(增亮 + 颜色凸显)
  let palette, rayOpacity
  if (alt < 6) {
    palette = {
      bloom: 'rgba(255, 175, 90, 0.85)',
      core: 'rgba(255, 160, 70, 0.95)',
      mid: 'rgba(255, 130, 50, 0.6)',
      ambient: 'rgba(255, 165, 80, 0.12)',
      shadow: 'rgba(45, 20, 5, 0.55)',
    }
    rayOpacity = 1.3
  } else if (alt < 15) {
    palette = {
      bloom: 'rgba(255, 200, 130, 0.78)',
      core: 'rgba(255, 190, 110, 0.92)',
      mid: 'rgba(255, 170, 85, 0.52)',
      ambient: 'rgba(255, 195, 115, 0.09)',
      shadow: 'rgba(50, 28, 8, 0.45)',
    }
    rayOpacity = 1.15
  } else if (alt < 60) {
    palette = {
      bloom: 'rgba(255, 230, 180, 0.68)',
      core: 'rgba(255, 222, 160, 0.9)',
      mid: 'rgba(255, 210, 140, 0.48)',
      ambient: 'rgba(255, 225, 170, 0.06)',
      shadow: 'rgba(55, 35, 12, 0.38)',
    }
    rayOpacity = 1.05
  } else {
    palette = {
      bloom: 'rgba(255, 242, 210, 0.62)',
      core: 'rgba(255, 238, 195, 0.9)',
      mid: 'rgba(255, 228, 170, 0.42)',
      ambient: 'rgba(255, 240, 200, 0.05)',
      shadow: 'rgba(60, 40, 15, 0.32)',
    }
    rayOpacity = 0.95
  }

  return {
    source: { x: sourceX + '%', y: alt < 6 ? '2%' : '-2%' },
    rotation,
    palette,
    rays: makeRays(rayOpacity),
    shadowVRotation,
    shadowHTop,
    shadowOpacity: 0.5,
    shadowVisible: true,
    frameWidth, frameHeight, frameSkew,
    frameOpacity: 0.35,
    altitude: alt,
    azimuth: az,
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

// 根据当前时间获取时隙索引(0-95)
export function currentSlotIndex() {
  const now = new Date()
  const h = now.getHours()
  const m = now.getMinutes()
  return Math.floor((h * 60 + m) / 15)
}

// 兼容旧调用(无 sunInfo 时回退到固定时段)
export function getSunBeams() { return makeRays(0.9) }
export function getSunBeam() { return getSunBeams()[0] }
export function getWindowBeam() {
  const h = new Date().getHours()
  if (h >= 6 && h < 9) {
    // 晨光:从左上射入地板
    return {
      bg: 'linear-gradient(150deg, rgba(255,240,200,0.5) 0%, rgba(255,240,200,0.2) 30%, transparent 60%)',
      opacity: 0.7,
    }
  }
  if (h >= 9 && h < 16) {
    // 正午:从正上方射入
    return {
      bg: 'linear-gradient(180deg, rgba(255,255,240,0.4) 0%, rgba(255,255,240,0.1) 50%, transparent 80%)',
      opacity: 0.5,
    }
  }
  if (h >= 16 && h < 19) {
    // 黄昏:从右上射入
    return {
      bg: 'linear-gradient(210deg, rgba(212,140,80,0.5) 0%, rgba(212,140,80,0.2) 30%, transparent 60%)',
      opacity: 0.7,
    }
  }
  // 夜:无阳光,窗内深色
  return { bg: 'transparent', opacity: 0 }
}

export function isLampOn() {
  const h = new Date().getHours()
  return h < 6 || h >= 18
}

export function getSeason() {
  const m = new Date().getMonth()
  if (m >= 2 && m <= 4) return 'spring'
  if (m >= 5 && m <= 7) return 'summer'
  if (m >= 8 && m <= 10) return 'autumn'
  return 'winter'
}

export function getWindowDecor() {
  const light = getWindowLight()
  const season = getSeason()
  if (light.scene === 'night') return { icon: '🌙', label: 'night' }
  if (light.scene === 'dawn') return { icon: '🌤️', label: 'dawn' }
  if (light.scene === 'dusk' || light.scene === 'evening') return { icon: '🌇', label: 'sunset' }
  if (season === 'spring') return { icon: '🌸', label: 'spring' }
  if (season === 'summer') return { icon: '☀️', label: 'summer' }
  if (season === 'autumn') return { icon: '🍂', label: 'autumn' }
  if (season === 'winter') return { icon: '❄️', label: 'winter' }
  return { icon: '', label: '' }
}
