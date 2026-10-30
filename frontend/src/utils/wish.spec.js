import { describe, it, expect } from 'vitest'
import { WISH_CATEGORIES, wishSourceRoute } from './wish'

describe('愿望单来源回跳', () => {
  it('放映厅带条目ID回作品详情,无ID回列表', () => {
    expect(wishSourceRoute('CINEMA', 'abc123')).toBe('/cinema/abc123')
    expect(wishSourceRoute('CINEMA', null)).toBe('/cinema')
    expect(wishSourceRoute('CINEMA', '')).toBe('/cinema')
  })

  it('纪念日/小游戏/家庭计划回各自页面,未知来源不回跳', () => {
    expect(wishSourceRoute('ANNIVERSARY', '12')).toBe('/plan?tab=anniversary')
    expect(wishSourceRoute('GAME', null)).toBe('/games')
    expect(wishSourceRoute('PLAN', '3')).toBe('/plan')
    expect(wishSourceRoute(null, null)).toBe('')
    expect(wishSourceRoute('UNKNOWN', '1')).toBe('')
  })

  it('分类候选项包含影视且无重复', () => {
    expect(WISH_CATEGORIES).toContain('影视')
    expect(new Set(WISH_CATEGORIES).size).toBe(WISH_CATEGORIES.length)
  })
})
