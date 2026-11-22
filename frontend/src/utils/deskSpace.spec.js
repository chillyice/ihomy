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
})
