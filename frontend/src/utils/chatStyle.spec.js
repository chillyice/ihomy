import { describe, it, expect } from 'vitest'
import { CHAT_FONT_DEFAULT, CHAT_FONT_MAX, CHAT_FONT_MIN, CHAT_TEXT_COLORS, normalizeChatStyle } from './chatStyle'

// 聊天字体偏好:字号夹在上下限内(小屏不许堆满),颜色只认预设值(不许任意 CSS)。
describe('normalizeChatStyle', () => {
  it('空值 / 脏值回默认', () => {
    expect(normalizeChatStyle(null)).toEqual({ size: CHAT_FONT_DEFAULT, color: '' })
    expect(normalizeChatStyle({ size: 'abc', color: 123 })).toEqual({ size: CHAT_FONT_DEFAULT, color: '' })
  })

  it('字号取整并夹在上下限内', () => {
    expect(normalizeChatStyle({ size: 999 }).size).toBe(CHAT_FONT_MAX)
    expect(normalizeChatStyle({ size: 1 }).size).toBe(CHAT_FONT_MIN)
    expect(normalizeChatStyle({ size: 16.6 }).size).toBe(17)
    expect(normalizeChatStyle({ size: CHAT_FONT_MAX + 0.4 }).size).toBe(CHAT_FONT_MAX)
  })

  it('颜色只接受预设值,其余回主题色', () => {
    const preset = CHAT_TEXT_COLORS.find(Boolean)
    expect(normalizeChatStyle({ size: 14, color: preset }).color).toBe(preset)
    expect(normalizeChatStyle({ size: 14, color: 'red;background:url(x)' }).color).toBe('')
    expect(normalizeChatStyle({ size: 14, color: '#123456' }).color).toBe('')
  })
})
