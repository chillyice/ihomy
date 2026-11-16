// 「快速拍摄」:底栏中间加号的动作内核。
// 原生壳走系统相机(camera.js),浏览器/PWA 回落 <input capture>,拿到 File 后上传到
// 「随手拍」相册(不存在就建一本,可见范围跟着家庭走)。
//
// 相册按名字查找并缓存 id:同一家庭只建一本,不占 backup_key(那是手机自动备份的语义)。
// 上传走现有 photoApi.upload,不新增后端接口。
import { albumApi, photoApi } from '@/api'
import { isNativeApp } from '@/utils/platform'

let quickAlbumId = null
let creating = null

/** 拍/选一张照片,返回 File;用户取消返回 null */
export async function capturePhotoFile() {
  if (isNativeApp()) {
    const camera = await import('@/native/camera')
    const ok = await camera.cameraAvailable()
    if (!ok) {
      const err = new Error('camera unavailable')
      err.code = 'unavailable'
      throw err
    }
    return camera.capturePhoto()
  }
  return pickPhoto()
}

// 浏览器回落:input[type=file][capture] 在手机上直接开相机;桌面端退化成选文件
function pickPhoto() {
  return new Promise((resolve) => {
    const input = document.createElement('input')
    input.type = 'file'
    input.accept = 'image/*'
    input.setAttribute('capture', 'environment')
    input.style.display = 'none'
    document.body.appendChild(input)

    let settled = false
    const cleanup = () => {
      window.removeEventListener('focus', onFocus)
      input.remove()
    }
    const done = (file) => {
      if (settled) return
      settled = true
      cleanup()
      resolve(file || null)
    }
    input.addEventListener('change', () => done(input.files && input.files[0]))
    // 取消:新版浏览器触发 cancel;老浏览器没有时靠「回到页面却没选到文件」兜底
    input.addEventListener('cancel', () => done(null))
    const onFocus = () => {
      setTimeout(() => {
        if (!input.files || !input.files.length) done(null)
      }, 1200)
    }
    window.addEventListener('focus', onFocus)
    input.click()
    // 兜底:相机页停留过久也结束等待,不让底栏一直转圈
    setTimeout(() => done(null), 600000)
  })
}

/** 取或建「随手拍」相册(name 由调用方传 i18n 文案) */
export async function ensureQuickAlbum(name) {
  if (quickAlbumId) return quickAlbumId
  if (creating) return creating
  creating = (async () => {
    try {
      const list = await albumApi.list()
      const hit = (list || []).find((a) => a && a.name === name)
      if (hit) {
        quickAlbumId = hit.id
        return quickAlbumId
      }
      const created = await albumApi.create({ name, type: 'private', visibility: 'FAMILY' })
      quickAlbumId = created.id
      return quickAlbumId
    } finally {
      creating = null
    }
  })()
  return creating
}

/** 拍一张并上传,返回 { skipped:true } 表示用户取消 */
export async function quickCapture(albumName) {
  const file = await capturePhotoFile()
  if (!file) return { skipped: true }
  const albumId = await ensureQuickAlbum(albumName)
  await photoApi.upload(albumId, [file])
  return { skipped: false, albumId }
}

/** 退出登录/切家庭后清掉缓存,避免下一家庭往上一家庭的相册传 */
export function resetQuickAlbumCache() {
  quickAlbumId = null
  creating = null
}
