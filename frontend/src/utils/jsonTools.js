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
