// 天气→AI 生图背景:生成/缓存/落库(家庭级 7 天缓存),供首页卡 + 暖居全屏氛围背景使用
// 当前天气无图时回退「上次生成的最后一张」(本机缓存 → 家庭「AI 生图」相册),避免背景留白
// 首页(Home.vue)与暖居外壳(WarmLayout)同源(同一缓存键,家庭内去重)
import { ref } from 'vue'
import { aiApi, albumApi, photoApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { readWeatherBgConfig, weatherArtwork } from '@/utils/weatherBackground'

const FEATURE = 'WEATHER_IMAGE'
const CACHE_KEY = 'ihomy:weather-bg:v1'
const ALBUM_NAME = 'AI 生图'
// 同一页面内首页、外壳与屏保可能同时取图，共享生成及入册操作。
const pending = new Map()

// 本机缓存治理:data: URL(内联图,体积大)只留内存不落盘;条目上限 + 最长保留期,写入前裁剪
const CACHE_MAX = 12
const CACHE_MAX_AGE = 30 * 86400000
const isPersistable = (e) => Boolean(e && e.url && !/^data:/i.test(e.url))

// 写本机缓存:data: URL 跳过;裁剪过期/超量条目;配额超限删最旧一条重试一次
const writeCache = (key, url) => {
  if (/^data:/i.test(url)) return
  try {
    const now = Date.now()
    const cache = JSON.parse(localStorage.getItem(CACHE_KEY) || '{}')
    cache[key] = { url, ts: now }
    const kept = Object.entries(cache)
      .filter(([, e]) => isPersistable(e) && now - e.ts < CACHE_MAX_AGE)
      .sort((a, b) => b[1].ts - a[1].ts)
      .slice(0, CACHE_MAX)
    try {
      localStorage.setItem(CACHE_KEY, JSON.stringify(Object.fromEntries(kept)))
    } catch {
      // 配额超限:丢最旧一条再试一次
      if (kept.length > 1) localStorage.setItem(CACHE_KEY, JSON.stringify(Object.fromEntries(kept.slice(0, -1))))
      else localStorage.removeItem(CACHE_KEY)
    }
  } catch {}
}

// 兜底:本机缓存里最新的天气图(优先当前城市,其次任意城市)
const latestCached = (w) => {
  try {
    const cache = JSON.parse(localStorage.getItem(CACHE_KEY) || '{}')
    const city = w?.city || ''
    let same = null
    let any = null
    for (const k in cache) {
      const e = cache[k]
      if (!isPersistable(e)) continue
      if (city && k.startsWith(city + '|') && (!same || e.ts > same.ts)) same = e
      if (!any || e.ts > any.ts) any = e
    }
    return (same || any)?.url || ''
  } catch {
    return ''
  }
}

// 兜底:家庭「AI 生图」相册的最新一张(跨设备持久;本机缓存为空时兜底)。
// 会话内只查一次——成功结果常驻缓存;请求失败则重置,允许下次调用重试(否则一次失败会锁死整个会话)
let lastAlbumPromise = null
const lastFromAlbum = () => {
  if (lastAlbumPromise) return lastAlbumPromise
  lastAlbumPromise = (async () => {
    try {
      const albums = await albumApi.list()
      const found = (albums || []).find((a) => a.name === ALBUM_NAME)
      return found?.cover || ''
    } catch {
      lastAlbumPromise = null
      return ''
    }
  })()
  return lastAlbumPromise
}

// 「上次生成的最后一张图」:当前天气没图可展示时回退用它(本机缓存 → 家庭相册)
export async function lastWeatherBgUrl(w, isLoggedIn) {
  const cached = latestCached(w)
  if (cached) return cached
  if (!isLoggedIn) return ''
  return lastFromAlbum()
}

export function useWeatherBg() {
  const weatherBg = ref('')
  let latestLoad = 0
  const userStore = useUserStore()
  let aiStatusChecked = false
  let aiImageAvail = false
  let albumPromise = null

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
      } catch {
        albumPromise = null
        return null
      }
    })()
    return albumPromise
  }

  const generate = async (w, cfg, prompt, key) => {
    if (!aiStatusChecked) {
      aiImageAvail = !!(await aiApi.status())?.weatherImage?.available
      aiStatusChecked = true
    }
    if (!aiImageAvail) return ''
    const res = await aiApi.image({ prompt, size: cfg.size, watermark: cfg.watermark === true }, FEATURE)
    const first = res?.[0] || {}
    let url = first.url || (first.b64_json ? 'data:image/png;base64,' + first.b64_json : '')
    if (!url) return ''
    const albumId = await ensureAlbum()
    if (albumId && (/^https?:\/\//i.test(url) || /^data:/i.test(url))) {
      try {
        // 相册描述限 255 字；完整提示词只发给模型。
        const description = [w.city, w.text, cfg.scene].filter(Boolean).join(' · ').slice(0, 255)
        const photos = await photoApi.saveFromUrl(albumId, { url, name: weatherImageName(w.city), description })
        url = photos?.[0]?.url || url
      } catch {}
    }
    writeCache(key, url)
    return url
  }

  const load = async (w) => {
    const seq = ++latestLoad
    const cfg = readWeatherBgConfig()
    if (!cfg.enabled) {
      weatherBg.value = ''
      return
    }
    // 无天气数据:有「上次生成的最后一张」就顶上,不留白
    if (!w) {
      if (!weatherBg.value) {
        const url = await lastWeatherBgUrl(null, userStore.isLoggedIn)
        if (seq === latestLoad) weatherBg.value = url
      }
      return
    }
    const { key, prompt } = weatherArtwork(w, cfg)
    const ttl = Math.max(1, cfg.refreshDays ?? 7) * 86400000
    // 当前天气状态有缓存且在保鲜期内:直接命中
    try {
      const cache = JSON.parse(localStorage.getItem(CACHE_KEY) || '{}')
      const hit = cache[key]
      if (hit && Date.now() - hit.ts < ttl) {
        weatherBg.value = hit.url
        return
      }
    } catch {}
    // 当前天气无图:先回退「上次生成的最后一张」,避免生成期间/失败时背景留白
    // 本机缓存同步取(即时);缓存为空则异步取家庭相册最新一张,不阻塞生成
    if (!weatherBg.value) {
      const cached = latestCached(w)
      if (cached) weatherBg.value = cached
      else if (userStore.isLoggedIn)
        lastWeatherBgUrl(w, true).then((u) => {
          if (seq === latestLoad && u && !weatherBg.value) weatherBg.value = u
        })
    }
    if (!userStore.isLoggedIn) return
    const requestKey = JSON.stringify([userStore.userInfo?.familyId, key])
    try {
      if (!pending.has(requestKey)) {
        pending.set(
          requestKey,
          generate(w, cfg, prompt, key).finally(() => pending.delete(requestKey)),
        )
      }
      const url = await pending.get(requestKey)
      if (seq === latestLoad && url) weatherBg.value = url
    } catch {
      // 本机或家庭相册的兜底图继续展示，下次加载可重试。
    }
  }

  return { weatherBg, load }
}
