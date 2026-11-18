// 健康页图表几何(纯函数,不引图表库):柱状 / 折线(+最小-最大带) / 入睡时刻散点。
// 与 views/ops/opsChart.js 同口径:固定 viewBox 由容器等比缩放,坐标在这里一次算完,模板只取值。
// viewBox 取 360×150(手机宽度量级):桌面端由容器 max-width 放大,文字不会缩到读不出。

export const CHART = { w: 360, h: 150, padL: 34, padR: 10, padT: 12, padB: 24 }

const innerW = () => CHART.w - CHART.padL - CHART.padR
const innerH = () => CHART.h - CHART.padT - CHART.padB

/** 纵轴上限:1/2/5×10^n 取「刚好装下」的整刻度,避免出现 137、213 这种怪刻度 */
export function axisMax(values, floor = 1) {
  const list = (values || []).map((v) => (Number.isFinite(v) ? v : 0))
  const max = Math.max(floor, ...list)
  const pow = Math.pow(10, Math.floor(Math.log10(max)))
  const n = max / pow
  const step = n <= 1 ? 1 : n <= 2 ? 2 : n <= 5 ? 5 : 10
  return +(step * pow).toFixed(2)
}

export const xAt = (i, n) => (n <= 1 ? CHART.padL + innerW() / 2 : CHART.padL + (innerW() * i) / (n - 1))
export const yAt = (v, max) => CHART.h - CHART.padB - (innerH() * Math.max(0, Math.min(v, max))) / (max || 1)

/** 横向网格 + 纵轴刻度(4 档) */
export function gridLines(max) {
  return [0, 0.25, 0.5, 0.75, 1].map((r) => {
    const v = max * r
    return { y: yAt(v, max), label: max >= 100 ? Math.round(v) : v.toFixed(max >= 10 ? 0 : 1) }
  })
}

/** 横轴标签采样:约 count 个 + 恒含首末 */
export function xTicks(labels, count = 7) {
  const list = labels || []
  const n = list.length
  if (!n) return []
  const step = Math.max(1, Math.ceil(n / count))
  const idx = []
  for (let i = 0; i < n; i += step) idx.push(i)
  if (idx[idx.length - 1] !== n - 1) idx.push(n - 1)
  return idx.map((i) => ({ x: xAt(i, n), label: list[i] }))
}

/** 柱状:null 不画(缺数据就是不画,不伪造 0) */
export function bars(values, max, ratio = 0.55) {
  const list = values || []
  const n = list.length
  const slot = n ? innerW() / n : innerW()
  const w = Math.max(4, slot * ratio)
  const out = []
  list.forEach((v, i) => {
    if (v == null || !Number.isFinite(Number(v))) return
    const y = yAt(Number(v), max)
    out.push({ i, x: CHART.padL + slot * i + (slot - w) / 2, y, w, h: CHART.h - CHART.padB - y })
  })
  return out
}

/** 折线:缺数据处断开,不把两段连成一条假直线 */
export function linePaths(values, max) {
  const list = values || []
  const n = list.length
  const out = []
  let cur = []
  const flush = () => {
    if (cur.length > 1) out.push(`M ${cur.join(' L ')}`)
    cur = []
  }
  list.forEach((v, i) => {
    if (v == null || !Number.isFinite(Number(v))) {
      flush()
      return
    }
    cur.push(`${xAt(i, n).toFixed(1)} ${yAt(Number(v), max).toFixed(1)}`)
  })
  flush()
  return out
}

/** 折线端点(单点数据也能看到位置) */
export function dots(values, max) {
  const list = values || []
  const n = list.length
  const out = []
  list.forEach((v, i) => {
    if (v == null || !Number.isFinite(Number(v))) return
    out.push({ i, x: xAt(i, n), y: yAt(Number(v), max) })
  })
  return out
}

/** 带最小/最大值的点(心率小时桶):min-max 带,缺任一端的点不参与成段 */
export function bandPaths(points, max) {
  const list = points || []
  const n = list.length
  const runs = []
  let cur = []
  list.forEach((p, i) => {
    if (p == null || p.min == null || p.max == null) {
      if (cur.length > 1) runs.push(cur)
      cur = []
      return
    }
    cur.push(i)
  })
  if (cur.length > 1) runs.push(cur)
  return runs.map((idx) => {
    const top = idx.map((i) => `${xAt(i, n).toFixed(1)} ${yAt(Number(list[i].max), max).toFixed(1)}`)
    const bottom = idx
      .slice()
      .reverse()
      .map((i) => `${xAt(i, n).toFixed(1)} ${yAt(Number(list[i].min), max).toFixed(1)}`)
    return `M ${top.join(' L ')} L ${bottom.join(' L ')} Z`
  })
}

// ========== 睡眠 ==========

/** 入睡时刻折算:18:00 起算的分钟(18:00→0,次日 06:00→720);
 *  06:00–17:59 属于白天小睡,不进「入睡」散点(返回 null,不硬塞到窗口边缘) */
export const BED_MAX = 720
export function bedtimeOffset(startTime) {
  const m = String(startTime || '').match(/T(\d{2}):(\d{2})/)
  if (!m) return null
  const mins = Number(m[1]) * 60 + Number(m[2])
  if (mins >= 18 * 60) return mins - 18 * 60
  if (mins <= 6 * 60) return mins + 6 * 60
  return null
}

/** 入睡散点的纵轴刻度:18:00 → 06:00,每 2 小时一格 */
export function bedtimeTicks() {
  const out = []
  for (let v = 0; v <= BED_MAX; v += 120) {
    const mins = (18 * 60 + v) % (24 * 60)
    const hh = String(Math.floor(mins / 60)).padStart(2, '0')
    const mm = String(mins % 60).padStart(2, '0')
    out.push({ v, label: `${hh}:${mm}` })
  }
  return out
}

export function bedtimeDots(nights) {
  const list = nights || []
  const n = list.length
  return list
    .map((s, i) => {
      const v = bedtimeOffset(s.startTime)
      if (v == null) return null
      return { i, x: xAt(i, n), y: yAt(v, BED_MAX), label: s.sleepDate }
    })
    .filter(Boolean)
}

/** 横向 100% 堆叠条:传 [{code, minutes}] → [{code, pct, x}] */
export function stackBar(parts) {
  const total = (parts || []).reduce((a, p) => a + (p.minutes || 0), 0)
  if (!total) return []
  let acc = 0
  return parts
    .filter((p) => p.minutes > 0)
    .map((p) => {
      const pct = (p.minutes / total) * 100
      const seg = { code: p.code, minutes: p.minutes, pct, x: acc }
      acc += pct
      return seg
    })
}

/**
 * 分期时间轴(某晚明细):把若干区间铺成 0~100 的横向进度条。
 * 起点取最早开始,终点取最晚结束;空档不画(有缺口说明原始数据本身不连续)。
 */
export function timelineSegments(stages, start, end) {
  const list = (stages || []).filter((s) => s && s.startTime && s.endTime)
  if (!list.length) return []
  const t0 = start ? new Date(start).getTime() : Math.min(...list.map((s) => new Date(s.startTime).getTime()))
  const t1 = end ? new Date(end).getTime() : Math.max(...list.map((s) => new Date(s.endTime).getTime()))
  const span = Math.max(1, t1 - t0)
  return list.map((s) => {
    const a = new Date(s.startTime).getTime()
    const b = new Date(s.endTime).getTime()
    return {
      code: s.stage,
      left: ((a - t0) / span) * 100,
      width: Math.max(0.4, ((b - a) / span) * 100),
      minutes: s.durationMin,
    }
  })
}
