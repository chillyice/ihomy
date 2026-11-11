// 下拉刷新:只在页面滚到顶部、向下拖过阈值时触发;只做意图识别,不 preventDefault。
// 指示器位移直接写 DOM style(规范 15:禁止 rAF 每帧写 Vue ref),松手才调用 refresh。
// 白名单页面:首页 / 通知 / 相册(AGENTS.md 前端规范 24;聊天、月历、游戏、电子书不接)。
import { nextTick, onBeforeUnmount, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import i18n from '@/i18n'
import { haptic } from '@/utils/haptics'
import { isGestureIgnored } from '@/composables/useRowGestures'

export const PULL_THRESHOLD = 56
const MIN_SPINNER_MS = 300
const MAX_OFFSET = 72
const EXTRA_IGNORE = '.photo-viewer, .el-image-viewer__wrapper, .chat-float-sheet, [data-no-pull]'

let hostCount = 0

function wait(ms) {
  return new Promise((resolve) => setTimeout(resolve, Math.max(0, ms)))
}

/**
 * @param {object} options
 * @param {{ value: HTMLElement|null }} options.target 页面根元素 ref
 * @param {() => Promise<void>} options.onRefresh 刷新函数(失败请 throw)
 * @param {number} [options.threshold] 触发位移,默认 56px
 * @param {() => boolean} [options.disabled] 临时禁用(如编辑模式)
 */
export function usePullRefresh(options = {}) {
  const threshold = options.threshold || PULL_THRESHOLD
  let rootEl = null
  let indicator = null
  let textEl = null
  let tracking = false
  let pulling = false
  let refreshing = false
  let startX = 0
  let startY = 0
  let lastY = 0
  let alive = true

  const t = (key, params) => i18n.global.t(key, params)

  const ensureIndicator = () => {
    if (indicator) return
    indicator = document.createElement('div')
    indicator.className = 'pull-refresh-indicator'
    indicator.setAttribute('aria-hidden', 'true')
    const spinner = document.createElement('span')
    spinner.className = 'pri-spinner'
    textEl = document.createElement('span')
    textEl.className = 'pri-text'
    indicator.appendChild(spinner)
    indicator.appendChild(textEl)
    document.body.appendChild(indicator)
  }

  const setOffset = (y) => {
    if (indicator) indicator.style.transform = `translate(-50%, ${y}px)`
  }

  const setVisible = (on) => {
    if (!indicator) return
    indicator.classList.toggle('is-visible', !!on)
  }

  const setStatus = (status) => {
    if (!indicator || !textEl) return
    indicator.classList.toggle('is-ready', status === 'ready')
    indicator.classList.toggle('is-refreshing', status === 'refreshing')
    textEl.textContent = t('common.pull.' + status)
  }

  const hide = () => {
    setVisible(false)
    setOffset(-16)
    setStatus('idle')
  }

  const reset = () => {
    tracking = false
    pulling = false
    if (!refreshing) hide()
  }

  const refresh = async () => {
    if (refreshing) return
    refreshing = true
    setStatus('refreshing')
    setOffset(48)
    haptic('tap')
    const started = Date.now()
    try {
      await options.onRefresh?.()
      await wait(MIN_SPINNER_MS - (Date.now() - started))
    } catch (e) {
      await wait(MIN_SPINNER_MS - (Date.now() - started))
      ElMessage.error(t('common.pull.failed'))
    } finally {
      refreshing = false
      hide()
    }
  }

  const ignored = (target) => {
    if (isGestureIgnored(target)) return true
    return target instanceof Element && !!target.closest(EXTRA_IGNORE)
  }

  const onTouchStart = (e) => {
    if (refreshing || options.disabled?.()) return
    if (e.touches.length !== 1) return
    if (!rootEl || !rootEl.contains(e.target)) return
    const top = window.scrollY || document.documentElement.scrollTop || 0
    if (top > 0) return
    if (ignored(e.target)) return
    const touch = e.touches[0]
    startX = touch.clientX
    startY = touch.clientY
    lastY = startY
    tracking = true
    pulling = false
  }

  const onTouchMove = (e) => {
    if (!tracking || refreshing || e.touches.length !== 1) return
    const touch = e.touches[0]
    const dx = touch.clientX - startX
    const dy = touch.clientY - startY
    lastY = touch.clientY
    // 向上滑/横向意图:让页面正常滚动或横滑,不接管
    if (dy < -8 || (Math.abs(dx) > 12 && Math.abs(dx) > Math.abs(dy))) {
      tracking = false
      if (!refreshing) hide()
      return
    }
    if (!pulling) {
      if (dy < 10 || dy < Math.abs(dx) * 2) return
      pulling = true
      ensureIndicator()
      setVisible(true)
    }
    setOffset(Math.min(MAX_OFFSET, dy * 0.6))
    setStatus(dy >= threshold ? 'ready' : 'pulling')
  }

  const onTouchEnd = () => {
    if (!tracking) return
    tracking = false
    if (!pulling) return
    const dy = lastY - startY
    pulling = false
    if (dy >= threshold) refresh()
    else hide()
  }

  const onTouchCancel = () => reset()

  const bind = (el) => {
    rootEl = el
    window.addEventListener('touchstart', onTouchStart, { passive: true })
    window.addEventListener('touchmove', onTouchMove, { passive: true })
    window.addEventListener('touchend', onTouchEnd, { passive: true })
    window.addEventListener('touchcancel', onTouchCancel, { passive: true })
  }

  onMounted(() => {
    nextTick(() => {
      // 页面可能在 nextTick 前就被切走:别把监听器挂到已卸载的组件上
      if (!alive) return
      const el = options.target?.value
      if (!el) return
      bind(el)
      if (hostCount++ === 0) document.documentElement.classList.add('pull-refresh-host')
    })
  })

  onBeforeUnmount(() => {
    alive = false
    window.removeEventListener('touchstart', onTouchStart)
    window.removeEventListener('touchmove', onTouchMove)
    window.removeEventListener('touchend', onTouchEnd)
    window.removeEventListener('touchcancel', onTouchCancel)
    indicator?.remove()
    indicator = null
    textEl = null
    if (rootEl && hostCount > 0) {
      hostCount -= 1
      if (hostCount === 0) document.documentElement.classList.remove('pull-refresh-host')
    }
    rootEl = null
  })

  return { refresh }
}
