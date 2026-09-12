<template>
  <div class="mobile-header">
    <span class="back-btn" @click="goBack">
      <el-icon><ArrowLeft /></el-icon>
    </span>
    <!-- 直达首页:功能页可能有多级历史(列表→详情→编辑),返回键逐级回退太深 -->
    <span class="home-btn" :title="$t('mobile.home')" @click="goHome">
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
import { useRouter } from 'vue-router'
const props = defineProps({ title: { type: String, default: '' } })
const router = useRouter()
const goBack = () => {
  // vue-router 会在 history.state.back 记录应用内上一条路由;无记录说明是外链/PWA 直达深页,
  // 此时 history.back() 会退出站点,改为直接回首页
  if (window.history.state && window.history.state.back != null) router.back()
  else router.push('/')
}
const goHome = () => router.push('/')
</script>

<style scoped>
.mobile-header {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: calc(48px + env(safe-area-inset-top, 0px));
  padding-top: env(safe-area-inset-top, 0px);
  display: flex;
  align-items: center;
  gap: 4px;
  padding-left: 8px;
  padding-right: 12px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(20px) saturate(1.1);
  -webkit-backdrop-filter: blur(20px) saturate(1.1);
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
  z-index: 60;
}
html.dark .mobile-header {
  background: rgba(var(--color-card-rgb), 0.92);
  border-bottom-color: rgba(255, 255, 255, 0.08);
}
.back-btn, .home-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  cursor: pointer;
  color: var(--color-text-primary, #333);
  -webkit-tap-highlight-color: transparent;
  font-size: 18px;
}
.back-btn:active, .home-btn:active { background: rgba(0, 0, 0, 0.06); }
html.dark .back-btn, html.dark .home-btn { color: #E8DCC8; }
html.dark .back-btn:active, html.dark .home-btn:active { background: rgba(255, 255, 255, 0.08); }
.header-title {
  flex: 1;
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-primary, #333);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
html.dark .header-title { color: #E8DCC8; }
.header-right { display: flex; align-items: center; gap: 8px; }
</style>
