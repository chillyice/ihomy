import { effectScope } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useWarmWidgetDrag } from './widgetDragData'

const pointer = (type, x, pointerId = 1) =>
  Object.assign(new Event(type), { clientX: x, clientY: 0, pointerId, button: 0, pointerType: 'mouse' })

describe('暖居侧栏组件投放', () => {
  let scope, drag, drop
  beforeEach(() => {
    vi.stubGlobal('window', new EventTarget())
    scope = effectScope()
    drag = scope.run(useWarmWidgetDrag)
    drop = vi.fn()
    drag.onMove((x) => drag.setOverGrid(x >= 100))
    drag.onDrop(drop)
    drag.startDrag('album', '相册', pointer('pointerdown', 5))
  })
  afterEach(() => {
    scope.stop()
    vi.unstubAllGlobals()
  })

  it('以松手坐标为准,其他指针不能完成本次拖动', () => {
    window.dispatchEvent(pointer('pointerup', 120, 2))
    expect(drag.dragging.value).toBe(true)
    window.dispatchEvent(pointer('pointerup', 120))
    expect(drop).toHaveBeenCalledExactlyOnceWith('album')
    expect(drag.dragging.value).toBe(false)
  })

  it('在网格外松手会清除占位', () => {
    window.dispatchEvent(pointer('pointermove', 120))
    expect(drag.overGrid.value).toBe(true)
    window.dispatchEvent(pointer('pointerup', 30))
    expect(drop).not.toHaveBeenCalled()
    expect(drag.overGrid.value).toBe(false)
  })

  it.each(['pointercancel', 'blur', 'Escape', 'unmount'])('%s 中断拖动后松手也不会写入布局', (reason) => {
    window.dispatchEvent(pointer('pointermove', 120))
    if (reason === 'unmount') scope.stop()
    else if (reason === 'Escape') window.dispatchEvent(Object.assign(new Event('keydown'), { key: 'Escape' }))
    else window.dispatchEvent(pointer(reason, 120))
    expect(drag.code.value).toBeNull()
    expect(drag.overGrid.value).toBe(false)
    window.dispatchEvent(pointer('pointerup', 120))
    expect(drop).not.toHaveBeenCalled()
  })
})
