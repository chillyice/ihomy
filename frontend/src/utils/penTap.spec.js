import { describe, expect, it } from 'vitest'
import {
  PEN_CLICK_RADIUS,
  PEN_CLICK_SWALLOW_MS,
  PEN_LONG_PRESS_MS,
  PEN_MOVE_TOLERANCE,
  isDisabledControl,
  isPenTapCandidate,
  isPenTapExcluded,
  resolvePenTapTarget,
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

describe('isDisabledControl', () => {
  it('抬起时停在 disabled 控件内不补发点击(与原生一致)', () => {
    const disabledButton = { closest: (sel) => (sel.includes('button[disabled]') ? { disabled: true } : null) }
    expect(isDisabledControl(disabledButton)).toBe(true)
    expect(isDisabledControl({ closest: (sel) => (sel.includes('fieldset[disabled]') ? {} : null) })).toBe(true)
  })

  it('enabled 元素与空值照常放行', () => {
    expect(isDisabledControl({ closest: () => null })).toBe(false)
    expect(isDisabledControl(null)).toBe(false)
    expect(isDisabledControl(undefined)).toBe(false)
  })
})

describe('resolvePenTapTarget', () => {
  const node = (extra = {}) => ({ contains: () => false, parentElement: null, isConnected: true, ...extra })

  it('按下/抬起同一元素 → 原元素', () => {
    const el = node()
    expect(resolvePenTapTarget({ upTarget: el, downTarget: el })).toBe(el)
  })

  it('内外层互为祖先 → 取公共的外层(与原生 click 目标一致)', () => {
    const inner = node()
    const outer = node({ contains: (n) => n === inner })
    expect(resolvePenTapTarget({ upTarget: inner, downTarget: outer })).toBe(outer)
    expect(resolvePenTapTarget({ upTarget: outer, downTarget: inner })).toBe(outer)
  })

  it('小幅漂移蹭到相邻元素 → 最近公共祖先,不会误点隔壁的牌', () => {
    const root = node()
    const a = node({ parentElement: root })
    const b = node({ parentElement: root })
    expect(resolvePenTapTarget({ upTarget: b, downTarget: a })).toBe(root)
  })

  it('按下时的元素已被移除(询问条关闭/手牌重排)→ 以抬起处命中为准', () => {
    const removed = node({ isConnected: false })
    const up = node()
    expect(resolvePenTapTarget({ upTarget: up, downTarget: removed })).toBe(up)
    expect(resolvePenTapTarget({ upTarget: up, downTarget: null })).toBe(up)
  })

  it('抬起处命中不到(视口外)→ 回退按下时仍连接的元素', () => {
    const down = node()
    expect(resolvePenTapTarget({ upTarget: null, downTarget: down, fallbackTarget: null })).toBe(down)
  })

  it('都拿不到 → 用抬起事件自带 target;全空返回 null', () => {
    const fallback = node()
    expect(resolvePenTapTarget({ upTarget: null, downTarget: null, fallbackTarget: fallback })).toBe(fallback)
    expect(resolvePenTapTarget({ upTarget: null, downTarget: null, fallbackTarget: null })).toBe(null)
  })

  it('按下/抬起无公共祖先(节点分属两棵树)→ 回退抬起事件 target', () => {
    const a = node()
    const b = node()
    const fallback = node()
    expect(resolvePenTapTarget({ upTarget: a, downTarget: b, fallbackTarget: fallback })).toBe(fallback)
  })
})
