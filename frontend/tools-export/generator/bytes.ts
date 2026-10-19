// @ts-nocheck
// 由 ihomy scripts/export-tools.mjs 从 frontend/src/utils/tools/bytes.js 导出。
// 字节与随机源的小工具:哈希与生成器共用,避免第二份 base64/hex 实现。
// 所有随机数都优先 Web Crypto;不可用时直接报错,不退回 Math.random(密码场景不能降级)。

const B64_ALPHABET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/'

export function bytesToHex(bytes) {
  let out = ''
  for (const byte of bytes) out += byte.toString(16).padStart(2, '0')
  return out
}

export function bytesToBase64(bytes) {
  let out = ''
  for (let i = 0; i < bytes.length; i += 3) {
    const b0 = bytes[i]
    const b1 = bytes[i + 1] ?? 0
    const b2 = bytes[i + 2] ?? 0
    const triplet = (b0 << 16) | (b1 << 8) | b2
    out += B64_ALPHABET[(triplet >> 18) & 63]
    out += B64_ALPHABET[(triplet >> 12) & 63]
    out += i + 1 < bytes.length ? B64_ALPHABET[(triplet >> 6) & 63] : '='
    out += i + 2 < bytes.length ? B64_ALPHABET[triplet & 63] : '='
  }
  return out
}

export function secureRandomBytes(length) {
  const bytes = new Uint8Array(length)
  if (!globalThis.crypto?.getRandomValues) throw new Error('cryptoUnavailable')
  globalThis.crypto.getRandomValues(bytes)
  return bytes
}

/** 无偏随机整数 [0, maxExclusive);用拒绝采样去掉取模偏差。 */
export function secureRandomInt(maxExclusive) {
  if (!Number.isInteger(maxExclusive) || maxExclusive <= 0) throw new Error('invalidRange')
  if (maxExclusive === 1) return 0
  const limit = Math.floor(0x100000000 / maxExclusive) * maxExclusive
  const buffer = new Uint32Array(1)
  let value
  do {
    if (!globalThis.crypto?.getRandomValues) throw new Error('cryptoUnavailable')
    globalThis.crypto.getRandomValues(buffer)
    value = buffer[0]
  } while (value >= limit)
  return value % maxExclusive
}
