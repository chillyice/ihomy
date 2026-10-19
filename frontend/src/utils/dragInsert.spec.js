import { describe, it, expect } from 'vitest'
import { pickInsertIndex } from './dragInsert'

const rect = (l, t, w, h) => ({ left: l, top: t, right: l + w, bottom: t + h })

// 视觉一行两列但行高不同:左列高卡(0..300) + 右列矮卡(0..100),其下第三张
const rects = [rect(0, 0, 200, 300), rect(200, 0, 200, 100), rect(0, 300, 200, 100)]

describe('pickInsertIndex', () => {
  it('指针在右侧卡片内 → 按其左右半插到前/后', () => {
    expect(pickInsertIndex(rects, 210, 50)).toBe(1)
    expect(pickInsertIndex(rects, 390, 50)).toBe(2)
  })

  it('指针在左侧高卡右半、但位于右侧矮卡之下方的邻近区域', () => {
    expect(pickInsertIndex(rects, 150, 250)).toBe(1)
  })

  it('右侧矮卡上方不再误落到最前(旧实现返回 0)', () => {
    expect(pickInsertIndex(rects, 210, 250)).toBe(1)
  })

  it('空列表 → 0', () => {
    expect(pickInsertIndex([], 10, 10)).toBe(0)
  })
})
