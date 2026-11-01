import { describe, it, expect } from 'vitest'
import {
  CELLS,
  DIFFICULTIES,
  clearPeerNotes,
  countSolutions,
  digitCounts,
  findConflicts,
  generatePuzzle,
  generateSolution,
  isSolved,
  isValidBoard,
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
