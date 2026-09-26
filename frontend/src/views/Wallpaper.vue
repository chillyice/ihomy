<!-- 壁纸页 /wallpaper(meta.public + meta.standalone):家庭私有「只读氛围屏」。
     定位:桌面壁纸/常开副屏用,只做展示——时钟、天气、家人照片轮播、纪念日、待办、动态流、计划、提醒、太阳驱动的光影。
     设计边界(勿扩张):除首次登录外不含任何写操作、不上传、不导航;要写博客传照片回正常 ihomy 窗口。
     交互(V9.93 起):天气 AI 生图为常驻底图(与暖居外壳同源 useWeatherBg);照片轮播/纪念日/待办
          三个小组件常驻显示,整卡可拖拽,位置固化进 localStorage(浏览器与 WE 环境各自记忆);
          角落控件仍保留「移动浮现、静止 2.2s 淡出」(待机浮现照片的旧逻辑已移除)。
     组件可增删(本轮):组件库 WIDGET_DEFS 列出可用小组件(照片/纪念日/待办 + 动态流/家庭计划/提醒),
          在角落控件的「组件」面板里点一下即添加/移除,启用态落 localStorage ihomy:wallpaper:widgets:v1
          (默认开照片/纪念日/待办,与旧版视觉一致;新组件默认关)。新增组件的落点:注册表加一条 +
          模板加 isOn('id') 分支 + 样式补默认坐标;未启用的组件不取数(壁纸请求数不随组件库膨胀)。
          原角落控件里的「复制壁纸令牌」按钮已移除,令牌入口只留设置页一处。
     主题:复用全局 themeStore(与站点共享 ihomy-theme 偏好);「自动」由太阳高度角驱动(useSunLight 内 applyAuto)。
     体积:本页刻意不引 Element Plus(原生 input/button + 主题 CSS 变量),保证壁纸首屏轻量。
     Wallpaper Engine:壳页(wallpaper-engine/)顶层跳转本页,属性面板的主题/晨暮走查询参数、令牌走 hash(见 onMessage)。 -->
<template>
  <div class="wp" :class="{ 'has-bg': !!weatherBg }" @mousemove="onMove" @touchstart.passive="onMove">
    <!-- 兜底底色:用户关掉毛玻璃等特效时也不留白 -->
    <div class="wp-base" aria-hidden="true"></div>

    <!-- 天气→AI 生图氛围底图(z=1,压在兜底底色之上、光影层之下;无图时隐藏走主题渐变) -->
    <div class="wp-weatherbg" :class="{ on: weatherBg }"
         :style="weatherBg ? { backgroundImage: `url(${weatherBg})` } : null" aria-hidden="true"></div>

    <!-- 全局光影层(体积光/窗影/尘/台灯/天气特效);太阳与天气状态由 App.vue provide -->
    <SunLightLayer />

    <!-- 常态环境层:时钟 + 天气(壁纸的主体内容,固定左下不可拖) -->
    <div class="wp-ambient">
      <div class="wp-time">{{ clock.time }}</div>
      <div class="wp-meta">
        {{ clock.date }} · {{ clock.week }}
        <span v-if="familyName"> · {{ familyName }}</span>
      </div>
      <div v-if="weather" class="wp-weather">
        <span class="wp-temp">{{ Math.round(weather.temp) }}°</span>
        <span class="wp-wtext">{{ weather.text }}</span>
        <span class="wp-wcity">{{ weather.city }}</span>
      </div>
    </div>

    <!-- 小组件:组件库见 WIDGET_DEFS,默认只开照片/纪念日/待办,其余在角落「组件」面板里添加。
         常驻显示、整卡可拖拽、位置记忆;光影层仍在其上(照片被光柱/浮尘覆盖) -->
    <div v-if="isOn('photos') && stackCards.length" class="wp-widget wpw-photos"
         :class="{ placed: widgetPos.photos, dragging: dragId === 'photos' }"
         :style="widgetStyle('photos')" @pointerdown="onWidgetDown('photos', $event)">
      <div class="wp-stack">
        <div v-for="(p, i) in stackCards" :key="p.id" class="wp-pcard" :style="pcardStyle(i)">
          <img :src="p.url" :alt="p.description || ''" loading="lazy" draggable="false" />
        </div>
      </div>
      <div v-if="topPhoto?.description" class="wp-cap">{{ topPhoto.description }}</div>
    </div>

    <div v-if="isOn('anni') && userStore.isLoggedIn && anniEvents.length" class="wp-widget wpw-anni"
         :class="{ placed: widgetPos.anni, dragging: dragId === 'anni' }"
         :style="widgetStyle('anni')" @pointerdown="onWidgetDown('anni', $event)">
      <div class="wpw-title">{{ $t('wallpaper.widgetAnni') }}</div>
      <div class="wpw-body">
        <div v-for="(ev, i) in anniEvents" :key="i" class="wpw-row">
          <div class="wpw-info">
            <div class="wpw-name">{{ ev.label }}</div>
            <div class="wpw-sub">{{ fmtEventDate(ev.date) }}</div>
          </div>
          <div class="wpw-days" :class="{ today: ev.days === 0 }">
            <template v-if="ev.days === 0">{{ $t('wallpaper.today') }}</template>
            <template v-else>{{ ev.days }}<span class="wpw-days-unit">{{ $t('wallpaper.daysUnit') }}</span></template>
          </div>
        </div>
      </div>
    </div>

    <div v-if="isOn('task') && userStore.isLoggedIn && todoTasks.length" class="wp-widget wpw-task"
         :class="{ placed: widgetPos.task, dragging: dragId === 'task' }"
         :style="widgetStyle('task')" @pointerdown="onWidgetDown('task', $event)">
      <div class="wpw-title">{{ $t('wallpaper.widgetTodo') }}</div>
      <div class="wpw-body">
        <div v-for="tk in todoTasks" :key="tk.id" class="wpw-row">
          <div class="wpw-name wpw-task-name">{{ tk.title }}</div>
          <div v-if="tk.rewardType === 'POINTS'" class="wpw-reward">
            {{ $t('task.rewardPointsText', { points: tk.rewardPoints }) }}
          </div>
          <div v-else-if="tk.rewardType === 'ITEM'" class="wpw-reward">{{ tk.rewardItem }}</div>
        </div>
      </div>
    </div>

    <!-- 家庭动态流(只读;启用才取数,未开时不打这个请求) -->
    <div v-if="isOn('feed') && feedItems.length" class="wp-widget wpw-feed"
         :class="{ placed: widgetPos.feed, dragging: dragId === 'feed' }"
         :style="widgetStyle('feed')" @pointerdown="onWidgetDown('feed', $event)">
      <div class="wpw-title">{{ $t('wallpaper.wFeed') }}</div>
      <div class="wpw-body">
        <div v-for="(f, i) in feedItems" :key="i" class="wpw-row">
          <div class="wpw-info">
            <div class="wpw-name">{{ feedText(f) }}</div>
            <div class="wpw-sub">{{ feedTypeLabel(f) }}</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 家庭计划(只读) -->
    <div v-if="isOn('plan') && plans.length" class="wp-widget wpw-plan"
         :class="{ placed: widgetPos.plan, dragging: dragId === 'plan' }"
         :style="widgetStyle('plan')" @pointerdown="onWidgetDown('plan', $event)">
      <div class="wpw-title">{{ $t('wallpaper.wPlan') }}</div>
      <div class="wpw-body">
        <div v-for="p in plans" :key="p.id" class="wpw-row">
          <div class="wpw-info">
            <div class="wpw-name">{{ p.title }}</div>
            <div class="wpw-sub">{{ fmtPlanSub(p) }}</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 提醒事项(只读) -->
    <div v-if="isOn('reminder') && reminders.length" class="wp-widget wpw-reminder"
         :class="{ placed: widgetPos.reminder, dragging: dragId === 'reminder' }"
         :style="widgetStyle('reminder')" @pointerdown="onWidgetDown('reminder', $event)">
      <div class="wpw-title">{{ $t('wallpaper.wReminder') }}</div>
      <div class="wpw-body">
        <div v-for="r in reminders" :key="r.id" class="wpw-row">
          <div class="wpw-info">
            <div class="wpw-name">{{ r.title }}</div>
            <div class="wpw-sub">{{ fmtRemindSub(r) }}</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 角落控件:鼠标移动浮现、静止隐去(壁纸不留常驻界面) -->
    <div class="wp-ui" :class="{ visible: uiVisible }">
      <div class="wp-seg">
        <button v-for="t in themeStore.themes" :key="t.id" class="wp-segbtn"
                :class="{ on: themeStore.theme === t.id }" @click="themeStore.setTheme(t.id)">
          {{ t.label[locale === 'en' ? 'en' : 'zh'] }}
        </button>
      </div>
      <div class="wp-seg">
        <button class="wp-segbtn" :class="{ on: themeStore.autoMode }"
                :title="$t('wallpaper.autoHint')" @click="themeStore.setAutoMode(true)">{{ $t('wallpaper.auto') }}</button>
        <button class="wp-segbtn" :class="{ on: !themeStore.autoMode && themeStore.mode === 'dawn' }"
                @click="themeStore.setMode('dawn')">{{ $t('wallpaper.dawn') }}</button>
        <button class="wp-segbtn" :class="{ on: !themeStore.autoMode && themeStore.mode === 'dusk' }"
                @click="themeStore.setMode('dusk')">{{ $t('wallpaper.dusk') }}</button>
      </div>
      <!-- 语言:WE 里 CEF 是 en-us,i18n 默认跟随浏览器语言会走英文,这里可显式切(WE 属性面板亦有同名选项) -->
      <div class="wp-seg">
        <button class="wp-segbtn" :class="{ on: locale === 'zh-CN' }" @click="setLang('zh-CN')">中</button>
        <button class="wp-segbtn" :class="{ on: locale === 'en' }" @click="setLang('en')">EN</button>
      </div>
      <!-- 组件:壁纸上显示哪些小组件在这里增删(点一下即添加/移除,启停记忆在 localStorage)。
           面板打开期间角落控件不自动淡出,否则用户点第二下之前控件就没了 -->
      <div class="wp-seg">
        <button class="wp-segbtn" :class="{ on: panelOpen }" @click="togglePanel">{{ $t('wallpaper.widgets') }}</button>
      </div>
      <div v-if="panelOpen" class="wp-widgets">
        <div class="wp-widgets-cap">{{ $t('wallpaper.widgetsTitle') }}</div>
        <button v-for="w in WIDGET_DEFS" :key="w.id" class="wp-widgets-row"
                :class="{ on: isOn(w.id) }" @click="toggleWidget(w.id)">
          <span class="wp-widgets-mark">{{ isOn(w.id) ? '✓' : '＋' }}</span>
          <span>{{ $t(w.label) }}</span>
        </button>
      </div>
    </div>

    <!-- 未登录:一次性登录卡(登录后本页只读;未登录时不显示任何家庭内容,演示家庭数据也不显示) -->
    <div v-if="!userStore.isLoggedIn" class="wp-login">
      <form class="wp-login-card" @submit.prevent="doLogin">
        <div class="wp-login-title">ihomy</div>
        <div class="wp-login-sub">{{ $t('wallpaper.loginSub') }}</div>
        <input v-model="form.email" class="wp-input" type="email" autocomplete="username"
               :placeholder="$t('wallpaper.email')" />
        <input v-model="form.password" class="wp-input" type="password" autocomplete="current-password"
               :placeholder="$t('wallpaper.password')" />
        <div class="wp-captcha">
          <input v-model="form.captchaCode" class="wp-input" :placeholder="$t('wallpaper.captcha')" />
          <img v-if="captchaImage" :src="captchaImage" class="wp-captcha-img"
               :alt="$t('wallpaper.captcha')" :title="$t('wallpaper.captchaRefresh')" @click="loadCaptcha" />
        </div>
        <div v-if="loginError" class="wp-login-err">{{ loginError }}</div>
        <button class="wp-login-btn" type="submit" :disabled="loggingIn">
          {{ loggingIn ? $t('wallpaper.loggingIn') : $t('wallpaper.login') }}
        </button>
      </form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, inject, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import SunLightLayer from '@/components/SunLightLayer.vue'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'
import { useUserStore } from '@/stores/user'
import { useThemeStore } from '@/stores/theme'
import { useWeatherBg } from '@/composables/useWeatherBg'
import { authApi, publicApi, taskApi, planApi, reminderApi } from '@/api'
import { applyLocale } from '@/i18n'

const { locale, t } = useI18n()
const route = useRoute()
const userStore = useUserStore()
const themeStore = useThemeStore()
// 全局光影状态(App.vue provide):天气与太阳时隙都从这里取,本页不重复请求
const sunLight = inject(SUN_LIGHT_KEY, null)
const weather = computed(() => sunLight?.weather?.value || null)

// 天气→AI 生图氛围底图(与暖居外壳同源同缓存键;当前天气无图自动回退上一张,不留白)
const { weatherBg, load: loadWeatherBg } = useWeatherBg()
watch(() => sunLight?.weather?.value, (w) => { if (w) loadWeatherBg(w) }, { immediate: true })

// ========== 时钟(每秒对表,仅在显示串变化时写 ref,避免每帧无谓渲染) ==========
const clock = reactive({ time: '', date: '', week: '' })
let clockTimer = null
const syncClock = () => {
  const d = new Date()
  const time = `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
  const date = t('wallpaper.dateFmt', { m: d.getMonth() + 1, d: d.getDate() })
  const week = t(`weatherPage.w${d.getDay()}`)
  if (time !== clock.time) clock.time = time
  if (date !== clock.date) clock.date = date
  if (week !== clock.week) clock.week = week
}

// ========== 家人照片轮播(仅登录后拉取;未登录不显示任何家庭内容) ==========
const photos = ref([])
const photoIndex = ref(0)
const familyName = ref('')
// 纪念日组件数据:后端已算好下一次日期与剩余天数(stats.upcomingEvents,与首页卡片同源)
const anniEvents = ref([])
const PHOTO_STACK_N = 4
const topPhoto = computed(() => photos.value[photoIndex.value] || null)
const stackCards = computed(() => {
  const arr = photos.value
  const n = arr.length
  if (!n) return []
  const out = []
  for (let i = 0; i < Math.min(PHOTO_STACK_N, n); i++) out.push(arr[(photoIndex.value + i) % n])
  return out
})
// 扇形几何走 CSS 变量(与暖居卡牌堆同口径:变量才能被类覆写,写死在内联 transform 里就改不动)
const pcardStyle = (i) => {
  const n = stackCards.value.length
  const mid = (n - 1) / 2
  return {
    '--dx': `${((i - mid) * 30).toFixed(1)}px`,
    '--dy': `${(Math.abs(i - mid) * 14).toFixed(1)}px`,
    '--rot': `${((i - mid) * 4).toFixed(2)}deg`,
    '--z': 10 - i,
  }
}
const loadPhotos = async () => {
  if (!userStore.isLoggedIn) return
  try {
    // 带 JWT 调公开聚合:后端识别为成员 → 返回 family 全量最新照片 + stats(纪念日 upcomingEvents 一并复用)
    const data = await publicApi.getHome()
    const list = Array.isArray(data?.photos) ? data.photos : []
    photos.value = list.filter((p) => p && p.url)
    if (photoIndex.value >= photos.value.length) photoIndex.value = 0
    if (!photos.value.length) stopSlide()
    if (data?.family?.name) familyName.value = data.family.name
    // 纪念日组件数据:后端已算好下一次日期与剩余天数(与首页卡片同源,零新增请求)
    anniEvents.value = Array.isArray(data?.stats?.upcomingEvents) ? data.stats.upcomingEvents.slice(0, 5) : []
  } catch (e) {
    // 网络/权限异常:保持环境层,照片/纪念日区留空即可(壁纸不弹错误提示)
  }
}

// ========== 待办组件(悬赏任务里进行中/待领取的,只读展示;壁纸不做任何写操作) ==========
const TODO_N = 5
const todoTasks = ref([])
const loadTodos = async () => {
  if (!userStore.isLoggedIn) return
  try {
    const list = await taskApi.list()
    // 进行中的排前面(更具体),待领取的在后;各保内部原有顺序
    todoTasks.value = (Array.isArray(list) ? list : [])
      .filter((tk) => tk && (tk.status === 'IN_PROGRESS' || tk.status === 'OPEN'))
      .sort((a, b) => (a.status === 'IN_PROGRESS' ? 0 : 1) - (b.status === 'IN_PROGRESS' ? 0 : 1))
      .slice(0, TODO_N)
  } catch (e) {
    // 静默:壁纸不弹错误
  }
}

// ========== 壁纸组件库:壁纸上显示哪些小组件,由角落「组件」面板增删 ==========
// 新增一种组件 = 这里加一条 + 模板里加一个 isOn('id') 的渲染分支(默认坐标在样式里)。
// 组件一律只读;未启用的组件不取数,组件库变大也不会多打请求(壁纸请求数是刻意控住的)。
const WIDGET_DEFS = [
  { id: 'photos', label: 'wallpaper.wPhotos' },
  { id: 'anni', label: 'wallpaper.widgetAnni' },
  { id: 'task', label: 'wallpaper.widgetTodo' },
  { id: 'feed', label: 'wallpaper.wFeed' },
  { id: 'plan', label: 'wallpaper.wPlan' },
  { id: 'reminder', label: 'wallpaper.wReminder' },
]
// 默认开照片/纪念日/待办(与 V9.93 的常驻组件一致,升级后视觉不变);新增组件默认关,由用户自己添加
const DEFAULT_WIDGETS = ['photos', 'anni', 'task']
const WIDGETS_KEY = 'ihomy:wallpaper:widgets:v1'
const enabled = ref((() => {
  try {
    const raw = JSON.parse(localStorage.getItem(WIDGETS_KEY) || 'null')
    return Array.isArray(raw) ? raw.filter((id) => WIDGET_DEFS.some((w) => w.id === id)) : [...DEFAULT_WIDGETS]
  } catch (e) { return [...DEFAULT_WIDGETS] }
})())
const isOn = (id) => enabled.value.includes(id)
const saveWidgets = () => {
  try { localStorage.setItem(WIDGETS_KEY, JSON.stringify(enabled.value)) } catch (e) { /* 无痕环境:仅本次会话生效 */ }
}
const panelOpen = ref(false)
const togglePanel = () => {
  panelOpen.value = !panelOpen.value
  if (panelOpen.value) onMove()
}
const toggleWidget = (id) => {
  const i = enabled.value.indexOf(id)
  if (i >= 0) enabled.value.splice(i, 1)
  else enabled.value.push(id)
  saveWidgets()
  // 只补拉「刚加进来」这一个组件的数据(移除的留着数据无副作用,再加回来时立即可见);
  // 逐个全量重载会让连点几下变成一串重复请求
  if (i < 0) loadWidgetData(id)
  if (id === 'photos') startSlide()
}

// 组件数据:动态流/计划/提醒各自独立取数,只有启用了才打请求;
// 传 only 时只拉这一个(面板里新添一个组件时不惊动其它组件)
const feedItems = ref([])
const plans = ref([])
const reminders = ref([])
const WIDGET_ROWS = 4
const loadWidgetData = async (only) => {
  if (!userStore.isLoggedIn) return
  if ((!only || only === 'feed') && isOn('feed')) {
    try {
      const list = await publicApi.getFeed(8)
      feedItems.value = (Array.isArray(list) ? list : []).slice(0, WIDGET_ROWS)
    } catch (e) { /* 静默:壁纸不弹错误 */ }
  }
  if ((!only || only === 'plan') && isOn('plan')) {
    try {
      const list = await planApi.list()
      plans.value = (Array.isArray(list) ? list : [])
        .filter((p) => p && p.status === 'ACTIVE')
        .sort((a, b) => String(a.targetDate || '9999').localeCompare(String(b.targetDate || '9999')))
        .slice(0, WIDGET_ROWS)
    } catch (e) { /* 静默 */ }
  }
  if ((!only || only === 'reminder') && isOn('reminder')) {
    try {
      const list = await reminderApi.list()
      reminders.value = (Array.isArray(list) ? list : [])
        .filter((r) => r && r.done !== 1)
        .sort((a, b) => String(a.remindDate + a.remindTime).localeCompare(String(b.remindDate + b.remindTime)))
        .slice(0, WIDGET_ROWS)
    } catch (e) { /* 静默 */ }
  }
}

// 动态流一行:一行文本 + 类型标签(与移动端首页动态流同口径,文案走 i18n)
const FEED_TYPES = ['blog', 'diary', 'photo', 'video', 'wish', 'task', 'recipe', 'book']
const feedTypeLabel = (f) => (FEED_TYPES.includes(f.type) ? t('wallpaper.feedType.' + f.type) : '')
const feedText = (f) => {
  if (f.type === 'photo') return t('wallpaper.feedPhotoCount', { n: f.count || 0 })
  if (f.type === 'diary') return (f.content || '').slice(0, 40)
  return f.title || ''
}
// 计划一行:目标日期 + 子任务进度(后端 list 已带 doneCount/totalCount)
const fmtPlanSub = (p) => {
  const parts = []
  if (p.targetDate) parts.push(fmtEventDate(p.targetDate))
  if (p.totalCount) parts.push(`${p.doneCount || 0}/${p.totalCount}`)
  return parts.join(' · ')
}
// 提醒一行:重复型显示「每日/每周/每月」(复用提醒页文案),一次性显示日期,后面统一带时间
const REPEAT_KEYS = { DAILY: 'reminder.daily', WEEKLY: 'reminder.weekly', MONTHLY: 'reminder.monthly' }
const fmtRemindSub = (r) => {
  const when = REPEAT_KEYS[r.repeatType] ? t(REPEAT_KEYS[r.repeatType]) : fmtEventDate(r.remindDate)
  return [when, (r.remindTime || '').slice(0, 5)].filter(Boolean).join(' ')
}

let photoRefreshTimer = null
// 家人随时会加照片/领任务:10 分钟级刷新(成员视图后端不缓存,别频繁打)。
// 页内登录的场景也要起算,否则登录后只加载一次、之后新数据再不出现。
const startPhotoRefresh = () => {
  if (photoRefreshTimer) return
  photoRefreshTimer = setInterval(() => { loadPhotos(); loadTodos(); loadWidgetData() }, 600000)
}

// 轮播:常驻跑(每 6s 换一张);照片不足 2 张不起
let slideTimer = null
const startSlide = () => {
  if (slideTimer || photos.value.length < 2) return
  slideTimer = setInterval(() => {
    const n = photos.value.length
    photoIndex.value = n ? (photoIndex.value + 1) % n : 0
  }, 6000)
}
const stopSlide = () => { if (slideTimer) { clearInterval(slideTimer); slideTimer = null } }

// 进入已登录状态后的统一收尾:页内登录、hash 令牌、WE 晚到令牌三条路都走这里
const enterLoggedIn = async () => {
  await loadPhotos()
  loadTodos()
  loadWidgetData()
  startSlide()
  startPhotoRefresh()
}

// ========== 角落控件隐去(移动浮现、静止 2.2s 淡出;照片/小组件已常驻,不再有待机浮现) ==========
const UI_HIDE_MS = 2200
// 组件面板开着时给更长的停留:用户要连点几下增删,2.2s 太短会点到一半控件就淡走
const PANEL_HIDE_MS = 15000
const uiVisible = ref(false)
let uiHideTimer = null
const onMove = () => {
  uiVisible.value = true
  clearTimeout(uiHideTimer)
  uiHideTimer = setTimeout(() => {
    if (panelOpen.value) panelOpen.value = false
    uiVisible.value = false
  }, panelOpen.value ? PANEL_HIDE_MS : UI_HIDE_MS)
}

// ========== 小组件拖拽:pointerdown 拖动,位置固化 px 落 localStorage ==========
// 默认位置由 CSS 给(照片居中、纪念日右上、待办右下);拖过一次就固化为 px 坐标,
// 浏览器与 Wallpaper Engine 各是独立 localStorage,互不影响。
const WIDGET_POS_KEY = 'ihomy:wallpaper:widget-pos:v1'
const widgetPos = reactive((() => {
  try { return JSON.parse(localStorage.getItem(WIDGET_POS_KEY) || '{}') } catch (e) { return {} }
})())
const dragId = ref('')
let dragCtx = null
const clamp = (v, lo, hi) => Math.min(Math.max(v, lo), Math.max(lo, hi))

const saveWidgetPos = () => {
  try { localStorage.setItem(WIDGET_POS_KEY, JSON.stringify(widgetPos)) } catch (e) { /* 无痕环境存不了就仅本次会话生效 */ }
}

const onWidgetDown = (id, e) => {
  if (e.button !== 0) return
  const r = e.currentTarget.getBoundingClientRect()
  // 把 CSS 默认位置固化成 px 坐标,从当前视觉位置无缝接管
  widgetPos[id] = { x: r.left, y: r.top }
  dragCtx = { id, dx: e.clientX - r.left, dy: e.clientY - r.top, w: r.width, h: r.height }
  dragId.value = id
  // 拖拽开始才挂监听器,结束即摘(事件监听器按需挂载,不要常驻)
  window.addEventListener('pointermove', onWidgetMove)
  window.addEventListener('pointerup', onWidgetUp)
  e.preventDefault()
}
const onWidgetMove = (e) => {
  if (!dragCtx) return
  const { id, dx, dy, w, h } = dragCtx
  widgetPos[id] = {
    x: clamp(e.clientX - dx, 0, window.innerWidth - w),
    y: clamp(e.clientY - dy, 0, window.innerHeight - h),
  }
}
const onWidgetUp = () => {
  dragCtx = null
  dragId.value = ''
  window.removeEventListener('pointermove', onWidgetMove)
  window.removeEventListener('pointerup', onWidgetUp)
  saveWidgetPos()
}
const widgetStyle = (id) => ({
  ...(widgetPos[id] ? { left: `${Math.round(widgetPos[id].x)}px`, top: `${Math.round(widgetPos[id].y)}px` } : {}),
  zIndex: dragId.value === id ? 60 : 30,
})
// 显示器切换/分辨率调整:把已固化的位置拉回可视区内
const onWinResize = () => {
  for (const id of Object.keys(widgetPos)) {
    const p = widgetPos[id]
    if (!p) continue
    const el = document.querySelector('.wpw-' + id)
    if (!el) continue
    const r = el.getBoundingClientRect()
    widgetPos[id] = { x: clamp(p.x, 0, window.innerWidth - r.width), y: clamp(p.y, 0, window.innerHeight - r.height) }
  }
}

// 纪念日下次日期后端给 ISO 串(YYYY-MM-DD),按当前语言格式化(复用 dateFmt)
const fmtEventDate = (iso) => {
  const m = /^(\d{4})-(\d{1,2})-(\d{1,2})$/.exec(iso || '')
  if (!m) return iso || ''
  return t('wallpaper.dateFmt', { m: Number(m[2]), d: Number(m[3]) })
}

// ========== 登录(一次性;token 落 localStorage,后续由 request.js 401 自动续期) ==========
const form = reactive({ email: '', password: '', captchaCode: '' })
const captchaId = ref('')
const captchaImage = ref('')
const loggingIn = ref(false)
const loginError = ref('')
const loadCaptcha = async () => {
  try {
    const data = await authApi.captcha()
    captchaId.value = data.captchaId
    captchaImage.value = data.image
  } catch (e) {
    loginError.value = t('wallpaper.captchaFailed')
  }
}
const doLogin = async () => {
  if (loggingIn.value) return
  loginError.value = ''
  if (!form.email.trim() || !form.password || !form.captchaCode.trim()) {
    loginError.value = t('wallpaper.needAll')
    return
  }
  loggingIn.value = true
  try {
    await userStore.login({
      email: form.email.trim(),
      password: form.password,
      captchaId: captchaId.value,
      captchaCode: form.captchaCode.trim(),
    })
    form.password = ''
    form.captchaCode = ''
    await enterLoggedIn()
  } catch (e) {
    loginError.value = t('wallpaper.loginFailed')
    form.captchaCode = ''
    loadCaptcha()
  } finally {
    loggingIn.value = false
  }
}

// ========== Wallpaper Engine 偏好:URL 参数优先于 postMessage ==========
// WE 桌面壁纸不能再用 iframe 嵌本页(跨域 iframe 会崩 WE 的 CEF,详见 wallpaper-engine/index.html),
// 壳页改成顶层跳转并把属性面板的 theme/mode/lang 挂在查询串上带过来。普通浏览器访问不受影响(参数可选)。
// 只做一次、且在挂载时执行:属性本身就是页面加载时一次性送达的,后续改面板需重启预览。
const applyQueryPrefs = () => {
  const q = route.query
  if (q.theme === 'warm' || q.theme === 'guangchen') themeStore.setTheme(q.theme)
  if (q.mode === 'auto') themeStore.setAutoMode(true)
  else if (q.mode === 'dawn' || q.mode === 'dusk') themeStore.setMode(q.mode)
  // WE 的 CEF 语言是 en-us,i18n 默认跟随浏览器语言 → 壁纸在 WE 里会走英文,故用属性显式指定
  if (q.lang === 'zh') applyLocale('zh-CN')
  else if (q.lang === 'en') applyLocale('en')
}

// 角落控件里的语言切换(与 WE 属性面板等效;未登录时也能用)。applyLocale 会一并写进 ihomy-lang。
const setLang = (l) => applyLocale(l)

// ========== 壁纸令牌:桌面壁纸拿不到键盘,登录卡填不了,改由属性面板粘令牌换取正式会话 ==========
// 令牌走 URL hash(不发给服务器);换到会话后由 userStore 落进 WE 自己的 localStorage,
// 之后靠 request.js 的 401 自动续期(两个 token 都轮换)保活,不必每次开屏都靠属性面板。
// 注:令牌的投递时机见壳页(wallpaper-engine/index.html)的 go() —— WE 分次投递属性,壳页要等静默再跳,
// 否则令牌会被丢掉(V9.90 修)。
const exchangeToken = async (tk) => {
  if (!tk) return false
  // ⚠ 本地已有会话时不要用属性里的令牌覆盖:续期会轮换 refresh token,属性面板那份是用户最初粘的旧值
  // (已失效),覆盖会把有效登录态冲掉。等到会话真的失效时,401 流程会登出清空,下个开屏自然重新引导。
  if (userStore.refreshToken) return false
  try {
    userStore.setToken('', tk)
    await userStore.refresh()
    return true
  } catch (e) {
    // 令牌无效/过期:清掉,静默保持未登录(壁纸不弹错误提示),重新复制一个即可
    userStore.setToken('', '')
    localStorage.removeItem('userInfo')
    return false
  }
}

const bootstrapToken = async () => {
  const m = /(?:^|[#&])token=([^&]+)/.exec(window.location.hash || '')
  console.log('[ihomy-wallpaper] hash 令牌:' + (m ? m[1].length + '字符' : '无'))
  if (!m) return
  let tk = ''
  try { tk = decodeURIComponent(m[1]) } catch (e) { return }
  console.log('[ihomy-wallpaper] 令牌换会话:' + (await exchangeToken(tk) ? '成功' : '失败'))
}

// WE 属性桥补充:WE 也会在**页面已加载之后**才把用户改过的值(令牌就是)投给当前页面。挂一份监听,
// 令牌晚到就当场换会话,不必等下个开屏;没有这个桥时,晚到的令牌到不了页面(壳页已经带着 hash 跳走了)。
// 页内 localStorage 一旦拿到会话就长期有效,所以「晚到一次」也够用。诊断行同 bootstrapToken。
const wePropsListener = {
  applyUserProperties: (p) => {
    const tk = p && p.token ? String(p.token.value || '').trim() : ''
    if (!tk) return
    console.log('[ihomy-wallpaper] WE 属性到达:令牌 ' + tk.length + ' 字符')
    exchangeToken(tk).then(async (ok) => {
      console.log('[ihomy-wallpaper] 晚到令牌换会话:' + (ok ? '成功' : '失败'))
      if (ok) await enterLoggedIn()
    })
  },
}

// 「复制壁纸令牌」已从本页移除(只保留设置页 个性化设置 → 壁纸氛围屏 那一个入口):
// 壁纸页是给桌面/副屏看的,令牌复制属于「配置」动作,留在设置页一处即可。

// ========== Wallpaper Engine 属性桥(可选) ==========
// WE 网页壁纸的壳页(wallpaper-engine/index.html)把属性面板的主题/晨暮 postMessage 进来,
// 因为桌面壁纸未必拿得到鼠标,控件点不动时靠这里换主题。普通浏览器访问完全不受影响。
// 只认形状正确的消息,且忽略自身派发的消息(e.source === window);不读任何家庭数据。
const onMessage = (e) => {
  if (e.source === window) return
  const msg = e.data && e.data.ihomyWallpaper
  if (!msg || typeof msg !== 'object') return
  if (msg.theme) themeStore.setTheme(msg.theme)
  if (msg.mode === 'auto') themeStore.setAutoMode(true)
  else if (msg.mode === 'dawn' || msg.mode === 'dusk') themeStore.setMode(msg.mode)
}

onMounted(async () => {
  window.addEventListener('message', onMessage)
  window.addEventListener('resize', onWinResize)
  // 注册 WE 属性桥(见 wePropsListener):令牌晚到时还能当场换会话
  window.wallpaperPropertyListener = wePropsListener
  applyQueryPrefs()
  document.title = 'ihomy'
  syncClock()
  clockTimer = setInterval(syncClock, 1000)
  // 先尝试用属性面板带来的令牌换会话,再决定拉照片还是拉验证码
  await bootstrapToken()
  if (userStore.isLoggedIn) {
    await enterLoggedIn()
  } else {
    loadCaptcha()
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('message', onMessage)
  window.removeEventListener('resize', onWinResize)
  if (window.wallpaperPropertyListener === wePropsListener) delete window.wallpaperPropertyListener
  clearInterval(clockTimer)
  clearInterval(photoRefreshTimer)
  clearTimeout(uiHideTimer)
  stopSlide()
  // 若卸载时还挂着拖拽监听(极端时序),一并摘掉
  window.removeEventListener('pointermove', onWidgetMove)
  window.removeEventListener('pointerup', onWidgetUp)
})
</script>

<style scoped>
.wp { position: fixed; inset: 0; overflow: hidden; color: var(--color-text); }

/* 兜底底色(压在所有光影层之下:SunLightLayer 的 glass-bg 是 z=2) */
.wp-base {
  position: fixed; inset: 0; z-index: 0; pointer-events: none;
  background: linear-gradient(160deg, var(--color-bg) 0%, var(--color-bg-2) 100%);
}

/* 天气 AI 生图底图(z=1,兜底底色之上、光影层之下);无图时 opacity 0 走主题渐变,有图 1s 淡入。
   ::after 自下而上压一层暗色渐变,保证左下时钟/天气行在浅色图片上仍可读 */
.wp-weatherbg {
  position: fixed; inset: 0; z-index: 1; pointer-events: none;
  background-size: cover; background-position: center;
  opacity: 0; transition: opacity 1.2s ease;
}
.wp-weatherbg.on { opacity: 1; }
.wp-weatherbg::after {
  content: ''; position: absolute; inset: 0;
  background: linear-gradient(180deg, rgba(0, 0, 0, .16) 0%, rgba(0, 0, 0, 0) 30%, rgba(0, 0, 0, .02) 55%, rgba(0, 0, 0, .42) 100%);
}
/* 有底图时环境层文字压白(浅色图片上保持可读) */
.wp.has-bg .wp-time, .wp.has-bg .wp-wtext, .wp.has-bg .wp-temp { color: #fff; text-shadow: 0 2px 20px rgba(0, 0, 0, .4); }
.wp.has-bg .wp-meta, .wp.has-bg .wp-wcity { color: rgba(255, 255, 255, .85); text-shadow: 0 1px 12px rgba(0, 0, 0, .35); }

/* ===== 常态环境层:时钟 + 天气(左下,固定不可拖) ===== */
.wp-ambient {
  position: fixed; inset: 0; z-index: 10; pointer-events: none;
  display: flex; flex-direction: column; justify-content: flex-end;
  padding: clamp(28px, 4.5vw, 72px); gap: 2px;
}
.wp-time {
  font-size: clamp(52px, 8.5vw, 118px); font-weight: 250; line-height: 1;
  letter-spacing: .01em; font-variant-numeric: tabular-nums;
}
.wp-meta {
  margin-top: 6px; font-size: 13.5px; letter-spacing: .08em; color: var(--color-text-secondary);
}
.wp-weather { display: flex; align-items: baseline; gap: 10px; margin-top: 14px; }
.wp-temp { font-size: 26px; font-weight: 500; font-variant-numeric: tabular-nums; }
.wp-wtext { font-size: 15px; }
.wp-wcity { font-size: 13px; color: var(--color-text-secondary); }

/* ===== 小组件:常驻可拖拽卡(照片轮播/纪念日/待办;z=30,拖拽中 60,仍低于光影浮尘/体积光 76/78) =====
   不用 backdrop-filter:壁纸长开、光影层持续动画,磨砂会每帧重算(性能规范) */
.wp-widget {
  position: fixed; z-index: 30; cursor: grab; box-sizing: border-box;
  border-radius: 16px; background: rgba(var(--color-card-rgb), .82);
  border: 1px solid var(--color-border); box-shadow: 0 14px 40px rgba(0, 0, 0, .16);
  user-select: none; -webkit-user-drag: none; touch-action: none;
}
.wp-widget.dragging { cursor: grabbing; box-shadow: 0 22px 56px rgba(0, 0, 0, .26); }
/* 拖过一次位置固化为 left/top px(内联),清掉默认定位的 right/bottom/transform */
.wp-widget.placed { right: auto; bottom: auto; transform: none; }
.wpw-photos { left: 50%; top: 45%; transform: translate(-50%, -50%); background: none; border: none; box-shadow: none; border-radius: 0; padding: 0; display: flex; flex-direction: column; align-items: center; }
.wpw-anni { top: clamp(20px, 4vh, 48px); right: clamp(24px, 4vw, 64px); width: 264px; padding: 12px 16px 10px; }
.wpw-task { bottom: clamp(88px, 13vh, 150px); right: clamp(24px, 4vw, 64px); width: 264px; padding: 12px 16px 10px; }
/* 组件面板加进来的三个默认位:左列上/中 + 右列中部(避开左下的时钟与已占的三处)。
   只是初始位置,拖过一次即固化为 px,与屏幕尺寸无关 */
.wpw-feed { top: clamp(20px, 5vh, 56px); left: clamp(24px, 4vw, 64px); width: 288px; padding: 12px 16px 10px; }
.wpw-plan { top: clamp(190px, 34vh, 360px); left: clamp(24px, 4vw, 64px); width: 288px; padding: 12px 16px 10px; }
.wpw-reminder { top: clamp(150px, 27vh, 290px); right: clamp(24px, 4vw, 64px); width: 288px; padding: 12px 16px 10px; }
.wpw-title { font-size: 11.5px; font-weight: 650; letter-spacing: .14em; color: var(--color-text-secondary); margin-bottom: 4px; }
.wpw-body { display: flex; flex-direction: column; }
.wpw-row { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; padding: 7px 0; }
.wpw-row + .wpw-row { border-top: 1px solid var(--color-border); }
.wpw-info { min-width: 0; }
.wpw-name { font-size: 13.5px; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.wpw-sub { font-size: 11px; color: var(--color-text-secondary); margin-top: 2px; font-variant-numeric: tabular-nums; }
.wpw-days { flex-shrink: 0; font-size: 17px; font-weight: 650; font-variant-numeric: tabular-nums; }
.wpw-days-unit { font-size: 11px; font-weight: 500; margin-left: 2px; color: var(--color-text-secondary); }
.wpw-days.today { color: var(--color-accent); font-size: 13px; font-weight: 700; }
.wpw-task-name { flex: 1; }
.wpw-reward { flex-shrink: 0; font-size: 11.5px; font-weight: 600; color: var(--color-brand); font-variant-numeric: tabular-nums; }

/* 照片轮播卡堆 */
.wp-stack { position: relative; width: min(46vw, 640px); aspect-ratio: 4 / 3; }
.wp-pcard {
  position: absolute; inset: 0; border-radius: 14px; overflow: hidden;
  background: var(--color-line); box-shadow: 0 18px 46px rgba(0, 0, 0, .24);
  z-index: var(--z, 10);
  transform: translate(var(--dx, 0), var(--dy, 0)) rotate(var(--rot, 0deg));
  transition: transform .8s cubic-bezier(.22, 1, .36, 1);
}
.wp-pcard img { width: 100%; height: 100%; object-fit: cover; display: block; }
.wp-cap {
  margin-top: 20px; padding: 7px 18px; border-radius: 999px; max-width: 46vw;
  font-size: 13px; color: #fff; background: rgba(0, 0, 0, .38);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}

/* ===== 角落控件:移动浮现、静止隐去(右下,避让左下时钟) ===== */
.wp-ui {
  position: fixed; right: clamp(24px, 4vw, 64px); bottom: clamp(24px, 4vw, 64px); z-index: 40;
  display: flex; align-items: center; gap: 10px; flex-wrap: wrap; justify-content: flex-end;
  opacity: 0; transform: translateY(6px); pointer-events: none;
  transition: opacity .5s ease, transform .5s ease;
}
.wp-ui.visible { opacity: 1; transform: none; pointer-events: auto; }
/* 窄屏(手机/竖屏副屏):右下角控件会换行并压住左下角的日期/天气行,改挂右上角;
   面板此时要排在控件行下方(挂右上时往上排会顶出屏幕) */
@media (max-width: 768px) {
  .wp-ui { top: clamp(18px, 4vw, 28px); bottom: auto; }
  .wp-widgets { order: 1; }
}
.wp-seg {
  display: flex; gap: 2px; padding: 3px; border-radius: 12px;
  background: rgba(var(--color-card-rgb), .74); border: 1px solid var(--color-border);
}
.wp-segbtn {
  all: unset; cursor: pointer; padding: 6px 12px; border-radius: 9px;
  font-size: 12.5px; font-weight: 600; color: var(--color-text-secondary);
  transition: background .2s ease, color .2s ease; white-space: nowrap;
}
.wp-segbtn:hover { color: var(--color-text); }
.wp-segbtn.on { background: var(--color-brand); color: var(--color-brand-text); }

/* 组件面板:撑满一行浮在控件行上方(order:-1);窄屏控件挂右上时改排到下方(见文件末尾 media) */
.wp-widgets {
  order: -1; width: 100%; box-sizing: border-box; padding: 8px;
  border-radius: 12px; background: rgba(var(--color-card-rgb), .86);
  border: 1px solid var(--color-border); box-shadow: 0 14px 40px rgba(0, 0, 0, .18);
}
.wp-widgets-cap {
  font-size: 11px; font-weight: 650; letter-spacing: .14em; color: var(--color-text-secondary);
  padding: 2px 6px 6px;
}
.wp-widgets-row {
  all: unset; cursor: pointer; display: flex; align-items: center; gap: 8px;
  padding: 7px 8px; border-radius: 8px; font-size: 12.5px; color: var(--color-text-secondary);
  transition: background .2s ease, color .2s ease;
}
.wp-widgets-row:hover { background: var(--color-card-2); color: var(--color-text); }
.wp-widgets-row.on { color: var(--color-text); }
.wp-widgets-mark { width: 14px; text-align: center; font-weight: 700; color: var(--color-brand); }

/* ===== 登录卡(z=90,压在体积光 78 之上保证表单清晰;灯光层 pointer-events:none 不挡输入) =====
   遮罩本身 pointer-events:none、只让卡片吃点击 —— 否则未登录时全屏遮罩会把右下角主题切换按钮一起挡住 */
.wp-login {
  position: fixed; inset: 0; z-index: 90; pointer-events: none;
  display: flex; align-items: center; justify-content: center; padding: 24px;
  background: rgba(0, 0, 0, .3);
}
.wp-login-card {
  width: min(360px, 92vw); padding: 30px 26px 24px; border-radius: 16px;
  display: flex; flex-direction: column; gap: 12px; pointer-events: auto;
  background: var(--color-card); border: 1px solid var(--color-border); box-shadow: var(--shadow-hover);
}
.wp-login-title { font-size: 22px; font-weight: 600; letter-spacing: .04em; }
.wp-login-sub { font-size: 12.5px; color: var(--color-text-secondary); margin-bottom: 4px; }
.wp-input {
  width: 100%; box-sizing: border-box; padding: 10px 12px; border-radius: 10px;
  border: 1px solid var(--color-border); background: var(--color-card-2);
  color: var(--color-text); font-size: 14px; font-family: inherit; outline: none;
}
.wp-input:focus { border-color: var(--color-brand); }
.wp-captcha { display: flex; gap: 10px; align-items: center; }
.wp-captcha-img {
  height: 40px; width: 108px; flex-shrink: 0; border-radius: 8px; cursor: pointer;
  object-fit: cover; border: 1px solid var(--color-border);
}
.wp-login-err { font-size: 12.5px; color: var(--color-accent); }
.wp-login-btn {
  all: unset; cursor: pointer; text-align: center; padding: 11px 16px; border-radius: 12px;
  font-size: 14px; font-weight: 600; background: var(--color-brand); color: var(--color-brand-text);
  transition: background .2s ease;
}
.wp-login-btn:hover { background: var(--color-brand-hover); }
.wp-login-btn[disabled] { opacity: .6; cursor: default; }
</style>
