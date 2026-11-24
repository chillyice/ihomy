// Coordinates describe the playing surface up to the cushions.
export const TABLE_WIDTH = 800
export const TABLE_HEIGHT = 400
export const BALL_RADIUS = 10
export const POCKET_RADIUS = 19
export const POCKETS = [0, TABLE_HEIGHT].flatMap((y) => [0, TABLE_WIDTH / 2, TABLE_WIDTH].map((x) => ({ x, y })))

const DIAMETER = BALL_RADIUS * 2
const FIXED_STEP = 1 / 240
const DECELERATION = 145
const MAX_SPEED = 1100
const BALL_RESTITUTION = 0.96
const CUSHION_RESTITUTION = 0.86
const HEAD_SPOT = { x: TABLE_WIDTH / 4, y: TABLE_HEIGHT / 2 }
const RACK_ORDER = [1, 10, 3, 5, 8, 14, 11, 6, 12, 4, 7, 9, 2, 15, 13]

function makeBall(id, x, y) {
  return { id, x, y, vx: 0, vy: 0, pocketed: false }
}

export function createPoolGame() {
  const balls = [makeBall(0, HEAD_SPOT.x, HEAD_SPOT.y)]
  const spacing = DIAMETER + 0.08
  for (let column = 0; column < 5; column++) {
    const x = TABLE_WIDTH * 0.7 + column * spacing * (Math.sqrt(3) / 2)
    for (let row = 0; row <= column; row++) {
      balls.push(makeBall(RACK_ORDER[balls.length - 1], x, HEAD_SPOT.y + (row - column / 2) * spacing))
    }
  }
  balls.sort((a, b) => a.id - b.id)
  return { balls, shots: 0, penalties: 0, phase: 'ready', remainder: 0 }
}

function overPocket(x, y) {
  return POCKETS.some((p) => (x - p.x) ** 2 + (y - p.y) ** 2 <= POCKET_RADIUS ** 2)
}

// Only a settled table can be resumed. Rebuild the balls so a stored object
// cannot inject velocities or fields into a new game.
export function restorePoolGame(saved) {
  if (
    !saved ||
    !['ready', 'won'].includes(saved.phase) ||
    !Number.isSafeInteger(saved.shots) ||
    saved.shots < 0 ||
    !Number.isSafeInteger(saved.penalties) ||
    saved.penalties < 0 ||
    saved.penalties > saved.shots ||
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
      (ball.x < BALL_RADIUS ||
        ball.x > TABLE_WIDTH - BALL_RADIUS ||
        ball.y < BALL_RADIUS ||
        ball.y > TABLE_HEIGHT - BALL_RADIUS ||
        overPocket(ball.x, ball.y) ||
        balls.some((other) => !other.pocketed && Math.hypot(ball.x - other.x, ball.y - other.y) < DIAMETER - 0.5))
    )
      return null
    balls[ball.id] = { ...makeBall(ball.id, ball.x, ball.y), pocketed: ball.pocketed }
  }
  if (balls[0].pocketed || balls.slice(1).every((b) => b.pocketed) !== (saved.phase === 'won')) return null
  return { balls, shots: saved.shots, penalties: saved.penalties, phase: saved.phase, remainder: 0 }
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

function collide(a, b) {
  const dx = b.x - a.x
  const dy = b.y - a.y
  const squared = dx * dx + dy * dy
  if (squared >= DIAMETER * DIAMETER) return
  const distance = Math.sqrt(squared)
  const nx = distance > 0 ? dx / distance : 1
  const ny = distance > 0 ? dy / distance : 0
  const correction = (DIAMETER - distance) / 2
  a.x -= correction * nx
  a.y -= correction * ny
  b.x += correction * nx
  b.y += correction * ny
  const approach = (a.vx - b.vx) * nx + (a.vy - b.vy) * ny
  if (approach <= 0) return
  const impulse = ((1 + BALL_RESTITUTION) * approach) / 2
  a.vx -= impulse * nx
  a.vy -= impulse * ny
  b.vx += impulse * nx
  b.vy += impulse * ny
}

function resolveTable(ball, game) {
  if (overPocket(ball.x, ball.y)) {
    ball.vx = 0
    ball.vy = 0
    ball.pocketed = true
    if (ball.id === 0) game.penalties++
    return
  }
  if (ball.x < BALL_RADIUS || ball.x > TABLE_WIDTH - BALL_RADIUS) {
    const side = ball.x < BALL_RADIUS ? 1 : -1
    ball.x = side > 0 ? BALL_RADIUS : TABLE_WIDTH - BALL_RADIUS
    if (ball.vx * side < 0) ball.vx *= -CUSHION_RESTITUTION
  }
  if (ball.y < BALL_RADIUS || ball.y > TABLE_HEIGHT - BALL_RADIUS) {
    const side = ball.y < BALL_RADIUS ? 1 : -1
    ball.y = side > 0 ? BALL_RADIUS : TABLE_HEIGHT - BALL_RADIUS
    if (ball.vy * side < 0) ball.vy *= -CUSHION_RESTITUTION
  }
}

function canPlaceCue(balls, x, y) {
  return !overPocket(x, y) && balls.every((ball) => ball.pocketed || Math.hypot(ball.x - x, ball.y - y) >= DIAMETER)
}

function respotCue(balls) {
  let spot = HEAD_SPOT
  if (!canPlaceCue(balls, spot.x, spot.y)) {
    // Choose the closest unoccupied grid point in the kitchen.
    let best = Infinity
    for (let x = BALL_RADIUS; x <= HEAD_SPOT.x; x += DIAMETER) {
      for (let y = BALL_RADIUS; y <= TABLE_HEIGHT - BALL_RADIUS; y += DIAMETER) {
        const distance = Math.hypot(x - HEAD_SPOT.x, y - HEAD_SPOT.y)
        if (distance < best && canPlaceCue(balls, x, y)) {
          spot = { x, y }
          best = distance
        }
      }
    }
  }
  Object.assign(balls[0], makeBall(0, spot.x, spot.y))
}

function tick(game) {
  const { balls } = game
  for (const ball of balls) {
    if (ball.pocketed) continue
    ball.x += ball.vx * FIXED_STEP
    ball.y += ball.vy * FIXED_STEP
    resolveTable(ball, game)
  }
  for (let i = 0; i < balls.length; i++) {
    if (balls[i].pocketed) continue
    for (let j = i + 1; j < balls.length; j++) {
      if (!balls[j].pocketed) collide(balls[i], balls[j])
    }
  }
  let moving = false
  for (const ball of balls) {
    if (ball.pocketed) continue
    // Contact correction can push a resting ball against a cushion.
    resolveTable(ball, game)
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
    if (balls[0].pocketed) respotCue(balls)
    game.phase = balls.slice(1).every((ball) => ball.pocketed) ? 'won' : 'ready'
    game.remainder = 0
  }
}

export function stepPool(game, elapsedSeconds) {
  if (game.phase !== 'rolling' || !Number.isFinite(elapsedSeconds) || elapsedSeconds <= 0) return
  // A background tab should resume play without simulating minutes of elapsed time.
  game.remainder += Math.min(elapsedSeconds, 0.1)
  while (game.remainder + 1e-12 >= FIXED_STEP && game.phase === 'rolling') {
    game.remainder = Math.max(0, game.remainder - FIXED_STEP)
    tick(game)
  }
}
