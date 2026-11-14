import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { usePickSelect, PICK_PRESS_MS } from './usePickSelect'

// haptic 静音(不依赖浏览器)
vi.mock('@/utils/haptics', () => ({ haptic: vi.fn() }))

describe('usePickSelect', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('toggle/enter/exit/clear/selectAll 基础流转', () => {
    const s = usePickSelect()
    expect(s.selectMode.value).toBe(false)
    s.enter(7)
    expect(s.selectMode.value).toBe(true)
    expect(s.selectedIds.value).toEqual([7])
    s.toggle(8)
    s.toggle(7)
    expect(s.selectedIds.value).toEqual([8])
    s.selectAll([1, 2, 3])
    expect(s.count.value).toBe(3)
    s.clear()
    expect(s.selectedIds.value).toEqual([])
    expect(s.selectMode.value).toBe(true)
    s.exit()
    expect(s.selectMode.value).toBe(false)
    expect(s.selectedIds.value).toEqual([])
  })

  it('长按 PICK_PRESS_MS 后进多选并预选该项', () => {
    const onLongPress = vi.fn()
    const s = usePickSelect({ onLongPress })
    expect(s.pressStart({ id: 42 })).toBe(true)
    vi.advanceTimersByTime(PICK_PRESS_MS - 1)
    expect(s.selectMode.value).toBe(false)
    vi.advanceTimersByTime(1)
    expect(s.selectMode.value).toBe(true)
    expect(s.selectedIds.value).toEqual([42])
    expect(onLongPress).toHaveBeenCalledWith({ id: 42 })
  })

  it('长按后的 click 在时间窗内被吞,过窗恢复', () => {
    const s = usePickSelect()
    s.pressStart({ id: 1 })
    vi.advanceTimersByTime(PICK_PRESS_MS)
    expect(s.swallowClick()).toBe(true)
    expect(s.swallowClick()).toBe(false)
    // 未长按时 click 不吞
    expect(s.swallowClick()).toBe(false)
  })

  it('pointerup 取消计时:不进多选、click 不吞', () => {
    const s = usePickSelect()
    s.pressStart({ id: 1 })
    s.cancelPress()
    vi.advanceTimersByTime(PICK_PRESS_MS + 100)
    expect(s.selectMode.value).toBe(false)
    expect(s.swallowClick()).toBe(false)
  })

  it('enabled=false 或已在多选时不接管长按', () => {
    const s = usePickSelect({ enabled: () => false })
    expect(s.pressStart({ id: 1 })).toBe(false)
    const s2 = usePickSelect()
    s2.enter(1)
    expect(s2.pressStart({ id: 2 })).toBe(false)
    expect(s2.selectedIds.value).toEqual([1])
  })

  it('多选模式下长按不触发(点击即切换选中)', () => {
    const s = usePickSelect()
    s.enter(1)
    s.pressStart({ id: 2 })
    vi.advanceTimersByTime(PICK_PRESS_MS + 100)
    expect(s.selectedIds.value).toEqual([1])
  })
})
