// 画中画(仅原生壳):Android 把整页缩进小窗(呈现 WebView),iOS 只把播放中的视频画面送进系统小窗。
// Web 端与不支持 PiP 的设备全部 no-op。
import { registerPlugin } from '@capacitor/core'
import { isNativeApp } from '@/utils/platform'

const IhomyPip = registerPlugin('IhomyPip')

/** 当前设备/系统是否支持画中画 */
export async function pipSupported() {
  if (!isNativeApp()) return false
  try {
    const r = await IhomyPip.isSupported()
    return !!r?.supported
  } catch (e) {
    return false
  }
}

/** 播放中置 true,暂停/关闭置 false:决定离开 App 时是否进小窗 */
export async function setPipActive(active) {
  if (!isNativeApp()) return
  try {
    await IhomyPip.setActive({ active: !!active })
  } catch (e) {
    console.warn('[pip] 设置画中画状态失败', e)
  }
}

/** 显式入口:立即进入小窗(失败静默,由播放页提示) */
export async function enterPip() {
  if (!isNativeApp()) return false
  try {
    await IhomyPip.enter()
    return true
  } catch (e) {
    return false
  }
}
