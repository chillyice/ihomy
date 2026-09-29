import { describe, it, expect } from 'vitest'
import { feedTypeLabel, feedSummary } from './feed'
import { pickDefaultFloor } from './floorPlanGeom'

// 用简单 t 桩:替换 {n}/{title} 占位,便于断言
const t = (key, params = {}) => key + JSON.stringify(params)

describe('feedTypeLabel', () => {
  it('已知类型返回 i18n key,未知返回空串', () => {
    expect(feedTypeLabel(t, 'blog')).toBe('feed.type.blog{}')
    expect(feedTypeLabel(t, 'nope')).toBe('')
  })
})

describe('feedSummary', () => {
  it('博客取标题、日记截断、其余按类型套模板', () => {
    expect(feedSummary(t, { type: 'blog', title: 'A' })).toBe('A')
    expect(feedSummary(t, { type: 'diary', content: 'x'.repeat(99) })).toBe('x'.repeat(40))
    expect(feedSummary(t, { type: 'photo', count: 3 })).toBe('feed.photoCount{"n":3}')
    expect(feedSummary(t, { type: 'wish', status: 'ACHIEVED', title: 'B' })).toBe('feed.wishAchieved{"title":"B"}')
    expect(feedSummary(t, { type: 'wish', status: 'PENDING', title: 'B' })).toBe('feed.wishMade{"title":"B"}')
  })

  it('空对象返回空串', () => {
    expect(feedSummary(t, null)).toBe('')
  })
})

describe('pickDefaultFloor', () => {
  it('有 1 楼选 1 楼', () => {
    expect(pickDefaultFloor('{"-1":{},"1":{},"2":{}}')).toBe(1)
  })
  it('无 1 楼选最高层,忽略 floorOrder', () => {
    expect(pickDefaultFloor('{"floorOrder":[3,-1],"3":{},"-1":{}}')).toBe(3)
  })
  it('合并额外楼层来源(如房间 floor)', () => {
    expect(pickDefaultFloor(null, [2, 4])).toBe(4)
    expect(pickDefaultFloor('{"2":{}}', [1])).toBe(1)
  })
  it('无任何来源回退 1 楼;脏 JSON 不抛错', () => {
    expect(pickDefaultFloor(null)).toBe(1)
    expect(pickDefaultFloor('{bad json')).toBe(1)
  })
})
