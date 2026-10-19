// @ts-nocheck
// 由 ihomy scripts/export-tools.mjs 从 frontend/src/utils/tools/hashTool.js 导出。
// 哈希与校验的纯逻辑:MD5 自带实现(Web Crypto 不提供),SHA 系走 Web Crypto,CRC32 查表。
// 与 bit_pouch 插件契约同构:run(input, options, ctx) 返回 { ok, output, data, stats } 或 { ok:false, error }。
// MD5 只用于校验与兼容旧系统,不应用于安全场景。

import { bytesToBase64, bytesToHex } from './bytes'

export { bytesToBase64, bytesToHex }

export const HASH_ALGORITHMS = ['md5', 'sha1', 'sha256', 'sha384', 'sha512', 'crc32']
export const DEFAULT_ALGORITHMS = ['md5', 'sha256']
export const MAX_TEXT_LENGTH = 1_000_000
export const MAX_FILE_BYTES = 64 * 1024 * 1024

const SAFE_ALGORITHMS = ['sha1', 'sha256', 'sha384', 'sha512']
const WEB_CRYPTO_NAMES = {
  sha1: 'SHA-1',
  sha256: 'SHA-256',
  sha384: 'SHA-384',
  sha512: 'SHA-512',
}

const MD5_SHIFTS = [
  7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 4, 11, 16, 23, 4, 11,
  16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21,
]

const MD5_K = Array.from({ length: 64 }, (_, i) => Math.floor(Math.abs(Math.sin(i + 1)) * 0x1_0000_0000) >>> 0)

/** RFC 1321 MD5;输入输出均为字节,避免把二进制先转成字符串。 */
export function md5(input) {
  const bytes = input instanceof Uint8Array ? input : new Uint8Array(input)
  const bitLength = bytes.length * 8
  const paddedLength = (((bytes.length + 8) >> 6) + 1) << 6
  const buffer = new Uint8Array(paddedLength)
  buffer.set(bytes)
  buffer[bytes.length] = 0x80

  const view = new DataView(buffer.buffer)
  view.setUint32(paddedLength - 8, bitLength >>> 0, true)
  view.setUint32(paddedLength - 4, Math.floor(bitLength / 0x1_0000_0000), true)

  let a0 = 0x67452301
  let b0 = 0xefcdab89
  let c0 = 0x98badcfe
  let d0 = 0x10325476
  const words = new Uint32Array(16)

  for (let offset = 0; offset < paddedLength; offset += 64) {
    for (let i = 0; i < 16; i += 1) words[i] = view.getUint32(offset + i * 4, true)

    let a = a0
    let b = b0
    let c = c0
    let d = d0

    for (let i = 0; i < 64; i += 1) {
      let f
      let g
      if (i < 16) {
        f = (b & c) | (~b & d)
        g = i
      } else if (i < 32) {
        f = (d & b) | (~d & c)
        g = (5 * i + 1) % 16
      } else if (i < 48) {
        f = b ^ c ^ d
        g = (3 * i + 5) % 16
      } else {
        f = c ^ (b | ~d)
        g = (7 * i) % 16
      }

      const previousD = d
      d = c
      c = b
      const sum = (a + f + MD5_K[i] + words[g]) >>> 0
      const shift = MD5_SHIFTS[i]
      b = (b + ((sum << shift) | (sum >>> (32 - shift)))) >>> 0
      a = previousD
    }

    a0 = (a0 + a) >>> 0
    b0 = (b0 + b) >>> 0
    c0 = (c0 + c) >>> 0
    d0 = (d0 + d) >>> 0
  }

  const output = new Uint8Array(16)
  const outputView = new DataView(output.buffer)
  outputView.setUint32(0, a0, true)
  outputView.setUint32(4, b0, true)
  outputView.setUint32(8, c0, true)
  outputView.setUint32(12, d0, true)
  return output
}

const CRC32_TABLE = (() => {
  const table = new Uint32Array(256)
  for (let i = 0; i < 256; i += 1) {
    let value = i
    for (let bit = 0; bit < 8; bit += 1) value = value & 1 ? (value >>> 1) ^ 0xedb88320 : value >>> 1
    table[i] = value >>> 0
  }
  return table
})()

/** CRC-32 (IEEE 802.3),返回无符号 32 位整数。 */
export function crc32(input) {
  const bytes = input instanceof Uint8Array ? input : new Uint8Array(input)
  let crc = 0xffffffff
  for (const byte of bytes) crc = (crc >>> 8) ^ CRC32_TABLE[(crc ^ byte) & 0xff]
  return (crc ^ 0xffffffff) >>> 0
}

export function isHashAlgorithm(value) {
  return typeof value === 'string' && HASH_ALGORITHMS.includes(value)
}

function toBytes(input) {
  if (input instanceof Uint8Array) return input
  if (input instanceof ArrayBuffer) return new Uint8Array(input)
  return new TextEncoder().encode(String(input ?? ''))
}

/** 单个算法的摘要;HMAC 只支持 SHA 系(Web Crypto 限制)。 */
export async function digestBytes(input, algorithm, { hmacKey = '' } = {}) {
  if (!isHashAlgorithm(algorithm)) throw new Error('invalidAlgorithm')
  const bytes = toBytes(input)
  if (algorithm === 'md5') return md5(bytes)
  if (algorithm === 'crc32') {
    const value = crc32(bytes)
    return new Uint8Array([(value >>> 24) & 0xff, (value >>> 16) & 0xff, (value >>> 8) & 0xff, value & 0xff])
  }

  const subtle = globalThis.crypto?.subtle
  if (!subtle) throw new Error('cryptoUnavailable')
  const name = WEB_CRYPTO_NAMES[algorithm]
  if (hmacKey) {
    const key = await subtle.importKey('raw', new TextEncoder().encode(hmacKey), { name: 'HMAC', hash: name }, false, ['sign'])
    return new Uint8Array(await subtle.sign('HMAC', key, bytes))
  }
  return new Uint8Array(await subtle.digest(name, bytes))
}

const MESSAGES = {
  invalidAlgorithm: { zh: '不支持的摘要算法', en: 'Unsupported hash algorithm' },
  noAlgorithm: { zh: '请至少选择一种算法', en: 'Select at least one algorithm' },
  tooLong: { zh: '输入超过 {max} 字符,请改用文件模式', en: 'Input exceeds {max} characters; use file mode instead' },
  cryptoUnavailable: {
    zh: '当前环境不支持安全摘要计算,请换用现代浏览器或桌面端',
    en: 'Secure digest is unavailable here; use a modern browser or the desktop app',
  },
  fileTooLarge: { zh: '文件超过 {max},请换用命令行工具', en: 'File exceeds {max}; use a command-line tool instead' },
}

function fail(code, locale, extra = {}) {
  const template = MESSAGES[code] || MESSAGES.invalidAlgorithm
  let message = template[locale] || template.zh
  for (const [key, value] of Object.entries(extra)) message = message.replace(`{${key}}`, String(value))
  return { ok: false, error: { code, message } }
}

export function formatBytes(bytes) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

function normalizeAlgorithms(value, hmacKey) {
  const list = Array.isArray(value) ? value : DEFAULT_ALGORITHMS
  const unique = [...new Set(list.filter(isHashAlgorithm))]
  // HMAC 模式只保留 SHA 系:MD5/CRC32 没有 Web Crypto HMAC 实现
  return hmacKey ? unique.filter((algorithm) => SAFE_ALGORITHMS.includes(algorithm)) : unique
}

function buildResult(algorithm, digest, format) {
  const hex = bytesToHex(digest)
  const base64 = bytesToBase64(digest)
  return {
    algorithm,
    hex,
    base64,
    value: format === 'base64' ? base64 : hex,
    bits: digest.length * 8,
  }
}

function composeOutput(results) {
  if (results.length === 1) return results[0].value
  return results.map((item) => `${item.algorithm.toUpperCase()}: ${item.value}`).join('\n')
}

function compareExpected(results, expected) {
  const target = String(expected ?? '')
    .trim()
    .toLowerCase()
  if (!target) return null
  const matched = results.filter((item) => item.hex.toLowerCase() === target || item.base64.toLowerCase() === target)
  return { expected: target, match: matched.length > 0, algorithms: matched.map((item) => item.algorithm) }
}

/**
 * 文本摘要入口。
 * options: { algorithms: string[], format: 'hex'|'base64', hmacKey?: string, expected?: string }
 */
export async function run(input, options = {}, ctx = {}) {
  const locale = ctx.locale === 'en' ? 'en' : 'zh'
  const text = String(input ?? '')
  if (text.length > MAX_TEXT_LENGTH) return fail('tooLong', locale, { max: MAX_TEXT_LENGTH })

  const hmacKey = String(options.hmacKey ?? '')
  const algorithms = normalizeAlgorithms(options.algorithms, hmacKey)
  if (!algorithms.length) return fail('noAlgorithm', locale)

  const format = options.format === 'base64' ? 'base64' : 'hex'
  const bytes = new TextEncoder().encode(text)
  const results = []
  try {
    for (const algorithm of algorithms) {
      const digest = await digestBytes(bytes, algorithm, { hmacKey })
      results.push(buildResult(algorithm, digest, format))
    }
  } catch (error) {
    if (error?.message === 'cryptoUnavailable') return fail('cryptoUnavailable', locale)
    return fail('invalidAlgorithm', locale)
  }

  const expected = compareExpected(results, options.expected)
  return {
    ok: true,
    output: composeOutput(results),
    data: {
      results,
      byteLength: bytes.length,
      format,
      hmac: Boolean(hmacKey),
      expected,
    },
    stats: { algorithms: results.length, bytes: bytes.length },
  }
}

/**
 * 文件摘要入口(File/Blob)。文件不离开浏览器;超过上限直接拒绝,避免一次性占用过多内存。
 * options 同 run,但 algorithms 默认全部。
 */
export async function runFile(file, options = {}, ctx = {}) {
  const locale = ctx.locale === 'en' ? 'en' : 'zh'
  const size = Number(file?.size ?? 0)
  if (size > MAX_FILE_BYTES) return fail('fileTooLarge', locale, { max: formatBytes(MAX_FILE_BYTES) })

  const hmacKey = String(options.hmacKey ?? '')
  const algorithms = normalizeAlgorithms(options.algorithms, hmacKey)
  if (!algorithms.length) return fail('noAlgorithm', locale)

  const format = options.format === 'base64' ? 'base64' : 'hex'
  const buffer = await file.arrayBuffer()
  const bytes = new Uint8Array(buffer)
  const results = []
  try {
    for (const algorithm of algorithms) {
      const digest = await digestBytes(bytes, algorithm, { hmacKey })
      results.push(buildResult(algorithm, digest, format))
    }
  } catch (error) {
    if (error?.message === 'cryptoUnavailable') return fail('cryptoUnavailable', locale)
    return fail('invalidAlgorithm', locale)
  }

  const expected = compareExpected(results, options.expected)
  return {
    ok: true,
    output: composeOutput(results),
    data: {
      results,
      byteLength: bytes.length,
      format,
      hmac: Boolean(hmacKey),
      expected,
      fileName: file?.name ?? '',
    },
    stats: { algorithms: results.length, bytes: bytes.length, fileName: file?.name ?? '' },
  }
}
