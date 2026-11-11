// 深链统一入口(仅原生壳):通知/卡片/快捷方式 → App 内路由,都汇到 navigate()。
// 外部唤起走 @capacitor/app 的 appUrlOpen;壳内程序化跳转(通知动作)走 ihomy-deeplink 事件,
// 两条通道共用同一落点,避免各处自己 router.push 口径不一。
import { App } from '@capacitor/app'
import router from '@/router'
import { openPalette } from '@/composables/useHotkeys'

// 一次性动作参数:消费后页面自己会清;同一 URL 再点一次快捷方式时先退回基础 URL,否则 router.push 是空操作
const ONE_SHOT_KEYS = ['new', 'voice', 'search']

function splitPath(p) {
  const hashAt = p.indexOf('#')
  const hash = hashAt >= 0 ? p.slice(hashAt) : ''
  const noHash = hashAt >= 0 ? p.slice(0, hashAt) : p
  const queryAt = noHash.indexOf('?')
  const pathname = queryAt >= 0 ? noHash.slice(0, queryAt) : noHash
  const query = new URLSearchParams(queryAt >= 0 ? noHash.slice(queryAt + 1) : '')
  return { pathname, query, hash }
}

function joinPath(pathname, query, hash) {
  const rest = query.toString()
  return pathname + (rest ? `?${rest}` : '') + hash
}

async function navigate(raw) {
  if (!raw) return
  try {
    // 卡片/外部唤起是 ihomy://plan?date=... 自定义 scheme——URL 解析器对非标准 scheme 的 host/path
    // 切分不可靠(Chromium 会把 //plan 塞进 pathname),所以手动剥前缀;
    // 通知与程序化跳转是站内路径(如 /plan?date=2026-11-12),直接用。
    let s = String(raw)
    let path
    if (s.startsWith('ihomy://')) {
      path = '/' + s.slice(8).replace(/^\/+/, '')
    } else if (s.startsWith('ihomy:')) {
      path = '/' + s.slice(6).replace(/^\/+/, '')
    } else if (s.startsWith('/')) {
      path = s
    } else {
      const u = new URL(s)
      path = u.pathname + u.search + u.hash
    }

    const { pathname, query, hash } = splitPath(path)

    // 搜索快捷方式:没有独立的 /search 路由,直接开命令面板。同一路由重复导航不会触发 watch,
    // 所以这里不 push,只把地址栏上的 search 参数清干净。
    if (query.get('search') === '1') {
      openPalette()
      query.delete('search')
      const target = joinPath(pathname, query, hash)
      if (router.currentRoute.value.fullPath !== target) router.replace(target).catch(() => {})
      return
    }

    const oneShot = ONE_SHOT_KEYS.some((k) => query.get(k) === '1')
    if (oneShot && router.currentRoute.value.fullPath === path) {
      const base = new URLSearchParams(query)
      ONE_SHOT_KEYS.forEach((k) => base.delete(k))
      await router.replace(joinPath(pathname, base, hash)).catch(() => {})
    }

    if (router.currentRoute.value.fullPath === path) return
    router.push(path).catch(() => {})
  } catch (e) {
    console.warn('[deeplink] 非法深链', raw, e)
  }
}

export async function initDeeplink() {
  App.addListener('appUrlOpen', ({ url }) => navigate(url))
  document.addEventListener('ihomy-deeplink', (e) => navigate(e.detail))
  // 冷启动:通知/卡片/快捷方式把 App 从进程未启动状态拉起时,启动意图不会触发 appUrlOpen
  // (那是运行期事件),必须主动取一次 launchUrl,否则一律停在首页。
  try {
    const launched = await App.getLaunchUrl()
    if (!launched?.url) return
    await router.isReady()
    navigate(launched.url)
  } catch (e) {
    console.warn('[deeplink] 启动链接读取失败', e)
  }
}
