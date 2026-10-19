// @ts-nocheck
// 由 ihomy scripts/export-tools.mjs 从 frontend/src/utils/tools/timeTool.js 导出。
// 时间工具箱的纯逻辑:时间戳解析、时区展示、日期差、日期加减与 Cron 表达式。
// 与 bit_pouch 插件契约同构:run(input, options, ctx) 返回 { ok, output, data, stats } 或 { ok:false, error }。
// 时区换算交给 Intl(自动处理夏令时);Cron 的 5/6/7 段解析与下次执行时间在这里手写,不引依赖。

export const MAX_INPUT_LENGTH = 200
export const MAX_DATE_MS = 8.64e15
export const CRON_FIELDS = ['minute', 'hour', 'dayOfMonth', 'month', 'dayOfWeek']
export const UNITS = ['minute', 'hour', 'day', 'week', 'month', 'year']

const MONTH_NAMES = { JAN: 1, FEB: 2, MAR: 3, APR: 4, MAY: 5, JUN: 6, JUL: 7, AUG: 8, SEP: 9, OCT: 10, NOV: 11, DEC: 12 }
const DOW_NAMES = { SUN: 0, MON: 1, TUE: 2, WED: 3, THU: 4, FRI: 5, SAT: 6 }

const MESSAGES = {
  empty: { zh: '请输入时间戳或日期', en: 'Enter a timestamp or date' },
  tooLong: { zh: '输入过长,请只填一个时间', en: 'Input is too long; provide a single time value' },
  invalidInput: { zh: '无法识别为时间戳或日期', en: 'Not a recognizable timestamp or date' },
  outOfRange: { zh: '时间超出可表示范围', en: 'Time is outside the representable range' },
  invalidTimeZone: { zh: '无法识别这个时区', en: 'Unrecognized time zone' },
  invalidAmount: { zh: '数量必须是整数', en: 'Amount must be an integer' },
  invalidUnit: { zh: '不支持的时间单位', en: 'Unsupported time unit' },
  invalidCron: { zh: 'Cron 表达式无效:{detail}', en: 'Invalid cron expression: {detail}' },
  cronNoMatch: { zh: '未来一段时间内没有匹配的执行时间', en: 'No matching run time found in the near future' },
  diffEmpty: { zh: '请填写开始与结束时间', en: 'Enter both start and end times' },
}

function fail(code, locale, extra = {}) {
  const template = MESSAGES[code] || MESSAGES.invalidInput
  let message = template[locale] || template.zh
  for (const [key, value] of Object.entries(extra)) message = message.replace(`{${key}}`, String(value))
  return { ok: false, error: { code, message } }
}

export function listTimeZones() {
  const values = typeof Intl.supportedValuesOf === 'function' ? Intl.supportedValuesOf('timeZone') : []
  const list = new Set(['UTC', ...values])
  return [...list].sort((a, b) => a.localeCompare(b))
}

export const TIME_ZONES = listTimeZones()

export function resolveTimeZone(value) {
  if (!value) return Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'
  // 构造一次即可验证 IANA 名称
  Intl.DateTimeFormat('en-US', { timeZone: value })
  return value
}

export function isTimestampUnit(value) {
  return value === 'auto' || value === 's' || value === 'ms'
}

/** 返回 { ms, numeric, unit };无法识别返回 null。numeric 为 true 时 unit 是 s/ms,否则是 date。 */
export function parseTimeInput(raw, unit = 'auto') {
  const text = String(raw ?? '').trim()
  if (!text) return null

  if (/^[+-]?\d+(\.\d+)?$/.test(text)) {
    const value = Number(text)
    if (!Number.isFinite(value)) return null
    const used = unit === 's' ? 's' : unit === 'ms' ? 'ms' : Math.abs(value) >= 1e12 ? 'ms' : 's'
    return { ms: used === 'ms' ? value : value * 1000, numeric: true, unit: used }
  }

  const parsed = Date.parse(text)
  if (Number.isNaN(parsed)) return null
  return { ms: parsed, numeric: false, unit: 'date' }
}

export function inRange(ms) {
  return Number.isFinite(ms) && Math.abs(ms) <= MAX_DATE_MS
}

function zoneFormatter(timeZone, locale, options) {
  return new Intl.DateTimeFormat(locale === 'en' ? 'en-US' : 'zh-CN', { timeZone, ...options })
}

export function zoneParts(ms, timeZone, locale = 'zh') {
  const zone = resolveTimeZone(timeZone)
  const date = new Date(ms)
  const dateTime = zoneFormatter(zone, locale, {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(date)
  const offset =
    zoneFormatter(zone, 'en', { timeZoneName: 'longOffset' })
      .formatToParts(date)
      .find((part) => part.type === 'timeZoneName')?.value ?? ''
  const weekday = zoneFormatter(zone, locale, { weekday: 'long' }).format(date)
  return { zone, dateTime, offset, weekday }
}

export function formatTime(ms, timeZone, locale = 'zh') {
  const parts = zoneParts(ms, timeZone, locale)
  return {
    ms,
    seconds: ms / 1000,
    iso: new Date(ms).toISOString(),
    utc: new Date(ms).toUTCString(),
    local: zoneFormatter(parts.zone, locale, { dateStyle: 'medium', timeStyle: 'medium' }).format(new Date(ms)),
    zoned: parts.dateTime,
    zone: parts.zone,
    offset: parts.offset,
    weekday: parts.weekday,
  }
}

/** 两个时刻的差值,按天/时/分/秒拆开;sign 表示后者相对前者。 */
export function diffTimes(startMs, endMs) {
  const delta = endMs - startMs
  const abs = Math.abs(delta)
  const days = Math.floor(abs / 86_400_000)
  const hours = Math.floor((abs % 86_400_000) / 3_600_000)
  const minutes = Math.floor((abs % 3_600_000) / 60_000)
  const seconds = Math.floor((abs % 60_000) / 1000)
  return {
    startMs,
    endMs,
    sign: Math.sign(delta),
    days,
    hours,
    minutes,
    seconds,
    totalMs: delta,
    totalDays: Math.round((delta / 86_400_000) * 1000) / 1000,
  }
}

/** 按 UTC 日历加减;月份/年份会夹到目标月最后一天(1 月 31 日 + 1 月 = 2 月 28/29 日)。 */
export function addToTime(ms, amount, unit = 'day') {
  if (!UNITS.includes(unit)) throw new Error('invalidUnit')
  if (!Number.isInteger(amount)) throw new Error('invalidAmount')
  const date = new Date(ms)
  if (unit === 'minute') date.setUTCMinutes(date.getUTCMinutes() + amount)
  else if (unit === 'hour') date.setUTCHours(date.getUTCHours() + amount)
  else if (unit === 'day') date.setUTCDate(date.getUTCDate() + amount)
  else if (unit === 'week') date.setUTCDate(date.getUTCDate() + amount * 7)
  else {
    const months = unit === 'year' ? amount * 12 : amount
    const day = date.getUTCDate()
    date.setUTCDate(1)
    date.setUTCMonth(date.getUTCMonth() + months)
    const lastDay = new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth() + 1, 0)).getUTCDate()
    date.setUTCDate(Math.min(day, lastDay))
  }
  return date.getTime()
}

function parseValue(raw, { min, max, names, normalize }) {
  const text = String(raw).trim().toUpperCase()
  const named = names?.[text]
  const value = named ?? Number(text)
  if (!Number.isInteger(value)) throw new Error(`value:${raw}`)
  const normalized = normalize ? normalize(value) : value
  if (normalized < min || normalized > max) throw new Error(`range:${raw}`)
  return normalized
}

function parseField(raw, definition) {
  const values = new Set()
  for (const part of String(raw).split(',')) {
    if (!part) throw new Error('empty')
    const [rangePart, stepPart] = part.split('/')
    const step = stepPart === undefined ? 1 : Number(stepPart)
    if (!Number.isInteger(step) || step < 1) throw new Error(`step:${part}`)

    let start
    let end
    if (rangePart === '*' || rangePart === '?') {
      start = definition.min
      end = definition.max
    } else if (rangePart.includes('-')) {
      const [a, b] = rangePart.split('-')
      start = parseValue(a, definition)
      end = parseValue(b, definition)
    } else {
      start = parseValue(rangePart, definition)
      end = stepPart === undefined ? start : definition.max
    }
    if (start > end) throw new Error(`order:${part}`)
    for (let value = start; value <= end; value += step) values.add(value)
  }
  return values
}

const CRON_DEFINITIONS = {
  second: { min: 0, max: 59 },
  minute: { min: 0, max: 59 },
  hour: { min: 0, max: 23 },
  dayOfMonth: { min: 1, max: 31 },
  month: { min: 1, max: 12, names: MONTH_NAMES },
  dayOfWeek: { min: 0, max: 7, names: DOW_NAMES, normalize: (value) => (value === 7 ? 0 : value) },
  year: { min: 1970, max: 2099 },
}

/** 解析 5 段(分 时 日 月 周)或 6/7 段(秒 分 时 日 月 周 [年])。 */
export function parseCron(expression) {
  const parts = String(expression ?? '')
    .trim()
    .split(/\s+/)
    .filter(Boolean)
  if (parts.length !== 5 && parts.length !== 6 && parts.length !== 7) {
    return { ok: false, error: { code: 'invalidCron', field: 'count', detail: `${parts.length} fields` } }
  }

  const hasSeconds = parts.length >= 6
  const names = hasSeconds
    ? ['second', 'minute', 'hour', 'dayOfMonth', 'month', 'dayOfWeek', 'year']
    : ['minute', 'hour', 'dayOfMonth', 'month', 'dayOfWeek']
  const chosen = names.slice(0, parts.length)
  const fields = {}
  for (let i = 0; i < chosen.length; i += 1) {
    const key = chosen[i]
    try {
      fields[key] = parseField(parts[i], CRON_DEFINITIONS[key])
    } catch (error) {
      return { ok: false, error: { code: 'invalidCron', field: key, detail: error.message } }
    }
  }
  // 星期字段 0 与 7 都表示周日,归一后去重
  if (fields.dayOfWeek) fields.dayOfWeek = new Set([...fields.dayOfWeek].map((value) => (value === 7 ? 0 : value)))
  return {
    ok: true,
    hasSeconds,
    fields,
    raw: parts,
    dayOfMonthRestricted: !['*', '?'].includes(parts[hasSeconds ? 3 : 2]),
    dayOfWeekRestricted: !['*', '?'].includes(parts[hasSeconds ? 5 : 4]),
  }
}

function cronMatches(date, parsed) {
  const { fields } = parsed
  if (!fields.minute.has(date.getMinutes())) return false
  if (!fields.hour.has(date.getHours())) return false
  if (!fields.month.has(date.getMonth() + 1)) return false
  if (fields.second && !fields.second.has(date.getSeconds())) return false
  if (fields.year && !fields.year.has(date.getFullYear())) return false

  const dom = fields.dayOfMonth.has(date.getDate())
  const dow = fields.dayOfWeek.has(date.getDay())
  if (parsed.dayOfMonthRestricted && parsed.dayOfWeekRestricted) return dom || dow
  if (parsed.dayOfMonthRestricted) return dom
  if (parsed.dayOfWeekRestricted) return dow
  return true
}

export function nextCronRuns(expression, fromMs = Date.now(), count = 5, maxIterations = 200_000) {
  const parsed = parseCron(expression)
  if (!parsed.ok) return parsed

  const cursor = new Date(fromMs)
  cursor.setMilliseconds(0)
  if (parsed.hasSeconds) cursor.setSeconds(cursor.getSeconds() + 1)
  else {
    cursor.setSeconds(0)
    cursor.setMinutes(cursor.getMinutes() + 1)
  }

  const runs = []
  for (let i = 0; i < maxIterations && runs.length < count; i += 1) {
    if (cronMatches(cursor, parsed)) runs.push(cursor.getTime())
    if (parsed.hasSeconds) cursor.setSeconds(cursor.getSeconds() + 1)
    else cursor.setMinutes(cursor.getMinutes() + 1)
  }

  return { ok: true, parsed, runs, exhausted: runs.length < count }
}

function parsePair(input) {
  const lines = String(input ?? '')
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
  return lines
}

/**
 * 统一入口。
 * options.mode: convert | diff | add | cron
 * options.unit: auto | s | ms(convert);minute...year(add)
 * options.timeZone: IANA 名称,缺省为运行环境时区
 */
export function run(input, options = {}, ctx = {}) {
  const locale = ctx.locale === 'en' ? 'en' : 'zh'
  const mode = options.mode || 'convert'
  let timeZone
  try {
    timeZone = resolveTimeZone(options.timeZone)
  } catch {
    return fail('invalidTimeZone', locale)
  }

  if (mode === 'convert') {
    const raw = String(input ?? '')
    if (!raw.trim()) return fail('empty', locale)
    if (raw.length > MAX_INPUT_LENGTH) return fail('tooLong', locale)
    const parsed = parseTimeInput(raw, isTimestampUnit(options.unit) ? options.unit : 'auto')
    if (!parsed) return fail('invalidInput', locale)
    if (!inRange(parsed.ms)) return fail('outOfRange', locale)
    const formatted = formatTime(parsed.ms, timeZone, locale)
    return {
      ok: true,
      output: formatted.iso,
      data: { mode, ...formatted, numeric: parsed.numeric, unit: parsed.unit },
      stats: { seconds: Math.floor(parsed.ms / 1000), zone: timeZone },
    }
  }

  if (mode === 'diff') {
    const pair = parsePair(input)
    if (pair.length < 2) return fail('diffEmpty', locale)
    const start = parseTimeInput(pair[0], 'auto')
    const end = parseTimeInput(pair[1], 'auto')
    if (!start || !end) return fail('invalidInput', locale)
    const diff = diffTimes(start.ms, end.ms)
    return {
      ok: true,
      output: String(diff.totalMs),
      data: { mode, ...diff, start: formatTime(start.ms, timeZone, locale), end: formatTime(end.ms, timeZone, locale) },
      stats: { totalDays: diff.totalDays },
    }
  }

  if (mode === 'add') {
    const parsed = parseTimeInput(input, 'auto')
    if (!parsed) return fail('invalidInput', locale)
    const amount = Number(options.amount ?? 1)
    if (!Number.isInteger(amount)) return fail('invalidAmount', locale)
    const unit = UNITS.includes(options.unit) ? options.unit : 'day'
    try {
      const ms = addToTime(parsed.ms, amount, unit)
      if (!inRange(ms)) return fail('outOfRange', locale)
      const formatted = formatTime(ms, timeZone, locale)
      return { ok: true, output: formatted.iso, data: { mode, ...formatted, amount, unit }, stats: { amount, unit } }
    } catch (error) {
      return fail(error?.message === 'invalidUnit' ? 'invalidUnit' : 'invalidAmount', locale)
    }
  }

  if (mode === 'cron') {
    const state = nextCronRuns(String(input ?? '').trim(), Number(options.from ?? Date.now()), Number(options.count ?? 5))
    if (!state.ok) {
      return fail('invalidCron', locale, { detail: `${state.error.field}: ${state.error.detail}` })
    }
    const next = state.runs.map((ms) => formatTime(ms, timeZone, locale))
    if (!next.length) return fail('cronNoMatch', locale)
    return {
      ok: true,
      output: next.map((item) => item.zoned).join('\n'),
      data: { mode, expression: String(input ?? '').trim(), next, hasSeconds: state.parsed.hasSeconds, exhausted: state.exhausted },
      stats: { count: next.length, zone: timeZone },
    }
  }

  return fail('invalidInput', locale)
}
