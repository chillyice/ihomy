import { describe, expect, it } from 'vitest'
import { GD_TYPE, gdAdvance, gdAiChoose, gdBeats, gdComboOf, gdCombos, gdDeal, gdIsWild, gdTribute, gdValue } from './guandan'
import { seededRng } from './seededRng'

let serial = 0
function card(rank, suit = 'S') {
  serial += 1
  const s = rank >= 16 ? 'JOKER' : suit
  return { id: `${s}-${rank}-${serial}`, suit: s, rank, deck: 0 }
}
const many = (rank, n, suit = 'S') => Array.from({ length: n }, (_, i) => card(rank, i === 0 ? suit : ['S', 'H', 'C', 'D'][i % 4]))

describe('掼蛋牌型识别', () => {
  it('级牌与逢人配', () => {
    const wild = card(2, 'H')
    expect(gdIsWild(wild, 2)).toBe(true)
    expect(gdComboOf([wild], 2)).toMatchObject({ type: GD_TYPE.SINGLE, rank: 15 })
    expect(gdComboOf([card(7), wild], 2)).toMatchObject({ type: GD_TYPE.PAIR, rank: 7 })
    expect(gdComboOf([card(9), card(9, 'C'), card(9, 'D'), wild], 2)).toMatchObject({
      type: GD_TYPE.BOMB,
      rank: 9,
      power: 4,
    })
    expect(gdComboOf([card(2, 'S'), card(2, 'C')], 2)).toMatchObject({ type: GD_TYPE.PAIR, rank: 15 })
  })

  it('顺子、三连对、钢板、同花顺', () => {
    const mixedStraight = [3, 4, 5, 6, 7].map((r, i) => card(r, i % 2 ? 'C' : 'S'))
    expect(gdComboOf(mixedStraight, 2)).toMatchObject({ type: GD_TYPE.STRAIGHT, rank: 7 })
    expect(gdComboOf([card(3), card(3, 'C'), card(4), card(4, 'C'), card(5), card(5, 'C')], 2)).toMatchObject({
      type: GD_TYPE.TUBE,
      rank: 5,
    })
    expect(gdComboOf([card(3), card(3, 'C'), card(3, 'D'), card(4), card(4, 'C'), card(4, 'D')], 2)).toMatchObject({
      type: GD_TYPE.PLATE,
      rank: 4,
    })
    expect(
      gdComboOf(
        [3, 4, 5, 6, 7].map((r) => card(r, 'H')),
        2,
      ),
    ).toMatchObject({
      type: GD_TYPE.STRAIGHT_FLUSH,
      power: 5.5,
    })
    expect(gdComboOf([card(3, 'S'), card(4, 'S'), card(5, 'S'), card(6, 'S'), card(2, 'H')], 2)).toMatchObject({
      type: GD_TYPE.STRAIGHT_FLUSH,
    })
  })

  it('三带二与王炸', () => {
    expect(gdComboOf([card(7), card(7, 'C'), card(7, 'D'), card(9), card(9, 'C')], 2)).toMatchObject({
      type: GD_TYPE.FULL_HOUSE,
      rank: 7,
    })
    expect(gdComboOf([card(16), card(16, 'H'), card(17), card(17, 'H')], 2)).toMatchObject({
      type: GD_TYPE.ROCKET,
      power: 9,
    })
  })

  it('拒绝非法组合', () => {
    expect(gdComboOf([card(3), card(4)], 2)).toBeNull()
    expect(gdComboOf([card(3), card(4), card(5), card(6), card(8)], 2)).toBeNull()
    expect(gdComboOf([card(16), card(3)], 2)).toBeNull()
    expect(gdComboOf([card(3), card(3, 'C'), card(3, 'D'), card(4), card(4, 'C'), card(4, 'D'), card(5)], 2)).toBeNull()
  })
})

describe('掼蛋大小比较', () => {
  const bomb4 = gdComboOf([card(3), card(3, 'C'), card(3, 'D'), card(3, 'H')], 2)
  const bomb5 = gdComboOf([card(3), card(3, 'C'), card(3, 'D'), card(3, 'H'), card(3, 'S')], 2)
  const sf = gdComboOf(
    [4, 5, 6, 7, 8].map((r) => card(r, 'S')),
    2,
  )
  const bomb6 = gdComboOf(
    [4, 4, 4, 4, 4, 4].map((r, i) => card(r, ['S', 'H', 'C', 'D'][i % 4])),
    2,
  )
  const rocket = gdComboOf([card(16), card(16, 'H'), card(17), card(17, 'H')], 2)

  it('炸弹梯度:4炸 < 5炸 < 同花顺 < 6炸 < 王炸', () => {
    expect(gdBeats(bomb5, bomb4)).toBe(true)
    expect(gdBeats(sf, bomb5)).toBe(true)
    expect(gdBeats(bomb6, sf)).toBe(true)
    expect(gdBeats(rocket, bomb6)).toBe(true)
    expect(gdBeats(bomb4, sf)).toBe(false)
  })

  it('同型同长比点数,级牌最大', () => {
    const pairA = gdComboOf([card(14), card(14, 'C')], 2)
    const pairLevel = gdComboOf([card(2), card(2, 'C')], 2)
    expect(gdBeats(pairLevel, pairA)).toBe(true)
    expect(gdValue(card(2), 2)).toBe(15)
    expect(gdValue(card(1), 2)).toBe(14)
  })
})

describe('掼蛋发牌、枚举与电脑', () => {
  it('两副牌四家各 27 张', () => {
    const hands = gdDeal(seededRng(9))
    expect(hands.map((h) => h.length)).toEqual([27, 27, 27, 27])
    expect(new Set(hands.flat().map((c) => c.id)).size).toBe(108)
  })

  it('候选牌型全部合法', () => {
    const hand = [...many(3, 4), ...many(4, 4), ...many(5, 4), card(6, 'S'), card(7, 'S'), card(8, 'S'), card(2, 'H')]
    const combos = gdCombos(hand, 2)
    expect(combos.length).toBeGreaterThan(0)
    for (const combo of combos) {
      expect(gdComboOf(combo.cards, 2)).not.toBeNull()
    }
  })

  it('电脑能压牌且不会拆对王', () => {
    const hand = [card(3), card(3, 'C'), card(9), card(9, 'C'), card(16), card(17)]
    const last = gdComboOf([card(8), card(8, 'C')], 2)
    const pick = gdAiChoose({ hand, last, level: 2, seat: 0, handCounts: [6, 10, 9, 11], lastSeat: 1 })
    expect(pick).not.toBeNull()
    expect(gdBeats(pick, last)).toBe(true)
    expect(pick.bomb).toBe(false)
  })

  it('升级与进贡还贡', () => {
    expect(gdAdvance(2)).toBe(3)
    expect(gdAdvance(3)).toBe(2)
    expect(gdAdvance(4)).toBe(1)
    const hands = [
      [card(3), card(4)],
      [card(5), card(6)],
      [card(7), card(8)],
      [card(13), card(6, 'C')],
    ]
    const result = gdTribute(hands, 2, [0, 1, 2, 3])
    expect(result.anti).toBe(false)
    expect(result.leader).toBe(0)
    expect(result.hands[0].length).toBe(2)
    expect(result.tribute.card.rank).toBe(13)
    const antiHands = [[card(3)], [card(4)], [card(5)], [card(17), card(17, 'H')]]
    expect(gdTribute(antiHands, 2, [0, 1, 2, 3]).anti).toBe(true)
  })
})
