import { describe, it, expect } from 'vitest'
import { DEADZONE, GAMEPAD_KEYMAP, REPEAT_MS, isTypingTarget, mapGamepadState, readGamepadKeys } from './useGamepad'

/** 造一个标准布局手柄:buttons[idx].pressed = true */
function pad({ buttons = [], axes = [0, 0] } = {}) {
  const arr = []
  for (let i = 0; i < 17; i++) arr.push({ pressed: buttons.includes(i), value: buttons.includes(i) ? 1 : 0 })
  return { id: 'Test Pad', connected: true, buttons: arr, axes }
}

function keys(events, type = 'keydown') {
  return events.filter((e) => e.type === type).map((e) => e.key)
}

describe('useGamepad 手柄映射', () => {
  it('十字键/左摇杆映射成四个方向键', () => {
    expect(readGamepadKeys(pad({ buttons: [12, 14] })).map((m) => m.key)).toEqual(['ArrowUp', 'ArrowLeft'])
    expect(readGamepadKeys(pad({ axes: [0.9, -0.9] })).map((m) => m.key)).toEqual(['ArrowRight', 'ArrowUp'])
  })

  it('摇杆死区内不产生方向输入,超过死区才触发', () => {
    expect(readGamepadKeys(pad({ axes: [DEADZONE - 0.01, 0] }))).toEqual([])
    expect(readGamepadKeys(pad({ axes: [DEADZONE + 0.01, 0] })).map((m) => m.key)).toEqual(['ArrowRight'])
  })

  it('ABXY 映射成 Enter/Escape/Space/KeyR', () => {
    expect(readGamepadKeys(pad({ buttons: [0, 1, 2, 3] })).map((m) => `${m.key}:${m.code}`)).toEqual([
      'Enter:Enter',
      'Escape:Escape',
      ' :Space',
      'r:KeyR',
    ])
  })

  it('按钮只认边沿:按住不放只派发一次 keydown', () => {
    const p = pad({ buttons: [0] })
    const first = mapGamepadState(p, {}, 1000)
    expect(keys(first.events)).toEqual(['Enter'])
    const second = mapGamepadState(p, first.next, 2000)
    expect(second.events).toEqual([])
    const released = mapGamepadState(pad({}), second.next, 2100)
    expect(keys(released.events, 'keyup')).toEqual(['Enter'])
  })

  it('方向键按住会按 120ms 连发,其余按钮不连发', () => {
    const p = pad({ buttons: [15, 0] })
    const first = mapGamepadState(p, {}, 0)
    expect(keys(first.events)).toEqual(['Enter', 'ArrowRight'])
    // 1ms 后:不连发
    expect(mapGamepadState(p, first.next, 1).events).toEqual([])
    // 到点:只补发方向键
    const repeat = mapGamepadState(p, first.next, REPEAT_MS)
    expect(keys(repeat.events)).toEqual(['ArrowRight'])
  })

  it('松开方向键补发 keyup,状态可清空后重新开始', () => {
    const p = pad({ buttons: [13] })
    const first = mapGamepadState(p, {}, 0)
    const released = mapGamepadState(pad({}), first.next, 10)
    expect(keys(released.events, 'keyup')).toEqual(['ArrowDown'])
    expect(released.next.active).toEqual({})
    const again = mapGamepadState(p, released.next, 20)
    expect(keys(again.events)).toEqual(['ArrowDown'])
  })

  it('没有手柄/空对象不报错也不派发', () => {
    expect(mapGamepadState(null, {}, 0).events).toEqual([])
    expect(mapGamepadState({ buttons: [], axes: [] }, {}, 0).events).toEqual([])
  })

  it('映射表覆盖标准布局且每个键都有 code', () => {
    const indices = GAMEPAD_KEYMAP.map((m) => m.index)
    expect(indices).toEqual([0, 1, 2, 3, 8, 9, 12, 13, 14, 15])
    GAMEPAD_KEYMAP.forEach((m) => expect(m.code).toBeTruthy())
  })
})

describe('useGamepad 焦点守卫', () => {
  it('输入框/文本域/下拉/contenteditable 内不接管', () => {
    expect(isTypingTarget({ tagName: 'INPUT' })).toBe(true)
    expect(isTypingTarget({ tagName: 'TEXTAREA' })).toBe(true)
    expect(isTypingTarget({ tagName: 'select' })).toBe(true)
    expect(isTypingTarget({ tagName: 'DIV', isContentEditable: true })).toBe(true)
    expect(isTypingTarget({ tagName: 'DIV' })).toBe(false)
    expect(isTypingTarget(null)).toBe(false)
  })
})
