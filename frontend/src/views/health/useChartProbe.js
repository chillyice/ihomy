// 图表指针探测(薄封装):鼠标移动即读数;触摸必须按下拖动才读数,
// 纵向滚动交给浏览器(CSS 侧 touch-action: pan-y),指针捕获只在真拖动时做(§3.35)。
// 坐标换算不在这里:调用方用 healthChart 的 viewBoxX / indexAtX 算好下标传进来。
import { ref } from 'vue'

/**
 * @param {(clientX:number)=>number} indexAt 屏幕 X → 数据下标(算不出返回 -1)
 * @param {{clearOnLeave?:boolean}} [options] 鼠标移出是否清空读数(默认清空)
 */
export function useChartProbe(indexAt, options = {}) {
  const { clearOnLeave = true } = options
  const index = ref(-1)
  const dragging = ref(false)

  const read = (event) => {
    const next = indexAt(event?.clientX)
    if (next != null && next >= 0) index.value = next
    return next
  }

  const onPointerDown = (event) => {
    if (event.pointerType === 'mouse') return
    dragging.value = true
    // 指针捕获只在真拖动时做:鼠标掠过不抢事件
    event.currentTarget?.setPointerCapture?.(event.pointerId)
    read(event)
  }

  const onPointerMove = (event) => {
    if (event.pointerType === 'mouse' || dragging.value) read(event)
  }

  const onPointerEnd = (event) => {
    if (!dragging.value) return
    dragging.value = false
    event?.currentTarget?.releasePointerCapture?.(event.pointerId)
  }

  const onPointerLeave = () => {
    if (clearOnLeave && !dragging.value) index.value = -1
  }

  return {
    index,
    dragging,
    handlers: {
      pointerdown: onPointerDown,
      pointermove: onPointerMove,
      pointerup: onPointerEnd,
      pointercancel: onPointerEnd,
      pointerleave: onPointerLeave,
    },
  }
}
