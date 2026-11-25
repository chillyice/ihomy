import { onMounted, onBeforeUnmount, shallowRef, toValue, watch } from 'vue'
import { useDevice } from './useDevice'
import { DESK_FLOATS, deskLanes, placeDeskItems } from '@/utils/deskSpace'

const CENTER =
  '.gc-studio,.app-main > .page,.window-content :is(.mobile-tab-content,.mobile-sub-content) > .page,.elder-home,.login-card,.kada > .kwrap,.bp > .bpwrap'
const NAV = '.app-sidebar,.window-rail'
const BOARD = '.app-main > .home-page'
const CARD = '.home-page .dash-card'
const TABLET_PAGE = '.mobile-layout,.app-main'
const MODAL = '[aria-modal="true"],dialog[open],.el-overlay'
const CONTROL =
  'button,a[href],label,input,select,textarea,[role="button"],[role="tab"],[role="link"],[tabindex="0"],[contenteditable="true"]'
const BUSY = [
  '.photo-viewer',
  '.el-image-viewer__wrapper',
  '.morning-overlay',
  '.call-overlay',
  '.reader-overlay',
  '.ss',
  '.game-shell',
  '.is-game-stage',
  '.gc-drag-ghost',
].join(',')
const EDITHOST = '.gc-home,.home-page,.gc-weatherbg'
const LANDMARKS = [CENTER, NAV, BOARD, CARD, TABLET_PAGE, MODAL, BUSY, EDITHOST, DESK_FLOATS, '.gc-side'].join(',')

function visibleRect(node) {
  const rect = node.getBoundingClientRect()
  return rect.width && rect.height && getComputedStyle(node).visibility !== 'hidden' ? rect : null
}

function samePlacements(left, right) {
  return (
    left.length === right.length &&
    left.every((p, i) => {
      const q = right[i]
      return (
        p.item === q.item &&
        p.x === q.x &&
        p.y === q.y &&
        p.size === q.size &&
        p.cropWidth === q.cropWidth &&
        p.angle === q.angle &&
        p.edge === q.edge
      )
    })
  )
}

// 只在整页结构发生变化时收集边界元素。滚动、尺寸和浮窗的拖动复用已收集的元素。
export function useDeskSpace(items, plan) {
  const placements = shallowRef([])
  const { isTablet } = useDevice()
  const modalOpen = shallowRef(false)
  let running = false,
    refresh = true,
    frame = 0
  let nodes, resize, attributes, tree
  let controlObserver
  let controls = new Set()
  const visibleControls = new Set()

  function clearScene() {
    if (placements.value.length) placements.value = []
  }
  function collect() {
    const all = [...document.querySelectorAll(LANDMARKS)]
    const matching = (selector) => all.filter((node) => node.matches(selector))
    nodes = {
      tracked: new Set(all),
      centers: matching(CENTER),
      nav: matching(NAV),
      board: matching(BOARD),
      cards: matching(CARD),
      tabletPages: matching(TABLET_PAGE),
      rails: matching(NAV + ',.gc-side'),
      modals: matching(MODAL),
      busy: matching(BUSY),
      editHosts: matching(EDITHOST),
      floats: matching(DESK_FLOATS),
    }
    if (controlObserver) {
      // The lighting controls float over the scene under inspection, including its desk shadows.
      const current = new Set(
        [...document.querySelectorAll(CONTROL)].filter((node) => !node.closest('[data-desk-layer],.light-test-console,' + MODAL)),
      )
      for (const node of controls) {
        if (!current.has(node)) {
          controlObserver.unobserve(node)
          visibleControls.delete(node)
        }
      }
      for (const node of current) if (!controls.has(node)) controlObserver.observe(node)
      controls = current
    }
    resize.disconnect()
    attributes.disconnect()
    // 日照系统也会改 html 的内联变量，只有类名切换才可能改变整个页面的布局。
    attributes.observe(document.documentElement, { attributes: true, attributeFilter: ['class'] })
    for (const node of all) {
      resize.observe(node)
      attributes.observe(node, { attributes: true, attributeFilter: ['class', 'style', 'hidden', 'open'] })
    }
    refresh = false
  }
  function measure() {
    frame = 0
    if (refresh) collect()
    modalOpen.value = nodes.modals.some(visibleRect)
    if (
      document.fullscreenElement ||
      document.documentElement.matches('.game-wide,.fp-edit-focus') ||
      nodes.editHosts.some((node) => node.matches('.gc-editing,.edit-mode,.revealed')) ||
      nodes.busy.some(visibleRect)
    ) {
      clearScene()
      return
    }
    const board = nodes.board.some(visibleRect)
    const center =
      nodes.centers.map(visibleRect).find(Boolean) || (!board && isTablet.value && nodes.tabletPages.map(visibleRect).find(Boolean))
    if (!center && !board) {
      clearScene()
      return
    }
    const navRight = Math.max(0, ...nodes.nav.map((node) => visibleRect(node)?.right || 0))
    const width = document.documentElement.clientWidth
    const height = window.innerHeight
    const lanes = deskLanes(width, height, navRight, center)
    const cards = nodes.cards.map(visibleRect).filter(Boolean)
    const objectObstacles = nodes.floats.concat(nodes.rails).map(visibleRect).filter(Boolean)
    for (const node of visibleControls) {
      const rect = node.getBoundingClientRect()
      // 大卡片由正文边界限制露出量，独立按钮和输入区保留完整点击面。
      const largeCard = rect.width > width / 2 || (rect.width >= 160 && rect.height >= 96)
      if (largeCard && !node.matches('input,textarea,select,[contenteditable="true"]')) continue
      if (rect.width && rect.height) objectObstacles.push(rect)
    }
    // 自由画板没有固定正文列，按候选高度附近的卡片实测剩余空间。
    const objectLanes = board && !center ? deskLanes(width, height, navRight, null, width) : lanes
    const next = placeDeskItems(toValue(items), toValue(plan), objectLanes, objectObstacles, cards)
    if (!samePlacements(placements.value, next)) placements.value = next
  }
  function schedule() {
    if (running && !document.hidden && !frame) frame = requestAnimationFrame(measure)
  }
  function onTreeChange(records) {
    if (refresh) return schedule()
    const selectors = LANDMARKS + ',' + CONTROL
    const relevant = (node) =>
      node.nodeType === 1 &&
      !node.matches('[data-desk-layer]') &&
      (nodes.tracked.has(node) || node.matches(selectors) || node.querySelector(selectors))
    for (const mutation of records) {
      if (mutation.target.closest?.('[data-desk-layer],.sun-light-layer')) continue
      if ([...mutation.addedNodes, ...mutation.removedNodes].some(relevant)) {
        refresh = true
        schedule()
        return
      }
    }
  }
  function onVisibilityChange() {
    if (document.hidden) clearScene()
    else schedule()
  }
  function start() {
    running = true
    refresh = true
    resize = new ResizeObserver(schedule)
    controlObserver = new IntersectionObserver((entries) => {
      for (const entry of entries) {
        if (entry.isIntersecting && controls.has(entry.target)) visibleControls.add(entry.target)
        else visibleControls.delete(entry.target)
      }
      schedule()
    })
    attributes = new MutationObserver(schedule)
    tree = new MutationObserver(onTreeChange)
    tree.observe(document.body, { childList: true, subtree: true })
    window.addEventListener('resize', schedule)
    document.addEventListener('scroll', schedule, { capture: true, passive: true })
    document.addEventListener('transitionend', onTransition)
    document.addEventListener('fullscreenchange', schedule)
    document.addEventListener('visibilitychange', onVisibilityChange)
    schedule()
  }
  function onTransition(event) {
    if (event.target.matches?.(LANDMARKS)) schedule()
  }
  function stop() {
    if (!running) return
    running = false
    resize.disconnect()
    controlObserver?.disconnect()
    controls.clear()
    visibleControls.clear()
    attributes.disconnect()
    tree.disconnect()
    window.removeEventListener('resize', schedule)
    document.removeEventListener('scroll', schedule, true)
    document.removeEventListener('transitionend', onTransition)
    document.removeEventListener('fullscreenchange', schedule)
    document.removeEventListener('visibilitychange', onVisibilityChange)
    cancelAnimationFrame(frame)
    frame = 0
    clearScene()
    nodes = null
  }
  watch([() => toValue(items), () => toValue(plan)], schedule, { flush: 'post' })
  onMounted(start)
  onBeforeUnmount(stop)
  return { placements, modalOpen }
}
