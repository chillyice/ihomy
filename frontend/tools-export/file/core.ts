// @ts-nocheck
// 由 ihomy scripts/export-tools.mjs 从 frontend/src/utils/tools/fileTool.js 导出。
// 文件工具的纯逻辑:magic bytes 类型识别与 ZIP 条目命名去重。
// ZIP 打包/解压本身依赖 fflate,放在页面里动态 import,kernel 保持零依赖、可被 bit_pouch 直接复用。

const SIG = (hex, offset = 0) => ({
  offset,
  bytes: hex
    .replace(/\s/g, '')
    .match(/.{2}/g)
    .map((byte) => parseInt(byte, 16)),
})

const startsWith = (data, signature) => signature.bytes.every((byte, index) => data[signature.offset + index] === byte)

const ascii = (text) => Array.from(text, (char) => char.charCodeAt(0))

const FILE_TYPES = [
  { ext: 'png', mime: 'image/png', name: 'PNG 图片', signatures: [SIG('89504e47 0d0a1a0a')] },
  { ext: 'jpg', mime: 'image/jpeg', name: 'JPEG 图片', signatures: [SIG('ffd8ff')] },
  { ext: 'gif', mime: 'image/gif', name: 'GIF 图片', signatures: [SIG('47494638')] },
  {
    ext: 'webp',
    mime: 'image/webp',
    name: 'WebP 图片',
    signatures: [
      { offset: 0, bytes: ascii('RIFF') },
      { offset: 8, bytes: ascii('WEBP') },
    ],
    compound: true,
  },
  { ext: 'bmp', mime: 'image/bmp', name: 'BMP 图片', signatures: [SIG('424d')] },
  { ext: 'tif', mime: 'image/tiff', name: 'TIFF 图片', signatures: [SIG('49492a00'), SIG('4d4d002a')] },
  { ext: 'ico', mime: 'image/x-icon', name: 'ICO 图标', signatures: [SIG('00000100')] },
  { ext: 'pdf', mime: 'application/pdf', name: 'PDF 文档', signatures: [SIG('25504446')] },
  { ext: 'zip', mime: 'application/zip', name: 'ZIP 压缩包', signatures: [SIG('504b0304'), SIG('504b0506')] },
  { ext: 'gz', mime: 'application/gzip', name: 'GZIP 压缩包', signatures: [SIG('1f8b')] },
  { ext: 'rar', mime: 'application/vnd.rar', name: 'RAR 压缩包', signatures: [SIG('526172211a07')] },
  { ext: '7z', mime: 'application/x-7z-compressed', name: '7-Zip 压缩包', signatures: [SIG('377abcaf271c')] },
  { ext: 'mp3', mime: 'audio/mpeg', name: 'MP3 音频', signatures: [SIG('494433'), SIG('fffb'), SIG('fff3')] },
  { ext: 'flac', mime: 'audio/flac', name: 'FLAC 音频', signatures: [SIG('664c6143')] },
  { ext: 'ogg', mime: 'audio/ogg', name: 'OGG 音频', signatures: [SIG('4f676753')] },
  {
    ext: 'wav',
    mime: 'audio/wav',
    name: 'WAV 音频',
    signatures: [
      { offset: 0, bytes: ascii('RIFF') },
      { offset: 8, bytes: ascii('WAVE') },
    ],
    compound: true,
  },
  { ext: 'mp4', mime: 'video/mp4', name: 'MP4 视频', signatures: [{ offset: 4, bytes: ascii('ftyp') }] },
  { ext: 'mkv', mime: 'video/x-matroska', name: 'Matroska 视频', signatures: [SIG('1a45dfa3')] },
  {
    ext: 'avi',
    mime: 'video/x-msvideo',
    name: 'AVI 视频',
    signatures: [
      { offset: 0, bytes: ascii('RIFF') },
      { offset: 8, bytes: ascii('AVI ') },
    ],
    compound: true,
  },
  { ext: 'elf', mime: 'application/x-elf', name: 'ELF 可执行文件', signatures: [SIG('7f454c46')] },
  { ext: 'exe', mime: 'application/vnd.microsoft.portable-executable', name: 'Windows 可执行文件', signatures: [SIG('4d5a')] },
  { ext: 'sqlite', mime: 'application/vnd.sqlite3', name: 'SQLite 数据库', signatures: [{ bytes: ascii('SQLite format 3\0') }] },
  { ext: 'ttf', mime: 'font/ttf', name: 'TrueType 字体', signatures: [SIG('00010000')] },
  { ext: 'otf', mime: 'font/otf', name: 'OpenType 字体', signatures: [{ bytes: ascii('OTTO') }] },
  { ext: 'woff', mime: 'font/woff', name: 'WOFF 字体', signatures: [{ bytes: ascii('wOFF') }] },
  { ext: 'woff2', mime: 'font/woff2', name: 'WOFF2 字体', signatures: [{ bytes: ascii('wOF2') }] },
  { ext: 'psd', mime: 'image/vnd.adobe.photoshop', name: 'Photoshop 文档', signatures: [{ bytes: ascii('8BPS') }] },
  { ext: 'wasm', mime: 'application/wasm', name: 'WebAssembly 模块', signatures: [SIG('0061736d')] },
  { ext: 'mid', mime: 'audio/midi', name: 'MIDI 音频', signatures: [{ bytes: ascii('MThd') }] },
  { ext: 'flv', mime: 'video/x-flv', name: 'FLV 视频', signatures: [{ bytes: ascii('FLV') }] },
]

const TEXT_TYPES = [
  { ext: 'svg', mime: 'image/svg+xml', name: 'SVG 图片', pattern: /<svg[\s>]/i },
  { ext: 'html', mime: 'text/html', name: 'HTML 文档', pattern: /<!doctype html|<html[\s>]/i },
  { ext: 'xml', mime: 'application/xml', name: 'XML 文档', pattern: /^\s*<\?xml/i },
  { ext: 'json', mime: 'application/json', name: 'JSON 文本', pattern: /^\s*[{[][\s\S]*[}\]]\s*$/ },
]

const decodeHead = (data) => new TextDecoder('utf-8', { fatal: false }).decode(data.subarray(0, 512))

/** 按文件头识别类型;识别不出返回 null。ZIP 容器(epub/docx 等)统一按 ZIP 报告。 */
export function detectFileType(input) {
  const data = input instanceof Uint8Array ? input : new Uint8Array(input)
  if (!data.length) return null
  for (const type of FILE_TYPES) {
    if (type.compound) {
      if (type.signatures.every((signature) => startsWith(data, signature))) return type
      continue
    }
    if (type.signatures.some((signature) => startsWith(data, signature))) return type
  }
  const head = decodeHead(data)
  for (const type of TEXT_TYPES) if (type.pattern.test(head)) return type
  return null
}

/** ZIP 条目重名策略:a.txt → a(1).txt → a(2).txt(大小写不敏感) */
export function uniqueName(name, used) {
  const taken = used instanceof Set ? used : new Set(used || [])
  const raw = String(name || 'file')
  if (!taken.has(raw.toLowerCase())) {
    taken.add(raw.toLowerCase())
    return raw
  }
  const dot = raw.lastIndexOf('.')
  const base = dot > 0 ? raw.slice(0, dot) : raw
  const ext = dot > 0 ? raw.slice(dot) : ''
  let index = 1
  let candidate = `${base}(${index})${ext}`
  while (taken.has(candidate.toLowerCase())) {
    index += 1
    candidate = `${base}(${index})${ext}`
  }
  taken.add(candidate.toLowerCase())
  return candidate
}

/** 一次为多个文件名去重(保持顺序) */
export function planZipNames(names) {
  const used = new Set()
  return names.map((name) => uniqueName(name, used))
}

export function formatFileSize(bytes) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

const MESSAGES = {
  empty: { zh: '先选择文件', en: 'Choose a file first' },
  invalidHex: { zh: '十六进制内容格式不正确', en: 'Invalid hex content' },
}

function fail(code, locale) {
  const template = MESSAGES[code] || MESSAGES.empty
  return { ok: false, error: { code, message: template[locale] || template.zh } }
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

/** 统一入口:input 为文件头的十六进制字节串,输出识别结果。 */
export function run(input, _options = {}, ctx = {}) {
  const locale = ctx.locale === 'en' ? 'en' : 'zh'
  const bytes = input instanceof Uint8Array ? input : fromHex(input)
  if (!bytes || !bytes.length) return fail('invalidHex', locale)
  const type = detectFileType(bytes)
  if (!type) return { ok: true, output: '', data: null, stats: { matched: false } }
  return {
    ok: true,
    output: `${type.name} (${type.mime})`,
    data: { ext: type.ext, mime: type.mime, name: type.name },
    stats: { matched: true },
  }
}
