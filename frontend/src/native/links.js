// 外链与文件下载:壳内 window.open / target=_blank / <a download> 都不可靠,
// 统一改成「站内路由留在 WebView、文件走原生下载 + 系统分享、其它交给系统浏览器」。
import { Browser } from '@capacitor/browser'
import { Filesystem, Directory } from '@capacitor/filesystem'
import { Share } from '@capacitor/share'
import { ElMessage } from 'element-plus'
import router from '@/router'
import i18n from '@/i18n'
import { useUserStore } from '@/stores/user'
import { filenameFrom } from '@/utils/filename'

/** 视为「要落到本地再交给系统打开」的扩展名(P0 只覆盖电子书/文档/音视频/压缩包) */
const DOWNLOAD_EXT =
  /\.(pdf|epub|mobi|azw3|zip|7z|rar|apk|exe|msi|dmg|appimage|deb|mp4|mkv|mov|avi|webm|mp3|flac|wav|m4a|gba|gb|gbc|nes|sfc|smc|md|n64|z64|iso|chd)$/i

function isDownloadUrl(url) {
  if (url.pathname.startsWith('/files/')) return true
  if (DOWNLOAD_EXT.test(url.pathname)) return true
  const download = url.searchParams.get('download')
  return download === 'true' || download === '1'
}

/** 原生流式下载(可带 Authorization,不受 CORS 限制),再交给系统「打开方式」 */
async function downloadAndOpen(url, nameHint) {
  const userStore = useUserStore()
  const filename = nameHint || filenameFrom(url)
  const path = `ihomy-download/${Date.now()}-${filename}`
  ElMessage({ message: i18n.global.t('common.preparingFile'), type: 'info', duration: 1200 })
  try {
    const res = await Filesystem.downloadFile({
      url,
      path,
      directory: Directory.Cache,
      headers: userStore.token ? { Authorization: `Bearer ${userStore.token}` } : undefined,
    })
    const uri = res.path?.startsWith('file://') ? res.path : `file://${res.path}`
    await Share.share({
      title: filename,
      files: [uri],
      dialogTitle: i18n.global.t('common.openWith'),
    })
  } catch (e) {
    console.warn('[native] 文件下载失败', e)
    ElMessage.error(i18n.global.t('common.fileDownloadFailed'))
  }
}

/** 统一出口:站内路由 / 文件下载 / 系统浏览器 */
function handleUrl(rawUrl, nameHint) {
  let url
  try {
    url = new URL(rawUrl, location.href)
  } catch {
    return
  }
  const download = isDownloadUrl(url)
  if (url.origin === location.origin && !download) {
    // 壳自带的站内页面:留在 WebView 里,跳系统浏览器会丢登录态。
    // 必须先判同源再判协议——iOS 壳页面是 capacitor://,相对路径解析出来也是 capacitor:。
    router.push(url.pathname + url.search + url.hash).catch(() => {})
    return
  }
  if (url.protocol !== 'https:' && url.protocol !== 'http:') return
  if (download) {
    downloadAndOpen(url.href, nameHint)
    return
  }
  Browser.open({ url: url.href })
}

export function installLinkHandler() {
  // 1) window.open:全站 7 处调用统一接管
  window.open = (url) => {
    const href = typeof url === 'string' ? url : url?.href
    if (href) handleUrl(href)
    return null
  }

  // 2) <a target=_blank> / <a download> / 外链:站内普通链接继续交给 Vue Router
  document.addEventListener('click', (e) => {
    const anchor = e.target?.closest?.('a[href]')
    if (!anchor) return
    const href = anchor.getAttribute('href')
    if (!href || href.startsWith('#') || href.startsWith('javascript:')) return
    let url
    try {
      url = new URL(href, location.href)
    } catch {
      return
    }
    const sameOrigin = url.origin === location.origin
    if (!sameOrigin && url.protocol !== 'https:' && url.protocol !== 'http:') return
    const download = anchor.hasAttribute('download') || isDownloadUrl(url)
    const leavesApp = !sameOrigin
    if (!download && !leavesApp && anchor.target !== '_blank') return
    e.preventDefault()
    handleUrl(url.href, anchor.getAttribute('download') || anchor.getAttribute('data-filename') || undefined)
  })
}
