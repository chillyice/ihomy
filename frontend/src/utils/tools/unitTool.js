// 单位换算的纯逻辑:静态系数表 + 温度特殊公式。
// 类别与单位 id 稳定(页面/i18n/bit_pouch 共用),系数以基准单位为准。

const LINEAR = {
  length: {
    base: 'm',
    units: [
      { id: 'mm', factor: 0.001 },
      { id: 'cm', factor: 0.01 },
      { id: 'm', factor: 1 },
      { id: 'km', factor: 1000 },
      { id: 'in', factor: 0.0254 },
      { id: 'ft', factor: 0.3048 },
      { id: 'yd', factor: 0.9144 },
      { id: 'mi', factor: 1609.344 },
      { id: 'nmi', factor: 1852 },
    ],
  },
  area: {
    base: 'm2',
    units: [
      { id: 'cm2', factor: 0.0001 },
      { id: 'm2', factor: 1 },
      { id: 'km2', factor: 1e6 },
      { id: 'ha', factor: 10000 },
      { id: 'mu', factor: 666.6667 },
      { id: 'acre', factor: 4046.8564 },
      { id: 'ft2', factor: 0.092903 },
      { id: 'in2', factor: 0.00064516 },
    ],
  },
  volume: {
    base: 'l',
    units: [
      { id: 'ml', factor: 0.001 },
      { id: 'l', factor: 1 },
      { id: 'm3', factor: 1000 },
      { id: 'tsp', factor: 0.00492892 },
      { id: 'tbsp', factor: 0.0147868 },
      { id: 'floz', factor: 0.0295735 },
      { id: 'cup', factor: 0.236588 },
      { id: 'pt', factor: 0.473176 },
      { id: 'qt', factor: 0.946353 },
      { id: 'gal', factor: 3.785412 },
    ],
  },
  weight: {
    base: 'kg',
    units: [
      { id: 'mg', factor: 1e-6 },
      { id: 'g', factor: 0.001 },
      { id: 'kg', factor: 1 },
      { id: 't', factor: 1000 },
      { id: 'jin', factor: 0.5 },
      { id: 'oz', factor: 0.0283495 },
      { id: 'lb', factor: 0.453592 },
    ],
  },
  speed: {
    base: 'mps',
    units: [
      { id: 'mps', factor: 1 },
      { id: 'kmh', factor: 1 / 3.6 },
      { id: 'mph', factor: 0.44704 },
      { id: 'knot', factor: 0.514444 },
      { id: 'fts', factor: 0.3048 },
    ],
  },
  pressure: {
    base: 'kpa',
    units: [
      { id: 'pa', factor: 0.001 },
      { id: 'hpa', factor: 0.1 },
      { id: 'kpa', factor: 1 },
      { id: 'mpa', factor: 1000 },
      { id: 'bar', factor: 100 },
      { id: 'atm', factor: 101.325 },
      { id: 'psi', factor: 6.894757 },
      { id: 'mmhg', factor: 0.133322 },
    ],
  },
  data: {
    base: 'b',
    units: [
      { id: 'bit', factor: 0.125 },
      { id: 'b', factor: 1 },
      { id: 'kb', factor: 1000 },
      { id: 'mb', factor: 1e6 },
      { id: 'gb', factor: 1e9 },
      { id: 'tb', factor: 1e12 },
      { id: 'kib', factor: 1024 },
      { id: 'mib', factor: 1024 ** 2 },
      { id: 'gib', factor: 1024 ** 3 },
      { id: 'tib', factor: 1024 ** 4 },
    ],
  },
  time: {
    base: 's',
    units: [
      { id: 'ms', factor: 0.001 },
      { id: 's', factor: 1 },
      { id: 'min', factor: 60 },
      { id: 'h', factor: 3600 },
      { id: 'day', factor: 86400 },
      { id: 'week', factor: 604800 },
      { id: 'month', factor: 2592000 },
      { id: 'year', factor: 31536000 },
    ],
  },
}

export const CATEGORY_IDS = [...Object.keys(LINEAR), 'temperature']

export function unitsOf(category) {
  if (category === 'temperature') return ['c', 'f', 'k']
  return (LINEAR[category]?.units || []).map((unit) => unit.id)
}

const TEMP_UNITS = ['c', 'f', 'k']

const toCelsius = (value, unit) => {
  if (unit === 'f') return ((value - 32) * 5) / 9
  if (unit === 'k') return value - 273.15
  return value
}

const fromCelsius = (value, unit) => {
  if (unit === 'f') return (value * 9) / 5 + 32
  if (unit === 'k') return value + 273.15
  return value
}

const findUnit = (category, unit) => {
  if (category === 'temperature') return TEMP_UNITS.includes(unit) ? { id: unit } : null
  return LINEAR[category]?.units.find((item) => item.id === unit) || null
}

/** 换算并保留 6 位有效数字(整数不加小数点)。 */
export function formatNumber(value) {
  if (!Number.isFinite(value)) return ''
  if (value === 0) return '0'
  const absolute = Math.abs(value)
  if (absolute >= 1e15 || absolute < 1e-9) return value.toExponential(6).replace(/\.?0+e/, 'e')
  const digits = Math.max(0, 6 - Math.floor(Math.log10(absolute)) - 1)
  return String(Number(value.toFixed(Math.min(digits, 10))))
}

export function convert(value, category, from, to) {
  const number = Number(value)
  if (!Number.isFinite(number)) throw new Error('invalidNumber')
  const source = findUnit(category, from)
  const target = findUnit(category, to)
  if (!source || !target) throw new Error('unknownUnit')
  if (category === 'temperature') return fromCelsius(toCelsius(number, from), to)
  const base = number * source.factor
  return base / target.factor
}

/** 某个单位在类别里的全部换算(页面做速查表)。 */
export function convertAll(value, category, from) {
  const number = Number(value)
  if (!Number.isFinite(number)) return []
  if (category === 'temperature') {
    return TEMP_UNITS.map((unit) => ({ unit, value: convert(number, category, from, unit) }))
  }
  const group = LINEAR[category]
  if (!group) return []
  return group.units.map((unit) => ({ unit: unit.id, value: convert(number, category, from, unit.id) }))
}

const MESSAGES = {
  invalidNumber: { zh: '请输入数字', en: 'Enter a number' },
  unknownUnit: { zh: '不支持这个单位', en: 'Unsupported unit' },
}

export function run(input, options = {}, ctx = {}) {
  const locale = ctx.locale === 'en' ? 'en' : 'zh'
  const category = options.category || 'length'
  const from = options.from || LINEAR[category]?.base || 'c'
  const to = options.to || from
  try {
    const result = convert(input, category, from, to)
    return {
      ok: true,
      output: `${formatNumber(Number(input))} ${from} = ${formatNumber(result)} ${to}`,
      data: { value: result, category, from, to, all: convertAll(input, category, from) },
      stats: { category },
    }
  } catch (error) {
    const code = error?.message === 'unknownUnit' ? 'unknownUnit' : 'invalidNumber'
    const template = MESSAGES[code]
    return { ok: false, error: { code, message: template[locale] || template.zh } }
  }
}
