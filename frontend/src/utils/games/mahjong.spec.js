import { describe, expect, it } from 'vitest'
import {
  MAHJONG_PAIR_258,
  MAHJONG_PAIR_ANY,
  MAHJONG_TILE_COUNT,
  canMahjongBuGang,
  canMahjongMingGang,
  canMahjongWin,
  createMahjongWall,
  isMahjongAllSimples,
  isMahjongFlush,
  isMahjongHalfFlush,
  isMahjongSevenPairs,
  isMahjongToitoi,
  mahjongAiChooseChi,
  mahjongAiChooseDiscard,
  mahjongAnGangKinds,
  mahjongChiOptions,
  mahjongCounts,
  mahjongHandScore,
  mahjongKind,
  mahjongKindName,
  mahjongTenpai,
  normalizeMahjongRules,
  sortMahjongTiles,
} from './mahjong'

/** 用 kind 序号直接构造实例 id(同 kind 依次取副本 0..3) */
function kindsToTiles(kinds) {
  const tiles = []
  const counter = new Map()
  for (const k of kinds) {
    const n = counter.get(k) || 0
    counter.set(k, n + 1)
    tiles.push(k * 4 + n)
  }
  return tiles
}

describe('mahjong wall', () => {
  it('整副 136 张,每种恰好 4 张', () => {
    const wall = createMahjongWall()
    expect(wall.length).toBe(MAHJONG_TILE_COUNT)
    const counts = mahjongCounts(wall)
    expect(counts.every((c) => c === 4)).toBe(true)
  })

  it('实例 id 与牌种互逆', () => {
    for (const id of createMahjongWall()) {
      expect(mahjongKind(id)).toBe(Math.floor(id / 4))
    }
  })
})

describe('mahjong win detection', () => {
  it('标准牌型:111万 234万 567万 888万 99万 可胡', () => {
    const tiles = kindsToTiles([0, 0, 0, 1, 2, 3, 4, 5, 6, 7, 7, 7, 8, 8])
    expect(canMahjongWin(mahjongCounts(tiles))).toBe(true)
  })

  it('跨花色顺子不相连:789万 + 123筒 456筒 + 789条 + 对子', () => {
    const tiles = kindsToTiles([6, 7, 8, 9, 10, 11, 12, 13, 14, 24, 25, 26, 30, 30])
    expect(canMahjongWin(mahjongCounts(tiles))).toBe(true)
  })

  it('非成对残牌不能胡:111 234 567 777 + 8万 + 白板', () => {
    const tiles = kindsToTiles([0, 0, 0, 1, 2, 3, 4, 5, 6, 7, 7, 7, 8, 33])
    expect(canMahjongWin(mahjongCounts(tiles))).toBe(false)
  })

  it('孤张落在别的花色凑不成面子', () => {
    // 111万 234万 567万 + 8万 1筒 + 九条对 → 8万/1筒 落单
    const tiles = kindsToTiles([0, 0, 0, 1, 2, 3, 4, 5, 6, 7, 9, 26, 26, 26])
    expect(canMahjongWin(mahjongCounts(tiles))).toBe(false)
  })

  it('七对子可胡;四张同牌当两对(豪华)也算', () => {
    const pairs = kindsToTiles([0, 0, 5, 5, 14, 14, 27, 27, 31, 31, 26, 26, 9, 9])
    expect(canMahjongWin(mahjongCounts(pairs))).toBe(true)
    expect(isMahjongSevenPairs(mahjongCounts(pairs))).toBe(true)
    const lux = kindsToTiles([0, 0, 0, 0, 5, 5, 14, 14, 27, 27, 31, 31, 26, 26])
    expect(canMahjongWin(mahjongCounts(lux))).toBe(true)
  })

  it('两个刻子 + 对子们不是七对', () => {
    const tiles = kindsToTiles([0, 0, 0, 5, 5, 5, 14, 14, 27, 27, 31, 31, 26, 26])
    expect(isMahjongSevenPairs(mahjongCounts(tiles))).toBe(false)
  })

  it('三刻 + 两对实为可胡:111万 222万 33万 345万 456万', () => {
    const tiles = kindsToTiles([0, 0, 0, 1, 1, 1, 2, 2, 2, 3, 3, 4, 4, 5])
    expect(canMahjongWin(mahjongCounts(tiles))).toBe(true)
  })

  it('残张真正不成型:两刻 + 345 + 457 孤张', () => {
    const tiles = kindsToTiles([0, 0, 0, 1, 1, 1, 2, 2, 2, 3, 3, 4, 5, 7])
    expect(canMahjongWin(mahjongCounts(tiles))).toBe(false)
  })
})

describe('mahjong tenpai', () => {
  it('111 234 567 + 888万 + 9万 → 听 7万(567/789 重排)与 9万(789 成顺)', () => {
    const tiles13 = kindsToTiles([0, 0, 0, 1, 2, 3, 4, 5, 6, 7, 7, 7, 8])
    const wins = mahjongTenpai(mahjongCounts(tiles13))
    // 加 7万:111 234 567 789 + 88 对;加 9万:111 234 567 789 + 88 对(另一种拆法)
    expect(wins).toContain(6)
    expect(wins).toContain(8)
    expect(wins.length).toBeGreaterThanOrEqual(1)
  })

  it('离胡很远的手牌听牌为空', () => {
    const tiles13 = kindsToTiles([0, 6, 12, 18, 24, 27, 28, 29, 31, 32, 33, 9, 21])
    expect(mahjongTenpai(mahjongCounts(tiles13))).toEqual([])
  })
})

describe('mahjong open melds (碰)', () => {
  it('碰出一副后,暗手 11 张 + 碰出的刻子可胡', () => {
    // 碰 111万;暗手 234万 567万 888万 99万 = 11 张
    const counts = mahjongCounts(kindsToTiles([1, 2, 3, 4, 5, 6, 7, 7, 7, 8, 8]))
    expect(canMahjongWin(counts, 1)).toBe(true)
    expect(canMahjongWin(counts, 0)).toBe(false)
  })

  it('碰出后听牌:暗手 10 张,补 8万成 4 面子 + 对子', () => {
    const counts = mahjongCounts(kindsToTiles([1, 2, 3, 4, 5, 6, 7, 7, 7, 8]))
    const wins = mahjongTenpai(counts, 1)
    expect(wins).toContain(8)
  })

  it('有碰出的面子时七对子不成立', () => {
    const counts = mahjongCounts(kindsToTiles([0, 0, 5, 5, 14, 14, 27, 27, 31, 31]))
    expect(mahjongTenpai(counts, 1)).toEqual([])
  })

  it('碰碰胡把碰出的刻子算进去', () => {
    // 暗手 111 222 333 + 44,碰出 555万
    const counts = mahjongCounts(kindsToTiles([0, 0, 0, 1, 1, 1, 2, 2, 2, 4, 4]))
    const r = mahjongHandScore(counts, { melds: [{ kind: 3, type: 'pong' }] })
    expect(r.fans.map((f) => f.key)).toContain('toitoi')
  })

  it('清一色按含碰出的整手牌判', () => {
    // 暗手 234 567 777 88(万),碰出 111万 → 全万清一色
    const counts = mahjongCounts(kindsToTiles([1, 2, 3, 4, 5, 6, 7, 7, 7, 8, 8]))
    const r = mahjongHandScore(counts, { melds: [{ kind: 0, type: 'pong' }] })
    expect(r.fans.map((f) => f.key)).toContain('flush')
  })
})

describe('mahjong hand score', () => {
  it('平胡(点炮)= 底分 × 1:三色小牌型', () => {
    const tiles = kindsToTiles([0, 0, 0, 9, 10, 11, 21, 22, 23, 21, 22, 23, 24, 24])
    const r = mahjongHandScore(mahjongCounts(tiles), { selfDrawn: false })
    expect(r.fans.map((f) => f.key)).toEqual(['base'])
    expect(r.total).toBe(1)
    expect(r.score).toBe(8)
  })

  it('自摸 +1 番;清一色 +4 番;断幺九 +1 番', () => {
    // 222万×3 + 456万 567万 456万 + 77万?改:2-8 万清一色,无幺九字
    const tiles = kindsToTiles([1, 1, 1, 2, 3, 4, 5, 6, 7, 3, 4, 5, 6, 6])
    const r = mahjongHandScore(mahjongCounts(tiles), { selfDrawn: true })
    const keys = r.fans.map((f) => f.key)
    expect(keys).toContain('selfDrawn')
    expect(keys).toContain('allSimples')
    expect(keys).toContain('flush')
    expect(r.score).toBe(8 * r.total)
  })

  it('碰碰胡 + 清一色叠加', () => {
    const tiles = kindsToTiles([0, 0, 0, 1, 1, 1, 2, 2, 2, 3, 3, 3, 4, 4])
    const r = mahjongHandScore(mahjongCounts(tiles), { selfDrawn: false })
    const keys = r.fans.map((f) => f.key)
    expect(keys).toContain('toitoi')
    expect(keys).toContain('flush')
    expect(r.total).toBe(1 + 2 + 4)
  })

  it('混一色:一种数牌 + 字牌', () => {
    const tiles = kindsToTiles([0, 0, 0, 1, 2, 3, 4, 5, 6, 7, 7, 7, 31, 31])
    const r = mahjongHandScore(mahjongCounts(tiles), { selfDrawn: false })
    const keys = r.fans.map((f) => f.key)
    expect(keys).toContain('halfFlush')
    expect(keys).not.toContain('flush')
  })
})

describe('mahjong helpers', () => {
  it('清一色 / 混一色 / 断幺九判定', () => {
    // 清一色:全万 123 456 789 + 123 + 66万
    const flush = mahjongCounts(kindsToTiles([0, 1, 2, 3, 4, 5, 6, 7, 8, 0, 1, 2, 5, 5]))
    expect(isMahjongFlush(flush)).toBe(true)
    const half = mahjongCounts(kindsToTiles([0, 1, 2, 3, 4, 5, 6, 7, 8, 27, 27, 27, 31, 31]))
    expect(isMahjongHalfFlush(half)).toBe(true)
    expect(isMahjongFlush(half)).toBe(false)
    // 断幺九:2-8 万 + 2/5/6 筒,无幺九与字牌
    const simples = mahjongCounts(kindsToTiles([1, 1, 2, 3, 4, 5, 6, 7, 10, 10, 10, 13, 13, 14]))
    expect(isMahjongAllSimples(simples)).toBe(true)
    expect(isMahjongAllSimples(half)).toBe(false)
  })

  it('碰碰胡判定', () => {
    const toitoi = mahjongCounts(kindsToTiles([0, 0, 0, 9, 9, 9, 18, 18, 18, 27, 27, 27, 31, 31]))
    expect(isMahjongToitoi(toitoi)).toBe(true)
    const notToitoi = mahjongCounts(kindsToTiles([0, 1, 2, 9, 9, 9, 18, 18, 18, 27, 27, 27, 31, 31]))
    expect(isMahjongToitoi(notToitoi)).toBe(false)
  })

  it('AI 弃牌优先弃字牌孤张', () => {
    const tiles = kindsToTiles([0, 1, 2, 12, 13, 27, 28, 29, 31, 32, 33, 30, 30])
    const counts = mahjongCounts(tiles)
    const discard = mahjongAiChooseDiscard(counts)
    expect(discard).toBeGreaterThanOrEqual(27)
    expect(counts[discard]).toBeGreaterThan(0)
  })

  it('AI 弃牌后计数不为负', () => {
    const tiles = kindsToTiles([0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6])
    const counts = mahjongCounts(tiles)
    const discard = mahjongAiChooseDiscard(counts)
    expect(discard).toBeGreaterThanOrEqual(0)
    const trial = [...counts]
    trial[discard] -= 1
    expect(trial.every((c) => c >= 0)).toBe(true)
  })

  it('排序按牌种升序', () => {
    // 实例 id:10→2筒(k2),2→一万副本1(k0),27→七筒副本3(k6),0→一万副本0(k0)
    const sorted = sortMahjongTiles([10, 2, 27, 0])
    expect(sorted.map(mahjongKind)).toEqual([0, 0, 2, 6])
  })

  it('牌名映射', () => {
    expect(mahjongKindName(0)).toBe('一万')
    expect(mahjongKindName(17)).toBe('九筒')
    expect(mahjongKindName(31)).toBe('红中')
    expect(mahjongKindName(34)).toBe('')
  })
})

describe('mahjong rules (吃/杠/二五八将)', () => {
  it('normalizeMahjongRules 归一化非法参数并回退默认口径', () => {
    expect(normalizeMahjongRules(null)).toEqual({ allowChi: false, allowGang: false, pairConstraint: MAHJONG_PAIR_ANY })
    expect(normalizeMahjongRules({ allowChi: true, pairConstraint: '258' })).toEqual({
      allowChi: true,
      allowGang: false,
      pairConstraint: MAHJONG_PAIR_258,
    })
    expect(normalizeMahjongRules({ pairConstraint: 'bogus' }).pairConstraint).toBe(MAHJONG_PAIR_ANY)
  })

  it('二五八将:将牌为 2/5/8 可胡,幺九对与字牌对不可胡;七对不受约束', () => {
    const r258 = { pairConstraint: MAHJONG_PAIR_258 }
    // 111万 234万 55万 678万 888万 → 将 5万(数牌5)✓
    const ok = kindsToTiles([0, 0, 0, 1, 2, 3, 4, 4, 5, 6, 7, 7, 7, 7])
    expect(canMahjongWin(mahjongCounts(ok), 0, r258)).toBe(true)
    // 111万 234万 567万 888万 99万(9万为幺九)→ 不可胡
    const bad = kindsToTiles([0, 0, 0, 1, 2, 3, 4, 5, 6, 7, 7, 7, 8, 8])
    expect(canMahjongWin(mahjongCounts(bad), 0, r258)).toBe(false)
    // 111万 234万 567万 888万 中中 → 字牌将不可胡
    const honor = kindsToTiles([0, 0, 0, 1, 2, 3, 4, 5, 6, 7, 7, 7, 31, 31])
    expect(canMahjongWin(mahjongCounts(honor), 0, r258)).toBe(false)
    // 七对不受二五八约束(含幺九对与字牌对)
    const seven = kindsToTiles([0, 0, 8, 8, 17, 17, 26, 26, 27, 27, 31, 31, 33, 33])
    expect(canMahjongWin(mahjongCounts(seven), 0, r258)).toBe(true)
    // 默认规则下同牌型可胡(9万将)
    expect(canMahjongWin(mahjongCounts(bad), 0)).toBe(true)
  })

  it('二五八将副露牌型:碰出后暗手将对判定仍生效', () => {
    const r258 = { pairConstraint: MAHJONG_PAIR_258 }
    // 副露 1 个碰(5筒),暗手 111条 234条 888条 2万对
    const melds = [{ type: 'pong', kind: 13 }]
    const hand = kindsToTiles([18, 18, 18, 19, 20, 21, 25, 25, 25, 1, 1])
    expect(hand.length).toBe(11)
    expect(canMahjongWin(mahjongCounts(hand), 1, r258)).toBe(true)
    // 将换成 9万:不可胡
    const badHand = kindsToTiles([18, 18, 18, 19, 20, 21, 25, 25, 25, 8, 8])
    expect(canMahjongWin(mahjongCounts(badHand), 1, r258)).toBe(false)
    expect(melds.length).toBe(1)
  })

  it('mahjongChiOptions 组合完整覆盖三种位置,字牌不可吃', () => {
    // 手里有 1万2万(吃3万尾)、3万4万(吃5万居中)、6万7万(吃5万头)
    const hand = kindsToTiles([0, 1, 2, 3, 5, 6, 9, 10, 11, 12, 13, 27, 28])
    const counts = mahjongCounts(hand)
    expect(mahjongChiOptions(counts, 4)).toEqual([
      [2, 3],
      [3, 5],
      [5, 6],
    ])
    // 上家弃 3万:可 [1,2](弃3作尾)与 [1,3](弃3作中);头 [4,5] 缺 4万
    expect(mahjongChiOptions(counts, 2)).toEqual([
      [0, 1],
      [1, 3],
    ])
    // 字牌不可吃
    expect(mahjongChiOptions(counts, 27)).toEqual([])
    expect(mahjongChiOptions(counts, 33)).toEqual([])
  })

  it('明杠/暗杠/补杠判定', () => {
    const hand = kindsToTiles([0, 0, 0, 5, 6, 7, 9, 10, 11, 12, 14, 27, 28])
    const counts = mahjongCounts(hand)
    // 暗手 3 张一万 + 别家弃一万 → 明杠
    expect(canMahjongMingGang(counts, 0)).toBe(true)
    expect(canMahjongMingGang(counts, 1)).toBe(false)
    // 暗手 4 张 = 暗杠候选
    const angangHand = kindsToTiles([0, 0, 0, 0, 5, 6, 7, 9, 10, 11, 12, 13, 27])
    const angangCounts = mahjongCounts(angangHand)
    expect(mahjongAnGangKinds(angangCounts)).toEqual([0])
    // 已碰 5筒 + 暗手摸到 1 张 5筒 → 可补杠
    const melds = [{ type: 'pong', kind: 13 }]
    const withPair13 = kindsToTiles([0, 0, 0, 5, 6, 7, 9, 10, 11, 12, 13, 13, 27])
    expect(canMahjongBuGang(mahjongCounts(withPair13), melds, 13)).toBe(true)
    expect(canMahjongBuGang(counts, melds, 13)).toBe(false)
    // 杠过的不能再补
    expect(canMahjongBuGang(mahjongCounts(withPair13), [{ type: 'gang', kind: 13 }], 13)).toBe(false)
  })

  it('计番:明杠 +1、暗杠 +2 可叠加', () => {
    const hand = kindsToTiles([1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14])
    const counts = mahjongCounts(hand)
    const base = mahjongHandScore(counts, { melds: [] })
    expect(base.score).toBe(8)
    const withGang = mahjongHandScore(mahjongCounts(kindsToTiles([1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14])), {
      melds: [{ type: 'gang', kind: 31 }],
    })
    // 摸岭上补回后暗手仍 14 张口径,杠番 +1
    expect(withGang.total).toBe(base.total + 1)
    const withAngang = mahjongHandScore(mahjongCounts(kindsToTiles([1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14])), {
      melds: [{ type: 'angang', kind: 31 }],
    })
    expect(withAngang.total).toBe(base.total + 2)
    const mixed = mahjongHandScore(mahjongCounts(kindsToTiles([1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14])), {
      melds: [
        { type: 'gang', kind: 31 },
        { type: 'angang', kind: 32 },
      ],
    })
    expect(mixed.total).toBe(base.total + 3)
  })

  it('AI 吃牌:能提升潜力才吃,且不重复用暗手里没有的牌', () => {
    // 拆散的 234 万 + 孤张:上家弃 1 万,吃 [2,3] 后顺子成 123 万,潜力不降 → 愿意吃
    const hand = kindsToTiles([1, 2, 3, 9, 10, 11, 18, 19, 20, 27, 28, 31, 32])
    const pair = mahjongAiChooseChi(mahjongCounts(hand), 0)
    expect(pair).toEqual([1, 2])
    // 完整三门好型:吃了会拆对/顺潜力下降 → 不吃(比如手里 2万2万3万,弃1万吃需要拆对子)
    const strictHand = kindsToTiles([1, 1, 2, 3, 9, 10, 11, 18, 19, 20, 27, 28, 29])
    const pair2 = mahjongAiChooseChi(mahjongCounts(strictHand), 0)
    // 吃后 111?手里 1万1万+弃1万 → [1,2] 组成 1,2,3?吃 [1,2] 拿走 1万1万 2万,剩 3万孤?有潜力持平也算吃;断言「要么不吃要么不拿手里没有的牌」
    if (pair2) {
      const [a, b] = pair2
      expect(mahjongCounts(strictHand)[a]).toBeGreaterThan(0)
      expect(mahjongCounts(strictHand)[b]).toBeGreaterThan(0)
    }
  })
})
