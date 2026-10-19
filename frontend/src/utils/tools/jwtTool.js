// JWT 与密钥工具的纯逻辑:解码 / HMAC 验签 / RSA·ECDSA 验签 / 密钥对与 PEM·JWK 互转 / AES-GCM 加解密。
// 全部走 Web Crypto;令牌、密钥与明文只在内存里转一圈,不读写任何存储。
// 与 utils/jwt.js(登录态令牌过期预检)无关,刻意不共用,避免工具页影响鉴权逻辑。

import { bytesToBase64, secureRandomBytes } from './bytes'

export const HMAC_ALGS = ['HS256', 'HS384', 'HS512']
export const ASYMMETRIC_ALGS = ['RS256', 'RS384', 'RS512', 'ES256', 'ES384', 'ES512']
export const MAX_TOKEN_LENGTH = 64 * 1024
const PBKDF2_ITERATIONS = 100000

const MESSAGES = {
  cryptoUnavailable: { zh: '当前环境不支持该加密操作', en: 'This crypto operation is unavailable here' },
  invalidToken: { zh: '不是有效的 JWT(应为 header.payload.signature 三段)', en: 'Not a valid JWT (expected header.payload.signature)' },
  invalidJson: { zh: '令牌内容不是有效的 JSON', en: 'The token content is not valid JSON' },
  tokenTooLong: { zh: '令牌过长,请粘贴单个令牌', en: 'The token is too long; paste a single token' },
  invalidAlg: { zh: '不支持的算法', en: 'Unsupported algorithm' },
  invalidSecret: { zh: '请填写密钥', en: 'Enter the secret' },
  invalidKey: { zh: '密钥格式不正确,需要 PEM 或 JWK', en: 'Invalid key format; PEM or JWK required' },
  invalidPassword: { zh: '请填写口令', en: 'Enter a password' },
  badPayload: { zh: '密文格式不正确或口令错误', en: 'The ciphertext is malformed or the password is wrong' },
}

function fail(code, locale, extra = {}) {
  const template = MESSAGES[code] || MESSAGES.invalidToken
  let message = template[locale] || template.zh
  for (const [key, value] of Object.entries(extra)) message = message.replace(`{${key}}`, String(value))
  return { ok: false, error: { code, message } }
}

const subtle = () => globalThis.crypto?.subtle

export function bytesToBase64Url(bytes) {
  return bytesToBase64(bytes).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

export function base64UrlToBytes(text) {
  const padded = String(text || '')
    .replace(/-/g, '+')
    .replace(/_/g, '/')
  const pad = padded.length % 4 === 0 ? '' : '='.repeat(4 - (padded.length % 4))
  const binary = atob(padded + pad)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i += 1) bytes[i] = binary.charCodeAt(i)
  return bytes
}

const decodeJson = (part) => {
  try {
    return JSON.parse(new TextDecoder().decode(base64UrlToBytes(part)))
  } catch {
    throw new Error('invalidJson')
  }
}

/** 解析令牌结构;不做验签。 */
export function decodeToken(token) {
  const raw = String(token || '').trim()
  if (raw.length > MAX_TOKEN_LENGTH) throw new Error('tokenTooLong')
  const parts = raw.split('.')
  if (parts.length !== 3) throw new Error('invalidToken')
  return {
    header: decodeJson(parts[0]),
    payload: decodeJson(parts[1]),
    signature: base64UrlToBytes(parts[2]),
    signingInput: `${parts[0]}.${parts[1]}`,
  }
}

export const toIso = (seconds) => (Number.isFinite(seconds) ? new Date(seconds * 1000).toISOString() : '')

/** exp / nbf / iat 的可读化与状态(now 毫秒,便于单测)。 */
export function describeTimeClaims(payload, now = Date.now()) {
  const items = []
  const push = (key, label) => {
    const value = Number(payload?.[key])
    if (!Number.isFinite(value) || payload?.[key] === null) return
    let status = 'ok'
    if (key === 'exp') status = value * 1000 <= now ? 'expired' : 'active'
    if (key === 'nbf') status = value * 1000 > now ? 'pending' : 'active'
    items.push({ key, label, value, iso: toIso(value), status })
  }
  push('iat', 'Issued at')
  push('nbf', 'Not before')
  push('exp', 'Expires at')
  return items
}

const constantTimeEqual = (a, b) => {
  if (a.length !== b.length) return false
  let diff = 0
  for (let i = 0; i < a.length; i += 1) diff |= a[i] ^ b[i]
  return diff === 0
}

const hmacHash = (alg) => ({ HS256: 'SHA-256', HS384: 'SHA-384', HS512: 'SHA-512' })[alg]

/** HS256/384/512 验签。 */
export async function verifyHmac(token, secret, alg = 'HS256') {
  if (!hmacHash(alg)) throw new Error('invalidAlg')
  if (!secret) throw new Error('invalidSecret')
  if (!subtle()) throw new Error('cryptoUnavailable')
  const { signature, signingInput } = decodeToken(token)
  const key = await subtle().importKey('raw', new TextEncoder().encode(secret), { name: 'HMAC', hash: hmacHash(alg) }, false, ['sign'])
  const expected = new Uint8Array(await subtle().sign('HMAC', key, new TextEncoder().encode(signingInput)))
  return constantTimeEqual(expected, signature)
}

const parseJwk = (key) => {
  if (key && typeof key === 'object' && key.kty) return key
  const trimmed = String(key || '').trim()
  if (!trimmed.startsWith('{')) return null
  try {
    const jwk = JSON.parse(trimmed)
    return jwk && jwk.kty ? jwk : null
  } catch {
    return null
  }
}

const pemBody = (pem) => {
  const match = String(pem).match(/-----BEGIN ([^-]+)-----([\s\S]*?)-----END \1-----/)
  if (!match) return null
  return { label: match[1].trim(), base64: match[2].replace(/\s+/g, '') }
}

const pemToDer = (pem) => {
  const body = pemBody(pem)
  if (!body) return null
  const binary = atob(body.base64)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i += 1) bytes[i] = binary.charCodeAt(i)
  return { label: body.label, bytes }
}

const derToPem = (bytes, label) => {
  const base64 = bytesToBase64(bytes)
  const lines = base64.match(/.{1,64}/g) || []
  return `-----BEGIN ${label}-----\n${lines.join('\n')}\n-----END ${label}-----`
}

const ecCurve = (alg) => ({ ES256: 'P-256', ES384: 'P-384', ES512: 'P-521' })[alg]
const rsaHash = (alg) => ({ RS256: 'SHA-256', RS384: 'SHA-384', RS512: 'SHA-512' })[alg]

const importParams = (alg, jwk) => {
  if (rsaHash(alg)) {
    const hash = rsaHash(alg)
    return { algorithm: { name: 'RSASSA-PKCS1-v1_5', hash }, usage: ['verify'] }
  }
  if (ecCurve(alg)) {
    const namedCurve = jwk?.crv || ecCurve(alg)
    return { algorithm: { name: 'ECDSA', namedCurve }, usage: ['verify'] }
  }
  throw new Error('invalidAlg')
}

/** RS 与 ES 系列验签:key 可为 PEM(SPKI)或 JWK 文本。 */
export async function verifyWithKey(token, keyText, alg = 'RS256') {
  if (!subtle()) throw new Error('cryptoUnavailable')
  const { algorithm, usage } = importParams(alg)
  const jwk = parseJwk(keyText)
  let key
  if (jwk) {
    key = await subtle().importKey('jwk', jwk, algorithm, false, usage)
  } else {
    const parsed = pemToDer(keyText)
    if (!parsed) throw new Error('invalidKey')
    if (!/PUBLIC KEY/.test(parsed.label)) throw new Error('invalidKey')
    key = await subtle().importKey('spki', parsed.bytes, algorithm, false, usage)
  }
  const { signature, signingInput } = decodeToken(token)
  return subtle().verify(algorithm, key, signature, new TextEncoder().encode(signingInput))
}

/** 生成密钥对,返回 PEM(PKCS8/SPKI)、JWK 与公钥指纹(SHA-256)。 */
export async function generateKeyPair(alg = 'RS256') {
  if (!subtle()) throw new Error('cryptoUnavailable')
  let pair
  let publicType = 'spki'
  let privateType = 'pkcs8'
  if (rsaHash(alg)) {
    const hash = rsaHash(alg)
    pair = await subtle().generateKey(
      { name: 'RSASSA-PKCS1-v1_5', modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash },
      true,
      ['sign', 'verify'],
    )
  } else if (ecCurve(alg)) {
    pair = await subtle().generateKey({ name: 'ECDSA', namedCurve: ecCurve(alg) }, true, ['sign', 'verify'])
  } else {
    throw new Error('invalidAlg')
  }
  const spki = new Uint8Array(await subtle().exportKey(publicType, pair.publicKey))
  const pkcs8 = new Uint8Array(await subtle().exportKey(privateType, pair.privateKey))
  const digest = new Uint8Array(await subtle().digest('SHA-256', spki))
  const fingerprint = Array.from(digest, (byte) => byte.toString(16).padStart(2, '0').toUpperCase()).join(':')
  return {
    alg,
    publicPem: derToPem(spki, 'PUBLIC KEY'),
    privatePem: derToPem(pkcs8, 'PRIVATE KEY'),
    publicJwk: await subtle().exportKey('jwk', pair.publicKey),
    privateJwk: await subtle().exportKey('jwk', pair.privateKey),
    fingerprint,
    publicDer: spki,
    privateDer: pkcs8,
  }
}

/** PEM → JWK;kty 由算法决定(RS* 为 RSA,ES* 为 EC)。 */
export async function pemToJwk(pem, alg = 'RS256') {
  if (!subtle()) throw new Error('cryptoUnavailable')
  const { algorithm } = importParams(alg)
  const parsed = pemToDer(pem)
  if (!parsed) throw new Error('invalidKey')
  const isPrivate = /PRIVATE KEY/.test(parsed.label)
  const format = isPrivate ? 'pkcs8' : 'spki'
  const usage = isPrivate ? ['sign'] : ['verify']
  try {
    const key = await subtle().importKey(format, parsed.bytes, algorithm, true, usage)
    return await subtle().exportKey('jwk', key)
  } catch {
    throw new Error('invalidKey')
  }
}

/** JWK → PEM;kind 决定导出公钥(spki)还是私钥(pkcs8)。 */
export async function jwkToPem(jwk, kind = 'public') {
  if (!subtle()) throw new Error('cryptoUnavailable')
  const value = typeof jwk === 'string' ? parseJwk(jwk) : jwk
  if (!value) throw new Error('invalidKey')
  const alg = value.kty === 'RSA' ? 'RS256' : value.kty === 'EC' ? 'ES256' : ''
  if (!alg) throw new Error('invalidKey')
  const { algorithm } = importParams(alg, value)
  try {
    const key = await subtle().importKey('jwk', value, algorithm, true, [kind === 'private' ? 'sign' : 'verify'])
    const der = new Uint8Array(await subtle().exportKey(kind === 'private' ? 'pkcs8' : 'spki', key))
    return derToPem(der, kind === 'private' ? 'PRIVATE KEY' : 'PUBLIC KEY')
  } catch {
    throw new Error('invalidKey')
  }
}

/** AES-GCM + PBKDF2(SHA-256,100k):输出 base64(salt|iv|cipher)。 */
export async function encryptText(text, password) {
  if (!subtle()) throw new Error('cryptoUnavailable')
  if (!password) throw new Error('invalidPassword')
  const salt = secureRandomBytes(16)
  const iv = secureRandomBytes(12)
  const base = await subtle().importKey('raw', new TextEncoder().encode(password), 'PBKDF2', false, ['deriveKey'])
  const key = await subtle().deriveKey(
    { name: 'PBKDF2', salt, iterations: PBKDF2_ITERATIONS, hash: 'SHA-256' },
    base,
    { name: 'AES-GCM', length: 256 },
    false,
    ['encrypt'],
  )
  const cipher = new Uint8Array(await subtle().encrypt({ name: 'AES-GCM', iv }, key, new TextEncoder().encode(text)))
  const combined = new Uint8Array(salt.length + iv.length + cipher.length)
  combined.set(salt, 0)
  combined.set(iv, salt.length)
  combined.set(cipher, salt.length + iv.length)
  return bytesToBase64(combined)
}

export async function decryptText(payload, password) {
  if (!subtle()) throw new Error('cryptoUnavailable')
  if (!password) throw new Error('invalidPassword')
  let combined
  try {
    combined = base64ToBytes(payload)
  } catch {
    throw new Error('badPayload')
  }
  if (combined.length <= 28) throw new Error('badPayload')
  const salt = combined.slice(0, 16)
  const iv = combined.slice(16, 28)
  const cipher = combined.slice(28)
  const base = await subtle().importKey('raw', new TextEncoder().encode(password), 'PBKDF2', false, ['deriveKey'])
  const key = await subtle().deriveKey(
    { name: 'PBKDF2', salt, iterations: PBKDF2_ITERATIONS, hash: 'SHA-256' },
    base,
    { name: 'AES-GCM', length: 256 },
    false,
    ['decrypt'],
  )
  try {
    const plain = await subtle().decrypt({ name: 'AES-GCM', iv }, key, cipher)
    return new TextDecoder().decode(plain)
  } catch {
    throw new Error('badPayload')
  }
}

function base64ToBytes(text) {
  const binary = atob(String(text).replace(/\s+/g, ''))
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i += 1) bytes[i] = binary.charCodeAt(i)
  return bytes
}

/** 统一入口(kernel 契约):解码令牌并给出声明摘要。 */
export function run(input, _options = {}, ctx = {}) {
  const locale = ctx.locale === 'en' ? 'en' : 'zh'
  try {
    const decoded = decodeToken(input)
    const times = describeTimeClaims(decoded.payload)
    const lines = Object.entries(decoded.payload)
      .map(([key, value]) => `${key}: ${typeof value === 'object' ? JSON.stringify(value) : value}`)
      .join('\n')
    return {
      ok: true,
      output: lines,
      data: { header: decoded.header, payload: decoded.payload, times },
      stats: { claims: Object.keys(decoded.payload).length },
    }
  } catch (error) {
    return fail(error?.message || 'invalidToken', locale)
  }
}
