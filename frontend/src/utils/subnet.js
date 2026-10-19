// 子网掩码计算的纯逻辑:IPv4 解析/输出、掩码与前缀互转、网络地址/广播地址/可用主机范围、
// 地址类型判断与二进制对照。全部基于手写计算,不引第三方库;失败只抛带 code 的 SubnetError。

export class SubnetError extends Error {
  constructor(code) {
    super(code)
    this.name = 'SubnetError'
    this.code = code
  }
}

const UINT32 = 0xffffffff

/** 32 位无符号整数 → 点分十进制 */
export const ipToString = (value) => [24, 16, 8, 0].map((shift) => (value >>> shift) & 0xff).join('.')

/** 点分十进制 → {value, text};每段必须是 0–255 的十进制 */
export function parseIpv4(text) {
  const raw = String(text).trim()
  const parts = raw.split('.')
  if (parts.length !== 4) throw new SubnetError('ipInvalid')
  let value = 0
  for (const part of parts) {
    if (!/^\d{1,3}$/.test(part)) throw new SubnetError('ipInvalid')
    const n = Number(part)
    if (n > 255) throw new SubnetError('ipInvalid')
    value = value * 256 + n
  }
  const unsigned = value >>> 0
  return { value: unsigned, text: ipToString(unsigned) }
}

/** 前缀长度 → 子网掩码(32 位无符号);0 位掩码为 0.0.0.0 */
export const prefixToMask = (prefix) => (prefix === 0 ? 0 : (UINT32 << (32 - prefix)) >>> 0)

/** 子网掩码 → 前缀长度;非连续掩码(如 255.0.255.0)抛 maskNonContiguous */
export function maskToPrefix(mask) {
  let prefix = 0
  while (prefix < 32 && ((mask >>> (31 - prefix)) & 1) === 1) prefix++
  if (prefixToMask(prefix) !== mask) throw new SubnetError('maskNonContiguous')
  return prefix
}

/** 掩码输入 → {prefix, mask}:接受 24、/24、255.255.255.0 三种写法 */
export function parseMask(text) {
  const raw = String(text).trim().replace(/^\//, '')
  if (!raw) throw new SubnetError('maskMissing')
  if (/^\d{1,2}$/.test(raw)) {
    const prefix = Number(raw)
    if (prefix > 32) throw new SubnetError('maskInvalid')
    return { prefix, mask: prefixToMask(prefix) }
  }
  let parsed
  try {
    parsed = parseIpv4(raw)
  } catch {
    throw new SubnetError('maskInvalid')
  }
  return { prefix: maskToPrefix(parsed.value), mask: parsed.value }
}

/** IPv4 地址类型:PRIVATE / SHARED / LOOPBACK / LINK_LOCAL / MULTICAST / RESERVED / PUBLIC */
export function classifyIp(value) {
  const first = value >>> 24
  const second = (value >>> 16) & 0xff
  if (first === 10) return 'PRIVATE'
  if (first === 172 && second >= 16 && second <= 31) return 'PRIVATE'
  if (first === 192 && second === 168) return 'PRIVATE'
  if (first === 100 && second >= 64 && second <= 127) return 'SHARED'
  if (first === 127) return 'LOOPBACK'
  if (first === 169 && second === 254) return 'LINK_LOCAL'
  if (first >= 224 && first <= 239) return 'MULTICAST'
  if (first === 0 || first >= 240) return 'RESERVED'
  return 'PUBLIC'
}

/**
 * 计算子网信息。hosts 与可用范围口径:
 * /0–/30 扣掉网络地址与广播地址;/31 两个地址都可用(点对点);/32 单机地址本身可用。
 */
export function subnetInfo(ipValue, prefix) {
  const mask = prefixToMask(prefix)
  const network = (ipValue & mask) >>> 0
  const wildcard = ~mask >>> 0
  const broadcast = (network | wildcard) >>> 0
  const total = 2 ** (32 - prefix)
  const hosts = prefix >= 31 ? total : total - 2
  return {
    ip: ipValue,
    prefix,
    mask,
    wildcard,
    network,
    broadcast,
    first: prefix >= 31 ? network : network + 1,
    last: prefix >= 31 ? broadcast : broadcast - 1,
    total,
    hosts,
    type: classifyIp(ipValue),
  }
}

/** 32 位整数 → 按字节分组的二进制串,如 11000000.10101000.00000001.00001010 */
export const ipToBinary = (value) => [24, 16, 8, 0].map((shift) => ((value >>> shift) & 0xff).toString(2).padStart(8, '0')).join('.')

/** 二进制串在网络位与主机位之间插入竖线,便于对照前缀长度 */
export function binaryWithPrefix(value, prefix) {
  const groups = 4
  const dots = Math.min(Math.floor(prefix / 8), groups - 1)
  const position = Math.min(prefix + dots, 35)
  const binary = ipToBinary(value)
  return `${binary.slice(0, position)}|${binary.slice(position)}`
}

// ---------------------------------------------------------------
// IPv4 整数互转 / IPv6 解析与压缩 / CIDR 包含判断 / MAC 规范化
// ---------------------------------------------------------------

/** 点分十进制 → 整数 */
export const ipv4ToInt = (text) => parseIpv4(text).value

/** 整数 → 点分十进制;超出 32 位抛 ipInvalid */
export function intToIpv4(value) {
  const number = Number(value)
  if (!Number.isInteger(number) || number < 0 || number > UINT32) throw new SubnetError('ipInvalid')
  return ipToString(number >>> 0)
}

/** IPv6 → 128 位 BigInt;支持 :: 压缩与内嵌 IPv4 */
export function parseIpv6(text) {
  let raw = String(text ?? '')
    .trim()
    .toLowerCase()
  if (!raw || !/^[0-9a-f:.]+$/.test(raw)) throw new SubnetError('ipv6Invalid')
  if (!raw.includes(':')) throw new SubnetError('ipv6Invalid')

  if (raw.includes('.')) {
    const lastColon = raw.lastIndexOf(':')
    const ipv4 = parseIpv4(raw.slice(lastColon + 1))
    raw = `${raw.slice(0, lastColon + 1)}${((ipv4.value >>> 16) & 0xffff).toString(16)}:${(ipv4.value & 0xffff).toString(16)}`
  }

  const double = raw.indexOf('::')
  let groups
  if (double >= 0) {
    if (raw.indexOf('::', double + 1) >= 0) throw new SubnetError('ipv6Invalid')
    const head = raw.slice(0, double)
    const tail = raw.slice(double + 2)
    const headGroups = head ? head.split(':') : []
    const tailGroups = tail ? tail.split(':') : []
    const missing = 8 - headGroups.length - tailGroups.length
    if (missing < 1) throw new SubnetError('ipv6Invalid')
    groups = [...headGroups, ...Array(missing).fill('0'), ...tailGroups]
  } else {
    groups = raw.split(':')
    if (groups.length !== 8) throw new SubnetError('ipv6Invalid')
  }

  let value = 0n
  for (const group of groups) {
    if (!/^[0-9a-f]{1,4}$/.test(group)) throw new SubnetError('ipv6Invalid')
    value = (value << 16n) + BigInt(parseInt(group, 16))
  }
  return { value, text: ipv6ToString(value) }
}

/** 128 位 BigInt → RFC 5952 压缩写法(小写、最长连续零段用 ::) */
export function ipv6ToString(value) {
  const groups = []
  for (let i = 7; i >= 0; i -= 1) groups.push(Number((value >> BigInt(i * 16)) & 0xffffn))
  let bestStart = -1
  let bestLength = 0
  let start = -1
  groups.forEach((group, index) => {
    if (group === 0) {
      if (start < 0) start = index
      return
    }
    if (start >= 0) {
      if (index - start > bestLength) {
        bestLength = index - start
        bestStart = start
      }
      start = -1
    }
  })
  if (start >= 0 && 8 - start > bestLength) {
    bestLength = 8 - start
    bestStart = start
  }
  const hex = groups.map((group) => group.toString(16))
  if (bestLength < 2) return hex.join(':')
  const before = hex.slice(0, bestStart).join(':')
  const after = hex.slice(bestStart + bestLength).join(':')
  return `${before}::${after}`
}

/** 展开成完整 8 段写法 */
export const expandIpv6 = (text) => {
  const { value } = parseIpv6(text)
  const groups = []
  for (let i = 7; i >= 0; i -= 1) groups.push(((value >> BigInt(i * 16)) & 0xffffn).toString(16).padStart(4, '0'))
  return groups.join(':')
}

/** CIDR 是否包含某地址(IPv4 / IPv6 均可,地址类型必须一致) */
export function cidrContains(cidr, address) {
  const raw = String(cidr ?? '').trim()
  const slash = raw.indexOf('/')
  if (slash < 0) throw new SubnetError('cidrInvalid')
  const base = raw.slice(0, slash)
  const prefix = Number(raw.slice(slash + 1))
  const ipv6 = base.includes(':')
  const parse = ipv6 ? (text) => parseIpv6(text).value : (text) => BigInt(parseIpv4(text).value)
  const max = ipv6 ? 128 : 32
  if (!Number.isInteger(prefix) || prefix < 0 || prefix > max) throw new SubnetError('cidrInvalid')
  const candidate = String(address ?? '').trim()
  if (candidate.includes(':') !== ipv6) throw new SubnetError('ipVersionMismatch')
  const network = parse(base)
  const target = parse(candidate)
  const shift = BigInt(max - prefix)
  return network >> shift === target >> shift
}

/** MAC 地址规范化:冒号 / 短横线 / 点分 / 纯十六进制互转 */
export function normalizeMac(text) {
  const raw = String(text ?? '').trim()
  const compact = raw.replace(/[\s:.-]/g, '').toLowerCase()
  if (!/^[0-9a-f]{12}$/.test(compact)) throw new SubnetError('macInvalid')
  const pairs = compact.match(/.{2}/g)
  return {
    lower: compact,
    upper: compact.toUpperCase(),
    colon: pairs.join(':'),
    colonUpper: pairs.join(':').toUpperCase(),
    dash: pairs.join('-'),
    dot: `${pairs.slice(0, 2).join('')}.${pairs.slice(2, 4).join('')}.${pairs.slice(4, 6).join('')}`,
  }
}
