import { describe, it, expect, vi, afterEach } from 'vitest'
import { createWakeTimers } from './wakeTimers'

/** node 环境替身:isHidden/subscribe 可控,show() 模拟页面回到前台 */
function stubVisibility() {
  const subs = new Set()
  let hidden = true
  return {
    isHidden: () => hidden,
    subscribe(fn) {
      subs.add(fn)
      return () => subs.delete(fn)
    },
    show() {
      hidden = false
      for (const fn of [...subs]) fn()
    },
    hide() {
      hidden = true
      for (const fn of [...subs]) fn()
    },
  }
}

afterEach(() => {
  vi.useRealTimers()
})

describe('回合引擎后台节流自愈(wakeTimers)', () => {
  it('到点正常触发,与 setTimeout 行为一致', () => {
    vi.useFakeTimers()
    const wake = createWakeTimers(stubVisibility())
    const fn = vi.fn()
    wake.schedule(fn, 700)
    vi.advanceTimersByTime(699)
    expect(fn).not.toHaveBeenCalled()
    vi.advanceTimersByTime(1)
    expect(fn).toHaveBeenCalledTimes(1)
    wake.destroy()
  })

  it('cancel 后不再触发,回前台也不补触发', () => {
    vi.useFakeTimers()
    const vis = stubVisibility()
    const wake = createWakeTimers(vis)
    const fn = vi.fn()
    const handle = wake.schedule(fn, 700)
    wake.cancel(handle)
    vi.advanceTimersByTime(5000)
    vis.show()
    expect(fn).not.toHaveBeenCalled()
    wake.destroy()
  })

  it('隐藏期间错过的定时器,回前台立即补触发', () => {
    // 只假 Date 不假 setTimeout:模拟「墙上时间走了、定时器被节流没跑」
    vi.useFakeTimers({ toFake: ['Date'] })
    const vis = stubVisibility()
    const wake = createWakeTimers(vis)
    const fn = vi.fn()
    wake.schedule(fn, 50)
    vi.setSystemTime(Date.now() + 60_000)
    vis.show()
    expect(fn).toHaveBeenCalledTimes(1)
    wake.destroy()
  })

  it('补触发后原定时器再响不重复执行(同一句柄只触发一次)', async () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    const vis = stubVisibility()
    const wake = createWakeTimers(vis)
    const fn = vi.fn()
    wake.schedule(fn, 30)
    vi.setSystemTime(Date.now() + 60_000)
    vis.show()
    expect(fn).toHaveBeenCalledTimes(1)
    await new Promise((res) => setTimeout(res, 60))
    expect(fn).toHaveBeenCalledTimes(1)
    wake.destroy()
  })

  it('未到期的定时器回前台不被提前触发', () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    const vis = stubVisibility()
    const wake = createWakeTimers(vis)
    const fn = vi.fn()
    const handle = wake.schedule(fn, 60_000)
    vi.setSystemTime(Date.now() + 500)
    vis.show()
    expect(fn).not.toHaveBeenCalled()
    wake.cancel(handle)
    wake.destroy()
  })

  it('destroy 摘除监听,后续回到前台不再唤醒', () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    const vis = stubVisibility()
    const wake = createWakeTimers(vis)
    const fn = vi.fn()
    wake.schedule(fn, 50)
    vi.setSystemTime(Date.now() + 60_000)
    wake.destroy()
    vis.show()
    expect(fn).not.toHaveBeenCalled()
  })

  it('hidden 方向的 visibilitychange 不唤醒', () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    const vis = stubVisibility()
    const wake = createWakeTimers(vis)
    const fn = vi.fn()
    wake.schedule(fn, 50)
    vi.setSystemTime(Date.now() + 60_000)
    vis.hide()
    expect(fn).not.toHaveBeenCalled()
    wake.destroy()
  })
})
