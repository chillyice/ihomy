<!-- 每日首屏早安页:每个客户端每天第一次进入站内页时盖一层全屏(复用看板取数口径)。
     内容:问候语 + 时间日期 + 今日待办(可直接勾选) + 当前天气,背景是当前天气动效。
     偏好与「今天已看过」都只存本机(utils/morning.js);设置页可关闭/改问候语/勾卡片/预览。
     只在登录后、非纯 OPS、非儿童模式、非通话、非独立页/沉浸页、页面可见且无弹窗时出现。 -->
<template>
  <Teleport to="body">
    <transition name="morning-fade">
      <div v-if="visible" class="morning-overlay" role="dialog" aria-modal="true" :aria-label="$t('morning.overlayLabel')" @wheel.stop>
        <component
          :is="sceneComponent"
          :condition="sceneCondition"
          :night="sceneNight"
          :sun-scene="sunScene"
          :wind-scale="windScale"
          :wind-deg="windDeg"
          :wind-dir="windDir"
          :precip-level="scenePrecipLevel"
          :animated="prefs.animation"
        />

        <div class="mo-body">
          <header class="mo-head">
            <div v-if="cardOn('greeting')" class="mo-greeting">{{ greeting }}</div>
            <div v-if="cardOn('greeting') && familyName" class="mo-family">{{ familyName }}</div>
            <span v-if="previewing" class="mo-preview-tag">{{ $t('morning.previewTag', { scene: sceneText }) }}</span>
            <el-button class="mo-close" text circle :aria-label="$t('morning.close')" @click="close">
              <el-icon><Close /></el-icon>
            </el-button>
          </header>

          <main class="mo-main">
            <div class="mo-hero">
              <section v-if="cardOn('date')" class="mo-block mo-clock">
                <div class="mo-time">{{ clock.time }}</div>
                <div class="mo-date">{{ clock.date }} · {{ clock.week }}</div>
              </section>

              <section v-if="cardOn('weather')" class="mo-block mo-weather">
                <template v-if="weather">
                  <div class="mo-temp">{{ tempText }}</div>
                  <div class="mo-weather-text">
                    {{ weather.text || conditionText }}
                    <span v-if="city" class="mo-city">· {{ city }}</span>
                  </div>
                  <div v-if="weatherSub" class="mo-weather-sub">{{ weatherSub }}</div>
                </template>
                <div v-else class="mo-weather-text">{{ $t('morning.weatherUnavailable') }}</div>
              </section>
            </div>

            <section v-if="cardOn('todo')" class="mo-todo">
              <div class="mo-todo-title">{{ $t('morning.todayTodo') }}</div>
              <ul v-if="todoRows.length" class="mo-list">
                <li v-for="it in todoRows" :key="it.key" class="mo-row" :class="{ done: it.done }">
                  <el-checkbox
                    v-if="canToggle(it)"
                    :model-value="it.done"
                    :disabled="toggling"
                    :aria-label="it.title"
                    @change="onToggle(it)"
                  />
                  <span v-else class="mo-dot" aria-hidden="true"></span>
                  <span class="mo-row-title">{{ it.title }}</span>
                  <span v-if="rowMeta(it)" class="mo-row-meta">{{ rowMeta(it) }}</span>
                </li>
              </ul>
              <div v-else class="mo-empty">{{ $t('morning.todoEmpty') }}</div>
              <div v-if="moreCount" class="mo-more">{{ $t('morning.more', { n: moreCount }) }}</div>
            </section>
          </main>

          <footer class="mo-foot">
            <el-button type="primary" size="large" round class="mo-start" @click="close">
              {{ $t('morning.start') }}
            </el-button>
            <el-button text class="mo-link" @click="openSchedule">{{ $t('morning.openSchedule') }}</el-button>
          </footer>
        </div>
      </div>
    </transition>
  </Teleport>
</template>

<script setup>
import { computed, inject, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Close } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import { useCallStore } from '@/stores/call'
import { useDisplayMode } from '@/composables/useDisplayMode'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'
import { planApi, reminderApi } from '@/api'
import { buildAgenda } from '@/utils/agenda'
import { isBusyScreen } from '@/utils/screensaver'
import {
  clearPreviewScene,
  loadLastShown,
  loadPrefs,
  markShown,
  MORNING_SCENE_LABELS,
  prefsTick,
  previewScene,
  previewTick,
  shouldShowMorning,
  todayKey,
} from '@/utils/morning'
import { resolveMorningWeather } from '@/utils/morningWeather'
import MorningScene from './MorningScene.vue'
import MorningSceneRandom from './MorningSceneRandom.vue'

const VISIBLE_TODOS = 6
const CLOCK_MS = 15000
const PREVIEW_PRECIP = { rain: 3, snow: 2, thunder: 4 }

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()
const callStore = useCallStore()
const { isChild } = useDisplayMode()
const sunLight = inject(SUN_LIGHT_KEY, null)

const prefs = reactive(loadPrefs())
const visible = ref(false)
// 云层样式:photo = 天气照片 + 云精灵贴图;random = 纯代码随机柔边云。设置页切换,本机记住。
const sceneComponent = computed(() => (prefs.cloudStyle === 'random' ? MorningSceneRandom : MorningScene))
const now = ref(new Date())
const agenda = ref([])
const toggling = ref(false)
let clockTimer = 0
let bootTimer = 0

const pad = (n) => String(n).padStart(2, '0')

const clock = computed(() => {
  const d = now.value
  return {
    time: `${pad(d.getHours())}:${pad(d.getMinutes())}`,
    date: t('morning.dateFmt', { y: d.getFullYear(), m: d.getMonth() + 1, d: d.getDate() }),
    week: t(`weatherPage.w${d.getDay()}`),
  }
})

const weather = computed(() => sunLight?.weather?.value || null)
const weatherDetail = computed(() => sunLight?.weatherDetail?.value || null)
const nowFull = computed(() => weather.value?.nowFull || weatherDetail.value?.nowFull || {})
const condition = computed(() => resolveMorningWeather(weather.value))
// 设置页预览时可以临时指定天气时段,只影响这一次预览(不落盘、不改真实天气)
const previewing = computed(() => !!previewScene.value.condition)
const sceneCondition = computed(() => previewScene.value.condition || condition.value)
const sceneNight = computed(() => (typeof previewScene.value.night === 'boolean' ? previewScene.value.night : isNight.value))
// 预览雨雪时使用该场景的强度,避免当地没有降水导致无动效。
const scenePrecipLevel = computed(() => {
  if (previewing.value) return PREVIEW_PRECIP[sceneCondition.value] ?? 0
  return weather.value?.precipLevel ?? 2
})
const windScale = computed(() => {
  const n = Number.parseFloat(nowFull.value.windScale ?? weather.value?.windScale)
  return Number.isFinite(n) ? n : 0
})
// 风向给天气场景用:wind360 是「来向」角度,没有它时退回中文风向由场景自己解析
const windDeg = computed(() => {
  const n = Number.parseFloat(nowFull.value.wind360)
  return Number.isFinite(n) ? n : null
})
const windDir = computed(() => String(nowFull.value.windDir || weather.value?.windDir || ''))
// 没有 sun-info 时,光影的初始场景并不代表实况,昼夜按本地小时兜底。
const sunScene = computed(() => (sunLight?.sunInfo?.value?.slots?.length ? sunLight.sunScene.value : null))
const isNight = computed(() => sunScene.value?.isNight ?? (now.value.getHours() < 6 || now.value.getHours() >= 19))
const familyName = computed(() => appStore.familyName)

const conditionText = computed(() => t(MORNING_SCENE_LABELS[condition.value]))
const sceneText = computed(() => t(MORNING_SCENE_LABELS[sceneCondition.value]))

const tempText = computed(() => {
  const n = Number(weather.value?.temp)
  return Number.isFinite(n) ? Math.round(n) + '°' : '—'
})

const city = computed(() => weather.value?.city || '')

const weatherSub = computed(() => {
  const parts = []
  if (windScale.value > 0) parts.push(t('morning.windFmt', { n: Math.round(windScale.value) }))
  const feels = Number(nowFull.value.feelsLike)
  if (Number.isFinite(feels)) parts.push(t('morning.feelsLike', { n: Math.round(feels) }))
  const humidity = Number(nowFull.value.humidity)
  if (Number.isFinite(humidity)) parts.push(t('morning.humidityFmt', { n: Math.round(humidity) }))
  return parts.join(' · ')
})

const greeting = computed(() => {
  const h = now.value.getHours()
  const key =
    h < 5
      ? 'greetingNight'
      : h < 9
        ? 'greetingMorning'
        : h < 12
          ? 'greetingForenoon'
          : h < 14
            ? 'greetingNoon'
            : h < 18
              ? 'greetingAfternoon'
              : h < 23
                ? 'greetingEvening'
                : 'greetingNight'
  const base = String(prefs.greeting || '').trim() || t(`morning.${key}`)
  const name = userStore.userInfo?.nickname || userStore.userInfo?.username || ''
  return name ? t('morning.withName', { greeting: base, name }) : base
})

const cardOn = (id) => prefs.cards[id] !== false
const todoRows = computed(() => agenda.value.slice(0, VISIBLE_TODOS))
const moreCount = computed(() => Math.max(0, agenda.value.length - VISIBLE_TODOS))

const canToggle = (it) => it.kind === 'reminder' || !!it.undated

const rowMeta = (it) => {
  if (it.undated && it.planTitle) return it.planTitle
  if (it.time) return it.time
  if (it.kind === 'plan') {
    if (it.totalCount) return t('morning.progressFmt', { done: it.doneCount, total: it.totalCount })
    return t('morning.planTag')
  }
  if (it.fromPlan) return t('morning.planTag')
  return ''
}

const context = () => ({
  enabled: prefs.enabled,
  isLoggedIn: userStore.isLoggedIn,
  isPureOps: userStore.isPureOps,
  mustChangePassword: userStore.mustChangePassword,
  lastShown: loadLastShown(),
  today: todayKey(),
  isChild: isChild.value,
  inCall: callStore.inCall,
  routeMeta: route.meta,
  busy: isBusyScreen(),
  hidden: typeof document !== 'undefined' && document.visibilityState !== 'visible',
})

const loadTodos = async () => {
  if (!userStore.isLoggedIn) {
    agenda.value = []
    return
  }
  try {
    const data = await planApi.today()
    agenda.value = buildAgenda(data)
  } catch (e) {
    agenda.value = []
  }
}

const syncClock = () => {
  now.value = new Date()
}

const startClock = () => {
  stopClock()
  syncClock()
  clockTimer = setInterval(syncClock, CLOCK_MS)
}
const stopClock = () => {
  if (clockTimer) clearInterval(clockTimer)
  clockTimer = 0
}

const show = (preview = false) => {
  if (visible.value) return
  if (!preview && !shouldShowMorning(context())) return
  // 正常首屏一律用真实天气:清掉可能残留的预览场景
  if (!preview) clearPreviewScene()
  visible.value = true
  if (typeof document !== 'undefined') document.documentElement.classList.add('morning-open')
  startClock()
  loadTodos()
  // 预览不消耗当天的首屏机会;正常展示一出现就记账,刷新页面不会重复弹
  if (!preview) markShown(todayKey())
}

const maybeShow = () => {
  if (visible.value) return
  show(false)
}

const close = () => {
  if (!visible.value) return
  visible.value = false
  stopClock()
  clearPreviewScene()
  if (typeof document !== 'undefined') document.documentElement.classList.remove('morning-open')
}

const onToggle = async (it) => {
  if (toggling.value) return
  toggling.value = true
  try {
    if (it.kind === 'reminder') {
      // 循环提醒按当天勾选;一次性提醒后端忽略 date
      await reminderApi.toggleDone(it.id, todayKey())
    } else if (it.sourceKind === 'PLAN_TASK') {
      await planApi.updateTask(it.id, { done: it.done ? 0 : 1 })
    } else {
      await planApi.update(it.id, { status: it.done ? 'ACTIVE' : 'DONE' })
    }
    await loadTodos()
  } catch (e) {
    ElMessage.error(t('morning.toggleFailed'))
  } finally {
    toggling.value = false
  }
}

const openSchedule = () => {
  close()
  router.push('/plan')
}

const onKeydown = (e) => {
  if (e.key === 'Escape' && visible.value) close()
}

const onVisibility = () => {
  if (typeof document === 'undefined') return
  if (document.visibilityState === 'visible') {
    // 打开时在后台、回到前台才补一次判定(不会重复:正常展示时已记「今天看过」)
    if (!visible.value) maybeShow()
  }
}

const syncPrefs = () => {
  Object.assign(prefs, loadPrefs())
  if (visible.value && !prefs.enabled) close()
}

watch(previewTick, () => {
  syncPrefs()
  show(true)
})

watch(prefsTick, syncPrefs)

watch(
  () => userStore.isLoggedIn,
  (v) => {
    if (!v) close()
    else maybeShow()
  },
)

watch(
  () => route.fullPath,
  () => {
    if (visible.value) close()
  },
)

watch(
  () => callStore.inCall,
  (v) => {
    if (v) close()
  },
)

watch(isChild, (v) => {
  if (v) close()
})

onMounted(() => {
  // 等外壳与首页聚合先发出去,首屏早安晚半拍出现,避免和路由过渡撞在一起
  bootTimer = setTimeout(maybeShow, 400)
  if (typeof document !== 'undefined') document.addEventListener('visibilitychange', onVisibility)
  if (typeof window !== 'undefined') window.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  if (bootTimer) clearTimeout(bootTimer)
  stopClock()
  if (typeof document !== 'undefined') {
    document.removeEventListener('visibilitychange', onVisibility)
    document.documentElement.classList.remove('morning-open')
  }
  if (typeof window !== 'undefined') window.removeEventListener('keydown', onKeydown)
})
</script>

<style scoped>
.morning-overlay {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: var(--z-immersive);
  overflow: hidden;
  color: #fff;
  user-select: none;
  -webkit-user-select: none;
}

.mo-body {
  position: relative;
  height: 100%;
  max-width: 1080px;
  margin: 0 auto;
  padding: clamp(20px, 4vw, 40px) clamp(18px, 4vw, 40px) calc(clamp(20px, 4vw, 40px) + env(safe-area-inset-bottom, 0px));
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: clamp(14px, 3vh, 28px);
  overflow-y: auto;
  overscroll-behavior: contain;
}

.mo-head {
  position: relative;
  flex: 0 0 auto;
  padding-right: 44px;
}
.mo-greeting {
  font-size: clamp(30px, 5.2vw, 54px);
  font-weight: 300;
  line-height: 1.15;
  letter-spacing: 0.01em;
  text-shadow: 0 2px 18px rgba(8, 16, 32, 0.35);
}
.mo-family {
  margin-top: 8px;
  font-size: clamp(13px, 1.6vw, 16px);
  opacity: 0.78;
  letter-spacing: 0.08em;
}
.mo-close {
  position: absolute;
  top: -6px;
  right: -6px;
  color: rgba(255, 255, 255, 0.82);
  font-size: 20px;
}
.mo-close:hover {
  color: #fff;
}
/* 设置页预览场景时的角标:说明画面是挑的场景,天气数字仍是实况 */
.mo-preview-tag {
  display: inline-block;
  margin-top: 10px;
  padding: 3px 10px;
  border: 1px solid rgba(255, 255, 255, 0.32);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.14);
  font-size: 12px;
  letter-spacing: 0.04em;
  backdrop-filter: blur(8px);
}

.mo-main {
  flex: 1 1 auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: clamp(16px, 3.5vh, 32px);
}

.mo-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: clamp(14px, 3vw, 40px);
}
.mo-block {
  min-width: 0;
}
.mo-time {
  font-size: clamp(58px, 12vw, 128px);
  font-weight: 200;
  line-height: 0.92;
  letter-spacing: 0.01em;
  font-variant-numeric: tabular-nums;
  text-shadow: 0 4px 26px rgba(8, 16, 32, 0.35);
}
.mo-date {
  margin-top: 10px;
  font-size: clamp(14px, 2vw, 19px);
  opacity: 0.85;
}
.mo-weather {
  text-align: right;
}
.mo-temp {
  font-size: clamp(40px, 7vw, 76px);
  font-weight: 300;
  line-height: 1;
  font-variant-numeric: tabular-nums;
  text-shadow: 0 4px 24px rgba(8, 16, 32, 0.3);
}
.mo-weather-text {
  margin-top: 8px;
  font-size: clamp(15px, 2vw, 20px);
  opacity: 0.92;
}
.mo-city {
  opacity: 0.72;
}
.mo-weather-sub {
  margin-top: 6px;
  font-size: clamp(12.5px, 1.5vw, 15px);
  opacity: 0.72;
}

/* 待办卡片:实色半透明底,长开页不用 backdrop-filter */
.mo-todo {
  flex: 0 1 auto;
  min-height: 0;
  border-radius: var(--radius);
  background: rgba(10, 18, 32, 0.34);
  border: 1px solid rgba(255, 255, 255, 0.16);
  padding: 14px 16px;
  max-width: 720px;
}
.mo-todo-title {
  font-size: 12.5px;
  font-weight: 600;
  letter-spacing: 0.08em;
  opacity: 0.72;
  margin-bottom: 4px;
}
.mo-list {
  list-style: none;
  margin: 0;
  padding: 0;
  max-height: min(34vh, 300px);
  overflow-y: auto;
  overscroll-behavior: contain;
}
.mo-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px solid rgba(255, 255, 255, 0.12);
  font-size: clamp(14px, 1.7vw, 16px);
  min-width: 0;
}
.mo-row:last-child {
  border-bottom: none;
}
.mo-row.done .mo-row-title {
  opacity: 0.55;
  text-decoration: line-through;
}
.mo-dot {
  flex: 0 0 auto;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.45);
  box-sizing: border-box;
}
.mo-row-title {
  flex: 1 1 auto;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.mo-row-meta {
  flex: 0 0 auto;
  max-width: 42%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12.5px;
  opacity: 0.66;
}
.mo-empty {
  padding: 10px 0 4px;
  font-size: 14px;
  opacity: 0.72;
}
.mo-more {
  margin-top: 8px;
  font-size: 12.5px;
  opacity: 0.62;
}

/* 勾选框在深色背景上保持可见 */
.mo-row :deep(.el-checkbox) {
  height: auto;
  margin-right: 0;
}
.mo-row :deep(.el-checkbox__inner) {
  background: rgba(255, 255, 255, 0.1);
  border-color: rgba(255, 255, 255, 0.55);
}

.mo-foot {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.mo-start {
  min-width: 168px;
  letter-spacing: 0.08em;
}
.mo-link {
  color: rgba(255, 255, 255, 0.78);
}
.mo-link:hover {
  color: #fff;
}

/* 进入/退出:淡入 + 轻微上浮,避免整屏硬切 */
.morning-fade-enter-active,
.morning-fade-leave-active {
  transition: opacity 0.5s ease;
}
.morning-fade-enter-from,
.morning-fade-leave-to {
  opacity: 0;
}

@media (max-width: 640px) {
  .mo-hero {
    align-items: flex-start;
    flex-direction: column;
    gap: 14px;
  }
  .mo-weather {
    text-align: left;
  }
  .mo-todo {
    max-width: none;
  }
  .mo-list {
    max-height: 32vh;
  }
  .mo-start {
    flex: 1 1 auto;
  }
}
</style>
