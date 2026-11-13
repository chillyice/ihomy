import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { createIdleController } from './useIdle'

// 空闲计时内核:活动重置、到点只触发一次、被拦住时重新计时、后台暂停。
describe('createIdleController', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('到点触发一次,之后不再重复', () => {
    let fired = 0
    const c = createIdleController({ idleMs: 1000, onIdle: () => fired++ })
    c.poke()
    vi.advanceTimersByTime(999)
    expect(fired).toBe(0)
    vi.advanceTimersByTime(1)
    expect(fired).toBe(1)
    expect(c.idle).toBe(true)
    vi.advanceTimersByTime(60000)
    expect(fired).toBe(1)
  })

  it('活动事件重置计时', () => {
    let fired = 0
    const c = createIdleController({ idleMs: 1000, onIdle: () => fired++ })
    c.poke()
    vi.advanceTimersByTime(800)
    c.poke()
    vi.advanceTimersByTime(800)
    expect(fired).toBe(0)
    vi.advanceTimersByTime(200)
    expect(fired).toBe(1)
  })

  it('canTrigger=false 时不弹并重新计时(关掉弹窗后不会立刻弹)', () => {
    let allow = false
    let fired = 0
    const c = createIdleController({ idleMs: 1000, canTrigger: () => allow, onIdle: () => fired++ })
    c.poke()
    vi.advanceTimersByTime(1000)
    expect(fired).toBe(0)
    expect(c.idle).toBe(false)
    allow = true
    vi.advanceTimersByTime(999)
    expect(fired).toBe(0)
    vi.advanceTimersByTime(1)
    expect(fired).toBe(1)
  })

  it('后台暂停:期间不触发,回前台重新整段起算', () => {
    let fired = 0
    const c = createIdleController({ idleMs: 1000, onIdle: () => fired++ })
    c.poke()
    vi.advanceTimersByTime(500)
    c.suspend()
    vi.advanceTimersByTime(5000)
    expect(fired).toBe(0)
    c.resume()
    vi.advanceTimersByTime(999)
    expect(fired).toBe(0)
    vi.advanceTimersByTime(1)
    expect(fired).toBe(1)
  })

  it('时长 0(关闭屏保)时不排计时器', () => {
    let fired = 0
    const c = createIdleController({ idleMs: () => 0, onIdle: () => fired++ })
    c.poke()
    vi.advanceTimersByTime(3600000)
    expect(fired).toBe(0)
  })

  it('stop 后彻底停用', () => {
    let fired = 0
    const c = createIdleController({ idleMs: 1000, onIdle: () => fired++ })
    c.poke()
    c.stop()
    vi.advanceTimersByTime(60000)
    expect(fired).toBe(0)
  })
})
