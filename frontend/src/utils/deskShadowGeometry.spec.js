import { describe, expect, it } from 'vitest'
import { deskShadowVector } from './deskShadowGeometry'
import { DESK_DEFAULT_PATHS } from './deskObjects'
import { DESK_OBJECT_PROFILES } from './deskObjectProfiles'
import { windowTransform } from './windowProjection'

const sun = (altitude, azimuth) => ({ altitude, azimuth, hasDirectLight: true })

describe('desk shadow geometry', () => {
  it.each([5, 30, 45, 80])('projects a 28 px cup height at %i degrees', (altitude) => {
    const { x, y } = deskShadowVector(sun(altitude, 135), 200, 'cup')
    const expected = 28 / Math.tan((altitude * Math.PI) / 180)
    expect(Math.hypot(x, y)).toBeCloseTo(expected, 8)
    expect(x).toBeCloseTo(Math.SQRT1_2 * expected, 8)
    expect(y).toBeCloseTo(Math.SQRT1_2 * expected, 8)
  })

  it.each([110, 180, 250])('casts along the real window rays at azimuth %i', (azimuth) => {
    const altitude = 30
    const [, , dx, dy] = windowTransform(1200, 800, altitude, azimuth, 1)
    const vector = deskShadowVector(sun(altitude, azimuth), 200, 'cup')
    // The real aperture spans 2 × 800 px, the cup 0.14 × 200 px.
    expect(vector.x).toBeCloseTo(dx * (28 / 1600), 8)
    expect(vector.y).toBeCloseTo(dy * (28 / 1600), 8)
  })

  it.each([0, 0.5, 1])('keeps cup and rail shadows parallel at projection weight %f', (weight) => {
    const scene = sun(65, 125)
    const vector = deskShadowVector(scene, 200, 'cup', weight)
    const [, , dx, dy] = windowTransform(1200, 800, scene.altitude, scene.azimuth, weight, 76)
    expect(vector.x / vector.y).toBeCloseTo(dx / dy, 8)
    const expectedDepth = 1 - weight + weight * Math.cos(((180 - scene.azimuth) * Math.PI) / 180)
    expect(vector.y).toBeCloseTo((28 * expectedDepth) / Math.tan((65 * Math.PI) / 180), 8)
  })

  it('scales with the displayed sprite size and its registered material height', () => {
    const scene = sun(45, 180)
    const paper = deskShadowVector(scene, 200, 'bookmark')
    const cup = deskShadowVector(scene, 200, 'cup')
    const fruit = deskShadowVector(scene, 200, 'fruit')
    expect(paper.y).toBeCloseTo(1.6)
    expect(fruit.y).toBeCloseTo(21)
    expect(cup.y).toBeCloseTo(28)
    expect(deskShadowVector(scene, 100, 'cup').y).toBeCloseTo(cup.y / 2)
    expect(cup.height).toBeGreaterThan(paper.height)
  })

  it('registers geometry for each selectable and data-driven desk object', () => {
    for (const kind of [...Object.keys(DESK_DEFAULT_PATHS), 'note', 'photo', 'phone', 'envelope']) {
      expect(DESK_OBJECT_PROFILES[kind]?.heightRatio).toBeGreaterThan(0)
      expect(DESK_OBJECT_PROFILES[kind]?.sizeRatio).toBeGreaterThan(0)
    }
    expect(deskShadowVector(sun(45, 180), 200, 'unregistered')).toBeNull()
  })

  it('drops casts outside the window’s direct daylight range or without usable geometry', () => {
    for (const altitude of [-2, 0, 90, NaN]) expect(deskShadowVector(sun(altitude, 180), 200, 'cup')).toBeNull()
    expect(deskShadowVector({ ...sun(45, 180), hasDirectLight: false }, 200, 'cup')).toBeNull()
    expect(deskShadowVector(sun(45, NaN), 200, 'cup')).toBeNull()
    expect(deskShadowVector(sun(45, 180), 0, 'cup')).toBeNull()
  })
})
