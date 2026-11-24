import { describe, expect, it, vi } from 'vitest'
import { drawWindowProjection, windowTransform } from './windowProjection'

describe('window-plane projection', () => {
  it.each([
    [1200, 800],
    [390, 844],
  ])('mirrors the footprint with solar bearing in a %i × %i viewport', (width, height) => {
    const [paneWidth, , morningShear, morningDepth, morningX, morningY] = windowTransform(width, height, 25, 115)
    const [, , eveningShear, eveningDepth, eveningX, eveningY] = windowTransform(width, height, 25, 245)
    expect(morningShear).toBeGreaterThan(0)
    expect(eveningShear).toBeCloseTo(-morningShear)
    expect(morningDepth).toBeCloseTo(eveningDepth)
    expect(morningY).toBeCloseTo(eveningY)
    expect(morningX + paneWidth + eveningX).toBeCloseTo(width)
  })

  it.each([
    [1200, 800],
    [390, 844],
  ])('keeps winter noon twice as tall as summer noon at %i �� %i', (width, height) => {
    for (const winterAltitude of [20, 26.66, 35]) {
      const winter = windowTransform(width, height, winterAltitude, 180)
      const summer = windowTransform(width, height, winterAltitude + 46.88, 180)
      expect(winter[3]).toBeCloseTo(2 * summer[3])
      expect(winter[5] + winter[3]).toBeLessThanOrEqual(height)
      expect(summer[3]).toBeGreaterThan(height * 0.3)
    }
  })

  it.each([
    ['clear', false],
    ['rain', true],
    ['snow', true],
    ['thunder', true],
  ])('clears the previous footprint for weather %s, direct sun %s', (weather, hasDirectLight) => {
    const clearRect = vi.fn()
    const canvas = { width: 512, height: 256, getContext: () => ({ clearRect }) }
    drawWindowProjection(canvas, { width: 4096, height: 2048, scene: { hasDirectLight }, weather })
    expect(clearRect).toHaveBeenCalledWith(0, 0, 512, 256)
  })

  it.each([0, 90])('clears the canvas at the solar boundary of %i degrees', (altitude) => {
    const clearRect = vi.fn()
    const canvas = { width: 100, height: 100, getContext: () => ({ clearRect }) }
    drawWindowProjection(canvas, { width: 200, height: 200, scene: { altitude, azimuth: 180, hasDirectLight: true } })
    expect(clearRect).toHaveBeenCalledWith(0, 0, 100, 100)
  })
})
