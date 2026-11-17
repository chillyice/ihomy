const clamp = (n) => Math.max(0, Math.min(1, n))
const mix = (a, b, t) => a + (b - a) * t
const ease = (t) => t * t * (3 - 2 * t)
const smooth = (a, b, n) => ease(clamp((n - a) / (b - a)))
const bell = (x) => Math.exp(-x * x)

// Box–Muller: 高斯样本只放在格点上，插值后才是空间连续的随机场。
// 用每朵云自己的种子，重绘不改变云形，也不影响其他随机效果。
function gaussianField(seed) {
  let state = seed >>> 0
  const next = () => {
    state = (Math.imul(1664525, state) + 1013904223) >>> 0
    return state / 4294967296
  }
  const size = 128
  const mask = size - 1
  const grid = new Float32Array(size * size)
  for (let i = 0; i < grid.length; i += 2) {
    const r = Math.sqrt(-2 * Math.log(1 - next()))
    const a = 2 * Math.PI * next()
    grid[i] = r * Math.cos(a)
    grid[i + 1] = r * Math.sin(a)
  }
  return (x, y) => {
    const ix = Math.floor(x)
    const iy = Math.floor(y)
    const a = (iy & mask) * size
    const b = ((iy + 1) & mask) * size
    const l = ix & mask
    const r = (ix + 1) & mask
    const u = ease(x - ix)
    return mix(mix(grid[a + l], grid[a + r], u), mix(grid[b + l], grid[b + r], u), ease(y - iy))
  }
}

const PROFILES = {
  cumulus: { edge: 0.29, shade: 0.97, stretch: 0.46, scale: 4.65, texture: 0.76, strength: 4.8 },
  cumulonimbus: { edge: 0.37, shade: 0.66, stretch: 0.76, scale: 4.85, texture: 0.63, strength: 5.2 },
  stratocumulus: { edge: 0.42, shade: 0.84, stretch: 0.38, scale: 5.45, texture: 0.79, strength: 4.3 },
  altocumulus: { edge: 0.38, shade: 0.92, stretch: 0.31, scale: 11.2, texture: 1.24, strength: 3.9 },
  cirrus: { edge: 0.22, shade: 0.94, stretch: 2.5, scale: 2.6, texture: 0.8, strength: 1.32 },
  stratus: { edge: 0.57, shade: 0.76, stretch: 1.12, scale: 2.15, texture: 0.42, strength: 2.05 },
  nimbostratus: { edge: 0.6, shade: 0.54, stretch: 0.65, scale: 3.2, texture: 0.5, strength: 3.15 },
  fogbank: { edge: 0.71, shade: 0.94, stretch: 1.42, scale: 1.95, texture: 0.46, strength: 1.4 },
}

// 云型只约束气团的分布、风切与底部高度，像素轮廓由扰动后的密度等值线自然形成。
function envelopeFor(kind, noise) {
  const skew = Math.max(-0.37, Math.min(0.37, noise(8, 32) * 0.19))
  const lift = Math.max(-0.18, Math.min(0.16, noise(39, 8) * 0.13))
  if (kind === 'cumulus') {
    return (x, y) => {
      const ridge = x - skew * (0.6 - y)
      const crown = -0.59 + lift + 0.59 * ridge * ridge + 0.18 * noise(x * 1.45, 35)
      return 1.61 * bell(ridge / (0.67 + 0.16 * y)) * smooth(crown - 0.17, crown + 0.33, y) * (1 - smooth(0.45, 0.74, y))
    }
  }
  if (kind === 'cumulonimbus') {
    return (x, y) => {
      const spine = skew * (0.44 - y) + 0.12 * noise(17, y * 1.4)
      const tower = 1.46 * bell((x - spine) / (0.42 + 0.12 * y)) * bell((y + 0.05) / 0.96)
      const anvil = 1.17 * bell((x + skew) / 0.91) * bell((y + 0.55 - x * skew * 0.23) / 0.25)
      return Math.max(tower, anvil) * (1 - smooth(0.61, 0.86, y))
    }
  }
  if (kind === 'cirrus') {
    return (x, y) => {
      const ribbon = y - 0.4 * x * x + (0.52 + skew) * x - lift
      return 1.07 * bell(x / 0.91) * bell(ribbon / 0.53)
    }
  }
  if (kind === 'altocumulus' || kind === 'stratocumulus') {
    const broken = kind === 'altocumulus'
    return (x, y) => {
      const base = y + 0.11 * noise(x * 1.9, 15) + x * skew
      return (broken ? 1.12 : 1.46) * bell(x / 0.89) * bell((base - lift) / (broken ? 0.69 : 0.59))
    }
  }
  return (x, y) => {
    const low = kind === 'fogbank'
    const band = y - (low ? 0.35 : 0.08) - 0.18 * noise(x * 1.52, 21) - x * skew
    return (low ? 1.13 : 1.47) * bell(x / 0.99) * bell((band - lift) / (kind === 'nimbostratus' ? 0.75 : 0.49))
  }
}

/**
 * 栅格化一次，运行时只移动画布。云光用局部密度梯度与沿光线探测的遮蔽近似，
 * 让突出的云壁先亮、厚云内部发暗；这是薄片的体积感近似，不是完整的光线步进。
 */
export function paintCloud(canvas, kind, seed, tone, width, height) {
  canvas.width = width
  canvas.height = height
  const ctx = canvas.getContext('2d')
  if (!ctx) return
  const image = ctx.createImageData(width, height)
  const density = new Float32Array(width * height)
  const noise = gaussianField(seed)
  const shape = envelopeFor(kind, noise)
  const { edge, stretch, scale, texture, shade, strength } = PROFILES[kind]
  const fine = kind === 'cirrus' || kind === 'fogbank'
  const warp = fine ? 0.068 : 0.034
  const cut = kind === 'altocumulus' ? 0.93 : kind === 'cirrus' ? 0.78 : 0.64
  const atX = (i) => (2 * i) / (width - 1) - 1
  const atY = (j) => (2 * j) / (height - 1) - 1
  for (let j = 1; j < height - 1; j++) {
    const v = atY(j)
    for (let i = 1; i < width - 1; i++) {
      const u = atX(i)
      const margin = (1 - smooth(0.86, 1, Math.abs(u))) * (1 - smooth(0.83, 1, Math.abs(v)))
      const x = u + warp * noise(u * 2.45 + 18.2, v * 2.34 + 23.8)
      const y = v + warp * noise(u * 2.5 + 35.9, v * 2.58 + 11.7)
      let nx = x * scale
      let ny = (y + (fine ? 0.37 * x - 0.38 * x * x : 0)) * scale * stretch
      let roughness = 0
      let gain = 0.48
      for (let layer = 0; layer < 5; layer++) {
        roughness += gain * noise(nx + layer * 13.2, ny + layer * 17.9)
        const nextX = nx * 1.94 + ny * 0.64
        ny = ny * 1.94 - nx * 0.64
        nx = nextX
        gain *= 0.61
      }
      const mass = shape(x, y)
      density[j * width + i] = margin * Math.max(0, mass - cut + roughness * texture * smooth(0.04, 0.78, mass))
    }
  }

  const stepX = Math.max(1, Math.round(width * 0.016))
  const stepY = Math.max(1, Math.round(height * 0.039))
  for (let j = 1; j < height - 1; j++) {
    for (let i = 1; i < width - 1; i++) {
      const k = j * width + i
      const d = density[k]
      if (d <= 0) continue
      const near = density[Math.max(0, j - stepY) * width + Math.max(0, i - stepX)]
      const far = density[Math.max(0, j - stepY * 3) * width + Math.max(0, i - stepX * 3)]
      const light = fine ? clamp(shade - 0.14 * d) : clamp(shade - atY(j) * 0.21 + (d - near) * 0.48 - near * 0.16 - far * 0.19)
      const alpha = (1 - Math.exp(-strength * d)) * smooth(0, edge, d)
      const p = k * 4
      for (let c = 0; c < 3; c++) image.data[p + c] = mix(tone.lo[c], tone.hi[c], light)
      image.data[p + 3] = alpha * 255
    }
  }
  ctx.putImageData(image, 0, 0)
}
