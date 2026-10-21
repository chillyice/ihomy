// CIDR 合并 / 范围互转 / 包含判断 / 排除。核心逻辑与 bit_pouch src/plugins/ip-range/run.ts 一致；IPv6 使用 BigInt。
// 由 bit_pouch 插件 ip-range 转写，两仓各自维护测试，不跨仓引用。
var __defProp = Object.defineProperty
var __defNormalProp = (obj, key, value) =>
  key in obj ? __defProp(obj, key, { enumerable: true, configurable: true, writable: true, value }) : (obj[key] = value)
var __publicField = (obj, key, value) => __defNormalProp(obj, typeof key !== 'symbol' ? key + '' : key, value)
const MAX_LINES = 1e4
const ERROR_TEXT = {
  empty: { zh: '\u6CA1\u6709\u6709\u6548\u8F93\u5165', en: 'No valid input' },
  invalidAddress: { zh: '\u5730\u5740\u4E0D\u5408\u6CD5', en: 'Invalid address' },
  invalidPrefix: { zh: '\u524D\u7F00\u957F\u5EA6\u4E0D\u5408\u6CD5', en: 'Invalid prefix length' },
  invalidRange: { zh: '\u8D77\u6B62\u5730\u5740\u987A\u5E8F\u4E0D\u5408\u6CD5', en: 'Invalid address range' },
  mixedFamily: { zh: '\u4E0D\u80FD\u6DF7\u5408 IPv4 \u4E0E IPv6', en: 'IPv4 and IPv6 cannot be mixed' },
  tooMany: { zh: '\u8F93\u5165\u8D85\u8FC7 10,000 \u884C', en: 'More than 10,000 lines' },
}
class IpError extends Error {
  constructor(code, detail, line) {
    super(code)
    __publicField(this, 'code')
    __publicField(this, 'line')
    __publicField(this, 'detail')
    this.code = code
    this.detail = detail
    this.line = line
  }
}
function toInt(text, base, line) {
  const value = Number.parseInt(text, base)
  if (!Number.isFinite(value)) throw new IpError('invalidAddress', text, line)
  return value
}
function parseIpv4(text, line) {
  const parts = text.split('.')
  if (parts.length !== 4) throw new IpError('invalidAddress', text, line)
  let value = 0n
  for (const part of parts) {
    if (!/^\d{1,3}$/.test(part)) throw new IpError('invalidAddress', text, line)
    const octet = toInt(part, 10, line)
    if (octet > 255) throw new IpError('invalidAddress', text, line)
    value = (value << 8n) | BigInt(octet)
  }
  return { value, family: 'ipv4', bits: 32 }
}
function parseIpv6Groups(text, original, line) {
  let left = []
  let right = []
  if (text.includes('::')) {
    const halves = text.split('::')
    if (halves.length > 2) throw new IpError('invalidAddress', original, line)
    left = halves[0] ? halves[0].split(':') : []
    right = halves[1] ? halves[1].split(':') : []
    if (left.length + right.length >= 8) throw new IpError('invalidAddress', original, line)
  } else {
    left = text.split(':')
    if (left.length !== 8) throw new IpError('invalidAddress', original, line)
  }
  const zeros = Array.from({ length: 8 - left.length - right.length }, () => '0')
  const groups = [...left, ...zeros, ...right]
  if (groups.some((group) => !/^[0-9a-fA-F]{1,4}$/.test(group))) throw new IpError('invalidAddress', original, line)
  let value = 0n
  for (const group of groups) value = (value << 16n) | BigInt(toInt(group, 16, line))
  return value
}
function parseAddress(raw, line) {
  const text = raw.trim().replace(/%.*$/, '')
  if (!text) throw new IpError('invalidAddress', raw, line)
  if (text.includes('.')) {
    if (!text.includes(':')) return parseIpv4(text, line)
    const lastColon = text.lastIndexOf(':')
    const ipv4 = parseIpv4(text.slice(lastColon + 1), line)
    const high = (ipv4.value >> 16n) & 0xffffn
    const low = ipv4.value & 0xffffn
    const prefix = text.slice(0, lastColon + 1)
    const value = parseIpv6Groups(`${prefix}${high.toString(16)}:${low.toString(16)}`, raw, line)
    return { value, family: 'ipv6', bits: 128 }
  }
  if (text.includes(':')) {
    return { value: parseIpv6Groups(text, raw, line), family: 'ipv6', bits: 128 }
  }
  return parseIpv4(text, line)
}
function parseEntry(text, line) {
  const trimmed = text.trim()
  const slash = trimmed.indexOf('/')
  if (slash >= 0) {
    const address2 = parseAddress(trimmed.slice(0, slash), line)
    const prefixText = trimmed.slice(slash + 1).trim()
    if (!/^\d+$/.test(prefixText)) throw new IpError('invalidPrefix', prefixText, line)
    const prefix = Number(prefixText)
    if (prefix < 0 || prefix > address2.bits) throw new IpError('invalidPrefix', prefixText, line)
    const hostBits = BigInt(address2.bits - prefix)
    const hostMask = (1n << hostBits) - 1n
    const start = address2.value & ~hostMask
    return { start, end: start | hostMask, family: address2.family }
  }
  const dash = trimmed.indexOf('-')
  if (dash >= 0) {
    const start = parseAddress(trimmed.slice(0, dash), line)
    const end = parseAddress(trimmed.slice(dash + 1), line)
    if (start.family !== end.family) throw new IpError('mixedFamily', trimmed, line)
    if (start.value > end.value) throw new IpError('invalidRange', trimmed, line)
    return { start: start.value, end: end.value, family: start.family }
  }
  const address = parseAddress(trimmed, line)
  return { start: address.value, end: address.value, family: address.family }
}
function familyBits(family) {
  return family === 'ipv4' ? 32 : 128
}
function formatAddress(value, family) {
  if (family === 'ipv4') {
    const parts = []
    for (let shift = 24n; shift >= 0n; shift -= 8n) parts.push(Number((value >> shift) & 0xffn))
    return parts.join('.')
  }
  const groups = []
  for (let index = 0; index < 8; index += 1) groups.push(Number((value >> BigInt(112 - index * 16)) & 0xffffn))
  let bestStart = -1
  let bestLength = 0
  for (let index = 0; index < 8; index += 1) {
    if (groups[index] !== 0) continue
    let length = 0
    while (index + length < 8 && groups[index + length] === 0) length += 1
    if (length > bestLength) {
      bestStart = index
      bestLength = length
    }
  }
  const hex = (group) => group.toString(16)
  if (bestLength >= 2) {
    const left = groups.slice(0, bestStart).map(hex).join(':')
    const right = groups
      .slice(bestStart + bestLength)
      .map(hex)
      .join(':')
    return `${left}::${right}`
  }
  return groups.map(hex).join(':')
}
function spanToCidrs(span) {
  const bits = BigInt(familyBits(span.family))
  const cidrs = []
  let cursor = span.start
  while (cursor <= span.end) {
    let hostBits = 0n
    while (hostBits < bits) {
      const block = 1n << (hostBits + 1n)
      if (cursor % block !== 0n) break
      if (cursor + block - 1n > span.end) break
      hostBits += 1n
    }
    const prefix = Number(bits - hostBits)
    cidrs.push(`${formatAddress(cursor, span.family)}/${prefix}`)
    cursor += 1n << hostBits
  }
  return cidrs
}
function mergeSpans(spans) {
  if (!spans.length) return []
  const sorted = [...spans].sort((a, b) => (a.start < b.start ? -1 : a.start > b.start ? 1 : 0))
  const merged = [{ ...sorted[0] }]
  for (const span of sorted.slice(1)) {
    const last = merged[merged.length - 1]
    if (span.start <= last.end + 1n) {
      if (span.end > last.end) last.end = span.end
    } else {
      merged.push({ ...span })
    }
  }
  return merged
}
function subtractSpans(base, exclusions) {
  const clipped = exclusions
    .filter((span) => span.end >= base.start && span.start <= base.end)
    .map((span) => ({
      start: span.start < base.start ? base.start : span.start,
      end: span.end > base.end ? base.end : span.end,
      family: base.family,
    }))
  const merged = mergeSpans(clipped)
  const result = []
  let cursor = base.start
  for (const span of merged) {
    if (span.end < cursor) continue
    if (span.start > cursor) result.push({ start: cursor, end: span.start - 1n, family: base.family })
    cursor = span.end + 1n
    if (cursor > base.end) break
  }
  if (cursor <= base.end) result.push({ start: cursor, end: base.end, family: base.family })
  return result
}
function meaningfulLines(text) {
  return text
    .split(/\r?\n/)
    .map((raw, index) => ({ text: raw.trim(), line: index + 1 }))
    .filter((entry) => entry.text && !entry.text.startsWith('#'))
}
function ensureSingleFamily(entries) {
  let family = null
  for (const entry of entries) {
    if (family && entry.span.family !== family) throw new IpError('mixedFamily', entry.text, entry.line)
    family = entry.span.family
  }
  return family ?? 'ipv4'
}
function failure(error, locale) {
  const base = ERROR_TEXT[error.code][locale]
  return {
    ok: false,
    error: {
      code: error.code,
      message: error.detail ? `${base}: ${error.detail}` : base,
      line: error.line,
      column: error.line ? 1 : void 0,
    },
  }
}
const SAMPLE_MERGE = `10.0.0.0/25
10.0.0.128/25
192.168.1.5
192.168.1.6
# IPv6 \u5355\u72EC\u8BA1\u7B97\uFF08\u4E0D\u80FD\u4E0E IPv4 \u6DF7\u5408\uFF09
# 2001:db8::/64
# 2001:db8:0:1::/64`
const SAMPLE_RANGE = `192.168.1.0/24
10.0.0.1-10.0.0.6`
const SAMPLE_EXCLUDE = `10.0.0.0/24
10.0.0.64/26
10.0.0.128/26`
const SAMPLE_CONTAINS = `10.0.0.0/24
10.0.0.1
10.0.0.255
10.0.1.1`
const run = (input, options, ctx) => {
  const text = String(input ?? '')
  const lines = meaningfulLines(text)
  if (!lines.length) return failure(new IpError('empty'), ctx.locale)
  if (lines.length > MAX_LINES) return failure(new IpError('tooMany'), ctx.locale)
  const mode = options.mode === 'range' || options.mode === 'contains' || options.mode === 'exclude' ? options.mode : 'merge'
  try {
    const entries = lines.map((entry) => ({
      text: entry.text,
      line: entry.line,
      span: parseEntry(entry.text, entry.line),
    }))
    const family = ensureSingleFamily(entries)
    const warnings = []
    if (mode === 'merge') {
      const merged = mergeSpans(entries.map((entry) => entry.span))
      const rows2 = merged.map((span) => ({
        input: `${formatAddress(span.start, family)} - ${formatAddress(span.end, family)}`,
        start: formatAddress(span.start, family),
        end: formatAddress(span.end, family),
        cidrs: spanToCidrs(span),
      }))
      const output2 = rows2.flatMap((row) => row.cidrs ?? []).join('\n')
      return {
        ok: true,
        output: output2,
        data: { mode, family, rows: rows2, warnings },
        stats: { family, cidrs: rows2.reduce((sum, row) => sum + (row.cidrs?.length ?? 0), 0), ranges: rows2.length },
      }
    }
    if (mode === 'range') {
      const rows2 = entries.map((entry) => {
        if (entry.text.includes('/')) {
          return {
            input: entry.text,
            start: formatAddress(entry.span.start, family),
            end: formatAddress(entry.span.end, family),
          }
        }
        return { input: entry.text, cidrs: spanToCidrs(entry.span) }
      })
      const output2 = rows2.map((row) => (row.cidrs ? row.cidrs.join('\n') : `${row.start} - ${row.end}`)).join('\n')
      return {
        ok: true,
        output: output2,
        data: { mode, family, rows: rows2, warnings },
        stats: { family, rows: rows2.length },
      }
    }
    const baseSpan = entries[0].span
    const base = {
      start: formatAddress(baseSpan.start, family),
      end: formatAddress(baseSpan.end, family),
      cidrs: spanToCidrs(baseSpan),
    }
    if (mode === 'contains') {
      const rows2 = entries.slice(1).map((entry) => ({
        input: entry.text,
        contained: entry.span.start >= baseSpan.start && entry.span.end <= baseSpan.end,
      }))
      const output2 = rows2.map((row) => `${row.input}: ${row.contained ? 'true' : 'false'}`).join('\n')
      return {
        ok: true,
        output: output2,
        data: { mode, family, base, rows: rows2, warnings },
        stats: { family, contained: rows2.filter((row) => row.contained).length, tested: rows2.length },
      }
    }
    const remaining = subtractSpans(
      baseSpan,
      entries.slice(1).map((entry) => entry.span),
    )
    const rows = remaining.map((span) => ({
      input: `${formatAddress(span.start, family)} - ${formatAddress(span.end, family)}`,
      start: formatAddress(span.start, family),
      end: formatAddress(span.end, family),
      cidrs: spanToCidrs(span),
    }))
    const output = rows.flatMap((row) => row.cidrs ?? []).join('\n')
    return {
      ok: true,
      output,
      data: { mode, family, base, rows, warnings },
      stats: { family, cidrs: rows.reduce((sum, row) => sum + (row.cidrs?.length ?? 0), 0) },
    }
  } catch (error) {
    if (error instanceof IpError) return failure(error, ctx.locale)
    const message = error instanceof Error ? error.message : String(error)
    return { ok: false, error: { code: 'PLUGIN_ERROR', message } }
  }
}
export { SAMPLE_CONTAINS, SAMPLE_EXCLUDE, SAMPLE_MERGE, SAMPLE_RANGE, run }
