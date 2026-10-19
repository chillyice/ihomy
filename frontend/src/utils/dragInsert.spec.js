import { describe, it, expect } from 'vitest'
import { pickInsertIndex } from './dragInsert'

const box = (id, left, top, right, bottom) => ({ id, left, top, right, bottom })
const others = (...ids) => ids.map((id) => ({ id }))
const GRID = { top: 0, bottom: 500 }

describe('pickInsertIndex', () => {
  // 同行左右并排、高度不一(旧算法会把右侧卡上方误判到最前)
  const A = box('a', 0, 0, 400, 100)
  const B = box('b', 412, 0, 800, 300)
  const C = box('c', 0, 112, 800, 300)

  it('落在卡片左/右半区决定插到它前/后', () => {
    expect(pickInsertIndex([A, B, C], others('a', 'b', 'c'), 700, 20, 2, 'c', GRID)).toBe(2)
    expect(pickInsertIndex([A, B, C], others('a', 'b', 'c'), 450, 20, 2, 'c', GRID)).toBe(1)
    expect(pickInsertIndex([A, B, C], others('a', 'b', 'c'), 390, 50, 1, 'c', GRID)).toBe(1)
  })

  it('压在源卡自身时保持当前下标(不因自身移动而抖)', () => {
    expect(pickInsertIndex([A, B, C], others('a', 'b', 'c'), 600, 150, 2, 'b', GRID)).toBe(2)
  })

  it('落在卡片间隙时保持当前下标', () => {
    expect(pickInsertIndex([A, B, C], others('a', 'b', 'c'), 406, 20, 0, 'c', GRID)).toBe(0)
  })

  it('越出网格上下边界收口到首/尾', () => {
    expect(pickInsertIndex([A, B, C], others('a', 'b', 'c'), 100, 600, 1, 'a', GRID)).toBe(3)
    expect(pickInsertIndex([A, B, C], others('a', 'b', 'c'), 100, -10, 1, 'a', GRID)).toBe(0)
  })

  it('无当前下标(首次拖入)时用最近卡片兜底', () => {
    expect(pickInsertIndex([A, B, C], others('a', 'b', 'c'), 405, 400, null, 'x', GRID)).toBe(3)
  })
})
