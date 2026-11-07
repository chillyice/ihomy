// 贪吃蛇 1v1 对战模拟(纯函数):一张盘面两条蛇、共享食物(先到先得,同 tick host 优先)、
// 尸体留场成障碍;撞墙/撞自己/撞对方(含头对头双亡)该方跑结束,双方都结束后对局终局。
// 胜负由比分决定(与后端 finish 结算口径一致:分高者胜,平分无胜者)。
// 确定性:同 seed + 同输入序列 → 双端状态一致;输入经 WS game-signal 转发。
import { DIRECTIONS, randomFood, turn as turnSnake, STATUS } from './snake'

export const HOST = 'host'
export const GUEST = 'guest'
export { STATUS }

const sameCell = (a, b) => !!a && !!b && a.x === b.x && a.y === b.y

/** 新对局:两蛇平行错行起跑,共享同一食物与同一条 rng 链(状态随对局保存,双端可重放) */
export function createMatch(seed, cols = 20, rows = 20) {
  const rng = makeRng(Number(seed) >>> 0)
  const host = createSide(cols, rows, 0)
  const guest = createSide(cols, rows, 1)
  // 首只共享食物:避开两条蛇的全部身体
  const food = randomFood([...host.snake, ...guest.snake], cols, rows, rng)
  if (food) {
    host.food = { ...food }
    guest.food = { ...food }
  }
  return { seed: Number(seed) >>> 0, cols, rows, host, guest, rngState: rng.getState(), over: false, winner: null }
}

function createSide(cols, rows, rowOffset) {
  const cx = Math.floor(cols / 2)
  const cy = Math.floor(rows / 2)
  const snake = [
    { x: cx, y: cy + rowOffset },
    { x: cx - 1, y: cy + rowOffset },
    { x: cx - 2, y: cy + rowOffset },
  ]
  return {
    cols,
    rows,
    snake,
    food: null,
    direction: 'right',
    nextDirection: 'right',
    score: 0,
    status: STATUS.PLAYING,
  }
}

/** 转向输入:side 为 host/guest;纯函数返回新对局状态 */
export function applyInput(m, side, direction) {
  if (m.over) return m
  const key = side === HOST ? 'host' : 'guest'
  return { ...m, [key]: turnSnake(m[key], direction) }
}

/**
 * 推进一个 tick:两蛇按各自 nextDirection 同时前进一步(对Pre-tick 身体判定,同时语义)。
 * 吃食物加分并由 rng 链刷新共享食物;某侧跑结束(LOST/WON)后其身体留场成为障碍。
 */
export function stepMatch(m) {
  if (m.over) return m
  const rng = makeRng(m.rngState)

  // 1. 双方同时位移(判定基于 Pre-tick 身体)
  const host = advanceSide(m, m.host, m.guest, rng)
  const guest = advanceSide(m, m.guest, m.host, rng)

  // 2. 头对头同格:双方同亡
  if (host.status === STATUS.PLAYING && guest.status === STATUS.PLAYING && sameCell(host.snake[0], guest.snake[0])) {
    host.status = STATUS.LOST
    guest.status = STATUS.LOST
  }

  // 3. 共享食物:被吃的一方已由 advanceSide 记分并拿到新食物;把食物镜像给另一侧
  const aliveWithFood = host.status === STATUS.PLAYING && host.food ? host : guest.food ? guest : host
  const food = aliveWithFood.food ? { ...aliveWithFood.food } : null
  if (host.status === STATUS.PLAYING) host.food = food
  if (guest.status === STATUS.PLAYING) guest.food = food

  // 4. 终局:双方跑结束才终局(尸体留场,先死者不再动)
  const hostDone = host.status !== STATUS.PLAYING
  const guestDone = guest.status !== STATUS.PLAYING
  const over = hostDone && guestDone
  const winner = !over ? null : host.score === guest.score ? null : host.score > guest.score ? HOST : GUEST
  return { ...m, host, guest, rngState: rng.getState(), over, winner }
}

export const sideStatus = (m, side) => (side === HOST ? m.host.status : m.guest.status)

/** 单侧前进一步:输入→新头;撞墙/撞身体(自己或对方)判负;吃到共享食物记分+换食物 */
function advanceSide(m, side, other, rng) {
  if (side.status !== STATUS.PLAYING) return side
  const vector = DIRECTIONS[side.nextDirection]
  const head = side.snake[0]
  const newHead = { x: head.x + vector.x, y: head.y + vector.y }

  const lost = { ...side, status: STATUS.LOST }
  if (newHead.x < 0 || newHead.x >= m.cols || newHead.y < 0 || newHead.y >= m.rows) {
    return lost
  }
  const ate = sameCell(newHead, side.food)
  const ownBody = ate ? side.snake : side.snake.slice(0, -1)
  if (ownBody.some((c) => sameCell(c, newHead))) {
    return lost
  }
  // 对方身体(Pre-tick):尸体留场,含其全身;不吃时对方尾巴本 tick 会移开,但「同时」语义按移动前判
  if (other.snake.some((c) => sameCell(c, newHead))) {
    return lost
  }
  const snake = ate ? [newHead, ...side.snake] : [newHead, ...side.snake.slice(0, -1)]
  const next = { ...side, snake, direction: side.nextDirection }
  if (ate) {
    next.score = side.score + 1
    const food = randomFood(snake, m.cols, m.rows, rng)
    if (!food) {
      // 盘面吃满:该侧以完成状态结束
      next.status = STATUS.WON
      next.food = null
    } else {
      next.food = food
    }
  }
  return next
}

/** 从 uint32 状态恢复 mulberry32(与 utils/games/seededRng 同算法,附状态导出) */
export function makeRng(state) {
  let a = Number(state) >>> 0
  const rng = () => {
    a = (a + 0x6d2b79f5) | 0
    let t = Math.imul(a ^ (a >>> 15), 1 | a)
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
  rng.getState = () => a >>> 0
  return rng
}
