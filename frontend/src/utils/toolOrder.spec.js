import { describe, it, expect } from 'vitest'
import { TOOL_ORDER_STORAGE_KEY, mergeToolOrder, loadToolOrder, saveToolOrder, moveToolTo, moveToolBy } from './toolOrder'

const DEFAULTS = ['mindmap', 'ai-playground', 'light-lab', 'pdf']

describe('mergeToolOrder', () => {
  it('保留已存顺序', () => {
    expect(mergeToolOrder(['pdf', 'mindmap'], DEFAULTS)).toEqual(['pdf', 'mindmap', 'ai-playground', 'light-lab'])
  })

  it('新工具按默认顺序补到末尾', () => {
    expect(mergeToolOrder(['light-lab', 'pdf'], DEFAULTS)).toEqual(['light-lab', 'pdf', 'mindmap', 'ai-playground'])
  })

  it('丢弃未知项并去重', () => {
    expect(mergeToolOrder(['pdf', 'removed-tool', 'pdf', 42], DEFAULTS)).toEqual(['pdf', 'mindmap', 'ai-playground', 'light-lab'])
  })

  it('非法输入回退默认顺序', () => {
    expect(mergeToolOrder(null, DEFAULTS)).toEqual(DEFAULTS)
    expect(mergeToolOrder('pdf', DEFAULTS)).toEqual(DEFAULTS)
    expect(mergeToolOrder({ 0: 'pdf' }, DEFAULTS)).toEqual(DEFAULTS)
  })

  it('不改动传入的默认数组', () => {
    const defaults = [...DEFAULTS]
    mergeToolOrder(['pdf'], defaults)
    expect(defaults).toEqual(DEFAULTS)
  })
})

describe('loadToolOrder / saveToolOrder', () => {
  const fakeStorage = (initial) => {
    const data = new Map(initial ? [[TOOL_ORDER_STORAGE_KEY, initial]] : [])
    return {
      getItem: (k) => data.get(k) ?? null,
      setItem: (k, v) => data.set(k, v),
    }
  }

  it('读回已存顺序并与默认清单合并', () => {
    expect(loadToolOrder(DEFAULTS, fakeStorage(JSON.stringify(['pdf', 'mindmap'])))).toEqual([
      'pdf',
      'mindmap',
      'ai-playground',
      'light-lab',
    ])
  })

  it('存档损坏时回退默认顺序', () => {
    expect(loadToolOrder(DEFAULTS, fakeStorage('{oops'))).toEqual(DEFAULTS)
  })

  it('存储不可用时回退默认顺序且写操作不抛错', () => {
    const broken = {
      getItem: () => {
        throw new Error('denied')
      },
      setItem: () => {
        throw new Error('denied')
      },
    }
    expect(loadToolOrder(DEFAULTS, broken)).toEqual(DEFAULTS)
    expect(() => saveToolOrder(DEFAULTS, broken)).not.toThrow()
  })

  it('保存后能原样读回', () => {
    const storage = fakeStorage()
    const order = ['light-lab', 'pdf', 'mindmap', 'ai-playground']
    saveToolOrder(order, storage)
    expect(loadToolOrder(DEFAULTS, storage)).toEqual(order)
  })
})

describe('moveToolTo', () => {
  it('向后移动到目标位置', () => {
    expect(moveToolTo(DEFAULTS, 'mindmap', 'light-lab')).toEqual(['ai-playground', 'light-lab', 'mindmap', 'pdf'])
  })

  it('向前移动到目标位置', () => {
    expect(moveToolTo(DEFAULTS, 'pdf', 'mindmap')).toEqual(['pdf', 'mindmap', 'ai-playground', 'light-lab'])
  })

  it('目标不存在或原地不动时返回原数组', () => {
    expect(moveToolTo(DEFAULTS, 'mindmap', 'mindmap')).toBe(DEFAULTS)
    expect(moveToolTo(DEFAULTS, 'mindmap', 'nope')).toBe(DEFAULTS)
  })
})

describe('moveToolBy', () => {
  it('按偏移量上下移动', () => {
    expect(moveToolBy(DEFAULTS, 'ai-playground', 1)).toEqual(['mindmap', 'light-lab', 'ai-playground', 'pdf'])
    expect(moveToolBy(DEFAULTS, 'ai-playground', -1)).toEqual(['ai-playground', 'mindmap', 'light-lab', 'pdf'])
  })

  it('到达首尾时不再越界', () => {
    expect(moveToolBy(DEFAULTS, 'mindmap', -1)).toBe(DEFAULTS)
    expect(moveToolBy(DEFAULTS, 'pdf', 1)).toBe(DEFAULTS)
    expect(moveToolBy(DEFAULTS, 'pdf', 99)).toBe(DEFAULTS)
  })

  it('id 不存在或偏移为 0 时返回原数组', () => {
    expect(moveToolBy(DEFAULTS, 'nope', 1)).toBe(DEFAULTS)
    expect(moveToolBy(DEFAULTS, 'pdf', 0)).toBe(DEFAULTS)
  })
})
