import { describe, expect, it } from 'vitest'
import {
  PEN_CLICK_RADIUS,
  PEN_CLICK_SWALLOW_MS,
  PEN_LONG_PRESS_MS,
  PEN_MOVE_TOLERANCE,
  isPenTapCandidate,
  isPenTapExcluded,
  shouldSwallowPenClick,
} from './penTap'

describe('isPenTapCandidate', () => {
  it('手写笔原地短按算点击', () => {
    expect(isPenTapCandidate({ pointerType: 'pen', distance: 0, duration: 120 })).toBe(true)
    expect(isPenTapCandidate({ pointerType: 'pen', distance: PEN_MOVE_TOLERANCE, duration: PEN_LONG_PRESS_MS })).toBe(true)
  })

  it('手写笔拖动超过阈值就不算点击(拖牌/滚页面)', () => {
    expect(isPenTapCandidate({ pointerType: 'pen', distance: PEN_MOVE_TOLERANCE + 1, duration: 120 })).toBe(false)
  })

  it('长按交给长按菜单,不补发点击', () => {
    expect(isPenTapCandidate({ pointerType: 'pen', distance: 0, duration: PEN_LONG_PRESS_MS + 1 })).toBe(false)
  })

  it('拖牌进行中不再补发点击', () => {
    expect(isPenTapCandidate({ pointerType: 'pen', distance: 0, duration: 120, busy: true })).toBe(false)
  })

  it('手指与鼠标维持原生 click,不接管', () => {
    expect(isPenTapCandidate({ pointerType: 'touch', distance: 0, duration: 120 })).toBe(false)
    expect(isPenTapCandidate({ pointerType: 'mouse', distance: 0, duration: 120 })).toBe(false)
    expect(isPenTapCandidate({ pointerType: undefined, distance: 0, duration: 120 })).toBe(false)
  })
})

describe('shouldSwallowPenClick', () => {
  it('自己补发的点击放行', () => {
    expect(shouldSwallowPenClick({ synthetic: true, sinceTap: 0, distanceFromTap: 0 })).toBe(false)
  })

  it('浏览器随后补发的同落点点击吞掉,一次按下只算一次点击', () => {
    expect(shouldSwallowPenClick({ sinceTap: 20, distanceFromTap: 0 })).toBe(true)
    expect(shouldSwallowPenClick({ sinceTap: 20, distanceFromTap: PEN_CLICK_RADIUS })).toBe(true)
  })

  it('不接管的区域(canvas 手写板/输入框)永不吞', () => {
    expect(shouldSwallowPenClick({ excluded: true, sinceTap: 10, distanceFromTap: 0 })).toBe(false)
  })

  it('范围外或过晚的点击不吞(别误伤手指点击)', () => {
    expect(shouldSwallowPenClick({ sinceTap: 20, distanceFromTap: PEN_CLICK_RADIUS + 1 })).toBe(false)
    expect(shouldSwallowPenClick({ sinceTap: PEN_CLICK_SWALLOW_MS, distanceFromTap: 0 })).toBe(false)
    expect(shouldSwallowPenClick({ sinceTap: -1, distanceFromTap: 0 })).toBe(false)
    expect(shouldSwallowPenClick({ sinceTap: 10, distanceFromTap: NaN })).toBe(false)
  })
})

describe('isPenTapExcluded', () => {
  const fake = (matched, contentEditable = false) => ({ closest: () => matched, isContentEditable: contentEditable })

  it('canvas / 输入框 / 标了 data-pen-tap="off" 的容器不接管', () => {
    expect(isPenTapExcluded(fake({ tagName: 'CANVAS' }))).toBe(true)
    expect(isPenTapExcluded(fake(null, true))).toBe(true)
  })

  it('普通元素照常接管', () => {
    expect(isPenTapExcluded(fake(null))).toBe(false)
    expect(isPenTapExcluded(null)).toBe(false)
    expect(isPenTapExcluded(undefined)).toBe(false)
  })
})
