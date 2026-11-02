import { describe, it, expect } from 'vitest'
import {
  COLS,
  ROWS,
  SHAPES,
  STATUS,
  TYPES,
  cellsOf,
  createGame,
  emptyBoard,
  ghostY,
  hardDrop,
  holdPiece,
  isValidState,
  rotate,
  softDrop,
  tick,
  tryMove,
} from './tetris'

function seeded(seed = 1) {
  let s = seed >>> 0
  return () => {
    s = (s * 1664525 + 1013904223) >>> 0
    return s / 4294967296
  }
}

const baseState = (patch = {}) => ({
  board: emptyBoard(),
  bag: ['T', 'S'],
  queue: ['S', 'Z', 'J', 'L', 'T'],
  current: { type: 'T', rotation: 0, x: 3, y: 0 },
  hold: null,
  canHold: true,
  score: 0,
  lines: 0,
  level: 1,
  lastClear: 0,
  clearSeq: 0,
  status: STATUS.PLAYING,
  ...patch,
})

describe('俄罗斯方块棋盘与方块', () => {
  it('新局棋盘 10×20 全空,当前块合法且队列足够', () => {
    const state = createGame(seeded(3))
    expect(state.board).toHaveLength(ROWS)
    expect(state.board.every((row) => row.length === COLS && row.every((c) => c === null))).toBe(true)
    expect(TYPES).toContain(state.current.type)
    expect(state.queue.length).toBeGreaterThanOrEqual(5)
    expect(state.status).toBe(STATUS.PLAYING)
    expect(isValidState(state)).toBe(true)
  })

  it('每个方块的四种旋转态都恰好四格且不越出包围盒', () => {
    for (const type of TYPES) {
      expect(SHAPES[type]).toHaveLength(4)
      for (let rotation = 0; rotation < 4; rotation++) {
        const cells = cellsOf({ type, rotation, x: 3, y: 0 })
        expect(cells).toHaveLength(4)
        expect(new Set(cells.map((c) => `${c.x},${c.y}`)).size).toBe(4)
      }
    }
  })

  it('7-bag:连续七块正好把七种方块各出一次', () => {
    let state = createGame(seeded(5))
    const seen = []
    for (let i = 0; i < 7; i++) {
      seen.push(state.current.type)
      state = hardDrop(state, seeded(5))
    }
    expect(new Set(seen).size).toBe(7)
  })
})

describe('俄罗斯方块移动与旋转', () => {
  it('左右移动会撞墙停住,向下移动一格', () => {
    const state = baseState()
    let moved = state
    for (let i = 0; i < 20; i++) moved = tryMove(moved, -1, 0)
    expect(Math.min(...cellsOf(moved.current).map((c) => c.x))).toBe(0)

    const down = tryMove(state, 0, 1)
    expect(down.current.y).toBe(1)
    expect(state.current.y).toBe(0)
  })

  it('旋转四次回到原状态,O 形不旋转', () => {
    let state = baseState()
    state = rotate(state, 1)
    state = rotate(state, 1)
    state = rotate(state, 1)
    state = rotate(state, 1)
    expect(state.current.rotation).toBe(0)

    const oState = baseState({ current: { type: 'O', rotation: 0, x: 3, y: 0 } })
    expect(rotate(oState, 1)).toBe(oState)
  })

  it('ghost 预览落在最底,硬降按距离加分并锁定', () => {
    const state = baseState({ current: { type: 'O', rotation: 0, x: 3, y: 0 } })
    expect(ghostY(state)).toBe(18)

    const dropped = hardDrop(state, seeded(7))
    expect(dropped.score).toBe(36)
    expect(dropped.current.type).not.toBe('O')
    const filled = dropped.board.flat().filter(Boolean)
    expect(filled).toHaveLength(4)
    expect(filled.every((c) => c === 'O')).toBe(true)
    expect(dropped.board[19].filter(Boolean)).toHaveLength(2)
  })

  it('软降每次加 1 分', () => {
    const state = baseState()
    const dropped = softDrop(state)
    expect(dropped.score).toBe(1)
    expect(dropped.current.y).toBe(1)
  })

  it('重力下落到底后锁定并出下一块', () => {
    let state = baseState()
    for (let i = 0; i < 30; i++) state = tick(state, seeded(9))
    expect(state.current.type).not.toBe('T')
    expect(state.board.flat().filter(Boolean)).toHaveLength(4)
  })
})

describe('俄罗斯方块消行与暂存', () => {
  it('一行填满后消行,按等级加分并更新行数与等级', () => {
    const board = emptyBoard()
    for (let c = 0; c < COLS; c++) if (c < 3 || c > 6) board[19][c] = 'T'
    const state = baseState({
      board,
      current: { type: 'I', rotation: 0, x: 3, y: 18 },
      lines: 9,
      level: 1,
    })
    const result = hardDrop(state, seeded(11))
    expect(result.lines).toBe(10)
    expect(result.level).toBe(2)
    expect(result.score).toBe(100)
    expect(result.lastClear).toBe(1)
    expect(result.board[19].every((c) => c === null)).toBe(true)
  })

  it('暂存一次后本回合不能再用,落定后恢复', () => {
    const state = baseState()
    const held = holdPiece(state, seeded(13))
    expect(held.hold).toBe('T')
    expect(held.canHold).toBe(false)
    expect(holdPiece(held, seeded(13))).toBe(held)

    const locked = hardDrop(held, seeded(13))
    expect(locked.canHold).toBe(true)
  })

  it('出生点被占满时判负', () => {
    const board = emptyBoard()
    for (let r = 0; r < 4; r++) for (let c = 3; c <= 6; c++) board[r][c] = 'I'
    const state = baseState({ board, current: { type: 'O', rotation: 0, x: 3, y: 0 } })
    expect(hardDrop(state, seeded(17)).status).toBe(STATUS.OVER)
  })
})

describe('俄罗斯方块读档校验', () => {
  it('合法局面可通过,非法格子或越界方块被拒绝', () => {
    const state = hardDrop(createGame(seeded(19)), seeded(19))
    expect(isValidState(state)).toBe(true)
    expect(isValidState(null)).toBe(false)

    const badCell = JSON.parse(JSON.stringify(state))
    badCell.board[0][0] = 'X'
    expect(isValidState(badCell)).toBe(false)

    const badPiece = JSON.parse(JSON.stringify(state))
    badPiece.current.x = -99
    expect(isValidState(badPiece)).toBe(false)
  })
})
