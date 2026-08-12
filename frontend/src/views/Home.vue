<!-- 首页 B:展开的相册。顶栏渐变 + 左侧动态/任务毛玻璃 + 中央相册右半大图 + 右上时间天气 + 右下唱片 -->
<template>
  <div ref="root" class="home-page">
    <!-- 背景:米白渐变 + 清新色块(随机飘动) -->
    <div class="bg-blobs" aria-hidden="true">
      <div class="blob blob-1"></div>
      <div class="blob blob-2"></div>
      <div class="blob blob-3"></div>
      <div class="blob blob-4"></div>
      <div class="blob blob-5"></div>
    </div>

    <!-- 暖色环境光:黄金时刻整体暖调(multiply 微染) -->
    <div class="ambient-layer" :style="ambientStyle" aria-hidden="true"></div>

    <!-- 窗户阴影:外框(4条)+内框十字(2条),同一层同一动效,平行四边形 -->
    <div v-if="sunScene.shadowVisible" class="window-shadow"
         :style="{ opacity: sunScene.shadowOpacity, '--rot': (sunScene.shadowVRotation || 0) + 'deg', '--htop': (sunScene.shadowHTop || 50) + '%' }"
         aria-hidden="true">
      <div class="shadow-bar frame-v-left"></div>
      <div class="shadow-bar frame-v-right"></div>
      <div class="shadow-bar frame-h-top"></div>
      <div class="shadow-bar frame-h-bottom"></div>
      <div class="shadow-bar shadow-v"></div>
      <div class="shadow-bar shadow-h"></div>
    </div>

    <!-- 柔和暗角:书本在桌上的聚焦感(边缘微压暗) -->
    <div class="vignette" aria-hidden="true"></div>

    <!-- 体积光:丁达尔效应(最上层,光源辉光 + 多层羽毛状光束) -->
    <div class="light-layer" aria-hidden="true">
      <div class="light-bloom" :style="bloomStyle"></div>
      <div class="light-source" :style="sourceStyle">
        <div
          v-for="(rs, i) in rayStyles"
          :key="i"
          class="light-ray"
          :style="rs"
        ></div>
      </div>
    </div>

    <!-- 灰尘粒子:光路中的飘浮微粒(暖金 + 发光) -->
    <div class="dust-layer" aria-hidden="true">
      <div
        v-for="d in dustParticles"
        :key="d.id"
        class="dust"
        :style="{
          left: d.left,
          top: d.top,
          width: d.size + 'px',
          height: d.size + 'px',
          animationDuration: d.duration + 's',
          animationDelay: d.delay + 's',
          '--drift': d.drift + 'px',
        }"
      ></div>
    </div>

    <!-- 中央背景:相册右半大图轮播 -->
    <main class="album-stage">
      <div class="album-frame">
        <!-- 牛皮纸基底:照片的托底,比照片大一圈,在照片下方背景上方 -->
        <div class="album-base" aria-hidden="true"></div>

        <transition name="album-fade" mode="out-in">
          <div v-if="currentSlide" :key="currentSlide.key" class="album-photo">
            <img :src="currentSlide.image" :alt="currentSlide.title || ''" />
            <!-- 照片下方文字(标题/摘要/描述,超出省略) -->
            <div class="album-caption">
              <div v-if="currentSlide.title" class="caption-title">{{ currentSlide.title }}</div>
              <div v-if="currentSlide.summary" class="caption-summary">{{ currentSlide.summary }}</div>
              <div v-if="currentSlide.desc" class="caption-desc">{{ currentSlide.desc }}</div>
            </div>
          </div>
          <div v-else :key="'empty'" class="album-empty">
            <el-empty :description="$t('home.noPhotos')" />
          </div>
        </transition>

        <!-- 相册左半暗示:左边缘露出的书脊/装订线 -->
        <div class="album-spine" aria-hidden="true"></div>
      </div>
    </main>

    <!-- 顶部导航栏:从上到下透明渐变 -->
    <header class="top-bar">
      <div class="bar-left">
        <span class="family-name" @click="$router.push('/')">{{ family?.name || 'ihomy' }}</span>
        <!-- 模块导航 -->
        <nav class="nav-modules">
          <span
            v-for="m in navPrimary"
            :key="m.code"
            class="nav-item"
            @click="$router.push(m.path)"
          >{{ m.title }}</span>
          <!-- 更多功能下拉 -->
          <el-dropdown v-if="navSecondary.length" trigger="hover" @command="(p) => $router.push(p)">
            <span class="nav-item nav-more">
              {{ $t('more.title') }}<el-icon class="more-arrow"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item v-for="m in navSecondary" :key="m.code" :command="m.path">{{ m.title }}</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </nav>
      </div>
      <div class="bar-right">
        <!-- 光照测试链接 -->
        <span class="nav-action light-test-link" @click="$router.push('/light-test')">光照测试</span>
        <!-- 语言切换 -->
        <el-dropdown trigger="click" @command="onLang">
          <span class="nav-action lang-trigger">{{ locale === 'en' ? 'EN' : '中' }}</span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="zh-CN" :disabled="locale === 'zh-CN'">中文</el-dropdown-item>
              <el-dropdown-item command="en" :disabled="locale === 'en'">English</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <!-- 主题选择 -->
        <el-dropdown trigger="click" @command="onTheme">
          <span class="nav-action">
            <el-icon><Sunny v-if="!theme.dark" /><Moon v-else /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item :command="{ dark: !theme.dark, theme: theme.theme }">
                {{ theme.dark ? $t('theme.light') : $t('theme.dark') }}
              </el-dropdown-item>
              <el-dropdown-item v-for="t in THEMES" :key="t.key" :command="{ dark: theme.dark, theme: t.key }">
                <span class="theme-swatch" :style="{ background: t.accent }"></span>
                {{ $t('theme.presets.' + t.key) }}
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <!-- 消息提醒铃铛 -->
        <el-popover v-if="userStore.isLoggedIn" placement="bottom-end" :width="340" trigger="click" @show="loadNotifications">
          <template #reference>
            <el-badge :value="unreadCount" :hidden="!unreadCount" class="msg-badge">
              <el-icon class="msg-icon"><Bell /></el-icon>
            </el-badge>
          </template>
          <div class="notify-panel">
            <div class="notify-head">
              <span>{{ $t('nav.notifications') }}</span>
              <el-button v-if="notifications.length" text size="small" @click="markAllRead">{{ $t('nav.allRead') }}</el-button>
            </div>
            <div v-if="notifications.length" class="notify-list">
              <div
                v-for="n in notifications"
                :key="n.id"
                class="notify-item"
                :class="{ unread: !n.isRead }"
                @click="onNotifyClick(n)"
              >
                <div class="notify-type">{{ notifyType(n.type) }}</div>
                <div class="notify-content">{{ n.content }}</div>
                <div class="notify-time">{{ notifyTime(n.createdAt) }}</div>
              </div>
            </div>
            <el-empty v-else :description="$t('nav.noNotification')" :image-size="60" />
          </div>
        </el-popover>
        <!-- 用户头像下拉 -->
        <el-dropdown v-if="userStore.isLoggedIn" trigger="click" @command="onUserCommand">
          <span class="nav-user">
            <el-avatar :size="28" :src="userInfo?.avatar">{{ (userInfo?.nickname || 'U').charAt(0) }}</el-avatar>
            <span class="user-name">{{ userInfo?.nickname || '我' }}</span>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">{{ $t('settings.profile') }}</el-dropdown-item>
              <el-dropdown-item command="settings">{{ $t('nav.settings') }}</el-dropdown-item>
              <el-dropdown-item divided command="logout">{{ $t('nav.logout') }}</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <template v-else>
          <span class="nav-action" @click="$router.push('/login')">{{ $t('home.loginToView') }}</span>
        </template>
      </div>
    </header>

    <!-- 左侧:家人动态 + 悬赏/任务(无边框毛玻璃,向外透明渐变) -->
    <aside class="left-panel">
      <!-- 家人动态(微信消息风) -->
      <div class="glass-panel feed-panel">
        <div class="panel-title">{{ $t('home.familyFeed') }}</div>
        <div class="feed-scroll">
          <div v-if="!feeds.length" class="empty-hint">{{ $t('home.sillEmpty') }}</div>
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

      <!-- 悬赏/任务 -->
      <div class="glass-panel task-panel">
        <div class="panel-title">{{ $t('home.tasksRewards') }}</div>
        <div class="task-scroll">
          <div v-if="!tasks.length" class="empty-hint">{{ $t('home.noTasks') }}</div>
          <div v-for="t in tasks.slice(0, 5)" :key="t.id" class="task-row" @click="$router.push('/task')">
            <span class="task-reward" :class="'reward-' + t.rewardType">{{ rewardIcon(t.rewardType) }}</span>
            <div class="task-info">
              <div class="task-title">{{ t.title }}</div>
              <div class="task-meta">{{ taskStatusLabel(t.status) }}</div>
            </div>
          </div>
        </div>
      </div>
    </aside>

    <!-- 右上:时间天气(毛玻璃) -->
    <div class="glass-panel weather-panel">
      <div class="clock">{{ clock }}</div>
      <div class="date">{{ dateStr }}</div>
      <div v-if="weather" class="weather">
        <span class="weather-icon">{{ weatherIcon }}</span>
        <span class="weather-temp">{{ weather.temp }}°</span>
        <span class="weather-text">{{ weatherText }}</span>
      </div>
    </div>

    <!-- 右侧天气下方:纪念日倒计时 -->
    <div v-if="anniversaries.length" class="glass-panel anniversary-panel">
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

    <!-- 黑胶唱片:藏在右边界,hover 滑出放大,点击播放 -->
    <div class="vinyl-wrap">
      <!-- 歌曲信息 + 播放控件:hover 时从左侧淡出 -->
      <div class="vinyl-info" :class="{ show: vinylHovered }">
        <div class="track-title">{{ currentTrack.title }}</div>
        <div class="track-artist">ihomy 背景音乐</div>
        <div class="track-controls">
          <span class="ctrl-btn" @click.stop="prevTrack">⏮</span>
          <span class="ctrl-btn ctrl-main" @click.stop="togglePlay">{{ musicPlaying ? '⏸' : '▶' }}</span>
          <span class="ctrl-btn" @click.stop="nextTrack">⏭</span>
        </div>
      </div>
      <!-- 唱片本体 -->
      <div
        class="vinyl-player"
        :class="{ playing: musicPlaying }"
        @mouseenter="vinylHovered = true"
        @mouseleave="vinylHovered = false"
        @click="togglePlay"
      >
        <div class="vinyl-disc">
          <div class="vinyl-groove"></div>
          <div class="vinyl-label"></div>
        </div>
        <div class="vinyl-arm"></div>
      </div>
    </div>

    <audio ref="audioEl" :src="currentTrack.url" @ended="nextTrack" preload="auto"></audio>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useI18n } from 'vue-i18n'
import { publicApi, homeApi, taskApi, notificationApi } from '@/api'
import { gsap } from 'gsap'
import { Sunny, Moon, Bell, ArrowDown } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { applyLocale } from '@/i18n'
import { THEMES, applyTheme, loadTheme } from '@/theme'
import { getSunScene, currentSlotIndex } from '@/utils/windowLight'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const { locale, t } = useI18n()

const root = ref(null)
let ctx

const family = ref({})
const modules = ref([])
const feeds = ref([])
const tasks = ref([])
const slides = ref([])
const slideIdx = ref(0)
const weather = ref(null)
const anniversaries = ref([])
const musicPlaying = ref(false)
const vinylHovered = ref(false)
const audioEl = ref(null)
const trackIdx = ref(0)
const theme = ref(loadTheme())
const sunInfo = ref(null)
const slotIdx = ref(currentSlotIndex())
const sunScene = ref({ source: { x: '50%', y: '-2%' }, rotation: 0, shadowSkew: 0, palette: { bloom: 'transparent', core: 'transparent', mid: 'transparent', ambient: 'transparent', shadow: 'rgba(0,0,0,0.3)' }, rays: [] })

// 灰尘粒子:40 个,阳光下的飘浮微粒
const dustParticles = ref(
  Array.from({ length: 40 }, (_, i) => ({
    id: i,
    left: Math.random() * 100 + '%',
    top: Math.random() * 100 + '%',
    size: 1.5 + Math.random() * 4,
    duration: 10 + Math.random() * 15,
    delay: Math.random() * 12,
    drift: 40 + Math.random() * 80,
  }))
)

// 播放列表:本站已上传的音频文件
const playlist = ref([
  { url: '/files/upload/202608/659c49bead454cd08ff33ea15d440443.mp3', title: '昼-02' },
  { url: '/files/music/1786367356819_song.mp3', title: 'song' },
])
const currentTrack = computed(() => playlist.value[trackIdx.value] || {})

// 体积光束:7 条细长光束,从光源旋转射出,重度模糊+screen 叠加
// 不用 clip-path(太硬),用线性渐变+blur 实现羽毛状边界
const rayStyles = computed(() => {
  const s = sunScene.value
  return s.rays.map((ray) => ({
    width: ray.width + 'px',
    transform: `translateX(${ray.offset}px) rotate(${s.rotation}deg)`,
    opacity: ray.opacity,
    filter: `blur(${ray.blur}px)`,
    background: `linear-gradient(to bottom, ${s.palette.core} 0%, ${s.palette.mid} 35%, transparent 75%)`,
  }))
})

const sourceStyle = computed(() => ({
  left: sunScene.value.source.x,
  top: sunScene.value.source.y,
}))

const bloomStyle = computed(() => ({
  left: sunScene.value.source.x,
  top: sunScene.value.source.y,
  background: `radial-gradient(circle, ${sunScene.value.palette.bloom} 0%, ${sunScene.value.palette.mid} 35%, transparent 70%)`,
}))

const ambientStyle = computed(() => ({
  background: sunScene.value.palette.ambient,
}))

let slideTimer = null
let clockTimer = null

const clock = ref('')
const dateStr = ref('')

const userInfo = computed(() => userStore.userInfo)

const currentSlide = computed(() => slides.value[slideIdx.value])

// 导航栏模块:前 5 个平铺,其余收进"更多"下拉
const NAV_PATHS = {
  blog: '/blog', diary: '/diary', album: '/album', anniversary: '/anniversary',
  cinema: '/cinema', member: '/member', points: '/points', task: '/task',
  reminder: '/reminder', plan: '/plan', wish: '/wish', book: '/book',
  chat: '/chat', tree: '/tree', cascade: '/cascade', storage: '/storage',
  item: '/item', kitchen: '/kitchen',
}
const allNavModules = computed(() => {
  if (!modules.value.length) {
    return [
      { code: 'blog', title: '博客', path: '/blog' },
      { code: 'album', title: '相册', path: '/album' },
      { code: 'anniversary', title: '纪念日', path: '/anniversary' },
      { code: 'task', title: '任务', path: '/task' },
      { code: 'book', title: '记账', path: '/book' },
      { code: 'chat', title: '聊天', path: '/chat' },
      { code: 'tree', title: '家谱', path: '/tree' },
    ]
  }
  return modules.value
    .filter(m => NAV_PATHS[m.code])
    .map(m => ({ code: m.code, title: m.title, path: NAV_PATHS[m.code] }))
})
const navPrimary = computed(() => allNavModules.value.slice(0, 5))
const navSecondary = computed(() => allNavModules.value.slice(5))

// 主题切换
const onTheme = (cmd) => { theme.value = applyTheme({ ...theme.value, ...cmd }) }

// 混合 slides:从 feed(博客封面/日记配图/照片 urls)提取所有有图的内容
// diary.images 是 JSON 字符串(后端原样返回),需 parse;photo.urls 是数组
const parseImages = (raw) => {
  if (!raw) return []
  if (Array.isArray(raw)) return raw
  try { return JSON.parse(raw) } catch (e) { return [] }
}

const buildSlides = (feedList, photos) => {
  const arr = []
  for (const f of feedList) {
    if (f.type === 'blog' && f.coverImage) {
      arr.push({ key: 'b' + f.id, image: f.coverImage, title: f.title, summary: f.summary, desc: '' })
    } else if (f.type === 'photo' && f.urls && f.urls.length) {
      f.urls.forEach((u, i) => arr.push({ key: 'p' + f.id + '_' + i, image: u, title: '', summary: '', desc: f.description || '' }))
    } else if (f.type === 'diary') {
      const imgs = parseImages(f.images)
      imgs.forEach((u, i) => arr.push({ key: 'd' + f.id + '_' + i, image: u, title: '', summary: '', desc: (f.content || '').slice(0, 60) }))
    }
  }
  // 兜底:公开照片
  if (!arr.length && photos.length) {
    photos.forEach((p, i) => arr.push({ key: 'ph' + i, image: p.url, title: '', summary: '', desc: p.description || '' }))
  }
  return arr
}

const feedTypeLabel = (type) => type === 'blog' ? '博客' : type === 'diary' ? '日记' : type === 'photo' ? '照片' : ''
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

const weatherIcon = computed(() => {
  if (!weather.value) return ''
  const map = { clear: '☀️', cloud: '☁️', rain: '🌧️', snow: '❄️', fog: '🌫️', thunder: '⛈️' }
  return map[weather.value.condition] || '☀️'
})
const weatherText = computed(() => {
  if (!weather.value) return ''
  const map = { clear: '晴', cloud: '多云', rain: '雨', snow: '雪', fog: '雾', thunder: '雷' }
  return map[weather.value.condition] || ''
})

const togglePlay = () => {
  if (!audioEl.value) return
  if (musicPlaying.value) {
    audioEl.value.pause()
    musicPlaying.value = false
  } else {
    audioEl.value.play().then(() => { musicPlaying.value = true }).catch(() => {})
  }
}
const nextTrack = () => {
  if (!playlist.value.length) return
  trackIdx.value = (trackIdx.value + 1) % playlist.value.length
  nextTick(() => {
    if (musicPlaying.value && audioEl.value) audioEl.value.play().catch(() => {})
  })
}
const prevTrack = () => {
  if (!playlist.value.length) return
  trackIdx.value = (trackIdx.value - 1 + playlist.value.length) % playlist.value.length
  nextTick(() => {
    if (musicPlaying.value && audioEl.value) audioEl.value.play().catch(() => {})
  })
}

// 通知
const unreadCount = ref(0)
const notifications = ref([])
const loadUnread = async () => {
  if (!userStore.isLoggedIn) return
  try { unreadCount.value = await notificationApi.unreadCount() } catch (e) {}
}
const loadNotifications = async () => {
  try {
    notifications.value = await notificationApi.list()
    await loadUnread()
  } catch (e) {}
}
const markAllRead = async () => {
  await notificationApi.markAllRead()
  notifications.value = notifications.value.map((n) => ({ ...n, isRead: 1 }))
  unreadCount.value = 0
}
const onNotifyClick = async (n) => {
  if (!n.isRead) {
    await notificationApi.markRead(n.id)
    n.isRead = 1
    await loadUnread()
  }
  if (n.contentType === 'blog') router.push(`/blog/${n.contentId}`)
  else if (n.contentType === 'diary') router.push('/diary')
  else if (n.contentType === 'photo' && n.contentId) router.push('/album')
}
const notifyType = (type) => type === 'reply' ? t('notify.typeReply') : type === 'system' ? t('notify.typeSystem') : t('notify.typeComment')
const notifyTime = (d) => {
  if (!d) return ''
  const date = new Date(d)
  const diff = Date.now() - date.getTime()
  if (diff < 3600000) return Math.max(1, Math.floor(diff / 60000)) + ' 分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + ' 小时前'
  return date.toLocaleDateString(locale.value === 'en' ? 'en-US' : 'zh-CN')
}

// 语言切换
const onLang = (lang) => {
  applyLocale(lang)
  ElMessage.success(lang === 'en' ? 'Language switched' : '语言已切换')
}

// 用户下拉命令
const onUserCommand = (cmd) => {
  if (cmd === 'logout') {
    userStore.logout()
    location.reload()
  } else if (cmd === 'profile' || cmd === 'settings') {
    router.push('/settings')
  }
}

const updateClock = () => {
  const d = new Date()
  const h = String(d.getHours()).padStart(2, '0')
  const m = String(d.getMinutes()).padStart(2, '0')
  clock.value = `${h}:${m}`
  const weekdays = ['日', '一', '二', '三', '四', '五', '六']
  dateStr.value = `${d.getMonth() + 1}月${d.getDate()}日 周${weekdays[d.getDay()]}`
}

const loadWeather = async () => {
  try {
    const res = await fetch('/api/public/weather')
    if (!res.ok) return
    const json = await res.json()
    if (json.code === 0 && json.data) weather.value = json.data
  } catch (e) {}
}

const homeId = computed(() => route.query.home_id || '')
const hid = computed(() => route.query.hid || '')

const loadAll = async () => {
  let photos = []
  try {
    const pub = await publicApi.getHome(homeId.value || undefined, hid.value || undefined)
    family.value = pub.family || {}
    modules.value = pub.modules || []
    photos = pub.photos || []
    const stats = pub.stats || {}
    anniversaries.value = stats.upcomingEvents || []
  } catch (e) {}
  // 太阳信息
  try {
    const res = await fetch('/api/public/sun-info')
    if (res.ok) {
      const json = await res.json()
      if (json.code === 0 && json.data) {
        sunInfo.value = json.data
        slotIdx.value = currentSlotIndex()
        sunScene.value = getSunScene(json.data, slotIdx.value)
      }
    }
  } catch (e) {}
  try {
    feeds.value = hid.value
      ? await publicApi.getFeed(20, undefined, hid.value)
      : homeId.value
        ? await publicApi.getFeed(20, homeId.value)
        : userStore.isGuest
          ? await publicApi.getFeed(20)
          : await homeApi.getFeed(20)
  } catch (e) { feeds.value = [] }
  slides.value = buildSlides(feeds.value, photos)
  if (slides.value.length > 1) {
    slideTimer = setInterval(() => { slideIdx.value = (slideIdx.value + 1) % slides.value.length }, 5000)
  }
  if (userStore.isLoggedIn) {
    try {
      const r = await taskApi.list()
      tasks.value = Array.isArray(r) ? r : (r.records || [])
    } catch (e) {}
  }
}

onMounted(() => {
  loadAll()
  loadUnread()
  updateClock()
  clockTimer = setInterval(updateClock, 1000)
  // 每 5 分钟更新时隙 + 每 10 秒微调光柱明暗
  setInterval(() => {
    if (sunInfo.value) {
      const newIdx = currentSlotIndex()
      if (newIdx !== slotIdx.value) {
        slotIdx.value = newIdx
        sunScene.value = getSunScene(sunInfo.value, newIdx)
      }
    }
  }, 300000)
  setInterval(() => {
    if (sunInfo.value) {
      const base = getSunScene(sunInfo.value, slotIdx.value)
      sunScene.value = {
        ...base,
        rays: base.rays.map((r) => ({
          ...r,
          opacity: Math.max(0.15, Math.min(0.85, r.opacity + (Math.random() - 0.5) * 0.12)),
        })),
      }
    }
  }, 10000)
  loadWeather()

  nextTick(() => {
    if (!root.value) return
    ctx = gsap.context(() => {
      gsap.from('.top-bar', { y: -20, autoAlpha: 0, duration: 0.6 })
      gsap.from('.left-panel', { x: -30, autoAlpha: 0, duration: 0.8, delay: 0.2 })
      gsap.from('.weather-panel', { x: 30, autoAlpha: 0, duration: 0.8, delay: 0.3 })
      gsap.from('.anniversary-panel', { x: 30, autoAlpha: 0, duration: 0.8, delay: 0.35 })
      gsap.from('.vinyl-wrap', { x: 60, autoAlpha: 0, duration: 0.8, delay: 0.4 })
      gsap.from('.album-base', { y: 40, autoAlpha: 0, duration: 1, delay: 0.3 })
      gsap.from('.album-photo', { scale: 1.05, autoAlpha: 0, duration: 1.2 })
    }, root.value)
  })
})

onUnmounted(() => {
  ctx?.revert()
  if (slideTimer) clearInterval(slideTimer)
  if (clockTimer) clearInterval(clockTimer)
})
</script>

<style scoped>
.home-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #EDE4D3 0%, #E2D8C4 50%, #D6CBB4 100%);
  position: relative;
  overflow: hidden;
  font-family: Georgia, 'Times New Roman', serif;
}

/* 背景色块:清新淡雅(浅绿/浅黄/浅粉/浅蓝),高斯模糊,随机飘动 */
.bg-blobs {
  position: absolute;
  inset: 0;
  z-index: 0;
  overflow: hidden;
  pointer-events: none;
}
.blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(60px);
  opacity: 0.4;
  will-change: transform;
}
.blob-1 {
  width: 480px;
  height: 480px;
  top: -120px;
  left: -100px;
  background: #9CD0B5; /* 绿 */
  animation: drift1 22s ease-in-out infinite;
}
.blob-2 {
  width: 560px;
  height: 560px;
  top: 25%;
  right: -180px;
  background: #EDDB8C; /* 黄 */
  animation: drift2 26s ease-in-out infinite;
}
.blob-3 {
  width: 420px;
  height: 420px;
  bottom: -120px;
  left: 18%;
  background: #ECC0AC; /* 粉 */
  animation: drift3 20s ease-in-out infinite;
}
.blob-4 {
  width: 380px;
  height: 380px;
  top: 35%;
  left: 32%;
  background: #A8C9DE; /* 蓝 */
  animation: drift4 24s ease-in-out infinite;
}
.blob-5 {
  width: 300px;
  height: 300px;
  bottom: 20%;
  right: 22%;
  background: #C0D8A8; /* 绿黄 */
  animation: drift5 18s ease-in-out infinite;
}
@keyframes drift1 {
  0%, 100% { transform: translate(0, 0) scale(1); }
  33% { transform: translate(180px, 120px) scale(1.3); }
  66% { transform: translate(-80px, 200px) scale(0.85); }
}
@keyframes drift2 {
  0%, 100% { transform: translate(0, 0) scale(1); }
  33% { transform: translate(-200px, 150px) scale(1.25); }
  66% { transform: translate(120px, -120px) scale(0.8); }
}
@keyframes drift3 {
  0%, 100% { transform: translate(0, 0) scale(1); }
  50% { transform: translate(240px, -160px) scale(1.35); }
}
@keyframes drift4 {
  0%, 100% { transform: translate(0, 0) scale(1); }
  40% { transform: translate(-160px, -120px) scale(1.28); }
  70% { transform: translate(200px, 80px) scale(0.82); }
}
@keyframes drift5 {
  0%, 100% { transform: translate(0, 0) scale(1); }
  50% { transform: translate(-120px, -180px) scale(1.3); }
}

/* 暖色环境光:黄金时刻整体微染(multiply 极低透明度) */
.ambient-layer {
  position: fixed;
  inset: 0;
  z-index: 2;
  pointer-events: none;
  mix-blend-mode: multiply;
  transition: background 3s ease;
}

/* 窗户阴影:外框+内框同一层 */
.window-shadow {
  position: fixed;
  inset: 0;
  z-index: 35;
  pointer-events: none;
  transition: opacity 3s ease;
}
/* 不透明色 + darken:重叠取 min(同色)=同色,不叠加变深 */
.shadow-bar {
  position: absolute;
  filter: blur(16px);
  background: rgb(106, 92, 77);
  mix-blend-mode: darken;
}

/* === 内框竖直:origin (50%, -7.5%),旋转 === */
.shadow-v {
  top: 0;
  left: 50%;
  width: 140px;
  margin-left: -70px;
  height: 150%;
  transform-origin: 50% -7.5%;
  transition: transform 3s ease;
  transform: rotate(var(--rot, 0deg));
}

/* === 左框:origin 在右边缘 (50%-42.5vw, -7.5%),旋转 === */
.frame-v-left {
  top: 0;
  left: 50%;
  width: 1400px;
  margin-left: -1400px;
  height: 150%;
  transform-origin: 100% -7.5%;
  transition: transform 3s ease;
  transform: translateX(-42.5vw) rotate(var(--rot, 0deg));
}

/* === 右框:origin 在左边缘 (50%+42.5vw, -7.5%),旋转 === */
.frame-v-right {
  top: 0;
  left: 50%;
  width: 1400px;
  height: 150%;
  transform-origin: 0% -7.5%;
  transition: transform 3s ease;
  transform: translateX(42.5vw) rotate(var(--rot, 0deg));
}

/* === 内框横向:不旋转,与顶/底框平行,top = htop === */
.shadow-h {
  left: -75%;
  right: -75%;
  height: 70px;
  top: var(--htop, 50%);
  transition: top 3s ease;
}

/* === 顶框:底边在 y=-7.5%(旋转原点水平线),height=140px,不旋转 === */
.frame-h-top {
  left: -75%;
  right: -75%;
  height: 140px;
  top: calc(-7.5% - 140px);
}

/* === 底框:top = 2×内框中线(2×(htop+35px)),height=1400px,不旋转 === */
.frame-h-bottom {
  left: -75%;
  right: -75%;
  height: 1400px;
  top: calc(var(--htop, 50%) * 2 + 70px);
  transition: top 3s ease;
}

/* 体积光层:丁达尔效应(最上层,screen 变亮) */
.light-layer {
  position: fixed;
  inset: 0;
  z-index: 48;
  pointer-events: none;
  mix-blend-mode: screen;
  overflow: hidden;
}
/* 光源辉光:大范围柔光球(太阳在窗后) */
.light-bloom {
  position: absolute;
  width: 700px;
  height: 700px;
  margin-left: -350px;
  margin-top: -350px;
  border-radius: 50%;
  filter: blur(60px);
  transition: background 3s ease, left 3s ease, top 3s ease;
}
/* 光源点:光束从这里发出 */
.light-source {
  position: absolute;
  width: 0;
  height: 0;
  transition: left 3s ease, top 3s ease;
}
/* 体积光束:细长条 + 重度模糊 = 羽毛状光柱(丁达尔效应) */
.light-ray {
  position: absolute;
  top: 0;
  left: 50%;
  height: 160vh;
  transform-origin: top center;
  transition: opacity 3s ease, filter 3s ease, transform 3s ease, background 3s ease;
}

/* 柔和暗角:聚焦中心(打开的书),边缘微压暗 */
.vignette {
  position: fixed;
  inset: 0;
  z-index: 44;
  pointer-events: none;
  background:
    radial-gradient(ellipse 90% 75% at 50% 42%,
      transparent 0%,
      transparent 55%,
      rgba(60, 38, 12, 0.08) 80%,
      rgba(45, 25, 8, 0.18) 100%);
}

/* 灰尘粒子:光路中的飘浮微粒(暖金 + 发光,screen 变亮) */
.dust-layer {
  position: fixed;
  inset: 0;
  z-index: 46;
  pointer-events: none;
  overflow: hidden;
  mix-blend-mode: screen;
}
.dust {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 238, 185, 0.85);
  box-shadow: 0 0 8px rgba(255, 225, 150, 0.7);
  animation: dust-float linear infinite;
}
@keyframes dust-float {
  0% {
    transform: translate(0, 0);
    opacity: 0;
  }
  15% { opacity: 0.9; }
  85% { opacity: 0.9; }
  100% {
    transform: translate(var(--drift, 60px), calc(var(--drift, 60px) * -1.5));
    opacity: 0;
  }
}

/* 中央相册舞台:上下对齐左侧面板(动态顶/任务底),高度更小 */
.album-stage {
  position: absolute;
  top: 80px;
  bottom: 24px;
  left: 0;
  right: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 420px 0 420px;
  z-index: 30;
}
/* album-frame:照片容器,牛皮纸和唱片都挂在这里;高度收窄(80%)使位置靠下 */
.album-frame {
  position: relative;
  width: 100%;
  height: 80%;
  max-width: 900px;
}
.album-photo {
  position: relative;
  z-index: 30;
  width: 100%;
  height: 100%;
  border-radius: 4px;
  overflow: hidden;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.5);
}
.album-photo img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.album-caption {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 20px 24px 16px;
  background: linear-gradient(to top, rgba(0,0,0,0.25) 0%, transparent 100%);
  color: #F5EFE0;
}
.caption-title {
  font-size: 20px;
  font-weight: 700;
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.caption-summary, .caption-desc {
  font-size: 15px;
  opacity: 0.85;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.album-empty {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 牛皮纸基底:照片的托底,比照片大一圈(inset -30px),在照片下方背景上方 */
.album-base {
  position: absolute;
  inset: -30px;
  z-index: 25;
  border-radius: 12px;
  background:
    repeating-linear-gradient(45deg, rgba(140, 110, 70, 0.05) 0 2px, transparent 2px 5px),
    repeating-linear-gradient(-45deg, rgba(120, 90, 50, 0.04) 0 2px, transparent 2px 6px),
    linear-gradient(135deg, #D4B896 0%, #C9A876 50%, #B8956A 100%);
  box-shadow: 0 12px 40px rgba(100, 70, 30, 0.25), 0 2px 8px rgba(0,0,0,0.1) inset;
  pointer-events: none;
}

/* 相册左半暗示:左边缘的装订线/书脊 */
.album-spine {
  position: absolute;
  top: 10%;
  bottom: 10%;
  left: -18px;
  width: 6px;
  background: linear-gradient(to right, rgba(0,0,0,0.35), transparent);
  pointer-events: none;
  z-index: 26;
}

/* 黑胶唱片:藏在右边界,hover 滑出放大(和每日一图动效一致) */
.vinyl-wrap {
  position: fixed;
  bottom: 80px;
  right: 0;
  z-index: 40;
  display: flex;
  align-items: center;
  gap: 12px;
}
/* 歌曲信息+控件:hover 时从左侧淡出 */
.vinyl-info {
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(20px) saturate(1.4);
  -webkit-backdrop-filter: blur(20px) saturate(1.4);
  border-radius: 16px;
  padding: 12px 18px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1);
  opacity: 0;
  transform: translateX(20px);
  pointer-events: none;
  transition: opacity 0.3s ease, transform 0.3s ease;
  white-space: nowrap;
}
.vinyl-info.show {
  opacity: 1;
  transform: translateX(0);
  pointer-events: auto;
}
.track-title {
  font-size: 15px;
  font-weight: 700;
  color: #3A2E22;
  margin-bottom: 2px;
}
.track-artist {
  font-size: 12px;
  opacity: 0.6;
  color: #3A2E22;
  margin-bottom: 8px;
}
.track-controls {
  display: flex;
  align-items: center;
  gap: 12px;
}
.ctrl-btn {
  font-size: 18px;
  cursor: pointer;
  opacity: 0.7;
  transition: opacity 0.2s, transform 0.2s;
  user-select: none;
}
.ctrl-btn:hover { opacity: 1; transform: scale(1.2); }
.ctrl-main { font-size: 22px; }

/* 唱片本体:半藏在右边界,hover 滑出+放大 */
.vinyl-player {
  position: relative;
  width: 120px;
  height: 120px;
  cursor: pointer;
  margin-right: -60px;
  transition: transform 0.25s ease, margin-right 0.25s ease;
  flex-shrink: 0;
}
.vinyl-wrap:hover .vinyl-player {
  margin-right: 0;
  transform: scale(1.08);
}
.vinyl-player.playing .vinyl-disc {
  animation: spin 4s linear infinite;
}
.vinyl-player.playing .vinyl-arm {
  transform: rotate(20deg);
}
.vinyl-disc {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: radial-gradient(circle, #3A2E22 0%, #1A1410 30%, #2A2018 60%, #1A1410 100%);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.5), inset 0 0 20px rgba(0,0,0,0.5);
  border: 2px solid #5C4332;
}
.vinyl-groove {
  position: absolute;
  inset: 10px;
  border-radius: 50%;
  border: 1px solid rgba(245, 239, 224, 0.1);
  background: repeating-radial-gradient(circle, transparent 0 2px, rgba(245, 239, 224, 0.04) 2px 3px);
}
.vinyl-label {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 32px;
  height: 32px;
  margin: -16px 0 0 -16px;
  border-radius: 50%;
  background: radial-gradient(circle, #A8483A 0%, #6B2E26 100%);
  border: 1px solid #3A2E22;
  box-shadow: 0 0 0 2px #1A1410;
}
.vinyl-arm {
  position: absolute;
  top: -6px;
  right: 22px;
  width: 5px;
  height: 60px;
  background: linear-gradient(to bottom, #C9A876, #8B6F47);
  border-radius: 3px;
  transform-origin: top center;
  transform: rotate(-15deg);
  transition: transform 0.4s ease;
  box-shadow: 0 2px 4px rgba(0,0,0,0.4);
}
.vinyl-arm::after {
  content: '';
  position: absolute;
  bottom: -5px;
  left: -3px;
  width: 10px;
  height: 10px;
  background: #C9A876;
  border-radius: 50%;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}

/* 顶部导航栏:浅色渐变(上深下透明) */
.top-bar {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: 64px;
  z-index: 50;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 32px;
  background: linear-gradient(to bottom,
    rgba(255, 255, 255, 0.5) 0%,
    rgba(255, 255, 255, 0.5) 60%,
    transparent 100%
  );
  pointer-events: none;
}
.top-bar > * { pointer-events: auto; }
.bar-left, .bar-right { display: flex; align-items: center; gap: 16px; }
.family-name {
  font-size: 24px;
  font-weight: 700;
  color: #3A2E22;
  cursor: pointer;
  letter-spacing: 1px;
  margin-right: 8px;
  white-space: nowrap;
}
.nav-modules {
  display: flex;
  align-items: center;
  gap: 18px;
}
.nav-item {
  font-size: 16px;
  color: rgba(58, 46, 34, 0.85);
  cursor: pointer;
  padding: 4px 6px;
  border-radius: 4px;
  transition: color 0.2s, background 0.2s;
  white-space: nowrap;
}
.nav-item:hover { color: #3A2E22; background: rgba(58, 46, 34, 0.08); }
.nav-action {
  display: inline-flex;
  align-items: center;
  font-size: 16px;
  color: #3A2E22;
  cursor: pointer;
  opacity: 0.85;
  padding: 4px 8px;
  border-radius: 4px;
  transition: opacity 0.2s, background 0.2s;
}
.nav-action:hover { opacity: 1; background: rgba(58, 46, 34, 0.08); }
.nav-user {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 2px 8px 2px 2px;
  border-radius: 20px;
  transition: background 0.2s;
}
.nav-user:hover { background: rgba(58, 46, 34, 0.08); }
.user-name { font-size: 15px; color: #3A2E22; }
.theme-swatch {
  display: inline-block;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  margin-right: 6px;
  vertical-align: -1px;
}
.lang-trigger { min-width: 28px; text-align: center; font-weight: 600; }
.nav-more { display: inline-flex; align-items: center; gap: 2px; }
.more-arrow { font-size: 12px; }
.msg-badge { display: inline-flex; align-items: center; }
.msg-icon {
  font-size: 20px;
  color: #3A2E22;
  cursor: pointer;
  opacity: 0.8;
}
.msg-icon:hover { opacity: 1; }
.notify-panel { display: flex; flex-direction: column; gap: 8px; }
.notify-head { display: flex; justify-content: space-between; align-items: center; font-weight: 600; }
.notify-list { max-height: 320px; overflow-y: auto; display: flex; flex-direction: column; }
.notify-item {
  padding: 10px 8px;
  border-radius: 8px;
  cursor: pointer;
  border-bottom: 1px solid rgba(58, 46, 34, 0.06);
}
.notify-item:hover { background: rgba(58, 46, 34, 0.05); }
.notify-item.unread { background: rgba(168, 72, 58, 0.06); }
.notify-type { font-size: 11px; color: #A8483A; margin-bottom: 2px; }
.notify-content { font-size: 13px; color: #3A2E22; }
.notify-time { font-size: 11px; opacity: 0.5; margin-top: 2px; }

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
.left-panel {
  position: fixed;
  top: 80px;
  left: 24px;
  bottom: 24px;
  width: 380px;
  z-index: 40;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.feed-panel {
  flex: 0 1 45%;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
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
}
.feed-scroll::-webkit-scrollbar { width: 0; }
.empty-hint {
  text-align: center;
  padding: 30px 12px;
  font-size: 14px;
  opacity: 0.5;
  font-style: italic;
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

/* 右上时间天气:宽度与左侧一致 380,向中间靠拢 */
.weather-panel {
  position: fixed;
  top: 80px;
  right: 24px;
  z-index: 40;
  width: 380px;
  padding: 22px 32px;
  text-align: center;
  box-sizing: border-box;
}
.clock {
  font-size: 32px;
  font-weight: 700;
  letter-spacing: 2px;
}
.date {
  font-size: 13px;
  opacity: 0.7;
  margin-bottom: 6px;
}
.weather {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  font-size: 15px;
}
.weather-icon { font-size: 20px; }
.weather-temp { font-weight: 700; }

/* 右侧纪念日面板:宽度与左侧一致 380 */
.anniversary-panel {
  position: fixed;
  top: 210px;
  right: 24px;
  z-index: 40;
  width: 380px;
  max-height: 340px;
  display: flex;
  flex-direction: column;
  padding-bottom: 10px;
  box-sizing: border-box;
}
.anni-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 0 28px 18px;
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

/* 相册切换过渡 */
.album-fade-enter-active, .album-fade-leave-active {
  transition: opacity 0.8s ease;
}
.album-fade-enter-from, .album-fade-leave-to { opacity: 0; }

/* 响应式 */
@media (max-width: 1280px) {
  .album-stage { padding: 0 360px 0 360px; }
  .left-panel { width: 320px; }
  .weather-panel, .anniversary-panel { width: 320px; }
}
@media (max-width: 960px) {
  .left-panel { display: none; }
  .album-stage { padding: 0 24px; }
  .album-spine { display: none; }
  .weather-panel { width: auto; padding: 10px 16px; }
  .clock { font-size: 26px; }
  .anniversary-panel { display: none; }
  .vinyl-player { width: 90px; height: 90px; margin-right: -45px; }
}
</style>
