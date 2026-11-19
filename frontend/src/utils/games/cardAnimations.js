// 纸牌发牌动画(FLIP):状态提交渲染后,把新出现的牌先平移回牌堆位置,再按次序飞到落点。
// 纯函数部分(flyOffset/dealtIds)可单测;DOM 部分全部走 WAAPI,不碰 Vue 响应式,动画结束自动清理。
export const FLY_DURATION = 300
export const FLY_STAGGER = 26
const REDUCED_MOTION = '(prefers-reduced-motion: reduce)'

/** FLIP 的 invert 量:把落点元素平移回起点(fromRect)的偏移 */
export function flyOffset(fromRect, elRect) {
  return { dx: fromRect.left - elRect.left, dy: fromRect.top - elRect.top }
}

/** 发出的牌 id:之前在牌堆、之后不在牌堆;牌堆数组尾是堆顶,出牌按堆顶先发所以倒序 */
export function dealtIds(prevStockIds, nextStockIds) {
  const rest = new Set(nextStockIds)
  return prevStockIds.filter((id) => !rest.has(id)).reverse()
}

/** 牌堆矩形:量 slot 容器(发牌后 stock↔recycle 元素互换,slot 位置不变) */
export function pileRect(root, selector) {
  const el = root && root.querySelector ? root.querySelector(selector) : null
  return el ? el.getBoundingClientRect() : null
}

/**
 * 把 els 依次从 fromRect 飞到各自当前位置(元素由调用方在渲染完成后按 data-card-id 查找)。
 * fill:'backwards' 让未起飞的牌先停在牌堆上;飞行途中临时抬 z 压过相邻列,落定还原。
 */
export function flyCardsFrom(fromRect, els, { duration = FLY_DURATION, stagger = FLY_STAGGER } = {}) {
  if (!fromRect || !Array.isArray(els) || !els.length) return
  if (typeof window === 'undefined' || !window.matchMedia || window.matchMedia(REDUCED_MOTION).matches) return
  if (typeof Element === 'undefined' || typeof Element.prototype.animate !== 'function') return
  els.forEach((el, i) => {
    if (!el || !el.isConnected) return
    const rect = el.getBoundingClientRect()
    if (!rect.width || !rect.height) return
    const { dx, dy } = flyOffset(fromRect, rect)
    if (!dx && !dy) return
    const prevZ = el.style.zIndex
    el.style.zIndex = '60'
    const anim = el.animate([{ transform: `translate(${dx}px, ${dy}px)` }, { transform: 'translate(0px, 0px)' }], {
      duration,
      delay: i * stagger,
      easing: 'cubic-bezier(0.2, 0.62, 0.28, 1)',
      fill: 'backwards',
    })
    const restore = () => {
      if (el.isConnected) el.style.zIndex = prevZ
    }
    anim.finished.then(restore).catch(restore)
  })
}
