import { expect, it } from 'vitest'
import { recentMonthRows } from './bookTrend'

const now = new Date(2026, 10, 23) // 2026-11-23

it('pads missing months with zeros and keeps chronological order', () => {
  const rows = recentMonthRows([{ month: '2026-08', income: 100, expense: 40 }], 6, now)
  expect(rows.map((r) => r.key)).toEqual(['2026-06', '2026-07', '2026-08', '2026-09', '2026-10', '2026-11'])
  expect(rows.map((r) => r.label)).toEqual(['6', '7', '8', '9', '10', '11'])
  expect(rows[2]).toMatchObject({ income: 100, expense: 40 })
  expect(rows[0]).toMatchObject({ income: 0, expense: 0 })
})

it('crosses the year boundary', () => {
  const rows = recentMonthRows([{ month: '2025-12', income: 7, expense: 3 }], 6, now)
  expect(rows.map((r) => r.key)).toEqual(['2026-06', '2026-07', '2026-08', '2026-09', '2026-10', '2026-11'])
  const jan = recentMonthRows([], 6, new Date(2026, 0, 15))
  expect(jan.map((r) => r.key)).toEqual(['2025-08', '2025-09', '2025-10', '2025-11', '2025-12', '2026-01'])
})

it('normalizes day-precision months and merges duplicates', () => {
  const rows = recentMonthRows(
    [
      { month: '2026-11-01', income: 5, expense: 1 },
      { month: '2026-11', income: 2, expense: 4 },
    ],
    1,
    now,
  )
  expect(rows).toHaveLength(1)
  expect(rows[0]).toMatchObject({ key: '2026-11', label: '11', income: 7, expense: 5 })
})

it('tolerates empty and malformed input', () => {
  expect(recentMonthRows(null, 3, now).map((r) => r.income)).toEqual([0, 0, 0])
  const rows = recentMonthRows([{ month: 'oops', income: 9 }, { income: 3 }], 2, now)
  expect(rows.every((r) => r.income === 0 && r.expense === 0)).toBe(true)
})
