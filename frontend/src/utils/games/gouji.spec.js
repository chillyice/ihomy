import { describe, expect, it } from 'vitest'
import { gjAiChoose, gjBeats, gjCanBeat, gjCombo, gjDeal, gjIsTeammate, gjLegal, gjOpposite, gjPlays, gjScores } from './gouji'
import { seededRng } from './seededRng'

let serial = 0
function card(rank, suit = 'S') {
  serial += 1
  const s = rank >= 16 ? 'JOKER' : suit
  return { id: `${s}-${rank}-${serial}`, suit: s, rank, deck: 0 }
}
const many = (rank, n, suit = 'S') => Array.from({ length: n }, (_, i) => card(rank, i === 0 ? suit : ['S', 'H', 'C', 'D'][i % 4]))

describe('够级牌堆与牌型', () => {
  it('4 副牌 216 张,六家各 36 张', () => {
    const hands = gjDeal(seededRng(3))
    expect(hands.map((h) => h.length)).toEqual(Array(6).fill(36))
    expect(new Set(hands.flat().map((c) => c.id)).size).toBe(216)
  })

  it('贴 2 与挂王都算同点数,够级牌按阈值判定', () => {
    expect(gjCombo(many(10, 5))).toMatchObject({ baseRank: 10, gouji: true })
    expect(gjCombo(many(10, 4)).gouji).toBe(false)
    expect(gjCombo(many(12, 3)).gouji).toBe(true)
    expect(gjCombo(many(13, 2)).gouji).toBe(true)
    expect(gjCombo([card(2)])).toMatchObject({ baseRank: 15, gouji: true })
    const withTwo = gjCombo([...many(7, 3), card(2, 'H')])
    expect(withTwo).toMatchObject({ baseRank: 7, twos: 1, gouji: false })
    expect(withTwo.values).toEqual([7, 7, 7, 7])
    expect(gjCombo([...many(10, 5), card(2, 'H')])).toMatchObject({ gouji: true })
    expect(gjCombo([...many(9, 2), card(17)])).toMatchObject({ jokers: 1, gouji: true })
  })

  it('拒绝不同点数,3/4 不能贴 2 或挂王', () => {
    expect(gjCombo([card(7), card(8)])).toBeNull()
    expect(gjCombo([...many(3, 2), card(2, 'H')])).toBeNull()
    expect(gjCombo([...many(4, 2), card(16)])).toBeNull()
    expect(gjCombo([card(16), card(17)])).toMatchObject({ baseRank: 0, jokers: 2 })
  })

  it('憋三:手里还有别的牌不能出 3,只剩 3 时必须一把出完', () => {
    const threes = many(3, 3)
    expect(gjLegal(gjCombo(threes.slice(0, 1)), threes)).toBe(false)
    expect(gjLegal(gjCombo(threes), threes)).toBe(true)
    expect(gjLegal(gjCombo(threes.slice(0, 1)), [...threes, card(9)])).toBe(false)
    expect(gjLegal(gjCombo(many(9, 1)), [...threes, card(9)])).toBe(true)
  })
})

describe('够级大小与够级限制', () => {
  it('同张数逐张比较', () => {
    expect(gjBeats(gjCombo(many(9, 4)), gjCombo(many(8, 4)))).toBe(true)
    expect(gjBeats(gjCombo([...many(8, 4), card(2, 'H')]), gjCombo(many(9, 5)))).toBe(false)
    expect(gjBeats(gjCombo([...many(8, 4), card(2, 'H')]), gjCombo(many(8, 5)))).toBe(false)
    expect(gjBeats(gjCombo([...many(9, 4), card(2, 'H')]), gjCombo(many(8, 5)))).toBe(true)
  })

  it('够级牌只有对头能上,四人乱缠后放开', () => {
    const last = { ...gjCombo(many(10, 5)), seat: 1 }
    const play = gjCombo(many(11, 5))
    expect(gjOpposite(1)).toBe(4)
    expect(gjCanBeat(play, last, { seat: 0, activeCount: 6 })).toBe(false)
    expect(gjCanBeat(play, last, { seat: 4, activeCount: 6 })).toBe(true)
    expect(gjCanBeat(play, last, { seat: 0, activeCount: 4 })).toBe(true)
  })

  it('候选出法合法且满足够级限制', () => {
    const hand = [...many(9, 4), ...many(10, 5), card(2, 'H'), card(17)]
    const last = { ...gjCombo(many(8, 4)), seat: 4 }
    const plays = gjPlays(hand, last, { seat: 1, activeCount: 6 })
    expect(plays.length).toBeGreaterThan(0)
    for (const play of plays) {
      expect(gjLegal(play, hand)).toBe(true)
      expect(gjCanBeat(play, last, { seat: 1, activeCount: 6 })).toBe(true)
    }
  })

  it('电脑领出时避免拆 3 与过早用王', () => {
    const hand = [...many(3, 2), ...many(6, 3), ...many(9, 2), card(17)]
    const pick = gjAiChoose({ hand, last: null, seat: 0, activeCount: 6, handCounts: [36, 36, 36, 36, 36, 36] })
    expect(pick).not.toBeNull()
    expect(pick.baseRank).not.toBe(3)
    expect(pick.jokers).toBe(0)
  })
})

describe('够级积分', () => {
  it('名次积分与串三户翻倍', () => {
    const normal = gjScores([0, 1, 2, 3, 4, 5])
    expect(normal.seatScores).toEqual([4, 2, 0, 0, -2, -4])
    expect(normal.teamScores).toEqual([2, -2])
    const shun = gjScores([0, 2, 4, 1, 3, 5])
    expect(shun.shun).toBe(true)
    expect(shun.teamScores[0]).toBe(12)
    expect(gjIsTeammate(0, 2)).toBe(true)
    expect(gjIsTeammate(0, 3)).toBe(false)
  })
})
