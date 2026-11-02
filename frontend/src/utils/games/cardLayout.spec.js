import { describe, it, expect } from 'vitest'
import { BACK_STEP, CARD_RATIO, FACEUP_STEP, MIN_STEP, fitCardWidth, pileSteps } from './cardLayout'

const pile = (n, faceUpFrom = 0) => Array.from({ length: n }, (_, i) => ({ id: i, suit: 'S', rank: (i % 13) + 1, faceUp: i >= faceUpFrom }))

describe('纸牌叠牌步进', () => {
  it('短列用标准步进,背面比正面更紧', () => {
    const steps = pileSteps(pile(3, 1), 5)
    expect(steps).toHaveLength(2)
    expect(steps[0]).toBe(BACK_STEP)
    expect(steps[1]).toBe(FACEUP_STEP)
  })

  it('长列按高度预算自动压紧,且不低于最小步进', () => {
    const steps = pileSteps(pile(19, 0), 5)
    expect(steps.every((s) => s >= MIN_STEP && s <= FACEUP_STEP)).toBe(true)
    const total = CARD_RATIO + steps.reduce((a, b) => a + b, 0)
    expect(total).toBeLessThanOrEqual(5 + 1e-9)
  })

  it('单张牌与空列没有步进', () => {
    expect(pileSteps(pile(0), 5)).toEqual([])
    expect(pileSteps(pile(1), 5)).toEqual([])
  })
})

describe('纸牌尺寸反推', () => {
  it('宽屏时牌宽由宽度决定,桌面区有剩余高度', () => {
    const { cardW, units } = fitCardWidth({
      availW: 876,
      availH: 560,
      columns: 7,
      gap: 6,
      topUnits: CARD_RATIO,
      rowGap: 10,
      piles: [pile(7, 1), pile(6, 1), pile(5, 1)],
    })
    expect(cardW).toBeCloseTo(120, 0)
    expect(units).toBeGreaterThan(CARD_RATIO)
  })

  it('列太长时缩小牌宽,保证整列不超过可用高度', () => {
    const piles = [pile(19, 0), pile(5, 1)]
    const availH = 420
    const { cardW, units } = fitCardWidth({
      availW: 876,
      availH,
      columns: 7,
      topUnits: CARD_RATIO,
      rowGap: 10,
      piles,
    })
    expect(cardW).toBeLessThan(120)
    const total = cardW * (CARD_RATIO + CARD_RATIO) + 10 + (19 - 1) * MIN_STEP * cardW
    expect(total).toBeLessThanOrEqual(availH + 1e-6)
    expect(units).toBeGreaterThanOrEqual(CARD_RATIO)
  })

  it('窄屏十列保持可点宽度,由牌桌横向滚动承载', () => {
    const { cardW } = fitCardWidth({
      availW: 330,
      availH: 600,
      columns: 10,
      gap: 5,
      fixedTop: 44,
      piles: [pile(6, 1)],
      minWidth: 44,
    })
    expect(cardW).toBe(44)
    expect(10 * cardW + 9 * 5).toBeGreaterThan(330)
  })

  it('尺寸未知(首帧)时回退到宽度上限,不出现 0 或负数', () => {
    const { cardW, units } = fitCardWidth({ availW: 0, availH: 0, columns: 7, piles: [pile(7, 1)] })
    expect(cardW).toBeGreaterThan(0)
    expect(units).toBeGreaterThanOrEqual(CARD_RATIO)
  })
})
