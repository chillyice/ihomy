// 统一屏保:长时间不动后盖一层全屏页。
// 背景(纯黑 / 天气图 / 我的图片)与卡片(时间天气 / 今日待办 / 未来日程 / 纪念日 / 家庭照片)
// 分开配置;原先的手机屏保、暖居背景待机浮现、窗边常亮看板合并到这里,一处口径。
// 偏好只存本机(设备相关:多久不动、这台设备显示什么),归一化与能否触发都收在纯函数里。
import { ref } from 'vue'
import { parseWarmBg, presetById, warmBgLayer } from '@/utils/warmBackground'

export const SS_KEY = 'ihomy:screensaver:v1'
export const BG = { BLACK: 'BLACK', WEATHER: 'WEATHER', IMAGE: 'IMAGE' }
export const CARDS = ['clock', 'todo', 'schedule', 'anni', 'photos']
export const IDLE_MIN = 1
export const IDLE_MAX = 30

export const IMAGE_DEFAULTS = { pick: 'preset', preset: 'amber', url: '', opacity: 60 }

export const DEFAULT_PREFS = {
  enabled: true,
  idleMinutes: 5,
  bg: BG.WEATHER,
  image: { ...IMAGE_DEFAULTS },
  cards: { clock: true, todo: true, schedule: true, anni: true, photos: true },
}

const isObj = (v) => !!v && typeof v === 'object' && !Array.isArray(v)

// 「我的图片」沿用暖居背景的那套键,归一化直接复用 parseWarmBg(预设白名单 / 上传地址白名单 / 浓度夹取)
function normalizeImage(raw) {
  const src = isObj(raw) ? { ...IMAGE_DEFAULTS, ...raw } : IMAGE_DEFAULTS
  const wb = parseWarmBg({ mode: 'mine', pick: src.pick, preset: src.preset, url: src.url, opacity: src.opacity })
  return { pick: wb.pick, preset: wb.preset, url: wb.url, opacity: wb.opacity }
}

/**
 * 归一化偏好:
 * - 未知背景回退天气图;空闲时长夹到 1–30 分钟;卡片只留白名单键
 * - 兼容旧版 {mode: PHOTO|BOARD, board:{...}}(屏保与看板合并前的存法):按「我的图片 + 原卡片勾选」迁移
 */
export function normalizePrefs(raw) {
  const src = isObj(raw) ? raw : {}
  const n = Number(src.idleMinutes)
  const idle = Number.isFinite(n) ? Math.min(IDLE_MAX, Math.max(IDLE_MIN, Math.round(n))) : DEFAULT_PREFS.idleMinutes
  const cards = {}
  CARDS.forEach((k) => {
    const fromCards = isObj(src.cards) ? src.cards[k] : undefined
    const fromBoard = isObj(src.board) ? src.board[k] : undefined
    const value = typeof fromCards === 'boolean' ? fromCards : typeof fromBoard === 'boolean' ? fromBoard : true
    cards[k] = value
  })
  const legacy = typeof src.mode === 'string' && !src.bg
  let bg = DEFAULT_PREFS.bg
  if (src.bg === BG.BLACK || src.bg === BG.IMAGE) bg = src.bg
  else if (legacy) bg = BG.IMAGE
  return {
    enabled: typeof src.enabled === 'boolean' ? src.enabled : DEFAULT_PREFS.enabled,
    idleMinutes: idle,
    bg,
    image: normalizeImage(src.image),
    cards,
  }
}

// 偏好变更广播:设置页写盘后自增,屏保组件据此重读(改了时长/背景/卡片不必等下次进页面)
export const prefsTick = ref(0)
if (typeof window !== 'undefined') {
  window.addEventListener('storage', ({ key }) => {
    if (key === null || key === SS_KEY) prefsTick.value++
  })
}

export function loadPrefs() {
  try {
    const raw = localStorage.getItem(SS_KEY)
    return normalizePrefs(raw ? JSON.parse(raw) : null)
  } catch (e) {
    return normalizePrefs(null)
  }
}

export function savePrefs(patch = {}) {
  const cur = loadPrefs()
  const p = isObj(patch) ? patch : {}
  const next = normalizePrefs({
    ...cur,
    ...p,
    image: { ...cur.image, ...(isObj(p.image) ? p.image : {}) },
    cards: { ...cur.cards, ...(isObj(p.cards) ? p.cards : {}) },
  })
  try {
    localStorage.setItem(SS_KEY, JSON.stringify(next))
  } catch (e) {}
  prefsTick.value += 1
  return next
}

/**
 * 是否禁止触发屏保。传入上下文而不是在函数里读 store,便于单测覆盖每条拦截原因。
 * @param {{isLoggedIn?:boolean,isChild?:boolean,inCall?:boolean,routeMeta?:object,busy?:boolean,hidden?:boolean}} ctx
 */
export function shouldSuppress(ctx = {}) {
  const c = isObj(ctx) ? ctx : {}
  if (!c.isLoggedIn) return true // 登录页/游客不弹
  if (c.isChild) return true // 儿童模式交给家长入口,别盖住
  if (c.inCall) return true // 通话中整屏接管,屏保让位
  if (c.hidden) return true // 页面在后台,计时暂停
  if (c.busy) return true // 弹窗/查看器/聊天面板开着
  const meta = isObj(c.routeMeta) ? c.routeMeta : {}
  if (meta.standalone || meta.immersive) return true // 独立产品页/沉浸页
  return false
}

// 这些浮层 z-index ≥ 屏保(--z-immersive 150),屏保出现会被压在下面,必须先让位。
// EP 弹窗/抽屉/确认框都挂在 .el-overlay;照片查看器与聊天半屏面板各有自己的根类;
// 每日首屏早安页同样占满一屏,屏保不许盖上来。
const BUSY_SELECTORS = ['.el-overlay', '.photo-viewer', '.chat-float-sheet', '.cmd-scrim', '.morning-overlay']

export function isBusyScreen() {
  if (typeof document === 'undefined' || !document.querySelector) return false
  return BUSY_SELECTORS.some((sel) => {
    return Array.from(document.querySelectorAll(sel)).some(
      (el) => el.getClientRects().length && window.getComputedStyle(el).visibility !== 'hidden',
    )
  })
}

// 编辑/阅读/游戏/播放这类「人在忙」的页面与状态:即便长时间不动也不该盖屏保
export function protectedStandbyRoute(path) {
  return /^\/games\/|^\/tools\/gba(?:\/|$)|^\/tools\/mindmap\/|\/(?:edit|new)(?:\/|$)|^\/cinema\//.test(path)
}

export function activeStandbyTask(doc) {
  if (!doc) return false
  if (doc.fullscreenElement || doc.activeElement?.closest?.('input, textarea, select, [contenteditable="true"], [contenteditable=""]'))
    return true
  if (
    Array.from(doc.querySelectorAll('.reader-overlay, .fp-sidebar, .recording, [data-standby-busy="true"]')).some(
      (el) => el.getClientRects().length,
    )
  )
    return true
  const drafts = doc.querySelectorAll('textarea, .chat-composer .msg-input input, [contenteditable="true"]')
  if (Array.from(drafts).some((el) => el.getClientRects().length && (el.value || el.textContent)?.trim())) return true
  return Array.from(doc.querySelectorAll('video')).some((video) => !video.paused && !video.ended && video.getClientRects().length)
}

const urlCss = (url) => `url("${String(url).replace(/"/g, '%22')}")`

/**
 * 算出屏保背景层(纯函数):
 * - 纯黑:什么都不画,只留黑底(防烧屏)
 * - 天气图:有当日天气图就满屏铺(浓度 1),没有就按纯黑
 * - 我的图片:内置氛围或上传照片,浓度取用户设定;上传图在未登录时不可读(/files/ 需登录),回落内置氛围
 * 返回 {kind:'black'} 或 {kind:'image', image, size, opacity}
 */
export function screensaverBgLayer(pref, { weatherUrl = '', isLoggedIn = false } = {}) {
  const p = pref || DEFAULT_PREFS
  if (p.bg === BG.BLACK) return { kind: 'black' }
  if (p.bg === BG.WEATHER) {
    return weatherUrl ? { kind: 'image', image: urlCss(weatherUrl), size: 'cover', opacity: 1 } : { kind: 'black' }
  }
  const layer = warmBgLayer({ mode: 'mine', ...p.image }, { isLoggedIn })
  if (layer) return { kind: 'image', ...layer }
  // 上传图未登录不可读时 warmBgLayer 会返回 null:退回用户选的内置氛围,别留白
  const preset = presetById(p.image.preset)
  return { kind: 'image', image: preset.css, size: preset.size, opacity: p.image.opacity / 100 }
}

// 设置页「预览」:自增一次即请求屏保立即展示(避免为一次预览引入全局 store)
export const previewTick = ref(0)
export function requestPreview() {
  previewTick.value += 1
}
