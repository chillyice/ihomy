import { describe, expect, it, vi } from 'vitest'
import { drawWindowProjection, windowTransform } from './windowProjection'
import { CASEMENTS, chooseWindowSashes, weatherClosesWindow } from './windowSash'

function openWindow(layout, ...angles) {
  const samples = [0.8, layout, ...angles]
  return chooseWindowSashes(() => samples.shift())
}

describe('window sash', () => {
  it.each([
    [null, false],
    [{ condition: 'rain', precipLevel: 2 }, false],
    [{ condition: 'rain', precipLevel: '3' }, true],
    [{ condition: 'snow', precipLevel: 2 }, false],
    [{ condition: 'snow', precipLevel: 3 }, true],
    [{ condition: 'rain', iconCode: '302', precipLevel: 1 }, true],
    [{ condition: 'rain', precipLevel: 1, text: '冻雨' }, true],
    [{ condition: 'rain', precipLevel: 1, nowFull: { text: '强阵雨' } }, true],
    [{ condition: 'clear', nowFull: { windScale: '4-5', windSpeed: '38' } }, false],
    [{ condition: 'cloud', nowFull: { windScale: '5-6' } }, true],
    [{ condition: 'clear', windScale: 6 }, true],
    [{ condition: 'clear', nowFull: { windSpeed: '39' } }, true],
  ])('applies the storm closure rule to %j', (weather, closed) => {
    expect(weatherClosesWindow(weather)).toBe(closed)
  })

  it('can keep both casements shut', () => {
    expect(chooseWindowSashes(() => 0.2)).toEqual(CASEMENTS)
    const next = chooseWindowSashes(() => 0.2, CASEMENTS)
    expect(next.some((sash) => sash.depth > 0)).toBe(true)
  })

  it('lets the two meeting stiles own the whole centre seam', () => {
    const [left, right] = CASEMENTS
    expect(left.latch).toBe(right.latch)
    expect(left.latch - left.hinge).toBeCloseTo(right.hinge - right.latch)
  })

  it.each([0, 0.5, 0.999])('mirrors the open gap around the meeting stiles at sample %f', (sample) => {
    const [left, shutRight] = openWindow(0.1, sample)
    const [shutLeft, right] = openWindow(0.5, sample)
    expect([shutLeft, shutRight]).toEqual(CASEMENTS)
    expect(left.hinge).toBeLessThan(left.tipX)
    expect(left.tipX).toBeLessThan(left.latch)
    expect(left.tipX + right.tipX).toBeCloseTo(1)
    expect(left.depth).toBeCloseTo(right.depth)
    expect(Math.hypot(left.tipX - left.hinge, left.depth)).toBeCloseTo(left.latch - left.hinge)
    expect(left.depth).toBeCloseTo((left.latch - left.hinge) * Math.sin(((30 + 45 * sample) * Math.PI) / 180))
  })

  it('draws an independent opening angle for each sash when both are open', () => {
    const [left, right] = openWindow(0.9, 0.1, 0.85)
    expect(left).toEqual(openWindow(0.1, 0.1)[0])
    expect(right).toEqual(openWindow(0.5, 0.85)[1])
    expect(left.depth).toBeLessThan(right.depth)
  })
})

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
