import { describe, it, expect } from 'vitest'
import { clampFloatRect } from './floatRect'

describe('clampFloatRect', () => {
  it('在视口内时原样返回', () => {
    expect(clampFloatRect({ x: 20, y: 30, w: 200, h: 120 }, { width: 800, height: 600 })).toEqual({
      x: 20,
      y: 30,
      w: 200,
      h: 120,
    })
  })

  it('窗口缩到比小窗还小:位置贴左上、尺寸缩到视口', () => {
    expect(clampFloatRect({ x: 500, y: 400, w: 360, h: 520 }, { width: 300, height: 480 })).toEqual({
      x: 0,
      y: 0,
      w: 300,
      h: 480,
    })
  })

  it('右/下越界:拉回视口内,尺寸不变', () => {
    expect(clampFloatRect({ x: 700, y: 560, w: 200, h: 120 }, { width: 800, height: 600 })).toEqual({
      x: 600,
      y: 480,
      w: 200,
      h: 120,
    })
  })

  it('anchorRight:x 是离右边缘距离,越界时回到 0..vw-w', () => {
    const opts = { anchorRight: true }
    expect(clampFloatRect({ x: 999, y: 10, w: 200, h: 120 }, { width: 800, height: 600 }, opts)).toEqual({
      x: 600,
      y: 10,
      w: 200,
      h: 120,
    })
    expect(clampFloatRect({ x: -30, y: 10, w: 200, h: 120 }, { width: 800, height: 600 }, opts).x).toBe(0)
  })

  it('遵守 minY 与 marginLeft(沉浸式首页侧栏)', () => {
    expect(clampFloatRect({ x: 10, y: 4, w: 200, h: 120 }, { width: 800, height: 600 }, { minY: 42, marginLeft: 220 })).toEqual({
      x: 220,
      y: 42,
      w: 200,
      h: 120,
    })
  })

  it('尺寸只缩不放:视口比小窗大时不放大', () => {
    expect(clampFloatRect({ x: 0, y: 0, w: 48, h: 48 }, { width: 1600, height: 900 }).w).toBe(48)
  })

  it('视口窄到边距 + 小窗放不下时,可见性优先(不按边距推到屏幕外)', () => {
    expect(
      clampFloatRect({ x: 460, y: 60, w: 320, h: 200 }, { width: 500, height: 600 }, { marginLeft: 420, minY: 42, minW: 200, minH: 120 }),
    ).toEqual({ x: 300, y: 60, w: 200, h: 200 })
  })

  it('视口非法值按 0 处理,不产生 NaN', () => {
    expect(clampFloatRect({ x: 10, y: 10, w: 48, h: 48 }, null)).toEqual({ x: 0, y: 0, w: 1, h: 1 })
  })
})
