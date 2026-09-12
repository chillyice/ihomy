<!-- 场景主题 P9：场景页为默认首页(路由 /→/scene,meta.immersive 全屏,隐藏侧边栏/页脚)。
     传统首页保留在 /home。分层：远景窗外(天气+昼夜) + 近景室内(抠窗洞) + 前景纱帘(摆动) + 前景桌面物件(独立 sprite) + 前景光影层(P6)。
     交互：mousemove 视差(gsap 直接操作 DOM transform)；物件 hover 微动效(CSS)；
           物件拖拽重排(mousedown→window mousemove/mouseup，冻结镜头，localStorage)；
           物件点击(按下抬起无位移)→ 打开全屏毛玻璃浮层。
     光影(P6)：光柱 + 桌面光斑 + Canvas 浮尘，随太阳方位角(复用 useSunLight 的 sunScene，全局 SunLightLayer 在沉浸页已隐藏)。
     纱帘(P7)：两片半透明纱帘 sprite 叠在窗框两侧，CSS @keyframes 摆动(布料感)。
     天气(P8)：窗外景随 weatherMode 变化——多云云朵漂移+明暗闪烁、阴天灰幕；室内光影随天气衰减。
     昼夜(P9)：全场景色温叠加(黄金时刻暖金/蓝调冷蓝/夜间深蓝) + 窗外景夜间压暗。
     雨雪雾(P10)：窗外雨丝 + 玻璃水痕滑落 / 雪花 / 雾能见度(复用 useSunLight 的 weatherMode)。
     季节(P11)：按月份判定春夏秋冬；秋=落叶🍂、春=花瓣🌸飘落；窗外景随季节叠加色温 tint。
     窗开关+植物搬入(P12)：坏天气(雨雪雾雷/阴天)关窗 + 阳台植物搬进桌面，好天气开窗 + 植物搬回阳台(天气联动)。
     便签墙(P13)：左墙软木板 5 张便签(今日提醒/任务悬赏/家庭计划/纪念日/愿望单)，hover 微抬、点击跳转对应功能页。
     便签交互(P14)：点便签撕下墙 → 展开成大号便签(图标+名称+简介+「打开」跳功能页+「钉回」贴回墙)；Esc/遮罩/钉回 收起。
     大挂图框(P15)：右墙木框挂图，7 张图翻页(户型图/家谱图/账本页/书单/菜单/兑换单/文件柜 = 查看类功能入口)，左右箭头/圆点翻页、点图进入功能页。
     挂图框翻页动效(P16)：3D 翻页(rotateY 从侧翻入，:key 重挂载触发) + 悬停「进入」提示；各功能落地(7 条路由点图直达)。
     桌下抽屉(P17)：桌下抽屉(照片瀑布/放映厅/家庭成员 3 项深处功能)，点拉手拉开上滑露出、镜头更大幅度下移+放大聚焦；点项进入功能页。 -->
<template>
  <div class="scene-root" :data-season="season" @mousemove="onMouseMove" @mouseleave="onIdle">
    <!-- 远景窗外：定位在窗洞区域，位移小 -->
    <div ref="layerFar" class="layer layer-far">
      <img :src="windowBg" class="layer-bg" alt="" draggable="false" :style="{ filter: farFilter }" />
      <!-- 窗开关(P12)：推拉窗玻璃扇，坏天气关窗(霜白 tint + 竖框盖住窗洞)、好天气开窗(滑到左侧) -->
      <div class="window-glass" :class="{ closed: !windowOpen }" aria-hidden="true"></div>
      <!-- 天气叠加层(P8)：多云云朵漂移 + 阴天灰幕 -->
      <div v-if="weatherMode === 'cloud'" class="window-weather" aria-hidden="true">
        <div class="cloud c1"></div>
        <div class="cloud c2"></div>
        <div class="cloud c3"></div>
        <div v-if="isOvercast" class="overcast-veil"></div>
      </div>
      <!-- 雨/雪/雾(P10)：窗内雨丝 + 玻璃水痕 + 雪花 + 雾 -->
      <div v-if="weatherMode === 'rain' || weatherMode === 'thunder' || weatherMode === 'snow' || weatherMode === 'fog'" class="window-precip" aria-hidden="true">
        <template v-if="weatherMode === 'rain' || weatherMode === 'thunder'">
          <span v-for="r in rainStreaks" :key="'r' + r.id" class="rain-streak" :style="{ left: r.left + '%', height: r.len + 'px', animationDuration: r.dur + 's', animationDelay: r.delay + 's', opacity: r.opacity }"></span>
          <span v-for="g in glassDrops" :key="'g' + g.id" class="glass-drop" :style="{ left: g.left + '%', width: g.size + 'px', height: g.size + 'px', animationDuration: g.dur + 's', animationDelay: g.delay + 's', opacity: g.opacity }"></span>
        </template>
        <template v-else-if="weatherMode === 'snow'">
          <span v-for="s in snowflakes" :key="'s' + s.id" class="snowflake" :style="{ left: s.left + '%', fontSize: s.size + 'px', animationDuration: s.dur + 's', animationDelay: s.delay + 's', opacity: s.opacity, '--drift': s.drift + 'px' }">❄</span>
        </template>
        <div v-else-if="weatherMode === 'fog'" class="fog-layer"></div>
      </div>
      <!-- 季节(P11)：秋=落叶🍂 / 春=花瓣🌸 飘落 + 窗外景季节色温 tint -->
      <div v-if="season === 'autumn' || season === 'spring'" class="window-season" aria-hidden="true">
        <span v-for="p in seasonParticles" :key="'se' + p.id" class="season-particle" :style="{ left: p.left + '%', fontSize: p.size + 'px', animationDuration: p.dur + 's', animationDelay: p.delay + 's', opacity: p.opacity, '--drift': p.drift + 'px', '--spin': p.spin + 'deg' }">{{ p.char }}</span>
      </div>
      <div v-if="seasonTint" class="season-tint" :style="seasonTint"></div>
    </div>
    <!-- 近景室内：铺满视口，位移大 -->
    <div ref="layerNear" class="layer layer-near">
      <img :src="roomMask" class="layer-bg" alt="" draggable="false" />
    </div>
    <!-- 便签墙(P13/P14)：左墙软木板 5 张便签 = 提醒类功能入口；点便签撕下墙展开(P14) -->
    <div ref="layerSticky" class="layer layer-sticky">
      <button v-for="n in stickyNotes" :key="n.key" class="sticky" :class="{ torn: tornKey === n.key }" :style="stickyStyle(n)" @click="tearOff(n)">
        <span class="sticky-pin" aria-hidden="true"></span>
        <span class="sticky-icon" aria-hidden="true">{{ n.icon }}</span>
        <span class="sticky-label">{{ n.label }}</span>
      </button>
    </div>
    <!-- 大挂图框(P15)：右墙木框挂图，7 张图翻页 = 查看类功能入口，点图进入功能页 -->
    <div ref="layerFrame" class="layer layer-frame">
      <div class="frame">
        <button class="frame-body" :aria-label="currentFrame.title + '·' + currentFrame.label" @click="enterFrame">
          <span :key="frameIndex" class="frame-canvas">
            <span class="frame-icon" aria-hidden="true">{{ currentFrame.icon }}</span>
            <span class="frame-title">{{ currentFrame.title }}</span>
            <span class="frame-label">{{ currentFrame.label }}</span>
          </span>
        </button>
        <span class="frame-enter-hint" aria-hidden="true">进入 ›</span>
        <button class="frame-arrow frame-prev" aria-label="上一张" @click="flipFrame(-1)">‹</button>
        <button class="frame-arrow frame-next" aria-label="下一张" @click="flipFrame(1)">›</button>
        <div class="frame-dots">
          <button v-for="(p, i) in framePages" :key="p.key" class="frame-dot" :class="{ active: i === frameIndex }" :aria-label="p.title" @click="setFrame(i)"></button>
        </div>
      </div>
    </div>
    <!-- 植物(P12)：阳台植物随天气搬进桌面(坏天气搬入、好天气搬回阳台)，与室内层同位移 -->
    <div ref="layerPlants" class="layer layer-plants" aria-hidden="true">
      <span v-for="p in plants" :key="p.id" class="plant" :class="{ inside: plantsInside }" :style="plantStyle(p)">🪴</span>
    </div>
    <!-- 前景纱帘(P7)：叠在窗框两侧，CSS 摆动，与室内层同位移 -->
    <div ref="layerCurtains" class="layer layer-curtains" aria-hidden="true">
      <div class="curtain curtain-left"><img :src="curtainImg" class="curtain-img" alt="" draggable="false" /></div>
      <div class="curtain curtain-right"><img :src="curtainImg" class="curtain-img" alt="" draggable="false" /></div>
    </div>
    <!-- 前景桌面物件：与室内层同盒同位移(贴合桌面)，物件各自独立 sprite -->
    <div ref="layerObjects" class="layer layer-objects">
      <div
        v-for="obj in objects"
        :key="obj.key"
        class="obj"
        :class="{ dragging: dragKey === obj.key }"
        :style="{ left: obj.left + '%', bottom: obj.bottom + '%', width: obj.width + '%' }"
        @mousedown="onObjDragStart($event, obj)"
      >
        <span class="obj-shadow" aria-hidden="true"></span>
        <img :src="obj.img" class="obj-img" :alt="obj.label" draggable="false" />
        <span class="obj-label">{{ obj.label }}</span>
      </div>
    </div>

    <!-- 桌下抽屉(P17)：桌下抽屉(照片瀑布/放映厅/家庭成员)，点拉手拉开上滑露出，镜头下移聚焦 -->
    <div ref="layerDrawer" class="layer layer-drawer">
      <div class="drawer" :class="{ open: drawerOpen }">
        <div class="drawer-items">
          <button v-for="d in drawerItems" :key="d.key" class="drawer-item" @click="goDrawerItem(d)">
            <span class="di-icon" aria-hidden="true">{{ d.icon }}</span>
            <span class="di-label">{{ d.label }}</span>
          </button>
        </div>
        <button class="drawer-handle" :aria-label="drawerOpen ? '收起抽屉' : '拉开抽屉'" @click="toggleDrawer">
          <span class="handle-bar" aria-hidden="true"></span>
        </button>
      </div>
    </div>

    <!-- 前景光影层(P6)：光柱 + 桌面光斑 + 浮尘，随太阳方位角，与室内层同位移 -->
    <div ref="layerLight" class="layer layer-light" aria-hidden="true">
      <div class="scene-shaft" :style="shaftStyle"></div>
      <div class="scene-desk-glow" :style="deskGlowStyle"></div>
      <canvas ref="dustCanvas" class="scene-dust"></canvas>
    </div>

    <!-- 昼夜色温叠加(P9)：黄金时刻暖金 / 蓝调冷蓝 / 夜间深蓝，全场景覆盖 -->
    <div v-if="timeTint" class="scene-tint" :style="timeTint"></div>

    <!-- 全屏毛玻璃浮层：物件点击后弹出，承载功能内容 -->
    <transition name="overlay">
      <div v-if="overlay" class="glass-overlay" @click.self="closeOverlay">
        <div class="overlay-panel">
          <div class="overlay-head">
            <span class="overlay-title">{{ overlay.label }}</span>
            <button class="overlay-close" aria-label="关闭" @click="closeOverlay">
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M6 6 L18 18 M18 6 L6 18"/></svg>
            </button>
          </div>
          <div class="overlay-body">
            <!-- 相册：真实数据链路 -->
            <template v-if="overlay.key === 'album'">
              <div v-if="overlayLoading" class="overlay-loading">加载中…</div>
              <template v-else>
                <div v-if="overlayAlbums.length" class="overlay-albums">
                  <div v-for="a in overlayAlbums" :key="a.id" class="overlay-album" @click="goAlbum(a)">
                    <div class="oa-cover" :style="a.cover ? { backgroundImage: `url(${a.cover})` } : {}">
                      <span v-if="!a.cover" class="oa-cover-empty">
                        <svg viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="5" width="18" height="14" rx="2"/><circle cx="9" cy="10" r="1.8"/><path d="M21 15l-5-4-5 4"/></svg>
                      </span>
                    </div>
                    <div class="oa-name">{{ a.name }}</div>
                    <div class="oa-meta">{{ a.totalPhotoCount ?? a.photoCount ?? 0 }} 张</div>
                  </div>
                </div>
                <div v-else class="overlay-empty">暂无相册</div>
              </template>
            </template>
            <!-- 其它物件：占位，功能地图 P13-P18 接入 -->
            <template v-else>
              <div class="overlay-placeholder">
                <div class="ph-icon">{{ objIcon(overlay.key) }}</div>
                <div class="ph-text">「{{ overlay.label }}」功能将在后续分期接入（P13-P18 功能地图）</div>
              </div>
            </template>
          </div>
        </div>
      </div>
    </transition>

    <!-- P14 便签撕下展开：点便签 → 撕下墙 → 展开成大号便签(可打开功能页 / 钉回墙) -->
    <transition name="sticky-expand">
      <div v-if="tornNote" class="sticky-expand-wrap" @click.self="pinBack">
        <div class="sticky-expand">
          <span class="sticky-pin" aria-hidden="true"></span>
          <span class="se-icon" aria-hidden="true">{{ tornNote.icon }}</span>
          <span class="se-label">{{ tornNote.label }}</span>
          <span class="se-desc">{{ tornNote.desc }}</span>
          <div class="se-actions">
            <button class="se-open" @click="openTornNote">打开「{{ tornNote.label }}」</button>
            <button class="se-pin" @click="pinBack">钉回墙上</button>
          </div>
        </div>
      </div>
    </transition>

    <!-- 主题切换 + 设置入口：沉浸页无侧边栏，此处提供返回传统首页/进设置的门 -->
    <div class="scene-controls">
      <button class="scene-ctrl" @click="switchToClassic" title="切到传统模块首页">
        <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/></svg>
        <span>传统主题</span>
      </button>
      <button class="scene-ctrl" @click="goSettings" title="设置">
        <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="3.2"/><path d="M19.4 15a1.7 1.7 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.7 1.7 0 0 0-1.82-.33 1.7 1.7 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.7 1.7 0 0 0 9 19.4a1.7 1.7 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.7 1.7 0 0 0 .33-1.82 1.7 1.7 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.7 1.7 0 0 0 4.6 9a1.7 1.7 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.7 1.7 0 0 0 1.82.33H9a1.7 1.7 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.7 1.7 0 0 0 1 1.51 1.7 1.7 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.7 1.7 0 0 0-.33 1.82V9a1.7 1.7 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.7 1.7 0 0 0-1.51 1z"/></svg>
        <span>设置</span>
      </button>
    </div>

    <!-- 场景主题提示 -->
    <div class="scene-hint">场景主题 · P17（默认首页 · 拖拽重排 · 点击相册 · 便签墙撕下/钉回 · 大挂图框翻页 · 桌下抽屉 · 光影 · 纱帘 · 天气 · 昼夜 · 雨雪雾 · 季节 · 窗开关+植物搬入）</div>
  </div>
</template>

<script setup>
import { ref, computed, watch, inject, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'
import gsap from 'gsap'
import { albumApi } from '@/api'
import { setHomeTheme, HOME_THEME_CLASSIC } from '@/theme/homeTheme'
import windowBg from '@/assets/scene-window.jpg'
import roomMask from '@/assets/scene-room.webp'
import objLaptop from '@/assets/scene-item-laptop.webp'
import objAlbum from '@/assets/scene-item-album.webp'
import objPhone from '@/assets/scene-item-phone.webp'
import objTurntable from '@/assets/scene-item-turntable.webp'
import curtainImg from '@/assets/scene-curtain.webp'

const router = useRouter()
const layerFar = ref(null)
const layerNear = ref(null)
const layerObjects = ref(null)
const layerPlants = ref(null)
const layerSticky = ref(null)
const layerFrame = ref(null)
const layerDrawer = ref(null)
const layerCurtains = ref(null)
const layerLight = ref(null)
const dustCanvas = ref(null)
let idleTimer = null
let enabled = false

// ---- P6 光影层：光柱 + 桌面光斑 + 浮尘，随太阳方位角 ----
// 复用全局 useSunLight 的太阳位置(App.vue 已 provide)。场景页为沉浸式，全局 SunLightLayer 已隐藏，
// 场景用自身贴合「窗→桌」几何的光影层。
const sunLight = inject(SUN_LIGHT_KEY, null)
const FALLBACK_SCENE = {
  rotation: 0,
  azimuth: 180,
  lightOpacity: 0,
  isNight: true,
  hasDirectLight: false,
  palette: { core: 'transparent', mid: 'transparent' },
}
const sunScene = computed(() => sunLight?.sunScene?.value || FALLBACK_SCENE)

// ---- P8 天气(窗外层)：复用 useSunLight 的 weatherMode/cloudFlicker ----
const weatherMode = computed(() => sunLight?.weatherMode?.value || 'clear')
const cloudFlicker = computed(() => sunLight?.cloudFlicker?.value ?? 1)
const weatherIcon = computed(() => sunLight?.weather?.value?.iconCode ?? '100')
const isOvercast = computed(() => weatherMode.value === 'cloud' && Number(weatherIcon.value) === 104)

// ---- P12 窗开关 + 植物搬进桌面(天气联动) ----
// 坏天气(雨/雪/雾/雷雨/阴天)：关窗挡风雨 + 阳台植物搬进桌面；好天气(晴/多云非阴)：开窗 + 植物搬回阳台。
const inclementWeather = computed(() =>
  weatherMode.value === 'rain' || weatherMode.value === 'snow' || weatherMode.value === 'fog' || weatherMode.value === 'thunder' || isOvercast.value
)
const windowOpen = computed(() => !inclementWeather.value)
const plantsInside = computed(() => inclementWeather.value)

// 阳台植物：2 盆，位置在「阳台(窗台)」与「桌面」之间随天气切换；left/bottom 为室内层(-8% 盒)百分比
const PLANTS = [
  { id: 1, size: 34, balcony: { left: 44, bottom: 33 }, desk: { left: 30, bottom: 25 } },
  { id: 2, size: 40, balcony: { left: 56, bottom: 33 }, desk: { left: 64, bottom: 25 } },
]
const plants = PLANTS
function plantStyle(p) {
  const pos = plantsInside.value ? p.desk : p.balcony
  return { left: pos.left + '%', bottom: pos.bottom + '%', fontSize: p.size + 'px' }
}

// ---- P13 便签墙：左墙软木板 5 张便签 = 提醒类功能入口 ----
// top/left 为室内层(-8% 盒)百分比；rot 为初始小角度(便签自然歪贴感)
const STICKY_NOTES = [
  { key: 'reminder', icon: '📌', label: '今日提醒', desc: '今天需要留意的提醒事项', path: '/reminder', top: 24, left: 11, rot: -3 },
  { key: 'task', icon: '🎯', label: '任务悬赏', desc: '家庭任务与悬赏', path: '/task', top: 31, left: 16, rot: 2 },
  { key: 'plan', icon: '📅', label: '家庭计划', desc: '家庭日程安排', path: '/plan', top: 38, left: 10, rot: -2 },
  { key: 'anniversary', icon: '💐', label: '纪念日', desc: '重要的纪念日子', path: '/anniversary', top: 45, left: 15, rot: 3 },
  { key: 'wish', icon: '⭐', label: '愿望单', desc: '大家的心愿清单', path: '/wish', top: 52, left: 11, rot: -1 },
]
const stickyNotes = STICKY_NOTES
function stickyStyle(n) {
  return { top: n.top + '%', left: n.left + '%', '--rot': n.rot + 'deg' }
}

// ---- P14 便签交互：撕下(点便签) / 展开(大号便签) / 钉回(贴回墙) ----
const tornKey = ref(null)
const tornNote = computed(() => STICKY_NOTES.find((n) => n.key === tornKey.value) || null)
function tearOff(n) {
  tornKey.value = n.key
}
function pinBack() {
  tornKey.value = null
}
function openTornNote() {
  if (tornNote.value) router.push(tornNote.value.path)
}

// ---- P15 大挂图框：右墙木框挂图，7 张图翻页 = 查看类功能入口(点图进入功能页) ----
// title=挂图名(户型图/家谱图…)，label=对应功能(物品定位/家谱…)
const FRAME_PAGES = [
  { key: 'item', icon: '🗺️', title: '户型图', label: '物品定位', path: '/item' },
  { key: 'tree', icon: '🌳', title: '家谱图', label: '家谱', path: '/tree' },
  { key: 'book', icon: '📒', title: '账本页', label: '记账本', path: '/book' },
  { key: 'library', icon: '📚', title: '书单', label: '书架', path: '/library' },
  { key: 'kitchen', icon: '🍳', title: '菜单', label: '厨房', path: '/kitchen' },
  { key: 'points', icon: '🎁', title: '兑换单', label: '积分商城', path: '/points' },
  { key: 'storage', icon: '🗄️', title: '文件柜', label: '文件浏览', path: '/storage/files' },
]
const framePages = FRAME_PAGES
const frameIndex = ref(0)
const currentFrame = computed(() => framePages[frameIndex.value])
function flipFrame(d) {
  frameIndex.value = (frameIndex.value + d + framePages.length) % framePages.length
}
function setFrame(i) {
  frameIndex.value = i
}
function enterFrame() {
  router.push(currentFrame.value.path)
}

// ---- P17 桌下抽屉：桌下抽屉(照片瀑布/放映厅/家庭成员 3 项深处功能) ----
const DRAWER_ITEMS = [
  { key: 'cascade', icon: '🖼️', label: '照片瀑布', path: '/cascade' },
  { key: 'cinema', icon: '🎬', label: '放映厅', path: '/cinema' },
  { key: 'member', icon: '👨‍👩‍👧', label: '家庭成员', path: '/member' },
]
const drawerItems = DRAWER_ITEMS
const drawerOpen = ref(false)
// 抽屉聚焦镜头：更大幅度下移(8%) + 放大(1.15)，露出桌下抽屉
const DRAWER_CAM_TY = 0.08
const DRAWER_CAM_SCALE = 1.15
function toggleDrawer() {
  drawerOpen.value = !drawerOpen.value
  onIdle() // 立即重算镜头(打开→下移聚焦，收起→回正)
}
function goDrawerItem(d) {
  router.push(d.path)
}

// ---- 主题切换 + 设置入口(沉浸页无侧边栏，此处提供返回传统首页/进设置的门) ----
function switchToClassic() {
  setHomeTheme(HOME_THEME_CLASSIC)
  router.push('/home')
}
function goSettings() {
  router.push('/settings')
}

// 窗外景滤镜：天气(晴/云/雾/雨雪) × 昼夜(夜间/蓝调压暗)；窗外景是恒昼图,夜间须强压暗
const farFilter = computed(() => {
  const s = sunScene.value
  const m = weatherMode.value
  let b, sat, con = 0.95, sep = 0.12
  if (m === 'clear') { b = 0.8; sat = 0.72 }
  else if (m === 'cloud') { b = 0.8 * (0.72 + 0.28 * cloudFlicker.value); sat = 0.66; sep = 0.13 }
  else if (m === 'fog') { b = 0.62; sat = 0.4; con = 0.9; sep = 0.14 }
  else { b = 0.5; sat = 0.48; sep = 0.15 }
  // 昼夜因子
  let tf = 1, sf = 1
  if (s.isNight) { tf = 0.22; sf = 0.42 }
  else {
    const p = s.dayProgress ?? 0.5
    if (p < 0.1 || p >= 0.9) { tf = 0.6; sf = 0.7 }  // 蓝调时刻
  }
  return `brightness(${(b * tf).toFixed(3)}) saturate(${(sat * sf).toFixed(3)}) contrast(${con}) sepia(${sep})`
})

// 天气对室内光照的衰减：云随 cloudFlicker 闪烁；阴/雨雪雾/雾进一步压低
const weatherLightFactor = computed(() => {
  switch (weatherMode.value) {
    case 'cloud': return 0.7 + 0.3 * cloudFlicker.value
    case 'overcast': return 0.4
    case 'fog': return 0.25
    case 'rain': case 'snow': case 'thunder': return 0.18
    default: return 1
  }
})

// 昼夜色温叠加(P9)：黄金时刻暖金 / 蓝调冷蓝 / 夜间深蓝；日间无叠加
const timeTint = computed(() => {
  const s = sunScene.value
  if (s.isNight) {
    return { background: 'linear-gradient(rgba(12, 20, 44, 0.5), rgba(16, 24, 48, 0.45))', mixBlendMode: 'multiply' }
  }
  const p = s.dayProgress ?? 0.5
  if (p < 0.1) return { background: 'linear-gradient(rgba(80, 120, 190, 0.32), rgba(60, 100, 170, 0.3))', mixBlendMode: 'soft-light' }
  if (p < 0.3) return { background: 'linear-gradient(rgba(255, 175, 90, 0.28), rgba(255, 150, 70, 0.22))', mixBlendMode: 'soft-light' }
  if (p < 0.7) return null
  if (p < 0.9) return { background: 'linear-gradient(rgba(255, 150, 60, 0.3), rgba(255, 120, 50, 0.24))', mixBlendMode: 'soft-light' }
  return { background: 'linear-gradient(rgba(70, 105, 170, 0.32), rgba(55, 90, 155, 0.3))', mixBlendMode: 'soft-light' }
})

// ---- P10 雨/雪/雾粒子(场景窗内，按 weatherMode 重新生成) ----
const rainStreaks = ref([])
const snowflakes = ref([])
const glassDrops = ref([])

function genWeatherParticles() {
  rainStreaks.value = []
  snowflakes.value = []
  glassDrops.value = []
  const m = weatherMode.value
  if (m === 'rain' || m === 'thunder') {
    rainStreaks.value = Array.from({ length: 40 }, (_, i) => ({
      id: i, left: Math.random() * 100, len: 16 + Math.random() * 26,
      dur: 0.7 + Math.random() * 0.6, delay: Math.random() * 2, opacity: 0.25 + Math.random() * 0.4,
    }))
    glassDrops.value = Array.from({ length: 9 }, (_, i) => ({
      id: i, left: Math.random() * 100, size: 2.5 + Math.random() * 3.5,
      dur: 3 + Math.random() * 4, delay: Math.random() * 5, opacity: 0.35 + Math.random() * 0.4,
    }))
  } else if (m === 'snow') {
    snowflakes.value = Array.from({ length: 30 }, (_, i) => ({
      id: i, left: Math.random() * 100, size: 10 + Math.random() * 10,
      dur: 7 + Math.random() * 8, delay: Math.random() * 8,
      drift: 20 + Math.random() * 60, opacity: 0.4 + Math.random() * 0.5,
    }))
  }
}
watch(weatherMode, genWeatherParticles, { immediate: true })

// ---- P11 季节：按月份判定春夏秋冬；秋=落叶、春=花瓣飘落；窗外景季节色温叠加 ----
function monthToSeason(month) {
  if (month === 11 || month <= 1) return 'winter'   // 12/1/2 月
  if (month <= 4) return 'spring'                    // 3/4/5 月
  if (month <= 7) return 'summer'                    // 6/7/8 月
  return 'autumn'                                    // 9/10/11 月
}
const season = ref(monthToSeason(new Date().getMonth()))

// 季节飘落粒子：秋=落叶(🍂/🍁)、春=花瓣(🌸)；夏/冬无飘落(冬的雪由天气 P10 承担)
const seasonParticles = ref([])
function genSeasonParticles() {
  seasonParticles.value = []
  const s = season.value
  if (s === 'autumn') {
    seasonParticles.value = Array.from({ length: 14 }, (_, i) => ({
      id: i, char: Math.random() < 0.5 ? '🍂' : '🍁', left: Math.random() * 100,
      size: 12 + Math.random() * 12, dur: 8 + Math.random() * 8, delay: Math.random() * 10,
      drift: 30 + Math.random() * 80, spin: 200 + Math.random() * 280, opacity: 0.55 + Math.random() * 0.4,
    }))
  } else if (s === 'spring') {
    seasonParticles.value = Array.from({ length: 12 }, (_, i) => ({
      id: i, char: '🌸', left: Math.random() * 100,
      size: 10 + Math.random() * 10, dur: 9 + Math.random() * 8, delay: Math.random() * 10,
      drift: 20 + Math.random() * 60, spin: 120 + Math.random() * 200, opacity: 0.5 + Math.random() * 0.4,
    }))
  }
}
watch(season, genSeasonParticles, { immediate: true })

// 窗外景季节色温：春=清新绿 / 夏=浓绿 / 秋=暖琥珀 / 冬=冷蓝(soft-light 叠加，不影响天气×昼夜滤镜)
const seasonTint = computed(() => {
  switch (season.value) {
    case 'spring': return { background: 'linear-gradient(rgba(120, 205, 120, 0.18), rgba(90, 185, 110, 0.14))', mixBlendMode: 'soft-light' }
    case 'summer': return { background: 'linear-gradient(rgba(70, 170, 95, 0.16), rgba(50, 150, 85, 0.12))', mixBlendMode: 'soft-light' }
    case 'autumn': return { background: 'linear-gradient(rgba(232, 152, 58, 0.2), rgba(202, 120, 40, 0.16))', mixBlendMode: 'soft-light' }
    case 'winter': return { background: 'linear-gradient(rgba(125, 145, 175, 0.16), rgba(105, 125, 155, 0.14))', mixBlendMode: 'soft-light' }
    default: return null
  }
})

// 光柱：从窗户斜射到桌面，角度随方位角(rotation=az-180)，强度随太阳光 opacity
const shaftStyle = computed(() => {
  const s = sunScene.value
  const core = s.palette?.core || 'transparent'
  const mid = s.palette?.mid || 'transparent'
  return {
    opacity: Math.min(0.9, (s.lightOpacity ?? 0) * 0.9 * weatherLightFactor.value),
    '--rot': (s.rotation ?? 0) + 'deg',
    background: `linear-gradient(to bottom, ${core} 0%, ${mid} 42%, transparent 80%)`,
  }
})

// 桌面光斑：落在桌面，水平位置随方位角平移(东→中→西)
const deskGlowStyle = computed(() => {
  const s = sunScene.value
  const shift = Math.max(-18, Math.min(18, (((s.azimuth ?? 180) - 180) / 90) * 18))
  const core = s.palette?.core || 'transparent'
  return {
    opacity: Math.min(0.75, (s.lightOpacity ?? 0) * 0.85 * weatherLightFactor.value),
    left: `calc(50% + ${shift}%)`,
    background: `radial-gradient(ellipse 52% 62% at 50% 50%, ${core} 0%, transparent 72%)`,
  }
})

// 浮尘粒子(Canvas)：光柱中飘浮微粒，集中在「窗→桌」区域，亮度随光照强度
const DUST_COUNT = 50
let dustCtx = null
let dustRaf = null
let dustParticles = []
let dustW = 0
let dustH = 0

function sizeDust() {
  const canvas = dustCanvas.value
  if (!canvas) return
  const dpr = Math.min(2, window.devicePixelRatio || 1)
  dustW = canvas.clientWidth
  dustH = canvas.clientHeight
  canvas.width = Math.max(1, Math.round(dustW * dpr))
  canvas.height = Math.max(1, Math.round(dustH * dpr))
  dustCtx = canvas.getContext('2d')
  dustCtx.setTransform(dpr, 0, 0, dpr, 0, 0)
  dustCtx.globalCompositeOperation = 'lighter'
}

function initDust() {
  sizeDust()
  dustParticles = Array.from({ length: DUST_COUNT }, () => ({
    x: 0.28 + Math.random() * 0.44,
    y: 0.08 + Math.random() * 0.7,
    r: 0.6 + Math.random() * 2.2,
    vx: (Math.random() - 0.5) * 0.00012,
    vy: -(0.00008 + Math.random() * 0.00018),
    a: 0.25 + Math.random() * 0.6,
    tw: Math.random() * Math.PI * 2,
  }))
}

function drawDust(now) {
  if (!dustCtx) return
  dustCtx.clearRect(0, 0, dustW, dustH)
  const intensity = Math.min(1, (sunScene.value.lightOpacity ?? 0) * weatherLightFactor.value)
  if (intensity <= 0.01) return
  for (const p of dustParticles) {
    p.x += p.vx
    p.y += p.vy
    if (p.y < -0.02) { p.y = 0.78; p.x = 0.28 + Math.random() * 0.44 }
    if (p.x < -0.02) p.x = 1.02
    if (p.x > 1.02) p.x = -0.02
    const twinkle = 0.6 + 0.4 * Math.sin(now * 0.001 + p.tw)
    const alpha = p.a * twinkle * intensity
    const px = p.x * dustW
    const py = p.y * dustH
    dustCtx.beginPath()
    dustCtx.arc(px, py, p.r, 0, Math.PI * 2)
    dustCtx.fillStyle = `rgba(255, 235, 190, ${alpha})`
    dustCtx.fill()
  }
}

function dustLoop(now) {
  drawDust(now)
  dustRaf = requestAnimationFrame(dustLoop)
}

function onVisibility() {
  if (document.hidden) {
    if (dustRaf) { cancelAnimationFrame(dustRaf); dustRaf = null }
  } else if (!dustRaf) {
    dustRaf = requestAnimationFrame(dustLoop)
  }
}

// 物件默认布局(左→右)：电脑 / 相册(中央) / 手机 / 唱片机；left/bottom 为相对室内层(-8% 盒)的百分比
// scene-item 套为「正面略俯/平摊」视角，相册与唱片机较扁平，宽度相应调大
const DEFAULT_OBJECTS = [
  { key: 'laptop', label: '电脑', img: objLaptop, left: 14, bottom: 20, width: 15 },
  { key: 'album', label: '相册', img: objAlbum, left: 44, bottom: 22, width: 17 },
  { key: 'phone', label: '手机', img: objPhone, left: 60, bottom: 23, width: 6.5 },
  { key: 'turntable', label: '唱片机', img: objTurntable, left: 70, bottom: 20, width: 17 },
]
const OBJ_ICONS = { laptop: '💻', album: '📖', phone: '📱', turntable: '🎵' }
const objIcon = (key) => OBJ_ICONS[key] || '🔖'
const STORAGE_KEY = 'ihomy:scene:objects'
const DRAG_LEFT_MIN = 6
const DRAG_LEFT_MAX = 76
const DRAG_BOTTOM_MIN = 14
const DRAG_BOTTOM_MAX = 34

function loadLayout() {
  let saved = null
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) saved = JSON.parse(raw)
  } catch (e) { /* ignore */ }
  return DEFAULT_OBJECTS.map((d) => {
    const s = Array.isArray(saved) ? saved.find((x) => x.key === d.key) : null
    return {
      ...d,
      left: s && typeof s.left === 'number' ? s.left : d.left,
      bottom: s && typeof s.bottom === 'number' ? s.bottom : d.bottom,
    }
  })
}
const objects = ref(loadLayout())

// ---- 镜头视差 ----
const maxTx = () => window.innerWidth * 0.045
const maxTy = () => window.innerHeight * 0.045

function onMouseMove(e) {
  if (!enabled || dragKey.value) return
  const nx = (e.clientX / window.innerWidth) * 2 - 1
  const ny = (e.clientY / window.innerHeight) * 2 - 1
  const radial = Math.min(1, Math.hypot(nx, ny) / Math.SQRT2)
  // P17：抽屉打开时镜头聚焦桌下(更大幅度下移+放大)，否则正常视差
  // ty 为负=图层上移=镜头下移(望向桌下)；正值为图层下移会把抽屉进一步推出视口
  const tx = drawerOpen.value ? -nx * window.innerWidth * 0.02 : -nx * maxTx()
  const ty = drawerOpen.value ? -window.innerHeight * DRAWER_CAM_TY : -ny * maxTy()
  const scale = drawerOpen.value ? DRAWER_CAM_SCALE : 1 + 0.06 * radial
  gsap.to(layerFar.value, { x: tx * 0.33, y: ty * 0.33, scale: 1, duration: 0.6, ease: 'power2.out', overwrite: 'auto', force3D: true })
  gsap.to(layerNear.value, { x: tx, y: ty, scale, duration: 0.6, ease: 'power2.out', overwrite: 'auto', force3D: true })
  gsap.to(layerObjects.value, { x: tx, y: ty, scale, duration: 0.6, ease: 'power2.out', overwrite: 'auto', force3D: true })
  gsap.to(layerPlants.value, { x: tx, y: ty, scale, duration: 0.6, ease: 'power2.out', overwrite: 'auto', force3D: true })
  gsap.to(layerSticky.value, { x: tx, y: ty, scale, duration: 0.6, ease: 'power2.out', overwrite: 'auto', force3D: true })
  gsap.to(layerFrame.value, { x: tx, y: ty, scale, duration: 0.6, ease: 'power2.out', overwrite: 'auto', force3D: true })
  gsap.to(layerDrawer.value, { x: tx, y: ty, scale, duration: 0.6, ease: 'power2.out', overwrite: 'auto', force3D: true })
  gsap.to(layerCurtains.value, { x: tx, y: ty, scale, duration: 0.6, ease: 'power2.out', overwrite: 'auto', force3D: true })
  gsap.to(layerLight.value, { x: tx, y: ty, scale, duration: 0.6, ease: 'power2.out', overwrite: 'auto', force3D: true })
  clearTimeout(idleTimer)
  idleTimer = setTimeout(onIdle, 3000)
}

function onIdle() {
  if (!layerFar.value) return
  // P17：抽屉打开时空闲停在「下移+放大」的抽屉聚焦，收起才回正视窗
  const ty = drawerOpen.value ? -window.innerHeight * DRAWER_CAM_TY : 0
  const scale = drawerOpen.value ? DRAWER_CAM_SCALE : 1
  gsap.to(layerFar.value, { x: 0, y: ty * 0.33, scale: 1, duration: 1.4, ease: 'power3.out', overwrite: 'auto', force3D: true })
  gsap.to(layerNear.value, { x: 0, y: ty, scale, duration: 1.4, ease: 'power3.out', overwrite: 'auto', force3D: true })
  gsap.to(layerObjects.value, { x: 0, y: ty, scale, duration: 1.4, ease: 'power3.out', overwrite: 'auto', force3D: true })
  gsap.to(layerPlants.value, { x: 0, y: ty, scale, duration: 1.4, ease: 'power3.out', overwrite: 'auto', force3D: true })
  gsap.to(layerSticky.value, { x: 0, y: ty, scale, duration: 1.4, ease: 'power3.out', overwrite: 'auto', force3D: true })
  gsap.to(layerFrame.value, { x: 0, y: ty, scale, duration: 1.4, ease: 'power3.out', overwrite: 'auto', force3D: true })
  gsap.to(layerDrawer.value, { x: 0, y: ty, scale, duration: 1.4, ease: 'power3.out', overwrite: 'auto', force3D: true })
  gsap.to(layerCurtains.value, { x: 0, y: ty, scale, duration: 1.4, ease: 'power3.out', overwrite: 'auto', force3D: true })
  gsap.to(layerLight.value, { x: 0, y: ty, scale, duration: 1.4, ease: 'power3.out', overwrite: 'auto', force3D: true })
}

// ---- 物件拖拽重排 + 点击判定 ----
const dragKey = ref(null)
let dragStart = null // { key, startX, startY, startLeft, startBottom, moved }

function onObjDragStart(e, obj) {
  if (!enabled || e.button !== 0) return
  e.preventDefault()
  e.stopPropagation()
  gsap.set(layerFar.value, { x: 0, y: 0, scale: 1, force3D: true })
  gsap.set(layerNear.value, { x: 0, y: 0, scale: 1, force3D: true })
  gsap.set(layerObjects.value, { x: 0, y: 0, scale: 1, force3D: true })
  gsap.set(layerPlants.value, { x: 0, y: 0, scale: 1, force3D: true })
  gsap.set(layerSticky.value, { x: 0, y: 0, scale: 1, force3D: true })
  gsap.set(layerFrame.value, { x: 0, y: 0, scale: 1, force3D: true })
  gsap.set(layerDrawer.value, { x: 0, y: 0, scale: 1, force3D: true })
  gsap.set(layerCurtains.value, { x: 0, y: 0, scale: 1, force3D: true })
  gsap.set(layerLight.value, { x: 0, y: 0, scale: 1, force3D: true })
  clearTimeout(idleTimer)
  dragKey.value = obj.key
  dragStart = { key: obj.key, startX: e.clientX, startY: e.clientY, startLeft: obj.left, startBottom: obj.bottom, moved: false }
  window.addEventListener('mousemove', onObjDragMove)
  window.addEventListener('mouseup', onObjDragEnd)
}

function onObjDragMove(e) {
  if (!dragStart) return
  const dx = e.clientX - dragStart.startX
  const dy = e.clientY - dragStart.startY
  if (!dragStart.moved && Math.abs(dx) + Math.abs(dy) > 6) dragStart.moved = true
  if (!dragStart.moved) return // 未构成拖拽，先不更新位置（留给点击判定）
  const vw = window.innerWidth
  const vh = window.innerHeight
  const dLeft = (dx * 100) / (vw * 1.16)
  const dBottom = (-dy * 100) / (vh * 1.16)
  const left = Math.min(DRAG_LEFT_MAX, Math.max(DRAG_LEFT_MIN, dragStart.startLeft + dLeft))
  const bottom = Math.min(DRAG_BOTTOM_MAX, Math.max(DRAG_BOTTOM_MIN, dragStart.startBottom + dBottom))
  const obj = objects.value.find((o) => o.key === dragStart.key)
  if (obj) {
    obj.left = left
    obj.bottom = bottom
  }
}

function onObjDragEnd() {
  if (!dragStart) return
  const wasClick = !dragStart.moved
  const key = dragStart.key
  dragStart = null
  dragKey.value = null
  window.removeEventListener('mousemove', onObjDragMove)
  window.removeEventListener('mouseup', onObjDragEnd)
  if (wasClick) {
    openOverlay(key)
  } else {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(objects.value.map((o) => ({ key: o.key, left: o.left, bottom: o.bottom }))))
    } catch (e) { /* ignore */ }
  }
}

// ---- 全屏毛玻璃浮层 ----
const overlay = ref(null) // { key, label }
const overlayAlbums = ref([])
const overlayLoading = ref(false)

function openOverlay(key) {
  const obj = objects.value.find((o) => o.key === key)
  if (!obj) return
  overlay.value = { key: obj.key, label: obj.label }
  if (key === 'album') loadAlbums()
}

function closeOverlay() {
  overlay.value = null
  overlayAlbums.value = []
}

async function loadAlbums() {
  overlayLoading.value = true
  try {
    const list = await albumApi.list()
    overlayAlbums.value = (list || []).filter((a) => !a.parentId)
  } catch (e) {
    overlayAlbums.value = []
  } finally {
    overlayLoading.value = false
  }
}

function goAlbum(a) {
  router.push(`/album/${a.id}`)
}

function onKeydown(e) {
  if (e.key !== 'Escape') return
  if (overlay.value) closeOverlay()
  else if (tornKey.value) pinBack()
}

onMounted(() => {
  const coarse = window.matchMedia('(pointer: coarse)').matches || 'ontouchstart' in window
  enabled = !coarse
  gsap.set(layerFar.value, { x: 0, y: 0, scale: 1, force3D: true })
  gsap.set(layerNear.value, { x: 0, y: 0, scale: 1, force3D: true })
  gsap.set(layerObjects.value, { x: 0, y: 0, scale: 1, force3D: true })
  gsap.set(layerPlants.value, { x: 0, y: 0, scale: 1, force3D: true })
  gsap.set(layerSticky.value, { x: 0, y: 0, scale: 1, force3D: true })
  gsap.set(layerFrame.value, { x: 0, y: 0, scale: 1, force3D: true })
  gsap.set(layerDrawer.value, { x: 0, y: 0, scale: 1, force3D: true })
  gsap.set(layerCurtains.value, { x: 0, y: 0, scale: 1, force3D: true })
  gsap.set(layerLight.value, { x: 0, y: 0, scale: 1, force3D: true })
  window.addEventListener('keydown', onKeydown)

  // P6 浮尘(Canvas)：尊重 prefers-reduced-motion；未减少动态时启动 rAF
  initDust()
  const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  if (!reduced) {
    dustRaf = requestAnimationFrame(dustLoop)
  }
  window.addEventListener('resize', sizeDust)
  document.addEventListener('visibilitychange', onVisibility)
})

onBeforeUnmount(() => {
  clearTimeout(idleTimer)
  if (dustRaf) { cancelAnimationFrame(dustRaf); dustRaf = null }
  window.removeEventListener('resize', sizeDust)
  document.removeEventListener('visibilitychange', onVisibility)
  window.removeEventListener('mousemove', onObjDragMove)
  window.removeEventListener('mouseup', onObjDragEnd)
  window.removeEventListener('keydown', onKeydown)
})
</script>

<style scoped>
.scene-root {
  position: fixed;
  inset: 0;
  overflow: hidden;
  background: #17110c;
}
.layer {
  position: absolute;
  will-change: transform;
}
.layer-bg {
  width: 100%;
  height: 100%;
  object-fit: cover;
  pointer-events: none;
  user-select: none;
}
.layer-far {
  left: 27%;
  right: 27%;
  top: 6%;
  bottom: 40%;
}
.layer-far .layer-bg {
  filter: brightness(0.8) saturate(0.72) contrast(0.95) sepia(0.12);
}
.layer-far::after {
  content: '';
  position: absolute;
  inset: -4%;
  background: radial-gradient(ellipse at center, rgba(20, 13, 9, 0) 58%, rgba(20, 13, 9, 0.42) 100%);
  pointer-events: none;
}

/* ---- P12 窗开关：推拉窗玻璃扇(坏天气关窗、好天气开窗) ---- */
.window-glass {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
}
/* 玻璃扇本体：霜白 tint + 竖框 + 内高光；open 时滑到左侧露出窗外，closed 时盖住窗洞 */
.window-glass::before {
  content: '';
  position: absolute;
  inset: -2%;
  background: linear-gradient(135deg, rgba(205, 222, 242, 0.30) 0%, rgba(190, 210, 235, 0.16) 45%, rgba(216, 229, 246, 0.26) 100%);
  border: 1px solid rgba(190, 205, 225, 0.35);
  border-right: 4px solid rgba(140, 158, 180, 0.45);
  box-shadow: inset 0 0 26px rgba(210, 225, 245, 0.16);
  transform: translateX(-101%);
  transition: transform 1.1s ease;
  will-change: transform;
}
.window-glass.closed::before {
  transform: translateX(0);
}

/* ---- P8 窗外天气层(多云云朵漂移 + 阴天灰幕) ---- */
.window-weather {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
}
.cloud {
  position: absolute;
  background: radial-gradient(ellipse at center, rgba(238, 242, 250, 0.85) 0%, rgba(238, 242, 250, 0.5) 45%, transparent 75%);
  filter: blur(8px);
  animation: cloudDrift linear infinite;
  will-change: transform;
}
.c1 { width: 55%; height: 32%; top: 6%; left: -55%; animation-duration: 26s; }
.c2 { width: 40%; height: 26%; top: 30%; left: -40%; animation-duration: 34s; animation-delay: -12s; }
.c3 { width: 50%; height: 28%; top: 16%; left: -50%; animation-duration: 30s; animation-delay: -20s; }
.overcast-veil {
  position: absolute;
  inset: 0;
  background: linear-gradient(rgba(150, 156, 166, 0.5), rgba(158, 164, 174, 0.45));
}
@keyframes cloudDrift {
  0% { transform: translateX(0); }
  100% { transform: translateX(330%); }
}

/* ---- P9 昼夜色温叠加层 ---- */
.scene-tint {
  position: absolute;
  inset: 0;
  pointer-events: none;
  transform: translateZ(0);
}

/* ---- P10 雨/雪/雾(窗内) ---- */
.window-precip {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
}
.rain-streak {
  position: absolute;
  top: -30px;
  width: 1.5px;
  background: linear-gradient(to bottom, rgba(185, 205, 235, 0.75), rgba(185, 205, 235, 0.15));
  transform: rotate(14deg);
  animation: rainFall linear infinite;
  will-change: transform;
}
@keyframes rainFall {
  0% { transform: translateY(0) rotate(14deg); opacity: 0; }
  10% { opacity: 1; }
  90% { opacity: 0.8; }
  100% { transform: translateY(680px) rotate(14deg); opacity: 0; }
}
.glass-drop {
  position: absolute;
  top: -10px;
  border-radius: 50%;
  background: radial-gradient(circle at 32% 30%, rgba(255, 255, 255, 0.55), rgba(205, 220, 240, 0.18) 55%, transparent 75%);
  box-shadow: 0 -26px 18px -10px rgba(200, 215, 235, 0.28);
  animation: glassSlide ease-in infinite;
  will-change: transform;
}
@keyframes glassSlide {
  0% { transform: translateY(0); opacity: 0; }
  20% { opacity: 1; }
  85% { opacity: 0.9; }
  100% { transform: translateY(220px); opacity: 0; }
}
.snowflake {
  position: absolute;
  top: -20px;
  color: rgba(255, 255, 255, 0.9);
  text-shadow: 0 0 4px rgba(255, 255, 255, 0.6);
  line-height: 1;
  user-select: none;
  animation: snowFall linear infinite;
}
@keyframes snowFall {
  0% { transform: translate(0, 0) rotate(0deg); opacity: 0; }
  10% { opacity: 1; }
  90% { opacity: 1; }
  100% { transform: translate(var(--drift, 30px), 560px) rotate(360deg); opacity: 0; }
}
.fog-layer {
  position: absolute;
  inset: -20%;
  background:
    radial-gradient(ellipse at 20% 30%, rgba(210, 218, 230, 0.5), transparent 60%),
    radial-gradient(ellipse at 70% 60%, rgba(205, 214, 226, 0.45), transparent 60%),
    radial-gradient(ellipse at 50% 40%, rgba(215, 222, 232, 0.4), transparent 70%);
  filter: blur(10px);
  animation: fogDrift 16s ease-in-out infinite;
}
@keyframes fogDrift {
  0%, 100% { transform: translateX(-3%); }
  50% { transform: translateX(3%); }
}

/* ---- P11 季节(窗内落叶/花瓣 + 季节色温) ---- */
.window-season {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
}
.season-particle {
  position: absolute;
  top: -30px;
  line-height: 1;
  user-select: none;
  will-change: transform;
  filter: drop-shadow(0 0 3px rgba(255, 255, 255, 0.25));
  animation: leafFall linear infinite;
}
@keyframes leafFall {
  0% { transform: translate(0, 0) rotate(0deg); opacity: 0; }
  10% { opacity: 0.9; }
  90% { opacity: 0.85; }
  100% { transform: translate(var(--drift, 40px), 560px) rotate(var(--spin, 320deg)); opacity: 0; }
}
.season-tint {
  position: absolute;
  inset: 0;
  pointer-events: none;
  transform: translateZ(0);
}
.layer-near {
  inset: -8%;
  pointer-events: none;
}
.layer-objects {
  inset: -8%;
  pointer-events: none;
}

/* ---- P17 桌下抽屉：桌下抽屉(照片瀑布/放映厅/家庭成员)，点拉手拉开上滑露出 ---- */
.layer-drawer {
  inset: -8%;
  pointer-events: none;
}
.drawer {
  position: absolute;
  left: 30%;
  /* bottom 相对 -8% 出血层；8% 使拉手落在可见的桌下暗色带内(2% 会落到视口下方不可见) */
  bottom: 8%;
  width: 40%;
  pointer-events: auto;
}
.drawer-items {
  display: flex;
  justify-content: center;
  gap: 10px;
  margin-bottom: 8px;
  opacity: 0;
  transform: translateY(80%);
  transition: opacity 0.35s ease, transform 0.4s cubic-bezier(0.34, 1.2, 0.64, 1);
  pointer-events: none;
}
.drawer.open .drawer-items {
  opacity: 1;
  transform: translateY(0);
  pointer-events: auto;
}
.drawer-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  padding: 9px 15px;
  background: linear-gradient(160deg, #c89a63 0%, #9c6a3d 48%, #7d542f 100%);
  border: 1px solid rgba(60, 38, 14, 0.4);
  border-radius: 9px;
  cursor: pointer;
  box-shadow: 0 6px 14px rgba(15, 8, 3, 0.4);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}
.drawer-item:hover {
  transform: translateY(-3px);
  box-shadow: 0 10px 20px rgba(15, 8, 3, 0.5);
}
.di-icon {
  font-size: 22px;
  line-height: 1;
}
.di-label {
  font-size: 12px;
  letter-spacing: 1px;
  color: #fff2de;
}
.drawer-handle {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  padding: 11px 0;
  background: linear-gradient(180deg, #a8784a 0%, #7d542f 100%);
  border: 1px solid rgba(40, 20, 8, 0.5);
  border-radius: 6px;
  cursor: pointer;
  box-shadow: 0 4px 10px rgba(15, 8, 3, 0.35);
  transition: transform 0.2s ease, background 0.2s ease;
}
.drawer-handle:hover {
  transform: translateY(-1px);
  background: linear-gradient(180deg, #b8845a 0%, #8a5f36 100%);
}
.handle-bar {
  width: 42%;
  height: 4px;
  border-radius: 2px;
  background: rgba(60, 38, 14, 0.55);
  box-shadow: inset 0 1px 0 rgba(255, 240, 210, 0.3);
}

/* ---- P12 植物层：阳台植物随天气搬进桌面 ---- */
.layer-plants {
  inset: -8%;
  pointer-events: none;
}
.plant {
  position: absolute;
  line-height: 1;
  user-select: none;
  filter: drop-shadow(6px 10px 8px rgba(15, 8, 3, 0.42));
  transition: left 1.2s cubic-bezier(0.34, 1.2, 0.64, 1), bottom 1.2s cubic-bezier(0.34, 1.2, 0.64, 1), transform 1.2s cubic-bezier(0.34, 1.2, 0.64, 1);
  will-change: left, bottom;
}
.plant.inside {
  /* 搬入桌面：略缩小、暖色柔和 */
  transform: scale(0.84);
  filter: drop-shadow(6px 12px 10px rgba(15, 8, 3, 0.5)) brightness(0.98) sepia(0.1);
}

/* ---- P13 便签墙：左墙软木板 5 张便签 = 提醒类功能入口 ---- */
.layer-sticky {
  inset: -8%;
  pointer-events: none;
}
.sticky {
  position: absolute;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 12px 13px 9px;
  min-width: 74px;
  background: linear-gradient(160deg, #f7e9a8 0%, #f1dc8a 100%);
  border: 1px solid rgba(120, 96, 40, 0.18);
  border-radius: 6px;
  box-shadow: 0 4px 10px rgba(15, 8, 3, 0.32), inset 0 1px 0 rgba(255, 252, 230, 0.7);
  transform: rotate(var(--rot, 0deg));
  cursor: pointer;
  pointer-events: auto;
  transition: transform 0.25s cubic-bezier(0.34, 1.56, 0.64, 1), box-shadow 0.25s ease;
}
.sticky:hover {
  transform: rotate(0deg) translateY(-6px) scale(1.06);
  box-shadow: 0 10px 22px rgba(15, 8, 3, 0.4), 0 0 18px rgba(255, 214, 120, 0.35), inset 0 1px 0 rgba(255, 252, 230, 0.7);
}
.sticky-pin {
  position: absolute;
  top: -4px;
  left: 50%;
  width: 9px;
  height: 9px;
  transform: translateX(-50%);
  border-radius: 50%;
  background: radial-gradient(circle at 35% 30%, #e88a6a 0%, #c94f3d 55%, #8c2f22 100%);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.4);
}
.sticky-icon {
  font-size: 20px;
  line-height: 1;
}
.sticky-label {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 1px;
  color: #5a4a26;
  white-space: nowrap;
}
/* P14：便签被撕下后墙上留淡影(已「离墙」)，禁止再点 */
.sticky.torn {
  opacity: 0.22;
  transform: rotate(var(--rot, 0deg)) scale(0.9);
  box-shadow: none;
  pointer-events: none;
}

/* ---- P14 便签撕下展开：大号便签浮层 ---- */
.sticky-expand-wrap {
  position: absolute;
  inset: 0;
  z-index: 55;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(22, 15, 10, 0.28);
  backdrop-filter: blur(8px) saturate(1.05);
}
.sticky-expand {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  width: min(340px, 82vw);
  padding: 34px 30px 26px;
  background: linear-gradient(160deg, #f9edb0 0%, #f2df8f 100%);
  border: 1px solid rgba(120, 96, 40, 0.22);
  border-radius: 10px;
  box-shadow: 0 24px 60px rgba(15, 8, 3, 0.5), inset 0 1px 0 rgba(255, 252, 230, 0.75);
}
.sticky-expand .sticky-pin {
  top: -5px;
  width: 12px;
  height: 12px;
}
.se-icon {
  font-size: 40px;
  line-height: 1;
}
.se-label {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 2px;
  color: #4a3c1d;
}
.se-desc {
  font-size: 13px;
  letter-spacing: 0.5px;
  color: #6b5a30;
  margin-bottom: 6px;
}
.se-actions {
  display: flex;
  gap: 10px;
  margin-top: 8px;
}
.se-open,
.se-pin {
  padding: 9px 16px;
  font-size: 13px;
  letter-spacing: 1px;
  border-radius: 9px;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease, background 0.2s ease;
}
.se-open {
  color: #fff6e6;
  background: linear-gradient(160deg, #8a6232 0%, #6f4a22 100%);
  border: 1px solid rgba(60, 38, 14, 0.4);
  box-shadow: 0 4px 12px rgba(15, 8, 3, 0.3);
}
.se-open:hover {
  transform: translateY(-1px);
  box-shadow: 0 8px 18px rgba(15, 8, 3, 0.36);
}
.se-pin {
  color: #5a4a26;
  background: rgba(255, 252, 230, 0.6);
  border: 1px solid rgba(120, 96, 40, 0.28);
}
.se-pin:hover {
  background: rgba(255, 252, 230, 0.9);
  transform: translateY(-1px);
}

/* 撕下/钉回过渡：便签从墙上「揭下」的轻微旋转放大入场 */
.sticky-expand-enter-active,
.sticky-expand-leave-active {
  transition: opacity 0.24s ease;
}
.sticky-expand-enter-active .sticky-expand,
.sticky-expand-leave-active .sticky-expand {
  transition: transform 0.3s cubic-bezier(0.34, 1.4, 0.64, 1), opacity 0.24s ease;
}
.sticky-expand-enter-from,
.sticky-expand-leave-to {
  opacity: 0;
}
.sticky-expand-enter-from .sticky-expand,
.sticky-expand-leave-to .sticky-expand {
  opacity: 0;
  transform: rotate(-4deg) scale(0.82) translateY(10px);
}

/* ---- P15 大挂图框：右墙木框挂图，7 张图翻页 = 查看类功能入口 ---- */
.layer-frame {
  inset: -8%;
  pointer-events: none;
}
.frame {
  position: absolute;
  left: 78%;
  top: 18%;
  width: 15%;
  height: 44%;
  pointer-events: auto;
  filter: drop-shadow(8px 14px 12px rgba(15, 8, 3, 0.4));
  transition: transform 0.3s cubic-bezier(0.34, 1.56, 0.64, 1), filter 0.3s ease;
}
.frame:hover {
  transform: translateY(-4px) rotate(-1deg);
  filter: drop-shadow(12px 20px 16px rgba(15, 8, 3, 0.48));
}
.frame-body {
  position: absolute;
  inset: 0;
  padding: 0;
  background: linear-gradient(160deg, #c89a63 0%, #9c6a3d 48%, #7d542f 100%);
  border: none;
  border-radius: 6px;
  cursor: pointer;
  perspective: 900px;
  box-shadow: inset 0 1px 0 rgba(255, 240, 210, 0.4), inset 0 -2px 0 rgba(40, 20, 8, 0.35);
}
.frame-canvas {
  position: absolute;
  inset: 7%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  background: linear-gradient(160deg, #f8f1de 0%, #eee1c3 100%);
  border-radius: 3px;
  box-shadow: inset 0 0 18px rgba(80, 55, 25, 0.18);
  transform-origin: center;
  animation: frameFlipIn 0.45s cubic-bezier(0.34, 1.2, 0.64, 1);
}
.frame-icon {
  font-size: 34px;
  line-height: 1;
}
.frame-title {
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 2px;
  color: #4a3c1d;
}
.frame-label {
  font-size: 11px;
  letter-spacing: 1px;
  color: #8a6a38;
}

/* P16 翻页动效：3D 翻页(rotateY 从侧翻入，:key 重挂载触发) */
@keyframes frameFlipIn {
  0% { transform: rotateY(-72deg) scale(0.94); opacity: 0.15; }
  60% { transform: rotateY(7deg) scale(1.01); opacity: 1; }
  100% { transform: rotateY(0) scale(1); opacity: 1; }
}
.frame-enter-hint {
  position: absolute;
  left: 50%;
  bottom: 7%;
  transform: translateX(-50%);
  padding: 3px 11px;
  font-size: 11px;
  letter-spacing: 1px;
  color: rgba(255, 245, 232, 0.92);
  background: rgba(20, 13, 9, 0.58);
  border: 1px solid rgba(255, 245, 232, 0.18);
  border-radius: 9px;
  backdrop-filter: blur(6px);
  white-space: nowrap;
  opacity: 0;
  transition: opacity 0.25s ease;
  pointer-events: none;
}
.frame:hover .frame-enter-hint {
  opacity: 1;
}
.frame-arrow {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 26px;
  height: 26px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  line-height: 1;
  color: rgba(255, 245, 232, 0.9);
  background: rgba(20, 13, 9, 0.5);
  border: 1px solid rgba(255, 245, 232, 0.2);
  border-radius: 50%;
  cursor: pointer;
  transition: background 0.2s ease;
}
.frame-arrow:hover {
  background: rgba(20, 13, 9, 0.72);
}
.frame-prev {
  left: -14px;
}
.frame-next {
  right: -14px;
}
.frame-dots {
  position: absolute;
  left: 0;
  right: 0;
  bottom: -22px;
  display: flex;
  justify-content: center;
  gap: 5px;
}
.frame-dot {
  width: 7px;
  height: 7px;
  padding: 0;
  border-radius: 50%;
  background: rgba(255, 245, 232, 0.35);
  border: 1px solid rgba(255, 245, 232, 0.3);
  cursor: pointer;
  transition: background 0.2s ease, transform 0.2s ease;
}
.frame-dot.active {
  background: rgba(255, 214, 120, 0.9);
  transform: scale(1.25);
}

/* ---- P7 纱帘层(两片半透明纱帘，CSS 摆动) ---- */
.layer-curtains {
  inset: -8%;
  pointer-events: none;
}
.curtain {
  position: absolute;
  top: 12%;
  width: 8%;
  height: 46%;
  transform-origin: top center;
  opacity: 0.58;
  filter: sepia(0.12) brightness(0.96) saturate(0.95);
  animation: curtainSway 7s ease-in-out infinite;
  will-change: transform;
}
.curtain-left {
  left: 27%;
}
.curtain-right {
  left: 67%;
}
.curtain-right .curtain-img {
  transform: scaleX(-1);
}
.curtain-img {
  width: 100%;
  height: 100%;
  object-fit: fill;
  display: block;
}
@keyframes curtainSway {
  0%, 100% { transform: translateX(0) skewX(0deg) scaleY(1); }
  30% { transform: translateX(9px) skewX(2deg) scaleY(1.02); }
  70% { transform: translateX(-7px) skewX(-1.5deg) scaleY(0.985); }
}

/* ---- P6 光影层(光柱 + 桌面光斑 + 浮尘) ---- */
.layer-light {
  inset: -8%;
  pointer-events: none;
  mix-blend-mode: screen;
}
.scene-shaft {
  position: absolute;
  left: 50%;
  top: 8%;
  width: 40%;
  height: 66%;
  transform-origin: top center;
  transform: translateX(-50%) rotate(var(--rot, 0deg));
  filter: blur(18px);
  opacity: 0;
  transition: opacity 3s ease, transform 3s ease;
}
.scene-desk-glow {
  position: absolute;
  top: 54%;
  width: 46%;
  height: 32%;
  transform: translateX(-50%);
  filter: blur(20px);
  opacity: 0;
  transition: opacity 3s ease, left 3s ease;
}
.scene-dust {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  display: block;
}

.obj {
  position: absolute;
  cursor: grab;
  user-select: none;
  pointer-events: auto;
}
.obj.dragging {
  cursor: grabbing;
}
.obj.dragging .obj-img {
  transform: translateZ(0);
  filter: brightness(0.9) sepia(0.2) saturate(1.0) contrast(1.02) drop-shadow(16px 26px 20px rgba(15, 8, 3, 0.45));
}
.obj-shadow {
  position: absolute;
  left: 4%;
  right: 4%;
  bottom: -8%;
  height: 30%;
  background: radial-gradient(ellipse at 50% 50%, rgba(15, 8, 3, 0.78) 0%, rgba(15, 8, 3, 0.32) 45%, rgba(15, 8, 3, 0) 72%);
  filter: blur(5px);
  transform: translateZ(0);
  transition: opacity 0.32s ease, transform 0.32s ease;
  pointer-events: none;
}
.obj-img {
  width: 100%;
  height: auto;
  display: block;
  position: relative;
  transform: translateZ(0);
  transition: transform 0.32s cubic-bezier(0.34, 1.56, 0.64, 1), filter 0.32s ease;
  will-change: transform, filter;
  /* 暖色统一 + 轻微压暗(抑制笔记本亮屏/冷灰与暖光割裂) */
  filter: brightness(0.94) sepia(0.18) saturate(1.02) contrast(1.03) drop-shadow(10px 18px 14px rgba(15, 8, 3, 0.36));
  pointer-events: none;
}
.obj:hover .obj-img {
  transform: translateZ(0) translateY(-7px) scale(1.035);
  filter: brightness(0.95) sepia(0.2) saturate(1.05) contrast(1.02) drop-shadow(14px 24px 18px rgba(15, 8, 3, 0.4)) drop-shadow(0 0 24px rgba(255, 190, 110, 0.5));
}
.obj:hover .obj-shadow {
  opacity: 0.45;
  transform: translateZ(0) scaleX(1.12);
}
.obj-label {
  position: absolute;
  left: 50%;
  top: 100%;
  transform: translate(-50%, 6px);
  padding: 3px 10px;
  font-size: 12px;
  letter-spacing: 1px;
  color: rgba(255, 245, 232, 0.95);
  background: rgba(20, 13, 9, 0.6);
  border: 1px solid rgba(255, 245, 232, 0.14);
  border-radius: 8px;
  backdrop-filter: blur(8px);
  white-space: nowrap;
  opacity: 0;
  transition: opacity 0.25s ease, transform 0.25s ease;
  pointer-events: none;
}
.obj:hover .obj-label {
  opacity: 1;
  transform: translate(-50%, 0);
}

/* ---- 全屏毛玻璃浮层(游戏"暂停菜单"式) ---- */
.glass-overlay {
  position: absolute;
  inset: 0;
  z-index: 50;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(22, 15, 10, 0.3);
  backdrop-filter: blur(20px) saturate(1.15);
}
.overlay-panel {
  width: min(720px, 88vw);
  max-height: 82vh;
  display: flex;
  flex-direction: column;
  background: rgba(36, 25, 17, 0.46);
  border: 1px solid rgba(255, 245, 232, 0.16);
  border-radius: 16px;
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.4);
  overflow: hidden;
}
.overlay-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid rgba(255, 245, 232, 0.1);
}
.overlay-title {
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 2px;
  color: rgba(255, 245, 232, 0.95);
}
.overlay-close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: 1px solid rgba(255, 245, 232, 0.14);
  border-radius: 10px;
  background: rgba(255, 245, 232, 0.06);
  color: rgba(255, 245, 232, 0.85);
  cursor: pointer;
  transition: background 0.2s ease, transform 0.2s ease;
}
.overlay-close:hover {
  background: rgba(255, 245, 232, 0.14);
  transform: rotate(90deg);
}
.overlay-body {
  padding: 20px;
  overflow-y: auto;
}
.overlay-loading,
.overlay-empty {
  padding: 48px 0;
  text-align: center;
  color: rgba(255, 245, 232, 0.55);
  font-size: 14px;
  letter-spacing: 1px;
}
.overlay-albums {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 16px;
}
.overlay-album {
  cursor: pointer;
  border-radius: 12px;
  overflow: hidden;
  transition: transform 0.25s ease;
}
.overlay-album:hover {
  transform: translateY(-4px);
}
.oa-cover {
  aspect-ratio: 4 / 3;
  background-color: rgba(255, 245, 232, 0.08);
  background-size: cover;
  background-position: center;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.oa-cover-empty {
  color: rgba(255, 245, 232, 0.55);
  display: flex;
  align-items: center;
  justify-content: center;
}
.oa-name {
  margin-top: 8px;
  font-size: 14px;
  color: rgba(255, 248, 238, 0.98);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.oa-meta {
  margin-top: 2px;
  font-size: 12px;
  color: rgba(255, 245, 232, 0.62);
}
.overlay-placeholder {
  padding: 56px 0;
  text-align: center;
}
.ph-icon {
  font-size: 40px;
  margin-bottom: 14px;
}
.ph-text {
  font-size: 14px;
  letter-spacing: 1px;
  color: rgba(255, 245, 232, 0.6);
}

/* 浮层进入/退出动画 */
.overlay-enter-active,
.overlay-leave-active {
  transition: opacity 0.28s ease;
}
.overlay-enter-active .overlay-panel,
.overlay-leave-active .overlay-panel {
  transition: transform 0.28s cubic-bezier(0.34, 1.4, 0.64, 1), opacity 0.28s ease;
}
.overlay-enter-from,
.overlay-leave-to {
  opacity: 0;
}
.overlay-enter-from .overlay-panel,
.overlay-leave-to .overlay-panel {
  opacity: 0;
  transform: translateY(18px) scale(0.96);
}

.scene-hint {
  position: absolute;
  top: 16px;
  right: 20px;
  padding: 6px 14px;
  font-size: 13px;
  line-height: 1.4;
  color: rgba(255, 245, 232, 0.9);
  background: rgba(20, 13, 9, 0.42);
  border: 1px solid rgba(255, 245, 232, 0.12);
  border-radius: 10px;
  backdrop-filter: blur(12px) saturate(1.1);
  pointer-events: none;
  letter-spacing: 0.5px;
}

/* ---- 主题切换 + 设置入口(左上角，与右上角 scene-hint 对应) ---- */
.scene-controls {
  position: absolute;
  top: 16px;
  left: 20px;
  display: flex;
  gap: 8px;
  z-index: 60;
}
.scene-ctrl {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 13px;
  font-size: 13px;
  letter-spacing: 1px;
  color: rgba(255, 245, 232, 0.92);
  background: rgba(20, 13, 9, 0.42);
  border: 1px solid rgba(255, 245, 232, 0.12);
  border-radius: 10px;
  backdrop-filter: blur(12px) saturate(1.1);
  -webkit-backdrop-filter: blur(12px) saturate(1.1);
  cursor: pointer;
  pointer-events: auto;
  transition: background 0.2s ease, transform 0.2s ease, border-color 0.2s ease;
}
.scene-ctrl:hover {
  background: rgba(20, 13, 9, 0.64);
  border-color: rgba(255, 245, 232, 0.3);
  transform: translateY(-1px);
}
.scene-ctrl svg {
  flex-shrink: 0;
  opacity: 0.9;
}
</style>
