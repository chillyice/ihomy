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

    <!-- 亮斑图层:模拟阳光照耀强度,在内容之上、阴影之下;台灯 mask 挖洞祛除染色 -->
    <div class="bright-spot" :style="{ ...brightSpotStyle, '--lamp-mask': lampMask }" aria-hidden="true"></div>

    <!-- 窗户阴影:6 条 bar 单层,不透明色+normal 合并重叠区,multiply 层半透明;夜间/日落末尾 transition 0s 防跳变扫光 -->
    <div class="window-shadow"
         :style="{ '--rot': (sunScene.shadowVRotation || 0) + 'deg', '--htop': (sunScene.shadowHTop || 50) + '%', '--shadow-alpha': (sunScene.shadowIntensity ?? 0.5), '--shadow-color': (sunScene.shadowColor || 'rgb(0,0,0)'), '--bar-transition': sunScene.isNight ? '0s' : '3s ease', '--frame-top-offset': (sunScene.frameTopOffset ?? 0) + 'vh', '--lamp-mask': lampMask }"
         aria-hidden="true">
      <div class="shadow-bar frame-h-top"></div>
      <div class="shadow-bar frame-h-bottom"></div>
      <div class="shadow-bar shadow-v"></div>
      <div class="shadow-bar shadow-h"></div>
      <div class="shadow-bar frame-v-left"></div>
      <div class="shadow-bar frame-v-right"></div>
    </div>

    <!-- 反光层:内容组件被阳光照亮的轻微高光(soft-light,夜间 0) -->
    <div class="reflection-layer" :style="reflectionStyle" aria-hidden="true"></div>

    <!-- 柔和暗角:书本在桌上的聚焦感(边缘微压暗);台灯 mask 挖洞 -->
    <div class="vignette" :style="{ '--lamp-mask': lampMask }" aria-hidden="true"></div>

    <!-- 体积光:丁达尔效应(z=48),光源在页面外上方,日出日落渐隐 -->
    <div class="light-layer" :style="{ opacity: sunScene.lightOpacity ?? 0, transition: sunScene.isNight ? 'none' : 'opacity 3s ease' }" aria-hidden="true">
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

    <!-- 台灯光源:左上黄金分割点+钟摆运动,中心亮外边暗,亮度控范围+强度,最顶层 -->
    <div class="lamp-light" :style="{
      opacity: lampDivOpacity,
      left: 'calc(38.2% + ' + lampPendulumX + 'vw)',
      top: '38.2%',
      transform: 'translate(-50%, -50%) scaleX(' + lampPendulumScaleX + ')',
      width: (lampRadius * 2) + 'vw',
      height: (lampRadius * 2) + 'vw',
      background: 'radial-gradient(circle, rgba(' + lampColor + ',0.6) 0%, rgba(' + lampColor + ',0.45) 15%, rgba(' + lampColor + ',0.3) 35%, rgba(' + lampColor + ',0.18) 55%, rgba(' + lampColor + ',0.08) 75%, transparent 95%)'
    }" aria-hidden="true"></div>

    <!-- 灰尘粒子:光路中的飘浮微粒(暖金 + 发光) -->
    <div class="dust-layer" :style="{ opacity: sunScene.lightOpacity ?? 0 }" aria-hidden="true">
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

    <!-- 右下角相册模块:近 7 天有新照片→散落拍立得堆;无→闭合相册 -->
    <div class="album-corner">
      <!-- 散落拍立得堆 -->
      <div v-if="recentPhotos.length" class="polaroid-stack">
        <div
          v-for="(p, i) in recentPhotos"
          :key="p.id"
          class="polaroid"
          :style="{
            transform: `rotate(${polaroidLayout[i].rotate}deg) translate(${polaroidLayout[i].dx}px, ${polaroidLayout[i].dy}px)`,
            zIndex: polaroidLayout[i].z,
          }"
          @click="openViewer(i)"
        >
          <img :src="p.url" :alt="p.description || ''" />
          <div v-if="p.description" class="polaroid-caption">{{ p.description }}</div>
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

    <!-- 图片查看器:点击拍立得查看近期照片 -->
    <el-image-viewer
      v-if="viewerVisible"
      :url-list="viewerUrls"
      :initial-index="viewerIdx"
      @close="viewerVisible = false"
    />

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
        <!-- 台灯开关 -->
        <span class="nav-action lamp-toggle" :class="{ on: lampMode !== 'off' }" @click="toggleLamp" title="台灯:自动/开/关">{{ lampMode === 'auto' ? '🌑' : lampMode === 'on' ? '💡' : '⬛' }}</span>
        <input type="range" min="0" max="100" v-model.number="lampTemp" class="temp-slider" title="色温" />
        <input type="range" min="0" max="100" v-model.number="lampBrightness" class="temp-slider" title="亮度" />
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
        <!-- 深色/浅色切换 -->
        <span class="nav-action" @click="onTheme({ dark: !theme.dark })" :title="theme.dark ? '浅色' : '深色'">
          <el-icon><Sunny v-if="!theme.dark" /><Moon v-else /></el-icon>
        </span>
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
    <!-- 家人动态(可拖拽毛玻璃) -->
    <div class="draggable-panel feed-panel"
      :style="{ left: feedDrag.pos.value.x + 'px', top: feedDrag.pos.value.y + 'px', width: feedDrag.size.value.w + 'px', height: feedDrag.size.value.h + 'px' }">
      <div class="drag-handle" @mousedown="feedDrag.onDragStart">
        <span class="handle-grip"></span>
      </div>
      <div class="panel-body">
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
      <div class="resize-handle"></div>
    </div>

    <!-- 悬赏/任务(可拖拽毛玻璃) -->
    <div class="draggable-panel task-panel"
      :style="{ left: taskDrag.pos.value.x + 'px', top: taskDrag.pos.value.y + 'px', width: taskDrag.size.value.w + 'px', height: taskDrag.size.value.h + 'px' }">
      <div class="drag-handle" @mousedown="taskDrag.onDragStart">
        <span class="handle-grip"></span>
      </div>
      <div class="panel-body">
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
      <div class="resize-handle"></div>
    </div>

    <!-- 右上:时间天气(可拖拽毛玻璃) -->
    <div class="draggable-panel weather-panel"
      :style="{ right: (-weatherDrag.pos.value.x) + 'px', top: weatherDrag.pos.value.y + 'px', width: weatherDrag.size.value.w + 'px', height: weatherDrag.size.value.h + 'px' }">
      <div class="drag-handle" @mousedown="weatherDrag.onDragStart">
        <span class="handle-grip"></span>
      </div>
      <div class="panel-body">
        <div class="clock">{{ clock }}</div>
        <div class="date">{{ dateStr }}</div>
        <div v-if="weather" class="weather">
          <span class="weather-icon">{{ weatherIcon }}</span>
          <span class="weather-temp">{{ weather.temp }}°</span>
          <span class="weather-text">{{ weatherText }}</span>
        </div>
      </div>
      <div class="resize-handle"></div>
    </div>

    <!-- 纪念日倒计时(可拖拽毛玻璃) -->
    <div v-if="anniversaries.length" class="draggable-panel anniversary-panel"
      :style="{ right: (-anniDrag.pos.value.x) + 'px', top: anniDrag.pos.value.y + 'px', width: anniDrag.size.value.w + 'px', height: anniDrag.size.value.h + 'px' }">
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
      <div class="resize-handle"></div>
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
import { ref, computed, onMounted, onUnmounted, nextTick, watchEffect } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useI18n } from 'vue-i18n'
import { publicApi, homeApi, taskApi, notificationApi } from '@/api'
import { gsap } from 'gsap'
import { Sunny, Moon, Bell, ArrowDown } from '@element-plus/icons-vue'
import { ElMessage, ElImageViewer } from 'element-plus'
import { applyLocale } from '@/i18n'
import { applyTheme, loadTheme, applyAutoTheme } from '@/theme'
import { useDragResize } from '@/utils/useDragResize'
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
const allPhotos = ref([])
const viewerVisible = ref(false)
const viewerIdx = ref(0)
const weather = ref(null)
const anniversaries = ref([])
const musicPlaying = ref(false)
const vinylHovered = ref(false)
const audioEl = ref(null)
const trackIdx = ref(0)
const theme = ref(loadTheme())
// 可拖拽面板:动态/任务/天气/纪念日
const feedDrag = useDragResize({ x: 24, y: 80, w: 380, h: 300 })
const taskDrag = useDragResize({ x: 24, y: 400, w: 380, h: 240 })
const weatherDrag = useDragResize({ x: -304, y: 80, w: 280, h: 160 })
const anniDrag = useDragResize({ x: -304, y: 260, w: 280, h: 200 })
const sunInfo = ref(null)
const slotIdx = ref(currentSlotIndex())
const sunScene = ref({ source: { x: '50%', y: '-15%' }, rotation: 0, shadowSkew: 0, palette: { bloom: 'transparent', core: 'transparent', mid: 'transparent', ambient: 'transparent' }, rays: [], shadowVRotation: 0, shadowHTop: 50, frameTopOffset: 0, shadowIntensity: 0.7, shadowColor: 'rgb(8,12,28)', brightSpotColor: 'rgb(8,12,28)', brightSpotOpacity: 0.7, reflectionOpacity: 0, lightOpacity: 0, lampOpacity: 1, isNight: true, dayProgress: 0 })
const lampMode = ref('auto')
const lampTemp = ref(30)
const lampBrightness = ref(50)
const toggleLamp = () => {
  const modes = ['auto', 'on', 'off']
  const i = modes.indexOf(lampMode.value)
  lampMode.value = modes[(i + 1) % modes.length]
}
const lampStrength = computed(() => {
  if (lampMode.value === 'off') return 0
  if (lampMode.value === 'on') return 1
  return sunScene.value.lampOpacity ?? 0
})
const lampB = computed(() => lampBrightness.value / 100)
const lampDivOpacity = computed(() => lampStrength.value * 0.3)
const lampRadius = computed(() => 65)
const lampMaskAlpha = computed(() => 0.03 + 0.97 * lampB.value)
const lampMask = computed(() => {
  if (lampStrength.value <= 0) return 'none'
  const r = lampRadius.value
  const tr = lampMaskAlpha.value * r
  const te = r + 30
  const cx = 38.2 + lampPendulumX.value
  return `radial-gradient(circle at ${cx}% 38.2%, transparent 0%, transparent ${tr}vw, rgba(0,0,0,0.15) ${tr + (te - tr) * 0.3}vw, rgba(0,0,0,0.4) ${tr + (te - tr) * 0.55}vw, rgba(0,0,0,0.7) ${tr + (te - tr) * 0.8}vw, black ${te}vw)`
})
const lampColor = computed(() => {
  const t = lampTemp.value / 100
  const r = Math.round(255 - t * 35)
  const g = Math.round(180 + t * 50)
  const b = Math.round(100 + t * 155)
  return `${r},${g},${b}`
})

// 近 7 天可访问照片(用于拍立得堆),最多 7 张
const SEVEN_DAYS = 7 * 86400000
const recentPhotos = computed(() => {
  const now = Date.now()
  return allPhotos.value
    .filter(p => p.createdAt && now - new Date(p.createdAt).getTime() < SEVEN_DAYS)
    .slice(0, 7)
})
// 拍立得随机姿态:一次性生成,进首页重新撒
const polaroidLayout = computed(() =>
  recentPhotos.value.map((p, i) => ({
    rotate: (Math.random() - 0.5) * 30,
    dx: (Math.random() - 0.5) * 240,
    dy: (Math.random() - 0.5) * 120,
    z: i + 1,
  }))
)
const viewerUrls = computed(() => recentPhotos.value.map(p => p.url))
const openViewer = (idx) => { viewerIdx.value = idx; viewerVisible.value = true }

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

const brightSpotStyle = computed(() => ({
  background: sunScene.value.brightSpotColor || 'transparent',
  opacity: sunScene.value.brightSpotOpacity ?? 0,
}))

const reflectionStyle = computed(() => ({
  background: `radial-gradient(ellipse 60% 50% at ${sunScene.value.source.x} ${sunScene.value.source.y}, rgba(255,245,220,1) 0%, rgba(255,235,200,0.6) 30%, transparent 70%)`,
  opacity: sunScene.value.reflectionOpacity ?? 0,
}))

let clockTimer = null
let lampRaf = null

// 台灯钟摆运动:慢速 sin 波,横向 ±5% 页宽(总 10%),两侧椭圆中间圆
const lampPendulumX = ref(0)
const lampPendulumScaleX = ref(1)
const startPendulum = () => {
  if (lampRaf) return
  const t0 = performance.now()
  const PERIOD = 8000
  const loop = (t) => {
    const phase = ((t - t0) % PERIOD) / PERIOD * Math.PI * 2
    const sin = Math.sin(phase)
    lampPendulumX.value = sin * 5
    lampPendulumScaleX.value = 1 - Math.abs(sin) * 0.2
    lampRaf = requestAnimationFrame(loop)
  }
  lampRaf = requestAnimationFrame(loop)
}
const stopPendulum = () => {
  if (lampRaf) { cancelAnimationFrame(lampRaf); lampRaf = null }
}

const clock = ref('')
const dateStr = ref('')

const userInfo = computed(() => userStore.userInfo)

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
        applyAutoTheme(sunScene.value.isNight)
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
  allPhotos.value = photos
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
  watchEffect(() => {
    if (lampStrength.value > 0) startPendulum()
    else stopPendulum()
  })
  // 每 5 分钟更新时隙 + 每 10 秒微调光柱明暗
  setInterval(() => {
    if (sunInfo.value) {
      const newIdx = currentSlotIndex()
      if (newIdx !== slotIdx.value) {
        slotIdx.value = newIdx
        sunScene.value = getSunScene(sunInfo.value, newIdx)
        applyAutoTheme(sunScene.value.isNight)
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
      gsap.from('.feed-panel', { x: -30, autoAlpha: 0, duration: 0.8, delay: 0.2 })
      gsap.from('.task-panel', { x: -30, autoAlpha: 0, duration: 0.8, delay: 0.3 })
      gsap.from('.weather-panel', { x: 30, autoAlpha: 0, duration: 0.8, delay: 0.3 })
      gsap.from('.anniversary-panel', { x: 30, autoAlpha: 0, duration: 0.8, delay: 0.35 })
      gsap.from('.vinyl-wrap', { x: 60, autoAlpha: 0, duration: 0.8, delay: 0.4 })
      gsap.from('.polaroid', { y: 40, autoAlpha: 0, duration: 0.6, stagger: 0.08, delay: 0.3 })
      gsap.from('.album-closed', { scale: 0.9, autoAlpha: 0, duration: 0.8, delay: 0.3 })
    }, root.value)
  })
})

onUnmounted(() => {
  ctx?.revert()
  if (clockTimer) clearInterval(clockTimer)
  stopPendulum()
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
/* 夜间深色背景 */
html.dark .home-page {
  background: linear-gradient(135deg, #0F1A2E 0%, #162238 50%, #1A2540 100%);
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
/* 夜间色块大幅压暗 */
html.dark .blob { opacity: 0.4; }
html.dark .blob-1 { box-shadow: 0 0 120px 40px rgba(120,200,160,0.4); }
html.dark .blob-2 { box-shadow: 0 0 120px 40px rgba(200,180,100,0.4); }
html.dark .blob-3 { box-shadow: 0 0 120px 40px rgba(200,160,140,0.4); }
html.dark .blob-4 { box-shadow: 0 0 120px 40px rgba(120,160,200,0.4); }
html.dark .blob-5 { box-shadow: 0 0 120px 40px rgba(160,180,120,0.4); }
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

/* 亮斑图层:在内容之上(z>30),阴影之下(z<35),multiply 让白色=透明、黑色=压暗、彩色=染色;台灯 mask 挖洞祛除染色 */
.bright-spot {
  position: fixed;
  inset: 0;
  z-index: 32;
  pointer-events: none;
  mix-blend-mode: multiply;
  -webkit-mask-image: var(--lamp-mask, none);
  mask-image: var(--lamp-mask, none);
  transition: background 3s ease, opacity 3s ease;
}

/* 反光层:内容被阳光照亮的轻微高光(soft-light,夜间 0) */
.reflection-layer {
  position: fixed;
  inset: 0;
  z-index: 42;
  pointer-events: none;
  mix-blend-mode: soft-light;
  transition: opacity 3s ease;
}

/* 台灯光源:左上黄金分割点(38.2%,38.2%),半径60vw(页面3/5),最顶层 */
.lamp-light {
  position: fixed;
  border-radius: 50%;
  z-index: 100;
  pointer-events: none;
  filter: blur(20px);
  transition: opacity 0.3s ease;
}

/* 窗户阴影:单层 6 条 bar,不透明色 normal 合并重叠区,multiply 层半透明;夜间固定 70%;台灯 mask 挖洞祛除阴影 */
.window-shadow {
  position: fixed;
  inset: 0;
  z-index: 35;
  pointer-events: none;
  mix-blend-mode: multiply;
  opacity: var(--shadow-alpha, 0.7);
  -webkit-mask-image: var(--lamp-mask, none);
  mask-image: var(--lamp-mask, none);
}
/* 不透明色:夜间深蓝黑 rgb(8,12,28),日间纯黑;重叠区 normal 合并=同色不复合 */
.shadow-bar {
  position: absolute;
  filter: blur(16px);
  background: var(--shadow-color, rgb(0, 0, 0));
}

/* === 三条竖直 bar:原点全部对齐到 (页面 50% X, 页面 7vh Y) === */
/* top:-50vh + height:337.5vh + transform-origin Y:60vh → 页面 Y = -50+60 = 10vh */
/* shadow-v: 宽度 112px(减20%),origin X = bar 中心(50%)= 页面 50% */
.shadow-v {
  top: -50vh;
  left: 50%;
  width: 112px;
  margin-left: -56px;
  height: 337.5vh;
  transform-origin: 50% 60vh;
  transition: transform var(--bar-transition, 3s ease);
  transform: rotate(var(--rot, 0deg));
}

/* === 左框:origin 在右边缘(100%)= 页面 50% X,旋转,长度延长 50% === */
.frame-v-left {
  top: -50vh;
  left: 50%;
  width: 1400px;
  margin-left: -1400px;
  height: 337.5vh;
  transform-origin: 100% 60vh;
  transition: transform var(--bar-transition, 3s ease);
  transform: translateX(-55vw) rotate(var(--rot, 0deg));
}

/* === 右框:origin 在左边缘(0%)= 页面 50% X,旋转,长度延长 50% === */
.frame-v-right {
  top: -50vh;
  left: 50%;
  width: 1400px;
  height: 337.5vh;
  transform-origin: 0% 60vh;
  transition: transform var(--bar-transition, 3s ease);
  transform: translateX(55vw) rotate(var(--rot, 0deg));
}

/* === 内框横向:不旋转,与顶/底框平行,top = htop === */
.shadow-h {
  left: -75%;
  right: -75%;
  height: 70px;
  top: var(--htop, 50%);
  transition: top var(--bar-transition, 3s ease);
}

/* === 顶框:底边在 y=7vh(旋转原点水平线),height=140px,不旋转 === */
.frame-h-top {
  left: -75%;
  right: -75%;
  height: 140px;
  top: calc(10vh - 140px + var(--frame-top-offset, 0vh));
  transition: top var(--bar-transition, 3s ease);
}

/* === 底框:top = 2×内框中线,height=1400px,不旋转 === */
.frame-h-bottom {
  left: -75%;
  right: -75%;
  height: 1400px;
  top: calc(var(--htop, 50%) * 2 + 70px);
  transition: top var(--bar-transition, 3s ease);
}

/* 体积光层:丁达尔效应(screen 变亮),光源在页面外上方 */
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
  height: 200vh;
  transform-origin: top center;
  transition: opacity 3s ease, filter 3s ease, transform 3s ease, background 3s ease;
}

/* 柔和暗角:聚焦中心(打开的书),边缘微压暗 */
.vignette {
  position: fixed;
  inset: 0;
  z-index: 44;
  pointer-events: none;
  -webkit-mask-image: var(--lamp-mask, none);
  mask-image: var(--lamp-mask, none);
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
.polaroid {
  position: absolute;
  top: 50%;
  left: 50%;
  width: 220px;
  margin-left: -110px;
  margin-top: -125px;
  background: #fff;
  padding: 10px 10px 38px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.25), 0 2px 6px rgba(0, 0, 0, 0.15);
  border-radius: 2px;
  cursor: pointer;
  transition: transform 0.3s ease, box-shadow 0.3s ease, z-index 0s;
}
.polaroid:hover {
  z-index: 99 !important;
  transform: rotate(0deg) translate(0, 0) scale(1.08) !important;
  box-shadow: 0 16px 40px rgba(0, 0, 0, 0.35), 0 4px 12px rgba(0, 0, 0, 0.2);
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
/* === 顶部导航栏:温暖磨砂玻璃 + 自然光效 === */
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
  padding: 0 36px;
  pointer-events: none;
}
.top-bar > * { pointer-events: auto; }
.bar-left, .bar-right { display: flex; align-items: center; gap: 14px; }

/* 家庭名:温暖刻字感,下方暖色细线 */
.family-name {
  font-size: 22px;
  font-weight: 600;
  color: #3A2E22;
  cursor: pointer;
  letter-spacing: 0.5px;
  margin-right: 12px;
  white-space: nowrap;
  position: relative;
  padding-bottom: 2px;
  transition: color 0.25s;
}
.family-name::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  width: 0;
  height: 2px;
  background: linear-gradient(90deg, #C9A876, #B8956A);
  border-radius: 1px;
  transition: width 0.3s ease;
}
.family-name:hover { color: #5A4530; }
.family-name:hover::after { width: 100%; }

/* 模块导航:胶囊式,温暖悬停 */
.nav-modules {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 3px;
  border-radius: 12px;
  background: rgba(255, 250, 240, 0.25);
}
.nav-item {
  font-size: 15px;
  color: rgba(58, 46, 34, 0.75);
  cursor: pointer;
  padding: 6px 14px;
  border-radius: 9px;
  transition: color 0.2s, background 0.2s;
  white-space: nowrap;
  font-weight: 500;
}
.nav-item:hover {
  color: #3A2E22;
  background: rgba(200, 170, 120, 0.18);
}
.nav-item:active {
  background: rgba(200, 170, 120, 0.28);
}

/* 右侧操作区 */
.nav-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  color: rgba(58, 46, 34, 0.8);
  cursor: pointer;
  padding: 7px 10px;
  border-radius: 9px;
  transition: color 0.2s, background 0.2s;
  min-width: 36px;
  min-height: 36px;
}
.nav-action:hover {
  color: #3A2E22;
  background: rgba(200, 170, 120, 0.15);
}
.lamp-toggle { font-size: 17px; }
.lamp-toggle.on {
  background: rgba(255, 210, 130, 0.22);
  box-shadow: 0 0 12px rgba(255, 200, 100, 0.3), 0 0 0 1px rgba(255, 200, 100, 0.2) inset;
}
.temp-slider { width: 72px; vertical-align: middle; cursor: pointer; opacity: 0.7; transition: opacity 0.2s; }
.temp-slider:hover { opacity: 1; }

/* 用户头像:温暖胶囊 */
.nav-user {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 3px 12px 3px 3px;
  border-radius: 22px;
  background: rgba(255, 250, 240, 0.3);
  transition: background 0.2s, box-shadow 0.2s;
}
.nav-user:hover {
  background: rgba(200, 170, 120, 0.15);
  box-shadow: 0 2px 8px rgba(140, 110, 70, 0.1);
}
.user-name { font-size: 14px; color: #3A2E22; font-weight: 500; }
.lang-trigger { min-width: 32px; text-align: center; font-weight: 600; font-size: 14px; }
.nav-more { display: inline-flex; align-items: center; gap: 3px; }
.more-arrow { font-size: 11px; }
.msg-badge { display: inline-flex; align-items: center; }
.msg-icon {
  font-size: 19px;
  color: rgba(58, 46, 34, 0.75);
  cursor: pointer;
  transition: color 0.2s;
}
.msg-icon:hover { color: #3A2E22; }
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
/* === 可拖拽面板通用样式 === */
.draggable-panel {
  position: fixed;
  z-index: 20;
  display: flex;
  flex-direction: column;
  background: rgba(255, 255, 255, 0.25);
  backdrop-filter: blur(30px) saturate(1.4);
  -webkit-backdrop-filter: blur(30px) saturate(1.4);
  border: 1px solid rgba(255, 255, 255, 0.5);
  border-radius: 28px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.08), inset 0 1px 0 rgba(255, 255, 255, 0.4);
  color: #3A2E22;
  overflow: hidden;
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
  text-align: center;
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

/* 纪念日面板 */
.anniversary-panel {
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

/* 响应式 */
@media (max-width: 1280px) {
  .draggable-panel { font-size: 13px; }
}
@media (max-width: 960px) {
  .feed-panel, .task-panel, .anniversary-panel { display: none; }
  .album-corner { width: 220px; height: 200px; right: 16px; bottom: 16px; }
  .polaroid { width: 120px; margin-left: -60px; margin-top: -68px; }
  .clock { font-size: 26px; }
  .vinyl-player { width: 90px; height: 90px; margin-right: -45px; }
}
</style>
