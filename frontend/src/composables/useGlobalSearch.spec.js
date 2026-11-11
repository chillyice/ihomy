import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import {
  SEARCH_DEBOUNCE_MS,
  SEARCH_LIMIT,
  buildSearchItem,
  createSearchState,
  matchFields,
  mergeResults,
  normalizeQuery,
} from './useGlobalSearch'

const settle = async (n = 20) => {
  for (let i = 0; i < n; i++) await Promise.resolve()
}

const blog = (over = {}) => ({ id: 1, title: 'Hello blog', createdAt: '2026-11-02T08:00:00', ...over })

function makeDeps(over = {}) {
  const api = {
    blogApi: { list: vi.fn().mockResolvedValue({ records: [blog()] }) },
    diaryApi: {
      list: vi.fn().mockResolvedValue({
        records: [{ id: 2, content: 'Hello diary', authorId: 7, authorName: 'Ann', createdAt: '2026-11-01T09:00:00' }],
      }),
    },
    albumApi: { list: vi.fn().mockResolvedValue([{ id: 3, name: 'Hello album', photoCount: 4 }]) },
    memberApi: { list: vi.fn().mockResolvedValue([{ id: 9, nickname: 'Helen', username: 'helen' }]) },
    wishApi: { list: vi.fn().mockResolvedValue([{ id: 4, title: 'Hello wish', reason: 'because' }]) },
    storageApi: { browse: vi.fn().mockResolvedValue([{ name: 'hello.txt', isDir: false }]) },
  }
  return { api, isGuest: () => false, ...over }
}

describe('normalizeQuery', () => {
  it('去首尾空白并转小写', () => {
    expect(normalizeQuery('  HeLLo  ')).toBe('hello')
    expect(normalizeQuery(null)).toBe('')
    expect(normalizeQuery(undefined)).toBe('')
    expect(normalizeQuery(12)).toBe('12')
  })
})

describe('matchFields', () => {
  const row = { title: 'Hello World', content: 'Nothing here' }

  it('大小写不敏感子串,多字段任一命中', () => {
    expect(matchFields(row, 'hello', ['title'])).toBe(true)
    expect(matchFields(row, 'HELLO', ['title'])).toBe(true)
    expect(matchFields(row, 'nothing', ['title', 'content'])).toBe(true)
  })

  it('空查询、空对象、字段缺失都不命中', () => {
    expect(matchFields(row, '   ', ['title'])).toBe(false)
    expect(matchFields(null, 'hello', ['title'])).toBe(false)
    expect(matchFields(row, 'hello', ['missing'])).toBe(false)
    expect(matchFields(row, 'hello', null)).toBe(false)
  })
})

describe('buildSearchItem', () => {
  it('统一结构:source 前缀 id、label/sub/path 齐备', () => {
    const item = buildSearchItem('blog', blog(), {
      labelFields: ['title'],
      sub: (r) => r.createdAt.slice(0, 10),
      path: (r) => `/blog/${r.id}`,
    })
    expect(item).toEqual({
      id: 'blog-1',
      group: 'search',
      source: 'blog',
      label: 'Hello blog',
      sub: '2026-11-02',
      path: '/blog/1',
    })
  })

  it('label 取不到时退回 #id,不出现 undefined', () => {
    const item = buildSearchItem('album', { id: 12 }, { labelFields: ['name'] })
    expect(item.label).toBe('#12')
    expect(item.sub).toBe('')
    expect(item.path).toBe('')
    expect(Object.values(item).every((v) => v !== undefined)).toBe(true)
  })

  it('label 函数可截断长正文(日记用)', () => {
    const item = buildSearchItem('diary', { id: 5, content: 'x'.repeat(80) }, { label: (r) => r.content.slice(0, 4) })
    expect(item.label).toBe('xxxx')
  })
})

describe('mergeResults', () => {
  const item = (id) => ({ id })
  const group = (source, n) => ({ source, items: Array.from({ length: n }, (_, i) => item(`${source}-${i}`)) })

  it('过滤空组并保持组的顺序', () => {
    const out = mergeResults([group('blog', 2), { source: 'diary', items: [] }, group('album', 1)])
    expect(out.map((g) => g.source)).toEqual(['blog', 'album'])
    expect(out[0].items.map((i) => i.id)).toEqual(['blog-0', 'blog-1'])
  })

  it('组内最多保留 SEARCH_LIMIT 条(截断)', () => {
    const out = mergeResults([group('wish', 8)])
    expect(out[0].items).toHaveLength(SEARCH_LIMIT)
    expect(out[0].items[0].id).toBe('wish-0')
  })

  it('非数组输入返回空数组', () => {
    expect(mergeResults(null)).toEqual([])
  })
})

describe('createSearchState', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  async function search(state, q, ms = SEARCH_DEBOUNCE_MS + 50) {
    state.run(q)
    await vi.advanceTimersByTimeAsync(ms)
    await settle()
  }

  it('少于 2 个字不发任何请求', async () => {
    const deps = makeDeps()
    const state = createSearchState(deps)
    await search(state, 'h')
    expect(deps.api.blogApi.list).not.toHaveBeenCalled()
    expect(deps.api.diaryApi.list).not.toHaveBeenCalled()
    expect(state.groups.value).toEqual([])
    expect(state.loading.value).toBe(false)
  })

  it('2 个字起并发查 6 类,按固定分组顺序返回', async () => {
    const deps = makeDeps()
    const state = createSearchState(deps)
    state.setFileContext({ deviceId: 1, path: 'docs' })
    await search(state, 'he')
    expect(deps.api.blogApi.list).toHaveBeenCalledWith({ current: 1, size: 5, keyword: 'he' })
    expect(state.groups.value.map((g) => g.source)).toEqual(['blog', 'diary', 'album', 'file', 'member', 'wish'])
  })

  it('防抖:连续输入只查最后一次', async () => {
    const deps = makeDeps()
    const state = createSearchState(deps)
    state.run('he')
    state.run('hel')
    state.run('hell')
    await vi.advanceTimersByTimeAsync(SEARCH_DEBOUNCE_MS + 50)
    await settle()
    expect(deps.api.blogApi.list).toHaveBeenCalledTimes(1)
    expect(deps.api.blogApi.list).toHaveBeenCalledWith({ current: 1, size: 5, keyword: 'hell' })
  })

  it('过期响应被丢弃:先发的慢响应不会覆盖后一次结果', async () => {
    let releaseFirst
    const deps = makeDeps()
    deps.api.blogApi.list = vi
      .fn()
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            releaseFirst = () => resolve({ records: [blog({ id: 11, title: 'aa first' })] })
          }),
      )
      .mockResolvedValue({ records: [blog({ id: 22, title: 'bb second' })] })
    const state = createSearchState(deps)

    state.run('aa')
    await vi.advanceTimersByTimeAsync(SEARCH_DEBOUNCE_MS + 10)
    await settle()

    state.run('bb')
    await vi.advanceTimersByTimeAsync(SEARCH_DEBOUNCE_MS + 10)
    await settle()

    releaseFirst()
    await settle()

    const blogGroup = state.groups.value.find((g) => g.source === 'blog')
    expect(blogGroup.items.map((i) => i.id)).toEqual(['blog-22'])
  })

  it('单类失败降级为空,其它类照常返回', async () => {
    const deps = makeDeps()
    deps.api.albumApi.list = vi.fn().mockRejectedValue(new Error('boom'))
    const state = createSearchState(deps)
    await search(state, 'he')
    expect(state.groups.value.map((g) => g.source)).not.toContain('album')
    expect(state.groups.value.map((g) => g.source)).toContain('blog')
    expect(state.groups.value.map((g) => g.source)).toContain('wish')
  })

  it('没有文件页上下文时不发 browse,也不出现文件组', async () => {
    const deps = makeDeps()
    const state = createSearchState(deps)
    await search(state, 'he')
    expect(deps.api.storageApi.browse).not.toHaveBeenCalled()
    expect(state.groups.value.map((g) => g.source)).not.toContain('file')
  })

  it('文件页上下文下搜当前目录,结果落到该目录', async () => {
    const deps = makeDeps()
    const state = createSearchState(deps)
    state.setFileContext({ deviceId: 3, path: 'docs' })
    await search(state, 'he')
    expect(deps.api.storageApi.browse).toHaveBeenCalledWith(3, 'docs')
    const group = state.groups.value.find((g) => g.source === 'file')
    expect(group.items).toHaveLength(1)
    expect(group.items[0].path).toBe('/storage/files?deviceId=3&path=docs')
  })

  it('游客不发成员请求', async () => {
    const deps = makeDeps({ isGuest: () => true })
    const state = createSearchState(deps)
    await search(state, 'he')
    expect(deps.api.memberApi.list).not.toHaveBeenCalled()
    expect(state.groups.value.map((g) => g.source)).not.toContain('member')
  })

  it('reset 清空结果,并把查询缩短到 1 个字也会清空', async () => {
    const deps = makeDeps()
    const state = createSearchState(deps)
    await search(state, 'he')
    expect(state.groups.value.length).toBeGreaterThan(0)

    state.reset()
    expect(state.groups.value).toEqual([])
    expect(state.loading.value).toBe(false)

    await search(state, 'he')
    await search(state, 'h')
    expect(state.groups.value).toEqual([])
  })

  it('dispose 同时清空文件上下文', async () => {
    const deps = makeDeps()
    const state = createSearchState(deps)
    state.setFileContext({ deviceId: 1, path: 'docs' })
    expect(state.fileContext.value).toEqual({ deviceId: 1, path: 'docs' })
    state.dispose()
    expect(state.fileContext.value).toBeNull()
  })
})
