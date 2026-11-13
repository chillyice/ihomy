// 用户界面偏好的跨设备同步(首页布局):本机 localStorage 是即时缓存,登录后与账号对齐。
// 对齐规则:服务端有值就以服务端为准(换设备登录即恢复自己的排版);服务端没有而本机有,
// 就把本机这份交上去(当前设备已有的布局成为账号布局);两处都没有就保持默认、不写库。
// 冲突取「整份后写覆盖」:不做字段级合并,也不做实时推送。游客只写本机,一个请求都不发。
import { userPrefApi } from '@/api'
import { useUserStore } from '@/stores/user'

// 整理首页时拖拽/缩放会连发很多次,去抖后只发最后一次
const SAVE_DEBOUNCE_MS = 400
// 「上次没传成功」标记:下次进页面时以本机为准补传,否则会被服务端的旧布局盖掉
const UNSYNCED_PREFIX = 'ihomy:pref:unsynced:'

const timers = new Map()
const pending = new Map()
const uploaded = new Map()
let remotePromise = null
let remoteMap = null

/**
 * 本机缓存与账号该往哪边走(纯函数,便于单测):
 * - 'use-remote' 用服务端这份覆盖本机
 * - 'push-local' 把本机这份交给账号
 * - 'none'       无需动作
 */
export function decideSync(local, remote, unsynced = false) {
  if (!local) return remote ? 'use-remote' : 'none'
  if (local === remote) return 'none'
  if (unsynced) return 'push-local'
  if (!remote) return 'push-local'
  return 'use-remote'
}

function isLoggedIn() {
  try {
    return !!useUserStore().isLoggedIn
  } catch (e) {
    // 还没装 pinia(极端时序):按游客处理,只写本机
    return false
  }
}

/** 同步读本机缓存(storage 可注入,便于单测) */
export function readPref(localKey, storage = globalThis.localStorage) {
  try {
    return storage?.getItem(localKey) ?? null
  } catch (e) {
    return null
  }
}

function writeLocal(localKey, value, storage) {
  try {
    storage?.setItem(localKey, value)
  } catch (e) {
    // 隐私模式/配额满写不进去:不阻断,远端照样保存
  }
}

function markUnsynced(localKey, on, storage) {
  try {
    if (on) storage?.setItem(UNSYNCED_PREFIX + localKey, '1')
    else storage?.removeItem(UNSYNCED_PREFIX + localKey)
  } catch (e) {
    /* 同上,标记只是补救线索 */
  }
}

// 一次会话只拉一次(GET 失败不留缓存,下次进页面再试)
async function loadRemote() {
  if (!remotePromise) {
    remotePromise = userPrefApi.all().then(
      (map) => {
        remoteMap = map && typeof map === 'object' ? map : {}
        return remoteMap
      },
      () => {
        remoteMap = null
        remotePromise = null
        return null
      },
    )
  }
  return remotePromise
}

async function push(localKey, serverKey, value, storage) {
  if (!value) return false
  try {
    await userPrefApi.save(serverKey, value)
    uploaded.set(serverKey, value)
    if (remoteMap) remoteMap[serverKey] = value
    markUnsynced(localKey, false, storage)
    return true
  } catch (e) {
    // 网络/服务端异常:留个标记,下次进页面优先把本机这份补上去
    markUnsynced(localKey, true, storage)
    return false
  }
}

/**
 * 进页面时调一次:与服务端对齐。
 * 返回「需要页面应用的值」(只有 use-remote 才有),其余情况返回 null 表示页面上的布局不用动。
 */
export async function syncPref(localKey, serverKey, storage = globalThis.localStorage) {
  if (!isLoggedIn()) return null
  const map = await loadRemote()
  // 拉取失败时保持现状:别把「拿不到」当成「服务端没有」,否则会拿本机旧布局盖掉账号里的新版
  if (!map) return null
  const remote = map[serverKey] ?? null
  const local = readPref(localKey, storage)
  const action = decideSync(local, remote, !!readPref(UNSYNCED_PREFIX + localKey, storage))
  if (action === 'use-remote') {
    writeLocal(localKey, remote, storage)
    uploaded.set(serverKey, remote)
    return remote
  }
  if (action === 'push-local') await push(localKey, serverKey, local, storage)
  return null
}

/** 改了布局就调它:本机立刻落盘,远端去抖上传(内容与上次上传相同则跳过) */
export function writePref(localKey, serverKey, value, storage = globalThis.localStorage) {
  writeLocal(localKey, value, storage)
  if (!isLoggedIn()) return
  if (value === uploaded.get(serverKey)) return
  pending.set(serverKey, { localKey, value, storage })
  clearTimeout(timers.get(serverKey))
  timers.set(
    serverKey,
    setTimeout(() => flushPref(serverKey), SAVE_DEBOUNCE_MS),
  )
}

/** 把挂着的那次上传立刻发出去(页面隐藏/关闭前调,不等去抖) */
export function flushPref(serverKey) {
  const keys = serverKey ? [serverKey] : [...pending.keys()]
  for (const key of keys) {
    const item = pending.get(key)
    if (!item) continue
    clearTimeout(timers.get(key))
    timers.delete(key)
    pending.delete(key)
    push(item.localKey, key, item.value, item.storage)
  }
}

/** App.vue 挂载时调一次:页面隐藏或关闭前把未发的那次上传发出去 */
export function initPrefSync() {
  if (typeof window === 'undefined') return
  window.addEventListener('pagehide', () => flushPref())
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden') flushPref()
  })
}
