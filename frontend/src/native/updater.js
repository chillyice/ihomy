// 应用内更新的原生出口(仅原生壳):下载走系统 DownloadManager,安装走自写插件 IhomyUpdater。
//
// 分工原因:WebView 里的 JS 没有权限「装应用」——外部私有目录里的 apk 文件也不能直接 file:// 外发,
// 必须由原生侧经 FileProvider 换成 content:// 再交给系统安装器;下载同理,WebView 一锁屏 JS 就停,
// 所以交给系统下载器托管(锁屏/切后台/进程被杀都继续),前端只轮询进度。
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

/** 交给系统下载器后台下载(锁屏/切后台/进程被杀都继续);返回 { id, file } */
export async function startApkDownload({ url, file }) {
  const res = await IhomyUpdater.download({ url, file })
  return res || null
}

/** 查后台下载进度:返回 { status, bytes, total, size, file };查询失败返回 null(调用方按「未知」继续等) */
export async function queryApkDownload() {
  try {
    const res = await IhomyUpdater.downloadStatus()
    return res || null
  } catch (e) {
    return null
  }
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

/**
 * 设置页:闲时自动更新开关 + 本机能不能真静默安装。
 * 开关/排期/去重都在原生(WidgetPrefs + WorkManager),这里只读写。
 */
export async function getAutoUpdate() {
  try {
    const res = await IhomyUpdater.getAutoUpdate()
    return { enabled: !!(res && res.enabled), deviceOwner: !!(res && res.deviceOwner) }
  } catch (e) {
    return { enabled: true, deviceOwner: false }
  }
}

export async function setAutoUpdate(enabled) {
  try {
    await IhomyUpdater.setAutoUpdate({ enabled: !!enabled })
  } catch (e) {
    console.warn('[updater] 保存闲时自动更新开关失败', e)
  }
}
