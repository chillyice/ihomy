// 数独纯逻辑:生成(保证唯一解)/求解计数/冲突检测/提示选格。
// 棋盘统一用长度 81 的数组表示,索引 = row * 9 + col,值 0 表示空格、1~9 表示数字。
// 变体:CLASSIC 标准 / DIAGONAL 对角线 / WINDOW 窗口(4 个额外 3×3 区) /
// JIGSAW 不规则(9 个连通异形区替代九宫) / GREATER 不等式(相邻格比较符号) /
// ODD_EVEN 奇偶(标记格限定奇数或偶数) / KILLER 杀手(无给定数字,笼内不重复且和等于标注) /
// ANTI_KNIGHT 反马步 / ANTI_KING 反王步(斜对角不同号) / THERMO 温度计(沿线严格递增) /
// CONSECUTIVE 连续(标记相邻格相差 1) / XV 罗马(标记相邻格和为 5 或 10) /
// SKYSCRAPER 摩天楼(四边外围给出可见楼层数) / ARITHMETIC 等差(标记连续格成等差数列) /
// PALINDROME 回文(标记线上的数字正反读一致) / ANTI_BISHOP 无象(象步隔一格不同号) /
// NON_CONSECUTIVE 不连续(相邻格数字差不等于 1) / DRAGON 九龙(九条龙形额外区域)。
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

/** 温度计:难度 → 温度计条数(条数越少、盘面给定越少,难度越高) */
const THERMO_COUNTS = {
  EASY: 15,
  MEDIUM: 12,
  HARD: 10,
  EXPERT: 8,
}

/** 单条温度计最多跨几格(太长会绕出难看的折线) */
const THERMO_MAX_LEN = 6

/** 摩天楼:难度 → 隐藏的外围提示数(提示越少越难;同一行/列最多藏一侧) */
const SKYSCRAPER_HIDDEN = {
  EASY: 0,
  MEDIUM: 6,
  HARD: 10,
  EXPERT: 14,
}

/** 等差线:难度 → 线数(线多 = 线索多 = 越简单) */
const ARITH_COUNTS = {
  EASY: 16,
  MEDIUM: 13,
  HARD: 10,
  EXPERT: 8,
}

/** 回文线:难度 → 线数(线多 = 线索多 = 越简单) */
const PALINDROME_COUNTS = {
  EASY: 16,
  MEDIUM: 13,
  HARD: 10,
  EXPERT: 8,
}

/** 等差线 3~5 格(直线,同一行或同一列) */
const ARITH_MIN_LEN = 3
const ARITH_MAX_LEN = 5

/** 回文线 3~5 格(可折线;偶数长度必然出现相邻同号,故只用奇数长度) */
const PAL_MIN_LEN = 3
const PAL_MAX_LEN = 5

/** 摩天楼四条边(与线索对象字段同序) */
const SKY_SIDES = ['top', 'bottom', 'left', 'right']

const pickCageSize = (weights, random) => {
  const entries = Object.entries(weights)
  let roll = random() * entries.reduce((acc, [, w]) => acc + w, 0)
  for (const [size, w] of entries) {
    roll -= w
    if (roll < 0) return Number(size)
  }
  return Number(entries[entries.length - 1][0])
}

export const VARIANT_KEYS = [
  'CLASSIC',
  'DIAGONAL',
  'WINDOW',
  'JIGSAW',
  'DRAGON',
  'GREATER',
  'ODD_EVEN',
  'KILLER',
  'ANTI_KNIGHT',
  'ANTI_KING',
  'ANTI_BISHOP',
  'THERMO',
  'CONSECUTIVE',
  'NON_CONSECUTIVE',
  'XV',
  'SKYSCRAPER',
  'ARITHMETIC',
  'PALINDROME',
]

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

/** 反马步:马步位置的两格不能同号(每对只取一次,组内就是一对格) */
const ANTI_KNIGHT_GROUPS = (() => {
  const moves = [
    [1, 2],
    [2, 1],
    [1, -2],
    [2, -1],
    [-1, 2],
    [-2, 1],
    [-1, -2],
    [-2, -1],
  ]
  const groups = []
  const seen = new Set()
  for (let r = 0; r < SIZE; r++) {
    for (let c = 0; c < SIZE; c++) {
      const a = r * SIZE + c
      for (const [dr, dc] of moves) {
        const nr = r + dr
        const nc = c + dc
        if (nr < 0 || nr >= SIZE || nc < 0 || nc >= SIZE) continue
        const b = nr * SIZE + nc
        const key = a < b ? a * CELLS + b : b * CELLS + a
        if (seen.has(key)) continue
        seen.add(key)
        groups.push([a, b])
      }
    }
  }
  return groups
})()

/** 反王步:斜对角相邻的两格不能同号(横竖相邻已由行/列约束保证) */
const ANTI_KING_GROUPS = (() => {
  const groups = []
  for (let r = 0; r < SIZE; r++) {
    for (let c = 0; c < SIZE; c++) {
      const a = r * SIZE + c
      for (const [dr, dc] of [
        [1, 1],
        [1, -1],
      ]) {
        const nr = r + dr
        const nc = c + dc
        if (nr < 0 || nr >= SIZE || nc < 0 || nc >= SIZE) continue
        groups.push([a, nr * SIZE + nc])
      }
    }
  }
  return groups
})()

/** 无象数独:象步隔一格(斜向相距 2 格)的两格不能同号 */
const ANTI_BISHOP_GROUPS = (() => {
  const groups = []
  for (let r = 0; r < SIZE; r++) {
    for (let c = 0; c < SIZE; c++) {
      const a = r * SIZE + c
      for (const [dr, dc] of [
        [2, 2],
        [2, -2],
      ]) {
        const nr = r + dr
        const nc = c + dc
        if (nr < 0 || nr >= SIZE || nc < 0 || nc >= SIZE) continue
        groups.push([a, nr * SIZE + nc])
      }
    }
  }
  return groups
})()

/**
 * 九龙数独:九条龙形额外区域(每条 9 格、连通),把 9×9 切成三行一带,
 * 每行按「起止点表」各切三段,使得同一带内三条龙一宽一窄互相咬合:
 * 第 1、3 带龙身自上而下收窄,第 2 带反向张开,盘面左右对称。
 */
const DRAGON_REGIONS = (() => {
  // 每行的三段起止列(三带共 9 行)
  const bounds = [
    [0, 3, 6, 9],
    [0, 4, 7, 9],
    [0, 2, 5, 9],
    [0, 3, 6, 9],
    [0, 2, 5, 9],
    [0, 4, 7, 9],
    [0, 3, 6, 9],
    [0, 4, 7, 9],
    [0, 2, 5, 9],
  ]
  const groups = Array.from({ length: 9 }, () => [])
  bounds.forEach((cuts, row) => {
    const band = Math.floor(row / 3)
    for (let k = 0; k < 3; k++) {
      for (let c = cuts[k]; c < cuts[k + 1]; c++) groups[band * 3 + k].push(row * SIZE + c)
    }
  })
  return groups
})()

/** 变体 → 行/列/宫之外的附加约束组 */
export function extraGroupsFor(variant) {
  if (variant === 'DIAGONAL') return DIAGONAL_GROUPS
  if (variant === 'WINDOW') return WINDOW_GROUPS
  if (variant === 'ANTI_KNIGHT') return ANTI_KNIGHT_GROUPS
  if (variant === 'ANTI_KING') return ANTI_KING_GROUPS
  if (variant === 'ANTI_BISHOP') return ANTI_BISHOP_GROUPS
  if (variant === 'DRAGON') return DRAGON_REGIONS
  return []
}

/** 相邻格关系码(连续/罗马数独把 144 条相邻边逐条标注,未标记边走否定约束) */
export const EDGE_REL = {
  CONSECUTIVE: 1,
  SUM_FIVE: 2,
  SUM_TEN: 3,
  NOT_CONSECUTIVE: 4,
  NO_SUM_FIVE_TEN: 5,
}

const pairKey = (a, b) => (a < b ? a * CELLS + b : b * CELLS + a)

/** 全部左右/上下相邻格(144 对),连续与罗马数独的线索都落在这批边上 */
const ADJACENT_PAIRS = (() => {
  const pairs = []
  for (let r = 0; r < SIZE; r++) {
    for (let c = 0; c < SIZE; c++) {
      const i = r * SIZE + c
      if (c < SIZE - 1) pairs.push([i, i + 1])
      if (r < SIZE - 1) pairs.push([i, i + SIZE])
    }
  }
  return pairs
})()

const ADJACENT_KEYS = new Set(ADJACENT_PAIRS.map(([a, b]) => pairKey(a, b)))

/** 不连续数独:144 条相邻边全部要求「差不为 1」,盘面不画任何标记 */
const NON_CONSECUTIVE_EDGES = ADJACENT_PAIRS.map(([a, b]) => ({ a, b, rel: EDGE_REL.NOT_CONSECUTIVE }))

/** 关系码 + 邻格数字 → 本格允许的候选掩码(位运算用) */
const EDGE_MASKS = (() => {
  const build = (accept) =>
    Array.from({ length: SIZE + 1 }, (_, w) => {
      if (!w) return 0
      let mask = 0
      for (let v = 1; v <= SIZE; v++) if (accept(v, w)) mask |= 1 << (v - 1)
      return mask
    })
  return {
    [EDGE_REL.CONSECUTIVE]: build((v, w) => Math.abs(v - w) === 1),
    [EDGE_REL.SUM_FIVE]: build((v, w) => v + w === 5),
    [EDGE_REL.SUM_TEN]: build((v, w) => v + w === 10),
    [EDGE_REL.NOT_CONSECUTIVE]: build((v, w) => Math.abs(v - w) !== 1),
    [EDGE_REL.NO_SUM_FIVE_TEN]: build((v, w) => v + w !== 5 && v + w !== 10),
  }
})()

/** 边约束 → 每格的相邻边 [{ other, rel }] */
function edgeAdjacency(edges) {
  const adj = new Array(CELLS).fill(null)
  const attach = (cell, other, rel) => {
    if (!adj[cell]) adj[cell] = []
    adj[cell].push({ other, rel })
  }
  for (const edge of edges) {
    attach(edge.a, edge.b, edge.rel)
    attach(edge.b, edge.a, edge.rel)
  }
  return adj
}

const edgeAllows = (rel, value, neighbor) => (EDGE_MASKS[rel][neighbor] & (1 << (value - 1))) !== 0

/** 依相邻边关系过滤候选:已填邻格给出本格允许的数字集合;空掩码表示这条支路已死 */
function edgeCandidatesAt(board, adj, cell, mask) {
  const rels = adj[cell]
  if (!rels) return mask
  for (const rel of rels) {
    const v = board[rel.other]
    if (!v) continue
    mask &= EDGE_MASKS[rel.rel][v]
    if (!mask) return 0
  }
  return mask
}

/** 序列线索(等差/回文) → 每格所属的线段及在线上的位置 [{ cells, pos }] */
function sequenceAdjacency(paths) {
  const adj = new Array(CELLS).fill(null)
  for (const cells of paths) {
    for (let pos = 0; pos < cells.length; pos++) {
      const cell = cells[pos]
      if (!adj[cell]) adj[cell] = []
      adj[cell].push({ cells, pos })
    }
  }
  return adj
}

/**
 * 等差候选过滤:线段内任意两个已填格都能定出公差,
 * 本格的值就被锁定;两个已填格推不出整数公差或不一致即判死。
 */
function arithCandidates(board, adj, cell, mask) {
  const entries = adj[cell]
  if (!entries) return mask
  for (const { cells, pos } of entries) {
    let refPos = -1
    let refVal = 0
    let step = null
    for (let q = 0; q < cells.length; q++) {
      if (q === pos) continue
      const v = board[cells[q]]
      if (!v) continue
      if (refPos < 0) {
        refPos = q
        refVal = v
        continue
      }
      const num = v - refVal
      const den = q - refPos
      if (num % den !== 0) return 0
      const d = num / den
      if (step === null) step = d
      else if (step !== d) return 0
    }
    if (step !== null) {
      const v = refVal + step * (pos - refPos)
      if (v < 1 || v > SIZE) return 0
      mask &= 1 << (v - 1)
      if (!mask) return 0
    }
  }
  return mask
}

/** 回文候选过滤:对称格已填时本格只能填同一个数字 */
function palindromeCandidates(board, adj, cell, mask) {
  const entries = adj[cell]
  if (!entries) return mask
  for (const { cells, pos } of entries) {
    const mirror = board[cells[cells.length - 1 - pos]]
    if (!mirror) continue
    mask &= 1 << (mirror - 1)
    if (!mask) return 0
  }
  return mask
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

/** 最少候选格(MRV),找不到空格时返回 null;无解返回 { dead: true }。filter 可继续裁剪候选掩码 */
function pickCell(board, rows, cols, units, unitOf, extras = null, membership = null, filter = null) {
  let best = -1
  let bestMask = 0
  let bestCount = SIZE + 1
  for (let i = 0; i < CELLS; i++) {
    if (board[i]) continue
    let mask = ALL & ~(rows[(i / SIZE) | 0] | cols[i % SIZE] | units[unitOf[i]])
    if (extras && membership[i].length) {
      for (const g of membership[i]) mask &= ~extras[g]
    }
    if (filter) {
      mask = filter(i, mask)
      if (!mask) return { dead: true }
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
 * opts.edges:相邻边关系(连续/罗马数独的 144 条边);
 * opts.nodeBudget:搜索节点预算,超过即放弃并返回 -1(用于杀手生成时廉价淘汰近唯一盘);
 * opts.skyscrapers:摩天楼四边线索 { top, bottom, left, right }(0 = 该侧无提示);
 * opts.arith:等差线段 [[cell...]](线段内数字成等差数列);
 * opts.palindromes:回文线段 [[cell...]](对称格数字相同)。
 * 返回 0 表示无解。
 */
export function countSolutions(input, limit = 2, opts = {}) {
  const board = Array.from(input)
  const extraGroups = opts.extraGroups || null
  const regions = opts.regions || null
  const cages = opts.cages || null
  const inequalities = opts.inequalities || null
  const parity = opts.parity || null
  const edges = opts.edges || null
  const skyscrapers = opts.skyscrapers || null
  const arith = opts.arith || null
  const palindromes = opts.palindromes || null
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
  const edgeAdj = edges ? edgeAdjacency(edges) : null
  if (edgeAdj) {
    for (let i = 0; i < CELLS; i++) {
      const rels = edgeAdj[i]
      if (!rels || !board[i]) continue
      for (const rel of rels) {
        const other = board[rel.other]
        if (other && !edgeAllows(rel.rel, board[i], other)) return 0
      }
    }
  }

  // 摩天楼/等差/回文的给定格先行校验(候选过滤只处理空格的落点)
  const skyLines = skyscrapers ? skylineClues(skyscrapers) : null
  if (skyLines && !skylineFeasible(board, skyLines)) return 0
  const arithAdj = arith ? sequenceAdjacency(arith) : null
  if (arith && arithViolationSet(board, arith).size) return 0
  const palindromeAdj = palindromes ? sequenceAdjacency(palindromes) : null
  if (palindromes && palindromeViolationSet(board, palindromes).size) return 0

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

  // 依相邻边关系(连续/罗马/不连续)剪枝:已填邻格给出本格允许的数字集合
  const edgeCandidates = (cell, mask) => edgeCandidatesAt(board, edgeAdj, cell, mask)

  let count = 0
  let nodes = 0
  let aborted = false

  const search = () => {
    if (aborted) return
    if (++nodes > budget) {
      aborted = true
      return
    }
    // 摩天楼:每条已部分填写的行/列都按可见楼层数剪枝
    if (skyLines && !skylineFeasible(board, skyLines)) return
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
      if (edgeAdj) mask = edgeCandidates(i, mask)
      if (arithAdj) mask = arithCandidates(board, arithAdj, i, mask)
      if (palindromeAdj) mask = palindromeCandidates(board, palindromeAdj, i, mask)
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
 * nodeBudget:搜索节点预算,超限返回带空格的盘面(调用方据此判定失败);
 * edges:相邻边关系(不连续数独靠它约束终盘)。
 */
export function generateSolution(random = Math.random, extraGroups = null, regions = null, nodeBudget = Infinity, edges = null) {
  const board = new Array(CELLS).fill(0)
  const rows = new Array(SIZE).fill(0)
  const cols = new Array(SIZE).fill(0)
  const units = new Array(SIZE).fill(0)
  const unitOf = regions ? unitIndexOf(regions) : BOX_INDEX
  const membership = extraGroups ? extraMembership(extraGroups) : null
  const extras = extraGroups ? extraGroups.map(() => 0) : null
  const edgeAdj = edges ? edgeAdjacency(edges) : null
  const filter = edgeAdj ? (cell, mask) => edgeCandidatesAt(board, edgeAdj, cell, mask) : null
  let nodes = 0
  let aborted = false

  const fill = () => {
    if (aborted) return false
    if (++nodes > nodeBudget) {
      aborted = true
      return false
    }
    const pick = pickCell(board, rows, cols, units, unitOf, extras, membership, filter)
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
  if (variant === 'THERMO') return generateThermo(difficulty, random)
  if (variant === 'CONSECUTIVE' || variant === 'XV') return generateEdgeVariant(difficulty, random, variant)
  if (variant === 'NON_CONSECUTIVE') return generateNonConsecutive(difficulty, random)
  if (variant === 'SKYSCRAPER') return generateSkyscraper(difficulty, random)
  if (variant === 'ARITHMETIC') return generateArithmetic(difficulty, random)
  if (variant === 'PALINDROME') return generatePalindrome(difficulty, random)
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
    edges: null,
    thermos: null,
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
  return {
    variant: 'GREATER',
    puzzle,
    solution,
    cages: null,
    regions: null,
    inequalities,
    parity: null,
    edges: null,
    thermos: null,
    blanks,
    difficulty,
  }
}

/** 连续/罗马数独:按终盘给 144 条相邻边逐条定关系(未标记边走否定约束) */
function classifyEdges(solution, variant) {
  const edges = []
  for (const [a, b] of ADJACENT_PAIRS) {
    const sum = solution[a] + solution[b]
    let rel
    if (variant === 'CONSECUTIVE') {
      rel = Math.abs(solution[a] - solution[b]) === 1 ? EDGE_REL.CONSECUTIVE : EDGE_REL.NOT_CONSECUTIVE
    } else {
      rel = sum === 5 ? EDGE_REL.SUM_FIVE : sum === 10 ? EDGE_REL.SUM_TEN : EDGE_REL.NO_SUM_FIVE_TEN
    }
    edges.push({ a, b, rel })
  }
  return edges
}

function generateEdgeVariant(difficulty, random, variant) {
  const solution = generateSolution(random)
  const edges = classifyEdges(solution, variant)
  const targetBlanks = DIFFICULTIES[difficulty] ?? DIFFICULTIES.MEDIUM
  // 边关系是额外线索,挖空口径与标准数独一致
  const { puzzle, blanks } = digHoles(solution, targetBlanks, random, { edges })
  return {
    variant,
    puzzle,
    solution,
    cages: null,
    regions: null,
    inequalities: null,
    parity: null,
    edges,
    thermos: null,
    blanks,
    difficulty,
  }
}

/**
 * 不连续数独:相邻格数字之差不能是 1(全盘不画短线)。
 * 终盘必须本身满足该约束,所以用带边过滤的回溯生成,挖空同样按 144 条边校验唯一解。
 */
function generateNonConsecutive(difficulty, random) {
  const targetBlanks = DIFFICULTIES[difficulty] ?? DIFFICULTIES.MEDIUM
  let solution = null
  for (let attempt = 0; attempt < 3 && !solution; attempt++) {
    const candidate = generateSolution(random, null, null, 200000, NON_CONSECUTIVE_EDGES)
    if (candidate.every((v) => v)) solution = candidate
  }
  // 预算内没搜到就放开预算再搜一次(可行盘一定存在,只是回溯路径长短不定)
  if (!solution) solution = generateSolution(random, null, null, Infinity, NON_CONSECUTIVE_EDGES)
  const { puzzle, blanks } = digHoles(solution, targetBlanks, random, { edges: NON_CONSECUTIVE_EDGES })
  return {
    variant: 'NON_CONSECUTIVE',
    puzzle,
    solution,
    cages: null,
    regions: null,
    inequalities: null,
    parity: null,
    edges: NON_CONSECUTIVE_EDGES,
    thermos: null,
    blanks,
    difficulty,
  }
}

/**
 * 温度计:从随机未占用格出发,只往数字更大的相邻格走,边走边加长(最多 6 格)。
 * 每条温度计独占格位,渲染时不会互相压线;条数按难度给。
 */
function buildThermometers(solution, random, count) {
  const used = new Set()
  const thermos = []
  let guard = count * 48
  while (thermos.length < count && guard-- > 0) {
    const start = Math.floor(random() * CELLS)
    if (used.has(start)) continue
    const path = [start]
    let cur = start
    while (path.length < THERMO_MAX_LEN) {
      const options = neighborsOf(cur).filter((nb) => !used.has(nb) && !path.includes(nb) && solution[nb] > solution[cur])
      if (!options.length) break
      // 优先走较小的后继,避免一步踩到 9 让温度计过早结束
      options.sort((a, b) => solution[a] - solution[b])
      const pick = options[Math.floor(random() * Math.min(2, options.length))]
      path.push(pick)
      cur = pick
    }
    if (path.length < 2) continue
    for (const cell of path) used.add(cell)
    thermos.push(path)
  }
  // 兜底:至少给出一条递增的相邻对
  if (!thermos.length) {
    for (const [a, b] of ADJACENT_PAIRS) {
      if (solution[a] < solution[b]) {
        thermos.push([a, b])
        break
      }
      if (solution[b] < solution[a]) {
        thermos.push([b, a])
        break
      }
    }
  }
  return thermos
}

function generateThermo(difficulty, random) {
  const solution = generateSolution(random)
  const thermos = buildThermometers(solution, random, THERMO_COUNTS[difficulty] ?? THERMO_COUNTS.MEDIUM)
  // 求解/判定复用不等式口径:温度计相邻两格按线路方向给出「前 < 后」
  const inequalities = []
  for (const path of thermos) {
    for (let i = 1; i < path.length; i++) inequalities.push([path[i - 1], path[i]])
  }
  const targetBlanks = DIFFICULTIES[difficulty] ?? DIFFICULTIES.MEDIUM
  const { puzzle, blanks } = digHoles(solution, targetBlanks, random, { inequalities })
  return {
    variant: 'THERMO',
    puzzle,
    solution,
    cages: null,
    regions: null,
    inequalities,
    parity: null,
    edges: null,
    thermos,
    blanks,
    difficulty,
  }
}

// ---------- 摩天楼数独 ----------

/**
 * 从一端看过去的可见楼层数:只有比前面所有数字都大的才露得出来。
 * 行/列两个方向共用同一口径,线索生成与判定都走这里。
 */
export function visibilityCount(values) {
  let tallest = 0
  let seen = 0
  for (const v of values) {
    if (v > tallest) {
      tallest = v
      seen += 1
    }
  }
  return seen
}

/** 摩天楼线索 → 四边的逐条线(0 表示该侧不给提示,那条线不参与约束) */
function skylineClues(skyscrapers) {
  const lines = []
  for (let k = 0; k < SIZE; k++) {
    const col = colGroups[k]
    const row = rowGroups[k]
    if (skyscrapers.top?.[k]) lines.push({ cells: col, clue: skyscrapers.top[k] })
    if (skyscrapers.bottom?.[k]) lines.push({ cells: col.slice().reverse(), clue: skyscrapers.bottom[k] })
    if (skyscrapers.left?.[k]) lines.push({ cells: row, clue: skyscrapers.left[k] })
    if (skyscrapers.right?.[k]) lines.push({ cells: row.slice().reverse(), clue: skyscrapers.right[k] })
  }
  return lines
}

/**
 * 部分盘面的摩天楼剪枝:只数「从该端连续已填的前缀」,
 * 前缀可见数不能超过提示,剩下的空格每格最多再露一层。
 */
function skylineFeasible(board, lines) {
  for (const line of lines) {
    const cells = line.cells
    let tallest = 0
    let seen = 0
    let i = 0
    while (i < cells.length && board[cells[i]]) {
      const v = board[cells[i]]
      if (v > tallest) {
        tallest = v
        seen += 1
      }
      i += 1
    }
    const rest = cells.length - i
    if (seen > line.clue || seen + rest < line.clue) return false
  }
  return true
}

/** 按终盘算出四边可见数,再按难度随机隐藏若干条(同一行/列最多藏一侧) */
function buildSkyscrapers(solution, random, difficulty) {
  const clues = {
    top: colGroups.map((cells) => visibilityCount(cells.map((cell) => solution[cell]))),
    bottom: colGroups.map((cells) =>
      visibilityCount(
        cells
          .slice()
          .reverse()
          .map((cell) => solution[cell]),
      ),
    ),
    left: rowGroups.map((cells) => visibilityCount(cells.map((cell) => solution[cell]))),
    right: rowGroups.map((cells) =>
      visibilityCount(
        cells
          .slice()
          .reverse()
          .map((cell) => solution[cell]),
      ),
    ),
  }
  const hidden = SKYSCRAPER_HIDDEN[difficulty] ?? SKYSCRAPER_HIDDEN.MEDIUM
  if (hidden <= 0) return clues
  const slots = []
  for (let k = 0; k < SIZE; k++) {
    slots.push({ side: 'top', index: k, line: `col${k}` })
    slots.push({ side: 'bottom', index: k, line: `col${k}` })
    slots.push({ side: 'left', index: k, line: `row${k}` })
    slots.push({ side: 'right', index: k, line: `row${k}` })
  }
  shuffle(slots, random)
  const hiddenLines = new Set()
  let done = 0
  for (const slot of slots) {
    if (done >= hidden) break
    if (hiddenLines.has(slot.line)) continue
    hiddenLines.add(slot.line)
    clues[slot.side][slot.index] = 0
    done += 1
  }
  return clues
}

function generateSkyscraper(difficulty, random) {
  const solution = generateSolution(random)
  const skyscrapers = buildSkyscrapers(solution, random, difficulty)
  const targetBlanks = DIFFICULTIES[difficulty] ?? DIFFICULTIES.MEDIUM
  const { puzzle, blanks } = digHoles(solution, targetBlanks, random, { skyscrapers })
  return {
    variant: 'SKYSCRAPER',
    puzzle,
    solution,
    cages: null,
    regions: null,
    inequalities: null,
    parity: null,
    edges: null,
    thermos: null,
    skyscrapers,
    arith: null,
    palindromes: null,
    blanks,
    difficulty,
  }
}

// ---------- 等差数独 ----------

/** 等差数列判定:至少 3 项、公差非 0、相邻差恒等 */
function isArithmeticSequence(values) {
  if (values.length < 3) return false
  const step = values[1] - values[0]
  if (!step) return false
  for (let i = 2; i < values.length; i++) if (values[i] - values[i - 1] !== step) return false
  return true
}

/** 终盘上所有「同行/同列连续格刚好成等差」的线段(3~5 格),这是等差数独的候选线索 */
function collectArithPaths(solution) {
  const paths = []
  const consider = (cells) => {
    if (isArithmeticSequence(cells.map((cell) => solution[cell]))) paths.push(cells)
  }
  for (let r = 0; r < SIZE; r++) {
    for (let c = 0; c < SIZE; c++) {
      for (let len = ARITH_MIN_LEN; len <= ARITH_MAX_LEN; len++) {
        if (c + len <= SIZE) consider(Array.from({ length: len }, (_, k) => r * SIZE + c + k))
        if (r + len <= SIZE) consider(Array.from({ length: len }, (_, k) => (r + k) * SIZE + c))
      }
    }
  }
  return paths
}

/** 从候选线段里随机挑出互不重叠的若干条(长线优先,长短交替) */
function pickNonOverlappingPaths(candidates, count, random) {
  const groups = new Map()
  for (const path of candidates) {
    if (!groups.has(path.length)) groups.set(path.length, [])
    groups.get(path.length).push(path)
  }
  const queues = [...groups.entries()].sort((a, b) => b[0] - a[0]).map(([, list]) => shuffle(list.slice(), random))
  const picked = []
  const used = new Set()
  let guard = 0
  while (picked.length < count && guard < 10000) {
    guard += 1
    let progressed = false
    for (const queue of queues) {
      if (picked.length >= count) break
      while (queue.length) {
        const path = queue.pop()
        if (path.some((cell) => used.has(cell))) continue
        for (const cell of path) used.add(cell)
        picked.push(path)
        progressed = true
        break
      }
    }
    if (!progressed) break
  }
  return picked
}

/** 已填格违反等差数列时返回涉及的格子(空盘或只有一个参照时视为未违反) */
function arithViolationSet(board, paths) {
  const bad = new Set()
  for (const path of paths) {
    let refPos = -1
    let refVal = 0
    let step = null
    let broken = false
    for (let pos = 0; pos < path.length; pos++) {
      const v = board[path[pos]]
      if (!v) continue
      if (refPos < 0) {
        refPos = pos
        refVal = v
        continue
      }
      const num = v - refVal
      const den = pos - refPos
      if (num % den !== 0) {
        broken = true
        break
      }
      const d = num / den
      if (step === null) step = d
      else if (step !== d) {
        broken = true
        break
      }
    }
    if (broken) for (const cell of path) if (board[cell]) bad.add(cell)
  }
  return bad
}

function generateArithmetic(difficulty, random) {
  const solution = generateSolution(random)
  const arith = pickNonOverlappingPaths(collectArithPaths(solution), ARITH_COUNTS[difficulty] ?? ARITH_COUNTS.MEDIUM, random)
  const targetBlanks = DIFFICULTIES[difficulty] ?? DIFFICULTIES.MEDIUM
  const { puzzle, blanks } = digHoles(solution, targetBlanks, random, { arith })
  return {
    variant: 'ARITHMETIC',
    puzzle,
    solution,
    cages: null,
    regions: null,
    inequalities: null,
    parity: null,
    edges: null,
    thermos: null,
    skyscrapers: null,
    arith,
    palindromes: null,
    blanks,
    difficulty,
  }
}

// ---------- 回文数独 ----------

/** 回文序列判定:正读反读一致 */
function isPalindromeSequence(values) {
  for (let i = 0, j = values.length - 1; i < j; i++, j--) if (values[i] !== values[j]) return false
  return true
}

/** 同一段回文线正反走是两条路径,用字典序较小的字符串去重 */
function palindromeKey(path) {
  const forward = path.join(',')
  const backward = path.slice().reverse().join(',')
  return forward < backward ? forward : backward
}

/**
 * 终盘上所有「已构成回文」的折线(3 格 / 5 格)。
 * 偶数长度的回文线必然有一对相邻格同号(行列宫冲突),所以只用奇数长度。
 */
function collectPalindromePaths(solution) {
  const seen = new Set()
  const paths = []
  const consider = (path) => {
    if (!isPalindromeSequence(path.map((cell) => solution[cell]))) return
    const key = palindromeKey(path)
    if (seen.has(key)) return
    seen.add(key)
    paths.push(path)
  }
  // 3 格:中心格 + 两个相邻格
  for (let center = 0; center < CELLS; center++) {
    const nbs = neighborsOf(center)
    for (const a of nbs) {
      for (const c of nbs) if (a !== c) consider([a, center, c])
    }
  }
  // 5 格:从任意起点做 4 步不重复游走
  const walk = (path) => {
    if (path.length === PAL_MAX_LEN) {
      consider(path.slice())
      return
    }
    const last = path[path.length - 1]
    for (const nb of neighborsOf(last)) {
      if (path.includes(nb)) continue
      path.push(nb)
      walk(path)
      path.pop()
    }
  }
  for (let start = 0; start < CELLS; start++) walk([start])
  return paths
}

/** 已填格违反回文关系时返回涉及的格子 */
function palindromeViolationSet(board, paths) {
  const bad = new Set()
  for (const path of paths) {
    for (let i = 0, j = path.length - 1; i < j; i++, j--) {
      const a = board[path[i]]
      const b = board[path[j]]
      if (a && b && a !== b) {
        bad.add(path[i])
        bad.add(path[j])
      }
    }
  }
  return bad
}

function generatePalindrome(difficulty, random) {
  const solution = generateSolution(random)
  const palindromes = pickNonOverlappingPaths(
    collectPalindromePaths(solution),
    PALINDROME_COUNTS[difficulty] ?? PALINDROME_COUNTS.MEDIUM,
    random,
  )
  const targetBlanks = DIFFICULTIES[difficulty] ?? DIFFICULTIES.MEDIUM
  const { puzzle, blanks } = digHoles(solution, targetBlanks, random, { palindromes })
  return {
    variant: 'PALINDROME',
    puzzle,
    solution,
    cages: null,
    regions: null,
    inequalities: null,
    parity: null,
    edges: null,
    thermos: null,
    skyscrapers: null,
    arith: null,
    palindromes,
    blanks,
    difficulty,
  }
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
 * extraGroups:附加组(对角线/窗口/杀手笼/反马步/反王步);regions:替代九宫的不规则区;
 * inequalities:不等式对(已填但方向不符时把两格都标冲突);
 * edges:相邻边关系(连续/罗马,已填但不满足关系时把两格都标冲突)。
 * 第二个参数兼容旧签名(直接传附加组数组)。
 */
export function findConflicts(board, opts = null) {
  const extraGroups = Array.isArray(opts) ? opts : opts?.extraGroups || null
  const regions = Array.isArray(opts) ? null : opts?.regions || null
  const inequalities = Array.isArray(opts) ? null : opts?.inequalities || null
  const edges = Array.isArray(opts) ? null : opts?.edges || null
  const skyscrapers = Array.isArray(opts) ? null : opts?.skyscrapers || null
  const arith = Array.isArray(opts) ? null : opts?.arith || null
  const palindromes = Array.isArray(opts) ? null : opts?.palindromes || null
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
  if (edges) {
    for (const edge of edges) {
      const { a, b } = edge
      if (board[a] && board[b] && !edgeAllows(edge.rel, board[a], board[b])) {
        conflicts.add(a)
        conflicts.add(b)
      }
    }
  }
  // 摩天楼:整条行/列填满但可见楼层数与线索不符时,整条线一起标冲突
  if (skyscrapers) {
    for (const line of skylineClues(skyscrapers)) {
      if (!line.cells.every((cell) => board[cell])) continue
      if (visibilityCount(line.cells.map((cell) => board[cell])) === line.clue) continue
      for (const cell of line.cells) conflicts.add(cell)
    }
  }
  if (arith) for (const cell of arithViolationSet(board, arith)) conflicts.add(cell)
  if (palindromes) for (const cell of palindromeViolationSet(board, palindromes)) conflicts.add(cell)
  return conflicts
}

/**
 * 全部填满且无冲突;opts 还要满足附加约束:
 * cages 各笼数字和等于标注、inequalities 比较方向正确、parity 奇偶与标记一致、
 * edges 相邻边关系成立。
 */
export function isSolved(board, opts = {}) {
  for (let i = 0; i < CELLS; i++) if (!board[i]) return false
  if (findConflicts(board, { extraGroups: opts.extraGroups || null, regions: opts.regions || null }).size !== 0) {
    return false
  }
  if (opts.edges) {
    for (const edge of opts.edges) {
      if (!edgeAllows(edge.rel, board[edge.a], board[edge.b])) return false
    }
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
  if (opts.skyscrapers) {
    for (const line of skylineClues(opts.skyscrapers)) {
      if (visibilityCount(line.cells.map((cell) => board[cell])) !== line.clue) return false
    }
  }
  if (opts.arith && arithViolationSet(board, opts.arith).size) return false
  if (opts.palindromes && palindromeViolationSet(board, opts.palindromes).size) return false
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

/**
 * 相邻边关系校验(连续/罗马读档用):恰好覆盖全部 144 条相邻边、每对只出现一次、
 * 关系码属于 EDGE_REL。
 */
export function isValidEdges(edges) {
  if (!Array.isArray(edges) || edges.length !== ADJACENT_PAIRS.length) return false
  const seen = new Set()
  for (const edge of edges) {
    if (!edge || !Number.isInteger(edge.a) || !Number.isInteger(edge.b)) return false
    if (!Number.isInteger(edge.rel) || !EDGE_MASKS[edge.rel]) return false
    const { a, b } = edge
    if (a < 0 || a >= CELLS || b < 0 || b >= CELLS || a === b) return false
    const key = pairKey(a, b)
    if (!ADJACENT_KEYS.has(key) || seen.has(key)) return false
    seen.add(key)
  }
  return seen.size === ADJACENT_PAIRS.length
}

/** 温度计结构校验:非空、每条至少 2 格、同行相邻/上下相接、格位不重叠 */
export function isValidThermos(thermos) {
  if (!Array.isArray(thermos) || !thermos.length) return false
  const used = new Set()
  for (const path of thermos) {
    if (!Array.isArray(path) || path.length < 2) return false
    for (let i = 0; i < path.length; i++) {
      const cell = path[i]
      if (!Number.isInteger(cell) || cell < 0 || cell >= CELLS || used.has(cell)) return false
      used.add(cell)
      if (i > 0 && !ADJACENT_KEYS.has(pairKey(path[i - 1], cell))) return false
    }
  }
  return true
}

/** 摩天楼线索结构校验:四边各 9 个数(0 = 该侧无提示),至少给 4 条提示 */
export function isValidSkyscrapers(skyscrapers) {
  if (!skyscrapers || typeof skyscrapers !== 'object') return false
  let clues = 0
  for (const side of SKY_SIDES) {
    const list = skyscrapers[side]
    if (!Array.isArray(list) || list.length !== SIZE) return false
    for (const v of list) {
      if (!Number.isInteger(v) || v < 0 || v > SIZE) return false
      if (v) clues += 1
    }
  }
  return clues >= 4
}

/**
 * 线段线索结构校验(等差/回文读档用):每条线长度在区间内、格位不越界不重叠、
 * 相邻两格必须上下左右相接;等差线还要求是同一条直线。
 */
function isValidSequencePaths(paths, minLen, maxLen, straightOnly) {
  if (!Array.isArray(paths) || !paths.length) return false
  const used = new Set()
  for (const path of paths) {
    if (!Array.isArray(path) || path.length < minLen || path.length > maxLen) return false
    for (let i = 0; i < path.length; i++) {
      const cell = path[i]
      if (!Number.isInteger(cell) || cell < 0 || cell >= CELLS || used.has(cell)) return false
      used.add(cell)
      if (i > 0 && !ADJACENT_KEYS.has(pairKey(path[i - 1], cell))) return false
    }
    if (!straightOnly) continue
    const dr = Math.floor(path[1] / SIZE) - Math.floor(path[0] / SIZE)
    const dc = (path[1] % SIZE) - (path[0] % SIZE)
    for (let i = 2; i < path.length; i++) {
      if (Math.floor(path[i] / SIZE) - Math.floor(path[i - 1] / SIZE) !== dr) return false
      if ((path[i] % SIZE) - (path[i - 1] % SIZE) !== dc) return false
    }
  }
  return true
}

/** 等差线结构校验:3~5 格、连续同一直线、互不重叠 */
export function isValidArithPaths(paths) {
  return isValidSequencePaths(paths, ARITH_MIN_LEN, ARITH_MAX_LEN, true)
}

/** 回文线结构校验:3~5 格、连续折线、互不重叠 */
export function isValidPalindromePaths(paths) {
  return isValidSequencePaths(paths, PAL_MIN_LEN, PAL_MAX_LEN, false)
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
