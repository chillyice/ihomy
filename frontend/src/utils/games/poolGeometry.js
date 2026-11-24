// All dimensions use the same coordinate system as the balls and canvas.
export const TABLE_WIDTH = 800
export const TABLE_HEIGHT = 400
export const BALL_RADIUS = 10
const R = BALL_RADIUS
const EPS = 1e-8
const CUSHION_RESTITUTION = 0.86

// Each mouth-to-throat pair is one cut rubber facing. The throat joins the
// ends of the facings and marks the edge of the supporting shelf.
function pocket(x, y, sideX, sideY) {
  const point = ([u, v]) => ({ x: x + u * (sideX || 1), y: y + v * sideY })
  const corner = sideX !== 0
  const mouth = (
    corner
      ? [
          [0, 34],
          [34, 0],
        ]
      : [
          [-26, 0],
          [26, 0],
        ]
  ).map(point)
  const throat = (
    corner
      ? [
          [-12, 21],
          [21, -12],
        ]
      : [
          [-19, -13],
          [19, -13],
        ]
  ).map(point)
  const outward = corner ? { x: -sideX * Math.SQRT1_2, y: -sideY * Math.SQRT1_2 } : { x: 0, y: -sideY }
  return { x, y, mouth, throat, outward }
}

export const POCKETS = [1, -1].flatMap((sideY) => {
  const y = sideY > 0 ? 0 : TABLE_HEIGHT
  return [pocket(0, y, 1, sideY), pocket(TABLE_WIDTH / 2, y, 0, sideY), pocket(TABLE_WIDTH, y, -1, sideY)]
})

function cushion(a, i, b, j) {
  return [a.throat[i], a.mouth[i], b.mouth[j], b.throat[j]]
}

const [tl, tm, tr, bl, bm, br] = POCKETS
export const CUSHIONS = [
  cushion(tl, 1, tm, 0),
  cushion(tm, 1, tr, 1),
  cushion(tr, 0, br, 0),
  cushion(br, 1, bm, 1),
  cushion(bm, 0, bl, 1),
  cushion(bl, 0, tl, 0),
]

// The polylines run clockwise in screen coordinates. Their left normals
// point into playable space, including on the angled facings.
function segment(a, b) {
  const length = Math.hypot(b.x - a.x, b.y - a.y)
  const tx = (b.x - a.x) / length
  const ty = (b.y - a.y) / length
  return { a, b, length, tx, ty, nx: -ty, ny: tx }
}
const faces = CUSHIONS.flatMap((points) => points.slice(1).map((b, i) => segment(points[i], b)))
const throats = POCKETS.map((p) => ({ ...segment(...p.throat), nx: p.outward.x, ny: p.outward.y }))
const bedEdges = [...faces, ...throats]
const vertices = CUSHIONS.flat()

function withinSegment(s, x, y) {
  const projection = (x - s.a.x) * s.tx + (y - s.a.y) * s.ty
  return projection >= -EPS && projection <= s.length + EPS
}

function innerBed(x, y, radius) {
  return x > radius && x < TABLE_WIDTH - radius && y > radius && y < TABLE_HEIGHT - radius
}

function pastThroat(x, y) {
  return throats.some((s) => (x - s.a.x) * s.nx + (y - s.a.y) * s.ny >= 0 && withinSegment(s, x, y))
}

function nearestOnSegment(s, x, y) {
  const along = Math.max(0, Math.min(s.length, (x - s.a.x) * s.tx + (y - s.a.y) * s.ty))
  const dx = x - (s.a.x + along * s.tx)
  const dy = y - (s.a.y + along * s.ty)
  return { dx, dy, distance: Math.hypot(dx, dy) }
}

// A ball can legally hang in the jaws. Its center must be on the supported
// side of the shelf and its disk must clear the cushion faces and noses.
export function supportedPoolPosition(x, y) {
  if (!Number.isFinite(x) || !Number.isFinite(y)) return false
  if (innerBed(x, y, R - EPS)) return true
  if (pastThroat(x, y)) return false
  let inside = false
  for (const { a, b } of bedEdges) {
    if (a.y > y !== b.y > y && x < a.x + ((b.x - a.x) * (y - a.y)) / (b.y - a.y)) inside = !inside
  }
  return inside && faces.every((s) => nearestOnSegment(s, x, y).distance >= R - EPS)
}

// Free placement is on the bed proper, with the whole ball clear of an opening.
export function cuePlacementOnCloth(x, y) {
  if (!Number.isFinite(x) || !Number.isFinite(y) || !innerBed(x, y, R - EPS)) return false
  return POCKETS.every(
    (p) =>
      (p.mouth[0].x - x) * p.outward.x + (p.mouth[0].y - y) * p.outward.y > R ||
      (p.outward.x === 0 && Math.abs(x - p.x) > Math.abs(p.mouth[0].x - p.x) + R),
  )
}

// Continuous intersections for a *ball center*. Offset faces and circular
// caps give the true contact normal when the disk clips a cushion nose.
export function tracePoolBoundary(x, y, dx, dy, maxDistance = Infinity) {
  if (
    Number.isFinite(maxDistance) &&
    innerBed(x, y, R) &&
    innerBed(x + dx * maxDistance, y + dy * maxDistance, R) &&
    Math.abs(dx * dx + dy * dy - 1) < EPS
  )
    return null
  const speed = Math.hypot(dx, dy)
  if (!(speed > 0) || maxDistance < 0) return null
  dx /= speed
  dy /= speed
  let hit = null
  let limit = maxDistance
  function record(type, t, nx, ny) {
    if (t < -EPS || t > limit + EPS || (hit && t >= limit - EPS)) return
    const distance = Math.min(maxDistance, Math.max(0, t))
    limit = distance
    hit = { type, distance, x: x + dx * distance, y: y + dy * distance, nx, ny }
  }
  for (const face of faces) {
    const toward = dx * face.nx + dy * face.ny
    if (toward >= -EPS) continue
    const gap = (x - face.a.x) * face.nx + (y - face.a.y) * face.ny - R
    const t = -gap / toward
    if (t >= -EPS && t <= limit + EPS && withinSegment(face, x + dx * t, y + dy * t)) record('rail', t, face.nx, face.ny)
  }
  for (const v of vertices) {
    const rx = x - v.x
    const ry = y - v.y
    const approach = -(rx * dx + ry * dy)
    if (approach <= 0) continue
    const cross = rx * dy - ry * dx
    const discriminant = R * R - cross * cross
    if (discriminant <= EPS) continue
    const t = approach - Math.sqrt(discriminant)
    if (t >= -EPS && t <= limit + EPS) record('rail', t, (rx + dx * t) / R, (ry + dy * t) / R)
  }
  for (const s of throats) {
    const toward = dx * s.nx + dy * s.ny
    if (toward <= EPS) continue
    const t = ((s.a.x - x) * s.nx + (s.a.y - y) * s.ny) / toward
    if (t >= -EPS && t <= limit + EPS && withinSegment(s, x + dx * t, y + dy * t)) record('pocket', t, s.nx, s.ny)
  }
  return hit
}

export function applyPoolBoundaryHit(ball, hit, onEvent) {
  if (ball.pocketed) return
  if (hit.type === 'pocket') {
    ball.vx = 0
    ball.vy = 0
    ball.pocketed = true
    onEvent?.('pocket', ball.id)
    return
  }
  const approaching = ball.vx * hit.nx + ball.vy * hit.ny
  if (approaching >= 0) return
  const change = (1 + CUSHION_RESTITUTION) * approaching
  ball.vx -= change * hit.nx
  ball.vy -= change * hit.ny
  onEvent?.('rail', ball.id)
}

// Pair-separation corrections can push a disk a fraction into a facing or
// across the drop line. Resolve this separately from free-flight raycasts.
export function resolvePoolBoundaryOverlap(ball, onEvent) {
  if (ball.pocketed || innerBed(ball.x, ball.y, R)) return
  for (const s of faces) {
    const { dx, dy, distance } = nearestOnSegment(s, ball.x, ball.y)
    if (distance >= R - EPS) continue
    const nx = distance > EPS ? dx / distance : s.nx
    const ny = distance > EPS ? dy / distance : s.ny
    ball.x += nx * (R - distance)
    ball.y += ny * (R - distance)
    applyPoolBoundaryHit(ball, { type: 'rail', nx, ny }, onEvent)
  }
  if (pastThroat(ball.x, ball.y)) applyPoolBoundaryHit(ball, { type: 'pocket' }, onEvent)
}
