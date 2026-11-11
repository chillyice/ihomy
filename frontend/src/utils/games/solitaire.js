// 纸牌接龙(Klondike)纯逻辑:洗牌发牌 / 翻牌堆 / 收牌到花色堆 / 桌面列叠牌 / 撤销快照校验。
// 牌用 { id, suit, rank, faceUp } 表示:id = 花色序号 * 13 + rank - 1,suit ∈ S/H/D/C,rank 1~13(A=1,K=13)。
// 桌面列(tableau)自下而上降序、红黑交替;花色堆(foundations)从 A 起同花色升序;空列只接 K。
// 所有函数不改入参,返回新状态;非法移动返回 null,页面据此不落盘、不记步数。

export const SUITS = ['S', 'H', 'D', 'C']
export const RED_SUITS = new Set(['H', 'D'])
export const TABLEAU_PILES = 7
export const DRAW_COUNTS = [1, 3]

export const STATUS = {
  PLAYING: 'PLAYING',
  WON: 'WON',
  LOST: 'LOST',
}

export const cardColor = (card) => (RED_SUITS.has(card.suit) ? 'red' : 'black')

/** 52 张牌,id 稳定,便于组件做 key 与读档校验 */
export function createDeck() {
  const deck = []
  SUITS.forEach((suit, suitIndex) => {
    for (let rank = 1; rank <= 13; rank++) {
      deck.push({ id: suitIndex * 13 + rank - 1, suit, rank, faceUp: false })
    }
  })
  return deck
}

/** Fisher-Yates 洗牌(不改入参) */
export function shuffle(deck, random = Math.random) {
  const out = deck.map((c) => ({ ...c }))
  for (let i = out.length - 1; i > 0; i--) {
    const j = Math.floor(random() * (i + 1))
    const tmp = out[i]
    out[i] = out[j]
    out[j] = tmp
  }
  return out
}

export function cloneState(state) {
  const foundations = {}
  for (const suit of SUITS) foundations[suit] = state.foundations[suit].map((c) => ({ ...c }))
  return {
    ...state,
    stock: state.stock.map((c) => ({ ...c })),
    waste: state.waste.map((c) => ({ ...c })),
    foundations,
    tableau: state.tableau.map((pile) => pile.map((c) => ({ ...c }))),
  }
}

/**
 * 发牌:7 列依次 1~7 张,每列最后一张翻开;其余 24 张进牌堆(全部背面)。
 * drawCount=3 时一次翻三张。
 */
export function deal(drawCount = 1, random = Math.random) {
  const deck = shuffle(createDeck(), random)
  const tableau = []
  let cursor = 0
  for (let i = 0; i < TABLEAU_PILES; i++) {
    const pile = deck.slice(cursor, cursor + i + 1)
    cursor += i + 1
    pile[pile.length - 1].faceUp = true
    tableau.push(pile)
  }
  const stock = deck.slice(cursor).map((c) => ({ ...c, faceUp: false }))
  return {
    stock,
    waste: [],
    foundations: { S: [], H: [], D: [], C: [] },
    tableau,
    moves: 0,
    score: 0,
    drawCount: DRAW_COUNTS.includes(drawCount) ? drawCount : 1,
    status: STATUS.PLAYING,
  }
}

/** 桌面列能否叠牌:空列只接 K,否则降一位且红黑交替 */
export function canStackOnTableau(top, card) {
  if (!top) return card.rank === 13
  if (!top.faceUp) return false
  return top.rank === card.rank + 1 && cardColor(top) !== cardColor(card)
}

/** 能否收到花色堆:同花色且正好是下一张(从 A 起) */
export function canPutOnFoundation(state, card) {
  const pile = state.foundations[card.suit]
  return !!pile && pile.length + 1 === card.rank
}

/** 桌面上从 index 到列尾是否是一条合法可整体搬运的序列(需全部正面) */
export function sequenceAt(state, pileIndex, index) {
  const pile = state.tableau[pileIndex]
  if (!pile || index < 0 || index >= pile.length || !pile[index].faceUp) return null
  for (let i = index; i < pile.length - 1; i++) {
    if (!canStackOnTableau(pile[i], pile[i + 1])) return null
  }
  return pile.slice(index)
}

/** 取源位置的牌组;牌堆/花色堆最多一张,桌面列可以是一段序列 */
export function sourceCards(state, from) {
  if (!from) return null
  if (from.type === 'waste') return state.waste.length ? [state.waste[state.waste.length - 1]] : null
  if (from.type === 'foundation') {
    const pile = state.foundations[from.suit]
    return pile && pile.length ? [pile[pile.length - 1]] : null
  }
  if (from.type === 'tableau') return sequenceAt(state, from.pile, from.index)
  return null
}

const removeFromSource = (state, from, count) => {
  if (from.type === 'waste') state.waste.pop()
  else if (from.type === 'foundation') state.foundations[from.suit].pop()
  else state.tableau[from.pile].splice(from.index, count)
}

/**
 * 执行一次移动。from: {type:'waste'} | {type:'foundation',suit} | {type:'tableau',pile,index}
 * to:   {type:'foundation',suit} | {type:'tableau',pile}
 * 非法移动返回 null。
 */
export function move(state, from, to) {
  if (!state || state.status !== STATUS.PLAYING || !from || !to) return null
  const cards = sourceCards(state, from)
  if (!cards || !cards.length) return null
  const head = cards[0]

  if (to.type === 'foundation') {
    if (cards.length !== 1) return null
    const suit = to.suit || head.suit
    if (suit !== head.suit || !canPutOnFoundation(state, head)) return null
  } else if (to.type === 'tableau') {
    const pile = state.tableau[to.pile]
    if (!pile) return null
    if (from.type === 'tableau' && from.pile === to.pile) return null
    const top = pile.length ? pile[pile.length - 1] : null
    if (!canStackOnTableau(top, head)) return null
  } else {
    return null
  }

  const next = cloneState(state)
  removeFromSource(next, from, cards.length)

  let gained = 0
  if (to.type === 'foundation') {
    next.foundations[to.suit || head.suit].push({ ...head, faceUp: true })
    gained += 10
  } else {
    for (const card of cards) next.tableau[to.pile].push({ ...card, faceUp: true })
    if (from.type === 'waste') gained += 5
    if (from.type === 'foundation') gained -= 15
  }

  // 桌面列搬走最后一段后,新露出的牌自动翻面
  if (from.type === 'tableau') {
    const pile = next.tableau[from.pile]
    if (pile.length && !pile[pile.length - 1].faceUp) {
      pile[pile.length - 1].faceUp = true
      gained += 5
    }
  }

  next.moves = state.moves + 1
  next.score = Math.max(0, state.score + gained)
  return settle(next)
}

/** 翻牌堆:有牌就翻 drawCount 张到废牌堆,牌堆空则把废牌堆整体扣回牌堆(扣 20 分) */
export function drawStock(state) {
  if (!state || state.status !== STATUS.PLAYING) return null
  const next = cloneState(state)
  next.moves = state.moves + 1

  if (next.stock.length) {
    const count = Math.min(next.drawCount, next.stock.length)
    for (let i = 0; i < count; i++) {
      const card = next.stock.pop()
      card.faceUp = true
      next.waste.push(card)
    }
    return settle(next)
  }
  if (!next.waste.length) return null

  while (next.waste.length) {
    const card = next.waste.pop()
    card.faceUp = false
    next.stock.push(card)
  }
  next.score = Math.max(0, state.score - 20)
  return settle(next)
}

/** 双点自动收纳:先试花色堆,再试桌面列(整列搬到空列的无意义移动跳过) */
export function autoMove(state, from) {
  if (!state || state.status !== STATUS.PLAYING) return null
  const cards = sourceCards(state, from)
  if (!cards || !cards.length) return null
  const head = cards[0]

  if (cards.length === 1 && canPutOnFoundation(state, head)) {
    return move(state, from, { type: 'foundation', suit: head.suit })
  }
  for (let pile = 0; pile < TABLEAU_PILES; pile++) {
    if (from.type === 'tableau' && from.pile === pile) continue
    const target = state.tableau[pile]
    const top = target.length ? target[target.length - 1] : null
    if (!canStackOnTableau(top, head)) continue
    // 把一整列搬到另一个空列没有意义
    if (from.type === 'tableau' && !top && from.index === 0) continue
    return move(state, from, { type: 'tableau', pile })
  }
  return null
}

/** 能收就收:反复把废牌堆顶与桌面列顶的牌送进花色堆,直到没有可收的牌 */
export function autoFinish(state) {
  let current = state
  if (!current || current.status !== STATUS.PLAYING) return current
  for (;;) {
    const candidates = []
    if (current.waste.length) candidates.push({ type: 'waste' })
    current.tableau.forEach((pile, pileIndex) => {
      const index = pile.length - 1
      if (index >= 0 && pile[index].faceUp) candidates.push({ type: 'tableau', pile: pileIndex, index })
    })
    let moved = false
    for (const from of candidates) {
      const cards = sourceCards(current, from)
      if (!cards || cards.length !== 1 || !canPutOnFoundation(current, cards[0])) continue
      const next = move(current, from, { type: 'foundation', suit: cards[0].suit })
      if (next) {
        current = next
        moved = true
        break
      }
    }
    if (!moved) return current
  }
}

export function isWon(state) {
  return SUITS.every((suit) => state.foundations[suit].length === 13)
}

/** 桌面上从 index 搬走是否会露出背面牌(列内背面牌总在正面牌之前) */
const revealsHidden = (pile, index) => index > 0 && !pile[index - 1].faceUp

/** 是否还存在任何合法移动(不含翻牌堆;花色堆回收到桌面视为无意义) */
function hasAnyMove(state) {
  if (state.waste.length) {
    const card = state.waste[state.waste.length - 1]
    if (canPutOnFoundation(state, card)) return true
    for (const pile of state.tableau) {
      const top = pile.length ? pile[pile.length - 1] : null
      if (canStackOnTableau(top, card)) return true
    }
  }
  for (let pi = 0; pi < state.tableau.length; pi++) {
    const pile = state.tableau[pi]
    for (let index = 0; index < pile.length; index++) {
      if (!pile[index].faceUp) continue
      const cards = sequenceAt(state, pi, index)
      if (!cards) continue
      if (cards.length === 1 && canPutOnFoundation(state, cards[0])) return true
      for (let pj = 0; pj < state.tableau.length; pj++) {
        if (pj === pi) continue
        const target = state.tableau[pj]
        const top = target.length ? target[target.length - 1] : null
        if (!canStackOnTableau(top, cards[0])) continue
        if (!top && index === 0) continue
        return true
      }
    }
  }
  return false
}

/** 无路可走判定:牌堆与废牌堆都空(再也翻不出牌)且桌面/花色堆都没有合法移动。
 * 只做保守判定——只要还能翻牌或还有一步可走,就不算输。 */
export function isStuck(state) {
  if (!state || state.status !== STATUS.PLAYING) return false
  if (isWon(state)) return false
  const total =
    state.stock.length +
    state.waste.length +
    state.tableau.reduce((n, pile) => n + pile.length, 0) +
    SUITS.reduce((n, suit) => n + state.foundations[suit].length, 0)
  if (total !== 52) return false
  if (state.stock.length || state.waste.length) return false
  return !hasAnyMove(state)
}

/** 结算通关 / 无路可走,写回 status */
function settle(state) {
  if (isWon(state)) state.status = STATUS.WON
  else if (isStuck(state)) state.status = STATUS.LOST
  return state
}

/**
 * 给出一个建议走法:优先收进花色堆,其次是能露出背面牌的搬运,最后才翻牌堆。
 * 返回 {from,to} 或 {kind:'draw'};没有建议时返回 null。
 */
export function findHint(state) {
  if (!state || state.status !== STATUS.PLAYING) return null
  const candidates = []
  const add = (score, from, to) => candidates.push({ score, from, to })

  if (state.waste.length) {
    const card = state.waste[state.waste.length - 1]
    if (canPutOnFoundation(state, card)) add(120, { type: 'waste' }, { type: 'foundation', suit: card.suit })
    for (let pi = 0; pi < state.tableau.length; pi++) {
      const pile = state.tableau[pi]
      const top = pile.length ? pile[pile.length - 1] : null
      if (!canStackOnTableau(top, card)) continue
      add(top ? 80 : 60, { type: 'waste' }, { type: 'tableau', pile: pi })
    }
  }

  for (let pi = 0; pi < state.tableau.length; pi++) {
    const pile = state.tableau[pi]
    for (let index = 0; index < pile.length; index++) {
      if (!pile[index].faceUp) continue
      const cards = sequenceAt(state, pi, index)
      if (!cards) continue
      if (cards.length === 1 && canPutOnFoundation(state, cards[0])) {
        add(110, { type: 'tableau', pile: pi, index }, { type: 'foundation', suit: cards[0].suit })
      }
      for (let pj = 0; pj < state.tableau.length; pj++) {
        if (pj === pi) continue
        const target = state.tableau[pj]
        const top = target.length ? target[target.length - 1] : null
        if (!canStackOnTableau(top, cards[0])) continue
        if (!top && index === 0) continue
        let score = 50
        if (revealsHidden(pile, index)) score += 40
        if (!top) score -= 10
        add(score, { type: 'tableau', pile: pi, index }, { type: 'tableau', pile: pj })
      }
    }
  }

  if (candidates.length) {
    candidates.sort((a, b) => b.score - a.score)
    return { from: candidates[0].from, to: candidates[0].to }
  }
  if (state.stock.length || state.waste.length) return { kind: 'draw' }
  return null
}

const isCard = (c) =>
  !!c &&
  Number.isInteger(c.id) &&
  SUITS.includes(c.suit) &&
  Number.isInteger(c.rank) &&
  c.rank >= 1 &&
  c.rank <= 13 &&
  typeof c.faceUp === 'boolean'

const isOrderedRun = (pile) => {
  for (let i = 0; i < pile.length - 1; i++) {
    if (!pile[i].faceUp || !canStackOnTableau(pile[i], pile[i + 1])) return false
  }
  return true
}

/** 读档校验:52 张 id 不重不漏、牌堆/废牌堆朝向正确、桌面列正面牌连续收尾、花色堆从 A 起升序 */
export function isValidState(raw) {
  if (!raw || typeof raw !== 'object') return false
  if (![STATUS.PLAYING, STATUS.WON, STATUS.LOST].includes(raw.status)) return false
  if (!Number.isInteger(raw.moves) || raw.moves < 0) return false
  if (!Number.isInteger(raw.score) || raw.score < 0) return false
  if (!DRAW_COUNTS.includes(raw.drawCount)) return false
  if (!Array.isArray(raw.stock) || !Array.isArray(raw.waste)) return false
  if (!Array.isArray(raw.tableau) || raw.tableau.length !== TABLEAU_PILES) return false
  if (!raw.foundations || typeof raw.foundations !== 'object') return false

  const ids = new Set()
  const collect = (card, { faceUp }) => {
    if (!isCard(card) || ids.has(card.id)) return false
    if (card.faceUp !== faceUp) return false
    ids.add(card.id)
    return true
  }

  if (!raw.stock.every((c) => collect(c, { faceUp: false }))) return false
  if (!raw.waste.every((c) => collect(c, { faceUp: true }))) return false
  for (const suit of SUITS) {
    const pile = raw.foundations[suit]
    if (!Array.isArray(pile)) return false
    for (let i = 0; i < pile.length; i++) {
      const card = pile[i]
      if (!collect(card, { faceUp: true })) return false
      if (card.suit !== suit || card.rank !== i + 1) return false
    }
  }
  for (const pile of raw.tableau) {
    if (!Array.isArray(pile)) return false
    let seenFaceUp = false
    for (const card of pile) {
      if (!isCard(card) || ids.has(card.id)) return false
      if (card.faceUp) seenFaceUp = true
      else if (seenFaceUp) return false
      ids.add(card.id)
    }
    const faceUpStart = pile.findIndex((c) => c.faceUp)
    if (faceUpStart >= 0 && !isOrderedRun(pile.slice(faceUpStart))) return false
  }
  if (ids.size !== 52) return false
  if (raw.status === STATUS.WON && !isWon(raw)) return false
  return true
}
