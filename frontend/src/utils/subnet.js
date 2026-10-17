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
