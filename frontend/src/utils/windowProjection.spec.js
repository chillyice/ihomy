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
    [15, 2 + Math.sqrt(3)],
    [30, Math.sqrt(3)],
    [45, 1],
    [60, 1 / Math.sqrt(3)],
    [90, 0],
  ])('projects the vertical span onto the floor at %i degrees', (altitude, expectedLengths) => {
    for (const [width, height] of [
      [1200, 800],
      [390, 844],
    ]) {
      for (const azimuth of [120, 180, 240]) {
        const [, , dx, dy] = windowTransform(width, height, altitude, azimuth)
        const windowHeight = 0.76 * height
        expect(Math.hypot(dx, dy) / windowHeight).toBeCloseTo(expectedLengths, 8)
      }
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
