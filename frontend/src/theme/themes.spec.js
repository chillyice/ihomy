import { afterEach, describe, expect, it, vi } from 'vitest'
import { createPinia } from 'pinia'
import { useThemeStore } from '@/stores/theme'
import { loadTheme, SELECTABLE_THEME_IDS, THEMES, THEME_STORAGE_KEY } from './index'

// 窗边主题已下线:可选集只剩暖居/光尘,旧偏好加载时要回落暖居(不能留着不可选的 theme-id)
function stubStorage(raw) {
  const map = new Map()
  if (raw != null) map.set(THEME_STORAGE_KEY, raw)
  vi.stubGlobal('localStorage', {
    getItem: (k) => (map.has(k) ? map.get(k) : null),
    setItem: (k, v) => map.set(k, String(v)),
  })
}

afterEach(() => vi.unstubAllGlobals())

it('光影预览跨越日落和日出时直接切换主题', () => {
  stubStorage()
  const classList = { add: vi.fn(), remove: vi.fn(), toggle: vi.fn() }
  const createElement = vi.fn()
  vi.stubGlobal('document', { documentElement: { classList }, querySelector: () => null, createElement })
  const theme = useThemeStore(createPinia())
  theme.setPreference({ theme: 'warm', mode: 'dawn', autoMode: true }, { sweep: false })
  theme.applyAuto(true, { sweep: false })
  expect(classList.toggle).toHaveBeenLastCalledWith('dark', true)
  theme.applyAuto(false, { sweep: false })
  expect(classList.toggle).toHaveBeenLastCalledWith('dark', false)
  expect(createElement).not.toHaveBeenCalled()
})

describe('主题可选集', () => {
  it('只列暖居与光尘,窗边条目仍留给遗留样式', () => {
    expect(SELECTABLE_THEME_IDS).toEqual(['warm', 'guangchen'])
    expect(THEMES.window).toBeTruthy()
  })

  it('旧偏好里的窗边在加载时回落暖居,晨暮与自动开关保留', () => {
    stubStorage(JSON.stringify({ theme: 'window', mode: 'dusk', autoMode: false }))
    expect(loadTheme()).toMatchObject({ theme: 'warm', mode: 'dusk', autoMode: false })
  })

  it('可选主题照常读回', () => {
    stubStorage(JSON.stringify({ theme: 'guangchen', mode: 'dawn', autoMode: true }))
    expect(loadTheme().theme).toBe('guangchen')
  })
})
