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
  ])('matches the Jinan noon landmarks at %i by %i', (width, height) => {
    const summer = windowTransform(width, height, 76.79, 180)
    const equinox = windowTransform(width, height, 53.35, 180)
    const winter = windowTransform(width, height, 29.91, 180)
    expect(summer[5] / height).toBeCloseTo(0.01, 8)
    expect(equinox[5] / height).toBeCloseTo(0.05, 8)
    expect((equinox[5] + equinox[3]) / height).toBeCloseTo(0.95, 8)
    expect(winter[5] / height).toBeCloseTo(0.09, 8)
    expect(winter[3] / summer[3]).toBeCloseTo(1.15, 8)
    expect((summer[3] + winter[3]) / (2 * height)).toBeCloseTo(0.9, 8)
  })

  it('projects a south-facing vertical window onto the floor using both solar angles', () => {
    const [, , dx, dy, , top] = windowTransform(1200, 1000, 30, 120, 1)
    expect(Math.hypot(dx, dy)).toBeCloseTo(2000 * Math.sqrt(3))
    expect(dy).toBeCloseTo((2000 * Math.sqrt(3)) / 2)
    expect(dx).toBeCloseTo(3000)
    expect(top).toBeCloseTo(dy * 0.15)
    expect(windowTransform(1200, 1000, 45, 180, 1)[3]).toBeCloseTo(2000)
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
