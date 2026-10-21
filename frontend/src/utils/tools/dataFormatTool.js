// YAML / TOML / INI / .env / Properties 与 JSON 互转。核心逻辑与 bit_pouch src/plugins/data-format/run.ts 一致，纯函数、不联网、不落盘；YAML / TOML 依赖按需动态加载。
// 由 bit_pouch 插件 data-format 转写，两仓各自维护测试，不跨仓引用。
var __defProp = Object.defineProperty
var __defNormalProp = (obj, key, value) =>
  key in obj ? __defProp(obj, key, { enumerable: true, configurable: true, writable: true, value }) : (obj[key] = value)
var __publicField = (obj, key, value) => __defNormalProp(obj, typeof key !== 'symbol' ? key + '' : key, value)
let yamlModule = null
let tomlModule = null
function loadYaml() {
  yamlModule ?? (yamlModule = import('yaml'))
  return yamlModule
}
function loadToml() {
  tomlModule ?? (tomlModule = import('smol-toml'))
  return tomlModule
}
const MAX_INPUT = 1e6
const MAX_DEPTH = 100
const MAX_ALIASES = 100
const UNSAFE_KEYS = /* @__PURE__ */ new Set(['__proto__', 'constructor', 'prototype'])
const FORMATS = ['json', 'yaml', 'toml', 'ini', 'env', 'properties']
const ENV_KEY_RE = /^[A-Za-z_][A-Za-z0-9_]*$/
const ERROR_TEXT = {
  empty: { zh: '\u5185\u5BB9\u4E3A\u7A7A', en: 'Input is empty' },
  tooLarge: { zh: '\u8F93\u5165\u8D85\u8FC7 1,000,000 \u5B57\u7B26', en: 'Input exceeds 1,000,000 characters' },
  tooDeep: { zh: '\u5D4C\u5957\u8D85\u8FC7 100 \u5C42', en: 'Nesting exceeds 100 levels' },
  syntaxError: { zh: '\u8BED\u6CD5\u9519\u8BEF', en: 'Syntax error' },
  unsupportedFormat: {
    zh: '\u65E0\u6CD5\u81EA\u52A8\u8BC6\u522B\u683C\u5F0F\uFF0C\u8BF7\u624B\u52A8\u9009\u62E9',
    en: 'Could not detect the format; select one manually',
  },
  multiDocument: {
    zh: '\u8F93\u5165\u5305\u542B\u591A\u4E2A YAML \u6587\u6863\uFF0C\u672C\u7248\u53EA\u652F\u6301\u5355\u4E2A\u6587\u6863',
    en: 'Input contains multiple YAML documents; only single documents are supported',
  },
  tooManyAliases: {
    zh: 'YAML \u522B\u540D\u8D85\u8FC7\u4E0A\u9650\uFF08100\uFF09',
    en: 'YAML alias count exceeds the limit (100)',
  },
  cyclicReference: { zh: 'YAML \u951A\u70B9\u5B58\u5728\u5FAA\u73AF\u5F15\u7528', en: 'YAML anchors contain a cyclic reference' },
  unsafeKey: { zh: '\u4E0D\u5B89\u5168\u7684\u952E\u540D', en: 'Unsafe key' },
  nestedNotSupported: {
    zh: '\u76EE\u6807\u683C\u5F0F\u4E0D\u652F\u6301\u5D4C\u5957\u5BF9\u8C61\u6216\u6570\u7EC4',
    en: 'The target format does not support nested objects or arrays',
  },
  invalidKey: { zh: '\u952E\u540D\u4E0D\u5408\u6CD5', en: 'Invalid key' },
  unsupportedValue: {
    zh: '\u76EE\u6807\u683C\u5F0F\u4E0D\u652F\u6301\u8BE5\u503C\u7C7B\u578B',
    en: 'The target format does not support this value type',
  },
}
const WARNING_TEXT = {
  nonFiniteNumber: {
    zh: '\u975E\u6709\u9650\u6570\u5B57\u5DF2\u8F6C\u4E3A\u5B57\u7B26\u4E32',
    en: 'Non-finite numbers were converted to strings',
  },
  commentsDropped: { zh: '\u6CE8\u91CA\u5DF2\u4E22\u5F03', en: 'Comments were dropped' },
  precisionLoss: {
    zh: '\u8D85\u8FC7\u5B89\u5168\u8303\u56F4\u7684\u6574\u6570\u53EF\u80FD\u4E22\u5931\u7CBE\u5EA6',
    en: 'Integers beyond the safe range may lose precision',
  },
  duplicateKey: { zh: '\u91CD\u590D\u952E\uFF0C\u540E\u8005\u8986\u76D6\u524D\u8005', en: 'Duplicate key; the later value wins' },
}
class DataError extends Error {
  constructor(code, detail, pos) {
    super(code)
    __publicField(this, 'code')
    __publicField(this, 'line')
    __publicField(this, 'column')
    __publicField(this, 'lineText')
    __publicField(this, 'detail')
    this.code = code
    this.detail = detail
    this.line = pos?.line
    this.column = pos?.column
    this.lineText = pos?.lineText
  }
}
function makeWarner(locale) {
  const warnings = []
  const seen = /* @__PURE__ */ new Set()
  const warn = (code, detail) => {
    const key = `${code}:${detail ?? ''}`
    if (seen.has(key)) return
    seen.add(key)
    const text = WARNING_TEXT[code][locale]
    warnings.push(detail ? `${code}: ${text} (${detail})` : `${code}: ${text}`)
  }
  return { warn, warnings }
}
function setSafe(target, key, value) {
  Object.defineProperty(target, key, { value, enumerable: true, writable: true, configurable: true })
}
function normalizeValue(value, warn, depth = 1) {
  if (depth > MAX_DEPTH) throw new DataError('tooDeep')
  if (value instanceof Date) return value.toISOString()
  if (typeof value === 'bigint') {
    const asNumber = Number(value)
    if (Number.isSafeInteger(asNumber)) return asNumber
    warn('precisionLoss', String(value))
    return String(value)
  }
  if (typeof value === 'number' && !Number.isFinite(value)) {
    warn('nonFiniteNumber')
    if (Number.isNaN(value)) return 'nan'
    return value > 0 ? 'inf' : '-inf'
  }
  if (Array.isArray(value)) return value.map((item) => normalizeValue(item, warn, depth + 1))
  if (value && typeof value === 'object') {
    const out = {}
    for (const key of Object.keys(value)) {
      if (UNSAFE_KEYS.has(key)) throw new DataError('unsafeKey', key)
      setSafe(out, key, normalizeValue(value[key], warn, depth + 1))
    }
    return out
  }
  return value
}
function sortDeep(value) {
  if (Array.isArray(value)) return value.map(sortDeep)
  if (value && typeof value === 'object') {
    const out = {}
    for (const key of Object.keys(value).sort()) {
      setSafe(out, key, sortDeep(value[key]))
    }
    return out
  }
  return value
}
function isScalar(value) {
  return value === null || ['string', 'number', 'boolean'].includes(typeof value)
}
function scalarText(value) {
  if (value === null || value === void 0) return ''
  return String(value)
}
function countKeys(value) {
  if (Array.isArray(value)) return value.length
  if (value && typeof value === 'object') return Object.keys(value).length
  return 0
}
function lineAt(text, line) {
  return text.split(/\r?\n/)[line - 1]
}
function jsonErrorPosition(message, text) {
  const match = /position\s+(\d+)/i.exec(message)
  const position = match ? Number(match[1]) : -1
  if (position < 0) return { line: 1, column: 1, lineText: lineAt(text, 1) }
  const before = text.slice(0, position)
  const line = before.split(/\r?\n/).length
  const column = position - before.lastIndexOf('\n')
  return { line, column, lineText: lineAt(text, line) }
}
function parseJsonText(text, warn) {
  const unsafe = findUnsafeInteger(text)
  if (unsafe) warn('precisionLoss', unsafe)
  try {
    return JSON.parse(text)
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error)
    throw new DataError('syntaxError', message, jsonErrorPosition(message, text))
  }
}
function findUnsafeInteger(text) {
  let index = 0
  while (index < text.length) {
    const char = text[index]
    if (char === '"') {
      index += 1
      while (index < text.length) {
        if (text[index] === '\\') {
          index += 2
          continue
        }
        if (text[index] === '"') {
          index += 1
          break
        }
        index += 1
      }
      continue
    }
    if (char === '-' || (char >= '0' && char <= '9')) {
      const start = index
      if (char === '-') index += 1
      while (index < text.length && text[index] >= '0' && text[index] <= '9') index += 1
      if (text[index] === '.' || text[index] === 'e' || text[index] === 'E') {
        index += 1
        while (index < text.length && /[0-9eE+-]/.test(text[index])) index += 1
        continue
      }
      const literal = text.slice(start, index)
      try {
        const value = BigInt(literal)
        if (value > BigInt(Number.MAX_SAFE_INTEGER) || value < BigInt(Number.MIN_SAFE_INTEGER)) return literal
      } catch {}
      continue
    }
    index += 1
  }
  return null
}
function hasComment(text, markers) {
  let quote = null
  for (let index = 0; index < text.length; index += 1) {
    const char = text[index]
    if (quote) {
      if (quote === '"' && char === '\\') {
        index += 1
        continue
      }
      if (char === quote) quote = null
      if (char === '\n') quote = null
      continue
    }
    if (char === '"' || char === "'") {
      quote = char
      continue
    }
    if (markers.includes(char) && (index === 0 || /[\s;,]/.test(text[index - 1]))) return true
    if (char === '\n') quote = null
  }
  return false
}
function hasLineComment(text, markers) {
  return text.split(/\r?\n/).some((line) => {
    const trimmed = line.trimStart()
    return markers.some((marker) => trimmed.startsWith(marker))
  })
}
function yamlError(error, text) {
  const pos = error.linePos?.[0]
  return new DataError('syntaxError', error.message, {
    line: pos?.line,
    column: pos?.col,
    lineText: pos ? lineAt(text, pos.line) : void 0,
  })
}
async function parseYamlText(text, warn) {
  const { parseAllDocuments } = await loadYaml()
  let docs
  try {
    docs = parseAllDocuments(text, { schema: 'core' })
  } catch (error) {
    const first = error.linePos?.[0]
    throw new DataError('syntaxError', error.message, {
      line: first?.line,
      column: first?.col,
      lineText: first ? lineAt(text, first.line) : void 0,
    })
  }
  const firstError = docs.flatMap((doc) => doc.errors)[0]
  if (firstError) throw yamlError(firstError, text)
  if (docs.length > 1) throw new DataError('multiDocument')
  if (!docs.length) throw new DataError('empty')
  if (hasComment(text, ['#'])) warn('commentsDropped')
  try {
    return docs[0].toJS({ maxAliasCount: MAX_ALIASES })
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error)
    if (/circular/i.test(message)) throw new DataError('cyclicReference')
    if (/alias/i.test(message)) throw new DataError('tooManyAliases')
    throw new DataError('syntaxError', message)
  }
}
async function parseTomlText(text, warn) {
  if (hasComment(text, ['#'])) warn('commentsDropped')
  const { parse } = await loadToml()
  try {
    return parse(text)
  } catch (error) {
    const problem = error
    throw new DataError('syntaxError', problem.message, {
      line: problem.line,
      column: problem.column,
      lineText: problem.line ? lineAt(text, problem.line) : void 0,
    })
  }
}
function parseIniText(text, warn) {
  if (hasLineComment(text, [';', '#'])) warn('commentsDropped')
  const result = {}
  let section = ''
  const lines = text.split(/\r?\n/)
  lines.forEach((raw, index) => {
    const line = raw.trim()
    if (!line || line.startsWith(';') || line.startsWith('#')) return
    if (line.startsWith('[')) {
      if (!line.endsWith(']')) {
        throw new DataError('syntaxError', line, { line: index + 1, column: 1, lineText: raw })
      }
      section = line.slice(1, -1).trim()
      return
    }
    const separator = findSeparator(line)
    if (separator < 0) {
      throw new DataError('syntaxError', line, { line: index + 1, column: 1, lineText: raw })
    }
    const key = line.slice(0, separator).trim()
    const value = line.slice(separator + 1).trim()
    if (!key) throw new DataError('syntaxError', line, { line: index + 1, column: 1, lineText: raw })
    const fullKey = section ? `${section}.${key}` : key
    if (Object.prototype.hasOwnProperty.call(result, fullKey)) warn('duplicateKey', fullKey)
    setSafe(result, fullKey, value)
  })
  return result
}
function findSeparator(line) {
  for (let index = 0; index < line.length; index += 1) {
    const char = line[index]
    if (char === '\\') {
      index += 1
      continue
    }
    if (char === '=' || char === ':') return index
  }
  return -1
}
function readLogicalLines(text) {
  const physical = text.split(/\r?\n/)
  const logical = []
  let buffer = ''
  let start = 1
  let continued = false
  physical.forEach((raw, index) => {
    if (!buffer) start = index + 1
    const part = continued ? raw.trimStart() : raw
    const backslashes = /\\*$/.exec(part)?.[0].length ?? 0
    if (backslashes % 2 === 1) {
      buffer += part.slice(0, -1)
      continued = true
      return
    }
    logical.push({ text: buffer + part, line: start })
    buffer = ''
    continued = false
  })
  if (buffer) logical.push({ text: buffer, line: start })
  return logical
}
function unescapeProperty(value) {
  let out = ''
  for (let index = 0; index < value.length; index += 1) {
    const char = value[index]
    if (char !== '\\') {
      out += char
      continue
    }
    const next = value[index + 1]
    if (next === void 0) break
    if (next === 'u' && /^[0-9a-fA-F]{4}$/.test(value.slice(index + 2, index + 6))) {
      out += String.fromCharCode(parseInt(value.slice(index + 2, index + 6), 16))
      index += 5
      continue
    }
    const escapes = { n: '\n', r: '\r', t: '	', f: '\f' }
    out += escapes[next] ?? next
    index += 1
  }
  return out
}
function parsePropertiesText(text, warn) {
  if (hasLineComment(text, ['#', '!'])) warn('commentsDropped')
  const result = {}
  for (const logical of readLogicalLines(text)) {
    const line = logical.text.trimStart()
    if (!line || line.startsWith('#') || line.startsWith('!')) continue
    const separator = findSeparator(line)
    const rawKey = separator < 0 ? line : line.slice(0, separator)
    const rawValue = separator < 0 ? '' : line.slice(separator + 1)
    const key = unescapeProperty(rawKey.trim())
    if (!key) throw new DataError('syntaxError', line, { line: logical.line, column: 1, lineText: line })
    if (Object.prototype.hasOwnProperty.call(result, key)) warn('duplicateKey', key)
    setSafe(result, key, unescapeProperty(rawValue.trim()))
  }
  return result
}
function parseEnvText(text, warn) {
  if (hasLineComment(text, ['#']) || /(^|\s)#/.test(text)) warn('commentsDropped')
  const result = {}
  const lines = text.split(/\r?\n/)
  lines.forEach((raw, index) => {
    let line = raw.trim()
    if (!line || line.startsWith('#')) return
    if (/^export\s+/.test(line)) line = line.replace(/^export\s+/, '')
    const separator = line.indexOf('=')
    if (separator < 0) {
      throw new DataError('syntaxError', raw, { line: index + 1, column: 1, lineText: raw })
    }
    const key = line.slice(0, separator).trim()
    if (!key) throw new DataError('syntaxError', raw, { line: index + 1, column: 1, lineText: raw })
    let rest = line.slice(separator + 1).trim()
    let value = ''
    if (rest.startsWith('"') || rest.startsWith("'")) {
      const quote = rest[0]
      let closed = false
      let body = ''
      for (let cursor = 1; cursor < rest.length; cursor += 1) {
        const char = rest[cursor]
        if (quote === '"' && char === '\\') {
          const next = rest[cursor + 1]
          const escapes = { n: '\n', r: '\r', t: '	', '"': '"', '\\': '\\', "'": "'", $: '$' }
          body += escapes[next] ?? next
          cursor += 1
          continue
        }
        if (char === quote) {
          closed = true
          rest = rest.slice(cursor + 1)
          break
        }
        body += char
      }
      if (!closed) {
        throw new DataError('syntaxError', raw, { line: index + 1, column: 1, lineText: raw })
      }
      value = body
    } else {
      const comment = rest.search(/\s#/)
      if (comment >= 0) rest = rest.slice(0, comment)
      value = rest.trim()
    }
    if (Object.prototype.hasOwnProperty.call(result, key)) warn('duplicateKey', key)
    setSafe(result, key, value)
  })
  return result
}
function detectDataFormat(text) {
  const trimmed = text.trim()
  if (!trimmed) return null
  if (trimmed.startsWith('{') || /^\[\s*(?:[{"\]\d-]|true\b|false\b|null\b)/.test(trimmed)) return 'json'
  const lines = trimmed.split(/\r?\n/).filter((line) => line.trim() && !/^\s*[;#]/.test(line))
  const hasSection = lines.some((line) => /^\s*\[.+\]\s*$/.test(line))
  const hasAssign = lines.some((line) => line.includes('='))
  if (hasSection && hasAssign) {
    const tomlSignals =
      /"""|'''/.test(trimmed) ||
      lines.some((line) => /^\s*\[\[/.test(line)) ||
      lines.some((line) => /^\s*["'][^"']+["']\s*=/.test(line)) ||
      lines.some((line) => /=\s*["'][^"']*["']\s*$/.test(line))
    return tomlSignals ? 'toml' : 'ini'
  }
  if (/^---/m.test(trimmed) || lines.some((line) => /^[^:]+:\s/.test(line))) return 'yaml'
  const assignments = lines.filter((line) => line.includes('='))
  if (assignments.length) {
    const keys = assignments.map((line) =>
      line
        .slice(0, line.indexOf('='))
        .replace(/^export\s+/, '')
        .trim(),
    )
    if (keys.every((key) => /^[A-Z_][A-Z0-9_]*$/.test(key))) return 'env'
    if (/\\u[0-9a-fA-F]{4}/.test(trimmed)) return 'properties'
    if (keys.every((key) => ENV_KEY_RE.test(key))) return 'env'
    if (keys.some((key) => key.includes('.'))) return 'properties'
  }
  return null
}
async function parseSource(format, text, warn) {
  switch (format) {
    case 'json':
      return parseJsonText(text, warn)
    case 'yaml':
      return parseYamlText(text, warn)
    case 'toml':
      return parseTomlText(text, warn)
    case 'ini':
      return parseIniText(text, warn)
    case 'env':
      return parseEnvText(text, warn)
    case 'properties':
      return parsePropertiesText(text, warn)
  }
}
function indentText(value) {
  if (value === '4') return '    '
  if (value === 'tab') return '	'
  return '  '
}
function sortKeysOf(value) {
  return Object.keys(value).sort()
}
function flattenPairs(value, prefix, out, sortKeys) {
  const keys = sortKeys ? sortKeysOf(value) : Object.keys(value)
  for (const key of keys) {
    const child = value[key]
    const path = prefix ? `${prefix}.${key}` : key
    if (child && typeof child === 'object' && !Array.isArray(child)) {
      flattenPairs(child, path, out, sortKeys)
    } else if (isScalar(child)) {
      out.push([path, scalarText(child)])
    } else {
      throw new DataError('nestedNotSupported', path)
    }
  }
}
function serializeIni(value, flatten, sortKeys) {
  const lines = []
  const keys = sortKeys ? sortKeysOf(value) : Object.keys(value)
  if (flatten) {
    const pairs = []
    for (const key of keys) {
      const child = value[key]
      if (child && typeof child === 'object' && !Array.isArray(child)) {
        flattenPairs(child, key, pairs, sortKeys)
      } else if (isScalar(child)) {
        pairs.push([key, scalarText(child)])
      } else {
        throw new DataError('nestedNotSupported', key)
      }
    }
    for (const [key, text] of pairs) lines.push(`${key}=${text}`)
    return lines.join('\n')
  }
  const globals = []
  const sections = []
  for (const key of keys) {
    const child = value[key]
    if (isScalar(child)) {
      globals.push(`${key}=${scalarText(child)}`)
      continue
    }
    if (!child || typeof child !== 'object' || Array.isArray(child)) {
      throw new DataError('nestedNotSupported', key)
    }
    const sectionKeys = sortKeys ? sortKeysOf(child) : Object.keys(child)
    const sectionLines = []
    for (const sectionKey of sectionKeys) {
      const leaf = child[sectionKey]
      if (!isScalar(leaf)) throw new DataError('nestedNotSupported', `${key}.${sectionKey}`)
      sectionLines.push(`${sectionKey}=${scalarText(leaf)}`)
    }
    sections.push(`[${key}]
${sectionLines.join('\n')}`)
  }
  if (globals.length) lines.push(globals.join('\n'))
  if (sections.length) lines.push(sections.join('\n\n'))
  return lines.join('\n\n')
}
function serializeEnv(value, sortKeys) {
  const keys = sortKeys ? sortKeysOf(value) : Object.keys(value)
  const lines = []
  for (const key of keys) {
    if (!ENV_KEY_RE.test(key)) throw new DataError('invalidKey', key)
    const child = value[key]
    if (!isScalar(child)) throw new DataError('nestedNotSupported', key)
    lines.push(`${key}=${envValue(child)}`)
  }
  return lines.join('\n')
}
function envValue(value) {
  const text = scalarText(value)
  if (text === '') return ''
  if (!/[\s"'\\#=]/.test(text)) return text
  const escaped = text.replace(/\\/g, '\\\\').replace(/"/g, '\\"').replace(/\n/g, '\\n').replace(/\r/g, '\\r').replace(/\t/g, '\\t')
  return `"${escaped}"`
}
function escapeProperty(text) {
  return text
    .replace(/\\/g, '\\\\')
    .replace(/\n/g, '\\n')
    .replace(/\r/g, '\\r')
    .replace(/\t/g, '\\t')
    .replace(/=/g, '\\=')
    .replace(/:/g, '\\:')
    .replace(/^ /, '\\ ')
    .replace(/([#!])/g, '\\$1')
}
function serializeProperties(value) {
  const pairs = []
  flattenPairs(value, '', pairs, false)
  return pairs.map(([key, text]) => `${escapeProperty(key)}=${escapeProperty(text)}`).join('\n')
}
function assertTomlSafe(value, path = '') {
  if (value === null || value === void 0) throw new DataError('unsupportedValue', path || 'root')
  if (Array.isArray(value)) {
    value.forEach((item, index) => assertTomlSafe(item, `${path}[${index}]`))
    return
  }
  if (typeof value === 'object') {
    for (const key of Object.keys(value)) {
      assertTomlSafe(value[key], path ? `${path}.${key}` : key)
    }
  }
}
async function serializeTarget(target, value, options) {
  const sortKeys = Boolean(options.sortKeys)
  const indent = indentText(options.indent)
  switch (target) {
    case 'json': {
      const prepared = sortKeys ? sortDeep(value) : value
      return JSON.stringify(prepared, null, indent)
    }
    case 'yaml': {
      const { stringify } = await loadYaml()
      return stringify(value, {
        indent: options.indent === 'tab' ? 2 : indent.length,
        lineWidth: 0,
        sortMapEntries: sortKeys,
      }).replace(/\n$/, '')
    }
    case 'toml': {
      if (!value || typeof value !== 'object' || Array.isArray(value)) {
        throw new DataError('nestedNotSupported', 'root')
      }
      assertTomlSafe(value)
      const prepared = sortKeys ? sortDeep(value) : value
      const { stringify } = await loadToml()
      return stringify(prepared).replace(/\n$/, '')
    }
    case 'ini': {
      if (!value || typeof value !== 'object' || Array.isArray(value)) {
        throw new DataError('nestedNotSupported', 'root')
      }
      return serializeIni(value, options.flatten !== false, sortKeys)
    }
    case 'env': {
      if (!value || typeof value !== 'object' || Array.isArray(value)) {
        throw new DataError('nestedNotSupported', 'root')
      }
      return serializeEnv(value, sortKeys)
    }
    case 'properties': {
      if (!value || typeof value !== 'object' || Array.isArray(value)) {
        throw new DataError('nestedNotSupported', 'root')
      }
      const prepared = sortKeys ? sortDeep(value) : value
      return serializeProperties(prepared)
    }
  }
}
function isFormat(value) {
  return typeof value === 'string' && FORMATS.includes(value)
}
function success(output, data) {
  const chars = output.length
  const bytes = new TextEncoder().encode(output).length
  return {
    ok: true,
    output,
    data,
    stats: { keys: data.keyCount, chars, bytes, warnings: data.warnings.length },
  }
}
function failure(error, locale) {
  const base = ERROR_TEXT[error.code][locale]
  return {
    ok: false,
    error: {
      code: error.code,
      message: error.detail ? `${base}: ${error.detail}` : base,
      line: error.line,
      column: error.column,
      lineText: error.lineText,
    },
  }
}
const SAMPLE_JSON = `{
  "name": "bit_pouch",
  "port": 8080,
  "features": ["format", "convert"],
  "database": { "host": "127.0.0.1", "pool": 8 }
}`
const SAMPLE_YAML = `# service config
name: bit_pouch
port: 8080
features:
  - format
  - convert
database:
  host: 127.0.0.1
  pool: 8
`
const SAMPLE_TOML = `# service config
name = "bit_pouch"
port = 8080
features = ["format", "convert"]

[database]
host = "127.0.0.1"
pool = 8
`
const SAMPLE_INI = `; service config
name=bit_pouch

[server]
host=127.0.0.1
port=8080

[database]
pool=8
`
const SAMPLE_ENV = `# service config
export APP_NAME=bit_pouch
PORT=8080
DEBUG=false
`
const SAMPLE_PROPERTIES = `# service config
app.name=bit_pouch
app.port=8080
database.pool=8
`
const run = async (input, options, ctx) => {
  const text = String(input ?? '')
  if (!text.trim()) return failure(new DataError('empty'), ctx.locale)
  if (text.length > MAX_INPUT) return failure(new DataError('tooLarge'), ctx.locale)
  const opts = options
  const { warn, warnings } = makeWarner(ctx.locale)
  const mode = opts.mode === 'fromJson' ? 'fromJson' : 'toJson'
  try {
    if (mode === 'toJson') {
      const requested = opts.format
      const sourceFormat =
        requested === 'auto' || requested === void 0
          ? (detectDataFormat(text) ??
            (() => {
              throw new DataError('unsupportedFormat')
            })())
          : isFormat(requested)
            ? requested
            : (() => {
                throw new DataError('unsupportedFormat')
              })()
      const parsed2 = normalizeValue(await parseSource(sourceFormat, text, warn), warn)
      const output2 = await serializeTarget('json', parsed2, {
        indent: opts.indent ?? '2',
        sortKeys: Boolean(opts.sortKeys),
      })
      return success(output2, {
        sourceFormat,
        targetFormat: 'json',
        keyCount: countKeys(parsed2),
        warnings,
      })
    }
    const parsed = normalizeValue(parseJsonText(text, warn), warn)
    const targetFormat = isFormat(opts.target) ? opts.target : 'yaml'
    const output = await serializeTarget(targetFormat, parsed, opts)
    return success(output, {
      sourceFormat: 'json',
      targetFormat,
      keyCount: countKeys(parsed),
      warnings,
    })
  } catch (error) {
    if (error instanceof DataError) return failure(error, ctx.locale)
    const message = error instanceof Error ? error.message : String(error)
    return { ok: false, error: { code: 'syntaxError', message } }
  }
}
export { SAMPLE_ENV, SAMPLE_INI, SAMPLE_JSON, SAMPLE_PROPERTIES, SAMPLE_TOML, SAMPLE_YAML, detectDataFormat, run }
