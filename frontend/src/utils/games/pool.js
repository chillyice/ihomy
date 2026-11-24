import {
  TABLE_WIDTH,
  TABLE_HEIGHT,
  BALL_RADIUS,
  cuePlacementOnCloth,
  supportedPoolPosition,
  tracePoolBoundary,
  applyPoolBoundaryHit,
  resolvePoolBoundaryOverlap,
} from './poolGeometry.js'
export { TABLE_WIDTH, TABLE_HEIGHT, BALL_RADIUS, CUSHIONS, POCKETS, tracePoolBoundary } from './poolGeometry.js'

const DIAMETER = BALL_RADIUS * 2
const FIXED_STEP = 1 / 240
const DECELERATION = 145
const MAX_SPEED = 1100
const BALL_RESTITUTION = 0.96
const HEAD_SPOT = { x: TABLE_WIDTH / 4, y: TABLE_HEIGHT / 2 }
const RACK_ORDER = [1, 10, 3, 5, 8, 14, 11, 6, 12, 4, 7, 9, 2, 15, 13]

function makeBall(id, x, y) {
  return { id, x, y, vx: 0, vy: 0, pocketed: false }
}

export function createPoolTable() {
  const balls = [makeBall(0, HEAD_SPOT.x, HEAD_SPOT.y)]
  const spacing = DIAMETER + 0.08
  for (let column = 0; column < 5; column++) {
    const x = TABLE_WIDTH * 0.7 + column * spacing * (Math.sqrt(3) / 2)
    for (let row = 0; row <= column; row++) {
      balls.push(makeBall(RACK_ORDER[balls.length - 1], x, HEAD_SPOT.y + (row - column / 2) * spacing))
    }
  }
  balls.sort((a, b) => a.id - b.id)
  return { balls, shots: 0, phase: 'ready', remainder: 0 }
}

// Only a settled table can be resumed. Rebuild the balls so a stored object
// cannot inject velocities or fields into a new game.
export function restoreSettledPoolTable(saved) {
  if (
    !saved ||
    !['ready', 'won'].includes(saved.phase) ||
    !Number.isSafeInteger(saved.shots) ||
    saved.shots < 0 ||
    !Array.isArray(saved.balls) ||
    saved.balls.length !== 16
  )
    return null

  const balls = []
  for (const ball of saved.balls) {
    if (
      !ball ||
      !Number.isInteger(ball.id) ||
      ball.id < 0 ||
      ball.id > 15 ||
      balls[ball.id] ||
      typeof ball.pocketed !== 'boolean' ||
      !Number.isFinite(ball.x) ||
      !Number.isFinite(ball.y) ||
      ball.vx !== 0 ||
      ball.vy !== 0
    )
      return null
    if (
      !ball.pocketed &&
      (!supportedPoolPosition(ball.x, ball.y) ||
        balls.some((other) => !other.pocketed && Math.hypot(ball.x - other.x, ball.y - other.y) < DIAMETER - 0.5))
    )
      return null
    balls[ball.id] = { ...makeBall(ball.id, ball.x, ball.y), pocketed: ball.pocketed }
  }
  if (balls[0].pocketed) return null
  return { balls, shots: saved.shots, phase: saved.phase, remainder: 0 }
}

export function shootPool(game, angle, power) {
  if (game.phase !== 'ready' || !Number.isFinite(angle) || !Number.isFinite(power) || power <= 0) return false
  const speed = MAX_SPEED * Math.min(power, 1)
  game.balls[0].vx = Math.cos(angle) * speed
  game.balls[0].vy = Math.sin(angle) * speed
  game.shots++
  game.phase = 'rolling'
  return true
}

// Solve |(b - a) - (va - vb)t| = diameter for the first approaching contact.
// The rationalized root stays accurate when two balls are nearly touching.
function timeToContact(a, b, remaining) {
  const vx = a.vx - b.vx
  const vy = a.vy - b.vy
  const dx = b.x - a.x
  const dy = b.y - a.y
  const approach = dx * vx + dy * vy
  if (approach <= 1e-8) return Infinity
  const gap = dx * dx + dy * dy - DIAMETER * DIAMETER
  if (gap <= 0) return 0
  const discriminant = approach * approach - (vx * vx + vy * vy) * gap
  if (discriminant <= 0) return Infinity
  const time = gap / (approach + Math.sqrt(discriminant))
  return time <= remaining ? time : Infinity
}

function collide(a, b, onEvent) {
  const dx = b.x - a.x
  const dy = b.y - a.y
  const squared = dx * dx + dy * dy
  const distance = Math.sqrt(squared)
  const nx = distance > 0 ? dx / distance : 1
  const ny = distance > 0 ? dy / distance : 0
  const correction = Math.max(0, DIAMETER - distance) / 2
  a.x -= correction * nx
  a.y -= correction * ny
  b.x += correction * nx
  b.y += correction * ny
  const approach = (a.vx - b.vx) * nx + (a.vy - b.vy) * ny
  if (approach <= 0) return
  if (a.id === 0) onEvent?.('contact', b.id)
  const impulse = ((1 + BALL_RESTITUTION) * approach) / 2
  a.vx -= impulse * nx
  a.vy -= impulse * ny
  b.vx += impulse * nx
  b.vy += impulse * ny
}

export function canPlacePoolBall(balls, id, x, y) {
  return (
    cuePlacementOnCloth(x, y) && balls.every((ball) => ball.id === id || ball.pocketed || Math.hypot(ball.x - x, ball.y - y) >= DIAMETER)
  )
}

export function respotPoolBall(balls, id) {
  const preferred = id === 0 ? HEAD_SPOT : { x: TABLE_WIDTH * 0.7, y: TABLE_HEIGHT / 2 }
  let spot = preferred
  if (!canPlacePoolBall(balls, id, spot.x, spot.y)) {
    // Choose the closest unoccupied grid point to the normal spot.
    let best = Infinity
    const maxX = id === 0 ? HEAD_SPOT.x : TABLE_WIDTH - BALL_RADIUS
    for (let x = BALL_RADIUS; x <= maxX; x += DIAMETER) {
      for (let y = BALL_RADIUS; y <= TABLE_HEIGHT - BALL_RADIUS; y += DIAMETER) {
        const distance = Math.hypot(x - preferred.x, y - preferred.y)
        if (distance < best && canPlacePoolBall(balls, id, x, y)) {
          spot = { x, y }
          best = distance
        }
      }
    }
  }
  Object.assign(balls[id], makeBall(id, spot.x, spot.y))
}

function advanceToContacts(game, onEvent) {
  const { balls } = game
  let remaining = FIXED_STEP
  // A dense cluster may produce several contacts at the same instant. Recompute
  // after each impulse, since both ball and cushion predictions can change.
  for (let impacts = 0; remaining > 1e-12 && impacts < 128; impacts++) {
    let first = Infinity
    let ball = null
    let other = null
    let boundary = null
    for (let i = 0; i < balls.length; i++) {
      const a = balls[i]
      if (a.pocketed) continue
      for (let j = i + 1; j < balls.length; j++) {
        const b = balls[j]
        if (b.pocketed) continue
        const time = timeToContact(a, b, Math.min(first, remaining))
        if (time < first) {
          first = time
          ball = a
          other = b
          boundary = null
        }
      }
      if (!a.vx && !a.vy) continue
      const limit = Math.min(first, remaining)
      const speed = Math.hypot(a.vx, a.vy)
      const hit = tracePoolBoundary(a.x, a.y, a.vx / speed, a.vy / speed, speed * limit)
      if (hit && hit.distance / speed < first) {
        first = hit.distance / speed
        ball = a
        other = null
        boundary = hit
      }
    }
    const elapsed = Math.min(first, remaining)
    if (elapsed > 0) {
      for (const b of balls) {
        if (!b.pocketed) {
          b.x += b.vx * elapsed
          b.y += b.vy * elapsed
        }
      }
      remaining -= elapsed
    }
    if (!ball) break
    if (boundary) {
      applyPoolBoundaryHit(ball, boundary, onEvent)
    } else {
      collide(ball, other, onEvent)
      resolvePoolBoundaryOverlap(ball, onEvent)
      resolvePoolBoundaryOverlap(other, onEvent)
    }
  }
  // At the contact cap leave the balls at their last resolved positions. Rolling
  // deceleration still applies this tick, so a trapped cluster can settle.
}

function tick(game, onEvent) {
  const { balls } = game
  advanceToContacts(game, onEvent)
  let moving = false
  for (const ball of balls) {
    if (ball.pocketed) continue
    const speed = Math.hypot(ball.vx, ball.vy)
    if (speed <= DECELERATION * FIXED_STEP) {
      ball.vx = 0
      ball.vy = 0
    } else {
      const factor = 1 - (DECELERATION * FIXED_STEP) / speed
      ball.vx *= factor
      ball.vy *= factor
      moving = true
    }
  }
  if (!moving) {
    if (balls[0].pocketed) respotPoolBall(balls, 0)
    game.phase = 'ready'
    game.remainder = 0
  }
}

export function stepPool(game, elapsedSeconds, onEvent) {
  if (game.phase !== 'rolling' || !Number.isFinite(elapsedSeconds) || elapsedSeconds <= 0) return
  // A background tab should resume play without simulating minutes of elapsed time.
  game.remainder += Math.min(elapsedSeconds, 0.1)
  while (game.remainder + 1e-12 >= FIXED_STEP && game.phase === 'rolling') {
    game.remainder = Math.max(0, game.remainder - FIXED_STEP)
    tick(game, onEvent)
  }
}
