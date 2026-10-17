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
