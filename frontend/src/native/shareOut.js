// 系统分享出口:文字 / 链接 / 文件。壳内走 Capacitor Filesystem + Share(可带登录令牌),
// Web 端优先 navigator.share,不可用时文字复制到剪贴板、文件退回下载(不复制私有直链)。
import i18n from '@/i18n'
import { ElMessage } from 'element-plus'
import { isNativeApp } from '@/utils/platform'
import { SERVER_ORIGIN } from '@/utils/assetUrl'
import { useUserStore } from '@/stores/user'
import { filenameFrom } from '@/utils/filename'

/** 单文件分享上限:与相册自动备份同口径,避免一次性把大视频读进内存/流量 */
export const SHARE_MAX_BYTES = 30 * 1024 * 1024
const SHARE_DIR = 'ihomy-share'

const t = (key, params) => i18n.global.t(key, params)

/** 业务接口只认 Bearer 头;/files 静态资源另有 HttpOnly cookie,两者都带上才覆盖所有分享来源 */
function authHeaders() {
  const token = useUserStore().token
  return token ? { Authorization: `Bearer ${token}` } : undefined
}

/** 站内相对地址 → 服务端绝对地址(壳内页面 origin 是 https://localhost,不能直接下载) */
export function absoluteUrl(url) {
  if (!url) return ''
  if (/^(https?:|blob:|data:)/i.test(url)) return url
  const base = SERVER_ORIGIN || (typeof location !== 'undefined' ? location.origin : '')
  try {
    return new URL(url, base).href
  } catch (e) {
    return url
  }
}

/** 复制文本:优先 Clipboard API,非安全上下文/旧 WebView 用临时 textarea 兜底 */
export async function copyText(text) {
  if (!text) return false
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text)
      return true
    }
  } catch (e) {
    /* 继续走兜底 */
  }
  try {
    const ta = document.createElement('textarea')
    ta.value = text
    ta.setAttribute('readonly', '')
    ta.style.position = 'fixed'
    ta.style.opacity = '0'
    document.body.appendChild(ta)
    ta.select()
    const ok = document.execCommand('copy')
    ta.remove()
    return ok
  } catch (e) {
    return false
  }
}

async function copyWithToast(text) {
  const ok = await copyText(text)
  if (ok) ElMessage.success(t('common.copied'))
  else ElMessage.error(t('common.copyFailed'))
  return ok
}

async function remoteSize(url, headers) {
  try {
    const res = await fetch(url, { method: 'HEAD', headers })
    if (!res.ok) return null
    const size = Number(res.headers.get('content-length'))
    return Number.isFinite(size) && size > 0 ? size : null
  } catch (e) {
    return null
  }
}

function downloadUrl(url) {
  const a = document.createElement('a')
  a.href = absoluteUrl(url)
  a.download = filenameFrom(url)
  a.rel = 'noopener'
  document.body.appendChild(a)
  a.click()
  a.remove()
}

/** 壳内逐张串行下载到 Cache/ihomy-share,再一次性拉起系统分享面板 */
async function nativeShareFiles(urls, title) {
  const { Filesystem, Directory } = await import('@capacitor/filesystem')
  const { Share } = await import('@capacitor/share')
  const headers = authHeaders()

  await Filesystem.rmdir({ path: SHARE_DIR, directory: Directory.Cache, recursive: true }).catch(() => {})
  await Filesystem.mkdir({ path: SHARE_DIR, directory: Directory.Cache, recursive: true }).catch(() => {})

  const files = []
  for (let i = 0; i < urls.length; i++) {
    const url = absoluteUrl(urls[i])
    const name = filenameFrom(url)
    const size = await remoteSize(url, headers)
    if (size && size > SHARE_MAX_BYTES) {
      ElMessage.warning(t('common.shareTooLarge', { name }))
      return false
    }
    const path = `${SHARE_DIR}/${Date.now()}-${i}-${name}`
    const res = await Filesystem.downloadFile({ url, path, directory: Directory.Cache, headers })
    const stat = await Filesystem.stat({ path, directory: Directory.Cache }).catch(() => null)
    if (stat && stat.size > SHARE_MAX_BYTES) {
      await Filesystem.deleteFile({ path, directory: Directory.Cache }).catch(() => {})
      ElMessage.warning(t('common.shareTooLarge', { name }))
      return false
    }
    files.push(res.path?.startsWith('file://') ? res.path : `file://${res.path}`)
  }
  await Share.share({ title, files, dialogTitle: t('common.share') })
  return true
}

/** Web 文件分享:先取 blob 才能交给 navigator.share;不支持时返回 unsupported 由调用方退回下载 */
async function webShareFiles(urls, title) {
  if (!navigator.share) return 'unsupported'
  const headers = authHeaders()
  try {
    const files = []
    for (const raw of urls) {
      const url = absoluteUrl(raw)
      const size = await remoteSize(url, headers)
      if (size && size > SHARE_MAX_BYTES) {
        ElMessage.warning(t('common.shareTooLarge', { name: filenameFrom(url) }))
        return 'too_large'
      }
      const res = await fetch(url, { credentials: 'include', headers })
      if (!res.ok) return 'failed'
      const blob = await res.blob()
      if (blob.size > SHARE_MAX_BYTES) {
        ElMessage.warning(t('common.shareTooLarge', { name: filenameFrom(url) }))
        return 'too_large'
      }
      files.push(new File([blob], filenameFrom(url), { type: blob.type || 'application/octet-stream' }))
    }
    if (navigator.canShare && !navigator.canShare({ files })) return 'unsupported'
    await navigator.share({ files, title })
    return 'shared'
  } catch (e) {
    return e?.name === 'AbortError' ? 'cancel' : 'unsupported'
  }
}

/** 分享纯文字:壳内系统面板,Web 优先系统分享,否则复制 */
export async function shareText(text, title = '') {
  if (!text) return false
  if (isNativeApp()) {
    try {
      const { Share } = await import('@capacitor/share')
      await Share.share({ title, text, dialogTitle: t('common.share') })
      return true
    } catch (e) {
      return false
    }
  }
  if (navigator.share) {
    try {
      await navigator.share({ title, text })
      return true
    } catch (e) {
      if (e?.name === 'AbortError') return false
    }
  }
  return copyWithToast(text)
}

/** 分享链接:壳内系统面板,Web 优先系统分享,否则复制链接 */
export async function shareUrl(url, title = '') {
  if (!url) return false
  const href = absoluteUrl(url)
  if (isNativeApp()) {
    try {
      const { Share } = await import('@capacitor/share')
      await Share.share({ title, url: href, dialogTitle: t('common.share') })
      return true
    } catch (e) {
      return false
    }
  }
  if (navigator.share) {
    try {
      await navigator.share({ title, url: href })
      return true
    } catch (e) {
      if (e?.name === 'AbortError') return false
    }
  }
  return copyWithToast(href)
}

/** 分享文件:壳内串行下载后拉起系统分享;Web 不支持文件分享时退回下载,绝不复制私有直链 */
export async function shareFiles(urls, title = '') {
  const list = (Array.isArray(urls) ? urls : [urls]).filter(Boolean)
  if (!list.length) return false
  if (isNativeApp()) {
    try {
      ElMessage({ message: t('common.shareDownloading', { n: list.length }), type: 'info', duration: 1400 })
      return await nativeShareFiles(list, title)
    } catch (e) {
      console.warn('[share] 文件分享失败', e)
      ElMessage.error(t('common.shareFailed'))
      return false
    }
  }
  const result = await webShareFiles(list, title)
  if (result === 'shared') return true
  if (result === 'cancel' || result === 'too_large') return false
  if (result === 'failed') {
    ElMessage.error(t('common.shareFailed'))
    return false
  }
  // 当前浏览器不支持文件分享:退回逐个下载(与下载按钮同口径)
  for (const url of list) downloadUrl(url)
  ElMessage.info(t('common.shareUnsupported'))
  return false
}
