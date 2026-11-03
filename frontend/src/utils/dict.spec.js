import { describe, it, expect } from 'vitest'
import { indexCatTone } from './dict'

describe('indexCatTone 生活指数类别分档', () => {
  it('舒适/适宜一端为 good(牛油果绿)', () => {
    for (const c of ['适宜', '较适宜', '舒适', '较舒适', '不易发', '少发', '最弱', '弱', '优', '良', '良好']) {
      expect(indexCatTone(c)).toBe('good')
    }
  })

  it('中间档不串档:较不适宜/较不舒适/较易发 是 poor 而不是 bad', () => {
    for (const c of ['一般', '中等', '中', '较冷', '较热']) expect(indexCatTone(c)).toBe('fair')
    for (const c of ['较不宜', '较不适宜', '较不舒适', '较易发', '较差', '热', '冷']) expect(indexCatTone(c)).toBe('poor')
  })

  it('不适宜一端为 bad(主题红)', () => {
    for (const c of ['不宜', '不适宜', '很不舒适', '极易发', '易发', '很差', '差', '强', '很强', '炎热', '寒冷']) {
      expect(indexCatTone(c)).toBe('bad')
    }
  })

  it('非舒适度量表与空值保持品牌色', () => {
    for (const c of ['保湿', '防晒', '控油', '开启', '关闭', '需要', '不需要', '', '   ', undefined, null]) {
      expect(indexCatTone(c)).toBe('')
    }
  })

  it('首尾空白不影响判定', () => {
    expect(indexCatTone(' 适宜 ')).toBe('good')
    expect(indexCatTone(' 很强 ')).toBe('bad')
  })
})
