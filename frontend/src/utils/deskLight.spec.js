import { describe, expect, it } from 'vitest'
import { apertureCoverage, castShadow, projectionQuad, quadHitsRect } from './deskLight'
import { windowTransform } from './windowProjection'

const sceneOf = (overrides = {}) => ({
  altitude: 53.35,
  azimuth: 180,
  hasDirectLight: true,
  shadowColor: 'rgb(0,0,0)',
  ...overrides,
})
const alphaOf = (color) => Number(color.match(/,\s*([\d.]+)\)$/)[1])

describe('desk light', () => {
  it('turns the aperture transform into viewport footprint corners', () => {
    const [a, , c, d, x, y] = windowTransform(1200, 800, 53.35, 180)
    expect(projectionQuad(1200, 800, sceneOf())).toEqual([
      [x, y],
      [x + a, y],
      [x + a + c, y + d],
      [x + c, y + d],
    ])
  })

  it('hits only the objects standing inside the projected aperture', () => {
    const quad = projectionQuad(1200, 800, sceneOf())
    expect(quadHitsRect(quad, { left: 500, right: 700, top: 200, bottom: 400 })).toBe(true)
    expect(quadHitsRect(quad, { left: 500, right: 700, top: 900, bottom: 1000 })).toBe(false)
    expect(quadHitsRect(quad, { left: 0, right: 40, top: 40, bottom: 400 })).toBe(false)
  })

  it('shears the footprint with the solar bearing', () => {
    const morning = projectionQuad(1200, 800, sceneOf({ altitude: 25, azimuth: 115 }))
    const evening = projectionQuad(1200, 800, sceneOf({ altitude: 25, azimuth: 245 }))
    expect(morning[2][0]).toBeGreaterThan(600)
    expect(evening[2][0]).toBeLessThan(600)
  })

  it('fades at a pane edge and leaves objects behind the frame and crossbars unlit', () => {
    const quad = [
      [0, 0],
      [1000, 0],
      [1000, 1000],
      [0, 1000],
    ]
    const measure = (left, top, right, bottom) => apertureCoverage(quad, { left, top, right, bottom })
    expect(measure(0, 100, 8, 120)).toBe(0)
    expect(measure(7, 100, 17, 120)).toBeCloseTo(0.5)
    expect(measure(100, 200, 200, 300)).toBeCloseTo(1)
    expect(measure(490, 100, 510, 180)).toBe(0)
    expect(measure(100, 470, 200, 490)).toBe(0)
  })

  it('clips against the slanted pane edge in viewport coordinates', () => {
    const quad = [
      [0, 0],
      [1000, 0],
      [1500, 1000],
      [500, 1000],
    ]
    // At y = 200 the left glass edge is x = 112; half the box is sunlit.
    expect(apertureCoverage(quad, { left: 102, right: 122, top: 190, bottom: 210 })).toBeCloseTo(0.5)
  })

  it('gives paper a tight shadow and raises the shadows of fruit and cups', () => {
    const paper = castShadow(sceneOf(), 200, 0.6, 'bookmark')
    for (const kind of ['fruit', 'cup']) {
      const raised = castShadow(sceneOf(), 200, 0.6, kind)
      expect(raised.y).toBeGreaterThan(paper.y * 5)
      expect(raised.blur).toBeGreaterThan(paper.blur)
    }
  })

  it.each([
    ['morning', 115, 1],
    ['evening', 245, -1],
  ])('throws the %s shadow away from the sun', (label, azimuth, direction) => {
    const shadow = castShadow(sceneOf({ altitude: 12, azimuth }), 300, 0.6)
    expect(Math.sign(shadow.x)).toBe(direction)
    expect(shadow.y).toBeGreaterThan(0)
    expect(shadow.blur).toBeGreaterThan(2)
  })

  it('lengthens the shadow while the sun is low', () => {
    const low = castShadow(sceneOf({ altitude: 6 }), 300, 0.6)
    const high = castShadow(sceneOf({ altitude: 70 }), 300, 0.6)
    expect(Math.hypot(low.x, low.y)).toBeGreaterThan(Math.hypot(high.x, high.y))
  })

  it('follows the ambient depth and lifts when there is no direct sun', () => {
    expect(alphaOf(castShadow(sceneOf(), 300, 0.7).color)).toBeGreaterThan(alphaOf(castShadow(sceneOf(), 300, 0.2).color))
    expect(castShadow(sceneOf({ altitude: -4, hasDirectLight: false }), 300, 0.7)).toBeNull()
  })
})
