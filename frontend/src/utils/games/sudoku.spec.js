import { describe, it, expect } from 'vitest'
import {
  CELLS,
  DIFFICULTIES,
  cageIndexOf,
  clearPeerNotes,
  countSolutions,
  digitCounts,
  extraGroupsFor,
  findConflicts,
  generatePuzzle,
  generateSolution,
  isSolved,
  isValidBoard,
  isValidCages,
  notesOf,
  pickHintIndex,
} from './sudoku'

// 可复现的随机源:同一个种子生成同一道题,测试结果稳定
function seeded(seed = 1) {
  let s = seed >>> 0
  return () => {
    s = (s * 1664525 + 1013904223) >>> 0
    return s / 4294967296
  }
}

const emptyBoard = () => new Array(CELLS).fill(0)

describe('数独生成', () => {
  it('终盘每行/每列/每宫都是 1~9 不重复', () => {
    const board = generateSolution(seeded(7))
    expect(board).toHaveLength(CELLS)
    expect(board.every((v) => v >= 1 && v <= 9)).toBe(true)
    for (let r = 0; r < 9; r++) {
      expect(new Set(board.slice(r * 9, r * 9 + 9)).size).toBe(9)
    }
    for (let c = 0; c < 9; c++) {
      expect(new Set(Array.from({ length: 9 }, (_, r) => board[r * 9 + c])).size).toBe(9)
    }
    expect(findConflicts(board).size).toBe(0)
  })

  it('生成的题目只有一个解,且给定数字都来自终盘', () => {
    const { puzzle, solution, blanks } = generatePuzzle('MEDIUM', seeded(11))
    expect(blanks).toBeGreaterThanOrEqual(40)
    expect(blanks).toBeLessThanOrEqual(DIFFICULTIES.MEDIUM)
    expect(countSolutions(puzzle, 2)).toBe(1)
    for (let i = 0; i < CELLS; i++) {
      if (puzzle[i]) expect(puzzle[i]).toBe(solution[i])
    }
    expect(findConflicts(puzzle).size).toBe(0)
  })

  it('不同种子生成不同题目', () => {
    const a = generatePuzzle('EASY', seeded(1))
    const b = generatePuzzle('EASY', seeded(2))
    expect(a.puzzle.join('')).not.toBe(b.puzzle.join(''))
  })

  it('解计数能识别无解盘面', () => {
    const board = emptyBoard()
    board[0] = 5
    board[1] = 5 // 同一行两个 5
    expect(countSolutions(board, 2)).toBe(0)
  })
})

describe('数独判定', () => {
  it('冲突检测覆盖行/列/宫,空格不参与', () => {
    const board = emptyBoard()
    board[0] = 3
    board[8] = 3 // 同行
    board[72] = 4
    board[9] = 4 // 同列(0 列与 0 行交叉处无影响)
    board[10] = 7
    board[11] = 7 // 同宫
    const conflicts = findConflicts(board)
    expect(conflicts.has(0)).toBe(true)
    expect(conflicts.has(8)).toBe(true)
    expect(conflicts.has(72)).toBe(true)
    expect(conflicts.has(9)).toBe(true)
    expect(conflicts.has(10)).toBe(true)
    expect(conflicts.has(11)).toBe(true)
    expect(conflicts.size).toBe(6)
  })

  it('isSolved 要求填满且无冲突', () => {
    const full = generateSolution(seeded(3))
    expect(isSolved(full)).toBe(true)
    const withHole = full.slice()
    withHole[40] = 0
    expect(isSolved(withHole)).toBe(false)
    const withDup = full.slice()
    withDup[1] = withDup[0]
    expect(isSolved(withDup)).toBe(false)
  })

  it('数字计数与笔记转换', () => {
    const board = emptyBoard()
    board[0] = 5
    board[1] = 5
    board[2] = 9
    expect(digitCounts(board)[5]).toBe(2)
    expect(digitCounts(board)[9]).toBe(1)
    expect(digitCounts(board)[1]).toBe(0)

    const mask = (1 << 0) | (1 << 3) | (1 << 8)
    expect(notesOf(mask)).toEqual([1, 4, 9])
  })

  it('落数字后清掉同行/列/宫的相同笔记', () => {
    const notes = new Array(CELLS).fill(0)
    notes[1] = 1 << 4 // 同行邻居,含 5
    notes[9] = 1 << 4 // 同列邻居,含 5
    notes[10] = 1 << 2 // 同宫邻居,含 3(不该被清)
    expect(clearPeerNotes(notes, 0, 5)).toBe(true)
    expect(notesOf(notes[1])).toEqual([])
    expect(notesOf(notes[9])).toEqual([])
    expect(notesOf(notes[10])).toEqual([3])
    // 再清一次没有变化
    expect(clearPeerNotes(notes, 0, 5)).toBe(false)
  })

  it('提示只挑空格', () => {
    const board = emptyBoard()
    for (let i = 0; i < CELLS - 1; i++) board[i] = 1
    expect(pickHintIndex(board, () => 0.99)).toBe(CELLS - 1)
    const full = generateSolution(seeded(5))
    expect(pickHintIndex(full)).toBe(-1)
  })

  it('存档结构校验挡脏数据', () => {
    expect(isValidBoard(emptyBoard())).toBe(true)
    expect(isValidBoard(new Array(80).fill(0))).toBe(false)
    expect(isValidBoard([...emptyBoard().slice(1), 'x'])).toBe(false)
    expect(isValidBoard([...emptyBoard().slice(1), 10])).toBe(false)
  })
})

describe('数独变体(对角线/杀手)', () => {
  it('对角线终盘两条对角线都是 1~9,题目唯一解', () => {
    const { puzzle, solution } = generatePuzzle('MEDIUM', seeded(21), { variant: 'DIAGONAL' })
    const mainDiag = Array.from({ length: 9 }, (_, i) => i * 9 + i)
    const antiDiag = Array.from({ length: 9 }, (_, i) => i * 9 + (8 - i))
    for (const diag of [mainDiag, antiDiag]) {
      expect(new Set(diag.map((i) => solution[i])).size).toBe(9)
    }
    expect(countSolutions(puzzle, 2, { extraGroups: extraGroupsFor('DIAGONAL') })).toBe(1)
    expect(generatePuzzle('EASY', seeded(1)).variant).toBe('CLASSIC')
  })

  it('对角线冲突检测:标准模式不冲突,附加对角线组后冲突', () => {
    const board = emptyBoard()
    board[0] = 5 // 主对角线起点
    board[40] = 5 // 中心(同对角线,不同行/列/宫)
    expect(findConflicts(board).size).toBe(0)
    const conflicts = findConflicts(board, extraGroupsFor('DIAGONAL'))
    expect(conflicts.has(0)).toBe(true)
    expect(conflicts.has(40)).toBe(true)
  })

  it('杀手题:全空盘,笼覆盖全盘、笼和与终盘一致且唯一解', () => {
    const { puzzle, solution, cages, blanks } = generatePuzzle('EASY', seeded(13), { variant: 'KILLER' })
    expect(blanks).toBe(CELLS)
    expect(puzzle.every((v) => v === 0)).toBe(true)
    expect(isValidCages(cages)).toBe(true)
    for (const cage of cages) {
      expect(cage.cells.reduce((acc, i) => acc + solution[i], 0)).toBe(cage.sum)
      // 笼内数字互不重复(终盘口径)
      expect(new Set(cage.cells.map((i) => solution[i])).size).toBe(cage.cells.length)
    }
    expect(countSolutions(puzzle, 2, { cages })).toBe(1)
  })

  it('杀手判定:填满无冲突但笼和不对不算完成', () => {
    const full = generateSolution(seeded(3))
    expect(isSolved(full, { cages: [{ cells: [0, 1], sum: full[0] + full[1] }] })).toBe(true)
    expect(isSolved(full, { cages: [{ cells: [0, 1], sum: full[0] + full[1] + 1 }] })).toBe(false)
  })

  it('笼工具:cageIndexOf 映射与 isValidCages 挡脏数据', () => {
    const idx = cageIndexOf([
      { cells: [4, 5], sum: 9 },
      { cells: [6], sum: 2 },
    ])
    expect(idx[4]).toBe(0)
    expect(idx[5]).toBe(0)
    expect(idx[6]).toBe(1)
    expect(idx[7]).toBe(-1)

    expect(isValidCages([])).toBe(false)
    expect(isValidCages([{ cells: [0, 1], sum: 5 }])).toBe(false) // 未覆盖全盘
    expect(
      isValidCages([
        { cells: [0, 0], sum: 3 },
        { cells: [1], sum: 2 },
        ...Array.from({ length: 79 }, (_, k) => ({ cells: [k + 2], sum: 1 })),
      ]),
    ).toBe(false) // 重叠
    expect(isValidCages([{ cells: [0, 1], sum: 0 }, ...Array.from({ length: 79 }, (_, k) => ({ cells: [k + 2], sum: 1 }))])).toBe(false) // sum 越界
  })
})
