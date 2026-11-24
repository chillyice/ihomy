// 侧边小物与窗光:判断便签有没有落在投射光斑里,并按太阳方位角/高度角给出落影向量。
// 光斑几何与窗面画布共用 windowTransform,保证「被光照到」的判定和眼睛看到的投影一致。
import { WINDOW_PANES, windowTransform } from './windowProjection'

// 投射光斑在视口里的四边形(顺时针,从左上角开始),与绘制窗面时用的是同一套变换。
export function projectionQuad(width, height, scene, realWeight = 0) {
  const [a, , c, d, x, y] = windowTransform(width, height, scene.altitude, scene.azimuth, realWeight)
  return [
    [x, y],
    [x + a, y],
    [x + a + c, y + d],
    [x + c, y + d],
  ]
}

// Clip against the four sides of the visible object. Keeping the intersections
// makes the fraction vary continuously as a pane edge moves across an object.
function clipToRect(polygon, rect) {
  for (const [axis, boundary, sign] of [
    [0, rect.left, 1],
    [0, rect.right, -1],
    [1, rect.top, 1],
    [1, rect.bottom, -1],
  ]) {
    if (!polygon.length) break
    const clipped = []
    let previous = polygon.at(-1)
    let previousInside = (previous[axis] - boundary) * sign >= 0
    for (const point of polygon) {
      const inside = (point[axis] - boundary) * sign >= 0
      if (inside !== previousInside) {
        const t = (boundary - previous[axis]) / (point[axis] - previous[axis])
        clipped.push([previous[0] + t * (point[0] - previous[0]), previous[1] + t * (point[1] - previous[1])])
      }
      if (inside) clipped.push(point)
      previous = point
      previousInside = inside
    }
    polygon = clipped
  }
  return polygon
}

export function quadHitsRect(quad, rect) {
  return clipToRect(quad, rect).length > 0
}

export function apertureCoverage(quad, rect) {
  const width = rect.right - rect.left
  const height = rect.bottom - rect.top
  if (width <= 0 || height <= 0) return 0
  const [[x, y], right, , bottom] = quad
  const project = (u, v) => [x + u * (right[0] - x) + v * (bottom[0] - x), y + u * (right[1] - y) + v * (bottom[1] - y)]
  let litArea = 0
  for (const pane of WINDOW_PANES) {
    const { left, top, right, bottom } = pane
    const overlap = clipToRect([project(left, top), project(right, top), project(right, bottom), project(left, bottom)], rect)
    let twiceArea = 0
    for (let i = 0; i < overlap.length; i++) {
      const [x1, y1] = overlap[i]
      const [x2, y2] = overlap[(i + 1) % overlap.length]
      twiceArea += x1 * y2 - x2 * y1
    }
    litArea += Math.abs(twiceArea) / 2
  }
  return Math.min(1, litArea / (width * height))
}

// Effective height above the desk as a fraction of each illustration's size.
const objectHeights = {
  bookmark: 0.008,
  note: 0.01,
  photo: 0.009,
  leaf: 0.012,
  envelope: 0.014,
  record: 0.018,
  recipe: 0.027,
  phone: 0.032,
  journal: 0.045,
  fruit: 0.105,
  cup: 0.14,
}

export function castShadow(scene, size, shadowAlpha = 0, kind = 'note') {
  const altitude = Number(scene?.altitude)
  if (!scene?.hasDirectLight || !Number.isFinite(altitude) || altitude <= 0 || altitude >= 90) return null
  const azimuth = Number(scene.azimuth ?? 180)
  const bearing = ((180 - azimuth) * Math.PI) / 180
  const objectHeight = Math.max(0, Number(size) || 0) * (objectHeights[kind] ?? objectHeights.note)
  // Limit grazing angles so an object on the sidebar cannot shadow the entire page.
  const length = objectHeight * Math.min(4, 1 / Math.tan((altitude * Math.PI) / 180))
  return {
    x: Math.sin(bearing) * length,
    y: Math.cos(bearing) * length,
    blur: 1 + objectHeight * 0.12 + length * 0.1,
    color: withAlpha(scene.shadowColor, Number(shadowAlpha) * 0.55),
  }
}

function withAlpha(color, alpha) {
  const strength = Math.round(Math.max(0, Math.min(0.5, Number(alpha) || 0)) * 1000) / 1000
  const found = String(color ?? '').match(/rgba?\(([^)]+)\)/)
  if (!found) return `rgba(0, 0, 0, ${strength})`
  const [r, g, b] = found[1].split(',').map((part) => Number.parseFloat(part) || 0)
  return `rgba(${r}, ${g}, ${b}, ${strength})`
}
