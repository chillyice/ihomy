// 光线与天气:基于太阳位置(高度角/方位角/日出日落)生成光影参数
// 被 useSunLight.js 调用,提供全页面共享的光影状态

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

  // 窗角:太阳直射窗户的角度(90°=正对窗户,0°=平行窗户无直射)
  const windowAngle = 90 - Math.abs(az - 180)
  // 是否有直射光:白天 + 窗角>0 + 太阳在地平线上
  const hasDirectLight = !isNight && windowAngle > 0 && alt > 0

  // 旋转:基于方位角驱动,az=90°开始(-90°)→ az=180°正中(0°)→ az=270°结束(+90°)
  // 窗角≤0(az<90 或 az>270)时 hold 在对应端点,无直射光不旋转
  let shadowVRotation
  if (hasDirectLight) {
    shadowVRotation = az - 180
  } else {
    shadowVRotation = az <= 90 ? -90 : 90
  }
  const lightRotation = shadowVRotation

  // 光源水平位置:由方位角驱动,夜间 hold 在日出位置(左侧 7.5%)
  const sourceX = isNight
    ? 7.5
    : Math.max(7.5, Math.min(92.5, ((az - 90) / 180) * 100))

  // 内框横条 top:太阳越高越靠近顶部
  const shadowHTop = Math.max(5, Math.min(85, 80 - Math.max(0, alt) * 0.8))

  // 顶框随太阳高度移动:太阳越高顶框越下移(窗口视觉变窄),范围 ±8vh
  const frameTopOffset = isNight ? 0 : -(Math.max(0, alt) - 45) * 0.18

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

  // 光源整体不透明度(控制 bloom 辉光):无直射光时 0,正弦过渡
  const lightOpacity = hasDirectLight ? Math.sin(dayProgress * Math.PI) : 0

  // 反光层:内容组件被阳光照亮的轻微高光(soft-light,无直射光时 0)
  const reflectionOpacity = hasDirectLight ? Math.sin(dayProgress * Math.PI) * 0.22 : 0

  // 台灯:太阳方位角≥270°(日落西方)开灯,≥90°且<270°(日出东方到日落前)关灯
  const lampOpacity = (az >= 270 || az < 90 || isNight) ? 1 : 0

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

// 根据当前时间获取时隙索引(0-287)
export function currentSlotIndex() {
  const now = new Date()
  const h = now.getHours()
  const m = now.getMinutes()
  return Math.floor((h * 60 + m) / 5)
}
