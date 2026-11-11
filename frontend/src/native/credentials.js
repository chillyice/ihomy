// 壳内页面 origin 是 https://localhost(Android) 或 capacitor://localhost(iOS),而文件在服务器域名下:
// fetch/XHR 默认不带跨站 cookie,私有图片/电子书/视频会被判未登录(403)。
// 这里只对「本服务 /files/ 下的请求」补 credentials,业务接口仍走 axios 的 Bearer 头。
import { SERVER_ORIGIN } from '@/utils/assetUrl'

function isServerFile(rawUrl) {
  if (!SERVER_ORIGIN || !rawUrl) return false
  try {
    const url = new URL(String(rawUrl), location.href)
    return url.origin === SERVER_ORIGIN && url.pathname.startsWith('/files/')
  } catch {
    return false
  }
}

export function installCredentialShim() {
  // fetch:pdfjs/epubjs/hls.js 等三方库的读取路径
  const nativeFetch = window.fetch
  if (typeof nativeFetch === 'function') {
    window.fetch = function (input, init) {
      const url = typeof input === 'string' ? input : input?.url
      if (isServerFile(url)) {
        return nativeFetch.call(this, input, { ...(init || {}), credentials: 'include' })
      }
      return nativeFetch.call(this, input, init)
    }
  }

  // XMLHttpRequest:epubjs / emulatorjs 等仍用 XHR 的库
  const nativeOpen = XMLHttpRequest.prototype.open
  const nativeSend = XMLHttpRequest.prototype.send
  XMLHttpRequest.prototype.open = function (method, url, ...rest) {
    this.__ihomyUrl = url
    return nativeOpen.call(this, method, url, ...rest)
  }
  XMLHttpRequest.prototype.send = function (...args) {
    if (isServerFile(this.__ihomyUrl)) this.withCredentials = true
    return nativeSend.apply(this, args)
  }
}
