// 系统分享收件(仅原生壳):其他应用「分享 → ihomy」时,把文字/图片交给站内 /share 页面决定去向。
// 原生侧只负责接住与复制文件,业务落点(相册/愿望/日记)全在前端,和站内新建走同一批接口。
import { registerPlugin } from '@capacitor/core'
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

/** 把原生复制到缓存目录的文件读成 File(上传接口要 FormData) */
export async function readShareFile(file) {
  const res = await IhomyShare.readFile({ path: file.path })
  return fileFromBase64(res.data, file.name || 'share', file.mime || 'application/octet-stream')
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
