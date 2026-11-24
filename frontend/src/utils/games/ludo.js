// 飞行棋规则内核:盘面几何 + 走子判定 + AI 选子,全部纯函数(无 Vue、无 DOM)。
// 「一起玩」房间局由房主权威跑这套函数并把 state 广播给全房,其余人只发操作意图;
// 单机局本地跑同一套,AI 顶替空位。动画所需的逐格路径(frames)也在这里产出,
// 两端拿同一份 frames 播放,画面才不会各跳各的。

/** 颜色序:0 左下 / 1 左上 / 2 右上 / 3 右下(顺时针,与主环行进方向一致) */
export const LUDO_PIECE_COUNT = 4
/**
 * 主环 52 格上走 49 步到自己的跑道口(起飞格 + 48,与主环同色),再走 6 格进中心停机坪。
 * 49 步是几何硬约束:跑道口那格必须与自家跑道首格正相邻,且仍属本色,否则「同色格飞跃」与
 * 「进跑道」会抢同一格。
 */
export const LUDO_RING_STEPS = 49
export const LUDO_LANE_STEPS = 6
export const LUDO_TOTAL_STEPS = LUDO_RING_STEPS + LUDO_LANE_STEPS

/** 各色起飞格在主环上的下标(落在自己机场门口那一格,本色格) */
export const LUDO_START = [0, 13, 26, 39]

/** 主环 52 格坐标(格点左上角,盘面按 15×15 网格铺) */
export const LUDO_RING = [
  [6, 13], // 0  左下机场门口
  [6, 12],
  [6, 11],
  [6, 10],
  [6, 9],
  [5, 8],
  [4, 8],
  [3, 8],
  [2, 8],
  [1, 8],
  [0, 8],
  [0, 7], // 11 左端拐角
  [0, 6],
  [1, 6], // 13 左上机场门口
  [2, 6],
  [3, 6],
  [4, 6],
  [5, 6],
  [6, 5],
  [6, 4],
  [6, 3],
  [6, 2],
  [6, 1],
  [6, 0],
  [7, 0], // 24 上端拐角
  [8, 0],
  [8, 1], // 26 右上机场门口
  [8, 2],
  [8, 3],
  [8, 4],
  [8, 5],
  [9, 6],
  [10, 6],
  [11, 6],
  [12, 6],
  [13, 6],
  [14, 6],
  [14, 7], // 37 右端拐角
  [14, 8],
  [13, 8], // 39 右下机场门口
  [12, 8],
  [11, 8],
  [10, 8],
  [9, 8],
  [8, 9],
  [8, 10],
  [8, 11],
  [8, 12],
  [8, 13],
  [8, 14], // 49 左下跑道口
  [7, 14],
  [6, 14],
]

/** 四色终点跑道:各 6 格,末格贴中心停机坪 */
export const LUDO_LANES = {
  0: [
    [7, 13],
    [7, 12],
    [7, 11],
    [7, 10],
    [7, 9],
    [7, 8],
  ],
  1: [
    [1, 7],
    [2, 7],
    [3, 7],
    [4, 7],
    [5, 7],
    [6, 7],
  ],
  2: [
    [7, 1],
    [7, 2],
    [7, 3],
    [7, 4],
    [7, 5],
    [7, 6],
  ],
  3: [
    [13, 7],
    [12, 7],
    [11, 7],
    [10, 7],
    [9, 7],
    [8, 7],
  ],
}

/** 机场(停机位)左上角,6×6 区块 */
export const LUDO_HANGARS = {
  0: [0, 9],
  1: [0, 0],
  2: [9, 0],
  3: [9, 9],
}

/** 主环某格归属的颜色(每 4 格一个同色格;起飞格/跑道口都落在本色上) */
export const ludoCellColor = (ringIndex) => ringIndex % 4

/** 棋子走了 step 步后落在主环哪一格 */
export const ludoRingIndex = (color, step) => (LUDO_START[color] + step - 1) % LUDO_RING.length

/**
 * 本色的起飞格 / 跑道口 / 航线格(主环下标)。
 * 航线格取起飞格后第 3 格(异色,不会与「同色格飞跃」抢同一个落点),飞跃 +12 格仍落在同色格上。
 */
export const ludoStartIndex = (color) => LUDO_START[color]
export const ludoEntryIndex = (color) => (LUDO_START[color] + LUDO_RING_STEPS - 1) % LUDO_RING.length
export const ludoPlaneIndex = (color) => (LUDO_START[color] + 2) % LUDO_RING.length

/** 开局席位配色:2 人隔角对坐,3 人顺时针连坐,4 人满场 */
export const ludoSeatColors = (count) => {
  const n = Math.max(2, Math.min(4, Math.floor(Number(count) || 4)))
  return n === 2 ? [0, 2] : [0, 1, 2, 3].slice(0, n)
}

/** 格心坐标(用于棋子落位) */
const center = ([x, y]) => [x + 0.5, y + 0.5]

/** 机场里第 i 个停机位的圆心 */
export const ludoHangarPad = (color, padIndex) => {
  const [ox, oy] = LUDO_HANGARS[color]
  const col = padIndex % 2
  const row = Math.floor(padIndex / 2)
  return [ox + 1.55 + col * 2.1, oy + 1.55 + row * 2.1]
}

/**
 * 棋子在盘面上的坐标:0 = 机场,1..49 = 主环,50..55 = 终点跑道。
 * 返回 null 表示该点位不存在(越界)。
 */
export const ludoStepPoint = (color, step, padIndex = 0) => {
  if (!step) return ludoHangarPad(color, padIndex)
  if (step <= LUDO_RING_STEPS) {
    const cell = LUDO_RING[ludoRingIndex(color, step)]
    return cell ? center(cell) : null
  }
  if (step <= LUDO_TOTAL_STEPS) {
    const cell = LUDO_LANES[color]?.[step - LUDO_RING_STEPS - 1]
    return cell ? center(cell) : null
  }
  return null
}

/** 某格是否安全(四色起飞格不进飞机,别人撞不下来) */
export const ludoIsSafeRing = (ringIndex) => LUDO_START.includes(ringIndex)

const clone = (value) => JSON.parse(JSON.stringify(value))

const emptySteps = () => [0, 0, 0, 0]

/** 开局状态:seats 按颜色升序,每人 4 架飞机停在机场 */
export function createLudoState(seats) {
  const list = (seats || [])
    .map((s) => ({
      color: Number(s.color),
      userId: s.userId ?? null,
      name: s.name || '',
      ai: !!s.ai,
    }))
    .filter((s) => s.color >= 0 && s.color <= 3)
    .sort((a, b) => a.color - b.color)
  const pieces = {}
  for (const seat of list) pieces[seat.color] = emptySteps()
  return {
    seats: list,
    pieces,
    turn: 0,
    dice: 0,
    awaiting: 'roll',
    moves: [],
    seq: 0,
    rollSeq: 0,
    phase: 'playing',
    winner: null,
    note: null,
    lastMove: null,
  }
}

/** 从 from 走到 to 的逐格刻度(to 不含起点,含终点) */
function walkFrames(from, to) {
  const frames = []
  if (to >= from) {
    for (let step = from + 1; step <= to; step += 1) frames.push({ step })
  } else {
    for (let step = from - 1; step >= to; step -= 1) frames.push({ step })
  }
  return frames
}

/**
 * 落点结算:进跑道 → 同色格飞跃 4 格 → 航线格飞越 12 格。
 * 飞跃只在主环内进行(不会跳进跑道),航线落地不再连锁。
 */
function resolveLanding(color, from, raw) {
  let step = raw
  let kind = raw > LUDO_RING_STEPS ? 'lane' : 'move'
  const frames = walkFrames(from, raw)
  if (step <= LUDO_RING_STEPS && ludoCellColor(ludoRingIndex(color, step)) === color && step + 4 <= LUDO_RING_STEPS) {
    step += 4
    kind = 'color'
    frames.push({ step, at: 'color' })
  }
  if (step <= LUDO_RING_STEPS && ludoRingIndex(color, step) === ludoPlaneIndex(color)) {
    const dest = step + 12
    if (dest <= LUDO_RING_STEPS) {
      step = dest
      kind = 'plane'
      frames.push({ step, at: 'plane' })
    }
  }
  if (step === LUDO_TOTAL_STEPS) kind = 'finish'
  return { step, kind, frames }
}

/** 当前可走的飞机:掷 6 可让机场里的飞机起飞,其余按点数前进(超终点弹回) */
export function ludoLegalMoves(state) {
  if (!state || state.phase !== 'playing' || state.awaiting !== 'move' || !state.dice) return []
  const seat = state.seats[state.turn]
  if (!seat) return []
  const color = seat.color
  const steps = state.pieces[color] || emptySteps()
  const dice = state.dice
  const out = []
  for (let piece = 0; piece < LUDO_PIECE_COUNT; piece += 1) {
    const from = steps[piece] || 0
    if (from >= LUDO_TOTAL_STEPS) continue
    if (from === 0) {
      if (dice !== 6 && (state.misses?.[color] || 0) < 6) continue
      out.push({ piece, from: 0, to: 1, kind: 'launch', frames: [{ step: 1, at: 'launch' }] })
      continue
    }
    const raw = from + dice
    if (raw > LUDO_TOTAL_STEPS) {
      const to = LUDO_TOTAL_STEPS * 2 - raw
      out.push({
        piece,
        from,
        to,
        kind: 'bounce',
        frames: [...walkFrames(from, LUDO_TOTAL_STEPS), ...walkFrames(LUDO_TOTAL_STEPS, to)],
      })
      continue
    }
    const landing = resolveLanding(color, from, raw)
    out.push({ piece, from, to: landing.step, kind: landing.kind, frames: landing.frames })
  }
  return out
}

/** 掷骰子:只负责发点数和算出可选落点;无人可动时直接过手 */
export function ludoRoll(state, value = 1 + Math.floor(Math.random() * 6)) {
  if (!state || state.phase !== 'playing' || state.awaiting !== 'roll') return state
  const next = clone(state)
  next.dice = Math.max(1, Math.min(6, Math.round(Number(value) || 1)))
  const color = next.seats[next.turn]?.color
  next.misses ??= {}
  next.misses[color] = next.dice === 6 ? 0 : (next.misses[color] || 0) + 1
  next.rollSeq = (next.rollSeq || 0) + 1
  next.awaiting = 'move'
  next.lastMove = null
  next.moves = ludoLegalMoves(next)
  next.seq = (next.seq || 0) + 1
  if (!next.moves.length) {
    const seat = next.seats[next.turn]
    next.note = { type: 'pass', color: seat ? seat.color : 0 }
    next.awaiting = 'roll'
    next.dice = 0
    next.turn = (next.turn + 1) % next.seats.length
    next.seq += 1
  }
  return next
}

/** 执行一步走子(落地结算 + 撞机 + 交棒/加掷) */
export function ludoApplyMove(state, move) {
  if (!state || !move || state.phase !== 'playing' || state.awaiting !== 'move') return state
  const next = clone(state)
  const seat = next.seats[next.turn]
  if (!seat) return state
  const color = seat.color
  const steps = next.pieces[color] || emptySteps()
  const from = steps[move.piece] || 0
  const active = steps.filter((step) => step > 0 && step < LUDO_TOTAL_STEPS).length
  const companions =
    from === 0 ? [] : steps.flatMap((step, i) => (step === from && i !== move.piece ? [i] : [])).slice(0, Math.max(0, active - 2))
  for (const piece of [move.piece, ...companions]) steps[piece] = move.to
  if (from === 0 && next.dice !== 6 && next.misses) next.misses[color] = 0

  const captured = []
  if (move.to >= 1 && move.to <= LUDO_RING_STEPS) {
    const ringIndex = ludoRingIndex(color, move.to)
    if (!ludoIsSafeRing(ringIndex)) {
      for (const rival of next.seats) {
        if (rival.color === color) continue
        const rivalSteps = next.pieces[rival.color] || []
        for (let piece = 0; piece < rivalSteps.length; piece += 1) {
          const step = rivalSteps[piece]
          if (step >= 1 && step <= LUDO_RING_STEPS && ludoRingIndex(rival.color, step) === ringIndex) {
            rivalSteps[piece] = 0
            captured.push({ color: rival.color, piece })
          }
        }
      }
    }
  }

  const dice = next.dice
  next.lastMove = {
    color,
    piece: move.piece,
    companions,
    from,
    to: move.to,
    kind: move.kind,
    frames: move.frames || [],
    captured,
    dice,
  }
  next.awaiting = 'roll'
  next.moves = []
  next.seq = (next.seq || 0) + 1

  const finished = steps.every((step) => step >= LUDO_TOTAL_STEPS)
  if (finished) {
    next.phase = 'ended'
    next.winner = color
    next.note = { type: 'win', color }
    return next
  }

  const extra = dice === 6
  next.dice = extra ? dice : 0
  if (!extra) next.turn = (next.turn + 1) % next.seats.length
  next.note = {
    type: move.kind,
    color,
    n: move.kind === 'plane' ? 12 : move.kind === 'color' ? 4 : move.kind === 'bounce' ? Math.max(1, from + dice - LUDO_TOTAL_STEPS) : dice,
    captured: captured.length,
  }
  return next
}

/** AI 选子:吃子 > 抵达终点 > 航线飞跃 > 同色飞跃 > 起飞 > 走得远 */
export function ludoAiPick(state) {
  const moves = state?.moves || []
  if (!moves.length) return null
  let best = null
  let bestScore = -Infinity
  const color = state.seats[state.turn].color
  for (const move of moves) {
    const ringIndex = move.to >= 1 && move.to <= LUDO_RING_STEPS ? ludoRingIndex(color, move.to) : -1
    const captures = countCaptures(state, color, ringIndex)
    let score = captures * 120
    if (move.kind === 'finish') score += 70
    if (move.kind === 'plane') score += 40
    if (move.kind === 'color') score += 18
    if (move.kind === 'launch') score += 26
    if (move.kind === 'bounce') score -= 30
    score += move.to * 0.6
    if (score > bestScore) {
      bestScore = score
      best = move
    }
  }
  return best
}

function countCaptures(state, color, ringIndex) {
  if (ringIndex < 0 || ludoIsSafeRing(ringIndex)) return 0
  let count = 0
  for (const seat of state.seats) {
    if (seat.color === color) continue
    for (const step of state.pieces[seat.color] || []) {
      if (step >= 1 && step <= LUDO_RING_STEPS && ludoRingIndex(seat.color, step) === ringIndex) count += 1
    }
  }
  return count
}

/** 已抵达中心的架数(玩家卡/比分展示用) */
export const ludoFinishedCount = (state, color) => (state?.pieces?.[color] || []).filter((step) => step >= LUDO_TOTAL_STEPS).length

/** 某格上的棋子(渲染同格叠放用) */
export function ludoPiecesOnCell(state, color, step) {
  const out = []
  const steps = state?.pieces?.[color] || []
  for (let piece = 0; piece < steps.length; piece += 1) if (steps[piece] === step) out.push(piece)
  return out
}
