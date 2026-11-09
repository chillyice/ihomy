// 服务端文件地址适配:数据库里存的是 /files/... 相对路径,Web 端与 API 同源可直接用;
// Capacitor 壳内页面 origin 是 https://localhost,相对路径会解析到壳自己的本地服务(404 白图),
// 因此原生下统一补上 API 所在服务的 origin。Web 端(VITE_API_BASE 未配置)零改动。
import { isNativeApp } from './platform'

const API_BASE = import.meta.env.VITE_API_BASE || ''

/** API 所在服务 origin(如 https://ihomy.top);未配置绝对基址(Web / 开发)时为空串 */
export const SERVER_ORIGIN = (() => {
  if (!API_BASE) return ''
  try {
    return new URL(API_BASE, 'https://localhost').origin
  } catch {
    return ''
  }
})()

/** 是否处于「壳内 + 配了绝对基址」的场景,才需要改写文件地址 */
export const NEED_FILE_ORIGIN = !!SERVER_ORIGIN && isNativeApp()

/** 站内绝对路径(如 /api/public/...)→ 服务端绝对地址;Web 端原样返回 */
export function serverUrl(path) {
  if (!NEED_FILE_ORIGIN || typeof path !== 'string') return path
  return path.startsWith('/') && !path.startsWith('//') ? SERVER_ORIGIN + path : path
}

/** 单个 /files/ 地址 → 服务端绝对地址;其它地址原样返回 */
export function assetUrl(url) {
  if (!NEED_FILE_ORIGIN || typeof url !== 'string') return url
  return url.startsWith('/files/') ? SERVER_ORIGIN + url : url
}

/**
 * 递归改写接口响应 / WS 消息里的文件地址(只动 /files/ 开头的字符串,原地修改)。
 * 深度上限防异常数据导致栈溢出。
 */
export function rewriteAssetUrls(value, depth = 0) {
  if (!NEED_FILE_ORIGIN || depth > 12 || value == null) return value
  if (typeof value === 'string') return assetUrl(value)
  if (Array.isArray(value)) {
    for (let i = 0; i < value.length; i++) value[i] = rewriteAssetUrls(value[i], depth + 1)
    return value
  }
  if (typeof value === 'object') {
    for (const key of Object.keys(value)) value[key] = rewriteAssetUrls(value[key], depth + 1)
    return value
  }
  return value
}
