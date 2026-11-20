// 全站手写笔点按兜底(iPad + Apple Pencil):在 App.vue 挂一次,所有页面共用。
//
// 为什么需要:iPadOS Safari 对手写笔(pointerType === 'pen')不保证补发 click,而站内交互几乎全靠 click
// (按钮点按、卡片选中、纸牌双击自动移动……)。症状是「手指点得动、手写笔点不动」。
// 做法:把「一次手写笔按下」确定性映射成「一次 click」;浏览器随后若又补发了真 click,在 document 捕获阶段吞掉,
// 保证一次按下 = 一次点击(双击计时、点选/取消选中才不会被一次按下算成两次)。
//
// 不接管:手指/鼠标、长按(PEN_LONG_PRESS_MS 以上)、拖动(位移超阈值)、拖牌进行中(body.card-dragging)、
// canvas 手写板与输入框(见 utils/penTap.js PEN_TAP_EXCLUDED_SELECTOR),需要例外的地方自己标 data-pen-tap="off"。
import { onBeforeUnmount, onMounted } from 'vue'
import { isDisabledControl, isPenTapCandidate, isPenTapExcluded, resolvePenTapTarget, shouldSwallowPenClick } from '@/utils/penTap'

export function usePenTap() {
  let down = null
  /** 上次补发点击的落笔点与时间:用来判浏览器补发的 click 要不要吞 */
  let tapPoint = null

  const onPointerDown = (event) => {
    if (event.pointerType !== 'pen' || isPenTapExcluded(event.target)) {
      down = null
      return
    }
    down = { pointerId: event.pointerId, at: Date.now(), x: event.clientX, y: event.clientY, target: event.target }
  }

  const onPointerUp = (event) => {
    const start = down
    if (!start || event.pointerId !== start.pointerId) return
    down = null
    const distance = Math.hypot(event.clientX - start.x, event.clientY - start.y)
    // 拖牌(useCardDrag)进行中由拖拽收尾,不能再补一次点击
    const busy = typeof document !== 'undefined' && document.body.classList.contains('card-dragging')
    if (!isPenTapCandidate({ pointerType: event.pointerType, distance, duration: Date.now() - start.at, busy })) return
    // 落点对齐原生 click:以「抬起时指针下」的命中测试为准;按下到抬起之间 DOM 会重排
    // (摸牌插入手牌、碰吃胡询问条出现/消失),按按下时的旧元素补发会点到已经挪走的那张牌。
    const upTarget =
      Number.isFinite(event.clientX) && Number.isFinite(event.clientY) ? document.elementFromPoint(event.clientX, event.clientY) : null
    const target = resolvePenTapTarget({ upTarget, downTarget: start.target, fallbackTarget: event.target })
    if (!target) return
    // 原生语义:停在 disabled 控件上的点击不发生(如牌局还没轮到你时点手牌)
    if (isDisabledControl(target)) return
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
    const swallow = shouldSwallowPenClick({
      synthetic: !!event.__ihomyPenTap,
      excluded: isPenTapExcluded(event.target),
      sinceTap,
      distanceFromTap,
    })
    if (!swallow) return
    event.stopPropagation()
    event.preventDefault()
  }

  onMounted(() => {
    document.addEventListener('pointerdown', onPointerDown, true)
    document.addEventListener('pointerup', onPointerUp, true)
    document.addEventListener('pointercancel', onPointerCancel, true)
    document.addEventListener('click', onClickCapture, true)
  })

  onBeforeUnmount(() => {
    document.removeEventListener('pointerdown', onPointerDown, true)
    document.removeEventListener('pointerup', onPointerUp, true)
    document.removeEventListener('pointercancel', onPointerCancel, true)
    document.removeEventListener('click', onClickCapture, true)
    down = null
  })
}
