import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { startSceneRotation } from './sceneRotation'

const { addListener } = vi.hoisted(() => ({ addListener: vi.fn() }))
vi.mock('@capacitor/app', () => ({ App: { addListener } }))
const minute = 60_000
let rotate, visibility, stop

function setHidden(hidden) {
  document.hidden = hidden
  document.dispatchEvent(new Event('visibilitychange'))
}

beforeEach(() => {
  vi.useFakeTimers()
  vi.setSystemTime(new Date(2026, 10, 25, 12))
  vi.stubGlobal('document', Object.assign(new EventTarget(), { hidden: false }))
  vi.stubGlobal('window', new EventTarget())
  rotate = vi.fn()
  visibility = vi.fn()
})
afterEach(() => {
  stop?.()
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

describe('resident app scenery', () => {
  it('rotates hourly across short interruptions without resetting its deadline', () => {
    stop = startSceneRotation(rotate, visibility)
    vi.advanceTimersByTime(35 * minute)
    setHidden(true)
    vi.advanceTimersByTime(10 * minute)
    setHidden(false)
    window.dispatchEvent(new Event('pageshow'))
    expect(rotate).not.toHaveBeenCalled()
    vi.advanceTimersByTime(15 * minute)
    expect(rotate).toHaveBeenCalledTimes(1)
  })

  it('handles local midnight and catches up only once after several nights away', () => {
    vi.setSystemTime(new Date(2026, 10, 25, 23, 55))
    stop = startSceneRotation(rotate, visibility)
    vi.advanceTimersByTime(5 * minute)
    expect(rotate).toHaveBeenCalledTimes(1)
    setHidden(true)
    vi.advanceTimersByTime(3 * 24 * 60 * minute)
    expect(rotate).toHaveBeenCalledTimes(1)
    setHidden(false)
    window.dispatchEvent(new Event('pageshow'))
    expect(rotate).toHaveBeenCalledTimes(2)
  })

  it('waits for the native foreground even if the WebView reports visibility first', async () => {
    window.Capacitor = { isNativePlatform: () => true }
    const remove = vi.fn()
    addListener.mockResolvedValue({ remove })
    stop = startSceneRotation(rotate, visibility)
    await vi.dynamicImportSettled()
    const nativeState = addListener.mock.lastCall[1]
    nativeState({ isActive: false })
    setHidden(true)
    vi.advanceTimersByTime(20 * minute)
    setHidden(false)
    expect(visibility).toHaveBeenLastCalledWith(false)
    nativeState({ isActive: true })
    window.dispatchEvent(new Event('pageshow'))
    expect(visibility).toHaveBeenLastCalledWith(true)
    expect(rotate).toHaveBeenCalledTimes(1)
    stop()
    stop = null
    vi.advanceTimersByTime(61 * minute)
    expect(remove).toHaveBeenCalledTimes(1)
    expect(rotate).toHaveBeenCalledTimes(1)
  })
})
