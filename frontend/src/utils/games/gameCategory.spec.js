import { describe, expect, it } from 'vitest'
import { GAME_CATEGORIES, OTHER_CATEGORY, countGameCategories, inGameCategory, normalizeGameCategory } from './gameCategory'

describe('gameCategory', () => {
  it('keeps known categories and trims/case-folds', () => {
    expect(normalizeGameCategory('PUZZLE')).toBe('PUZZLE')
    expect(normalizeGameCategory(' simulation ')).toBe('SIMULATION')
  })

  it('falls back to OTHER for empty or unknown values', () => {
    expect(normalizeGameCategory(null)).toBe(OTHER_CATEGORY)
    expect(normalizeGameCategory(undefined)).toBe(OTHER_CATEGORY)
    expect(normalizeGameCategory('')).toBe(OTHER_CATEGORY)
    expect(normalizeGameCategory('SHOOTER')).toBe(OTHER_CATEGORY)
  })

  it('counts every category including old rows without a category', () => {
    const counts = countGameCategories([
      { category: 'PUZZLE' },
      { category: 'PUZZLE' },
      { category: 'SIMULATION' },
      { category: 'unknown' },
      {},
    ])
    expect(Object.keys(counts)).toEqual(GAME_CATEGORIES)
    expect(counts.PUZZLE).toBe(2)
    expect(counts.SIMULATION).toBe(1)
    expect(counts.OTHER).toBe(2)
    expect(counts.ACTION).toBe(0)
  })

  it('filters by selected category, ALL passes everything', () => {
    expect(inGameCategory({ category: 'PUZZLE' }, 'ALL')).toBe(true)
    expect(inGameCategory({ category: 'PUZZLE' }, '')).toBe(true)
    expect(inGameCategory({ category: 'PUZZLE' }, 'PUZZLE')).toBe(true)
    expect(inGameCategory({ category: 'PUZZLE' }, 'ACTION')).toBe(false)
    expect(inGameCategory({}, 'OTHER')).toBe(true)
  })
})
