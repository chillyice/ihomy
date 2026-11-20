import { describe, expect, it } from 'vitest'
import { PEN_CLICK_RADIUS, PEN_CLICK_SWALLOW_MS, PEN_MOVE_TOLERANCE, isPenTapCandidate, shouldSwallowPenClick } from './penTap'

describe('isPenTapCandidate', () => {
  it('手写笔原地按一下算点击', () => {
    expect(isPenTapCandidate({ pointerType: 'pen', distance: 0, busy: false })).toBe(true)
    expect(isPenTapCandidate({ pointerType: 'pen', distance: PEN_MOVE_TOLERANCE, busy: false })).toBe(true)
  })

  it('手写笔拖动超过了阈值就不算点击(拖牌/滚页面)', () => {
    expect(isPenTapCandidate({ pointerType: 'pen', distance: PEN_MOVE_TOLERANCE + 1, busy: false })).toBe(false)
  })

  it('已经在拖牌时不再补发点击', () => {
    expect(isPenTapCandidate({ pointerType: 'pen', distance: 0, busy: true })).toBe(false)
  })

  it('手指与鼠标维持原生 click,不接管', () => {
    expect(isPenTapCandidate({ pointerType: 'touch', distance: 0, busy: false })).toBe(false)
    expect(isPenTapCandidate({ pointerType: 'mouse', distance: 0, busy: false })).toBe(false)
    expect(isPenTapCandidate({ pointerType: undefined, distance: 0, busy: false })).toBe(false)
  })
})

describe('shouldSwallowPenClick', () => {
  it('自己补发的点击放行', () => {
    expect(shouldSwallowPenClick({ pointerType: 'pen', synthetic: true, sinceTap: 0, distanceFromTap: 0 })).toBe(false)
  })

  it('浏览器补发的手写笔点击一律吞掉,避免一次按下算两次', () => {
    expect(shouldSwallowPenClick({ pointerType: 'pen', synthetic: false, sinceTap: 9999, distanceFromTap: 999 })).toBe(true)
  })

  it('手指/鼠标的点击照常放行', () => {
    expect(shouldSwallowPenClick({ pointerType: 'touch', synthetic: false, sinceTap: 10, distanceFromTap: 0 })).toBe(false)
    expect(shouldSwallowPenClick({ pointerType: 'mouse', synthetic: false, sinceTap: 10, distanceFromTap: 0 })).toBe(false)
  })

  it('老 Safari 的 MouseEvent 只能按落点与时间兜:近且快才吞', () => {
    expect(shouldSwallowPenClick({ pointerType: undefined, synthetic: false, sinceTap: 20, distanceFromTap: 4 })).toBe(true)
    expect(shouldSwallowPenClick({ pointerType: undefined, synthetic: false, sinceTap: 20, distanceFromTap: PEN_CLICK_RADIUS + 1 })).toBe(
      false,
    )
    expect(shouldSwallowPenClick({ pointerType: undefined, synthetic: false, sinceTap: PEN_CLICK_SWALLOW_MS, distanceFromTap: 4 })).toBe(
      false,
    )
  })

  it('没按下过(没有落笔点)时不吞', () => {
    expect(shouldSwallowPenClick({ pointerType: undefined, synthetic: false, sinceTap: -1, distanceFromTap: 0 })).toBe(false)
    expect(shouldSwallowPenClick({ pointerType: undefined, synthetic: false, sinceTap: 10, distanceFromTap: NaN })).toBe(false)
  })
})
