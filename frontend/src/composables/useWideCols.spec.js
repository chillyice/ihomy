import { describe, it, expect } from 'vitest'
import { computeColMaxHeight } from './useWideCols'

// 双栏列净高:滚动容器高 − 双栏顶边在容器内的偏移 − 底部留白,下限 240。
// 公式只有这一份(Schedule / 相册 / 文件 / 图书馆共用),这里守住口径。
describe('computeColMaxHeight', () => {
  it('常规:容器高减顶边偏移与底部留白', () => {
    expect(computeColMaxHeight({ areaHeight: 900, topInArea: 316 })).toBe(560)
  })

  it('顶边下推(面包屑/工具栏/近期条越高,列越矮)', () => {
    expect(computeColMaxHeight({ areaHeight: 900, topInArea: 400 })).toBe(476)
  })

  it('不足下限时钉在 240,不把列表压没', () => {
    expect(computeColMaxHeight({ areaHeight: 300, topInArea: 250 })).toBe(240)
    expect(computeColMaxHeight({ areaHeight: 100, topInArea: 400 })).toBe(240)
  })

  it('量不到可视区(未挂载/隐藏)返回 0,调用方不写 --wide-col-max', () => {
    expect(computeColMaxHeight({ areaHeight: 0, topInArea: 0 })).toBe(0)
    expect(computeColMaxHeight({ areaHeight: undefined, topInArea: 10 })).toBe(0)
    expect(computeColMaxHeight()).toBe(0)
  })

  it('四舍五入到整像素,避免半像素抖动', () => {
    expect(computeColMaxHeight({ areaHeight: 900.4, topInArea: 316.2 })).toBe(560)
  })
})
