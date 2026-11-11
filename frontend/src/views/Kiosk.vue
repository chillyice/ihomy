<!-- 看板 / 相框(独立壳内页 /kiosk):横屏平板常亮显示时钟、天气、未来 7 天日程与家庭照片轮播。
     退出方式是右下角隐形热区长按 3 秒(有指纹先验指纹),防止客人随手点走。 -->
<template>
  <div class="kiosk" :style="{ '--shift-x': shift.x + 'px', '--shift-y': shift.y + 'px' }">
    <SunLightLayer />

    <div v-if="!userStore.isLoggedIn" class="kiosk-empty">
      <div class="empty-title">{{ $t('kiosk.loginTitle') }}</div>
      <div class="empty-tip">{{ $t('kiosk.loginTip') }}</div>
      <el-button type="primary" round @click="goLogin">{{ $t('kiosk.loginAction') }}</el-button>
    </div>

    <div v-else class="kiosk-stage">
      <header class="kiosk-head">
        <div class="kiosk-clock">{{ clock.time }}</div>
        <div class="kiosk-date">
          {{ clock.date }} · {{ clock.week }}
          <span v-if="familyName"> · {{ familyName }}</span>
        </div>
        <div v-if="weather" class="kiosk-weather">
          <span class="w-temp">{{ Math.round(Number(weather.temp) || 0) }}°</span>
          <span class="w-text">{{ weather.text }}</span>
          <span v-if="weather.city" class="w-city">{{ weather.city }}</span>
        </div>
      </header>

      <!-- 天气预警:和风预警非空时贴一条(取最严重/最新的一条,颜色沿用预警自身色值) -->
      <div v-if="topWarning" class="kiosk-warning" :style="{ '--warn-color': topWarning.severityColor || 'var(--color-warning)' }">
        <span class="kw-type">{{ topWarning.typeName || topWarning.title }}</span>
        <span class="kw-title">{{ topWarning.title || topWarning.typeName }}</span>
      </div>

      <main class="kiosk-main">
        <section class="kiosk-schedule">
          <div class="section-label">{{ $t('kiosk.todayTodo') }}</div>
          <ul v-if="todayTodos.length" class="kiosk-list">
            <li
              v-for="item in todayTodos"
              :key="item.key"
              class="kiosk-row todo-row"
              :class="{ done: item.done, actionable: item.checkable }"
            >
              <button
                v-if="item.checkable"
                type="button"
                class="todo-check"
                :aria-label="$t('kiosk.toggleDone')"
                :aria-pressed="item.done"
                @click="toggleItem(item)"
              >
                <span v-if="item.done" aria-hidden="true">✓</span>
              </button>
              <span v-else class="todo-dot" aria-hidden="true"></span>
              <span class="row-time">{{ item.timeLabel }}</span>
              <span class="row-title">{{ item.title }}</span>
              <span class="row-source">{{ item.sourceLabel }}</span>
            </li>
          </ul>
          <div v-else class="kiosk-muted">{{ $t('kiosk.noTodo') }}</div>

          <div class="section-label kiosk-upcoming-label">{{ $t('kiosk.upcoming') }}</div>
          <ul v-if="agenda.length" class="kiosk-list">
            <li v-for="item in agenda" :key="item.key" class="kiosk-row">
              <span class="row-day">{{ item.dayLabel }}</span>
              <span class="row-time">{{ item.timeLabel }}</span>
              <span class="row-title">{{ item.title }}</span>
              <span class="row-source">{{ item.sourceLabel }}</span>
            </li>
          </ul>
          <div v-else class="kiosk-muted">{{ $t('kiosk.noSchedule') }}</div>
        </section>

        <section class="kiosk-photo">
          <transition name="kiosk-fade">
            <img v-if="currentPhoto" :key="currentPhoto.id || currentPhoto.url" :src="currentPhoto.url" alt="" />
          </transition>
          <div v-if="!currentPhoto" class="kiosk-muted">{{ $t('kiosk.noPhoto') }}</div>
        </section>
      </main>

      <div class="kiosk-foot">
        <span class="kiosk-muted">{{ $t('kiosk.footHint') }}</span>
      </div>
    </div>

    <!-- 隐形退出热区:长按 3 秒(不显示边界,按住时给一句提示) -->
    <div
      class="kiosk-exit-zone"
      @pointerdown="onExitDown"
      @pointerup="onExitUp"
      @pointercancel="onExitUp"
      @pointerleave="onExitUp"
      @contextmenu.prevent
    >
      <span v-if="holding" class="exit-hint">{{ $t('kiosk.exitHoldHint') }}</span>
    </div>
  </div>
</template>

<script setup>
import { computed, inject, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import SunLightLayer from '@/components/SunLightLayer.vue'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'
import { useUserStore } from '@/stores/user'
import { planApi, publicApi, reminderApi, scheduleApi } from '@/api'
import { isNativeApp } from '@/utils/platform'

const { t } = useI18n()
const router = useRouter()
const userStore = useUserStore()
const sunLight = inject(SUN_LIGHT_KEY, null)
const weather = computed(() => sunLight?.weather?.value || null)
const weatherDetail = computed(() => sunLight?.weatherDetail?.value || null)
// 预警条只取第一条(和风按严重度返回);无预警时整条不渲染
const topWarning = computed(() => {
  const list = weatherDetail.value?.warning
  return Array.isArray(list) && list.length ? list[0] : null
})

// 原生能力一律动态引入(首屏零 Capacitor 运行时)
const screenNative = () => import('@/native/screen')
const biometricNative = () => import('@/native/biometric')

const KIOSK_CLASS = 'kiosk-page'
const PHOTO_INTERVAL_MS = 20000
const SHIFT_INTERVAL_MS = 60000
const REFRESH_MS = 600000
const EXIT_HOLD_MS = 3000

const clock = ref({ time: '', date: '', week: '' })
// 今日日期串(本地时区),syncClock 每秒对表时顺手更新:勾选后按它分「今日待办 / 未来几天」
const todayStr = ref('')
const familyName = ref('')
const photos = ref([])
const photoIndex = ref(0)
const scheduleItems = ref([])
const shift = ref({ x: 8, y: 6 })

const currentPhoto = computed(() => photos.value[photoIndex.value] || null)

let clockTimer = 0
let photoTimer = 0
let shiftTimer = 0
let refreshTimer = 0
let shiftFlip = false
let exitTimer = 0
const holding = ref(false)

const pad = (n) => String(n).padStart(2, '0')
const localDateStr = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`

// 每秒对表,只在显示串变化时写 ref(与壁纸页同口径)
const syncClock = () => {
  const d = new Date()
  const time = `${pad(d.getHours())}:${pad(d.getMinutes())}`
  const date = t('kiosk.dateFmt', { m: d.getMonth() + 1, d: d.getDate() })
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
  if (diff === 0) return t('kiosk.dayToday')
  if (diff === 1) return t('kiosk.dayTomorrow')
  return t('kiosk.dayFmt', { m: parts[1], d: parts[2] })
}

const sourceLabel = (kind) => {
  const key = `kiosk.kind.${kind || 'REMINDER'}`
  return t(key)
}

// 可勾选的来源:提醒(含循环)与计划子任务;纪念日/还款日/理财到期只是展示
const CHECKABLE_KINDS = ['REMINDER', 'TASK', 'PLAN_TASK']

const toAgendaItem = (it) => ({
  key: it.key || `${it.kind}:${it.id}:${it.date}`,
  kind: it.kind,
  id: it.id,
  date: it.date,
  done: !!it.done,
  checkable: CHECKABLE_KINDS.includes(it.kind),
  dayLabel: dayLabel(it.date),
  timeLabel: it.time ? String(it.time).slice(0, 5) : t('kiosk.allDay'),
  title: it.title || '',
  sourceLabel: sourceLabel(it.kind),
})

const mappedItems = computed(() => scheduleItems.value.map(toAgendaItem).filter((it) => it.title))

// 今日待办:今天是主角,可勾选的带圆点方框;完成后行变淡(不隐藏,免得客人以为事项消失)
const todayTodos = computed(() => mappedItems.value.filter((it) => it.date && it.date === todayStr.value))
// 未来几天:不再重复今天的条目;逾期未完成的仍留在列表里(dayLabel 会显示具体日期)
const agenda = computed(() => mappedItems.value.filter((it) => it.date !== todayStr.value))

const togglingKey = ref('')
const toggleItem = async (item) => {
  if (!item.checkable || togglingKey.value) return
  togglingKey.value = item.key
  try {
    if (item.kind === 'PLAN_TASK') await planApi.updateTask(item.id, { done: item.done ? 0 : 1 })
    else await reminderApi.toggleDone(item.id, item.date)
    await loadSchedule()
  } catch (e) {
    ElMessage.error(t('kiosk.toggleFailed'))
  } finally {
    togglingKey.value = ''
  }
}

const loadHome = async () => {
  if (!userStore.isLoggedIn) return
  try {
    const data = await publicApi.getHome()
    const list = Array.isArray(data?.photos) ? data.photos : []
    photos.value = list.filter((p) => p && p.url)
    if (photoIndex.value >= photos.value.length) photoIndex.value = 0
    if (data?.family?.name) familyName.value = data.family.name
  } catch (e) {
    // 看板不弹错误:网络抖动时保持上一轮内容
  }
}

const loadSchedule = async () => {
  if (!userStore.isLoggedIn) return
  try {
    const data = await scheduleApi.upcoming(7)
    const items = Array.isArray(data?.items) ? data.items : Array.isArray(data) ? data : []
    scheduleItems.value = items.slice(0, 10)
  } catch (e) {
    // 同上:静默保留旧列表
  }
}

const refreshAll = async () => {
  await Promise.all([loadHome(), loadSchedule()])
}

const goLogin = () => router.push('/login')

// ---- 退出:长按 3 秒 → 指纹(无指纹/网页降级为确认框) ----
const onExitDown = (e) => {
  if (e.pointerType === 'mouse' && e.button !== 0) return
  holding.value = true
  clearTimeout(exitTimer)
  exitTimer = setTimeout(async () => {
    holding.value = false
    if (await confirmExit()) exitKiosk()
  }, EXIT_HOLD_MS)
}

const onExitUp = () => {
  clearTimeout(exitTimer)
  exitTimer = 0
  holding.value = false
}

const confirmExit = async () => {
  if (isNativeApp()) {
    try {
      const bio = await biometricNative()
      const status = await bio.biometricStatus()
      if (status && status.available && status.enrolled) {
        return await bio.verifyWithPrompt(t('kiosk.exitTitle'), t('kiosk.exitTip'))
      }
    } catch (e) {
      // 指纹不可用:落到下面的确认框
    }
  }
  try {
    await ElMessageBox.confirm(t('kiosk.exitConfirm'), t('kiosk.exitTitle'), {
      type: 'warning',
      confirmButtonText: t('kiosk.exitOk'),
      cancelButtonText: t('common.cancel'),
    })
    return true
  } catch (e) {
    return false
  }
}

const exitKiosk = () => {
  screenNative()
    .then((m) => m.setKeepOn(false))
    .catch(() => {})
  router.replace('/')
}

onMounted(async () => {
  document.documentElement.classList.add(KIOSK_CLASS)
  syncClock()
  clockTimer = setInterval(syncClock, 1000)
  // 照片 20 秒换一张,淡入淡出交给 CSS transition(不用 GSAP 持续动画)
  photoTimer = setInterval(() => {
    const n = photos.value.length
    photoIndex.value = n ? (photoIndex.value + 1) % n : 0
  }, PHOTO_INTERVAL_MS)
  // 防烧屏:整块内容每 60 秒在 ±8px 之间挪一次
  shiftTimer = setInterval(() => {
    shiftFlip = !shiftFlip
    shift.value = shiftFlip ? { x: -8, y: -6 } : { x: 8, y: 6 }
  }, SHIFT_INTERVAL_MS)
  await refreshAll()
  refreshTimer = setInterval(refreshAll, REFRESH_MS)
  // 常亮:壳内是 FLAG_KEEP_SCREEN_ON,网页端是 no-op
  screenNative()
    .then((m) => m.setKeepOn(true))
    .catch(() => {})
})

onBeforeUnmount(() => {
  document.documentElement.classList.remove(KIOSK_CLASS)
  clearInterval(clockTimer)
  clearInterval(photoTimer)
  clearInterval(shiftTimer)
  clearInterval(refreshTimer)
  clearTimeout(exitTimer)
  clockTimer = 0
  photoTimer = 0
  shiftTimer = 0
  refreshTimer = 0
  exitTimer = 0
  screenNative()
    .then((m) => m.setKeepOn(false))
    .catch(() => {})
})
</script>

<style scoped>
.kiosk {
  /* 防烧屏位移量:组件里按 60 秒节律改这两个变量 */
  --shift-x: 0px;
  --shift-y: 0px;
  position: fixed;
  inset: 0;
  z-index: var(--z-content);
  overflow: hidden;
  /* 长开页不用 backdrop-filter(AGENTS 前端规范 7):纯色 + 主题变量 */
  background: linear-gradient(160deg, var(--color-bg) 0%, var(--color-bg-2) 100%);
  color: var(--color-text);
  transition: transform 2s ease;
  transform: translate(var(--shift-x, 0), var(--shift-y, 0));
}

.kiosk-empty {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  padding: 24px;
  text-align: center;
}
.empty-title {
  font-size: 30px;
  font-weight: 700;
}
.empty-tip {
  max-width: 520px;
  color: var(--color-text-secondary);
  line-height: 1.7;
}

.kiosk-stage {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  padding: clamp(20px, 4vw, 56px);
  gap: clamp(12px, 2vh, 26px);
}

.kiosk-head {
  flex: 0 0 auto;
}
.kiosk-clock {
  font-family: inherit;
  font-size: clamp(64px, 12vw, 168px);
  font-weight: 200;
  line-height: 0.95;
  letter-spacing: 0.02em;
  font-variant-numeric: tabular-nums;
}
.kiosk-date {
  margin-top: 8px;
  font-size: clamp(16px, 1.8vw, 26px);
  color: var(--color-text-secondary);
}
.kiosk-weather {
  margin-top: 10px;
  display: flex;
  align-items: baseline;
  gap: 12px;
  color: var(--color-text-secondary);
}
.w-temp {
  font-size: clamp(28px, 3vw, 44px);
  font-weight: 600;
  color: var(--color-text);
}
.w-text,
.w-city {
  font-size: clamp(14px, 1.5vw, 22px);
}

.kiosk-main {
  flex: 1 1 auto;
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(280px, 1fr) minmax(0, 1.5fr);
  gap: clamp(16px, 3vw, 40px);
}

.kiosk-schedule {
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.kiosk-list {
  list-style: none;
  margin: 0;
  padding: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.kiosk-row {
  display: grid;
  grid-template-columns: 4.2em 3.4em minmax(0, 1fr) auto;
  align-items: baseline;
  gap: 10px;
  padding: 10px 4px;
  border-bottom: 1px solid rgba(var(--color-brand-rgb), 0.12);
  font-size: clamp(14px, 1.4vw, 21px);
}
.row-day,
.row-time {
  color: var(--color-text-secondary);
  font-variant-numeric: tabular-nums;
}
.row-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.row-source {
  color: var(--color-text-tertiary);
  font-size: 0.78em;
  white-space: nowrap;
}

.kiosk-photo {
  position: relative;
  min-height: 0;
  border-radius: 18px;
  overflow: hidden;
  background: rgba(var(--color-brand-rgb), 0.08);
  display: flex;
  align-items: center;
  justify-content: center;
}
.kiosk-photo img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.kiosk-fade-enter-active,
.kiosk-fade-leave-active {
  transition: opacity 1.6s ease;
}
.kiosk-fade-enter-from,
.kiosk-fade-leave-to {
  opacity: 0;
}
.kiosk-fade-leave-active {
  position: absolute;
  inset: 0;
}

.kiosk-foot {
  flex: 0 0 auto;
  text-align: right;
}
.kiosk-muted {
  color: var(--color-text-tertiary);
  font-size: clamp(12px, 1.1vw, 16px);
}

/* 天气预警条:左侧色条用预警自带色,文字只用克制的两行 */
.kiosk-warning {
  --warn-color: var(--color-warning);
  flex: 0 0 auto;
  display: flex;
  align-items: baseline;
  gap: 10px;
  padding: 8px 14px;
  border-left: 5px solid var(--warn-color, var(--color-warning));
  background: rgba(var(--color-brand-rgb), 0.1);
  border-radius: 10px;
  font-size: clamp(13px, 1.2vw, 18px);
}
.kw-type {
  font-weight: 700;
  color: var(--warn-color, var(--color-warning));
  white-space: nowrap;
}
.kw-title {
  color: var(--color-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 今日待办:勾选圆点比文字稍大,触屏好点按 */
.kiosk-upcoming-label {
  margin-top: 12px;
}
.todo-row {
  grid-template-columns: 1.8em 3.4em minmax(0, 1fr) auto;
  align-items: center;
}
.todo-row.done .row-title {
  text-decoration: line-through;
  color: var(--color-text-tertiary);
}
.todo-check {
  width: 1.35em;
  height: 1.35em;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 2px solid var(--color-text-tertiary);
  border-radius: 6px;
  background: transparent;
  color: var(--color-text);
  font-size: 0.95em;
  line-height: 1;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}
.todo-row.done .todo-check {
  border-color: var(--color-green);
  color: var(--color-green);
}
.todo-dot {
  width: 0.45em;
  height: 0.45em;
  margin-left: 0.35em;
  border-radius: 50%;
  background: var(--color-text-tertiary);
}

.kiosk-exit-zone {
  position: absolute;
  right: 0;
  bottom: 0;
  width: 110px;
  height: 110px;
  display: flex;
  align-items: flex-end;
  justify-content: flex-end;
  padding: 14px;
  cursor: default;
  touch-action: none;
  -webkit-tap-highlight-color: transparent;
}
.exit-hint {
  color: var(--color-text-tertiary);
  font-size: 12px;
}

@media (max-width: 900px), (orientation: portrait) {
  .kiosk-main {
    grid-template-columns: 1fr;
    grid-template-rows: minmax(0, 1fr) minmax(0, 1fr);
  }
}
</style>

<style>
/* 独立页接管根背景,避免外壳底色从边角透出;离开时由组件移除类 */
html.kiosk-page,
html.kiosk-page body {
  background: var(--color-bg);
  overflow: hidden;
  overscroll-behavior: none;
}
</style>
