// 懒加载 chunk 失败自愈 + 常开页面主动查新。
// 背景:每次构建的哈希文件名都会变、旧文件被删;旧标签页或 PWA 预缓存仍引用它们时,
// 动态 import 404 → router-view 什么都不渲染(2026-11-27 壁纸页整页空白、只剩聊天球)。
// 判定与顺序收在纯函数里,真正的刷新/注销动作留在 router.onError 与 main.js。

/** 同一轮故障的自愈窗口:窗口内最多走到「注销 SW」这一级,过期重新从第一步开始 */
export const HEAL_WINDOW_MS = 60_000

/**
 * 是否为「懒加载 chunk 取不到」类的路由错误(各浏览器/打包器文案不同)。
 * Chrome/Edge: Failed to fetch dynamically imported module
 * Safari/Firefox: error loading dynamically imported module / Importing a module script failed
 * 老 webpack 产物: Loading chunk 12 failed
 */
export function isChunkLoadError(error) {
  const msg = String(error?.message ?? error ?? '').toLowerCase()
  return msg.includes('dynamically imported module') || msg.includes('importing a module script') || msg.includes('loading chunk')
}

/**
 * 这一轮故障该走第几步自愈:
 * - 1 = 整页刷新(刷新前让 SW 去要一次新清单,新的预缓存 index.html 才会跟上)
 * - 2 = 注销 Service Worker + 清 Cache Storage 再刷新(预缓存把旧 index.html 顶回来时)
 * - 0 = 放弃(这一轮已经走到 2;服务端真的缺文件/断网时不要无限刷新)
 * @param {{at?:number, stage?:number}|null} saved 上一轮记在 sessionStorage 里的状态
 */
export function nextHealStep(saved, now = Date.now()) {
  const at = Number(saved?.at)
  const stage = Number(saved?.stage)
  const fresh = Number.isFinite(at) && Number.isFinite(stage) && now - at < HEAL_WINDOW_MS ? stage : 0
  return fresh >= 2 ? 0 : Math.max(0, fresh) + 1
}

/** 注销所有 Service Worker 并清空 Cache Storage:旧预缓存的 index.html 会把刷新顶回旧构建 */
export async function purgeOfflineCaches({ sw, cacheStorage } = {}) {
  const jobs = []
  if (typeof sw?.getRegistrations === 'function') {
    jobs.push(
      Promise.resolve(sw.getRegistrations())
        .then((regs) => Promise.all((regs || []).map((reg) => reg?.unregister?.())))
        .catch(() => {}),
    )
  }
  if (typeof cacheStorage?.keys === 'function') {
    jobs.push(
      Promise.resolve(cacheStorage.keys())
        .then((keys) => Promise.all((keys || []).map((key) => cacheStorage.delete(key))))
        .catch(() => {}),
    )
  }
  await Promise.all(jobs)
}

/**
 * 常开页面(壁纸大屏、桌面壁纸)不会自己发现部署:加载后、回到前台时、每半小时各要一次新清单,
 * 让下一次刷新/自愈直接拿到新构建。返回停止函数(正常情况下页面不卸载,仅测试用)。
 */
export function watchServiceWorkerUpdates({ sw, doc, intervalMs = 30 * 60 * 1000 } = {}) {
  if (!sw || typeof sw.getRegistration !== 'function') return () => {}
  let stopped = false
  const check = () => {
    if (stopped) return
    Promise.resolve(sw.getRegistration())
      .then((reg) => reg?.update?.())
      .catch(() => {})
  }
  Promise.resolve(sw.ready)
    .then(() => check())
    .catch(() => {})
  const onVisible = () => {
    if (!doc?.hidden) check()
  }
  doc?.addEventListener?.('visibilitychange', onVisible)
  const timer = setInterval(check, intervalMs)
  return () => {
    stopped = true
    clearInterval(timer)
    doc?.removeEventListener?.('visibilitychange', onVisible)
  }
}
