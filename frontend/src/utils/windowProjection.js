import { drawWindowFoliage } from './windowFoliage'
import { applyDeskShadowMask } from './deskShadowMask'
import { floorShadowDirection, groundShadowPerHeight } from './windowLight'
import { CASEMENTS, WINDOW_WIDTH_RATIO } from './windowSash'

// Keep both pane heights when sizing the frame outwards from the glazing bar.
const lowerPaneHeight = 0.44175
const upperPaneHeight = 0.47575
const railHeight = 0.036
const frameDepth = 0.015 // fraction of the full window width, perpendicular to the sash
const transomHeight = 0.088
const transomTop = 0.483 - transomHeight / 2
const transomBottom = transomTop + transomHeight
const glassSill = transomTop - lowerPaneHeight
const glassLintel = transomBottom + upperPaneHeight
const frameSill = glassSill - railHeight
const frameHeight = glassLintel + railHeight - frameSill

// A source above and slightly to one side of the window, independent of the sun at night.
export const LIGHTNING_SOURCE = {
  kind: 'lightning',
  altitude: 64,
  azimuth: 174,
  hasDirectLight: true,
  palette: { core: 'rgba(230,242,255,0.64)', mid: 'rgba(214,231,255,0.48)', ambient: 'rgba(201,218,250,0.22)' },
}

const equinoxAltitude = 90 - 36.65
const sillHeightRatio = 0.15

export function windowTransform(width, height, altitude, azimuth, realWeight = 0, noonAltitude = equinoxAltitude) {
  const direction = floorShadowDirection(azimuth, realWeight)
  // Infer one effective window height for the day from its calibrated noon footprint.
  // Above the Jinan summer-solstice altitude, retain that height even at the zenith.
  const calibrationAltitude = Math.min(noonAltitude, equinoxAltitude + 23.44)
  const phase = (equinoxAltitude - calibrationAltitude) / 23.44
  const winterSummerRatio = 1.15
  const amplitude = (winterSummerRatio - 1) / (winterSummerRatio + 1)
  const referenceReach = height * 0.9 * (1 + amplitude * phase)
  const correctedHeight = referenceReach * Math.tan((calibrationAltitude * Math.PI) / 180)
  // A 1.5 m opening spans two viewport heights at real scale (0.75 m per viewport).
  const windowHeight = correctedHeight * (1 - realWeight) + 2 * height * realWeight
  const reach = windowHeight * groundShadowPerHeight(altitude)
  const paneWidth = width * WINDOW_WIDTH_RATIO
  const shear = direction.x * reach
  const depth = direction.y * reach
  // The ground at the foot of the wall stays fixed all day. Frame the corrected view
  // at noon, then project the raised sill along the very same rays as the rails above it.
  const noonSillY = height * (0.05 + 0.04 * phase)
  const wallY = (1 - realWeight) * (noonSillY - sillHeightRatio * referenceReach)
  return [paneWidth, 0, shear, depth, (width - paneWidth) / 2 + sillHeightRatio * shear, wallY + sillHeightRatio * depth]
}

// Opposite pairs keep the light disk centred, even with a small sample budget.
const samples = Array.from({ length: 8 }, (_, i) => {
  const angle = i * Math.PI * (3 - Math.sqrt(5))
  const radius = Math.sqrt((i + 0.5) / 8)
  const x = radius * Math.cos(angle)
  const y = radius * Math.sin(angle)
  return [
    [x, y],
    [-x, -y],
  ]
}).flat()
const radians = Math.PI / 180
const weatherSoftness = { cloud: 0.25, overcast: 0.65, fog: 0.85, rain: 0.72, snow: 0.72 }
const scratchBuffers = new WeakMap()

// Keep the weather's relative softness at both ends of the user control.
// The centre reproduces the unadjusted apparent radius of the light source.
export function shadowSourceRadius(softness, featherStrength = 50) {
  const strength = Number.isFinite(featherStrength) ? Math.min(100, Math.max(0, featherStrength)) : 50
  const angularRadius = 0.266 + 2 * Math.max(0, Math.min(1, softness))
  return Math.tan(angularRadius * 3 ** ((strength - 50) / 50) * radians)
}

// Project a two-dimensional light disk in the tangent plane of the central
// ray. A point at the wall foot stays fixed for every ray; the spread increases
// with the height of the blocker. This also avoids a singular azimuth at noon.
export function sampleWindowRays(altitude, azimuth, sourceRadius) {
  const alt = altitude * radians
  const { x, y } = floorShadowDirection(azimuth)
  const up = Math.sin(alt)
  const horizontal = Math.cos(alt)
  return samples.map(([side, rise]) => {
    const tangent = side * sourceRadius
    const vertical = rise * sourceRadius
    const length = horizontal - vertical * up
    const dx = x * length + y * tangent
    const dy = y * length - x * tangent
    return {
      altitude: Math.atan2(up + vertical * horizontal, Math.hypot(dx, dy)) / radians,
      azimuth: 180 - Math.atan2(dx, dy) / radians,
    }
  })
}

function resize(canvas, width, height) {
  if (canvas.width !== width) canvas.width = width
  if (canvas.height !== height) canvas.height = height
}

// An open sash frees the whole wall opening, including the space occupied by
// its rails when shut. Its moving frame is then projected onto that light.
export function sashApertures({ hinge, glassStart, glassEdge, latch, depth }) {
  if (depth) return [[Math.min(hinge, latch), frameSill, Math.abs(latch - hinge), frameHeight]]
  const left = Math.min(glassStart, glassEdge)
  const width = Math.abs(glassEdge - glassStart)
  return [
    [left, glassSill, width, lowerPaneHeight],
    [left, transomBottom, width, upperPaneHeight],
  ]
}

export function castOpenSash(mask, { hinge, glassStart, glassEdge, latch, tipX, depth }, paneWidth, riseX, riseY, originX, originY) {
  const span = latch - hinge
  const glassU = (glassStart - hinge) / span
  const glassWidth = (glassEdge - glassStart) / span
  const uX = paneWidth * (tipX - hinge)
  const uY = paneWidth * depth
  // The frame's thickness turns with it. When seen edge-on the reveals still block light.
  const thicknessX = (uY * frameDepth) / span
  const thicknessY = (-uX * frameDepth) / span
  const hingeX = originX + paneWidth * hinge

  // Front and side faces often cover the same antialiased edge pixel. Build
  // their union and erase it once per ray, so a partially covered pixel is not
  // darkened twice. All bars have the same winding in floor coordinates.
  const winding = uX * riseY - uY * riseX >= 0 ? 1 : -1
  const bar = (u, v, width, height) => {
    const x = hingeX + u * uX + v * riseX
    const y = originY + u * uY + v * riseY
    const ux = uX * width
    const uy = uY * width
    const vx = riseX * height
    const vy = riseY * height
    const corners = [
      [x, y],
      [x + ux, y + uy],
      [x + ux + vx, y + uy + vy],
      [x + vx, y + vy],
    ]
    for (let i = 0; i < 4; i++) {
      const [ax, ay] = corners[i]
      const [bx, by] = corners[(i + 1) % 4]
      // Pick the outside edge of each of the four side faces of this prism.
      const back = ((bx - ax) * thicknessY - (by - ay) * thicknessX) * winding < 0
      const dx = back ? thicknessX : 0
      const dy = back ? thicknessY : 0
      if (i === 0) mask.moveTo(ax + dx, ay + dy)
      else mask.lineTo(ax + dx, ay + dy)
      mask.lineTo(bx + dx, by + dy)
    }
    mask.closePath()
  }
  mask.resetTransform()
  mask.beginPath()
  bar(0, frameSill, glassU, frameHeight)
  bar(glassU + glassWidth, frameSill, 1 - glassU - glassWidth, frameHeight)
  bar(glassU, frameSill, glassWidth, railHeight)
  bar(glassU, transomTop, glassWidth, transomHeight)
  bar(glassU, glassLintel, glassWidth, railHeight)
  mask.fill()
}

// Cache the planar silhouettes once per paint; only the out-of-plane open
// sash needs new geometry for each ray. Both the frame and the pot use the
// same sill coordinate in this texture and in every projection of it.
function paintWindowPlane(plane, sashes, occluders, foliageTime) {
  const ctx = plane.getContext('2d')
  if (!ctx) return false
  ctx.clearRect(0, 0, plane.width, plane.height)
  ctx.save()
  const yScale = plane.height / frameHeight
  ctx.setTransform(plane.width, 0, 0, yScale, 0, -frameSill * yScale)
  ctx.fillStyle = '#fff'
  ctx.beginPath()
  for (const sash of sashes) for (const rect of sashApertures(sash)) ctx.rect(...rect)
  ctx.fill()
  ctx.globalCompositeOperation = 'destination-out'
  drawWindowFoliage(ctx, foliageTime, frameSill)
  for (const shape of occluders) ctx.fill(typeof shape === 'string' ? new Path2D(shape) : shape)
  ctx.restore()
  return true
}

// occluders accepts filled SVG path strings or Path2D objects in window-plane
// coordinates. Overlapping silhouettes block the same light only once.
export function drawWindowProjection(
  canvas,
  {
    width,
    height,
    scene,
    weather = 'clear',
    softness = weatherSoftness[weather] ?? 0,
    featherStrength = 50,
    shadowAlpha = 0,
    realWeight = 0,
    foliageTime = 0,
    occluders = [],
    deskCasters = [],
    sashes = CASEMENTS,
  },
) {
  const scale = Math.min(0.5, 512 / Math.max(1, width, height))
  const w = Math.max(1, Math.round(width * scale))
  const h = Math.max(1, Math.round(height * scale))
  resize(canvas, w, h)
  const ctx = canvas.getContext('2d')
  if (!ctx) return
  ctx.clearRect(0, 0, w, h)
  if (
    !scene?.hasDirectLight ||
    !(scene.altitude > 0 && scene.altitude < 90 && scene.azimuth > 90 && scene.azimuth < 270) ||
    width <= 0 ||
    height <= 0
  )
    return

  let buffers = scratchBuffers.get(canvas)
  if (!buffers) {
    buffers = { plane: canvas.ownerDocument.createElement('canvas'), scratch: canvas.ownerDocument.createElement('canvas') }
    scratchBuffers.set(canvas, buffers)
  }
  const { plane, scratch } = buffers
  resize(plane, Math.max(128, w), Math.max(128, h * 2))
  resize(scratch, w, h)
  const mask = scratch.getContext('2d')
  if (!mask || !paintWindowPlane(plane, sashes, occluders, foliageTime)) return

  const openSashes = sashes.filter((sash) => sash.depth)
  const sourceRadius = shadowSourceRadius(softness, featherStrength)
  ctx.save()
  ctx.globalCompositeOperation = 'lighter'
  ctx.globalAlpha = 1 / samples.length
  for (const ray of sampleWindowRays(scene.altitude, scene.azimuth, sourceRadius)) {
    if (ray.altitude <= 0 || ray.azimuth <= 90 || ray.azimuth >= 270) continue
    const [a, b, c, d, x, y] = windowTransform(w, h, ray.altitude, ray.azimuth, realWeight, scene.noonAltitude)
    if (openSashes.length) {
      mask.clearRect(0, 0, w, h)
      mask.save()
      mask.setTransform(a, b, c, d, x, y)
      mask.drawImage(plane, 0, frameSill, 1, frameHeight)
      mask.globalCompositeOperation = 'destination-out'
      for (const sash of openSashes) castOpenSash(mask, sash, a, c, d, x, y)
      mask.restore()
      ctx.drawImage(scratch, 0, 0)
    } else {
      ctx.setTransform(a, b, c, d, x, y)
      ctx.drawImage(plane, 0, frameSill, 1, frameHeight)
    }
  }
  ctx.restore()
  applyDeskShadowMask(ctx, mask, deskCasters, scene, width, height, realWeight, sourceRadius)

  const [a, , c, d, x, y] = windowTransform(w, h, scene.altitude, scene.azimuth, realWeight, scene.noonAltitude)
  const transmittedLight = ctx.createLinearGradient(x + a * 0.5, y, x + a * 0.5 + c, y + d)
  transmittedLight.addColorStop(0, scene.palette.core)
  transmittedLight.addColorStop(0.6, scene.palette.mid)
  transmittedLight.addColorStop(1, scene.palette.ambient)
  if (scene.kind === 'lightning') {
    ctx.save()
    ctx.globalCompositeOperation = 'source-in'
    ctx.fillStyle = transmittedLight
    ctx.fillRect(0, 0, w, h)
    ctx.restore()
    return true
  }

  mask.clearRect(0, 0, scratch.width, scratch.height)
  mask.drawImage(canvas, 0, 0)
  mask.save()
  mask.globalCompositeOperation = 'source-in'
  mask.fillStyle = transmittedLight
  mask.globalAlpha = 0.22
  mask.fillRect(0, 0, w, h)
  mask.restore()

  ctx.save()
  ctx.globalCompositeOperation = 'source-out'
  ctx.fillStyle = scene.shadowColor
  ctx.globalAlpha = Math.min(1, Math.max(0, shadowAlpha))
  ctx.fillRect(0, 0, w, h)
  ctx.globalAlpha = 1
  ctx.globalCompositeOperation = 'lighter'
  ctx.drawImage(scratch, 0, 0)
  ctx.restore()
  return true
}
