<template>
  <div class="mobile-layout">
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
      <MobileTabBar :model-value="activeTab" @update:model-value="onTabPick" />
    </template>

    <!-- 子页面:顶部返回栏 + router-view(页面切换带方向感的淡入) -->
    <template v-else>
      <MobileHeader :title="pageTitle">
        <template #right>
          <slot name="header-right" />
        </template>
      </MobileHeader>
      <div class="mobile-sub-content">
        <router-view v-slot="{ Component, route: childRoute }">
          <Transition :name="childRoute.meta.transition || 'mobile-page'" mode="out-in">
            <component :is="Component" :key="childRoute.path" />
          </Transition>
        </router-view>
      </div>
    </template>

    <BackToTop />
    <InstallPrompt />
    <MusicPlayer v-if="floatsStore.music" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import MobileTabBar from '@/components/MobileTabBar.vue'
import MobileHeader from '@/components/MobileHeader.vue'
import MobileHomeFeed from '@/components/MobileHomeFeed.vue'
import MobileMoreGrid from '@/components/MobileMoreGrid.vue'
import MobileMePage from '@/components/MobileMePage.vue'
import BackToTop from '@/components/BackToTop.vue'
import InstallPrompt from '@/components/InstallPrompt.vue'
import MusicPlayer from '@/components/MusicPlayer.vue'
import { isMobileHomePath, mobileHeaderTitleKey } from '@/utils/mobileNav'
import { useFloatsStore } from '@/stores/floats'

const route = useRoute()
const { t } = useI18n()
const floatsStore = useFloatsStore()
const activeTab = ref('home')

// 三个 Tab 常驻缓存,切换时只换当前组件;KeepAlive 保证筛选/输入等状态不丢
const tabComponent = computed(() => ({ home: MobileHomeFeed, more: MobileMoreGrid, me: MobileMePage })[activeTab.value])

// ========== 左右滑动切 Tab ==========
// 触摸手势只做「意图识别」,不 preventDefault:纵向滚动、内部横滑列表都保持浏览器原生行为。
const TAB_ORDER = ['home', 'more', 'me']
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
  if (!TAB_ORDER.includes(next) || next === activeTab.value) return
  tabScroll[activeTab.value] = window.scrollY
  tabTransition.value = transition
  activeTab.value = next
}

const onTabPick = (next) => startTabSwitch(next)

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
  const idx = TAB_ORDER.indexOf(activeTab.value)
  const nextIdx = dx < 0 ? idx + 1 : idx - 1
  if (nextIdx < 0 || nextIdx >= TAB_ORDER.length) return
  startTabSwitch(TAB_ORDER[nextIdx], dx < 0 ? 'mobile-tab-next' : 'mobile-tab-prev')
}

// 首页判据与页头标题键都在 utils/mobileNav.js(纯函数,带单测);路由把 '/' 重定向到 '/home',两者都要认
const isHomeRoute = computed(() => isMobileHomePath(route.path))

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
/* 回顶按钮让开底栏:底栏高 56px,再留 14px 呼吸位,避免压住「我的」Tab 的点击区 */
.mobile-layout :deep(.back-to-top) {
  bottom: calc(56px + env(safe-area-inset-bottom, 0px) + 14px);
}
</style>
