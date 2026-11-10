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
      // 诊断采集尽早装:越早接管,启动期前端报错越不会漏
      import('./diagnostics').then((m) => m.installDiagnostics()),
      import('./deeplink').then((m) => m.initDeeplink()),
      import('./reminders').then((m) => m.initReminders()),
      import('./appLock').then((m) => m.installAppLock()),
      import('./widgets').then((m) => m.installWidgetBridge()),
      import('./shareInbox').then((m) => m.initShareInbox()),
      // 相册自动备份:回前台/启动时按开关补传手机新照片
      import('./photoBackup').then((m) => m.installPhotoBackup()),
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
