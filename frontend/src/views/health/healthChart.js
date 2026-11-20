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

/** 指针读数的下标换算:viewBox x → 最近的数据下标(超出两端夹到端点);无数据返回 -1 */
export function indexAtX(x, n, mode = 'line') {
  const count = Number(n) || 0
  if (!count) return -1
  if (count === 1) return 0
  const px = Number(x)
  if (mode === 'bar') {
    const slot = innerW() / count
    const i = Math.floor((px - CHART.padL) / slot)
    return Math.max(0, Math.min(count - 1, Number.isFinite(i) ? i : 0))
  }
  const ratio = (px - CHART.padL) / innerW()
  const i = Math.round(ratio * (count - 1))
  return Math.max(0, Math.min(count - 1, Number.isFinite(i) ? i : 0))
}

/** 柱状槽位中心(读数竖线用,缺数据的那天也能定位) */
export function slotCenterX(i, n) {
  const count = Number(n) || 0
  if (!count) return CHART.padL
  const slot = innerW() / count
  return CHART.padL + slot * i + slot / 2
}

/** 屏幕 clientX → viewBox 内的 x(SVG 等比缩放;rect 取不到时按左内边算) */
export function viewBoxX(clientX, rect, viewW = CHART.w) {
  if (!rect || !rect.width) return CHART.padL
  return ((Number(clientX) - rect.left) / rect.width) * viewW
}

/** 有值点的平均(跳过 null/非数);没有值返回 null(不补 0) */
export function avgOf(values) {
  const list = (values || [])
    .filter((v) => v != null && v !== '')
    .map(Number)
    .filter((v) => Number.isFinite(v))
  return list.length ? list.reduce((a, b) => a + b, 0) / list.length : null
}

/** 柱状:null 不画(缺数据就是不画,不伪造 0) */ export function bars(values, max, ratio = 0.55) {
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

// ========== 睡眠进度轴(按钟点铺的夜视图) ==========

/** 泳道画布:四行阶段自下而上「深睡 → 核心 → 快速眼动 → 清醒」,左侧一行文字标签 */
export const LANE = { w: 360, h: 160, padL: 48, padR: 10, padT: 8, padB: 22, rowH: 30, rowGap: 2 }
/** 行顺序自上而下(SVG 的 y 向下长),所以清醒在最上、深睡在最下 */
export const LANE_ROWS = ['AWAKE', 'REM', 'LIGHT', 'DEEP']

const MIN_MS = 60 * 1000
const HOUR_MS = 60 * MIN_MS
const LANE_INNER_W = () => LANE.w - LANE.padL - LANE.padR

const timeOf = (v) => {
  const ms = Date.parse(v)
  return Number.isFinite(ms) ? ms : null
}
const floorHour = (ms) => {
  const d = new Date(ms)
  d.setMinutes(0, 0, 0)
  return d.getTime()
}
const ceilHour = (ms) => {
  const d = new Date(ms)
  d.setMinutes(0, 0, 0)
  return d.getTime() < ms ? d.getTime() + HOUR_MS : d.getTime()
}
const clamp01 = (v) => Math.max(0, Math.min(1, Number.isFinite(v) ? v : 0))

/**
 * 进度轴横轴:入睡前 30 分钟 → 起床后 30 分钟,两端再向外扩到整点
 * (22:35 入睡 → 22:00 起轴;07:05 起床 → 08:00 收轴)。
 * 入参缺失时用分期区间自身的最早/最晚兜底;没有可用时间返回 null(页面显示空态)。
 * @return {{from:number,to:number,span:number}|null} 绝对毫秒
 */
export function sleepAxis(startTime, endTime, stages) {
  const list = (stages || []).filter((s) => s && s.startTime && s.endTime)
  const starts = list.map((s) => timeOf(s.startTime)).filter((v) => v != null)
  const ends = list.map((s) => timeOf(s.endTime)).filter((v) => v != null)
  const t0 = timeOf(startTime) ?? (starts.length ? Math.min(...starts) : null)
  const t1 = timeOf(endTime) ?? (ends.length ? Math.max(...ends) : null)
  if (t0 == null || t1 == null || t1 <= t0) return null
  const from = floorHour(t0 - 30 * MIN_MS)
  const to = ceilHour(t1 + 30 * MIN_MS)
  return { from, to, span: to - from }
}

/** 整点刻度:跨度超 12 小时按 2 小时一格,8px 字才不会挤成一团 */
export function sleepAxisTicks(axis) {
  if (!axis || !axis.span) return []
  const stepH = axis.span > 12 * HOUR_MS ? 2 : 1
  const out = []
  for (let t = axis.from; t <= axis.to; t += stepH * HOUR_MS) {
    const d = new Date(t)
    out.push({
      x: LANE.padL + LANE_INNER_W() * ((t - axis.from) / axis.span),
      label: `${String(d.getHours()).padStart(2, '0')}:00`,
    })
  }
  return out
}

/** 四行标签(纯几何,配色由页面按 code 取 CSS 变量) */
export function sleepLaneRows() {
  return LANE_ROWS.map((code, i) => ({
    code,
    y: LANE.padT + i * (LANE.rowH + LANE.rowGap),
  }))
}

/**
 * 分期区间 → 泳道矩形。只画四行已知阶段(在床/未分期不占行,免得把整夜糊成一条),
 * 超出横轴的部分按轴两端裁掉;合法区间起点=终点(上游已跳过)这里也不画。
 */
export function sleepLaneBars(stages, axis) {
  if (!axis || !axis.span) return []
  const inner = LANE_INNER_W()
  const out = []
  for (const s of stages || []) {
    const code = String(s?.stage || '').toUpperCase()
    const row = LANE_ROWS.indexOf(code)
    if (row < 0) continue
    const a = timeOf(s.startTime)
    const b = timeOf(s.endTime)
    if (a == null || b == null || b <= a) continue
    const x0 = LANE.padL + inner * clamp01((a - axis.from) / axis.span)
    const x1 = LANE.padL + inner * clamp01((b - axis.from) / axis.span)
    if (x1 <= x0) continue
    out.push({
      code,
      x: x0,
      w: Math.max(1.2, x1 - x0),
      y: LANE.padT + row * (LANE.rowH + LANE.rowGap),
      h: LANE.rowH,
      from: a,
      to: b,
      minutes: s.durationMin,
    })
  }
  return out
}

/** 进度轴 x → 绝对毫秒(超出轴两端夹到端点);轴缺失返回 null */
export function timeAtX(x, axis) {
  if (!axis || !axis.span) return null
  return axis.from + clamp01((Number(x) - LANE.padL) / LANE_INNER_W()) * axis.span
}

/** 某一时刻落在哪段分期里(空档/轴外返回 null,页面据此显示「无数据」) */
export function stageAt(bars, ms) {
  if (ms == null) return null
  return (bars || []).find((b) => ms >= b.from && ms <= b.to) || null
}

/** 18:00 起算的分钟 → HH:MM(跨午夜自动绕回) */
const clockFromOffset = (off) => {
  const mins = (18 * 60 + off) % (24 * 60)
  return `${String(Math.floor(mins / 60)).padStart(2, '0')}:${String(mins % 60).padStart(2, '0')}`
}

const endOffset = (endTime) => {
  const m = String(endTime || '').match(/T(\d{2}):(\d{2})/)
  if (!m) return null
  let mins = Number(m[1]) * 60 + Number(m[2])
  if (mins < 18 * 60) mins += 24 * 60
  return mins - 18 * 60
}

/** 若干晚的平均入睡 / 起床钟点(按 18:00 起点折算,跨午夜不会算歪) */
export function avgClock(nights) {
  const beds = []
  const wakes = []
  for (const n of nights || []) {
    const bed = bedtimeOffset(n?.startTime)
    if (bed != null) beds.push(bed)
    const wake = endOffset(n?.endTime)
    if (wake != null) wakes.push(wake)
  }
  const mean = (list) => (list.length ? Math.round(list.reduce((a, b) => a + b, 0) / list.length) : null)
  const bed = mean(beds)
  const wake = mean(wakes)
  return {
    bed: bed == null ? null : clockFromOffset(bed),
    wake: wake == null ? null : clockFromOffset(wake),
    nights: beds.length,
  }
}
