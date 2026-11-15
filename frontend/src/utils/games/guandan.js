// 掼蛋规则内核:两副牌 108 张、四人两联邦、打几(级牌)、红桃级牌逢人配、常规牌型与炸弹梯度。
// 纯函数:发牌、牌型识别/比较、候选枚举、电脑选牌、进贡还贡与升级。
// 简化口径(与主流打法一致的部分):顺子/三连对/钢板不含 2(允许 A2345 最小顺);同花顺算炸;
// 进贡还贡自动执行,双大王抗贡;一局打到过 A 即整场获胜。
import { JOKER_BIG, JOKER_SMALL, buildDeck, shuffle, uniqueCards } from './cardCore'

export const GD_TYPE = {
  SINGLE: 'SINGLE',
  PAIR: 'PAIR',
  TRIPLE: 'TRIPLE',
  FULL_HOUSE: 'FULL_HOUSE',
  STRAIGHT: 'STRAIGHT',
  TUBE: 'TUBE',
  PLATE: 'PLATE',
  STRAIGHT_FLUSH: 'STRAIGHT_FLUSH',
  BOMB: 'BOMB',
  ROCKET: 'ROCKET',
}

export const GD_LEVELS = [2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14]

const STRAIGHT_WINDOWS = [
  [14, 2, 3, 4, 5],
  [3, 4, 5, 6, 7],
  [4, 5, 6, 7, 8],
  [5, 6, 7, 8, 9],
  [6, 7, 8, 9, 10],
  [7, 8, 9, 10, 11],
  [8, 9, 10, 11, 12],
  [9, 10, 11, 12, 13],
  [10, 11, 12, 13, 14],
]
const TUBE_WINDOWS = [
  [3, 4, 5],
  [4, 5, 6],
  [5, 6, 7],
  [6, 7, 8],
  [7, 8, 9],
  [8, 9, 10],
  [9, 10, 11],
  [10, 11, 12],
  [11, 12, 13],
  [12, 13, 14],
]
const PLATE_WINDOWS = [
  [3, 4],
  [4, 5],
  [5, 6],
  [6, 7],
  [7, 8],
  [8, 9],
  [9, 10],
  [10, 11],
  [11, 12],
  [12, 13],
  [13, 14],
]

/** 自然点数:A=14,2..10 原值,J/Q/K=11/12/13 */
export function gdNatural(card) {
  if (!card) return 0
  if (card.suit === 'JOKER') return card.rank
  return card.rank === 1 ? 14 : card.rank
}

/** 红桃级牌 = 逢人配 */
export const gdIsWild = (card, level) => card.suit === 'H' && gdNatural(card) === level

/** 比大小用的点数:级牌抬到 15,A=14,...,2=2,小王 16,大王 17 */
export function gdValue(card, level) {
  if (!card) return 0
  if (card.suit === 'JOKER') return card.rank
  const natural = gdNatural(card)
  return natural === level ? 15 : natural
}

/** 级牌文案:2..10 / J / Q / K / A */
export const gdLevelText = (level) => ({ 11: 'J', 12: 'Q', 13: 'K', 14: 'A' })[level] || String(level)

function combo(type, cards, rank, { bomb = false, power = 0 } = {}) {
  return { type, cards, rank, len: cards.length, bomb, power }
}

/** 识别一手牌的牌型;非法返回 null */
export function gdComboOf(cards, level) {
  if (!Array.isArray(cards) || !cards.length || !uniqueCards(cards)) return null
  const n = cards.length
  const wilds = cards.filter((c) => gdIsWild(c, level))
  const rest = cards.filter((c) => !gdIsWild(c, level))
  const jokers = rest.filter((c) => c.suit === 'JOKER')
  if (jokers.length) {
    if (wilds.length || jokers.length !== rest.length) return null
    const small = jokers.filter((c) => c.rank === JOKER_SMALL).length
    const big = jokers.filter((c) => c.rank === JOKER_BIG).length
    if (n === 4 && small === 2 && big === 2) return combo(GD_TYPE.ROCKET, cards, 99, { bomb: true, power: 9 })
    if (small && !big && small === n && n <= 2) return combo(n === 1 ? GD_TYPE.SINGLE : GD_TYPE.PAIR, cards, 16)
    if (big && !small && big === n && n <= 2) return combo(n === 1 ? GD_TYPE.SINGLE : GD_TYPE.PAIR, cards, 17)
    return null
  }

  const byRank = new Map()
  for (const c of rest) {
    const v = gdNatural(c)
    if (!byRank.has(v)) byRank.set(v, [])
    byRank.get(v).push(c)
  }
  const naturalRanks = [...byRank.keys()].sort((a, b) => a - b)
  const wildCount = wilds.length

  // 同点数:单/对/三/炸弹(级牌含红桃配)
  if (naturalRanks.length <= 1) {
    const rank = naturalRanks.length ? naturalRanks[0] : level
    if (n === 1) return combo(GD_TYPE.SINGLE, cards, rank === level ? 15 : rank)
    if (n === 2) return combo(GD_TYPE.PAIR, cards, rank === level ? 15 : rank)
    if (n === 3) return combo(GD_TYPE.TRIPLE, cards, rank === level ? 15 : rank)
    if (n >= 4 && n <= 8) return combo(GD_TYPE.BOMB, cards, rank === level ? 15 : rank, { bomb: true, power: n })
    return null
  }

  // 三带二
  if (n === 5) {
    const candidates = [...naturalRanks]
    if (wildCount) candidates.push(level)
    let best = null
    for (const t of candidates) {
      for (const p of candidates) {
        if (t === p) continue
        const tc = byRank.get(t)?.length || 0
        const pc = byRank.get(p)?.length || 0
        if (tc > 3 || pc > 2 || tc + pc !== rest.length) continue
        if (3 - tc + (2 - pc) !== wildCount) continue
        const rank = t === level ? 15 : t
        if (!best || rank > best.rank) best = combo(GD_TYPE.FULL_HOUSE, cards, rank)
      }
    }
    if (best) return best
  }

  const fitsWindow = (window, per) => {
    const need = new Map()
    for (const v of window) need.set(v, per)
    for (const [v, list] of byRank) {
      if (!need.has(v) || list.length > need.get(v)) return false
      need.set(v, need.get(v) - list.length)
    }
    let missing = 0
    for (const left of need.values()) missing += left
    return missing === wildCount
  }

  // 同花顺(炸弹,优先于普通顺子)
  if (n === 5) {
    const suits = new Set(rest.map((c) => c.suit))
    if (suits.size <= 1) {
      for (const window of STRAIGHT_WINDOWS) {
        if (fitsWindow(window, 1)) {
          return combo(GD_TYPE.STRAIGHT_FLUSH, cards, window[window.length - 1], { bomb: true, power: 5.5 })
        }
      }
    }
    for (const window of STRAIGHT_WINDOWS) {
      if (fitsWindow(window, 1)) return combo(GD_TYPE.STRAIGHT, cards, window[window.length - 1])
    }
  }

  // 三连对 / 钢板
  if (n === 6) {
    for (const window of PLATE_WINDOWS) {
      if (fitsWindow(window, 3)) return combo(GD_TYPE.PLATE, cards, window[window.length - 1])
    }
    for (const window of TUBE_WINDOWS) {
      if (fitsWindow(window, 2)) return combo(GD_TYPE.TUBE, cards, window[window.length - 1])
    }
  }
  return null
}

/** a 能否压过 b */
export function gdBeats(a, b) {
  if (!a) return false
  if (!b) return true
  if (a.bomb && !b.bomb) return true
  if (!a.bomb && b.bomb) return false
  if (a.bomb && b.bomb) {
    if (a.power !== b.power) return a.power > b.power
    return a.rank > b.rank
  }
  return a.type === b.type && a.len === b.len && a.rank > b.rank
}

function pickCards(hand, level, requirements, { suit = null, allowWild = true } = {}) {
  const wilds = hand.filter((c) => gdIsWild(c, level))
  const used = new Set()
  const picks = []
  let need = 0
  for (const [rank, count] of requirements) {
    const pool = hand.filter((c) => !used.has(c.id) && !gdIsWild(c, level) && gdNatural(c) === rank && (suit ? c.suit === suit : true))
    const take = pool.slice(0, count)
    for (const c of take) used.add(c.id)
    picks.push(...take)
    need += count - take.length
  }
  const wildPool = wilds.filter((c) => !used.has(c.id))
  if (need > wildPool.length) return null
  if (need > 0 && !allowWild) return null
  if (need > 0) picks.push(...wildPool.slice(0, need))
  return picks
}

function countNatural(hand, level, rank) {
  return hand.filter((c) => !gdIsWild(c, level) && c.suit !== 'JOKER' && gdNatural(c) === rank).length
}

/** 枚举手牌所有可出牌型(用于提示与电脑决策) */
export function gdCombos(hand, level) {
  const out = []
  const seen = new Set()
  const wilds = hand.filter((c) => gdIsWild(c, level))
  const wildN = wilds.length
  const add = (cards) => {
    if (!cards || !cards.length) return
    const parsed = gdComboOf(cards, level)
    if (!parsed) return
    const key = `${parsed.type}:${parsed.rank}:${parsed.len}:${parsed.cards
      .map((c) => c.id)
      .sort()
      .join(',')}`
    if (seen.has(key)) return
    seen.add(key)
    out.push(parsed)
  }

  // 同点数组合
  for (let rank = 2; rank <= 14; rank += 1) {
    const natural = countNatural(hand, level, rank)
    if (rank !== level && natural === 0) continue
    const max = Math.min(8, natural + wildN)
    for (let n = 1; n <= max; n += 1) add(pickCards(hand, level, [[rank, n]]))
  }
  // 对王/王炸
  const small = hand.filter((c) => c.suit === 'JOKER' && c.rank === JOKER_SMALL)
  const big = hand.filter((c) => c.suit === 'JOKER' && c.rank === JOKER_BIG)
  if (small.length) add(small.slice(0, 1))
  if (small.length >= 2) add(small.slice(0, 2))
  if (big.length) add(big.slice(0, 1))
  if (big.length >= 2) add(big.slice(0, 2))
  if (small.length >= 2 && big.length >= 2) add([...small.slice(0, 2), ...big.slice(0, 2)])

  // 三带二
  for (let t = 2; t <= 14; t += 1) {
    for (let p = 2; p <= 14; p += 1) {
      if (t === p) continue
      const tn = countNatural(hand, level, t)
      const pn = countNatural(hand, level, p)
      if (t !== level && tn === 0) continue
      if (p !== level && pn === 0) continue
      if (Math.max(0, 3 - tn) + Math.max(0, 2 - pn) > wildN) continue
      add(
        pickCards(hand, level, [
          [t, 3],
          [p, 2],
        ]),
      )
    }
  }
  // 顺子 / 三连对 / 钢板
  for (const window of STRAIGHT_WINDOWS)
    add(
      pickCards(
        hand,
        level,
        window.map((v) => [v, 1]),
      ),
    )
  for (const window of TUBE_WINDOWS)
    add(
      pickCards(
        hand,
        level,
        window.map((v) => [v, 2]),
      ),
    )
  for (const window of PLATE_WINDOWS)
    add(
      pickCards(
        hand,
        level,
        window.map((v) => [v, 3]),
      ),
    )
  // 同花顺
  for (const suit of ['S', 'H', 'C', 'D']) {
    for (const window of STRAIGHT_WINDOWS) {
      add(
        pickCards(
          hand,
          level,
          window.map((v) => [v, 1]),
          { suit },
        ),
      )
    }
  }
  return out
}

/** 校验玩家选中的牌:返回牌型或 null */
export function gdMatch(cards, level) {
  return gdComboOf(cards, level)
}

/** 发牌:4 家各 27 张 */
export function gdDeal(rng = Math.random) {
  const deck = shuffle(buildDeck({ decks: 2 }), rng)
  return [deck.slice(0, 27), deck.slice(27, 54), deck.slice(54, 81), deck.slice(81, 108)]
}

/** 升级步数:队友二游 +3、三游 +2、末游 +1 */
export function gdAdvance(partnerRank) {
  if (partnerRank === 2) return 3
  if (partnerRank === 3) return 2
  return 1
}

/** 进贡还贡:返回新的手牌数组、首出座位与说明 */
export function gdTribute(hands, level, lastOrder) {
  const next = hands.map((h) => [...h])
  const winner = lastOrder[0]
  const loser = lastOrder[lastOrder.length - 1]
  if (winner == null || loser == null || winner === loser) return { hands: next, leader: winner ?? 0, anti: false }
  const bigJokers = next[loser].filter((c) => c.suit === 'JOKER' && c.rank === JOKER_BIG)
  if (bigJokers.length >= 2) return { hands: next, leader: winner, anti: true }
  const tributeCard = [...next[loser]].sort((a, b) => gdValue(b, level) - gdValue(a, level))[0]
  if (!tributeCard) return { hands: next, leader: winner, anti: false }
  next[loser] = next[loser].filter((c) => c.id !== tributeCard.id)
  next[winner] = [...next[winner], tributeCard]
  const backPool = next[winner]
    .filter((c) => c.id !== tributeCard.id && gdNatural(c) <= 10 && c.suit !== 'JOKER')
    .sort((a, b) => gdValue(a, level) - gdValue(b, level))
  const back = backPool[0] || null
  if (back) {
    next[winner] = next[winner].filter((c) => c.id !== back.id)
    next[loser] = [...next[loser], back]
  }
  return {
    hands: next,
    leader: winner,
    anti: false,
    tribute: { from: loser, to: winner, card: tributeCard },
    back: { from: winner, to: loser, card: back },
  }
}

/** 电脑选牌;partnerSeat = (seat+2)%4 */
export function gdAiChoose({ hand, last, level, seat, handCounts = [], lastSeat = null }) {
  const partnerSeat = (seat + 2) % 4
  const combos = gdCombos(hand, level)
  const plays = last ? combos.filter((c) => gdBeats(c, last)) : combos
  if (!plays.length) return null
  const finishes = plays.filter((p) => p.len === hand.length)
  if (finishes.length) return finishes.sort((a, b) => a.rank - b.rank || a.power - b.power)[0]

  const wildCount = (p) => p.cards.filter((c) => gdIsWild(c, level)).length
  const cost = (p) => {
    let value = p.rank + wildCount(p) * 8
    if (p.bomb) value += 300
    if (p.cards.some((c) => c.suit === 'JOKER')) value += 40
    return value - p.len
  }
  const nonBombs = plays.filter((p) => !p.bomb)

  if (!last) {
    const clean = nonBombs.filter((p) => wildCount(p) === 0 && !p.cards.some((c) => c.suit === 'JOKER'))
    const structural = clean.filter((p) => p.len >= 5 && p.rank <= 12)
    if (structural.length) return structural.sort((a, b) => a.rank - b.rank || b.len - a.len)[0]
    const low = clean.filter((p) => p.len <= 3)
    if (low.length) return low.sort((a, b) => cost(a) - cost(b))[0]
    if (nonBombs.length) return nonBombs.sort((a, b) => cost(a) - cost(b))[0]
    return plays.sort((a, b) => cost(a) - cost(b))[0]
  }

  if (lastSeat === partnerSeat) return null
  const opponentMin = Math.min(...[0, 1, 2, 3].filter((i) => i !== seat && i !== partnerSeat).map((i) => handCounts[i] ?? 99))
  if (nonBombs.length) return nonBombs.sort((a, b) => cost(a) - cost(b))[0]
  if (opponentMin <= 5 || hand.length <= 6) return plays.sort((a, b) => cost(a) - cost(b))[0]
  return null
}
