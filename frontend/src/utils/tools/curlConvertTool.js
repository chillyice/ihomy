// cURL → fetch / axios / Python / Go / PowerShell。核心逻辑与 bit_pouch src/plugins/curl-convert/run.ts 一致；只做词法解析，不执行命令、不发请求。
// 由 bit_pouch 插件 curl-convert 转写，两仓各自维护测试，不跨仓引用。
var __defProp = Object.defineProperty
var __defNormalProp = (obj, key, value) =>
  key in obj ? __defProp(obj, key, { enumerable: true, configurable: true, writable: true, value }) : (obj[key] = value)
var __publicField = (obj, key, value) => __defNormalProp(obj, typeof key !== 'symbol' ? key + '' : key, value)
const MAX_INPUT = 1e5
const TARGETS = ['fetch', 'axios', 'python', 'go', 'powershell']
const ERROR_TEXT = {
  empty: { zh: '\u5185\u5BB9\u4E3A\u7A7A', en: 'Input is empty' },
  notCurl: { zh: '\u547D\u4EE4\u5FC5\u987B\u4EE5 curl \u5F00\u5934', en: 'The command must start with curl' },
  unterminatedQuote: { zh: '\u5F15\u53F7\u672A\u95ED\u5408', en: 'Unterminated quote' },
  missingValue: { zh: '\u9009\u9879\u7F3A\u5C11\u53C2\u6570', en: 'Option is missing a value' },
  invalidUrl: {
    zh: '\u6CA1\u6709\u627E\u5230 URL\uFF08\u82E5\u547D\u4EE4\u542B\u672A\u77E5\u9009\u9879\uFF0C\u53EF\u80FD\u628A URL \u541E\u6389\u4E86\uFF09',
    en: 'No URL found (an unknown option may have swallowed it)',
  },
  invalidHeader: { zh: '\u5934\u90E8\u7F3A\u5C11\u5192\u53F7', en: 'Header is missing a colon' },
  tooLong: { zh: '\u8F93\u5165\u8D85\u8FC7 100,000 \u5B57\u7B26', en: 'Input exceeds 100,000 characters' },
}
const WARNING_TEXT = {
  unexpandedVariable: {
    zh: '\u53D8\u91CF\u672A\u5C55\u5F00\uFF0C\u4FDD\u7559\u4E3A\u5B57\u9762\u91CF',
    en: 'Variable left unexpanded as a literal',
  },
  multipleUrls: {
    zh: '\u5B58\u5728\u591A\u4E2A URL\uFF0C\u53EA\u4F7F\u7528\u7B2C\u4E00\u4E2A',
    en: 'Multiple URLs found; only the first is used',
  },
  unsupportedScheme: { zh: '\u975E http/https \u534F\u8BAE', en: 'Non-http(s) scheme' },
  unsupportedOption: { zh: '\u9009\u9879\u672A\u652F\u6301\uFF0C\u5DF2\u5FFD\u7565', en: 'Unsupported option ignored' },
  cookieFileUnsupported: {
    zh: 'Cookie \u6587\u4EF6\u5F62\u5F0F\u672A\u652F\u6301\uFF0C\u4EC5\u89E3\u6790 Cookie \u5B57\u7B26\u4E32',
    en: 'Cookie file is not supported; only cookie strings are parsed',
  },
  ignoredOption: { zh: '\u9009\u9879\u5BF9\u751F\u6210\u7684\u4EE3\u7801\u65E0\u5F71\u54CD', en: 'Option has no effect on generated code' },
  ignoredBody: { zh: '\u540C\u65F6\u5B58\u5728 -d \u4E0E -F\uFF0C\u4EE5\u8868\u5355\u4E3A\u51C6', en: 'Both -d and -F present; form wins' },
}
class CurlError extends Error {
  constructor(code, detail, pos) {
    super(code)
    __publicField(this, 'code')
    __publicField(this, 'line')
    __publicField(this, 'column')
    __publicField(this, 'detail')
    this.code = code
    this.detail = detail
    this.line = pos?.line
    this.column = pos?.column
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
function positionOf(text, index) {
  const before = text.slice(0, index)
  const line = before.split(/\r?\n/).length
  const column = index - before.lastIndexOf('\n')
  return { line, column }
}
const DOUBLE_ESCAPES = {
  '"': '"',
  '\\': '\\',
  $: '$',
  '`': '`',
  n: '\n',
  t: '	',
}
function readAnsiC(text, start) {
  let index = start + 2
  let value = ''
  while (index < text.length) {
    const char = text[index]
    if (char === "'") return { value, next: index + 1 }
    if (char !== '\\') {
      value += char
      index += 1
      continue
    }
    const next = text[index + 1]
    if (next === void 0) break
    if (next === 'x' && /^[0-9a-fA-F]{2}$/.test(text.slice(index + 2, index + 4))) {
      value += String.fromCharCode(parseInt(text.slice(index + 2, index + 4), 16))
      index += 4
      continue
    }
    if (next === 'u' && /^[0-9a-fA-F]{4}$/.test(text.slice(index + 2, index + 6))) {
      value += String.fromCharCode(parseInt(text.slice(index + 2, index + 6), 16))
      index += 6
      continue
    }
    if (next >= '0' && next <= '7') {
      const octal = /^[0-7]{1,3}/.exec(text.slice(index + 1))
      if (octal) {
        value += String.fromCharCode(parseInt(octal[0], 8))
        index += 1 + octal[0].length
        continue
      }
    }
    const escapes = { n: '\n', t: '	', r: '\r', a: '\x07', b: '\b', f: '\f', v: '\v', '\\': '\\', "'": "'", '"': '"' }
    value += escapes[next] ?? next
    index += 2
  }
  throw new CurlError('unterminatedQuote', "'", positionOf(text, start))
}
function tokenize(input) {
  const text = input.replace(/\\\r?\n/g, '')
  const tokens = []
  let current = ''
  let tokenStart = -1
  let inToken = false
  let index = 0
  const flush = () => {
    if (inToken) tokens.push({ value: current, start: tokenStart })
    current = ''
    inToken = false
    tokenStart = -1
  }
  while (index < text.length) {
    const char = text[index]
    if (!inToken && /\s/.test(char)) {
      index += 1
      continue
    }
    if (!inToken) {
      inToken = true
      tokenStart = index
    }
    if (char === "'") {
      index += 1
      while (index < text.length && text[index] !== "'") {
        current += text[index]
        index += 1
      }
      if (index >= text.length) throw new CurlError('unterminatedQuote', "'", positionOf(text, tokenStart))
      index += 1
      continue
    }
    if (char === '"') {
      index += 1
      let closed = false
      while (index < text.length) {
        const inner = text[index]
        if (inner === '"') {
          closed = true
          index += 1
          break
        }
        if (inner === '\\') {
          const next = text[index + 1]
          if (next === void 0) break
          if (next === '\n') {
            index += 2
            continue
          }
          if (Object.prototype.hasOwnProperty.call(DOUBLE_ESCAPES, next)) {
            current += DOUBLE_ESCAPES[next]
          } else {
            current += `\\${next}`
          }
          index += 2
          continue
        }
        current += inner
        index += 1
      }
      if (!closed) throw new CurlError('unterminatedQuote', '"', positionOf(text, tokenStart))
      continue
    }
    if (char === '$' && text[index + 1] === "'") {
      const ansi = readAnsiC(text, index)
      current += ansi.value
      index = ansi.next
      continue
    }
    if (char === '\\') {
      const next = text[index + 1]
      if (next !== void 0) {
        current += next
        index += 2
        continue
      }
      current += char
      index += 1
      continue
    }
    if (/\s/.test(char)) {
      flush()
      index += 1
      continue
    }
    current += char
    index += 1
  }
  flush()
  return tokens
}
function hasHeader(headers, name) {
  return headers.some(([headerName]) => headerName.toLowerCase() === name.toLowerCase())
}
function addHeader(headers, name, value) {
  headers.push([name, value])
}
function encodeDataUrl(value) {
  const equals = value.indexOf('=')
  if (equals >= 0) return `${value.slice(0, equals)}=${encodeURIComponent(value.slice(equals + 1))}`
  return encodeURIComponent(value)
}
function parseFormField(value) {
  const equals = value.indexOf('=')
  const name = equals < 0 ? value : value.slice(0, equals)
  let rest = equals < 0 ? '' : value.slice(equals + 1)
  let contentType
  const typeIndex = rest.indexOf(';type=')
  if (typeIndex >= 0) {
    contentType = rest.slice(typeIndex + 6)
    rest = rest.slice(0, typeIndex)
  }
  if (rest.startsWith('@')) return { name, file: rest.slice(1), contentType }
  if (rest.startsWith('<')) return { name, file: rest.slice(1), contentType }
  return { name, value: rest, contentType }
}
const SHORT_FLAGS = /* @__PURE__ */ new Set(['s', 'v', 'i', 'k', 'L', 'G', 'S'])
const SHORT_VALUES = /* @__PURE__ */ new Set(['X', 'H', 'd', 'F', 'u', 'b', 'A', 'e', 'o', 'm'])
function parseCurl(text, warn) {
  const tokens = tokenize(text)
  if (!tokens.length) throw new CurlError('empty')
  if (!['curl', 'curl.exe'].includes(tokens[0].value.toLowerCase())) throw new CurlError('notCurl', tokens[0].value)
  const state = {
    request: {
      method: 'GET',
      url: '',
      headers: [],
      insecure: false,
      followRedirects: false,
      compressed: false,
    },
    dataParts: [],
    getMode: false,
    urls: [],
    form: [],
  }
  for (const token of tokens) {
    if (token.value.includes('$')) {
      const variables = token.value.match(/\$(?:\{([A-Za-z_][A-Za-z0-9_]*)\}|([A-Za-z_][A-Za-z0-9_]*))/g)
      for (const variable of variables ?? []) warn('unexpandedVariable', variable)
    }
  }
  let index = 1
  let endOptions = false
  const takeValue = (option) => {
    index += 1
    const next = tokens[index]
    if (!next) throw new CurlError('missingValue', option)
    return next.value
  }
  const applyLong = (name, inlineValue) => {
    const valueOf = () => (inlineValue !== void 0 ? inlineValue : takeValue(`--${name}`))
    switch (name) {
      case 'url':
        state.urls.push(valueOf())
        break
      case 'request':
        state.explicitMethod = valueOf()
        break
      case 'header': {
        const header = valueOf()
        const colon = header.indexOf(':')
        if (colon < 0) throw new CurlError('invalidHeader', header)
        addHeader(state.request.headers, header.slice(0, colon).trim(), header.slice(colon + 1).trim())
        break
      }
      case 'data':
      case 'data-raw':
      case 'data-binary':
        state.dataParts.push(valueOf())
        break
      case 'data-urlencode':
        state.dataParts.push(encodeDataUrl(valueOf()))
        break
      case 'form':
        state.form.push(parseFormField(valueOf()))
        break
      case 'get':
        state.getMode = true
        break
      case 'user': {
        const user = valueOf()
        const colon = user.indexOf(':')
        state.request.auth = colon < 0 ? { user, password: '' } : { user: user.slice(0, colon), password: user.slice(colon + 1) }
        break
      }
      case 'cookie': {
        const cookie = valueOf()
        if (cookie.includes('=')) state.request.cookies = cookie
        else warn('cookieFileUnsupported', cookie)
        break
      }
      case 'user-agent':
        addHeader(state.request.headers, 'User-Agent', valueOf())
        break
      case 'referer':
        addHeader(state.request.headers, 'Referer', valueOf())
        break
      case 'compressed':
        state.request.compressed = true
        break
      case 'insecure':
        state.request.insecure = true
        break
      case 'location':
        state.request.followRedirects = true
        break
      case 'max-redirs': {
        const maxRedirects = Number(valueOf())
        if (maxRedirects === 0) state.request.followRedirects = false
        break
      }
      case 'connect-timeout':
      case 'max-time':
      case 'retry':
        valueOf()
        break
      case 'proxy':
      case 'cert':
      case 'key':
        valueOf()
        warn('unsupportedOption', `--${name}`)
        break
      case 'output':
        valueOf()
        warn('ignoredOption', '--output')
        break
      case 'include':
        warn('ignoredOption', '--include')
        break
      case 'silent':
      case 'verbose':
        break
      default:
        warn('unsupportedOption', `--${name}`)
    }
  }
  const applyShortValue = (flag, value) => {
    switch (flag) {
      case 'X':
        state.explicitMethod = value
        break
      case 'H': {
        const colon = value.indexOf(':')
        if (colon < 0) throw new CurlError('invalidHeader', value)
        addHeader(state.request.headers, value.slice(0, colon).trim(), value.slice(colon + 1).trim())
        break
      }
      case 'd':
        state.dataParts.push(value)
        break
      case 'F':
        state.form.push(parseFormField(value))
        break
      case 'u': {
        const colon = value.indexOf(':')
        state.request.auth = colon < 0 ? { user: value, password: '' } : { user: value.slice(0, colon), password: value.slice(colon + 1) }
        break
      }
      case 'b':
        if (value.includes('=')) state.request.cookies = value
        else warn('cookieFileUnsupported', value)
        break
      case 'A':
        addHeader(state.request.headers, 'User-Agent', value)
        break
      case 'e':
        addHeader(state.request.headers, 'Referer', value)
        break
      case 'o':
        warn('ignoredOption', '-o')
        break
      case 'm':
        break
    }
  }
  const applyShortFlag = (flag) => {
    switch (flag) {
      case 'G':
        state.getMode = true
        break
      case 'k':
        state.request.insecure = true
        break
      case 'L':
        state.request.followRedirects = true
        break
      case 'i':
        warn('ignoredOption', '-i')
        break
      case 's':
      case 'v':
      case 'S':
        break
    }
  }
  for (index = 1; index < tokens.length; index += 1) {
    const token = tokens[index].value
    if (!token) continue
    if (endOptions || !token.startsWith('-') || token === '-') {
      state.urls.push(token)
      continue
    }
    if (token === '--') {
      endOptions = true
      continue
    }
    if (token.startsWith('--')) {
      const equals = token.indexOf('=')
      const name = (equals < 0 ? token.slice(2) : token.slice(2, equals)).toLowerCase()
      applyLong(name, equals < 0 ? void 0 : token.slice(equals + 1))
      continue
    }
    const chars = token.slice(1)
    for (let cursor = 0; cursor < chars.length; cursor += 1) {
      const flag = chars[cursor]
      if (SHORT_VALUES.has(flag)) {
        const inline = chars.slice(cursor + 1)
        applyShortValue(flag, inline || takeValue(`-${flag}`))
        break
      }
      if (SHORT_FLAGS.has(flag)) {
        applyShortFlag(flag)
        continue
      }
      warn('unsupportedOption', `-${flag}`)
    }
  }
  if (!state.urls.length) throw new CurlError('invalidUrl')
  if (state.urls.length > 1) warn('multipleUrls', state.urls.slice(1).join(', '))
  state.request.url = state.urls[0]
  if (!/^https?:\/\//i.test(state.request.url)) warn('unsupportedScheme', state.request.url.split(':')[0])
  if (state.dataParts.length && state.form.length) warn('ignoredBody')
  if (state.form.length) {
    state.request.form = state.form
  } else if (state.dataParts.length && state.getMode) {
    const separator = state.request.url.includes('?') ? '&' : '?'
    state.request.url += separator + state.dataParts.join('&')
  } else if (state.dataParts.length) {
    state.request.body = state.dataParts.join('&')
  }
  state.request.method = (
    state.explicitMethod ?? (state.getMode ? 'GET' : state.request.body || state.request.form ? 'POST' : 'GET')
  ).toUpperCase()
  if (state.request.compressed && !hasHeader(state.request.headers, 'accept-encoding')) {
    addHeader(state.request.headers, 'Accept-Encoding', 'gzip, deflate, br')
  }
  if (state.request.cookies && !hasHeader(state.request.headers, 'cookie')) {
    addHeader(state.request.headers, 'Cookie', state.request.cookies)
  }
  return state.request
}
function base64Utf8(text) {
  const bytes = new TextEncoder().encode(text)
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/'
  let out = ''
  for (let index = 0; index < bytes.length; index += 3) {
    const first = bytes[index]
    const second = bytes[index + 1] ?? 0
    const third = bytes[index + 2] ?? 0
    out += alphabet[first >> 2]
    out += alphabet[((first & 3) << 4) | (second >> 4)]
    out += index + 1 < bytes.length ? alphabet[((second & 15) << 2) | (third >> 6)] : '='
    out += index + 2 < bytes.length ? alphabet[third & 63] : '='
  }
  return out
}
function authHeader(request) {
  if (!request.auth) return void 0
  return `Basic ${base64Utf8(`${request.auth.user}:${request.auth.password}`)}`
}
function mergeDuplicateHeaders(headers) {
  const merged = []
  for (const [name, value] of headers) {
    const existing = merged.find(([entryName]) => entryName.toLowerCase() === name.toLowerCase())
    if (existing) existing[1] = `${existing[1]}, ${value}`
    else merged.push([name, value])
  }
  return merged
}
function hasDuplicateHeaders(headers) {
  const seen = /* @__PURE__ */ new Set()
  for (const [name] of headers) {
    const lower = name.toLowerCase()
    if (seen.has(lower)) return true
    seen.add(lower)
  }
  return false
}
function jsString(value) {
  return JSON.stringify(value)
}
function jsBody(value) {
  if (!value.includes('\n')) return JSON.stringify(value)
  return `\`${value.replace(/\\/g, '\\\\').replace(/`/g, '\\`').replace(/\$\{/g, '\\${')}\``
}
function pyString(value) {
  if (!value.includes('\n')) return JSON.stringify(value)
  return `"""${value.replace(/\\/g, '\\\\').replace(/"""/g, '\\"\\"\\"')}"""`
}
function psString(value) {
  return `'${value.replace(/'/g, "''")}'`
}
function jsHeaders(headers) {
  if (!headers.length) return '{}'
  if (hasDuplicateHeaders(headers)) {
    return `[
${headers.map(([name, value]) => `    [${jsString(name)}, ${jsString(value)}],`).join('\n')}
  ]`
  }
  return `{
${headers.map(([name, value]) => `    ${jsString(name)}: ${jsString(value)},`).join('\n')}
  }`
}
function pyHeaders(headers) {
  const merged = mergeDuplicateHeaders(headers)
  if (!merged.length) return '{}'
  return `{
${merged.map(([name, value]) => `    ${pyString(name)}: ${pyString(value)},`).join('\n')}
}`
}
function psHeaders(headers) {
  const merged = mergeDuplicateHeaders(headers)
  if (!merged.length) return ''
  return `@{
${merged.map(([name, value]) => `    ${psString(name)} = ${psString(value)}`).join('\n')}
}`
}
function requestHeaders(request) {
  const headers = [...request.headers]
  const authorization = authHeader(request)
  if (authorization && !hasHeader(headers, 'authorization')) headers.push(['Authorization', authorization])
  return headers
}
function generateFetch(request, warn) {
  const headers = requestHeaders(request)
  const lines = []
  if (request.insecure) {
    warn('unsupportedOption', '-k (fetch)')
    lines.push('// curl -k \u5728\u6D4F\u89C8\u5668\u4E2D\u4E0D\u53EF\u7528\uFF1Afetch \u65E0\u6CD5\u5173\u95ED TLS \u6821\u9A8C\u3002')
  }
  if (request.compressed) lines.push('// --compressed\uFF1A\u6D4F\u89C8\u5668\u4F1A\u81EA\u52A8\u534F\u5546\u5E76\u89E3\u538B\u3002')
  let prelude = ''
  let bodyExpression = ''
  if (request.form?.length) {
    prelude =
      'const form = new FormData()\n' +
      request.form
        .map((field) => {
          if (field.file)
            return `// \u6587\u4EF6\u5B57\u6BB5\uFF1A\u5728\u6D4F\u89C8\u5668\u4E2D\u4ECE <input type="file"> \u6216 File \u5BF9\u8C61\u83B7\u53D6
form.append(${jsString(field.name)}, file, ${jsString(field.file)})`
          return `form.append(${jsString(field.name)}, ${jsString(field.value ?? '')})`
        })
        .join('\n')
    bodyExpression = 'form'
  } else if (request.body !== void 0 && !['GET', 'HEAD'].includes(request.method)) {
    bodyExpression = jsBody(request.body)
  }
  lines.push(`const response = await fetch(${jsString(request.url)}, {`)
  lines.push(`  method: ${jsString(request.method)},`)
  if (headers.length) lines.push(`  headers: ${jsHeaders(headers)},`)
  if (bodyExpression) lines.push(`  body: ${bodyExpression},`)
  lines.push(`  redirect: ${request.followRedirects ? "'follow'" : "'manual'"},`)
  lines.push('})')
  lines.push('const data = await response.text()')
  return (
    (prelude
      ? `${prelude}

`
      : '') + lines.join('\n')
  )
}
function generateAxios(request, warn) {
  const headers = [...request.headers]
  const lines = []
  const imports = []
  if (request.form?.length) {
    if (request.form.some((field) => field.file)) imports.push("import fs from 'node:fs'")
    imports.push("import FormData from 'form-data'")
  }
  if (request.insecure) {
    warn('unsupportedOption', '-k (axios)')
    lines.push(
      '// curl -k\uFF1ANode \u4E2D\u53EF\u7528 httpsAgent: new https.Agent({ rejectUnauthorized: false }) \u5173\u95ED\u6821\u9A8C\uFF08\u6709\u98CE\u9669\uFF09\u3002',
    )
  }
  let dataExpression = 'undefined'
  if (request.form?.length) {
    dataExpression = 'form'
    lines.push('const form = new FormData()')
    for (const field of request.form) {
      if (field.file) {
        lines.push(`form.append(${jsString(field.name)}, fs.createReadStream(${jsString(field.file)}))`)
      } else {
        lines.push(`form.append(${jsString(field.name)}, ${jsString(field.value ?? '')})`)
      }
    }
  } else if (request.body !== void 0) {
    dataExpression = jsBody(request.body)
  }
  lines.push('const response = await axios.request({')
  lines.push(`  method: ${jsString(request.method.toLowerCase())},`)
  lines.push(`  url: ${jsString(request.url)},`)
  if (headers.length) lines.push(`  headers: ${jsHeaders(headers)},`)
  if (dataExpression !== 'undefined') lines.push(`  data: ${dataExpression},`)
  if (request.auth) {
    lines.push(`  auth: { username: ${jsString(request.auth.user)}, password: ${jsString(request.auth.password)} },`)
  }
  if (!request.followRedirects) lines.push('  maxRedirects: 0,')
  lines.push('})')
  lines.push('const data = response.data')
  return `${
    imports.length
      ? `${imports.join('\n')}

`
      : ''
  }${lines.join('\n')}`
}
function generatePython(request, warn) {
  const headers = [...request.headers]
  const lines = ['import requests', '']
  if (request.insecure) {
    warn('unsupportedOption', '-k (python)')
    lines.push('# curl -k\uFF1Averify=False \u5173\u95ED\u8BC1\u4E66\u6821\u9A8C\uFF0C\u5B58\u5728\u4E2D\u95F4\u4EBA\u98CE\u9669\u3002')
  }
  if (headers.length) lines.push(`headers = ${pyHeaders(headers)}`, '')
  let dataArgument = ''
  let filesArgument = ''
  if (request.form?.length) {
    const plain = request.form.filter((field) => !field.file)
    const files = request.form.filter((field) => field.file)
    if (plain.length) {
      lines.push(
        `data = {
${plain.map((field) => `    ${pyString(field.name)}: ${pyString(field.value ?? '')},`).join('\n')}
}`,
        '',
      )
      dataArgument = 'data=data,'
    }
    if (files.length) {
      lines.push(
        `files = {
${files.map((field) => `    ${pyString(field.name)}: open(${pyString(field.file ?? '')}, "rb"),`).join('\n')}
}`,
        '',
      )
      filesArgument = 'files=files,'
    }
  } else if (request.body !== void 0) {
    lines.push(`data = ${pyString(request.body)}`, '')
    dataArgument = 'data=data,'
  }
  lines.push('response = requests.request(')
  lines.push(`    ${pyString(request.method)},`)
  lines.push(`    ${pyString(request.url)},`)
  if (headers.length) lines.push('    headers=headers,')
  if (dataArgument) lines.push(`    ${dataArgument}`)
  if (filesArgument) lines.push(`    ${filesArgument}`)
  if (request.auth) lines.push(`    auth=(${pyString(request.auth.user)}, ${pyString(request.auth.password)}),`)
  if (request.insecure) lines.push('    verify=False,')
  if (!request.followRedirects) lines.push('    allow_redirects=False,')
  lines.push(')')
  lines.push('print(response.text)')
  return lines.join('\n')
}
function generateGo(request, warn) {
  const headers = [...request.headers]
  const imports = /* @__PURE__ */ new Set(['fmt', 'io', 'net/http'])
  const lines = []
  const usesForm = Boolean(request.form?.length)
  const usesBody = !usesForm && request.body !== void 0
  if (usesForm) {
    imports.add('bytes')
    imports.add('mime/multipart')
    if (request.form?.some((field) => field.file)) imports.add('os')
  } else if (usesBody) {
    imports.add('strings')
  }
  if (request.insecure) imports.add('crypto/tls')
  if (request.insecure) {
    warn('unsupportedOption', '-k (go)')
    lines.push(
      '	// curl -k\uFF1AInsecureSkipVerify \u5173\u95ED\u8BC1\u4E66\u6821\u9A8C\uFF0C\u5B58\u5728\u4E2D\u95F4\u4EBA\u98CE\u9669\u3002',
    )
  }
  if (usesForm) {
    lines.push('	var buf bytes.Buffer')
    lines.push('	writer := multipart.NewWriter(&buf)')
    for (const field of request.form ?? []) {
      if (field.file) {
        lines.push(`	file, err := os.Open(${jsString(field.file)})`)
        lines.push('	if err != nil {\n		panic(err)\n	}')
        lines.push(`	part, err := writer.CreateFormFile(${jsString(field.name)}, ${jsString(field.file)})`)
        lines.push('	if err != nil {\n		panic(err)\n	}')
        lines.push('	io.Copy(part, file)')
        lines.push('	file.Close()')
      } else {
        lines.push(`	writer.WriteField(${jsString(field.name)}, ${jsString(field.value ?? '')})`)
      }
    }
    lines.push('	writer.Close()')
    lines.push(`	req, err := http.NewRequest(${jsString(request.method)}, ${jsString(request.url)}, &buf)`)
  } else {
    const bodyExpression = usesBody ? `strings.NewReader(${jsString(request.body ?? '')})` : 'nil'
    lines.push(`	req, err := http.NewRequest(${jsString(request.method)}, ${jsString(request.url)}, ${bodyExpression})`)
  }
  lines.push('	if err != nil {\n		panic(err)\n	}')
  for (const [name, value] of headers) lines.push(`	req.Header.Add(${jsString(name)}, ${jsString(value)})`)
  if (request.auth) {
    lines.push(`	req.SetBasicAuth(${jsString(request.auth.user)}, ${jsString(request.auth.password)})`)
  }
  if (usesForm) lines.push('	req.Header.Set("Content-Type", writer.FormDataContentType())')
  lines.push('	client := &http.Client{}')
  if (request.insecure) {
    lines.push('	client.Transport = &http.Transport{TLSClientConfig: &tls.Config{InsecureSkipVerify: true}}')
  }
  if (!request.followRedirects) {
    lines.push('	client.CheckRedirect = func(req *http.Request, via []*http.Request) error {\n		return http.ErrUseLastResponse\n	}')
  }
  lines.push('	resp, err := client.Do(req)')
  lines.push('	if err != nil {\n		panic(err)\n	}')
  lines.push('	defer resp.Body.Close()')
  lines.push('	body, _ := io.ReadAll(resp.Body)')
  lines.push('	fmt.Println(string(body))')
  const importLines = [...imports]
    .sort()
    .map((name) => `	"${name}"`)
    .join('\n')
  return `package main

import (
${importLines}
)

func main() {
${lines.join('\n')}
}`
}
function generatePowerShell(request, warn) {
  const headers = requestHeaders(request)
  const lines = []
  if (request.insecure) {
    warn('unsupportedOption', '-k (powershell)')
    lines.push('# -SkipCertificateCheck \u9700\u8981 PowerShell 7+\u3002')
  }
  if (headers.length) lines.push(`$headers = ${psHeaders(headers)}`, '')
  const arguments_ = [`-Uri ${psString(request.url)}`, `-Method ${psString(request.method)}`]
  if (headers.length) arguments_.push('-Headers $headers')
  if (request.form?.length) {
    const entries = request.form.map((field) =>
      field.file
        ? `    ${psString(field.name)} = Get-Item ${psString(field.file ?? '')}`
        : `    ${psString(field.name)} = ${psString(field.value ?? '')}`,
    )
    lines.push(
      `$form = @{
${entries.join('\n')}
}`,
      '',
    )
    arguments_.push('-Form $form')
  } else if (request.body !== void 0) {
    arguments_.push(`-Body ${psString(request.body)}`)
  }
  if (request.insecure) arguments_.push('-SkipCertificateCheck')
  if (!request.followRedirects) arguments_.push('-MaximumRedirection 0')
  lines.push(`$response = Invoke-WebRequest ${arguments_.join(' ')}`)
  lines.push('$response.Content')
  return lines.join('\n')
}
const GENERATORS = {
  fetch: generateFetch,
  axios: generateAxios,
  python: generatePython,
  go: generateGo,
  powershell: generatePowerShell,
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
    },
  }
}
const SAMPLE_JSON = `curl 'https://api.example.com/v1/login?next=%2Fhome' \\
  -H 'Content-Type: application/json' \\
  -H 'Accept: application/json' \\
  -u 'demo:secret' \\
  --data-raw '{"name":"demo","remember":true}'`
const SAMPLE_FORM = `curl -X POST 'https://api.example.com/v1/upload' \\
  -F 'name=demo' \\
  -F 'file=@./avatar.png' \\
  -b 'sid=abc123'`
const run = (input, options, ctx) => {
  const text = String(input ?? '')
  if (!text.trim()) return failure(new CurlError('empty'), ctx.locale)
  if (text.length > MAX_INPUT) return failure(new CurlError('tooLong'), ctx.locale)
  const { warn, warnings } = makeWarner(ctx.locale)
  try {
    const request = parseCurl(text, warn)
    const target = TARGETS.includes(options.target) ? options.target : 'fetch'
    const output = GENERATORS[target](request, warn)
    return {
      ok: true,
      output,
      data: { request, warnings },
      stats: {
        target,
        method: request.method,
        headers: request.headers.length,
        chars: output.length,
      },
    }
  } catch (error) {
    if (error instanceof CurlError) return failure(error, ctx.locale)
    const message = error instanceof Error ? error.message : String(error)
    return { ok: false, error: { code: 'PLUGIN_ERROR', message } }
  }
}
export { SAMPLE_FORM, SAMPLE_JSON, run }
