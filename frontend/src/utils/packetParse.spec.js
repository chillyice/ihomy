import { describe, it, expect } from 'vitest'
import {
  PacketError,
  asciiOf,
  bigEndianValue,
  checksumSet,
  describeBytes,
  encodeUtf8,
  hexOf,
  parseFieldTemplate,
  parseHexBytes,
  splitFields,
} from './packetParse'

const bytesOf = (...values) => Uint8Array.from(values)

describe('十六进制报文转字节', () => {
  it('接受空格 / 逗号 / 冒号 / 换行分隔与连续写法', () => {
    const expected = [0xaa, 0x55, 0x00, 0x0c]
    expect(Array.from(parseHexBytes('AA 55 00 0C'))).toEqual(expected)
    expect(Array.from(parseHexBytes('AA,55,00,0C'))).toEqual(expected)
    expect(Array.from(parseHexBytes('AA:55:00:0C'))).toEqual(expected)
    expect(Array.from(parseHexBytes('AA55000C'))).toEqual(expected)
    expect(Array.from(parseHexBytes('AA 55\n00 0C'))).toEqual(expected)
  })

  it('接受 0x / \\x 前缀与中文标点分隔', () => {
    const expected = [0xaa, 0x55]
    expect(Array.from(parseHexBytes('0xAA 0x55'))).toEqual(expected)
    expect(Array.from(parseHexBytes('\\xAA\\x55'))).toEqual(expected)
    expect(Array.from(parseHexBytes('AA，55、'))).toEqual(expected)
  })

  it('空内容返回空字节', () => {
    expect(parseHexBytes('   ').length).toBe(0)
  })

  it('非十六进制字符 / 奇数位 / 超长时给出可识别错误', () => {
    expect(() => parseHexBytes('AA GG')).toThrow(PacketError)
    expect(() => parseHexBytes('AA GG')).toThrow(expect.objectContaining({ code: 'invalidHex' }))
    expect(() => parseHexBytes('AAB')).toThrow(expect.objectContaining({ code: 'oddHex' }))
    expect(() => parseHexBytes('AABBCC', 2)).toThrow(expect.objectContaining({ code: 'tooLong', limit: 2 }))
  })

  it('文本按 UTF-8 编码', () => {
    expect(Array.from(encodeUtf8('hi'))).toEqual([0x68, 0x69])
    expect(Array.from(encodeUtf8('你'))).toEqual([0xe4, 0xbd, 0xa0])
  })
})

describe('字段模板', () => {
  it('支持空格 / 冒号 / 逗号分隔与可选单位、注释', () => {
    const { fields, invalid } = parseFieldTemplate('# 协议\n帧头 2\n长度:2\n命令，1 字节\n数据,4 # 载荷')
    expect(invalid).toEqual([])
    expect(fields).toEqual([
      { name: '帧头', length: 2 },
      { name: '长度', length: 2 },
      { name: '命令', length: 1 },
      { name: '数据', length: 4 },
    ])
  })

  it('字段名含空格时按最后一组「分隔符 + 数字」切分', () => {
    const { fields } = parseFieldTemplate('Source Address 2')
    expect(fields).toEqual([{ name: 'Source Address', length: 2 }])
  })

  it('无法识别的行按行号回报,其余行照常解析', () => {
    const { fields, invalid } = parseFieldTemplate('帧头 2\n这一行没有长度\n校验 2')
    expect(fields.map((f) => f.name)).toEqual(['帧头', '校验'])
    expect(invalid).toEqual([{ line: 2, text: '这一行没有长度' }])
  })

  it('合计字节数由字段长度累加', () => {
    expect(parseFieldTemplate('帧头 2\n数据 4\n校验 2').total).toBe(8)
  })
})

describe('按模板切分', () => {
  const bytes = bytesOf(0xaa, 0x55, 0x00, 0x0c, 0x01, 0x03)
  const { fields } = parseFieldTemplate('帧头 2\n长度 2\n命令 1')

  it('长度、偏移与字节内容正确', () => {
    const { rows } = splitFields(bytes, fields)
    expect(rows.map((r) => [r.name, r.offset, r.length])).toEqual([
      ['帧头', 0, 2],
      ['长度', 2, 2],
      ['命令', 4, 1],
    ])
    expect(hexOf(rows[0].bytes)).toBe('AA 55')
    expect(hexOf(rows[1].bytes)).toBe('00 0C')
    expect(rows.every((r) => !r.truncated)).toBe(true)
  })

  it('模板未覆盖的尾部进 rest', () => {
    const { rest } = splitFields(bytes, fields)
    expect(rest.offset).toBe(5)
    expect(hexOf(rest.bytes)).toBe('03')
  })

  it('报文不足时字段标 truncated,不越界', () => {
    const { rows } = splitFields(bytesOf(0xaa, 0x55), fields)
    expect(rows[0]).toMatchObject({ length: 2, truncated: false })
    expect(rows[1]).toMatchObject({ length: 0, truncated: true })
    expect(rows[2]).toMatchObject({ length: 0, truncated: true })
  })

  it('模板为空时全部字节都是剩余数据', () => {
    const { rows, rest } = splitFields(bytes, [])
    expect(rows).toEqual([])
    expect(rest.offset).toBe(0)
    expect(rest.bytes.length).toBe(bytes.length)
  })
})

describe('逐字节拆解', () => {
  it('给出偏移、HEX、十进制、二进制与可见 ASCII', () => {
    const { rows, truncated } = describeBytes(bytesOf(0x41, 0x20, 0x0a))
    expect(rows).toEqual([
      { offset: 0, hex: '41', dec: 65, bin: '01000001', ascii: 'A' },
      { offset: 1, hex: '20', dec: 32, bin: '00100000', ascii: '␠' },
      { offset: 2, hex: '0A', dec: 10, bin: '00001010', ascii: '·' },
    ])
    expect(truncated).toBe(false)
  })

  it('超过上限时截断并标记', () => {
    const { rows, total, truncated } = describeBytes(bytesOf(1, 2, 3), 2)
    expect(rows.length).toBe(2)
    expect(total).toBe(3)
    expect(truncated).toBe(true)
  })

  it('ASCII 串把不可见字节显示为点', () => {
    expect(asciiOf(bytesOf(0x4f, 0x4b, 0x00))).toBe('OK·')
  })
})

describe('字段数值与校验值', () => {
  it('按大端解释字段数值', () => {
    expect(bigEndianValue(bytesOf(0x00, 0x0c))).toBe(12n)
    expect(bigEndianValue(bytesOf(0xff))).toBe(255n)
    expect(bigEndianValue(new Uint8Array(0))).toBeNull()
    expect(bigEndianValue(new Uint8Array(9))).toBeNull()
  })

  it('SUM8 / XOR8 / SUM16 按字节累加', () => {
    const { full } = checksumSet(bytesOf(0x01, 0x02, 0x03))
    expect(full.sum8).toBe('06')
    expect(full.xor8).toBe('00')
    expect(full.sum16).toBe('0006')
  })

  it('CRC16/MODBUS 与标准报文一致(01 03 00 00 00 0A → CDC5)', () => {
    const frame = bytesOf(0x01, 0x03, 0x00, 0x00, 0x00, 0x0a, 0xc5, 0xcd)
    // 校验位正确时,整包再算一遍 CRC 的结果为 0
    expect(checksumSet(frame).full.crc16).toBe('0000')
    expect(checksumSet(frame).body.crc16).toBe('CDC5')
  })

  it('「除末尾 2 字节」口径排除尾部校验位', () => {
    const frame = bytesOf(0x01, 0x03, 0x00, 0x00, 0x00, 0x0a, 0xc5, 0xcd)
    expect(checksumSet(frame).body.sum8).toBe('0E')
    expect(checksumSet(frame.subarray(0, 6)).full.sum8).toBe('0E')
  })
})
