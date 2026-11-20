import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { trackAssistantHold } from './assistantHold'
import { assistantPath } from './assistantNavigation'

describe('assistant ball gesture', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => vi.useRealTimers())
  function gesture() {
    const window = new EventTarget()
    const actions = { hold: vi.fn(), release: vi.fn(), cancel: vi.fn() }
    const origin = { pointerId: 1, clientX: 20, clientY: 20 }
    trackAssistantHold(origin, actions, window)
    const event = (type, override = {}) => window.dispatchEvent(Object.assign(new Event(type), origin, override))
    return { actions, event }
  }
  it('claims a stationary press until release, ignoring other fingers', () => {
    const { actions, event } = gesture()
    vi.advanceTimersByTime(450)
    event('pointerup', { pointerId: 2 })
    expect(actions.hold).toHaveBeenCalledOnce()
    expect(actions.release).not.toHaveBeenCalled()
    event('pointerup')
    expect(actions.release).toHaveBeenCalledOnce()
  })
  it('leaves a drag or quick tap to normal chat', () => {
    for (const [type, movement] of [
      ['pointermove', { clientX: 26 }],
      ['pointerup', {}],
    ]) {
      const { actions, event } = gesture()
      event(type, movement)
      vi.advanceTimersByTime(500)
      expect(actions.hold).not.toHaveBeenCalled()
    }
  })
  it('discards an interrupted recording', () => {
    const { actions, event } = gesture()
    vi.advanceTimersByTime(450)
    event('pointercancel')
    event('pointerup')
    expect(actions.cancel).toHaveBeenCalledOnce()
    expect(actions.release).not.toHaveBeenCalled()
  })
})

describe('assistant result navigation', () => {
  it('allows known modules and typed detail links', () => {
    expect(assistantPath('/diary/book/4?entry=91')).toBe('/diary/book/4?entry=91')
    expect(assistantPath('/book')).toBe('/book')
    expect(assistantPath('/book', true)).toBe('')
  })
  it('rejects external, unknown, traversing and restricted destinations', () => {
    for (const path of ['https://example.test', '//example.test', '/blog/../ops', '/fake', '/vault', '/health', '/ops', '/blog/%2e%2e']) {
      expect(assistantPath(path)).toBe('')
    }
  })
})
