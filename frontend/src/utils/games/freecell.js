// 自由纸牌(FreeCell)纯逻辑:52 张全明牌、八列、四个自由格、四个花色堆。
// 桌面列红黑交替降序;整段搬运受空自由格与空列数限制;A 起同花色升序收进花色堆,收满四堆通关。
// 牌用 { id, suit, rank, faceUp } 表示(全明牌,faceUp 恒为 true),id 与 solitaire.js 同口径。
// 所有函数不改入参,返回新状态;非法移动返回 null,页面据此不落盘、不记步数。

import { SUITS, cardColor, createDeck, shuffle } from './solitaire'

export { SUITS, cardColor }

export const COLUMNS = 8
export const FREE_CELLS = 4

export const STATUS = {
  PLAYING: 'PLAYING',
  WON: 'WON',
  LOST: 'LOST',
}

export function deal(random = Math.random) {
  const deck = shuffle(createDeck(), random)
  const tableau = []
  const counts = [7, 7, 7, 7, 6, 6, 6, 6]
  let cursor = 0
  for (const count of counts) {
    tableau.push(deck.slice(cursor, cursor + count).map((card) => ({ ...card, faceUp: true })))
    cursor += count
  }
  return {
    tableau,
    freeCells: Array.from({ length: FREE_CELLS }, () => null),
    foundations: { S: [], H: [], D: [], C: [] },
    moves: 0,
    score: 0,
    status: STATUS.PLAYING,
  }
}

export function cloneState(state) {
  const foundations = {}
  for (const suit of SUITS) foundations[suit] = state.foundations[suit].map((c) => ({ ...c }))
  return {
    ...state,
    tableau: state.tableau.map((pile) => pile.map((c) => ({ ...c }))),
    freeCells: state.freeCells.map((c) => (c ? { ...c } : null)),
    foundations,
  }
}

/** 桌面列能否叠牌:空列接任意牌,否则降一位且红黑交替 */
export function canStackOnTableau(top, card) {
  if (!top) return true
  if (!top.faceUp) return false
  return top.rank === card.rank + 1 && cardColor(top) !== cardColor(card)
}

/** 能否收到花色堆:同花色且正好是下一张(从 A 起) */
export function canPutOnFoundation(state, card) {
  const pile = state.foundations[card.suit]
  return !!pile && pile.length + 1 === card.rank
}

/** 桌面上从 index 到列尾是否是一条红黑交替降序序列 */
export function sequenceAt(state, pileIndex, index) {
  const pile = state.tableau[pileIndex]
  if (!pile || index < 0 || index >= pile.length || !pile[index].faceUp) return null
  for (let i = index; i < pile.length - 1; i++) {
    if (!canStackOnTableau(pile[i], pile[i + 1])) return null
  }
  return pile.slice(index)
}

export function sourceCards(state, from) {
  if (!from) return null
  if (from.type === 'tableau') return sequenceAt(state, from.pile, from.index)
  if (from.type === 'free') {
    const card = state.freeCells[from.cell]
    return card ? [card] : null
  }
  if (from.type === 'foundation') {
    const pile = state.foundations[from.suit]
    return pile && pile.length ? [pile[pile.length - 1]] : null
  }
  return null
}

export const emptyCellCount = (state) => state.freeCells.filter((card) => !card).length
export const emptyColumnCount = (state) => state.tableau.filter((pile) => !pile.length).length

/** 一次最多能搬几张:空自由格 + 1,每多一个空列翻倍(目标空列不计入) */
export function maxMoveCount(state, toEmptyColumn = false) {
  const free = emptyCellCount(state)
  let empty = emptyColumnCount(state)
  if (toEmptyColumn) empty = Math.max(0, empty - 1)
  return (free + 1) * 2 ** empty
}

const removeFromSource = (state, from, count) => {
  if (from.type === 'tableau') state.tableau[from.pile].splice(from.index, count)
  else if (from.type === 'free') state.freeCells[from.cell] = null
  else state.foundations[from.suit].pop()
}

/**
 * 执行一次移动。
 * from: {type:'tableau',pile,index} | {type:'free',cell} | {type:'foundation',suit}
 * to:   {type:'tableau',pile} | {type:'free',cell} | {type:'foundation',suit}
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
  } else if (to.type === 'free') {
    if (cards.length !== 1) return null
    if (!Number.isInteger(to.cell) || to.cell < 0 || to.cell >= FREE_CELLS) return null
    if (from.type === 'free' && from.cell === to.cell) return null
    if (state.freeCells[to.cell]) return null
  } else if (to.type === 'tableau') {
    const target = state.tableau[to.pile]
    if (!target) return null
    if (from.type === 'tableau' && from.pile === to.pile) return null
    const top = target.length ? target[target.length - 1] : null
    if (!canStackOnTableau(top, head)) return null
    // 把整列挪到另一个空列只是原地换位置,不产生任何进展
    if (!top && from.type === 'tableau' && cards.length === state.tableau[from.pile].length) return null
    if (cards.length > maxMoveCount(state, !top)) return null
  } else {
    return null
  }

  const next = cloneState(state)
  removeFromSource(next, from, cards.length)

  let gained = 0
  if (to.type === 'foundation') {
    next.foundations[to.suit || head.suit].push({ ...head, faceUp: true })
    gained += 10
  } else if (to.type === 'free') {
    next.freeCells[to.cell] = { ...head, faceUp: true }
  } else {
    for (const card of cards) next.tableau[to.pile].push({ ...card, faceUp: true })
    if (from.type === 'foundation') gained -= 10
  }

  next.moves = state.moves + 1
  next.score = Math.max(0, state.score + gained)
  return settle(next)
}

/** 双点自动收纳:优先花色堆,其次空自由格,最后桌面列 */
export function autoMove(state, from) {
  if (!state || state.status !== STATUS.PLAYING) return null
  const cards = sourceCards(state, from)
  if (!cards || !cards.length) return null
  const head = cards[0]

  if (cards.length === 1 && canPutOnFoundation(state, head)) {
    return move(state, from, { type: 'foundation', suit: head.suit })
  }
  if (cards.length === 1 && from.type !== 'free') {
    const cell = state.freeCells.findIndex((card) => !card)
    if (cell >= 0) return move(state, from, { type: 'free', cell })
  }
  for (let pile = 0; pile < COLUMNS; pile++) {
    if (from.type === 'tableau' && from.pile === pile) continue
    const target = state.tableau[pile]
    const top = target.length ? target[target.length - 1] : null
    if (!canStackOnTableau(top, head)) continue
    if (!top && from.type === 'tableau' && cards.length === state.tableau[from.pile].length) continue
    if (cards.length > maxMoveCount(state, !top)) continue
    return move(state, from, { type: 'tableau', pile })
  }
  return null
}

/** 能收就收:反复把自由格与桌面列顶的牌送进花色堆 */
export function autoFinish(state) {
  let current = state
  if (!current || current.status !== STATUS.PLAYING) return current
  for (;;) {
    const candidates = []
    current.freeCells.forEach((card, cell) => {
      if (card) candidates.push({ type: 'free', cell })
    })
    current.tableau.forEach((pile, pileIndex) => {
      const index = pile.length - 1
      if (index >= 0) candidates.push({ type: 'tableau', pile: pileIndex, index })
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

const sourceEntries = (state) => {
  const list = []
  state.tableau.forEach((pile, pileIndex) => {
    for (let index = 0; index < pile.length; index++) {
      if (sequenceAt(state, pileIndex, index)) list.push({ type: 'tableau', pile: pileIndex, index })
    }
  })
  state.freeCells.forEach((card, cell) => {
    if (card) list.push({ type: 'free', cell })
  })
  for (const suit of SUITS) {
    if (state.foundations[suit].length) list.push({ type: 'foundation', suit })
  }
  return list
}

/** 是否还有合法移动(整列搬到空列不算) */
function hasAnyMove(state) {
  for (const from of sourceEntries(state)) {
    const cards = sourceCards(state, from)
    if (!cards || !cards.length) continue
    const head = cards[0]
    if (cards.length === 1 && canPutOnFoundation(state, head)) return true
    if (cards.length === 1 && from.type !== 'free' && emptyCellCount(state) > 0) return true
    for (let pile = 0; pile < COLUMNS; pile++) {
      if (from.type === 'tableau' && from.pile === pile) continue
      const target = state.tableau[pile]
      const top = target.length ? target[target.length - 1] : null
      if (!canStackOnTableau(top, head)) continue
      if (!top && from.type === 'tableau' && cards.length === state.tableau[from.pile].length) continue
      if (cards.length > maxMoveCount(state, !top)) continue
      return true
    }
  }
  return false
}

/** 无路可走:没有任何合法移动(自由格与桌面都动不了) */
export function isStuck(state) {
  if (!state || state.status !== STATUS.PLAYING) return false
  if (isWon(state)) return false
  const total =
    state.tableau.reduce((n, pile) => n + pile.length, 0) +
    state.freeCells.filter(Boolean).length +
    SUITS.reduce((n, suit) => n + state.foundations[suit].length, 0)
  if (total !== 52) return false
  return !hasAnyMove(state)
}

function settle(state) {
  if (isWon(state)) state.status = STATUS.WON
  else if (isStuck(state)) state.status = STATUS.LOST
  return state
}

/**
 * 给出一个建议走法:优先收进花色堆,其次是能从自由格/列里腾出牌的操作。
 * 返回 {from,to};没有建议时返回 null。
 */
export function findHint(state) {
  if (!state || state.status !== STATUS.PLAYING) return null
  let best = null
  let bestScore = -1
  for (const from of sourceEntries(state)) {
    const cards = sourceCards(state, from)
    if (!cards || !cards.length) continue
    const head = cards[0]
    const consider = (score, to) => {
      if (score > bestScore) {
        bestScore = score
        best = { from, to }
      }
    }
    if (cards.length === 1 && canPutOnFoundation(state, head)) {
      consider(120, { type: 'foundation', suit: head.suit })
    }
    if (cards.length === 1 && from.type !== 'free') {
      const cell = state.freeCells.findIndex((card) => !card)
      if (cell >= 0) consider(from.type === 'foundation' ? 20 : 50, { type: 'free', cell })
    }
    for (let pile = 0; pile < COLUMNS; pile++) {
      if (from.type === 'tableau' && from.pile === pile) continue
      const target = state.tableau[pile]
      const top = target.length ? target[target.length - 1] : null
      if (!canStackOnTableau(top, head)) continue
      if (!top && from.type === 'tableau' && cards.length === state.tableau[from.pile].length) continue
      if (cards.length > maxMoveCount(state, !top)) continue
      let score = top ? 70 : 40
      if (from.type === 'free') score += 10
      if (from.type === 'tableau' && from.index > 0) score += 10
      consider(score, { type: 'tableau', pile })
    }
  }
  return best
}

const isCard = (c) =>
  !!c &&
  Number.isInteger(c.id) &&
  c.id >= 0 &&
  c.id < 52 &&
  SUITS.includes(c.suit) &&
  Number.isInteger(c.rank) &&
  c.rank >= 1 &&
  c.rank <= 13 &&
  c.faceUp === true

/** 读档校验:52 张 id 不重不漏、八列四格结构正确、花色堆从 A 起升序 */
export function isValidState(raw) {
  if (!raw || typeof raw !== 'object') return false
  if (![STATUS.PLAYING, STATUS.WON, STATUS.LOST].includes(raw.status)) return false
  if (!Number.isInteger(raw.moves) || raw.moves < 0) return false
  if (!Number.isInteger(raw.score) || raw.score < 0) return false
  if (!Array.isArray(raw.tableau) || raw.tableau.length !== COLUMNS) return false
  if (!Array.isArray(raw.freeCells) || raw.freeCells.length !== FREE_CELLS) return false
  if (!raw.foundations || typeof raw.foundations !== 'object') return false

  const ids = new Set()
  const collect = (card) => {
    if (!isCard(card) || ids.has(card.id)) return false
    ids.add(card.id)
    return true
  }

  for (const pile of raw.tableau) {
    if (!Array.isArray(pile)) return false
    if (!pile.every(collect)) return false
  }
  for (const card of raw.freeCells) {
    if (card && !collect(card)) return false
  }
  for (const suit of SUITS) {
    const pile = raw.foundations[suit]
    if (!Array.isArray(pile)) return false
    for (let i = 0; i < pile.length; i++) {
      const card = pile[i]
      if (!collect(card)) return false
      if (card.suit !== suit || card.rank !== i + 1) return false
    }
  }
  if (ids.size !== 52) return false
  if (raw.status === STATUS.WON && !isWon(raw)) return false
  return true
}
