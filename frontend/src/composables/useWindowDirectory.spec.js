import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope, nextTick, reactive, ref } from 'vue'
import { WINDOW_PREF } from '@/utils/windowDirectory'
import { useWindowDirectory } from './useWindowDirectory'
const shared = vi.hoisted(() => ({ user: null, app: null, all: vi.fn(), save: vi.fn() }))
vi.mock('@/api', () => ({ userPrefApi: { all: shared.all, save: shared.save } }))
vi.mock('@/stores/user', () => ({ useUserStore: () => shared.user }))
vi.mock('@/stores/app', () => ({ useAppStore: () => shared.app }))
vi.mock('./useDisplayMode', () => ({ useDisplayMode: () => ({ isChild: ref(false) }) }))
vi.mock('vue-router', () => ({ useRouter: () => ({ resolve: () => ({ meta: {} }) }) }))
vi.mock('vue-i18n', () => ({ useI18n: () => ({ t: (k) => k, te: () => false, locale: ref('zh-CN') }) }))

describe('window favorites by account', () => {
  let scope
  let nav
  beforeEach(() => {
    shared.all.mockReset()
    shared.save.mockReset()
    const storage = new Map()
    vi.stubGlobal('localStorage', { getItem: (k) => storage.get(k) ?? null, setItem: (k, v) => storage.set(k, v) })
    shared.user = reactive({ isLoggedIn: false, userInfo: null, canReadBook: true, hasPerm: () => false })
    shared.app = reactive({
      modules: [
        { code: 'music', title: 'Music' },
        { code: 'item', title: 'Find' },
      ],
    })
    scope = effectScope()
    nav = scope.run(() => useWindowDirectory())
  })
  afterEach(() => {
    scope.stop()
    vi.unstubAllGlobals()
  })
  const settle = async () => {
    await Promise.resolve()
    await nextTick()
  }
  it('keeps guest edits local, restores remote order after login, and remembers removing every entry', async () => {
    await nav.save(['item'])
    expect(shared.save).not.toHaveBeenCalled()
    shared.all.mockResolvedValue({ [WINDOW_PREF]: '["music","item"]' })
    shared.user.userInfo = { id: 7 }
    shared.user.isLoggedIn = true
    await settle()
    expect(nav.favorites.value.map((i) => i.code)).toEqual(['music', 'item'])
    await nav.save([])
    expect(shared.save).toHaveBeenCalledWith(WINDOW_PREF, '[]')
    shared.user.isLoggedIn = false
    expect(nav.codes.value).toEqual(['item'])
    shared.all.mockResolvedValue({ [WINDOW_PREF]: '[]' })
    shared.user.isLoggedIn = true
    await settle()
    expect(nav.codes.value).toEqual([])
  })
  it('drops a late reply from the previous account after switching or logging out', async () => {
    let resolveOld
    shared.all
      .mockReturnValueOnce(
        new Promise((resolve) => {
          resolveOld = resolve
        }),
      )
      .mockResolvedValueOnce({ [WINDOW_PREF]: '["item"]' })
    shared.user.userInfo = { id: 11 }
    shared.user.isLoggedIn = true
    shared.user.userInfo = { id: 12 }
    await settle()
    resolveOld({ [WINDOW_PREF]: '["music"]' })
    await settle()
    expect(nav.codes.value).toEqual(['item'])
    expect(localStorage.getItem('ihomy:' + WINDOW_PREF + ':12')).toBe('["item"]')
  })
})
