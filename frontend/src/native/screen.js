// 屏幕保护(仅原生壳):保险箱防窥、看板常亮。
// Android 用窗口 Flag 真正禁止截屏/录屏;iOS 系统不允许阻止截屏,只能切后台/录屏时盖一层遮罩。
import { registerPlugin } from '@capacitor/core'
import { isNativeApp } from '@/utils/platform'

const IhomyScreen = registerPlugin('IhomyScreen')

/** 保险箱页进出时开关防窥;离开必须传 false */
export async function setSecure(secure) {
  if (!isNativeApp()) return
  try {
    await IhomyScreen.setSecure({ secure: !!secure })
  } catch (e) {
    console.warn('[screen] 设置防截屏失败', e)
  }
}

/** 看板/相框页常亮(本批未用,留给 B3) */
export async function setKeepOn(keepOn) {
  if (!isNativeApp()) return
  try {
    await IhomyScreen.setKeepOn({ keepOn: !!keepOn })
  } catch (e) {
    console.warn('[screen] 设置常亮失败', e)
  }
}
