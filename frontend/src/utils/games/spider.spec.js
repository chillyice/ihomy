import { describe, it, expect } from 'vitest'
import {
  COLUMNS,
  STATUS,
  autoMove,
  canDealStock,
  canStackOnTableau,
  createDeck,
  deal,
  dealStock,
  findHint,
  isStuck,
  isValidState,
  isWon,
  move,
  sequenceAt,
} from './spider'

function seeded(seed = 1) {
  let s = seed >>> 0
  return () => {
    s = (s * 1664525 + 1013904223) >>> 0
    return s / 4294967296
  }
}

const card = (suit, rank, faceUp = true, id = undefined) => ({
  id: id ?? Math.floor(Math.random() * 104),
  suit,
  rank,
  faceUp,
})

function blankState() {
  return {
    difficulty: 4,
    stock: [],
    tableau: Array.from({ length: COLUMNS }, () => []),
    completed: 0,
    moves: 0,
    score: 0,
    status: STATUS.PLAYING,
  }
}

/** 给定花色的 K→A(13 张,正面) */
function fullRun(suit) {
  return Array.from({ length: 13 }, (_, i) => card(suit, 13 - i, true, i))
}

describe('蜘蛛纸牌发牌', () => {
  it.each([1, 2, 4])('%i 色牌组共 104 张且花色数量正确', (difficulty) => {
    const deck = createDeck(difficulty)
    expect(deck).toHaveLength(104)
    expect(new Set(deck.map((c) => c.id)).size).toBe(104)
    const suits = new Set(deck.map((c) => c.suit))
    expect(suits.size).toBe(difficulty)
    for (const suit of suits) {
      expect(deck.filter((c) => c.suit === suit)).toHaveLength(104 / difficulty)
    }
  })

  it('前 4 列 6 张、后 6 列 5 张,共 54 张在桌面、50 张在牌堆', () => {
    const state = deal(2, seeded(7))
    expect(state.tableau.map((p) => p.length)).toEqual([6, 6, 6, 6, 5, 5, 5, 5, 5, 5])
    expect(state.stock).toHaveLength(50)
    for (const pile of state.tableau) {
      expect(pile[pile.length - 1].faceUp).toBe(true)
      expect(pile.slice(0, -1).every((c) => !c.faceUp)).toBe(true)
    }
    expect(isValidState(state)).toBe(true)
  })
})

describe('蜘蛛纸牌叠牌与搬运', () => {
  it('桌面列只比大小不限花色,空列接任意牌', () => {
    expect(canStackOnTableau(null, card('S', 3))).toBe(true)
    expect(canStackOnTableau(card('H', 9), card('S', 8))).toBe(true)
    expect(canStackOnTableau(card('H', 9), card('S', 7))).toBe(false)
    expect(canStackOnTableau({ ...card('H', 9), faceUp: false }, card('S', 8))).toBe(false)
  })

  it('整段搬运要求同花色连续降序', () => {
    const state = blankState()
    state.tableau[0] = [card('S', 9, true, 1), card('S', 8, true, 2), card('H', 7, true, 3)]
    expect(sequenceAt(state, 0, 0)).toBeNull()
    expect(sequenceAt(state, 0, 1)).toBeNull()
    state.tableau[0] = [card('S', 9, true, 1), card('S', 8, true, 2), card('S', 7, true, 3)]
    expect(sequenceAt(state, 0, 0).map((c) => c.rank)).toEqual([9, 8, 7])
    expect(sequenceAt(state, 0, 1).map((c) => c.rank)).toEqual([8, 7])
  })

  it('搬走整段后翻开下张背面牌并加分', () => {
    const state = blankState()
    state.tableau[0] = [card('S', 5, false, 1), card('S', 4, true, 2)]
    state.tableau[1] = [card('H', 5, true, 3)]
    const next = move(state, { type: 'tableau', pile: 0, index: 1 }, { type: 'tableau', pile: 1 })
    expect(next.tableau[0]).toHaveLength(1)
    expect(next.tableau[0][0].faceUp).toBe(true)
    expect(next.tableau[1].map((c) => c.rank)).toEqual([5, 4])
    expect(next.score).toBe(5)
    expect(next.moves).toBe(1)
  })

  it('凑齐 K→A 同花色自动收走并记一组完成', () => {
    const state = blankState()
    state.tableau[0] = fullRun('S').slice(0, 12) // K..2
    state.tableau[1] = [card('S', 1, true, 100)]
    const next = move(state, { type: 'tableau', pile: 1, index: 0 }, { type: 'tableau', pile: 0 })
    expect(next.completed).toBe(1)
    expect(next.tableau[0]).toHaveLength(0)
    expect(next.score).toBe(100)
    expect(isWon(next)).toBe(false)
  })
})

describe('蜘蛛纸牌发牌堆', () => {
  it('有空列时不允许发新牌', () => {
    const state = blankState()
    state.stock = Array.from({ length: 10 }, (_, i) => card('S', 2, false, i))
    state.tableau[0] = [card('S', 3, true, 20)]
    expect(canDealStock(state)).toBe(false)
    expect(dealStock(state)).toBeNull()
  })

  it('每列各补一张正面牌,牌堆与步数正确', () => {
    const state = blankState()
    state.stock = Array.from({ length: 10 }, (_, i) => card('S', (i % 13) + 1, false, i))
    state.tableau = state.tableau.map((_, i) => [card('H', 5, true, 30 + i)])
    const next = dealStock(state)
    expect(next.stock).toHaveLength(0)
    expect(next.tableau.every((p) => p.length === 2 && p[1].faceUp)).toBe(true)
    expect(next.moves).toBe(1)
  })
})

describe('蜘蛛纸牌收组翻牌', () => {
  it('收走一组 K→A 后,列底被压住的背面牌会立即翻开', () => {
    const state = blankState()
    state.tableau[1] = [card('C', 9, false, 90)]
    for (let i = 0; i < 12; i++) state.tableau[1].push(card('S', 13 - i, true, i))
    state.tableau[0] = [card('S', 1, true, 20)]
    const next = move(state, { type: 'tableau', pile: 0, index: 0 }, { type: 'tableau', pile: 1 })
    expect(next.completed).toBe(1)
    expect(next.tableau[1]).toHaveLength(1)
    expect(next.tableau[1][0].faceUp).toBe(true)
  })

  it('没有可搬运的牌且不能发牌时判输', () => {
    const deck = createDeck(4)
    const tops = deck.filter((c) => c.rank === 2)
    tops.push(...deck.filter((c) => c.rank === 4).slice(0, COLUMNS - tops.length))
    const topIds = new Set(tops.map((c) => c.id))
    const rest = deck.filter((c) => !topIds.has(c.id))
    const state = blankState()
    state.difficulty = 4
    state.stock = []
    state.tableau = Array.from({ length: COLUMNS }, () => [])
    rest.forEach((c, i) => state.tableau[i % COLUMNS].push({ ...c, faceUp: true }))
    tops.forEach((top, i) => state.tableau[i].push({ ...top, faceUp: true }))
    expect(isStuck(state)).toBe(true)
    expect(findHint(state)).toBeNull()
  })
})

describe('蜘蛛纸牌自动移动与读档', () => {
  it('双点优先接到同花色的大牌上', () => {
    const state = blankState()
    state.tableau[0] = [card('S', 9, true, 1)]
    state.tableau[1] = [card('H', 10, true, 2)]
    state.tableau[2] = [card('S', 10, true, 3)]
    const next = autoMove(state, { type: 'tableau', pile: 0, index: 0 })
    expect(next.tableau[2].map((c) => c.rank)).toEqual([10, 9])
    expect(next.tableau[1]).toHaveLength(1)
  })

  it('读档校验拒绝重复牌、花色与难度不符与正面牌被背面牌压住', () => {
    const state = deal(2, seeded(11))
    expect(isValidState(state)).toBe(true)

    const duplicated = JSON.parse(JSON.stringify(state))
    duplicated.tableau[1][0].id = duplicated.tableau[0][0].id
    expect(isValidState(duplicated)).toBe(false)

    const wrongSuit = JSON.parse(JSON.stringify(state))
    wrongSuit.tableau[0][0].suit = 'D'
    expect(isValidState(wrongSuit)).toBe(false)

    const badOrder = JSON.parse(JSON.stringify(state))
    badOrder.tableau[0][0].faceUp = true
    expect(isValidState(badOrder)).toBe(false)
  })
})
