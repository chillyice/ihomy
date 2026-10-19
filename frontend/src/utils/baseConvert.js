// 进制转换的纯逻辑:2–36 进制互转,整数部分用 BigInt 精确计算,小数部分按目标进制逐步乘取整。
// 不用 Number 解析:超过 2^53 的长整数会静默丢精度,而进制换算经常用来核对 id / 哈希片段。

export const BASE_MIN = 2
export const BASE_MAX = 36
export const COMMON_BASES = [2, 8, 10, 16, 36]
export const MAX_INPUT_LENGTH = 2000
export const MAX_FRACTION_DIGITS = 16

const DIGITS = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ'

export class BaseConvertError extends Error {
  constructor(code, extra = {}) {
    super(code)
    this.code = code
    Object.assign(this, extra)
  }
}

const digitValue = (ch) => DIGITS.indexOf(ch.toUpperCase())

export function assertBase(base) {
  if (!Number.isInteger(base) || base < BASE_MIN || base > BASE_MAX) throw new BaseConvertError('invalidBase')
}

/** 解析输入:返回符号、整数 BigInt 与小数分数(分子/分母),非法输入抛 BaseConvertError */
export function parseValue(raw, base) {
  assertBase(base)
  let input = String(raw ?? '').trim()
  if (!input) throw new BaseConvertError('empty')
  if (input.length > MAX_INPUT_LENGTH) throw new BaseConvertError('tooLong')

  input = input.replace(/[\s_]/g, '')
  let sign = 1
  if (input[0] === '+' || input[0] === '-') {
    sign = input[0] === '-' ? -1 : 1
    input = input.slice(1)
  }
  // 常见前缀只在进制对得上时才剥掉:0b 在 16 进制里是合法数字 B,不能无条件删
  if (base === 16 && /^0[xX]/.test(input)) input = input.slice(2)
  else if (base === 2 && /^0[bB]/.test(input)) input = input.slice(2)
  else if (base === 8 && /^0[oO]/.test(input)) input = input.slice(2)

  if (input.indexOf('.') !== input.lastIndexOf('.')) throw new BaseConvertError('invalidNumber')

  const dot = input.indexOf('.')
  let intPart = dot === -1 ? input : input.slice(0, dot)
  const fracPart = dot === -1 ? '' : input.slice(dot + 1)
  if (!intPart && !fracPart) throw new BaseConvertError('empty')
  if (!intPart) intPart = '0'

  for (const ch of intPart + fracPart) {
    const value = digitValue(ch)
    if (value < 0 || value >= base) throw new BaseConvertError('invalidDigit', { char: ch, base })
  }

  const bigBase = BigInt(base)
  let intValue = 0n
  for (const ch of intPart) intValue = intValue * bigBase + BigInt(digitValue(ch))
  let fracNumerator = 0n
  for (const ch of fracPart) fracNumerator = fracNumerator * bigBase + BigInt(digitValue(ch))
  const fracDenominator = bigBase ** BigInt(fracPart.length)

  return { sign, intValue, fracNumerator, fracDenominator }
}

/** 整数 BigInt → 指定进制字符串(不含符号) */
export function toBaseString(value, base, { uppercase = true } = {}) {
  assertBase(base)
  let rest = value < 0n ? -value : value
  if (rest === 0n) return '0'
  const bigBase = BigInt(base)
  let out = ''
  while (rest > 0n) {
    out = DIGITS[Number(rest % bigBase)] + out
    rest /= bigBase
  }
  return uppercase ? out : out.toLowerCase()
}

function formatParsed(parsed, toBase, { uppercase = true, maxFractionDigits = MAX_FRACTION_DIGITS } = {}) {
  assertBase(toBase)
  const { sign, intValue, fracNumerator, fracDenominator } = parsed
  const negative = sign < 0 && (intValue > 0n || fracNumerator > 0n)
  let out = (negative ? '-' : '') + toBaseString(intValue, toBase, { uppercase })
  let approximate = false

  if (fracNumerator > 0n) {
    const bigBase = BigInt(toBase)
    let numerator = fracNumerator
    let digits = ''
    let exact = false
    while (digits.length < maxFractionDigits) {
      numerator *= bigBase
      const digit = numerator / fracDenominator
      numerator %= fracDenominator
      digits += DIGITS[Number(digit)]
      if (numerator === 0n) {
        exact = true
        break
      }
    }
    // 近似截断时末尾的 0 不再提供信息,统一去掉;能整除的也一并去掉
    digits = digits.replace(/0+$/, '')
    if (digits) out += `.${uppercase ? digits : digits.toLowerCase()}`
    approximate = !exact
  }

  return { text: out, approximate }
}

/** 单次换算:返回 {ok, text, approximate} 或抛 BaseConvertError */
export function convert(raw, fromBase, toBase, options) {
  const parsed = parseValue(raw, fromBase)
  return { ok: true, ...formatParsed(parsed, toBase, options) }
}

/**
 * 一次换算到多个目标进制(解析只做一次)。
 * 成功:{ok:true, results:[{base, text, approximate}]};失败:{ok:false, error, char}
 */
export function convertAll(raw, fromBase, bases = COMMON_BASES, options) {
  let parsed
  try {
    parsed = parseValue(raw, fromBase)
  } catch (e) {
    if (!(e instanceof BaseConvertError)) throw e
    return { ok: false, error: e.code, char: e.char }
  }
  const targets = [...new Set(bases)].filter((b) => Number.isInteger(b) && b >= BASE_MIN && b <= BASE_MAX)
  return { ok: true, results: targets.map((base) => ({ base, ...formatParsed(parsed, base, options) })) }
}

// ---------------------------------------------------------------
// IEEE 754 浮点(单/双精度)与十六进制互转、按位运算
// ---------------------------------------------------------------

const hexByte = (byte) => byte.toString(16).padStart(2, '0').toUpperCase()

/** 浮点数 → 按大端顺序的十六进制字节串 */
export function floatToHex(value, { double = false } = {}) {
  const buffer = new ArrayBuffer(double ? 8 : 4)
  const view = new DataView(buffer)
  if (double) view.setFloat64(0, Number(value))
  else view.setFloat32(0, Number(value))
  return Array.from(new Uint8Array(buffer), hexByte).join('')
}

/** 十六进制字节串 → 浮点数;字节数必须与精度一致 */
export function hexToFloat(hex, { double = false } = {}) {
  const cleaned = String(hex ?? '')
    .replace(/[\s,]/g, '')
    .replace(/^0x/i, '')
  const expected = double ? 16 : 8
  if (!new RegExp(`^[0-9a-fA-F]{${expected}}$`).test(cleaned)) throw new BaseConvertError('invalidFloatHex', { expected })
  const bytes = new Uint8Array(expected / 2)
  for (let i = 0; i < bytes.length; i += 1) bytes[i] = parseInt(cleaned.slice(i * 2, i * 2 + 2), 16)
  const view = new DataView(bytes.buffer)
  const value = double ? view.getFloat64(0) : view.getFloat32(0)
  return { value, hex: cleaned.toUpperCase() }
}

/** 拆解浮点位:符号 / 指数 / 尾数,NaN 与无穷单独标注 */
export function describeFloat(hex, { double = false } = {}) {
  const bits = double ? 64n : 32n
  const exponentBits = double ? 11n : 8n
  const mantissaBits = double ? 52n : 23n
  const cleaned = String(hex ?? '')
    .replace(/[\s,]/g, '')
    .replace(/^0x/i, '')
  if (!new RegExp(`^[0-9a-fA-F]{${double ? 16 : 8}}$`).test(cleaned))
    throw new BaseConvertError('invalidFloatHex', { expected: double ? 16 : 8 })

  const raw = BigInt(`0x${cleaned}`)
  const sign = (raw >> (bits - 1n)) & 1n
  const exponent = (raw >> mantissaBits) & ((1n << exponentBits) - 1n)
  const mantissa = raw & ((1n << mantissaBits) - 1n)
  const bias = (1n << (exponentBits - 1n)) - 1n
  const maxExponent = (1n << exponentBits) - 1n

  let kind = 'normal'
  if (exponent === maxExponent) kind = mantissa === 0n ? 'infinity' : 'nan'
  else if (exponent === 0n) kind = mantissa === 0n ? 'zero' : 'subnormal'

  const { value } = hexToFloat(cleaned, { double })
  const fraction = kind === 'normal' ? 1 + Number(mantissa) / 2 ** Number(mantissaBits) : Number(mantissa) / 2 ** Number(mantissaBits)
  const realExponent = Number(exponent) - Number(bias)

  return {
    hex: cleaned.toUpperCase(),
    value,
    kind,
    sign: Number(sign),
    signLabel: sign === 1n ? '-' : '+',
    exponent: Number(exponent),
    exponentBias: Number(bias),
    realExponent,
    mantissa: mantissa
      .toString(16)
      .toUpperCase()
      .padStart(Number(mantissaBits) / 4, '0'),
    fraction,
    binary: raw.toString(2).padStart(Number(bits), '0'),
  }
}

export const BITWISE_OPS = ['AND', 'OR', 'XOR', 'NOT', 'SHL', 'SHR']
export const BITWISE_WIDTHS = [8, 16, 32, 64]

function parseBits(text, base, width) {
  assertBase(base)
  const input = String(text ?? '')
    .trim()
    .replace(/[\s_]/g, '')
  if (!input) throw new BaseConvertError('empty')
  const negative = input.startsWith('-')
  const digits = (negative ? input.slice(1) : input).toLowerCase()
  if (!digits) throw new BaseConvertError('invalidNumber')
  let value = 0n
  const bigBase = BigInt(base)
  for (const ch of digits) {
    const digit = digitValue(ch)
    if (digit < 0 || digit >= base) throw new BaseConvertError('invalidDigit', { char: ch, base })
    value = value * bigBase + BigInt(digit)
  }
  const mask = (1n << BigInt(width)) - 1n
  return (negative ? -value : value) & mask
}

/**
 * 按位运算;结果同时给出无符号值、二进制与十六进制。
 * ops: AND / OR / XOR / NOT / SHL / SHR(逻辑右移,无符号)
 */
export function bitwiseCalc(leftText, rightText, op, { base = 10, width = 32 } = {}) {
  if (!BITWISE_WIDTHS.includes(width)) throw new BaseConvertError('invalidWidth')
  if (!BITWISE_OPS.includes(op)) throw new BaseConvertError('invalidOp')
  const mask = (1n << BigInt(width)) - 1n
  const left = parseBits(leftText, base, width)
  const right = op === 'NOT' ? 0n : parseBits(rightText || '0', base, width)
  let result
  switch (op) {
    case 'AND':
      result = left & right
      break
    case 'OR':
      result = left | right
      break
    case 'XOR':
      result = left ^ right
      break
    case 'NOT':
      result = ~left & mask
      break
    case 'SHL':
      result = (left << right) & mask
      break
    case 'SHR':
      result = (left >> right) & mask
      break
    default:
      throw new BaseConvertError('invalidOp')
  }
  const signBit = 1n << BigInt(width - 1)
  const signed = result & signBit ? result - (1n << BigInt(width)) : result
  return {
    unsigned: result.toString(),
    signed: signed.toString(),
    binary: result.toString(2).padStart(width, '0'),
    hex: result
      .toString(16)
      .toUpperCase()
      .padStart(width / 4, '0'),
    octal: result.toString(8),
    width,
  }
}
