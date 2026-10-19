// 颜色与前端设计工具的纯逻辑:HEX / RGB / HSL / HSV / LAB 互转、WCAG 对比度、配色与混色。
// 不做色彩管理(全部按 sRGB / D65),不引第三方颜色库。

const clamp = (value, min, max) => Math.min(max, Math.max(min, value))
const round = (value, digits = 0) => {
  const factor = 10 ** digits
  return Math.round(value * factor) / factor
}

const toHexPair = (value) => clamp(Math.round(value), 0, 255).toString(16).padStart(2, '0')

/** 解析 #rgb / #rgba / #rrggbb / #rrggbbaa / rgb() / rgba() / hsl() / hsla();失败返回 null。 */
export function parseColor(input) {
  const text = String(input || '')
    .trim()
    .toLowerCase()
  if (!text) return null

  const hex = text.match(/^#?([0-9a-f]{3,8})$/)
  if (hex) {
    const raw = hex[1]
    if (![3, 4, 6, 8].includes(raw.length)) return null
    const expand = (chunk) => (raw.length <= 4 ? chunk + chunk : chunk)
    const parts = raw.length <= 4 ? raw.split('') : raw.match(/.{2}/g)
    const [r, g, b, a] = parts.map((part) => parseInt(expand(part), 16))
    return { r, g, b, a: a === undefined ? 1 : round(a / 255, 2) }
  }

  const rgb = text.match(/^rgba?\(([^)]+)\)$/)
  if (rgb) {
    const parts = rgb[1].split(/[\s,/]+/).filter(Boolean)
    if (parts.length < 3) return null
    const channel = (value) => (value.endsWith('%') ? (parseFloat(value) / 100) * 255 : parseFloat(value))
    const [r, g, b] = parts.slice(0, 3).map(channel)
    if ([r, g, b].some((value) => !Number.isFinite(value))) return null
    const alpha = parts[3] === undefined ? 1 : parseFloat(parts[3])
    return {
      r: clamp(Math.round(r), 0, 255),
      g: clamp(Math.round(g), 0, 255),
      b: clamp(Math.round(b), 0, 255),
      a: Number.isFinite(alpha) ? clamp(alpha, 0, 1) : 1,
    }
  }

  const hsl = text.match(/^hsla?\(([^)]+)\)$/)
  if (hsl) {
    const parts = hsl[1].split(/[\s,/]+/).filter(Boolean)
    if (parts.length < 3) return null
    const hue = parseFloat(parts[0])
    const saturation = parseFloat(parts[1])
    const lightness = parseFloat(parts[2])
    if ([hue, saturation, lightness].some((value) => !Number.isFinite(value))) return null
    const rgbValue = hslToRgb({ h: hue, s: clamp(saturation, 0, 100), l: clamp(lightness, 0, 100) })
    const alpha = parts[3] === undefined ? 1 : parseFloat(parts[3])
    return { ...rgbValue, a: Number.isFinite(alpha) ? clamp(alpha, 0, 1) : 1 }
  }

  return null
}

export function rgbToHex({ r, g, b, a = 1 }) {
  const base = `#${toHexPair(r)}${toHexPair(g)}${toHexPair(b)}`
  return a >= 1 ? base : `${base}${toHexPair(a * 255)}`
}

export function rgbToHsl({ r, g, b }) {
  const rn = r / 255
  const gn = g / 255
  const bn = b / 255
  const max = Math.max(rn, gn, bn)
  const min = Math.min(rn, gn, bn)
  const delta = max - min
  const l = (max + min) / 2
  let h = 0
  let s = 0
  if (delta) {
    s = l > 0.5 ? delta / (2 - max - min) : delta / (max + min)
    if (max === rn) h = ((gn - bn) / delta) % 6
    else if (max === gn) h = (bn - rn) / delta + 2
    else h = (rn - gn) / delta + 4
    h *= 60
    if (h < 0) h += 360
  }
  return { h: round(h, 1), s: round(s * 100, 1), l: round(l * 100, 1) }
}

export function hslToRgb({ h, s, l }) {
  const hue = ((h % 360) + 360) % 360
  const sat = clamp(s, 0, 100) / 100
  const light = clamp(l, 0, 100) / 100
  const c = (1 - Math.abs(2 * light - 1)) * sat
  const x = c * (1 - Math.abs(((hue / 60) % 2) - 1))
  const m = light - c / 2
  const sector = Math.floor(hue / 60) % 6
  const table = [
    [c, x, 0],
    [x, c, 0],
    [0, c, x],
    [0, x, c],
    [x, 0, c],
    [c, 0, x],
  ][sector]
  return { r: Math.round((table[0] + m) * 255), g: Math.round((table[1] + m) * 255), b: Math.round((table[2] + m) * 255) }
}

export function rgbToHsv({ r, g, b }) {
  const rn = r / 255
  const gn = g / 255
  const bn = b / 255
  const max = Math.max(rn, gn, bn)
  const min = Math.min(rn, gn, bn)
  const delta = max - min
  let h = 0
  if (delta) {
    if (max === rn) h = ((gn - bn) / delta) % 6
    else if (max === gn) h = (bn - rn) / delta + 2
    else h = (rn - gn) / delta + 4
    h *= 60
    if (h < 0) h += 360
  }
  return {
    h: round(h, 1),
    s: round((max === 0 ? 0 : delta / max) * 100, 1),
    v: round(max * 100, 1),
  }
}

const srgbToLinear = (channel) => {
  const value = channel / 255
  return value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4
}

export function rgbToLab({ r, g, b }) {
  const rl = srgbToLinear(r)
  const gl = srgbToLinear(g)
  const bl = srgbToLinear(b)
  const x = (rl * 0.4124 + gl * 0.3576 + bl * 0.1805) / 0.95047
  const y = rl * 0.2126 + gl * 0.7152 + bl * 0.0722
  const z = (rl * 0.0193 + gl * 0.1192 + bl * 0.9505) / 1.08883
  const f = (value) => (value > 0.008856 ? Math.cbrt(value) : 7.787 * value + 16 / 116)
  const fx = f(x)
  const fy = f(y)
  const fz = f(z)
  return {
    l: round(116 * fy - 16, 2),
    a: round(500 * (fx - fy), 2),
    b: round(200 * (fy - fz), 2),
  }
}

const relativeLuminance = ({ r, g, b }) => 0.2126 * srgbToLinear(r) + 0.7152 * srgbToLinear(g) + 0.0722 * srgbToLinear(b)

/** WCAG 2.x 对比度(1–21)。 */
export function contrastRatio(colorA, colorB) {
  const a = relativeLuminance(colorA)
  const b = relativeLuminance(colorB)
  const [light, dark] = a > b ? [a, b] : [b, a]
  return round((light + 0.05) / (dark + 0.05), 2)
}

export function wcagReport(ratio) {
  return {
    aa: ratio >= 4.5,
    aaa: ratio >= 7,
    aaLarge: ratio >= 3,
    aaaLarge: ratio >= 4.5,
  }
}

/** 黑/白两种文字色里对底色对比度更高的那个。 */
export function readableText(color) {
  const black = { r: 0, g: 0, b: 0 }
  const white = { r: 255, g: 255, b: 255 }
  return contrastRatio(color, black) >= contrastRatio(color, white) ? '#000000' : '#ffffff'
}

export function mix(colorA, colorB, weight = 0.5) {
  const ratio = clamp(weight, 0, 1)
  return {
    r: Math.round(colorA.r * (1 - ratio) + colorB.r * ratio),
    g: Math.round(colorA.g * (1 - ratio) + colorB.g * ratio),
    b: Math.round(colorA.b * (1 - ratio) + colorB.b * ratio),
    a: round((colorA.a ?? 1) * (1 - ratio) + (colorB.a ?? 1) * ratio, 2),
  }
}

const rotate = (hsl, degrees) => hslToRgb({ h: hsl.h + degrees, s: hsl.s, l: hsl.l })

/** 互补 / 邻近 / 三角 / 分裂互补 / 四角配色。 */
export function harmonize(color) {
  const hsl = rgbToHsl(color)
  const groups = [
    { kind: 'complementary', offsets: [180] },
    { kind: 'analogous', offsets: [-30, 30] },
    { kind: 'triadic', offsets: [120, 240] },
    { kind: 'split', offsets: [150, 210] },
    { kind: 'tetradic', offsets: [90, 180, 270] },
  ]
  return groups.map((group) => ({
    kind: group.kind,
    colors: group.offsets.map((offset) => rgbToHex(rotate(hsl, offset))),
  }))
}

/** CSS 变量片段(页面直接复制)。 */
export function cssVariables(color) {
  const hsl = rgbToHsl(color)
  return [
    `--color: ${rgbToHex(color)};`,
    `--color-rgb: ${color.r}, ${color.g}, ${color.b};`,
    `--color-hsl: ${hsl.h} ${hsl.s}% ${hsl.l}%;`,
  ].join('\n')
}

const MESSAGES = {
  invalidColor: { zh: '无法识别这个颜色,支持 HEX / RGB / HSL', en: 'Unrecognized color; use HEX / RGB / HSL' },
}

/** 统一入口(kernel 契约):输入一个颜色,输出各格式、对比度与配色。 */
export function run(input, _options = {}, ctx = {}) {
  const locale = ctx.locale === 'en' ? 'en' : 'zh'
  const color = parseColor(input)
  if (!color) {
    const template = MESSAGES.invalidColor
    return { ok: false, error: { code: 'invalidColor', message: template[locale] || template.zh } }
  }
  const hsl = rgbToHsl(color)
  const hsv = rgbToHsv(color)
  const lab = rgbToLab(color)
  const hex = rgbToHex(color)
  const onWhite = contrastRatio(color, { r: 255, g: 255, b: 255 })
  const onBlack = contrastRatio(color, { r: 0, g: 0, b: 0 })
  return {
    ok: true,
    output: [hex, `rgb(${color.r}, ${color.g}, ${color.b})`, `hsl(${hsl.h}, ${hsl.s}%, ${hsl.l}%)`].join('\n'),
    data: { hex, rgb: color, hsl, hsv, lab, onWhite, onBlack, harmonize: harmonize(color), css: cssVariables(color) },
    stats: { contrastWhite: onWhite, contrastBlack: onBlack },
  }
}
