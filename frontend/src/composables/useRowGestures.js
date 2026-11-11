// 列表行手势内核:长按菜单 + 左右滑动操作,只做意图识别,不 preventDefault。
// 起手在输入框/弹窗/横滚容器/拖拽手柄内一律不接管(AGENTS.md 前端规范 24)。
// 拖动位移直接写 DOM style,手势结束才提交 open 状态(规范 15:禁止 rAF 每帧写 Vue ref)。
import { ref, onBeforeUnmount } from 'vue'
import { haptic } from '@/utils/haptics'

export const LONG_PRESS_MS = 480
export const MOVE_CANCEL_PX = 10
export const SWIPE_START_PX = 8
export const SWIPE_MIN_PX = 36
export const SWIPE_OPEN_RATIO = 0.6
export const SWIPE_ACTION_WIDTH = 88

const IGNORE_SELECTOR = [
  'input',
  'textarea',
  'select',
  'button',
  'a',
  '[contenteditable="true"]',
  '.el-button',
  '.el-checkbox',
  '.el-switch',
  '.el-dialog',
  '.el-drawer',
  '.el-message-box',
  '.el-overlay',
  '[data-no-gesture]',
  '[data-drag-handle]',
].join(', ')

/** 从目标往上找横向可滚动容器:这类区域优先响应自身横滑,不抢行手势 */
export function isHorizontalScroller(target) {
  let el = target instanceof Element ? target : null
  while (el && el !== document.body) {
    if (el.scrollWidth > el.clientWidth + 4) {
      const overflowX = getComputedStyle(el).overflowX
      if (overflowX === 'auto' || overflowX === 'scroll') return true
    }
    el = el.parentElement
  }
  return false
}

/** 起手是否应被排除(纯函数,方便单测) */
export function isGestureIgnored(target) {
  if (!(target instanceof Element)) return true
  if (target.closest(IGNORE_SELECTOR)) return true
  return isHorizontalScroller(target)
}

// 同一时刻只允许一行处于展开/菜单状态:模块级单例记录当前关闭函数
let closeActive = null

export function useRowGestures(options = {}) {
  const contentEl = ref(null)
  const open = ref('') // '' | 'left' | 'right'
  const menu = ref({ visible: false, x: 0, y: 0 })
  const actionWidth = options.actionWidth || SWIPE_ACTION_WIDTH
  const hasLeft = options.hasLeft || (() => (options.leftActions || []).length > 0)
  const hasRight = options.hasRight || (() => (options.rightActions || []).length > 0)
  const hasMenu = options.hasMenu || (() => true)

  let press = null
  let longTimer = null
  let swallowClickUntil = 0

  const applyTransform = (x, animate) => {
    const el = contentEl.value
    if (!el) return
    el.style.transition = animate ? 'transform 0.22s cubic-bezier(0.22, 0.61, 0.36, 1)' : 'none'
    el.style.transform = `translateX(${x}px)`
  }

  const onDocPointerDown = (e) => {
    if (!open.value) return
    // 整行(含露出的操作按钮)都算「自己」:否则点操作按钮时 capture 阶段先把行收起,
    // 按钮随之失去 pointer-events,click 永远到不了业务。
    if (contentEl.value?.parentElement?.contains(e.target)) return
    close()
  }

  const clearLongTimer = () => {
    if (longTimer) {
      clearTimeout(longTimer)
      longTimer = null
    }
  }

  function close() {
    open.value = ''
    applyTransform(0, true)
    document.removeEventListener('pointerdown', onDocPointerDown, true)
    if (closeActive === close) closeActive = null
  }

  const setOpen = (side) => {
    if (side && closeActive && closeActive !== close) closeActive()
    open.value = side
    applyTransform(side === 'right' ? -actionWidth : side === 'left' ? actionWidth : 0, true)
    if (side) {
      closeActive = close
      document.addEventListener('pointerdown', onDocPointerDown, true)
    } else {
      document.removeEventListener('pointerdown', onDocPointerDown, true)
      if (closeActive === close) closeActive = null
    }
  }

  const openMenuAt = (x = 0, y = 0) => {
    close()
    menu.value = { visible: true, x, y }
  }

  const closeMenu = () => {
    menu.value.visible = false
  }

  const fireLongPress = () => {
    if (!press) return
    if (!hasMenu()) {
      clearLongTimer()
      return
    }
    longTimer = null
    press.longFired = true
    // 长按后浏览器还会补发一次 click:记录时间戳,capture 阶段吞掉,避免同时进详情
    swallowClickUntil = Date.now() + 320
    haptic('select')
    openMenuAt(press.x, press.y)
  }

  const cleanupPointer = () => {
    clearLongTimer()
    window.removeEventListener('pointermove', onPointerMove)
    window.removeEventListener('pointerup', onPointerUp)
    window.removeEventListener('pointercancel', onPointerCancel)
  }

  const onPointerDown = (e) => {
    if (options.disabled?.()) return
    if (e.pointerType === 'mouse' && e.button !== 0) return
    if (isGestureIgnored(e.target)) return
    // 已展开时先收起,本次点击不进入业务
    if (open.value) {
      swallowClickUntil = Date.now() + 320
      close()
      return
    }
    press = {
      id: e.pointerId,
      x: e.clientX,
      y: e.clientY,
      lastX: e.clientX,
      horizontal: false,
      longFired: false,
    }
    longTimer = setTimeout(fireLongPress, LONG_PRESS_MS)
    window.addEventListener('pointermove', onPointerMove, { passive: true })
    window.addEventListener('pointerup', onPointerUp)
    window.addEventListener('pointercancel', onPointerCancel)
  }

  const onPointerMove = (e) => {
    if (!press || e.pointerId !== press.id) return
    const dx = e.clientX - press.x
    const dy = e.clientY - press.y
    if (Math.abs(dx) > MOVE_CANCEL_PX || Math.abs(dy) > MOVE_CANCEL_PX) clearLongTimer()
    if (!press.horizontal) {
      // 纵向意图明确:放弃本次手势,交给页面滚动
      if (Math.abs(dy) > MOVE_CANCEL_PX && Math.abs(dy) > Math.abs(dx)) {
        press = null
        cleanupPointer()
        return
      }
      // 横向意图明确才开始接管;|dx| 要明显大于 |dy|
      if (Math.abs(dx) > SWIPE_START_PX && Math.abs(dx) > Math.abs(dy) * 1.5) {
        press.horizontal = true
        const el = contentEl.value
        // 指针捕获只在真正拖动时做(踩坑速查 §3.35)
        if (el?.setPointerCapture) {
          try {
            el.setPointerCapture(e.pointerId)
          } catch (err) {}
        }
      }
    }
    if (!press.horizontal) return
    press.lastX = e.clientX
    let x = dx
    if (dx < 0 && !hasRight()) x = dx * 0.2
    if (dx > 0 && !hasLeft()) x = dx * 0.2
    const limit = actionWidth * 1.15
    x = Math.max(-limit, Math.min(limit, x))
    applyTransform(x, false)
  }

  const onPointerUp = (e) => {
    if (!press || (e.pointerId !== undefined && e.pointerId !== press.id)) return
    const p = press
    press = null
    cleanupPointer()
    const el = contentEl.value
    if (el?.hasPointerCapture?.(p.id)) {
      try {
        el.releasePointerCapture(p.id)
      } catch (err) {}
    }
    if (p.longFired) return
    if (!p.horizontal) return
    const dx = p.lastX - p.x
    const threshold = Math.max(SWIPE_MIN_PX, actionWidth * SWIPE_OPEN_RATIO)
    if (dx <= -threshold && hasRight()) setOpen('right')
    else if (dx >= threshold && hasLeft()) setOpen('left')
    else close()
    // 真实滑动后吞掉补发的 click,避免滑动结束又进详情
    if (Math.abs(dx) > SWIPE_START_PX) swallowClickUntil = Date.now() + 320
  }

  // 系统打断(滚动接管/来电/手势取消):不提交展开状态,直接把行复位
  const onPointerCancel = (e) => {
    if (!press || (e.pointerId !== undefined && e.pointerId !== press.id)) return
    press = null
    cleanupPointer()
    close()
  }

  const onClickCapture = (e) => {
    if (Date.now() < swallowClickUntil) {
      e.stopPropagation()
      e.preventDefault()
      swallowClickUntil = 0
      return
    }
    if (open.value) {
      e.stopPropagation()
      e.preventDefault()
      close()
    }
  }

  onBeforeUnmount(() => {
    cleanupPointer()
    document.removeEventListener('pointerdown', onDocPointerDown, true)
    if (closeActive === close) closeActive = null
  })

  return {
    contentEl,
    open,
    menu,
    actionWidth,
    close,
    closeMenu,
    openMenuAt,
    onPointerDown,
    onClickCapture,
  }
}
