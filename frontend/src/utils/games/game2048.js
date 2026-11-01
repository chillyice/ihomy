// 2048 纯逻辑:方块用 { id, value, row, col } 表示(id 稳定,页面靠它做滑动/合并动画)。
// 移动 = 每行/每列向一个方向紧凑 + 相邻同值合并一次;所有判定都在这里,组件只渲染与收键盘/手势。
// 棋盘边长参数化(4~8 列模式):各函数带 size 参数,缺省 4,旧调用与旧存档校验不受影响。

export const SIZE = 4
export const CELLS = SIZE * SIZE
export const BOARD_SIZES = [4, 5, 6, 7, 8]
export const WIN_VALUE = 2048

/** 新方块进场的数字分布:2 占九成、4 占一成 */
const TWO_RATE = 0.9

const nextId = (tiles) => tiles.reduce((max, t) => Math.max(max, t.id), 0) + 1

/** 4×4 数字矩阵 → 方块数组(测试/读档用) */
export function tilesFromGrid(grid) {
  const tiles = []
  grid.forEach((row, r) =>
    row.forEach((value, c) => {
      if (value) tiles.push({ id: tiles.length + 1, value, row: r, col: c })
    }),
  )
  return tiles
}

/** 方块数组 → 数字矩阵(size×size,测试/调试用) */
export function gridOf(tiles, size = SIZE) {
  const grid = Array.from({ length: size }, () => new Array(size).fill(0))
  for (const t of tiles) grid[t.row][t.col] = t.value
  return grid
}

/**
 * 朝一个方向移动一次。
 * 返回 { tiles, gained, moved, mergedIds }:新方块数组(未落新方块)、本步得分、是否发生位移或合并、本次合并出的方块 id。
 */
export function move(tiles, direction, size = SIZE) {
  const horizontal = direction === 'left' || direction === 'right'
  const ascending = direction === 'left' || direction === 'up'
  const lineAxis = horizontal ? 'row' : 'col'
  const travelAxis = horizontal ? 'col' : 'row'

  const lines = new Map()
  for (const tile of tiles) {
    const key = tile[lineAxis]
    if (!lines.has(key)) lines.set(key, [])
    lines.get(key).push(tile)
  }

  const result = []
  const mergedIds = []
  let gained = 0
  let moved = false

  for (const [key, lineTiles] of lines) {
    lineTiles.sort((a, b) => (ascending ? a[travelAxis] - b[travelAxis] : b[travelAxis] - a[travelAxis]))
    const slots = []
    for (const tile of lineTiles) {
      const last = slots[slots.length - 1]
      // merged 标记保证「一次移动里每块只合并一次」:[4,4,8,8] 左移应得 [8,16]
      if (last && !last.merged && last.value === tile.value) {
        last.value *= 2
        last.merged = true
        gained += last.value
        mergedIds.push(last.id)
        moved = true
      } else {
        slots.push({ id: tile.id, value: tile.value, merged: false, from: tile })
      }
    }
    slots.forEach((slot, position) => {
      const index = ascending ? position : size - 1 - position
      const tile = { id: slot.id, value: slot.value, merged: slot.merged }
      tile[lineAxis] = key
      tile[travelAxis] = index
      if (slot.from[lineAxis] !== key || slot.from[travelAxis] !== index) moved = true
      result.push(tile)
    })
  }

  return { tiles: result, gained, moved, mergedIds }
}

/** 在随机空格落一个新方块;盘面已满返回 null */
export function spawnTile(tiles, random = Math.random, size = SIZE) {
  const occupied = new Set(tiles.map((t) => t.row * size + t.col))
  const empty = []
  for (let i = 0; i < size * size; i++) if (!occupied.has(i)) empty.push(i)
  if (!empty.length) return null
  const cell = empty[Math.min(empty.length - 1, Math.floor(random() * empty.length))]
  return {
    id: nextId(tiles),
    value: random() < TWO_RATE ? 2 : 4,
    row: (cell / size) | 0,
    col: cell % size,
  }
}

/** 开局:两个随机新方块 */
export function createGame(random = Math.random, size = SIZE) {
  const tiles = []
  tiles.push(spawnTile(tiles, random, size))
  tiles.push(spawnTile(tiles, random, size))
  return tiles
}

/** 已达成 2048(或更高) */
export function hasWon(tiles) {
  return tiles.some((t) => t.value >= WIN_VALUE)
}

/** 满盘且四邻无同值 = 无路可走 */
export function isGameOver(tiles, size = SIZE) {
  if (tiles.length < size * size) return false
  const grid = gridOf(tiles, size)
  for (let r = 0; r < size; r++) {
    for (let c = 0; c < size; c++) {
      const v = grid[r][c]
      if (!v) return false
      if (c + 1 < size && v === grid[r][c + 1]) return false
      if (r + 1 < size && v === grid[r + 1][c]) return false
    }
  }
  return true
}

/** 读档校验:方块字段合法且不重叠、不越界 */
export function isValidTiles(tiles, size = SIZE) {
  if (!Array.isArray(tiles) || tiles.length > size * size) return false
  const seen = new Set()
  const ids = new Set()
  for (const t of tiles) {
    if (!t || !Number.isInteger(t.id) || !Number.isInteger(t.value) || t.value < 2) return false
    if (ids.has(t.id)) return false
    ids.add(t.id)
    const isPowerOfTwo = (t.value & (t.value - 1)) === 0
    if (!isPowerOfTwo) return false
    if (!Number.isInteger(t.row) || !Number.isInteger(t.col)) return false
    if (t.row < 0 || t.row >= size || t.col < 0 || t.col >= size) return false
    const cell = t.row * size + t.col
    if (seen.has(cell)) return false
    seen.add(cell)
  }
  return true
}
