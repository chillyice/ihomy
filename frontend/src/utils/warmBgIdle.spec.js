import { describe, it, expect } from 'vitest'
import { BG_IDLE_MS, BG_CONTENT_SELECTOR, isBackgroundPlate } from './warmBgIdle'

// 简易 Element 替身:按祖先类名链实现 closest(选择器里都是单个类选择器,直接按类名比对)
function elWithAncestors(classes = []) {
  return {
    closest(selector) {
      const wanted = selector.split(',').map((s) => s.trim())
      return classes.find((c) => wanted.includes('.' + c)) ? { matched: true } : null
    },
  }
}

describe('warmBgIdle', () => {
  it('待机阈值是 1 分钟', () => {
    expect(BG_IDLE_MS).toBe(60000)
  })

  it('照片空隙(无祖先)算背景板', () => {
    expect(isBackgroundPlate(elWithAncestors([]))).toBe(true)
    expect(isBackgroundPlate(elWithAncestors(['gc-root']))).toBe(true)
  })

  it('拿不到元素时不算背景板', () => {
    expect(isBackgroundPlate(null)).toBe(false)
    expect(isBackgroundPlate(undefined)).toBe(false)
    expect(isBackgroundPlate({})).toBe(false)
  })

  it('studio 外框内(侧栏/主区/卡片)不算背景板', () => {
    expect(isBackgroundPlate(elWithAncestors(['gc-studio', 'gc-nav-item']))).toBe(false)
  })

  it('顶栏与胞吐工具栏(返回键/晨暮开关/工具栏按钮)不算背景板', () => {
    expect(isBackgroundPlate(elWithAncestors(['gc-topbar']))).toBe(false)
    expect(isBackgroundPlate(elWithAncestors(['gc-topbar', 'gc-back']))).toBe(false)
    expect(isBackgroundPlate(elWithAncestors(['gc-topbar', 'gc-pin', 'page-toolbar']))).toBe(false)
  })

  it('常驻浮层(音乐/聊天室/通话/提示)不算背景板', () => {
    const floating = [
      ['music-player'],
      ['music-player', 'player-left'],
      ['chat-float-ball'],
      ['chat-float-panel'],
      ['chat-float-sheet'],
      ['call-overlay'],
      ['call-mini'],
      ['back-to-top'],
      ['site-footer'],
      ['install-prompt'],
      ['el-popper'],
      ['el-notification'],
    ]
    floating.forEach((classes) => {
      expect(isBackgroundPlate(elWithAncestors(classes)), classes.join(' ')).toBe(false)
    })
  })

  it('选择器登记了全部已知常驻浮层', () => {
    const known = ['music-player', 'chat-float-ball', 'chat-float-panel', 'chat-float-sheet', 'call-overlay', 'call-mini']
    known.forEach((cls) => expect(BG_CONTENT_SELECTOR).toContain('.' + cls))
  })
})
