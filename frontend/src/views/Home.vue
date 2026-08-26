<!-- 首页:12列×9行栅格仪表盘,编辑模式可拖拽/缩放/增删组件 -->
<template>
  <div ref="root" class="home-page" :class="{ 'edit-mode': editMode }">
    <PhotoViewer v-model:visible="viewerVisible" :photos="sevenDayPhotos" :initial-index="viewerIdx" />

    <!-- 编辑模式工具栏(hover隐藏) -->
    <Transition name="fade">
      <div v-if="editMode" class="edit-toolbar edit-toolbar-hover">
        <span class="edit-label">编辑模式 · 拖拽移动 · 右下角调整大小</span>
        <el-button size="small" @click="resetLayout">恢复默认</el-button>
        <el-button size="small" type="primary" @click="finishEdit">完成</el-button>
      </div>
    </Transition>

    <!-- 栅格背景(编辑模式可见,出现动画) -->
    <Transition name="grid-fade" appear>
      <div v-if="editMode" class="grid-overlay" :style="gridOverlayStyle">
        <div v-for="i in 108" :key="i" class="grid-cell" :style="{ animationDelay: (i * 5) + 'ms' }"></div>
      </div>
    </Transition>

    <!-- 组件 -->
    <template v-for="w in visibleWidgets" :key="w.uid">
      <div
        class="dash-card"
        :class="[w.id, { 'edit-active': editMode, dragging: w._dragging, 'h-1': w.h === 1 }]"
        :style="cardStyle(w)"
        @click="bringToFront(w)"
      >
        <div v-if="editMode" class="drag-bar" @mousedown="onDragStart($event, w)"><span class="grip"></span></div>
        <button v-if="editMode" class="del-btn" @click.stop="removeWidget(w)">✕</button>
        <div v-if="editMode" class="resize-corner" @mousedown.stop="onResizeStart($event, w)"></div>

        <div class="card-inner">
          <!-- 家人动态 -->
          <template v-if="w.id === 'feed'">
            <div class="card-head">家人动态</div>
            <div class="card-scroll">
              <div v-if="!feeds.length" class="empty-hint">暂无动态</div>
              <div v-for="(f, i) in feeds" :key="i" class="feed-row" @click="!editMode && goFeed(f)">
                <el-avatar :size="36" :src="f.authorAvatar" class="feed-avatar">{{ (f.authorName || 'U').charAt(0) }}</el-avatar>
                <div class="feed-content">
                  <div class="feed-nick">{{ f.authorName || '家人' }}</div>
                  <div class="feed-bubble">
                    <div class="bubble-type">{{ feedTypeLabel(f.type) }}</div>
                    <div class="bubble-body">{{ feedSummary(f) }}</div>
                    <div class="bubble-time">{{ formatTime(f.createdAt) }}</div>
                  </div>
                </div>
              </div>
            </div>
          </template>

          <!-- 悬赏任务 -->
          <template v-else-if="w.id === 'task'">
            <div class="card-head">悬赏任务</div>
            <div class="card-scroll">
              <div v-if="!tasks.length" class="empty-hint">暂无任务</div>
              <div v-for="t in tasks.slice(0, 5)" :key="t.id" class="task-row" @click="!editMode && $router.push('/task')">
                <span class="task-reward">{{ rewardIcon(t.rewardType) }}</span>
                <div class="task-info"><div class="task-title">{{ t.title }}</div><div class="task-meta"><span class="task-status-dot" :class="'s-' + t.status"></span>{{ taskStatusLabel(t.status) }}</div></div>
              </div>
            </div>
          </template>

          <!-- 今日 -->
          <template v-else-if="w.id === 'today'">
            <div class="card-head">今日</div>
            <div class="card-scroll">
              <div class="today-points">
                <div class="tp-item" @click="!editMode && $router.push('/points')"><span class="tp-num">{{ pointsStats.balance ?? 0 }}</span><span class="tp-label">积分</span></div>
                <div class="tp-item" @click="!editMode && $router.push('/points')"><span class="tp-num">{{ pointsStats.streak ?? 0 }}</span><span class="tp-label">连续天数</span></div>
                <el-button size="small" type="primary" round :disabled="pointsStats.checkedToday" @click="doCheckin">{{ pointsStats.checkedToday ? '已签到' : '签到 +' + (pointsStats.todayPoints ?? 5) }}</el-button>
              </div>
              <div class="today-reminders">
                <div v-if="!reminders.length" class="empty-hint">今日无待办</div>
                <div v-for="r in reminders.slice(0, 5)" :key="r.id" class="today-reminder" @click="!editMode && $router.push('/reminder')"><span class="tr-dot"></span><span class="tr-title">{{ r.title }}</span><span class="tr-time">{{ (r.remindTime || '').slice(0, 5) }}</span></div>
              </div>
            </div>
          </template>

          <!-- 天气 -->
          <template v-else-if="w.id === 'weather'">
            <div class="card-head">天气</div>
            <div class="card-scroll weather-scroll">
              <div v-if="weather" class="weather-main">
                <div class="weather-city">{{ weather.city || '济南' }}</div>
                <div class="weather-current"><i v-if="weather.iconCode" :class="'qi-' + weather.iconCode" class="weather-icon-float"></i><span class="weather-temp-large">{{ weather.temp }}<span class="temp-unit">°</span></span></div>
                <div class="weather-condition">{{ weatherText }}</div>
              </div>
              <div v-else class="weather-loading-text">天气加载中…</div>
              <div v-if="weatherDetailData" class="weather-detail">
                <div v-if="weatherDetailData.warning && weatherDetailData.warning.length" class="wd-section">
                  <div v-for="w in weatherDetailData.warning" :key="w.id" class="wd-warning-item"><span class="wd-warn-type">{{ w.typeName }} {{ w.level }}预警</span><span class="wd-warn-text">{{ w.text }}</span></div>
                </div>
                <div v-if="weatherDetailData.daily" class="wd-section">
                  <div class="wd-title">未来三天</div>
                  <div class="wd-forecast"><div v-for="d in weatherDetailData.daily.slice(0, 3)" :key="d.fxDate" class="wd-fc-card"><span class="wd-fc-date">{{ formatFcDate(d.fxDate) }}</span><i :class="'qi-' + d.iconDay" class="wd-fc-icon"></i><span class="wd-fc-temp">{{ d.tempMin }}° / {{ d.tempMax }}°</span><span class="wd-fc-text">{{ d.textDay }}</span></div></div>
                </div>
                <div v-if="weatherDetailData.air" class="wd-section wd-air"><span class="wd-air-label">空气</span><span class="wd-air-aqi">{{ weatherDetailData.air.aqi }}</span><span class="wd-air-cat">{{ weatherDetailData.air.category }}</span><span class="wd-air-pm">PM2.5 {{ weatherDetailData.air.pm2p5 }}</span></div>
              </div>
            </div>
          </template>

          <!-- 纪念日 -->
          <template v-else-if="w.id === 'anni'">
            <div class="card-head">近期纪念日</div>
            <div class="card-scroll">
              <div v-for="(a, i) in anniversaries" :key="i" class="anni-row" @click="!editMode && $router.push('/anniversary')">
                <div class="anni-info"><div class="anni-name">{{ a.label }}</div><div class="anni-date">{{ a.date }}</div></div>
                <div class="anni-days"><span class="days-num">{{ a.days }}</span><span class="days-unit">天</span></div>
              </div>
            </div>
          </template>

          <!-- 今日推荐 -->
          <template v-else-if="w.id === 'recipe'">
            <div class="card-head">今日推荐</div>
            <div class="card-scroll">
              <div v-if="todayRecipes.length" class="recipe-list">
                <router-link v-for="r in todayRecipes" :key="r.id" :to="`/kitchen/recipe/${r.id}`" class="recipe-item">
                  <img v-if="r.coverImage" :src="r.coverImage" class="recipe-cover" />
                  <div v-else class="recipe-cover placeholder">🍳</div>
                  <span class="recipe-name">{{ r.name }}</span>
                </router-link>
              </div>
              <div v-else class="widget-empty">暂无推荐<div class="widget-empty-hint">去厨房添加菜谱</div></div>
            </div>
            <router-link to="/kitchen" class="card-more">查看菜谱 →</router-link>
          </template>

          <!-- 物品寻找 -->
          <template v-else-if="w.id === 'search'">
            <div class="card-head">物品寻找</div>
            <div class="card-scroll">
              <el-input v-model="itemKeyword" placeholder="输入物品名称..." clearable prefix-icon="Search" @keyup.enter="searchItems" style="margin-bottom: 8px" />
              <div v-if="searchResults.length" class="search-results">
                <div v-for="r in searchResults.slice(0, 6)" :key="r.id" class="search-item"><span class="search-name">{{ r.name }}</span><span class="search-loc">{{ [r.house_name, r.room_name, r.furniture_name].filter(Boolean).join(' ') }}</span></div>
              </div>
              <div v-else-if="searched" class="widget-empty">未找到相关物品</div>
              <div v-else class="widget-empty">输入关键词搜索</div>
            </div>
            <router-link to="/item" class="card-more">物品管理 →</router-link>
          </template>

          <!-- 愿望单 -->
          <template v-else-if="w.id === 'wish'">
            <div class="card-head">愿望单</div>
            <div class="card-scroll">
              <div v-if="wishes.length" class="wish-list">
                <div v-for="w in wishes.slice(0, 8)" :key="w.id" class="wish-item" :class="{ done: w.status === 'ACHIEVED' }"><span class="wish-dot" :class="w.status"></span><span class="wish-name">{{ w.title }}</span></div>
              </div>
              <div v-else class="widget-empty">暂无愿望<div class="widget-empty-hint">去愿望单记录家庭心愿</div></div>
            </div>
            <router-link to="/wish" class="card-more">查看全部 →</router-link>
          </template>

          <!-- 本月收支 -->
          <template v-else-if="w.id === 'finance'">
            <div class="card-head">本月收支</div>
            <div class="finance-body">
              <div class="fin-item"><span class="fin-label">收入</span><span class="fin-val income">+{{ bookSummary?.income || 0 }}</span></div>
              <div class="fin-item"><span class="fin-label">支出</span><span class="fin-val expense">-{{ bookSummary?.expense || 0 }}</span></div>
              <div class="fin-item"><span class="fin-label">结余</span><span class="fin-val" :class="(bookSummary?.balance || 0) >= 0 ? 'income' : 'expense'">{{ bookSummary?.balance || 0 }}</span></div>
            </div>
            <router-link to="/book" class="card-more">查看明细 →</router-link>
          </template>

          <!-- 音乐 -->
          <template v-else-if="w.id === 'music'">
            <div class="card-head">背景音乐</div>
            <div class="card-scroll">
              <div v-if="musicPlaylist && musicTracks.length" class="music-list">
                <div class="music-pl-name">🎵 {{ musicPlaylist.name }}</div>
                <div v-for="t in musicTracks.slice(0, 6)" :key="t.id" class="music-item">
                  <span class="music-title">{{ t.title || t.name || '未知曲目' }}</span>
                  <span class="music-artist">{{ t.artist || '' }}</span>
                </div>
              </div>
              <div v-else class="widget-empty">
                <div class="music-empty-icon">🎵</div>
                未设置背景音乐歌单
                <div class="widget-empty-hint">前往设置页选择背景歌单</div>
              </div>
            </div>
            <router-link v-if="userStore.isLoggedIn" to="/settings" class="card-more">设置音乐 →</router-link>
          </template>

          <!-- 拍立得 -->
          <template v-else-if="w.id === 'album'">
            <div class="album-container" :style="{ '--polaroid-w': Math.min(140, Math.max(80, w.w * 30)) + 'px' }">
              <div v-if="recentPhotos.length" class="polaroid-stack">
                <div v-for="(p, i) in recentPhotos" :key="p.id" class="polaroid-pos" :style="{ transform: `rotate(${polaroidLayout[i]?.rotate || 0}deg) translate(${polaroidLayout[i]?.dx || 0}px, ${polaroidLayout[i]?.dy || 0}px)`, zIndex: polaroidLayout[i]?.z || 1 }">
                  <div class="polaroid" @click="!editMode && openViewer(i)"><img :src="p.url" :alt="p.description || ''" /><div v-if="p.description" class="polaroid-caption">{{ p.description }}</div></div>
                </div>
              </div>
              <div v-else class="album-closed" @click="!editMode && $router.push('/album')"><div class="album-cover"><div class="cover-title">{{ family?.name || 'ihomy' }}</div><div class="cover-sub">家庭相册</div></div></div>
            </div>
          </template>
        </div>
      </div>
    </template>

    <!-- 拖拽幽灵(从侧边栏拖出组件时) -->
    <div v-if="ghostActive" class="drag-ghost" :class="{ 'ghost-grown': ghostGrown }" :style="{ left: ghostX + 'px', top: ghostY + 'px' }">
      <span class="ghost-label">{{ ghostLabel }}</span>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, inject, nextTick, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import { useI18n } from 'vue-i18n'
import { publicApi, homeApi, taskApi, pointsApi, reminderApi, bookApi, wishApi, itemApi, kitchenApi, musicApi } from '@/api'
import { gsap } from 'gsap'
import { ElMessage } from 'element-plus'
import PhotoViewer from '@/components/PhotoViewer.vue'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'
import { useWidgetDrag } from '@/utils/useWidgetDrag'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const appStore = useAppStore()
const { t } = useI18n()
const sunLight = inject(SUN_LIGHT_KEY)
const root = ref(null)
let ctx

const editMode = computed(() => appStore.homeEditMode)
const finishEdit = () => { appStore.toggleHomeEditMode() }

// ========== 栅格尺寸:自适应屏幕分辨率 ==========
const COLS = 12
const ROWS = 9
const GAP = 40
const SIDEBAR_W = 220
const MARGIN = { top: 32, right: 40, bottom: 40, left: SIDEBAR_W + 40 }

const winW = ref(window.innerWidth)
const winH = ref(window.innerHeight)
const onWinResize = () => { winW.value = window.innerWidth; winH.value = window.innerHeight }
window.addEventListener('resize', onWinResize)
onUnmounted(() => window.removeEventListener('resize', onWinResize))

const cellW = computed(() => {
  const gridW = winW.value - MARGIN.left - MARGIN.right
  return Math.max(0, (gridW - GAP * (COLS - 1)) / COLS)
})
const cellH = computed(() => {
  const gridH = winH.value - MARGIN.top - MARGIN.bottom
  return Math.max(0, (gridH - GAP * (ROWS - 1)) / ROWS)
})

const cardStyle = (w) => {
  const cw = cellW.value
  const ch = cellH.value
  const left = MARGIN.left + w.col * (cw + GAP)
  const top = MARGIN.top + w.row * (ch + GAP)
  const width = w.w * cw + (w.w - 1) * GAP
  const height = w.h * ch + (w.h - 1) * GAP
  const s = { left: left + 'px', top: top + 'px', width: width + 'px', height: height + 'px' }
  if (w._z) s.zIndex = w._z
  return s
}

const gridOverlayStyle = computed(() => ({
  left: MARGIN.left + 'px',
  top: MARGIN.top + 'px',
  right: MARGIN.right + 'px',
  bottom: MARGIN.bottom + 'px',
  '--cell-w': cellW.value + 'px',
  '--cell-h': cellH.value + 'px',
}))

// ========== 数据 ==========
const family = ref({})
const feeds = ref([])
const tasks = ref([])
const allPhotos = ref([])
const viewerVisible = ref(false)
const viewerIdx = ref(0)
const weather = computed(() => sunLight?.weather.value)
const weatherDetailData = ref(null)
const anniversaries = ref([])
const pointsStats = ref({})
const reminders = ref([])
const bookSummary = ref(null)
const wishes = ref([])
const itemKeyword = ref('')
const searchResults = ref([])
const searched = ref(false)
const todayRecipes = ref([])
const musicPlaylist = ref(null)
const musicTracks = ref([])

const loadPoints = async () => { if (userStore.isLoggedIn) { try { pointsStats.value = await pointsApi.stats() } catch (e) {} } }
const loadReminders = async () => { if (userStore.isLoggedIn) { try { const r = await reminderApi.list(); reminders.value = (Array.isArray(r) ? r : []).filter(x => x.done !== 1) } catch (e) {} } }
const loadBookSummary = async () => { if (userStore.isLoggedIn) { try { bookSummary.value = await bookApi.summary() } catch (e) {} } }
const loadWishes = async () => { if (userStore.isLoggedIn) { try { wishes.value = await wishApi.list() } catch (e) {} } }
const searchItems = async () => { if (!itemKeyword.value.trim()) { searchResults.value = []; searched.value = false; return }; try { searchResults.value = await itemApi.list({ keyword: itemKeyword.value.trim() }); searched.value = true } catch (e) { searchResults.value = []; searched.value = true } }
const loadTodayRecipes = async () => { if (userStore.isLoggedIn) { try { const data = await kitchenApi.menu(); todayRecipes.value = data?.todayRecommend || [] } catch (e) {} } }
const doCheckin = async () => { try { const r = await pointsApi.checkin(); ElMessage.success(`签到成功 +${r.points} 积分,连续 ${r.streak} 天`); await loadPoints() } catch (e) {} }
const loadWeatherDetail = async () => { try { const res = await fetch('/api/public/weather/detail'); if (res.ok) { const json = await res.json(); if (json.code === 0 && json.data) weatherDetailData.value = json.data } } catch (e) {} }
const loadMusic = async () => { try { const r = await musicApi.getBackground(); musicPlaylist.value = r?.playlist || null; musicTracks.value = r?.tracks || [] } catch (e) {} }

const SEVEN_DAYS = 7 * 86400000
const sevenDayPhotos = computed(() => { const now = Date.now(); return allPhotos.value.filter(p => p.createdAt && now - new Date(p.createdAt).getTime() < SEVEN_DAYS) })
const recentPhotos = computed(() => {
  const ps = sevenDayPhotos.value.slice()
  for (let i = ps.length - 1; i > 0; i--) { const j = Math.floor(Math.random() * (i + 1)); [ps[i], ps[j]] = [ps[j], ps[i]] }
  return ps.slice(0, 7)
})
const polaroidLayout = ref([])
watch(recentPhotos, (ps) => { polaroidLayout.value = ps.map((p, i) => ({ rotate: (Math.random() - 0.5) * 50, dx: (Math.random() - 0.5) * 340, dy: (Math.random() - 0.5) * 140, z: i + 1 })) }, { immediate: true })
const openViewer = (idx) => {
  const all = sevenDayPhotos.value
  const clicked = recentPhotos.value[idx]
  const realIdx = clicked ? all.findIndex(p => p.id === clicked.id) : 0
  viewerIdx.value = realIdx < 0 ? 0 : realIdx
  viewerVisible.value = true
}

const feedTypeLabel = (type) => type === 'blog' ? '博客' : type === 'diary' ? '日记' : type === 'photo' ? '照片' : ''
const feedSummary = (f) => { if (f.type === 'blog') return f.title || ''; if (f.type === 'diary') return (f.content || '').slice(0, 40); if (f.type === 'photo') return `${f.count || 0} 张照片`; return '' }
const formatTime = (d) => { if (!d) return ''; const date = new Date(d); const now = new Date(); const diff = (now - date) / 1000; if (diff < 3600) return Math.floor(diff / 60) + ' 分钟前'; if (diff < 86400) return Math.floor(diff / 3600) + ' 小时前'; return date.toLocaleDateString('zh-CN') }
const goFeed = (f) => { if (f.type === 'blog' && f.id) router.push(`/blog/${f.id}`); else if (f.type === 'diary') router.push('/diary'); else if (f.type === 'photo') router.push('/album') }
const rewardIcon = (t) => t === 1 ? '🎁' : t === 2 ? '📦' : '⭕'
const taskStatusLabel = (s) => ({ 0: '待领取', 1: '进行中', 2: '待确认', 3: '已完成', 4: '已取消' }[s] || '')
const weatherText = computed(() => weather.value?.text || '')
const formatFcDate = (dateStr) => { if (!dateStr) return ''; const d = new Date(dateStr); const weekdays = ['日', '一', '二', '三', '四', '五', '六']; return `${d.getMonth() + 1}/${d.getDate()} 周${weekdays[d.getDay()]}` }

const homeId = computed(() => route.query.home_id || '')
const hid = computed(() => route.query.hid || '')
const loadAll = async () => {
  const homePromise = publicApi.getHome(homeId.value || undefined, hid.value || undefined).then(pub => { family.value = pub.family || {}; anniversaries.value = (pub.stats || {}).upcomingEvents || []; return pub.photos || [] }).catch(() => [])
  const feedPromise = (hid.value ? publicApi.getFeed(20, undefined, hid.value) : homeId.value ? publicApi.getFeed(20, homeId.value) : publicApi.getFeed(20)).then(r => { feeds.value = r || [] }).catch(() => { feeds.value = [] })
  const taskPromise = userStore.isLoggedIn ? taskApi.list().then(r => { tasks.value = (Array.isArray(r) ? r : (r.records || [])).filter(t => t.status !== 'CANCELLED') }).catch(() => {}) : Promise.resolve()
  const [photos] = await Promise.all([homePromise, feedPromise, taskPromise])
  allPhotos.value = photos
}

// ========== 布局状态 ==========
const DEFAULT_LAYOUT = [
  { id: 'feed', col: 0, row: 0, w: 5, h: 5 },
  { id: 'anni', col: 5, row: 0, w: 3, h: 4 },
  { id: 'weather', col: 9, row: 1, w: 3, h: 2 },
  { id: 'today', col: 0, row: 5, w: 3, h: 2 },
  { id: 'recipe', col: 8, row: 3, w: 4, h: 4 },
  { id: 'wish', col: 5, row: 4, w: 3, h: 2 },
  { id: 'album', col: 3, row: 6, w: 5, h: 3 },
  { id: 'finance', col: 9, row: 7, w: 2, h: 2 },
]

const STORAGE_KEY = 'ihomy:dashboard:layout'
const loadLayout = () => {
  try { const raw = localStorage.getItem(STORAGE_KEY); if (raw) return JSON.parse(raw) } catch (e) {}
  return null
}
const saveLayout = () => { try { localStorage.setItem(STORAGE_KEY, JSON.stringify(widgets.value.map(w => ({ id: w.id, col: w.col, row: w.row, w: w.w, h: w.h })))) } catch (e) {} }

const makeWidget = (cfg) => ({ ...cfg, uid: cfg.id + '_' + Date.now() + '_' + Math.random().toString(36).slice(2, 6), _z: 0, _dragging: false })

const widgets = ref((loadLayout() || DEFAULT_LAYOUT).map(makeWidget))

const visibleWidgets = computed(() => widgets.value.filter(w => {
  if (['today', 'task', 'recipe', 'search', 'wish', 'finance', 'music'].includes(w.id)) return userStore.isLoggedIn
  if (w.id === 'anni') return anniversaries.value.length > 0
  return true
}))

const resetLayout = () => {
  widgets.value = DEFAULT_LAYOUT.map(makeWidget)
  saveLayout()
  ElMessage.success('布局已重置')
}

const removeWidget = (w) => {
  widgets.value = widgets.value.filter(x => x.uid !== w.uid)
  saveLayout()
}

// ========== 拖拽 + 缩放(栅格吸附) ==========
let zCounter = 20
let dragState = null

const bringToFront = (w) => {
  zCounter = Math.min(zCounter + 1, 59)
  w._z = zCounter
}

const onDragStart = (e, w) => {
  if (e.target.classList.contains('resize-corner') || e.target.classList.contains('del-btn')) return
  w._dragging = true
  zCounter = Math.min(zCounter + 1, 59)
  w._z = zCounter
  dragState = { w, startX: e.clientX, startY: e.clientY, startCol: w.col, startRow: w.row }
  e.preventDefault()
  window.addEventListener('mousemove', onMouseMove)
  window.addEventListener('mouseup', onMouseUp)
}

const onResizeStart = (e, w) => {
  w._dragging = true
  zCounter = Math.min(zCounter + 1, 59)
  w._z = zCounter
  dragState = { w, startX: e.clientX, startY: e.clientY, startW: w.w, startH: w.h, isResize: true }
  e.preventDefault()
  window.addEventListener('mousemove', onMouseMove)
  window.addEventListener('mouseup', onMouseUp)
}

const onMouseMove = (e) => {
  if (!dragState) return
  const { w } = dragState
  const cw = cellW.value
  const ch = cellH.value
  const dx = e.clientX - dragState.startX
  const dy = e.clientY - dragState.startY
  const dCol = Math.round(dx / (cw + GAP))
  const dRow = Math.round(dy / (ch + GAP))
  if (dragState.isResize) {
    w.w = Math.max(1, Math.min(COLS - w.col, dragState.startW + dCol))
    w.h = Math.max(1, Math.min(ROWS - w.row, dragState.startH + dRow))
  } else {
    w.col = Math.max(0, Math.min(COLS - w.w, dragState.startCol + dCol))
    w.row = Math.max(0, Math.min(ROWS - w.h, dragState.startRow + dRow))
  }
}

const onMouseUp = () => {
  if (dragState) { dragState.w._dragging = false; saveLayout() }
  dragState = null
  window.removeEventListener('mousemove', onMouseMove)
  window.removeEventListener('mouseup', onMouseUp)
}

onUnmounted(() => { window.removeEventListener('mousemove', onMouseMove); window.removeEventListener('mouseup', onMouseUp) })

// ========== 从侧边栏拖入组件 ==========
const WIDGET_LABELS = { feed: '家人动态', task: '悬赏任务', today: '今日', weather: '天气', anni: '纪念日', recipe: '今日推荐', search: '物品寻找', wish: '愿望单', finance: '本月收支', album: '相册', music: '音乐' }
const ghostActive = ref(false)
const ghostGrown = ref(false)
const ghostX = ref(0)
const ghostY = ref(0)
const ghostLabel = ref('')

const { dragging: wdDragging, dragType: wdType, dragX: wdX, dragY: wdY, crossed: wdCrossed, onDrop } = useWidgetDrag()

watch(wdDragging, (active) => {
  if (active) {
    ghostActive.value = true
    ghostGrown.value = false
    ghostLabel.value = WIDGET_LABELS[wdType.value] || wdType.value
  } else {
    ghostActive.value = false
    ghostGrown.value = false
  }
})

watch(wdX, () => { ghostX.value = wdX.value; ghostY.value = wdY.value })
watch(wdCrossed, (crossed) => {
  if (crossed) { ghostGrown.value = true }
})

onDrop((type, x, y) => {
  const cw = cellW.value
  const ch = cellH.value
  const col = Math.max(0, Math.min(COLS - 4, Math.round((x - MARGIN.left) / (cw + GAP))))
  const row = Math.max(0, Math.min(ROWS - 5, Math.round((y - MARGIN.top) / (ch + GAP))))
  widgets.value.push(makeWidget({ id: type, col, row, w: 4, h: 5 }))
  saveLayout()
  ElMessage.success(`已添加 ${WIDGET_LABELS[type] || type} 组件`)
})

onMounted(() => {
  loadAll(); loadWeatherDetail(); loadPoints(); loadReminders(); loadBookSummary(); loadWishes(); loadTodayRecipes(); loadMusic()
  nextTick(() => { if (!root.value) return; ctx = gsap.context(() => { gsap.from('.dash-card', { y: 16, autoAlpha: 0, duration: 0.4, stagger: 0.04, ease: 'power2.out' }) }, root.value) })
})
onBeforeUnmount(() => { ctx?.revert() })
</script>

<style scoped>
.home-page { min-height: 100vh; }
.home-page.edit-mode { user-select: none; }

/* 编辑模式工具栏(hover时隐藏,不挡操作) */
.edit-toolbar {
  position: fixed; top: 32px; right: 40px; z-index: 70;
  display: flex; align-items: center; gap: 10px;
  padding: 8px 16px; border-radius: 12px;
  background: rgba(255,255,255,0.6); backdrop-filter: blur(20px) saturate(1.2);
  border: 1px solid rgba(255,255,255,0.5); box-shadow: 0 4px 16px rgba(58,46,34,0.1);
  transition: opacity 0.3s ease, transform 0.3s ease;
}
.edit-toolbar-hover:hover { opacity: 0; transform: translateY(-10px); pointer-events: none; }
html.dark .edit-toolbar { background: rgba(30,42,72,0.6); border-color: rgba(255,255,255,0.1); }
.edit-label { font-size: 12px; opacity: 0.6; }

/* 栅格背景 */
.grid-overlay {
  position: fixed; z-index: 15; pointer-events: none;
  display: grid;
  grid-template-columns: repeat(12, var(--cell-w));
  grid-template-rows: repeat(9, var(--cell-h));
  gap: 40px;
}
.grid-cell { border: 1px dashed rgba(184,140,110,0.15); border-radius: 8px; opacity: 0; animation: cellAppear 0.4s ease forwards; }
html.dark .grid-cell { border-color: rgba(212,178,152,0.1); }
@keyframes cellAppear { from { opacity: 0; transform: scale(0.8); } to { opacity: 1; transform: scale(1); } }
.grid-fade-enter-active, .grid-fade-leave-active { transition: opacity 0.3s ease; }
.grid-fade-enter-from, .grid-fade-leave-to { opacity: 0; }

/* 卡片通用 */
.dash-card {
  position: fixed; z-index: 20;
  display: flex; flex-direction: column;
  background: rgba(255,255,255,0.42);
  backdrop-filter: blur(28px) saturate(1.4);
  -webkit-backdrop-filter: blur(28px) saturate(1.4);
  border: 1px solid rgba(255,255,255,0.5);
  border-radius: 20px;
  box-shadow: 0 8px 28px rgba(58,46,34,0.1), inset 0 1px 0 rgba(255,255,255,0.6);
  color: #3A2E22; overflow: hidden;
  transition: transform 0.25s ease, box-shadow 0.25s ease;
  contain: layout style;
}
.dash-card:not(.edit-active):hover { box-shadow: 0 12px 40px rgba(58,46,34,0.18); }
.dash-card.edit-active {
  cursor: default;
  border-color: rgba(184,140,110,0.3);
  box-shadow: 0 4px 16px rgba(184,140,110,0.15);
  transition: left 0.15s cubic-bezier(0.4,0,0.2,1), top 0.15s cubic-bezier(0.4,0,0.2,1), width 0.15s cubic-bezier(0.4,0,0.2,1), height 0.15s cubic-bezier(0.4,0,0.2,1), box-shadow 0.25s ease;
}
.dash-card.dragging { opacity: 0.9; }
html.dark .dash-card { background: rgba(30,42,72,0.5); border-color: rgba(255,255,255,0.1); color: #E8DCC8; box-shadow: 0 8px 28px rgba(0,0,0,0.25), inset 0 1px 0 rgba(255,255,255,0.06); }

.card-inner { flex: 1; display: flex; flex-direction: column; overflow: hidden; min-height: 0; }
.dash-card.edit-active .card-inner { pointer-events: none; }
.card-head { padding: 10px 18px 6px; font-size: 13px; font-weight: 600; opacity: 0.7; flex-shrink: 0; transition: opacity 0.3s ease, max-height 0.3s ease, padding 0.3s ease, margin 0.3s ease; max-height: 30px; overflow: hidden; }
html.dark .card-head { opacity: 0.75; }
/* h=1时标题行消失,内容占满 */
.dash-card.h-1 .card-head { opacity: 0; max-height: 0; padding: 0; margin: 0; }
.dash-card.h-1 .card-scroll { padding-top: 10px; }
.card-scroll { flex: 1; overflow-y: auto; padding: 0 18px 14px; min-height: 0; transform: translateZ(0); }
.card-scroll::-webkit-scrollbar { width: 0; }
.card-more { font-size: 13px; color: #b88c6e; text-decoration: none; padding: 0 18px 10px; display: block; font-weight: 500; transition: color 0.15s, transform 0.15s; }
.card-more:hover { color: #a06a4e; }
html.dark .card-more { color: #d4b298; }
.empty-hint { text-align: center; padding: 16px 12px; font-size: 13px; opacity: 0.4; font-style: italic; }
.widget-empty { font-size: 12px; color: #9a9088; padding: 14px 0; text-align: center; line-height: 1.6; }
.widget-empty-hint { font-size: 11px; color: #b0a89e; margin-top: 4px; }
html.dark .widget-empty { color: rgba(232,220,200,0.3); }

/* 编辑模式拖拽条/删除/缩放 */
.drag-bar { height: 18px; flex-shrink: 0; display: flex; align-items: center; justify-content: center; cursor: grab; }
.drag-bar:active { cursor: grabbing; }
.grip { width: 32px; height: 3px; border-radius: 2px; background: rgba(184,140,110,0.3); }
.del-btn { position: absolute; top: 4px; right: 4px; width: 20px; height: 20px; border: none; border-radius: 50%; background: rgba(201,116,116,0.15); color: #c97474; font-size: 11px; cursor: pointer; z-index: 10; display: flex; align-items: center; justify-content: center; }
.del-btn:hover { background: rgba(201,116,116,0.3); }
.resize-corner { position: absolute; bottom: 0; right: 0; width: 18px; height: 18px; cursor: nwse-resize; background: linear-gradient(135deg, transparent 50%, rgba(184,140,110,0.25) 50%); border-bottom-right-radius: 20px; z-index: 10; }

/* 家人动态 */
.feed-row { display: flex; align-items: flex-start; gap: 10px; margin-bottom: 10px; cursor: pointer; }
.feed-avatar { flex-shrink: 0; }
.feed-content { flex: 1; min-width: 0; margin-top: 2px; }
.feed-nick { font-size: 12px; font-weight: 600; color: rgba(58,46,34,0.8); margin-bottom: 2px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
html.dark .feed-nick { color: rgba(232,220,200,0.8); }
.feed-bubble { background: rgba(255,255,255,0.4); border-radius: 4px 14px 14px 14px; padding: 8px 12px; transition: background 0.2s, transform 0.2s; }
.feed-row:hover .feed-bubble { background: rgba(255,255,255,0.6); }
html.dark .feed-bubble { background: rgba(255,255,255,0.06); }
html.dark .feed-row:hover .feed-bubble { background: rgba(255,255,255,0.1); }
.bubble-type { font-size: 11px; opacity: 0.4; margin-bottom: 2px; }
.bubble-body { font-size: 13px; line-height: 1.5; display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; }
.bubble-time { font-size: 10px; opacity: 0.3; margin-top: 3px; text-align: right; }

/* 任务 */
.task-row { display: flex; align-items: center; gap: 10px; padding: 6px 8px; border-radius: 8px; cursor: pointer; transition: background 0.2s; }
.task-row:hover { background: rgba(58,46,34,0.05); }
html.dark .task-row:hover { background: rgba(255,255,255,0.04); }
.task-reward { font-size: 16px; }
.task-info { flex: 1; min-width: 0; }
.task-title { font-size: 13px; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.task-meta { font-size: 11px; opacity: 0.4; margin-top: 2px; display: flex; align-items: center; gap: 4px; }
.task-status-dot { width: 6px; height: 6px; border-radius: 50%; flex-shrink: 0; background: #C9A876; }
.task-status-dot.s-0 { background: #C9A876; } .task-status-dot.s-1 { background: #6b9b6b; } .task-status-dot.s-2 { background: #d4a843; } .task-status-dot.s-3 { background: #8a9a8a; } .task-status-dot.s-4 { background: #b06a58; opacity: 0.5; }
html.dark .task-status-dot.s-0 { background: #c4a884; } html.dark .task-status-dot.s-1 { background: #7dba7d; } html.dark .task-status-dot.s-2 { background: #d4b86a; } html.dark .task-status-dot.s-3 { background: #6a7a6a; } html.dark .task-status-dot.s-4 { background: #c97474; opacity: 0.5; }

/* 今日 */
.today-points { display: flex; align-items: center; gap: 14px; padding: 2px 0 10px; }
.tp-item { display: flex; flex-direction: column; align-items: center; cursor: pointer; padding: 4px 8px; border-radius: 10px; transition: background 0.2s; }
.tp-item:hover { background: rgba(58,46,34,0.05); }
html.dark .tp-item:hover { background: rgba(255,255,255,0.04); }
.tp-num { font-size: 20px; font-weight: 700; color: #A8483A; line-height: 1; }
.tp-label { font-size: 11px; opacity: 0.45; margin-top: 3px; }
html.dark .tp-num { color: #d4b298; }
.today-reminders { border-top: 1px solid rgba(58,46,34,0.06); padding-top: 4px; }
html.dark .today-reminders { border-color: rgba(232,220,200,0.05); }
.today-reminder { display: flex; align-items: center; gap: 8px; padding: 6px 4px; border-radius: 8px; cursor: pointer; transition: background 0.2s; }
.today-reminder:hover { background: rgba(58,46,34,0.04); }
html.dark .today-reminder:hover { background: rgba(255,255,255,0.04); }
.tr-dot { width: 6px; height: 6px; border-radius: 50%; background: #C9A876; flex-shrink: 0; }
.tr-title { flex: 1; min-width: 0; font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.tr-time { font-size: 11px; opacity: 0.35; font-variant-numeric: tabular-nums; }

/* 天气 */
.weather-scroll { text-align: center; }
.weather-main { padding: 4px 0 10px; }
.weather-city { font-size: 13px; opacity: 0.5; }
.weather-current { display: flex; align-items: center; justify-content: center; gap: 8px; margin: 4px 0 2px; }
.weather-icon-float { font-size: 36px; animation: icon-float 3s ease-in-out infinite; display: inline-block; }
@keyframes icon-float { 0%,100% { transform: translateY(0); } 50% { transform: translateY(-4px); } }
.weather-temp-large { font-size: 42px; font-weight: 700; line-height: 1; }
.temp-unit { font-size: 24px; opacity: 0.6; }
.weather-condition { font-size: 14px; opacity: 0.7; font-weight: 500; }
.weather-loading-text { font-size: 13px; opacity: 0.4; padding: 20px; }
.weather-detail { text-align: left; padding: 0 12px 12px; }
.wd-section { margin-bottom: 8px; }
.wd-section:last-child { margin-bottom: 0; }
.wd-title { font-size: 11px; opacity: 0.4; margin-bottom: 4px; font-weight: 600; text-transform: uppercase; }
.wd-forecast { display: flex; gap: 4px; }
.wd-fc-card { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 2px; padding: 6px 2px; border-radius: 10px; background: rgba(255,255,255,0.2); }
html.dark .wd-fc-card { background: rgba(255,255,255,0.05); }
.wd-fc-date { font-size: 10px; opacity: 0.5; } .wd-fc-icon { font-size: 24px; } .wd-fc-temp { font-size: 11px; font-weight: 600; } .wd-fc-text { font-size: 9px; opacity: 0.5; }
.wd-air { display: flex; align-items: center; gap: 8px; background: rgba(100,200,100,0.1); border-radius: 10px; padding: 6px 10px; }
.wd-air-label { font-size: 11px; opacity: 0.5; } .wd-air-aqi { font-weight: 700; font-size: 16px; } .wd-air-cat { font-size: 11px; opacity: 0.7; } .wd-air-pm { font-size: 10px; opacity: 0.4; margin-left: auto; }
.wd-warning-item { background: rgba(255,180,100,0.15); border-radius: 10px; padding: 6px 10px; margin-bottom: 4px; }
.wd-warn-type { font-weight: 600; color: #d97706; display: block; font-size: 12px; } .wd-warn-text { font-size: 11px; opacity: 0.7; display: block; margin-top: 2px; line-height: 1.4; }

/* 纪念日 */
.anni-row { display: flex; align-items: center; justify-content: space-between; padding: 8px 6px; border-bottom: 1px solid rgba(58,46,34,0.05); cursor: pointer; transition: background 0.2s; }
.anni-row:last-child { border-bottom: none; }
.anni-row:hover { background: rgba(58,46,34,0.04); border-radius: 8px; }
html.dark .anni-row { border-color: rgba(232,220,200,0.05); }
html.dark .anni-row:hover { background: rgba(255,255,255,0.04); }
.anni-info { flex: 1; min-width: 0; }
.anni-name { font-size: 13px; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.anni-date { font-size: 11px; opacity: 0.4; margin-top: 2px; }
.anni-days { display: flex; align-items: baseline; gap: 2px; flex-shrink: 0; margin-left: 6px; }
.days-num { font-size: 20px; font-weight: 700; color: #A8483A; }
.days-unit { font-size: 11px; opacity: 0.5; }
html.dark .days-num { color: #d4b298; }

/* 愿望单 */
.wish-list { display: flex; flex-direction: column; gap: 5px; }
.wish-item { display: flex; align-items: center; gap: 8px; font-size: 12px; color: #3A2E22; padding: 4px 6px; border-radius: 8px; transition: background 0.2s; }
html.dark .wish-item { color: #E8DCC8; }
.wish-item.done { opacity: 0.4; text-decoration: line-through; }
.wish-item:hover { background: rgba(58,46,34,0.04); }
html.dark .wish-item:hover { background: rgba(255,255,255,0.04); }
.wish-dot { width: 6px; height: 6px; border-radius: 50%; flex-shrink: 0; background: #c4a884; }
.wish-dot.ACHIEVED { background: #6b9b6b; } .wish-dot.ABANDONED { background: #b06a58; }
.wish-name { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

/* 物品寻找 */
.search-results { display: flex; flex-direction: column; gap: 5px; }
.search-item { display: flex; flex-direction: column; gap: 2px; padding: 4px 6px; border-bottom: 1px solid rgba(58,46,34,0.04); border-radius: 6px; transition: background 0.2s; }
.search-item:last-child { border: none; }
.search-item:hover { background: rgba(58,46,34,0.04); }
html.dark .search-item:hover { background: rgba(255,255,255,0.04); }
.search-name { font-size: 12px; font-weight: 500; color: #3A2E22; }
html.dark .search-name { color: #E8DCC8; }
.search-loc { font-size: 10px; color: #9a9088; }

/* 菜谱 */
.recipe-list { display: flex; flex-direction: column; gap: 6px; }
.recipe-item { display: flex; align-items: center; gap: 10px; text-decoration: none; padding: 4px 6px; border-radius: 8px; transition: background 0.2s; }
.recipe-item:hover { background: rgba(58,46,34,0.04); }
html.dark .recipe-item:hover { background: rgba(255,255,255,0.04); }
.recipe-cover { width: 36px; height: 36px; border-radius: 8px; object-fit: cover; flex-shrink: 0; }
.recipe-cover.placeholder { display: flex; align-items: center; justify-content: center; background: #ede5d8; font-size: 16px; }
html.dark .recipe-cover.placeholder { background: rgba(232,220,200,0.06); }
.recipe-name { font-size: 12px; font-weight: 500; color: #3A2E22; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
html.dark .recipe-name { color: #E8DCC8; }

/* 收支 */
.finance-body { display: flex; gap: 20px; padding: 4px 18px 8px; }
.fin-item { display: flex; flex-direction: column; gap: 2px; }
.fin-label { font-size: 10px; opacity: 0.4; color: #9a9088; }
.fin-val { font-size: 18px; font-weight: 700; font-variant-numeric: tabular-nums; }
.fin-val.income { color: #6b9b6b; } .fin-val.expense { color: #b06a58; }
html.dark .fin-val.income { color: #7dba7d; } html.dark .fin-val.expense { color: #d9806a; }

/* 音乐 */
.music-list { display: flex; flex-direction: column; gap: 4px; }
.music-pl-name { font-size: 13px; font-weight: 600; color: #b88c6e; margin-bottom: 4px; }
html.dark .music-pl-name { color: #d4b298; }
.music-item { display: flex; align-items: center; gap: 8px; padding: 4px 6px; border-radius: 8px; transition: background 0.2s; }
.music-item:hover { background: rgba(58,46,34,0.04); }
html.dark .music-item:hover { background: rgba(255,255,255,0.04); }
.music-title { font-size: 12px; font-weight: 500; flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: #3A2E22; }
html.dark .music-title { color: #E8DCC8; }
.music-artist { font-size: 10px; opacity: 0.4; flex-shrink: 0; }
.music-empty-icon { font-size: 28px; margin-bottom: 4px; }

/* 拍立得(照片可溢出组件边界,z-index高于其他卡片) */
.album-container { position: relative; width: 100%; flex: 1; overflow: visible; }
.polaroid-stack { position: relative; width: 100%; height: 100%; }
.polaroid-pos { position: absolute; top: 50%; left: 50%; width: var(--polaroid-w, 120px); margin-left: calc(var(--polaroid-w, 120px) / -2); margin-top: calc(var(--polaroid-w, 120px) * -0.567); }
.polaroid { background: #fff; padding: 6px 6px 22px; box-shadow: 0 6px 18px rgba(0,0,0,0.25); border-radius: 2px; cursor: pointer; transition: transform 0.3s ease; }
.polaroid:hover { transform: scale(1.15); z-index: 99 !important; }
.polaroid-pos:hover { z-index: 99 !important; }
.polaroid img { width: 100%; aspect-ratio: 4/3; object-fit: cover; display: block; }
.polaroid-caption { position: absolute; bottom: 4px; left: 4px; right: 4px; font-size: 9px; color: #5a4a3a; text-align: center; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.album-closed { position: absolute; top: 50%; left: 50%; width: 85%; aspect-ratio: 4/3; transform: translate(-50%, -50%) rotate(-4deg); cursor: pointer; transition: transform 0.3s ease; }
.album-closed:hover { transform: translate(-50%, -50%) rotate(0deg) scale(1.05); }
.album-cover { width: 100%; height: 100%; background: linear-gradient(135deg, #8B6F47 0%, #6B5435 50%, #5a4530 100%); border-radius: 4px; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 12px; position: relative; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.3), 0 8px 4px -4px rgba(0,0,0,0.35), 0 12px 8px -6px rgba(0,0,0,0.25); }
.album-cover::before { content: ''; position: absolute; inset: 0; background-image: repeating-linear-gradient(45deg, transparent, transparent 8px, rgba(245,230,200,0.04) 8px, rgba(245,230,200,0.04) 16px); pointer-events: none; }
.album-cover::after { content: ''; position: absolute; inset: 6px; border: 1px solid rgba(245,230,200,0.15); border-radius: 2px; pointer-events: none; }
.cover-title { font-size: 13px; font-weight: 700; color: #F5E6C8; text-shadow: 0 1px 2px rgba(0,0,0,0.4); }
.cover-sub { font-size: 9px; color: rgba(245,230,200,0.7); }

/* album卡片本身允许溢出,拍立得z-index高于其他卡片 */
.dash-card.album { overflow: visible; }
.dash-card.album .card-inner { overflow: visible; }
.dash-card.album .polaroid-stack { z-index: 40; }

/* 拖拽幽灵 */
.drag-ghost {
  position: fixed; z-index: 200;
  width: 80px; height: 60px;
  margin-left: -40px; margin-top: -30px;
  display: flex; align-items: center; justify-content: center;
  background: rgba(184,140,110,0.3);
  backdrop-filter: blur(12px);
  border: 1px solid rgba(184,140,110,0.4);
  border-radius: 14px;
  pointer-events: none;
  transform: scale(0.3);
  transition: transform 0.3s cubic-bezier(0.34, 1.56, 0.64, 1), background 0.3s;
}
.drag-ghost.ghost-grown {
  transform: scale(1);
  background: rgba(184,140,110,0.15);
  width: 200px; height: 150px;
  margin-left: -100px; margin-top: -75px;
}
.ghost-label { font-size: 12px; font-weight: 600; color: #3A2E22; white-space: nowrap; }
html.dark .ghost-label { color: #E8DCC8; }

/* Transition */
.fade-enter-active, .fade-leave-active { transition: opacity 0.2s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }

@media (max-width: 960px) {
  .dash-card { display: none; }
  .home-page::before { content: '请使用电脑或平板访问首页仪表盘'; position: fixed; top: 50%; left: 50%; transform: translate(-50%,-50%); font-size: 14px; opacity: 0.4; }
}
</style>
