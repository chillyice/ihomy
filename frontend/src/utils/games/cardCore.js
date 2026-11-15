// 扑克牌公共内核:牌对象、洗牌、排序与牌面文案。
// 牌对象 { id, suit:'S'|'H'|'C'|'D'|'JOKER', rank:1..13 或 16/17, deck:0..N }
// rank 1 = A, 11 = J, 12 = Q, 13 = K;大小王固定 16(小王)/17(大王),与各玩法点数口径一致。
export const SUITS = ['S', 'H', 'C', 'D']
export const SUIT_ORDER = { S: 0, H: 1, C: 2, D: 3, JOKER: 4 }
export const JOKER_SMALL = 16
export const JOKER_BIG = 17

const ALL_RANKS = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13]

export const RANK_TEXT = { 1: 'A', 11: 'J', 12: 'Q', 13: 'K' }

/** 牌面文案:JOKER 固定显示大小王,A/J/Q/K 用字母,其余数字 */
export function cardRankText(card) {
  if (!card) return ''
  if (card.suit === 'JOKER') return card.rank === JOKER_BIG ? 'JK' : 'jk'
  return RANK_TEXT[card.rank] || String(card.rank)
}

/**
 * 生成牌堆。
 * @param {object} [options]
 * @param {number} [options.decks=1] 副数
 * @param {number[]} [options.ranks] 参与的点数(A=1),默认 1..13
 * @param {boolean} [options.jokers=true] 是否带大小王
 */
export function buildDeck({ decks = 1, ranks = ALL_RANKS, jokers = true } = {}) {
  const cards = []
  for (let d = 0; d < decks; d += 1) {
    for (const suit of SUITS) {
      for (const rank of ranks) cards.push({ id: `${suit}-${rank}-${d}`, suit, rank, deck: d })
    }
    if (jokers) {
      cards.push({ id: `JOKER-${JOKER_SMALL}-${d}`, suit: 'JOKER', rank: JOKER_SMALL, deck: d })
      cards.push({ id: `JOKER-${JOKER_BIG}-${d}`, suit: 'JOKER', rank: JOKER_BIG, deck: d })
    }
  }
  return cards
}

/** Fisher-Yates 原地洗牌,返回同一数组;rng 默认 Math.random */
export function shuffle(cards, rng = Math.random) {
  for (let i = cards.length - 1; i > 0; i -= 1) {
    const j = Math.floor(rng() * (i + 1))
    const tmp = cards[i]
    cards[i] = cards[j]
    cards[j] = tmp
  }
  return cards
}

/** 按给定点数取值排序(稳定:同值再按花色、副数),返回新数组 */
export function sortByValue(cards, valueOf) {
  return [...cards].sort((a, b) => {
    const diff = valueOf(a) - valueOf(b)
    if (diff) return diff
    const suitDiff = (SUIT_ORDER[a.suit] ?? 9) - (SUIT_ORDER[b.suit] ?? 9)
    if (suitDiff) return suitDiff
    return a.id.localeCompare(b.id)
  })
}

/** 同点数分组:Map<value, card[]>,组内按花色稳定排序 */
export function groupByValue(cards, valueOf) {
  const groups = new Map()
  for (const card of cards) {
    const value = valueOf(card)
    if (!groups.has(value)) groups.set(value, [])
    groups.get(value).push(card)
  }
  for (const list of groups.values()) list.sort((a, b) => (SUIT_ORDER[a.suit] ?? 9) - (SUIT_ORDER[b.suit] ?? 9))
  return groups
}

/** 从若干组里取最低的 n 张(可排除指定点数),不足返回 null */
export function takeLowest(groups, n, { exclude = [], from = null } = {}) {
  const pool = []
  for (const [value, list] of groups) {
    if (exclude.includes(value)) continue
    pool.push(...list)
  }
  pool.sort((a, b) => (from ? from(a) - from(b) : 0) || (SUIT_ORDER[a.suit] ?? 9) - (SUIT_ORDER[b.suit] ?? 9))
  return pool.length >= n ? pool.slice(0, n) : null
}

/** 统计每张牌出现次数(按 id),用于校验选择没有重复 */
export function uniqueCards(cards) {
  return new Set(cards.map((c) => c.id)).size === cards.length
}
