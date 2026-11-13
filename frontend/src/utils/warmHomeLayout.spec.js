import { describe, expect, it } from 'vitest'
import { parseWarmLayout } from './warmHomeLayout'

// 用最简注册表模拟页面:weather 8 列 4 行、feed 4 列 4 行、其余 4 列 2 行;link: 开头的只要 code 在白名单里
const KNOWN = new Set(['weather', 'feed', 'photos', 'link:diary'])
const opts = {
  spanOf: (id) => (id === 'weather' ? 8 : 4),
  rowOf: (id) => (id === 'weather' || id === 'feed' ? 4 : 2),
  isValid: (item) => KNOWN.has(item.id),
}

describe('parseWarmLayout', () => {
  it('空值与坏 JSON 一律回 null(交给调用方用默认布局)', () => {
    expect(parseWarmLayout(null, opts)).toBeNull()
    expect(parseWarmLayout('', opts)).toBeNull()
    expect(parseWarmLayout('{不是数组}', opts)).toBeNull()
    expect(parseWarmLayout('null', opts)).toBeNull()
  })

  it('空数组不算有效布局', () => {
    expect(parseWarmLayout('[]', opts)).toBeNull()
  })

  it('v1 的字符串数组按注册表补齐 span/row', () => {
    expect(parseWarmLayout('["weather","feed"]', opts)).toEqual([
      { id: 'weather', span: 8, row: 4 },
      { id: 'feed', span: 4, row: 4 },
    ])
  })

  it('对象项保留已有 span,缺 row 时补默认行高', () => {
    expect(parseWarmLayout('[{"id":"photos","span":12}]', opts)).toEqual([{ id: 'photos', span: 12, row: 2 }])
  })

  it('快捷入口条目原样保留(span/row 已在对象里)', () => {
    expect(parseWarmLayout('[{"id":"link:diary","span":4,"row":2}]', opts)).toEqual([{ id: 'link:diary', span: 4, row: 2 }])
  })

  it('已下线的组件/模块被丢掉,其余保留(span 缺省留给 resolveWidget 取注册表默认值)', () => {
    expect(parseWarmLayout('[{"id":"gone","span":4,"row":2},{"id":"feed"}]', opts)).toEqual([{ id: 'feed', row: 4 }])
  })

  it('一项都不剩时回 null(旧实现会返回空数组把首页清空)', () => {
    expect(parseWarmLayout('[{"id":"gone"}]', opts)).toBeNull()
    expect(parseWarmLayout('[null,"gone"]', opts)).toBeNull()
  })
})
