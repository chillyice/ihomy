import { describe, expect, it } from 'vitest'
import { CARD_FACE_IDS, cardFaceUrl } from './cardAssets'

describe('cardAssets', () => {
  it('52 张标准牌都有图,大小王也在素材里', () => {
    expect(CARD_FACE_IDS).toHaveLength(54)
    expect(CARD_FACE_IDS).toContain('S-1')
    expect(CARD_FACE_IDS).toContain('D-13')
    expect(CARD_FACE_IDS).toContain('joker-small')
  })

  it('按 suit + rank 取图', () => {
    expect(cardFaceUrl({ suit: 'H', rank: 12 })).toMatch(/(\.webp$|^data:image\/webp)/)
    expect(cardFaceUrl({ suit: 'C', rank: 7 })).toMatch(/(\.webp$|^data:image\/webp)/)
  })

  it('大小王按 joker-small / joker-big 取图', () => {
    expect(cardFaceUrl({ suit: 'JOKER', rank: 14 })).toContain('joker-small')
    expect(cardFaceUrl({ suit: 'JOKER', rank: 15 })).toContain('joker-big')
  })

  it('非法牌与缺图返回空串', () => {
    expect(cardFaceUrl(null)).toBe('')
    expect(cardFaceUrl({ suit: 'X', rank: 1 })).toBe('')
    expect(cardFaceUrl({ suit: 'S', rank: 99 })).toBe('')
  })
})
