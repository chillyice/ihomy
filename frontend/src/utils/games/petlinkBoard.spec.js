import { describe, it, expect } from 'vitest'
import {
  PETLINK_CELLS,
  PETLINK_COLS,
  createPetLinkBoard,
  findPetLinkPair,
  findPetLinkPath,
  petLinkOddTypes,
  removePetLinkPair,
  shufflePetLinkBoard,
  simulatePetLinkLevel,
  typeCountOf,
} from './petlinkBoard'

// 固定种子的 PRNG,保证回归可复现
function mulberry32(seed) {
  return function () {
    seed |= 0
    seed = (seed + 0x6d2b79f5) | 0
    let t = Math.imul(seed ^ (seed >>> 15), 1 | seed)
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

const empty = () => Array.from({ length: PETLINK_CELLS }, () => null)
const put = (board, r, c, type, id) => {
  board[r * PETLINK_COLS + c] = { id, type }
}

describe('createPetLinkBoard', () => {
  it('140 张、每型成对、id 连续唯一', () => {
    for (const level of [1, 2, 5, 8]) {
      const { board, maxId } = createPetLinkBoard(level, 100, mulberry32(level))
      expect(board).toHaveLength(PETLINK_CELLS)
      const counts = new Map()
      const ids = new Set()
      for (const t of board) {
        expect(t.type).toBeGreaterThanOrEqual(1)
        expect(t.type).toBeLessThanOrEqual(typeCountOf(level))
        counts.set(t.type, (counts.get(t.type) || 0) + 1)
        ids.add(t.id)
      }
      expect(ids.size).toBe(PETLINK_CELLS)
      expect(maxId).toBe(100 + PETLINK_CELLS)
      for (const n of counts.values()) expect(n % 2).toBe(0)
      // 种类数不少于该关配置,且每种至少一对
      expect(counts.size).toBeGreaterThanOrEqual(typeCountOf(level))
    }
  })
})

describe('findPetLinkPath', () => {
  it('同行直连 / 一拐 / 两拐 / 绕外圈', () => {
    const board = empty()
    put(board, 0, 0, 1, 1)
    put(board, 0, 5, 1, 2)
    expect(findPetLinkPath(board, 0, 0, 0, 5)).toHaveLength(2)

    const board2 = empty()
    put(board2, 0, 0, 2, 3)
    put(board2, 3, 3, 2, 4)
    expect(findPetLinkPath(board2, 0, 0, 3, 3)).toHaveLength(3)

    const board3 = empty()
    put(board3, 1, 1, 3, 5)
    put(board3, 4, 4, 3, 6)
    put(board3, 1, 2, 9, 7)
    put(board3, 2, 1, 9, 8)
    put(board3, 2, 2, 9, 9)
    expect(findPetLinkPath(board3, 1, 1, 4, 4)).toHaveLength(4)

    const board4 = empty()
    put(board4, 0, 0, 4, 10)
    put(board4, 0, 13, 4, 11)
    for (let c = 1; c < 13; c++) put(board4, 0, c, 9, 20 + c)
    // 直线被堵,只能从上方外圈绕过去
    expect(findPetLinkPath(board4, 0, 0, 0, 13)).toEqual([
      [0, 0],
      [-1, 0],
      [-1, 13],
      [0, 13],
    ])
  })

  it('不同类型 / 完全堵死返回 null', () => {
    const board = empty()
    put(board, 0, 0, 1, 1)
    put(board, 0, 1, 2, 2)
    expect(findPetLinkPath(board, 0, 0, 0, 1)).toBeNull()
  })
})

describe('removePetLinkPair 不变量(第 5/8 关历史缺陷回归)', () => {
  it('第 1~2 关只清掉两张', () => {
    for (const level of [1, 2]) {
      const board = empty()
      put(board, 2, 2, 7, 1)
      put(board, 2, 9, 7, 2)
      removePetLinkPair(board, level, 2 * PETLINK_COLS + 2, 2 * PETLINK_COLS + 9)
      expect(board.filter(Boolean)).toHaveLength(0)
    }
  })

  it('第 5 关同行连消:先移右张,不会多消左边的牌', () => {
    // 旧实现先移左张,左移后第二张坐标落到了右邻牌身上,连带吃掉一张无关宠物
    const board = empty()
    for (let c = 0; c < 14; c++) put(board, 0, c, c + 1, c + 1)
    const left = 0 * PETLINK_COLS + 2
    const right = 0 * PETLINK_COLS + 6
    board[left].type = 1
    board[right].type = 1
    const keptIds = board.filter(Boolean).map((t) => t.id)
    removePetLinkPair(board, 5, left, right)
    const afterIds = new Set(board.filter(Boolean).map((t) => t.id))
    expect(keptIds.filter((id) => !afterIds.has(id)).sort((x, y) => x - y)).toEqual([left + 1, right + 1])
    expect(board.filter(Boolean)).toHaveLength(12)
  })

  it('第 8 关同行连消:先移左张,不会多消右边的牌', () => {
    const board = empty()
    for (let c = 0; c < 14; c++) put(board, 0, c, c + 1, c + 1)
    const left = 0 * PETLINK_COLS + 3
    const right = 0 * PETLINK_COLS + 9
    board[left].type = 1
    board[right].type = 1
    const keptIds = board.filter(Boolean).map((t) => t.id)
    removePetLinkPair(board, 8, left, right)
    const afterIds = new Set(board.filter(Boolean).map((t) => t.id))
    expect(keptIds.filter((id) => !afterIds.has(id)).sort((x, y) => x - y)).toEqual([left + 1, right + 1])
    expect(board.filter(Boolean)).toHaveLength(12)
  })
})

describe('simulatePetLinkLevel 全关卡自动对局', () => {
  it('第 3~9 关每步都只吃掉同型两张,剩余牌始终成对', () => {
    for (let level = 3; level <= 9; level++) {
      const report = simulatePetLinkLevel(level, 40, mulberry32(1000 + level))
      expect(report.issues, `第 ${level} 关自检异常:${JSON.stringify(report.issues.slice(0, 3))}`).toEqual([])
      expect(report.steps).toBeGreaterThan(0)
    }
  }, 30000) // 7 关 × 40 局全量模拟是纯 CPU 重活:并行跑全量测试时默认 5s 会被机器负载挤爆(误报超时)

  it('第 1~2 关(无牌位移动)同样成立', () => {
    for (const level of [1, 2]) {
      const report = simulatePetLinkLevel(level, 20, mulberry32(2000 + level))
      expect(report.issues).toEqual([])
    }
  })
})

describe('petLinkOddTypes', () => {
  it('奇数张会被点名', () => {
    const board = empty()
    put(board, 0, 0, 1, 1)
    put(board, 0, 1, 1, 2)
    put(board, 0, 2, 2, 3)
    expect(petLinkOddTypes(board)).toEqual([{ type: 2, count: 1 }])
  })
})

describe('findPetLinkPair', () => {
  it('随机盘面配合重排可以消到空盘', () => {
    const rand = mulberry32(7)
    const { board } = createPetLinkBoard(5, 0, rand)
    let guard = 0
    while (guard++ < 800) {
      const pair = findPetLinkPair(board)
      if (!pair) {
        shufflePetLinkBoard(board, rand)
        continue
      }
      removePetLinkPair(board, 5, pair[0], pair[1])
      if (!board.some(Boolean)) break
    }
    expect(board.filter(Boolean)).toHaveLength(0)
  })
})
