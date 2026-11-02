import { describe, it, expect } from 'vitest'
import {
  LEVELS,
  STATUS,
  chord,
  createBoard,
  isValidBoard,
  neighborsOf,
  placeMines,
  remainingMines,
  revealCell,
  toggleFlag,
} from './minesweeper'

function seeded(seed = 1) {
  let s = seed >>> 0
  return () => {
    s = (s * 1664525 + 1013904223) >>> 0
    return s / 4294967296
  }
}

const centerIndex = (board) => Math.floor(board.rows / 2) * board.cols + Math.floor(board.cols / 2)

describe('扫雷布雷', () => {
  it('新棋盘尺寸/雷数取自难度,尚未布雷', () => {
    const board = createBoard('MEDIUM')
    expect(board.rows).toBe(LEVELS.MEDIUM.rows)
    expect(board.cols).toBe(LEVELS.MEDIUM.cols)
    expect(board.mines).toBe(LEVELS.MEDIUM.mines)
    expect(board.cells).toHaveLength(board.rows * board.cols)
    expect(board.minesPlaced).toBe(false)
    expect(board.status).toBe(STATUS.READY)
  })

  it('首点及八邻都不是雷,且 adjacent 与实际邻雷一致', () => {
    const board = createBoard('EASY')
    const safe = centerIndex(board)
    const placed = placeMines(board, safe, seeded(3))
    const forbidden = new Set([safe, ...neighborsOf(placed, safe)])
    expect(placed.cells.filter((c) => c.mine)).toHaveLength(placed.mines)
    for (const index of forbidden) expect(placed.cells[index].mine).toBe(false)
    for (let i = 0; i < placed.cells.length; i++) {
      if (placed.cells[i].mine) continue
      const expected = neighborsOf(placed, i).filter((n) => placed.cells[n].mine).length
      expect(placed.cells[i].adjacent).toBe(expected)
    }
    expect(placed.minesPlaced).toBe(true)
    expect(placed.status).toBe(STATUS.PLAYING)
  })

  it('已布雷的棋盘再布雷不会改变雷位', () => {
    const placed = placeMines(createBoard('EASY'), centerIndex(createBoard('EASY')), seeded(9))
    const again = placeMines(placed, 0, seeded(99))
    expect(again.cells.map((c) => c.mine)).toEqual(placed.cells.map((c) => c.mine))
  })
})

describe('扫雷翻格', () => {
  it('首次翻格安全,0 邻雷会连锁展开多格', () => {
    const board = createBoard('EASY')
    const safe = centerIndex(board)
    const next = revealCell(board, safe, seeded(5))
    const cell = next.cells[safe]
    expect(cell.mine).toBe(false)
    expect(cell.revealed).toBe(true)
    expect(cell.adjacent).toBe(0)
    expect(next.revealedCount).toBeGreaterThan(1)
    expect(next.status).toBe(STATUS.PLAYING)
  })

  it('踩雷亮出全部雷并结束,之后不再接受翻格', () => {
    const placed = placeMines(createBoard('EASY'), centerIndex(createBoard('EASY')), seeded(7))
    const mine = placed.cells.findIndex((c) => c.mine)
    const lost = revealCell(placed, mine)
    expect(lost.status).toBe(STATUS.LOST)
    expect(lost.exploded).toBe(mine)
    expect(lost.cells.filter((c) => c.revealed)).toHaveLength(lost.mines)
    expect(
      revealCell(
        lost,
        placed.cells.findIndex((c, i) => !c.mine && i !== mine),
      ),
    ).toBe(lost)
  })

  it('翻完所有非雷格即胜利,雷全部自动插旗', () => {
    const placed = placeMines(createBoard('EASY'), centerIndex(createBoard('EASY')), seeded(11))
    let board = placed
    for (let i = 0; i < placed.cells.length; i++) {
      if (!placed.cells[i].mine) board = revealCell(board, i)
    }
    expect(board.status).toBe(STATUS.WON)
    expect(board.revealedCount).toBe(board.rows * board.cols - board.mines)
    expect(board.flagCount).toBe(board.mines)
    expect(board.cells.filter((c) => c.mine && c.flagged)).toHaveLength(board.mines)
  })
})

describe('扫雷插旗与和弦', () => {
  it('插旗/取消插旗只影响未翻开的格子', () => {
    const board = revealCell(createBoard('EASY'), centerIndex(createBoard('EASY')), seeded(13))
    const hidden = board.cells.findIndex((c) => !c.revealed)
    const flagged = toggleFlag(board, hidden)
    expect(flagged.cells[hidden].flagged).toBe(true)
    expect(flagged.flagCount).toBe(1)
    expect(remainingMines(flagged)).toBe(flagged.mines - 1)
    expect(toggleFlag(flagged, hidden).cells[hidden].flagged).toBe(false)

    const revealedIndex = board.cells.findIndex((c) => c.revealed)
    expect(toggleFlag(board, revealedIndex)).toBe(board)
    expect(toggleFlag(board, -1)).toBe(board)
  })

  it('旗数等于数字时和弦翻开其余邻格', () => {
    let board = revealCell(
      placeMines(createBoard('EASY'), centerIndex(createBoard('EASY')), seeded(17)),
      centerIndex(createBoard('EASY')),
      seeded(17),
    )
    const index = board.cells.findIndex((c) => c.revealed && c.adjacent > 0)
    expect(index).toBeGreaterThanOrEqual(0)
    const around = neighborsOf(board, index)
    const mineNeighbors = around.filter((n) => board.cells[n].mine)
    expect(mineNeighbors.length).toBe(board.cells[index].adjacent)
    for (const n of mineNeighbors) board = toggleFlag(board, n)

    const after = chord(board, index)
    for (const n of around) {
      if (board.cells[n].mine) continue
      expect(after.cells[n].revealed).toBe(true)
    }
    expect(after.status).not.toBe(STATUS.LOST)
  })

  it('旗数不足时和弦不生效;未翻开的格子不响应和弦', () => {
    let board = revealCell(
      placeMines(createBoard('EASY'), centerIndex(createBoard('EASY')), seeded(19)),
      centerIndex(createBoard('EASY')),
      seeded(19),
    )
    // 选一个至少挨着两颗雷的格子,插一面旗时才严格「旗数不足」
    const index = board.cells.findIndex((c) => c.revealed && c.adjacent >= 2)
    expect(index).toBeGreaterThanOrEqual(0)
    expect(chord(board, index)).toBe(board)

    const mineNeighbor = neighborsOf(board, index).find((n) => board.cells[n].mine)
    board = toggleFlag(board, mineNeighbor)
    expect(chord(board, index)).toBe(board)
    expect(
      chord(
        board,
        board.cells.findIndex((c) => !c.revealed),
      ),
    ).toBe(board)
  })
})

describe('扫雷读档校验', () => {
  it('合法存档可通过,篡改邻雷数会被拒绝', () => {
    const board = revealCell(
      placeMines(createBoard('EASY'), centerIndex(createBoard('EASY')), seeded(23)),
      centerIndex(createBoard('EASY')),
      seeded(23),
    )
    expect(isValidBoard(board)).toBe(true)
    expect(isValidBoard(null)).toBe(false)

    const tampered = JSON.parse(JSON.stringify(board))
    const index = tampered.cells.findIndex((c) => !c.mine)
    tampered.cells[index].adjacent = (tampered.cells[index].adjacent + 1) % 9
    expect(isValidBoard(tampered)).toBe(false)
  })

  it('首点前允许已有插旗,但不允许已有雷', () => {
    const board = createBoard('EASY')
    const flagged = toggleFlag(board, 0)
    expect(isValidBoard(flagged)).toBe(true)

    const bad = JSON.parse(JSON.stringify(flagged))
    bad.cells[1].mine = true
    expect(isValidBoard(bad)).toBe(false)
  })
})
