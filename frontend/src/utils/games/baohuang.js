// 保皇规则内核(JJ 标准简明版):4 副牌去掉 3/4/5 共 168 张、5 人、皇帝+侍卫对三名革命党。
// 牌型只有「同点数套牌 + 大小王挂靠」;跟牌必须张数相同且逐张压过上家(王挂靠按牌逐张比较)。
// 不带顺子/连对/炸弹等牌型。皇帝牌/侍卫牌各一张(带标记的王),侍卫可明保/暗保或与皇帝同人为独保。
import { JOKER_BIG, JOKER_SMALL, buildDeck, groupByValue, shuffle, uniqueCards } from './cardCore'

export const BH_MARK = { EMPEROR: 'EMPEROR', GUARD: 'GUARD' }

/** 保皇点数:6..K 原值,A=14,2=15,小王 16,大王 17 */
export function bhValue(card) {
  if (!card) return 0
  if (card.suit === 'JOKER') return card.rank
  if (card.rank === 1) return 14
  if (card.rank === 2) return 15
  return card.rank
}

export const bhRankText = (value) => ({ 14: 'A', 15: '2', 16: 'jk', 17: 'JK' })[value] || String(value)

/** 生成 168 张保皇牌,并给一张大王/一张小王打上皇帝/侍卫标记 */
export function bhBuildDeck() {
  const deck = buildDeck({ decks: 4, ranks: [1, 2, 6, 7, 8, 9, 10, 11, 12, 13] })
  let emperor = false
  let guard = false
  for (const card of deck) {
    if (!emperor && card.suit === 'JOKER' && card.rank === JOKER_BIG) {
      card.mark = BH_MARK.EMPEROR
      emperor = true
    } else if (!guard && card.suit === 'JOKER' && card.rank === JOKER_SMALL) {
      card.mark = BH_MARK.GUARD
      guard = true
    }
  }
  return deck
}

/** 发牌:三人 34 张、两人 33 张 */
export function bhDeal(rng = Math.random) {
  const deck = shuffle(bhBuildDeck(), rng)
  return [deck.slice(0, 34), deck.slice(34, 68), deck.slice(68, 102), deck.slice(102, 135), deck.slice(135, 168)]
}

/**
 * 一手牌:非王必须同点数,大小王可任意挂靠;纯王也可出。
 * 返回 { cards, len, values(升序), baseRank, jokers } 或 null。
 */
export function bhCombo(cards) {
  if (!Array.isArray(cards) || cards.length === 0 || !uniqueCards(cards)) return null
  const nonJokers = cards.filter((c) => c.suit !== 'JOKER')
  let baseRank = 0
  if (nonJokers.length) {
    baseRank = bhValue(nonJokers[0])
    if (nonJokers.some((c) => bhValue(c) !== baseRank)) return null
  }
  const values = cards.map(bhValue).sort((a, b) => a - b)
  return { cards, len: cards.length, values, baseRank, jokers: cards.length - nonJokers.length }
}

/** a 能否压过 b:张数相同,且排序后逐张严格大于 */
export function bhBeats(a, b) {
  if (!a) return false
  if (!b) return true
  if (a.len !== b.len) return false
  return a.values.every((v, i) => v > b.values[i])
}

/** 枚举手牌出法(last 为空=自由出牌) */
export function bhPlays(hand, last = null) {
  const out = []
  const seen = new Set()
  const add = (cards) => {
    const parsed = bhCombo(cards)
    if (!parsed || !bhBeats(parsed, last)) return
    const key = parsed.cards
      .map((c) => c.id)
      .sort()
      .join(',')
    if (seen.has(key)) return
    seen.add(key)
    out.push(parsed)
  }
  const groups = groupByValue(hand, bhValue)
  const small = (groups.get(JOKER_SMALL) || []).slice().sort((a, b) => a.id.localeCompare(b.id))
  const big = (groups.get(JOKER_BIG) || []).slice().sort((a, b) => a.id.localeCompare(b.id))
  const baseValues = [...groups.keys()].filter((v) => v < JOKER_SMALL).sort((a, b) => a - b)

  if (!last) {
    for (const v of baseValues) {
      const list = groups.get(v)
      for (let b = 1; b <= list.length; b += 1) add(list.slice(0, b))
      for (let j = 1; j <= Math.min(3, small.length + big.length); j += 1) {
        for (let b = 1; b <= list.length; b += 1) {
          for (let kb = 0; kb <= Math.min(j, big.length); kb += 1) {
            const ks = j - kb
            if (ks <= small.length) add([...list.slice(0, b), ...small.slice(0, ks), ...big.slice(0, kb)])
          }
        }
      }
    }
    for (let j = 1; j <= small.length + big.length; j += 1) {
      for (let kb = 0; kb <= Math.min(j, big.length); kb += 1) {
        const ks = j - kb
        if (ks <= small.length) add([...small.slice(0, ks), ...big.slice(0, kb)])
      }
    }
    return out.sort((a, b) => a.baseRank - b.baseRank || b.len - a.len)
  }

  const n = last.len
  const jokerTotal = small.length + big.length
  for (let b = 0; b <= n; b += 1) {
    const j = n - b
    if (j > jokerTotal) continue
    if (b === 0) {
      for (let kb = 0; kb <= Math.min(j, big.length); kb += 1) {
        const ks = j - kb
        if (ks <= small.length) add([...small.slice(0, ks), ...big.slice(0, kb)])
      }
      continue
    }
    for (const v of baseValues) {
      const list = groups.get(v)
      if (list.length < b) continue
      for (let kb = 0; kb <= Math.min(j, big.length); kb += 1) {
        const ks = j - kb
        if (ks > small.length) continue
        add([...list.slice(0, b), ...small.slice(0, ks), ...big.slice(0, kb)])
      }
    }
  }
  return out.sort((a, b) => a.baseRank - b.baseRank || a.jokers - b.jokers)
}

/** 身份:皇帝/侍卫/独保;royal 为保皇派座位 */
export function bhRoles(hands) {
  let emperor = -1
  let guard = -1
  hands.forEach((hand, seat) => {
    for (const card of hand) {
      if (card.mark === BH_MARK.EMPEROR) emperor = seat
      if (card.mark === BH_MARK.GUARD) guard = seat
    }
  })
  const solo = emperor >= 0 && emperor === guard
  const royal = solo ? [emperor] : [emperor, guard].filter((s) => s >= 0)
  return { emperor, guard, solo, royal }
}

/** 手牌强度(电脑决定明保/暗保与叫牌参考) */
export function bhStrength(hand) {
  const groups = groupByValue(hand, bhValue)
  let score = 0
  for (const [v, list] of groups) {
    if (v >= JOKER_BIG) score += 3
    else if (v === JOKER_SMALL) score += 2
    else if (v === 15) score += 1.5
    else if (v === 14) score += 0.6
    score += list.length * 0.12
  }
  return score
}

/**
 * 电脑出牌。
 * @param {object} p
 * @param {boolean} p.lastIsAlly 上一手是否队友(由页面按身份与明保口径判定)
 */
export function bhAiChoose({ hand, last, lastIsAlly = false, opponentMin = 99 }) {
  const plays = bhPlays(hand, last)
  if (!plays.length) return null
  const finishes = plays.filter((p) => p.len === hand.length)
  if (finishes.length) return finishes.sort((a, b) => a.baseRank - b.baseRank || a.jokers - b.jokers)[0]
  if (last && lastIsAlly) return null

  const cost = (p) => {
    let value = p.baseRank + p.jokers * 35
    if (p.baseRank === 0) value += 40
    return value
  }
  if (!last) {
    const natural = plays.filter((p) => p.jokers === 0)
    if (natural.length) return natural.sort((a, b) => cost(a) - cost(b) || b.len - a.len)[0]
    return plays.sort((a, b) => cost(a) - cost(b))[0]
  }
  const plain = plays.filter((p) => p.jokers === 0)
  if (plain.length) return plain.sort((a, b) => cost(a) - cost(b))[0]
  if (opponentMin <= 5) return plays.sort((a, b) => cost(a) - cost(b))[0]
  return null
}
