// 扫雷纯逻辑:布雷(首点必安全)/翻格(空格连锁展开)/插旗/双击和弦/胜负判定。
// 棋盘统一用 rows×cols 的一维数组表示,索引 = row * cols + col;每个格子自带
// { mine, adjacent, revealed, flagged }。所有函数不改入参,返回新棋盘,便于单测与组件回滚。
// 首次翻格才布雷:避开首点及其八邻,保证第一下不会直接踩雷。

export const LEVELS = {
  EASY: { rows: 9, cols: 9, mines: 10 },
  MEDIUM: { rows: 16, cols: 16, mines: 40 },
  HARD: { rows: 16, cols: 30, mines: 99 },
}

export const LEVEL_KEYS = Object.keys(LEVELS)

export const STATUS = {
  READY: 'READY',
  PLAYING: 'PLAYING',
  WON: 'WON',
  LOST: 'LOST',
}

const levelOf = (level) => (LEVELS[level] ? level : 'EASY')

/** 新棋盘:只记尺寸与雷数,格子全部未翻开;布雷推迟到首次翻格 */
export function createBoard(level = 'EASY') {
  const key = levelOf(level)
  const { rows, cols, mines } = LEVELS[key]
  return {
    level: key,
    rows,
    cols,
    mines,
    minesPlaced: false,
    status: STATUS.READY,
    exploded: null,
    cells: Array.from({ length: rows * cols }, () => ({ mine: false, adjacent: 0, revealed: false, flagged: false })),
    revealedCount: 0,
    flagCount: 0,
  }
}

const cloneBoard = (board) => ({ ...board, cells: board.cells.map((c) => ({ ...c })) })

/** 八邻索引(越界自动丢弃) */
export function neighborsOf(board, index) {
  const { rows, cols } = board
  const row = Math.floor(index / cols)
  const col = index % cols
  const out = []
  for (let dr = -1; dr <= 1; dr++) {
    for (let dc = -1; dc <= 1; dc++) {
      if (!dr && !dc) continue
      const r = row + dr
      const c = col + dc
      if (r >= 0 && r < rows && c >= 0 && c < cols) out.push(r * cols + c)
    }
  }
  return out
}

/** 部分洗牌:只打乱前 count 项,避免整盘 O(n) 之外的额外开销 */
const pickRandom = (pool, count, random) => {
  const picked = []
  for (let i = 0; i < count && i < pool.length; i++) {
    const j = i + Math.floor(random() * (pool.length - i))
    const tmp = pool[i]
    pool[i] = pool[j]
    pool[j] = tmp
    picked.push(pool[i])
  }
  return picked
}

/**
 * 布雷:避开首点及其八邻(可用格不足时只避开首点),并重算每格 adjacent。
 * 已布过雷的棋盘原样返回。
 */
export function placeMines(board, safeIndex = -1, random = Math.random) {
  const next = cloneBoard(board)
  if (next.minesPlaced) return next

  const total = next.rows * next.cols
  const excluded = new Set()
  if (safeIndex >= 0 && safeIndex < total) {
    excluded.add(safeIndex)
    for (const n of neighborsOf(next, safeIndex)) excluded.add(n)
  }
  let pool = []
  for (let i = 0; i < total; i++) if (!excluded.has(i)) pool.push(i)
  if (pool.length < next.mines) {
    pool = []
    for (let i = 0; i < total; i++) if (i !== safeIndex) pool.push(i)
  }
  for (const index of pickRandom(pool, next.mines, random)) next.cells[index].mine = true

  next.cells.forEach((cell, index) => {
    if (cell.mine) return
    cell.adjacent = neighborsOf(next, index).filter((n) => next.cells[n].mine).length
  })
  next.minesPlaced = true
  next.status = STATUS.PLAYING
  return next
}

const revealAllMines = (board, exploded) => {
  board.cells.forEach((cell) => {
    if (cell.mine) cell.revealed = true
  })
  board.exploded = exploded
  board.status = STATUS.LOST
}

/** 从 index 开始翻开;0 邻雷的格子继续向外连锁(已插旗/已翻开的格子跳过) */
const floodReveal = (board, index) => {
  const stack = [index]
  while (stack.length) {
    const current = stack.pop()
    const cell = board.cells[current]
    if (!cell || cell.revealed || cell.flagged) continue
    cell.revealed = true
    board.revealedCount++
    if (cell.adjacent === 0 && !cell.mine) {
      for (const n of neighborsOf(board, current)) {
        if (!board.cells[n].revealed) stack.push(n)
      }
    }
  }
}

const checkWin = (board) => {
  if (board.status !== STATUS.PLAYING) return
  if (board.revealedCount === board.rows * board.cols - board.mines) {
    board.status = STATUS.WON
    board.cells.forEach((cell) => {
      if (cell.mine) cell.flagged = true
    })
    board.flagCount = board.mines
  }
}

/** 翻开一格:首次翻格先布雷;踩雷则亮出全部雷并结束;安全格按 0 连锁展开 */
export function revealCell(board, index, random = Math.random) {
  if (!board || index < 0 || index >= board.rows * board.cols) return board
  if (board.status === STATUS.WON || board.status === STATUS.LOST) return board
  if (board.cells[index].flagged || board.cells[index].revealed) return board

  const base = board.minesPlaced ? board : placeMines(board, index, random)
  const next = cloneBoard(base)
  if (next.cells[index].mine) {
    revealAllMines(next, index)
    return next
  }
  floodReveal(next, index)
  checkWin(next)
  return next
}

/** 插旗/取消插旗:已翻开的格子不响应;终局后不再接受操作 */
export function toggleFlag(board, index) {
  if (!board || index < 0 || index >= board.rows * board.cols) return board
  if (board.status === STATUS.WON || board.status === STATUS.LOST) return board
  const cell = board.cells[index]
  if (cell.revealed) return board
  const next = cloneBoard(board)
  const target = next.cells[index]
  target.flagged = !target.flagged
  next.flagCount += target.flagged ? 1 : -1
  return next
}

/**
 * 双击/和弦:已翻开的数字格,周围旗数等于该数字时,翻开其余相邻格。
 * 若其中有雷则照常引爆(经典规则);没有变化时返回原棋盘。
 */
export function chord(board, index) {
  if (!board || board.status !== STATUS.PLAYING || board.minesPlaced === false) return board
  const cell = board.cells[index]
  if (!cell || !cell.revealed || cell.adjacent === 0) return board
  const around = neighborsOf(board, index)
  const flags = around.filter((n) => board.cells[n].flagged).length
  if (flags !== cell.adjacent) return board

  const next = cloneBoard(board)
  const pending = around.filter((n) => !next.cells[n].flagged && !next.cells[n].revealed)
  if (!pending.length) return board
  for (const n of pending) {
    if (next.cells[n].mine) {
      revealAllMines(next, n)
      return next
    }
    floodReveal(next, n)
    if (next.status === STATUS.WON) return next
  }
  checkWin(next)
  return next
}

/** 剩余待插旗数(负数表示插多了) */
export function remainingMines(board) {
  return board.mines - board.flagCount
}

/** 读档校验:尺寸/雷数/计数自洽,布雷后每格 adjacent 必须等于实际邻雷数 */
export function isValidBoard(raw) {
  if (!raw || typeof raw !== 'object') return false
  if (!LEVELS[raw.level]) return false
  const { rows, cols, mines } = LEVELS[raw.level]
  if (raw.rows !== rows || raw.cols !== cols || raw.mines !== mines) return false
  if (![STATUS.READY, STATUS.PLAYING, STATUS.WON, STATUS.LOST].includes(raw.status)) return false
  if (typeof raw.minesPlaced !== 'boolean') return false
  if (!Array.isArray(raw.cells) || raw.cells.length !== rows * cols) return false

  let mineCount = 0
  let revealed = 0
  let flagged = 0
  for (const cell of raw.cells) {
    if (!cell || typeof cell.mine !== 'boolean' || typeof cell.revealed !== 'boolean' || typeof cell.flagged !== 'boolean') {
      return false
    }
    if (!Number.isInteger(cell.adjacent) || cell.adjacent < 0 || cell.adjacent > 8) return false
    if (cell.mine) mineCount++
    if (cell.revealed) revealed++
    if (cell.flagged) flagged++
  }
  if (raw.revealedCount !== revealed || raw.flagCount !== flagged) return false
  // 首次翻格前允许先插旗,但不可能有雷或已翻开的格子
  if (!raw.minesPlaced) return mineCount === 0 && revealed === 0
  if (mineCount !== mines) return false

  const board = { rows, cols }
  for (let i = 0; i < raw.cells.length; i++) {
    if (raw.cells[i].mine) continue
    const expected = neighborsOf(board, i).filter((n) => raw.cells[n].mine).length
    if (raw.cells[i].adjacent !== expected) return false
  }
  return true
}
