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

  it('keeps three visible photos within the measured margins on either side of the navigation and page', () => {
    const placed = placeDeskItems(photos, plan, large, [])
    expect(placed).toHaveLength(3)
    for (const { x, y, size } of placed) {
      const r = Math.ceil(size * 0.58) + 5
      expect(large.some((l) => x - r >= l.left && x + r <= l.right && y - r >= l.top && y + r <= l.bottom)).toBe(true)
    }
    expect(placeDeskItems(photos, plan, large, [])).toEqual(placed)
    expect(placePawTrail(placed, large, [])).toEqual([])
  })

  it('skips a cup that cannot fit even if a smaller item fits and rejects occupied board edges', () => {
    const narrow = deskLanes(1440, 900, 220, { left: 290, right: 1370 })
    const items = [{ kind: 'cup' }, { kind: 'leaf' }]
    expect(placeDeskItems(items, plan, narrow, []).map((p) => p.item.kind)).toEqual(['leaf'])
    const board = deskLanes(1500, 800, 220, null)
    expect(placeDeskItems(photos, plan, board, board)).toEqual([])
    expect(placePawTrail([], board, board)).toEqual([])
  })

  it('removes footprints when a floating panel covers the path or its margin disappears', () => {
    const placed = placeDeskItems(photos.slice(0, 2), plan, large, [])
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
  ])('peeks out of tablet edges with a visible touch target at %i × %i', (width, height) => {
    const viewport = { width, height }
    const placed = placeDeskItems(photos, plan, [], [], viewport)
    expect(placed).toHaveLength(3)
    expect(new Set(placed.map((p) => p.edge))).toEqual(new Set(['left', 'right']))
    for (const { x, y, size } of placed) {
      const onScreen = Math.min(x + size / 2, width) - Math.max(x - size / 2, 0)
      expect(onScreen).toBeGreaterThanOrEqual(44)
      expect(onScreen).toBeLessThan(size)
      expect(y - size / 2).toBeGreaterThan(0)
      expect(y + size / 2).toBeLessThan(height)
    }
    expect(placeDeskItems(photos, [], [], [], viewport)).toEqual([])
  })

  it('uses only one edge object in tablet split view and moves aside for controls and navigation', () => {
    const viewport = { width: 540, height: 768 }
    const rightRail = { left: 480, right: 540, top: 0, bottom: 768 }
    const [left] = placeDeskItems(photos, plan, [], [rightRail], viewport)
    expect(left.edge).toBe('left')
    const button = { left: 0, right: 52, top: left.y - 20, bottom: left.y + 20 }
    const moved = placeDeskItems(photos, plan, [], [rightRail, button], viewport)
    expect(moved).toHaveLength(1)
    expect(moved[0].edge).toBe('left')
    expect(Math.abs(moved[0].y - left.y)).toBeGreaterThan(moved[0].size / 2 + 20)
    expect(placeDeskItems(photos, plan, [], [rightRail, { ...rightRail, left: 0, right: 60 }], viewport)).toEqual([])
  })
})
