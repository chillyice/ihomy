// 深链统一入口(仅原生壳):通知/卡片/快捷方式 → App 内路由,都汇到 navigate()。
// 外部唤起走 @capacitor/app 的 appUrlOpen;壳内程序化跳转(通知动作)走 ihomy-deeplink 事件,
// 两条通道共用同一落点,避免各处自己 router.push 口径不一。
import { App } from '@capacitor/app'
import router from '@/router'

function navigate(raw) {
  if (!raw) return
  try {
    // 通知/卡片给的是站内路径(如 /plan?date=2026-11-12);外部唤起可能是完整 URL
    const u = new URL(raw, 'https://localhost')
    const path = u.pathname + u.search + u.hash
    if (router.currentRoute.value.fullPath === path) return
    router.push(path).catch(() => {})
  } catch (e) {
    console.warn('[deeplink] 非法深链', raw, e)
  }
}

export function initDeeplink() {
  App.addListener('appUrlOpen', ({ url }) => navigate(url))
  document.addEventListener('ihomy-deeplink', (e) => navigate(e.detail))
}
