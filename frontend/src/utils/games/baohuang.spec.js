import { describe, expect, it } from 'vitest'
import { BH_MARK, bhAiChoose, bhBeats, bhBuildDeck, bhCombo, bhDeal, bhPlays, bhRoles, bhValue } from './baohuang'
import { seededRng } from './seededRng'

let serial = 0
function card(rank, suit = 'S') {
  serial += 1
  const s = rank >= 16 ? 'JOKER' : suit
  return { id: `${s}-${rank}-${serial}`, suit: s, rank, deck: 0 }
}
const many = (rank, n, suit = 'S') => Array.from({ length: n }, (_, i) => card(rank, i === 0 ? suit : ['S', 'H', 'C', 'D'][i % 4]))

describe('保皇牌堆与身份', () => {
  it('168 张、去掉 3/4/5、皇帝牌与侍卫牌各一张', () => {
    const deck = bhBuildDeck()
    expect(deck).toHaveLength(168)
    expect(deck.some((c) => [3, 4, 5].includes(c.rank))).toBe(false)
    expect(deck.filter((c) => c.mark === BH_MARK.EMPEROR)).toHaveLength(1)
    expect(deck.filter((c) => c.mark === BH_MARK.GUARD)).toHaveLength(1)
  })

  it('发牌 5 家 34/34/34/33/33 且身份可定位', () => {
    const hands = bhDeal(seededRng(11))
    expect(hands.map((h) => h.length)).toEqual([34, 34, 34, 33, 33])
    const roles = bhRoles(hands)
    expect(roles.emperor).toBeGreaterThanOrEqual(0)
    expect(roles.guard).toBeGreaterThanOrEqual(0)
    expect(roles.royal.length).toBe(roles.solo ? 1 : 2)
  })
})

describe('保皇牌型与比较', () => {
  it('同点套牌可挂王,混点数不行', () => {
    expect(bhCombo([card(7), card(7, 'C'), card(7, 'D'), card(16)])).toMatchObject({ baseRank: 7, jokers: 1 })
    expect(bhCombo([card(7), card(8)])).toBeNull()
    expect(bhCombo([card(16), card(17)])).toMatchObject({ baseRank: 0, jokers: 2 })
    expect(bhValue(card(2))).toBe(15)
    expect(bhValue(card(1))).toBe(14)
  })

  it('逐张比较:挂小王必须挂大王才压得住,自然套牌压不住挂王', () => {
    const small = card(16)
    const big = card(17)
    const triple7 = bhCombo([card(7), card(7, 'C'), card(7, 'D')])
    const triple8 = bhCombo([card(8), card(8, 'C'), card(8, 'D')])
    expect(bhBeats(triple8, triple7)).toBe(true)
    const sevenSmall = bhCombo([card(7), card(7, 'C'), card(7, 'D'), small])
    const eightSmall = bhCombo([card(8), card(8, 'C'), card(8, 'D'), small])
    expect(bhBeats(eightSmall, sevenSmall)).toBe(false)
    const nineBig = bhCombo([card(9), card(9, 'C'), card(9, 'D'), big])
    expect(bhBeats(nineBig, sevenSmall)).toBe(true)
    const fourEights = bhCombo([card(8), card(8, 'C'), card(8, 'D'), card(8, 'H')])
    expect(bhBeats(fourEights, sevenSmall)).toBe(false)
  })

  it('枚举出的出法都能压过上一手', () => {
    const hand = [...many(7, 3), ...many(8, 4), card(16), card(17)]
    const last = bhCombo([card(6), card(6, 'C')])
    const plays = bhPlays(hand, last)
    expect(plays.length).toBeGreaterThan(0)
    for (const play of plays) expect(bhBeats(play, last)).toBe(true)
    expect(bhPlays(hand, last).some((p) => p.len === 2 && p.baseRank === 7)).toBe(true)
  })

  it('电脑跟牌优先不用王', () => {
    const hand = [...many(7, 2), ...many(9, 2), card(16)]
    const last = bhCombo([card(8), card(8, 'C')])
    const pick = bhAiChoose({ hand, last, opponentMin: 10 })
    expect(pick).not.toBeNull()
    expect(pick.baseRank).toBe(9)
    expect(pick.jokers).toBe(0)
  })
})
