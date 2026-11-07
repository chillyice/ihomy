// 横向滚动容器的鼠标滚轮映射。
// 余额条这类「隐藏了原生滚动条」的横向列表,触屏可以原生横滑,鼠标只能靠滚轮:
// 只在「该方向还能滚」时消费这一次 wheel,到边缘立刻放行给页面,避免把整页纵向滚动劫持掉。

/**
 * 纯函数:算出这次滚轮应该滚到哪里。
 * @param {{scrollLeft:number, clientWidth:number, scrollWidth:number, delta:number}} p
 * @returns {{consume:boolean, left:number}} consume=false 表示已到边缘,交给页面处理
 */
export function wheelScroll({ scrollLeft, clientWidth, scrollWidth, delta }) {
  const max = Math.max(0, scrollWidth - clientWidth)
  if (max <= 0 || !delta) return { consume: false, left: scrollLeft }
  const left = Math.min(max, Math.max(0, scrollLeft + delta))
  return { consume: left !== scrollLeft, left }
}

/**
 * 把纵向滚轮(或触控板横向手势)映射为容器横向滚动。
 * @param {HTMLElement|null} el
 * @returns {() => void} 解绑函数(组件卸载时调用)
 */
export function bindHorizontalWheel(el) {
  if (!el) return () => {}
  const onWheel = (e) => {
    // 触控板会同时给 deltaX/deltaY,取绝对值大的那个当滚动方向
    const delta = Math.abs(e.deltaX) > Math.abs(e.deltaY) ? e.deltaX : e.deltaY
    const { consume, left } = wheelScroll({
      scrollLeft: el.scrollLeft,
      clientWidth: el.clientWidth,
      scrollWidth: el.scrollWidth,
      delta,
    })
    if (!consume) return // 到边缘:不阻止,让页面正常滚动
    e.preventDefault()
    el.scrollLeft = left
  }
  el.addEventListener('wheel', onWheel, { passive: false })
  return () => el.removeEventListener('wheel', onWheel)
}
