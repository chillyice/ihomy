// 原生能力总入口:只在壳内由 App.vue 懒加载调用;所有子模块再各自按需 import,
// 保证 Capacitor 运行时代码不进入 Web 首屏包。
import { isNativeApp } from '@/utils/platform'

let booted = null

export function bootNative() {
  if (!isNativeApp()) return Promise.resolve()
  if (!booted) {
    booted = Promise.allSettled([
      import('./backButton').then((m) => m.initBackButton()),
      import('./statusBar').then((m) => m.initStatusBar()),
      import('./links').then((m) => m.installLinkHandler()),
      import('./network').then((m) => m.initNetworkStatus()),
      import('./credentials').then((m) => m.installCredentialShim()),
      import('./deeplink').then((m) => m.initDeeplink()),
      import('./reminders').then((m) => m.initReminders()),
      // 更新检查放最后:等前面的能力就绪,且不阻塞其它初始化
      import('./appUpdate').then((m) => m.checkAppUpdate()),
    ]).then((results) => {
      for (const r of results) {
        if (r.status === 'rejected') console.warn('[native] 初始化项失败', r.reason)
      }
    })
  }
  return booted
}

export { getAppVersion } from './version'
