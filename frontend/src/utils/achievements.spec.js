import { describe, it, expect } from 'vitest'
import {
  LEVELS,
  CATEGORY_ORDER,
  levelOf,
  nextLevelScore,
  levelFloor,
  progressPercent,
  levelProgress,
  groupByCategory,
  categoryKey,
  catalogKeys,
  unlockDate,
} from './achievements'

const item = (code, category, progress, threshold = 10) => ({ code, category, progress, threshold })

describe('成就等级换算', () => {
  it('等级档位从 1 起、门槛单调递增', () => {
    expect(LEVELS[0]).toEqual({ level: 1, minScore: 0 })
    for (let i = 1; i < LEVELS.length; i += 1) {
      expect(LEVELS[i].minScore).toBeGreaterThan(LEVELS[i - 1].minScore)
    }
  })

  it('按累计分取当前等级与下一档门槛', () => {
    expect(levelOf(0)).toBe(1)
    expect(levelOf(99)).toBe(1)
    expect(levelOf(100)).toBe(2)
    expect(levelOf(249)).toBe(2)
    expect(levelOf(800)).toBe(LEVELS.length)
    expect(levelOf(5000)).toBe(LEVELS.length)
  })

  it('满级后没有下一档', () => {
    expect(nextLevelScore(0)).toBe(100)
    expect(nextLevelScore(140)).toBe(250)
    expect(nextLevelScore(800)).toBe(-1)
    expect(levelProgress(900, 6)).toBe(100)
  })

  it('等级起始分与进度百分比按区间计算', () => {
    expect(levelFloor(2)).toBe(100)
    expect(levelFloor(99)).toBe(0)
    expect(levelProgress(100, 2)).toBe(0)
    expect(levelProgress(175, 2)).toBe(50)
    expect(levelProgress(249, 2)).toBe(99)
  })
})

describe('成就进度', () => {
  it('进度按阈值百分比四舍五入并封顶', () => {
    expect(progressPercent(item('a', 'GAME', 1, 10))).toBe(10)
    expect(progressPercent(item('a', 'GAME', 5, 10))).toBe(50)
    expect(progressPercent(item('a', 'GAME', 30, 10))).toBe(100)
    expect(progressPercent(item('a', 'GAME', 0, 10))).toBe(0)
  })

  it('缺字段或阈值非法时兜底', () => {
    expect(progressPercent(null)).toBe(100)
    expect(progressPercent({ threshold: 0, progress: 3 })).toBe(100)
    expect(progressPercent({ threshold: 10 })).toBe(0)
  })
})

describe('成就分类分组', () => {
  it('按固定分类顺序分组且只保留有内容的分类', () => {
    const groups = groupByCategory([
      item('game_first', 'GAME', 1, 1),
      item('checkin_first', 'CHECKIN', 1, 1),
      item('points_100', 'POINTS', 10, 100),
    ])
    expect(groups.map((g) => g.category)).toEqual(['CHECKIN', 'POINTS', 'GAME'])
    expect(groups[2].items.map((i) => i.code)).toEqual(['game_first'])
  })

  it('未登记的分类排在最后,不丢条目', () => {
    const groups = groupByCategory([item('x', 'OTHER', 1), item('y', 'GAME', 1)])
    expect(groups.map((g) => g.category)).toEqual(['GAME', 'OTHER'])
    expect(groups[1].items[0].code).toBe('x')
  })

  it('空列表与未分类条目兜底', () => {
    expect(groupByCategory([])).toEqual([])
    expect(groupByCategory(null)).toEqual([])
    expect(groupByCategory([{ code: 'z' }])[0].category).toBe('OTHER')
  })

  it('分类顺序常量覆盖后端五种分类', () => {
    expect([...CATEGORY_ORDER].sort()).toEqual(['CHECKIN', 'CONTENT', 'GAME', 'POINTS', 'TASK'])
  })
})

describe('成就键与时间显示', () => {
  it('分类与目录键走 i18n 命名空间', () => {
    expect(categoryKey('CHECKIN')).toBe('achievements.categories.CHECKIN')
    expect(catalogKeys('blog_first')).toEqual({
      name: 'achievements.catalog.blog_first.name',
      desc: 'achievements.catalog.blog_first.desc',
    })
  })

  it('解锁时间只留日期', () => {
    expect(unlockDate('2026-11-26T09:30:00')).toBe('2026-11-26')
    expect(unlockDate('2026-11-26 09:30:00')).toBe('2026-11-26')
    expect(unlockDate(null)).toBe('')
  })
})
