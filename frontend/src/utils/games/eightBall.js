import { canPlacePoolBall, createPoolTable, respotPoolBall, restoreSettledPoolTable, shootPool, stepPool } from './pool.js'

const FOUL_RESULTS = ['foulScratch', 'foulBreak', 'foulContact', 'foulRail']
const TURN_RESULTS = ['continue', 'switch', ...FOUL_RESULTS]

function groupOf(id) {
  if (id > 0 && id < 8) return 'solid'
  if (id > 8 && id <= 15) return 'stripe'
  return null
}

export function createEightBallGame() {
  return {
    ...createPoolTable(),
    // V1 saved tables and room packets include the number of cue-ball scratches.
    penalties: 0,
    player: 0,
    groups: [null, null],
    ballInHand: false,
    winner: null,
    lastResult: null,
    stroke: null,
  }
}

export function restoreEightBallGame(saved) {
  const table = restoreSettledPoolTable(saved)
  if (
    !table ||
    !Number.isSafeInteger(saved.penalties) ||
    saved.penalties < 0 ||
    saved.penalties > saved.shots ||
    ![0, 1].includes(saved.player) ||
    typeof saved.ballInHand !== 'boolean' ||
    !Array.isArray(saved.groups) ||
    saved.groups.length !== 2
  )
    return null
  const [a, b] = saved.groups
  if (!((a === null && b === null) || (a === 'solid' && b === 'stripe') || (a === 'stripe' && b === 'solid'))) return null
  if (table.shots === 1 && (a !== null || table.phase === 'won')) return null
  if (table.shots === 0) {
    const fresh = createEightBallGame()
    if (
      saved.player !== 0 ||
      a !== null ||
      saved.ballInHand ||
      saved.winner !== null ||
      saved.lastResult !== null ||
      table.phase !== 'ready' ||
      table.balls.some((ball, i) => ball.pocketed || ball.x !== fresh.balls[i].x || ball.y !== fresh.balls[i].y)
    )
      return null
    return fresh
  }
  if (table.phase === 'won') {
    if (
      !table.balls[8].pocketed ||
      saved.ballInHand ||
      !['winEight', 'loseEight'].includes(saved.lastResult) ||
      (saved.lastResult === 'winEight' && !legalTargets({ ...table, player: saved.player, groups: saved.groups }).includes(8)) ||
      saved.winner !== (saved.lastResult === 'winEight' ? saved.player : 1 - saved.player)
    )
      return null
  } else if (
    table.balls[8].pocketed ||
    saved.winner !== null ||
    !TURN_RESULTS.includes(saved.lastResult) ||
    FOUL_RESULTS.includes(saved.lastResult) !== saved.ballInHand
  )
    return null
  return {
    ...table,
    penalties: saved.penalties,
    player: saved.player,
    groups: [a, b],
    ballInHand: saved.ballInHand,
    winner: saved.winner,
    lastResult: saved.lastResult,
    stroke: null,
  }
}

export function canPlaceEightBallCue(game, x, y) {
  return game.phase === 'ready' && game.ballInHand && canPlacePoolBall(game.balls, 0, x, y)
}

export function placeEightBallCue(game, x, y) {
  if (!canPlaceEightBallCue(game, x, y)) return false
  Object.assign(game.balls[0], { x, y })
  return true
}

function legalTargets(game) {
  const group = game.groups[game.player]
  const colors = game.balls.filter((ball) => !ball.pocketed && groupOf(ball.id) && (!group || groupOf(ball.id) === group))
  return colors.length ? colors.map((ball) => ball.id) : [8]
}

export function shootEightBall(game, angle, power) {
  if (!shootPool(game, angle, power)) return false
  // Eligibility for the eight is fixed at the start of the stroke.
  game.stroke = { targets: legalTargets(game), events: [] }
  game.ballInHand = false
  return true
}

// Evaluate the ordered physics events from one stroke. A cushion touched
// before the cue first contacted an object ball cannot satisfy the rail rule.
export function ruleEightBallShot(game, targets, events) {
  const opening = game.shots === 1
  const firstContact = events.findIndex((e) => e.type === 'contact')
  const following = firstContact < 0 ? [] : events.slice(firstContact + 1)
  const rails = new Set(following.filter((e) => e.type === 'rail' && e.id !== 0).map((e) => e.id))
  const sunk = events.filter((e) => e.type === 'pocket').map((e) => e.id)
  const sunkEight = sunk.includes(8)
  let foul = null
  if (sunk.includes(0)) foul = 'foulScratch'
  else if (firstContact < 0 || (!opening && !targets.includes(events[firstContact].id))) foul = 'foulContact'
  else if (opening && !sunk.length && rails.size < 4) foul = 'foulBreak'
  else if (!opening && !following.some((e) => e.type === 'pocket' || e.type === 'rail')) foul = 'foulRail'

  const player = game.player
  const opponent = 1 - player
  const groups = [...game.groups]
  const end = !opening && sunkEight
  if (end) {
    const win = !foul && targets.includes(8)
    return { player, groups, ballInHand: false, lastResult: win ? 'winEight' : 'loseEight', winner: win ? player : opponent }
  }
  if (foul) return { player: opponent, groups, ballInHand: true, lastResult: foul, winner: null }
  const firstColor = sunk.find((id) => groupOf(id))
  if (!opening && groups[player] === null && firstColor !== undefined) {
    groups[player] = groupOf(firstColor)
    groups[opponent] = groups[player] === 'solid' ? 'stripe' : 'solid'
  }
  const keepsTurn = opening ? sunk.length > 0 : sunk.some((id) => groupOf(id) === groups[player])
  return { player: keepsTurn ? player : opponent, groups, ballInHand: false, lastResult: keepsTurn ? 'continue' : 'switch', winner: null }
}

export function stepEightBall(game, seconds) {
  if (game.phase !== 'rolling') return
  const { targets, events } = game.stroke
  stepPool(game, seconds, (type, id) => {
    events.push({ type, id })
    if (type === 'pocket' && id === 0) game.penalties++
  })
  if (game.phase === 'rolling') return
  if (game.shots === 1 && game.balls[8].pocketed) respotPoolBall(game.balls, 8)
  Object.assign(game, ruleEightBallShot(game, targets, events))
  game.phase = game.winner === null ? 'ready' : 'won'
  game.stroke = null
}
