import { describe, expect, it } from 'vitest'
import {
  BALL_RADIUS,
  POCKETS,
  TABLE_HEIGHT,
  TABLE_WIDTH,
  createPoolTable,
  restoreSettledPoolTable,
  shootPool,
  stepPool,
  tracePoolBoundary,
} from './pool.js'

function settle(game, frame = 1 / 60) {
  for (let elapsed = 0; elapsed < 15 && game.phase === 'rolling'; elapsed += frame) {
    stepPool(game, frame)
  }
  expect(game.phase).not.toBe('rolling')
}

describe('pool', () => {
  function isolatedObject(x, y, vx, vy) {
    const game = createPoolTable()
    game.balls.slice(2).forEach((ball) => {
      ball.pocketed = true
    })
    Object.assign(game.balls[1], { x, y, vx, vy })
    game.shots = 1
    game.phase = 'rolling'
    return game
  }

  it('lets a shot travel through the mouth of each pocket before falling across the throat', () => {
    for (const { outward, throat } of POCKETS) {
      const tx = (throat[0].x + throat[1].x) / 2
      const ty = (throat[0].y + throat[1].y) / 2
      const x = tx - outward.x * 36
      const y = ty - outward.y * 36
      const hit = tracePoolBoundary(x, y, outward.x, outward.y)
      expect(hit.type).toBe('pocket')
      expect(hit.distance).toBeCloseTo(36)
      const g = isolatedObject(x, y, outward.x * 180, outward.y * 180)
      stepPool(g, 0.1)
      expect(g.balls[1].pocketed).toBe(false)
      expect(restoreSettledPoolTable(g)).toBeNull()
      settle(g)
      expect(g.balls[1].pocketed).toBe(true)
      expect(g.phase).toBe('ready')
    }
  })

  it('sinks cushion-hugging shots on either side of all four corner mouths at full speed', () => {
    for (const sx of [1, -1])
      for (const sy of [1, -1]) {
        const ox = sx > 0 ? 0 : TABLE_WIDTH
        const oy = sy > 0 ? 0 : TABLE_HEIGHT
        for (const [x, y, vx, vy] of [
          [85, 10, -1100, 0],
          [10, 85, 0, -1100],
        ]) {
          const g = isolatedObject(ox + sx * x, oy + sy * y, sx * vx, sy * vy)
          const events = []
          stepPool(g, 0.1, (type, id) => events.push([type, id]))
          expect(events).toEqual([['pocket', 1]])
        }
      }
  })

  it('bounces off a rounded nose and a bevel using their different contact normals', () => {
    for (const [x, expectedNX] of [
      [377, 0.3],
      [384, 13 / Math.hypot(7, 13)],
    ]) {
      const hit = tracePoolBoundary(x, 30, 0, -1)
      expect(hit.type).toBe('rail')
      expect(hit.nx).toBeCloseTo(expectedNX)
      const g = isolatedObject(x, 30, 0, -400)
      const events = []
      for (let i = 0; i < 24 && !events.length; i++) stepPool(g, 1 / 240, (type, id) => events.push([type, id]))
      expect(events).toEqual([['rail', 1]])
      expect(g.balls[1].vx).toBeGreaterThan(0)
      expect(g.balls[1].vx * hit.nx + g.balls[1].vy * hit.ny).toBeGreaterThan(0)
    }
  })

  it('restores a ball hanging in the pocket jaws and resumes it on the next shot', () => {
    const g = isolatedObject(400, 0, 0, -50)
    settle(g)
    expect(g.balls[1].y).toBeLessThan(0)
    expect(g.balls[1].y).toBeGreaterThan(-13)
    const restored = restoreSettledPoolTable(g)
    expect(restored).toEqual(g)
    Object.assign(restored.balls[0], { x: 400, y: 30 })
    shootPool(restored, -Math.PI / 2, 0.15)
    settle(restored)
    expect(restored.balls[1].pocketed).toBe(true)
    Object.assign(g.balls[1], { x: 13, y: 14 }) // Valid pre-jaw save near the corner.
    expect(restoreSettledPoolTable(g)).toEqual(g)
    for (const [x, y] of [
      [400, -14],
      [370, -4],
      [2, 50],
      [-80, -80],
    ]) {
      Object.assign(g.balls[1], { x, y })
      expect(restoreSettledPoolTable(g)).toBeNull()
    }
  })

  it('reports a bank before first contact separately from cushions reached after contact', () => {
    const game = createPoolTable()
    // Bank off the left rail, then hit a ball frozen near the right rail.
    game.balls.slice(2).forEach((ball) => {
      ball.pocketed = true
    })
    Object.assign(game.balls[0], { x: 120, y: 180 })
    Object.assign(game.balls[1], { x: 745, y: 180 })
    shootPool(game, Math.PI, 1)
    const events = []
    for (let i = 0; i < 300 && game.phase === 'rolling'; i++) {
      stepPool(game, 1 / 30, (type, id) => events.push([type, id]))
    }
    expect(events.slice(0, 3)).toEqual([
      ['rail', 0],
      ['contact', 1],
      ['rail', 1],
    ])
  })

  it('starts with an isolated cue ball and a triangular rack of fifteen numbered balls', () => {
    const game = createPoolTable()
    expect(game.balls.map((b) => b.id)).toEqual(Array.from({ length: 16 }, (_, id) => id))
    for (const [i, a] of game.balls.entries()) {
      for (const b of game.balls.slice(i + 1)) {
        expect(Math.hypot(a.x - b.x, a.y - b.y)).toBeGreaterThanOrEqual(2 * BALL_RADIUS)
      }
    }
    expect(restoreSettledPoolTable(game)).toEqual(game)
  })

  it('accepts one finite, positive stroke while the table is ready', () => {
    const game = createPoolTable()
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
    expect(game.balls[0].x).toBeGreaterThan(200)
    expect(game.balls[0].vx).toBe(0)
  })

  it('transfers velocity on impact and gives the same break at 30 and 120 fps', () => {
    const slow = createPoolTable()
    shootPool(slow, 0, 1)
    const fast = structuredClone(slow)
    for (let frame = 0; frame < 60; frame++) stepPool(slow, 1 / 30)
    for (let frame = 0; frame < 240; frame++) stepPool(fast, 1 / 120)
    expect(fast.balls).toEqual(slow.balls)
    expect(slow.balls.slice(1).some((b) => b.pocketed || Math.hypot(b.vx, b.vy) > 20)).toBe(true)
    settle(slow, 1 / 30)
    expect(restoreSettledPoolTable(slow)).toEqual(slow)
  })

  it('reflects a ball from a cushion between pockets', () => {
    const game = createPoolTable()
    Object.assign(game.balls[0], { x: 25, y: 120 })
    shootPool(game, Math.PI, 0.3)
    stepPool(game, 0.1)
    expect(game.balls[0].vx).toBeGreaterThan(0)
    expect(game.balls[0].x).toBeGreaterThanOrEqual(BALL_RADIUS)
  })

  it('reports the cue ball falling once and respots clear of an occupied head spot', () => {
    const game = createPoolTable()
    Object.assign(game.balls[1], { x: game.balls[0].x, y: game.balls[0].y })
    Object.assign(game.balls[0], { x: TABLE_WIDTH / 2, y: 45 })
    shootPool(game, -Math.PI / 2, 0.25)
    const pockets = []
    for (let frame = 0; frame < 600 && game.phase === 'rolling'; frame++) {
      stepPool(game, 1 / 60, (event, id) => {
        if (event === 'pocket') pockets.push(id)
      })
    }
    expect(pockets).toEqual([0])
    expect(game.shots).toBe(1)
    expect(game.balls[0].pocketed).toBe(false)
    expect(restoreSettledPoolTable(game)).toEqual(game)
  })

  it('rejects a damaged, moving, overlapping, or inconsistent saved table', () => {
    expect(restoreSettledPoolTable(null)).toBeNull()
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
        g.shots = -1
      },
      (g) => {
        g.phase = 'rolling'
      },
      (g) => {
        g.phase = 'invalid'
      },
    ]) {
      const game = createPoolTable()
      damage(game)
      expect(restoreSettledPoolTable(game)).toBeNull()
    }
  })
})
