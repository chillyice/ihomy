// App 版本号:壳内取安装包 versionName(gradle 由根 VERSION 注入),Web 回落构建期 __APP_VERSION__
import { App } from '@capacitor/app'
import { getWebVersion, isNativeApp } from '@/utils/platform'

export async function getAppVersion() {
  if (!isNativeApp()) return getWebVersion()
  try {
    const info = await App.getInfo()
    return info.version || getWebVersion()
  } catch {
    return getWebVersion()
  }
}
