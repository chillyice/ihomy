import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useAppIconStore } from './appIcon'
import { launcherIcon } from '@/native/appIcon'
import { isAndroidApp, isNativeApp } from '@/utils/platform'

vi.mock('@/native/appIcon', () => ({ launcherIcon: { getIcon: vi.fn(), setIcon: vi.fn() } }))
vi.mock('@/utils/platform', () => ({ isAndroidApp: vi.fn(), isNativeApp: vi.fn() }))

describe('device app icon', () => {
  let saved, links

  beforeEach(() => {
    setActivePinia(createPinia())
    vi.resetAllMocks()
    saved = new Map()
    links = {}
    vi.stubGlobal('localStorage', { getItem: (key) => saved.get(key), setItem: (key, value) => saved.set(key, value) })
    vi.stubGlobal('document', {
      querySelector: (selector) => ({ setAttribute: (_, href) => (links[selector] = href) }),
    })
  })

  afterEach(() => vi.unstubAllGlobals())

  it('uses the dark window for a fresh or unrecognized preference', async () => {
    saved.set('ihomy:app-icon', 'unknown')
    const store = useAppIconStore()
    await store.initialize()
    expect(store.id).toBe('window-dark')
    expect(links['link[rel="icon"]']).toBe('/app-icons/window-dark/icon-96.png')
  })

  it('keeps the web brand, touch icon and install manifest in sync across reloads', async () => {
    await useAppIconStore().select('leaf-light')
    setActivePinia(createPinia())
    const store = useAppIconStore()
    await store.initialize()
    expect(store.image).toBe('/app-icons/leaf-light/icon-96.png')
    expect(links).toEqual({
      'link[rel="icon"]': store.image,
      'link[rel="apple-touch-icon"]': '/app-icons/leaf-light/apple-touch.png',
      'link[rel="manifest"]': '/app-icons/leaf-light/manifest.webmanifest',
    })
    expect(launcherIcon.setIcon).not.toHaveBeenCalled()
  })

  it('restores the Android launcher choice and only saves a successful change', async () => {
    isAndroidApp.mockReturnValue(true)
    isNativeApp.mockReturnValue(true)
    launcherIcon.getIcon.mockResolvedValue({ id: 'window-light' })
    const store = useAppIconStore()
    await store.initialize()
    expect(store.id).toBe('window-light')
    launcherIcon.setIcon.mockRejectedValueOnce(new Error('unavailable'))
    await expect(store.select('leaf-dark')).rejects.toThrow('unavailable')
    expect(saved.get('ihomy:app-icon')).toBe('window-light')
    await store.select('leaf-dark')
    expect(launcherIcon.setIcon).toHaveBeenLastCalledWith({ id: 'leaf-dark' })
    expect(saved.get('ihomy:app-icon')).toBe('leaf-dark')
  })
})
