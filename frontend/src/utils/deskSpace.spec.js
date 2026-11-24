import { describe, expect, it } from 'vitest'
import { deskLanes, pawStepCount, pawTrailDelay, pawTrailFits, placeDeskItems, placePawTrail, sampleDeskPlan } from './deskSpace'

const photos = Array.from({ length: 5 }, (_, i) => ({ kind: 'photo', key: i }))
const large = deskLanes(1920, 900, 220, { left: 520, right: 1620 })
const plan = sampleDeskPlan(() => 0.94)

describe('window desk margins', () => {
  it('can select an empty desk and draws a nonempty budget on manual rearrangement', () => {
    expect(sampleDeskPlan(() => 0)).toEqual([])
    expect(sampleDeskPlan(() => 0, 1)).toHaveLength(1)
  })

  describe('two visible objects', () => {
    const height = 914
    const lanes = deskLanes(2560, height, 0, { left: 600, right: 1960 })
    const slots = [1, 0, 1].map((side) => ({ side, u: 0.5, v: 0.8, angle: 0 }))
    const pair = [{ kind: 'journal' }, { kind: 'leaf' }]
    const rightStrip = { left: 2250, right: 2260, top: 0, bottom: height }
    const lowerLeft = { left: 0, right: 600, top: 510, bottom: height }

    it('uses opposite edges and opposite halves even when both slots prefer the same height', () => {
      const placed = placeDeskItems(pair, slots.slice(0, 2), lanes, [])
      expect(placed).toHaveLength(2)
      expect(placed[0].edge).not.toBe(placed[1].edge)
      expect(placed[0].y < height / 2).not.toBe(placed[1].y < height / 2)
    })

    it('uses the first object’s actual corner after the obstacles force it across and up', () => {
      // The right strip blocks the journal, but leaves enough room beside it for the leaf.
      const placed = placeDeskItems(pair, slots.slice(0, 2), lanes, [rightStrip, lowerLeft])
      expect(placed.map((p) => p.edge)).toEqual(['left', 'right'])
      expect(placed[0].y).toBeLessThan(height / 2)
      expect(placed[1].y).toBeGreaterThan(height / 2)
    })

    it('can move the first item when the second only fits in one corner', () => {
      const leftStrip = { ...rightStrip, left: 300, right: 310 }
      const upperRight = { left: 1960, right: 2560, top: 0, bottom: 400 }
      const placed = placeDeskItems([pair[1], pair[0]], slots.slice(1), lanes, [leftStrip, upperRight])
      expect(placed.map((p) => p.edge)).toEqual(['left', 'right'])
      expect(placed[0].y).toBeLessThan(height / 2)
      expect(placed[1].y).toBeGreaterThan(height / 2)
    })

    it('separates the two that fit when a three-object plan cannot fit the last one', () => {
      const leftStrip = { ...rightStrip, left: 300, right: 310 }
      const items = [{ kind: 'leaf' }, { kind: 'note' }, { kind: 'journal' }]
      const placed = placeDeskItems(items, slots, lanes, [leftStrip, rightStrip])
      expect(placed.map((p) => p.item)).toEqual(items.slice(0, 2))
      expect(placed[0].edge).not.toBe(placed[1].edge)
      expect(placed[0].y < height / 2).not.toBe(placed[1].y < height / 2)
    })

    it('uses the upper and lower ends of one edge when the other edge is blocked', () => {
      const rail = { ...rightStrip, right: 2560 }
      const placed = placeDeskItems(pair, slots.slice(0, 2), lanes, [rail])
      expect(placed.map((p) => p.edge)).toEqual(['left', 'left'])
      expect(placed[0].y < height / 2).not.toBe(placed[1].y < height / 2)
    })
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
    const trail = placePawTrail(large, [], () => 0.4)
    expect(trail).toHaveLength(8)
    expect(pawTrailFits(trail, large, [])).toBe(true)
    const { x, y } = trail[0]
    const blocker = { left: x - 1, right: x + 1, top: y - 1, bottom: y + 1 }
    expect(pawTrailFits(trail, large, [blocker])).toBe(false)
    expect(pawTrailFits(trail, [], [])).toBe(false)
  })

  it('doubles the trail spacing and count, and bends the path in a random direction', () => {
    const seq = (values) => {
      let i = 0
      return () => values[i++ % values.length]
    }
    // 座位随机值固定,只让弯曲幅度换两端:同一串的步距不变,只有弧线方向相反
    const bentRight = placePawTrail(large, [], seq([0.4, 0.4, 0.4, 1, 0.6, 0.6]))
    const bentLeft = placePawTrail(large, [], seq([0.4, 0.4, 0.4, 0, 0.6, 0.6]))
    expect(bentRight).toHaveLength(8)
    expect(bentLeft).toHaveLength(8)
    // 纵向步距 0.44 个物件尺寸:8 步合计明显长于旧版 4 步的 0.22 间距
    expect(Math.abs(bentRight[0].y - bentRight[7].y)).toBeGreaterThan(200)
    // 中段偏离直线的幅度随弯曲方向改变
    expect(Math.abs(bentRight[4].x - bentLeft[4].x)).toBeGreaterThan(10)
    expect(bentRight[0].delay).toBeLessThan(bentRight[7].delay)
  })

  it('can place a trail at a phone edge even with three desk objects on screen', () => {
    const lanes = deskLanes(375, 667, 0, null, 84)
    const objects = [50, 300, 550].map((top) => ({ left: 0, right: 84, top, bottom: top + 110 }))
    const trail = placePawTrail(lanes, objects, () => 0.4)
    expect(trail).toHaveLength(8)
    expect(pawTrailFits(trail, lanes, objects)).toBe(true)
  })

  it('randomises the step count as an even number between 4 and 12', () => {
    expect(pawStepCount(() => 0)).toBe(4)
    expect(pawStepCount(() => 0.99)).toBe(12)
    const seq = (values) => {
      let i = 0
      return () => values[i++ % values.length]
    }
    // 座位/弯曲固定,第 7 个随机值决定步数
    for (const roll of [0, 0.19, 0.2, 0.59, 0.99]) {
      const trail = placePawTrail(large, [], seq([0.4, 0.4, 0.4, 0.4, 0.6, 0.4, roll]))
      expect(trail.length % 2).toBe(0)
      expect(trail.length).toBeGreaterThanOrEqual(4)
      expect(trail.length).toBeLessThanOrEqual(12)
    }
  })

  it('keeps every paw facing the way the steps walk, and flips the trail when it heads the other way', () => {
    const seq = (values) => {
      let i = 0
      return () => values[i++ % values.length]
    }
    // 第 6 个随机值决定走向:0.6 往下走、0.4 往上走
    const down = placePawTrail(large, [], seq([0.4, 0.4, 0.4, 0.4, 0.6, 0.6, 0.4]))
    const up = placePawTrail(large, [], seq([0.4, 0.4, 0.4, 0.4, 0.6, 0.4, 0.4]))
    expect(down.length).toBeGreaterThan(0)
    expect(up.length).toBeGreaterThan(0)
    expect(Math.min(...down.map(({ angle }) => Math.abs(angle)))).toBeGreaterThan(150)
    expect(Math.max(...up.map(({ angle }) => Math.abs(angle)))).toBeLessThan(30)
  })

  it('waits at least five random minutes between two trails', () => {
    expect(pawTrailDelay(() => 0)).toBe(5 * 60_000)
    expect(pawTrailDelay(() => 0.5)).toBe(7.5 * 60_000)
    expect(pawTrailDelay(() => 0.99)).toBeLessThanOrEqual(10 * 60_000)
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
