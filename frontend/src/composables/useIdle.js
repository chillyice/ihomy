// 空闲计时(屏保用):活动事件重置计时;页面切后台暂停、回前台重新起算;
// 到点时由调用方判定是否真能触发(有弹窗/通话就让位并重新计时)。
// 计时内核抽成 createIdleController,不依赖组件生命周期,便于单测。
import { onBeforeUnmount, onMounted, ref, unref, watch } from 'vue'

const ACTIVITY_EVENTS = ['pointerdown', 'keydown', 'touchstart', 'scroll', 'wheel']
const MOVE_EVENT = 'pointermove'
const MOVE_THROTTLE_MS = 2000

const readMs = (idleMs) => {
  const raw = typeof idleMs === 'function' ? idleMs() : unref(idleMs)
  const n = Number(raw)
  return Number.isFinite(n) && n > 0 ? n : 0
}

/**
 * 纯计时内核。
 * - poke() 记一次活动并重新计时
 * - suspend() / resume() 后台暂停 / 前台重新起算
 * - stop() 永久停用(卸载)
 */
export function createIdleController({ idleMs, canTrigger = () => true, onIdle } = {}) {
  let timer = null
  let idle = false
  let suspended = false

  const clear = () => {
    if (timer) clearTimeout(timer)
    timer = null
  }

  const arm = () => {
    clear()
    if (suspended || idle) return
    const ms = readMs(idleMs)
    if (!ms) return
    timer = setTimeout(() => {
      timer = null
      if (suspended) return
      if (!canTrigger()) {
        arm()
        return
      }
      idle = true
      if (typeof onIdle === 'function') onIdle()
    }, ms)
  }

  return {
    get idle() {
      return idle
    },
    poke() {
      idle = false
      if (!suspended) arm()
    },
    suspend() {
      suspended = true
      idle = false
      clear()
    },
    resume() {
      suspended = false
      idle = false
      arm()
    },
    stop() {
      suspended = true
      idle = false
      clear()
    },
    rearm() {
      if (!idle) arm()
    },
  }
}

export function useIdle({ idleMs, canTrigger = () => true, onIdle } = {}) {
  const isIdle = ref(false)
  let lastMoveAt = 0

  const controller = createIdleController({
    idleMs,
    canTrigger,
    onIdle: () => {
      isIdle.value = true
      if (typeof onIdle === 'function') onIdle()
    },
  })

  const poke = () => {
    isIdle.value = false
    controller.poke()
  }

  // pointermove 一秒能来几十次:节流,别每次都清定时器重排
  const onMove = () => {
    const now = Date.now()
    if (now - lastMoveAt < MOVE_THROTTLE_MS) return
    lastMoveAt = now
    poke()
  }

  const onVisibility = () => {
    if (document.visibilityState === 'hidden') controller.suspend()
    else poke()
  }

  onMounted(() => {
    if (typeof window === 'undefined') return
    ACTIVITY_EVENTS.forEach((ev) => window.addEventListener(ev, poke, { passive: true }))
    window.addEventListener(MOVE_EVENT, onMove, { passive: true })
    document.addEventListener('visibilitychange', onVisibility)
    if (document.visibilityState === 'hidden') controller.suspend()
    else controller.resume()
  })

  onBeforeUnmount(() => {
    if (typeof window === 'undefined') return
    ACTIVITY_EVENTS.forEach((ev) => window.removeEventListener(ev, poke))
    window.removeEventListener(MOVE_EVENT, onMove)
    document.removeEventListener('visibilitychange', onVisibility)
    controller.stop()
  })

  // 设置里改了空闲时长/开关(0 表示关)时重新武装
  watch(
    () => readMs(idleMs),
    () => controller.rearm(),
  )

  return { isIdle, poke }
}
