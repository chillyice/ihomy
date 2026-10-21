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
