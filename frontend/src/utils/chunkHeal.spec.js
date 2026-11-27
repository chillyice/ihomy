import { describe, it, expect, vi, afterEach } from 'vitest'
import { HEAL_WINDOW_MS, isChunkLoadError, nextHealStep, purgeOfflineCaches, watchServiceWorkerUpdates } from './chunkHeal'

afterEach(() => {
  vi.useRealTimers()
  vi.restoreAllMocks()
})

describe('isChunkLoadError', () => {
  it('认得各浏览器的「动态 import 取不到」文案', () => {
    expect(isChunkLoadError(new TypeError('Failed to fetch dynamically imported module: https://x/assets/Wallpaper-1.js'))).toBe(true)
    expect(isChunkLoadError(new TypeError('error loading dynamically imported module'))).toBe(true)
    expect(isChunkLoadError(new Error('Importing a module script failed.'))).toBe(true)
    expect(isChunkLoadError(new Error('Loading chunk 12 failed.'))).toBe(true)
  })

  it('别的错误不接管', () => {
    expect(isChunkLoadError(new Error('Cannot read properties of undefined'))).toBe(false)
    expect(isChunkLoadError(null)).toBe(false)
    expect(isChunkLoadError(undefined)).toBe(false)
  })
})

describe('nextHealStep', () => {
  const now = 1_800_000_000_000

  it('第一次失败:整页刷新', () => {
    expect(nextHealStep(null, now)).toBe(1)
    expect(nextHealStep({ at: now - HEAL_WINDOW_MS - 1, stage: 2 }, now)).toBe(1)
  })

  it('刷新后仍失败:升级到注销 SW + 清缓存', () => {
    expect(nextHealStep({ at: now - 5_000, stage: 1 }, now)).toBe(2)
  })

  it('走到第二级还是失败:放弃,避免无限刷新', () => {
    expect(nextHealStep({ at: now - 5_000, stage: 2 }, now)).toBe(0)
  })

  it('窗口过期后重新从第一步开始', () => {
    expect(nextHealStep({ at: now - HEAL_WINDOW_MS, stage: 2 }, now)).toBe(1)
  })

  it('存了脏数据也按第一次处理', () => {
    expect(nextHealStep({ at: 'x', stage: 1 }, now)).toBe(1)
    expect(nextHealStep({}, now)).toBe(1)
  })
})

describe('purgeOfflineCaches', () => {
  it('注销所有 SW 并清空所有 cache', async () => {
    const unregister = vi.fn()
    const del = vi.fn()
    const sw = { getRegistrations: () => Promise.resolve([{ unregister }, { unregister }]) }
    const cacheStorage = { keys: () => Promise.resolve(['ihomy-precache', 'ihomy-page-assets']), delete: del }
    await purgeOfflineCaches({ sw, cacheStorage })
    expect(unregister).toHaveBeenCalledTimes(2)
    expect(del.mock.calls.map((c) => c[0])).toEqual(['ihomy-precache', 'ihomy-page-assets'])
  })

  it('环境里没有 SW/caches 也不报错', async () => {
    await expect(purgeOfflineCaches({})).resolves.toBeUndefined()
    await expect(
      purgeOfflineCaches({
        sw: { getRegistrations: () => Promise.reject(new Error('x')) },
        cacheStorage: { keys: () => Promise.reject(new Error('y')) },
      }),
    ).resolves.toBeUndefined()
  })
})

describe('watchServiceWorkerUpdates', () => {
  it('SW 就绪后要一次新清单,回到前台再要一次', async () => {
    vi.useFakeTimers()
    const update = vi.fn(() => Promise.resolve())
    const sw = { ready: Promise.resolve({}), getRegistration: () => Promise.resolve({ update }) }
    const listeners = {}
    const doc = {
      hidden: true,
      addEventListener: (name, fn) => {
        listeners[name] = fn
      },
      removeEventListener: vi.fn(),
    }
    const stop = watchServiceWorkerUpdates({ sw, doc, intervalMs: 1000 })
    await vi.waitFor(() => expect(update).toHaveBeenCalledTimes(1))

    listeners.visibilitychange()
    await vi.waitFor(() => expect(update).toHaveBeenCalledTimes(1)) // 仍在后台,不查
    doc.hidden = false
    listeners.visibilitychange()
    await vi.waitFor(() => expect(update).toHaveBeenCalledTimes(2))

    vi.advanceTimersByTime(1000)
    await vi.waitFor(() => expect(update).toHaveBeenCalledTimes(3))

    stop()
    vi.advanceTimersByTime(3000)
    await vi.waitFor(() => expect(update).toHaveBeenCalledTimes(3))
  })

  it('没有 SW 能力时什么都不做', () => {
    expect(typeof watchServiceWorkerUpdates({})).toBe('function')
  })
})
