import { describe, it, expect } from 'vitest'
import { dealtIds, flyOffset } from './cardAnimations'

describe('flyOffset', () => {
  it('invert 量 = 起点 − 落点', () => {
    expect(flyOffset({ left: 100, top: 50 }, { left: 320, top: 240 })).toEqual({ dx: -220, dy: -190 })
  })
  it('起点与落点重合时为零位移', () => {
    expect(flyOffset({ left: 320, top: 240 }, { left: 320, top: 240 })).toEqual({ dx: 0, dy: 0 })
  })
})

describe('dealtIds', () => {
  it('取从牌堆消失的 id,堆顶(数组尾)先发', () => {
    expect(dealtIds(['a', 'b', 'c', 'd'], ['a', 'b'])).toEqual(['d', 'c'])
    expect(dealtIds(['a', 'b', 'c', 'd'], ['a', 'b', 'c', 'd'])).toEqual([])
  })
  it('回收的牌回到牌堆不算发牌', () => {
    expect(dealtIds(['x'], ['x', 'y', 'z'])).toEqual([])
  })
})
