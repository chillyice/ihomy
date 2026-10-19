// JSON 格式化工具的纯逻辑:自写 token 解析器,不用 JSON.parse + JSON.stringify。
// 原因:JSON.parse 会把 19 位长整数转成双精度浮点(末尾精度丢失),也会吞掉重复键;
// 这里保留数字与字符串的原始字面量,只重排缩进/换行(可选按键名排序),内容一个字符都不改。
// 错误只返回 code + 行列,文案由页面按 i18n 渲染。

const WHITESPACE = ' \t\n\r'
const NUMBER_RE = /-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?/y
export const JSON_MAX_DEPTH = 200

class JsonSyntaxError extends Error {
  constructor(code, index, char = '') {
    super(code)
    this.code = code
    this.index = index
    this.char = char
  }
}

class JsonParser {
  constructor(text) {
    this.text = text
    this.i = 0
  }

  get len() {
    return this.text.length
  }

  fail(code, index = this.i, char = '') {
    throw new JsonSyntaxError(code, index, char)
  }

  skipWs() {
    while (this.i < this.len && WHITESPACE.includes(this.text[this.i])) this.i++
  }

  parse() {
    this.skipWs()
    if (this.i >= this.len) this.fail('empty')
    const node = this.parseValue(0)
    this.skipWs()
    if (this.i < this.len) this.fail('trailingContent', this.i, this.text[this.i])
    return node
  }

  parseValue(depth) {
    this.skipWs()
    if (this.i >= this.len) this.fail('unexpectedEnd')
    const ch = this.text[this.i]
    if (ch === '{') return this.parseObject(depth + 1)
    if (ch === '[') return this.parseArray(depth + 1)
    if (ch === '"') return { type: 'string', raw: this.parseString() }
    if (ch === '-' || (ch >= '0' && ch <= '9')) return { type: 'number', raw: this.parseNumber() }
    for (const literal of ['true', 'false', 'null']) {
      if (this.text.startsWith(literal, this.i)) {
        this.i += literal.length
        return { type: literal, raw: literal }
      }
    }
    this.fail('unexpectedChar', this.i, ch)
  }

  parseObject(depth) {
    if (depth > JSON_MAX_DEPTH) this.fail('tooDeep')
    this.i++ // {
    const entries = []
    this.skipWs()
    if (this.text[this.i] === '}') {
      this.i++
      return { type: 'object', entries }
    }
    for (;;) {
      this.skipWs()
      if (this.i >= this.len) this.fail('unexpectedEnd')
      if (this.text[this.i] === '}') this.fail('trailingComma', this.i, '}')
      if (this.text[this.i] !== '"') this.fail('expectedKey', this.i, this.text[this.i])
      const key = this.parseString()
      this.skipWs()
      if (this.text[this.i] !== ':') this.fail('expectedColon', this.i, this.text[this.i] || '')
      this.i++
      entries.push({ key, value: this.parseValue(depth) })
      this.skipWs()
      if (this.i >= this.len) this.fail('unexpectedEnd')
      const ch = this.text[this.i]
      if (ch === ',') {
        this.i++
        continue
      }
      if (ch === '}') {
        this.i++
        break
      }
      this.fail('expectedComma', this.i, ch)
    }
    return { type: 'object', entries }
  }

  parseArray(depth) {
    if (depth > JSON_MAX_DEPTH) this.fail('tooDeep')
    this.i++ // [
    const items = []
    this.skipWs()
    if (this.text[this.i] === ']') {
      this.i++
      return { type: 'array', items }
    }
    for (;;) {
      this.skipWs()
      if (this.i >= this.len) this.fail('unexpectedEnd')
      if (this.text[this.i] === ']') this.fail('trailingComma', this.i, ']')
      items.push(this.parseValue(depth))
      this.skipWs()
      if (this.i >= this.len) this.fail('unexpectedEnd')
      const ch = this.text[this.i]
      if (ch === ',') {
        this.i++
        continue
      }
      if (ch === ']') {
        this.i++
        break
      }
      this.fail('expectedComma', this.i, ch)
    }
    return { type: 'array', items }
  }

  // 返回含首尾引号的原始片段:转义写法和 \uXXXX 原样保留
  parseString() {
    const start = this.i
    this.i++ // 开引号
    while (this.i < this.len) {
      const ch = this.text[this.i]
      if (ch === '"') {
        this.i++
        return this.text.slice(start, this.i)
      }
      if (ch === '\\') {
        this.i++
        if (this.i >= this.len) this.fail('unterminatedString', this.i)
        const esc = this.text[this.i]
        if (esc === 'u') {
          if (!/^[0-9a-fA-F]{4}$/.test(this.text.slice(this.i + 1, this.i + 5))) this.fail('invalidEscape', this.i)
          this.i += 5
        } else if ('"\\/bfnrt'.includes(esc)) {
          this.i++
        } else {
          this.fail('invalidEscape', this.i, esc)
        }
        continue
      }
      if (ch === '\n' || ch === '\r' || ch.charCodeAt(0) < 0x20) this.fail('unterminatedString', this.i)
      this.i++
    }
    this.fail('unterminatedString', this.i)
  }

  parseNumber() {
    const start = this.i
    NUMBER_RE.lastIndex = start
    const m = NUMBER_RE.exec(this.text)
    if (!m) this.fail('invalidNumber', start)
    this.i = NUMBER_RE.lastIndex
    return m[0]
  }
}

const decodeKey = (raw) => {
  try {
    return JSON.parse(raw)
  } catch {
    return raw
  }
}

// 按码位比较(不随运行环境语言变化);sort 稳定,重复键保持原相对顺序
const compareKeys = (a, b) => {
  const ka = decodeKey(a.key)
  const kb = decodeKey(b.key)
  return ka < kb ? -1 : ka > kb ? 1 : 0
}

function writeNode(node, indent, depth, sortKeys) {
  const pretty = indent !== ''
  if (node.type === 'object') {
    if (!node.entries.length) return '{}'
    const entries = sortKeys ? [...node.entries].sort(compareKeys) : node.entries
    if (!pretty) return `{${entries.map((e) => `${e.key}:${writeNode(e.value, indent, depth + 1, sortKeys)}`).join(',')}}`
    const inner = indent.repeat(depth + 1)
    const outer = indent.repeat(depth)
    const body = entries.map((e) => `${inner}${e.key}: ${writeNode(e.value, indent, depth + 1, sortKeys)}`).join(',\n')
    return `{\n${body}\n${outer}}`
  }
  if (node.type === 'array') {
    if (!node.items.length) return '[]'
    if (!pretty) return `[${node.items.map((v) => writeNode(v, indent, depth + 1, sortKeys)).join(',')}]`
    const inner = indent.repeat(depth + 1)
    const outer = indent.repeat(depth)
    const body = node.items.map((v) => `${inner}${writeNode(v, indent, depth + 1, sortKeys)}`).join(',\n')
    return `[\n${body}\n${outer}]`
  }
  return node.raw
}

const byteLength = (s) => new TextEncoder().encode(s).length

function describeError(input, err) {
  const pos = Math.min(Math.max(err.index ?? 0, 0), input.length)
  let line = 1
  let lineStart = 0
  for (let i = 0; i < pos; i++) {
    if (input[i] === '\n') {
      line++
      lineStart = i + 1
    }
  }
  const lineEnd = input.indexOf('\n', lineStart)
  const rawLine = input.slice(lineStart, lineEnd === -1 ? input.length : lineEnd).replace(/\r$/, '')
  return {
    code: err.code,
    line,
    column: pos - lineStart + 1,
    char: err.char || '',
    lineText: rawLine.length > 200 ? `${rawLine.slice(0, 200)}…` : rawLine,
  }
}

/**
 * 格式化/压缩 JSON。
 * indent 传 '' 即压缩为单行;sortKeys 递归按键名排序;失败只返回错误码与行列,不抛异常。
 */
export function formatJson(text, { indent = '  ', sortKeys = false } = {}) {
  const input = String(text ?? '')
  if (!input.trim()) {
    return { ok: false, error: { code: 'empty', line: 1, column: 1, char: '', lineText: '' } }
  }
  try {
    const node = new JsonParser(input).parse()
    const output = writeNode(node, indent, 0, sortKeys)
    return {
      ok: true,
      text: output,
      stats: { lines: output.split('\n').length, chars: output.length, bytes: byteLength(output) },
    }
  } catch (err) {
    if (!(err instanceof JsonSyntaxError)) throw err
    return { ok: false, error: describeError(input, err) }
  }
}

/**
 * 把已格式化好的 JSON 切成着色片段:输出是合法 JSON,按词法扫描即可,不对内容做任何改写。
 * 页面用 v-for 渲染这些片段(不走 v-html)。
 */
export function tokenizeJson(text) {
  const tokens = []
  const re = /"(?:[^"\\]|\\.)*"|\b(?:true|false|null)\b|-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?|[{}[\],:]/g
  let last = 0
  let m
  while ((m = re.exec(text))) {
    if (m.index > last) tokens.push({ type: 'plain', text: text.slice(last, m.index) })
    const raw = m[0]
    let type = 'punct'
    if (raw[0] === '"') {
      // 紧跟冒号的字符串是键名,与普通字符串区分上色
      type = /^\s*:/.test(text.slice(re.lastIndex)) ? 'key' : 'string'
    } else if (raw === 'true' || raw === 'false') {
      type = 'boolean'
    } else if (raw === 'null') {
      type = 'null'
    } else if (raw[0] === '-' || (raw[0] >= '0' && raw[0] <= '9')) {
      type = 'number'
    }
    tokens.push({ type, text: raw })
    last = re.lastIndex
  }
  if (last < text.length) tokens.push({ type: 'plain', text: text.slice(last) })
  return tokens
}

// ---------------------------------------------------------------
// 数据转换:JSON ↔ CSV / XML,以及 JSON → TypeScript / Java / SQL 代码生成。
// 转换类函数失败抛带 code 的 JsonConvertError,YAML 由页面按需动态加载 js-yaml。
// ---------------------------------------------------------------

export class JsonConvertError extends Error {
  constructor(code, extra = {}) {
    super(code)
    this.code = code
    Object.assign(this, extra)
  }
}

const isPlainObject = (value) => value !== null && typeof value === 'object' && !Array.isArray(value)

const parseJsonValue = (text) => {
  const input = String(text ?? '')
  if (!input.trim()) throw new JsonConvertError('empty')
  try {
    return JSON.parse(input)
  } catch {
    throw new JsonConvertError('invalidJson')
  }
}

const pascal = (text) =>
  String(text ?? '')
    .split(/[^A-Za-z0-9]+/)
    .filter(Boolean)
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join('') || 'Value'

function flattenValue(value, prefix, out) {
  if (Array.isArray(value)) {
    value.forEach((item, index) => flattenValue(item, prefix ? `${prefix}.${index}` : String(index), out))
    return out
  }
  if (isPlainObject(value)) {
    for (const [key, child] of Object.entries(value)) flattenValue(child, prefix ? `${prefix}.${key}` : key, out)
    return out
  }
  out[prefix || 'value'] = value === null || value === undefined ? '' : String(value)
  return out
}

const csvCell = (value) => {
  const text = String(value ?? '')
  return /[",\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text
}

/** JSON → CSV(嵌套对象/数组用点路径拍平,数组按索引展开) */
export function jsonToCsv(text, { delimiter = ',' } = {}) {
  const value = parseJsonValue(text)
  const rows = (Array.isArray(value) ? value : [value]).map((item) => flattenValue(item, '', {}))
  const headers = []
  for (const row of rows) for (const key of Object.keys(row)) if (!headers.includes(key)) headers.push(key)
  if (!headers.length) throw new JsonConvertError('empty')
  const lines = [headers.map(csvCell).join(delimiter)]
  for (const row of rows) lines.push(headers.map((key) => csvCell(row[key] ?? '')).join(delimiter))
  return lines.join('\n')
}

const parseScalar = (raw) => {
  const text = raw.trim()
  if (text === '') return ''
  if (text === 'null') return null
  if (text === 'true') return true
  if (text === 'false') return false
  if (/^-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?$/.test(text)) return Number(text)
  return raw
}

function setPath(target, path, value) {
  const parts = path.split('.')
  let node = target
  for (let i = 0; i < parts.length - 1; i += 1) {
    const key = parts[i]
    if (!isPlainObject(node[key])) node[key] = {}
    node = node[key]
  }
  node[parts[parts.length - 1]] = value
}

/** CSV / TSV → JSON(首行为键名,点路径还原嵌套;不含键名的列无法表达数组) */
export function csvToJson(text, { delimiter } = {}) {
  const lines = String(text ?? '')
    .replace(/\r\n?/g, '\n')
    .split('\n')
    .filter((line) => line.trim() !== '')
  if (lines.length < 2) throw new JsonConvertError('empty')
  const separators = delimiter ? [delimiter] : [',', '\t']
  const split = (line) => {
    const cells = []
    let current = ''
    let quoted = false
    for (let i = 0; i < line.length; i += 1) {
      const ch = line[i]
      if (quoted) {
        if (ch === '"' && line[i + 1] === '"') {
          current += '"'
          i += 1
        } else if (ch === '"') quoted = false
        else current += ch
      } else if (ch === '"') quoted = true
      else if (separators.includes(ch)) {
        cells.push(current)
        current = ''
      } else current += ch
    }
    cells.push(current)
    return cells
  }
  const headers = split(lines[0]).map((cell) => cell.trim())
  const rows = lines.slice(1).map((line) => {
    const cells = split(line)
    const row = {}
    headers.forEach((header, index) => {
      if (header) setPath(row, header, parseScalar(cells[index] ?? ''))
    })
    return row
  })
  return JSON.stringify(rows, null, 2)
}

const escapeXml = (value) =>
  String(value).replace(/[&<>'"]/g, (ch) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&apos;', '"': '&quot;' })[ch])

function xmlNode(name, value, depth) {
  const pad = '  '.repeat(depth)
  if (Array.isArray(value)) return value.map((item) => xmlNode(name, item, depth)).join('\n')
  if (isPlainObject(value)) {
    const children = Object.entries(value)
      .map(([key, child]) => xmlNode(key, child, depth + 1))
      .filter(Boolean)
      .join('\n')
    return children ? `${pad}<${name}>\n${children}\n${pad}</${name}>` : `${pad}<${name}/>`
  }
  if (value === null || value === undefined) return `${pad}<${name}/>`
  return `${pad}<${name}>${escapeXml(value)}</${name}>`
}

/** JSON → XML(对象/数组递归成元素,数组元素复用父节点名) */
export function jsonToXml(text, { root = 'root' } = {}) {
  const value = parseJsonValue(text)
  if (isPlainObject(value)) {
    return `<?xml version="1.0" encoding="UTF-8"?>\n${xmlNode(root, value, 0)}`
  }
  const body = xmlNode('item', value, 1)
  return `<?xml version="1.0" encoding="UTF-8"?>\n<${root}>\n${body}\n</${root}>`
}

function elementToValue(element) {
  const children = Array.from(element.children || [])
  if (!children.length) return element.textContent ?? ''
  const result = {}
  for (const child of children) {
    const value = elementToValue(child)
    if (result[child.tagName] === undefined) result[child.tagName] = value
    else if (Array.isArray(result[child.tagName])) result[child.tagName].push(value)
    else result[child.tagName] = [result[child.tagName], value]
  }
  return result
}

/** XML → JSON(依赖浏览器 DOMParser;重复同名子节点自动合成数组) */
export function xmlToJson(text, { root = 'root' } = {}) {
  if (typeof DOMParser === 'undefined') throw new JsonConvertError('domUnavailable')
  const input = String(text ?? '')
  if (!input.trim()) throw new JsonConvertError('empty')
  const doc = new DOMParser().parseFromString(input, 'application/xml')
  if (doc.querySelector('parsererror')) throw new JsonConvertError('invalidXml')
  const element = doc.documentElement
  if (!element) throw new JsonConvertError('invalidXml')
  return JSON.stringify({ [root]: elementToValue(element) }, null, 2)
}

const tsType = (value) => {
  if (value === null || value === undefined) return 'unknown'
  if (Array.isArray(value)) return value.length ? `${tsType(value[0])}[]` : 'unknown[]'
  if (isPlainObject(value)) return 'Record<string, unknown>'
  if (typeof value === 'number' || typeof value === 'bigint') return 'number'
  if (typeof value === 'boolean') return 'boolean'
  if (typeof value === 'string') return 'string'
  return 'unknown'
}

/** JSON → TypeScript interface(嵌套对象提升为命名接口,同名不同结构自动加序号) */
export function toTypeScript(text, { rootName = 'Root' } = {}) {
  const value = parseJsonValue(text)
  const defined = new Map()
  const queue = []

  const nameFor = (hint, source) => {
    const base = pascal(hint)
    let name = base
    let index = 2
    while (defined.has(name) && defined.get(name).source !== source) name = `${base}${index++}`
    if (!defined.has(name)) {
      defined.set(name, { source, fields: null })
      queue.push({ name, value: source })
    }
    return name
  }

  const typeOf = (item, hint) => {
    if (Array.isArray(item)) return item.length ? `${typeOf(item[0], `${pascal(hint)}Item`)}[]` : 'unknown[]'
    if (isPlainObject(item)) return nameFor(hint, item)
    return tsType(item)
  }

  let rootType
  if (Array.isArray(value) && isPlainObject(value[0])) {
    rootType = `${typeOf(value[0], `${rootName}Item`)}[]`
  } else if (isPlainObject(value)) {
    rootType = nameFor(rootName, value)
  } else {
    return `export type ${pascal(rootName)} = ${tsType(value)}\n`
  }

  while (queue.length) {
    const { name, value: object } = queue.shift()
    defined.get(name).fields = Object.entries(object).map(([key, child]) => ({
      key,
      type: typeOf(child, key),
    }))
  }

  const blocks = []
  for (const [name, { fields }] of defined) {
    const body = (fields || [])
      .map((field) => `  ${/^[A-Za-z_$][\w$]*$/.test(field.key) ? field.key : JSON.stringify(field.key)}: ${field.type}`)
      .join('\n')
    blocks.push(`export interface ${name} {\n${body}\n}`)
  }
  const head = isPlainObject(value) ? '' : `export type ${pascal(rootName)} = ${rootType}\n\n`
  return `${head}${blocks.join('\n\n')}\n`
}

const javaType = (value, hint, registry) => {
  if (value === null || value === undefined) return 'Object'
  if (Array.isArray(value)) return `List<${value.length ? javaType(value[0], hint, registry) : 'Object'}>`
  if (isPlainObject(value)) {
    const name = pascal(hint)
    registry.set(name, value)
    return name
  }
  if (typeof value === 'boolean') return 'Boolean'
  if (typeof value === 'number') return Number.isInteger(value) && Math.abs(value) < 2 ** 31 ? 'Integer' : 'Double'
  return 'String'
}

/** JSON → Java POJO(嵌套对象生成 static 内部类) */
export function toJava(text, { className = 'Root', packageName = '' } = {}) {
  const value = parseJsonValue(text)
  if (!isPlainObject(value)) throw new JsonConvertError('notObject')
  const rootName = pascal(className)
  const registry = new Map()
  registry.set(rootName, value)
  const bodies = new Map()
  const processed = new Set()
  while (registry.size > processed.size) {
    for (const [name, object] of registry) {
      if (processed.has(name)) continue
      processed.add(name)
      const fields = Object.entries(object).map(([key, child]) => {
        const type = javaType(child, key, registry)
        return `    private ${type} ${/^[A-Za-z_$][\w$]*$/.test(key) ? key : `field_${pascal(key)}`};`
      })
      bodies.set(name, fields)
    }
  }

  const nested = []
  for (const [name, fields] of bodies) {
    if (name === rootName) continue
    const indented = fields.map((line) => `    ${line}`).join('\n')
    nested.push(`    public static class ${name} {\n${indented}\n    }`)
  }
  const rootBody = [...bodies.get(rootName), '', ...nested].join('\n')
  const imports = [...bodies.values()].some((fields) => fields.some((line) => line.includes('List<'))) ? 'import java.util.List;\n\n' : ''
  const header = packageName ? `package ${packageName};\n\n` : ''
  return `${header}${imports}public class ${rootName} {\n${rootBody}\n}\n`
}

/** JSON → SQL INSERT(对象或对象数组,列取并集,字符串转义) */
export function toSqlInsert(text, { table = 'data' } = {}) {
  const value = parseJsonValue(text)
  const rows = (Array.isArray(value) ? value : [value]).filter(isPlainObject)
  if (!rows.length) throw new JsonConvertError('notObject')
  const columns = []
  for (const row of rows) for (const key of Object.keys(row)) if (!columns.includes(key)) columns.push(key)
  const sqlValue = (item) => {
    if (item === null || item === undefined) return 'NULL'
    if (typeof item === 'number') return String(item)
    if (typeof item === 'boolean') return item ? '1' : '0'
    if (typeof item === 'object') return `'${JSON.stringify(item).replace(/'/g, "''")}'`
    return `'${String(item).replace(/'/g, "''")}'`
  }
  const tuples = rows.map((row) => `  (${columns.map((column) => sqlValue(row[column])).join(', ')})`)
  return `INSERT INTO ${table} (${columns.join(', ')})\nVALUES\n${tuples.join(',\n')};\n`
}
