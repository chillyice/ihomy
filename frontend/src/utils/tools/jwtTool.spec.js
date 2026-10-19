import { describe, expect, it } from 'vitest'
import {
  base64UrlToBytes,
  bytesToBase64Url,
  decodeToken,
  decryptText,
  describeTimeClaims,
  encryptText,
  generateKeyPair,
  jwkToPem,
  pemToJwk,
  run,
  verifyHmac,
  verifyWithKey,
} from './jwtTool'

const encoder = new TextEncoder()
const b64url = (value) => bytesToBase64Url(encoder.encode(JSON.stringify(value)))

const makeToken = async (header, payload, secret, alg = 'HS256') => {
  const signingInput = `${b64url(header)}.${b64url(payload)}`
  const hash = { HS256: 'SHA-256', HS384: 'SHA-384', HS512: 'SHA-512' }[alg]
  const key = await crypto.subtle.importKey('raw', encoder.encode(secret), { name: 'HMAC', hash }, false, ['sign'])
  const signature = new Uint8Array(await crypto.subtle.sign('HMAC', key, encoder.encode(signingInput)))
  return `${signingInput}.${bytesToBase64Url(signature)}`
}

describe('base64url', () => {
  it('往返不丢字节', () => {
    const bytes = Uint8Array.from([0, 1, 250, 255, 128])
    expect(Array.from(base64UrlToBytes(bytesToBase64Url(bytes)))).toEqual([0, 1, 250, 255, 128])
  })
})

describe('解码', () => {
  it('读出 header 与 payload', () => {
    const token = `${b64url({ alg: 'HS256', typ: 'JWT' })}.${b64url({ sub: 'u1', name: '家' })}.sig`
    const decoded = decodeToken(token)
    expect(decoded.header.alg).toBe('HS256')
    expect(decoded.payload.name).toBe('家')
    expect(decoded.signingInput.split('.')).toHaveLength(2)
  })

  it('段数不对或内容非 JSON 报错', () => {
    expect(() => decodeToken('a.b')).toThrow('invalidToken')
    expect(() => decodeToken('a.b.c')).toThrow()
  })

  it('run 输出声明摘要', () => {
    const token = `${b64url({ alg: 'none' })}.${b64url({ sub: 'u1', exp: 1700000000 })}.eA`
    const result = run(token, {}, { locale: 'en' })
    expect(result.ok).toBe(true)
    expect(result.output).toContain('sub: u1')
    expect(result.stats.claims).toBe(2)
  })
})

describe('时间声明', () => {
  const now = 1_700_000_000_000

  it('过期 / 生效 / 尚未生效', () => {
    const items = describeTimeClaims({ iat: 1699999000, nbf: 1700000100, exp: 1699999999 }, now)
    expect(items.find((item) => item.key === 'exp').status).toBe('expired')
    expect(items.find((item) => item.key === 'nbf').status).toBe('pending')
    expect(items.find((item) => item.key === 'iat').status).toBe('ok')
    expect(items[0].iso).toMatch(/^2023-/)
  })

  it('没有时间声明时返回空列表', () => {
    expect(describeTimeClaims({}, now)).toEqual([])
  })
})

describe('HMAC 验签', () => {
  it('正确密钥通过、错误密钥失败', async () => {
    const token = await makeToken({ alg: 'HS256', typ: 'JWT' }, { sub: 'u1' }, 'secret')
    expect(await verifyHmac(token, 'secret')).toBe(true)
    expect(await verifyHmac(token, 'wrong')).toBe(false)
    expect(await verifyHmac(token, 'secret', 'HS512')).toBe(false)
  })

  it('空密钥与不支持算法报错', async () => {
    const token = await makeToken({ alg: 'HS256' }, { sub: 'u1' }, 'secret')
    await expect(verifyHmac(token, '')).rejects.toThrow('invalidSecret')
    await expect(verifyHmac(token, 'secret', 'none')).rejects.toThrow('invalidAlg')
  })
})

describe('非对称验签与密钥', () => {
  it('RS256 密钥对:签名令牌后可用公钥 PEM 验签', async () => {
    const pair = await generateKeyPair('RS256')
    expect(pair.publicPem).toContain('BEGIN PUBLIC KEY')
    expect(pair.privatePem).toContain('BEGIN PRIVATE KEY')
    expect(pair.fingerprint.split(':')).toHaveLength(32)

    const signingInput = `${b64url({ alg: 'RS256', typ: 'JWT' })}.${b64url({ sub: 'u1' })}`
    const der = Uint8Array.from(atob(pair.privatePem.replace(/-----[^-]+-----|\s/g, '')), (char) => char.charCodeAt(0))
    const privateKey = await crypto.subtle.importKey('pkcs8', der, { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, ['sign'])
    const signature = new Uint8Array(await crypto.subtle.sign('RSASSA-PKCS1-v1_5', privateKey, encoder.encode(signingInput)))
    const token = `${signingInput}.${bytesToBase64Url(signature)}`

    expect(await verifyWithKey(token, pair.publicPem, 'RS256')).toBe(true)
    expect(await verifyWithKey(token, pair.publicJwk, 'RS256')).toBe(true)
    expect(await verifyWithKey(token, (await generateKeyPair('RS256')).publicPem, 'RS256')).toBe(false)
  })

  it('JWK ↔ PEM 往返', async () => {
    const pair = await generateKeyPair('RS256')
    const pem = await jwkToPem(pair.publicJwk)
    const jwk = await pemToJwk(pem, 'RS256')
    expect(jwk.kty).toBe('RSA')
    expect(jwk.n).toBe(pair.publicJwk.n)
    expect(jwk.e).toBe(pair.publicJwk.e)
  })

  it('PEM 格式不对报错', async () => {
    await expect(verifyWithKey('a.b.c', 'not a key', 'RS256')).rejects.toThrow('invalidKey')
  })
})

describe('AES-GCM 加解密', () => {
  it('口令往返', async () => {
    const cipher = await encryptText('家庭共享的私密内容', 'correct horse')
    expect(cipher).not.toContain('家庭')
    expect(await decryptText(cipher, 'correct horse')).toBe('家庭共享的私密内容')
  })

  it('口令错误或密文损坏不返回明文', async () => {
    const cipher = await encryptText('hello', 'pw')
    await expect(decryptText(cipher, 'nope')).rejects.toThrow('badPayload')
    await expect(decryptText('AAAA', 'pw')).rejects.toThrow('badPayload')
  })
})
