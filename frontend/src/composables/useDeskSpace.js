import { onMounted, onBeforeUnmount, shallowRef, toValue, watch } from 'vue'
import { useDevice } from './useDevice'
import { deskLanes, pawTrailFits, placeDeskItems, placePawTrail, sampleDeskPlan } from '@/utils/deskSpace'

const CENTER =
  '.gc-studio,.app-main > .page,.window-content :is(.mobile-tab-content,.mobile-sub-content) > .page,.elder-home,.login-card,.kada > .kwrap,.bp > .bpwrap'
const NAV = '.app-sidebar,.window-rail'
const BOARD = '.app-main > .home-page'
const BUSY = [
  '[aria-modal="true"]',
  'dialog[open]',
  '.el-overlay',
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
const FLOATS = [
  '.gc-topbar',
  '.window-top',
  '.site-footer',
  '.back-to-top',
  '.install-prompt',
  '.web-update',
  '.child-lock',
  '.music-player',
  '.music-player .playlist',
  '.window-player',
  '.window-queue',
  '.chat-float-ball',
  '.chat-float-panel',
  '.chat-float-bubble',
  '.call-mini',
  '.light-test-console',
  '.daily-add-fab',
  '.home-page .dash-card',
  '.home-page .door-sign',
  '.el-popper',
].join(',')
const LANDMARKS = [CENTER, NAV, BOARD, BUSY, EDITHOST, FLOATS].join(',')
const cooldown = 10 * 60_000
let visitPlan,
  lastPawAt = 0

function visibleRect(node) {
  const rect = node.getBoundingClientRect()
  return rect.width && rect.height && getComputedStyle(node).visibility !== 'hidden' ? rect : null
}

function samePlacements(left, right) {
  return (
    left.length === right.length &&
    left.every((p, i) => {
      const q = right[i]
      return p.item === q.item && p.x === q.x && p.y === q.y && p.size === q.size && p.angle === q.angle
    })
  )
}

// 只在整页结构发生变化时收集边界元素。滚动、尺寸和浮窗的拖动复用已收集的元素。
export function useDeskSpace(items, enabled = true, arrangement) {
  const placements = shallowRef([])
  const pawTrail = shallowRef([])
  const { isMobile } = useDevice()
  let running = false,
    refresh = true,
    frame = 0
  let nodes, scene, resize, attributes, tree, reduced
  let catTimer, fadeTimer, stopActive
  visitPlan ??= sampleDeskPlan()

  function clearTrail() {
    clearTimeout(fadeTimer)
    if (pawTrail.value.length) pawTrail.value = []
  }
  function clearScene() {
    scene = null
    clearTrail()
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
      busy: matching(BUSY),
      editHosts: matching(EDITHOST),
      floats: matching(FLOATS),
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
    if (
      document.fullscreenElement ||
      document.documentElement.matches('.game-wide,.fp-edit-focus') ||
      nodes.editHosts.some((node) => node.matches('.gc-editing,.edit-mode,.revealed')) ||
      nodes.busy.some(visibleRect)
    ) {
      clearScene()
      return
    }
    const center = nodes.centers.map(visibleRect).find(Boolean)
    if (!center && !nodes.board.some(visibleRect)) {
      clearScene()
      return
    }
    const navRight = Math.max(0, ...nodes.nav.map((node) => visibleRect(node)?.right || 0))
    const lanes = deskLanes(document.documentElement.clientWidth, window.innerHeight, navRight, center)
    const obstacles = nodes.floats.map(visibleRect).filter(Boolean)
    scene = { lanes, obstacles }
    const next = placeDeskItems(toValue(items), visitPlan, lanes, obstacles)
    if (!samePlacements(placements.value, next)) placements.value = next
    if (pawTrail.value.length && !pawTrailFits(pawTrail.value, next, lanes, obstacles)) clearTrail()
  }
  function schedule() {
    if (running && !document.hidden && !frame) frame = requestAnimationFrame(measure)
  }
  function onTreeChange(records) {
    if (refresh) return schedule()
    const relevant = (node) => node.nodeType === 1 && (nodes.tracked.has(node) || node.matches(LANDMARKS) || node.querySelector(LANDMARKS))
    for (const mutation of records) {
      if (mutation.target.closest?.('[data-desk-layer],.sun-light-layer')) continue
      if ([...mutation.addedNodes, ...mutation.removedNodes].some(relevant)) {
        refresh = true
        schedule()
        return
      }
    }
  }
  function armCats() {
    clearTimeout(catTimer)
    if (!running || document.hidden || reduced.matches) return
    // 首轮也先留出安静时间。后台回到前台从新的一轮等待开始。
    const wait = Math.max(0, lastPawAt + cooldown - Date.now()) + (2 + 3 * Math.random()) * 60_000
    catTimer = setTimeout(() => {
      if (!document.hidden && !reduced.matches && scene && Math.random() < 0.6) {
        const trail = placePawTrail(placements.value, scene.lanes, scene.obstacles)
        if (trail.length) {
          pawTrail.value = trail
          lastPawAt = Date.now()
          try {
            sessionStorage.setItem('ihomy:desk:last-paw', String(lastPawAt))
          } catch {
            /* 无存储时仍有内存冷却 */
          }
          fadeTimer = setTimeout(clearTrail, 4800)
        }
      }
      armCats()
    }, wait)
  }
  function onConditions() {
    clearTrail()
    if (document.hidden) clearScene()
    else schedule()
    armCats()
  }
  function start() {
    running = true
    refresh = true
    resize = new ResizeObserver(schedule)
    attributes = new MutationObserver(schedule)
    tree = new MutationObserver(onTreeChange)
    tree.observe(document.body, { childList: true, subtree: true })
    reduced.addEventListener('change', onConditions)
    window.addEventListener('resize', schedule)
    document.addEventListener('scroll', schedule, { capture: true, passive: true })
    document.addEventListener('transitionend', onTransition)
    document.addEventListener('fullscreenchange', schedule)
    document.addEventListener('visibilitychange', onConditions)
    schedule()
    armCats()
  }
  function onTransition(event) {
    if (event.target.matches?.(LANDMARKS)) schedule()
  }
  function stop() {
    if (!running) return
    running = false
    resize.disconnect()
    attributes.disconnect()
    tree.disconnect()
    reduced.removeEventListener('change', onConditions)
    window.removeEventListener('resize', schedule)
    document.removeEventListener('scroll', schedule, true)
    document.removeEventListener('transitionend', onTransition)
    document.removeEventListener('fullscreenchange', schedule)
    document.removeEventListener('visibilitychange', onConditions)
    cancelAnimationFrame(frame)
    frame = 0
    clearTimeout(catTimer)
    clearScene()
    nodes = null
  }
  watch(() => toValue(items), schedule, { flush: 'post' })
  watch(
    () => toValue(arrangement),
    () => {
      visitPlan = sampleDeskPlan(Math.random, 1)
      clearTrail()
      schedule()
    },
  )
  onMounted(() => {
    reduced = matchMedia('(prefers-reduced-motion: reduce)')
    try {
      lastPawAt = Math.max(lastPawAt, Math.min(Number(sessionStorage.getItem('ihomy:desk:last-paw')) || 0, Date.now()))
    } catch {
      /* 会话内仍有冷却 */
    }
    stopActive = watch(
      () => !isMobile.value && toValue(enabled),
      (active) => (active ? start() : stop()),
      { immediate: true },
    )
  })
  onBeforeUnmount(() => {
    stopActive?.()
    stop()
  })
  return { placements, pawTrail }
}
