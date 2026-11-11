// 纸牌拖拽内核:Pointer Events + 阈值判定。
// 指针按下后先不进入拖拽态,超过阈值才把牌组提起来;未超过阈值时仍交给 click 完成点选/双击。
// 落点用 elementFromPoint 找最近的 [data-drop] 容器,牌桌只需在列/花色堆/空格上标 data 属性。
import { reactive } from 'vue'

const THRESHOLD = 6
const CLICK_SUPPRESS_MS = 320

const DROP_TYPES = new Set(['tableau', 'foundation', 'free'])

function dropTargetAt(clientX, clientY) {
  if (typeof document === 'undefined') return null
  const hit = document.elementFromPoint(clientX, clientY)
  const el = hit && hit.closest ? hit.closest('[data-drop]') : null
  if (!el) return null
  const type = el.dataset.drop
  if (!DROP_TYPES.has(type)) return null
  if (type === 'tableau') return { type, pile: Number(el.dataset.pile) }
  if (type === 'foundation') return { type, suit: el.dataset.suit }
  return { type, cell: Number(el.dataset.cell) }
}

/**
 * @param {Object} opts
 * @param {(from:Object)=>Array|null} opts.getCards 取源位置牌组;返回 null 表示不可拖
 * @param {(from:Object, to:Object|null)=>void} opts.onDrop 放手时回调
 * @param {number} [opts.threshold]
 */
export function useCardDrag({ getCards, onDrop, threshold = THRESHOLD }) {
  const drag = reactive({ active: false, cards: [], x: 0, y: 0, cardW: 0, step: 0 })
  let session = null
  let suppressUntil = 0

  const cleanup = () => {
    if (typeof window !== 'undefined') {
      window.removeEventListener('pointermove', onMove)
      window.removeEventListener('pointerup', onUp)
      window.removeEventListener('pointercancel', onCancel)
    }
    if (typeof document !== 'undefined') document.body.classList.remove('card-dragging')
    if (session) {
      session.started = false
      session = null
    }
    drag.active = false
    drag.cards = []
  }

  const onMove = (event) => {
    if (!session || event.pointerId !== session.pointerId) return
    const dx = event.clientX - session.startX
    const dy = event.clientY - session.startY
    if (!session.started) {
      if (Math.hypot(dx, dy) < threshold) return
      session.started = true
      drag.active = true
      drag.cards = session.cards
      drag.cardW = session.cardW
      drag.step = session.step
      if (typeof document !== 'undefined') document.body.classList.add('card-dragging')
    }
    drag.x = event.clientX - session.offsetX
    drag.y = event.clientY - session.offsetY
    // 触屏上避免拖动时页面跟着滚动(牌面本身已设 touch-action:none,这里再兜一层)
    if (event.cancelable) event.preventDefault()
  }

  const onUp = (event) => {
    if (!session || event.pointerId !== session.pointerId) return
    const active = session.started
    const from = session.from
    const target = active ? dropTargetAt(event.clientX, event.clientY) : null
    cleanup()
    if (!active) return
    suppressUntil = Date.now() + CLICK_SUPPRESS_MS
    onDrop(from, target)
  }

  const onCancel = (event) => {
    if (!session || event.pointerId !== session.pointerId) return
    cleanup()
  }

  const startDrag = (from, event) => {
    if (!event || (event.button != null && event.button !== 0)) return
    if (session) return
    const cards = getCards(from)
    if (!cards || !cards.length) return
    const el = event.currentTarget
    const rect = el && el.getBoundingClientRect ? el.getBoundingClientRect() : null
    if (!rect || rect.width <= 0) return
    session = {
      from,
      cards,
      pointerId: event.pointerId,
      startX: event.clientX,
      startY: event.clientY,
      cardW: rect.width,
      step: Math.max(8, rect.width * 0.42),
      offsetX: event.clientX - rect.left,
      offsetY: event.clientY - rect.top,
      started: false,
    }
    if (typeof window !== 'undefined') {
      window.addEventListener('pointermove', onMove, { passive: false })
      window.addEventListener('pointerup', onUp)
      window.addEventListener('pointercancel', onCancel)
    }
  }

  /** 拖拽结束后的那次 click 要吞掉,否则会顺手选中/双击自动移动 */
  const shouldIgnoreClick = () => {
    if (Date.now() < suppressUntil) {
      suppressUntil = 0
      return true
    }
    return false
  }

  return { drag, startDrag, shouldIgnoreClick, cancelDrag: cleanup }
}
