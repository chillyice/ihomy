import { describe, it, expect } from 'vitest'
import { formatAny } from './formatTool'
import { run as runUrl } from './urlTool'
import { run as runUuid } from './uuidTool'

const ctx = { locale: 'en' }

describe('format 在线逻辑（JSON / XML / HTML 合并入口）', () => {
  it('自动识别 JSON 并格式化', () => {
    const result = formatAny('{"b":1,"a":[1,2]}', { mode: 'auto' })
    expect(result.ok).toBe(true)
    expect(result.mode).toBe('json')
    expect(result.output).toContain('\n  "b": 1')
  })

  it('自动识别 XML 并格式化', () => {
    const result = formatAny('<?xml version="1.0"?><root><item id="1">a</item></root>', { mode: 'auto' })
    expect(result.ok).toBe(true)
    expect(result.mode).toBe('xml')
    expect(result.output).toContain('\n  <item id="1">a</item>')
  })

  it('HTML 容错格式化', () => {
    const result = formatAny('<div><br><span>x</span></div>', { mode: 'html' })
    expect(result.ok).toBe(true)
    expect(result.mode).toBe('html')
    expect(result.output).toContain('<br>')
  })

  it('JSON 键名排序与压缩', () => {
    const result = formatAny('{"b":1,"a":2}', { mode: 'json', sortKeys: true })
    expect(result.ok).toBe(true)
    expect(result.output.indexOf('"a"')).toBeLessThan(result.output.indexOf('"b"'))

    const minified = formatAny('{"b":1,"a":2}', { mode: 'json', minify: true })
    expect(minified.ok).toBe(true)
    expect(minified.output).toBe('{"b":1,"a":2}')
  })

  it('错误按格式返回 kind 与行列', () => {
    const json = formatAny('{"a":}', { mode: 'json' })
    expect(json.ok).toBe(false)
    expect(json.kind).toBe('json')
    expect(json.error.code).toBeTruthy()

    const xml = formatAny('<?xml version="1.0"?><root><a></root>', { mode: 'xml' })
    expect(xml.ok).toBe(false)
    expect(xml.kind).toBe('markup')
    expect(xml.error.code).toBe('mismatchedTag')
  })
})

describe('url 在线逻辑', () => {
  it('拆解组件并保留重复查询参数', () => {
    const result = runUrl('https://example.com:8443/a/b?tag=x&tag=y&q=%E4%B8%AD#top', {}, ctx)
    expect(result.ok).toBe(true)
    expect(result.data.protocol).toBe('https:')
    expect(result.data.port).toBe('8443')
    expect(result.data.pathname).toBe('/a/b')
    expect(result.data.hash).toBe('#top')
    expect(result.data.params).toEqual([
      { key: 'tag', value: 'x', rawKey: 'tag', rawValue: 'x' },
      { key: 'tag', value: 'y', rawKey: 'tag', rawValue: 'y' },
      { key: 'q', value: '中', rawKey: 'q', rawValue: '%E4%B8%AD' },
    ])
  })

  it('空输入与非法 URL 返回错误码', () => {
    expect(runUrl('', {}, ctx).error.code).toBe('empty')
    expect(runUrl('not a url', {}, ctx).error.code).toBe('invalidUrl')
  })
})

describe('uuid 在线逻辑', () => {
  it('批量生成 v4', () => {
    const result = runUuid('', { count: 3 }, ctx)
    expect(result.ok).toBe(true)
    expect(result.data.uuids).toHaveLength(3)
    for (const uuid of result.data.uuids) {
      expect(uuid).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/)
    }
  })

  it('解析版本与变体', () => {
    const result = runUuid('550e8400-e29b-41d4-a716-446655440000', {}, ctx)
    expect(result.ok).toBe(true)
    expect(result.data.mode).toBe('parse')
    expect(result.data.parsed.version).toBe(4)
    expect(result.data.parsed.variant).toBe('RFC 4122')
  })

  it('非法 UUID 与数量返回错误码', () => {
    expect(runUuid('nope', {}, ctx).error.code).toBe('invalidUuid')
    expect(runUuid('', { count: 0 }, ctx).error.code).toBe('invalidCount')
  })
})
