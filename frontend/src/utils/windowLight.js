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

// 体积光场景:基于日出日落时间驱动旋转生命周期
// sunInfo: { sunrise, sunset, solarNoon, slots: [{time, altitude, azimuth}, ...] }
// slotIndex: 0-287(每 5 分钟一个时隙)
// 旋转:日出时 -90°,正午 0°,日落 +90°,夜间待命在日出角度
// 亮斑:夜黑→凌晨黄→清晨白→日间透明→傍晚橙→日落红→深夜黑
// 阴影强度:夜间最深(1),正午最浅(0.3),正弦过渡

function parseTimeToMinutes(timeStr) {
  if (!timeStr) return null
  const parts = String(timeStr).split(':').map(Number)
  if (parts.length < 2 || isNaN(parts[0]) || isNaN(parts[1])) return null
  return parts[0] * 60 + parts[1]
}

function lerpColor(c1, c2, t) {
  const r = Math.round(c1[0] + (c2[0] - c1[0]) * t)
  const g = Math.round(c1[1] + (c2[1] - c1[1]) * t)
  const b = Math.round(c1[2] + (c2[2] - c1[2]) * t)
  return `rgba(${r},${g},${b},1)`
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

  // 旋转:线性 -90°(日出)→ 0°(正午)→ +90°(日落),夜间 hold
  const shadowVRotation = (dayProgress - 0.5) * 180
  // 光柱旋转:与阴影同角度
  const lightRotation = shadowVRotation

  // 光源水平位置:由方位角驱动,夜间 hold 在日出位置(左侧 7.5%)
  const sourceX = isNight
    ? 7.5
    : Math.max(7.5, Math.min(92.5, ((az - 90) / 180) * 100))

  // 内框横条 top:太阳越高越靠近顶部
  const shadowHTop = Math.max(5, Math.min(85, 80 - Math.max(0, alt) * 0.8))

  // 顶框微移:太阳越高顶框越低(窗口视觉变高),小范围 ±2vh,不影响与内横框/底框的间距
  const frameTopOffset = isNight ? 0 : (Math.max(0, alt) - 45) * 0.045

  // 阴影强度:夜间 0.7,正午 0.3,日出日落 0.7(不超过 70%)
  const shadowIntensity = isNight ? 0.7 : 0.7 - Math.sin(dayProgress * Math.PI) * 0.4
  // 阴影颜色:夜间深蓝黑,日间纯黑
  const shadowColor = isNight ? 'rgb(8,12,28)' : 'rgb(0,0,0)'

  // 亮斑颜色与不透明度:夜黑→凌晨黄→清晨白→日间透明→傍晚橙→日落红→深夜黑
  let brightSpotColor, brightSpotOpacity
  if (isNight) {
    brightSpotColor = 'rgb(8,12,28)'
    brightSpotOpacity = 0.7
  } else if (dayProgress < 0.1) {
    // 凌晨:深蓝黑→黄
    const t = dayProgress / 0.1
    brightSpotColor = lerpColor([8, 12, 28], [255, 200, 80], t)
    brightSpotOpacity = 0.7
  } else if (dayProgress < 0.3) {
    // 清晨:黄→白,不透明度 0.7→0.1
    const t = (dayProgress - 0.1) / 0.2
    brightSpotColor = lerpColor([255, 200, 80], [255, 250, 230], t)
    brightSpotOpacity = 0.7 - t * 0.6
  } else if (dayProgress < 0.7) {
    // 日间:白→透明
    const t = (dayProgress - 0.3) / 0.4
    brightSpotColor = 'rgba(255,250,230,1)'
    brightSpotOpacity = 0.1 * (1 - t)
  } else if (dayProgress < 0.9) {
    // 傍晚:透明→橙,不透明度 0→0.7
    const t = (dayProgress - 0.7) / 0.2
    brightSpotColor = lerpColor([255, 250, 230], [255, 140, 50], t)
    brightSpotOpacity = t * 0.7
  } else {
    // 日落:橙→深蓝黑,不透明度 0.7
    const t = (dayProgress - 0.9) / 0.1
    brightSpotColor = lerpColor([255, 140, 50], [8, 12, 28], t)
    brightSpotOpacity = 0.7
  }

  // 光柱颜色:夜间全透明(不发光),日间基于高度角
  let palette, rayBaseOpacity
  if (isNight) {
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

  // 光柱不透明度:夜间 0(不发光),日间正弦过渡(正午最强)
  const rayOpacity = isNight ? 0 : Math.sin(dayProgress * Math.PI) * rayBaseOpacity

  // 光源整体不透明度(控制 bloom 辉光):夜间 0,日出日落 0,正午 1,正弦过渡
  const lightOpacity = isNight ? 0 : Math.sin(dayProgress * Math.PI)

  // 反光层:内容组件被阳光照亮的轻微高光(soft-light,夜间 0)
  const reflectionOpacity = isNight ? 0 : Math.sin(dayProgress * Math.PI) * 0.22

  // 台灯:傍晚开始时开(dayProgress≥0.9 或夜间),清晨结束时关(dayProgress>0.1)
  const lampOpacity = (isNight || dayProgress >= 0.9 || dayProgress <= 0.1) ? 1 : 0

  return {
    source: { x: sourceX + '%', y: '-15%' },
    rotation: lightRotation,
    palette,
    rays: makeRays(rayOpacity),
    shadowVRotation,
    shadowHTop,
    frameTopOffset,
    shadowIntensity,
    shadowColor,
    brightSpotColor,
    brightSpotOpacity,
    reflectionOpacity,
    lightOpacity,
    lampOpacity,
    altitude: alt,
    azimuth: az,
    isNight,
    dayProgress,
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

// 根据当前时间获取时隙索引(0-287)
export function currentSlotIndex() {
  const now = new Date()
  const h = now.getHours()
  const m = now.getMinutes()
  return Math.floor((h * 60 + m) / 5)
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
