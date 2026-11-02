// 宠物连连看棋盘内核:发牌、连线判定(最多两拐 + 可绕外圈)、消除后的牌位聚拢。
// 纯函数,不碰 Vue / DOM;页面只负责渲染与动画。第 3~9 关按关卡名做不同的牌位移动,
// 同排/同列两张牌一起被移走时**必须先移后一张**(见 moveOrder 注释),否则第二张的
// 目标位已被第一张搬空,会连带吃掉相邻的无关宠物(第 5/8 关的历史缺陷)。

export const PETLINK_ROWS = 10
export const PETLINK_COLS = 14
export const PETLINK_CELLS = PETLINK_ROWS * PETLINK_COLS
export const PETLINK_MAX_LEVEL = 9

/** 每关用到的宠物种类数:第 1 关 30、第 2 关 35、第 3 关起 40 */
export const typeCountOf = (level) => (level === 1 ? 30 : level === 2 ? 35 : 40)

/** 发牌:140 张 = 70 对,前 typeCount 种各一对,其余随机补足后整体洗牌;id 从 startId+1 连续编号 */
export function createPetLinkBoard(level, startId = 0, rand = Math.random) {
  const typeCount = typeCountOf(level)
  const arr = []
  for (let i = 1; i <= typeCount; i++) arr.push(i)
  for (let i = typeCount + 1; i <= 70; i++) arr.push(Math.floor(rand() * typeCount) + 1)
  const pairs = [...arr, ...arr]
  for (let k = pairs.length - 1; k > 0; k--) {
    const m = Math.floor(rand() * (k + 1))
    ;[pairs[k], pairs[m]] = [pairs[m], pairs[k]]
  }
  let id = startId
  const board = pairs.map((type) => ({ id: ++id, type }))
  return { board, maxId: id }
}

/** 取格子类型,越界与空格都返回 0 */
export function cellAt(board, r, c) {
  if (r < 0 || r >= PETLINK_ROWS || c < 0 || c >= PETLINK_COLS) return 0
  const t = board[r * PETLINK_COLS + c]
  return t ? t.type : 0
}

function walkable(board, r, c) {
  return cellAt(board, r, c) === 0
}
function horizClear(board, r, c1, c2) {
  const lo = Math.min(c1, c2)
  const hi = Math.max(c1, c2)
  for (let c = lo + 1; c < hi; c++) if (cellAt(board, r, c) !== 0) return false
  return true
}
function vertClear(board, c, r1, r2) {
  const lo = Math.min(r1, r2)
  const hi = Math.max(r1, r2)
  for (let r = lo + 1; r < hi; r++) if (cellAt(board, r, c) !== 0) return false
  return true
}

/** 连线判定(移植原版 findlu):同型且连线不超过两次转弯;返回折点数组或 null */
export function findPetLinkPath(board, r1, c1, r2, c2) {
  if (r1 === r2 && c1 === c2) return null
  const a = cellAt(board, r1, c1)
  const b = cellAt(board, r2, c2)
  if (a === 0 || b === 0 || a !== b) return null
  if (r1 === r2 && horizClear(board, r1, c1, c2))
    return [
      [r1, c1],
      [r2, c2],
    ]
  if (c1 === c2 && vertClear(board, c1, r1, r2))
    return [
      [r1, c1],
      [r2, c2],
    ]
  if (walkable(board, r1, c2) && horizClear(board, r1, c1, c2) && vertClear(board, c2, r1, r2))
    return [
      [r1, c1],
      [r1, c2],
      [r2, c2],
    ]
  if (walkable(board, r2, c1) && vertClear(board, c1, r1, r2) && horizClear(board, r2, c1, c2))
    return [
      [r1, c1],
      [r2, c1],
      [r2, c2],
    ]
  for (let c = -1; c <= PETLINK_COLS; c++) {
    if (c === c1 || c === c2) continue
    if (
      walkable(board, r1, c) &&
      walkable(board, r2, c) &&
      horizClear(board, r1, c1, c) &&
      horizClear(board, r2, c2, c) &&
      vertClear(board, c, r1, r2)
    )
      return [
        [r1, c1],
        [r1, c],
        [r2, c],
        [r2, c2],
      ]
  }
  for (let r = -1; r <= PETLINK_ROWS; r++) {
    if (r === r1 || r === r2) continue
    if (
      walkable(board, r, c1) &&
      walkable(board, r, c2) &&
      vertClear(board, c1, r1, r) &&
      vertClear(board, c2, r2, r) &&
      horizClear(board, r, c1, c2)
    )
      return [
        [r1, c1],
        [r, c1],
        [r, c2],
        [r2, c2],
      ]
  }
  return null
}

/** 找任意一对可消的牌,返回 [i, j] 或 null */
export function findPetLinkPair(board) {
  const groups = {}
  board.forEach((t, i) => {
    if (t) (groups[t.type] ||= []).push(i)
  })
  for (const v in groups) {
    const arr = groups[v]
    for (let a = 0; a < arr.length; a++) {
      for (let b = a + 1; b < arr.length; b++) {
        const i = arr[a]
        const j = arr[b]
        if (findPetLinkPath(board, Math.floor(i / PETLINK_COLS), i % PETLINK_COLS, Math.floor(j / PETLINK_COLS), j % PETLINK_COLS))
          return [i, j]
      }
    }
  }
  return null
}

/** 把剩余牌原地洗牌(空格位不变);返回 board 便于链式使用 */
export function shufflePetLinkBoard(board, rand = Math.random) {
  const remaining = board.filter((v) => v !== null)
  for (let k = remaining.length - 1; k > 0; k--) {
    const m = Math.floor(rand() * (k + 1))
    ;[remaining[k], remaining[m]] = [remaining[m], remaining[k]]
  }
  let p = 0
  for (let i = 0; i < board.length; i++) if (board[i] !== null) board[i] = remaining[p++]
  return board
}

// ---------- 消除后的牌位移动(复刻原版 the_pass_nm 分支) ----------
// 第 1~2 关仅消除;第 3~9 关消除后按关卡名做不同的牌位聚拢:
// 3 上下分离(上半上移/下半下移)、4 左右分离(左半左移/右半右移)、5 向左看齐(整行左移)、
// 6 地心引力(整列下移)、7 飘向天空(整列上移)、8 向右看齐(整行右移)、9 中央集中(向中心聚拢)。
//
// moveOrder:两张牌落在同一个会影响彼此位次的通道(同行/同列/同半区)时,必须**后一张先移**。
// 先移后一张,前一张的坐标才不被搬动;反过来会把前一张的目标位提前腾空,
// 第二次移动就去吃它旁边的无关宠物 —— 第 5/8 关曾因此每次连消多消一张、最后只剩单牌。

/** 消除 a、b 两张牌并做该关的牌位聚拢(原地修改 board) */
export function removePetLinkPair(board, level, a, b) {
  if (level <= 2) {
    board[a] = null
    board[b] = null
    return
  }
  const ra = Math.floor(a / PETLINK_COLS)
  const ca = a % PETLINK_COLS
  const rb = Math.floor(b / PETLINK_COLS)
  const cb = b % PETLINK_COLS
  let t1 = { r: ra, c: ca }
  let t2 = { r: rb, c: cb }

  if (level === 3) {
    // 上下分离:同列且同半区时,上半区行大者先(上移)、下半区行小者先(下移)
    if (ca === cb) {
      if (ra <= 4 && rb <= 4 && ra < rb) [t1, t2] = [t2, t1]
      if (ra >= 5 && rb >= 5 && rb < ra) [t1, t2] = [t2, t1]
    }
    shiftVertHalf(board, t1.r, t1.c)
    shiftVertHalf(board, t2.r, t2.c)
  } else if (level === 4) {
    // 左右分离:同行且同半区时,左半区列大者先(左移)、右半区列小者先(右移)
    if (ra === rb) {
      if (ca <= 6 && cb <= 6 && ca < cb) [t1, t2] = [t2, t1]
      if (ca >= 7 && cb >= 7 && cb < ca) [t1, t2] = [t2, t1]
    }
    shiftHorizHalf(board, t1.r, t1.c)
    shiftHorizHalf(board, t2.r, t2.c)
  } else if (level === 5) {
    // 向左看齐:同行时右者先(左移会把右侧牌提前搬进左者目标位)
    if (ca < cb) [t1, t2] = [t2, t1]
    shiftRowLeft(board, t1.r, t1.c)
    shiftRowLeft(board, t2.r, t2.c)
  } else if (level === 6) {
    // 地心引力(整列下移):同列时上者先
    if (rb < ra) [t1, t2] = [t2, t1]
    shiftColDown(board, t1.r, t1.c)
    shiftColDown(board, t2.r, t2.c)
  } else if (level === 7) {
    // 飘向天空(整列上移):同列时下者先
    if (ra < rb) [t1, t2] = [t2, t1]
    shiftColUp(board, t1.r, t1.c)
    shiftColUp(board, t2.r, t2.c)
  } else if (level === 8) {
    // 向右看齐:同行时左者先(右移会把左侧牌提前搬进右者目标位)
    if (cb < ca) [t1, t2] = [t2, t1]
    shiftRowRight(board, t1.r, t1.c)
    shiftRowRight(board, t2.r, t2.c)
  } else if (level === 9) {
    board[a] = null
    board[b] = null
    toCenter(board)
  }
}

function shiftVertHalf(board, r, c) {
  if (r <= 4) {
    for (let rr = r; rr < 4; rr++) board[rr * PETLINK_COLS + c] = board[(rr + 1) * PETLINK_COLS + c]
    board[4 * PETLINK_COLS + c] = null
  } else {
    for (let rr = r; rr > 5; rr--) board[rr * PETLINK_COLS + c] = board[(rr - 1) * PETLINK_COLS + c]
    board[5 * PETLINK_COLS + c] = null
  }
}
function shiftHorizHalf(board, r, c) {
  if (c <= 6) {
    for (let cc = c; cc < 6; cc++) board[r * PETLINK_COLS + cc] = board[r * PETLINK_COLS + cc + 1]
    board[r * PETLINK_COLS + 6] = null
  } else {
    for (let cc = c; cc > 7; cc--) board[r * PETLINK_COLS + cc] = board[r * PETLINK_COLS + cc - 1]
    board[r * PETLINK_COLS + 7] = null
  }
}
function shiftRowLeft(board, r, c) {
  for (let cc = c; cc < PETLINK_COLS - 1; cc++) board[r * PETLINK_COLS + cc] = board[r * PETLINK_COLS + cc + 1]
  board[r * PETLINK_COLS + PETLINK_COLS - 1] = null
}
function shiftRowRight(board, r, c) {
  for (let cc = c; cc > 0; cc--) board[r * PETLINK_COLS + cc] = board[r * PETLINK_COLS + cc - 1]
  board[r * PETLINK_COLS] = null
}
function shiftColDown(board, r, c) {
  for (let rr = r; rr > 0; rr--) board[rr * PETLINK_COLS + c] = board[(rr - 1) * PETLINK_COLS + c]
  board[c] = null
}
function shiftColUp(board, r, c) {
  for (let rr = r; rr < PETLINK_ROWS - 1; rr++) board[rr * PETLINK_COLS + c] = board[(rr + 1) * PETLINK_COLS + c]
  board[(PETLINK_ROWS - 1) * PETLINK_COLS + c] = null
}
function toCenter(board) {
  let moved
  do {
    moved = false
    for (let i = 0; i < PETLINK_CELLS; i++) {
      if (board[i] !== null) continue
      const r = Math.floor(i / PETLINK_COLS)
      const c = i % PETLINK_COLS
      if (r > 0 && r < PETLINK_ROWS - 1) {
        // 排除首末行
        if (r < 5 && board[i - PETLINK_COLS] !== null) {
          // 上半区:上方牌下移
          board[i] = board[i - PETLINK_COLS]
          board[i - PETLINK_COLS] = null
          moved = true
        } else if (r >= 5 && board[i + PETLINK_COLS] !== null) {
          // 下半区:下方牌上移
          board[i] = board[i + PETLINK_COLS]
          board[i + PETLINK_COLS] = null
          moved = true
        }
      }
      if (board[i] !== null) continue
      if (c > 0 && c < PETLINK_COLS - 1) {
        // 排除首末列
        if (c < 7 && board[i - 1] !== null) {
          // 左半区:左方牌右移
          board[i] = board[i - 1]
          board[i - 1] = null
          moved = true
        } else if (c >= 7 && board[i + 1] !== null) {
          // 右半区:右方牌左移
          board[i] = board[i + 1]
          board[i + 1] = null
          moved = true
        }
      }
    }
  } while (moved)
}

// ---------- 盘面体检(调试面板与存档校验共用) ----------

/** 各宠物剩余张数:Map<type, count> */
export function petLinkTypeCounts(board) {
  const m = new Map()
  for (const t of board) if (t) m.set(t.type, (m.get(t.type) || 0) + 1)
  return m
}

/** 奇数张的宠物(正常盘面每型必须成对);返回 [{ type, count }] */
export function petLinkOddTypes(board) {
  const out = []
  for (const [type, count] of petLinkTypeCounts(board)) if (count % 2 !== 0) out.push({ type, count })
  return out
}

/**
 * 自检:按当前关卡自动对局若干局,校验每次消除是否恰好吃掉同型两张。
 * 返回 { games, steps, issues: [{ kind, level, ... }] } —— issues 为空即逻辑正常。
 */
export function simulatePetLinkLevel(level, games = 20, rand = Math.random, maxSteps = 200) {
  const issues = []
  let steps = 0
  for (let g = 0; g < games; g++) {
    const { board } = createPetLinkBoard(level, 0, rand)
    for (let step = 0; step < maxSteps; step++) {
      const pair = findPetLinkPair(board)
      if (!pair) break
      const [a, b] = pair
      const ta = board[a]
      const tb = board[b]
      const beforeCount = board.filter(Boolean).length
      const beforeTypes = petLinkTypeCounts(board)
      const beforeIds = new Set(board.filter(Boolean).map((t) => t.id))
      removePetLinkPair(board, level, a, b)
      steps += 1
      const afterCount = board.filter(Boolean).length
      const lostIds = [...beforeIds].filter((id) => !board.some((t) => t && t.id === id))
      const lostExtra = lostIds.filter((id) => id !== ta.id && id !== tb.id)
      if (beforeCount - afterCount !== 2 || lostExtra.length) {
        issues.push({
          kind: 'EXTRA_REMOVED',
          level,
          step,
          a,
          b,
          ra: Math.floor(a / PETLINK_COLS),
          ca: a % PETLINK_COLS,
          rb: Math.floor(b / PETLINK_COLS),
          cb: b % PETLINK_COLS,
          type: ta.type,
          removed: beforeCount - afterCount,
          extra: lostExtra.length,
        })
        break
      }
      const afterTypes = petLinkTypeCounts(board)
      let wrongType = 0
      for (const [type, count] of beforeTypes) {
        const expect = type === ta.type ? count - 2 : count
        if ((afterTypes.get(type) || 0) !== expect) wrongType = type
      }
      if (wrongType) {
        issues.push({ kind: 'WRONG_TYPE', level, step, type: wrongType, expectType: ta.type })
        break
      }
    }
  }
  return { level, games, steps, issues }
}
