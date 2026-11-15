// 够级规则内核:6 人 4 副牌 216 张、三人一联邦(隔座)、憋三、点数为准的同点套牌 + 2 贴牌 + 王挂花。
// 够级牌:5 张 10 / 4 张 J / 3 张 Q / 2 张 K / 2 张 A / 1 张 2 / 任意带王;够级时只有对头能上牌(烧牌简化为不开放)。
// 纯函数:发牌、牌型识别/比较、候选枚举、电脑选牌、名次积分。
import { JOKER_BIG, JOKER_SMALL, buildDeck, groupByValue, shuffle, uniqueCards } from './cardCore'

export const GJ_GOUJI_MIN = { 10: 5, 11: 4, 12: 3, 13: 2, 14: 2, 15: 1 }

/** 够级点数:3..K 原值,A=14,2=15,小王 16,大王 17 */
export function gjValue(card) {
  if (!card) return 0
  if (card.suit === 'JOKER') return card.rank
  if (card.rank === 1) return 14
  if (card.rank === 2) return 15
  return card.rank
}

export const gjRankText = (value) => ({ 11: 'J', 12: 'Q', 13: 'K', 14: 'A', 15: '2', 16: 'jk', 17: 'JK' })[value] || String(value)

/** 对头 = 隔三座 */
export const gjOpposite = (seat) => (seat + 3) % 6

/** 队友 = 同联邦(隔座 0/2/4 与 1/3/5) */
export const gjIsTeammate = (a, b) => a !== b && a % 2 === b % 2

/**
 * 识别一手够级牌。
 * 规则:非王非 2 的牌必须同点数;2 可贴到 5..A(视为同点数),王可挂到 5..A;
 * 3/4 不能贴 2 或挂王;纯 2、纯王也可出。
 */
export function gjCombo(cards) {
  if (!Array.isArray(cards) || cards.length === 0 || !uniqueCards(cards)) return null
  const n = cards.length
  const jokers = cards.filter((c) => c.suit === 'JOKER')
  const twos = cards.filter((c) => c.suit !== 'JOKER' && gjValue(c) === 15)
  const bases = cards.filter((c) => c.suit !== 'JOKER' && gjValue(c) !== 15)
  const baseValues = [...new Set(bases.map(gjValue))]
  if (baseValues.length > 1) return null
  const baseRank = baseValues.length ? baseValues[0] : twos.length ? 15 : 0
  const baseCards = baseValues.length ? bases : twos
  if (baseRank === 3 || baseRank === 4) {
    if (twos.length && baseValues.length) return null
    if (jokers.length) return null
  }
  if (!baseValues.length && !twos.length && !jokers.length) return null
  const values = []
  if (baseRank > 0) {
    const converted = baseCards.length + (baseValues.length ? twos.length : 0)
    for (let i = 0; i < converted; i += 1) values.push(baseRank)
  }
  for (const j of jokers) values.push(j.rank)
  values.sort((a, b) => a - b)
  const small = jokers.filter((c) => c.rank === JOKER_SMALL).length
  const big = jokers.filter((c) => c.rank === JOKER_BIG).length
  const effective = baseRank > 0 ? baseCards.length + (baseValues.length ? twos.length : 0) : 0
  const gouji = jokers.length > 0 || (baseRank >= 10 && effective >= (GJ_GOUJI_MIN[baseRank] || 99)) || (baseRank === 15 && effective >= 1)
  return {
    cards,
    len: n,
    values,
    baseRank,
    twos: baseValues.length ? twos.length : 0,
    jokers: jokers.length,
    small,
    big,
    hasThree: baseRank === 3,
    gouji,
  }
}

/** a 能否压过 b(逐张严格大于,张数相同) */
export function gjBeats(a, b) {
  if (!a) return false
  if (!b) return true
  if (a.len !== b.len) return false
  if (!a.values.every((v, i) => v > b.values[i])) return false
  if (a.hasThree && !a.cards.every((c) => gjValue(c) === 3)) return false
  return true
}

/** 憋三:手里还有别的牌时不能出 3;只剩 3 时必须一次出完 */
export function gjLegal(play, hand) {
  if (!play) return false
  const allThrees = hand.every((c) => gjValue(c) === 3)
  if (play.cards.some((c) => gjValue(c) === 3)) return allThrees && play.len === hand.length
  if (allThrees) return play.len === hand.length
  return true
}

/**
 * 够级牌只能由对头上牌(场上剩余 5 人及以上的阶段);
 * 头科、二科产生后进入四人乱缠,不再限制。
 */
export function gjCanBeat(play, last, { seat, activeCount }) {
  if (!gjBeats(play, last)) return false
  if (last?.gouji && activeCount > 4 && seat !== gjOpposite(last.seat)) return false
  return true
}

function makeCandidate(gjComboFn, cards, hand) {
  const parsed = gjComboFn(cards)
  if (!parsed || !gjLegal(parsed, hand)) return null
  return parsed
}

/** 枚举手牌出法(last 为空=自由出牌) */
export function gjPlays(hand, last = null, { seat = 0, activeCount = 6 } = {}) {
  const out = []
  const seen = new Set()
  const add = (cards) => {
    const parsed = makeCandidate(gjCombo, cards, hand)
    if (!parsed) return
    if (last && !gjCanBeat(parsed, last, { seat, activeCount })) return
    const key = parsed.cards
      .map((c) => c.id)
      .sort()
      .join(',')
    if (seen.has(key)) return
    seen.add(key)
    out.push(parsed)
  }
  const groups = groupByValue(hand, gjValue)
  const small = (groups.get(JOKER_SMALL) || []).slice().sort((a, b) => a.id.localeCompare(b.id))
  const big = (groups.get(JOKER_BIG) || []).slice().sort((a, b) => a.id.localeCompare(b.id))
  const twos = (groups.get(15) || []).slice()
  const allThrees = hand.every((c) => gjValue(c) === 3)

  if (!last) {
    for (let rank = 3; rank <= 14; rank += 1) {
      const list = groups.get(rank)
      if (!list) continue
      if (rank === 3 && !allThrees) continue
      for (let n = 1; n <= list.length; n += 1) add(list.slice(0, n))
      if (rank >= 5) {
        for (let t = 0; t <= Math.min(2, twos.length); t += 1) {
          for (let n = 1; n <= list.length && n + t <= 8; n += 1) {
            add([...list.slice(0, n), ...twos.slice(0, t)])
            for (let j = 1; j <= Math.min(2, small.length + big.length); j += 1) {
              for (let kb = 0; kb <= Math.min(j, big.length); kb += 1) {
                const ks = j - kb
                if (ks <= small.length) add([...list.slice(0, n), ...twos.slice(0, t), ...small.slice(0, ks), ...big.slice(0, kb)])
              }
            }
          }
        }
      }
    }
    for (let n = 1; n <= twos.length; n += 1) add(twos.slice(0, n))
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
  const candidateRanks = allThrees ? [3] : [4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14]
  for (const rank of candidateRanks) {
    const list = groups.get(rank) || []
    if (!list.length) continue
    const maxAttach = rank >= 5 ? twos.length + jokerTotal : 0
    for (let b = 1; b <= Math.min(list.length, n); b += 1) {
      const need = n - b
      if (need > maxAttach) continue
      if (rank < 5 && need > 0) continue
      for (let t = 0; t <= Math.min(need, twos.length); t += 1) {
        const j = need - t
        if (j > jokerTotal) continue
        for (let kb = 0; kb <= Math.min(j, big.length); kb += 1) {
          const ks = j - kb
          if (ks > small.length) continue
          add([...list.slice(0, b), ...twos.slice(0, t), ...small.slice(0, ks), ...big.slice(0, kb)])
        }
      }
    }
  }
  // 纯 2
  if (twos.length >= n) add(twos.slice(0, n))
  // 纯王
  for (let kb = 0; kb <= Math.min(n, big.length); kb += 1) {
    const ks = n - kb
    if (ks <= small.length) add([...small.slice(0, ks), ...big.slice(0, kb)])
  }
  return out.sort((a, b) => a.baseRank - b.baseRank || a.jokers - b.jokers)
}

/** 发牌:6 家各 36 张 */
export function gjDeal(rng = Math.random) {
  const deck = shuffle(buildDeck({ decks: 4 }), rng)
  return Array.from({ length: 6 }, (_, seat) => deck.slice(seat * 36, seat * 36 + 36))
}

/** 名次积分:头科 4、二科 2、三科/四科 0、二落 -2、大落 -4;一方包揽前三(串三户)翻倍 */
export function gjScores(finishOrder) {
  const points = [4, 2, 0, 0, -2, -4]
  const seatScores = Array(6).fill(0)
  finishOrder.forEach((seat, index) => {
    if (seat >= 0) seatScores[seat] = points[index] ?? 0
  })
  const teamScores = [0, 0]
  for (let seat = 0; seat < 6; seat += 1) teamScores[seat % 2] += seatScores[seat]
  const top3 = finishOrder.slice(0, 3)
  const shun = top3.length === 3 && top3.every((s) => s >= 0 && s % 2 === top3[0] % 2)
  if (shun) {
    teamScores[0] *= 2
    teamScores[1] *= 2
  }
  return { seatScores, teamScores, shun }
}

/** 电脑选牌 */
export function gjAiChoose({ hand, last, seat, activeCount, handCounts = [], lastSeat = null }) {
  if (!hand.length) return null
  if (hand.every((c) => gjValue(c) === 3)) return gjPlays(hand, last, { seat, activeCount })[0] || null
  const plays = gjPlays(hand, last, { seat, activeCount })
  if (!plays.length) return null
  const finishes = plays.filter((p) => p.len === hand.length)
  if (finishes.length) return finishes.sort((a, b) => a.baseRank - b.baseRank)[0]
  if (last && lastSeat != null && gjIsTeammate(seat, lastSeat)) return null

  const cost = (p) => p.baseRank + p.twos * 6 + p.jokers * 40 - p.len
  if (!last) {
    const natural = plays.filter((p) => p.twos === 0 && p.jokers === 0)
    if (natural.length) {
      const low = natural.filter((p) => p.baseRank <= 12)
      return (low.length ? low : natural).sort((a, b) => a.baseRank - b.baseRank || b.len - a.len)[0]
    }
    return plays.sort((a, b) => cost(a) - cost(b))[0]
  }
  const natural = plays.filter((p) => p.twos === 0 && p.jokers === 0)
  if (natural.length) return natural.sort((a, b) => cost(a) - cost(b))[0]
  const opponentMin = Math.min(...[0, 1, 2, 3, 4, 5].filter((i) => !gjIsTeammate(seat, i) && i !== seat).map((i) => handCounts[i] ?? 99))
  if (opponentMin <= 5 || hand.length <= 8) return plays.sort((a, b) => cost(a) - cost(b))[0]
  return null
}
