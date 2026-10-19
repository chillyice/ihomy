import { describe, expect, it } from 'vitest'
import { executeRegex, normalizeFlags, REGEX_PRESETS } from './core'

const run = (input, options) => executeRegex(input, options, { locale: 'zh' })

describe('normalizeFlags', () => {
  it('去重并保留顺序', () => {
    expect(normalizeFlags('ggim')).toBe('gim')
  })

  it('拒绝未知标志', () => {
    expect(() => normalizeFlags('gx')).toThrow('invalidFlags')
  })
})

describe('executeRegex', () => {
  it('空表达式返回可读错误', () => {
    const result = run('abc', { pattern: '' })
    expect(result.ok).toBe(false)
    expect(result.error.code).toBe('emptyPattern')
  })

  it('非法表达式带原因', () => {
    const result = run('abc', { pattern: '(' })
    expect(result.ok).toBe(false)
    expect(result.error.code).toBe('invalidPattern')
    expect(result.error.message).toContain('不合法')
  })

  it('非法标志', () => {
    const result = run('abc', { pattern: 'a', flags: 'gz' })
    expect(result.error.code).toBe('invalidFlags')
  })

  it('全局匹配并记录位置', () => {
    const result = run('a1 b22 c333', { pattern: '\\d+', flags: 'g' })
    expect(result.ok).toBe(true)
    expect(result.data.matches.map((m) => m.value)).toEqual(['1', '22', '333'])
    expect(result.data.matches[1].index).toBe(4)
  })

  it('非全局只取第一个', () => {
    const result = run('a a', { pattern: 'a' })
    expect(result.data.matches).toHaveLength(1)
  })

  it('捕获组与命名组', () => {
    const result = run('2026-10-20', { pattern: '(?<year>\\d{4})-(\\d{2})-(\\d{2})', flags: 'g' })
    expect(result.data.matches[0].groups.map((g) => g.value)).toEqual(['2026', '10', '20'])
    expect(result.data.matches[0].named.year).toBe('2026')
    expect(result.data.groups.some((g) => g.name === 'year' && g.value === '2026')).toBe(true)
  })

  it('替换预览支持 $1', () => {
    const result = run('2026-10-20', { pattern: '(\\d+)-(\\d+)-(\\d+)', replacement: '$3/$2/$1' })
    expect(result.data.replaced).toBe('20/10/2026')
  })

  it('split 拆分', () => {
    const result = run('a,b;c', { pattern: '[,;]', flags: 'g' })
    expect(result.data.split).toEqual(['a', 'b', 'c'])
  })

  it('g 标志的 lastIndex 不跨次残留', () => {
    const first = run('aa', { pattern: 'a', flags: 'g' })
    const second = run('aa', { pattern: 'a', flags: 'g' })
    expect(second.data.matches).toHaveLength(2)
    expect(first.data.matches).toHaveLength(2)
  })

  it('中文与 emoji 按字符正常匹配', () => {
    const result = run('家😀 家庭', { pattern: '家庭?', flags: 'g' })
    expect(result.data.matches.map((m) => m.value)).toEqual(['家', '家庭'])
  })

  it('匹配数量超过上限时截断', () => {
    const result = run('a'.repeat(600), { pattern: 'a', flags: 'g' })
    expect(result.data.matches).toHaveLength(500)
    expect(result.data.truncated).toBe(true)
  })

  it('内置常用正则可用', () => {
    for (const preset of REGEX_PRESETS) {
      expect(() => new RegExp(preset.pattern, preset.flags)).not.toThrow()
    }
    const ipv4 = REGEX_PRESETS.find((p) => p.id === 'ipv4')
    expect(run('网关 192.168.1.1', ipv4).data.matches[0].value).toBe('192.168.1.1')
  })
})
