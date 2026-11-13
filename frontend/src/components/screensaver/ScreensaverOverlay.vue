<!-- 手机端空闲屏保:长时间不动后盖一层全屏页,两种模式(设置里可切、看板卡片可勾选)
       - 背景模式:全屏家庭照片轮播 + 时钟/日期/星期/家庭名(有天气时带一行小字)
       - 看板模式:只读信息板,卡片按时钟天气/今日待办/未来日程/纪念日/照片勾选渲染
     交互口径:轻点任意处唤醒,这一下只用于关屏保——根节点吞掉 click/pointerdown,
       背后页面的按钮与链接收不到这次事件(不穿透);滚动/右键/长按菜单同样拦下。
     只读:屏保内不发任何写请求(不做待办勾选),避免误触改数据。 -->
<template>
  <Teleport to="body">
    <div
      v-if="visible"
      v-a11y-click
      class="ss"
      :class="isBoard ? 'ss-board' : 'ss-photo-mode'"
      role="dialog"
      aria-modal="true"
      :aria-label="$t('screensaver.overlayLabel')"
      @click.stop="wake"
      @pointerdown.stop
      @keydown.stop="wake"
      @wheel.prevent
      @touchmove.prevent
      @contextmenu.prevent
    >
      <!-- 背景模式 -->
      <template v-if="!isBoard">
        <div class="ss-photo" aria-hidden="true">
          <transition name="ss-fade">
            <img v-if="currentPhoto" :key="currentPhoto.id || currentPhoto.url" :src="currentPhoto.url" alt="" />
          </transition>
        </div>
        <div class="ss-photo-scrim" aria-hidden="true"></div>
        <div class="ss-photo-info">
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
        </div>
      </template>

      <!-- 看板模式 -->
      <template v-else>
        <div class="ss-board-head">
          <template v-if="cardOn('clock')">
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
          </template>
        </div>

        <div class="ss-cards">
          <section v-if="cardOn('todo') && todayTodos.length" class="ss-card">
            <div class="ss-card-title">{{ $t('screensaver.todayTodo') }}</div>
            <ul class="ss-list">
              <li v-for="item in todayTodos.slice(0, 5)" :key="item.key" class="ss-row">
                <span class="ss-time">{{ item.timeLabel }}</span>
                <span class="ss-title">{{ item.title }}</span>
                <span class="ss-source">{{ item.sourceLabel }}</span>
              </li>
            </ul>
          </section>

          <section v-if="cardOn('schedule') && agenda.length" class="ss-card">
            <div class="ss-card-title">{{ $t('screensaver.upcoming') }}</div>
            <ul class="ss-list">
              <li v-for="item in agenda.slice(0, 6)" :key="item.key" class="ss-row">
                <span class="ss-time">{{ item.dayLabel }}</span>
                <span class="ss-title">{{ item.title }}</span>
                <span class="ss-source">{{ item.sourceLabel }}</span>
              </li>
            </ul>
          </section>

          <section v-if="cardOn('anni') && events.length" class="ss-card">
            <div class="ss-card-title">{{ $t('screensaver.anniversary') }}</div>
            <ul class="ss-list">
              <li v-for="(ev, i) in events.slice(0, 4)" :key="`${ev.label}-${i}`" class="ss-row">
                <span class="ss-time">{{ ev.days === 0 ? $t('screensaver.today') : ev.date }}</span>
                <span class="ss-title">{{ ev.label }}</span>
                <span v-if="ev.days !== 0" class="ss-source">{{ $t('screensaver.daysLeft', { n: ev.days }) }}</span>
              </li>
            </ul>
          </section>

          <section v-if="cardOn('photos') && currentPhoto" class="ss-card ss-card-photo">
            <div class="ss-card-title">{{ $t('screensaver.photos') }}</div>
            <transition name="ss-fade">
              <img :key="currentPhoto.id || currentPhoto.url" :src="currentPhoto.url" alt="" />
            </transition>
          </section>
        </div>
      </template>

      <div class="ss-hint">{{ $t('screensaver.exitHint') }}</div>
    </div>
  </Teleport>
</template>

<script setup>
import { computed, inject, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/user'
import { useCallStore } from '@/stores/call'
import { useDisplayMode } from '@/composables/useDisplayMode'
import { useIdle } from '@/composables/useIdle'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'
import { publicApi, scheduleApi } from '@/api'
import { isBusyScreen, loadPrefs, MODES, prefsTick, previewTick, shouldSuppress } from '@/utils/screensaver'

const { t } = useI18n()
const route = useRoute()
const userStore = useUserStore()
const callStore = useCallStore()
const { isChild } = useDisplayMode()
const sunLight = inject(SUN_LIGHT_KEY, null)
const weather = computed(() => sunLight?.weather?.value || null)

const PHOTO_INTERVAL_MS = 8000
const REFRESH_MS = 600000
const DATA_TTL_MS = 300000

const prefs = reactive(loadPrefs())
const visible = ref(false)
const clock = ref({ time: '', date: '', week: '' })
const todayStr = ref('')
const familyName = ref('')
const photos = ref([])
const photoIndex = ref(0)
const events = ref([])
const scheduleItems = ref([])

const isBoard = computed(() => prefs.mode === MODES.BOARD)
const cardOn = (id) => prefs.board[id] !== false

// 取数缓存:屏保反复进出不重复打接口(5 分钟内拿缓存)
const cache = { at: 0, photos: [], familyName: '', events: [], schedule: [] }
let clockTimer = 0
let photoTimer = 0
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
  date: it.date,
  dayLabel: dayLabel(it.date),
  timeLabel: it.time ? String(it.time).slice(0, 5) : t('screensaver.allDay'),
  title: it.title || '',
  sourceLabel: t(`kiosk.kind.${it.kind || 'REMINDER'}`),
})

const mappedItems = computed(() => scheduleItems.value.map(toAgendaItem).filter((it) => it.title))
const todayTodos = computed(() => mappedItems.value.filter((it) => it.date && it.date === todayStr.value))
const agenda = computed(() => mappedItems.value.filter((it) => it.date !== todayStr.value))

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
  // 两个接口互不依赖:并行;任一失败不拖垮整个屏保(照片/日程各自留空)
  const [home, upcoming] = await Promise.all([publicApi.getHome().catch(() => null), scheduleApi.upcoming(7).catch(() => null)])
  const list = Array.isArray(upcoming) ? upcoming : Array.isArray(upcoming?.items) ? upcoming.items : []
  cache.photos = Array.isArray(home?.photos) ? home.photos.filter((p) => p && p.url) : []
  cache.familyName = home?.family?.name || ''
  cache.events = Array.isArray(home?.stats?.upcomingEvents) ? home.stats.upcomingEvents : []
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

const stopTimers = () => {
  if (clockTimer) clearInterval(clockTimer)
  if (refreshTimer) clearInterval(refreshTimer)
  clockTimer = 0
  refreshTimer = 0
  stopPhoto()
}

const syncPrefs = () => Object.assign(prefs, loadPrefs())

// 进入屏保:时钟/照片轮播/数据刷新一起起来,退出时统一 stopTimers
const enter = () => {
  stopTimers()
  visible.value = true
  syncClock()
  clockTimer = setInterval(syncClock, 1000)
  syncPhotoTimer()
  loadData()
  refreshTimer = setInterval(() => loadData(true), REFRESH_MS)
}

const show = () => {
  syncPrefs()
  if (!prefs.enabled || shouldSuppress(context())) return
  enter()
}

const hide = () => {
  if (!visible.value) return
  visible.value = false
  stopTimers()
}

// 轻点任意处唤醒:该次 click 已被根节点吞下,背后页面收不到
const wake = () => {
  if (!visible.value) return
  hide()
  poke()
}

const context = () => ({
  isLoggedIn: userStore.isLoggedIn,
  isChild: isChild.value,
  inCall: callStore.inCall,
  routeMeta: route.meta,
  busy: isBusyScreen(),
  hidden: typeof document !== 'undefined' && document.visibilityState !== 'visible',
})

const { isIdle, poke } = useIdle({
  idleMs: () => (prefs.enabled ? prefs.idleMinutes * 60000 : 0),
  canTrigger: () => !shouldSuppress(context()),
})

// 照片面板只在看板模式勾了照片、或背景模式下才需要轮播
const syncPhotoTimer = () => {
  if (isBoard.value && !cardOn('photos')) stopPhoto()
  else startPhoto()
}
watch([isBoard, () => prefs.board.photos], () => {
  if (visible.value) syncPhotoTimer()
})
// 照片是异步取回来的:进屏保时先起来的那份可能还是空数组,数据到货后补上轮播
watch(photos, () => {
  if (visible.value) syncPhotoTimer()
})

watch(isIdle, (idle) => {
  if (idle && !visible.value) show()
})

// 设置页「预览」:立即展示(会照常吞点击,点一下即退出)
watch(previewTick, () => {
  syncPrefs()
  if (!visible.value) enter()
})

// 设置页改了时长/模式/卡片:重读偏好,useIdle 会按时长变化重新武装计时
watch(prefsTick, () => syncPrefs())

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

// 回到前台重新读偏好(设置页刚改的时长/模式立即生效);切后台时屏保本身也该收起
const onVisibility = () => {
  if (document.visibilityState === 'visible') {
    syncPrefs()
    return
  }
  hide()
}

if (typeof document !== 'undefined') document.addEventListener('visibilitychange', onVisibility)

onBeforeUnmount(() => {
  stopTimers()
  if (typeof document !== 'undefined') document.removeEventListener('visibilitychange', onVisibility)
})
</script>

<style scoped>
.ss {
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
  cursor: default;
}

/* 长开页不用 backdrop-filter(前端规范 7):背景模式压暗照片,看板模式走主题底色 */
.ss-photo-mode {
  background: var(--overlay-scrim-strong);
  color: #fff;
}

.ss-board {
  background: linear-gradient(160deg, var(--color-bg) 0%, var(--color-bg-2) 100%);
  color: var(--color-text);
  padding: clamp(18px, 4vw, 32px) clamp(16px, 4vw, 28px) calc(clamp(18px, 4vw, 32px) + env(safe-area-inset-bottom, 0px));
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

/* ===== 背景模式 ===== */
.ss-photo {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  /* 无照片时的兜底:主题渐变,不留白也不留纯黑 */
  background: linear-gradient(160deg, var(--color-bg) 0%, var(--color-bg-2) 100%);
}
.ss-photo img {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
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
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
}
.ss-photo-scrim {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  background: linear-gradient(to top, rgba(0, 0, 0, 0.62) 0%, rgba(0, 0, 0, 0.12) 45%, rgba(0, 0, 0, 0.3) 100%);
}
.ss-photo-info {
  position: absolute;
  left: clamp(20px, 6vw, 40px);
  right: clamp(20px, 6vw, 40px);
  bottom: calc(clamp(48px, 12vh, 96px) + env(safe-area-inset-bottom, 0px));
}

/* ===== 共用排版 ===== */
.ss-clock {
  font-family: inherit;
  font-size: clamp(58px, 18vw, 132px);
  font-weight: 200;
  line-height: 0.95;
  letter-spacing: 0.02em;
  font-variant-numeric: tabular-nums;
}
.ss-meta {
  margin-top: 8px;
  font-size: clamp(14px, 4vw, 20px);
  opacity: 0.82;
}
.ss-weather {
  margin-top: 8px;
  display: flex;
  align-items: baseline;
  gap: 10px;
  font-size: clamp(13px, 3.6vw, 18px);
  opacity: 0.82;
}
.ss-temp {
  font-size: clamp(22px, 6vw, 34px);
  font-weight: 600;
  opacity: 1;
}

/* ===== 看板模式 ===== */
.ss-board-head {
  flex: 0 0 auto;
  min-height: 0;
}
.ss-board-head .ss-clock {
  font-size: clamp(48px, 14vw, 96px);
}
.ss-board-head .ss-meta,
.ss-board-head .ss-weather {
  color: var(--color-text-secondary);
  opacity: 1;
}
.ss-board-head .ss-temp {
  color: var(--color-text);
}

.ss-cards {
  flex: 1 1 auto;
  min-height: 0;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  grid-auto-rows: minmax(0, auto);
  gap: 10px;
  overflow: hidden;
}
.ss-card {
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  padding: 10px 12px;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.ss-card-title {
  flex: 0 0 auto;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-secondary);
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
  padding: 6px 0;
  border-bottom: 1px solid rgba(var(--color-brand-rgb), 0.12);
  font-size: clamp(12.5px, 3.4vw, 15px);
  min-width: 0;
}
.ss-row:last-child {
  border-bottom: none;
}
.ss-time {
  flex: 0 0 auto;
  color: var(--color-text-secondary);
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
  color: var(--color-text-tertiary);
}
.ss-card-photo {
  padding: 10px;
  min-height: 150px;
  position: relative;
}
.ss-card-photo img {
  width: 100%;
  flex: 1 1 auto;
  min-height: 0;
  object-fit: cover;
  border-radius: calc(var(--radius) - 4px);
  display: block;
}

/* 唤醒提示:不抢注意力,淡在底部 */
.ss-hint {
  position: absolute;
  left: 0;
  right: 0;
  bottom: calc(14px + env(safe-area-inset-bottom, 0px));
  text-align: center;
  font-size: 12px;
  opacity: 0.55;
  pointer-events: none;
}
.ss-photo-mode .ss-hint {
  color: #fff;
}
.ss-board .ss-hint {
  color: var(--color-text-secondary);
}
</style>
