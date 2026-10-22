import { describe, it, expect } from 'vitest'
import {
  PacketError,
  analyzePacket,
  parsePacketInput,
} from './packetDecode'

const bytesOf = (...values) => Uint8Array.from(values)
const layer = (analysis, name) => analysis.layers.find((item) => item.name === name)
const field = (analysis, layerName, fieldName) => layer(analysis, layerName)?.fields.find((item) => item.name === fieldName)

const ETHERNET_TCP = `00 11 22 33 44 55 66 77 88 99 AA BB 08 00
45 00 00 2D 1C 46 40 00 40 06 9C 6C C0 A8 00 01 C0 A8 00 C7
00 50 01 BB 00 00 00 00 00 00 00 00 50 02 20 00 00 00 00 00
68 65 6C 6C 6F`

function ipv4WithPayload(protocol, payload, extra = 0) {
  const total = 20 + payload.length + extra
  return bytesOf(
    0x45, 0x00, total >> 8, total & 0xff,
    0x00, 0x01, 0x40, 0x00,
    0x40, protocol, 0x00, 0x00,
    0x0a, 0x00, 0x00, 0x01,
    0x0a, 0x00, 0x00, 0x02,
    ...payload,
  )
}

describe('报文输入形态识别', () => {
  it('保留输入分行，逐行给出 HEX、ASCII 与偏移', () => {
    const parsed = parsePacketInput('AA 55\n00 0C 01')
    expect(parsed.mode).toBe('hex')
    expect(parsed.kind).toBe('hex')
    expect(parsed.lines.map((line) => [line.offset, line.hex, line.ascii])).toEqual([
      [0, 'AA 55', '·U'],
      [2, '00 0C 01', '···'],
    ])
    expect(Array.from(parsed.bytes)).toEqual([0xaa, 0x55, 0x00, 0x0c, 0x01])
  })

  it('识别连续 HEX 与 0x / \\x 前缀', () => {
    expect(Array.from(parsePacketInput('AA55000C').bytes)).toEqual([0xaa, 0x55, 0x00, 0x0c])
    expect(Array.from(parsePacketInput('0xAA 0x55').bytes)).toEqual([0xaa, 0x55])
    expect(Array.from(parsePacketInput('AA  55   00').bytes)).toEqual([0xaa, 0x55, 0x00])
    expect(Array.from(parsePacketInput('\\xAA\\x55').bytes)).toEqual([0xaa, 0x55])
  })

  it('识别 Wireshark / xxd 风格的带偏移与 ASCII 列转储', () => {
    const wireshark = '0000   45 00 00 28 1c 46 40 00   E..( .F@.\n0010   40 06 9c 6c c0 a8 00 01   @..l....'
    const parsed = parsePacketInput(wireshark)
    expect(parsed.kind).toBe('hexdump')
    expect(parsed.lines.length).toBe(2)
    expect(parsed.lines.map((line) => line.bytes.length)).toEqual([8, 8])
    expect(parsed.lines[1].hex).toBe('40 06 9C 6C C0 A8 00 01')
    const xxd = '00000000: 45 00 00 28 1c 46 40 00  E..(.F@.'
    expect(parsePacketInput(xxd).lines[0].hex).toBe('45 00 00 28 1C 46 40 00')
  })

  it('普通文本按 UTF-8 编码并按行保留', () => {
    const parsed = parsePacketInput('GET / HTTP/1.1\nHost: a', 'auto')
    expect(parsed.mode).toBe('text')
    expect(parsed.lines.map((line) => line.text)).toEqual(['GET / HTTP/1.1', 'Host: a'])
    expect(parsed.lines[1].offset).toBe(14)
  })

  it('显式 HEX 模式下非法 / 奇数位 / 超长给出错误码', () => {
    expect(() => parsePacketInput('AA GG', 'hex')).toThrow(expect.objectContaining({ code: 'invalidHex' }))
    expect(() => parsePacketInput('AAB', 'hex')).toThrow(expect.objectContaining({ code: 'oddHex' }))
    expect(() => parsePacketInput('AABBCC', 'hex', 2)).toThrow(expect.objectContaining({ code: 'tooLong', limit: 2 }))
    expect(() => parsePacketInput('AAB', 'hex')).toThrow(PacketError)
  })
})

describe('网络协议自动识别', () => {
  it('以太网 + IPv4 + TCP + 应用数据', () => {
    const bytes = parsePacketInput(ETHERNET_TCP).bytes
    const analysis = analyzePacket(bytes, 'zh')
    expect(analysis.kind).toBe('ethernet')
    expect(analysis.format).toBe('Ethernet II › IPv4 › TCP › 应用数据')
    expect(layer(analysis, 'Ethernet II')).toBeTruthy()
    expect(field(analysis, 'Ethernet II', '目的 MAC').value).toBe('00:11:22:33:44:55')
    expect(field(analysis, 'IPv4', '源 IP').value).toBe('192.168.0.1')
    expect(field(analysis, 'IPv4', '目的 IP').value).toBe('192.168.0.199')
    expect(field(analysis, 'IPv4', '首部校验和').meaning).toBe('校验通过')
    expect(field(analysis, 'TCP', '源端口').value).toBe('80 · HTTP')
    expect(field(analysis, 'TCP', '目的端口').value).toBe('443 · HTTPS')
    expect(field(analysis, 'TCP', '标志').value).toContain('SYN')
    expect(field(analysis, '应用数据', '负载').value).toContain('hello')
  })

  it('裸 IPv4 + UDP + DNS 查询', () => {
    const dns = bytesOf(
      0x12, 0x34, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0x03, 0x77, 0x77, 0x77, 0x07, 0x65, 0x78, 0x61, 0x6d, 0x70, 0x6c, 0x65, 0x03, 0x63, 0x6f, 0x6d, 0x00,
      0x00, 0x01, 0x00, 0x01,
    )
    const udp = bytesOf(0x30, 0x39, 0x00, 0x35, 0x00, 0x29, 0x00, 0x00, ...dns)
    const analysis = analyzePacket(ipv4WithPayload(17, udp), 'zh')
    expect(analysis.kind).toBe('ipv4')
    expect(analysis.format).toBe('IPv4 › UDP › DNS')
    expect(field(analysis, 'UDP', '目的端口').value).toBe('53 · DNS')
    expect(field(analysis, 'DNS', '问题 1').value).toBe('www.example.com')
    expect(layer(analysis, 'DNS').note).toBe('问题 1 · 回答 0')
  })

  it('裸 IPv4 + ICMP 回显请求', () => {
    const icmp = bytesOf(0x08, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01)
    const analysis = analyzePacket(ipv4WithPayload(1, icmp), 'zh')
    expect(analysis.format).toBe('IPv4 › ICMP')
    expect(field(analysis, 'ICMP', '类型').value).toContain('回显请求')
    expect(field(analysis, 'ICMP', '标识符').value).toBe('1')
  })

  it('ARP 请求字段完整', () => {
    const arp = bytesOf(
      0x00, 0x01, 0x08, 0x00, 0x06, 0x04, 0x00, 0x01,
      0x00, 0x11, 0x22, 0x33, 0x44, 0x55,
      0xc0, 0xa8, 0x00, 0x01,
      0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
      0xc0, 0xa8, 0x00, 0x02,
    )
    const analysis = analyzePacket(arp, 'zh')
    expect(analysis.kind).toBe('arp')
    expect(field(analysis, 'ARP', '操作码').value).toContain('请求')
    expect(field(analysis, 'ARP', '目标 IP').value).toBe('192.168.0.2')
  })

  it('裸 IPv4 + TCP + HTTP 请求', () => {
    const http = new TextEncoder().encode('GET / HTTP/1.1\r\nHost: example.com\r\n\r\n')
    const tcp = bytesOf(
      0x30, 0x39, 0x00, 0x50,
      0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00,
      0x50, 0x18, 0x20, 0x00, 0x00, 0x00, 0x00, 0x00,
      ...http,
    )
    const analysis = analyzePacket(ipv4WithPayload(6, tcp), 'zh')
    expect(analysis.format).toBe('IPv4 › TCP › HTTP')
    expect(field(analysis, 'HTTP', '请求行').value).toBe('GET / HTTP/1.1')
    expect(field(analysis, 'HTTP', 'Host').value).toBe('example.com')
  })

  it('IPv6 + UDP 自动识别地址压缩', () => {
    const udp = bytesOf(0x30, 0x39, 0x00, 0x35, 0x00, 0x08, 0x00, 0x00)
    const ipv6 = bytesOf(
      0x60, 0x00, 0x00, 0x00, 0x00, 0x08, 0x11, 0x40,
      0x20, 0x01, 0x0d, 0xb8, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01,
      0x20, 0x01, 0x0d, 0xb8, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x02,
      ...udp,
    )
    const analysis = analyzePacket(ipv6, 'zh')
    expect(analysis.kind).toBe('ipv6')
    expect(analysis.format).toBe('IPv6 › UDP')
    expect(field(analysis, 'IPv6', '源地址').value).toBe('2001:db8::1')
    expect(field(analysis, 'IPv6', '目的地址').value).toBe('2001:db8::2')
  })

  it('IPv6 + ICMPv6 不做错误的伪首部校验', () => {
    const icmp = bytesOf(0x80, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01)
    const ipv6 = bytesOf(
      0x60, 0x00, 0x00, 0x00, 0x00, 0x08, 0x3a, 0x40,
      0x20, 0x01, 0x0d, 0xb8, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01,
      0x20, 0x01, 0x0d, 0xb8, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x02,
      ...icmp,
    )
    const analysis = analyzePacket(ipv6, 'zh')
    expect(analysis.format).toBe('IPv6 › ICMPv6')
    expect(field(analysis, 'ICMPv6', '校验和').meaning).toContain('伪首部')
  })
  it('802.1Q VLAN 标签后继续解析 IPv4', () => {
    const frame = bytesOf(
      0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, 0x99, 0xaa, 0xbb,
      0x81, 0x00, 0xa0, 0x64, 0x08, 0x00,
      0x45, 0x00, 0x00, 0x14, 0x00, 0x00, 0x40, 0x00, 0x40, 0x01, 0x00, 0x00, 0x0a, 0x00, 0x00, 0x01, 0x0a, 0x00, 0x00, 0x02,
    )
    const analysis = analyzePacket(frame, 'zh')
    expect(analysis.format).toBe('Ethernet II › 802.1Q VLAN › IPv4')
    expect(field(analysis, '802.1Q VLAN', '优先级与 VLAN ID').value).toBe('PCP=5 DEI=0 VID=100')
  })

  it('未识别格式回退为原始字节并给出提示', () => {
    const analysis = analyzePacket(bytesOf(0xde, 0xad, 0xbe, 0xef), 'zh')
    expect(analysis.kind).toBe('unknown')
    expect(analysis.format).toBe('原始数据')
    expect(analysis.warnings.length).toBe(1)
    expect(layer(analysis, '原始数据').fields[0].value).toBe('4 B')
  })

  it('截断的 Ethernet 报文不抛异常', () => {
    const analysis = analyzePacket(bytesOf(0x00, 0x11, 0x22), 'zh')
    expect(analysis.format).toBe('原始数据')
  })

  it('英文文案随 locale 切换', () => {
    const analysis = analyzePacket(parsePacketInput(ETHERNET_TCP).bytes, 'en')
    expect(field(analysis, 'Ethernet II', 'Destination MAC').value).toBe('00:11:22:33:44:55')
    expect(field(analysis, 'IPv4', 'Header checksum').meaning).toBe('Checksum valid')
  })
})
