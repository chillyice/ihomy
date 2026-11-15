// 斗地主规则内核:54 张牌、三家叫分、牌型判定/比较、候选牌型枚举与电脑选牌。
// 全部纯函数,页面只负责发牌、回合驱动与展示。牌型口径采用常见三人斗地主:
// 单/对/三/三带一/三带二/顺子/连对/飞机/飞机带翅/四带二/四带两对/炸弹/王炸。
import { JOKER_BIG, JOKER_SMALL, buildDeck, groupByValue, shuffle, uniqueCards } from './cardCore'

export const DDZ_COMBO = {
  SINGLE: 'SINGLE',
  PAIR: 'PAIR',
  TRIPLE: 'TRIPLE',
  TRIPLE_ONE: 'TRIPLE_ONE',
  TRIPLE_PAIR: 'TRIPLE_PAIR',
  STRAIGHT: 'STRAIGHT',
  PAIR_STRAIGHT: 'PAIR_STRAIGHT',
  PLANE: 'PLANE',
  PLANE_ONE: 'PLANE_ONE',
  PLANE_PAIR: 'PLANE_PAIR',
  FOUR_TWO: 'FOUR_TWO',
  FOUR_TWO_PAIR: 'FOUR_TWO_PAIR',
  BOMB: 'BOMB',
  ROCKET: 'ROCKET',
}

/** 斗地主点数:3..10 原值,A=14,2=15,小王 16,大王 17 */
export function dzValue(card) {
  if (!card) return 0
  if (card.suit === 'JOKER') return card.rank
  if (card.rank === 1) return 14
  if (card.rank === 2) return 15
  return card.rank
}

const isBombType = (type) => type === DDZ_COMBO.BOMB || type === DDZ_COMBO.ROCKET

function combo(type, cards, rank) {
  return { type, cards, rank, len: cards.length }
}

function consecutive(values) {
  for (let i = 1; i < values.length; i += 1) {
    if (values[i] !== values[i - 1] + 1) return false
  }
  return true
}

function runsOf(groups, minCount, minLength, maxValue = 14) {
  const values = [...groups.keys()].filter((v) => v >= 3 && v <= maxValue && groups.get(v).length >= minCount).sort((a, b) => a - b)
  const runs = []
  let start = 0
  for (let i = 1; i <= values.length; i += 1) {
    if (i === values.length || values[i] !== values[i - 1] + 1) {
      const length = i - start
      if (length >= minLength) runs.push(values.slice(start, i))
      start = i
    }
  }
  return runs
}

/**
 * 识别一手牌的牌型;非法组合返回 null。
 * @returns {{type:string, cards:Object[], rank:number, len:number}|null}
 */
export function dzCombo(cards) {
  if (!Array.isArray(cards) || cards.length === 0 || !uniqueCards(cards)) return null
  const n = cards.length
  const groups = groupByValue(cards, dzValue)
  const values = [...groups.keys()].sort((a, b) => a - b)
  const count = (v) => groups.get(v)?.length || 0

  if (n === 2 && cards.every((c) => c.suit === 'JOKER')) return combo(DDZ_COMBO.ROCKET, cards, 100)
  if (n === 4 && values.length === 1 && values[0] < JOKER_SMALL) return combo(DDZ_COMBO.BOMB, cards, values[0])
  if (n === 1) return combo(DDZ_COMBO.SINGLE, cards, values[0])
  if (n === 2 && values.length === 1) return combo(DDZ_COMBO.PAIR, cards, values[0])
  if (n === 3 && values.length === 1) return combo(DDZ_COMBO.TRIPLE, cards, values[0])
  if (n === 4 && values.length === 2 && count(values[0]) + count(values[1]) === 4) {
    const triple = values.find((v) => count(v) === 3)
    if (triple != null) return combo(DDZ_COMBO.TRIPLE_ONE, cards, triple)
  }
  if (n === 5 && values.length === 2 && count(values[0]) === 3 && count(values[1]) === 2) {
    return combo(
      DDZ_COMBO.TRIPLE_PAIR,
      cards,
      values.find((v) => count(v) === 3),
    )
  }
  if (n >= 5 && values.length === n && values[values.length - 1] <= 14 && values[0] >= 3 && consecutive(values)) {
    return combo(DDZ_COMBO.STRAIGHT, cards, values[values.length - 1])
  }
  if (
    n >= 6 &&
    n % 2 === 0 &&
    values.every((v) => count(v) === 2) &&
    values[values.length - 1] <= 14 &&
    values[0] >= 3 &&
    consecutive(values)
  ) {
    return combo(DDZ_COMBO.PAIR_STRAIGHT, cards, values[values.length - 1])
  }
  if (
    n >= 6 &&
    n % 3 === 0 &&
    values.every((v) => count(v) === 3) &&
    values.length >= 2 &&
    values[values.length - 1] <= 14 &&
    values[0] >= 3 &&
    consecutive(values)
  ) {
    return combo(DDZ_COMBO.PLANE, cards, values[values.length - 1])
  }

  // 飞机带单 / 飞机带对:先找连续三张,再校验翅膀
  for (const [type, per] of [
    [DDZ_COMBO.PLANE_ONE, 4],
    [DDZ_COMBO.PLANE_PAIR, 5],
  ]) {
    if (n % per !== 0) continue
    const planeLen = n / per
    if (planeLen < 2) continue
    const tripleRuns = runsOf(groups, 3, planeLen)
    for (const run of tripleRuns) {
      const used = new Set()
      for (const v of run) {
        for (const card of groups.get(v).slice(0, 3)) used.add(card.id)
      }
      const wings = cards.filter((c) => !used.has(c.id))
      const wingGroups = groupByValue(wings, dzValue)
      const wingValues = [...wingGroups.keys()]
      if (wingValues.some((v) => run.includes(v))) continue
      if (type === DDZ_COMBO.PLANE_ONE && wings.length === planeLen) {
        return combo(type, cards, run[run.length - 1])
      }
      if (
        type === DDZ_COMBO.PLANE_PAIR &&
        wings.length === planeLen * 2 &&
        wingValues.length === planeLen &&
        wingValues.every((v) => wingGroups.get(v).length === 2)
      ) {
        return combo(type, cards, run[run.length - 1])
      }
    }
  }

  if (n === 6) {
    const four = values.find((v) => count(v) === 4)
    if (four != null) return combo(DDZ_COMBO.FOUR_TWO, cards, four)
  }
  if (n === 8) {
    const four = values.find((v) => count(v) === 4)
    if (four != null) {
      const rest = values.filter((v) => v !== four)
      if (rest.length === 2 && rest.every((v) => count(v) === 2)) return combo(DDZ_COMBO.FOUR_TWO_PAIR, cards, four)
    }
  }
  return null
}

/** a 能否压过 b(b 为空表示自由出牌) */
export function dzBeats(a, b) {
  if (!a) return false
  if (!b) return true
  if (a.type === DDZ_COMBO.ROCKET) return b.type !== DDZ_COMBO.ROCKET
  if (b.type === DDZ_COMBO.ROCKET) return false
  if (a.type === DDZ_COMBO.BOMB && b.type !== DDZ_COMBO.BOMB) return true
  if (a.type !== DDZ_COMBO.BOMB && b.type === DDZ_COMBO.BOMB) return false
  return a.type === b.type && a.len === b.len && a.rank > b.rank
}

/** 从手牌里枚举所有能压过 last 的出法(last 为空=自由出牌,返回全部候选) */
export function dzPlays(hand, last = null) {
  const out = []
  const seen = new Set()
  const add = (cards) => {
    if (!cards || cards.length === 0) return
    const parsed = dzCombo(cards)
    if (!parsed || !dzBeats(parsed, last)) return
    const key = parsed.cards
      .map((c) => c.id)
      .sort()
      .join(',')
    if (seen.has(key)) return
    seen.add(key)
    out.push(parsed)
  }
  const groups = groupByValue(hand, dzValue)
  const values = [...groups.keys()].sort((a, b) => a - b)
  const remaining = (used) => hand.filter((c) => !used.has(c.id))

  for (const v of values) {
    const list = groups.get(v)
    if (list.length >= 1) add(list.slice(0, 1))
    if (list.length >= 2) add(list.slice(0, 2))
    if (list.length >= 3) {
      const triple = list.slice(0, 3)
      add(triple)
      const used = new Set(triple.map((c) => c.id))
      const rest = remaining(used).sort((a, b) => dzValue(a) - dzValue(b) || a.id.localeCompare(b.id))
      if (rest.length >= 1) add([...triple, rest[0]])
      const pairValue = values.find((x) => x !== v && groups.get(x).length >= 2)
      if (pairValue != null) add([...triple, ...groups.get(pairValue).slice(0, 2)])
    }
    if (list.length >= 4) add(list.slice(0, 4))
  }

  const small = groups.get(JOKER_SMALL) || []
  const big = groups.get(JOKER_BIG) || []
  if (small.length && big.length) add([small[0], big[0]])

  // 顺子
  for (let start = 3; start <= 10; start += 1) {
    const path = []
    for (let v = start; v <= 14; v += 1) {
      if (!groups.has(v)) break
      path.push(groups.get(v)[0])
      if (path.length >= 5) add([...path])
    }
  }
  // 连对
  for (let start = 3; start <= 12; start += 1) {
    const path = []
    for (let v = start; v <= 14; v += 1) {
      if (!groups.has(v) || groups.get(v).length < 2) break
      path.push(...groups.get(v).slice(0, 2))
      if (path.length >= 6 && path.length % 2 === 0) add([...path])
    }
  }
  // 飞机(纯)/带单/带对
  const tripleRuns = runsOf(groups, 3, 2)
  for (const run of tripleRuns) {
    const body = run.flatMap((v) => groups.get(v).slice(0, 3))
    add([...body])
    for (let m = 2; m <= run.length; m += 1) {
      const part = run.slice(0, m).flatMap((v) => groups.get(v).slice(0, 3))
      const partUsed = new Set(part.map((c) => c.id))
      const partRest = remaining(partUsed).sort((a, b) => dzValue(a) - dzValue(b) || a.id.localeCompare(b.id))
      if (partRest.length >= m) add([...part, ...partRest.slice(0, m)])
      const pairValues = [...groups.keys()].filter((v) => !run.slice(0, m).includes(v) && groups.get(v).length >= 2).sort((a, b) => a - b)
      if (pairValues.length >= m) add([...part, ...pairValues.slice(0, m).flatMap((v) => groups.get(v).slice(0, 2))])
    }
  }
  // 四带二 / 四带两对
  for (const v of values) {
    const list = groups.get(v)
    if (list.length < 4) continue
    const four = list.slice(0, 4)
    const used = new Set(four.map((c) => c.id))
    const rest = remaining(used).sort((a, b) => dzValue(a) - dzValue(b) || a.id.localeCompare(b.id))
    if (rest.length >= 2) add([...four, ...rest.slice(0, 2)])
    const pairValues = values.filter((x) => x !== v && groups.get(x).length >= 2).sort((a, b) => a - b)
    if (pairValues.length >= 2) add([...four, ...pairValues.slice(0, 2).flatMap((x) => groups.get(x).slice(0, 2))])
  }

  return out.sort((a, b) => a.rank - b.rank || b.len - a.len)
}

/** 叫分参考:王、2、炸弹与高牌越多越敢叫 */
export function dzBidStrength(hand) {
  const groups = groupByValue(hand, dzValue)
  let score = 0
  for (const [v, list] of groups) {
    if (v === JOKER_BIG) score += 2.5
    else if (v === JOKER_SMALL) score += 2
    else if (v === 15) score += 1.1 * list.length
    else if (v === 14) score += 0.5 * list.length
    if (list.length === 4 && v < JOKER_SMALL) score += 3
  }
  if ((groups.get(JOKER_SMALL)?.length || 0) && (groups.get(JOKER_BIG)?.length || 0)) score += 3
  return score
}

/** 电脑叫分:0=不叫,1..3 分 */
export function dzAiBid(hand) {
  const score = dzBidStrength(hand)
  if (score >= 9.5) return 3
  if (score >= 6.5) return 2
  if (score >= 4) return 1
  return 0
}

/** 发牌:三家各 17 张 + 3 张底牌 */
export function dzDeal(rng = Math.random) {
  const deck = shuffle(buildDeck({ decks: 1 }), rng)
  return {
    hands: [deck.slice(0, 17), deck.slice(17, 34), deck.slice(34, 51)],
    bottom: deck.slice(51, 54),
  }
}

/** 电脑选牌:返回要打出的牌型(null=不要) */
export function dzAiChoose({ hand, last, seat, landlord, handCounts, lastSeat, playCounts = [] }) {
  const plays = dzPlays(hand, last)
  if (!plays.length) return null
  const finishes = plays.filter((p) => p.len === hand.length)
  if (finishes.length) return finishes.sort((a, b) => a.rank - b.rank)[0]

  const groups = groupByValue(hand, dzValue)
  const isLandlord = seat === landlord
  const lastIsTeammate = lastSeat != null && lastSeat !== seat && (lastSeat === landlord) === isLandlord
  const counts = handCounts || []
  const opponentSeats = counts.map((_, i) => i).filter((i) => i !== seat && (i === landlord) !== isLandlord)
  const opponentMin = opponentSeats.length ? Math.min(...opponentSeats.map((i) => counts[i] ?? 99)) : 99

  const breaksBomb = (p) => {
    if (isBombType(p.type)) return false
    const usedValues = new Set(p.cards.map(dzValue))
    for (const v of usedValues) {
      const held = groups.get(v)?.length || 0
      if (held >= 4 && v < JOKER_SMALL && p.cards.filter((c) => dzValue(c) === v).length < 4) return true
    }
    if (p.cards.some((c) => c.suit === 'JOKER') && (groups.get(JOKER_SMALL)?.length || 0) && (groups.get(JOKER_BIG)?.length || 0)) {
      return p.type !== DDZ_COMBO.ROCKET
    }
    return false
  }
  const cost = (p) => {
    let value = p.rank * 4 - p.len * 2
    if (isBombType(p.type)) value += 200
    if (p.cards.some((c) => dzValue(c) >= 15)) value += 26
    if (breaksBomb(p)) value += 40
    return value
  }

  if (!last) {
    const normal = plays.filter((p) => !isBombType(p.type))
    const pool = normal.length ? normal : plays
    const structural = pool.filter((p) => p.len >= 5 && p.rank <= 12 && !p.cards.some((c) => dzValue(c) >= 15) && !breaksBomb(p))
    const base = structural.length ? structural : pool
    return base.sort((a, b) => cost(a) - cost(b))[0]
  }

  if (lastIsTeammate) {
    const teammateLeft = counts[lastSeat] ?? 99
    if (teammateLeft > 4 || last.rank >= 15) return null
    if (playCounts[lastSeat] === 0) return null
  }

  const nonBombs = plays.filter((p) => !isBombType(p.type))
  if (nonBombs.length) {
    const pick = nonBombs.sort((a, b) => cost(a) - cost(b))[0]
    // 对手快走完时不再吝啬大牌
    if (opponentMin > 3 && pick.cards.some((c) => dzValue(c) >= 16) && nonBombs.length > 1) {
      const cheap = nonBombs.filter((p) => !p.cards.some((c) => dzValue(c) >= 16))
      if (cheap.length) return cheap.sort((a, b) => cost(a) - cost(b))[0]
    }
    return pick
  }

  if (opponentMin <= 5 || hand.length <= 6) return plays.sort((a, b) => cost(a) - cost(b))[0]
  return null
}
