// 户型图几何工具(画布渲染与页面执行共享)

export const samePt = (p, q, eps = 0.5) => Math.hypot(p.x - q.x, p.y - q.y) < eps

export const pointInPoly = (p, poly) => {
  let inside = false
  for (let i = 0, j = poly.length - 1; i < poly.length; j = i++) {
    if (((poly[i].y > p.y) !== (poly[j].y > p.y)) &&
      (p.x < ((poly[j].x - poly[i].x) * (p.y - poly[i].y)) / (poly[j].y - poly[i].y) + poly[i].x)) inside = !inside
  }
  return inside
}

export const onSegment = (p, a, b) => {
  const cross = (b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x)
  if (Math.abs(cross) > 1e-9) return false
  return p.x >= Math.min(a.x, b.x) - 1e-9 && p.x <= Math.max(a.x, b.x) + 1e-9 &&
    p.y >= Math.min(a.y, b.y) - 1e-9 && p.y <= Math.max(a.y, b.y) + 1e-9
}

export const segsIntersect = (p1, p2, p3, p4) => {
  const d = (a, b, c) => (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)
  const d1 = d(p3, p4, p1); const d2 = d(p3, p4, p2); const d3 = d(p1, p2, p3); const d4 = d(p1, p2, p4)
  return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))
}

// 点到线段的投影(最近点与距离)
export const projectToSegment = (p, a, b) => {
  const abx = b.x - a.x; const aby = b.y - a.y
  const len2 = abx * abx + aby * aby
  let t = 0
  if (len2 > 0) t = ((p.x - a.x) * abx + (p.y - a.y) * aby) / len2
  t = Math.max(0, Math.min(1, t))
  const point = { x: a.x + t * abx, y: a.y + t * aby }
  return { point, dist: Math.hypot(p.x - point.x, p.y - point.y) }
}

// 边界吸附检测(裁剪/粘合共用,纯函数):收集阈值内全部顶点/边候选,再按归属优先级挑。
// 共享墙/共享角在相邻房间各有一份几何,归属错了裁剪预览永远不绿。优先级:
// 1) 已下刀(cutId) → 房间归属优先于候选类型:裁剪房间自己的端点照常吸附顶点;
//    另一房间的端点落在裁剪房间的边上(T 形交界)时,该点仍选为裁剪房间的边点(终点可落共享墙);
// 2) 裁剪房间此处无候选或未下刀 → 顶点优先,再按鼠标所在房间 → 最近边所属房间 → 就近,
//    归属到别的房间时由组件的重锚机制切换目标房间。
// 边中点手柄不作吸附目标。rooms: [{ id, poly }],opts.th 可选阈值(屏幕恒定时传 12/缩放),默认 12。
// 返回 { kind: 'vertex'|'edge', room, point, vertexIdx?/edgeIdx? } 或 null
export const detectBoundary = (rooms, p, opts = {}) => {
  const TH = opts.th || 12
  const vCands = []
  const eCands = []
  for (const r of rooms) {
    const poly = r.poly
    for (let i = 0; i < poly.length; i++) {
      const vd = Math.hypot(p.x - poly[i].x, p.y - poly[i].y)
      if (vd < TH) vCands.push({ room: r, vertexIdx: i, dist: vd })
      const proj = projectToSegment(p, poly[i], poly[(i + 1) % poly.length])
      if (proj.dist < TH) eCands.push({ room: r, edgeIdx: i, point: proj.point, dist: proj.dist })
    }
  }
  if (opts.cutId != null) {
    const cv = vCands.filter((c) => c.room.id === opts.cutId)
    if (cv.length) {
      const v = cv.reduce((a, b) => (a.dist <= b.dist ? a : b))
      return { kind: 'vertex', room: v.room, vertexIdx: v.vertexIdx, point: { ...v.room.poly[v.vertexIdx] } }
    }
    const ce = eCands.filter((c) => c.room.id === opts.cutId)
    if (ce.length) {
      const e = ce.reduce((a, b) => (a.dist <= b.dist ? a : b))
      return { kind: 'edge', room: e.room, edgeIdx: e.edgeIdx, point: e.point }
    }
  }
  const nearestEdge = eCands.length ? eCands.reduce((a, b) => (a.dist <= b.dist ? a : b)) : null
  const pickPool = (cands) => {
    if (cands.length < 2) return cands
    let pref = cands.filter((c) => pointInPoly(p, c.room.poly))
    if (!pref.length && nearestEdge) pref = cands.filter((c) => c.room === nearestEdge.room)
    return pref.length ? pref : cands
  }
  if (vCands.length) {
    const v = pickPool(vCands).reduce((a, b) => (a.dist <= b.dist ? a : b))
    return { kind: 'vertex', room: v.room, vertexIdx: v.vertexIdx, point: { ...v.room.poly[v.vertexIdx] } }
  }
  if (eCands.length) {
    const e = pickPool(eCands).reduce((a, b) => (a.dist <= b.dist ? a : b))
    return { kind: 'edge', room: e.room, edgeIdx: e.edgeIdx, point: e.point }
  }
  return null
}

// 裁剪连线合法性:切点可落在顶点上(切角/对角切),但连线内部不得穿过任何顶点、
// 不与任何边严格相交、中点在多边形内(防凹形外绕)
export const cutSegmentValid = (poly, p1, p2) => {
  if (samePt(p1, p2)) return false
  for (const v of poly) {
    if (samePt(v, p1) || samePt(v, p2)) continue
    if (onSegment(v, p1, p2)) return false
  }
  for (let i = 0; i < poly.length; i++) {
    if (segsIntersect(p1, p2, poly[i], poly[(i + 1) % poly.length])) return false
  }
  return pointInPoly({ x: (p1.x + p2.x) / 2, y: (p1.y + p2.y) / 2 }, poly)
}

// 连续重合顶点去重(切点恰为顶点时,拼接处会产生重复点)
const dedupSeq = (pts) => {
  const out = []
  for (const p of pts) {
    if (!out.length || !samePt(out[out.length - 1], p)) out.push(p)
  }
  if (out.length > 1 && samePt(out[0], out[out.length - 1])) out.pop()
  return out
}

// 裁剪完整方案校验:连线合法 + 切完两侧都是有效多边形(各 ≥3 顶点)。
// 拒绝退化切法:切点落在顶点、另一切点又在该顶点的邻边上(切出 2 点残片/沿线虚切)
export const cutPlanValid = (poly, iA, pA, iB, pB) => {
  if (!cutSegmentValid(poly, pA, pB)) return false
  const [q1, q2] = splitPoly(poly, iA, pA, iB, pB)
  return q1.length >= 3 && q2.length >= 3
}

// 拆分:边 iA 上点 pA、边 iB 上点 pB(点在边内部或恰为顶点),把多边形切成两个
export const splitPoly = (poly, iA, pA, iB, pB) => {
  if (iA > iB) { const ti = iA; iA = iB; iB = ti; const tp = pA; pA = pB; pB = tp }
  const first = [{ ...pA }]
  for (let i = iA + 1; i <= iB; i++) first.push({ ...poly[i] })
  first.push({ ...pB })
  const second = [{ ...pB }]
  for (let i = iB + 1; i < poly.length; i++) second.push({ ...poly[i] })
  for (let i = 0; i <= iA; i++) second.push({ ...poly[i] })
  second.push({ ...pA })
  return [dedupSeq(first), dedupSeq(second)]
}

// 两线段共线重叠检测(粘合的吸附边):返回重叠段 { s, t }(沿 a1→a2 方向,s 靠 a1)或 null
export const segOverlap = (a1, a2, b1, b2) => {
  const ux = a2.x - a1.x; const uy = a2.y - a1.y
  const len2 = ux * ux + uy * uy
  if (len2 < 1e-9) return null
  const cross1 = Math.abs(ux * (b1.y - a1.y) - uy * (b1.x - a1.x))
  const cross2 = Math.abs(ux * (b2.y - a1.y) - uy * (b2.x - a1.x))
  if (cross1 > 2 || cross2 > 2) return null // 共线容差 2px
  const d1 = ((b1.x - a1.x) * ux + (b1.y - a1.y) * uy) / len2
  const d2 = ((b2.x - a1.x) * ux + (b2.y - a1.y) * uy) / len2
  const lo = Math.max(0, Math.min(d1, d2))
  const hi = Math.min(1, Math.max(d1, d2))
  if (hi - lo <= 1e-6) return null
  const s = { x: a1.x + ux * lo, y: a1.y + uy * lo }
  const t = { x: a1.x + ux * hi, y: a1.y + uy * hi }
  if (Math.hypot(t.x - s.x, t.y - s.y) < 2) return null // 重叠段至少 2px
  return { s, t }
}

// 顶点冗余判定:落在两邻点连线上(垂直距离 < 0.5px 且投影在两点之间)
const collinearBetween = (p, a, b) => {
  const ux = b.x - a.x; const uy = b.y - a.y
  const len2 = ux * ux + uy * uy
  if (len2 < 1e-9) return false
  const cross = Math.abs(ux * (p.y - a.y) - uy * (p.x - a.x))
  if (cross / Math.sqrt(len2) > 0.5) return false
  const dot = (p.x - a.x) * ux + (p.y - a.y) * uy
  return dot > -1e-9 && dot < len2 + 1e-9
}

// 去共线顶点:剪切产生的切点在粘合后落在原边上成为冗余中间点(一条边被拆成两段),
// 删除后恢复原边;粘合拼接处的共线接缝点同理清除,真实拐点不受影响
const dropCollinear = (pts) => {
  const out = [...pts]
  let changed = true
  while (changed && out.length > 3) {
    changed = false
    for (let i = 0; i < out.length; i++) {
      const prev = out[(i - 1 + out.length) % out.length]
      const next = out[(i + 1) % out.length]
      if (collinearBetween(out[i], prev, next)) { out.splice(i, 1); changed = true; break }
    }
  }
  return out
}

// 合并:两多边形沿共线重叠边拼接为一个;找不到共享边返回 null
export const mergePolys = (A, B) => {
  const n = A.length; const m = B.length
  for (let i = 0; i < n; i++) {
    const a1 = A[i]; const a2 = A[(i + 1) % n]
    for (let j = 0; j < m; j++) {
      const b1 = B[j]; const b2 = B[(j + 1) % m]
      const ov = segOverlap(a1, a2, b1, b2)
      if (!ov) continue
      const { s, t } = ov
      // A 从 t 绕行到 s,B 从 s 对应点绕行到 t,闭环去重
      const pts = [t]
      for (let k = 0; k < n; k++) pts.push(A[(i + 1 + k) % n])
      pts.push(s)
      for (let k = 0; k < m; k++) pts.push(B[(j + 1 + k) % m])
      pts.push(t)
      const out = []
      for (const p of pts) {
        if (!out.length || !samePt(out[out.length - 1], p)) out.push(p)
      }
      if (out.length > 1 && samePt(out[0], out[out.length - 1])) out.pop() // 首尾重复
      return dropCollinear(out)
    }
  }
  return null
}

export const polyBBox = (poly) => {
  const xs = poly.map((p) => p.x); const ys = poly.map((p) => p.y)
  return { minX: Math.min(...xs), minY: Math.min(...ys), maxX: Math.max(...xs), maxY: Math.max(...ys) }
}
