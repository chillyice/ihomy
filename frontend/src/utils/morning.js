// 每日首屏早安页:每个客户端每天第一次进入站内页时展示一次,复用屏保看板的取数口径。
// 偏好只存本机(localStorage),不落账号;「今天已经看过」也按本机日期记。
import { ref } from 'vue'
import { shouldSuppress } from './screensaver'

export const MORNING_KEY = 'ihomy:morning:v1'
export const MORNING_LAST_KEY = 'ihomy:morning:last'
export const MORNING_CARDS = ['greeting', 'date', 'todo', 'weather']

// 组件大小档位(时钟/天气/待办三张卡):sm 小 / md 标准 / lg 大
export const MORNING_CARD_SIZES = ['sm', 'md', 'lg']
/** 可调大小的卡片(问候语是全宽文本,不参与) */
export const MORNING_SIZED_CARDS = ['date', 'weather', 'todo']

export const DEFAULT_PREFS = {
  enabled: true,
  greeting: '',
  animation: true,
  cards: { greeting: true, date: true, todo: true, weather: true },
  sizes: { date: 'md', weather: 'md', todo: 'md' },
}

const isObj = (v) => !!v && typeof v === 'object' && !Array.isArray(v)

const pad = (n) => String(n).padStart(2, '0')

/** 本机日期键 YYYY-MM-DD(不依赖 UTC,跨零点按本地日历算) */
export function todayKey(d = new Date()) {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** 归一化偏好:卡片/大小档只认白名单,问候语限长,其余回落默认值 */
export function normalizePrefs(raw) {
  const src = isObj(raw) ? raw : {}
  const cards = {}
  MORNING_CARDS.forEach((k) => {
    cards[k] = isObj(src.cards) && typeof src.cards[k] === 'boolean' ? src.cards[k] : DEFAULT_PREFS.cards[k]
  })
  const sizes = {}
  MORNING_SIZED_CARDS.forEach((k) => {
    sizes[k] = isObj(src.sizes) && MORNING_CARD_SIZES.includes(src.sizes[k]) ? src.sizes[k] : DEFAULT_PREFS.sizes[k]
  })
  return {
    enabled: typeof src.enabled === 'boolean' ? src.enabled : DEFAULT_PREFS.enabled,
    greeting: typeof src.greeting === 'string' ? src.greeting.slice(0, 60) : '',
    animation: typeof src.animation === 'boolean' ? src.animation : DEFAULT_PREFS.animation,
    cards,
    sizes,
  }
}

export const prefsTick = ref(0)
export const previewTick = ref(0)

// 设置页「预览场景」:只影响紧接着的那一次预览,不落 localStorage、不改真实天气。
// condition 为空串 = 跟随实况;night 为 null = 跟随后端 sun-info 的昼夜判定。
export const MORNING_SCENE_LABELS = {
  clear: 'morning.weatherClear',
  cloud: 'morning.weatherCloud',
  overcast: 'morning.weatherOvercast',
  rain: 'morning.weatherRain',
  snow: 'morning.weatherSnow',
  fog: 'morning.weatherFog',
  thunder: 'morning.weatherThunder',
}
export const MORNING_SCENES = Object.keys(MORNING_SCENE_LABELS)
export const previewScene = ref({ condition: '', night: null })

export function setPreviewScene(condition = '', night = null) {
  const c = MORNING_SCENES.includes(condition) ? condition : ''
  previewScene.value = { condition: c, night: typeof night === 'boolean' ? night : null }
}

export function clearPreviewScene() {
  previewScene.value = { condition: '', night: null }
}

export function loadPrefs() {
  try {
    const raw = localStorage.getItem(MORNING_KEY)
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
    cards: { ...cur.cards, ...(isObj(p.cards) ? p.cards : {}) },
    sizes: { ...cur.sizes, ...(isObj(p.sizes) ? p.sizes : {}) },
  })
  try {
    localStorage.setItem(MORNING_KEY, JSON.stringify(next))
  } catch (e) {}
  prefsTick.value += 1
  return next
}

export function loadLastShown() {
  try {
    return localStorage.getItem(MORNING_LAST_KEY) || ''
  } catch (e) {
    return ''
  }
}

export function markShown(day = todayKey()) {
  try {
    localStorage.setItem(MORNING_LAST_KEY, day)
  } catch (e) {}
}

/** 设置页「预览」:即使今天已看过/已关闭开关也立即展示一次,不消耗当天的首屏机会 */
export function requestPreview() {
  previewTick.value += 1
}

/**
 * 是否应该展示每日首屏。判定收在纯函数里,组件只负责渲染。
 * @param {{enabled?:boolean,isLoggedIn?:boolean,isPureOps?:boolean,mustChangePassword?:boolean,
 *          lastShown?:string,today?:string,isChild?:boolean,inCall?:boolean,routeMeta?:object,
 *          busy?:boolean,hidden?:boolean}} ctx
 */
export function shouldShowMorning(ctx = {}) {
  const c = isObj(ctx) ? ctx : {}
  if (!c.enabled || !c.isLoggedIn || c.isPureOps || c.mustChangePassword) return false
  if (!c.today || c.lastShown === c.today) return false
  return !shouldSuppress(c)
}
