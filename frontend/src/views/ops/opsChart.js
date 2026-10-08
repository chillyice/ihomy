// 运维页折线/饼图共用几何(纯函数):天气与 AI 统计两标签共用,条形图几何各标签自持。
export const chartW = 800
export const chartH = 280
export const padL = 40
export const padR = 20
export const padB = 30
export const padT = 20

export const yMaxFor = (data) => {
  const mx = Math.max(...data.map((d) => d.total), 1)
  return mx <= 5 ? 5 : Math.ceil(mx / 5) * 5
}
export const yTicksFor = (ymax) => {
  const ticks = []
  for (let i = 0; i <= 4; i++) {
    const val = Math.round((ymax * i) / 4)
    ticks.push({ y: chartH - padB - ((chartH - padB - padT) * i) / 4, label: val })
  }
  return ticks
}
export const xPosFor = (data, i) => {
  const n = data.length
  if (n <= 1) return padL
  return padL + ((chartW - padL - padR) * i) / (n - 1)
}
// 采样标签:约 8 个 + 恒含首末;坐标用真实数据下标计算(修标签全挤左侧的 bug)
export const xLabelsFor = (data) => {
  const n = data.length
  if (n === 0) return []
  const step = Math.max(1, Math.ceil(n / 8))
  const indices = []
  for (let i = 0; i < n; i += step) indices.push(i)
  if (indices[indices.length - 1] !== n - 1) indices.push(n - 1)
  return indices.map((i) => ({ x: xPosFor(data, i), label: data[i].time_bucket }))
}
export const linePointsFor = (data, ymax, vals) => {
  const n = vals.length
  if (n === 0) return ''
  return vals
    .map((v, i) => {
      const x = n <= 1 ? padL : padL + ((chartW - padL - padR) * i) / (n - 1)
      const y = chartH - padB - ((chartH - padB - padT) * v) / ymax
      return `${x},${y}`
    })
    .join(' ')
}
// 悬浮提示:比例定位 + 边缘 clamp,防止 tooltip 超出容器
export const tooltipStyleFor = (data, idx) => {
  if (idx < 0) return {}
  const ratio = Math.min(Math.max(xPosFor(data, idx) / chartW, 0.15), 0.85)
  return { left: ratio * 100 + '%' }
}

export const PIE_COLORS = [
  '#b88c6e',
  '#a87c5e',
  '#c4a884',
  '#8a6d3b',
  '#b04a3a',
  '#d4b298',
  '#6b8a6b',
  '#e0862f',
  '#4a90d9',
  '#9b8ec4',
  '#c97474',
  '#9a9a9a',
]
// 饼图(纯函数):标签防重叠——相邻小占比扇区引导线终点彼此靠近,按左右分侧自上而下强制最小垂直间距,
// 引导线随标签位移自然弯折(扇区外缘 → 标签尖端 → 水平短线);labelOf 由调用方提供切片名。
export const buildPieSlices = (dist, labelOf) => {
  const total = dist.reduce((a, d) => a + (d.count || 0), 0)
  if (!total) return []
  const cx = 240,
    cy = 130,
    r = 64
  const TIP = r + 22 // 标签尖端到圆心的水平距离
  const MIN_GAP = 15,
    MIN_Y = 12,
    MAX_Y = 244
  let angle = -Math.PI / 2
  const slices = dist.map((d, i) => {
    const frac = (d.count || 0) / total
    const a2 = angle + frac * Math.PI * 2
    const large = frac > 0.5 ? 1 : 0
    const x1 = cx + r * Math.cos(angle),
      y1 = cy + r * Math.sin(angle)
    const x2 = cx + r * Math.cos(a2),
      y2 = cy + r * Math.sin(a2)
    const mid = (angle + a2) / 2
    const cos = Math.cos(mid),
      sin = Math.sin(mid)
    const right = cos >= 0
    const slice = {
      // 单一类型占 100% 时画整圆
      path:
        frac >= 0.999
          ? `M ${cx - r} ${cy} a ${r} ${r} 0 1 0 ${2 * r} 0 a ${r} ${r} 0 1 0 -${2 * r} 0 Z`
          : `M ${cx} ${cy} L ${x1} ${y1} A ${r} ${r} 0 ${large} 1 ${x2} ${y2} Z`,
      edgeX: cx + (r + 3) * cos,
      edgeY: cy + (r + 3) * sin,
      tipX: cx + (right ? TIP : -TIP),
      naturalY: cy + (r + 12) * sin,
      right,
      color: PIE_COLORS[i % PIE_COLORS.length],
      label: `${labelOf(d)} ${Math.round(frac * 1000) / 10}%`,
    }
    angle = a2
    return slice
  })
  for (const side of [true, false]) {
    const group = slices.filter((s) => s.right === side).sort((a, b) => a.naturalY - b.naturalY)
    let prev = -Infinity
    for (const s of group) {
      s.labelY = Math.max(Math.min(s.naturalY, MAX_Y), prev + MIN_GAP, MIN_Y)
      prev = s.labelY
    }
    for (let k = group.length - 1; k >= 0 && prev > MAX_Y; k--) {
      group[k].labelY = Math.min(group[k].labelY, prev - MIN_GAP)
      prev = group[k].labelY
    }
  }
  return slices.map((s) => ({
    path: s.path,
    color: s.color,
    label: s.label,
    line: `M ${s.edgeX} ${s.edgeY} L ${s.tipX} ${s.labelY} L ${s.tipX + (s.right ? 10 : -10)} ${s.labelY}`,
    labelX: s.tipX + (s.right ? 13 : -13),
    labelY: s.labelY + 4,
    anchor: s.right ? 'start' : 'end',
  }))
}
