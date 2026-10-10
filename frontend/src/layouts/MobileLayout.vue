<template>
  <div class="mobile-layout">
    <!-- 首页路由:三 Tab 模式 -->
    <template v-if="isHomeRoute">
      <!-- 用 v-show 而非 v-if:三个 Tab 常驻挂载,切回时不重新拉数据、不丢滚动位置与输入状态 -->
      <div class="mobile-tab-content">
        <MobileHomeFeed v-show="activeTab === 'home'" />
        <MobileMoreGrid v-show="activeTab === 'more'" />
        <MobileMePage v-show="activeTab === 'me'" />
      </div>
      <MobileTabBar v-model="activeTab" />
    </template>

    <!-- 子页面:顶部返回栏 + router-view -->
    <template v-else>
      <MobileHeader :title="pageTitle">
        <template #right>
          <slot name="header-right" />
        </template>
      </MobileHeader>
      <div class="mobile-sub-content">
        <router-view />
      </div>
    </template>

    <BackToTop />
    <InstallPrompt />
    <MusicPlayer />
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
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

const route = useRoute()
const { t } = useI18n()
const activeTab = ref('home')

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
</style>
