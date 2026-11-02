// 俄罗斯方块纯逻辑:7-bag 随机、旋转踢墙、软降/硬降、消行计分、暂存(Hold)。
// 棋盘 10×20,格子存方块字母(I/J/L/O/S/T/Z)或 null;方块用 { type, rotation, x, y } 表示,
// 坐标是 4×4 包围盒左上角。所有函数不改入参,返回新状态;不可执行的动作原样返回。

export const COLS = 10
export const ROWS = 20
export const TYPES = ['I', 'J', 'L', 'O', 'S', 'T', 'Z']

/** 四种旋转态在 4×4 包围盒内的格子偏移([x, y]) */
export const SHAPES = {
  I: [
    [
      [0, 1],
      [1, 1],
      [2, 1],
      [3, 1],
    ],
    [
      [2, 0],
      [2, 1],
      [2, 2],
      [2, 3],
    ],
    [
      [0, 2],
      [1, 2],
      [2, 2],
      [3, 2],
    ],
    [
      [1, 0],
      [1, 1],
      [1, 2],
      [1, 3],
    ],
  ],
  J: [
    [
      [0, 0],
      [0, 1],
      [1, 1],
      [2, 1],
    ],
    [
      [1, 0],
      [2, 0],
      [1, 1],
      [1, 2],
    ],
    [
      [0, 1],
      [1, 1],
      [2, 1],
      [2, 2],
    ],
    [
      [1, 0],
      [1, 1],
      [0, 2],
      [1, 2],
    ],
  ],
  L: [
    [
      [2, 0],
      [0, 1],
      [1, 1],
      [2, 1],
    ],
    [
      [1, 0],
      [1, 1],
      [1, 2],
      [2, 2],
    ],
    [
      [0, 1],
      [1, 1],
      [2, 1],
      [0, 2],
    ],
    [
      [0, 0],
      [1, 0],
      [1, 1],
      [1, 2],
    ],
  ],
  O: [
    [
      [1, 0],
      [2, 0],
      [1, 1],
      [2, 1],
    ],
    [
      [1, 0],
      [2, 0],
      [1, 1],
      [2, 1],
    ],
    [
      [1, 0],
      [2, 0],
      [1, 1],
      [2, 1],
    ],
    [
      [1, 0],
      [2, 0],
      [1, 1],
      [2, 1],
    ],
  ],
  S: [
    [
      [1, 0],
      [2, 0],
      [0, 1],
      [1, 1],
    ],
    [
      [1, 0],
      [1, 1],
      [2, 1],
      [2, 2],
    ],
    [
      [1, 1],
      [2, 1],
      [0, 2],
      [1, 2],
    ],
    [
      [0, 0],
      [0, 1],
      [1, 1],
      [1, 2],
    ],
  ],
  T: [
    [
      [1, 0],
      [0, 1],
      [1, 1],
      [2, 1],
    ],
    [
      [1, 0],
      [1, 1],
      [2, 1],
      [1, 2],
    ],
    [
      [0, 1],
      [1, 1],
      [2, 1],
      [1, 2],
    ],
    [
      [1, 0],
      [0, 1],
      [1, 1],
      [1, 2],
    ],
  ],
  Z: [
    [
      [0, 0],
      [1, 0],
      [1, 1],
      [2, 1],
    ],
    [
      [2, 0],
      [1, 1],
      [2, 1],
      [1, 2],
    ],
    [
      [0, 1],
      [1, 1],
      [1, 2],
      [2, 2],
    ],
    [
      [1, 0],
      [0, 1],
      [1, 1],
      [0, 2],
    ],
  ],
}

export const STATUS = {
  PLAYING: 'PLAYING',
  OVER: 'OVER',
}

export const LINE_SCORES = [0, 100, 300, 500, 800]

/** 旋转贴墙时的横向/纵向修正顺序(取第一个不碰撞的位置) */
const KICKS = [
  [0, 0],
  [-1, 0],
  [1, 0],
  [-2, 0],
  [2, 0],
  [0, -1],
  [-1, -1],
  [1, -1],
]

export const emptyBoard = () => Array.from({ length: ROWS }, () => new Array(COLS).fill(null))

export const cellsOf = (piece) =>
  SHAPES[piece.type][piece.rotation].map(([dx, dy]) => ({ x: piece.x + dx, y: piece.y + dy, type: piece.type }))

const collides = (board, piece) => cellsOf(piece).some(({ x, y }) => x < 0 || x >= COLS || y >= ROWS || (y >= 0 && board[y][x] !== null))

const cloneState = (state) => ({
  ...state,
  board: state.board.map((row) => row.slice()),
  bag: state.bag.slice(),
  queue: state.queue.slice(),
  current: state.current ? { ...state.current } : null,
})

const shuffledBag = (random) => {
  const bag = TYPES.slice()
  for (let i = bag.length - 1; i > 0; i--) {
    const j = Math.floor(random() * (i + 1))
    const tmp = bag[i]
    bag[i] = bag[j]
    bag[j] = tmp
  }
  return bag
}

const ensureQueue = (state, random) => {
  while (state.queue.length < 5) {
    if (!state.bag.length) state.bag = shuffledBag(random)
    state.queue.push(state.bag.pop())
  }
}

const makePiece = (type) => ({ type, rotation: 0, x: 3, y: 0 })

/** 出下一个方块;若出生点已被占用则判负 */
const spawnNext = (state, random) => {
  ensureQueue(state, random)
  const next = cloneState(state)
  const type = next.queue.shift()
  ensureQueue(next, random)
  next.current = makePiece(type)
  if (collides(next.board, next.current)) next.status = STATUS.OVER
  return next
}

export function createGame(random = Math.random) {
  const state = {
    board: emptyBoard(),
    bag: [],
    queue: [],
    current: null,
    hold: null,
    canHold: true,
    score: 0,
    lines: 0,
    level: 1,
    lastClear: 0,
    clearSeq: 0,
    status: STATUS.PLAYING,
  }
  return spawnNext(state, random)
}

/** 左右/下移一格;撞墙或撞块时原样返回 */
export function tryMove(state, dx, dy) {
  if (!state || state.status !== STATUS.PLAYING || !state.current) return state
  const piece = { ...state.current, x: state.current.x + dx, y: state.current.y + dy }
  if (collides(state.board, piece)) return state
  const next = cloneState(state)
  next.current = piece
  return next
}

/** 顺时针/逆时针旋转,带简易踢墙 */
export function rotate(state, direction = 1) {
  if (!state || state.status !== STATUS.PLAYING || !state.current) return state
  const current = state.current
  if (current.type === 'O') return state
  const rotation = (current.rotation + (direction < 0 ? 3 : 1)) % 4
  for (const [dx, dy] of KICKS) {
    const piece = { ...current, rotation, x: current.x + dx, y: current.y + dy }
    if (!collides(state.board, piece)) {
      const next = cloneState(state)
      next.current = piece
      return next
    }
  }
  return state
}

/** 方块继续下落会停在哪一行(ghost 预览用) */
export function ghostY(state) {
  if (!state || !state.current) return 0
  let y = state.current.y
  while (!collides(state.board, { ...state.current, y: y + 1 })) y++
  return y
}

/** 落定:写入棋盘 → 消行计分 → 出下一块;顶部放不下则判负 */
function lock(state, bonus = 0, random = Math.random) {
  const next = cloneState(state)
  const cells = cellsOf(next.current)
  if (cells.some(({ y }) => y < 0)) {
    next.status = STATUS.OVER
    return next
  }
  for (const { x, y, type } of cells) next.board[y][x] = type

  const kept = next.board.filter((row) => row.some((cell) => cell === null))
  const cleared = ROWS - kept.length
  if (cleared > 0) {
    const fresh = Array.from({ length: cleared }, () => new Array(COLS).fill(null))
    next.board = [...fresh, ...kept]
    next.lines = state.lines + cleared
    next.level = 1 + Math.floor(next.lines / 10)
    next.score = state.score + LINE_SCORES[cleared] * state.level + bonus
  } else {
    next.board = kept
    next.score = state.score + bonus
  }
  next.lastClear = cleared
  next.clearSeq = (state.clearSeq || 0) + 1
  next.canHold = true
  return spawnNext(next, random)
}

/** 重力下落一格;落到底就锁定 */
export function tick(state, random = Math.random) {
  if (!state || state.status !== STATUS.PLAYING) return state
  const moved = tryMove(state, 0, 1)
  return moved === state ? lock(state, 0, random) : moved
}

/** 软降:能下就下并 +1 分,到底直接锁定 */
export function softDrop(state, random = Math.random) {
  if (!state || state.status !== STATUS.PLAYING) return state
  const moved = tryMove(state, 0, 1)
  if (moved === state) return lock(state, 0, random)
  const next = cloneState(moved)
  next.score = moved.score + 1
  return next
}

/** 硬降:直接落到最底并 +2 分/格,随后锁定 */
export function hardDrop(state, random = Math.random) {
  if (!state || state.status !== STATUS.PLAYING || !state.current) return state
  const targetY = ghostY(state)
  const distance = targetY - state.current.y
  const dropped = { ...state, current: { ...state.current, y: targetY } }
  return lock(dropped, distance * 2, random)
}

/** 暂存/交换当前方块(每次落定后只能用一次) */
export function holdPiece(state, random = Math.random) {
  if (!state || state.status !== STATUS.PLAYING || !state.current || !state.canHold) return state
  const next = cloneState(state)
  const currentType = next.current.type
  next.canHold = false
  if (!next.hold) {
    next.hold = currentType
    return spawnNext(next, random)
  }
  const swap = next.hold
  next.hold = currentType
  const piece = makePiece(swap)
  if (collides(next.board, piece)) return state
  next.current = piece
  return next
}

/** 读档校验:棋盘尺寸/取值、当前块与队列合法、计分字段自洽 */
export function isValidState(raw) {
  if (!raw || typeof raw !== 'object') return false
  if (![STATUS.PLAYING, STATUS.OVER].includes(raw.status)) return false
  if (!Array.isArray(raw.board) || raw.board.length !== ROWS) return false
  if (!raw.board.every((row) => Array.isArray(row) && row.length === COLS && row.every((c) => c === null || TYPES.includes(c)))) {
    return false
  }
  if (!Array.isArray(raw.queue) || !raw.queue.every((t) => TYPES.includes(t))) return false
  if (!Array.isArray(raw.bag) || !raw.bag.every((t) => TYPES.includes(t))) return false
  if (raw.hold !== null && !TYPES.includes(raw.hold)) return false
  if (!raw.current || !TYPES.includes(raw.current.type)) return false
  if (!Number.isInteger(raw.current.rotation) || raw.current.rotation < 0 || raw.current.rotation > 3) return false
  if (!Number.isInteger(raw.current.x) || !Number.isInteger(raw.current.y)) return false
  if (!Number.isInteger(raw.score) || raw.score < 0) return false
  if (!Number.isInteger(raw.lines) || raw.lines < 0) return false
  if (!Number.isInteger(raw.level) || raw.level < 1) return false
  if (typeof raw.canHold !== 'boolean') return false
  if (collides(raw.board, raw.current)) return false
  return true
}
