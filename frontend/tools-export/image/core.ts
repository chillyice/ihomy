// @ts-nocheck
// 由 ihomy scripts/export-tools.mjs 从 frontend/src/utils/tools/imageTool.js 导出。
// 图片工具的纯逻辑:ICO 打包器与 JPEG EXIF 解析(手写,不引第三方库)。
// 图片压缩/缩放/清除元数据走 Canvas,属于浏览器能力,放在页面里;kernel 只保留可单测的字节处理。

export const ICO_SIZES = [16, 32, 48, 64, 128, 256]
export const MAX_ICO_IMAGES = 16
export const MAX_IMAGE_BYTES = 30 * 1024 * 1024

export class ImageToolError extends Error {
  constructor(code, extra = {}) {
    super(code)
    this.code = code
    Object.assign(this, extra)
  }
}

/**
 * 把若干 PNG 图像打包成 ICO(Vista+ 允许条目直接内嵌 PNG)。
 * images: [{ width, height, data: Uint8Array }],尺寸必须是 1–256 的正方形。
 */
export function buildIco(images) {
  if (!Array.isArray(images) || !images.length) throw new ImageToolError('empty')
  if (images.length > MAX_ICO_IMAGES) throw new ImageToolError('tooMany', { max: MAX_ICO_IMAGES })
  for (const image of images) {
    if (!(image?.data instanceof Uint8Array) || !image.data.length) throw new ImageToolError('invalidImage')
    if (!Number.isInteger(image.width) || !Number.isInteger(image.height)) throw new ImageToolError('invalidSize')
    if (image.width < 1 || image.width > 256 || image.width !== image.height) throw new ImageToolError('invalidSize')
  }

  const headerSize = 6 + images.length * 16
  const total = images.reduce((sum, image) => sum + image.data.length, headerSize)
  const output = new Uint8Array(total)
  const view = new DataView(output.buffer)
  view.setUint16(0, 0, true)
  view.setUint16(2, 1, true)
  view.setUint16(4, images.length, true)

  let entryOffset = 6
  let dataOffset = headerSize
  for (const image of images) {
    view.setUint8(entryOffset, image.width === 256 ? 0 : image.width)
    view.setUint8(entryOffset + 1, image.height === 256 ? 0 : image.height)
    view.setUint8(entryOffset + 2, 0)
    view.setUint8(entryOffset + 3, 0)
    view.setUint16(entryOffset + 4, 1, true)
    view.setUint16(entryOffset + 6, 32, true)
    view.setUint32(entryOffset + 8, image.data.length, true)
    view.setUint32(entryOffset + 12, dataOffset, true)
    output.set(image.data, dataOffset)
    entryOffset += 16
    dataOffset += image.data.length
  }
  return output
}

/** 读取 ICO 目录(供测试与页面展示条目),返回 [{width,height,size,offset}] */
export function readIcoDirectory(bytes) {
  const data = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes)
  if (data.length < 6) throw new ImageToolError('invalidIco')
  const view = new DataView(data.buffer, data.byteOffset, data.byteLength)
  if (view.getUint16(0, true) !== 0 || view.getUint16(2, true) !== 1) throw new ImageToolError('invalidIco')
  const count = view.getUint16(4, true)
  if (6 + count * 16 > data.length) throw new ImageToolError('invalidIco')
  const entries = []
  for (let i = 0; i < count; i += 1) {
    const offset = 6 + i * 16
    entries.push({
      width: view.getUint8(offset) || 256,
      height: view.getUint8(offset + 1) || 256,
      size: view.getUint32(offset + 8, true),
      offset: view.getUint32(offset + 12, true),
    })
  }
  return entries
}

const EXIF_TAGS = {
  0x010e: 'ImageDescription',
  0x010f: 'Make',
  0x0110: 'Model',
  0x0112: 'Orientation',
  0x011a: 'XResolution',
  0x011b: 'YResolution',
  0x0128: 'ResolutionUnit',
  0x0131: 'Software',
  0x0132: 'DateTime',
  0x829a: 'ExposureTime',
  0x829d: 'FNumber',
  0x8827: 'ISO',
  0x9003: 'DateTimeOriginal',
  0x9004: 'DateTimeDigitized',
  0x9209: 'Flash',
  0x920a: 'FocalLength',
  0xa002: 'PixelXDimension',
  0xa003: 'PixelYDimension',
  0xa434: 'LensModel',
}

const TYPE_SIZES = { 1: 1, 2: 1, 3: 2, 4: 4, 5: 8, 6: 1, 7: 1, 8: 2, 9: 4, 10: 8, 11: 4, 12: 8 }

function readTagValue(view, type, count, valueOffset, tiffStart, littleEndian) {
  const size = (TYPE_SIZES[type] || 1) * count
  const start = size <= 4 ? valueOffset : view.getUint32(valueOffset, littleEndian) + tiffStart
  if (start < 0 || start + size > view.byteLength) return ''
  const values = []
  for (let i = 0; i < count; i += 1) {
    const offset = start + i * (TYPE_SIZES[type] || 1)
    switch (type) {
      case 1:
      case 7:
        values.push(view.getUint8(offset))
        break
      case 2:
        values.push(String.fromCharCode(view.getUint8(offset)))
        break
      case 3:
        values.push(view.getUint16(offset, littleEndian))
        break
      case 4:
      case 9:
        values.push(view.getUint32(offset, littleEndian))
        break
      case 5:
      case 10: {
        const numerator = view.getUint32(offset, littleEndian)
        const denominator = view.getUint32(offset + 4, littleEndian)
        values.push(denominator ? `${numerator}/${denominator}` : String(numerator))
        break
      }
      default:
        values.push('')
    }
  }
  if (type === 2) return values.join('').replace(/\0+$/, '').trim()
  return values.length === 1 ? values[0] : values.join(', ')
}

function readIfd(view, tiffStart, ifdOffset, tags, visited, littleEndian) {
  if (visited.has(ifdOffset) || ifdOffset < 8) return
  visited.add(ifdOffset)
  if (ifdOffset + 2 > view.byteLength) return
  const count = view.getUint16(ifdOffset, littleEndian)
  for (let i = 0; i < count; i += 1) {
    const entry = ifdOffset + 2 + i * 12
    if (entry + 12 > view.byteLength) return
    const tag = view.getUint16(entry, littleEndian)
    const type = view.getUint16(entry + 2, littleEndian)
    const length = view.getUint32(entry + 4, littleEndian)
    if (tag === 0x8769 || tag === 0x8825) continue
    const value = readTagValue(view, type, length, entry + 8, tiffStart, littleEndian)
    if (value !== '' && EXIF_TAGS[tag]) tags.push({ tag: `0x${tag.toString(16).padStart(4, '0')}`, name: EXIF_TAGS[tag], value })
  }
  const pointerAt = ifdOffset + 2 + count * 12
  if (pointerAt + 4 <= view.byteLength) {
    const next = view.getUint32(pointerAt, littleEndian)
    if (next) readIfd(view, tiffStart, next, tags, visited, littleEndian)
  }
  // Exif 子目录里放拍摄时间等常用项
  const exifPointer = findTag(view, ifdOffset, count, 0x8769, littleEndian)
  if (exifPointer) readIfd(view, tiffStart, exifPointer, tags, visited, littleEndian)
}

function findTag(view, ifdOffset, count, wanted, littleEndian) {
  for (let i = 0; i < count; i += 1) {
    const entry = ifdOffset + 2 + i * 12
    if (entry + 12 > view.byteLength) return 0
    if (view.getUint16(entry, littleEndian) === wanted) return view.getUint32(entry + 8, littleEndian)
  }
  return 0
}

/** 解析 JPEG 的 APP1/EXIF;非 JPEG 或没有 EXIF 时返回 hasExif: false */
export function parseJpegExif(input) {
  const data = input instanceof Uint8Array ? input : new Uint8Array(input)
  if (data.length < 2 || data[0] !== 0xff || data[1] !== 0xd8) throw new ImageToolError('notJpeg')
  let offset = 2
  while (offset + 4 <= data.length) {
    if (data[offset] !== 0xff) {
      offset += 1
      continue
    }
    const marker = data[offset + 1]
    if (marker === 0xd8 || marker === 0x01 || (marker >= 0xd0 && marker <= 0xd7)) {
      offset += 2
      continue
    }
    if (marker === 0xda || marker === 0xd9) break
    const length = (data[offset + 2] << 8) | data[offset + 3]
    if (length < 2) break
    if (marker === 0xe1 && data[offset + 4] === 0x45 && data[offset + 5] === 0x78) {
      const tiffStart = offset + 10
      if (tiffStart + 8 > data.length) break
      const view = new DataView(data.buffer, data.byteOffset + tiffStart, Math.min(length - 8, data.length - tiffStart))
      const littleEndian = view.getUint8(0) === 0x49
      if (view.getUint16(2, littleEndian) !== 42) break
      const ifdOffset = view.getUint32(4, littleEndian)
      const tags = []
      readIfd(view, 0, ifdOffset, tags, new Set(), littleEndian)
      return { hasExif: true, tags }
    }
    offset += 2 + length
  }
  return { hasExif: false, tags: [] }
}

const MESSAGES = {
  empty: { zh: '先选择图片', en: 'Choose an image first' },
  tooMany: { zh: '一次最多打包 {max} 个尺寸', en: 'At most {max} sizes per icon' },
  invalidImage: { zh: '图片数据为空', en: 'The image data is empty' },
  invalidSize: { zh: '图标尺寸必须是 1–256 的正方形', en: 'Icon sizes must be squares between 1 and 256' },
  invalidIco: { zh: 'ICO 文件不完整', en: 'Incomplete ICO file' },
  notJpeg: { zh: '请选择 JPEG 图片', en: 'Choose a JPEG image' },
  cryptoUnavailable: { zh: '当前环境不支持此操作', en: 'This operation is unavailable here' },
}

function fail(code, locale, extra = {}) {
  const template = MESSAGES[code] || MESSAGES.invalidImage
  let message = template[locale] || template.zh
  for (const [key, value] of Object.entries(extra)) message = message.replace(`{${key}}`, String(value))
  return { ok: false, error: { code, message } }
}

const fromHex = (text) => {
  const clean = String(text ?? '')
    .replace(/0x/gi, '')
    .replace(/[\s,]/g, '')
  if (!clean || clean.length % 2 || !/^[0-9a-f]+$/i.test(clean)) return null
  const bytes = new Uint8Array(clean.length / 2)
  for (let i = 0; i < bytes.length; i += 1) bytes[i] = parseInt(clean.slice(i * 2, i * 2 + 2), 16)
  return bytes
}

/** 统一入口(kernel 契约):input 为 JPEG 的十六进制字节串,输出 EXIF 摘要。 */
export function run(input, _options = {}, ctx = {}) {
  const locale = ctx.locale === 'en' ? 'en' : 'zh'
  const bytes = input instanceof Uint8Array ? input : fromHex(input)
  if (!bytes) return fail('invalidImage', locale)
  try {
    const result = parseJpegExif(bytes)
    return {
      ok: true,
      output: result.tags.map((tag) => `${tag.name}: ${tag.value}`).join('\n'),
      data: result,
      stats: { tags: result.tags.length },
    }
  } catch (error) {
    return fail(error?.code || 'notJpeg', locale)
  }
}
