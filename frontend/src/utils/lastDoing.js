// 「上次在做」:记录账号最近一次续做的内容(读书 / 看片 / 写博客 / 写日记)。
// - 内容数据源:各功能在续做发生点显式 recordDoing,不依赖 updated_at(博客/日记的 updated_at
//   会被浏览/点赞计数刷新,读书借阅也没有「我的借阅按最近阅读」接口)。
// - 存储:本机 localStorage 立即生效,登录后经 sys_user_pref 的 recent.doing.v1 跟账号走;
//   与首页排版不同,这里按每条记录的 at 取新合并(多设备各自记过的东西都要保留)。
// - 游客不记录、不展示:同一台设备换号时按 uid 校验本机缓存,避免串号。
import { ref } from 'vue'
import { readPref, readRemotePrefMap, writePref } from '@/utils/userPref'
import { useUserStore } from '@/stores/user'

export const LAST_DOING_LOCAL_KEY = 'ihomy:last-doing:v1'
export const LAST_DOING_PREF_KEY = 'recent.doing.v1'
/** 最多留 8 条(卡片只展示前几条,留点余量给跨设备合并) */
export const MAX_DOING_ITEMS = 8
/** 30 天没再续做的条目不再展示(记录仍留着,重新续做会刷新) */
export const DOING_VISIBLE_DAYS = 30
/** 关闭阅读器时 close() 与 onBeforeUnmount 会先后各存一次,1 秒内同一目标只记一次 */
const REPEAT_GUARD_MS = 1000

export const DOING_KINDS = ['BOOK', 'CINEMA', 'BLOG', 'DIARY']

const doingItems = ref([])
let activeSession = null
const lastRecordAt = new Map()

function currentUid() {
  try {
    const store = useUserStore()
    if (!store.isLoggedIn) return null
    return store.userInfo?.id ?? null
  } catch (e) {
    return null
  }
}

function entryKey(entry) {
  return `${entry.kind}:${entry.id}`
}

/** 单条记录归一化:不合法(缺 kind/id/title/at)返回 null */
export function normalizeDoingEntry(raw) {
  if (!raw || typeof raw !== 'object') return null
  const kind = String(raw.kind || '').toUpperCase()
  // 本站内容 id 是数字;Jellyfin 的条目 id 是 32 位十六进制串,统一按字符串校验
  const idText = String(raw.id ?? '').trim()
  const at = Number(raw.at)
  const title = String(raw.title || '')
    .trim()
    .slice(0, 120)
  if (!DOING_KINDS.includes(kind) || !idText || idText.length > 64 || !Number.isFinite(at) || at <= 0 || !title) {
    return null
  }
  const out = { kind, id: /^\d+$/.test(idText) ? Number(idText) : idText, title, at }
  if (typeof out.id === 'number' && out.id <= 0) return null
  const progress = Number(raw.progress)
  if (Number.isFinite(progress)) out.progress = Math.max(0, Math.min(100, Math.round(progress)))
  if (raw.episodeId) out.episodeId = String(raw.episodeId)
  if (raw.episodeCode) out.episodeCode = String(raw.episodeCode).slice(0, 16)
  const authorId = Number(raw.authorId)
  if (Number.isFinite(authorId) && authorId > 0) out.authorId = authorId
  return out
}

function sortAndSlice(items, max) {
  return [...items].sort((a, b) => b.at - a.at).slice(0, max)
}

/** 合并一条记录:同 kind+id 取较新的 at;新记录缺的附加字段沿用旧的;结果按 at 倒序截断 */
export function mergeDoing(items, entry, max = MAX_DOING_ITEMS) {
  const list = Array.isArray(items) ? items : []
  const next = normalizeDoingEntry(entry)
  if (!next)
    return sortAndSlice(
      list.filter((it) => normalizeDoingEntry(it)),
      max,
    )
  const map = new Map()
  for (const raw of list) {
    const it = normalizeDoingEntry(raw)
    if (!it) continue
    const key = entryKey(it)
    const prev = map.get(key)
    if (!prev || it.at > prev.at) map.set(key, it)
  }
  const key = entryKey(next)
  const prev = map.get(key)
  if (prev && prev.at > next.at) {
    // 旧记录更新(例如另一台设备刚同步过来),不要被这次迟到的事件盖回去
    map.set(key, prev)
  } else if (prev) {
    const merged = { ...prev, ...next }
    for (const field of ['progress', 'episodeId', 'episodeCode', 'authorId']) {
      if (next[field] === undefined && prev[field] !== undefined) merged[field] = prev[field]
    }
    map.set(key, merged)
  } else {
    map.set(key, next)
  }
  return sortAndSlice([...map.values()], max)
}

/** 卡片实际展示的条目:lastDays 天内(默认 30 天) */
export function visibleDoing(items, now = Date.now(), days = DOING_VISIBLE_DAYS) {
  const cutoff = now - days * 24 * 60 * 60 * 1000
  return (Array.isArray(items) ? items : []).filter((it) => {
    const at = Number(it?.at)
    return Number.isFinite(at) && at >= cutoff && at <= now + 5 * 60 * 1000
  })
}

/** 续做目标的站内地址 */
export function buildDoingPath(entry) {
  if (!entry) return '/'
  const id = encodeURIComponent(String(entry.id))
  if (entry.kind === 'BOOK') return `/library?book=${id}`
  if (entry.kind === 'CINEMA') {
    return `/cinema/${id}${entry.episodeId ? `?ep=${encodeURIComponent(String(entry.episodeId))}` : ''}`
  }
  if (entry.kind === 'BLOG') return `/blog/${id}`
  if (entry.kind === 'DIARY') {
    return entry.authorId ? `/diary/book/${entry.authorId}?entry=${id}` : '/diary'
  }
  return '/'
}

/** 三种形态:手机首页顶部 / 平板与窄屏浮卡 / 宽屏侧栏停靠 */
export function lastDoingMode({ isMobile, isTablet, isWide }) {
  if (isMobile) return 'mobile'
  if (isTablet || !isWide) return 'float'
  return 'dock'
}

/** 解析本机/远端保存的整份数据(uid 不符或格式损坏都当空) */
export function parseDoingStore(raw, uid) {
  try {
    const obj = typeof raw === 'string' ? JSON.parse(raw) : raw
    if (!obj || typeof obj !== 'object') return []
    if (Number(obj.uid) !== Number(uid)) return []
    const list = Array.isArray(obj.items) ? obj.items : []
    return list.reduce((acc, it) => mergeDoing(acc, it), [])
  } catch (e) {
    return []
  }
}

export function serializeDoingStore(items, uid) {
  return JSON.stringify({ v: 1, uid, items: Array.isArray(items) ? items : [] })
}

function writeLocal(uid, items) {
  try {
    localStorage.setItem(LAST_DOING_LOCAL_KEY, serializeDoingStore(items, uid))
  } catch (e) {
    // 隐私模式/配额满:不阻断,远端仍会保存
  }
}

function persist() {
  const uid = currentUid()
  if (!uid) return
  writePref(LAST_DOING_LOCAL_KEY, LAST_DOING_PREF_KEY, serializeDoingStore(doingItems.value, uid))
}

/** 记一条续做;未登录直接忽略 */
export function recordDoing(entry) {
  const uid = currentUid()
  if (!uid) return
  if (activeSession?.uid !== uid) initLastDoing()
  const now = Date.now()
  const normalized = normalizeDoingEntry({ ...entry, at: entry?.at ?? now })
  if (!normalized) return
  const key = entryKey(normalized)
  if (now - (lastRecordAt.get(key) ?? 0) < REPEAT_GUARD_MS) return
  lastRecordAt.set(key, now)
  doingItems.value = mergeDoing(doingItems.value, normalized)
  persist()
}

/**
 * 登录/切换账号后调一次:本机缓存 + 远端偏好按 at 取新合并。
 * 远端拉取失败时保留本机,不把「拿不到」当成「没有」。
 */
export async function initLastDoing() {
  const uid = currentUid()
  if (!uid) {
    resetLastDoing()
    return
  }
  if (activeSession?.uid === uid) return
  const session = { uid }
  activeSession = session
  lastRecordAt.clear()
  doingItems.value = parseDoingStore(readPref(LAST_DOING_LOCAL_KEY), uid)
  const map = await readRemotePrefMap()
  // 同一账号退出再登录也是新会话，旧响应不能回填或触发上传。
  if (!map || activeSession !== session || currentUid() !== uid) return
  const remote = parseDoingStore(map[LAST_DOING_PREF_KEY], uid)
  const local = doingItems.value
  if (!remote.length) {
    if (local.length) persist()
    return
  }
  const merged = remote.reduce((acc, it) => mergeDoing(acc, it), local)
  const sameAsRemote =
    merged.length === remote.length && merged.every((it, i) => entryKey(it) === entryKey(remote[i]) && it.at === remote[i].at)
  doingItems.value = merged
  if (sameAsRemote) writeLocal(uid, merged)
  else persist()
}

/** 退出登录 / 切换账号:清内存态(本机缓存按 uid 校验,不主动删) */
export function resetLastDoing() {
  doingItems.value = []
  activeSession = null
  lastRecordAt.clear()
}

/** 组件读状态用 */
export function useLastDoing() {
  return { items: doingItems }
}
