import { describe, it, expect } from 'vitest'

import { wheelScroll } from './horizontalWheel'

// 余额条隐藏原生滚动条后靠滚轮映射横向滚动:到边缘必须放行给页面,否则整页纵向滚动被吃掉
describe('wheelScroll 横向滚轮映射', () => {
  const base = { scrollLeft: 100, clientWidth: 200, scrollWidth: 600, delta: 0 }

  it('容器不溢出时不消费滚轮', () => {
    expect(wheelScroll({ ...base, scrollWidth: 200, delta: 40 })).toEqual({ consume: false, left: 100 })
  })

  it('中间位置消费滚轮并累加 scrollLeft', () => {
    expect(wheelScroll({ ...base, delta: 40 })).toEqual({ consume: true, left: 140 })
    expect(wheelScroll({ ...base, delta: -40 })).toEqual({ consume: true, left: 60 })
  })

  it('到达右边缘后放行(不再 preventDefault)', () => {
    expect(wheelScroll({ ...base, scrollLeft: 400, delta: 40 })).toEqual({ consume: false, left: 400 })
  })

  it('到达左边缘后放行', () => {
    expect(wheelScroll({ ...base, scrollLeft: 0, delta: -40 })).toEqual({ consume: false, left: 0 })
  })

  it('单次滚动超过剩余距离时收敛到边缘', () => {
    expect(wheelScroll({ ...base, scrollLeft: 380, delta: 80 })).toEqual({ consume: true, left: 400 })
  })
})
