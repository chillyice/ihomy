import { describe, it, expect } from 'vitest'
import {
  MAX_BYTES,
  MAX_COUNT,
  buildPool,
  entropyBits,
  makePassword,
  makePin,
  nanoid,
  run,
  shuffle,
  strengthOf,
  ulid,
  uuidV4,
  uuidV7,
} from './generatorTool'

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/

describe('密码', () => {
  it('长度、数量与字符类型符合设置', () => {
    const result = run('', { mode: 'password', length: 24, count: 3 }, { locale: 'zh' })
    expect(result.ok).toBe(true)
    expect(result.data.items).toHaveLength(3)
    for (const item of result.data.items) {
      expect(item).toHaveLength(24)
      expect(item).toMatch(/[a-z]/)
      expect(item).toMatch(/[A-Z]/)
      expect(item).toMatch(/[0-9]/)
      expect(item).toMatch(/[^a-zA-Z0-9]/)
    }
  })

  it('排除易混淆字符', () => {
    for (let i = 0; i < 40; i += 1) {
      const password = makePassword({ length: 32, excludeAmbiguous: true }).value
      expect(password).not.toMatch(/[0Oo1lI|`]/)
    }
  })

  it('只选小写时字符集正确', () => {
    const password = makePassword({ length: 16, lower: true, upper: false, digits: false, symbols: false }).value
    expect(password).toMatch(/^[a-z]+$/)
  })

  it('熵与强度档位', () => {
    expect(entropyBits(20, 64)).toBe(120)
    expect(strengthOf(90)).toBe('veryStrong')
    expect(strengthOf(65)).toBe('strong')
    expect(strengthOf(45)).toBe('fair')
    expect(strengthOf(20)).toBe('weak')
  })

  it('长度不足或未选字符类型时报错', () => {
    expect(run('', { mode: 'password', length: 3, lower: true, upper: true, digits: true, symbols: true }, { locale: 'zh' })).toMatchObject(
      {
        ok: false,
        error: { code: 'invalidLength' },
      },
    )
    expect(run('', { mode: 'password', lower: false, upper: false, digits: false, symbols: false }, { locale: 'zh' })).toMatchObject({
      ok: false,
      error: { code: 'noCharset' },
    })
  })

  it('字符集长度按排除项计算', () => {
    const classes = buildPool({ symbols: false })
    expect(classes).toHaveLength(3)
    expect(classes.join('')).not.toMatch(/[0Oo1lI|`]/)
  })
})

describe('PIN', () => {
  it('只含数字且长度正确', () => {
    const pin = makePin({ length: 8 })
    expect(pin).toMatch(/^\d{8}$/)
    const result = run('', { mode: 'pin', length: 6, count: 5 }, { locale: 'zh' })
    expect(result.data.items).toHaveLength(5)
    for (const item of result.data.items) expect(item).toMatch(/^\d{6}$/)
  })
})

describe('UUID / ULID / NanoID', () => {
  it('UUID v4 版本与变体位正确', () => {
    const uuid = uuidV4()
    expect(uuid).toMatch(UUID_RE)
    expect(uuid[14]).toBe('4')
    expect(['8', '9', 'a', 'b']).toContain(uuid[19])
  })

  it('UUID v7 前缀携带毫秒时间戳', () => {
    const uuid = uuidV7(1700000000000)
    expect(uuid).toMatch(UUID_RE)
    expect(uuid[14]).toBe('7')
    expect(uuid.replace(/-/g, '').slice(0, 12)).toBe('018bcfe56800')
  })

  it('UUID 支持大写与去连字符', () => {
    const result = run('', { mode: 'uuid', count: 2, uppercase: true, hyphens: false }, { locale: 'zh' })
    for (const item of result.data.items) {
      expect(item).toMatch(/^[0-9A-F]{32}$/)
    }
  })

  it('ULID 为 26 位 Crockford 字符', () => {
    const value = ulid(1700000000000)
    expect(value).toHaveLength(26)
    expect(value).toMatch(/^[0-9A-HJKMNP-TV-Z]{26}$/)
    expect(value.slice(0, 10)).toBe('01HF7YAT00')
  })

  it('NanoID 长度与字符表', () => {
    const value = nanoid(16, 'abc')
    expect(value).toHaveLength(16)
    expect(value).toMatch(/^[abc]+$/)
    expect(() => nanoid(10, 'a')).toThrow('invalidAlphabet')
  })
})

describe('随机字节与随机整数', () => {
  it('随机字节的格式与长度', () => {
    const hex = run('', { mode: 'bytes', length: 32, count: 2 }, { locale: 'zh' })
    for (const item of hex.data.items) expect(item).toMatch(/^[0-9a-f]{64}$/)

    const base64 = run('', { mode: 'bytes', length: 16, format: 'base64' }, { locale: 'zh' })
    expect(base64.data.items[0]).toMatch(/^[A-Za-z0-9+/]+={0,2}$/)
  })

  it('随机整数落在闭区间内并可去重', () => {
    const result = run('', { mode: 'number', min: 5, max: 7, count: 3, unique: true }, { locale: 'zh' })
    expect(result.data.items.sort()).toEqual(['5', '6', '7'])

    const many = run('', { mode: 'number', min: 1, max: 10, count: 50 }, { locale: 'zh' })
    for (const item of many.data.items) {
      const value = Number(item)
      expect(value).toBeGreaterThanOrEqual(1)
      expect(value).toBeLessThanOrEqual(10)
    }
  })

  it('区间不足或非法时报错', () => {
    expect(run('', { mode: 'number', min: 1, max: 3, count: 5, unique: true }, { locale: 'zh' })).toMatchObject({
      ok: false,
      error: { code: 'rangeTooSmall' },
    })
    expect(run('', { mode: 'number', min: 9, max: 1 }, { locale: 'zh' })).toMatchObject({
      ok: false,
      error: { code: 'invalidRange' },
    })
  })
})

describe('抽签 / 洗牌 / 分组', () => {
  const input = '甲\n乙\n丙\n丁\n戊'

  it('抽签不重复且数量正确', () => {
    const result = run(input, { mode: 'pick', count: 3 }, { locale: 'zh' })
    expect(result.data.items).toHaveLength(3)
    expect(new Set(result.data.items).size).toBe(3)
  })

  it('洗牌保留全部条目', () => {
    const result = run(input, { mode: 'shuffle' }, { locale: 'zh' })
    expect(result.data.items.sort()).toEqual(['丁', '丙', '乙', '戊', '甲'].sort())
  })

  it('分组覆盖全部条目', () => {
    const groups = run(input, { mode: 'group', groupCount: 2 }, { locale: 'zh' }).data.groups
    expect(groups).toHaveLength(2)
    expect(groups.flat().sort()).toEqual(['丁', '丙', '乙', '戊', '甲'].sort())
  })

  it('条目不足或分组非法时报错', () => {
    expect(run('甲\n乙', { mode: 'pick', count: 3 }, { locale: 'zh' })).toMatchObject({
      ok: false,
      error: { code: 'notEnoughItems' },
    })
    expect(run('甲\n乙', { mode: 'group', groupCount: 3 }, { locale: 'zh' })).toMatchObject({
      ok: false,
      error: { code: 'invalidGroupCount' },
    })
  })

  it('shuffle 不修改入参', () => {
    const source = ['a', 'b', 'c']
    shuffle(source)
    expect(source).toEqual(['a', 'b', 'c'])
  })
})

describe('统一入口的边界', () => {
  it('未知模式与超量数量给出可读错误', () => {
    expect(run('', { mode: 'nope' }, { locale: 'zh' })).toMatchObject({ ok: false, error: { code: 'invalidMode' } })
    expect(run('', { mode: 'pin', count: MAX_COUNT + 1 }, { locale: 'zh' })).toMatchObject({
      ok: false,
      error: { code: 'invalidCount' },
    })
    expect(run('', { mode: 'bytes', length: MAX_BYTES + 1 }, { locale: 'zh' })).toMatchObject({
      ok: false,
      error: { code: 'invalidLength' },
    })
  })

  it('英文错误信息', () => {
    const result = run('', { mode: 'nope' }, { locale: 'en' })
    expect(result.error.message).toMatch(/Unsupported/)
  })
})
