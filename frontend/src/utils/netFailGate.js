// 「连不上网」的提示闸门:一次网络抖动不打扰用户,连续多次连不上才放行一次提示。
// 纯状态机(时间可注入),提示文案与弹窗留在调用方,便于单测。
export const NET_FAIL_THRESHOLD = 3
// 同一批并发(页面加载时几个请求同时挂掉)只算一次,间隔小于该值的失败并入上一批
export const NET_FAIL_BURST_MS = 800

/**
 * @param {{threshold?: number, burstMs?: number, now?: () => number}} [options]
 * @returns {{fail: () => boolean, ok: () => void, count: () => number}}
 */
export function createNetFailGate({ threshold = NET_FAIL_THRESHOLD, burstMs = NET_FAIL_BURST_MS, now = () => Date.now() } = {}) {
  let count = 0
  let lastAt = 0
  let notified = false

  return {
    /** 记一次「没连上」;返回 true 表示这一轮该提示用户(每轮断网最多提示一次) */
    fail() {
      const t = now()
      if (count > 0 && t - lastAt < burstMs) {
        lastAt = t
        return false
      }
      count += 1
      lastAt = t
      if (notified || count < threshold) return false
      notified = true
      return true
    },
    /** 拿到了任何真实响应(哪怕 4xx/5xx):网络是通的,清零重新计 */
    ok() {
      count = 0
      notified = false
    },
    count: () => count,
  }
}
