// 蜘蛛纸牌(Spider)纯逻辑:两副牌(104 张)、十列、按难度使用 1/2/4 种花色。
// 桌面列内任意花色都能降序叠放,但整段搬运必须是同花色连续降序;凑齐 K→A 同花色自动收走,收满 8 组通关。
// 牌用 { id, suit, rank, faceUp } 表示;id 在 0~103 内唯一(每副牌 13 张,花色随难度变化)。
// 所有函数不改入参,返回新状态;非法移动/发牌返回 null,页面据此不落盘、不记步数。

export const SUITS = ['S', 'H', 'D', 'C']
export const RED_SUITS = new Set(['H', 'D'])
export const COLUMNS = 10
export const SEQUENCE_LEN = 13
export const COMPLETE_TARGET = 8
export const DIFFICULTIES = [1, 2, 4]
export const STATUS = {
  PLAYING: 'PLAYING',
  WON: 'WON',
  LOST: 'LOST',
}

/** 难度 → 八副牌的花色序列(1 色简单 / 2 色普通 / 4 色困难) */
const SUIT_SETS = {
  1: ['S', 'S', 'S', 'S', 'S', 'S', 'S', 'S'],
  2: ['S', 'H', 'S', 'H', 'S', 'H', 'S', 'H'],
  4: ['S', 'H', 'D', 'C', 'S', 'H', 'D', 'C'],
}

export const cardColor = (card) => (RED_SUITS.has(card.suit) ? 'red' : 'black')

export function createDeck(difficulty = 1) {
  const suits = SUIT_SETS[difficulty] || SUIT_SETS[1]
  const deck = []
  suits.forEach((suit, set) => {
    for (let rank = 1; rank <= 13; rank++) {
      deck.push({ id: set * 13 + rank - 1, suit, rank, faceUp: false })
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
  return {
    ...state,
    stock: state.stock.map((c) => ({ ...c })),
    tableau: state.tableau.map((pile) => pile.map((c) => ({ ...c }))),
  }
}

/**
 * 发牌:前 4 列 6 张、后 6 列 5 张(共 54 张),各列只有列尾正面;剩余 50 张进牌堆。
 */
export function deal(difficulty = 1, random = Math.random) {
  const diff = DIFFICULTIES.includes(difficulty) ? difficulty : 1
  const deck = shuffle(createDeck(diff), random)
  const tableau = []
  let cursor = 0
  for (let i = 0; i < COLUMNS; i++) {
    const count = i < 4 ? 6 : 5
    const pile = deck.slice(cursor, cursor + count)
    cursor += count
    pile[pile.length - 1].faceUp = true
    tableau.push(pile)
  }
  return {
    difficulty: diff,
    stock: deck.slice(cursor).map((c) => ({ ...c, faceUp: false })),
    tableau,
    completed: 0,
    moves: 0,
    score: 0,
    status: STATUS.PLAYING,
  }
}

/** 桌面列只比大小、不限花色:空列接任意牌 */
export function canStackOnTableau(top, card) {
  if (!top) return true
  if (!top.faceUp) return false
  return top.rank === card.rank + 1
}

/** 桌面上从 index 到列尾是否是一条同花色连续降序、可整体搬运的序列 */
export function sequenceAt(state, pileIndex, index) {
  const pile = state.tableau[pileIndex]
  if (!pile || index < 0 || index >= pile.length || !pile[index].faceUp) return null
  for (let i = index; i < pile.length - 1; i++) {
    if (pile[i].suit !== pile[i + 1].suit || pile[i].rank !== pile[i + 1].rank + 1) return null
  }
  return pile.slice(index)
}

export function sourceCards(state, from) {
  if (!from) return null
  if (from.type === 'tableau') return sequenceAt(state, from.pile, from.index)
  return null
}

/** 检查某列尾部是否刚好是 K→A 的同花序列;是则移除并记一组完成 */
const collectCompleted = (state) => {
  let gained = 0
  for (const pile of state.tableau) {
    if (pile.length < SEQUENCE_LEN) continue
    const tail = pile.slice(-SEQUENCE_LEN)
    let ok = tail[0].rank === 13 && tail[0].faceUp
    for (let i = 1; ok && i < SEQUENCE_LEN; i++) {
      ok = tail[i].faceUp && tail[i].suit === tail[0].suit && tail[i].rank === SEQUENCE_LEN - i
    }
    if (!ok) continue
    pile.splice(-SEQUENCE_LEN, SEQUENCE_LEN)
    state.completed += 1
    gained += 100
    // 收走一整组后,列底可能露出背面牌,必须立刻翻开,否则这一列会永远卡住
    if (pile.length && !pile[pile.length - 1].faceUp) {
      pile[pile.length - 1].faceUp = true
      gained += 5
    }
  }
  return gained
}

/**
 * 执行一次移动。from: {type:'tableau',pile,index};to: {type:'tableau',pile}
 * 非法移动返回 null。
 */
export function move(state, from, to) {
  if (!state || state.status !== STATUS.PLAYING || !from || !to) return null
  if (to.type !== 'tableau' || from.type !== 'tableau') return null
  const cards = sourceCards(state, from)
  if (!cards || !cards.length) return null
  if (from.pile === to.pile) return null
  const target = state.tableau[to.pile]
  if (!target) return null
  const top = target.length ? target[target.length - 1] : null
  if (!canStackOnTableau(top, cards[0])) return null

  const next = cloneState(state)
  next.tableau[from.pile].splice(from.index, cards.length)
  for (const card of cards) next.tableau[to.pile].push({ ...card, faceUp: true })

  let gained = 0
  const source = next.tableau[from.pile]
  if (source.length && !source[source.length - 1].faceUp) {
    source[source.length - 1].faceUp = true
    gained += 5
  }
  gained += collectCompleted(next)
  next.moves = state.moves + 1
  next.score = state.score + gained
  return settle(next)
}

/** 牌堆还能不能发:有空列时不允许(标准规则) */
export function canDealStock(state) {
  return !!state && state.status === STATUS.PLAYING && state.stock.length >= COLUMNS && state.tableau.every((p) => p.length > 0)
}

/** 发一排:每列各加一张正面牌 */
export function dealStock(state) {
  if (!canDealStock(state)) return null
  const next = cloneState(state)
  for (const pile of next.tableau) {
    const card = next.stock.pop()
    if (card) pile.push({ ...card, faceUp: true })
  }
  const gained = collectCompleted(next)
  next.moves = state.moves + 1
  next.score = state.score + gained
  return settle(next)
}

/** 双点自动移动:优先同花色接续,其次任意可接列(整列搬到空列视为无意义) */
export function autoMove(state, from) {
  if (!state || state.status !== STATUS.PLAYING) return null
  const cards = sourceCards(state, from)
  if (!cards || !cards.length) return null
  const head = cards[0]
  let fallback = -1
  for (let pile = 0; pile < COLUMNS; pile++) {
    if (pile === from.pile) continue
    const target = state.tableau[pile]
    const top = target.length ? target[target.length - 1] : null
    if (!canStackOnTableau(top, head)) continue
    if (!top && from.index === 0) continue
    if (top && top.suit === head.suit) return move(state, from, { type: 'tableau', pile })
    if (fallback < 0) fallback = pile
  }
  return fallback >= 0 ? move(state, from, { type: 'tableau', pile: fallback }) : null
}

export function isWon(state) {
  return !!state && state.completed >= COMPLETE_TARGET
}

/** 是否还有可搬运的合法移动(整列搬到空列视为无意义) */
function hasAnyMove(state) {
  for (let from = 0; from < COLUMNS; from++) {
    const pile = state.tableau[from]
    for (let index = 0; index < pile.length; index++) {
      if (!pile[index].faceUp) continue
      const cards = sequenceAt(state, from, index)
      if (!cards) continue
      for (let to = 0; to < COLUMNS; to++) {
        if (to === from) continue
        const target = state.tableau[to]
        const top = target.length ? target[target.length - 1] : null
        if (!canStackOnTableau(top, cards[0])) continue
        if (!top && index === 0) continue
        return true
      }
    }
  }
  return false
}

/**
 * 无路可走判定:既不能发新牌,也没有任何合法搬运。
 * 只做保守判定,有任何一步能走都不算输。
 */
export function isStuck(state) {
  if (!state || state.status !== STATUS.PLAYING) return false
  if (isWon(state)) return false
  const total = state.stock.length + state.tableau.reduce((n, pile) => n + pile.length, 0) + state.completed * SEQUENCE_LEN
  if (total !== 104) return false
  if (canDealStock(state)) return false
  return !hasAnyMove(state)
}

/** 结算通关 / 无路可走,写回 status */
function settle(state) {
  if (isWon(state)) state.status = STATUS.WON
  else if (isStuck(state)) state.status = STATUS.LOST
  return state
}

/**
 * 给出一个建议走法:优先搬走能露出背面牌的序列,其次同花色接续,最后才发新牌。
 * 返回 {from,to} 或 {kind:'deal'};没有建议时返回 null。
 */
export function findHint(state) {
  if (!state || state.status !== STATUS.PLAYING) return null
  let best = null
  let bestScore = -1
  for (let from = 0; from < COLUMNS; from++) {
    const pile = state.tableau[from]
    for (let index = 0; index < pile.length; index++) {
      if (!pile[index].faceUp) continue
      const cards = sequenceAt(state, from, index)
      if (!cards) continue
      for (let to = 0; to < COLUMNS; to++) {
        if (to === from) continue
        const target = state.tableau[to]
        const top = target.length ? target[target.length - 1] : null
        if (!canStackOnTableau(top, cards[0])) continue
        if (!top && index === 0) continue
        let score = Math.min(cards.length, 10)
        if (index > 0 && !pile[index - 1].faceUp) score += 100
        if (top && top.suit === cards[0].suit) score += 50
        if (index > 0) score += 20
        if (top) score += 10
        if (score > bestScore) {
          bestScore = score
          best = {
            from: { type: 'tableau', pile: from, index },
            to: { type: 'tableau', pile: to },
          }
        }
      }
    }
  }
  if (best) return best
  if (canDealStock(state)) return { kind: 'deal' }
  return null
}

const isCard = (c) =>
  !!c &&
  Number.isInteger(c.id) &&
  c.id >= 0 &&
  c.id < 104 &&
  SUITS.includes(c.suit) &&
  Number.isInteger(c.rank) &&
  c.rank >= 1 &&
  c.rank <= 13 &&
  typeof c.faceUp === 'boolean'

/** 读档校验:104 张 id 不重不漏、花色与难度一致、背面牌在正面牌之前、完成组数与剩余牌数自洽 */
export function isValidState(raw) {
  if (!raw || typeof raw !== 'object') return false
  if (!DIFFICULTIES.includes(raw.difficulty)) return false
  if (![STATUS.PLAYING, STATUS.WON, STATUS.LOST].includes(raw.status)) return false
  if (!Number.isInteger(raw.moves) || raw.moves < 0) return false
  if (!Number.isInteger(raw.score) || raw.score < 0) return false
  if (!Number.isInteger(raw.completed) || raw.completed < 0 || raw.completed > COMPLETE_TARGET) return false
  if (!Array.isArray(raw.stock) || !Array.isArray(raw.tableau) || raw.tableau.length !== COLUMNS) return false

  const suits = SUIT_SETS[raw.difficulty]
  const ids = new Set()
  let count = 0
  const collect = (card, faceUp) => {
    if (!isCard(card) || ids.has(card.id) || card.faceUp !== faceUp) return false
    if (card.suit !== suits[Math.floor(card.id / 13)]) return false
    ids.add(card.id)
    count++
    return true
  }

  if (!raw.stock.every((c) => collect(c, false))) return false
  for (const pile of raw.tableau) {
    if (!Array.isArray(pile)) return false
    let seenFaceUp = false
    for (const card of pile) {
      if (!isCard(card) || ids.has(card.id) || !collect(card, card.faceUp)) return false
      if (card.faceUp) seenFaceUp = true
      else if (seenFaceUp) return false
    }
  }
  if (count !== 104) return false
  if (count + raw.completed * SEQUENCE_LEN !== 104) return false
  if (raw.status === STATUS.WON && raw.completed !== COMPLETE_TARGET) return false
  return true
}
