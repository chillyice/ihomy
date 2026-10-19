import { describe, it, expect } from 'vitest'
import {
  COMMON_BASES,
  MAX_FRACTION_DIGITS,
  bitwiseCalc,
  convert,
  convertAll,
  describeFloat,
  floatToHex,
  hexToFloat,
  parseValue,
  toBaseString,
} from './baseConvert'

const text = (raw, from, to, options) => convert(raw, from, to, options).text

describe('convert 整数', () => {
  it('常用进制互转', () => {
    expect(text('255', 10, 2)).toBe('11111111')
    expect(text('255', 10, 16)).toBe('FF')
    expect(text('11111111', 2, 10)).toBe('255')
    expect(text('FF', 16, 10)).toBe('255')
    expect(text('777', 8, 2)).toBe('111111111')
  })

  it('超出 2^53 的长整数也精确', () => {
    expect(text('12345678901234567890', 10, 16)).toBe('AB54A98CEB1F0AD2')
    expect(text('AB54A98CEB1F0AD2', 16, 10)).toBe('12345678901234567890')
  })

  it('支持 36 进制与大小写控制', () => {
    expect(text('z', 36, 10)).toBe('35')
    expect(text('35', 10, 36)).toBe('Z')
    expect(text('35', 10, 36, { uppercase: false })).toBe('z')
  })

  it('负号保留,零不带符号', () => {
    expect(text('-255', 10, 16)).toBe('-FF')
    expect(text('-0', 10, 16)).toBe('0')
  })

  it('忽略空格与下划线,支持 0x / 0b 前缀写法', () => {
    expect(text('FF FF', 16, 10)).toBe('65535')
    expect(text('1_000', 10, 16)).toBe('3E8')
    expect(text('0xFF', 16, 10)).toBe('255')
    expect(text('0b1010', 2, 10)).toBe('10')
    expect(text('.5', 10, 2)).toBe('0.1')
  })
})

describe('convert 小数', () => {
  it('能整除时精确结束', () => {
    expect(text('10.5', 10, 2)).toBe('1010.1')
    expect(text('3.75', 10, 2)).toBe('11.11')
    expect(text('11.11', 2, 10)).toBe('3.75')
  })

  it('除不尽时按上限截断并标记近似', () => {
    const r = convert('0.1', 10, 2)
    expect(r.approximate).toBe(true)
    expect(r.text).toMatch(/^0\.0001100110011001$/)
    expect(r.text.split('.')[1]).toHaveLength(MAX_FRACTION_DIGITS)
  })

  it('小数在 16 进制下正常换算', () => {
    expect(text('0.5', 10, 16)).toBe('0.8')
    expect(text('0.8', 16, 10)).toBe('0.5')
  })
})

describe('输入校验', () => {
  const errorOf = (raw, base) => {
    try {
      parseValue(raw, base)
      return null
    } catch (e) {
      return e
    }
  }

  it('空输入', () => {
    expect(errorOf('  ', 10).code).toBe('empty')
    expect(errorOf('.', 10).code).toBe('empty')
  })

  it('数字超出进制范围时指出具体字符', () => {
    expect(errorOf('102', 2)).toMatchObject({ code: 'invalidDigit', char: '2', base: 2 })
    expect(errorOf('G', 16).code).toBe('invalidDigit')
  })

  it('多个小数点与超长输入', () => {
    expect(errorOf('1.2.3', 10).code).toBe('invalidNumber')
    expect(errorOf('1'.repeat(2001), 10).code).toBe('tooLong')
  })

  it('进制越界', () => {
    expect(errorOf('1', 1).code).toBe('invalidBase')
    expect(errorOf('1', 37).code).toBe('invalidBase')
  })
})

describe('convertAll', () => {
  it('一次解析换算到多个进制', () => {
    const r = convertAll('255', 10, COMMON_BASES)
    expect(r.ok).toBe(true)
    expect(r.results.map((x) => [x.base, x.text])).toEqual([
      [2, '11111111'],
      [8, '377'],
      [10, '255'],
      [16, 'FF'],
      [36, '73'],
    ])
  })

  it('去重并过滤非法目标进制', () => {
    const r = convertAll('10', 10, [16, 16, 1, 37, 2])
    expect(r.results.map((x) => x.base)).toEqual([16, 2])
  })

  it('输入错误时返回错误码与字符', () => {
    expect(convertAll('12', 2)).toMatchObject({ ok: false, error: 'invalidDigit', char: '2' })
  })

  it('toBaseString 支持 BigInt 与零', () => {
    expect(toBaseString(0n, 16)).toBe('0')
    expect(toBaseString(255n, 16)).toBe('FF')
  })
})

describe('IEEE 754', () => {
  it('单精度往返', () => {
    expect(floatToHex(1)).toBe('3F800000')
    expect(floatToHex(-2.5)).toBe('C0200000')
    expect(hexToFloat('3F800000').value).toBe(1)
    expect(floatToHex(hexToFloat('C0200000').value)).toBe('C0200000')
  })

  it('双精度往返', () => {
    expect(floatToHex(1, { double: true })).toBe('3FF0000000000000')
    expect(hexToFloat('3FF0000000000000', { double: true }).value).toBe(1)
  })

  it('拆解符号/指数/尾数', () => {
    const info = describeFloat('3F800000')
    expect(info.kind).toBe('normal')
    expect(info.sign).toBe(0)
    expect(info.realExponent).toBe(0)
    expect(info.fraction).toBe(1)
  })

  it('NaN 与无穷', () => {
    expect(describeFloat('7F800000').kind).toBe('infinity')
    expect(describeFloat('7FC00000').kind).toBe('nan')
  })

  it('十六进制长度不符报错', () => {
    expect(() => hexToFloat('3F80')).toThrow('invalidFloatHex')
  })
})

describe('按位运算', () => {
  it('AND / OR / XOR / NOT', () => {
    expect(bitwiseCalc('12', '10', 'AND').unsigned).toBe('8')
    expect(bitwiseCalc('12', '10', 'OR').unsigned).toBe('14')
    expect(bitwiseCalc('12', '10', 'XOR').unsigned).toBe('6')
    expect(bitwiseCalc('0', '0', 'NOT', { width: 8 }).unsigned).toBe('255')
  })

  it('移位与有符号显示', () => {
    expect(bitwiseCalc('1', '4', 'SHL', { width: 8 }).binary).toBe('00010000')
    expect(bitwiseCalc('255', '0', 'SHR', { width: 8 }).unsigned).toBe('255')
    const signed = bitwiseCalc('255', '255', 'AND', { width: 8 })
    expect(signed.signed).toBe('-1')
  })

  it('非法宽度与运算报错', () => {
    expect(() => bitwiseCalc('1', '1', 'AND', { width: 12 })).toThrow('invalidWidth')
    expect(() => bitwiseCalc('1', '1', 'NOPE')).toThrow('invalidOp')
  })
})
