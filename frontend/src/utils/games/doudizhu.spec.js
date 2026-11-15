import { describe, expect, it } from 'vitest'
import { DDZ_COMBO, dzAiChoose, dzBeats, dzBidStrength, dzCombo, dzDeal, dzPlays, dzValue } from './doudizhu'
import { seededRng } from './seededRng'

let serial = 0
function card(rank, suit = null) {
  serial += 1
  if (rank >= 16) return { id: `JOKER-${rank}-${serial}`, suit: 'JOKER', rank, deck: 0 }
  const s = suit || ['S', 'H', 'C', 'D'][serial % 4]
  return { id: `${s}-${rank}-${serial}`, suit: s, rank, deck: 0 }
}
const rankCard = (rank, n = 1, suit = null) => Array.from({ length: n }, () => card(rank, suit))

describe('斗地主牌型识别', () => {
  it('识别基础牌型', () => {
    expect(dzCombo(rankCard(3, 1)).type).toBe(DDZ_COMBO.SINGLE)
    expect(dzCombo(rankCard(3, 2)).type).toBe(DDZ_COMBO.PAIR)
    expect(dzCombo(rankCard(3, 3)).type).toBe(DDZ_COMBO.TRIPLE)
    expect(dzCombo([...rankCard(3, 3), card(4)]).type).toBe(DDZ_COMBO.TRIPLE_ONE)
    expect(dzCombo([...rankCard(3, 3), ...rankCard(4, 2)]).type).toBe(DDZ_COMBO.TRIPLE_PAIR)
    expect(dzCombo(rankCard(3, 4)).type).toBe(DDZ_COMBO.BOMB)
    expect(dzCombo([card(16), card(17)]).type).toBe(DDZ_COMBO.ROCKET)
  })

  it('识别顺子、连对、飞机', () => {
    const straight = [3, 4, 5, 6, 7].map((r) => card(r))
    expect(dzCombo(straight)).toMatchObject({ type: DDZ_COMBO.STRAIGHT, rank: 7 })
    expect(dzCombo([...rankCard(3, 2), ...rankCard(4, 2), ...rankCard(5, 2)]).type).toBe(DDZ_COMBO.PAIR_STRAIGHT)
    expect(dzCombo([...rankCard(3, 3), ...rankCard(4, 3)]).type).toBe(DDZ_COMBO.PLANE)
    expect(dzCombo([...rankCard(3, 3), ...rankCard(4, 3), card(9), card(10)]).type).toBe(DDZ_COMBO.PLANE_ONE)
    expect(dzCombo([...rankCard(3, 3), ...rankCard(4, 3), ...rankCard(9, 2), ...rankCard(10, 2)]).type).toBe(DDZ_COMBO.PLANE_PAIR)
    expect(dzCombo([...rankCard(3, 4), card(9), card(10)]).type).toBe(DDZ_COMBO.FOUR_TWO)
    expect(dzCombo([...rankCard(3, 4), ...rankCard(9, 2), ...rankCard(10, 2)]).type).toBe(DDZ_COMBO.FOUR_TWO_PAIR)
  })

  it('拒绝非法组合', () => {
    expect(dzCombo([])).toBeNull()
    const dup = card(3)
    expect(dzCombo([dup, dup])).toBeNull()
    expect(dzCombo([card(3), card(4), card(5), card(6)])).toBeNull()
    expect(dzCombo([card(1), card(2), card(3), card(4), card(5)])).toBeNull()
    expect(dzCombo([...rankCard(3, 3), ...rankCard(5, 3)])).toBeNull()
    expect(dzCombo([card(15), card(15), card(15), card(15)]).type).toBe(DDZ_COMBO.BOMB)
  })
})

describe('斗地主大小比较', () => {
  it('同型同长比点数,炸弹压普通牌,王炸最大', () => {
    const pair5 = dzCombo(rankCard(5, 2))
    const pair6 = dzCombo(rankCard(6, 2))
    expect(dzBeats(pair6, pair5)).toBe(true)
    expect(dzBeats(pair5, pair6)).toBe(false)
    expect(dzBeats(dzCombo(rankCard(9, 4)), pair6)).toBe(true)
    expect(dzBeats(pair6, dzCombo(rankCard(9, 4)))).toBe(false)
    expect(dzBeats(dzCombo([card(16), card(17)]), dzCombo(rankCard(9, 4)))).toBe(true)
    expect(dzBeats(dzCombo(rankCard(9, 4)), dzCombo([card(16), card(17)]))).toBe(false)
  })

  it('长度不同不能比较', () => {
    const straight5 = dzCombo([3, 4, 5, 6, 7].map((r) => card(r)))
    const straight6 = dzCombo([4, 5, 6, 7, 8, 9].map((r) => card(r)))
    expect(dzBeats(straight6, straight5)).toBe(false)
  })
})

describe('斗地主发牌与选牌', () => {
  it('发牌张数正确且不重复', () => {
    const { hands, bottom } = dzDeal(seededRng(7))
    expect(hands.map((h) => h.length)).toEqual([17, 17, 17])
    expect(bottom).toHaveLength(3)
    const ids = new Set([...hands.flat(), ...bottom].map((c) => c.id))
    expect(ids.size).toBe(54)
  })

  it('枚举的候选牌型全部合法且能压过上一手', () => {
    const hand = [...rankCard(3, 2), ...rankCard(4, 2), ...rankCard(5, 1), ...rankCard(9, 4), card(16), card(17)]
    const last = dzCombo(rankCard(6, 2))
    const plays = dzPlays(hand, last)
    expect(plays.length).toBeGreaterThan(0)
    for (const play of plays) {
      expect(dzCombo(play.cards)).not.toBeNull()
      expect(dzBeats(play, last)).toBe(true)
    }
  })

  it('电脑能出牌且不会拆王炸去压小牌', () => {
    const hand = [...rankCard(3, 1), ...rankCard(4, 1), ...rankCard(5, 1), card(16), card(17)]
    const last = dzCombo(rankCard(4, 1))
    const pick = dzAiChoose({ hand, last, seat: 0, landlord: 0, handCounts: [5, 8, 8], lastSeat: 1, playCounts: [0, 3, 3] })
    expect(pick).not.toBeNull()
    expect(pick.type).toBe(DDZ_COMBO.SINGLE)
    expect(pick.cards.some((c) => c.suit === 'JOKER')).toBe(false)
  })

  it('叫分强度随大牌增加', () => {
    const weak = [...rankCard(3, 4), ...rankCard(4, 4), ...rankCard(5, 4)]
    const strong = [...rankCard(15, 4), ...rankCard(14, 4), card(16), card(17)]
    expect(dzBidStrength(strong)).toBeGreaterThan(dzBidStrength(weak))
    expect(dzValue(card(2))).toBe(15)
  })
})
