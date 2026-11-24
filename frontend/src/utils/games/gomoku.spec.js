import { describe, expect, it } from 'vitest'
import { GOMOKU_SIZE as size, chooseGomokuMove, createGomoku, playGomoku, restoreGomoku } from './gomoku'

const cell = (r, c) => r * size + c
const interleave = (black, white) => black.flatMap((move, i) => (i < white.length ? [move, white[i]] : [move]))

describe('freestyle gomoku', () => {
  it.each([
    [0, 1],
    [1, 0],
    [1, 1],
    [1, -1],
  ])('detects an unbroken five along (%i, %i)', (dr, dc) => {
    const stones = Array.from({ length: 5 }, (_, i) => cell(6 + dr * i, 6 + dc * i))
    const result = restoreGomoku(interleave(stones, [0, 2, 4, 6]))
    expect(result.result).toBe('black')
    expect(result.line.sort((a, b) => a - b)).toEqual(stones.toSorted((a, b) => a - b))
    expect(playGomoku(result, 9)).toBeNull()
  })

  it('counts six stones joined across a gap and never wraps across a board edge', () => {
    const stones = [0, 1, 2, 4, 5, 3].map((c) => cell(7, c))
    const win = restoreGomoku(interleave(stones, [0, 2, 4, 6, 8]))
    expect(win.line).toHaveLength(6)
    expect(restoreGomoku(interleave([13, 14, 15, 16, 17], [30, 32, 34, 36])).result).toBeNull()
  })

  it('recognizes a draw and prefers a last-move win to a draw', () => {
    const black = [],
      white = []
    for (let r = 0; r < size; r++) {
      for (let c = 0; c < size; c++) ((r + 2 * c) % 4 < 2 ? black : white).push(cell(r, c))
    }
    const full = restoreGomoku(interleave(black, white))
    expect(full.result).toBe('draw')
    expect(full.moves).toHaveLength(size * size)
    expect(chooseGomokuMove(full)).toBeNull()
    // At the last free intersection both terminal conditions can be true.
    const board = [...full.board]
    board[1] = null
    for (const i of [0, 2, 3, 4]) board[i] = 'black'
    const before = { ...full, board, moves: full.moves.slice(0, -1), turn: 'black', result: null }
    expect(playGomoku(before, 1).result).toBe('black')
  })

  it('restores by replay and rejects impossible histories', () => {
    const fresh = createGomoku()
    const game = playGomoku(fresh, 112)
    expect(fresh.moves).toEqual([])
    expect(game.turn).toBe('white')
    expect(restoreGomoku(game.moves)).toEqual(game)
    for (const invalid of [
      null,
      {},
      [112, 112],
      [-1],
      [225],
      [0.5],
      new Array(226).fill(0),
      interleave([0, 1, 2, 3, 4], [15, 16, 17, 18, 19]),
    ]) {
      expect(restoreGomoku(invalid)).toBeNull()
    }
  })

  it('takes its own immediate win even when the opponent threatens five', () => {
    const moves = interleave([45, 46, 47, 48, 65], [15, 16, 17, 18])
    const game = restoreGomoku(moves)
    expect(game.turn).toBe('white')
    expect(chooseGomokuMove(game)).toBe(19)
  })

  it('blocks an immediate win including a gap and prefers the center on an empty board', () => {
    expect(chooseGomokuMove(createGomoku())).toBe(cell(7, 7))
    const game = restoreGomoku(interleave([0, 1, 3, 4], [35, 38, 40]))
    expect(chooseGomokuMove(game)).toBe(2)
    expect(game.moves).toHaveLength(7)
  })
})
