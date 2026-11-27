<!-- 统一屏保:长期不动后盖一层全屏页。
     默认随闲置时间和昼夜切换，也可固定纯黑、天气图或我的图片。
     原先的手机屏保、暖居背景待机浮现与窗边常亮看板合并到这里(见 utils/screensaver.js)。
     交互口径:轻点/移动/按键任意一种都只做唤醒——根节点吞掉 click/pointerdown,
       背后页面的按钮与链接收不到这次事件(不穿透);滚动/右键/长按菜单同样拦下。
     只读:屏保内不发任何写请求(待办不可勾选),避免误触改数据。 -->
<template>
  <Teleport to="body">
    <div
      v-if="visible"
      class="ss"
      :style="{ '--ss-x': shift.x + 'px', '--ss-y': shift.y + 'px' }"
      :class="{ 'ss-dim': phase === 'dim', 'ss-cover': phase === 'black' || phase === 'cards', 'ss-waking': waking }"
      ref="overlay"
      tabindex="-1"
      role="dialog"
      aria-modal="true"
      :aria-label="$t('screensaver.overlayLabel')"
      @click.stop="wake"
      @pointerdown.stop
      @pointermove="onOverlayMove"
      @wheel.stop.prevent
      @touchmove.stop.prevent
      @contextmenu.prevent
    >
      <MorningScene
        v-if="phase === 'cards'"
        class="ss-weather-art"
        :condition="resolveMorningWeather(weather)"
        :night="sceneNight"
        :sun-scene="sunScene"
        :wind-scale="weather?.nowFull?.windScale ?? weather?.windScale"
        :wind-deg="weather?.nowFull?.wind360"
        :wind-dir="weather?.nowFull?.windDir"
        :precip-level="weather?.precipLevel"
      />
      <div
        v-if="phase === 'classic' && bg.kind === 'image'"
        class="ss-bg"
        aria-hidden="true"
        :style="{ backgroundImage: bg.image, backgroundSize: bg.size, opacity: bg.opacity }"
      ></div>
      <div
        v-if="showCards && (phase === 'cards' || bg.kind === 'image') && hasContent"
        class="ss-scrim"
        :class="{ 'ss-scrim-photo': phase === 'classic' && bg.photo }"
        aria-hidden="true"
      ></div>

      <div v-if="showCards" class="ss-stage">
        <header v-if="cards.clock" class="ss-head">
          <div class="ss-clock">{{ clock.time }}</div>
          <div class="ss-meta">
            {{ clock.date }} · {{ clock.week }}
            <span v-if="familyName"> · {{ familyName }}</span>
          </div>
          <div v-if="weather" class="ss-weather">
            <span class="ss-temp">{{ Math.round(Number(weather.temp) || 0) }}°</span>
            <span>{{ weather.text }}</span>
            <span v-if="weather.city">{{ weather.city }}</span>
          </div>
          <div v-if="topWarning" class="ss-warn" :style="{ '--warn-color': topWarning.severityColor || 'var(--color-warning)' }">
            <span class="ss-warn-type">{{ topWarning.typeName || topWarning.title }}</span>
            <span class="ss-warn-title">{{ topWarning.title || topWarning.typeName }}</span>
          </div>
        </header>

        <div v-if="hasContent" class="ss-cards" :class="{ 'ss-cards-single': !cards.clock }">
          <section v-if="cards.todo && todayTodos.length" class="ss-card">
            <div class="ss-card-title">{{ $t('screensaver.todayTodo') }}</div>
            <ul class="ss-list">
              <li v-for="item in todayTodos.slice(0, 5)" :key="item.key" class="ss-row">
                <span class="ss-time">{{ item.timeLabel }}</span>
                <span class="ss-title">{{ item.title }}</span>
                <span class="ss-source">{{ item.sourceLabel }}</span>
              </li>
            </ul>
          </section>

          <section v-if="cards.schedule && agenda.length" class="ss-card">
            <div class="ss-card-title">{{ $t('screensaver.upcoming') }}</div>
            <ul class="ss-list">
              <li v-for="item in agenda.slice(0, 6)" :key="item.key" class="ss-row">
                <span class="ss-time">{{ item.dayLabel }}</span>
                <span class="ss-title">{{ item.title }}</span>
                <span class="ss-source">{{ item.sourceLabel }}</span>
              </li>
            </ul>
          </section>

          <section v-if="cards.anni && events.length" class="ss-card">
            <div class="ss-card-title">{{ $t('screensaver.anniversary') }}</div>
            <ul class="ss-list">
              <li v-for="(ev, i) in events.slice(0, 4)" :key="`${ev.label}-${i}`" class="ss-row">
                <span class="ss-time">{{ ev.days === 0 ? $t('screensaver.today') : ev.date }}</span>
                <span class="ss-title">{{ ev.label }}</span>
                <span v-if="ev.days != null && ev.days !== 0" class="ss-source">{{ $t('screensaver.daysLeft', { n: ev.days }) }}</span>
              </li>
            </ul>
          </section>

          <section v-if="cards.photos && currentPhoto" class="ss-card ss-card-photo">
            <div class="ss-card-title">{{ $t('screensaver.photos') }}</div>
            <transition name="ss-fade">
              <img :key="currentPhoto.id || currentPhoto.url" :src="currentPhoto.url" alt="" />
            </transition>
          </section>
        </div>
      </div>

      <div v-if="showCards" class="ss-hint">{{ $t('screensaver.exitHint') }}</div>
    </div>
  </Teleport>
</template>

<script setup>
import { computed, defineAsyncComponent, inject, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/user'
import { useCallStore } from '@/stores/call'
import { useDisplayMode } from '@/composables/useDisplayMode'
import { useIdle } from '@/composables/useIdle'
import { useWeatherBg } from '@/composables/useWeatherBg'
import { isVoiceCaptureActive } from '@/composables/useVoiceRecorder'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'
import { resolveMorningWeather } from '@/utils/morningWeather'
import { publicApi, scheduleApi } from '@/api'
import {
  BG,
  activeStandbyTask,
  defaultScreensaverStage,
  isBusyScreen,
  loadPrefs,
  prefsTick,
  previewTick,
  protectedStandbyRoute,
  screensaverBgLayer,
  shouldSuppress,
} from '@/utils/screensaver'

const { t } = useI18n()
const MorningScene = defineAsyncComponent(() => import('@/components/morning/MorningScene.vue'))
const route = useRoute()
const userStore = useUserStore()
const callStore = useCallStore()
const { isChild } = useDisplayMode()
const sunLight = inject(SUN_LIGHT_KEY, null)
const weather = computed(() => sunLight?.weather?.value || null)
const weatherDetail = computed(() => sunLight?.weatherDetail?.value || null)
// 预警条只取第一条(和风按严重度返回);无预警时整条不渲染
const topWarning = computed(() => {
  const list = weatherDetail.value?.warning
  return Array.isArray(list) && list.length ? list[0] : null
})

const PHOTO_INTERVAL_MS = 8000
const REFRESH_MS = 600000
const DATA_TTL_MS = 300000
// 防烧屏:内容和时钟每 60 秒在 ±8px 之间挪一次(与旧看板同口径)
const SHIFT_INTERVAL_MS = 60000

const prefs = reactive(loadPrefs())
const visible = ref(false)
const phase = ref('classic')
const waking = ref(false)
const showCards = computed(() => phase.value === 'classic' || phase.value === 'cards')
let phaseTimer
let wakeTimer
watch(
  () => prefs.enabled && prefs.bg === BG.DEFAULT && !isChild.value && !userStore.isPureOps && !userStore.mustChangePassword,
  (enabled) => {
    if (sunLight) sunLight.seatLighting.value = enabled
  },
  { immediate: true },
)
const overlay = ref(null)
let previousFocus = null
let keyToRelease = null
let shownAt = 0
let loadSequence = 0
const clock = ref({ time: '', date: '', week: '' })
const sunScene = computed(() => (sunLight?.activeSunInfo.value?.slots?.length ? sunLight.sunScene.value : null))
const sceneNight = computed(() => sunScene.value?.isNight ?? clock.value.time >= '19:00')
const todayStr = ref('')
const familyName = ref('')
const photos = ref([])
const photoIndex = ref(0)
const events = ref([])
const scheduleItems = ref([])
const shift = ref({ x: 0, y: 0 })
let shiftStep = 0

const cards = computed(() => prefs.cards)
// 天气图按需取(不选天气图时不触发生图);暖居外壳/首页也共用同一份缓存
const { weatherBg, load: loadWeatherBg } = useWeatherBg()
const bg = computed(() => screensaverBgLayer(prefs, { weatherUrl: weatherBg.value, isLoggedIn: userStore.isLoggedIn }))

// 取数缓存:屏保反复进出不重复打接口(5 分钟内拿缓存)
const cache = { at: 0, photos: [], familyName: '', events: [], schedule: [] }
let clockTimer = 0
let photoTimer = 0
let shiftTimer = 0
let refreshTimer = 0

const currentPhoto = computed(() => photos.value[photoIndex.value] || null)

const pad = (n) => String(n).padStart(2, '0')
const localDateStr = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`

const syncClock = () => {
  const d = new Date()
  const time = `${pad(d.getHours())}:${pad(d.getMinutes())}`
  const date = t('screensaver.dateFmt', { m: d.getMonth() + 1, d: d.getDate() })
  const week = t(`weatherPage.w${d.getDay()}`)
  const ds = localDateStr(d)
  if (ds !== todayStr.value) todayStr.value = ds
  if (time !== clock.value.time || date !== clock.value.date || week !== clock.value.week) {
    clock.value = { time, date, week }
  }
}

const dayLabel = (dateStr) => {
  if (!dateStr) return ''
  const parts = String(dateStr).split('-').map(Number)
  if (parts.length < 3 || parts.some((n) => !Number.isFinite(n))) return dateStr
  const now = new Date()
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const target = new Date(parts[0], parts[1] - 1, parts[2])
  const diff = Math.round((target - today) / 86400000)
  if (diff === 0) return t('screensaver.dayToday')
  if (diff === 1) return t('screensaver.dayTomorrow')
  return t('screensaver.dayFmt', { m: parts[1], d: parts[2] })
}

const toAgendaItem = (it) => ({
  key: it.key || `${it.kind}:${it.id}:${it.date}`,
  kind: it.kind,
  date: it.date,
  dayLabel: dayLabel(it.date),
  timeLabel: it.time ? String(it.time).slice(0, 5) : t('screensaver.allDay'),
  title: it.title || '',
  sourceLabel: t(`screensaver.kind.${it.kind || 'REMINDER'}`),
})

const mappedItems = computed(() => scheduleItems.value.map(toAgendaItem).filter((it) => it.title))
const todayTodos = computed(() => mappedItems.value.filter((it) => it.date && it.date === todayStr.value))
const agenda = computed(() => mappedItems.value.filter((it) => it.date !== todayStr.value))

// 有卡片要显示才铺压暗层:纯背景图 + 只看图片时保持画面干净
const hasContent = computed(() => {
  const c = prefs.cards
  return !!(
    (c.clock && (clock.value.time || weather.value)) ||
    (c.todo && todayTodos.value.length) ||
    (c.schedule && agenda.value.length) ||
    (c.anni && events.value.length) ||
    (c.photos && currentPhoto.value)
  )
})

const applyCache = () => {
  photos.value = cache.photos
  familyName.value = cache.familyName
  events.value = cache.events
  scheduleItems.value = cache.schedule
  photoIndex.value = 0
}

const loadData = async (force = false) => {
  if (!userStore.isLoggedIn) return
  if (!force && cache.at && Date.now() - cache.at < DATA_TTL_MS) {
    applyCache()
    return
  }
  const sequence = ++loadSequence
  // 两个接口互不依赖:并行;任一失败不拖垮整个屏保(照片/日程各自留空)
  const [home, upcoming] = await Promise.all([publicApi.getHome().catch(() => null), scheduleApi.upcoming(7).catch(() => null)])
  if (sequence !== loadSequence || !visible.value) return
  const list = Array.isArray(upcoming) ? upcoming : Array.isArray(upcoming?.items) ? upcoming.items : []
  cache.photos = Array.isArray(home?.photos) ? home.photos.filter((p) => p && p.url) : []
  cache.familyName = home?.family?.name || ''
  const homeEvents = Array.isArray(home?.stats?.upcomingEvents) ? home.stats.upcomingEvents : []
  cache.events = homeEvents.length
    ? homeEvents
    : list.filter((it) => it.kind === 'ANNIVERSARY').map((it) => ({ label: it.title, date: it.date }))
  cache.schedule = list
  cache.at = Date.now()
  applyCache()
}

const stopPhoto = () => {
  if (photoTimer) clearInterval(photoTimer)
  photoTimer = 0
}
const startPhoto = () => {
  stopPhoto()
  if (photos.value.length < 2) return
  photoTimer = setInterval(() => {
    photoIndex.value = (photoIndex.value + 1) % photos.value.length
  }, PHOTO_INTERVAL_MS)
}

const stopShift = () => {
  if (shiftTimer) clearInterval(shiftTimer)
  shiftTimer = 0
  shift.value = { x: 0, y: 0 }
}
const startShift = () => {
  stopShift()
  shiftTimer = setInterval(() => {
    shiftStep++
    shift.value = { x: ((shiftStep * 7) % 17) - 8, y: ((shiftStep * 5) % 13) - 6 }
  }, SHIFT_INTERVAL_MS)
}

const stopTimers = () => {
  if (clockTimer) clearInterval(clockTimer)
  if (refreshTimer) clearInterval(refreshTimer)
  clockTimer = 0
  refreshTimer = 0
  stopPhoto()
  stopShift()
}

const syncPrefs = () => Object.assign(prefs, loadPrefs())

// 照片面板只在勾了照片时才轮播
const syncPhotoTimer = () => {
  if (prefs.cards.photos) startPhoto()
  else stopPhoto()
}

const displayPhase = () => {
  clearTimeout(phaseTimer)
  stopTimers()
  loadSequence++
  if (prefs.bg === BG.DEFAULT) {
    const stage = defaultScreensaverStage(shownAt)
    phase.value = stage.phase
    phaseTimer = setTimeout(displayPhase, Math.max(1, stage.nextAt - Date.now()))
  } else phase.value = 'classic'
  if (sunLight) sunLight.saverPhase.value = prefs.bg === BG.DEFAULT ? phase.value : null
  if (!showCards.value) return
  syncClock()
  clockTimer = setInterval(syncClock, 1000)
  startShift()
  syncPhotoTimer()
  if (bg.value.kind === 'image' && prefs.bg === BG.WEATHER && !weatherBg.value) loadWeatherBg(weather.value)
  loadData()
  refreshTimer = setInterval(() => loadData(true), REFRESH_MS)
}

const enter = () => {
  visible.value = true
  shownAt = Date.now()
  previousFocus = document.activeElement
  nextTick(() => overlay.value?.focus({ preventScroll: true }))
  displayPhase()
}

const show = () => {
  syncPrefs()
  if (!prefs.enabled || shouldSuppress(context())) return
  enter()
}

const hide = () => {
  if (!visible.value) return
  clearTimeout(phaseTimer)
  clearTimeout(wakeTimer)
  waking.value = false
  if (sunLight) sunLight.saverPhase.value = null
  visible.value = false
  loadSequence++
  stopTimers()
  if (previousFocus?.isConnected && !callStore.inCall) previousFocus.focus?.({ preventScroll: true })
  previousFocus = null
}

// 轻点任意处唤醒:该次 click 已被根节点吞下,背后页面收不到
const wake = () => {
  if (!visible.value || waking.value) return
  if (prefs.bg === BG.DEFAULT) {
    waking.value = true
    clearTimeout(phaseTimer)
    if (sunLight) sunLight.saverPhase.value = null
    // Keep the input shield and its current picture while the room brightens.
    wakeTimer = setTimeout(() => {
      hide()
      poke()
    }, 2400)
    return
  }
  hide()
  poke()
}

// 桌面端没有「轻点」这一步:鼠标一动就该醒;刚进入的 600ms 内忽略合成事件,避免刚盖上就被自己关掉
const onOverlayMove = () => {
  if (Date.now() - shownAt < 600) return
  wake()
}

const context = () => ({
  isLoggedIn: userStore.isLoggedIn,
  isChild: isChild.value,
  inCall: callStore.inCall,
  routeMeta: route.meta,
  busy:
    sunLight?.suspended.value ||
    isBusyScreen() ||
    isVoiceCaptureActive() ||
    protectedStandbyRoute(route.path) ||
    activeStandbyTask(document) ||
    userStore.mustChangePassword ||
    userStore.isPureOps,
  hidden: typeof document !== 'undefined' && document.visibilityState !== 'visible',
})

const { isIdle, poke } = useIdle({
  idleMs: () => (prefs.enabled ? prefs.idleMinutes * 60000 : 0),
  canTrigger: () => !shouldSuppress(context()),
})

watch(isIdle, (idle) => {
  if (idle && !visible.value) show()
})

// 设置页「预览」:立即展示(会照常吞点击,点一下即退出)
watch(previewTick, () => {
  syncPrefs()
  if (sunLight?.suspended.value || protectedStandbyRoute(route.path) || route.meta.immersive) return
  if (!visible.value && !callStore.inCall && !isChild.value && !userStore.mustChangePassword && !userStore.isPureOps) enter()
})

// 照片是异步取回来的:进屏保时先起来的那份可能还是空数组,数据到货后补上轮播
watch(photos, () => {
  if (visible.value && showCards.value) syncPhotoTimer()
})
watch(
  () => prefs.cards.photos,
  () => {
    if (visible.value && showCards.value) syncPhotoTimer()
  },
)

// 设置页改了时长/背景/卡片:重读偏好,useIdle 会按时长变化重新武装计时
const resetDisplay = () => {
  loadSequence++
  cache.at = 0
  photos.value = []
  events.value = []
  scheduleItems.value = []
  familyName.value = ''
  hide()
  poke()
  syncPrefs()
}
watch([prefsTick], resetDisplay, { flush: 'sync' })

// 关闭条件:离开普通页面、登出、进通话、切儿童模式
watch(
  () => route.fullPath,
  () => {
    if (visible.value) hide()
    else syncPrefs()
  },
)
watch(
  () => userStore.isLoggedIn,
  (v) => {
    if (!v) hide()
    else syncPrefs()
  },
)
watch(
  () => callStore.inCall,
  (v) => {
    if (v) hide()
  },
)
watch(isChild, (v) => {
  if (v) hide()
})
watch(
  () => sunLight?.suspended.value,
  (paused) => {
    if (paused) hide()
  },
)

// 天气到货后补取天气图(只在「天气图」背景时)
watch(
  () => weather.value,
  (w) => {
    if (w && prefs.bg === BG.WEATHER) loadWeatherBg(w)
  },
  { immediate: true },
)

// 回到前台重新读偏好(设置页刚改的时长/背景立即生效);切后台时屏保本身也该收起
const onVisibility = () => {
  if (document.visibilityState === 'visible') {
    syncPrefs()
    if (visible.value && prefs.bg === BG.DEFAULT && !waking.value) displayPhase()
    return
  }
  if (visible.value && prefs.bg === BG.DEFAULT && !waking.value) {
    clearTimeout(phaseTimer)
    loadSequence++
    stopTimers()
    return
  }
  hide()
}

const onWakeKey = (event) => {
  if (!visible.value && event.code !== keyToRelease) return
  event.preventDefault()
  event.stopImmediatePropagation()
  if (event.type === 'keyup') keyToRelease = null
  else {
    keyToRelease = event.code
    wake()
  }
}
if (typeof document !== 'undefined') {
  document.addEventListener('visibilitychange', onVisibility)
  window.addEventListener('keydown', onWakeKey, true)
  window.addEventListener('keyup', onWakeKey, true)
}

onBeforeUnmount(() => {
  hide()
  if (sunLight) sunLight.seatLighting.value = false
  stopTimers()
  if (typeof document !== 'undefined') document.removeEventListener('visibilitychange', onVisibility)
  window.removeEventListener('keydown', onWakeKey, true)
  window.removeEventListener('keyup', onWakeKey, true)
})
</script>

<style scoped>
.ss {
  --ss-x: 0px;
  --ss-y: 0px;
  position: fixed;
  /* 构建目标 safari14 不支持 inset 简写 */
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: var(--z-immersive);
  overflow: hidden;
  touch-action: none;
  overscroll-behavior: contain;
  user-select: none;
  -webkit-user-select: none;
  /* 屏保自带深色底:纯黑即整屏留黑;图片背景也压在这层之上,不随站点晨暮变化 */
  background: #000;
  color: #fff;
  cursor: none;
}

/* 背景图:自身浓度由用户设置(天气图固定满浓度),压暗层只负责文字可读性 */
.ss-dim {
  /* Cover stationary navigation and text while the original window and weather remain above. */
  background: linear-gradient(rgba(0, 0, 0, 0.36), rgba(0, 0, 0, 0.36)), var(--color-bg);
}
.ss-cover {
  z-index: var(--z-topmost);
}
.ss-cover:focus {
  outline: none;
}
.ss-waking {
  opacity: 0;
  transition: opacity 2.4s ease-in-out;
}
.ss-bg {
  position: absolute;
  background-position: center;
}
.ss-bg,
.ss .ss-weather-art {
  top: -10px;
  right: -10px;
  bottom: -10px;
  left: -10px;
}
.ss-stage,
.ss-hint,
.ss-bg,
.ss-weather-art {
  transform: translate(var(--ss-x), var(--ss-y));
  transition: transform 2s ease;
}
.ss-scrim {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  background: linear-gradient(to top, rgba(0, 0, 0, 0.68) 0%, rgba(0, 0, 0, 0.16) 46%, rgba(0, 0, 0, 0.44) 100%);
}
/* 自己的照片是屏保主角:只压底部一条给唤醒提示,上方原图不动(卡片自带深色底、文字靠阴影立住),
   别用整屏压暗把照片糊掉 */
.ss-scrim-photo {
  background: linear-gradient(to top, rgba(0, 0, 0, 0.52) 0%, rgba(0, 0, 0, 0.06) 30%, rgba(0, 0, 0, 0) 52%);
}

.ss-stage {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  display: flex;
  flex-direction: column;
  gap: clamp(12px, 2vh, 26px);
  padding: clamp(20px, 4vw, 56px);
}

.ss-head {
  flex: 0 0 auto;
  /* 照片背景不整屏压暗,白字靠自身阴影立住 */
  text-shadow:
    0 2px 18px rgba(0, 0, 0, 0.6),
    0 1px 3px rgba(0, 0, 0, 0.45);
}
.ss-clock {
  font-family: inherit;
  font-size: clamp(56px, 12vw, 168px);
  font-weight: 200;
  line-height: 0.95;
  letter-spacing: 0.02em;
  font-variant-numeric: tabular-nums;
}
.ss-meta {
  margin-top: 8px;
  font-size: clamp(14px, 1.8vw, 24px);
  opacity: 0.82;
}
.ss-weather {
  margin-top: 8px;
  display: flex;
  align-items: baseline;
  gap: 10px;
  font-size: clamp(13px, 1.4vw, 20px);
  opacity: 0.82;
}
.ss-temp {
  font-size: clamp(22px, 2.4vw, 40px);
  font-weight: 600;
  opacity: 1;
}
/* 天气预警条:左色条沿用预警自身色,两行文字不抢视线 */
.ss-warn {
  --warn-color: var(--color-warning);
  margin-top: 10px;
  display: flex;
  align-items: baseline;
  gap: 10px;
  width: max-content;
  max-width: 100%;
  padding: 6px 12px;
  border-left: 4px solid var(--warn-color, var(--color-warning));
  border-radius: 10px;
  background: rgba(0, 0, 0, 0.36);
  font-size: clamp(12px, 1.3vw, 17px);
  overflow: hidden;
}
.ss-warn-type {
  flex: 0 0 auto;
  font-weight: 700;
  color: var(--warn-color, var(--color-warning));
  white-space: nowrap;
}
.ss-warn-title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  opacity: 0.9;
}

.ss-cards {
  flex: 1 1 auto;
  min-height: 0;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  grid-auto-rows: minmax(0, auto);
  gap: clamp(10px, 1.4vw, 18px);
  align-content: start;
  overflow: hidden;
}
/* 时间天气关掉时卡片从顶部铺开,不留一整块空白 */
.ss-cards-single {
  flex: 0 0 auto;
}
.ss-card {
  background: rgba(0, 0, 0, 0.42);
  border: 1px solid rgba(255, 255, 255, 0.14);
  border-radius: 16px;
  padding: 12px 14px;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.ss-card-title {
  flex: 0 0 auto;
  font-size: 12px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.6);
  margin-bottom: 6px;
}
.ss-list {
  list-style: none;
  margin: 0;
  padding: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.ss-row {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 7px 0;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  font-size: clamp(13px, 1.4vw, 17px);
  min-width: 0;
}
.ss-row:last-child {
  border-bottom: none;
}
.ss-time {
  flex: 0 0 auto;
  opacity: 0.66;
  font-variant-numeric: tabular-nums;
}
.ss-title {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ss-source {
  flex: 0 0 auto;
  font-size: 11.5px;
  opacity: 0.5;
}
.ss-card-photo {
  padding: 10px;
  min-height: 180px;
  position: relative;
}
.ss-card-photo img {
  width: 100%;
  flex: 1 1 auto;
  min-height: 0;
  object-fit: cover;
  border-radius: 10px;
  display: block;
}
.ss-fade-enter-active,
.ss-fade-leave-active {
  transition: opacity 1.4s ease;
}
.ss-fade-enter-from,
.ss-fade-leave-to {
  opacity: 0;
}
.ss-fade-leave-active {
  position: absolute;
  top: 10px;
  right: 10px;
  bottom: 10px;
  left: 10px;
}

/* 唤醒提示:不抢注意力,淡在底部 */
.ss-hint {
  position: absolute;
  left: 0;
  right: 0;
  bottom: calc(14px + env(safe-area-inset-bottom, 0px));
  text-align: center;
  font-size: 12px;
  opacity: 0.5;
  pointer-events: none;
  color: #fff;
  text-shadow: 0 1px 6px rgba(0, 0, 0, 0.7);
}

/* 手机竖屏:卡片单列、时钟略小,避免和卡片挤在一屏 */
@media (max-width: 760px) {
  .ss-cards {
    grid-template-columns: 1fr;
  }
  .ss-clock {
    font-size: clamp(48px, 16vw, 96px);
  }
}
/* 手机横屏/矮屏:时钟再压一档,卡片保持两列 */
@media (orientation: landscape) and (max-height: 520px) {
  .ss-stage {
    padding: clamp(12px, 2vw, 24px);
    gap: 8px;
  }
  .ss-clock {
    font-size: clamp(36px, 8vw, 88px);
  }
  .ss-cards {
    grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  }
}
@media (prefers-reduced-motion: reduce) {
  /* Keep the luminance ramp even when movement is disabled. */
  .ss-waking {
    transition-duration: 2.4s !important;
  }
  .ss-stage,
  .ss-hint,
  .ss-bg,
  .ss-weather-art,
  .ss-fade-enter-active,
  .ss-fade-leave-active {
    transition: none;
  }
}
</style>
