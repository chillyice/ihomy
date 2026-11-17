<template>
  <div class="mobile-layout" :class="{ 'has-tabbar': showTabBar }">
    <!-- 首页路由:三 Tab 模式 -->
    <template v-if="isHomeRoute">
      <!-- KeepAlive 保住三个 Tab 的组件状态(筛选/输入),切换只做轻量过渡,不重新拉数据 -->
      <div
        class="mobile-tab-content"
        @touchstart.passive="onTouchStart"
        @touchmove.passive="onTouchMove"
        @touchend.passive="onTouchEnd"
        @touchcancel="onTouchCancel"
      >
        <Transition :name="tabTransition" mode="out-in" @after-enter="restoreTabScroll">
          <KeepAlive>
            <component :is="tabComponent" :key="activeTab" />
          </KeepAlive>
        </Transition>
      </div>
    </template>

    <!-- 子页面:顶部返回栏 + router-view(页面切换带方向感的淡入) -->
    <template v-else>
      <!-- 游戏沉浸态:页头与底栏全部收起,整屏交给游戏;返回键由左上角浮动按钮提供 -->
      <MobileHeader v-if="!isGameRoute" :title="pageTitle">
        <template #right>
          <slot name="header-right" />
        </template>
      </MobileHeader>
      <div class="mobile-sub-content" :class="{ 'is-game-stage': isGameRoute }">
        <router-view v-slot="{ Component, route: childRoute }">
          <Transition :name="childRoute.meta.transition || 'mobile-page'" mode="out-in">
            <component :is="Component" :key="childRoute.path" />
          </Transition>
        </router-view>
      </div>
    </template>

    <!-- 底部导航:五格可自定义,站内页面常驻(沉浸页/游戏/登录页除外) -->
    <MobileTabBar v-if="showTabBar" :items="tabs" :active-code="activeCode" :capturing="capturing" @pick="onTabPick" @edit="openEditor" />
    <MobileTabEditor v-model="editorOpen" />

    <MobileGameChrome v-if="isGameRoute" @back="exitGame" />

    <BackToTop />
    <InstallPrompt />
    <MusicPlayer v-if="floatsStore.music" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, provide, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import MobileTabBar from '@/components/MobileTabBar.vue'
import MobileTabEditor from '@/components/MobileTabEditor.vue'
import MobileHeader from '@/components/MobileHeader.vue'
import MobileGameChrome from '@/components/games/MobileGameChrome.vue'
import MobileHomeFeed from '@/components/MobileHomeFeed.vue'
import MobileMoreGrid from '@/components/MobileMoreGrid.vue'
import MobileMePage from '@/components/MobileMePage.vue'
import BackToTop from '@/components/BackToTop.vue'
import InstallPrompt from '@/components/InstallPrompt.vue'
import MusicPlayer from '@/components/MusicPlayer.vue'
import { isMobileGamePath, isMobileHomePath, mobileHeaderTitleKey } from '@/utils/mobileNav'
import { isNativeApp } from '@/utils/platform'
import { useDevice } from '@/composables/useDevice'
import { GAME_EXIT_KEY, createGameExitRegistry } from '@/composables/useGameExit'
import { isMobileTabActive } from '@/utils/mobileTabs'
import { quickCapture, resetQuickAlbumCache } from '@/utils/quickCapture'
import { resetPrefCache } from '@/utils/userPref'
import { useMobileTabs, resetMobileTabsSync } from '@/composables/useMobileTabs'
import { useUserStore } from '@/stores/user'
import { useFloatsStore } from '@/stores/floats'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const floatsStore = useFloatsStore()
const userStore = useUserStore()
// 只有真手机(判据在 useDevice)才自动进浏览器全屏:窄窗桌面端不抢窗口
const { isPhone } = useDevice()
const activeTab = ref('home')

const { tabs, editorOpen, openEditor, ensureSynced } = useMobileTabs()

// 三个 Tab 常驻缓存,切换时只换当前组件;KeepAlive 保证筛选/输入等状态不丢
const tabComponent = computed(() => ({ home: MobileHomeFeed, more: MobileMoreGrid, me: MobileMePage })[activeTab.value])

// ========== 左右滑动切 Tab ==========
// 触摸手势只做「意图识别」,不 preventDefault:纵向滚动、内部横滑列表都保持浏览器原生行为。
const TAB_ORDER = ['home', 'more', 'me']
// 只有还在底栏上的外壳 Tab 才允许滑到:用户把「更多」换掉后,首页左滑应直接到「我的」而不是进一个没有入口的幽灵页
const tabOrder = computed(() => TAB_ORDER.filter((t) => t === activeTab.value || tabs.value.some((i) => i.shellTab === t)))
// 点底栏用轻位移过渡,滑动按方向给左右推入,方向感与手势一致
const tabTransition = ref('mobile-tab')
// 每个 Tab 记住离开时的滚动位置,切回来不从头开始(否则从「我的」切回首页会跳到半截)
const tabScroll = reactive({ home: 0, more: 0, me: 0 })
let touchState = null

const SWIPE_IGNORE_SELECTOR = [
  'input',
  'textarea',
  'select',
  '[contenteditable="true"]',
  'audio',
  'video',
  '.el-dialog',
  '.el-drawer',
  '.el-message-box',
  '.el-overlay',
  '.photo-viewer',
  '.el-image-viewer__wrapper',
  '.filter-bar',
  '.el-slider',
  '.el-carousel',
  '.chat-float-sheet',
].join(', ')

const startTabSwitch = (next, transition = 'mobile-tab') => {
  if (!tabOrder.value.includes(next) || next === activeTab.value) return
  tabScroll[activeTab.value] = window.scrollY
  tabTransition.value = transition
  activeTab.value = next
}

const restoreTabScroll = () => {
  window.scrollTo(0, tabScroll[activeTab.value] || 0)
}

// 从触摸目标往上找横向可滚动容器(筛选条/横向卡片列表):这类区域优先响应自身横滑,不能抢来切 Tab
const canStartSwipe = (target) => {
  if (!(target instanceof Element) || target.closest(SWIPE_IGNORE_SELECTOR)) return false
  let el = target
  while (el && el !== document.body) {
    if (el.scrollWidth > el.clientWidth + 4) {
      const overflowX = getComputedStyle(el).overflowX
      if (overflowX === 'auto' || overflowX === 'scroll') return false
    }
    el = el.parentElement
  }
  return true
}

const onTouchStart = (e) => {
  touchState = null
  // 多指(缩放/双指滑动)与屏幕最左右 24px(Safari 前进后退手势区)都不接
  if (e.touches.length !== 1) return
  const t = e.touches[0]
  if (t.clientX < 24 || t.clientX > window.innerWidth - 24) return
  if (!canStartSwipe(e.target)) return
  touchState = { x: t.clientX, y: t.clientY, at: Date.now() }
}

const onTouchMove = (e) => {
  if (!touchState || e.touches.length !== 1) return
  const t = e.touches[0]
  const dx = t.clientX - touchState.x
  const dy = t.clientY - touchState.y
  // 纵向意图一旦明确就放弃本次手势,交给页面正常滚动
  if (Math.abs(dy) > 12 && Math.abs(dy) > Math.abs(dx)) touchState = null
}

const onTouchCancel = () => {
  touchState = null
}

const onTouchEnd = (e) => {
  const start = touchState
  touchState = null
  const t = e.changedTouches[0]
  if (!start || !t) return
  const dx = t.clientX - start.x
  const dy = t.clientY - start.y
  if (Date.now() - start.at > 650) return
  // 横向位移足够、且明显大于纵向,才认成切 Tab
  if (Math.abs(dx) < 48 || Math.abs(dx) < Math.abs(dy) * 1.2) return
  const idx = tabOrder.value.indexOf(activeTab.value)
  const nextIdx = dx < 0 ? idx + 1 : idx - 1
  if (nextIdx < 0 || nextIdx >= tabOrder.value.length) return
  startTabSwitch(tabOrder.value[nextIdx], dx < 0 ? 'mobile-tab-next' : 'mobile-tab-prev')
}

// 首页判据与页头标题键都在 utils/mobileNav.js(纯函数,带单测);路由把 '/' 重定向到 '/home',两者都要认
const isHomeRoute = computed(() => isMobileHomePath(route.path))

// 游戏沉浸态:进游戏就收起页头/底栏,整屏让给游戏;判据是纯函数(utils/mobileNav.js)
const isGameRoute = computed(() => isMobileGamePath(route.path))

// 沉浸页(阅读器/游戏等)与登录类页面不挂底栏:这些页面需要整屏或本就无站内导航
const HIDE_TABBAR_PATHS = ['/login', '/reset-password']
const showTabBar = computed(() => !route.meta.immersive && !isGameRoute.value && !HIDE_TABBAR_PATHS.includes(route.path))

// 房间局(飞行棋等)退出前要退房/结束房间:页面注册自己的退出逻辑,浮动返回键优先走它
const gameExit = createGameExitRegistry()
provide(GAME_EXIT_KEY, gameExit.provideValue)

/** 原生壳:游戏期间隐藏系统状态栏(Web 端由浏览器全屏接管) */
async function applyNativeStatusBar(hidden) {
  if (!isNativeApp()) return
  try {
    const m = await import('@/native/statusBar')
    await m.setStatusBarHidden(hidden)
  } catch (e) {
    console.warn('[game] 状态栏切换失败', e)
  }
}

const canUseBrowserFullscreen = () => !isNativeApp() && typeof document?.documentElement?.requestFullscreen === 'function'

/**
 * 手机浏览器进游戏:直接吃掉地址栏与系统状态栏,整块屏幕交给游戏。
 * 浏览器只认用户手势,从游戏卡点进来时成功;直达深链/浏览器返回时会被拒绝,静默保持窗口态,
 * 左上角的浮动全屏按钮仍可手动补一次。
 */
async function enterGameFullscreen() {
  if (!isPhone.value || document.fullscreenElement || !canUseBrowserFullscreen()) return
  try {
    await document.documentElement.requestFullscreen({ navigationUI: 'hide' })
  } catch {
    // iOS Safari 不支持 / 没有用户手势:保持窗口态,不影响继续玩
  }
}

/** 离开游戏:不管走浮动返回还是系统返回手势,都要把全屏收掉,否则整站会留在全屏里 */
function exitGameFullscreen() {
  if (!document.fullscreenElement) return
  document.exitFullscreen?.().catch(() => {})
}

/** 进出游戏时同步 html 根类:main.css 据此收起外壳、让页面占满视口 */
function applyGameMode(on) {
  document.documentElement.classList.toggle('game-mobile-immersive', on)
  applyNativeStatusBar(on)
  if (on) {
    window.scrollTo(0, 0)
    enterGameFullscreen()
  } else {
    exitGameFullscreen()
  }
}

/** 左上角浮动返回:优先页面注册的退出逻辑,否则回到访问前的页面(直达深链回游戏列表) */
async function exitGame() {
  if (document.fullscreenElement) {
    try {
      await document.exitFullscreen?.()
    } catch {
      // 退出全屏失败不阻塞返回
    }
  }
  if (gameExit.hasHandler()) {
    gameExit.run()
    return
  }
  if (window.history.state && window.history.state.back != null) router.back()
  else router.push('/games')
}

watch(isGameRoute, applyGameMode, { immediate: true })

onBeforeUnmount(() => {
  applyGameMode(false)
  gameExit.reset()
})

// 当前高亮:首页看三 Tab,子页面按路由前缀匹配(详情页也点亮所属入口)
const activeCode = computed(() => {
  if (isHomeRoute.value) return activeTab.value
  const hit = tabs.value.find((item) => isMobileTabActive(item, route.path))
  return hit ? hit.code : ''
})

// ========== 底栏交互 ==========
const capturing = ref(false)

const onTabPick = (code) => {
  const item = tabs.value.find((i) => i.code === code)
  if (!item) return
  if (item.capture) {
    runQuickCapture()
    return
  }
  if (item.shellTab) {
    if (!isHomeRoute.value) router.push('/home')
    startTabSwitch(item.shellTab)
    return
  }
  if (route.path !== item.path) router.push(item.path)
}

const runQuickCapture = async () => {
  if (capturing.value) return
  if (!userStore.isLoggedIn) {
    ElMessage.warning(t('mobile.captureLogin'))
    router.push('/login')
    return
  }
  capturing.value = true
  try {
    const res = await quickCapture(t('mobile.captureAlbum'))
    if (!res.skipped) ElMessage.success(t('mobile.captureDone'))
  } catch (e) {
    if (e && e.code === 'cancel') return
    if (e && e.code === 'unavailable') {
      ElMessage.warning(t('mobile.captureUnavailable'))
      return
    }
    console.warn('[mobile] quick capture failed', e)
    ElMessage.error(t('album.captureFailed'))
  } finally {
    capturing.value = false
  }
}

const pageTitle = computed(() => {
  const key = mobileHeaderTitleKey(route.path)
  return key ? t(key) : ''
})

watch(
  () => route.path,
  () => {
    window.scrollTo(0, 0)
  },
)

// 换账号(退出)后清掉「随手拍」相册缓存,避免下一个登录者往上一家的相册里传;
// 登录后补一次底栏偏好同步(游客阶段 ensureSynced 会直接返回,不会把本机当已对齐)
watch(
  () => userStore.isLoggedIn,
  (v) => {
    resetMobileTabsSync()
    resetPrefCache()
    resetQuickAlbumCache()
    if (v) ensureSynced()
  },
)

onMounted(ensureSynced)
</script>

<style scoped>
.mobile-layout {
  min-height: 100vh;
}
/* 这两个 padding 是跨文件高度契约:56px 必须与 MobileTabBar 的高度一致、48px 与 MobileHeader 一致,
   改任一处都要同步改这里,否则内容会被底栏压住或顶部露出(含 safe-area 刘海/小白条) */
.mobile-tab-content {
  padding-bottom: calc(56px + env(safe-area-inset-bottom, 0px));
  min-height: 100vh;
}
.mobile-sub-content {
  padding-top: calc(48px + env(safe-area-inset-top, 0px));
  min-height: 100vh;
}
/* 游戏沉浸态:撤掉页头占位,整屏交给游戏(安全区由游戏页自身内边距处理) */
.mobile-sub-content.is-game-stage {
  padding: 0;
  min-height: 0;
  height: 100dvh;
  overflow: hidden;
}
/* 子页面也常驻底栏:留出与首页同一份高度,避免最后一行被加号挡住 */
.has-tabbar .mobile-sub-content {
  padding-bottom: calc(56px + env(safe-area-inset-bottom, 0px));
}
/* 回顶按钮让开底栏:底栏高 56px,再留 14px 呼吸位,避免压住「我的」Tab 的点击区 */
.mobile-layout :deep(.back-to-top) {
  bottom: calc(56px + env(safe-area-inset-bottom, 0px) + 14px);
}
</style>
