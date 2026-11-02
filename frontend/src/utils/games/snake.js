// 贪吃蛇纯逻辑:转向(禁止直接掉头)/前进/吃食物/撞墙撞身判定/吃满全盘判胜。
// 蛇用 [{x, y}] 数组表示,头在索引 0;方向存成 up/down/left/right 字符串。
// 所有函数不改入参,返回新状态;无效转向或已结束时原样返回。

export const DIRECTIONS = {
  up: { x: 0, y: -1 },
  down: { x: 0, y: 1 },
  left: { x: -1, y: 0 },
  right: { x: 1, y: 0 },
}

export const OPPOSITE = { up: 'down', down: 'up', left: 'right', right: 'left' }

export const STATUS = {
  PLAYING: 'PLAYING',
  LOST: 'LOST',
  WON: 'WON',
}

export const DEFAULT_COLS = 20
export const DEFAULT_ROWS = 20
export const START_LENGTH = 3

const cloneState = (state) => ({
  ...state,
  snake: state.snake.map((c) => ({ ...c })),
  food: state.food ? { ...state.food } : null,
})

const sameCell = (a, b) => !!a && !!b && a.x === b.x && a.y === b.y

/** 在空格里随机放一个食物;盘面已满返回 null */
export function randomFood(snake, cols, rows, random = Math.random) {
  const occupied = new Set(snake.map((c) => c.y * cols + c.x))
  const free = []
  for (let i = 0; i < cols * rows; i++) if (!occupied.has(i)) free.push(i)
  if (!free.length) return null
  const cell = free[Math.min(free.length - 1, Math.floor(random() * free.length))]
  return { x: cell % cols, y: Math.floor(cell / cols) }
}

/** 新局:长度 3 居中、向右出发,食物随机 */
export function createGame(cols = DEFAULT_COLS, rows = DEFAULT_ROWS, random = Math.random) {
  const cx = Math.floor(cols / 2)
  const cy = Math.floor(rows / 2)
  const snake = [
    { x: cx, y: cy },
    { x: cx - 1, y: cy },
    { x: cx - 2, y: cy },
  ]
  return {
    cols,
    rows,
    snake,
    food: randomFood(snake, cols, rows, random),
    direction: 'right',
    nextDirection: 'right',
    score: 0,
    status: STATUS.PLAYING,
  }
}

/** 转向:只接受上下左右,且不能与当前待执行方向相反 */
export function turn(state, direction) {
  if (!state || state.status !== STATUS.PLAYING) return state
  if (!DIRECTIONS[direction]) return state
  if (OPPOSITE[state.nextDirection] === direction) return state
  if (state.nextDirection === direction) return state
  const next = cloneState(state)
  next.nextDirection = direction
  return next
}

/** 前进一步:撞墙/撞身判负,吃到食物变长并刷新食物,吃满全盘判胜 */
export function step(state, random = Math.random) {
  if (!state || state.status !== STATUS.PLAYING) return state
  const vector = DIRECTIONS[state.nextDirection]
  const head = state.snake[0]
  const newHead = { x: head.x + vector.x, y: head.y + vector.y }

  if (newHead.x < 0 || newHead.x >= state.cols || newHead.y < 0 || newHead.y >= state.rows) {
    const lost = cloneState(state)
    lost.status = STATUS.LOST
    lost.direction = state.nextDirection
    return lost
  }

  const eating = sameCell(newHead, state.food)
  // 不吃食物时尾巴会让位,允许头进入当前尾巴所在格
  const body = eating ? state.snake : state.snake.slice(0, -1)
  if (body.some((cell) => sameCell(cell, newHead))) {
    const lost = cloneState(state)
    lost.status = STATUS.LOST
    lost.direction = state.nextDirection
    return lost
  }

  const next = cloneState(state)
  next.direction = state.nextDirection
  next.snake = eating ? [newHead, ...next.snake] : [newHead, ...next.snake.slice(0, -1)]
  if (eating) {
    next.score = state.score + 1
    next.food = randomFood(next.snake, state.cols, state.rows, random)
    if (!next.food) next.status = STATUS.WON
  }
  return next
}

/** 当前速度(毫秒/步):吃得越多越快,封顶 70ms */
export const stepInterval = (score) => Math.max(70, 180 - Math.floor(score / 5) * 18)

/** 读档校验:尺寸合法、蛇身连续且不重叠、食物不在蛇身上 */
export function isValidState(raw) {
  if (!raw || typeof raw !== 'object') return false
  if (!Number.isInteger(raw.cols) || !Number.isInteger(raw.rows)) return false
  if (raw.cols < 4 || raw.rows < 4 || raw.cols > 60 || raw.rows > 60) return false
  if (![STATUS.PLAYING, STATUS.LOST, STATUS.WON].includes(raw.status)) return false
  if (!DIRECTIONS[raw.direction] || !DIRECTIONS[raw.nextDirection]) return false
  if (!Number.isInteger(raw.score) || raw.score < 0) return false
  if (!Array.isArray(raw.snake) || raw.snake.length < START_LENGTH) return false

  const seen = new Set()
  for (const cell of raw.snake) {
    if (!cell || !Number.isInteger(cell.x) || !Number.isInteger(cell.y)) return false
    if (cell.x < 0 || cell.x >= raw.cols || cell.y < 0 || cell.y >= raw.rows) return false
    const key = cell.y * raw.cols + cell.x
    if (seen.has(key)) return false
    seen.add(key)
  }
  for (let i = 1; i < raw.snake.length; i++) {
    const dx = Math.abs(raw.snake[i].x - raw.snake[i - 1].x)
    const dy = Math.abs(raw.snake[i].y - raw.snake[i - 1].y)
    if (dx + dy !== 1) return false
  }

  if (raw.status === STATUS.WON) return raw.food === null
  if (!raw.food) return false
  if (!Number.isInteger(raw.food.x) || !Number.isInteger(raw.food.y)) return false
  if (raw.food.x < 0 || raw.food.x >= raw.cols || raw.food.y < 0 || raw.food.y >= raw.rows) return false
  if (seen.has(raw.food.y * raw.cols + raw.food.x)) return false
  return true
}
