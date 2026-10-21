// UUID 生成与解析：空输入批量生成 v4，非空输入解析版本与变体。核心逻辑与 bit_pouch src/plugins/uuid/run.ts 一致；随机源优先 Web Crypto。
// 由 bit_pouch 插件 uuid 转写，两仓各自维护测试，不跨仓引用。
const MAX_COUNT = 100
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
const MESSAGES = {
  invalidCount: {
    zh: `\u751F\u6210\u6570\u91CF\u5FC5\u987B\u662F 1\u2013${MAX_COUNT} \u7684\u6574\u6570`,
    en: `Count must be an integer between 1 and ${MAX_COUNT}`,
  },
  invalidUuid: {
    zh: '\u4E0D\u662F\u5408\u6CD5\u7684 UUID\uFF088-4-4-4-12 \u5341\u516D\u8FDB\u5236\uFF09',
    en: 'Not a valid UUID (8-4-4-4-12 hex)',
  },
}
function fail(code, locale) {
  return { ok: false, error: { code, message: MESSAGES[code][locale] } }
}
function randomBytes(length) {
  const bytes = new Uint8Array(length)
  const cryptoObject = globalThis.crypto
  if (cryptoObject?.getRandomValues) {
    cryptoObject.getRandomValues(bytes)
    return bytes
  }
  for (let i = 0; i < length; i += 1) bytes[i] = Math.floor(Math.random() * 256)
  return bytes
}
function uuidV4() {
  const bytes = randomBytes(16)
  bytes[6] = (bytes[6] & 15) | 64
  bytes[8] = (bytes[8] & 63) | 128
  const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}
function parseUuid(raw) {
  const value = raw.trim()
  if (!UUID_RE.test(value)) return null
  const version = Number.parseInt(value[14], 16)
  const variantBits = Number.parseInt(value[19], 16)
  let variant = 'Future'
  if ((variantBits & 8) === 0) variant = 'NCS'
  else if ((variantBits & 12) === 8) variant = 'RFC 4122'
  else if ((variantBits & 14) === 12) variant = 'Microsoft'
  return { version, variant, normalized: value.toLowerCase() }
}
function formatUuid(uuid, uppercase, hyphens) {
  const value = hyphens ? uuid : uuid.replace(/-/g, '')
  return uppercase ? value.toUpperCase() : value.toLowerCase()
}
const run = (input, options, ctx) => {
  const raw = String(input ?? '').trim()
  const uppercase = options.uppercase === true
  const hyphens = options.hyphens !== false
  if (raw) {
    const parsed = parseUuid(raw)
    if (!parsed) return fail('invalidUuid', ctx.locale)
    return {
      ok: true,
      output: parsed.normalized,
      stats: { version: parsed.version, variant: parsed.variant },
      data: { mode: 'parse', uuids: [parsed.normalized], parsed, uppercase, hyphens },
    }
  }
  const requested = Number(options.count ?? 1)
  if (!Number.isInteger(requested) || requested < 1 || requested > MAX_COUNT) return fail('invalidCount', ctx.locale)
  const uuids = Array.from({ length: requested }, () => formatUuid(uuidV4(), uppercase, hyphens))
  return {
    ok: true,
    output: uuids.join('\n'),
    stats: { count: uuids.length, version: 4, variant: 'RFC 4122' },
    data: { mode: 'generate', uuids, parsed: null, uppercase, hyphens },
  }
}
export { MAX_COUNT, parseUuid, run, uuidV4 }
