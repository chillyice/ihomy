import { describe, it, expect } from 'vitest'
import { generatePassword, passwordStrength } from './password'

describe('generatePassword', () => {
  it('按指定长度生成', () => {
    expect(generatePassword({ length: 8 })).toHaveLength(8)
    expect(generatePassword({ length: 32 })).toHaveLength(32)
  })

  it('勾选的每类字符集至少出现一个', () => {
    const pw = generatePassword({ length: 16, lower: true, upper: true, digit: true, symbol: true })
    expect(/[a-z]/.test(pw)).toBe(true)
    expect(/[A-Z]/.test(pw)).toBe(true)
    expect(/\d/.test(pw)).toBe(true)
    expect(/[^a-zA-Z\d]/.test(pw)).toBe(true)
  })

  it('noAmbiguous 剔除易混淆字符 0/O/1/l/I', () => {
    const pw = generatePassword({ length: 64, noAmbiguous: true })
    expect(/[0O1lI]/.test(pw)).toBe(false)
  })

  it('未勾选任何字符集时返回空串', () => {
    expect(generatePassword({ lower: false, upper: false, digit: false, symbol: false })).toBe('')
  })
})

describe('passwordStrength', () => {
  it('空密码最弱', () => {
    expect(passwordStrength('')).toMatchObject({ score: 0, labelKey: 'empty' })
  })

  it('常见弱密码直接判最弱', () => {
    expect(passwordStrength('password').score).toBe(0)
    expect(passwordStrength('abc123').score).toBe(0)
  })

  it('长且多字符类判强', () => {
    expect(passwordStrength('Xk9$mQ2#vL7!').score).toBe(4)
  })

  it('单一字符重复降档', () => {
    expect(passwordStrength('aaaaaaaa').score).toBe(0)
  })
})
