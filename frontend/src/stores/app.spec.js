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
})
