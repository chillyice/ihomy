import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import { useFloatDelete, FLOAT_DELETE_ZONE, FLOAT_APPROACH_ZONE } from './useFloatDelete'

// 浮窗拖到底部删除:两档热区(接近才浮出、进入热区才亮)+ 松手判定与复位。
// 拖动阈值在调用方(位移 >6px 才 begin),这个 composable 只管指针高度。
describe('useFloatDelete', () => {
  beforeEach(() => {
    globalThis.window = { innerHeight: 800 }
  })
  afterEach(() => {
    delete globalThis.window
  })

  it('没 begin 时怎么移动都不出现、不命中(点一下悬浮球不会被当成删除)', () => {
    const d = useFloatDelete()
    d.update(799)
    expect(d.active.value).toBe(false)
    expect(d.over.value).toBe(false)
    expect(d.end()).toBe(false)
  })

  it('拖到窗口下方才浮出删除条,进入底部热区才发亮,松手命中并复位', () => {
    const d = useFloatDelete()
    d.begin()
    d.update(200)
    expect(d.active.value).toBe(false)

    d.update(800 - FLOAT_APPROACH_ZONE - 1)
    expect(d.active.value).toBe(false)
    d.update(800 - FLOAT_APPROACH_ZONE)
    expect(d.active.value).toBe(true)
    expect(d.over.value).toBe(false)

    d.update(800 - FLOAT_DELETE_ZONE - 1)
    expect(d.over.value).toBe(false)
    d.update(800 - FLOAT_DELETE_ZONE)
    expect(d.over.value).toBe(true)

    expect(d.end()).toBe(true)
    expect(d.active.value).toBe(false)
    expect(d.over.value).toBe(false)
  })

  it('拖进去又拖回来松手不删除;cancel 立即收起', () => {
    const d = useFloatDelete()
    d.begin()
    d.update(790)
    d.update(400)
    expect(d.active.value).toBe(false)
    expect(d.over.value).toBe(false)
    expect(d.end()).toBe(false)

    d.begin()
    d.update(790)
    d.cancel()
    expect(d.active.value).toBe(false)
    expect(d.over.value).toBe(false)
    expect(d.end()).toBe(false)
  })
})
