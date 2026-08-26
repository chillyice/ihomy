<template>
  <div class="mobile-tabbar">
    <div class="tab-item" :class="{ active: modelValue === 'home' }" @click="$emit('update:modelValue', 'home')">
      <el-icon><HomeFilled /></el-icon>
      <span>{{ $t('mobile.home') }}</span>
    </div>
    <div class="tab-item" :class="{ active: modelValue === 'more' }" @click="$emit('update:modelValue', 'more')">
      <el-icon><Grid /></el-icon>
      <span>{{ $t('mobile.more') }}</span>
    </div>
    <div class="tab-item" :class="{ active: modelValue === 'me' }" @click="$emit('update:modelValue', 'me')">
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
  background: rgba(20, 28, 45, 0.92);
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
.tab-item .el-icon { font-size: 22px; }
.tab-item.active { color: var(--color-primary, #b88c6e); }
html.dark .tab-item.active { color: #d4b298; }
</style>
