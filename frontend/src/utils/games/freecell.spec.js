import { describe, it, expect } from 'vitest'
import {
  COLUMNS,
  FREE_CELLS,
  STATUS,
  autoMove,
  canStackOnTableau,
  deal,
  findHint,
  isStuck,
  isValidState,
  isWon,
  maxMoveCount,
  move,
} from './freecell'
import { SUITS } from './solitaire'

const card = (suit, rank, id = SUITS.indexOf(suit) * 13 + rank - 1) => ({ id, suit, rank, faceUp: true })

function blankState() {
  return {
    tableau: Array.from({ length: COLUMNS }, () => []),
    freeCells: Array.from({ length: FREE_CELLS }, () => null),
    foundations: { S: [], H: [], D: [], C: [] },
    moves: 0,
    score: 0,
    status: STATUS.PLAYING,
  }
}

describe('自由纸牌发牌', () => {
  it('八列 52 张全明牌,前四列 7 张、后四列 6 张', () => {
    const state = deal(() => 0.5)
    expect(state.tableau).toHaveLength(COLUMNS)
    expect(state.tableau.map((pile) => pile.length)).toEqual([7, 7, 7, 7, 6, 6, 6, 6])
    expect(state.tableau.flat().every((c) => c.faceUp)).toBe(true)
    expect(new Set(state.tableau.flat().map((c) => c.id)).size).toBe(52)
    expect(state.freeCells.every((c) => c === null)).toBe(true)
    expect(isValidState(state)).toBe(true)
  })

  it('读档校验拒绝重复牌', () => {
    const state = deal(() => 0.25)
    state.tableau[1].push({ ...state.tableau[0][0] })
    expect(isValidState(state)).toBe(false)
  })
})

describe('自由纸牌移动', () => {
  it('红黑交替降序才能叠到桌面列', () => {
    const state = blankState()
    state.tableau[0] = [card('H', 9)]
    state.tableau[1] = [card('S', 8)]
    expect(canStackOnTableau(state.tableau[0][0], state.tableau[1][0])).toBe(true)
    expect(canStackOnTableau(state.tableau[0][0], card('S', 7))).toBe(false)
    const next = move(state, { type: 'tableau', pile: 1, index: 0 }, { type: 'tableau', pile: 0 })
    expect(next).not.toBeNull()
    expect(next.tableau[0].map((c) => c.rank)).toEqual([9, 8])
  })

  it('单张牌可放进空自由格,不能叠第二张', () => {
    const state = blankState()
    state.tableau[0] = [card('S', 5), card('H', 4)]
    const next = move(state, { type: 'tableau', pile: 0, index: 1 }, { type: 'free', cell: 0 })
    expect(next.freeCells[0].rank).toBe(4)
    expect(move(next, { type: 'tableau', pile: 0, index: 0 }, { type: 'free', cell: 0 })).toBeNull()
  })

  it('整段搬运数量受空自由格与空列限制', () => {
    const state = blankState()
    state.tableau[0] = [card('C', 2), card('S', 13), card('H', 12), card('S', 11)]
    state.tableau[1] = []
    // 只留一个空列,避免其他空列把可搬数量放大
    for (let pile = 2; pile < COLUMNS; pile++) state.tableau[pile] = [card('C', pile + 1)]
    state.freeCells[0] = card('D', 3)
    state.freeCells[1] = card('D', 4)
    state.freeCells[2] = card('D', 5)
    state.freeCells[3] = card('D', 6)
    expect(maxMoveCount(state, true)).toBe(1)
    const from = { type: 'tableau', pile: 0, index: 1 }
    expect(move(state, from, { type: 'tableau', pile: 1 })).toBeNull()
    state.freeCells[3] = null
    expect(maxMoveCount(state, true)).toBe(2)
    expect(move(state, from, { type: 'tableau', pile: 1 })).toBeNull()
    state.freeCells[2] = null
    expect(maxMoveCount(state, true)).toBe(3)
    expect(move(state, from, { type: 'tableau', pile: 1 })).not.toBeNull()
  })

  it('A 起同花色收进花色堆,双点自动收纳优先花色堆', () => {
    const state = blankState()
    state.tableau[0] = [card('S', 1)]
    const hint = findHint(state)
    expect(hint.to).toEqual({ type: 'foundation', suit: 'S' })
    const next = autoMove(state, { type: 'tableau', pile: 0, index: 0 })
    expect(next.foundations.S.map((c) => c.rank)).toEqual([1])
  })

  it('四堆收满判定通关', () => {
    const state = blankState()
    for (const suit of SUITS) {
      state.foundations[suit] = Array.from({ length: 13 }, (_, i) => card(suit, i + 1, SUITS.indexOf(suit) * 13 + i))
    }
    expect(isWon(state)).toBe(true)
  })
})

describe('自由纸牌无路可走与提示', () => {
  it('完全没有可移动的牌时判输', () => {
    // 52 张全部在场:四张 5 在自由格,八列各 6 张且列顶只有 2/4,互不相接也进不了空花色堆
    const deck = []
    for (const suit of SUITS) {
      for (let rank = 1; rank <= 13; rank++) deck.push(card(suit, rank))
    }
    const tops = [card('S', 2), card('H', 2), card('D', 2), card('C', 2), card('S', 4), card('H', 4), card('D', 4), card('C', 4)]
    const topKeys = new Set(tops.map((c) => `${c.suit}${c.rank}`))
    const fives = deck.filter((c) => c.rank === 5)
    const rest = deck.filter((c) => c.rank !== 5 && !topKeys.has(`${c.suit}${c.rank}`))
    const state = blankState()
    state.freeCells = fives.slice(0, FREE_CELLS)
    state.tableau = tops.map((top, i) => rest.slice(i * 5, i * 5 + 5).concat([top]))
    expect(isStuck(state)).toBe(true)
    const hint = findHint(state)
    expect(hint).toBeNull()
  })

  it('有牌可叠时不算输', () => {
    const state = blankState()
    state.tableau[0] = [card('H', 9)]
    state.tableau[1] = [card('S', 8)]
    expect(isStuck(state)).toBe(false)
  })

  it('提示优先给出能露出背面牌之外最有用的接续', () => {
    const state = blankState()
    state.tableau[0] = [card('S', 7)]
    state.tableau[1] = [card('H', 8)]
    const hint = findHint(state)
    expect(hint.from).toEqual({ type: 'tableau', pile: 0, index: 0 })
    expect(hint.to).toEqual({ type: 'tableau', pile: 1 })
  })
})
