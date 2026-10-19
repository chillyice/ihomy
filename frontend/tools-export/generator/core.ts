// @ts-nocheck
// 由 ihomy scripts/export-tools.mjs 从 frontend/src/utils/tools/generatorTool.js 导出。
// 随机生成器的纯逻辑:密码 / PIN / UUID / ULID / NanoID / 随机字节 / 随机整数 / 抽签洗牌分组。
// 与 bit_pouch 插件契约同构:run(input, options, ctx) 返回 { ok, output, data, stats } 或 { ok:false, error }。
// 所有随机都走 Web Crypto;缺失时直接报错,不退回 Math.random。

import { bytesToBase64, bytesToHex, secureRandomBytes, secureRandomInt } from './bytes'

export const MODES = ['password', 'pin', 'uuid', 'ulid', 'nano', 'bytes', 'number', 'pick', 'shuffle', 'group']
export const MAX_COUNT = 200
export const MAX_BYTES = 1024
export const MAX_LENGTH = 128

export const CHARSETS = {
  lower: 'abcdefghijklmnopqrstuvwxyz',
  upper: 'ABCDEFGHIJKLMNOPQRSTUVWXYZ',
  digits: '0123456789',
  symbols: '!@#$%^&*-_=+?.,:;()[]{}<>/|~',
}
const AMBIGUOUS = '0Oo1lI|`'
const DEFAULT_NANO_ALPHABET = '_-0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ'
const CROCKFORD = '0123456789ABCDEFGHJKMNPQRSTVWXYZ'

const MESSAGES = {
  invalidMode: { zh: '不支持这种生成类型', en: 'Unsupported generator type' },
  invalidCount: { zh: '数量必须是 1–{max} 的整数', en: 'Count must be an integer between 1 and {max}' },
  invalidLength: { zh: '长度必须是 {min}–{max} 的整数', en: 'Length must be an integer between {min} and {max}' },
  noCharset: { zh: '至少选择一种字符类型', en: 'Select at least one character type' },
  lengthTooShort: { zh: '长度不足以包含每种已选字符', en: 'Length is too short for every selected character type' },
  cryptoUnavailable: { zh: '当前环境无法提供安全随机数', en: 'Secure randomness is unavailable here' },
  invalidRange: { zh: '最小值不能大于最大值', en: 'Minimum cannot be greater than maximum' },
  rangeTooSmall: { zh: '区间内的整数不够生成不重复结果', en: 'Not enough distinct integers in the range' },
  notEnoughItems: { zh: '可用条目少于要抽取的数量', en: 'Not enough items to pick from' },
  invalidAlphabet: { zh: '字符表需要 2–64 个不重复字符', en: 'Alphabet needs 2–64 unique characters' },
  invalidGroupCount: { zh: '分组数必须在 2 到条目数之间', en: 'Group count must be between 2 and the item count' },
}

function fail(code, locale, extra = {}) {
  const template = MESSAGES[code] || MESSAGES.invalidMode
  let message = template[locale] || template.zh
  for (const [key, value] of Object.entries(extra)) message = message.replace(`{${key}}`, String(value))
  return { ok: false, error: { code, message } }
}

const intInRange = (value, min, max, fallback) => {
  const number = Number(value)
  if (!Number.isInteger(number)) return fallback
  if (number < min || number > max) return null
  return number
}

export function entropyBits(length, poolSize) {
  if (length <= 0 || poolSize <= 1) return 0
  return Math.round(length * Math.log2(poolSize) * 10) / 10
}

export function strengthOf(bits) {
  if (bits >= 80) return 'veryStrong'
  if (bits >= 60) return 'strong'
  if (bits >= 40) return 'fair'
  return 'weak'
}

export function buildPool({ lower = true, upper = true, digits = true, symbols = true, excludeAmbiguous = true } = {}) {
  const enabled = []
  if (lower) enabled.push(CHARSETS.lower)
  if (upper) enabled.push(CHARSETS.upper)
  if (digits) enabled.push(CHARSETS.digits)
  if (symbols) enabled.push(CHARSETS.symbols)
  return enabled.map((charset) => (excludeAmbiguous ? [...charset].filter((char) => !AMBIGUOUS.includes(char)).join('') : charset))
}

function randomChar(charset) {
  return charset[secureRandomInt(charset.length)]
}

export function shuffle(items) {
  const copy = [...items]
  for (let i = copy.length - 1; i > 0; i -= 1) {
    const j = secureRandomInt(i + 1)
    ;[copy[i], copy[j]] = [copy[j], copy[i]]
  }
  return copy
}

export function makePassword(options = {}) {
  const length = intInRange(options.length, 4, MAX_LENGTH, 20)
  const classes = buildPool(options)
  if (!classes.length) throw new Error('noCharset')
  if (length === null) throw new Error('invalidLength')
  if (options.requireEach !== false && classes.length > length) throw new Error('lengthTooShort')

  const pool = classes.join('')
  const chars = []
  if (options.requireEach !== false) for (const charset of classes) chars.push(randomChar(charset))
  while (chars.length < length) chars.push(randomChar(pool))
  return { value: shuffle(chars).join(''), poolSize: pool.length, length }
}

export function makePin(options = {}) {
  const length = intInRange(options.length, 3, 12, 6)
  if (length === null) throw new Error('invalidLength')
  return String.fromCharCode(...Array.from({ length }, () => 48 + secureRandomInt(10)))
}

function formatUuid(bytes, { uppercase = false, hyphens = true } = {}) {
  const hex = bytesToHex(bytes)
  const value = hyphens ? `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}` : hex
  return uppercase ? value.toUpperCase() : value.toLowerCase()
}

export function uuidV4(options = {}) {
  const bytes = secureRandomBytes(16)
  bytes[6] = (bytes[6] & 0x0f) | 0x40
  bytes[8] = (bytes[8] & 0x3f) | 0x80
  return formatUuid(bytes, options)
}

export function uuidV7(now = Date.now(), options = {}) {
  const bytes = secureRandomBytes(16)
  const timestamp = BigInt(Math.floor(now))
  for (let i = 0; i < 6; i += 1) {
    bytes[i] = Number((timestamp >> BigInt(8 * (5 - i))) & 0xffn)
  }
  bytes[6] = (bytes[6] & 0x0f) | 0x70
  bytes[8] = (bytes[8] & 0x3f) | 0x80
  return formatUuid(bytes, options)
}

function encodeCrockford(value, length) {
  let rest = BigInt(value)
  let out = ''
  for (let i = 0; i < length; i += 1) {
    out = CROCKFORD[Number(rest & 31n)] + out
    rest >>= 5n
  }
  return out
}

export function ulid(now = Date.now(), { lowercase = false } = {}) {
  const time = encodeCrockford(now, 10)
  let random = ''
  for (let i = 0; i < 16; i += 1) random += CROCKFORD[secureRandomInt(32)]
  const value = time + random
  return lowercase ? value.toLowerCase() : value
}

export function nanoid(length = 21, alphabet = DEFAULT_NANO_ALPHABET) {
  const chars = [...new Set([...String(alphabet)])]
  if (chars.length < 2 || chars.length > 64) throw new Error('invalidAlphabet')
  let out = ''
  for (let i = 0; i < length; i += 1) out += chars[secureRandomInt(chars.length)]
  return out
}

function parseLines(input) {
  return String(input ?? '')
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
}

function randomNumbers(options) {
  const min = Number(options.min ?? 1)
  const max = Number(options.max ?? 100)
  const count = intInRange(options.count, 1, MAX_COUNT, 1)
  if (!Number.isSafeInteger(min) || !Number.isSafeInteger(max) || min > max) throw new Error('invalidRange')
  if (count === null) throw new Error('invalidCount')

  const span = max - min + 1
  if (options.unique) {
    if (span < count) throw new Error('rangeTooSmall')
    const seen = new Set()
    while (seen.size < count) seen.add(min + secureRandomInt(span))
    return [...seen].map(String)
  }
  return Array.from({ length: count }, () => String(min + secureRandomInt(span)))
}

function groupItems(items, groupCount) {
  const next = shuffle(items)
  const groups = Array.from({ length: groupCount }, () => [])
  next.forEach((item, index) => groups[index % groupCount].push(item))
  return groups
}

/**
 * 统一入口。
 * options.mode: password | pin | uuid | ulid | nano | bytes | number | pick | shuffle | group
 * input 仅在 pick / shuffle / group 模式下使用(每行一个条目)。
 */
export function run(input, options = {}, ctx = {}) {
  const locale = ctx.locale === 'en' ? 'en' : 'zh'
  const mode = options.mode || 'password'
  if (!MODES.includes(mode)) return fail('invalidMode', locale)

  const count = intInRange(options.count, 1, MAX_COUNT, 1)
  if (count === null) return fail('invalidCount', locale, { max: MAX_COUNT })

  try {
    if (mode === 'password') {
      const first = makePassword(options)
      const items = [first.value]
      for (let i = 1; i < count; i += 1) items.push(makePassword(options).value)
      const bits = entropyBits(first.length, first.poolSize)
      return {
        ok: true,
        output: items.join('\n'),
        data: { mode, items, length: first.length, poolSize: first.poolSize, entropy: bits, strength: strengthOf(bits) },
        stats: { count, length: first.length, entropy: bits },
      }
    }

    if (mode === 'pin') {
      const items = Array.from({ length: count }, () => makePin(options))
      return { ok: true, output: items.join('\n'), data: { mode, items }, stats: { count, length: items[0].length } }
    }

    if (mode === 'uuid') {
      const version = options.version === 'v7' ? 'v7' : 'v4'
      const items = Array.from({ length: count }, () => (version === 'v7' ? uuidV7(Date.now(), options) : uuidV4(options)))
      return { ok: true, output: items.join('\n'), data: { mode, items, version }, stats: { count, version } }
    }

    if (mode === 'ulid') {
      const items = Array.from({ length: count }, () => ulid(Date.now(), options))
      return { ok: true, output: items.join('\n'), data: { mode, items }, stats: { count } }
    }

    if (mode === 'nano') {
      const length = intInRange(options.length, 4, MAX_LENGTH, 21)
      if (length === null) return fail('invalidLength', locale, { min: 4, max: MAX_LENGTH })
      const items = Array.from({ length: count }, () => nanoid(length, options.alphabet || DEFAULT_NANO_ALPHABET))
      return { ok: true, output: items.join('\n'), data: { mode, items }, stats: { count, length } }
    }

    if (mode === 'bytes') {
      const length = intInRange(options.length, 1, MAX_BYTES, 16)
      if (length === null) return fail('invalidLength', locale, { min: 1, max: MAX_BYTES })
      const format = options.format === 'base64' ? 'base64' : 'hex'
      const items = Array.from({ length: count }, () => {
        const bytes = secureRandomBytes(length)
        return format === 'base64' ? bytesToBase64(bytes) : bytesToHex(bytes)
      })
      return { ok: true, output: items.join('\n'), data: { mode, items, format }, stats: { count, length } }
    }

    if (mode === 'number') {
      const items = randomNumbers({ ...options, count })
      return { ok: true, output: items.join('\n'), data: { mode, items, unique: Boolean(options.unique) }, stats: { count } }
    }

    const lines = parseLines(input)
    if (mode === 'pick') {
      if (lines.length < count) return fail('notEnoughItems', locale)
      const items = shuffle(lines).slice(0, count)
      return { ok: true, output: items.join('\n'), data: { mode, items }, stats: { count, pool: lines.length } }
    }

    if (mode === 'shuffle') {
      const items = shuffle(lines)
      return { ok: true, output: items.join('\n'), data: { mode, items }, stats: { count: items.length } }
    }

    const groupCount = intInRange(options.groupCount, 2, Math.max(2, lines.length), 2)
    if (!lines.length || groupCount === null || groupCount > lines.length) return fail('invalidGroupCount', locale)
    const groups = groupItems(lines, groupCount)
    const items = groups.map((group, index) => `${index + 1}. ${group.join(' / ')}`)
    return { ok: true, output: items.join('\n'), data: { mode, items, groups }, stats: { groups: groupCount, count: lines.length } }
  } catch (error) {
    const code = error?.message
    if (code === 'invalidCount') return fail(code, locale, { max: MAX_COUNT })
    if (code === 'invalidLength' || code === 'lengthTooShort') return fail(code, locale, { min: 4, max: MAX_LENGTH })
    if (MESSAGES[code]) return fail(code, locale)
    if (code === 'cryptoUnavailable') return fail('cryptoUnavailable', locale)
    return fail('invalidMode', locale)
  }
}
