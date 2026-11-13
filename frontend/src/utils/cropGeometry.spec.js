import { describe, it, expect } from 'vitest'
import { clampCropPosition, cropBoxSize, cropOutputSize, cropSourceRect, normalizeRatio } from './cropGeometry'

describe('cropGeometry 选区尺寸', () => {
  it('1:1 头像沿用旧口径:短边铺满,缩放按比例', () => {
    // 旧实现 = min(宽,高) * zoom,头像行为必须零变化
    expect(cropBoxSize(460, 345, 1, 1)).toEqual({ w: 345, h: 345 })
    expect(cropBoxSize(460, 345, 1, 0.5)).toEqual({ w: 173, h: 173 })
  })

  it('16:9 横图:宽铺满,高按比例;竖图:高受限,宽按比例', () => {
    expect(cropBoxSize(460, 345, 16 / 9, 1)).toEqual({ w: 460, h: 259 })
    expect(cropBoxSize(270, 360, 16 / 9, 1)).toEqual({ w: 270, h: 152 })
    expect(cropBoxSize(460, 345, 16 / 9, 0.5)).toEqual({ w: 230, h: 129 })
  })

  it('异常入参不产生 0/NaN 尺寸', () => {
    expect(cropBoxSize(0, 0, 16 / 9, 1)).toEqual({ w: 1, h: 1 })
    expect(normalizeRatio(0)).toBe(1)
    expect(normalizeRatio(-3)).toBe(1)
    expect(normalizeRatio(undefined)).toBe(1)
    expect(normalizeRatio(16 / 9)).toBeCloseTo(1.7778, 3)
  })
})

describe('cropGeometry 位置与导出', () => {
  it('选区拖出图片时贴边,不越界', () => {
    expect(clampCropPosition(-20, -8, 200, 150, 460, 345)).toEqual({ x: 0, y: 0 })
    expect(clampCropPosition(999, 999, 200, 150, 460, 345)).toEqual({ x: 260, y: 195 })
    // 选区比图片大(理论不会出现)时锁在左上角,不出现负偏移
    expect(clampCropPosition(30, 30, 500, 400, 460, 345)).toEqual({ x: 0, y: 0 })
  })

  it('取样矩形按原图/显示图比例换算', () => {
    const box = { x: 100, y: 50, w: 200, h: 150 }
    expect(cropSourceRect(box, 460, 345, 1840, 1380)).toEqual({ sx: 400, sy: 200, sw: 800, sh: 600 })
  })

  it('导出尺寸:头像 256 方图、封面 1280×720', () => {
    expect(cropOutputSize(256, 1)).toEqual({ width: 256, height: 256 })
    expect(cropOutputSize(1280, 16 / 9)).toEqual({ width: 1280, height: 720 })
  })
})
