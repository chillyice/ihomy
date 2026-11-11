import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'

// 只测右键入口的「接 / 不接」:触屏必须放行(Android 长按后会补发 contextmenu,
// 不挡就会长按菜单与右键菜单双弹),精细指针则复用同一份 menuItems。
const { pointerState } = vi.hoisted(() => ({ pointerState: { value: true } }))

vi.mock('@/composables/useDevice', () => ({ useDevice: () => ({ hasPointer: pointerState }) }))
vi.mock('vue', async (importOriginal) => {
  const actual = await importOriginal()
  return { ...actual, onBeforeUnmount: () => {} }
})

const { useRowGestures } = await import('./useRowGestures')

const makeEvent = (over = {}) => ({
  clientX: 12,
  clientY: 34,
  defaultPrevented: false,
  preventDefault() {
    this.defaultPrevented = true
  },
  ...over,
})

beforeEach(() => {
  pointerState.value = true
  globalThis.document = { addEventListener: vi.fn(), removeEventListener: vi.fn() }
})

afterEach(() => {
  delete globalThis.document
})

describe('useRowGestures.openContextMenu', () => {
  it('精细指针右键:阻止浏览器默认菜单,并按指针位置打开同一份菜单', () => {
    const g = useRowGestures({ hasMenu: () => true })
    const e = makeEvent()
    g.openContextMenu(e)
    expect(e.defaultPrevented).toBe(true)
    expect(g.menu.value).toEqual({ visible: true, x: 12, y: 34 })
  })

  it('触屏(hasPointer=false):完全不接管,事件照常交给浏览器', () => {
    pointerState.value = false
    const g = useRowGestures({ hasMenu: () => true })
    const e = makeEvent()
    g.openContextMenu(e)
    expect(e.defaultPrevented).toBe(false)
    expect(g.menu.value.visible).toBe(false)
  })

  it('没有菜单项时:不弹空菜单,也不吞右键', () => {
    const g = useRowGestures({ hasMenu: () => false })
    const e = makeEvent()
    g.openContextMenu(e)
    expect(e.defaultPrevented).toBe(false)
    expect(g.menu.value.visible).toBe(false)
  })

  it('空事件:安静返回,不报错', () => {
    const g = useRowGestures({ hasMenu: () => true })
    expect(() => g.openContextMenu(null)).not.toThrow()
    expect(g.menu.value.visible).toBe(false)
  })
})
