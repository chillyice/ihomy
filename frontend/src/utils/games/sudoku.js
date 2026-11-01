// 数独纯逻辑:生成(保证唯一解)/求解计数/冲突检测/提示选格。
// 棋盘统一用长度 81 的数组表示,索引 = row * 9 + col,值 0 表示空格、1~9 表示数字。
// 组件只负责渲染与交互,所有判定都在这里,便于单测覆盖。

export const SIZE = 9
export const CELLS = SIZE * SIZE

/** 挖空数量(越小越简单);生成时达不到目标就停在能保证唯一解的最大挖空数 */
export const DIFFICULTIES = {
  EASY: 40,
  MEDIUM: 48,
  HARD: 52,
  EXPERT: 56,
}

export const DIFFICULTY_KEYS = Object.keys(DIFFICULTIES)

const ALL = 0x1ff

const BIT_VALUE = new Array(1 << SIZE).fill(0)
for (let v = 1; v <= SIZE; v++) BIT_VALUE[1 << (v - 1)] = v

const popcount = (x) => {
  let n = 0
  while (x) {
    x &= x - 1
    n++
  }
  return n
}

const boxOf = (index) => {
  const r = (index / SIZE) | 0
  const c = index % SIZE
  return ((r / 3) | 0) * 3 + ((c / 3) | 0)
}

/** 27 个分组(9 行 + 9 列 + 9 宫),冲突检测与提示共用 */
const GROUPS = (() => {
  const groups = []
  for (let r = 0; r < SIZE; r++) groups.push(Array.from({ length: SIZE }, (_, c) => r * SIZE + c))
  for (let c = 0; c < SIZE; c++) groups.push(Array.from({ length: SIZE }, (_, r) => r * SIZE + c))
  for (let br = 0; br < 3; br++) {
    for (let bc = 0; bc < 3; bc++) {
      const cells = []
      for (let r = 0; r < 3; r++) for (let c = 0; c < 3; c++) cells.push((br * 3 + r) * SIZE + bc * 3 + c)
      groups.push(cells)
    }
  }
  return groups
})()

/** 每格的 20 个同行/同列/同宫邻居 */
const PEERS = (() => {
  const peers = Array.from({ length: CELLS }, () => new Set())
  for (const group of GROUPS) {
    for (const a of group) {
      for (const b of group) if (a !== b) peers[a].add(b)
    }
  }
  return peers.map((s) => [...s])
})()

const shuffle = (arr, random) => {
  for (let i = arr.length - 1; i > 0; i--) {
    const j = Math.floor(random() * (i + 1))
    const tmp = arr[i]
    arr[i] = arr[j]
    arr[j] = tmp
  }
  return arr
}

/** 建立行/列/宫位掩码;已有重复数字时返回 null(非法盘面) */
function buildMasks(board) {
  const rows = new Array(SIZE).fill(0)
  const cols = new Array(SIZE).fill(0)
  const boxes = new Array(SIZE).fill(0)
  for (let i = 0; i < CELLS; i++) {
    const v = board[i]
    if (!v) continue
    const bit = 1 << (v - 1)
    const r = (i / SIZE) | 0
    const c = i % SIZE
    const b = boxOf(i)
    if ((rows[r] | cols[c] | boxes[b]) & bit) return null
    rows[r] |= bit
    cols[c] |= bit
    boxes[b] |= bit
  }
  return { rows, cols, boxes }
}

/** 最少候选格(MRV),找不到空格时返回 null;无解返回 { dead: true } */
function pickCell(board, rows, cols, boxes) {
  let best = -1
  let bestMask = 0
  let bestCount = SIZE + 1
  for (let i = 0; i < CELLS; i++) {
    if (board[i]) continue
    const mask = ALL & ~(rows[(i / SIZE) | 0] | cols[i % SIZE] | boxes[boxOf(i)])
    if (!mask) return { dead: true }
    const n = popcount(mask)
    if (n < bestCount) {
      bestCount = n
      best = i
      bestMask = mask
      if (n === 1) break
    }
  }
  return best < 0 ? null : { index: best, mask: bestMask }
}

/**
 * 统计解的数量,最多数到 limit 就停(判唯一解用 limit=2)。
 * 返回 0 表示无解。
 */
export function countSolutions(input, limit = 2) {
  const board = Array.from(input)
  const masks = buildMasks(board)
  if (!masks) return 0
  const { rows, cols, boxes } = masks
  let count = 0

  const search = () => {
    const pick = pickCell(board, rows, cols, boxes)
    if (pick && pick.dead) return
    if (!pick) {
      count++
      return
    }
    const r = (pick.index / SIZE) | 0
    const c = pick.index % SIZE
    const b = boxOf(pick.index)
    let mask = pick.mask
    while (mask) {
      const bit = mask & -mask
      mask ^= bit
      board[pick.index] = BIT_VALUE[bit]
      rows[r] |= bit
      cols[c] |= bit
      boxes[b] |= bit
      search()
      rows[r] ^= bit
      cols[c] ^= bit
      boxes[b] ^= bit
      board[pick.index] = 0
      if (count >= limit) return
    }
  }

  search()
  return count
}

/** 生成一个完整终盘(随机回溯,同一次调用内确定) */
export function generateSolution(random = Math.random) {
  const board = new Array(CELLS).fill(0)
  const rows = new Array(SIZE).fill(0)
  const cols = new Array(SIZE).fill(0)
  const boxes = new Array(SIZE).fill(0)

  const fill = () => {
    const pick = pickCell(board, rows, cols, boxes)
    if (!pick) return true
    if (pick.dead) return false
    const candidates = []
    let mask = pick.mask
    while (mask) {
      const bit = mask & -mask
      mask ^= bit
      candidates.push(bit)
    }
    shuffle(candidates, random)
    const r = (pick.index / SIZE) | 0
    const c = pick.index % SIZE
    const b = boxOf(pick.index)
    for (const bit of candidates) {
      board[pick.index] = BIT_VALUE[bit]
      rows[r] |= bit
      cols[c] |= bit
      boxes[b] |= bit
      if (fill()) return true
      rows[r] ^= bit
      cols[c] ^= bit
      boxes[b] ^= bit
      board[pick.index] = 0
    }
    return false
  }

  fill()
  return board
}

/**
 * 生成一道题:先出终盘,再按随机顺序挖空,每挖一格都校验唯一解。
 * 返回 { puzzle, solution, blanks, difficulty }。
 */
export function generatePuzzle(difficulty = 'MEDIUM', random = Math.random) {
  const targetBlanks = DIFFICULTIES[difficulty] ?? DIFFICULTIES.MEDIUM
  const solution = generateSolution(random)
  const puzzle = solution.slice()
  let blanks = 0
  for (const index of shuffle(
    Array.from({ length: CELLS }, (_, i) => i),
    random,
  )) {
    if (blanks >= targetBlanks) break
    const backup = puzzle[index]
    puzzle[index] = 0
    if (countSolutions(puzzle, 2) === 1) blanks++
    else puzzle[index] = backup
  }
  return { puzzle, solution, blanks, difficulty }
}

/** 返回所有与同行/同列/同宫重复的格子索引集合 */
export function findConflicts(board) {
  const conflicts = new Set()
  for (const group of GROUPS) {
    const byValue = new Map()
    for (const index of group) {
      const v = board[index]
      if (!v) continue
      const list = byValue.get(v)
      if (list) list.push(index)
      else byValue.set(v, [index])
    }
    for (const list of byValue.values()) {
      if (list.length > 1) for (const index of list) conflicts.add(index)
    }
  }
  return conflicts
}

/** 全部填满且无冲突 */
export function isSolved(board) {
  for (let i = 0; i < CELLS; i++) if (!board[i]) return false
  return findConflicts(board).size === 0
}

/** 每个数字已经落了多少个(索引 1~9),数字键盘剩余计数用 */
export function digitCounts(board) {
  const counts = new Array(SIZE + 1).fill(0)
  for (const v of board) if (v) counts[v]++
  return counts
}

/** 落一个数字时清掉邻居格里的同数字笔记,返回是否改动 */
export function clearPeerNotes(notes, index, value) {
  const bit = 1 << (value - 1)
  let changed = false
  for (const peer of PEERS[index]) {
    if (notes[peer] & bit) {
      notes[peer] &= ~bit
      changed = true
    }
  }
  return changed
}

/** 笔记位掩码 → 数字数组 */
export function notesOf(mask) {
  const out = []
  for (let v = 1; v <= SIZE; v++) if (mask & (1 << (v - 1))) out.push(v)
  return out
}

/** 随机挑一个空格作为提示(没有空格返回 -1) */
export function pickHintIndex(board, random = Math.random) {
  const empty = []
  for (let i = 0; i < CELLS; i++) if (!board[i]) empty.push(i)
  if (!empty.length) return -1
  return empty[Math.min(empty.length - 1, Math.floor(random() * empty.length))]
}

/** 本地存档结构校验(读回 localStorage 时挡掉脏数据) */
export function isValidBoard(board, { allowZero = true } = {}) {
  if (!Array.isArray(board) || board.length !== CELLS) return false
  return board.every((v) => Number.isInteger(v) && v >= (allowZero ? 0 : 1) && v <= SIZE)
}
