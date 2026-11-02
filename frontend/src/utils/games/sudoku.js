// 数独纯逻辑:生成(保证唯一解)/求解计数/冲突检测/提示选格。
// 棋盘统一用长度 81 的数组表示,索引 = row * 9 + col,值 0 表示空格、1~9 表示数字。
// 变体:CLASSIC 标准 / DIAGONAL 对角线 / WINDOW 窗口(4 个额外 3×3 区) /
// JIGSAW 不规则(9 个连通异形区替代九宫) / GREATER 不等式(相邻格比较符号) /
// ODD_EVEN 奇偶(标记格限定奇数或偶数) / KILLER 杀手(无给定数字,笼内不重复且和等于标注)。
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

/** 奇偶数独:难度 → 标记格数 */
const PARITY_MARKS = {
  EASY: 26,
  MEDIUM: 22,
  HARD: 18,
  EXPERT: 14,
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

export const VARIANT_KEYS = ['CLASSIC', 'DIAGONAL', 'WINDOW', 'JIGSAW', 'GREATER', 'ODD_EVEN', 'KILLER']

/** 对角线数独的两条附加组(主对角线 + 反对角线) */
const DIAGONAL_GROUPS = [
  Array.from({ length: SIZE }, (_, i) => i * SIZE + i),
  Array.from({ length: SIZE }, (_, i) => i * SIZE + (SIZE - 1 - i)),
]

/** 窗口数独:四个不贴边的 3×3 窗口,与九宫共用行列约束 */
const WINDOW_GROUPS = [
  [10, 11, 12, 19, 20, 21, 28, 29, 30],
  [14, 15, 16, 23, 24, 25, 32, 33, 34],
  [46, 47, 48, 55, 56, 57, 64, 65, 66],
  [50, 51, 52, 59, 60, 61, 68, 69, 70],
]

/** 变体 → 行/列/宫之外的附加约束组 */
export function extraGroupsFor(variant) {
  if (variant === 'DIAGONAL') return DIAGONAL_GROUPS
  if (variant === 'WINDOW') return WINDOW_GROUPS
  return []
}

const ALL = 0x1ff

const BIT_VALUE = new Array(1 << SIZE).fill(0)
for (let v = 1; v <= SIZE; v++) BIT_VALUE[1 << (v - 1)] = v

// 奇偶掩码:位 0/2/4/6/8 是奇数,位 1/3/5/7 是偶数
const ODD_MASK = 0x155
const EVEN_MASK = 0x0aa
// 比较掩码:小于 v / 大于 v 的候选位
const LT_MASK = new Array(SIZE + 1).fill(0)
const GT_MASK = new Array(SIZE + 1).fill(0)
for (let v = 1; v <= SIZE; v++) {
  LT_MASK[v] = (1 << (v - 1)) - 1
  GT_MASK[v] = ALL & ~((1 << v) - 1)
}

const popcount = (x) => {
  let n = 0
  while (x) {
    x &= x - 1
    n++
  }
  return n
}

/** 9 个标准九宫(不规则数独换成 regions 时,它同时是兜底布局) */
export const BOX_GROUPS = (() => {
  const groups = []
  for (let br = 0; br < 3; br++) {
    for (let bc = 0; bc < 3; bc++) {
      const cells = []
      for (let r = 0; r < 3; r++) for (let c = 0; c < 3; c++) cells.push((br * 3 + r) * SIZE + bc * 3 + c)
      groups.push(cells)
    }
  }
  return groups
})()

const boxOf = (index) => {
  const r = (index / SIZE) | 0
  const c = index % SIZE
  return ((r / 3) | 0) * 3 + ((c / 3) | 0)
}

const BOX_INDEX = (() => {
  const map = new Int8Array(CELLS)
  for (let i = 0; i < CELLS; i++) map[i] = boxOf(i)
  return map
})()

const rowGroups = Array.from({ length: SIZE }, (_, r) => Array.from({ length: SIZE }, (_, c) => r * SIZE + c))
const colGroups = Array.from({ length: SIZE }, (_, c) => Array.from({ length: SIZE }, (_, r) => r * SIZE + c))

/** 27 个标准分组(9 行 + 9 列 + 9 宫),冲突检测与提示共用 */
const GROUPS = [...rowGroups, ...colGroups, ...BOX_GROUPS]

/** 每格的 20 个同行/同列/同宫邻居(标准九宫口径,笔记清理共用) */
const PEERS = (() => {
  const peers = Array.from({ length: CELLS }, () => new Set())
  for (const group of GROUPS) {
    for (const a of group) {
      for (const b of group) if (a !== b) peers[a].add(b)
    }
  }
  return peers.map((s) => [...s])
})()

/**
 * 每格的约束邻居:同行/同列/同单元;regions 提供时用异形区替代九宫(不规则数独),
 * extraGroups 追加附加约束组(对角线/窗口/杀手笼)。都不传时复用标准九宫常量。
 */
export function buildPeers({ regions = null, extraGroups = null } = {}) {
  const regionGroups = regions && regions.length ? regions : null
  const extra = extraGroups && extraGroups.length ? extraGroups : null
  if (!regionGroups && !extra) return PEERS
  const peers = Array.from({ length: CELLS }, () => new Set())
  const link = (group) => {
    for (const a of group) {
      for (const b of group) if (a !== b) peers[a].add(b)
    }
  }
  for (const group of rowGroups) link(group)
  for (const group of colGroups) link(group)
  for (const group of regionGroups || BOX_GROUPS) link(group)
  if (extra) for (const group of extra) link(group)
  return peers.map((set) => [...set])
}

/** 单元组(宫/不规则区)→ 每格所属组索引;模块级常量用 WeakMap 缓存 */
const unitIndexCache = new WeakMap()
function unitIndexOf(regions) {
  let map = unitIndexCache.get(regions)
  if (!map) {
    map = new Int8Array(CELLS).fill(-1)
    regions.forEach((cells, g) => {
      for (const cell of cells) map[cell] = g
    })
    unitIndexCache.set(regions, map)
  }
  return map
}

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

/** 不等式约束 → 每格的相邻关系 [{ other, less }]:less=true 表示本格 < other */
function inequalityAdjacency(inequalities) {
  const adj = new Array(CELLS).fill(null)
  const attach = (cell, rel) => {
    if (!adj[cell]) adj[cell] = []
    adj[cell].push(rel)
  }
  for (const [a, b] of inequalities) {
    attach(a, { other: b, less: true })
    attach(b, { other: a, less: false })
  }
  return adj
}

/**
 * 建立行/列/单元组掩码(可叠加附加组);已有重复数字或给定违反奇偶/比较时返回 null。
 * regions 提供时用它替代九宫(不规则数独)。
 */
function buildMasks(board, extraGroups = null, regions = null) {
  const rows = new Array(SIZE).fill(0)
  const cols = new Array(SIZE).fill(0)
  const units = new Array(SIZE).fill(0)
  const unitOf = regions ? unitIndexOf(regions) : BOX_INDEX
  const membership = extraGroups ? extraMembership(extraGroups) : null
  const extras = extraGroups ? extraGroups.map(() => 0) : null
  for (let i = 0; i < CELLS; i++) {
    const v = board[i]
    if (!v) continue
    const bit = 1 << (v - 1)
    const r = (i / SIZE) | 0
    const c = i % SIZE
    const u = unitOf[i]
    if (u < 0) return null
    if ((rows[r] | cols[c] | units[u]) & bit) return null
    rows[r] |= bit
    cols[c] |= bit
    units[u] |= bit
    if (extras) {
      for (const g of membership[i]) {
        if (extras[g] & bit) return null
        extras[g] |= bit
      }
    }
  }
  return { rows, cols, units, unitOf, extras, membership }
}

/** 最少候选格(MRV),找不到空格时返回 null;无解返回 { dead: true } */
function pickCell(board, rows, cols, units, unitOf, extras = null, membership = null) {
  let best = -1
  let bestMask = 0
  let bestCount = SIZE + 1
  for (let i = 0; i < CELLS; i++) {
    if (board[i]) continue
    let mask = ALL & ~(rows[(i / SIZE) | 0] | cols[i % SIZE] | units[unitOf[i]])
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
 * opts.extraGroups:附加约束组(对角线/窗口);opts.regions:替代九宫的不规则区;
 * opts.cages:杀手笼 [{ cells, sum }];opts.inequalities:不等式对 [小, 大];
 * opts.parity:每格奇偶标记(0 无约束 / 1 奇数 / 2 偶数);
 * opts.nodeBudget:搜索节点预算,超过即放弃并返回 -1(用于杀手生成时廉价淘汰近唯一盘)。
 * 返回 0 表示无解。
 */
export function countSolutions(input, limit = 2, opts = {}) {
  const board = Array.from(input)
  const extraGroups = opts.extraGroups || null
  const regions = opts.regions || null
  const cages = opts.cages || null
  const inequalities = opts.inequalities || null
  const parity = opts.parity || null
  const budget = opts.nodeBudget ?? Infinity
  const masks = buildMasks(board, extraGroups, regions)
  if (!masks) return 0
  const { rows, cols, units, unitOf, extras, membership } = masks

  // 给定格自身违反奇偶/比较关系时直接判无解(候选过滤只检查已填的邻居)
  if (parity) {
    for (let i = 0; i < CELLS; i++) {
      const p = parity[i]
      if (p && board[i] && board[i] % 2 !== (p === 1 ? 1 : 0)) return 0
    }
  }
  const ineqAdj = inequalities ? inequalityAdjacency(inequalities) : null
  if (ineqAdj) {
    for (let i = 0; i < CELLS; i++) {
      const rels = ineqAdj[i]
      if (!rels || !board[i]) continue
      for (const rel of rels) {
        const other = board[rel.other]
        if (!other) continue
        if (rel.less ? board[i] >= other : board[i] <= other) return 0
      }
    }
  }

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

  // 依相邻格的比较关系剪枝:邻格已填时,本格候选必须落在符号指向的一侧
  const inequalityCandidates = (cell, mask) => {
    const rels = ineqAdj[cell]
    if (!rels) return mask
    for (const rel of rels) {
      const v = board[rel.other]
      if (!v) continue
      mask &= rel.less ? LT_MASK[v] : GT_MASK[v]
      if (!mask) return 0
    }
    return mask
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
    // MRV 选格:候选掩码叠加附加组/笼/奇偶/比较约束
    let best = -1
    let bestMask = 0
    let bestCount = SIZE + 1
    for (let i = 0; i < CELLS; i++) {
      if (board[i]) continue
      let mask = ALL & ~(rows[(i / SIZE) | 0] | cols[i % SIZE] | units[unitOf[i]])
      if (extras && membership[i].length) {
        for (const g of membership[i]) mask &= ~extras[g]
      }
      if (parity) {
        const p = parity[i]
        if (p === 1) mask &= ODD_MASK
        else if (p === 2) mask &= EVEN_MASK
      }
      if (cageOf) mask = cageCandidates(i, mask)
      if (ineqAdj) mask = inequalityCandidates(i, mask)
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
    const u = unitOf[best]
    const cageG = cageOf ? cageOf[best] : -1
    let mask = bestMask
    while (mask) {
      const bit = mask & -mask
      mask ^= bit
      const value = BIT_VALUE[bit]
      board[best] = value
      rows[r] |= bit
      cols[c] |= bit
      units[u] |= bit
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
      units[u] ^= bit
      board[best] = 0
      if (aborted || count >= limit) return
    }
  }

  search()
  return aborted ? -1 : count
}

/**
 * 生成一个完整终盘(随机回溯,同一次调用内确定)。
 * extraGroups:附加约束组(如对角线);regions:替代九宫的不规则区;
 * nodeBudget:搜索节点预算,超限返回带空格的盘面(调用方据此判定失败)。
 */
export function generateSolution(random = Math.random, extraGroups = null, regions = null, nodeBudget = Infinity) {
  const board = new Array(CELLS).fill(0)
  const rows = new Array(SIZE).fill(0)
  const cols = new Array(SIZE).fill(0)
  const units = new Array(SIZE).fill(0)
  const unitOf = regions ? unitIndexOf(regions) : BOX_INDEX
  const membership = extraGroups ? extraMembership(extraGroups) : null
  const extras = extraGroups ? extraGroups.map(() => 0) : null
  let nodes = 0
  let aborted = false

  const fill = () => {
    if (aborted) return false
    if (++nodes > nodeBudget) {
      aborted = true
      return false
    }
    const pick = pickCell(board, rows, cols, units, unitOf, extras, membership)
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
    const u = unitOf[pick.index]
    for (const bit of candidates) {
      board[pick.index] = BIT_VALUE[bit]
      rows[r] |= bit
      cols[c] |= bit
      units[u] |= bit
      if (extras) {
        for (const g of membership[pick.index]) extras[g] |= bit
      }
      if (fill()) return true
      if (aborted) return false
      if (extras) {
        for (const g of membership[pick.index]) extras[g] &= ~bit
      }
      rows[r] ^= bit
      cols[c] ^= bit
      units[u] ^= bit
      board[pick.index] = 0
    }
    return false
  }

  fill()
  return board
}

/** 按唯一解逐格挖空;opts 为 countSolutions 的约束参数 */
function digHoles(solution, targetBlanks, random, opts) {
  const puzzle = solution.slice()
  let blanks = 0
  for (const index of shuffle(
    Array.from({ length: CELLS }, (_, i) => i),
    random,
  )) {
    if (blanks >= targetBlanks) break
    const backup = puzzle[index]
    puzzle[index] = 0
    if (countSolutions(puzzle, 2, opts) === 1) blanks++
    else puzzle[index] = backup
  }
  return { puzzle, blanks }
}

/**
 * 生成一道题:先出终盘,再按随机顺序挖空,每挖一格都校验唯一解。
 * opts.variant:VARIANT_KEYS 之一(杀手/不等式为无给定数字的盘面,各自另给线索)。
 * 返回 { variant, puzzle, solution, cages, regions, inequalities, parity, blanks, difficulty }。
 */
export function generatePuzzle(difficulty = 'MEDIUM', random = Math.random, { variant = 'CLASSIC' } = {}) {
  if (variant === 'KILLER') return generateKiller(difficulty, random)
  if (variant === 'JIGSAW') return generateJigsaw(difficulty, random)
  if (variant === 'GREATER') return generateGreater(difficulty, random)
  const extraGroups = extraGroupsFor(variant)
  const targetBlanks = DIFFICULTIES[difficulty] ?? DIFFICULTIES.MEDIUM
  const solution = generateSolution(random, extraGroups.length ? extraGroups : null)
  const parity = variant === 'ODD_EVEN' ? generateParity(solution, random, difficulty) : null
  const { puzzle, blanks } = digHoles(solution, targetBlanks, random, {
    extraGroups: extraGroups.length ? extraGroups : undefined,
    parity: parity || undefined,
  })
  return {
    variant,
    puzzle,
    solution,
    cages: null,
    regions: null,
    inequalities: null,
    parity,
    blanks,
    difficulty,
  }
}

/** 相邻格(上下左右) */
function neighborsOf(index) {
  const r = (index / SIZE) | 0
  const c = index % SIZE
  const out = []
  if (r > 0) out.push(index - SIZE)
  if (r < SIZE - 1) out.push(index + SIZE)
  if (c > 0) out.push(index - 1)
  if (c < SIZE - 1) out.push(index + 1)
  return out
}

const isConnected = (cells) => {
  if (cells.length <= 1) return true
  const members = new Set(cells)
  const stack = [cells[0]]
  const seen = new Set(stack)
  while (stack.length) {
    const cur = stack.pop()
    for (const nb of neighborsOf(cur)) {
      if (members.has(nb) && !seen.has(nb)) {
        seen.add(nb)
        stack.push(nb)
      }
    }
  }
  return seen.size === cells.length
}

/**
 * 不规则数独的区域划分:从九宫出发,不断把某区的一格与邻区的一格互换,
 * 互换后两区都必须连通(正对边界的两格直接互换必然断开,所以第二格在邻区里随机取)。
 * 返回 9 个升序格索引数组(恰好覆盖全盘)。
 */
export function generateJigsawRegions(random = Math.random) {
  const swaps = 24 + Math.floor(random() * 24)
  for (let attempt = 0; attempt < 40; attempt++) {
    const units = BOX_GROUPS.map((cells) => cells.slice())
    const owner = Int8Array.from(BOX_INDEX)
    let done = 0
    let guard = 0
    while (done < swaps && guard < 2400) {
      guard++
      const a = Math.floor(random() * CELLS)
      const nbs = neighborsOf(a)
      const seed = nbs[Math.floor(random() * nbs.length)]
      const ga = owner[a]
      const gb = owner[seed]
      if (ga === gb) continue
      const source = units[gb]
      const b = source[Math.floor(random() * source.length)]
      const cellsA = units[ga].filter((cell) => cell !== a).concat(b)
      const cellsB = source.filter((cell) => cell !== b).concat(a)
      if (!isConnected(cellsA) || !isConnected(cellsB)) continue
      units[ga] = cellsA
      units[gb] = cellsB
      owner[a] = gb
      owner[b] = ga
      done++
    }
    // 一次成功的交换都没有(退化回九宫)就换一轮随机序列
    if (!done) continue
    return units.map((cells) => cells.slice().sort((x, y) => x - y))
  }
  return BOX_GROUPS.map((cells) => cells.slice())
}

function generateJigsaw(difficulty, random) {
  const targetBlanks = DIFFICULTIES[difficulty] ?? DIFFICULTIES.MEDIUM
  const attempt = (regions) => {
    const solution = generateSolution(random, null, regions, 30000)
    if (solution.some((v) => !v)) return null
    const { puzzle, blanks } = digHoles(solution, targetBlanks, random, { regions })
    return { variant: 'JIGSAW', puzzle, solution, cages: null, regions, inequalities: null, parity: null, blanks, difficulty }
  }
  for (let round = 0; round < 10; round++) {
    const result = attempt(generateJigsawRegions(random))
    if (result) return result
  }
  // 兜底:九宫布局必然可解(此时不规则区退化为标准宫)
  return attempt(BOX_GROUPS.map((cells) => cells.slice()))
}

/** 全盘相邻格的比较关系(按终盘定向),这是不等式数独的线索 */
function buildInequalities(solution) {
  const pairs = []
  for (let r = 0; r < SIZE; r++) {
    for (let c = 0; c < SIZE; c++) {
      const i = r * SIZE + c
      if (c < SIZE - 1) {
        const j = i + 1
        pairs.push(solution[i] < solution[j] ? [i, j] : [j, i])
      }
      if (r < SIZE - 1) {
        const j = i + SIZE
        pairs.push(solution[i] < solution[j] ? [i, j] : [j, i])
      }
    }
  }
  return pairs
}

function generateGreater(difficulty, random) {
  const solution = generateSolution(random)
  const inequalities = buildInequalities(solution)
  const targetBlanks = DIFFICULTIES[difficulty] ?? DIFFICULTIES.MEDIUM
  // 与标准数独同口径挖空(比较符号是额外线索),难度对应关系保持一致
  const { puzzle, blanks } = digHoles(solution, targetBlanks, random, { inequalities })
  return { variant: 'GREATER', puzzle, solution, cages: null, regions: null, inequalities, parity: null, blanks, difficulty }
}

/** 奇偶标记:随机挑若干格,按终盘标出该格是奇数还是偶数 */
function generateParity(solution, random, difficulty) {
  const marks = Math.min(CELLS, PARITY_MARKS[difficulty] ?? 22)
  const parity = new Array(CELLS).fill(0)
  const order = shuffle(
    Array.from({ length: CELLS }, (_, i) => i),
    random,
  )
  for (let k = 0; k < marks; k++) parity[order[k]] = solution[order[k]] % 2 === 1 ? 1 : 2
  return parity
}

/** 在完整终盘上随机生长划分笼:从随机未归属格出发,逐格随机扩张到权重抽出的目标大小 */
function partitionCages(solution, random, weights) {
  const cageOf = new Int16Array(CELLS).fill(-1)
  const cages = []
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
        return {
          variant: 'KILLER',
          puzzle: new Array(CELLS).fill(0),
          solution,
          cages,
          regions: null,
          inequalities: null,
          parity: null,
          blanks: CELLS,
          difficulty,
        }
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
  return {
    variant: 'KILLER',
    puzzle: new Array(CELLS).fill(0),
    solution,
    cages,
    regions: null,
    inequalities: null,
    parity: null,
    blanks: CELLS,
    difficulty,
  }
}

/**
 * 返回所有重复的格子索引集合。
 * extraGroups:附加组(对角线/窗口/杀手笼);regions:替代九宫的不规则区;
 * inequalities:不等式对(已填但方向不符时把两格都标冲突)。第二个参数兼容旧签名(直接传附加组数组)。
 */
export function findConflicts(board, opts = null) {
  const extraGroups = Array.isArray(opts) ? opts : opts?.extraGroups || null
  const regions = Array.isArray(opts) ? null : opts?.regions || null
  const inequalities = Array.isArray(opts) ? null : opts?.inequalities || null
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
  for (const group of rowGroups) check(group)
  for (const group of colGroups) check(group)
  for (const group of regions || BOX_GROUPS) check(group)
  if (extraGroups) for (const group of extraGroups) check(group)
  if (inequalities) {
    for (const [a, b] of inequalities) {
      if (board[a] && board[b] && board[a] >= board[b]) {
        conflicts.add(a)
        conflicts.add(b)
      }
    }
  }
  return conflicts
}

/**
 * 全部填满且无冲突;opts 还要满足附加约束:
 * cages 各笼数字和等于标注、inequalities 比较方向正确、parity 奇偶与标记一致。
 */
export function isSolved(board, opts = {}) {
  for (let i = 0; i < CELLS; i++) if (!board[i]) return false
  if (findConflicts(board, { extraGroups: opts.extraGroups || null, regions: opts.regions || null }).size !== 0) {
    return false
  }
  if (opts.cages) {
    for (const cage of opts.cages) {
      let sum = 0
      for (const cell of cage.cells) sum += board[cell]
      if (sum !== cage.sum) return false
    }
  }
  if (opts.inequalities) {
    for (const [a, b] of opts.inequalities) if (board[a] >= board[b]) return false
  }
  if (opts.parity) {
    for (let i = 0; i < CELLS; i++) {
      const p = opts.parity[i]
      if (p && board[i] % 2 !== (p === 1 ? 1 : 0)) return false
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

/**
 * 落一个数字时清掉邻居格里的同数字笔记,返回是否改动。
 * opts.regions 用异形区替代九宫(不规则数独);opts.extraGroups 追加附加约束组(对角线/窗口/杀手笼)。
 */
export function clearPeerNotes(notes, index, value, opts = null) {
  const bit = 1 << (value - 1)
  const peers = opts && (opts.regions || opts.extraGroups) ? buildPeers(opts)[index] : PEERS[index]
  let changed = false
  for (const peer of peers) {
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

/** 不规则区结构校验:恰好 9 个区、每区 9 格、不越界不重叠、覆盖全盘 */
export function isValidRegions(regions) {
  if (!Array.isArray(regions) || regions.length !== SIZE) return false
  const seen = new Set()
  for (const cells of regions) {
    if (!Array.isArray(cells) || cells.length !== SIZE) return false
    for (const cell of cells) {
      if (!Number.isInteger(cell) || cell < 0 || cell >= CELLS || seen.has(cell)) return false
      seen.add(cell)
    }
  }
  return seen.size === CELLS
}

/** 不等式结构校验:非空、每项是 [小, 大] 两个不同格、同一对不重复 */
export function isValidInequalities(list) {
  if (!Array.isArray(list) || !list.length) return false
  const seen = new Set()
  for (const pair of list) {
    if (!Array.isArray(pair) || pair.length !== 2 || !pair.every(Number.isInteger)) return false
    const [a, b] = pair
    if (a < 0 || b < 0 || a >= CELLS || b >= CELLS || a === b) return false
    const key = a < b ? a * CELLS + b : b * CELLS + a
    if (seen.has(key)) return false
    seen.add(key)
  }
  return true
}

/** 奇偶标记结构校验:81 格且只取 0(无约束)/1(奇)/2(偶) */
export function isValidParity(parity) {
  return Array.isArray(parity) && parity.length === CELLS && parity.every((p) => p === 0 || p === 1 || p === 2)
}

/** 笼划分 → 每格所属笼索引(Int16Array,未归属为 -1);KILLER 渲染笼边框/求和标注用 */
export function cageIndexOf(cages) {
  const map = new Int16Array(CELLS).fill(-1)
  cages.forEach((cage, g) => {
    for (const cell of cage.cells) map[cell] = g
  })
  return map
}

/** 不规则区 → 每格所属区索引(Int8Array,未归属为 -1);JIGSAW 渲染区域边界/淡色底用 */
export function regionIndexOf(regions) {
  const map = new Int8Array(CELLS).fill(-1)
  regions.forEach((cells, g) => {
    for (const cell of cells) map[cell] = g
  })
  return map
}
