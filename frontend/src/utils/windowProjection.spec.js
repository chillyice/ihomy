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

  it('projects a longer footprint under low sun, with finite limits at grazing and vertical incidence', () => {
    const lowSun = windowTransform(100, 100, 0, 180)
    const highSun = windowTransform(100, 100, 90, 180)
    expect([...lowSun, ...highSun].every(Number.isFinite)).toBe(true)
    expect(lowSun[2]).toBe(0)
    expect(highSun[3]).toBeGreaterThan(0)
    expect(lowSun[3]).toBeGreaterThan(highSun[3])
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
})
