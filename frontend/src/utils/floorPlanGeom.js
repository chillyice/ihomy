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

// 裁剪连线合法性:不穿任何顶点、不与任何边严格相交、中点在多边形内(防凹形外绕)
export const cutSegmentValid = (poly, p1, p2) => {
  for (const v of poly) {
    if (onSegment(v, p1, p2)) return false
  }
  for (let i = 0; i < poly.length; i++) {
    if (segsIntersect(p1, p2, poly[i], poly[(i + 1) % poly.length])) return false
  }
  return pointInPoly({ x: (p1.x + p2.x) / 2, y: (p1.y + p2.y) / 2 }, poly)
}

// 拆分:边 iA 上点 pA、边 iB 上点 pB(点在边内部),把多边形切成两个
export const splitPoly = (poly, iA, pA, iB, pB) => {
  if (iA > iB) { const ti = iA; iA = iB; iB = ti; const tp = pA; pA = pB; pB = tp }
  const first = [{ ...pA }]
  for (let i = iA + 1; i <= iB; i++) first.push({ ...poly[i] })
  first.push({ ...pB })
  const second = [{ ...pB }]
  for (let i = iB + 1; i < poly.length; i++) second.push({ ...poly[i] })
  for (let i = 0; i <= iA; i++) second.push({ ...poly[i] })
  second.push({ ...pA })
  return [first, second]
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
      return out
    }
  }
  return null
}

export const polyBBox = (poly) => {
  const xs = poly.map((p) => p.x); const ys = poly.map((p) => p.y)
  return { minX: Math.min(...xs), minY: Math.min(...ys), maxX: Math.max(...xs), maxY: Math.max(...ys) }
}
