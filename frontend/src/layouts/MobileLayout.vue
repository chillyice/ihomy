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

const route = useRoute()
const { t } = useI18n()
const activeTab = ref('home')

const isHomeRoute = computed(() => route.path === '/')

// 页头标题:走 i18n(切语言即时跟着变),键为空即不显示标题
const PAGE_TITLES = {
  '/blog': 'blog.title',
  '/diary': 'diary.title',
  '/album': 'album.title',
  '/anniversary': 'anniversary.title',
  '/cinema': 'cinema.title',
  '/music': 'music.title',
  '/member': 'member.title',
  '/points': 'points.title',
  '/task': 'task.title',
  '/reminder': 'reminder.title',
  '/plan': 'plan.title',
  '/wish': 'wish.title',
  '/book': 'book.title',
  '/chat': 'chat.title',
  '/tree': 'tree.title',
  '/item': 'mobile.title.item',
  '/kitchen': 'kitchen.title',
  '/library': 'library.title',
  '/settings': 'settings.title',
  '/ops': 'mobile.title.ops',
  '/vault': 'vault.title',
  '/login': 'mobile.title.login',
}
const pageTitle = computed(() => {
  const p = route.path
  const edit = p.includes('/edit/')
  if (p.startsWith('/blog/')) return t(edit ? 'mobile.title.blogEdit' : 'mobile.title.blogDetail')
  if (p.startsWith('/diary')) return t(edit ? 'mobile.title.diaryWrite' : 'diary.title')
  if (p.startsWith('/album/')) return t('mobile.title.albumDetail')
  if (p.startsWith('/library/')) return t(edit ? 'mobile.title.bookEdit' : 'mobile.title.bookDetail')
  if (p.startsWith('/kitchen/recipe/')) return t(edit ? 'mobile.title.recipeEdit' : 'mobile.title.recipeDetail')
  if (p.startsWith('/kitchen/ingredients')) return t('mobile.title.ingredients')
  if (p.startsWith('/cinema/')) return t('mobile.title.cinemaDetail')
  if (p.startsWith('/tools/loan')) return t('mobile.title.loan')
  const key = PAGE_TITLES[p]
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
