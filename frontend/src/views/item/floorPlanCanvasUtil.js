// 户型图画布纯几何/工具函数:不依赖任何响应式状态,纯输入输出。
// 从 FloorPlanCanvas.vue 逐字抽取,行为保持不变。
import { pointInPoly, onSegment, segsIntersect } from '@/utils/floorPlanGeom'

export const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v))

// ---- 蜡笔笔触(与日记本 doodle.js crayon 同款):确定性抖动,重绘不闪变 ----
export const mulberry32 = (a) => () => {
  a |= 0
  a = (a + 0x6d2b79f5) | 0
  let t = Math.imul(a ^ (a >>> 15), 1 | a)
  t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296
}
// id → 32 位确定性种子(每房间/家具边界抖动固定,缩放/重绘不闪变)
export const seedFromId = (id) => {
  let h = 2166136261
  const s = String(id)
  for (let i = 0; i < s.length; i++) {
    h ^= s.charCodeAt(i)
    h = Math.imul(h, 16777619)
  }
  return h >>> 0
}
// 多边形边界 → 3 遍手绘蜡笔闭合路径(doodle.js crayon 笔触):
// 每条边按长度细分成小段,沿边加小幅正弦曲折(垂直边方向)+ 更小的随机抖动;
// 波浪按空间波长连续(相位沿周长累积),幅度小、频率自然,边不再是生硬的直线。
export const crayonStrokePaths = (poly, seed, amp) => {
  const n = poly.length
  const edgeLens = []
  let perim = 0
  for (let i = 0; i < n; i++) {
    const a = poly[i]
    const b = poly[(i + 1) % n]
    const len = Math.hypot(b.x - a.x, b.y - a.y)
    edgeLens.push(len)
    perim += len
  }
  if (perim < 1) return []
  const paths = []
  for (let pass = 0; pass < 3; pass++) {
    const rnd = mulberry32(seed + pass * 104729)
    // 波长固定(px)而非"周长/波数",大房间小房间都呈现相近的手绘波浪频率;每 pass 相位错开
    const wavelength = 36 + rnd() * 44
    const freq = (Math.PI * 2) / wavelength
    const phase = rnd() * Math.PI * 2
    const pts = []
    let acc = 0
    for (let i = 0; i < n; i++) {
      const a = poly[i]
      const b = poly[(i + 1) % n]
      const dx = b.x - a.x
      const dy = b.y - a.y
      const len = edgeLens[i] || 1
      const px = -dy / len
      const py = dx / len
      const seg = Math.max(2, Math.min(12, Math.round(len / 16)))
      for (let s = 0; s <= seg; s++) {
        const t = s / seg
        const dist = acc + t * len
        const wob = Math.sin(dist * freq + phase) * amp * 0.5
        const jx = (rnd() - 0.5) * amp * 0.18
        const jy = (rnd() - 0.5) * amp * 0.18
        pts.push([a.x + dx * t + px * wob + jx, a.y + dy * t + py * wob + jy])
      }
      acc += len
    }
    let d = `M${pts[0][0]},${pts[0][1]}`
    for (let i = 1; i < pts.length; i++) d += `L${pts[i][0]},${pts[i][1]}`
    d += 'Z'
    paths.push(d)
  }
  return paths
}

// 顶点轴对齐吸附:边贴近横/纵轴时吸附到 prev/next 的 x/y,辅助调节成矩形
export const snapVertex = (prev, next, px, py, threshold = 6) => {
  let sx = px
  let sy = py
  let snapped = false
  if (Math.abs(px - prev.x) < threshold) {
    sx = prev.x
    snapped = true
  }
  if (Math.abs(py - prev.y) < threshold) {
    sy = prev.y
    snapped = true
  }
  if (Math.abs(px - next.x) < threshold) {
    sx = next.x
    snapped = true
  }
  if (Math.abs(py - next.y) < threshold) {
    sy = next.y
    snapped = true
  }
  return { x: sx, y: sy, snapped }
}

// 点吸附到最近顶点(阈值内),返回目标顶点或 null
export const snapPoint = (p, vertices, threshold = 6) => {
  let best = null
  let bestDist = threshold
  for (const v of vertices) {
    const dist = Math.hypot(p.x - v.x, p.y - v.y)
    if (dist < bestDist) {
      bestDist = dist
      best = { x: v.x, y: v.y }
    }
  }
  return best
}

// 邻边吸直:邻边接近水平/垂直时,自由端对齐到拖动端点的 x/y(axis='x' 对齐 x)。
// 判定用拖动前的原始端点(origFixed),对齐值仍用当前拖动端点:边 ab 插入点 c 后拖 ac 时,
// b 侧邻边 c-b 是共线延续段,若用拖动后的 c 位置判定,短子边 + 大位移会把 c-b 误判为"接近垂直"从而把 b 也拽动;
// 用原始共线几何则 ratio≈1 跳过,而直角邻边仍按当前端点对齐(水平位移跟随)。
export const straightenNeighbor = (room, fixedIdx, otherIdx, axis, origFixed) => {
  const current = room.poly[fixedIdx]
  const other = room.poly[otherIdx]
  if (!current || !other) return
  const ref = origFixed || current
  const len = Math.hypot(ref.x - other.x, ref.y - other.y) || 1
  const ratio = axis === 'x' ? Math.abs(ref.x - other.x) / len : Math.abs(ref.y - other.y) / len
  if (ratio < 0.25) {
    if (axis === 'x') other.x = current.x
    else other.y = current.y
  }
}

// 家具矩形控制特征:4 角 + 4 边(移动吸附复用)
export const furnControl = (rect) => ({
  corners: [
    { x: rect.x, y: rect.y },
    { x: rect.x + rect.w, y: rect.y },
    { x: rect.x + rect.w, y: rect.y + rect.h },
    { x: rect.x, y: rect.y + rect.h },
  ],
  vEdges: [
    { val: rect.x, lo: rect.y, hi: rect.y + rect.h, ref: { x1: rect.x, y1: rect.y } },
    { val: rect.x + rect.w, lo: rect.y, hi: rect.y + rect.h, ref: { x1: rect.x + rect.w, y1: rect.y } },
  ],
  hEdges: [
    { val: rect.y, lo: rect.x, hi: rect.x + rect.w, ref: { x1: rect.x, y1: rect.y } },
    { val: rect.y + rect.h, lo: rect.x, hi: rect.x + rect.w, ref: { x1: rect.x, y1: rect.y + rect.h } },
  ],
})

// 角对点吸附:家具角贴到目标角点,返回 {dx,dy,line} 或 null
export const snapRectCorner = (fCorners, targetCorners, SNAP) => {
  let best = null
  let bestDist = SNAP
  for (const fc of fCorners) {
    for (const c of targetCorners) {
      const dx = c.x - fc.x
      const dy = c.y - fc.y
      const dist = Math.hypot(dx, dy)
      if (dist < bestDist) {
        bestDist = dist
        best = { dx, dy, line: { x1: fc.x, y1: fc.y, x2: c.x, y2: c.y } }
      }
    }
  }
  return best
}

// 边对边吸附:家具边贴到目标边,返回 {dx,dy,line} 或 null
export const snapRectEdges = (fV, fH, vEdges, hEdges, SNAP) => {
  let best = null
  let bestDist = SNAP
  for (const fe of fV) {
    for (const re of vEdges) {
      const diff = re.val - fe.val
      if (Math.abs(diff) >= bestDist) continue
      if (fe.hi < re.lo || fe.lo > re.hi) continue
      bestDist = Math.abs(diff)
      best = { dx: diff, dy: 0, line: { x1: fe.ref.x1, y1: fe.ref.y1, x2: fe.ref.x1 + diff, y2: fe.ref.y1 } }
    }
  }
  for (const fe of fH) {
    for (const re of hEdges) {
      const diff = re.val - fe.val
      if (Math.abs(diff) >= bestDist) continue
      if (fe.hi < re.lo || fe.lo > re.hi) continue
      bestDist = Math.abs(diff)
      best = { dx: 0, dy: diff, line: { x1: fe.ref.x1, y1: fe.ref.y1, x2: fe.ref.x1, y2: fe.ref.y1 + diff } }
    }
  }
  return best
}

export const parseGeom = (g) => {
  try {
    const a = JSON.parse(g || '[]')
    return Array.isArray(a) ? a : []
  } catch {
    return []
  }
}

export const buildRoom = (r) => {
  const poly = parseGeom(r.geometry)
  const xs = poly.map((p) => p.x)
  const ys = poly.map((p) => p.y)
  const minX = xs.length ? Math.min(...xs) : 0
  const minY = ys.length ? Math.min(...ys) : 0
  const maxX = xs.length ? Math.max(...xs) : 0
  const maxY = ys.length ? Math.max(...ys) : 0
  const mids = []
  for (let i = 0; i < poly.length; i++) {
    const a = poly[i]
    const b = poly[(i + 1) % poly.length]
    mids.push({ x: (a.x + b.x) / 2, y: (a.y + b.y) / 2, a: i, b: (i + 1) % poly.length })
  }
  return { id: r.id, name: r.name, floor: r.floor, poly, minX, minY, maxX, maxY, cx: (minX + maxX) / 2, cy: (minY + maxY) / 2, mids }
}

export const rectCorners = (f) => [
  { x: f.x, y: f.y },
  { x: f.x + f.w, y: f.y },
  { x: f.x + f.w, y: f.y + f.h },
  { x: f.x, y: f.y + f.h },
]

// ---- 房间重叠检测(编辑态警示) ----
// 点在边上(相邻房间共边/角对角不算重叠)
export const pointStrictlyInside = (p, poly) => {
  for (let i = 0; i < poly.length; i++) {
    if (onSegment(p, poly[i], poly[(i + 1) % poly.length])) return false
  }
  return pointInPoly(p, poly)
}

export const polysOverlap = (pa, pb) => {
  if (pa.length < 3 || pb.length < 3) return false
  if (pa.some((p) => pointStrictlyInside(p, pb))) return true
  if (pb.some((p) => pointStrictlyInside(p, pa))) return true
  for (let i = 0; i < pa.length; i++) {
    for (let j = 0; j < pb.length; j++) {
      if (segsIntersect(pa[i], pa[(i + 1) % pa.length], pb[j], pb[(j + 1) % pb.length])) return true
    }
  }
  return false
}

export const pts = (poly) => poly.map((p) => `${p.x},${p.y}`).join(' ')

// 多边形面积(shoelace,像素²)
export const polyArea = (poly) => {
  let s = 0
  for (let i = 0; i < poly.length; i++) {
    const a = poly[i]
    const b = poly[(i + 1) % poly.length]
    s += a.x * b.y - b.x * a.y
  }
  return Math.abs(s) / 2
}

// 米数格式化(像素→米,保留 2 位)
export const fmtM = (px, scale) => {
  const m = px / (scale || 100)
  return m >= 100 ? m.toFixed(0) : m.toFixed(2)
}

// 边 i 的长度(米)
export const edgeLenM = (r, i, scale) => {
  const a = r.poly[i]
  const b = r.poly[(i + 1) % r.poly.length]
  return fmtM(Math.hypot(b.x - a.x, b.y - a.y), scale)
}

// 联动拖拽时跟随的重合顶点:与拖点重合,从磁吸候选池排除(吸附到自身无意义且会滞后一帧)
export const linkedExcludeVerts = (d) => (d.links && d.links.length ? d.links.map((lk) => ({ id: lk.room.id, idx: lk.idx })) : [])

// 联动拖拽时跟随的房间 id 集合:这些房间的重合边是"自身",边线对齐吸附需整体排除
export const linkedRoomIds = (d) => (d.links && d.links.length ? new Set(d.links.map((lk) => lk.room.id)) : null)
