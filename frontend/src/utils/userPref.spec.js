import { beforeEach, describe, expect, it, vi } from 'vitest'

// '@/api' 与 '@/stores/user' 都打桩:这里只验证同步决策与"本机缓存 / 远端上传"的交互
const api = { all: vi.fn(), save: vi.fn() }
let user = { isLoggedIn: false }
vi.mock('@/api', () => ({ userPrefApi: api }))
vi.mock('@/stores/user', () => ({ useUserStore: () => user }))

const LOCAL_KEY = 'ihomy:dashboard:layout:v3'
const SERVER_KEY = 'home.layout.v3'

function fakeStorage(initial = {}) {
  const map = new Map(Object.entries(initial))
  return {
    getItem: (k) => (map.has(k) ? map.get(k) : null),
    setItem: (k, v) => map.set(k, String(v)),
    removeItem: (k) => map.delete(k),
    get size() {
      return map.size
    },
  }
}

let pref

beforeEach(async () => {
  vi.resetModules()
  api.all.mockReset()
  api.save.mockReset()
  user = { isLoggedIn: false }
  pref = await import('@/utils/userPref')
})

describe('decideSync', () => {
  it('本机没有、服务端有 → 用服务端', () => {
    expect(pref.decideSync(null, '[]')).toBe('use-remote')
  })

  it('两边都没有 → 不动', () => {
    expect(pref.decideSync(null, null)).toBe('none')
  })

  it('两边一致 → 不动', () => {
    expect(pref.decideSync('[]', '[]')).toBe('none')
  })

  it('本机有、服务端没有 → 首次迁移,推本机', () => {
    expect(pref.decideSync('[1]', null)).toBe('push-local')
  })

  it('两边不同 → 服务端为准(别的设备改过)', () => {
    expect(pref.decideSync('[1]', '[2]')).toBe('use-remote')
  })

  it('上次没传成功 → 本机为准补传,别被服务端旧值盖掉', () => {
    expect(pref.decideSync('[1]', '[2]', true)).toBe('push-local')
  })
})

describe('readPref / writePref', () => {
  it('读不到时返回 null', () => {
    expect(pref.readPref(LOCAL_KEY, fakeStorage())).toBeNull()
  })

  it('游客只写本机,不发请求', async () => {
    const storage = fakeStorage()
    pref.writePref(LOCAL_KEY, SERVER_KEY, '[1]', storage)
    expect(pref.readPref(LOCAL_KEY, storage)).toBe('[1]')
    expect(api.save).not.toHaveBeenCalled()
  })

  it('登录后去抖上传,连续改动只发最后一次', async () => {
    vi.useFakeTimers()
    user = { isLoggedIn: true }
    api.save.mockResolvedValue({})
    const storage = fakeStorage()

    pref.writePref(LOCAL_KEY, SERVER_KEY, '[1]', storage)
    pref.writePref(LOCAL_KEY, SERVER_KEY, '[2]', storage)
    pref.writePref(LOCAL_KEY, SERVER_KEY, '[3]', storage)
    expect(api.save).not.toHaveBeenCalled()

    await vi.advanceTimersByTimeAsync(400)
    expect(api.save).toHaveBeenCalledTimes(1)
    expect(api.save).toHaveBeenCalledWith(SERVER_KEY, '[3]')
    vi.useRealTimers()
  })

  it('内容与上次上传相同则不再发(进页面时不会平白写库)', async () => {
    vi.useFakeTimers()
    user = { isLoggedIn: true }
    api.save.mockResolvedValue({})
    const storage = fakeStorage()

    pref.writePref(LOCAL_KEY, SERVER_KEY, '[1]', storage)
    await vi.advanceTimersByTimeAsync(400)
    pref.writePref(LOCAL_KEY, SERVER_KEY, '[1]', storage)
    await vi.advanceTimersByTimeAsync(400)

    expect(api.save).toHaveBeenCalledTimes(1)
    vi.useRealTimers()
  })
})

describe('syncPref', () => {
  it('游客不发请求,返回 null', async () => {
    expect(await pref.syncPref(LOCAL_KEY, SERVER_KEY, fakeStorage())).toBeNull()
    expect(api.all).not.toHaveBeenCalled()
  })

  it('服务端有值:覆盖本机缓存并把值交给页面应用', async () => {
    user = { isLoggedIn: true }
    api.all.mockResolvedValue({ [SERVER_KEY]: '[9]' })
    const storage = fakeStorage({ [LOCAL_KEY]: '[1]' })

    expect(await pref.syncPref(LOCAL_KEY, SERVER_KEY, storage)).toBe('[9]')
    expect(pref.readPref(LOCAL_KEY, storage)).toBe('[9]')
    expect(api.save).not.toHaveBeenCalled()
  })

  it('服务端没有而本机有:把本机布局交给账号(首次迁移)', async () => {
    user = { isLoggedIn: true }
    api.all.mockResolvedValue({})
    api.save.mockResolvedValue({})
    const storage = fakeStorage({ [LOCAL_KEY]: '[1]' })

    expect(await pref.syncPref(LOCAL_KEY, SERVER_KEY, storage)).toBeNull()
    expect(api.save).toHaveBeenCalledWith(SERVER_KEY, '[1]')
  })

  it('本机有未同步标记:即使服务端有值也以本机为准补传', async () => {
    user = { isLoggedIn: true }
    api.all.mockResolvedValue({ [SERVER_KEY]: '[2]' })
    api.save.mockResolvedValue({})
    const storage = fakeStorage({ [LOCAL_KEY]: '[1]', 'ihomy:pref:unsynced:ihomy:dashboard:layout:v3': '1' })

    expect(await pref.syncPref(LOCAL_KEY, SERVER_KEY, storage)).toBeNull()
    expect(api.save).toHaveBeenCalledWith(SERVER_KEY, '[1]')
  })

  it('拉取失败时什么都不做(别把拿不到当成服务端没有)', async () => {
    user = { isLoggedIn: true }
    api.all.mockRejectedValue(new Error('offline'))
    const storage = fakeStorage({ [LOCAL_KEY]: '[1]' })

    expect(await pref.syncPref(LOCAL_KEY, SERVER_KEY, storage)).toBeNull()
    expect(api.save).not.toHaveBeenCalled()
    expect(pref.readPref(LOCAL_KEY, storage)).toBe('[1]')
  })

  it('上传失败留下未同步标记,下次还能补传', async () => {
    user = { isLoggedIn: true }
    api.all.mockResolvedValue({})
    api.save.mockRejectedValue(new Error('500'))
    const storage = fakeStorage({ [LOCAL_KEY]: '[1]' })

    await pref.syncPref(LOCAL_KEY, SERVER_KEY, storage)
    expect(pref.readPref('ihomy:pref:unsynced:' + LOCAL_KEY, storage)).toBe('1')
  })

  it('一次会话只拉一次偏好', async () => {
    user = { isLoggedIn: true }
    api.all.mockResolvedValue({})
    const storage = fakeStorage()

    await pref.syncPref(LOCAL_KEY, SERVER_KEY, storage)
    await pref.syncPref('ihomy:guangchen:home:v3', 'home.warm.v3', storage)

    expect(api.all).toHaveBeenCalledTimes(1)
  })
})

describe('flushPref', () => {
  it('页面隐藏时把挂着的那次上传立刻发出去', async () => {
    vi.useFakeTimers()
    user = { isLoggedIn: true }
    api.save.mockResolvedValue({})
    const storage = fakeStorage()

    pref.writePref(LOCAL_KEY, SERVER_KEY, '[5]', storage)
    pref.flushPref(SERVER_KEY)
    await vi.advanceTimersByTimeAsync(0)

    expect(api.save).toHaveBeenCalledWith(SERVER_KEY, '[5]')
    vi.useRealTimers()
  })
})
