import { describe, expect, it } from 'vitest'
import {
  CHECKER_HOLES,
  checkersMoves,
  checkersNextSteps,
  chooseCheckersMove,
  createCheckersGame,
  playCheckersMove,
  restoreCheckersGame,
} from './chineseCheckers.js'

const hole = (q, r) => CHECKER_HOLES.find((h) => h.q === q && h.r === r).id

function branchingChain() {
  const game = createCheckersGame()
  game.board.fill(null)
  game.board[hole(0, 0)] = 1
  game.board[hole(1, 0)] = 1
  game.board[hole(0, 1)] = 0
  game.board[hole(2, 1)] = 0
  game.board[hole(1, 2)] = 1
  return game
}

describe('Chinese checkers', () => {
  it('builds the 121-hole six-point star with symmetric opposed camps of ten', () => {
    const game = createCheckersGame()
    expect(CHECKER_HOLES).toHaveLength(121)
    expect(Array.from({ length: 17 }, (_, i) => CHECKER_HOLES.filter((h) => h.r === i - 8).length)).toEqual([
      1, 2, 3, 4, 13, 12, 11, 10, 9, 10, 11, 12, 13, 4, 3, 2, 1,
    ])
    for (const { id, q, r, camp } of CHECKER_HOLES) {
      expect(CHECKER_HOLES[hole(-q, -r)].camp).toBe(camp === null ? null : 1 - camp)
      expect(game.board[id]).toBe(camp)
    }
    expect(game.board.filter((v) => v === null)).toHaveLength(101)
    expect(game.player).toBe(1)
  })

  it('allows one vacant adjacent hole in any of six directions, then changes turns', () => {
    const game = branchingChain()
    game.board.fill(null)
    const from = hole(0, 0)
    game.board[from] = 1
    const steps = checkersNextSteps(game, [from])
    expect(steps).toHaveLength(6)
    expect(steps.every((s) => !s.jump)).toBe(true)
    const to = steps[0].to
    expect(checkersNextSteps(game, [from, to])).toEqual([])
    expect(playCheckersMove(game, from, hole(2, 0), [from, to, hole(2, 0)])).toBe(false)
    expect(playCheckersMove(game, from, to)).toBe(true)
    expect(game).toMatchObject({ player: 0, turns: 1, lastMove: { from, to, path: [from, to] } })
    expect(checkersMoves(game, to)).toEqual([])
  })

  it('jumps over friendly or opposing marbles, may turn, and can stop at an intermediate hole', () => {
    const from = hole(0, 0),
      a = hole(2, 0),
      b = hole(0, 2),
      to = hole(2, 2)
    const game = branchingChain()
    const before = [...game.board]
    for (const middle of [a, b]) {
      expect(checkersNextSteps(game, [from])).toContainEqual({ to: middle, jump: true })
      expect(checkersNextSteps(game, [from, middle])).toContainEqual({ to, jump: true })
      expect(checkersNextSteps(game, [from, middle]).some((s) => s.to === from)).toBe(false)
    }
    const moves = checkersMoves(game, from)
    expect(moves.filter((m) => m.to === to)).toHaveLength(1)
    const other = moves.find((m) => m.to === to).path[1] === a ? b : a
    const path = [from, other, to]
    expect(playCheckersMove(game, from, to, path)).toBe(true)
    expect(game.lastMove.path).toEqual(path)
    const expected = [...before]
    expected[from] = null
    expected[to] = 1
    expect(game.board).toEqual(expected)
    expect(playCheckersMove(branchingChain(), from, a)).toBe(true)
  })

  it('rejects occupied landings, multiple intervening marbles, mixed steps, and malformed paths without changing state', () => {
    const game = branchingChain()
    const from = hole(0, 0),
      mid = hole(2, 0),
      to = hole(2, 2)
    game.board[mid] = 0
    const before = structuredClone(game)
    for (const p of [
      [from, mid],
      [from, hole(3, 0)],
      [from, hole(0, 2), hole(0, 3)],
      [from, mid, to],
      [from, hole(0, 2), from],
      [from, -1],
      [from, 999],
      [from, '1'],
      [null, to],
    ]) {
      expect(playCheckersMove(game, p[0], p.at(-1), p)).toBe(false)
      expect(checkersNextSteps(game, p)).toEqual([])
    }
    expect(game).toEqual(before)
  })

  it('permits travel through unoccupied side arms and permits moving out of either camp', () => {
    const game = branchingChain()
    for (const [from, to] of [
      [hole(4, -2), hole(5, -2)],
      [hole(1, -5), hole(1, -4)],
      [hole(-1, 5), hole(-1, 4)],
    ]) {
      game.board.fill(null)
      game.board[from] = 1
      game.player = 1
      expect(playCheckersMove(game, from, to)).toBe(true)
    }
  })

  it('finishes only on the tenth arrival and lets the computer find it immediately', () => {
    const game = createCheckersGame()
    game.board.fill(null)
    for (const h of CHECKER_HOLES.filter((h) => h.camp === 0)) game.board[h.id] = 1
    const from = hole(1, -4),
      to = hole(1, -5)
    game.board[to] = null
    game.board[from] = 1
    for (const h of CHECKER_HOLES.filter((h) => h.r === 0 || h.r === 1).slice(0, 10)) game.board[h.id] = 0
    game.turns = 40
    const move = chooseCheckersMove(game)
    expect(move).toMatchObject({ from, to })
    expect(playCheckersMove(game, from, to, move.path)).toBe(true)
    expect(game).toMatchObject({ winner: 1, player: 1, turns: 41 })
    expect(chooseCheckersMove(game)).toBeNull()
    expect(restoreCheckersGame(game)).toEqual(game)
    expect(playCheckersMove(game, to, from)).toBe(false)
  })

  it('restores independent snapshots and rejects contradictory turns, counts, winners and paths', () => {
    const game = createCheckersGame()
    expect(restoreCheckersGame(game)).toEqual(game)
    const m = chooseCheckersMove(game)
    playCheckersMove(game, m.from, m.to, m.path)
    const copy = restoreCheckersGame(JSON.parse(JSON.stringify(game)))
    expect(copy).toEqual(game)
    expect(copy.board).not.toBe(game.board)
    expect(copy.lastMove.path).not.toBe(game.lastMove.path)
    for (const bad of [
      null,
      { ...game, turns: 0 },
      { ...game, board: [...game.board, 1] },
      { ...game, player: 1 },
      { ...game, board: game.board.map((v) => (v === 0 ? null : v)) },
      { ...game, winner: 1 },
      { ...game, lastMove: null },
      { ...game, lastMove: { ...m, path: [m.to, m.from] } },
      { ...game, turns: Infinity },
    ]) {
      expect(restoreCheckersGame(bad)).toBeNull()
    }
  })

  it('plays a full computer match with legal moves and no changes during move selection', () => {
    const game = createCheckersGame()
    for (let turn = 0; turn < 200 && game.winner === null; turn++) {
      const before = structuredClone(game)
      const m = chooseCheckersMove(game)
      expect(game).toEqual(before)
      expect(playCheckersMove(game, m.from, m.to, m.path)).toBe(true)
      expect(restoreCheckersGame(game)).toEqual(game)
    }
    expect([0, 1]).toContain(game.winner)
    expect(CHECKER_HOLES.filter((h) => h.camp === 1 - game.winner && game.board[h.id] === game.winner)).toHaveLength(10)
  })
})
