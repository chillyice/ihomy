import { describe, expect, it } from 'vitest'
import { createPoolGame, shootPool, stepPool } from './pool'

const TICK = 1 / 240

function table(...positions) {
  const game = createPoolGame()
  game.balls.forEach((ball, i) => {
    if (i < positions.length) [ball.x, ball.y] = positions[i]
    else ball.pocketed = true
  })
  return game
}

describe('continuous pool contacts', () => {
  it.each([30, 45, 60, 82])('sends a full-power %i-degree cut along its contact normal at every tick offset', (degrees) => {
    const offset = 20 * Math.sin((degrees * Math.PI) / 180)
    for (let distance = 40; distance <= 50; distance += 0.125) {
      const game = table([200, 200], [200 + distance, 200 + offset])
      shootPool(game, 0, 1)
      const contacts = []
      for (let i = 0; i < 16 && !contacts.length; i++) {
        stepPool(game, TICK, (event, id) => {
          if (event === 'contact') contacts.push(id)
        })
      }
      expect(contacts).toEqual([1])
      const ball = game.balls[1]
      expect((Math.atan2(ball.vy, ball.vx) * 180) / Math.PI).toBeCloseTo(degrees, 6)
    }
  })

  it('resolves two cue contacts in time order when the farther ball has the lower ID', () => {
    const game = table([200, 200], [218, 212], [216.9, 188])
    shootPool(game, 0, 1)
    const contacts = []
    stepPool(game, TICK, (type, id) => {
      if (type === 'contact') contacts.push(id)
    })
    expect(contacts).toEqual([2, 1])
  })

  it('finds the contact on the reflected path within the same tick as a cushion impact', () => {
    const game = table([200, 11], [200, 31.3])
    shootPool(game, -Math.PI / 2, 1)
    const events = []
    stepPool(game, TICK, (type, id) => events.push([type, id]))
    expect(events).toEqual([
      ['rail', 0],
      ['contact', 1],
    ])
    expect(game.balls[1].vy).toBeGreaterThan(0)
  })
})
