// 暖居桌面外壳的全屏背景:来源可选「每日天气图(默认)」或「我的背景」。
// 「我的背景」= 内置氛围(纯 CSS,跟随晨暮色板)或用户上传的照片。
// 偏好跟账号走:本机 localStorage 即时生效,登录后经 userPref 与账号对齐(与首页布局同一套规则)。
import { reactive } from 'vue'
import { readPref, syncPref, writePref } from '@/utils/userPref'

export const WARM_BG_STORAGE_KEY = 'ihomy:warm-bg:v1'
// 键格式受 UserPrefService 限制:点分小写、版本号写在键里
export const WARM_BG_PREF_KEY = 'home.warm.bg.v1'

// 内置氛围:不打包位图,用主题 token 拼渐变(晨/暮自动换色),亚麻一档复用首页纸纹材质。
// background-size 与 background-image 一一对应(多层时按逗号顺序)。
export const BG_PRESETS = [
  {
    id: 'amber',
    labelKey: 'warmBg.presetAmber',
    css: 'radial-gradient(circle at 22% 16%, rgba(var(--blob-2), 0.55), transparent 58%), radial-gradient(circle at 82% 6%, rgba(var(--blob-1), 0.42), transparent 55%), linear-gradient(155deg, var(--color-bg), var(--color-bg-2))',
    size: 'cover',
  },
  {
    id: 'sage',
    labelKey: 'warmBg.presetSage',
    css: 'radial-gradient(circle at 14% 76%, rgba(var(--blob-3), 0.5), transparent 60%), radial-gradient(circle at 78% 20%, rgba(var(--blob-5), 0.4), transparent 58%), linear-gradient(200deg, var(--color-bg), var(--color-bg-2))',
    size: 'cover',
  },
  {
    id: 'linen',
    labelKey: 'warmBg.presetLinen',
    css: 'linear-gradient(rgba(var(--color-card-rgb), 0.42), rgba(var(--color-card-rgb), 0.42)), var(--home-grain)',
    size: 'auto, var(--home-grain-size)',
  },
  {
    id: 'dusk',
    labelKey: 'warmBg.presetDusk',
    css: 'radial-gradient(circle at 76% 82%, rgba(var(--blob-1), 0.5), transparent 62%), radial-gradient(circle at 20% 8%, rgba(var(--blob-2), 0.36), transparent 56%), linear-gradient(170deg, var(--color-bg-2), var(--color-bg))',
    size: 'cover',
  },
  {
    id: 'plain',
    labelKey: 'warmBg.presetPlain',
    css: 'linear-gradient(180deg, var(--color-bg), var(--color-bg-2))',
    size: 'cover',
  },
]

const PRESET_IDS = new Set(BG_PRESETS.map((p) => p.id))

// 默认:天气图(保持既有体验);我的背景默认落在第一档内置氛围,浓度 60%
export const DEFAULT_WARM_BG = { mode: 'weather', pick: 'preset', preset: 'amber', url: '', opacity: 60 }

export function presetById(id) {
  return BG_PRESETS.find((p) => p.id === id) || BG_PRESETS[0]
}

// 只接受站内上传或 http(s) 图片地址;data: URL 体积大,不允许进偏好
const isImageUrl = (u) => typeof u === 'string' && (/^\/files\//.test(u) || /^https?:\/\//i.test(u))

/**
 * 把本机/账号里的原始偏好归一成可用状态(纯函数):
 * 未知来源回落天气图、未知预设回落第一档、浓度夹在 20~100。
 */
export function parseWarmBg(raw) {
  const out = { ...DEFAULT_WARM_BG }
  if (!raw) return out
  let obj = raw
  if (typeof raw === 'string') {
    try {
      obj = JSON.parse(raw)
    } catch (e) {
      return out
    }
  }
  if (!obj || typeof obj !== 'object') return out
  if (obj.mode === 'mine') out.mode = 'mine'
  if (obj.pick === 'upload') out.pick = 'upload'
  if (typeof obj.preset === 'string' && PRESET_IDS.has(obj.preset)) out.preset = obj.preset
  if (isImageUrl(obj.url)) out.url = obj.url
  const opacity = Number(obj.opacity)
  if (Number.isFinite(opacity)) out.opacity = Math.min(100, Math.max(20, Math.round(opacity)))
  return out
}

const urlCss = (url) => `url("${String(url).replace(/"/g, '%22')}")`

/**
 * 算出背景层要用的样式(纯函数):
 * - 天气图:沿用既有 30% 浓度 + 待机浮现
 * - 内置氛围/上传照片:用用户设定的浓度
 * - 上传图在未登录时不可读(/files/ 需登录),回落天气图
 * 返回 null 表示没有可画的底图(不渲染背景层)。
 */
export function warmBgLayer(pref, { weatherUrl = '', isLoggedIn = false } = {}) {
  const p = pref || DEFAULT_WARM_BG
  const weatherLayer = weatherUrl ? { image: urlCss(weatherUrl), size: 'cover', opacity: 0.3 } : null
  if (p.mode !== 'mine') return weatherLayer
  if (p.pick === 'upload') {
    if (!p.url) return { ...presetLayer(p) }
    if (!isLoggedIn) return weatherLayer
    return { image: urlCss(p.url), size: 'cover', opacity: p.opacity / 100 }
  }
  return presetLayer(p)
}

function presetLayer(p) {
  const preset = presetById(p.preset)
  return { image: preset.css, size: preset.size, opacity: p.opacity / 100 }
}

// —— 响应式单例:外壳与设置面板共用一份(设置改动立即反映到背景层) ——
// 模块加载时先读本机缓存同步生效;账号同步在 initWarmBg 里做,失败保持本机现状。
export const warmBgState = reactive(parseWarmBg(readPref(WARM_BG_STORAGE_KEY)))

let touched = false
let initPromise = null

/** 进外壳/设置页时调一次(幂等):与服务端对齐,账号有值以账号为准 */
export function initWarmBg() {
  if (initPromise) return initPromise
  initPromise = (async () => {
    const raw = await syncPref(WARM_BG_STORAGE_KEY, WARM_BG_PREF_KEY).catch(() => null)
    // 用户在同步回来之前已经动过设置:以用户这次选择为准,别被远端盖掉
    if (raw && !touched) Object.assign(warmBgState, parseWarmBg(raw))
  })()
  return initPromise
}

/** 改背景设置:本机立刻落盘,账号侧去抖上传(writePref 内部处理) */
export function setWarmBg(patch) {
  if (!patch) return
  touched = true
  Object.assign(warmBgState, parseWarmBg({ ...warmBgState, ...patch }))
  writePref(WARM_BG_STORAGE_KEY, WARM_BG_PREF_KEY, JSON.stringify({ ...warmBgState }))
}

export function resetWarmBg() {
  setWarmBg({ ...DEFAULT_WARM_BG })
}
