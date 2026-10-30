import { describe, expect, it } from 'vitest'

import { EMPTY_MAPPING, decodeCsvBuffer, guessMapping, parseAmount, parseCsv, parseDate, previewRows, validatePreviewRow } from './csvLite'

const mapping = { ...EMPTY_MAPPING, dateCol: 0, typeCol: 1, amountCol: 2, categoryCol: 3, accountCol: 4, remarkCol: 5 }

describe('parseCsv', () => {
  it('splits simple rows and keeps header as first record', () => {
    const { headers, rows } = parseCsv('日期,金额\n2026-01-05,10\n')
    expect(headers).toEqual(['日期', '金额'])
    expect(rows).toHaveLength(2)
    expect(rows[1]).toEqual({ line: 2, cells: ['2026-01-05', '10'] })
  })

  it('keeps commas inside quotes', () => {
    const { rows } = parseCsv('a,"b,c",d\n')
    expect(rows[0].cells).toEqual(['a', 'b,c', 'd'])
  })

  it('keeps newlines inside quotes and tracks line numbers', () => {
    const { rows } = parseCsv('a,"line1\nline2",c\nx,y,z\n')
    expect(rows[0].cells).toEqual(['a', 'line1\nline2', 'c'])
    expect(rows[1].line).toBe(3)
  })

  it('unescapes doubled quotes and strips BOM', () => {
    const { rows } = parseCsv('\uFEFF"a""b",c\r\n')
    expect(rows[0].cells).toEqual(['a"b', 'c'])
  })

  it('skips blank lines without inventing records', () => {
    const { rows } = parseCsv('a,b\n\n1,2\n')
    expect(rows.map((r) => r.cells)).toEqual([
      ['a', 'b'],
      ['1', '2'],
    ])
  })
})

describe('decodeCsvBuffer', () => {
  it('decodes UTF-8 with BOM', () => {
    const bytes = new Uint8Array([0xef, 0xbb, 0xbf, ...new TextEncoder().encode('日期,金额\n')])
    expect(decodeCsvBuffer(bytes.buffer)).toBe('日期,金额\n')
  })

  it('decodes plain UTF-8', () => {
    const bytes = new TextEncoder().encode('日期,金额')
    expect(decodeCsvBuffer(bytes.buffer)).toBe('日期,金额')
  })
})

describe('parse helpers', () => {
  it('parses amount with symbols and parentheses', () => {
    expect(parseAmount('¥1,234.50')).toBe(1234.5)
    expect(parseAmount('(300)')).toBe(-300)
    expect(parseAmount('abc')).toBeNull()
  })

  it('parses common date formats and rejects impossible days', () => {
    expect(parseDate('2026/1/5')).toBe('2026-01-05')
    expect(parseDate('2026年1月5日')).toBe('2026-01-05')
    expect(parseDate('20260105')).toBe('2026-01-05')
    expect(parseDate('2026-02-30')).toBeNull()
  })
})

describe('guessMapping', () => {
  it('maps Chinese and English headers', () => {
    const m = guessMapping(['交易日期', '收支', '金额', '分类', '账户', '备注'])
    expect(m.dateCol).toBe(0)
    expect(m.typeCol).toBe(1)
    expect(m.amountCol).toBe(2)
    expect(m.categoryCol).toBe(3)
    expect(m.accountCol).toBe(4)
    expect(m.remarkCol).toBe(5)
  })

  it('leaves unknown columns unmapped', () => {
    const m = guessMapping(['foo', 'bar'])
    expect(m.dateCol).toBe(-1)
    expect(m.amountCol).toBe(-1)
  })
})

describe('validatePreviewRow', () => {
  it('accepts a good row and infers type from sign when type is blank', () => {
    const r = validatePreviewRow(['2026-01-05', '', '-12.5', '', '', ''], mapping)
    expect(r.ok).toBe(true)
    expect(r.data.type).toBe('EXPENSE')
    expect(r.data.amount).toBe(12.5)
    expect(r.data.category).toBe('其他')
  })

  it('reports a bad date row and a quoted comma remark', () => {
    const bad = validatePreviewRow(['2026-02-30', '支出', '10', '', '', ''], mapping)
    expect(bad).toMatchObject({ ok: false, message: 'dateBad' })

    const good = validatePreviewRow(['2026-01-05', '支出', '10', '餐饮', '现金', '早餐,豆浆'], mapping)
    expect(good.ok).toBe(true)
    expect(good.data.remark).toBe('早餐,豆浆')
  })

  it('rejects zero amount, transfer rows and empty required cells', () => {
    expect(validatePreviewRow(['2026-01-05', '支出', '0', '', '', ''], mapping).message).toBe('amountZero')
    expect(validatePreviewRow(['2026-01-05', '转账', '10', '', '', ''], mapping).message).toBe('transferUnsupported')
    expect(validatePreviewRow(['', '支出', '10', '', '', ''], mapping).message).toBe('dateEmpty')
    expect(validatePreviewRow(['2026-01-05', '支出', 'abc', '', '', ''], mapping).message).toBe('amountBad')
  })
})

describe('previewRows', () => {
  it('skips the header and counts valid vs invalid rows', () => {
    const parsed = parseCsv('日期,类型,金额\n2026-01-05,支出,10\n2026-02-30,支出,20\n')
    const stats = previewRows(parsed, mapping)
    expect(stats.total).toBe(2)
    expect(stats.valid).toBe(1)
    expect(stats.invalid).toBe(1)
    expect(stats.samples[1]).toMatchObject({ line: 3, ok: false, message: 'dateBad' })
  })
})
