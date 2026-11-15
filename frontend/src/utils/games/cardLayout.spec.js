import { describe, it, expect } from 'vitest'
import { BACK_STEP, CARD_RATIO, FACEUP_STEP, MIN_BACK_STEP, MIN_FACEUP_PIXELS, fitCardWidth, pileSteps } from './cardLayout'

const pile = (n, faceUpFrom = 0) => Array.from({ length: n }, (_, i) => ({ faceUp: i >= faceUpFrom }))

describe('纸牌叠牌步进', () => {
  it('短列用标准步进,背面比正面更紧', () => {
    const steps = pileSteps(pile(3, 1), 5, 80)
    expect(steps[0]).toBeCloseTo(BACK_STEP)
    expect(steps[1]).toBeCloseTo(FACEUP_STEP)
  })

  it('有空间时将长列收进高度预算,同时保留牌标', () => {
    const steps = pileSteps(pile(19, 8), 6.6, 80)
    expect(steps[0]).toBeGreaterThanOrEqual(MIN_BACK_STEP)
    // 素材图左上角的原始花色需要约 36% 牌宽的露出高度。
    expect(steps[8]).toBeGreaterThanOrEqual(0.36)
    expect(CARD_RATIO + steps.reduce((sum, step) => sum + step, 0)).toBeCloseTo(6.6)
  })

  it('空间不足时允许长列溢出,每张正面牌仍露出完整牌标', () => {
    const steps = pileSteps(pile(30), 4, 44)
    expect(steps).toHaveLength(29)
    expect(steps.every((step) => step * 44 >= MIN_FACEUP_PIXELS)).toBe(true)
    expect(CARD_RATIO + steps.reduce((sum, step) => sum + step, 0)).toBeGreaterThan(4)
  })

  it('单张牌与空列没有步进', () => {
    expect(pileSteps(pile(0), 5, 80)).toEqual([])
    expect(pileSteps(pile(1), 5, 80)).toEqual([])
  })
})

describe('纸牌尺寸反推', () => {
  it('宽屏时牌宽由宽度决定,桌面区有剩余高度', () => {
    const { cardW, units } = fitCardWidth({ availW: 876, availH: 560, columns: 7, topUnits: CARD_RATIO })
    expect(cardW).toBeCloseTo(120, 0)
    expect(units).toBeGreaterThan(CARD_RATIO)
  })

  it('矮屏会给叠放区留出空间', () => {
    const { cardW, units } = fitCardWidth({ availW: 876, availH: 280, columns: 7, topUnits: CARD_RATIO })
    expect(cardW).toBeLessThan(120)
    expect(units).toBeCloseTo(2 * CARD_RATIO)
  })

  it('窄屏十列保持可点宽度,由牌桌横向滚动承载', () => {
    const { cardW } = fitCardWidth({ availW: 330, availH: 600, columns: 10, gap: 5, fixedTop: 44, minWidth: 44 })
    expect(cardW).toBe(44)
  })

  it('尺寸未知时回退到宽度上限和标准步进', () => {
    const { cardW, units } = fitCardWidth()
    expect(cardW).toBeGreaterThan(0)
    expect(pileSteps(pile(2), units, cardW)[0]).toBeCloseTo(FACEUP_STEP)
  })
})
