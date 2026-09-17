// 天气→AI 生图背景:生成/缓存/落库(家庭级 7 天缓存),供暖居全屏氛围背景使用
// 与 Home.vue 的天气卡片背景同源(同一缓存键,家庭内去重)
import { ref } from 'vue'
import { aiApi, albumApi, photoApi } from '@/api'
import { useUserStore } from '@/stores/user'

const FEATURE = 'WEATHER_IMAGE'
const CACHE_KEY = 'ihomy:weather-bg:v1'
const CFG_KEY = 'ihomy:weather-bg-config:v1'
const CFG_DEFAULT = { enabled: true, size: '2048x2048', refreshDays: 7, watermark: false }
const ALBUM_NAME = 'AI 生图'

// 风格/地点随机池:风格不固定(摄影/手绘/油画…),地点不固定(突出天气氛围,弱化地标)
const WEATHER_STYLES = ['电影感摄影', '水彩手绘', '油画', '极简插画', '复古胶片', '日系动漫', '水墨淡彩']
const WEATHER_SCENES = ['城市街道', '公园', '海边', '山间', '郊野', '湖边', '窗前']
const pickRandom = (arr) => arr[Math.floor(Math.random() * arr.length)]

export function useWeatherBg() {
  const weatherBg = ref('')
  const loading = ref(false)
  const userStore = useUserStore()
  let aiStatusChecked = false
  let aiImageAvail = false
  let albumPromise = null

  const readCfg = () => {
    try { const raw = localStorage.getItem(CFG_KEY); return raw ? { ...CFG_DEFAULT, ...JSON.parse(raw) } : { ...CFG_DEFAULT } } catch { return { ...CFG_DEFAULT } }
  }
  const season = () => { const m = new Date().getMonth() + 1; return (m >= 3 && m <= 5) ? '春' : (m >= 6 && m <= 8) ? '夏' : (m >= 9 && m <= 11) ? '秋' : '冬' }
  const dayNight = () => { const h = new Date().getHours(); return (h >= 6 && h < 19) ? 'day' : 'night' }
  const keyOf = (w) => [w?.city, w?.text, w?.iconCode, dayNight(), season()].join('|')

  const dateLabel = () => { const d = new Date(); return `${d.getMonth() + 1}月${d.getDate()}日` }
  const timeOfDayLabel = () => {
    const h = new Date().getHours()
    if (h < 5) return '凌晨'
    if (h < 8) return '清晨'
    if (h < 11) return '上午'
    if (h < 14) return '午后'
    if (h < 17) return '下午'
    if (h < 19) return '傍晚'
    return '夜晚'
  }
  // 落库文件名:城市-日期-上下午-时间(如 济南-2026-09-13-下午-17:30,冒号由后端落盘时归一为 _)
  const weatherImageName = (city) => {
    const d = new Date()
    const pad = (n) => String(n).padStart(2, '0')
    const c = city || '未知城市'
    const date = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
    const ampm = d.getHours() < 12 ? '上午' : '下午'
    const time = `${pad(d.getHours())}:${pad(d.getMinutes())}`
    return `${c}-${date}-${ampm}-${time}`
  }

  const ensureAlbum = () => {
    if (albumPromise) return albumPromise
    albumPromise = (async () => {
      try {
        const albums = await albumApi.list()
        const found = (albums || []).find((a) => a.name === ALBUM_NAME)
        if (found) return found.id
        const created = await albumApi.create({ name: ALBUM_NAME, type: 'private' })
        return created.id
      } catch { albumPromise = null; return null }
    })()
    return albumPromise
  }

  // 兜底:上次本地区域(优先当前城市)生成的天气图,供「生成不了新图」时回退展示
  const latestCached = (w) => {
    try {
      const cache = JSON.parse(localStorage.getItem(CACHE_KEY) || '{}')
      const city = w?.city || ''
      let same = null
      let any = null
      for (const k in cache) {
        const e = cache[k]
        if (!e || !e.url) continue
        if (city && k.startsWith(city + '|') && (!same || e.ts > same.ts)) same = e
        if (!any || e.ts > any.ts) any = e
      }
      return (same || any)?.url || ''
    } catch { return '' }
  }

  const load = async (w) => {
    const key = keyOf(w)
    if (!key || !w) return
    const cfg = readCfg()
    if (cfg.enabled === false) return
    const ttl = Math.max(1, cfg.refreshDays ?? 7) * 86400000
    try {
      const cache = JSON.parse(localStorage.getItem(CACHE_KEY) || '{}')
      const hit = cache[key]
      if (hit && Date.now() - hit.ts < ttl) { weatherBg.value = hit.url; return }
    } catch {}
    if (!userStore.isLoggedIn) return
    if (loading.value) return
    loading.value = true
    try {
      if (!aiStatusChecked) { try { aiImageAvail = !!(await aiApi.status())?.weatherImage?.available } catch {} aiStatusChecked = true }
      if (!aiImageAvail) { weatherBg.value = latestCached(w); return }
      const prompt = `${pickRandom(WEATHER_STYLES)},${dateLabel()}${timeOfDayLabel()},${season()}季,${w.city || ''} ${w.text || ''},${pickRandom(WEATHER_SCENES)},突出天气氛围,弱化地点地标,柔和高级色调,高清#`
      const res = await aiApi.image({ prompt, size: cfg.size || '2048x2048', watermark: cfg.watermark === true }, FEATURE)
      const first = res?.[0] || {}
      const url = first.url || (first.b64_json ? 'data:image/png;base64,' + first.b64_json : '')
      if (url) {
        weatherBg.value = url
        try { const c = JSON.parse(localStorage.getItem(CACHE_KEY) || '{}'); c[key] = { url, ts: Date.now() }; localStorage.setItem(CACHE_KEY, JSON.stringify(c)) } catch {}
        const albumId = await ensureAlbum()
        if (albumId && (/^https?:\/\//i.test(url) || /^data:/i.test(url))) { try { await photoApi.saveFromUrl(albumId, { url, name: weatherImageName(w.city), description: prompt }) } catch {} }
      } else {
        weatherBg.value = latestCached(w)
      }
    } catch {
      weatherBg.value = latestCached(w)
    }
    finally { loading.value = false }
  }

  return { weatherBg, load }
}
