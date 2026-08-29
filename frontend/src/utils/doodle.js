/**
 * 日记信纸涂鸦引擎:矢量笔画存储 + canvas 渲染 + 橡皮擦。
 * 笔画坐标 = paper-body(编辑页信纸正文区)左上角原点的 CSS 像素;查看页(DiaryPage)
 * 的 sheet-body 与编辑页 paper-body 宽度同为 496px、正文从 y=0 起,坐标系一致。
 * 荧光笔(marker)画在专用画布上,由 CSS mix-blend-mode: multiply(深色模式 screen)叠在文字上方。
 * 蜡笔/铅笔的抖动纹理用笔画自带的种子(mulberry32)确定性生成,重绘不闪变。
 */
export const BRUSHES = [
  { id: 'gel', labelKey: 'diary.penGel' },
  { id: 'pencil', labelKey: 'diary.penPencil' },
  { id: 'crayon', labelKey: 'diary.penCrayon' },
  { id: 'marker', labelKey: 'diary.penMarker' },
  { id: 'brush', labelKey: 'diary.penBrush' },
  { id: 'eraserP', labelKey: 'diary.penEraserP' },
  { id: 'eraserO', labelKey: 'diary.penEraserO' },
]

export const INK_COLORS = [
  '#1A1A1A', '#3A2E22', '#6B5C4A', '#9AA0A6', '#C8CDD2', '#F5F2EA', '#EFE6D0',
  '#A8483A', '#C05B4D', '#D97B29', '#E8963C', '#E3B23C', '#E8CC5A', '#F0E4A0',
  '#7BA05B', '#5B8C5A', '#3D6B4F', '#4A9E8F', '#7FB3D5', '#4A7FB5', '#2E5A8F',
  '#1E3A5F', '#7B5EA7', '#A98FC9', '#C96A8B', '#E8B4C8', '#8B6F47', '#B5976B',
]

const clamp = (v, lo, hi) => Math.min(hi, Math.max(lo, v))

function mulberry32(a) {
  return function () {
    a |= 0; a = (a + 0x6D2B79F5) | 0
    let t = Math.imul(a ^ (a >>> 15), 1 | a)
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

function pathThrough(ctx, pts) {
  ctx.beginPath()
  ctx.moveTo(pts[0][0], pts[0][1])
  if (pts.length === 1) ctx.lineTo(pts[0][0] + 0.01, pts[0][1])
  else for (let i = 1; i < pts.length; i++) ctx.lineTo(pts[i][0], pts[i][1])
  ctx.stroke()
}

function jitterPts(pts, rnd, amp) {
  return pts.map((p) => [p[0] + (rnd() - 0.5) * amp, p[1] + (rnd() - 0.5) * amp])
}

/** 渲染单笔。ctx 需预先按 dpr 缩放(setupCanvas);marker 传专用荧光画布 ctx;s.a 为用户透明度(0-1,缺省 1) */
export function renderStroke(ctx, s) {
  const pts = s.pts
  if (!pts || !pts.length) return
  const sa = s.a == null ? 1 : Math.min(1, Math.max(0.02, s.a))
  ctx.save()
  ctx.lineJoin = 'round'
  ctx.strokeStyle = s.c
  switch (s.t) {
    case 'marker': // 荧光笔:宽平头,半透明由画布元素 opacity 承担
      ctx.lineCap = 'butt'
      ctx.lineWidth = Math.max(8, s.w * 4)
      ctx.globalAlpha = sa
      pathThrough(ctx, pts)
      break
    case 'gel': // 签字笔:实色圆头
      ctx.lineCap = 'round'
      ctx.lineWidth = Math.max(1, s.w * 0.8)
      ctx.globalAlpha = 0.95 * sa
      pathThrough(ctx, pts)
      break
    case 'pencil': // 铅笔:两遍抖动细线,颗粒感
      ctx.lineCap = 'round'
      ctx.globalAlpha = 0.3 * sa
      ctx.lineWidth = Math.max(1, s.w * 0.5)
      for (let pass = 0; pass < 2; pass++) pathThrough(ctx, jitterPts(pts, mulberry32((s.s || 1) + pass * 7919), s.w * 0.9))
      break
    case 'crayon': // 蜡笔:三遍大幅抖动钝粗线,蜡质感
      ctx.lineCap = 'round'
      ctx.globalAlpha = 0.28 * sa
      ctx.lineWidth = Math.max(3, s.w * 1.7)
      for (let pass = 0; pass < 3; pass++) pathThrough(ctx, jitterPts(pts, mulberry32((s.s || 1) + pass * 104729), s.w * 1.4))
      break
    case 'brush': { // 画笔:随运笔速度变宽(慢粗快细)
      ctx.lineCap = 'round'
      ctx.globalAlpha = 0.9 * sa
      let w = s.w * 1.2
      if (pts.length === 1) {
        ctx.lineWidth = Math.max(1, w)
        pathThrough(ctx, pts)
        break
      }
      for (let i = 1; i < pts.length; i++) {
        const d = Math.hypot(pts[i][0] - pts[i - 1][0], pts[i][1] - pts[i - 1][1])
        w = w * 0.7 + s.w * 1.2 * clamp(1.6 - d / 24, 0.3, 1.6) * 0.3
        ctx.lineWidth = Math.max(0.8, w)
        ctx.beginPath()
        ctx.moveTo(pts[i - 1][0], pts[i - 1][1])
        ctx.lineTo(pts[i][0], pts[i][1])
        ctx.stroke()
      }
      break
    }
  }
  ctx.restore()
}

/** 全量渲染:荧光笔走 markCtx,其余走 inkCtx */
export function renderStrokes(inkCtx, markCtx, strokes) {
  for (const s of strokes) renderStroke(s.t === 'marker' ? markCtx : inkCtx, s)
}

/** 像素橡皮:按半径切断笔画,落在半径内的点被移除,剩余点串拆成新笔画;无变化时返回原数组引用(供撤销历史判断) */
export function erasePixel(strokes, x, y, r) {
  const out = []
  let changed = false
  for (const s of strokes) {
    const runs = []
    let cur = []
    for (const p of s.pts) {
      if (Math.hypot(p[0] - x, p[1] - y) > r) cur.push(p)
      else if (cur.length) { runs.push(cur); cur = [] }
    }
    if (cur.length) runs.push(cur)
    if (!runs.length) { changed = true; continue }
    if (runs.length === 1 && runs[0].length === s.pts.length) { out.push(s); continue }
    changed = true
    for (const run of runs) out.push({ ...s, pts: run })
  }
  return changed ? out : strokes
}

/** 对象橡皮:擦到任何一点的整笔删除;无变化时返回原数组引用 */
export function eraseObject(strokes, x, y, r) {
  const out = strokes.filter((s) => !s.pts.some((p) => Math.hypot(p[0] - x, p[1] - y) < r))
  return out.length === strokes.length ? strokes : out
}

export function parseDoodle(str) {
  if (!str) return []
  try {
    const o = JSON.parse(str)
    if (!Array.isArray(o?.strokes)) return []
    return o.strokes.filter((s) => s && typeof s.t === 'string' && Array.isArray(s.pts) && s.pts.length)
  } catch {
    return []
  }
}

/** 涂鸦最低需要的高度(决定查看页画布高度与日记页数下限) */
export function doodleExtentY(strokes) {
  let max = 0
  for (const s of strokes) for (const p of s.pts) if (p[1] > max) max = p[1]
  return max ? max + 24 : 0
}

/** canvas 尺寸对齐 CSS 尺寸 × dpr,ctx 预缩放;返回 ctx */
export function setupCanvas(cv) {
  const dpr = window.devicePixelRatio || 1
  const w = Math.max(1, Math.round((cv.clientWidth || 1) * dpr))
  const h = Math.max(1, Math.round((cv.clientHeight || 1) * dpr))
  if (cv.width !== w || cv.height !== h) { cv.width = w; cv.height = h }
  const ctx = cv.getContext('2d')
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  return ctx
}

export function clearCanvas(ctx, cv) {
  ctx.save()
  ctx.setTransform(1, 0, 0, 1, 0, 0)
  ctx.clearRect(0, 0, cv.width, cv.height)
  ctx.restore()
}
