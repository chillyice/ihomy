import { describe, expect, it } from 'vitest'
import {
  RIVER_ROWS,
  TABLE,
  TILE,
  contentCorners,
  drawnTileTransform,
  fitCameraDistance,
  handTileTransform,
  maxNdc,
  meldTileTransform,
  riverRowCols,
  riverTileTransform,
  rowPlan,
  seatFrame,
  wallStackCounts,
  wallStackTransform,
  wallTiles,
  windPlateTransform,
} from './mahjong3dTable'

const CAMERA_MAIN = { pos: [0, 8.9, 10.6], look: [0, 0, 0.55] }
const CAMERA_TOP = { pos: [0, 16.2, 0.02], look: [0, 0, 0] }

/** 水平足迹 AABB:立放/卧放的牌水平投影都是 行向 W × 朝向 D */
function tileBox(x, z, yaw) {
  const c = Math.abs(Math.cos(yaw))
  const s = Math.abs(Math.sin(yaw))
  return {
    minX: x - (c * TILE.W) / 2 - (s * TILE.D) / 2,
    maxX: x + (c * TILE.W) / 2 + (s * TILE.D) / 2,
    minZ: z - (s * TILE.W) / 2 - (c * TILE.D) / 2,
    maxZ: z + (s * TILE.W) / 2 + (c * TILE.D) / 2,
  }
}
function boxesOverlap(a, b) {
  return a.minX < b.maxX && a.maxX > b.minX && a.minZ < b.maxZ && a.maxZ > b.minZ
}
function riverBoxes(seat, count = 24) {
  return Array.from({ length: count }, (_, i) => {
    const t = riverTileTransform(seat, i)
    return tileBox(t.x, t.z, t.yaw)
  })
}

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

  it('手牌行贴桌沿(锚点 = 桌半宽 − edgeGap),墙圈在其内侧', () => {
    expect(seatFrame(0).anchor.z).toBeCloseTo(TABLE.D / 2 - 0.95)
    expect(wallStackTransform(0, 0, 11).z).toBeLessThan(seatFrame(0).anchor.z)
    expect(wallStackTransform(0, 0, 11).z).toBeGreaterThan(riverTileTransform(0, 0).z) // 墙在河外圈
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

  it('带 plan 时整行平移/压缩,牌距不小于牌宽(不相交下限)', () => {
    const plan = rowPlan(1, true, [4, 4, 4, 4])
    expect(plan.pitch).toBeGreaterThanOrEqual(TILE.W)
    const drawn = drawnTileTransform(0, 1, plan)
    expect(drawn.x).toBeGreaterThan(handTileTransform(0, 0, 1, plan).x)
  })
})

describe('riverTileTransform 牌河(风车形)', () => {
  it('行 0 最靠近本方墙,向中心逐行增长', () => {
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

  it('列数按行收敛 [6,6,6,4,2],容量 24 ≥ 最坏 21 张弃牌', () => {
    expect([0, 1, 2, 3, 4].map(riverRowCols)).toEqual([6, 6, 6, 4, 2])
    expect(RIVER_ROWS).toBe(5)
    expect([0, 1, 2, 3, 4].map(riverRowCols).reduce((s, n) => s + n, 0)).toBeGreaterThanOrEqual(21)
  })

  it('守门:任意两席牌河都装满 24 张时互不相交(风车形不交叉)', () => {
    const pairs = [
      [0, 1],
      [0, 3],
      [1, 2],
      [2, 3],
    ]
    for (const [a, b] of pairs) {
      const boxesA = riverBoxes(a)
      const boxesB = riverBoxes(b)
      for (const ba of boxesA) {
        for (const bb of boxesB) {
          expect(boxesOverlap(ba, bb), `seat ${a} 与 seat ${b} 的牌河相交`).toBe(false)
        }
      }
    }
  })

  it('守门:牌河不进中央区(风位牌圈外),也不压自家墙', () => {
    for (const box of riverBoxes(0)) {
      expect(box.minZ).toBeGreaterThan(0.93) // 风位牌最远 z ≈ 0.93
      expect(box.maxZ).toBeLessThan(wallStackTransform(0, 0, 11).z - TILE.D / 2 + 0.01)
    }
  })
})

describe('rowPlan 整行排布(手牌+摸牌+副露同行)', () => {
  it('常规局不位移:13 手/带摸牌/一组副露都直接放得下', () => {
    expect(rowPlan(13, false, []).shift).toBe(0)
    expect(rowPlan(13, true, []).shift).toBe(0)
    expect(rowPlan(10, false, [3]).shift).toBe(0)
  })

  it('副露把手行推过预算时整行左移居中,两端仍在呢面内', () => {
    const plan = rowPlan(7, false, [3, 3])
    expect(plan.shift).toBeLessThan(0)
    const rightEnd = plan.meldStart + 6 * plan.pitch + plan.meldGap + plan.shift
    expect(rightEnd).toBeLessThanOrEqual(TABLE.W / 2)
    expect(-plan.handHalf + plan.shift).toBeGreaterThanOrEqual(-TABLE.W / 2)
  })

  it('守门:最坏局面(1 手 + 摸牌 + 四暗杠)整行都在呢面内且牌互不相交', () => {
    const plan = rowPlan(1, true, [4, 4, 4, 4])
    const boxes = []
    const hand = handTileTransform(0, 0, 1, plan)
    boxes.push(tileBox(hand.x, hand.z, hand.yaw))
    const drawn = drawnTileTransform(0, 1, plan)
    boxes.push(tileBox(drawn.x, drawn.z, drawn.yaw))
    let startU = plan.meldStart + plan.shift
    for (const size of [4, 4, 4, 4]) {
      for (let i = 0; i < size; i++) {
        const t = meldTileTransform(0, startU, i, plan.pitch)
        boxes.push(tileBox(t.x, t.z, t.yaw))
      }
      startU += size * plan.pitch + plan.meldGap
    }
    for (const box of boxes) {
      expect(box.maxX).toBeLessThanOrEqual(TABLE.W / 2 + 0.01)
      expect(box.minX).toBeGreaterThanOrEqual(-TABLE.W / 2 - 0.01)
    }
    for (let i = 0; i < boxes.length; i++) {
      for (let j = i + 1; j < boxes.length; j++) {
        expect(boxesOverlap(boxes[i], boxes[j]), `第 ${i}/${j} 张牌相交`).toBe(false)
      }
    }
  })

  it('守门:常规满配(13 手 + 摸牌 + 三组副露)整行不超呢面', () => {
    const plan = rowPlan(4, true, [3, 3, 3])
    const end = plan.meldStart + plan.shift + (9 * plan.pitch + 2 * plan.meldGap)
    expect(end).toBeLessThanOrEqual(TABLE.W / 2)
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

describe('wallTiles 牌墙牌位(两层摞 + 固定原点逐张消失)', () => {
  it('满墙 84 张逐张对应剩余数,0 与 83 安全', () => {
    expect(wallTiles(84)).toHaveLength(84)
    expect(wallTiles(83)).toHaveLength(83)
    expect(wallTiles(0)).toHaveLength(0)
  })

  it('两层堆叠:底层 y=D/2、顶层 y=1.5D 上下贴齐不相交', () => {
    const tiles = wallTiles(84)
    const bottoms = tiles.filter((t) => Math.abs(t.y - TILE.D / 2) < 1e-6)
    const tops = tiles.filter((t) => t.y > TILE.D)
    expect(bottoms).toHaveLength(44)
    expect(tops).toHaveLength(40)
    for (const top of tops) {
      expect(top.y).toBeGreaterThanOrEqual(TILE.D) // 顶层底面 ≥ 底层顶面(不互相吃进)
      expect(top.y).toBeLessThan(TILE.D * 1.5 + 0.01)
    }
  })

  it('消耗不滑动:少墙的每个牌位都是满墙牌位的子集(只从行尾消失)', () => {
    const full = new Set(wallTiles(84).map((t) => `${t.x.toFixed(4)},${t.y.toFixed(4)},${t.z.toFixed(4)}`))
    for (const t of wallTiles(80)) {
      expect(full.has(`${t.x.toFixed(4)},${t.y.toFixed(4)},${t.z.toFixed(4)}`)).toBe(true)
    }
  })

  it('牌墙行都在呢面内', () => {
    for (const t of wallTiles(84)) {
      expect(Math.abs(t.x)).toBeLessThanOrEqual(TABLE.W / 2)
      expect(Math.abs(t.z)).toBeLessThanOrEqual(TABLE.D / 2)
    }
  })
})

describe('wallStackTransform / meld / wind', () => {
  it('牌墙摞沿行方向居中分布,在呢面内', () => {
    const a = wallStackTransform(0, 0, 11)
    const b = wallStackTransform(0, 10, 11)
    expect(b.x - a.x).toBeCloseTo(10 * (TILE.W + 0.06))
    expect(a.z).toBeGreaterThan(0) // 我方墙在我与中心之间
    expect(Math.abs(a.x)).toBeLessThan(TABLE.W / 2)
  })

  it('副露与手牌同行,排在其右侧', () => {
    const plan = rowPlan(10, false, [3])
    const hand = handTileTransform(0, 9, 10, plan) // 该 plan 下手牌末张
    const meld = meldTileTransform(0, plan.meldStart + plan.shift, 0, plan.pitch)
    expect(meld.z).toBeCloseTo(hand.z)
    expect(meld.x).toBeGreaterThan(hand.x)
    expect(meld.x).toBeLessThan(TABLE.W / 2)
  })

  it('风位牌贴中央四边,朝向座位', () => {
    const w0 = windPlateTransform(0)
    expect(w0.z).toBeGreaterThan(0)
    expect(windPlateTransform(2).z).toBeLessThan(0)
  })
})

describe('相机取景 fitCameraDistance', () => {
  const corners = contentCorners()

  it('竖屏拉得比横屏远,且都让内容完整入镜', () => {
    const wide = fitCameraDistance(CAMERA_MAIN, 1.6, corners)
    const tall = fitCameraDistance(CAMERA_MAIN, 0.46, corners)
    expect(tall).toBeGreaterThan(wide)
    expect(maxNdc(CAMERA_MAIN, wide, 1.6, corners)).toBeLessThanOrEqual(0.95)
    expect(maxNdc(CAMERA_MAIN, tall, 0.46, corners)).toBeLessThanOrEqual(0.95)
    expect(maxNdc(CAMERA_TOP, fitCameraDistance(CAMERA_TOP, 1.6, corners), 1.6, corners)).toBeLessThanOrEqual(0.95)
  })

  it('取景距离有限且为正', () => {
    const d = fitCameraDistance(CAMERA_MAIN, 1.0, corners)
    expect(d).toBeGreaterThan(4)
    expect(d).toBeLessThan(160)
  })
})
