import { beforeEach, describe, it, expect, vi } from 'vitest'

vi.mock('@/utils/userPref', () => ({ readPref: vi.fn(), readRemotePrefMap: vi.fn(), writePref: vi.fn() }))
let user
vi.mock('@/stores/user', () => ({ useUserStore: () => user }))
import { readRemotePrefMap, writePref } from '@/utils/userPref'

import {
  MAX_DOING_ITEMS,
  LAST_DOING_PREF_KEY,
  buildDoingPath,
  initLastDoing,
  lastDoingMode,
  mergeDoing,
  normalizeDoingEntry,
  parseDoingStore,
  recordDoing,
  resetLastDoing,
  serializeDoingStore,
  useLastDoing,
  visibleDoing,
} from './lastDoing'

const NOW = Date.UTC(2026, 10, 23, 8, 0, 0)
const HOUR = 60 * 60 * 1000

const book = { kind: 'BOOK', id: 12, title: '雪国', at: NOW, progress: 42 }

describe('initLastDoing', () => {
  const remoteHistory = (uid, entries) => ({ [LAST_DOING_PREF_KEY]: serializeDoingStore(entries, uid) })

  beforeEach(() => {
    resetLastDoing()
    vi.resetAllMocks()
    user = { isLoggedIn: true, userInfo: { id: 7 } }
  })

  it('退出后忽略未完成的请求', async () => {
    const request = Promise.withResolvers()
    readRemotePrefMap.mockReturnValueOnce(request.promise)
    const loading = initLastDoing()
    user = { isLoggedIn: false, userInfo: null }
    resetLastDoing()
    request.resolve(remoteHistory(7, [book]))
    await loading
    expect(useLastDoing().items.value).toEqual([])
    expect(writePref).not.toHaveBeenCalled()
  })

  it.each([7, 8])('重新登录账号 %i 后忽略上次登录的响应', async (uid) => {
    const request = Promise.withResolvers()
    readRemotePrefMap.mockReturnValueOnce(request.promise)
    const loading = initLastDoing()
    resetLastDoing()
    user.userInfo.id = uid
    const current = { ...book, id: 24 }
    readRemotePrefMap.mockResolvedValueOnce(remoteHistory(uid, [current]))
    await initLastDoing()
    request.resolve(remoteHistory(7, [book]))
    await loading
    expect(useLastDoing().items.value).toEqual([current])
    expect(writePref).not.toHaveBeenCalled()
  })

  it('合并等待远端响应期间刚发生的续做', async () => {
    const request = Promise.withResolvers()
    readRemotePrefMap.mockReturnValueOnce(request.promise)
    const loading = initLastDoing()
    const current = { ...book, id: 24, at: NOW + HOUR }
    recordDoing(current)
    request.resolve(remoteHistory(7, [book]))
    await loading
    expect(useLastDoing().items.value).toEqual([current, book])
    expect(parseDoingStore(writePref.mock.lastCall[2], 7)).toEqual([current, book])
  })
})

describe('normalizeDoingEntry', () => {
  it('大写 kind 并夹紧进度', () => {
    const e = normalizeDoingEntry({ kind: 'book', id: '12', title: ' 雪国 ', at: NOW, progress: 186 })
    expect(e).toEqual({ kind: 'BOOK', id: 12, title: '雪国', at: NOW, progress: 100 })
  })

  it('缺 kind/id/title/at 或 kind 未知返回 null', () => {
    expect(normalizeDoingEntry({ kind: 'BOOK', id: 1, at: NOW })).toBeNull()
    expect(normalizeDoingEntry({ kind: 'VIDEO', id: 1, title: 'x', at: NOW })).toBeNull()
    expect(normalizeDoingEntry({ kind: 'BOOK', id: 0, title: 'x', at: NOW })).toBeNull()
    expect(normalizeDoingEntry(null)).toBeNull()
  })

  it('保留影视分集与日记作者', () => {
    const e = normalizeDoingEntry({
      kind: 'CINEMA',
      id: 8,
      title: '长片',
      at: NOW,
      episodeId: 'ep-3',
      episodeCode: 'S01E03',
      authorId: '6',
    })
    expect(e.episodeId).toBe('ep-3')
    expect(e.episodeCode).toBe('S01E03')
    expect(e.authorId).toBe(6)
  })

  it('媒体服务器 id 是十六进制串,按字符串保留', () => {
    const guid = 'd41d8cd98f00b204e9800998ecf8427e'
    const e = normalizeDoingEntry({ kind: 'CINEMA', id: guid, title: '剧集', at: NOW })
    expect(e.id).toBe(guid)
  })
})

describe('mergeDoing', () => {
  it('同 kind+id 取较新的一条,不重复', () => {
    const newer = { ...book, at: NOW + HOUR, progress: 55 }
    const out = mergeDoing([book], newer)
    expect(out).toHaveLength(1)
    expect(out[0].at).toBe(NOW + HOUR)
    expect(out[0].progress).toBe(55)
  })

  it('新记录缺的附加字段沿用旧记录', () => {
    const older = { kind: 'DIARY', id: 3, title: '雨', at: NOW, authorId: 6 }
    const newer = { kind: 'DIARY', id: 3, title: '雨', at: NOW + HOUR }
    expect(mergeDoing([older], newer)[0].authorId).toBe(6)
  })

  it('迟到的旧事件不覆盖较新记录', () => {
    const newer = { ...book, at: NOW + HOUR, progress: 90 }
    const out = mergeDoing([newer], book)
    expect(out[0].at).toBe(NOW + HOUR)
    expect(out[0].progress).toBe(90)
  })

  it('按 at 倒序并截断到上限', () => {
    let list = []
    for (let i = 0; i < MAX_DOING_ITEMS + 3; i += 1) {
      list = mergeDoing(list, { kind: 'BLOG', id: i + 1, title: `第 ${i} 篇`, at: NOW + i * 1000 })
    }
    expect(list).toHaveLength(MAX_DOING_ITEMS)
    expect(list[0].id).toBe(MAX_DOING_ITEMS + 3)
  })

  it('非法记录被丢弃', () => {
    const out = mergeDoing(
      [
        { kind: 'BOOK', id: 1, title: 'ok', at: NOW },
        { kind: 'NOPE', id: 2, title: 'x', at: NOW },
      ],
      book,
    )
    expect(out).toHaveLength(2)
    expect(out.every((it) => it.kind === 'BOOK')).toBe(true)
  })
})

describe('visibleDoing', () => {
  it('超过 24 小时的记录不再展示,未来时钟偏移容忍 5 分钟', () => {
    const items = [
      { ...book, id: 1, at: NOW },
      { ...book, id: 2, at: NOW - 23 * HOUR },
      { ...book, id: 3, at: NOW - 25 * HOUR },
      { ...book, id: 4, at: NOW + 2 * 60 * 1000 },
      { ...book, id: 5, at: NOW + 10 * 60 * 1000 },
    ]
    expect(visibleDoing(items, NOW).map((it) => it.id)).toEqual([1, 2, 4])
  })
})

describe('buildDoingPath', () => {
  it('四种内容的跳转地址', () => {
    expect(buildDoingPath(book)).toBe('/library?book=12')
    expect(buildDoingPath({ kind: 'CINEMA', id: 9 })).toBe('/cinema/9')
    expect(buildDoingPath({ kind: 'CINEMA', id: 9, episodeId: 'ep 1' })).toBe('/cinema/9?ep=ep%201')
    expect(buildDoingPath({ kind: 'BLOG', id: 4 })).toBe('/blog/4')
    expect(buildDoingPath({ kind: 'DIARY', id: 5, authorId: 6 })).toBe('/diary/book/6?entry=5')
    expect(buildDoingPath({ kind: 'DIARY', id: 5 })).toBe('/diary')
  })
})

describe('lastDoingMode', () => {
  it('手机移动布局 → mobile,平板/窄窗口 → float,宽屏电脑 → dock', () => {
    expect(lastDoingMode({ isMobile: true, isTablet: false, isWide: false })).toBe('mobile')
    expect(lastDoingMode({ isMobile: false, isTablet: false, isWide: false })).toBe('float')
    expect(lastDoingMode({ isMobile: false, isTablet: true, isWide: true })).toBe('float')
    expect(lastDoingMode({ isMobile: false, isTablet: false, isWide: true })).toBe('dock')
  })
})

describe('parseDoingStore / serializeDoingStore', () => {
  it('uid 不符视为空,避免换号串数据', () => {
    const raw = serializeDoingStore([book], 7)
    expect(parseDoingStore(raw, 7)).toHaveLength(1)
    expect(parseDoingStore(raw, 8)).toEqual([])
  })

  it('损坏的 JSON 与旧格式都按空处理', () => {
    expect(parseDoingStore('{oops', 7)).toEqual([])
    expect(parseDoingStore('{"uid":7,"items":[{"kind":"BOOK"}]}', 7)).toEqual([])
  })
})
