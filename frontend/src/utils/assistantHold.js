// Keep listening on the window after the pressed ball is replaced by the expanded panel.
export function trackAssistantHold(event, { hold, release, cancel }, target = window) {
  let triggered = false
  const timer = setTimeout(() => {
    triggered = true
    hold()
  }, 450)
  function detach() {
    clearTimeout(timer)
    target.removeEventListener('pointermove', move)
    target.removeEventListener('pointerup', end)
    target.removeEventListener('pointercancel', end)
    target.removeEventListener('blur', abort)
  }
  function abort() {
    detach()
    if (triggered) cancel()
    triggered = false
  }
  function move(e) {
    if (e.pointerId === event.pointerId && !triggered && Math.hypot(e.clientX - event.clientX, e.clientY - event.clientY) >= 6) abort()
  }
  function end(e) {
    if (e.pointerId !== event.pointerId) return
    detach()
    if (triggered) {
      if (e.type === 'pointercancel') cancel()
      else release()
    }
    triggered = false
  }
  target.addEventListener('pointermove', move)
  target.addEventListener('pointerup', end)
  target.addEventListener('pointercancel', end)
  target.addEventListener('blur', abort)
  return abort
}
