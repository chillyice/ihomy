// 系统分享收件(仅原生壳):其他应用「分享 → ihomy」或「用 ihomy 打开」时,
// 把文字/图片/文档/视频交给站内 /share 页面决定去向。
// 原生侧只负责接住与复制文件,业务落点(相册/图书馆/放映厅/愿望/日记)全在前端,和站内新建走同一批接口。
import { Capacitor, registerPlugin } from '@capacitor/core'
import router from '@/router'
import { useUserStore } from '@/stores/user'
import { fileFromBase64 } from '@/utils/file'

const IhomyShare = registerPlugin('IhomyShare')
/** 会话内缓存:登录跳转/刷新页面后仍能拿回同一份分享内容 */
const CACHE_KEY = 'ihomy:share:pending'

function readCache() {
  try {
    const raw = sessionStorage.getItem(CACHE_KEY)
    const v = raw ? JSON.parse(raw) : null
    return v && v.has ? v : null
  } catch {
    return null
  }
}

function writeCache(payload) {
  try {
    sessionStorage.setItem(CACHE_KEY, JSON.stringify(payload))
  } catch (e) {
    // 缩略图过大导致超出会话存储配额时丢弃预览,内容仍在原生侧
    console.warn('[shareInbox] 缓存分享内容失败', e)
    try {
      sessionStorage.setItem(CACHE_KEY, JSON.stringify({ ...payload, files: [] }))
    } catch {
      /* 忽略:退化为仅本次会话内存可用 */
    }
  }
}

/** 当前待处理分享(原生侧优先,其次会话缓存);无内容返回 null */
export async function loadSharePayload() {
  const pending = await IhomyShare.getPending().catch(() => null)
  if (pending && pending.has) {
    writeCache(pending)
    return pending
  }
  return readCache()
}

/** 保存完成或用户放弃:清掉原生与会话两侧 */
export async function clearSharePayload() {
  sessionStorage.removeItem(CACHE_KEY)
  await IhomyShare.clearPending().catch(() => {})
}

/** 把原生缓存文件读成 File(上传接口要 FormData)。
 *
 * 文档/视频在原生侧是**惰性复制**:payload 里只有元数据,这里先 prepare 落盘再取;
 * 取文件优先走本地资源地址流式读(不占桥内存),读不到才退 base64(硬限 10MB,只作兜底)。
 */
export async function readShareFile(file) {
  const f = await materialize(file)
  const name = f.name || 'share'
  const mime = f.mime || 'application/octet-stream'
  try {
    const res = await fetch(Capacitor.convertFileSrc(f.path))
    if (res.ok) {
      const blob = await res.blob()
      if (blob.size > 0) return new File([blob], name, { type: mime })
    }
  } catch (e) {
    /* 退到 base64 通道 */
  }
  const out = await IhomyShare.readFile({ path: f.path })
  return fileFromBase64(out.data, name, mime)
}

/** 确保 payload 里的这一项已落盘:有 path 直接用,没有则让原生按 index 复制(结果回写到同一份 payload) */
async function materialize(file) {
  if (!file) throw new Error('no file')
  if (file.path) return file
  if (file.index === undefined || file.index === null) throw new Error('no index')
  const res = await IhomyShare.prepare({ index: file.index })
  if (!res || !res.path) throw new Error('prepare failed')
  file.path = res.path
  if (res.name) file.name = res.name
  if (res.mime) file.mime = res.mime
  if (typeof res.size === 'number' && res.size > 0) file.size = res.size
  return file
}

async function openShare({ broadcast = false } = {}) {
  const userStore = useUserStore()
  const payload = await loadSharePayload()
  if (!payload) return
  if (!userStore.isLoggedIn) return // 登录后由下面的 watch 再唤起一次
  if (router.currentRoute.value.name !== 'ShareInbox') {
    router.push('/share')
    return
  }
  // 已经在分享页时再次分享:让页面就地换内容
  if (broadcast) listeners.forEach((fn) => fn(payload))
}

/** 页面订阅「新的分享内容到达」:分享页正在打开时也能刷新 */
const listeners = new Set()
export function onSharePayload(fn) {
  listeners.add(fn)
  return () => listeners.delete(fn)
}

/** 原生桥安装:冷启动取一次待处理内容;应用已在前台时监听再次分享 */
export async function initShareInbox() {
  await IhomyShare.addListener('shareReceived', () => {
    openShare({ broadcast: true }).catch((e) => console.warn('[shareInbox] 打开分享页失败', e))
  }).catch(() => {})
  const { watch } = await import('vue')
  const userStore = useUserStore()
  watch(
    () => userStore.isLoggedIn,
    (v) => {
      if (v) openShare().catch(() => {})
    },
  )
  await openShare()
}
