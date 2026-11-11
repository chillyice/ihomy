import { describe, it, expect } from 'vitest'
import { hotkeyAction, isEditableTarget } from './useHotkeys'

// 全局快捷键只做两件事:Ctrl/⌘+K 开命令面板、/ 聚焦本页搜索。
// 这里守住「哪些事件算 / 不算」以及「输入框判定」,避免以后又把方向键/空格加进全局监听。
const key = (over = {}) => ({
  key: '',
  isComposing: false,
  keyCode: 0,
  altKey: false,
  ctrlKey: false,
  metaKey: false,
  shiftKey: false,
  ...over,
})

const fakeTarget = (match) => ({ closest: (sel) => (sel.includes(match) ? {} : null) })

describe('hotkeyAction', () => {
  it('Ctrl+K / ⌘+K 都归为命令面板(大小写与左右修饰键都认)', () => {
    expect(hotkeyAction(key({ key: 'k', ctrlKey: true }))).toBe('palette')
    expect(hotkeyAction(key({ key: 'K', ctrlKey: true }))).toBe('palette')
    expect(hotkeyAction(key({ key: 'k', metaKey: true }))).toBe('palette')
  })

  it('Ctrl+Shift+K 不接管(留给浏览器/其它页面)', () => {
    expect(hotkeyAction(key({ key: 'K', ctrlKey: true, shiftKey: true }))).toBeNull()
  })

  it('裸 / 归为聚焦搜索,带修饰键的 / 不接管', () => {
    expect(hotkeyAction(key({ key: '/' }))).toBe('search')
    expect(hotkeyAction(key({ key: '/', ctrlKey: true }))).toBeNull()
    expect(hotkeyAction(key({ key: '/', metaKey: true }))).toBeNull()
    expect(hotkeyAction(key({ key: '/', shiftKey: true }))).toBeNull()
  })

  it('输入法组字中 / 按住 Alt 时一个都不触发', () => {
    expect(hotkeyAction(key({ key: 'k', ctrlKey: true, isComposing: true }))).toBeNull()
    expect(hotkeyAction(key({ key: 'k', ctrlKey: true, keyCode: 229 }))).toBeNull()
    expect(hotkeyAction(key({ key: 'k', ctrlKey: true, altKey: true }))).toBeNull()
    expect(hotkeyAction(key({ key: '/' }))).toBe('search')
  })

  it('方向键 / 空格 / Esc / 字母不进全局监听', () => {
    for (const k of ['ArrowDown', 'ArrowUp', ' ', 'Escape', 'Enter', 'n', 'F5', 'Backspace']) {
      expect(hotkeyAction(key({ key: k }))).toBeNull()
    }
  })
})

describe('isEditableTarget', () => {
  it('输入框 / 文本域 / 下拉 / 可编辑区 / 显式忽略区都算可编辑', () => {
    expect(isEditableTarget(fakeTarget('input'))).toBe(true)
    expect(isEditableTarget(fakeTarget('textarea'))).toBe(true)
    expect(isEditableTarget(fakeTarget('select'))).toBe(true)
    expect(isEditableTarget(fakeTarget('[contenteditable="true"]'))).toBe(true)
    expect(isEditableTarget(fakeTarget('[data-hotkeys-ignore]'))).toBe(true)
  })

  it('普通元素 / 空值 / 非 DOM 对象返回 false', () => {
    expect(isEditableTarget(fakeTarget('div'))).toBe(false)
    expect(isEditableTarget(null)).toBe(false)
    expect(isEditableTarget({})).toBe(false)
  })
})
