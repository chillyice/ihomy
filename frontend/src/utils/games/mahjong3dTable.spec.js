import { describe, expect, it } from 'vitest'
import {
  TILE,
  drawnTileTransform,
  handTileTransform,
  meldTileTransform,
  riverTileTransform,
  seatFrame,
  wallStackCounts,
  wallStackTransform,
  windPlateTransform,
} from './mahjong3dTable'

describe('seatFrame 座位坐标系', () => {
  it('seat0=我(南,+Z),对家在北,下家在屏幕右(+X),上家在屏幕左(−X)', () => {
    expect(seatFrame(0).anchor.z).toBeGreaterThan(0)
    expect(seatFrame(2).anchor.z).toBeLessThan(0)
    expect(seatFrame(1).anchor.x).toBeGreaterThan(0)
    expect(seatFrame(3).anchor.x).toBeLessThan(0)
  })

  it('各行方向:我沿 X,左右家沿 Z;朝向:我朝 +Z(镜头),他家背对我', () => {
    expect(seatFrame(0).row.x).toBeCloseTo(1)
    expect(seatFrame(1).row.z).toBeCloseTo(-1)
    expect(seatFrame(0).face.z).toBeCloseTo(1)
    expect(seatFrame(2).face.z).toBeCloseTo(-1)
  })

  it('负数/超界座位取模不抛错', () => {
    expect(seatFrame(4).anchor.z).toBeCloseTo(seatFrame(0).anchor.z)
    expect(seatFrame(-1).anchor.x).toBeCloseTo(seatFrame(3).anchor.x)
  })
})

describe('handTileTransform 手牌排布', () => {
  it('13 张以 0 为中心对称,间距 = PITCH', () => {
    const a = handTileTransform(0, 0, 13)
    const b = handTileTransform(0, 6, 13)
    const c = handTileTransform(0, 12, 13)
    expect(a.x).toBeCloseTo(-6 * TILE.PITCH)
    expect(b.x).toBeCloseTo(0)
    expect(c.x).toBeCloseTo(6 * TILE.PITCH)
  })

  it('摸牌单放右侧:持牌按自身数居中,摸牌在右端外', () => {
    const mid = handTileTransform(0, 6, 13)
    const drawn = drawnTileTransform(0, 13)
    expect(mid.x).toBeCloseTo(0)
    expect(drawn.x).toBeGreaterThan(handTileTransform(0, 12, 13).x)
    expect(drawn.x - handTileTransform(0, 12, 13).x).toBeGreaterThan(TILE.PITCH) // 有间隙
  })

  it('左右家手牌沿 Z 排布(index 0 在牌主左手位)', () => {
    const t = handTileTransform(1, 0, 13)
    expect(t.z).toBeGreaterThan(0)
    expect(t.x).toBeGreaterThan(0)
  })
})

describe('riverTileTransform 牌河', () => {
  it('每行 6 张,行 0 最靠近本方(向中心逐行增长)', () => {
    const first = riverTileTransform(0, 0)
    const seventh = riverTileTransform(0, 6)
    expect(first.z).toBeGreaterThan(seventh.z) // 我方牌河向 −Z(中心)增长
    expect(Math.abs(first.x - riverTileTransform(0, 5).x)).toBeCloseTo(5 * TILE.PITCH)
  })

  it('对家牌河镜像(z 取反增长)', () => {
    const mine = riverTileTransform(0, 0)
    const theirs = riverTileTransform(2, 0)
    expect(mine.z).toBeGreaterThan(0)
    expect(theirs.z).toBeLessThan(0)
  })
})

describe('wallStackCounts 牌墙分配', () => {
  it('整除时四边相等,每边两层摞', () => {
    const sides = wallStackCounts(84)
    expect(sides.map((s) => s.count)).toEqual([21, 21, 21, 21])
    expect(sides[0].stacks).toBe(11)
    expect(sides[0].tops[10]).toBe(false) // 21 张 = 10 满摞 + 1 单张
    expect(sides[0].tops[0]).toBe(true)
  })

  it('余数从 seat0 起补', () => {
    const sides = wallStackCounts(82)
    expect(sides.map((s) => s.count)).toEqual([21, 21, 20, 20])
  })

  it('0 与负数安全', () => {
    expect(wallStackCounts(0).every((s) => s.count === 0 && s.stacks === 0)).toBe(true)
    expect(wallStackCounts(-3).every((s) => s.count === 0)).toBe(true)
  })
})

describe('wallStackTransform / meld / wind', () => {
  it('牌墙摞沿行方向居中分布', () => {
    const a = wallStackTransform(0, 0, 11)
    const b = wallStackTransform(0, 10, 11)
    expect(b.x - a.x).toBeCloseTo(10 * (TILE.W + 0.06))
    expect(a.z).toBeGreaterThan(0) // 我方墙在我与中心之间
  })

  it('副露排在手牌右侧更靠中心一行', () => {
    const hand = handTileTransform(0, 0, 13)
    const meld = meldTileTransform(0, 0, 0)
    expect(meld.z).toBeLessThan(hand.z)
    expect(meld.x).toBeGreaterThan(hand.x)
  })

  it('风位牌贴中央四边,朝向座位', () => {
    const w0 = windPlateTransform(0)
    expect(w0.z).toBeGreaterThan(0)
    expect(windPlateTransform(2).z).toBeLessThan(0)
  })
})
