<template>
  <div class="mobile-layout">
    <!-- 首页路由:三 Tab 模式 -->
    <template v-if="isHomeRoute">
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
import MobileTabBar from '@/components/MobileTabBar.vue'
import MobileHeader from '@/components/MobileHeader.vue'
import MobileHomeFeed from '@/components/MobileHomeFeed.vue'
import MobileMoreGrid from '@/components/MobileMoreGrid.vue'
import MobileMePage from '@/components/MobileMePage.vue'
import BackToTop from '@/components/BackToTop.vue'
import InstallPrompt from '@/components/InstallPrompt.vue'
import MusicPlayer from '@/components/MusicPlayer.vue'

const route = useRoute()
const activeTab = ref('home')

const isHomeRoute = computed(() => route.path === '/')

const PAGE_TITLES = {
  '/blog': '博客', '/diary': '日记', '/album': '相册', '/anniversary': '纪念日',
  '/cinema': '放映厅', '/music': '音乐', '/member': '成员', '/points': '积分',
  '/task': '任务', '/reminder': '提醒', '/plan': '计划', '/wish': '愿望单',
  '/book': '记账', '/chat': '聊天室', '/tree': '家谱', '/cascade': '照片瀑布',
  '/item': '物品', '/kitchen': '厨房', '/library': '书架', '/settings': '设置',
  '/ops': '运维管理', '/login': '登录',
}
const pageTitle = computed(() => {
  if (route.path.startsWith('/blog/')) return route.path.includes('/edit/') ? '编辑博客' : '博客详情'
  if (route.path.startsWith('/diary')) return route.path.includes('/edit/') ? '写日记' : '日记'
  if (route.path.startsWith('/album/')) return '相册详情'
  if (route.path.startsWith('/library/')) return route.path.includes('/edit/') ? '编辑图书' : '图书详情'
  if (route.path.startsWith('/kitchen/recipe/')) return route.path.includes('/edit/') ? '编辑菜谱' : '菜谱详情'
  if (route.path.startsWith('/kitchen/ingredients')) return '食材'
  return PAGE_TITLES[route.path] || ''
})

watch(() => route.path, () => { window.scrollTo(0, 0) })
</script>

<style scoped>
.mobile-layout { min-height: 100vh; }
.mobile-tab-content { padding-bottom: calc(56px + env(safe-area-inset-bottom, 0px)); min-height: 100vh; }
.mobile-sub-content { padding-top: calc(48px + env(safe-area-inset-top, 0px)); min-height: 100vh; }
</style>
