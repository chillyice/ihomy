import { describe, expect, it } from 'vitest'
import { formatMarkup, MARKUP_MAX_INPUT } from './markupTool'

describe('XML 格式化', () => {
  it('按层级展开，自闭合标签补空格', () => {
    const result = formatMarkup('<root><item id="1">a</item><item id="2"/></root>', { mode: 'xml' })
    expect(result.ok).toBe(true)
    if (!result.ok) return
    expect(result.text).toBe('<root>\n  <item id="1">a</item>\n  <item id="2" />\n</root>')
    expect(result.stats.tags).toBe(3)
  })

  it('保留注释、CDATA、DOCTYPE 与处理指令', () => {
    const input = '<?xml version="1.0"?><!DOCTYPE note [<!ENTITY x "y">]><note><!-- c --><![CDATA[a < b]]><to>Tove</to></note>'
    const result = formatMarkup(input, { mode: 'xml', indent: '    ' })
    expect(result.ok).toBe(true)
    if (!result.ok) return
    expect(result.text).toBe(
      '<?xml version="1.0"?>\n<!DOCTYPE note [<!ENTITY x "y">]>\n<note>\n    <!-- c -->\n    <![CDATA[a < b]]>\n    <to>Tove</to>\n</note>',
    )
  })

  it('混合文本保持行内', () => {
    const result = formatMarkup('<p>Hello <b>world</b>!</p>', { mode: 'xml' })
    expect(result.ok && result.text).toBe('<p>Hello <b>world</b>!</p>')
  })

  it('标签错配返回行列', () => {
    const result = formatMarkup('<root>\n  <a>\n</root>', { mode: 'xml' })
    expect(result.ok).toBe(false)
    if (result.ok) return
    expect(result.error.code).toBe('mismatchedTag')
    expect(result.error.line).toBe(3)
  })
})

describe('HTML 格式化', () => {
  it('识别空元素并保持行内内容', () => {
    const input = '<!DOCTYPE html><html><body><h1>Hi</h1><p>Hello <b>world</b>!</p><br><img src="a>b.png" alt="x"></body></html>'
    const result = formatMarkup(input, { mode: 'html' })
    expect(result.ok).toBe(true)
    if (!result.ok) return
    expect(result.text).toBe(
      '<!DOCTYPE html>\n<html>\n  <body>\n    <h1>Hi</h1>\n    <p>Hello <b>world</b>!</p>\n    <br>\n    <img src="a>b.png" alt="x">\n  </body>\n</html>',
    )
  })

  it('容错省略闭合标签', () => {
    const result = formatMarkup('<ul><li>a<li>b</ul><p>x<div>y</div>', { mode: 'html' })
    expect(result.ok).toBe(true)
    if (!result.ok) return
    expect(result.text).toBe('<ul>\n  <li>a</li>\n  <li>b</li>\n</ul>\n<p>x</p>\n<div>y</div>')
  })

  it('script 内容原样保留', () => {
    const input = '<script>if (a < b) { console.log("</div>") }</script>'
    expect(formatMarkup(input, { mode: 'html' }).ok && formatMarkup(input, { mode: 'html' }).text).toBe(input)
  })
})

describe('压缩与推断', () => {
  it('XML 去掉格式化空白', () => {
    const result = formatMarkup('<root>\n  <a> 1 </a>\n  <b/>\n</root>', { mode: 'xml', minify: true })
    expect(result.ok && result.text).toBe('<root><a> 1 </a><b /></root>')
  })

  it('HTML 保留行内空格', () => {
    const result = formatMarkup('<div>\n  <p><span>a</span> <span>b</span></p>\n</div>', { mode: 'html', minify: true })
    expect(result.ok && result.text).toBe('<div><p><span>a</span> <span>b</span></p></div>')
  })

  it('自动识别 XML 声明与 HTML DOCTYPE', () => {
    const xml = formatMarkup('<?xml version="1.0"?><root/>')
    expect(xml.ok && xml.mode).toBe('xml')
    const html = formatMarkup('<!DOCTYPE html><div><br></div>')
    expect(html.ok && html.mode).toBe('html')
  })

  it('空输入与超限输入返回错误', () => {
    expect(formatMarkup('   ').ok).toBe(false)
    const big = formatMarkup('a'.repeat(MARKUP_MAX_INPUT + 1))
    expect(big.ok).toBe(false)
    if (big.ok) return
    expect(big.error.code).toBe('tooLarge')
  })
})
