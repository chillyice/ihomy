// 正则测试的纯逻辑:实时匹配、捕获组、替换与分割预览。
// 匹配一律新建 RegExp 实例,避免 g 标志的 lastIndex 跨次执行残留;调用方负责把执行放进 Worker 限时,
// 本文件只做单次同步计算(非法表达式与超长输入返回可读错误,不抛裸异常)。

export const REGEX_FLAGS = ['g', 'i', 'm', 's', 'u', 'y', 'd']
export const MAX_INPUT_LENGTH = 200_000
export const MAX_MATCHES = 500
export const MAX_SPLIT_PARTS = 1000

export const REGEX_PRESETS = [
  { id: 'email', pattern: '[\\w.+-]+@[\\w-]+\\.[\\w.-]+', flags: 'g' },
  { id: 'url', pattern: 'https?://[^\\s"\'<>]+', flags: 'g' },
  { id: 'ipv4', pattern: '\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b', flags: 'g' },
  { id: 'mobile', pattern: '1[3-9]\\d{9}', flags: 'g' },
  { id: 'idcard', pattern: '\\d{17}[\\dXx]', flags: 'g' },
  { id: 'date', pattern: '\\d{4}-\\d{2}-\\d{2}', flags: 'g' },
  { id: 'cjk', pattern: '[\\u4e00-\\u9fa5]+', flags: 'g' },
  { id: 'number', pattern: '-?\\d+(?:\\.\\d+)?', flags: 'g' },
]

const MESSAGES = {
  emptyPattern: { zh: '先输入正则表达式', en: 'Enter a regular expression first' },
  tooLong: { zh: '输入超过 {max} 字符,请缩短后再试', en: 'Input exceeds {max} characters; shorten it and retry' },
  invalidPattern: { zh: '正则表达式不合法', en: 'Invalid regular expression' },
  invalidFlags: { zh: '标志只能是 g i m s u y d 的组合', en: 'Flags may only combine g i m s u y d' },
}

function fail(code, locale, extra = {}) {
  const template = MESSAGES[code] || MESSAGES.invalidPattern
  let message = template[locale] || template.zh
  for (const [key, value] of Object.entries(extra)) message = message.replace(`{${key}}`, String(value))
  return { ok: false, error: { code, message } }
}

/** 去重并校验标志;非法标志抛 invalidFlags */
export function normalizeFlags(flags) {
  const raw = String(flags ?? '')
  let out = ''
  for (const flag of raw) {
    if (!REGEX_FLAGS.includes(flag)) throw new Error('invalidFlags')
    if (!out.includes(flag)) out += flag
  }
  return out
}

const reasonOf = (error) =>
  String(error?.message || '')
    .replace(/^Invalid regular expression:\s*/i, '')
    .trim()

function toMatch(match, text) {
  const groups = []
  for (let i = 1; i < match.length; i += 1) groups.push({ index: i, value: match[i] ?? '' })
  return {
    value: match[0],
    index: match.index,
    end: match.index + match[0].length,
    groups,
    named: match.groups ? { ...match.groups } : {},
    text,
  }
}

/**
 * 执行一次正则:
 * options = { pattern, flags, replacement }
 * 返回 { ok, output, data: { matches, groups, replaced, split, truncated, flags } }。
 */
export function executeRegex(input, options = {}, ctx = {}) {
  const locale = ctx.locale === 'en' ? 'en' : 'zh'
  const text = String(input ?? '')
  const pattern = String(options.pattern ?? '')
  if (!pattern) return fail('emptyPattern', locale)
  if (text.length > MAX_INPUT_LENGTH) return fail('tooLong', locale, { max: MAX_INPUT_LENGTH })

  let flags
  try {
    flags = normalizeFlags(options.flags)
  } catch {
    return fail('invalidFlags', locale)
  }

  let base
  try {
    base = new RegExp(pattern, flags)
  } catch (error) {
    const reason = reasonOf(error)
    const mapped = fail('invalidPattern', locale)
    if (reason) mapped.error.message = `${mapped.error.message}: ${reason}`
    return mapped
  }

  const matches = []
  let truncated = false
  if (base.global) {
    const scanner = new RegExp(pattern, flags.includes('g') ? flags : `${flags}g`)
    let match
    while ((match = scanner.exec(text)) !== null) {
      matches.push(toMatch(match, text))
      if (match[0] === '') scanner.lastIndex += 1
      if (matches.length >= MAX_MATCHES) {
        truncated = scanner.lastIndex < text.length
        break
      }
    }
  } else {
    const match = base.exec(text)
    if (match) matches.push(toMatch(match, text))
  }

  const groupRows = []
  matches.forEach((match, index) => {
    for (const group of match.groups) groupRows.push({ match: index + 1, name: '', ...group })
    for (const [name, value] of Object.entries(match.named)) groupRows.push({ match: index + 1, index: '', name, value: value ?? '' })
  })

  let replaced = null
  if (options.replacement !== undefined && options.replacement !== null) {
    try {
      replaced = text.replace(new RegExp(pattern, flags.includes('g') ? flags : `${flags}g`), String(options.replacement))
    } catch {
      replaced = null
    }
  }

  const split = text.split(new RegExp(pattern, flags.replace(/[gy]/g, ''))).slice(0, MAX_SPLIT_PARTS)

  const output = matches.length ? matches.map((item) => item.value).join('\n') : ''
  return {
    ok: true,
    output,
    data: { matches, groups: groupRows, replaced, split, truncated, flags, global: base.global },
    stats: { matches: matches.length, groups: groupRows.length, length: text.length },
  }
}
