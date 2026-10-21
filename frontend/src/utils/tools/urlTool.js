// URL 解析：拆解协议、主机、端口、路径、片段与查询参数。核心逻辑与 bit_pouch src/plugins/url/run.ts 一致；只在本机解析，不发请求。
// 由 bit_pouch 插件 url 转写，两仓各自维护测试，不跨仓引用。
const MAX_INPUT_LENGTH = 1e5
const MESSAGES = {
  empty: { zh: '\u8BF7\u8F93\u5165 URL', en: 'Enter a URL' },
  tooLong: { zh: `\u8F93\u5165\u8D85\u8FC7 ${MAX_INPUT_LENGTH} \u4E2A\u5B57\u7B26`, en: `Input exceeds ${MAX_INPUT_LENGTH} characters` },
  invalidUrl: {
    zh: '\u4E0D\u662F\u5408\u6CD5\u7684\u7EDD\u5BF9 URL\uFF08\u9700\u8981\u5305\u542B\u534F\u8BAE\uFF0C\u5982 https://\uFF09',
    en: 'Not a valid absolute URL (include a scheme such as https://)',
  },
}
function fail(code, locale) {
  return { ok: false, error: { code, message: MESSAGES[code][locale] } }
}
function safeDecode(value) {
  try {
    return decodeURIComponent(value.replace(/\+/g, ' '))
  } catch {
    return value
  }
}
function parseQuery(search) {
  const source = search.replace(/^\?/, '')
  if (!source) return []
  return source
    .split('&')
    .filter((pair) => pair.length > 0)
    .map((pair) => {
      const separator = pair.indexOf('=')
      const rawKey = separator === -1 ? pair : pair.slice(0, separator)
      const rawValue = separator === -1 ? '' : pair.slice(separator + 1)
      return { key: safeDecode(rawKey), value: safeDecode(rawValue), rawKey, rawValue }
    })
}
const run = (input, _options, ctx) => {
  const text = String(input ?? '').trim()
  if (!text) return fail('empty', ctx.locale)
  if (text.length > MAX_INPUT_LENGTH) return fail('tooLong', ctx.locale)
  let url
  try {
    url = new URL(text)
  } catch {
    return fail('invalidUrl', ctx.locale)
  }
  const data = {
    href: url.href,
    protocol: url.protocol,
    username: url.username,
    password: url.password,
    host: url.host,
    hostname: url.hostname,
    port: url.port,
    pathname: url.pathname,
    search: url.search,
    hash: url.hash,
    origin: url.origin,
    params: parseQuery(url.search),
  }
  return {
    ok: true,
    output: JSON.stringify(data, null, 2),
    stats: { protocol: url.protocol, host: url.host || '\u2014', params: data.params.length },
    data,
  }
}
export { MAX_INPUT_LENGTH, parseQuery, run }
