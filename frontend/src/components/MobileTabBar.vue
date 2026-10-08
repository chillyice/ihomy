<template>
  <div class="mobile-tabbar">
    <div v-a11y-click class="tab-item" :class="{ active: modelValue === 'home' }" @click="$emit('update:modelValue', 'home')">
      <el-icon><HomeFilled /></el-icon>
      <span>{{ $t('mobile.home') }}</span>
    </div>
    <div v-a11y-click class="tab-item" :class="{ active: modelValue === 'more' }" @click="$emit('update:modelValue', 'more')">
      <el-icon><Grid /></el-icon>
      <span>{{ $t('mobile.more') }}</span>
    </div>
    <div v-a11y-click class="tab-item" :class="{ active: modelValue === 'me' }" @click="$emit('update:modelValue', 'me')">
      <el-icon><User /></el-icon>
      <span>{{ $t('mobile.me') }}</span>
    </div>
  </div>
</template>

<script setup>
import { HomeFilled, Grid, User } from '@element-plus/icons-vue'
defineProps({ modelValue: String })
defineEmits(['update:modelValue'])
</script>

<style scoped>
/* 固定底栏:高度 56px 与 MobileLayout 的 .mobile-tab-content padding-bottom 是同一契约,须同步改。
   这里用 backdrop-filter 是刻意保留的例外——只在固定的窄条上生效、不随长页面滚动,不触发重算开销。 */
.mobile-tabbar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  height: calc(56px + env(safe-area-inset-bottom, 0px));
  padding-bottom: env(safe-area-inset-bottom, 0px);
  display: flex;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(20px) saturate(1.1);
  -webkit-backdrop-filter: blur(20px) saturate(1.1);
  border-top: 1px solid rgba(0, 0, 0, 0.06);
  z-index: 60;
}
html.dark .mobile-tabbar {
  background: rgba(var(--color-card-rgb), 0.92);
  border-top-color: rgba(255, 255, 255, 0.08);
}
.tab-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  color: var(--color-text-secondary, #999);
  font-size: 11px;
  cursor: pointer;
  transition: color 0.2s;
  -webkit-tap-highlight-color: transparent;
}
.tab-item .el-icon {
  font-size: 22px;
}
.tab-item.active {
  color: var(--color-primary, var(--color-brand));
}
html.dark .tab-item.active {
  color: var(--color-brand);
}
</style>
