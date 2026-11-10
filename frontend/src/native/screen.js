// 屏幕保护(仅安卓壳):保险箱防截屏、看板常亮。只做窗口 Flag,不改系统设置。
import { registerPlugin } from '@capacitor/core'
import { isAndroidApp } from '@/utils/platform'

const IhomyScreen = registerPlugin('IhomyScreen')

/** 保险箱页进出时开关 FLAG_SECURE;离开必须传 false,否则全站截图都会变黑 */
export async function setSecure(secure) {
  if (!isAndroidApp()) return
  try {
    await IhomyScreen.setSecure({ secure: !!secure })
  } catch (e) {
    console.warn('[screen] 设置防截屏失败', e)
  }
}

/** 看板/相框页常亮(本批未用,留给 B3) */
export async function setKeepOn(keepOn) {
  if (!isAndroidApp()) return
  try {
    await IhomyScreen.setKeepOn({ keepOn: !!keepOn })
  } catch (e) {
    console.warn('[screen] 设置常亮失败', e)
  }
}
