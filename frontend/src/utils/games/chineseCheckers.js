// Axial coordinates on a hex grid. The central 61 holes have radius four;
// each of the six arms adds a ten-hole triangle.
export const CHECKER_HOLES = []
const holeAt = new Map()
for (let r = -8; r <= 8; r++) {
  for (let q = -8; q <= 8; q++) {
    const s = -q - r
    if ([q, r, s].filter((axis) => Math.abs(axis) > 4).length > 1) continue
    const id = CHECKER_HOLES.length
    CHECKER_HOLES.push({ id, q, r, x: q + r / 2, y: (r * Math.sqrt(3)) / 2, camp: r < -4 ? 0 : r > 4 ? 1 : null })
    holeAt.set(q + ',' + r, id)
  }
}

const directions = [
  [1, 0],
  [0, 1],
  [-1, 1],
  [-1, 0],
  [0, -1],
  [1, -1],
]
const links = CHECKER_HOLES.map(({ q, r }) =>
  directions.map(([dq, dr]) => ({
    near: holeAt.get(q + dq + ',' + (r + dr)),
    far: holeAt.get(q + 2 * dq + ',' + (r + 2 * dr)),
  })),
)
const camps = [0, 1].map((player) => CHECKER_HOLES.filter((h) => h.camp === player).map((h) => h.id))
const distances = CHECKER_HOLES.map((a) =>
  CHECKER_HOLES.map((b) => Math.max(Math.abs(a.q - b.q), Math.abs(a.r - b.r), Math.abs(a.q + a.r - b.q - b.r))),
)

export function createCheckersGame() {
  return { board: CHECKER_HOLES.map((hole) => hole.camp), player: 1, winner: null, turns: 0, lastMove: null }
}

function isHole(id) {
  return Number.isInteger(id) && id >= 0 && id < CHECKER_HOLES.length
}

function nextSteps(board, origin, current, jumpsOnly) {
  const steps = []
  for (const { near, far } of links[current]) {
    if (near === undefined) continue
    if (board[near] === null || near === origin) {
      if (!jumpsOnly && near !== origin) steps.push({ to: near, jump: false })
    } else if (far !== undefined && far !== origin && board[far] === null) {
      steps.push({ to: far, jump: true })
    }
  }
  return steps
}

// The starting hole is vacated after takeoff. Other marbles remain in place
// throughout the chain. Revisiting a landing adds no new reachable endpoint.
function inspectPath(game, path) {
  if (game.winner !== null || !Array.isArray(path) || !path.length || path.length > CHECKER_HOLES.length) return null
  const from = path[0]
  if (!isHole(from) || game.board[from] !== game.player) return null
  const seen = new Set([from])
  let jump = false
  for (let i = 1; i < path.length; i++) {
    const to = path[i]
    if (seen.has(to) || (i > 1 && !jump)) return null
    const step = nextSteps(game.board, from, path[i - 1], i > 1).find((s) => s.to === to)
    if (!step) return null
    jump = step.jump
    seen.add(to)
  }
  return { seen, jump }
}

export function checkersNextSteps(game, path) {
  const chain = inspectPath(game, path)
  if (!chain || (path.length > 1 && !chain.jump)) return []
  return nextSteps(game.board, path[0], path.at(-1), path.length > 1).filter((step) => !chain.seen.has(step.to))
}

// A breadth-first search picks a shortest demonstration path to each possible
// landing. Players may submit a different legal path to the same hole.
export function checkersMoves(game, from) {
  if (game.winner !== null || !isHole(from) || game.board[from] !== game.player) return []
  const moves = []
  const queue = [[from]]
  const reached = new Set([from])
  for (let i = 0; i < queue.length; i++) {
    const path = queue[i]
    for (const { to, jump } of nextSteps(game.board, from, path.at(-1), i > 0)) {
      if (reached.has(to)) continue
      reached.add(to)
      const extended = [...path, to]
      moves.push({ from, to, path: extended })
      if (jump) queue.push(extended)
    }
  }
  return moves
}

function finished(board, player) {
  return camps[1 - player].every((id) => board[id] === player)
}

export function playCheckersMove(game, from, to, path) {
  if (path === undefined) path = checkersMoves(game, from).find((move) => move.to === to)?.path
  if (!Array.isArray(path) || path.length < 2 || path[0] !== from || path.at(-1) !== to || !inspectPath(game, path)) return false
  game.board[from] = null
  game.board[to] = game.player
  game.turns++
  game.lastMove = { from, to, path: [...path] }
  if (finished(game.board, game.player)) game.winner = game.player
  else game.player = 1 - game.player
  return true
}

export function restoreCheckersGame(saved) {
  if (
    !saved ||
    !Array.isArray(saved.board) ||
    saved.board.length !== CHECKER_HOLES.length ||
    !Number.isSafeInteger(saved.turns) ||
    saved.turns < 0 ||
    ![0, 1].includes(saved.player) ||
    ![null, 0, 1].includes(saved.winner)
  )
    return null
  const board = [...saved.board]
  if (board.some((value) => ![null, 0, 1].includes(value)) || [0, 1].some((p) => board.filter((v) => v === p).length !== 10)) return null
  if (saved.turns === 0) {
    const fresh = createCheckersGame()
    return saved.lastMove === null && saved.winner === null && saved.player === fresh.player && fresh.board.every((v, i) => board[i] === v)
      ? fresh
      : null
  }
  const mover = saved.turns % 2
  const winner = finished(board, mover) ? mover : null
  if (finished(board, 1 - mover) || saved.winner !== winner || saved.player !== (winner ?? 1 - mover)) return null
  const move = saved.lastMove
  if (
    !move ||
    !isHole(move.from) ||
    !isHole(move.to) ||
    board[move.from] !== null ||
    board[move.to] !== mover ||
    !Array.isArray(move.path) ||
    move.path.length < 2 ||
    move.path[0] !== move.from ||
    move.path.at(-1) !== move.to
  )
    return null
  const before = [...board]
  before[move.from] = mover
  before[move.to] = null
  if (finished(before, mover) || !inspectPath({ board: before, player: mover, winner: null }, move.path)) return null
  return { board, player: saved.player, winner, turns: saved.turns, lastMove: { from: move.from, to: move.to, path: [...move.path] } }
}

function allMoves(game) {
  return game.board.flatMap((owner, id) => (owner === game.player ? checkersMoves(game, id) : []))
}

// Price the distance of each remaining marble to an unfinished target and
// each target to an available marble. Squared distance favors bringing up
// stragglers and discourages abandoning the deep holes behind the front row.
function positionScore(board, player) {
  const targets = camps[1 - player].filter((id) => board[id] !== player)
  if (!targets.length) return 100000
  const nearest = targets.map(() => Infinity)
  let cost = 0
  for (let id = 0; id < board.length; id++) {
    if (board[id] !== player || CHECKER_HOLES[id].camp === 1 - player) continue
    let best = Infinity
    for (let j = 0; j < targets.length; j++) {
      const d = distances[id][targets[j]]
      nearest[j] = Math.min(nearest[j], d)
      best = Math.min(best, d)
    }
    cost += best * (best + 6)
  }
  for (let j = 0; j < targets.length; j++) cost += nearest[j] * 2 + Math.abs(CHECKER_HOLES[targets[j]].r)
  return -cost
}

function scoreMove(game, move) {
  game.board[move.from] = null
  game.board[move.to] = game.player
  const score = positionScore(game.board, game.player)
  game.board[move.to] = null
  game.board[move.from] = game.player
  return score
}

export function chooseCheckersMove(state) {
  // Search on a private board so Vue and stored snapshots only observe real moves.
  const game = { ...state, board: [...state.board] }
  const moves = allMoves(game).map((move) => ({ move, score: scoreMove(game, move) }))
  if (!moves.length) return null
  moves.sort((a, b) => b.score - a.score)
  if (moves[0].score === 100000) return moves[0].move
  let bestMove = moves[0].move
  let best = -Infinity
  for (const { move, score } of moves.slice(0, 6)) {
    game.board[move.from] = null
    game.board[move.to] = game.player
    let followup = -Infinity
    for (const next of allMoves(game)) followup = Math.max(followup, scoreMove(game, next))
    game.board[move.to] = null
    game.board[move.from] = game.player
    const plan = score + 0.75 * followup
    if (plan > best) {
      best = plan
      bestMove = move
    }
  }
  return bestMove
}
