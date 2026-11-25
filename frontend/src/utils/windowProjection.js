import { drawWindowFoliage } from './windowFoliage'
import { applyDeskShadowMask } from './deskShadowMask'
import { floorShadowDirection } from './windowLight'
import { CASEMENTS, WINDOW_WIDTH_RATIO } from './windowSash'

// One solid sash with two glazed openings, in hinge-to-latch × sill-to-head coordinates.
const stileWidth = 0.093
const railHeight = 0.065
const frameDepth = 0.032 // fraction of the full window width, perpendicular to the sash
const halfTransom = 0.055 / 2
const transomTop = 0.483 - halfTransom
const transomBottom = 0.483 + halfTransom
const sashContours = [
  [0, 0, 1, 1],
  [stileWidth, railHeight, 1 - 2 * stileWidth, transomTop - railHeight],
  [stileWidth, transomBottom, 1 - 2 * stileWidth, 1 - railHeight - transomBottom],
]

// A source above and slightly to one side of the window, independent of the sun at night.
export const LIGHTNING_SOURCE = {
  kind: 'lightning',
  altitude: 64,
  azimuth: 174,
  hasDirectLight: true,
  palette: { core: 'rgba(230,242,255,0.64)', mid: 'rgba(214,231,255,0.48)', ambient: 'rgba(201,218,250,0.22)' },
}

export function windowTransform(width, height, altitude, azimuth, realWeight = 0) {
  const radians = Math.PI / 180
  const direction = floorShadowDirection(azimuth)
  // Calibrate against solar noon in Jinan (36.65° N). Outside noon the
  // same continuous curve follows the actual solar elevation and bearing.
  const phase = (90 - 36.65 - altitude) / 23.44
  const winterSummerRatio = 1.15
  const amplitude = (winterSummerRatio - 1) / (winterSummerRatio + 1)
  const correctedReach = height * 0.9 * (1 + amplitude * phase)
  // The 1.5 m opening spans two viewport heights (0.75 m per viewport). Its sill is 15% of the opening above the floor.
  const realReach = realWeight > 0 && altitude > 0 && altitude < 90 ? (height * 2) / Math.tan(altitude * radians) : 0
  const reach = correctedReach + (realReach - correctedReach) * realWeight
  const paneWidth = width * WINDOW_WIDTH_RATIO
  const shear = direction.x * reach
  const depth = direction.y * reach
  const correctedTop = height * (0.05 + 0.04 * phase)
  const top = correctedTop + (0.15 * realReach * direction.y - correctedTop) * realWeight
  return [paneWidth, 0, shear, depth, (width - paneWidth) / 2 + shear * 0.15, top]
}

// Average a small disk of shifted copies of the aperture. Each sample represents
// one point on an extended light source, so frame and silhouettes share a penumbra.
const samples = Array.from({ length: 32 }, (_, i) => {
  const angle = i * Math.PI * (3 - Math.sqrt(5))
  const radius = Math.sqrt((i + 0.5) / 32)
  return [radius * Math.cos(angle), radius * Math.sin(angle)]
})
const softness = { clear: 0.012, cloud: 0.029, overcast: 0.065, fog: 0.085 }
const scratchBuffers = new WeakMap()

function resize(canvas, width, height) {
  if (canvas.width !== width) canvas.width = width
  if (canvas.height !== height) canvas.height = height
}

function castSash(mask, { hinge, latch, tipX = latch, depth = 0 }, paneWidth, riseX, riseY, originX, originY) {
  const span = latch - hinge
  const uX = paneWidth * (tipX - hinge)
  const uY = paneWidth * depth
  // The frame's thickness turns with it. When seen edge-on the reveals still block light.
  const thicknessX = (-uY * frameDepth) / span
  const thicknessY = (uX * frameDepth) / span
  const hingeX = originX + paneWidth * hinge

  mask.setTransform(uX, uY, riseX, riseY, hingeX, originY)
  mask.beginPath()
  for (const [u, v, width, height] of sashContours) mask.rect(u, v, width, height)
  mask.fill('evenodd')

  // Sweep every rim (outside and around both panes) to the back face.
  // Along a ray, any point beyond the front profile is reached through one of these sides.
  for (const [u, v, width, height] of sashContours) {
    for (const edgeV of [v, v + height]) {
      mask.setTransform(uX, uY, thicknessX, thicknessY, hingeX + riseX * edgeV, originY + riseY * edgeV)
      mask.fillRect(u, 0, width, 1)
    }
    for (const edgeU of [u, u + width]) {
      mask.setTransform(riseX, riseY, thicknessX, thicknessY, hingeX + uX * edgeU, originY + uY * edgeU)
      mask.fillRect(v, 0, height, 1)
    }
  }
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
    shadowAlpha = 0,
    realWeight = 0,
    foliageTime = 0,
    occluders = [],
    deskCasters = [],
    sash = null,
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
    scene.altitude <= 0 ||
    scene.altitude >= 90 ||
    (scene.kind !== 'lightning' && ['rain', 'snow', 'thunder'].includes(weather)) ||
    width <= 0 ||
    height <= 0
  )
    return

  const radius = Math.max(1, Math.min(w, h) * (softness[weather] ?? softness.clear))
  const pad = Math.ceil(radius) + 1
  let scratch = scratchBuffers.get(canvas)
  if (!scratch) {
    scratch = canvas.ownerDocument.createElement('canvas')
    scratchBuffers.set(canvas, scratch)
  }
  resize(scratch, w + 2 * pad, h + 2 * pad)
  const mask = scratch.getContext('2d')
  if (!mask) return
  mask.clearRect(0, 0, scratch.width, scratch.height)
  const [a, b, c, d, x, y] = windowTransform(w, h, scene.altitude, scene.azimuth, realWeight)
  mask.save()
  mask.setTransform(a, b, c, d, x + pad, y + pad)
  mask.fillStyle = '#fff'
  const [left, right] = CASEMENTS
  mask.fillRect(left.hinge, 0, right.hinge - left.hinge, 1)
  mask.globalCompositeOperation = 'destination-out'
  mask.save()
  for (const closed of CASEMENTS) {
    castSash(mask, sash?.hinge === closed.hinge ? sash : closed, a, c, d, x + pad, y + pad)
  }
  mask.restore()
  drawWindowFoliage(mask, foliageTime)
  for (const shape of occluders) mask.fill(typeof shape === 'string' ? new Path2D(shape) : shape)
  mask.restore()

  // Keep the integrated transmission on the output surface while reusing the
  // scratch surface for the illuminated color. No GPU readback or filter API.
  ctx.save()
  ctx.globalCompositeOperation = 'lighter'
  ctx.globalAlpha = 1 / samples.length
  for (const [dx, dy] of samples) ctx.drawImage(scratch, dx * radius - pad, dy * radius - pad)
  ctx.restore()
  applyDeskShadowMask(ctx, mask, deskCasters, scene, width, height)

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
