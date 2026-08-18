<!-- 首页:相册 + 可拖拽面板 + 唱片。光影层与导航已全局化(App.vue) -->
<template>
  <div ref="root" class="home-page">
    <!-- 右下角相册模块:近 7 天有新照片→散落拍立得堆;无→闭合相册 -->
    <div class="album-corner">
      <!-- 散落拍立得堆 -->
      <div v-if="recentPhotos.length" class="polaroid-stack">
        <div
          v-for="(p, i) in recentPhotos"
          :key="p.id"
          class="polaroid-pos"
          :style="{
            transform: `rotate(${polaroidLayout[i].rotate}deg) translate(${polaroidLayout[i].dx}px, ${polaroidLayout[i].dy}px)`,
            zIndex: polaroidLayout[i].z,
          }"
        >
          <div class="polaroid" @click="openViewer(i)">
            <img :src="p.url" :alt="p.description || ''" />
            <div v-if="p.description" class="polaroid-caption">{{ p.description }}</div>
          </div>
        </div>
      </div>
      <!-- 闭合相册:近 7 天无新照片 -->
      <div v-else class="album-closed" @click="$router.push('/album')">
        <div class="album-cover">
          <div class="cover-title">{{ family?.name || 'ihomy' }}</div>
          <div class="cover-sub">家庭相册</div>
        </div>
      </div>
    </div>

    <!-- 图片查看器:点击拍立得全屏浏览(原生按钮:翻页左右+关闭右上) -->
    <el-image-viewer
      v-if="viewerVisible"
      :url-list="viewerUrls"
      :initial-index="viewerIdx"
      @close="viewerVisible = false"
    />

    <!-- 左侧:家人动态 + 悬赏/任务(无边框毛玻璃,向外透明渐变) -->
    <!-- 家人动态(可拖拽毛玻璃) -->
    <div class="draggable-panel feed-panel"
      :style="{ left: feedDrag.pos.value.x + 'px', top: feedDrag.pos.value.y + 'px', width: feedDrag.size.value.w + 'px', height: feedDrag.size.value.h + 'px', zIndex: feedDrag.zIndex.value }">
      <div class="drag-handle" @mousedown="feedDrag.onDragStart">
        <span class="handle-grip"></span>
      </div>
      <div class="panel-body">
        <div class="panel-title">{{ $t('home.familyFeed') }}</div>
        <div class="feed-scroll">
          <div v-if="!feeds.length" class="empty-state">
            <span class="empty-hint">{{ $t('home.sillEmpty') }}</span>
            <el-button size="small" type="primary" plain @click="$router.push('/blog')">+ 写博客</el-button>
          </div>
          <div v-for="(f, i) in feeds" :key="i" class="feed-row" @click="goFeed(f)">
            <el-avatar :size="36" :src="f.authorAvatar" class="feed-avatar">{{ (f.authorName || 'U').charAt(0) }}</el-avatar>
            <div class="feed-content">
              <div class="feed-nick">{{ f.authorName || $t('feed.authorFallback') }}</div>
              <div class="feed-bubble">
                <div class="bubble-type">{{ feedTypeLabel(f.type) }}</div>
                <div class="bubble-body">{{ feedSummary(f) }}</div>
                <div class="bubble-time">{{ formatTime(f.createdAt) }}</div>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div class="resize-handle" @mousedown="feedDrag.onResizeStart"></div>
    </div>

    <!-- 悬赏/任务(可拖拽毛玻璃) -->
    <div class="draggable-panel task-panel"
      :style="{ left: taskDrag.pos.value.x + 'px', top: taskDrag.pos.value.y + 'px', width: taskDrag.size.value.w + 'px', height: taskDrag.size.value.h + 'px', zIndex: taskDrag.zIndex.value }">
      <div class="drag-handle" @mousedown="taskDrag.onDragStart">
        <span class="handle-grip"></span>
      </div>
      <div class="panel-body">
        <div class="panel-title">{{ $t('home.tasksRewards') }}</div>
        <div class="task-scroll">
          <div v-if="!tasks.length" class="empty-state">
            <span class="empty-hint">{{ $t('home.noTasks') }}</span>
            <el-button size="small" type="primary" plain @click="$router.push('/task')">+ 新增任务</el-button>
          </div>
          <div v-for="t in tasks.slice(0, 5)" :key="t.id" class="task-row" @click="$router.push('/task')">
            <span class="task-reward" :class="'reward-' + t.rewardType">{{ rewardIcon(t.rewardType) }}</span>
            <div class="task-info">
              <div class="task-title">{{ t.title }}</div>
              <div class="task-meta">{{ taskStatusLabel(t.status) }}</div>
            </div>
          </div>
        </div>
      </div>
      <div class="resize-handle" @mousedown="taskDrag.onResizeStart"></div>
    </div>

    <!-- 右侧:天气面板(默认 180px 只显示当前,点击展开详情) -->
    <div class="draggable-panel weather-panel"
      :style="{ left: weatherDrag.pos.value.x + 'px', top: weatherDrag.pos.value.y + 'px', width: weatherDrag.size.value.w + 'px', height: (weatherExpanded ? 440 : weatherDrag.size.value.h) + 'px', zIndex: weatherDrag.zIndex.value }">
      <div class="drag-handle" @mousedown="weatherDrag.onDragStart">
        <span class="handle-grip"></span>
      </div>
      <div class="panel-body weather-scroll">
        <div v-if="weather" class="weather-main" @click="weatherExpanded = !weatherExpanded">
          <div class="weather-city">{{ weather.city || '济南' }}</div>
          <div class="weather-current">
            <i v-if="weather.iconCode" :class="'qi-' + weather.iconCode" class="weather-icon-float"></i>
            <span class="weather-temp-large">{{ weather.temp }}<span class="temp-unit">°</span></span>
          </div>
          <div class="weather-condition">{{ weatherText }}<el-icon class="weather-expand-icon"><ArrowDown :class="{ flipped: weatherExpanded }" /></el-icon></div>
        </div>
        <div v-else class="weather-loading-text">天气加载中…</div>

        <div v-if="weatherExpanded && weatherDetailData" class="weather-detail">
          <div v-if="weatherDetailData.warning && weatherDetailData.warning.length" class="wd-section wd-warning">
            <div v-for="w in weatherDetailData.warning" :key="w.id" class="wd-warning-item">
              <span class="wd-warn-type">{{ w.typeName }} {{ w.level }}预警</span>
              <span class="wd-warn-text">{{ w.text }}</span>
            </div>
          </div>

          <div v-if="weatherDetailData.daily" class="wd-section">
            <div class="wd-title">未来三天</div>
            <div class="wd-forecast">
              <div v-for="d in weatherDetailData.daily.slice(0, 3)" :key="d.fxDate" class="wd-fc-card">
                <span class="wd-fc-date">{{ formatFcDate(d.fxDate) }}</span>
                <i :class="'qi-' + d.iconDay" class="wd-fc-icon"></i>
                <span class="wd-fc-temp">{{ d.tempMin }}° / {{ d.tempMax }}°</span>
                <span class="wd-fc-text">{{ d.textDay }}</span>
              </div>
            </div>
          </div>

          <div v-if="weatherDetailData.air" class="wd-section wd-air">
            <span class="wd-air-label">空气</span>
            <span class="wd-air-aqi">{{ weatherDetailData.air.aqi }}</span>
            <span class="wd-air-cat">{{ weatherDetailData.air.category }}</span>
            <span class="wd-air-pm">PM2.5 {{ weatherDetailData.air.pm2p5 }}</span>
          </div>

          <div v-if="weatherDetailData.minutely" class="wd-section wd-minutely">
            <span class="wd-minutely-text">{{ weatherDetailData.minutely.short }}</span>
            <span class="wd-minutely-desc">{{ weatherDetailData.minutely.description }}</span>
          </div>

          <div v-if="weatherDetailData.indices" class="wd-section">
            <div class="wd-title">生活指数</div>
            <div class="wd-indices">
              <div v-for="i in weatherDetailData.indices.slice(0, 5)" :key="i.date" class="wd-idx-item">
                <span class="wd-idx-name">{{ i.name }}</span>
                <span class="wd-idx-cat">{{ i.category }}</span>
                <span class="wd-idx-text">{{ i.text }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div class="resize-handle" @mousedown="weatherDrag.onResizeStart"></div>
    </div>

    <!-- 纪念日倒计时(可拖拽毛玻璃) -->
    <div v-if="anniversaries.length" class="draggable-panel anniversary-panel"
      :style="{ left: anniDrag.pos.value.x + 'px', top: anniDrag.pos.value.y + 'px', width: anniDrag.size.value.w + 'px', height: anniDrag.size.value.h + 'px', zIndex: anniDrag.zIndex.value }">
      <div class="drag-handle" @mousedown="anniDrag.onDragStart">
        <span class="handle-grip"></span>
      </div>
      <div class="panel-body">
        <div class="panel-title">{{ $t('home.upcomingEvents') || '近期纪念日' }}</div>
        <div class="anni-scroll">
          <div v-for="(a, i) in anniversaries" :key="i" class="anni-row" @click="$router.push('/anniversary')">
            <div class="anni-info">
              <div class="anni-name">{{ a.label }}</div>
              <div class="anni-date">{{ a.date }}</div>
            </div>
            <div class="anni-days">
              <span class="days-num">{{ a.days }}</span>
              <span class="days-unit">天</span>
            </div>
          </div>
        </div>
      </div>
      <div class="resize-handle" @mousedown="anniDrag.onResizeStart"></div>
    </div>

    <!-- 今日概览:积分签到 + 待办提醒(可拖拽毛玻璃,登录可见) -->
    <div v-if="userStore.isLoggedIn" class="draggable-panel today-panel"
      :style="{ left: todayDrag.pos.value.x + 'px', top: todayDrag.pos.value.y + 'px', width: todayDrag.size.value.w + 'px', height: todayDrag.size.value.h + 'px', zIndex: todayDrag.zIndex.value }">
      <div class="drag-handle" @mousedown="todayDrag.onDragStart">
        <span class="handle-grip"></span>
      </div>
      <div class="panel-body">
        <div class="panel-title">今日</div>
        <div class="today-scroll">
          <div class="today-points">
            <div class="tp-item" @click="$router.push('/points')">
              <span class="tp-num">{{ pointsStats.balance ?? 0 }}</span>
              <span class="tp-label">积分</span>
            </div>
            <div class="tp-item" @click="$router.push('/points')">
              <span class="tp-num">{{ pointsStats.streak ?? 0 }}</span>
              <span class="tp-label">连续天数</span>
            </div>
            <el-button size="small" type="primary" round :disabled="pointsStats.checkedToday" @click="doCheckin">
              {{ pointsStats.checkedToday ? '已签到' : '签到 +' + (pointsStats.todayPoints ?? 5) }}
            </el-button>
          </div>
          <div class="today-reminders">
            <div v-if="!reminders.length" class="empty-state">
              <span class="empty-hint">今日无待办提醒</span>
              <el-button size="small" type="primary" plain @click="$router.push('/reminder')">+ 新增提醒</el-button>
            </div>
            <div v-for="r in reminders.slice(0, 3)" :key="r.id" class="today-reminder" @click="$router.push('/reminder')">
              <span class="tr-dot"></span>
              <span class="tr-title">{{ r.title }}</span>
              <span class="tr-time">{{ (r.remindTime || '').slice(0, 5) }}</span>
            </div>
          </div>
        </div>
      </div>
      <div class="resize-handle" @mousedown="todayDrag.onResizeStart"></div>
    </div>

    <!-- 音乐播放器已全局化到 MusicPlayer.vue(App.vue 挂载) -->

    <!-- 光照测试控制台:左下角,点击"进入光照测试"后显示 -->
    <div v-if="sunLight?.lightTestMode.value" class="light-test-console">
      <div class="lt-header">
        <span class="lt-title">光照测试</span>
        <span class="lt-time">{{ testTimeDisplay }}</span>
        <button class="lt-btn lt-reset" @click="sunLight.stopLightTest" title="重置到真实时间并关闭">⏹</button>
      </div>
      <div class="lt-info">
        <span>高度 {{ sunLight.sunScene.value.altitude?.toFixed(1) }}°</span>
        <span>方位 {{ sunLight.sunScene.value.azimuth?.toFixed(1) }}°</span>
        <span class="lt-phase">{{ testPhase }}</span>
      </div>
      <div class="lt-info" v-if="sunLight?.sunInfo.value">
        <span>日出 {{ sunLight.sunInfo.value.sunrise || '--' }}</span>
        <span>日落 {{ sunLight.sunInfo.value.sunset || '--' }}</span>
      </div>
      <div class="lt-controls">
        <button class="lt-btn" @click="sunLight.stepLightTest(-1)" title="后退 5 分钟">⏮</button>
        <button class="lt-btn lt-main" @click="sunLight.pauseLightTest">{{ sunLight.lightTestPaused.value ? '▶' : '⏸' }}</button>
        <button class="lt-btn" @click="sunLight.stepLightTest(1)" title="前进 5 分钟">⏭</button>
      </div>
      <div class="lt-weather">
        <button class="lt-btn" :class="{ active: sunLight.weatherMode.value === 'clear' }" @click="sunLight.setWeather('clear', 0)" title="晴天">☀️</button>
        <button class="lt-btn" :class="{ active: sunLight.weatherMode.value === 'cloud' }" @click="sunLight.setWeather('cloud', 0)" title="多云">☁️</button>
        <button class="lt-btn" :class="{ active: sunLight.weatherMode.value === 'rain' }" @click="sunLight.setWeather('rain', sunLight.precipLevel.value || 1)" title="下雨">🌧️</button>
        <button class="lt-btn" :class="{ active: sunLight.weatherMode.value === 'snow' }" @click="sunLight.setWeather('snow', sunLight.precipLevel.value || 1)" title="下雪">❄️</button>
      </div>
      <div v-if="sunLight.weatherMode.value === 'rain' || sunLight.weatherMode.value === 'snow'" class="lt-sliders">
        <label class="lt-slider-row"><span>{{ sunLight.weatherMode.value === 'rain' ? '雨量' : '雪量' }}</span><input type="range" min="1" max="6" v-model.number="sunLight.precipLevel.value" class="lt-slider" @input="sunLight.setWeather(sunLight.weatherMode.value, sunLight.precipLevel.value)" /></label>
      </div>
      <div class="lt-sliders">
        <label class="lt-slider-row"><span>色温</span><input type="range" min="0" max="100" v-model.number="sunLight.lampTemp.value" class="lt-slider" /></label>
        <label class="lt-slider-row"><span>亮度</span><input type="range" min="0" max="100" v-model.number="sunLight.lampBrightness.value" class="lt-slider" /></label>
      </div>
      <div class="lt-progress"><div class="lt-progress-fill" :style="{ width: ((sunLight.slotIdx.value / 288) * 100) + '%' }"></div></div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, inject, nextTick, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useI18n } from 'vue-i18n'
import { publicApi, homeApi, taskApi, pointsApi, reminderApi } from '@/api'
import { gsap } from 'gsap'
import { ElMessage, ElImageViewer } from 'element-plus'
import { useDragResize } from '@/utils/useDragResize'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const { t } = useI18n()

// 注入全局光影状态(用于光照测试控制台)
const sunLight = inject(SUN_LIGHT_KEY)

const root = ref(null)
let ctx

const family = ref({})
const feeds = ref([])
const tasks = ref([])
const allPhotos = ref([])
const viewerVisible = ref(false)
const viewerIdx = ref(0)
const weather = computed(() => sunLight?.weather.value)
const weatherDetailData = ref(null)
const anniversaries = ref([])
// 可拖拽面板:动态/任务/天气/纪念日/今日(位置/大小持久化到 localStorage)
const feedDrag = useDragResize({ x: 244, y: 80, w: 380, h: 300, storageKey: 'ihomy:panel:feed', marginLeft: 220 })
const taskDrag = useDragResize({ x: 244, y: 400, w: 380, h: 240, storageKey: 'ihomy:panel:task', marginLeft: 220 })
const weatherDrag = useDragResize({ x: 1000, y: 210, w: 320, h: 180, storageKey: 'ihomy:panel:weather', marginLeft: 220 })
const anniDrag = useDragResize({ x: 1000, y: 410, w: 280, h: 200, storageKey: 'ihomy:panel:anniversary', marginLeft: 220 })
const todayDrag = useDragResize({ x: 244, y: 660, w: 380, h: 200, storageKey: 'ihomy:panel:today', marginLeft: 220 })
const weatherExpanded = ref(false)
const resetPanelLayout = () => {
  feedDrag.reset(); taskDrag.reset(); weatherDrag.reset(); anniDrag.reset(); todayDrag.reset()
  weatherExpanded.value = false
  ElMessage.success('面板布局已重置')
}
// 今日概览:积分签到 + 待办提醒
const pointsStats = ref({})
const reminders = ref([])
const loadPoints = async () => {
  if (!userStore.isLoggedIn) return
  try { pointsStats.value = await pointsApi.stats() } catch (e) {}
}
const loadReminders = async () => {
  if (!userStore.isLoggedIn) return
  try {
    const r = await reminderApi.list()
    reminders.value = (Array.isArray(r) ? r : []).filter(x => x.done !== 1)
  } catch (e) {}
}
const doCheckin = async () => {
  try {
    const r = await pointsApi.checkin()
    ElMessage.success(`签到成功 +${r.points} 积分,连续 ${r.streak} 天`)
    await loadPoints()
  } catch (e) {}
}

// 近 7 天可访问照片(用于拍立得堆),最多 7 张
const SEVEN_DAYS = 7 * 86400000
const recentPhotos = computed(() => {
  const now = Date.now()
  return allPhotos.value
    .filter(p => p.createdAt && now - new Date(p.createdAt).getTime() < SEVEN_DAYS)
    .slice(0, 7)
})
// 拍立得随机姿态:每次 recentPhotos 变化时重新生成一次,而非 computed 内每次访问都重算(否则视觉跳动)
const polaroidLayout = ref([])
watch(recentPhotos, (ps) => {
  polaroidLayout.value = ps.map((p, i) => ({
    rotate: (Math.random() - 0.5) * 30,
    dx: (Math.random() - 0.5) * 240,
    dy: (Math.random() - 0.5) * 120,
    z: i + 1,
  }))
}, { immediate: true })
const viewerUrls = computed(() => recentPhotos.value.map(p => p.url))
const openViewer = (idx) => { viewerIdx.value = idx; viewerVisible.value = true }

// 音乐播放器已移至全局组件 MusicPlayer.vue

const feedTypeLabel = (type) => type === 'blog' ? '博客' : type === 'diary' ? '日记' : type === 'photo' ? '照片' : ''

// 光照测试:时隙索引→时间显示 + 阶段标签
const testTimeDisplay = computed(() => {
  if (!sunLight) return ''
  const idx = sunLight.slotIdx.value
  const totalMin = idx * 5
  const h = Math.floor(totalMin / 60)
  const m = totalMin % 60
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`
})
const testPhase = computed(() => {
  if (!sunLight) return ''
  const s = sunLight.sunScene.value
  if (s.isNight) return '夜间'
  const p = s.dayProgress ?? 0
  if (p < 0.1) return '日出'
  if (p < 0.25) return '清晨'
  if (p < 0.75) return '日间'
  if (p < 0.9) return '傍晚'
  return '日落'
})
const feedSummary = (f) => {
  if (f.type === 'blog') return f.title || ''
  if (f.type === 'diary') return (f.content || '').slice(0, 40)
  if (f.type === 'photo') return `${f.count || 0} 张照片`
  return ''
}
const formatTime = (d) => {
  if (!d) return ''
  const date = new Date(d)
  const now = new Date()
  const diff = (now - date) / 1000
  if (diff < 3600) return Math.floor(diff / 60) + ' 分钟前'
  if (diff < 86400) return Math.floor(diff / 3600) + ' 小时前'
  return date.toLocaleDateString('zh-CN')
}

const goFeed = (f) => {
  if (f.type === 'blog' && f.id) router.push(`/blog/${f.id}`)
  else if (f.type === 'diary') router.push('/diary')
  else if (f.type === 'photo') router.push('/album')
}

const rewardIcon = (t) => t === 1 ? '🎁' : t === 2 ? '📦' : '⭕'
const taskStatusLabel = (s) => ({ 0: '待领取', 1: '进行中', 2: '待确认', 3: '已完成', 4: '已取消' }[s] || '')

const weatherText = computed(() => {
  if (!weather.value) return ''
  return weather.value.text || ''
})

const formatFcDate = (dateStr) => {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const weekdays = ['日', '一', '二', '三', '四', '五', '六']
  return `${d.getMonth() + 1}/${d.getDate()} 周${weekdays[d.getDay()]}`
}
const loadWeatherDetail = async () => {
  try {
    const res = await fetch('/api/public/weather/detail')
    if (res.ok) {
      const json = await res.json()
      if (json.code === 0 && json.data) weatherDetailData.value = json.data
    }
  } catch (e) {}
}

const homeId = computed(() => route.query.home_id || '')
const hid = computed(() => route.query.hid || '')

const loadAll = async () => {
  // 三类请求并行发起,各自容错;不再串行 await(原版每等一个 RTT)
  const homePromise = publicApi.getHome(homeId.value || undefined, hid.value || undefined)
    .then(pub => {
      family.value = pub.family || {}
      anniversaries.value = (pub.stats || {}).upcomingEvents || []
      return pub.photos || []
    })
    .catch(() => [])

  const feedPromise = (hid.value
    ? publicApi.getFeed(20, undefined, hid.value)
    : homeId.value
      ? publicApi.getFeed(20, homeId.value)
      : userStore.isGuest
        ? publicApi.getFeed(20)
        : homeApi.getFeed(20))
    .then(r => { feeds.value = r || [] })
    .catch(() => { feeds.value = [] })

  const taskPromise = userStore.isLoggedIn
    ? taskApi.list()
        .then(r => { tasks.value = Array.isArray(r) ? r : (r.records || []) })
        .catch(() => {})
    : Promise.resolve()

  const [photos] = await Promise.all([homePromise, feedPromise, taskPromise])
  allPhotos.value = photos
}

onMounted(() => {
  loadAll()
  loadWeatherDetail()
  loadPoints()
  loadReminders()
  nextTick(() => {
    if (!root.value) return
    ctx = gsap.context(() => {
      gsap.from('.feed-panel', { x: -30, autoAlpha: 0, duration: 0.8, delay: 0.2 })
      gsap.from('.task-panel', { x: -30, autoAlpha: 0, duration: 0.8, delay: 0.3 })
      gsap.from('.weather-panel', { x: 30, autoAlpha: 0, duration: 0.8, delay: 0.3 })
      gsap.from('.anniversary-panel', { x: 30, autoAlpha: 0, duration: 0.8, delay: 0.35 })
      gsap.from('.today-panel', { y: 40, autoAlpha: 0, duration: 0.8, delay: 0.35 })
      gsap.from('.album-corner', { scale: 0.95, autoAlpha: 0, duration: 0.8, delay: 0.4 })
      gsap.from('.polaroid', { y: 40, autoAlpha: 0, duration: 0.6, stagger: 0.08, delay: 0.3 })
      gsap.from('.album-closed', { scale: 0.9, autoAlpha: 0, duration: 0.8, delay: 0.3 })}, root.value)
  })
})

onUnmounted(() => {
  ctx?.revert()
})
</script>

<style scoped>
.home-page {
  min-height: 100vh;
  position: relative;
}
/* 光影层(背景/体积光/阴影/台灯/灰尘)已全局化到 SunLightLayer.vue(App.vue 挂载) */

/* === 右下角相册模块:拍立得堆 / 闭合相册 === */
.album-corner {
  position: fixed;
  right: 5vw;
  bottom: 5vh;
  z-index: 10;
  width: 27.5vw;
  height: 27.5vw;
  min-height: 308px;
}

/* 散落拍立得堆:白边相纸 + 随机旋转 + 投影 */
.polaroid-stack {
  position: relative;
  width: 100%;
  height: 100%;
}
.polaroid-pos {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 220px;
  margin-left: -110px;
  margin-top: -125px;
}
.polaroid {
  background: #fff;
  padding: 10px 10px 38px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.25), 0 2px 6px rgba(0, 0, 0, 0.15);
  border-radius: 2px;
  cursor: pointer;
  transition: transform 0.3s ease, box-shadow 0.3s ease;
}
.polaroid:hover {
  transform: scale(1.08);
  box-shadow: 0 16px 40px rgba(0, 0, 0, 0.35), 0 4px 12px rgba(0, 0, 0, 0.2);
}
.polaroid-pos:hover {
  z-index: 99 !important;
}
.polaroid img {
  width: 100%;
  aspect-ratio: 4/3;
  object-fit: cover;
  display: block;
  border-radius: 1px;
}
.polaroid-caption {
  position: absolute;
  bottom: 6px;
  left: 8px;
  right: 8px;
  font-size: 11px;
  color: #5a4a3a;
  text-align: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 闭合相册:平躺封面,温暖木色 */
.album-closed {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 420px;
  height: 315px;
  margin-left: -210px;
  margin-top: -157px;
  transform: rotate(-4deg);
  cursor: pointer;
  transition: transform 0.3s ease, box-shadow 0.3s ease;
}
.album-closed:hover {
  transform: rotate(0deg) scale(1.05);
  box-shadow: 0 16px 40px rgba(0, 0, 0, 0.35);
}
.album-cover {
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg, #8B6F47 0%, #6B5435 50%, #5a4530 100%);
  border-radius: 4px;
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.3), 0 2px 8px rgba(0, 0, 0, 0.15) inset;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 16px;
}
.cover-title {
  font-size: 18px;
  font-weight: 700;
  color: #F5E6C8;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.4);
  margin-bottom: 4px;
}
.cover-sub {
  font-size: 13px;
  color: rgba(245, 230, 200, 0.7);
  letter-spacing: 2px;
}

/* 顶栏导航已移到 AppSidebar.vue(左侧导航) */

/* 毛玻璃通用样式:透明度 25% + 实边框 + 大圆角,无外部渐变 */
/* 调整位置:改 border-radius(圆角)、border(边框)、background(透明度) */
.glass-panel {
  background: rgba(255, 255, 255, 0.25);
  backdrop-filter: blur(30px) saturate(1.4);
  -webkit-backdrop-filter: blur(30px) saturate(1.4);
  border: 1px solid rgba(255, 255, 255, 0.5);
  border-radius: 28px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.08), inset 0 1px 0 rgba(255, 255, 255, 0.4);
  color: #3A2E22;
}

/* 左侧面板:宽度 380,向中间靠拢 */
/* === 可拖拽面板通用样式 === */
.draggable-panel {
  position: fixed;
  z-index: 20;
  display: flex;
  flex-direction: column;
  background: rgba(255, 255, 255, 0.42);
  backdrop-filter: blur(30px) saturate(1.5);
  -webkit-backdrop-filter: blur(30px) saturate(1.5);
  border: 1px solid rgba(255, 255, 255, 0.6);
  border-radius: 24px;
  box-shadow: 0 12px 40px rgba(58, 46, 34, 0.14), 0 2px 8px rgba(58, 46, 34, 0.08),
              inset 0 1px 0 rgba(255, 255, 255, 0.7), inset 0 -1px 0 rgba(58, 46, 34, 0.05);
  color: #3A2E22;
  overflow: hidden;
  transition: box-shadow 0.3s ease, background-color 1s ease, border-color 1s ease, color 1s ease;
  /* 隔离合层:backdrop-filter 不因子元素滚动而触发全页重绘 */
  contain: layout style;
}
.draggable-panel:hover {
  box-shadow: 0 16px 48px rgba(58, 46, 34, 0.2), 0 4px 12px rgba(58, 46, 34, 0.1),
              inset 0 1px 0 rgba(255, 255, 255, 0.8), inset 0 -1px 0 rgba(58, 46, 34, 0.05);
}
/* 拖动条:面板顶部 */
.drag-handle {
  height: 24px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: grab;
  user-select: none;
}
.drag-handle:active { cursor: grabbing; }
.handle-grip {
  width: 40px;
  height: 4px;
  border-radius: 2px;
  background: rgba(58, 46, 34, 0.2);
}
/* 面板内容区 */
.panel-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0;
}
/* 调整大小手柄:右下角 */
.resize-handle {
  position: absolute;
  bottom: 0;
  right: 0;
  width: 20px;
  height: 20px;
  cursor: nwse-resize;
  background: linear-gradient(135deg, transparent 50%, rgba(58, 46, 34, 0.15) 50%);
  border-bottom-right-radius: 28px;
}
/* 右锚定面板用左下角把手:右边缘固定,左边缘可拖动 */
.resize-handle-left {
  left: 0;
  right: auto;
  cursor: nesw-resize;
  background: linear-gradient(45deg, transparent 50%, rgba(58, 46, 34, 0.15) 50%);
  border-bottom-right-radius: 0;
  border-bottom-left-radius: 28px;
}
html.dark .resize-handle,
html.dark .resize-handle-left {
  background: linear-gradient(135deg, transparent 50%, rgba(232, 220, 200, 0.25) 50%);
}
html.dark .resize-handle-left {
  background: linear-gradient(45deg, transparent 50%, rgba(232, 220, 200, 0.25) 50%);
}
.feed-panel { }
.panel-title {
  padding: 28px 32px 14px;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 1px;
  opacity: 0.7;
  flex-shrink: 0;
}
.feed-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 0 28px 24px;
  transform: translateZ(0);
}
.feed-scroll::-webkit-scrollbar { width: 0; }
.empty-hint {
  text-align: center;
  padding: 12px 12px;
  font-size: 14px;
  opacity: 0.5;
  font-style: italic;
}
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 20px 12px;
}
.feed-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 14px;
  cursor: pointer;
}
.feed-avatar { flex-shrink: 0; }
/* nick + bubble 同一个容器,bubble 紧贴 nick 下方,整体上对齐头像顶部 */
.feed-content {
  flex: 1;
  min-width: 0;
  margin-top: 2px;
}
.feed-nick {
  font-size: 12px;
  font-weight: 600;
  color: rgba(58, 46, 34, 0.85);
  line-height: 16px;
  margin-bottom: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.feed-bubble {
  background: rgba(255, 255, 255, 0.45);
  border-radius: 4px 14px 14px 14px;
  padding: 10px 14px;
  transition: background 0.2s, transform 0.2s;
}
.feed-row:hover .feed-bubble {
  background: rgba(255, 255, 255, 0.65);
  transform: translateX(-3px);
}
.bubble-type { font-size: 12px; opacity: 0.6; margin-bottom: 3px; }
.bubble-body {
  font-size: 14px;
  line-height: 1.55;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.bubble-time { font-size: 11px; opacity: 0.5; margin-top: 4px; text-align: right; }

.task-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.task-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 0 28px 24px;
  transform: translateZ(0);
}
.task-scroll::-webkit-scrollbar { width: 0; }
.task-row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 14px;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.2s;
}
.task-row:hover { background: rgba(58, 46, 34, 0.06); }
.task-reward { font-size: 20px; }
.task-info { flex: 1; min-width: 0; }
.task-title {
  font-size: 15px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.task-meta { font-size: 12px; opacity: 0.6; margin-top: 3px; }

/* === 天气面板 === */
.weather-panel { text-align: center; }
.weather-scroll { overflow-y: auto; max-height: 100%; transform: translateZ(0); }
.weather-scroll::-webkit-scrollbar { width: 0; }
.weather-main { padding: 4px 20px 12px; cursor: pointer; transition: background 0.2s; border-radius: 12px; }
.weather-main:hover { background: rgba(255, 255, 255, 0.15); }
.weather-expand-icon { font-size: 11px; margin-left: 4px; vertical-align: middle; transition: transform 0.3s; }
.weather-expand-icon .flipped { transform: rotate(180deg); }
.weather-city {
  font-size: 13px;
  opacity: 0.6;
  letter-spacing: 1px;
}
.weather-current {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin: 6px 0 2px;
}
.weather-icon-float {
  font-size: 36px;
  animation: icon-float 3s ease-in-out infinite;
  display: inline-block;
}
@keyframes icon-float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-4px); }
}
.weather-temp-large {
  font-size: 42px;
  font-weight: 700;
  line-height: 1;
  transition: color 0.6s ease;
}
.temp-unit { font-size: 24px; font-weight: 400; opacity: 0.7; }
.weather-condition {
  font-size: 14px;
  opacity: 0.8;
  font-weight: 500;
}

.weather-loading-text { font-size: 13px; opacity: 0.5; padding: 20px; text-align: center; }

.weather-detail {
  padding: 0 20px 16px;
  text-align: left;
}
.wd-section { margin-bottom: 10px; }
.wd-section:last-child { margin-bottom: 0; }
.wd-title {
  font-size: 11px;
  opacity: 0.5;
  margin-bottom: 6px;
  font-weight: 600;
  letter-spacing: 0.5px;
  text-transform: uppercase;
}

/* 3 天预报:横向卡片 */
.wd-forecast { display: flex; gap: 6px; }
.wd-fc-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  padding: 8px 4px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.3);
  transition: background 0.2s, transform 0.2s;
  cursor: default;
}
.wd-fc-card:hover { background: rgba(255, 255, 255, 0.5); transform: translateY(-2px); }
.wd-fc-date { font-size: 11px; opacity: 0.6; }
.wd-fc-icon { font-size: 28px; }
.wd-fc-temp { font-size: 12px; font-weight: 600; }
.wd-fc-text { font-size: 10px; opacity: 0.6; text-align: center; }

/* 空气质量 */
.wd-air {
  display: flex;
  align-items: center;
  gap: 8px;
  background: rgba(100, 200, 100, 0.12);
  border-radius: 12px;
  padding: 8px 12px;
}
.wd-air-label { font-size: 11px; opacity: 0.6; }
.wd-air-aqi { font-weight: 700; font-size: 16px; }
.wd-air-cat { font-size: 11px; opacity: 0.8; }
.wd-air-pm { font-size: 10px; opacity: 0.5; margin-left: auto; }

/* 预警 */
.wd-warning-item {
  background: rgba(255, 180, 100, 0.2);
  border-radius: 12px;
  padding: 8px 12px;
  margin-bottom: 4px;
}
.wd-warn-type { font-weight: 600; color: #d97706; display: block; font-size: 12px; }
.wd-warn-text { font-size: 11px; opacity: 0.8; display: block; margin-top: 3px; line-height: 1.4; }

/* 分钟降水 */
.wd-minutely {
  background: rgba(100, 150, 255, 0.12);
  border-radius: 12px;
  padding: 8px 12px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.wd-minutely-text { font-weight: 600; font-size: 13px; }
.wd-minutely-desc { font-size: 11px; opacity: 0.7; }

/* 生活指数 */
.wd-indices { display: flex; flex-direction: column; gap: 4px; }
.wd-idx-item {
  display: grid;
  grid-template-columns: 56px 48px 1fr;
  gap: 6px;
  font-size: 11px;
  align-items: baseline;
  padding: 4px 0;
}
.wd-idx-name { opacity: 0.6; }
.wd-idx-cat { font-weight: 600; }
.wd-idx-text { opacity: 0.7; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

/* 纪念日面板 */
.anniversary-panel {
}
.anni-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 0 28px 18px;
  transform: translateZ(0);
}
.anni-scroll::-webkit-scrollbar { width: 0; }
.anni-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 12px;
  border-bottom: 1px solid rgba(58, 46, 34, 0.08);
  cursor: pointer;
  transition: background 0.2s;
}
.anni-row:last-child { border-bottom: none; }
.anni-row:hover { background: rgba(58, 46, 34, 0.05); border-radius: 8px; }
.anni-info { flex: 1; min-width: 0; }
.anni-name {
  font-size: 14px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.anni-date { font-size: 12px; opacity: 0.6; margin-top: 2px; }
.anni-days {
  display: flex;
  align-items: baseline;
  gap: 2px;
  flex-shrink: 0;
  margin-left: 8px;
}
.days-num { font-size: 20px; font-weight: 700; color: #A8483A; }
.days-unit { font-size: 12px; opacity: 0.7; }

/* === 今日概览面板:积分签到 + 待办提醒 === */
.today-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 0 24px 18px;
  transform: translateZ(0);
}
.today-scroll::-webkit-scrollbar { width: 0; }
.today-points {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 2px 0 12px;
}
.tp-item { display: flex; flex-direction: column; align-items: center; cursor: pointer; padding: 4px 10px; border-radius: 10px; transition: background 0.2s; }
.tp-item:hover { background: rgba(58, 46, 34, 0.06); }
.tp-num { font-size: 20px; font-weight: 700; color: #A8483A; line-height: 1; }
.tp-label { font-size: 11px; opacity: 0.6; margin-top: 3px; }
.today-reminders {
  border-top: 1px solid rgba(58, 46, 34, 0.08);
  padding-top: 6px;
}
.today-reminder {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 6px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s;
}
.today-reminder:hover { background: rgba(58, 46, 34, 0.05); }
.tr-dot { width: 6px; height: 6px; border-radius: 50%; background: #C9A876; flex-shrink: 0; }
.tr-title { flex: 1; min-width: 0; font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.tr-time { font-size: 11px; opacity: 0.5; font-variant-numeric: tabular-nums; }
html.dark .tp-num { color: #D4886A; }

/* 响应式 */
@media (max-width: 1280px) {
  .draggable-panel { font-size: 13px; }
}
@media (max-width: 960px) {
  .feed-panel, .task-panel, .anniversary-panel, .today-panel { display: none; }
  .album-corner { width: 220px; height: 200px; right: 16px; bottom: 16px; }
  .polaroid { width: 120px; margin-left: -60px; margin-top: -68px; }
  .topbar-date { display: none; }
}

/* === 光照测试控制台(左下角) === */
.light-test-console {
  position: fixed;
  left: 240px;
  bottom: 24px;
  z-index: 80;
  background: rgba(20, 28, 45, 0.72);
  backdrop-filter: blur(20px) saturate(1.3);
  -webkit-backdrop-filter: blur(20px) saturate(1.3);
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 14px;
  padding: 14px 18px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
  color: #fff;
  min-width: 260px;
}
.lt-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; gap: 8px; }
.lt-title { font-size: 13px; font-weight: 600; opacity: 0.8; }
.lt-time { font-size: 22px; font-weight: 700; font-variant-numeric: tabular-nums; flex: 1; text-align: center; }
.lt-info { display: flex; gap: 12px; font-size: 11px; opacity: 0.7; margin-bottom: 6px; }
.lt-phase { color: #C9A876; font-weight: 600; }
.lt-controls { display: flex; gap: 6px; justify-content: center; margin-bottom: 8px; }
.lt-weather { display: flex; gap: 6px; justify-content: center; margin-bottom: 8px; }
.lt-sliders { display: flex; flex-direction: column; gap: 6px; margin-bottom: 8px; }
.lt-slider-row { display: flex; align-items: center; gap: 8px; font-size: 11px; opacity: 0.8; }
.lt-slider { flex: 1; cursor: pointer; }
.lt-btn {
  background: rgba(255,255,255,0.1);
  border: 1px solid rgba(255,255,255,0.2);
  border-radius: 8px;
  color: #fff;
  padding: 5px 12px;
  font-size: 15px;
  cursor: pointer;
  transition: background 0.2s;
}
.lt-btn:hover { background: rgba(255,255,255,0.2); }
.lt-btn.active { background: rgba(255,200,100,0.3); border-color: rgba(255,200,100,0.5); }
.lt-btn.lt-main { font-size: 17px; }
.lt-btn.lt-reset { background: rgba(244,67,54,0.3); border-color: rgba(244,67,54,0.5); }
.lt-btn.lt-reset:hover { background: rgba(244,67,54,0.5); }
.lt-progress { height: 3px; background: rgba(255,255,255,0.1); border-radius: 2px; overflow: hidden; }
.lt-progress-fill { height: 100%; background: linear-gradient(90deg, #C9A876, #A8483A); transition: width 0.2s; }
</style>
