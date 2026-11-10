// 应用内更新的原生出口(仅原生壳):下载走 @capacitor/filesystem,安装走自写插件 IhomyUpdater。
//
// 分工原因:WebView 里的 JS 没有权限「装应用」——缓存里的 apk 文件也不能直接 file:// 外发,
// 必须由原生侧经 FileProvider 换成 content:// 再交给系统安装器;这一步只能写插件。
import { registerPlugin } from '@capacitor/core'

const IhomyUpdater = registerPlugin('IhomyUpdater')

/** 本机现在能不能装:系统版本够 + 已获「安装未知应用」授权 */
export async function canInstallApk() {
  try {
    const res = await IhomyUpdater.status()
    return !!(res && res.canInstall)
  } catch (e) {
    return false
  }
}

/** 打开本应用的「安装未知应用」授权页(Android 8 以下无此设置,直接 reject) */
export async function openInstallSettings() {
  await IhomyUpdater.openInstallSettings()
}

/** 调起系统安装器;失败码:file-missing / install-permission / no-installer */
export async function installApk(file) {
  await IhomyUpdater.install({ file })
}

/** 清理下载目录里的旧安装包(默认保留 keep) */
export async function cleanupApks(keep) {
  try {
    const res = await IhomyUpdater.cleanup({ keep: keep || '' })
    return (res && res.removed) || 0
  } catch (e) {
    return 0
  }
}
