/**
 * 回合引擎的后台节流自愈。
 *
 * 电脑玩家的思考停顿、房间局的回合/应答超时都压在 setTimeout 上:页面隐藏期间
 * (切标签、切 App、锁屏)浏览器把定时器节流(桌面后台约 1 次/分钟,手机端直接挂起),
 * 表现为电脑玩家「思考中」迟迟不落子、超时迟迟不交棒。这里把这类定时器登记进共享表,
 * 页面回到前台(visibilitychange 变可见)时,已到期的立即补触发——错过的等待一次补齐;
 * 未到期的继续按原计时走,前台路径行为与 setTimeout 完全一致。
 *
 * 每个游戏页面(或 useCardRoomPlay 实例)各建一个实例,卸载时 destroy(),
 * 避免监听器与登记表跨页面泄漏。visibility 参数供 node 单测注入替身。
 */

/** 浏览器可见性源:document 不可用时(纯逻辑单测环境)由调用方注入替身 */
const domVisibility =
  typeof document === 'undefined'
    ? null
    : {
        isHidden: () => document.hidden,
        subscribe(onChange) {
          document.addEventListener('visibilitychange', onChange)
          return () => document.removeEventListener('visibilitychange', onChange)
        },
      }

export function createWakeTimers(visibility = domVisibility) {
  /** @type {Set<{deadline: number, fire: Function, timer: any}>} */
  const pending = new Set()
  let unsubscribe = null

  const wakeDue = () => {
    if (!visibility || visibility.isHidden()) return
    for (const handle of [...pending]) {
      if (Date.now() >= handle.deadline) handle.fire()
    }
  }

  if (visibility) unsubscribe = visibility.subscribe(wakeDue)

  /**
   * setTimeout 语义:ms 后触发 fn,返回句柄交给 cancel()。
   * 页面隐藏期间错过的,回前台时若已到期立即补触发;同一句柄只触发一次
   * (补触发后原定时器再响也不会重复执行)。
   */
  function schedule(fn, ms) {
    const delay = Math.max(0, Number(ms) || 0)
    const handle = { deadline: Date.now() + delay, fire: null, timer: null }
    let fired = false
    const fire = () => {
      if (fired) return
      fired = true
      pending.delete(handle)
      if (handle.timer) {
        clearTimeout(handle.timer)
        handle.timer = null
      }
      fn()
    }
    handle.fire = fire
    handle.timer = setTimeout(fire, delay)
    pending.add(handle)
    return handle
  }

  /** clearTimeout 语义:取消句柄,登记一并摘除(回前台不会再唤醒,原定时器也不再触发) */
  function cancel(handle) {
    if (!handle) return
    pending.delete(handle)
    if (handle.fire) handle.fire = null
    if (handle.timer) {
      clearTimeout(handle.timer)
      handle.timer = null
    }
  }

  /** 页面卸载:摘掉监听、清空登记;已排队的 setTimeout 由调用方各自收尾 */
  function destroy() {
    if (unsubscribe) {
      unsubscribe()
      unsubscribe = null
    }
    pending.clear()
  }

  return { schedule, cancel, destroy }
}
