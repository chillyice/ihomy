import { describe, expect, it } from 'vitest'
import {
  LUDO_HANGARS,
  LUDO_LANES,
  LUDO_RING,
  LUDO_RING_STEPS,
  LUDO_START,
  LUDO_TOTAL_STEPS,
  createLudoState,
  ludoAiPick,
  ludoApplyMove,
  ludoCellColor,
  ludoEntryIndex,
  ludoFinishedCount,
  ludoLegalMoves,
  ludoPlaneIndex,
  ludoRingIndex,
  ludoRoll,
  ludoSeatColors,
  ludoStepPoint,
} from './ludo'

const seats = () => [
  { color: 0, userId: 1, name: 'A' },
  { color: 1, userId: 2, name: 'B' },
  { color: 2, userId: 3, name: 'C' },
  { color: 3, userId: 4, name: 'D' },
]

/** 摆一个「轮到我掷了」的局面 */
const ready = (steps = {}, turn = 0, dice = 0) => {
  const state = createLudoState(seats())
  state.turn = turn
  for (const [color, list] of Object.entries(steps)) state.pieces[color] = list.slice()
  if (dice) {
    state.dice = dice
    state.awaiting = 'move'
    state.moves = ludoLegalMoves(state)
  }
  return state
}

describe('ludo 盘面几何', () => {
  it('主环 52 格且不重叠,四色各 13 格', () => {
    expect(LUDO_RING).toHaveLength(52)
    expect(new Set(LUDO_RING.map((cell) => cell.join(','))).size).toBe(52)
    for (let color = 0; color < 4; color += 1) {
      expect(LUDO_RING.filter((_, index) => ludoCellColor(index) === color)).toHaveLength(13)
    }
  })

  it('每色起飞格紧邻自家机场、跑道口是本色格、航线格飞 12 格', () => {
    const starts = LUDO_START
    expect(starts).toEqual([0, 13, 26, 39])
    for (let color = 0; color < 4; color += 1) {
      expect(ludoCellColor(starts[color])).toBe(color)
      expect(ludoCellColor(ludoEntryIndex(color))).toBe(color)
      // 航线格刻意取异色格:否则会和「同色格飞跃」抢同一个落点
      expect(ludoCellColor(ludoPlaneIndex(color))).not.toBe(color)
      expect(ludoRingIndex(color, 1)).toBe(starts[color])
      expect(ludoRingIndex(color, LUDO_RING_STEPS)).toBe(ludoEntryIndex(color))
      // 起飞格紧贴自家机场(6×6 区块)边缘
      const [hx, hy] = LUDO_HANGARS[color]
      const [sx, sy] = LUDO_RING[starts[color]]
      const dx = Math.max(hx - sx, 0, sx - (hx + 5))
      const dy = Math.max(hy - sy, 0, sy - (hy + 5))
      expect(dx + dy).toBe(1)
    }
  })

  it('跑道每色 6 格且与跑道口正相邻', () => {
    for (let color = 0; color < 4; color += 1) {
      expect(LUDO_LANES[color]).toHaveLength(6)
      const entry = LUDO_RING[ludoEntryIndex(color)]
      const first = LUDO_LANES[color][0]
      expect(Math.abs(entry[0] - first[0]) + Math.abs(entry[1] - first[1])).toBe(1)
    }
  })

  it('点位坐标:机场在内、主环在外、终点跑道末格贴中心', () => {
    expect(ludoStepPoint(0, 0, 0)).toEqual([1.55, 10.55])
    expect(ludoStepPoint(0, 1)).toEqual([6.5, 13.5])
    expect(ludoStepPoint(0, LUDO_TOTAL_STEPS)).toEqual([7.5, 8.5])
    expect(ludoStepPoint(0, LUDO_TOTAL_STEPS + 1)).toBeNull()
  })

  it('席位配色:2 人隔角、3/4 人顺时针补位', () => {
    expect(ludoSeatColors(2)).toEqual([0, 2])
    expect(ludoSeatColors(3)).toEqual([0, 1, 2])
    expect(ludoSeatColors(4)).toEqual([0, 1, 2, 3])
  })
})

describe('ludo 走子规则', () => {
  it('掷 6 才能起飞,掷 6 额外加一次', () => {
    const state = ready({}, 0, 5)
    expect(state.moves).toHaveLength(0)
    const six = ready({}, 0, 6)
    expect(six.moves.every((move) => move.kind === 'launch')).toBe(true)
    const launched = ludoApplyMove(six, six.moves[0])
    expect(launched.pieces[0][0]).toBe(1)
    expect(launched.turn).toBe(0)
    expect(launched.awaiting).toBe('roll')
  })

  it('无人可动时掷骰直接过手', () => {
    const state = ludoRoll(ready({}, 0, 0), 5)
    expect(state.turn).toBe(1)
    expect(state.note.type).toBe('pass')
  })

  it('停在自己颜色的格子飞跃 4 格', () => {
    // color0 走 5 步落在主环第 4 格(自己的颜色),再飞跃到第 9 格
    const state = ready({ 0: [4, 0, 0, 0] }, 0, 1)
    expect(state.moves[0].kind).toBe('color')
    expect(state.moves[0].to).toBe(9)
    expect(state.moves[0].frames.at(-1)).toEqual({ step: 9, at: 'color' })
  })

  it('停在航线格沿虚线飞越 12 格', () => {
    const state = ready({ 0: [2, 0, 0, 0] }, 0, 1)
    expect(state.moves[0].kind).toBe('plane')
    expect(state.moves[0].to).toBe(15)
  })

  it('超过终点弹回,不触发飞跃', () => {
    const state = ready({ 0: [54, 0, 0, 0] }, 0, 4)
    expect(state.moves[0].kind).toBe('bounce')
    expect(state.moves[0].to).toBe(52)
    expect(state.note).toBeNull()
    const applied = ludoApplyMove(state, state.moves[0])
    expect(applied.pieces[0][0]).toBe(52)
    expect(applied.note.type).toBe('bounce')
    expect(applied.note.n).toBe(3)
    expect(applied.turn).toBe(1)
  })

  it('落在对手飞机上把它撞回机场', () => {
    // color0 走到主环 6 号格,color1 的飞机停在同一格
    const state = ready({ 0: [6, 0, 0, 0], 1: [46, 0, 0, 0] }, 0, 1)
    expect(ludoRingIndex(1, 46)).toBe(ludoRingIndex(0, 7))
    const applied = ludoApplyMove(state, state.moves[0])
    expect(applied.pieces[1][0]).toBe(0)
    expect(applied.lastMove.captured).toEqual([{ color: 1, piece: 0 }])
  })

  it('起飞点安全,停上去不撞人', () => {
    // 主环 26 号格是 color2 的起飞格
    const state = ready({ 0: [26, 0, 0, 0], 2: [1, 0, 0, 0] }, 0, 1)
    expect(ludoRingIndex(0, 27)).toBe(26)
    expect(state.moves[0].kind).toBe('move')
    const applied = ludoApplyMove(state, state.moves[0])
    expect(applied.pieces[2][0]).toBe(1)
    expect(applied.lastMove.captured).toEqual([])
  })

  it('四架抵达中心即获胜', () => {
    const state = ready({ 0: [LUDO_TOTAL_STEPS, LUDO_TOTAL_STEPS, LUDO_TOTAL_STEPS, LUDO_TOTAL_STEPS - 1] }, 0, 1)
    const applied = ludoApplyMove(state, state.moves[0])
    expect(applied.pieces[0][0]).toBe(LUDO_TOTAL_STEPS)
    expect(applied.phase).toBe('ended')
    expect(applied.winner).toBe(0)
    expect(ludoFinishedCount(applied, 0)).toBe(4)
  })

  it('掷 6 走完再掷,不交棒', () => {
    const state = ready({ 0: [10, 0, 0, 0] }, 0, 6)
    const applied = ludoApplyMove(state, state.moves[0])
    expect(applied.turn).toBe(0)
    expect(applied.dice).toBe(6)
  })
})

describe('ludo AI', () => {
  it('优先撞人', () => {
    // 两种选择:吃子 or 普通前进
    const state = ready({ 0: [6, 0, 0, 0], 1: [46, 0, 0, 0] }, 0, 1)
    const pick = ludoAiPick(state)
    expect(pick.piece).toBe(0)
  })

  it('没有可选落点时返回 null', () => {
    const state = ready({}, 0, 0)
    expect(ludoAiPick(state)).toBeNull()
  })
})
