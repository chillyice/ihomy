import { nextTick, onScopeDispose, ref } from 'vue'

// Keep the live card mounted while its footprint participates in CSS grid placement.
// Only a change of slot goes through Vue. Pointer motion writes the card's translation.
export function useWarmCardReorder({ gridEl, indexOf, indexAt, captureRects, flipLayout, commit }) {
  const preview = ref(null)
  let drag = null
  let frame = 0

  function positionCard(state, bounds) {
    state.card.style.translate = `${state.x - state.grabX - bounds.left}px ${state.y - state.grabY - bounds.top}px`
  }

  function scrollAtEdge(state) {
    const scroller = state.scroller
    if (!scroller) return false
    const { top, bottom } = scroller.getBoundingClientRect()
    const edge = Math.min(64, (bottom - top) / 5)
    if (!edge) return false
    const speed = state.y < top + edge ? (state.y - top - edge) / edge : Math.max(0, (state.y - bottom + edge) / edge)
    if (!speed) return false
    const before = scroller.scrollTop
    scroller.scrollTop += Math.sign(speed) * Math.ceil(Math.min(1, Math.abs(speed)) * 14)
    return scroller.scrollTop !== before
  }

  function renderFrame() {
    frame = 0
    const state = drag
    if (!state || !preview.value) return
    const scrolled = scrollAtEdge(state)
    const bounds = gridEl.value.getBoundingClientRect()
    positionCard(state, bounds)
    if (state.renderedX === state.x && state.renderedY === state.y && state.gridTop === bounds.top && state.gridLeft === bounds.left) {
      if (scrolled) scheduleFrame()
      return
    }
    const index = indexAt(state.x, state.y, preview.value.index, state.id)
    state.renderedX = state.x
    state.renderedY = state.y
    state.gridTop = bounds.top
    state.gridLeft = bounds.left
    if (index !== preview.value.index) {
      const prev = captureRects()
      preview.value = { ...preview.value, index }
      nextTick(() => flipLayout(prev))
    }
    if (scrolled) scheduleFrame()
  }

  function scheduleFrame() {
    if (!frame) frame = requestAnimationFrame(renderFrame)
  }

  function move(e) {
    const state = drag
    if (!state || e.pointerId !== state.pointerId) return
    state.x = e.clientX
    state.y = e.clientY
    if (!preview.value) {
      if (Math.hypot(state.x - state.startX, state.y - state.startY) < 5) return
      gridEl.value.setPointerCapture?.(state.pointerId)
      const prev = captureRects()
      preview.value = { id: state.id, index: indexOf(state.id), width: state.width, height: state.height }
      positionCard(state, gridEl.value.getBoundingClientRect())
      nextTick(() => flipLayout(prev))
      state.scroller?.addEventListener('scroll', scheduleFrame, { passive: true })
    }
    scheduleFrame()
  }

  function end(e, save) {
    const state = drag
    if (!state || (e?.pointerId != null && e.pointerId !== state.pointerId)) return
    drag = null
    cancelAnimationFrame(frame)
    frame = 0
    window.removeEventListener('pointermove', move)
    window.removeEventListener('pointerup', release)
    window.removeEventListener('pointercancel', cancel)
    window.removeEventListener('blur', cancel)
    window.removeEventListener('keydown', onKey)
    const grid = gridEl.value
    grid?.removeEventListener('lostpointercapture', onLostCapture)
    state.scroller?.removeEventListener('scroll', scheduleFrame)
    if (grid?.hasPointerCapture?.(state.pointerId)) grid.releasePointerCapture(state.pointerId)
    if (!preview.value) return
    let index = preview.value.index
    if (save && (state.renderedX !== e.clientX || state.renderedY !== e.clientY)) {
      state.x = e.clientX
      state.y = e.clientY
      positionCard(state, grid.getBoundingClientRect())
      index = indexAt(state.x, state.y, index, state.id)
    }
    const prev = captureRects()
    preview.value = null
    state.card.style.removeProperty('translate')
    if (save && index !== indexOf(state.id)) commit(state.id, index)
    nextTick(() => flipLayout(prev))
  }

  function release(e) {
    end(e, true)
  }
  function cancel(e) {
    end(e, false)
  }
  function onLostCapture(e) {
    if (e.target === gridEl.value) cancel(e)
  }
  function onKey(e) {
    if (e.key === 'Escape') cancel()
  }

  function start(e, id) {
    if (drag || e.button !== 0 || e.isPrimary === false) return
    const card = e.currentTarget
    const bounds = card.getBoundingClientRect()
    drag = {
      id,
      card,
      pointerId: e.pointerId,
      startX: e.clientX,
      startY: e.clientY,
      grabX: e.clientX - bounds.left,
      grabY: e.clientY - bounds.top,
      width: bounds.width,
      height: bounds.height,
      scroller: card.closest('.gc-main'),
    }
    window.addEventListener('pointermove', move)
    window.addEventListener('pointerup', release)
    window.addEventListener('pointercancel', cancel)
    window.addEventListener('blur', cancel)
    window.addEventListener('keydown', onKey)
    gridEl.value.addEventListener('lostpointercapture', onLostCapture)
  }

  onScopeDispose(cancel)
  return { preview, start, cancel }
}
