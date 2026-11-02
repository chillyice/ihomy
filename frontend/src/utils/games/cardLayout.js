// 纸牌类游戏(Klondike / 蜘蛛纸牌)的尺寸内核:页面固定一屏时按可用宽高反推牌宽,
// 再按每列当前长度分配叠牌步进。长列自动压紧自己的间距,而不是把页面顶高。
// 所有函数只做纯计算,不读 DOM;组件把 ResizeObserver 量到的内容区宽高传进来。

/** 牌高 / 牌宽(标准扑克 5:7) */
export const CARD_RATIO = 1.4
/** 正面牌的叠牌步进:下一张露出的高度占牌宽的比例(注意牌高是 1.4 倍牌宽) */
export const FACEUP_STEP = 0.42
/** 背面牌只需露出一点背纹 */
export const BACK_STEP = 0.22
/** 再高的列也要留出可点的高度,不能让牌完全叠死 */
export const MIN_STEP = 0.12
export const MAX_CARD_W = 132
export const MIN_CARD_W = 26

/**
 * 单列在给定高度预算下每张牌的步进(可见高度,单位=牌宽)。
 * 渲染时 marginTop = -(CARD_RATIO - step) * 100%,因为百分比负边距按牌宽计算。
 * @param {Array<{faceUp:boolean}>} pile 自下而上的牌
 * @param {number} unitsBudget 桌面区可用高度,单位=牌宽
 * @returns {number[]} 第 i 张(从 1 开始)相对上一张露出的高度
 */
export function pileSteps(pile, unitsBudget) {
  const n = Array.isArray(pile) ? pile.length : 0
  if (n <= 1) return []
  const uniform = (unitsBudget - CARD_RATIO) / (n - 1)
  const steps = []
  for (let i = 1; i < n; i++) {
    const base = pile[i - 1].faceUp ? FACEUP_STEP : BACK_STEP
    steps.push(Math.max(MIN_STEP, Math.min(base, uniform)))
  }
  return steps
}

/**
 * 计算牌宽与桌面区高度预算。
 * @param {Object} opts
 * @param {number} opts.availW 牌桌内容区宽度(px,已扣内边距)
 * @param {number} opts.availH 牌桌内容区高度(px,已扣内边距)
 * @param {number} opts.columns 列数
 * @param {number} [opts.gap] 列间距
 * @param {number} [opts.topUnits] 顶部行占用的牌高倍数(Klondike 顶部一行 = 1.4)
 * @param {number} [opts.fixedTop] 顶部固定块高度(蜘蛛牌堆栏,px)
 * @param {number} [opts.rowGap] 顶部行与桌面列的间距
 * @param {Array<Array>} [opts.piles] 各列当前牌,用于保证最长列在最紧步进下也放得下
 * @param {number} [opts.maxWidth]
 * @param {number} [opts.minWidth]
 * @returns {{ cardW:number, units:number }} units 为桌面区可用高度(单位=牌宽)
 */
export function fitCardWidth({
  availW = 0,
  availH = 0,
  columns = 7,
  gap = 6,
  topUnits = 0,
  fixedTop = 0,
  rowGap = 10,
  piles = [],
  maxWidth = MAX_CARD_W,
  minWidth = MIN_CARD_W,
} = {}) {
  const byW = availW > 0 ? (availW - (columns - 1) * gap) / columns : maxWidth
  let cardW = Math.min(byW, maxWidth)

  // 最长的列按最紧步进需要多少牌宽的高度
  const tightUnits = piles.reduce((max, pile) => Math.max(max, CARD_RATIO + Math.max(0, pile.length - 1) * MIN_STEP), CARD_RATIO)
  const byH = availH > 0 ? (availH - fixedTop - rowGap) / (tightUnits + topUnits) : Infinity
  cardW = Math.min(cardW, byH)

  // 牌宽不低于可读下限;窄屏十列放不下时由牌桌横向滚动承载,不把牌压成一条缝
  cardW = Math.max(minWidth, cardW)

  const units = availH > 0 ? (availH - fixedTop - rowGap - topUnits * cardW) / cardW : tightUnits
  return { cardW, units: Math.max(CARD_RATIO, units) }
}
