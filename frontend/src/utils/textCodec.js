// 字符编码转换的纯逻辑:Unicode 转义 / HTML 实体 / URL / Base64 / UTF-8 字节 / 逐字码位。
// 全部基于浏览器原生 API(TextEncoder/TextDecoder/btoa/atob)与手写循环,不引第三方库;
// 解码失败只抛带 code 的 CodecError,文案由页面按 i18n 渲染。

export const CODEC_FORMATS = ['unicode', 'html', 'url', 'base64', 'hex']

export class CodecError extends Error {
  constructor(code) {
    super(code)
    this.code = code
  }
}

const hex = (n, pad = 2) => n.toString(16).toUpperCase().padStart(pad, '0')

// 控制字符用可见符号显示,否则表格里是一片空白;换行/制表符用更常见的符号
const visibleChar = (cp) => {
  if (cp === 0x0a) return '⏎'
  if (cp === 0x09) return '⇥'
  if (cp === 0x20) return '␠'
  if (cp < 0x20 || cp === 0x7f) return String.fromCodePoint(cp < 0x20 ? 0x2400 + cp : 0x2421)
  return String.fromCodePoint(cp)
}

const NAMED_ENTITIES = {
  amp: '&',
  lt: '<',
  gt: '>',
  quot: '"',
  apos: "'",
  nbsp: '\u00a0',
}

const ESCAPED_ENTITIES = {
  '&': '&amp;',
  '<': '&lt;',
  '>': '&gt;',
  '"': '&quot;',
  "'": '&#39;',
}

/** 文本 → Unicode 转义:非 ASCII 才转,emoji 按 UTF-16 代理对输出 */
export function toUnicodeEscapes(text) {
  let out = ''
  for (const ch of text) {
    const cp = ch.codePointAt(0)
    if (cp < 0x80) {
      out += ch
    } else if (cp > 0xffff) {
      const off = cp - 0x10000
      out += `\\u${hex(0xd800 + (off >> 10), 4)}\\u${hex(0xdc00 + (off & 0x3ff), 4)}`
    } else {
      out += `\\u${hex(cp, 4)}`
    }
  }
  return out
}

/** Unicode 转义 → 文本:同时接受 \uXXXX、\u{XXXXX} 与 U+XXXX 三种写法 */
export function fromUnicodeEscapes(text) {
  return text.replace(/\\u\{([0-9a-fA-F]{1,6})\}|\\u([0-9a-fA-F]{4})|U\+([0-9a-fA-F]{4,6})(?:\s+(?=U\+))?/g, (raw, braced, four, plus) => {
    const cp = parseInt(braced || four || plus, 16)
    return cp <= 0x10ffff ? String.fromCodePoint(cp) : raw
  })
}

/** 文本 → HTML 实体:五个特殊字符用命名实体,其余非 ASCII 用十进制数字实体 */
export function toHtmlEntities(text) {
  let out = ''
  for (const ch of text) {
    if (ESCAPED_ENTITIES[ch]) {
      out += ESCAPED_ENTITIES[ch]
      continue
    }
    const cp = ch.codePointAt(0)
    out += cp > 0x7e ? `&#${cp};` : ch
  }
  return out
}

/** HTML 实体 → 文本:支持 &#123;、&#x7B; 与常见命名实体 */
export function fromHtmlEntities(text) {
  return text.replace(/&#x([0-9a-fA-F]+);|&#(\d+);|&([a-zA-Z][a-zA-Z0-9]*);/g, (raw, x, dec, name) => {
    if (x) {
      const cp = parseInt(x, 16)
      return cp <= 0x10ffff ? String.fromCodePoint(cp) : raw
    }
    if (dec) {
      const cp = parseInt(dec, 10)
      return cp <= 0x10ffff ? String.fromCodePoint(cp) : raw
    }
    const value = NAMED_ENTITIES[name.toLowerCase()]
    return value === undefined ? raw : value
  })
}

export const toUrlEncoded = (text) => encodeURIComponent(text)

export function fromUrlEncoded(text) {
  try {
    return decodeURIComponent(text)
  } catch {
    throw new CodecError('invalidUrl')
  }
}

/** 文本 → Base64(按 UTF-8 字节,分块避免大文本触发参数上限) */
export function toBase64(text) {
  const bytes = new TextEncoder().encode(text)
  let bin = ''
  const CHUNK = 0x8000
  for (let i = 0; i < bytes.length; i += CHUNK) {
    bin += String.fromCharCode(...bytes.subarray(i, i + CHUNK))
  }
  return btoa(bin)
}

/** Base64 → 文本:忽略空白,允许省略末尾 = */
export function fromBase64(text) {
  const clean = text.replace(/\s+/g, '')
  if (!/^[A-Za-z0-9+/]*={0,2}$/.test(clean) || clean.length % 4 === 1) throw new CodecError('invalidBase64')
  const padded = clean + '='.repeat((4 - (clean.length % 4)) % 4)
  let bin
  try {
    bin = atob(padded)
  } catch {
    throw new CodecError('invalidBase64')
  }
  const bytes = Uint8Array.from(bin, (c) => c.charCodeAt(0))
  return new TextDecoder('utf-8').decode(bytes)
}

/** 文本 → UTF-8 字节(十六进制,空格分隔) */
export function toUtf8Hex(text) {
  return Array.from(new TextEncoder().encode(text), (b) => hex(b)).join(' ')
}

/** UTF-8 字节 → 文本:接受空格/逗号/换行分隔,也接受 0x41、\x41、4142 这类写法 */
export function fromUtf8Hex(text) {
  const cleaned = text
    .replace(/\\x/gi, '')
    .replace(/0x/gi, '')
    .replace(/[\s,]+/g, '')
  if (!cleaned || !/^[0-9a-fA-F]+$/.test(cleaned) || cleaned.length % 2) throw new CodecError('invalidHex')
  const bytes = new Uint8Array(cleaned.length / 2)
  for (let i = 0; i < bytes.length; i++) bytes[i] = parseInt(cleaned.slice(i * 2, i * 2 + 2), 16)
  return new TextDecoder('utf-8').decode(bytes)
}

/**
 * 按格式解码。返回 {ok, text} 或 {ok:false, error:'invalidUrl'|'invalidBase64'|'invalidHex'}。
 * Unicode 转义没有「非法」概念(不符合模式的部分原样保留),因此不会失败。
 */
export function decodeText(format, text) {
  try {
    switch (format) {
      case 'unicode':
        return { ok: true, text: fromUnicodeEscapes(text) }
      case 'html':
        return { ok: true, text: fromHtmlEntities(text) }
      case 'url':
        return { ok: true, text: fromUrlEncoded(text) }
      case 'base64':
        return { ok: true, text: fromBase64(text) }
      case 'hex':
        return { ok: true, text: fromUtf8Hex(text) }
      default:
        return { ok: true, text }
    }
  } catch (e) {
    return { ok: false, error: e instanceof CodecError ? e.code : 'invalidUrl' }
  }
}

/** 逐字码位表:字符 / 码位 / 十进制 / UTF-8 字节 / HTML 实体,最多取 limit 个字符 */
export function describeChars(text, limit = 500) {
  const items = []
  let total = 0
  for (const ch of text) {
    total++
    if (items.length >= limit) continue
    const cp = ch.codePointAt(0)
    const entity = ESCAPED_ENTITIES[ch] ? ESCAPED_ENTITIES[ch] : cp < 0x20 || cp === 0x20 || cp > 0x7e ? `&#${cp};` : '—'
    items.push({
      char: visibleChar(cp),
      codePoint: `U+${hex(cp, cp > 0xffff ? 5 : 4)}`,
      dec: cp,
      utf8: toUtf8Hex(ch),
      entity,
    })
  }
  return { items, total, truncated: total > items.length }
}

// ---------------------------------------------------------------
// 乱码修复:把「按 A 编码的字节被当成 B 编码显示」的文本还原。
// 浏览器只能按编码解码、不能编码旧编码,因此先用 TextDecoder 反查单/双字节表建「字符 → 字节」映射,
// 再用目标编码重新解码;候选固定为 GBK / Big5 / Shift_JIS / Windows-1252,按可读性排序。
// ---------------------------------------------------------------

const ENCODING_LABELS = {
  gbk: 'GBK',
  gb18030: 'GB18030',
  big5: 'Big5',
  shift_jis: 'Shift_JIS',
  'windows-1252': 'Windows-1252',
  'utf-8': 'UTF-8',
}

const REPAIR_SOURCES = ['gbk', 'big5', 'shift_jis', 'windows-1252']
const REPAIR_TARGETS = ['utf-8', 'gb18030', 'big5', 'shift_jis', 'windows-1252']
const reverseMaps = new Map()

/** 反查编码表:字符 → 原始字节(只收录能解出单个字符的字节序列) */
export function buildReverseMap(encoding) {
  if (reverseMaps.has(encoding)) return reverseMaps.get(encoding)
  const map = new Map()
  let decoder
  try {
    decoder = new TextDecoder(encoding)
  } catch {
    reverseMaps.set(encoding, map)
    return map
  }
  const record = (bytes) => {
    const char = decoder.decode(Uint8Array.from(bytes))
    if (char.length === 1 && char !== '\ufffd' && !map.has(char)) map.set(char, bytes)
  }
  for (let byte = 0x20; byte <= 0xff; byte += 1) record([byte])
  for (let lead = 0x81; lead <= 0xfe; lead += 1) {
    for (let trail = 0x40; trail <= 0xfe; trail += 1) {
      if (trail >= 0x7f && trail <= 0x80) continue
      record([lead, trail])
    }
  }
  reverseMaps.set(encoding, map)
  return map
}

function encodeWith(text, encoding) {
  if (encoding === 'windows-1252') {
    const bytes = []
    for (const ch of text) {
      const cp = ch.codePointAt(0)
      if (cp > 0xff) return null
      bytes.push(cp)
    }
    return Uint8Array.from(bytes)
  }
  const map = buildReverseMap(encoding)
  const bytes = []
  for (const ch of text) {
    const pair = map.get(ch)
    if (!pair) return null
    bytes.push(...pair)
  }
  return Uint8Array.from(bytes)
}

function readability(text) {
  let bad = 0
  let printable = 0
  let cjk = 0
  for (const ch of text) {
    const cp = ch.codePointAt(0)
    if (cp === 0xfffd) {
      bad += 1
      continue
    }
    if (cp >= 0x20 && cp !== 0x7f) printable += 1
    if (cp >= 0x3000 && cp <= 0x9fff) cjk += 1
  }
  const total = Math.max(1, Array.from(text).length)
  return (printable / total) * 100 + (cjk / total) * 30 - (bad / total) * 200
}

/** 尝试修复乱码;返回按可信度降序的候选 [{source, target, label, text, score}] */
export function repairMojibake(text, { sources = REPAIR_SOURCES } = {}) {
  const input = String(text ?? '')
  const results = []
  const seen = new Set()
  for (const source of sources) {
    const bytes = encodeWith(input, source)
    if (!bytes) continue
    for (const target of REPAIR_TARGETS) {
      if (target === source) continue
      let decoded
      try {
        decoded = new TextDecoder(target).decode(bytes)
      } catch {
        continue
      }
      if (decoded === input || decoded.includes('\ufffd') || seen.has(decoded)) continue
      seen.add(decoded)
      results.push({
        source,
        target,
        label: `${ENCODING_LABELS[source] || source} → ${ENCODING_LABELS[target] || target}`,
        text: decoded,
        score: Math.round(readability(decoded) * 10) / 10,
      })
    }
  }
  return results.sort((a, b) => b.score - a.score).slice(0, 6)
}
