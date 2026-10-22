// 个人主页「日月吊偶」的天球投影与月相路径。
// 观察者面朝正南,舞台就是面前一块竖直背景板,日月按正交投影落板:
//   x = cos(alt)·sin(az)   东在左、西在右
//   y = sin(alt)           地平线在下、天顶在上
// 高度角 ≤ 0(地平线以下)不显示。
// ponytail: 正交投影丢掉南北深度,正北与正南同高度会重合;用 depth 做 ±6% 缩放区分,
// 若日后要严格区分,换成把方位角整圈展开的等距圆柱投影。
const D2R = Math.PI / 180

/** 高度角/方位角(度)→ 舞台百分比坐标(x 0~100, y 0~100,0 为天顶/顶线) */
export const projectBody = (altitude, azimuth) => {
  const alt = Number(altitude)
  const az = Number(azimuth)
  if (!Number.isFinite(alt) || !Number.isFinite(az)) {
    return { visible: false, x: 50, y: 100, depth: 0, scale: 1 }
  }
  // 未升起/已落下仍按方位角停在东/西边缘的地平线上,避免从舞台中央滑入滑出
  const visible = alt > 0
  const a = (visible ? alt : 0) * D2R
  const depth = -Math.cos(a) * Math.cos(az * D2R) // 正南(板前)为正
  return {
    visible,
    x: 50 - Math.cos(a) * Math.sin(az * D2R) * 44,
    y: visible ? 100 - Math.sin(alt * D2R) * 100 : 100,
    depth,
    scale: 1 + depth * 0.06,
  }
}

/**
 * 全天轨迹 → 若干条 SVG path(视口 0~100):
 * 只连接地平线以上的采样点,落到地平线下就断开,日月各回各的弧。
 * 输入是后端 astroDay 的 [{ t, alt, az }];非法点直接跳过。
 */
export const projectTrack = (points) => {
  const paths = []
  let d = ''
  for (const point of Array.isArray(points) ? points : []) {
    const p = projectBody(point?.alt, point?.az)
    if (!p.visible) {
      if (d) paths.push(d)
      d = ''
      continue
    }
    d += `${d ? ' L' : 'M'}${p.x.toFixed(2)} ${p.y.toFixed(2)}`
  }
  if (d) paths.push(d)
  return paths
}

/**
 * 月相亮部路径(以圆盘中心为原点,SVG viewBox 用 -r..r):
 * phase 0=新月、0.25=上弦(右半亮)、0.5=满月、0.75=下弦(左半亮)。
 * 亮部 = 外缘半圆 + 明暗界线(半椭圆),半椭圆短轴 = r·|cos(2π·phase)|,
 * 月亮盈(0~0.5)亮部在右,亏(0.5~1)在左。
 */
export const moonLitPath = (phase, r = 50) => {
  const p = (((Number(phase) || 0) % 1) + 1) % 1
  const minor = Math.abs(Math.cos(2 * Math.PI * p)) * r
  const outerSweep = p < 0.5 ? 1 : 0
  const terminatorSweep = p < 0.5 ? (p < 0.25 ? 0 : 1) : p < 0.75 ? 0 : 1
  const n = Math.round(minor * 100) / 100
  return `M 0 ${-r} A ${r} ${r} 0 0 ${outerSweep} 0 ${r} A ${n} ${r} 0 0 ${terminatorSweep} 0 ${-r} Z`
}

/**
 * 光源(太阳,夜里换成月亮)→ 舞台光影变量(绑到 .cy-scene 的 CSS 变量):
 * 投影方向与光源左右相反,太阳越低影子越长、越暖;高度角 ≤ 0 时不投影,只剩夜色。
 * moon=true 时冷色补光、亮度减半 —— 月光照不出正午那种硬影子。
 */
export const stageLight = (altitude, azimuth, { moon = false } = {}) => {
  const alt = Number(altitude)
  const p = projectBody(altitude, azimuth)
  const lit = Number.isFinite(alt) ? Math.max(0, Math.min(1, alt / 12)) : 0
  const low = Number.isFinite(alt) ? Math.max(0, Math.min(1, 1 - alt / 32)) : 1
  // strength 只管刚出地平线那几个度:太阳贴地时投影还没法从画面底部伸出来
  const strength = Number.isFinite(alt) ? Math.max(0, Math.min(1, alt / 6)) : 0
  const side = (p.x - 50) / 50
  const reach = (moon ? 22 : 56) * (0.22 + 0.78 * low) * strength
  // 封顶 0.62:再暗就看不出是画框里的夜景了
  const night = 0.62 * Math.max(0, Math.min(1, -(Number.isFinite(alt) ? alt : 0) / 14))
  return {
    '--cy-sh-x': `${(-side * reach * 0.85).toFixed(1)}px`,
    '--cy-sh-y': `${(3 + reach * 0.35).toFixed(1)}px`,
    '--cy-sh-blur': `${(3 + low * 5).toFixed(1)}px`,
    '--cy-sh-a': (0.1 + (moon ? 0.08 : 0.22) * lit).toFixed(2),
    '--cy-light-x': `${p.x.toFixed(1)}%`,
    '--cy-light-y': `${Math.max(0, Math.min(100, p.y)).toFixed(1)}%`,
    '--cy-warm-a': (strength * (0.25 + 0.75 * low) * (moon ? 0.08 : 0.3)).toFixed(2),
    '--cy-night': night.toFixed(2),
  }
}

// 天空按太阳高度角分档:深夜 → 晨昏 → 日出/日落 → 金光 → 白昼。
// 文字色不在档位里插值——底色跨过中间灰时两套都会糊,改为按底色亮度二选一。
const SKY_STOPS = [
  { alt: -14, bg: [10, 17, 29], a: [34, 56, 92, 0.5], b: [58, 40, 76, 0.42] },
  { alt: -4, bg: [42, 32, 51], a: [96, 58, 74, 0.46], b: [58, 56, 104, 0.44] },
  { alt: 0, bg: [122, 63, 52], a: [214, 108, 62, 0.46], b: [120, 78, 108, 0.4] },
  { alt: 5, bg: [201, 138, 92], a: [233, 160, 90, 0.5], b: [176, 146, 152, 0.36] },
  { alt: 12, bg: [238, 214, 180], a: [233, 186, 128, 0.52], b: [152, 176, 200, 0.4] },
  { alt: 24, bg: [246, 242, 234], a: [233, 190, 132, 0.55], b: [142, 174, 204, 0.42] },
]
const UI_ON_DARK = [
  '#f1ece4',
  'rgba(241, 236, 228, 0.58)',
  'rgba(241, 236, 228, 0.18)',
  'rgba(241, 236, 228, 0.3)',
  '#e0a06a',
  'rgba(241, 236, 228, 0.12)',
  'rgba(241, 236, 228, 0.26)',
]
const UI_ON_LIGHT = [
  '#201d19',
  'rgba(32, 29, 25, 0.62)',
  'rgba(32, 29, 25, 0.14)',
  'rgba(32, 29, 25, 0.26)',
  '#b96a3d',
  'rgba(32, 29, 25, 0.16)',
  'rgba(32, 29, 25, 0.22)',
]

const mix = (p, q, t) => Math.round(p + (q - p) * t)
const rgba = (c) => `rgba(${c[0]}, ${c[1]}, ${c[2]}, ${c[3]})`

/** 太阳高度角(度)→ 页面天空配色(直接绑成 CSS 变量) */
export const skyStyle = (altitude) => {
  const alt = altitude == null || !Number.isFinite(Number(altitude)) ? 24 : Number(altitude)
  let i = 0
  while (i < SKY_STOPS.length - 2 && alt > SKY_STOPS[i + 1].alt) i += 1
  const p = SKY_STOPS[i]
  const q = SKY_STOPS[i + 1]
  const t = Math.min(1, Math.max(0, (alt - p.alt) / (q.alt - p.alt)))
  const bg = p.bg.map((v, k) => mix(v, q.bg[k], t))
  const a = p.a.map((v, k) => mix(v, q.a[k], t))
  const b = p.b.map((v, k) => mix(v, q.b[k], t))
  // 相对亮度(sRGB → 线性),决定文字用深色还是浅色;阈值取两套对比度相等处附近
  const lin = (v) => (v / 255 <= 0.04045 ? v / 255 / 12.92 : ((v / 255 + 0.055) / 1.055) ** 2.4)
  const lum = 0.2126 * lin(bg[0]) + 0.7152 * lin(bg[1]) + 0.0722 * lin(bg[2])
  const [ink, muted, line, string, accent, moonDark, moonLine] = lum > 0.19 ? UI_ON_LIGHT : UI_ON_DARK
  return {
    '--cy-bg': `rgb(${bg[0]}, ${bg[1]}, ${bg[2]})`,
    '--cy-spot-a': rgba(a),
    '--cy-spot-b': rgba(b),
    '--cy-ink': ink,
    '--cy-muted': muted,
    '--cy-line': line,
    '--cy-string': string,
    '--cy-accent': accent,
    '--cy-moon-dark': moonDark,
    '--cy-moon-line': moonLine,
    'color-scheme': lum > 0.19 ? 'light' : 'dark',
  }
}
