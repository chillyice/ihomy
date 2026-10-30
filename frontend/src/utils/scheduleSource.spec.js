import { describe, expect, it, vi } from 'vitest'
import {
  SOURCE_STORAGE_KEY,
  defaultSourcePrefs,
  filterEventDays,
  filterSourceItems,
  isReadOnlySource,
  loadSourcePrefs,
  saveSourcePrefs,
  sourceOf,
} from './scheduleSource'

const memoryStorage = (initial) => {
  const data = { ...initial }
  return {
    getItem: (k) => (k in data ? data[k] : null),
    setItem: vi.fn((k, v) => {
      data[k] = v
    }),
    data,
  }
}

const items = () => [
  { kind: 'ANNIVERSARY', id: 1 },
  { kind: 'PLAN_TASK', id: 2 },
  { kind: 'LOAN', id: 3, source: 'LOAN', readOnly: true },
  { kind: 'FINANCE', id: 4, source: 'FINANCE', readOnly: true },
  { kind: 'ITEM_EXPIRY', id: 5, source: 'ITEM_EXPIRY', readOnly: true },
]

describe('scheduleSource 偏好', () => {
  it('空存储回落全开', () => {
    expect(loadSourcePrefs(memoryStorage())).toEqual(defaultSourcePrefs())
  })

  it('坏 JSON / 非布尔值回落全开', () => {
    expect(loadSourcePrefs(memoryStorage({ [SOURCE_STORAGE_KEY]: '{oops' }))).toEqual(defaultSourcePrefs())
    const prefs = loadSourcePrefs(memoryStorage({ [SOURCE_STORAGE_KEY]: '{"LOAN":"no"}' }))
    expect(prefs.LOAN).toBe(true)
  })

  it('只读取认识的键,布尔值生效', () => {
    const prefs = loadSourcePrefs(memoryStorage({ [SOURCE_STORAGE_KEY]: '{"LOAN":false,"UNKNOWN":false}' }))
    expect(prefs.LOAN).toBe(false)
    expect(prefs.FINANCE).toBe(true)
    expect(prefs).not.toHaveProperty('UNKNOWN')
  })

  it('保存写入约定键;存储抛错不影响调用方', () => {
    const storage = memoryStorage()
    saveSourcePrefs({ ...defaultSourcePrefs(), FINANCE: false }, storage)
    expect(storage.setItem).toHaveBeenCalledWith(SOURCE_STORAGE_KEY, expect.stringContaining('"FINANCE":false'))
    const broken = {
      getItem: () => {
        throw new Error('denied')
      },
      setItem: () => {
        throw new Error('denied')
      },
    }
    expect(() => saveSourcePrefs(defaultSourcePrefs(), broken)).not.toThrow()
    expect(loadSourcePrefs(broken)).toEqual(defaultSourcePrefs())
  })
})

describe('scheduleSource 来源判定与过滤', () => {
  it('计划子任务由 kind 推导来源,其余日程无来源', () => {
    expect(sourceOf({ kind: 'PLAN_TASK' })).toBe('PLAN_TASK')
    expect(sourceOf({ kind: 'LOAN', source: 'LOAN' })).toBe('LOAN')
    expect(sourceOf({ kind: 'PLAN' })).toBe('')
    expect(sourceOf(null)).toBe('')
  })

  it('只有贷款/理财/食材算只读,计划子任务仍可勾完成', () => {
    expect(isReadOnlySource({ kind: 'LOAN' })).toBe(true)
    expect(isReadOnlySource({ kind: 'FINANCE' })).toBe(true)
    expect(isReadOnlySource({ kind: 'ITEM_EXPIRY' })).toBe(true)
    expect(isReadOnlySource({ kind: 'PLAN_TASK' })).toBe(false)
    expect(isReadOnlySource({ kind: 'ANNIVERSARY' })).toBe(false)
  })

  it('关闭的来源整类隐藏,无来源日程始终保留', () => {
    const prefs = { ...defaultSourcePrefs(), LOAN: false, ITEM_EXPIRY: false }
    const kept = filterSourceItems(items(), prefs).map((it) => it.kind)
    expect(kept).toEqual(['ANNIVERSARY', 'PLAN_TASK', 'FINANCE'])
  })

  it('全开时一项不少,且不改入参', () => {
    const source = items()
    const snapshot = JSON.stringify(source)
    expect(filterSourceItems(source, defaultSourcePrefs())).toHaveLength(source.length)
    expect(JSON.stringify(source)).toBe(snapshot)
  })

  it('filterEventDays 保留日期键,只裁来源项', () => {
    const days = { '2026-10-30': items(), '2026-10-31': [] }
    const prefs = { ...defaultSourcePrefs(), FINANCE: false, PLAN_TASK: false }
    const out = filterEventDays(days, prefs)
    expect(Object.keys(out)).toEqual(['2026-10-30', '2026-10-31'])
    expect(out['2026-10-30'].map((it) => it.kind)).toEqual(['ANNIVERSARY', 'LOAN', 'ITEM_EXPIRY'])
    expect(out['2026-10-31']).toEqual([])
  })
})
