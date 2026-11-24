import { describe, expect, it } from 'vitest'
import {
  createEightBallGame,
  placeEightBallCue,
  restoreEightBallGame,
  ruleEightBallShot,
  shootEightBall,
  stepEightBall,
} from './eightBall.js'

const contact = (id) => ({ type: 'contact', id })
const rail = (id) => ({ type: 'rail', id })
const pocket = (id) => ({ type: 'pocket', id })

function rule(events, { targets = [1, 2], player = 0, shots = 2, groups = ['solid', 'stripe'] } = {}) {
  return ruleEightBallShot({ player, groups, shots }, targets, events)
}

function settle(game) {
  for (let i = 0; i < 600 && game.phase === 'rolling'; i++) stepEightBall(game, 1 / 60)
  expect(game.phase).not.toBe('rolling')
}

describe('eight-ball house rules', () => {
  it('counts only distinct object balls hitting cushions after contact on a dry break', () => {
    const opening = { shots: 1, groups: [null, null] }
    expect(rule([rail(7), contact(1), rail(0), rail(1), rail(2), rail(3), rail(3)], opening)).toMatchObject({
      player: 1,
      ballInHand: true,
      lastResult: 'foulBreak',
    })
    expect(rule([contact(1), rail(1), rail(2), rail(3), rail(4)], opening)).toMatchObject({
      player: 1,
      ballInHand: false,
      lastResult: 'switch',
    })
  })

  it('keeps the break open even on scoring, and respots a break eight even with a scratch', () => {
    for (const scratch of [false, true]) {
      const g = createEightBallGame()
      // Start the eight at the mouth, with the cue behind it on the pocket line.
      Object.assign(g.balls[0], { x: 400, y: 65 })
      Object.assign(g.balls[8], { x: 400, y: 30 })
      shootEightBall(g, -Math.PI / 2, 0.4)
      if (scratch) {
        Object.assign(g.balls[0], { x: 400, y: 10 })
        g.balls[8].vy = -120
      }
      settle(g)
      expect(g.groups).toEqual([null, null])
      expect(g.balls[8].pocketed).toBe(false)
      expect(g.winner).toBeNull()
      expect(g.lastResult).toBe(scratch ? 'foulScratch' : 'continue')
      expect(g.penalties).toBe(scratch ? 1 : 0)
      expect(g.ballInHand).toBe(scratch)
      expect(restoreEightBallGame(g)).toEqual(g)
    }
  })

  it('assigns the first sunk color on a legal open stroke, including mixed pockets', () => {
    const open = { player: 1, groups: [null, null], targets: [1, 12] }
    expect(rule([contact(1), pocket(12), pocket(1)], open)).toMatchObject({
      player: 1,
      groups: ['solid', 'stripe'],
      lastResult: 'continue',
    })
    expect(rule([contact(8), pocket(12)], open)).toMatchObject({
      player: 0,
      groups: [null, null],
      lastResult: 'foulContact',
    })
  })

  it('requires legal first contact and a subsequent pocket or cushion, allows the cue cushion', () => {
    expect(rule([rail(0), contact(1)])).toMatchObject({ lastResult: 'foulRail', ballInHand: true })
    expect(rule([contact(1), rail(0)])).toMatchObject({ lastResult: 'switch', ballInHand: false })
    expect(rule([contact(12), contact(1), pocket(1)])).toMatchObject({ lastResult: 'foulContact', player: 1 })
    expect(rule([rail(0)])).toMatchObject({ lastResult: 'foulContact' })
    expect(rule([contact(1), pocket(12)])).toMatchObject({ lastResult: 'switch' })
    expect(rule([contact(1), pocket(12), pocket(1)])).toMatchObject({ lastResult: 'continue' })
  })

  it('loses for an early eight or scratch with the eight, even if the last color dropped first', () => {
    for (const events of [
      [contact(1), pocket(8)],
      [contact(1), pocket(1), pocket(8)],
    ]) {
      expect(rule(events, { targets: [1] })).toMatchObject({ winner: 1, lastResult: 'loseEight', ballInHand: false })
    }
    expect(rule([contact(8), pocket(8), pocket(0)], { targets: [8] })).toMatchObject({ winner: 1, lastResult: 'loseEight' })
    expect(rule([contact(8), pocket(8)], { targets: [8] })).toMatchObject({ winner: 0, lastResult: 'winEight' })
  })
})

describe('eight-ball physical turns and persistence', () => {
  it('restores a pristine table and passes free placement to the opponent after a missed break', () => {
    const g = createEightBallGame()
    expect(restoreEightBallGame(g)).toEqual(g)
    expect(placeEightBallCue(g, 50, 50)).toBe(false)
    expect(shootEightBall(g, 0, 0.05)).toBe(true)
    expect(restoreEightBallGame(g)).toBeNull()
    expect(shootEightBall(g, 0, 1)).toBe(false)
    settle(g)
    expect(g).toMatchObject({ player: 1, ballInHand: true, lastResult: 'foulContact' })
    for (const [x, y] of [
      [5, 50],
      [400, 10],
      [NaN, 30],
      [g.balls[1].x, g.balls[1].y],
    ]) {
      expect(placeEightBallCue(g, x, y)).toBe(false)
    }
    expect(placeEightBallCue(g, 645, 100)).toBe(true)
    const restored = restoreEightBallGame(JSON.parse(JSON.stringify(g)))
    expect(restored).toEqual(g)
    expect(shootEightBall(restored, 0, 0.1)).toBe(true)
    expect(restored.ballInHand).toBe(false)
    expect(placeEightBallCue(restored, 100, 100)).toBe(false)
  })

  it('judges and restores a win when opposing balls are still on the table', () => {
    const g = createEightBallGame()
    g.shots = 3
    g.groups = ['solid', 'stripe']
    for (let id = 1; id <= 7; id++) g.balls[id].pocketed = true
    Object.assign(g.balls[0], { x: 400, y: 90 })
    Object.assign(g.balls[8], { x: 400, y: 45 })
    shootEightBall(g, -Math.PI / 2, 0.24)
    settle(g)
    expect(g).toMatchObject({ winner: 0, phase: 'won', lastResult: 'winEight' })
    expect(restoreEightBallGame(g)).toEqual(g)
    expect(shootEightBall(g, 0, 1)).toBe(false)
  })

  it('rejects inconsistent groups, winner, turn, opening setup or placement rights', () => {
    const g = createEightBallGame()
    for (const bad of [{ groups: ['solid', 'solid'] }, { winner: 1 }, { ballInHand: true }, { player: 2 }]) {
      expect(restoreEightBallGame({ ...g, ...bad })).toBeNull()
    }
    g.balls[0].x += 5
    expect(restoreEightBallGame(g)).toBeNull()
    Object.assign(g, { shots: 2, lastResult: 'foulRail', ballInHand: false })
    expect(restoreEightBallGame(g)).toBeNull()
    Object.assign(g, { lastResult: 'switch', phase: 'won', winner: 0 })
    expect(restoreEightBallGame(g)).toBeNull()
  })
})
