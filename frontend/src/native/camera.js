// 相机直传(仅原生壳):调系统相机拍一张,拿回 File 交给现有上传接口。
//
// 为什么不用 <input type="file" capture>:WebView 对 capture 属性的支持各机型不一,
// 有的直接弹文件选择器;系统相机 ACTION_IMAGE_CAPTURE 由原生侧接管才是稳定的「拍完即传」。
import { registerPlugin } from '@capacitor/core'
import { fileFromBase64 } from '@/utils/file'

const IhomyCamera = registerPlugin('IhomyCamera')

/** 是否有可用相机(无相机/被禁用时页面上不显示拍照入口) */
export async function cameraAvailable() {
  try {
    const res = await IhomyCamera.available()
    return !!(res && res.available)
  } catch (e) {
    return false
  }
}

/**
 * 拍一张照片并读成 File。
 * 用户取消时抛 code='cancel' 的错误(调用方静默处理),其它失败抛 code='failed'。
 */
export async function capturePhoto() {
  let shot
  try {
    shot = await IhomyCamera.capture()
  } catch (e) {
    const msg = String((e && e.message) || '')
    const err = new Error(msg || 'failed')
    err.code = msg.includes('cancel') ? 'cancel' : 'failed'
    throw err
  }
  if (!shot || !shot.path) {
    const err = new Error('cancel')
    err.code = 'cancel'
    throw err
  }
  const res = await IhomyCamera.readFile({ path: shot.path })
  return fileFromBase64(res.data, res.name || shot.name || 'photo.jpg', 'image/jpeg')
}
