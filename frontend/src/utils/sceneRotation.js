import { isNativeApp } from './platform'

const HOUR = 60 * 60_000
const LONG_ABSENCE = 20 * 60_000

// One clock for the app shell, independent of routes and decorative component mounts.
export function startSceneRotation(rotate, onVisibility) {
  let lastRotation = Date.now()
  let hiddenSince = null
  let nativeActive = true
  let timer
  let nativeListener
  let stopped = false

  function sync() {
    if (stopped) return
    clearTimeout(timer)
    const now = new Date()
    const visible = !document.hidden && nativeActive
    onVisibility(visible)
    if (!visible) {
      hiddenSince ??= now.getTime()
      return
    }

    const longAbsence = hiddenSince !== null && now - hiddenSince >= LONG_ABSENCE
    hiddenSince = null
    if (longAbsence || now - lastRotation >= HOUR || now.toDateString() !== new Date(lastRotation).toDateString()) {
      lastRotation = now.getTime()
      rotate()
    }

    const midnight = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1)
    timer = setTimeout(sync, Math.min(lastRotation + HOUR, midnight.getTime()) - now.getTime())
  }

  document.addEventListener('visibilitychange', sync)
  window.addEventListener('pageshow', sync)
  sync()
  if (isNativeApp()) {
    import('@capacitor/app')
      .then(async ({ App }) => {
        if (stopped) return
        nativeListener = await App.addListener('appStateChange', ({ isActive }) => {
          nativeActive = isActive
          sync()
        })
        if (stopped) await nativeListener.remove()
      })
      .catch((error) => console.warn('[sceneRotation] Native visibility listener unavailable', error))
  }
  return () => {
    stopped = true
    clearTimeout(timer)
    document.removeEventListener('visibilitychange', sync)
    window.removeEventListener('pageshow', sync)
    nativeListener?.remove()
  }
}
