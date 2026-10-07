import { describe, it, expect } from 'vitest'
import { decodeJwtPayload, isTokenExpired } from './jwt'

// 造不验签的 JWT(本模块只读载荷,签名段内容无关)
const makeToken = (payload) => {
  const seg = (o) => Buffer.from(JSON.stringify(o)).toString('base64url')
  return `${seg({ alg: 'HS512', typ: 'JWT' })}.${seg(payload)}.sig`
}
const nowSec = () => Math.floor(Date.now() / 1000)

describe('decodeJwtPayload', () => {
  it('解出载荷,中文按 UTF-8 还原', () => {
    const p = decodeJwtPayload(makeToken({ sub: '6', username: '高大尚', familyId: 2 }))
    expect(p.username).toBe('高大尚')
    expect(p.familyId).toBe(2)
  })

  it('非 JWT 结构与空值返回 null', () => {
    expect(decodeJwtPayload('not-a-jwt')).toBe(null)
    expect(decodeJwtPayload('')).toBe(null)
    expect(decodeJwtPayload(undefined)).toBe(null)
  })
})

describe('isTokenExpired', () => {
  it('已过期的令牌为 true', () => {
    expect(isTokenExpired(makeToken({ exp: nowSec() - 1 }))).toBe(true)
  })

  it('有效期内为 false', () => {
    expect(isTokenExpired(makeToken({ exp: nowSec() + 3600 }))).toBe(false)
  })

  it('即将到期(在提前量内)按已过期处理', () => {
    expect(isTokenExpired(makeToken({ exp: nowSec() + 5 }))).toBe(true)
    expect(isTokenExpired(makeToken({ exp: nowSec() + 5 }), 0)).toBe(false)
  })

  it('无 exp 或无法解析时为 false(交回 401 流程)', () => {
    expect(isTokenExpired(makeToken({ sub: '6' }))).toBe(false)
    expect(isTokenExpired('broken')).toBe(false)
  })
})
