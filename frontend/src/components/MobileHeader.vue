<template>
  <div class="mobile-header">
    <span v-a11y-click class="back-btn" @click="goBack">
      <el-icon><ArrowLeft /></el-icon>
    </span>
    <!-- 直达首页:详情/编辑页的返回只上一级,想一步回首页走这个键 -->
    <span v-a11y-click class="home-btn" :title="$t('mobile.home')" @click="goHome">
      <el-icon><HomeFilled /></el-icon>
    </span>
    <span class="header-title">{{ title }}</span>
    <span class="header-right">
      <slot name="right" />
    </span>
  </div>
</template>

<script setup>
import { ArrowLeft, HomeFilled } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import { parentPathOf } from '@/utils/navParent'
defineProps({ title: { type: String, default: '' } })
const route = useRoute()
const router = useRouter()
const goBack = () => {
  // 上一级而不是上一页:从首页卡片/聊天分享/通知深链直达详情时,浏览历史里的来路不是上级;
  // 按路由表退一级,落点与从列表点进来的路径一致(见 utils/navParent.js)
  router.push(parentPathOf(route.path, router.getRoutes()) || '/home')
}
const goHome = () => router.push('/')
</script>

<style scoped>
.mobile-header {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: calc(48px + var(--safe-area-inset-top, env(safe-area-inset-top, 0px)));
  padding-top: var(--safe-area-inset-top, env(safe-area-inset-top, 0px));
  display: flex;
  align-items: center;
  gap: 4px;
  padding-left: 8px;
  padding-right: 12px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(20px) saturate(1.1);
  -webkit-backdrop-filter: blur(20px) saturate(1.1);
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
  z-index: var(--z-nav);
}
html.dark .mobile-header {
  background: rgba(var(--color-card-rgb), 0.92);
  border-bottom-color: rgba(255, 255, 255, 0.08);
}
.back-btn,
.home-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 10px;
  cursor: pointer;
  color: var(--color-text-primary, #333);
  -webkit-tap-highlight-color: transparent;
  font-size: 18px;
  transition:
    background 0.18s ease,
    transform 0.18s var(--ease-spring);
}
.back-btn:active,
.home-btn:active {
  background: rgba(0, 0, 0, 0.06);
  transform: scale(0.9);
}
html.dark .back-btn,
html.dark .home-btn {
  color: #e8dcc8;
}
html.dark .back-btn:active,
html.dark .home-btn:active {
  background: rgba(255, 255, 255, 0.08);
}
.header-title {
  flex: 1;
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-primary, #333);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
html.dark .header-title {
  color: #e8dcc8;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
html.theme-warm .mobile-header {
  background: rgba(var(--color-card-rgb), 0.96);
  border-color: var(--color-border);
}
html.theme-warm .header-title {
  color: var(--color-text);
}
html.theme-warm .back-btn,
html.theme-warm .home-btn {
  color: var(--color-green);
}
html.theme-warm .back-btn:active,
html.theme-warm .home-btn:active {
  background: var(--color-line);
}
</style>
