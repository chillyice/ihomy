// 手机端空闲屏保:长时间不动后盖一层全屏「背景(照片)」或「看板(只读信息板)」。
// 偏好只存本机(ihomy:<feature>:v1 约定);能否触发收在 shouldSuppress 纯函数里,
// 组件只做渲染与吞事件——判定逻辑有单测,改口径先改这里。
import { ref } from 'vue'

export const SS_KEY = 'ihomy:screensaver:v1'
export const MODES = { PHOTO: 'PHOTO', BOARD: 'BOARD' }
export const BOARD_CARDS = ['clock', 'todo', 'schedule', 'anni', 'photos']
export const IDLE_MIN = 1
export const IDLE_MAX = 30

export const DEFAULT_PREFS = {
  enabled: true,
  mode: MODES.PHOTO,
  idleMinutes: 5,
  board: { clock: true, todo: true, schedule: true, anni: true, photos: true },
}

const isObj = (v) => !!v && typeof v === 'object' && !Array.isArray(v)

/** 归一化偏好:模式回退、时长夹到 1–30 分钟、只保留已知卡片键 */
export function normalizePrefs(raw) {
  const src = isObj(raw) ? raw : {}
  const n = Number(src.idleMinutes)
  const idle = Number.isFinite(n) ? Math.min(IDLE_MAX, Math.max(IDLE_MIN, Math.round(n))) : DEFAULT_PREFS.idleMinutes
  const board = {}
  BOARD_CARDS.forEach((k) => {
    board[k] = isObj(src.board) && typeof src.board[k] === 'boolean' ? src.board[k] : DEFAULT_PREFS.board[k]
  })
  return {
    enabled: typeof src.enabled === 'boolean' ? src.enabled : DEFAULT_PREFS.enabled,
    mode: src.mode === MODES.BOARD ? MODES.BOARD : MODES.PHOTO,
    idleMinutes: idle,
    board,
  }
}

// 偏好变更广播:设置页写盘后自增,屏保组件据此重读一遍(改了时长/模式不必等下次进页面)
export const prefsTick = ref(0)

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
  const next = normalizePrefs({ ...cur, ...p, board: { ...cur.board, ...(isObj(p.board) ? p.board : {}) } })
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
// EP 弹窗/抽屉/确认框都挂在 .el-overlay;照片查看器与聊天半屏面板各有自己的根类。
const BUSY_SELECTORS = ['.el-overlay', '.photo-viewer', '.chat-float-sheet', '.cmd-scrim']

export function isBusyScreen() {
  if (typeof document === 'undefined' || !document.querySelector) return false
  return BUSY_SELECTORS.some((sel) => {
    const el = document.querySelector(sel)
    if (!el) return false
    if (typeof window === 'undefined' || !window.getComputedStyle) return true
    const style = window.getComputedStyle(el)
    return style.display !== 'none' && style.visibility !== 'hidden'
  })
}

// 设置页「预览」:自增一次即请求屏保立即展示(避免为一次预览引入全局 store)
export const previewTick = ref(0)
export function requestPreview() {
  previewTick.value += 1
}
