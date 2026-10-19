import { describe, it, expect } from 'vitest'
import {
  CODEC_FORMATS,
  buildReverseMap,
  decodeText,
  describeChars,
  fromBase64,
  fromHtmlEntities,
  fromUnicodeEscapes,
  fromUrlEncoded,
  fromUtf8Hex,
  repairMojibake,
  toBase64,
  toHtmlEntities,
  toUnicodeEscapes,
  toUrlEncoded,
  toUtf8Hex,
} from './textCodec'

describe('Unicode 转义', () => {
  it('只转非 ASCII,BMP 字符用 \\uXXXX', () => {
    expect(toUnicodeEscapes('Hi你好')).toBe('Hi\\u4F60\\u597D')
  })

  it('emoji 输出 UTF-16 代理对', () => {
    expect(toUnicodeEscapes('😀')).toBe('\\uD83D\\uDE00')
  })

  it('解码支持 \\uXXXX / \\u{XXXXX} / U+XXXX 三种写法', () => {
    expect(fromUnicodeEscapes('Hi\\u4F60\\u597D')).toBe('Hi你好')
    expect(fromUnicodeEscapes('\\u{1F600}')).toBe('😀')
    expect(fromUnicodeEscapes('U+4F60 U+597D')).toBe('你好')
    expect(fromUnicodeEscapes('\\uD83D\\uDE00')).toBe('😀')
  })

  it('往返一致(含非 BMP 与 ASCII 混排)', () => {
    const text = 'a你😀好 z'
    expect(fromUnicodeEscapes(toUnicodeEscapes(text))).toBe(text)
  })

  it('不符合模式的内容原样保留', () => {
    expect(fromUnicodeEscapes('plain text')).toBe('plain text')
    expect(fromUnicodeEscapes('\\uZZZZ')).toBe('\\uZZZZ')
  })
})

describe('HTML 实体', () => {
  it('特殊字符用命名实体,非 ASCII 用十进制实体', () => {
    expect(toHtmlEntities('<a href="x">你</a>')).toBe('&lt;a href=&quot;x&quot;&gt;&#20320;&lt;/a&gt;')
    expect(toHtmlEntities("Tom & Jerry's")).toBe('Tom &amp; Jerry&#39;s')
  })

  it('解码支持十进制、十六进制与命名实体', () => {
    expect(fromHtmlEntities('&#20320;&#x597D;')).toBe('你好')
    expect(fromHtmlEntities('&lt;&amp;&gt;&quot;&apos;&nbsp;')).toBe('<&>"\'\u00a0')
  })

  it('未知实体原样保留', () => {
    expect(fromHtmlEntities('&unknown;')).toBe('&unknown;')
  })

  it('往返一致', () => {
    const text = '<p>你好 & "ihomy"</p>'
    expect(fromHtmlEntities(toHtmlEntities(text))).toBe(text)
  })
})

describe('URL 与 Base64 / 十六进制字节', () => {
  it('URL 编码往返一致(中文、空格、符号)', () => {
    const text = '你好 ihomy&x=1/2?'
    expect(toUrlEncoded(text)).toBe('%E4%BD%A0%E5%A5%BD%20ihomy%26x%3D1%2F2%3F')
    expect(fromUrlEncoded(toUrlEncoded(text))).toBe(text)
  })

  it('非法 URL 编码返回错误码', () => {
    expect(decodeText('url', '%E4%BD')).toEqual({ ok: false, error: 'invalidUrl' })
  })

  it('Base64 按 UTF-8 编码,可省略末尾等号', () => {
    expect(toBase64('你好, ihomy')).toBe('5L2g5aW9LCBpaG9teQ==')
    expect(fromBase64('5L2g5aW9LCBpaG9teQ')).toBe('你好, ihomy')
    expect(fromBase64('8J+YgA==')).toBe('😀')
  })

  it('非法 Base64 返回错误码', () => {
    expect(decodeText('base64', 'not base64!')).toEqual({ ok: false, error: 'invalidBase64' })
    expect(decodeText('base64', 'a')).toEqual({ ok: false, error: 'invalidBase64' })
  })

  it('UTF-8 字节十六进制往返一致,兼容多种分隔写法', () => {
    expect(toUtf8Hex('Hi你')).toBe('48 69 E4 BD A0')
    expect(fromUtf8Hex('48 69 E4 BD A0')).toBe('Hi你')
    expect(fromUtf8Hex('0x48,0x69')).toBe('Hi')
    expect(fromUtf8Hex('4869')).toBe('Hi')
    expect(fromUtf8Hex('\\x48\\x69')).toBe('Hi')
  })

  it('非法十六进制返回错误码', () => {
    expect(decodeText('hex', '4G')).toEqual({ ok: false, error: 'invalidHex' })
    expect(decodeText('hex', '464')).toEqual({ ok: false, error: 'invalidHex' })
  })
})

describe('decodeText 分发', () => {
  it('五种格式都能往返', () => {
    const text = 'a你😀'
    const encoded = {
      unicode: toUnicodeEscapes(text),
      html: toHtmlEntities(text),
      url: toUrlEncoded(text),
      base64: toBase64(text),
      hex: toUtf8Hex(text),
    }
    for (const format of CODEC_FORMATS) {
      expect(decodeText(format, encoded[format])).toEqual({ ok: true, text })
    }
  })
})

describe('describeChars', () => {
  it('逐字给出码位、十进制、UTF-8 与实体', () => {
    const { items, total, truncated } = describeChars('A你😀')
    expect(total).toBe(3)
    expect(truncated).toBe(false)
    expect(items[0]).toMatchObject({ char: 'A', codePoint: 'U+0041', dec: 65, utf8: '41', entity: '—' })
    expect(items[1]).toMatchObject({ char: '你', codePoint: 'U+4F60', dec: 20320, utf8: 'E4 BD A0', entity: '&#20320;' })
    expect(items[2]).toMatchObject({ codePoint: 'U+1F600', dec: 128512, utf8: 'F0 9F 98 80', entity: '&#128512;' })
  })

  it('控制字符与空格用可见符号代替', () => {
    const { items } = describeChars('\n ')
    expect(items[0].char).toBe('⏎')
    expect(items[1].char).toBe('␠')
  })

  it('超出上限时截断并标记', () => {
    const { items, total, truncated } = describeChars('abcdef', 3)
    expect(items).toHaveLength(3)
    expect(total).toBe(6)
    expect(truncated).toBe(true)
  })
})

describe('乱码修复', () => {
  it('Windows-1252 被当成 UTF-8 显示后可还原', () => {
    const garbled = 'ä½\u00a0å¥½'
    const [best] = repairMojibake(garbled)
    expect(best.text).toBe('你好')
    expect(best.label).toContain('Windows-1252')
  })

  it('GBK 显示乱码可还原', () => {
    const results = repairMojibake('浣犲ソ')
    expect(results.some((item) => item.text === '你好')).toBe(true)
  })

  it('正常文本不会给出离谱的候选', () => {
    const results = repairMojibake('hello world')
    expect(results.every((item) => item.text !== 'hello world')).toBe(true)
  })

  it('反查表可复用', () => {
    const map = buildReverseMap('gbk')
    expect(map.get('浣')).toEqual([0xe4, 0xbd])
  })
})
