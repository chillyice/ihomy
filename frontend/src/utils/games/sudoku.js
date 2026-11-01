// 数独纯逻辑:生成(保证唯一解)/求解计数/冲突检测/提示选格。
// 棋盘统一用长度 81 的数组表示,索引 = row * 9 + col,值 0 表示空格、1~9 表示数字。
// 变体:CLASSIC 标准 / DIAGONAL 对角线(两条对角线也须 1~9 不重复)/ KILLER 杀手
// (盘面无给定数字,按笼划分,各笼内数字不重复且数字和等于笼标注)。
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

/** 杀手笼:难度 → 笼大小权重(大笼约束弱、题更难;小笼多撒能提升唯一解命中率) */
export const KILLER_CAGE_WEIGHTS = {
  EASY: { 2: 6, 3: 4 },
  MEDIUM: { 2: 5, 3: 4, 4: 2 },
  HARD: { 2: 4, 3: 4, 4: 3, 5: 1.5 },
  EXPERT: { 2: 3, 3: 4, 4: 3, 5: 2, 6: 1 },
}

const pickCageSize = (weights, random) => {
  const entries = Object.entries(weights)
  let roll = random() * entries.reduce((acc, [, w]) => acc + w, 0)
  for (const [size, w] of entries) {
    roll -= w
    if (roll < 0) return Number(size)
  }
  return Number(entries[entries.length - 1][0])
}

export const VARIANT_KEYS = ['CLASSIC', 'DIAGONAL', 'KILLER']

/** 对角线数独的两条附加组(主对角线 + 反对角线) */
const DIAGONAL_GROUPS = [
  Array.from({ length: SIZE }, (_, i) => i * SIZE + i),
  Array.from({ length: SIZE }, (_, i) => i * SIZE + (SIZE - 1 - i)),
]

/** 变体 → 行/列/宫之外的附加约束组 */
export function extraGroupsFor(variant) {
  if (variant === 'DIAGONAL') return DIAGONAL_GROUPS
  return []
}

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

/** 每格所属附加组的索引列表(模块级常量组用 WeakMap 缓存) */
const extraMembershipCache = new WeakMap()
function extraMembership(extraGroups) {
  let membership = extraMembershipCache.get(extraGroups)
  if (!membership) {
    membership = Array.from({ length: CELLS }, () => [])
    extraGroups.forEach((cells, g) => {
      for (const cell of cells) membership[cell].push(g)
    })
    extraMembershipCache.set(extraGroups, membership)
  }
  return membership
}

const shuffle = (arr, random) => {
  for (let i = arr.length - 1; i > 0; i--) {
    const j = Math.floor(random() * (i + 1))
    const tmp = arr[i]
    arr[i] = arr[j]
    arr[j] = tmp
  }
  return arr
}

/** 建立行/列/宫位掩码(可叠加附加组);已有重复数字时返回 null(非法盘面) */
function buildMasks(board, extraGroups = null) {
  const rows = new Array(SIZE).fill(0)
  const cols = new Array(SIZE).fill(0)
  const boxes = new Array(SIZE).fill(0)
  const membership = extraGroups ? extraMembership(extraGroups) : null
  const extras = extraGroups ? extraGroups.map(() => 0) : null
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
    if (extras) {
      for (const g of membership[i]) {
        if (extras[g] & bit) return null
        extras[g] |= bit
      }
    }
  }
  return { rows, cols, boxes, extras, membership }
}

/** 最少候选格(MRV),找不到空格时返回 null;无解返回 { dead: true } */
function pickCell(board, rows, cols, boxes, extras = null, membership = null) {
  let best = -1
  let bestMask = 0
  let bestCount = SIZE + 1
  for (let i = 0; i < CELLS; i++) {
    if (board[i]) continue
    let mask = ALL & ~(rows[(i / SIZE) | 0] | cols[i % SIZE] | boxes[boxOf(i)])
    if (extras && membership[i].length) {
      for (const g of membership[i]) mask &= ~extras[g]
    }
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
 * opts.extraGroups:附加约束组(如对角线);opts.cages:杀手笼 [{ cells, sum }];
 * opts.nodeBudget:搜索节点预算,超过即放弃并返回 -1(用于杀手生成时廉价淘汰近唯一盘)。
 * 返回 0 表示无解。
 */
export function countSolutions(input, limit = 2, opts = {}) {
  const board = Array.from(input)
  const extraGroups = opts.extraGroups || null
  const cages = opts.cages || null
  const budget = opts.nodeBudget ?? Infinity
  const masks = buildMasks(board, extraGroups)
  if (!masks) return 0
  const { rows, cols, boxes, extras, membership } = masks

  // 杀手笼状态:各笼剩余和 / 未填格数 / 笼内已用数字掩码;盘面已有笼内重复直接判无解
  let cageSum = null
  let cageLeft = null
  let cageUsed = null
  let cageOf = null
  if (cages) {
    cageOf = new Int16Array(CELLS).fill(-1)
    cageSum = []
    cageLeft = []
    cageUsed = []
    cages.forEach((cage, g) => {
      for (const cell of cage.cells) cageOf[cell] = g
      cageSum.push(cage.sum)
      cageLeft.push(cage.cells.length)
      cageUsed.push(0)
    })
    for (let i = 0; i < CELLS; i++) {
      const v = board[i]
      if (!v) continue
      const g = cageOf[i]
      if (g < 0) continue
      const bit = 1 << (v - 1)
      if (cageUsed[g] & bit) return 0
      cageUsed[g] |= bit
      cageSum[g] -= v
      cageLeft[g] -= 1
    }
  }

  // 依笼约束过滤候选:笼内未用过 + 扣掉该数字后,剩余格的可攻和区间要装得下剩余数字
  const cageCandidates = (cell, mask) => {
    const g = cageOf[cell]
    if (g < 0) return mask
    let m = mask & ~cageUsed[g]
    let filtered = 0
    while (m) {
      const bit = m & -m
      m ^= bit
      const rest = cageSum[g] - BIT_VALUE[bit]
      const restCells = cageLeft[g] - 1
      if (rest < 0) continue
      if (restCells === 0) {
        if (rest !== 0) continue
      } else {
        // restCells 个互异数字:最小和 = 1+…+restCells,最大和 = 9+8+…
        const minSum = (restCells * (restCells + 1)) / 2
        const maxSum = (restCells * (19 - restCells)) / 2
        if (rest < minSum || rest > maxSum) continue
      }
      filtered |= bit
    }
    return filtered
  }

  let count = 0
  let nodes = 0
  let aborted = false

  const search = () => {
    if (aborted) return
    if (++nodes > budget) {
      aborted = true
      return
    }
    // MRV 选格:候选掩码叠加附加组与笼约束
    let best = -1
    let bestMask = 0
    let bestCount = SIZE + 1
    for (let i = 0; i < CELLS; i++) {
      if (board[i]) continue
      let mask = ALL & ~(rows[(i / SIZE) | 0] | cols[i % SIZE] | boxes[boxOf(i)])
      if (extras && membership[i].length) {
        for (const g of membership[i]) mask &= ~extras[g]
      }
      if (cageOf) mask = cageCandidates(i, mask)
      if (!mask) return
      const n = popcount(mask)
      if (n < bestCount) {
        bestCount = n
        best = i
        bestMask = mask
        if (n === 1) break
      }
    }
    if (best < 0) {
      count++
      return
    }
    const r = (best / SIZE) | 0
    const c = best % SIZE
    const b = boxOf(best)
    const cageG = cageOf ? cageOf[best] : -1
    let mask = bestMask
    while (mask) {
      const bit = mask & -mask
      mask ^= bit
      const value = BIT_VALUE[bit]
      board[best] = value
      rows[r] |= bit
      cols[c] |= bit
      boxes[b] |= bit
      if (extras) {
        for (const g of membership[best]) extras[g] |= bit
      }
      if (cageG >= 0) {
        cageUsed[cageG] |= bit
        cageSum[cageG] -= value
        cageLeft[cageG] -= 1
      }
      search()
      if (cageG >= 0) {
        cageUsed[cageG] &= ~bit
        cageSum[cageG] += value
        cageLeft[cageG] += 1
      }
      if (extras) {
        for (const g of membership[best]) extras[g] &= ~bit
      }
      rows[r] ^= bit
      cols[c] ^= bit
      boxes[b] ^= bit
      board[best] = 0
      if (aborted || count >= limit) return
    }
  }

  search()
  return aborted ? -1 : count
}

/** 生成一个完整终盘(随机回溯,同一次调用内确定);extraGroups 为附加约束组(如对角线) */
export function generateSolution(random = Math.random, extraGroups = null) {
  const board = new Array(CELLS).fill(0)
  const rows = new Array(SIZE).fill(0)
  const cols = new Array(SIZE).fill(0)
  const boxes = new Array(SIZE).fill(0)
  const membership = extraGroups ? extraMembership(extraGroups) : null
  const extras = extraGroups ? extraGroups.map(() => 0) : null

  const fill = () => {
    const pick = pickCell(board, rows, cols, boxes, extras, membership)
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
      if (extras) {
        for (const g of membership[pick.index]) extras[g] |= bit
      }
      if (fill()) return true
      if (extras) {
        for (const g of membership[pick.index]) extras[g] &= ~bit
      }
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
 * opts.variant:CLASSIC / DIAGONAL / KILLER(杀手为全空盘 + 笼划分,唯一解校验带笼约束)。
 * 返回 { variant, puzzle, solution, cages, blanks, difficulty }。
 */
export function generatePuzzle(difficulty = 'MEDIUM', random = Math.random, { variant = 'CLASSIC' } = {}) {
  if (variant === 'KILLER') return generateKiller(difficulty, random)
  const extraGroups = extraGroupsFor(variant)
  const targetBlanks = DIFFICULTIES[difficulty] ?? DIFFICULTIES.MEDIUM
  const solution = generateSolution(random, extraGroups.length ? extraGroups : null)
  const puzzle = solution.slice()
  const solveOpts = extraGroups.length ? { extraGroups } : undefined
  let blanks = 0
  for (const index of shuffle(
    Array.from({ length: CELLS }, (_, i) => i),
    random,
  )) {
    if (blanks >= targetBlanks) break
    const backup = puzzle[index]
    puzzle[index] = 0
    if (countSolutions(puzzle, 2, solveOpts) === 1) blanks++
    else puzzle[index] = backup
  }
  return { variant, puzzle, solution, cages: null, blanks, difficulty }
}

/** 在完整终盘上随机生长划分笼:从随机未归属格出发,逐格随机扩张到权重抽出的目标大小 */
function partitionCages(solution, random, weights) {
  const cageOf = new Int16Array(CELLS).fill(-1)
  const cages = []
  const neighborsOf = (i) => {
    const r = (i / SIZE) | 0
    const c = i % SIZE
    const out = []
    if (r > 0) out.push(i - SIZE)
    if (r < SIZE - 1) out.push(i + SIZE)
    if (c > 0) out.push(i - 1)
    if (c < SIZE - 1) out.push(i + 1)
    return out
  }
  for (let start = 0; start < CELLS; start++) {
    if (cageOf[start] >= 0) continue
    const target = pickCageSize(weights, random)
    const cells = [start]
    cageOf[start] = cages.length
    while (cells.length < target) {
      const frontier = []
      for (const cell of cells) {
        for (const nb of neighborsOf(cell)) if (cageOf[nb] < 0) frontier.push(nb)
      }
      if (!frontier.length) break
      const picked = frontier[Math.floor(random() * frontier.length)]
      cageOf[picked] = cages.length
      cells.push(picked)
    }
    let sum = 0
    for (const cell of cells) sum += solution[cell]
    cages.push({ cells: cells.slice().sort((a, b) => a - b), sum })
  }
  return cages
}

function generateKiller(difficulty, random) {
  const baseWeights = KILLER_CAGE_WEIGHTS[difficulty] ?? KILLER_CAGE_WEIGHTS.MEDIUM
  let weights = baseWeights
  let solution = null
  for (let round = 0; round < 8; round++) {
    solution = generateSolution(random)
    for (let attempt = 0; attempt < 12; attempt++) {
      const cages = partitionCages(solution, random, weights)
      // 近唯一盘的解计数可能极慢:给节点预算,超时当多解题淘汰,保证出题耗时可控
      if (countSolutions(new Array(CELLS).fill(0), 2, { cages, nodeBudget: 8000 }) === 1) {
        return { variant: 'KILLER', puzzle: new Array(CELLS).fill(0), solution, cages, blanks: CELLS, difficulty }
      }
    }
    // 该笼大小反复出多解题:收缩最大笼让约束变强,保证能出题
    const maxSize = Math.max(...Object.keys(weights).map(Number))
    if (maxSize > 2) {
      const next = {}
      for (const [size, w] of Object.entries(weights)) if (Number(size) < maxSize) next[size] = w
      weights = next
    }
  }
  // 兜底:全 1 格笼 = 直接亮答案,必然唯一
  const cages = solution.map((v, i) => ({ cells: [i], sum: v }))
  return { variant: 'KILLER', puzzle: new Array(CELLS).fill(0), solution, cages, blanks: CELLS, difficulty }
}

/** 返回所有与同行/同列/同宫(及 extraGroups 附加组,如对角线/杀手笼)重复的格子索引集合 */
export function findConflicts(board, extraGroups = null) {
  const conflicts = new Set()
  const check = (group) => {
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
  for (const group of GROUPS) check(group)
  if (extraGroups) for (const group of extraGroups) check(group)
  return conflicts
}

/** 全部填满且无冲突;opts.cages 提供时还要求各笼数字和等于笼标注(笼内重复已含在冲突里) */
export function isSolved(board, opts = {}) {
  for (let i = 0; i < CELLS; i++) if (!board[i]) return false
  if (findConflicts(board, opts.extraGroups || null).size !== 0) return false
  if (opts.cages) {
    for (const cage of opts.cages) {
      let sum = 0
      for (const cell of cage.cells) sum += board[cell]
      if (sum !== cage.sum) return false
    }
  }
  return true
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

/** 杀手笼结构校验(读档用):cells 不越界不重叠,sum 在 1~45,且恰好覆盖全盘 81 格 */
export function isValidCages(cages) {
  if (!Array.isArray(cages) || !cages.length) return false
  const seen = new Set()
  for (const cage of cages) {
    if (!cage || !Array.isArray(cage.cells) || !cage.cells.length) return false
    if (!Number.isInteger(cage.sum) || cage.sum < 1 || cage.sum > 45) return false
    for (const cell of cage.cells) {
      if (!Number.isInteger(cell) || cell < 0 || cell >= CELLS || seen.has(cell)) return false
      seen.add(cell)
    }
  }
  return seen.size === CELLS
}

/** 笼划分 → 每格所属笼索引(Int16Array,未归属为 -1);KILLER 渲染笼边框/求和标注用 */
export function cageIndexOf(cages) {
  const map = new Int16Array(CELLS).fill(-1)
  cages.forEach((cage, g) => {
    for (const cell of cage.cells) map[cell] = g
  })
  return map
}
