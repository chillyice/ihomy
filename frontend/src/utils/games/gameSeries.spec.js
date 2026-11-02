import { describe, expect, it } from 'vitest'
import { UNSERIES, inGameSeries, normalizeGameSeries, seriesOptionsOf } from './gameSeries'

describe('gameSeries', () => {
  it('normalizes blank and padded values', () => {
    expect(normalizeGameSeries(' 宝可梦 ')).toBe('宝可梦')
    expect(normalizeGameSeries(null)).toBe('')
    expect(normalizeGameSeries(undefined)).toBe('')
  })

  it('lists distinct non-empty series sorted by locale', () => {
    expect(seriesOptionsOf([{ series: '马里奥' }, { series: '宝可梦' }, { series: '宝可梦' }, { series: '' }, {}])).toEqual([
      '宝可梦',
      '马里奥',
    ])
  })

  it('filters by series, unsorted bucket and all', () => {
    expect(inGameSeries({ series: '宝可梦' }, '宝可梦')).toBe(true)
    expect(inGameSeries({ series: '宝可梦' }, '马里奥')).toBe(false)
    expect(inGameSeries({ series: '' }, UNSERIES)).toBe(true)
    expect(inGameSeries({ series: '宝可梦' }, UNSERIES)).toBe(false)
    expect(inGameSeries({}, '')).toBe(true)
    expect(inGameSeries({}, null)).toBe(true)
  })
})
