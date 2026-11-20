// 触控笔点按兜底(iPad + Apple Pencil):Safari 对 pointerType 为 pen 的手势不保证补发 click,
// 纸牌的「点选 / 双击自动移动」全靠 click,于是手写笔双击没反应、手指正常。
// 做法:在 pointerup 上直接把这次按下补发成一次 click(带 __ihomyPenTap 标记),
// 浏览器随后若又补发了真 click,就在捕获阶段吞掉,保证「一次按下 = 一次点击」,双击计时也才准。
// 只管手写笔;手指/鼠标继续走原生 click,拖牌本体仍由 useCardDrag 处理。
import { onBeforeUnmount, onMounted } from 'vue'
import { isPenTapCandidate, shouldSwallowPenClick } from '@/utils/games/penTap'

/**
 * @param {import('vue').Ref<HTMLElement|null>} elRef 牌桌根容器(事件在捕获阶段挂在它上面)
 * @param {{ isBusy?: () => boolean }} [opts] isBusy:拖牌已开始时返回 true,那次按下交给拖拽收尾
 */
export function usePenTap(elRef, { isBusy = () => false } = {}) {
  let down = null
  /** 上次补发点击的落笔点与时间,用于判浏览器补发的 click 要不要吞 */
  let tapPoint = null
  let el = null

  const onPointerDown = (event) => {
    if (event.pointerType !== 'pen') {
      down = null
      return
    }
    down = { pointerId: event.pointerId, x: event.clientX, y: event.clientY, target: event.target }
  }

  const onPointerUp = (event) => {
    const start = down
    if (!start || event.pointerId !== start.pointerId) return
    down = null
    const distance = Math.hypot(event.clientX - start.x, event.clientY - start.y)
    if (!isPenTapCandidate({ pointerType: event.pointerType, distance, busy: isBusy() })) return
    const target = start.target && start.target.isConnected ? start.target : event.target
    if (!target) return
    tapPoint = { at: Date.now(), x: event.clientX, y: event.clientY }
    const click = new MouseEvent('click', {
      bubbles: true,
      cancelable: true,
      composed: true,
      view: window,
      detail: 1,
      clientX: event.clientX,
      clientY: event.clientY,
    })
    click.__ihomyPenTap = true
    target.dispatchEvent(click)
  }

  const onPointerCancel = () => {
    down = null
  }

  const onClickCapture = (event) => {
    const tap = tapPoint
    const sinceTap = tap ? Date.now() - tap.at : -1
    const distanceFromTap = tap ? Math.hypot(event.clientX - tap.x, event.clientY - tap.y) : NaN
    if (!shouldSwallowPenClick({ pointerType: event.pointerType, synthetic: !!event.__ihomyPenTap, sinceTap, distanceFromTap })) return
    event.stopPropagation()
    event.preventDefault()
  }

  onMounted(() => {
    el = elRef.value
    if (!el) return
    el.addEventListener('pointerdown', onPointerDown, true)
    el.addEventListener('pointerup', onPointerUp, true)
    el.addEventListener('pointercancel', onPointerCancel, true)
    el.addEventListener('click', onClickCapture, true)
  })

  onBeforeUnmount(() => {
    if (!el) return
    el.removeEventListener('pointerdown', onPointerDown, true)
    el.removeEventListener('pointerup', onPointerUp, true)
    el.removeEventListener('pointercancel', onPointerCancel, true)
    el.removeEventListener('click', onClickCapture, true)
    el = null
    down = null
  })
}
