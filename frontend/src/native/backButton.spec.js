import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { App } from '@capacitor/app'
import router from '@/router'
import { initBackButton } from './backButton'

vi.mock('@capacitor/app', () => ({ App: { addListener: vi.fn(), minimizeApp: vi.fn() } }))
vi.mock('@/stores/user', () => ({ useUserStore: () => ({ isPureOps: false }) }))
vi.mock('@/router', () => ({
  default: {
    currentRoute: { value: {} },
    getRoutes: () => [{ path: '/home' }, { path: '/blog' }, { path: '/blog/:id' }],
    replace: vi.fn(),
    back: vi.fn(),
  },
}))

let pressBack

function openPage(fullPath) {
  router.currentRoute.value = { path: fullPath.split('?')[0], fullPath, query: {}, meta: {} }
}

beforeEach(async () => {
  vi.clearAllMocks()
  vi.stubGlobal('window', Object.assign(new EventTarget(), { history: { state: null } }))
  vi.stubGlobal('document', { querySelectorAll: vi.fn().mockReturnValue([]) })
  await initBackButton()
  const [name, handler] = App.addListener.mock.lastCall
  expect(name).toBe('backButton')
  pressBack = handler
})

afterEach(() => vi.unstubAllGlobals())

describe('Android 系统返回', () => {
  it('从其他模块来过也按详情、列表、首页依次返回', async () => {
    window.history.state = { back: '/album/9' }
    openPage('/blog/42')
    await pressBack({ canGoBack: true })
    expect(router.replace).toHaveBeenLastCalledWith('/blog')
    openPage('/blog')
    await pressBack({ canGoBack: true })
    expect(router.replace).toHaveBeenLastCalledWith('/home')
    expect(router.back).not.toHaveBeenCalled()
    expect(App.minimizeApp).not.toHaveBeenCalled()
  })

  it('首页单次返回即退到系统桌面，即使仍有 WebView 历史', async () => {
    openPage('/home')
    await pressBack({ canGoBack: true })
    expect(App.minimizeApp).toHaveBeenCalledTimes(1)
    expect(router.back).not.toHaveBeenCalled()
    expect(router.replace).not.toHaveBeenCalled()
  })

  it('从深链首次打开详情也能回到列表', async () => {
    openPage('/blog/42')
    await pressBack({ canGoBack: false })
    expect(router.replace).toHaveBeenCalledWith('/blog')
    expect(App.minimizeApp).not.toHaveBeenCalled()
  })

  it('可见弹窗先处理返回', async () => {
    openPage('/blog/42')
    const closeDialog = vi.fn()
    const onPageBack = vi.fn()
    window.addEventListener('ihomy:app-back', onPageBack)
    vi.stubGlobal('getComputedStyle', () => ({ zIndex: '200' }))
    document.querySelectorAll.mockReturnValue([{ getClientRects: () => [{}], querySelector: () => ({ click: closeDialog }) }])
    await pressBack({ canGoBack: true })
    expect(closeDialog).toHaveBeenCalledOnce()
    expect(onPageBack).not.toHaveBeenCalled()
    expect(router.replace).not.toHaveBeenCalled()
    expect(router.back).not.toHaveBeenCalled()
    expect(App.minimizeApp).not.toHaveBeenCalled()
  })

  it('页面消费返回后仍停在当前路由', async () => {
    openPage('/blog/42')
    const consume = vi.fn((event) => event.preventDefault())
    window.addEventListener('ihomy:app-back', consume)
    await pressBack({ canGoBack: true })
    expect(consume).toHaveBeenCalledOnce()
    expect(router.replace).not.toHaveBeenCalled()
    expect(router.back).not.toHaveBeenCalled()
    expect(App.minimizeApp).not.toHaveBeenCalled()
  })
})
