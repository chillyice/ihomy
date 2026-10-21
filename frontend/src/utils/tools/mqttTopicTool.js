// MQTT 主题解析 / 匹配 / 校验。核心逻辑与 bit_pouch src/plugins/mqtt-topic/run.ts 一致；不连接 broker。
// 由 bit_pouch 插件 mqtt-topic 转写，两仓各自维护测试，不跨仓引用。
var __defProp = Object.defineProperty
var __defNormalProp = (obj, key, value) =>
  key in obj ? __defProp(obj, key, { enumerable: true, configurable: true, writable: true, value }) : (obj[key] = value)
var __publicField = (obj, key, value) => __defNormalProp(obj, typeof key !== 'symbol' ? key + '' : key, value)
const MAX_LINES = 1e4
const MAX_TOPIC_BYTES = 65535
const ERROR_TEXT = {
  emptyTopic: { zh: '\u4E3B\u9898\u4E0D\u80FD\u4E3A\u7A7A', en: 'Topic cannot be empty' },
  emptyFilter: { zh: '\u8FC7\u6EE4\u5668\u4E0D\u80FD\u4E3A\u7A7A', en: 'Filter cannot be empty' },
  invalidWildcard: {
    zh: '+ \u5FC5\u987B\u72EC\u5360\u4E00\u5C42\uFF0C# \u5FC5\u987B\u72EC\u5360\u6700\u540E\u4E00\u5C42',
    en: '+ must occupy a whole level; # must be the last level',
  },
  invalidShared: {
    zh: '\u5171\u4EAB\u8BA2\u9605\u683C\u5F0F\u5FC5\u987B\u662F $share/{group}/{filter}\uFF0Cgroup \u4E0D\u80FD\u542B / + #',
    en: 'Shared subscription must be $share/{group}/{filter}; group cannot contain / + #',
  },
  publishWildcard: {
    zh: '\u53D1\u5E03\u4E3B\u9898\u4E0D\u80FD\u5305\u542B\u901A\u914D\u7B26',
    en: 'Publish topics cannot contain wildcards',
  },
  tooLong: { zh: '\u4E3B\u9898\u8D85\u8FC7 65,535 \u5B57\u8282', en: 'Topic exceeds 65,535 bytes' },
}
class MqttError extends Error {
  constructor(code, detail, line) {
    super(code)
    __publicField(this, 'code')
    __publicField(this, 'line')
    __publicField(this, 'detail')
    this.code = code
    this.detail = detail
    this.line = line
  }
}
function byteLength(text) {
  return new TextEncoder().encode(text).length
}
function checkLength(topic, line) {
  if (byteLength(topic) > MAX_TOPIC_BYTES) throw new MqttError('tooLong', void 0, line)
}
function parseFilter(text, line) {
  const trimmed = text.trim()
  if (!trimmed) throw new MqttError('emptyFilter', void 0, line)
  checkLength(trimmed, line)
  let shared
  let filter = trimmed
  if (trimmed.startsWith('$share/')) {
    const segments = trimmed.split('/')
    const group = segments[1] ?? ''
    const inner = segments.slice(2).join('/')
    if (!group || /[/+#]/.test(group) || !inner) throw new MqttError('invalidShared', trimmed, line)
    shared = { group, filter: inner }
    filter = inner
  }
  const levels = filter.split('/')
  const wildcards = []
  levels.forEach((level, index) => {
    if (level === '#') {
      if (index !== levels.length - 1) throw new MqttError('invalidWildcard', filter, line)
      wildcards.push({ level: index, kind: '#' })
      return
    }
    if (level.includes('#')) throw new MqttError('invalidWildcard', filter, line)
    if (level === '+') {
      wildcards.push({ level: index, kind: '+' })
      return
    }
    if (level.includes('+')) throw new MqttError('invalidWildcard', filter, line)
  })
  return { levels, wildcards, shared }
}
function parseTopic(text, line) {
  const trimmed = text.trim()
  if (!trimmed) throw new MqttError('emptyTopic', void 0, line)
  checkLength(trimmed, line)
  if (trimmed.includes('+') || trimmed.includes('#')) throw new MqttError('publishWildcard', trimmed, line)
  return trimmed.split('/')
}
function matches(filter, topic) {
  if (topic[0]?.startsWith('$') && (filter.levels[0] === '#' || filter.levels[0] === '+')) return false
  let topicIndex = 0
  for (let filterIndex = 0; filterIndex < filter.levels.length; filterIndex += 1) {
    const level = filter.levels[filterIndex]
    if (level === '#') return true
    if (level === '+') {
      if (topicIndex >= topic.length) return false
      topicIndex += 1
      continue
    }
    if (topicIndex >= topic.length || level !== topic[topicIndex]) return false
    topicIndex += 1
  }
  return topicIndex === topic.length
}
function meaningfulLines(text) {
  return text
    .split(/\r?\n/)
    .map((raw, index) => ({ text: raw.trim(), line: index + 1 }))
    .filter((entry) => entry.text)
}
function failure(error, locale) {
  const base = ERROR_TEXT[error.code][locale]
  return {
    ok: false,
    error: {
      code: error.code,
      message: error.detail ? `${base}: ${error.detail}` : base,
      line: error.line,
      column: error.line ? 1 : void 0,
    },
  }
}
function rowFromFilter(entry) {
  const filter = parseFilter(entry.text, entry.line)
  return {
    input: entry.text,
    levels: filter.levels,
    wildcards: filter.wildcards,
    shared: filter.shared,
    system: !filter.shared && entry.text.startsWith('$'),
  }
}
function describeError(error, locale) {
  if (error instanceof MqttError) return ERROR_TEXT[error.code][locale]
  return error instanceof Error ? error.message : String(error)
}
const SAMPLE_PARSE = `sensors/room1/temperature
sensors/+/temperature
sensors/#
$SYS/broker/uptime
$share/workers/sensors/+/temperature`
const SAMPLE_MATCH = `sensors/+/temperature
sensors/room1/temperature
sensors/room2/humidity
$SYS/broker/uptime
$share/workers/sensors/+/temperature`
const SAMPLE_VALIDATE = `sensors/room1/temperature
sensors/+/temperature
sensors/room1#
bad/level+/x
$share//sensors/#
$share/workers/sensors/+/temperature`
const run = (input, options, ctx) => {
  const lines = meaningfulLines(String(input ?? ''))
  if (!lines.length) return failure(new MqttError('emptyFilter'), ctx.locale)
  if (lines.length > MAX_LINES) {
    return {
      ok: false,
      error: { code: 'tooLong', message: ctx.locale === 'zh' ? '\u8F93\u5165\u884C\u6570\u8D85\u8FC7 10,000' : 'More than 10,000 lines' },
    }
  }
  const mode = options.mode === 'match' || options.mode === 'validate' ? options.mode : 'parse'
  try {
    if (mode === 'parse') {
      const rows2 = lines.map(rowFromFilter)
      const output2 = rows2
        .map((row) => {
          const parts = [`${row.input} \u2192 levels=${row.levels?.length ?? 0}`]
          if (row.wildcards?.length) {
            parts.push(`wildcards=${row.wildcards.map((wildcard) => `${wildcard.kind}@${wildcard.level}`).join(',')}`)
          }
          if (row.shared) parts.push(`shared=${row.shared.group}`)
          if (row.system) parts.push('system')
          return parts.join('  ')
        })
        .join('\n')
      return {
        ok: true,
        output: output2,
        data: { mode, rows: rows2, warnings: [] },
        stats: {
          topics: rows2.length,
          wildcards: rows2.reduce((sum, row) => sum + (row.wildcards?.length ?? 0), 0),
          shared: rows2.filter((row) => row.shared).length,
        },
      }
    }
    if (mode === 'match') {
      const filter = parseFilter(lines[0].text, lines[0].line)
      const rows2 = lines.slice(1).map((entry) => ({
        input: entry.text,
        matched: matches(filter, parseTopic(entry.text, entry.line)),
      }))
      const output2 = rows2.map((row) => `${row.input}: ${row.matched ? 'true' : 'false'}`).join('\n')
      return {
        ok: true,
        output: output2,
        data: { mode, rows: rows2, warnings: [] },
        stats: { matched: rows2.filter((row) => row.matched).length, tested: rows2.length },
      }
    }
    const rows = lines.map((entry) => {
      const errors = []
      let validFilter = true
      let validTopic = true
      try {
        parseFilter(entry.text, entry.line)
      } catch (error) {
        validFilter = false
        errors.push(describeError(error, ctx.locale))
      }
      try {
        parseTopic(entry.text, entry.line)
      } catch (error) {
        validTopic = false
        const message = describeError(error, ctx.locale)
        if (!errors.includes(message)) errors.push(message)
      }
      return { input: entry.text, validFilter, validTopic, errors }
    })
    const output = rows
      .map((row) => `${row.input}: filter=${row.validFilter ? 'true' : 'false'} topic=${row.validTopic ? 'true' : 'false'}`)
      .join('\n')
    return {
      ok: true,
      output,
      data: { mode, rows, warnings: [] },
      stats: {
        rows: rows.length,
        validFilters: rows.filter((row) => row.validFilter).length,
        validTopics: rows.filter((row) => row.validTopic).length,
      },
    }
  } catch (error) {
    if (error instanceof MqttError) return failure(error, ctx.locale)
    const message = error instanceof Error ? error.message : String(error)
    return { ok: false, error: { code: 'PLUGIN_ERROR', message } }
  }
}
export { SAMPLE_MATCH, SAMPLE_PARSE, SAMPLE_VALIDATE, run }
