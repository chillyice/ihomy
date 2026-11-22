import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { homeApi, publicApi } from '@/api'
import { useAppStore } from './app'

const user = vi.hoisted(() => ({ isLoggedIn: false }))
vi.mock('./user', () => ({ useUserStore: () => user }))
vi.mock('@/api', () => ({ publicApi: { getHome: vi.fn() }, homeApi: { getDashboard: vi.fn() } }))

describe('family home initialization', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.resetAllMocks()
    vi.stubGlobal('window', { location: { search: '' } })
    user.isLoggedIn = false
  })

  it('can recover from an offline first visit', async () => {
    publicApi.getHome.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce({ family: { id: 1 } })
    const app = useAppStore()
    await app.init()
    expect(app.loadFailed).toBe(true)
    await app.init()
    expect(app.loadFailed).toBe(false)
    expect(app.family.id).toBe(1)
    expect(homeApi.getDashboard).not.toHaveBeenCalled()
  })

  it('retries the missing part and retains family information', async () => {
    user.isLoggedIn = true
    publicApi.getHome.mockResolvedValue({ family: { id: 2 }, modules: ['public'] })
    homeApi.getDashboard.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce({ modules: ['private'] })
    const app = useAppStore()
    await app.init()
    expect(app.modules).toEqual(['public'])
    expect(app.loadFailed).toBe(true)
    await app.init()
    expect(app.modules).toEqual(['private'])
    expect(publicApi.getHome).toHaveBeenCalledTimes(1)
    expect(app.loadFailed).toBe(false)
  })

  it('drops old in-flight responses when the family changes', async () => {
    let finishOld
    publicApi.getHome.mockImplementationOnce(() => new Promise((resolve) => (finishOld = resolve)))
    const app = useAppStore()
    const old = app.init()
    app.reset()
    publicApi.getHome.mockResolvedValueOnce({ family: { id: 2 } })
    await app.init()
    finishOld({ family: { id: 1 } })
    await old
    expect(app.family.id).toBe(2)
    expect(app.loadFailed).toBe(false)
  })

  it('keeps the shared home modules for a member visiting a different family', async () => {
    user.isLoggedIn = true
    window.location.search = '?hid=shared-home'
    publicApi.getHome.mockResolvedValue({ family: { id: 8 }, modules: ['album'] })
    const app = useAppStore()
    await app.init()
    expect(publicApi.getHome).toHaveBeenCalledWith(undefined, 'shared-home')
    expect(homeApi.getDashboard).not.toHaveBeenCalled()
    expect(app.modules).toEqual(['album'])
  })

  it('refreshes only the current photo strip, with share context, and drops late photos after switching homes', async () => {
    const app = useAppStore()
    user.isLoggedIn = true
    window.location.search = '?hid=shared-home'
    publicApi.getHome.mockResolvedValueOnce({ family: { id: 8 }, photos: [{ id: 1 }] })
    await app.init()
    const late = Promise.withResolvers()
    publicApi.getHome.mockReturnValueOnce(late.promise)
    const pending = app.refreshPhotos()
    const [homeId, hid, config] = publicApi.getHome.mock.lastCall
    expect([homeId, hid, config.isBackgroundRead()]).toEqual([undefined, 'shared-home', true])
    app.reset()
    window.location.search = ''
    publicApi.getHome.mockResolvedValueOnce({ family: { id: 9 }, photos: [{ id: 7 }] })
    homeApi.getDashboard.mockResolvedValueOnce({ modules: [] })
    await app.init()
    late.resolve({ family: { id: 8 }, photos: [{ id: 3 }] })
    await pending
    expect(app.photos).toEqual([{ id: 7 }])
    publicApi.getHome.mockRejectedValueOnce(new Error('offline'))
    await expect(app.refreshPhotos()).rejects.toThrow('offline')
    expect(app.familyLoaded).toBe(true)
    expect(app.family.id).toBe(9)
    expect(homeApi.getDashboard).toHaveBeenCalledTimes(1)
  })
})
