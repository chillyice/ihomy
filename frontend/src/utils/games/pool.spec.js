import { describe, expect, it } from 'vitest'
import { BALL_RADIUS, TABLE_HEIGHT, TABLE_WIDTH, createPoolGame, restorePoolGame, shootPool, stepPool } from './pool.js'

function settle(game, frame = 1 / 60) {
  for (let elapsed = 0; elapsed < 15 && game.phase === 'rolling'; elapsed += frame) {
    stepPool(game, frame)
  }
  expect(game.phase).not.toBe('rolling')
}

describe('pool', () => {
  it('starts with an isolated cue ball and a triangular rack of fifteen numbered balls', () => {
    const game = createPoolGame()
    expect(game.balls.map((b) => b.id)).toEqual(Array.from({ length: 16 }, (_, id) => id))
    for (const [i, a] of game.balls.entries()) {
      for (const b of game.balls.slice(i + 1)) {
        expect(Math.hypot(a.x - b.x, a.y - b.y)).toBeGreaterThanOrEqual(2 * BALL_RADIUS)
      }
    }
    expect(restorePoolGame(game)).toEqual(game)
  })

  it('accepts one finite, positive stroke while the table is ready', () => {
    const game = createPoolGame()
    for (const [angle, power] of [
      [NaN, 1],
      [0, Infinity],
      [0, 0],
      [0, -1],
    ]) {
      expect(shootPool(game, angle, power)).toBe(false)
    }
    expect(shootPool(game, 0, 0.1)).toBe(true)
    expect(shootPool(game, 0, 1)).toBe(false)
    stepPool(game, NaN)
    expect(game.balls[0].x).toBe(200)
    settle(game)
    expect(game.shots).toBe(1)
    expect(game.penalties).toBe(0)
    expect(game.balls[0].x).toBeGreaterThan(200)
    expect(game.balls[0].vx).toBe(0)
  })

  it('transfers velocity on impact and gives the same break at 30 and 120 fps', () => {
    const slow = createPoolGame()
    shootPool(slow, 0, 1)
    const fast = structuredClone(slow)
    for (let frame = 0; frame < 60; frame++) stepPool(slow, 1 / 30)
    for (let frame = 0; frame < 240; frame++) stepPool(fast, 1 / 120)
    expect(fast.balls).toEqual(slow.balls)
    expect(slow.balls.slice(1).some((b) => b.pocketed || Math.hypot(b.vx, b.vy) > 20)).toBe(true)
    settle(slow, 1 / 30)
    expect(restorePoolGame(slow)).toEqual(slow)
  })

  it('reflects a ball from a cushion between pockets', () => {
    const game = createPoolGame()
    Object.assign(game.balls[0], { x: 25, y: 120 })
    shootPool(game, Math.PI, 0.3)
    stepPool(game, 0.1)
    expect(game.balls[0].vx).toBeGreaterThan(0)
    expect(game.balls[0].x).toBeGreaterThanOrEqual(BALL_RADIUS)
  })

  it('counts a scratch once and respots clear of an occupied head spot', () => {
    const game = createPoolGame()
    Object.assign(game.balls[1], { x: game.balls[0].x, y: game.balls[0].y })
    Object.assign(game.balls[0], { x: TABLE_WIDTH / 2, y: 45 })
    shootPool(game, -Math.PI / 2, 0.25)
    settle(game)
    expect(game.penalties).toBe(1)
    expect(game.shots).toBe(1)
    expect(game.balls[0].pocketed).toBe(false)
    expect(restorePoolGame(game)).toEqual(game)
  })

  it('keeps object balls in pockets and finishes when the last ball drops', () => {
    const game = createPoolGame()
    game.balls.slice(2).forEach((b) => {
      b.pocketed = true
    })
    Object.assign(game.balls[0], { x: TABLE_WIDTH / 2, y: TABLE_HEIGHT - 90 })
    Object.assign(game.balls[1], { x: TABLE_WIDTH / 2, y: TABLE_HEIGHT - 45 })
    shootPool(game, Math.PI / 2, 0.2)
    settle(game)
    expect(game.phase).toBe('won')
    expect(game.penalties).toBe(0)
    expect(game.balls[1].pocketed).toBe(true)
    expect(shootPool(game, 0, 1)).toBe(false)
    expect(restorePoolGame(game)).toEqual(game)
  })

  it('rejects a damaged, moving, overlapping, or inconsistent saved table', () => {
    expect(restorePoolGame(null)).toBeNull()
    for (const damage of [
      (g) => {
        g.balls.pop()
      },
      (g) => {
        g.balls[1].id = 0
      },
      (g) => {
        g.balls[1].x = Infinity
      },
      (g) => {
        g.balls[1].y = -5
      },
      (g) => {
        g.balls[1].vx = 1
      },
      (g) => {
        Object.assign(g.balls[1], { x: g.balls[0].x, y: g.balls[0].y })
      },
      (g) => {
        g.balls[0].pocketed = true
      },
      (g) => {
        g.penalties = g.shots + 1
      },
      (g) => {
        g.phase = 'rolling'
      },
      (g) => {
        g.phase = 'won'
      },
    ]) {
      const game = createPoolGame()
      damage(game)
      expect(restorePoolGame(game)).toBeNull()
    }
  })
})
