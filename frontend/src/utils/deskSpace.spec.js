import { describe, expect, it } from 'vitest'
import { deskLanes, pawTrailFits, placeDeskItems, placePawTrail, sampleDeskPlan } from './deskSpace'

const photos = Array.from({ length: 5 }, (_, i) => ({ kind: 'photo', key: i }))
const large = deskLanes(1920, 900, 220, { left: 520, right: 1620 })
const plan = sampleDeskPlan(() => 0.94)

describe('window desk margins', () => {
  it('can select an empty desk and draws a nonempty budget on manual rearrangement', () => {
    expect(sampleDeskPlan(() => 0)).toEqual([])
    expect(sampleDeskPlan(() => 0, 1)).toHaveLength(1)
  })

  it('uses the 381 / 914 reference for large pieces and preserves relative sizes and a ceiling', () => {
    function sizeAt(kind, height) {
      const lanes = deskLanes(2560, height, 220, { left: 900, right: 1660 })
      return placeDeskItems([{ kind }], plan, lanes, [])[0].size
    }
    expect(sizeAt('journal', 914)).toBe(381)
    expect(sizeAt('journal', 720)).toBe(300)
    expect(sizeAt('journal', 2160)).toBe(480)
    expect(sizeAt('leaf', 914)).toBeLessThan(sizeAt('cup', 914))
    expect(sizeAt('cup', 914)).toBeLessThan(sizeAt('journal', 914))
  })

  it('preserves the layout between measurements and keeps navigation and the middle clear', () => {
    const placed = placeDeskItems(photos, plan, large, [])
    expect(placed.length).toBeGreaterThanOrEqual(2)
    for (const { x, y, size, angle, cropWidth } of placed) {
      const rad = (Math.abs(angle) * Math.PI) / 180
      const r = Math.ceil((size / 2) * (Math.abs(Math.cos(rad)) + Math.abs(Math.sin(rad)))) + 5
      expect(x - (cropWidth ? cropWidth / 2 : r)).toBeGreaterThanOrEqual(220)
      expect(y / 900 <= 0.33 || y / 900 >= 0.67).toBe(true)
      expect(y - r).toBeGreaterThanOrEqual(0)
      expect(y + r).toBeLessThanOrEqual(900)
    }
    expect(placeDeskItems(photos, plan, large, [])).toEqual(placed)
  })

  it('tilts objects at a wider random angle capped at 30 degrees', () => {
    const angles = [0, 0.25, 0.5, 0.75, 1].map((roll) => sampleDeskPlan(() => roll, 1)[0].angle)
    expect(Math.max(...angles)).toBeLessThanOrEqual(30)
    expect(Math.min(...angles)).toBeGreaterThanOrEqual(-30)
    expect(Math.max(...angles.map(Math.abs))).toBeGreaterThan(20)
  })

  it('lets a full-width board card reduce the exposed strip even on a wide monitor', () => {
    const board = deskLanes(1800, 914, 220, null, 1800)
    const card = { left: 220, right: 1800, top: 0, bottom: 914 }
    const [open] = placeDeskItems(photos, plan, board, [])
    const [crowded] = placeDeskItems(photos, plan, board, [], [card])
    expect(crowded.size).toBeLessThan(open.size)
    expect(crowded.edge).toBe('right')
    expect(card.right - (crowded.x - crowded.cropWidth / 2)).toBeLessThanOrEqual(44)
    expect(placeDeskItems(photos, plan, board, [card])).toEqual([])
  })

  it('removes footprints when a floating panel covers the path or its margin disappears', () => {
    const placed = [{ x: 300, y: 300, size: 84 }]
    const trail = placePawTrail(placed, large, [], () => 0.4)
    expect(trail).toHaveLength(4)
    expect(pawTrailFits(trail, placed, large, [])).toBe(true)
    const { x, y } = trail[0]
    const blocker = { left: x - 1, right: x + 1, top: y - 1, bottom: y + 1 }
    expect(pawTrailFits(trail, placed, large, [blocker])).toBe(false)
    expect(pawTrailFits(trail, placed, [], [])).toBe(false)
  })

  it.each([
    [768, 1024],
    [1024, 768],
    [820, 1180],
    [1000, 914],
  ])('bounds the rotated artwork at crowded edges at %i × %i', (width, height) => {
    const lanes = deskLanes(width, height, 0, { left: 0, right: width })
    const placed = placeDeskItems(photos, plan, lanes, [])
    expect(placed).toHaveLength(3)
    expect(new Set(placed.map((p) => p.edge))).toEqual(new Set(['left', 'right']))
    for (const { x, y, size, cropWidth } of placed) {
      expect(cropWidth).toBeGreaterThan(12)
      expect(cropWidth).toBeLessThanOrEqual(44)
      expect(size).toBeGreaterThan(cropWidth * 3)
      expect(Math.min(x, width - x)).toBe(cropWidth / 2)
      expect(y / height <= 0.33 || y / height >= 0.67).toBe(true)
      expect(y - size / 2).toBeGreaterThan(0)
      expect(y + size / 2).toBeLessThan(height)
    }
    expect(placeDeskItems(photos, [], lanes, [])).toEqual([])
  })

  it('uses only one edge object in tablet split view and moves aside for controls and navigation', () => {
    const lanes = deskLanes(540, 768, 0, { left: 0, right: 540 })
    const rightRail = { left: 480, right: 540, top: 0, bottom: 768 }
    const [left] = placeDeskItems(photos, plan, lanes, [rightRail])
    expect(left.edge).toBe('left')
    const button = { left: 0, right: 52, top: left.y - 20, bottom: left.y + 20 }
    const moved = placeDeskItems(photos, plan, lanes, [rightRail, button])
    expect(moved).toHaveLength(1)
    expect(moved[0].edge).toBe('left')
    expect(Math.abs(moved[0].y - left.y)).toBeGreaterThan(moved[0].size / 2 + 20)
    expect(placeDeskItems(photos, plan, lanes, [rightRail, { ...rightRail, left: 0, right: 60 }])).toEqual([])
  })
})
