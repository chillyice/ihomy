// 牌宽由牌桌可用尺寸确定,移牌不改变整桌比例。
export const CARD_RATIO = 1.4
export const FACEUP_STEP = 0.42
export const BACK_STEP = 0.22
export const MIN_BACK_STEP = 0.12
export const FACEUP_INDEX_HEIGHT = 16
export const MAX_CARD_W = 132
export const MIN_CARD_W = 36

// 返回每张被覆盖的牌露出的高度(以牌宽为单位)。空间不足时保留完整牌标,余下交给牌桌滚动。
export function pileSteps(pile, unitsBudget, cardW) {
  const covered = pile.slice(0, -1)
  const faceMin = FACEUP_INDEX_HEIGHT / cardW
  const facePreferred = Math.max(faceMin, FACEUP_STEP)
  let min = 0
  let extra = 0
  for (const card of covered) {
    min += card.faceUp ? faceMin : MIN_BACK_STEP
    extra += card.faceUp ? facePreferred - faceMin : BACK_STEP - MIN_BACK_STEP
  }
  const fraction = extra > 0 ? Math.min(1, Math.max(0, (unitsBudget - CARD_RATIO - min) / extra)) : 0
  return covered.map((card) =>
    card.faceUp ? faceMin + (facePreferred - faceMin) * fraction : MIN_BACK_STEP + (BACK_STEP - MIN_BACK_STEP) * fraction,
  )
}

export function fitCardWidth({
  availW = 0,
  availH = 0,
  columns = 7,
  gap = 6,
  topUnits = 0,
  fixedTop = 0,
  rowGap = 10,
  maxWidth = MAX_CARD_W,
  minWidth = MIN_CARD_W,
} = {}) {
  const byW = availW > 0 ? (availW - (columns - 1) * gap) / columns : maxWidth
  const boardH = availH - fixedTop - rowGap
  // 桌面至少给一张完整牌与约一张牌高的叠放空间,过矮或过窄时保持可点尺寸。
  const byH = availH > 0 ? boardH / (topUnits + 2 * CARD_RATIO) : Infinity
  const cardW = Math.max(minWidth, Math.min(byW, byH, maxWidth))
  const units = availH > 0 ? Math.max(CARD_RATIO, boardH / cardW - topUnits) : Infinity
  return { cardW, units }
}
