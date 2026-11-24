import { effectScope, ref } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useWarmCardReorder } from './useWarmCardReorder'

describe('warm home pointer reorder', () => {
  let grid, card, reorder, commit, scope, render

  function pointer(type, clientX, pointerId = 1) {
    const event = Object.assign(new Event(type), { pointerId, clientX, clientY: 40 })
    window.dispatchEvent(event)
  }
  function press() {
    reorder.start({ currentTarget: card, button: 0, pointerId: 1, clientX: 30, clientY: 40 }, 'photos')
  }

  beforeEach(() => {
    vi.stubGlobal('window', new EventTarget())
    vi.stubGlobal('requestAnimationFrame', (cb) => {
      render = cb
      return 1
    })
    vi.stubGlobal('cancelAnimationFrame', () => {})
    grid = Object.assign(new EventTarget(), {
      setPointerCapture: vi.fn(),
      getBoundingClientRect: () => ({ left: 10, top: 20 }),
    })
    card = {
      getBoundingClientRect: () => ({ left: 15, top: 25, width: 100, height: 75 }),
      style: { removeProperty: vi.fn() },
      closest: () => null,
    }
    commit = vi.fn()
    scope = effectScope()
    reorder = scope.run(() =>
      useWarmCardReorder({
        gridEl: ref(grid),
        indexOf: () => 0,
        indexAt: (x) => Math.floor(x / 100),
        captureRects: () => new Map(),
        flipLayout: vi.fn(),
        commit,
      }),
    )
  })
  afterEach(() => {
    scope.stop()
    vi.unstubAllGlobals()
  })

  it('keeps the grab point under the pointer and commits the final slot even before its animation frame', () => {
    press()
    pointer('pointermove', 105)
    render()
    expect(grid.setPointerCapture).toHaveBeenCalledWith(1)
    expect(card.style.translate).toBe('80px 5px')
    expect(reorder.preview.value).toMatchObject({ id: 'photos', index: 1, width: 100, height: 75 })
    expect(commit).not.toHaveBeenCalled()
    pointer('pointermove', 201)
    pointer('pointerup', 205)
    expect(commit).toHaveBeenCalledExactlyOnceWith('photos', 2)
    expect(reorder.preview.value).toBeNull()
  })

  it('preserves the displayed slot when releasing without additional motion', () => {
    press()
    pointer('pointermove', 120)
    render()
    pointer('pointerup', 120, 2)
    expect(reorder.preview.value.index).toBe(1)
    pointer('pointerup', 120)
    expect(commit).toHaveBeenCalledExactlyOnceWith('photos', 1)
  })

  it.each(['pointercancel', 'blur', 'Escape', 'unmount'])('rolls back on %s and ignores a late release', (reason) => {
    press()
    pointer('pointermove', 120)
    render()
    if (reason === 'unmount') scope.stop()
    else if (reason === 'Escape') window.dispatchEvent(Object.assign(new Event('keydown'), { key: 'Escape' }))
    else pointer(reason, 120)
    pointer('pointerup', 120)
    expect(reorder.preview.value).toBeNull()
    expect(commit).not.toHaveBeenCalled()
  })

  it('ignores a tap and secondary pointers', () => {
    press()
    pointer('pointermove', 140, 2)
    pointer('pointermove', 31)
    pointer('pointerup', 31)
    expect(grid.setPointerCapture).not.toHaveBeenCalled()
    expect(commit).not.toHaveBeenCalled()
  })
})
