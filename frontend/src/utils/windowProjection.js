// Window-plane coordinates run from (0, 0) to (1, 1). The projected footprint
// stretches in the direction opposite the sun; grazing incidence is bounded
// because only the portion of the receiving surface inside the room is visible.
export function windowTransform(width, height, altitude, azimuth) {
  const radians = Math.PI / 180
  const bearing = (180 - azimuth) * radians
  const reach = 1 / (0.65 + Math.tan(Math.min(89.9, Math.max(0, altitude)) * radians))
  const paneWidth = width * 0.88
  const shear = Math.sin(bearing) * reach * width * 0.63
  const depth = height * (0.43 + Math.cos(bearing) * reach * 0.61)
  return [paneWidth, 0, shear, depth, (width - paneWidth) / 2 + shear * 0.15, height * 0.075]
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

// occluders accepts filled SVG path strings or Path2D objects in window-plane
// coordinates. Overlapping silhouettes block the same light only once.
export function drawWindowProjection(canvas, { width, height, scene, weather = 'clear', shadowAlpha = 0, occluders = [] }) {
  const scale = Math.min(0.5, 512 / Math.max(1, width, height))
  const w = Math.max(1, Math.round(width * scale))
  const h = Math.max(1, Math.round(height * scale))
  resize(canvas, w, h)
  const ctx = canvas.getContext('2d')
  if (!ctx) return
  ctx.clearRect(0, 0, w, h)
  if (!scene?.hasDirectLight || ['rain', 'snow', 'thunder'].includes(weather) || width <= 0 || height <= 0) return

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
  const [a, b, c, d, x, y] = windowTransform(w, h, scene.altitude, scene.azimuth)
  mask.save()
  mask.setTransform(a, b, c, d, x + pad, y + pad)
  mask.fillStyle = '#fff'
  mask.fillRect(0, 0, 1, 1)
  mask.globalCompositeOperation = 'destination-out'
  mask.fillRect(0.48, 0, 0.04, 1)
  mask.fillRect(0, 0.46, 1, 0.046)
  for (const shape of occluders) mask.fill(typeof shape === 'string' ? new Path2D(shape) : shape)
  mask.restore()

  // Keep the integrated transmission on the output surface while reusing the
  // scratch surface for the illuminated color. No GPU readback or filter API.
  ctx.save()
  ctx.globalCompositeOperation = 'lighter'
  ctx.globalAlpha = 1 / samples.length
  for (const [dx, dy] of samples) ctx.drawImage(scratch, dx * radius - pad, dy * radius - pad)
  ctx.restore()

  mask.clearRect(0, 0, scratch.width, scratch.height)
  mask.drawImage(canvas, 0, 0)
  mask.save()
  mask.globalCompositeOperation = 'source-in'
  const sunlight = mask.createLinearGradient(x + a * 0.5, y, x + a * 0.5 + c, y + d)
  sunlight.addColorStop(0, scene.palette.core)
  sunlight.addColorStop(0.6, scene.palette.mid)
  sunlight.addColorStop(1, scene.palette.ambient)
  mask.fillStyle = sunlight
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
}
