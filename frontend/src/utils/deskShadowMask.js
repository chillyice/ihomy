import { deskShadowVector } from './deskShadowGeometry'

const castBuffers = new WeakMap()

// Coordinates arrive from WindowDesk in CSS pixels. Clip in desk coordinates
// before rotating the artwork, matching the DOM crop and object-fit: contain.
function paintBody(ctx, caster, scaleX, scaleY) {
  const { image, size, angle, x, y, cropLeft, cropWidth, kind } = caster
  ctx.save()
  ctx.scale(scaleX, scaleY)
  if (cropWidth) {
    ctx.beginPath()
    ctx.rect(cropLeft, y - size * 0.75, cropWidth, size * 1.5)
    ctx.clip()
  }
  ctx.translate(x, y)
  ctx.rotate((angle * Math.PI) / 180)
  if (kind === 'photo') {
    // instant-print is opaque paper, including the margin around the photograph.
    ctx.fillRect(-size * 0.42, -size * 0.465, size * 0.84, size * 0.93)
  } else {
    const fit = size / Math.max(image.naturalWidth, image.naturalHeight)
    const width = image.naturalWidth * fit
    const height = image.naturalHeight * fit
    ctx.drawImage(image, -width / 2, -height / 2, width, height)
  }
  ctx.restore()
}

// Remove blocked sunlight from the existing transmission map. One final color/
// alpha pass then shades the window frame, foliage and desk objects together.
export function applyDeskShadowMask(light, work, casters, scene, width, height, realWeight, sourceRadius) {
  if (!casters.length) return
  const canvas = light.canvas
  const w = canvas.width
  const h = canvas.height
  const scaleX = w / width
  const scaleY = h / height
  let buffer = castBuffers.get(canvas)
  if (!buffer) {
    buffer = canvas.ownerDocument.createElement('canvas')
    castBuffers.set(canvas, buffer)
  }
  if (buffer.width !== w) buffer.width = w
  if (buffer.height !== h) buffer.height = h
  const cast = buffer.getContext('2d')
  if (!cast) return
  cast.clearRect(0, 0, w, h)

  // Sample each sliding object once for this canvas paint, including its crop.
  const positions = casters.map((caster) => {
    const dx = parseFloat(caster.node?.style.getPropertyValue('--desk-shift'))
    return dx ? { ...caster, x: caster.x + dx, cropLeft: caster.cropLeft + dx } : caster
  })
  for (const caster of positions) {
    const vector = deskShadowVector(scene, caster.size, caster.kind, realWeight)
    if (!vector || !caster.image.naturalWidth) continue
    work.clearRect(0, 0, work.canvas.width, work.canvas.height)
    paintBody(work, caster, scaleX, scaleY)
    work.save()
    work.globalCompositeOperation = 'destination-in'
    work.drawImage(canvas, 0, 0)
    work.restore()

    // Sweep the sunlit silhouette towards its projected tip, keeping long, low-sun
    // shadows attached. Only sample the portion that can reach this viewport.
    const length = Math.hypot(vector.x, vector.y)
    if (!length) continue
    const visible = Math.min(length, Math.hypot(width, height) + caster.size * 2)
    const steps = Math.max(1, Math.ceil(visible / Math.max(1, caster.size * 0.1)))
    const radius = caster.size * 0.72
    const left = Math.max(0, Math.floor((caster.x - radius) * scaleX))
    const top = Math.max(0, Math.floor((caster.y - radius) * scaleY))
    const bw = Math.min(w, Math.ceil((caster.x + radius) * scaleX)) - left
    const bh = Math.min(h, Math.ceil((caster.y + radius) * scaleY)) - top
    if (bw <= 0 || bh <= 0) continue
    cast.save()
    // Draw only the blurred shadow onto the mask. Drawing the source on top
    // would keep a hard second outline even at the far end of the cast.
    const tipBlur = Math.min(
      caster.size * 0.25,
      (Math.hypot(vector.height, length) * sourceRadius) / Math.sin((scene.altitude * Math.PI) / 180),
    )
    const offscreen = w + Math.ceil(visible * scaleX) + bw + 2
    cast.shadowColor = '#000'
    cast.shadowOffsetX = offscreen
    for (let i = 0; i <= steps; i++) {
      const fraction = (i / steps) * (visible / length)
      cast.shadowBlur = tipBlur * fraction * scaleY
      const dx = vector.x * fraction * scaleX
      const dy = vector.y * fraction * scaleY
      cast.drawImage(work.canvas, left, top, bw, bh, left + dx - offscreen, top + dy, bw, bh)
    }
    cast.restore()
  }
  // Objects sit above the receiving plane. Protect their painted faces from
  // their own cast, including overlapping silhouettes from neighboring objects.
  cast.save()
  cast.globalCompositeOperation = 'destination-out'
  for (const caster of positions) {
    if (caster.image.naturalWidth) paintBody(cast, caster, scaleX, scaleY)
  }
  cast.restore()
  light.save()
  light.globalCompositeOperation = 'destination-out'
  light.drawImage(buffer, 0, 0)
  light.restore()
}
