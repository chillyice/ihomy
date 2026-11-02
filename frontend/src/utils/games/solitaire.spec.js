import { describe, it, expect } from 'vitest'
import {
  STATUS,
  SUITS,
  autoFinish,
  autoMove,
  canPutOnFoundation,
  canStackOnTableau,
  createDeck,
  deal,
  drawStock,
  isWon,
  isValidState,
  move,
} from './solitaire'

function seeded(seed = 1) {
  let s = seed >>> 0
  return () => {
    s = (s * 1664525 + 1013904223) >>> 0
    return s / 4294967296
  }
}

const card = (suit, rank, faceUp = true) => ({ id: SUITS.indexOf(suit) * 13 + rank - 1, suit, rank, faceUp })

function blankState() {
  return {
    stock: [],
    waste: [],
    foundations: { S: [], H: [], D: [], C: [] },
    tableau: [[], [], [], [], [], [], []],
    moves: 0,
    score: 0,
    drawCount: 1,
    status: STATUS.PLAYING,
  }
}

/** 52 张全部背面在牌堆的合法局面,用于构造读档校验的边界样本 */
function stockState() {
  const state = blankState()
  state.stock = createDeck().map((c) => ({ ...c, faceUp: false }))
  return state
}

function takeFromStock(state, suit, rank) {
  const index = state.stock.findIndex((c) => c.suit === suit && c.rank === rank)
  const [taken] = state.stock.splice(index, 1)
  return taken
}

describe('纸牌接龙发牌', () => {
  it('7 列依次 1~7 张且只有列尾正面,其余 24 张进牌堆', () => {
    const state = deal(1, seeded(2))
    expect(state.tableau.map((p) => p.length)).toEqual([1, 2, 3, 4, 5, 6, 7])
    expect(state.stock).toHaveLength(24)
    expect(state.waste).toHaveLength(0)
    for (const pile of state.tableau) {
      expect(pile[pile.length - 1].faceUp).toBe(true)
      expect(pile.slice(0, -1).every((c) => !c.faceUp)).toBe(true)
    }
    const ids = new Set([...state.stock.map((c) => c.id), ...state.tableau.flat().map((c) => c.id)])
    expect(ids.size).toBe(52)
    expect(isValidState(state)).toBe(true)
  })

  it('牌面 52 张 id 唯一、四种花色各 13 张', () => {
    const deck = createDeck()
    expect(deck).toHaveLength(52)
    expect(new Set(deck.map((c) => c.id)).size).toBe(52)
    for (const suit of SUITS) expect(deck.filter((c) => c.suit === suit)).toHaveLength(13)
  })
})

describe('纸牌接龙叠牌规则', () => {
  it('桌面列降序且红黑交替,空列只接 K', () => {
    expect(canStackOnTableau(null, card('S', 13))).toBe(true)
    expect(canStackOnTableau(null, card('S', 12))).toBe(false)
    expect(canStackOnTableau(card('S', 10), card('H', 9))).toBe(true)
    expect(canStackOnTableau(card('S', 10), card('D', 9))).toBe(true)
    expect(canStackOnTableau(card('S', 10), card('S', 9))).toBe(false)
    expect(canStackOnTableau(card('S', 10), card('H', 8))).toBe(false)
    expect(canStackOnTableau({ ...card('S', 10), faceUp: false }, card('H', 9))).toBe(false)
  })

  it('花色堆从 A 起同花色升序', () => {
    const state = blankState()
    state.foundations.S.push(card('S', 1))
    expect(canPutOnFoundation(state, card('S', 2))).toBe(true)
    expect(canPutOnFoundation(state, card('S', 3))).toBe(false)
    expect(canPutOnFoundation(state, card('H', 2))).toBe(false)
  })
})

describe('纸牌接龙翻牌堆', () => {
  it('一次翻 drawCount 张;牌堆空后废牌堆整体扣回并扣分', () => {
    const state = blankState()
    state.stock = [card('S', 3, false), card('H', 4, false), card('D', 5, false)]
    state.drawCount = 3
    state.score = 100
    const drawn = drawStock(state)
    expect(drawn.waste.map((c) => c.rank)).toEqual([5, 4, 3])
    expect(drawn.stock).toHaveLength(0)
    expect(drawn.waste.every((c) => c.faceUp)).toBe(true)

    const recycled = drawStock(drawn)
    expect(recycled.stock.map((c) => c.rank)).toEqual([3, 4, 5])
    expect(recycled.stock.every((c) => !c.faceUp)).toBe(true)
    expect(recycled.waste).toHaveLength(0)
    expect(recycled.score).toBe(80)

    expect(drawStock(blankState())).toBeNull()
  })
})

describe('纸牌接龙移动', () => {
  it('废牌堆顶可以叠到桌面列,花色/顺序不对时拒绝', () => {
    const state = blankState()
    state.waste.push(card('H', 9))
    state.tableau[0].push(card('S', 10))

    const next = move(state, { type: 'waste' }, { type: 'tableau', pile: 0 })
    expect(next).not.toBeNull()
    expect(next.tableau[0].map((c) => c.rank)).toEqual([10, 9])
    expect(next.moves).toBe(1)
    expect(next.score).toBe(5)

    expect(move(state, { type: 'waste' }, { type: 'tableau', pile: 1 })).toBeNull()
    const blackOnBlack = blankState()
    blackOnBlack.waste.push(card('C', 9))
    blackOnBlack.tableau[0].push(card('S', 10))
    expect(move(blackOnBlack, { type: 'waste' }, { type: 'tableau', pile: 0 })).toBeNull()
  })

  it('整段序列可以搬走,露出背面牌自动翻开并加分', () => {
    const state = blankState()
    state.tableau[0] = [card('D', 7, false), card('S', 13), card('H', 12)]
    const next = move(state, { type: 'tableau', pile: 0, index: 1 }, { type: 'tableau', pile: 1 })
    expect(next.tableau[0]).toHaveLength(1)
    expect(next.tableau[0][0].faceUp).toBe(true)
    expect(next.tableau[1].map((c) => c.rank)).toEqual([13, 12])
    expect(next.score).toBe(5)
  })

  it('不能把牌放回自己所在的列', () => {
    const state = blankState()
    state.tableau[0] = [card('S', 13), card('H', 12)]
    expect(move(state, { type: 'tableau', pile: 0, index: 1 }, { type: 'tableau', pile: 0 })).toBeNull()
  })

  it('收到花色堆加 10 分,全部收完判胜', () => {
    const state = blankState()
    state.waste.push(card('S', 1))
    const next = move(state, { type: 'waste' }, { type: 'foundation', suit: 'S' })
    expect(next.foundations.S).toHaveLength(1)
    expect(next.score).toBe(10)
    expect(next.status).toBe(STATUS.PLAYING)

    const won = blankState()
    SUITS.forEach((suit) => {
      for (let rank = 1; rank <= 12; rank++) won.foundations[suit].push(card(suit, rank))
    })
    won.foundations.S.push(card('S', 13))
    won.foundations.H.push(card('H', 13))
    won.foundations.D.push(card('D', 13))
    won.waste.push(card('C', 13))
    const finished = move(won, { type: 'waste' }, { type: 'foundation', suit: 'C' })
    expect(finished.status).toBe(STATUS.WON)
    expect(isWon(finished)).toBe(true)
  })
})

describe('纸牌接龙自动操作', () => {
  it('双点优先收花色堆', () => {
    const state = blankState()
    state.waste.push(card('H', 1))
    const next = autoMove(state, { type: 'waste' })
    expect(next.foundations.H).toHaveLength(1)
  })

  it('自动收牌会反复把桌面列顶与废牌堆顶送进花色堆', () => {
    const state = blankState()
    state.waste.push(card('S', 1))
    state.tableau[0] = [card('H', 1)]
    state.tableau[1] = [card('D', 2), card('D', 1)]
    state.tableau[2] = [card('C', 1)]
    const next = autoFinish(state)
    expect(next.foundations.S).toHaveLength(1)
    expect(next.foundations.H).toHaveLength(1)
    expect(next.foundations.D.map((c) => c.rank)).toEqual([1, 2])
    expect(next.foundations.C).toHaveLength(1)
    expect(next.status).toBe(STATUS.PLAYING)
    expect(autoFinish(next)).toBe(next)
  })
})

describe('纸牌接龙读档校验', () => {
  it('合法发牌可通过;重复牌/正面牌后夹背面牌会被拒绝', () => {
    const state = deal(3, seeded(7))
    expect(isValidState(state)).toBe(true)
    expect(isValidState(null)).toBe(false)

    const duplicate = JSON.parse(JSON.stringify(state))
    duplicate.waste.push({ ...duplicate.tableau[0][0], faceUp: true })
    expect(isValidState(duplicate)).toBe(false)

    const badOrder = stockState()
    expect(isValidState(badOrder)).toBe(true)
    badOrder.tableau[0] = [
      { ...takeFromStock(badOrder, 'S', 13), faceUp: true },
      { ...takeFromStock(badOrder, 'H', 12), faceUp: false },
    ]
    expect(isValidState(badOrder)).toBe(false)
  })

  it('花色堆不是从 A 起升序会被拒绝', () => {
    const state = stockState()
    state.foundations.S.push({ ...takeFromStock(state, 'S', 1), faceUp: true })
    expect(isValidState(state)).toBe(true)
    state.foundations.S.push({ ...takeFromStock(state, 'S', 3), faceUp: true })
    expect(isValidState(state)).toBe(false)
  })
})
