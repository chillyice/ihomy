import { describe, it, expect } from 'vitest'
import {
  SubnetError,
  binaryWithPrefix,
  cidrContains,
  classifyIp,
  expandIpv6,
  intToIpv4,
  ipToBinary,
  ipToString,
  ipv4ToInt,
  ipv6ToString,
  maskToPrefix,
  normalizeMac,
  parseIpv4,
  parseIpv6,
  parseMask,
  prefixToMask,
  subnetInfo,
} from './subnet'

const ip = (text) => parseIpv4(text).value

describe('IPv4 解析与输出', () => {
  it('点分十进制与整数互转', () => {
    expect(ipToString(0xc0a8010a)).toBe('192.168.1.10')
    expect(ip('192.168.1.10')).toBe(0xc0a8010a)
    expect(ip('0.0.0.0')).toBe(0)
    expect(ip('255.255.255.255')).toBe(0xffffffff)
  })

  it('拒绝段数不足、越界与非数字', () => {
    expect(() => parseIpv4('192.168.1')).toThrow(expect.objectContaining({ code: 'ipInvalid' }))
    expect(() => parseIpv4('192.168.1.256')).toThrow(expect.objectContaining({ code: 'ipInvalid' }))
    expect(() => parseIpv4('192.168.1.a')).toThrow(expect.objectContaining({ code: 'ipInvalid' }))
  })
})

describe('子网掩码与前缀', () => {
  it('三种掩码写法等价', () => {
    for (const text of ['24', '/24', '255.255.255.0']) {
      expect(parseMask(text)).toEqual({ prefix: 24, mask: 0xffffff00 })
    }
    expect(parseMask('0')).toEqual({ prefix: 0, mask: 0 })
    expect(parseMask('255.255.255.255')).toEqual({ prefix: 32, mask: 0xffffffff })
  })

  it('前缀与掩码互转', () => {
    expect(prefixToMask(8)).toBe(0xff000000)
    expect(prefixToMask(30)).toBe(0xfffffffc)
    expect(maskToPrefix(0xffff0000)).toBe(16)
    expect(maskToPrefix(0xfffffffc)).toBe(30)
  })

  it('非连续掩码与非法输入分别报错', () => {
    expect(() => parseMask('255.0.255.0')).toThrow(expect.objectContaining({ code: 'maskNonContiguous' }))
    expect(() => parseMask('255.255.300.0')).toThrow(expect.objectContaining({ code: 'maskInvalid' }))
    expect(() => parseMask('33')).toThrow(expect.objectContaining({ code: 'maskInvalid' }))
    expect(() => parseMask('')).toThrow(SubnetError)
    expect(() => parseMask('')).toThrow(expect.objectContaining({ code: 'maskMissing' }))
  })
})

describe('子网信息', () => {
  it('/24 给出网络地址、广播地址与可用范围', () => {
    const info = subnetInfo(ip('192.168.1.10'), 24)
    expect(ipToString(info.network)).toBe('192.168.1.0')
    expect(ipToString(info.broadcast)).toBe('192.168.1.255')
    expect(ipToString(info.first)).toBe('192.168.1.1')
    expect(ipToString(info.last)).toBe('192.168.1.254')
    expect(ipToString(info.wildcard)).toBe('0.0.0.255')
    expect(info.total).toBe(256)
    expect(info.hosts).toBe(254)
  })

  it('/30 只保留两个可用地址', () => {
    const info = subnetInfo(ip('192.168.1.10'), 30)
    expect(ipToString(info.network)).toBe('192.168.1.8')
    expect(ipToString(info.broadcast)).toBe('192.168.1.11')
    expect(info.hosts).toBe(2)
  })

  it('/31 按点对点计算,两个地址都可用', () => {
    const info = subnetInfo(ip('192.168.1.10'), 31)
    expect(ipToString(info.network)).toBe('192.168.1.10')
    expect(ipToString(info.broadcast)).toBe('192.168.1.11')
    expect(ipToString(info.first)).toBe('192.168.1.10')
    expect(ipToString(info.last)).toBe('192.168.1.11')
    expect(info.hosts).toBe(2)
  })

  it('/32 为单机地址', () => {
    const info = subnetInfo(ip('192.168.1.10'), 32)
    expect(ipToString(info.network)).toBe('192.168.1.10')
    expect(ipToString(info.broadcast)).toBe('192.168.1.10')
    expect(info.hosts).toBe(1)
  })

  it('/0 覆盖全部 IPv4 地址', () => {
    const info = subnetInfo(ip('192.168.1.10'), 0)
    expect(ipToString(info.network)).toBe('0.0.0.0')
    expect(ipToString(info.broadcast)).toBe('255.255.255.255')
    expect(ipToString(info.first)).toBe('0.0.0.1')
    expect(ipToString(info.last)).toBe('255.255.255.254')
    expect(info.total).toBe(4294967296)
    expect(info.hosts).toBe(4294967294)
  })

  it('/16 的主机数不含网络位与广播位', () => {
    const info = subnetInfo(ip('10.1.2.3'), 16)
    expect(ipToString(info.network)).toBe('10.1.0.0')
    expect(ipToString(info.broadcast)).toBe('10.1.255.255')
    expect(info.hosts).toBe(65534)
  })
})

describe('地址类型', () => {
  it('识别私网 / CGNAT / 回环 / 链路本地 / 组播 / 保留 / 公网', () => {
    expect(classifyIp(ip('10.1.2.3'))).toBe('PRIVATE')
    expect(classifyIp(ip('172.16.5.5'))).toBe('PRIVATE')
    expect(classifyIp(ip('172.32.0.1'))).toBe('PUBLIC')
    expect(classifyIp(ip('192.168.0.1'))).toBe('PRIVATE')
    expect(classifyIp(ip('100.64.0.1'))).toBe('SHARED')
    expect(classifyIp(ip('127.0.0.1'))).toBe('LOOPBACK')
    expect(classifyIp(ip('169.254.1.1'))).toBe('LINK_LOCAL')
    expect(classifyIp(ip('224.0.0.1'))).toBe('MULTICAST')
    expect(classifyIp(ip('240.0.0.1'))).toBe('RESERVED')
    expect(classifyIp(ip('8.8.8.8'))).toBe('PUBLIC')
  })
})

describe('二进制对照', () => {
  it('按字节分组输出二进制', () => {
    expect(ipToBinary(ip('192.168.1.10'))).toBe('11000000.10101000.00000001.00001010')
  })

  it('在网络位与主机位之间插入竖线', () => {
    expect(binaryWithPrefix(ip('192.168.1.10'), 24)).toBe('11000000.10101000.00000001.|00001010')
    expect(binaryWithPrefix(ip('192.168.1.10'), 8)).toBe('11000000.|10101000.00000001.00001010')
    expect(binaryWithPrefix(ip('192.168.1.10'), 20)).toBe('11000000.10101000.0000|0001.00001010')
    expect(binaryWithPrefix(ip('192.168.1.10'), 0)).toBe('|11000000.10101000.00000001.00001010')
  })
})

describe('IPv4 整数互转', () => {
  it('双向转换', () => {
    expect(ipv4ToInt('8.8.8.8')).toBe(0x08080808)
    expect(intToIpv4(0x08080808)).toBe('8.8.8.8')
    expect(() => intToIpv4(2 ** 32)).toThrow(SubnetError)
  })
})

describe('IPv6', () => {
  it('解析压缩写法并还原', () => {
    expect(parseIpv6('2001:db8::1').text).toBe('2001:db8::1')
    expect(expandIpv6('2001:db8::1')).toBe('2001:0db8:0000:0000:0000:0000:0000:0001')
    expect(parseIpv6('::1').text).toBe('::1')
    expect(parseIpv6('::').text).toBe('::')
  })

  it('内嵌 IPv4 写法', () => {
    expect(parseIpv6('::ffff:192.168.1.1').text).toBe('::ffff:c0a8:101')
  })

  it('最长零段压缩', () => {
    expect(ipv6ToString(0n)).toBe('::')
    expect(ipv6ToString(1n)).toBe('::1')
  })

  it('非法写法报错', () => {
    expect(() => parseIpv6('1::2::3')).toThrow(SubnetError)
    expect(() => parseIpv6('12345::1')).toThrow(SubnetError)
  })
})

describe('CIDR 包含判断', () => {
  it('IPv4 网段', () => {
    expect(cidrContains('192.168.1.0/24', '192.168.1.200')).toBe(true)
    expect(cidrContains('192.168.1.0/24', '192.168.2.1')).toBe(false)
    expect(cidrContains('10.0.0.0/8', '10.255.0.1')).toBe(true)
  })

  it('IPv6 网段', () => {
    expect(cidrContains('2001:db8::/32', '2001:db8::1')).toBe(true)
    expect(cidrContains('2001:db8::/32', '2001:db9::1')).toBe(false)
  })

  it('版本不一致报错', () => {
    expect(() => cidrContains('10.0.0.0/8', '2001:db8::1')).toThrow(SubnetError)
  })
})

describe('MAC 规范化', () => {
  it('四种写法互转', () => {
    const mac = normalizeMac('AA-BB-CC-DD-EE-FF')
    expect(mac.colon).toBe('aa:bb:cc:dd:ee:ff')
    expect(mac.dot).toBe('aabb.ccdd.eeff')
    expect(mac.upper).toBe('AABBCCDDEEFF')
  })

  it('非法输入报错', () => {
    expect(() => normalizeMac('aa:bb:cc')).toThrow(SubnetError)
  })
})
