<!-- 面包屑导航:首页 + 传入的分级项,带 to/path 的项可点击跳转 -->
<template>
  <nav class="breadcrumb">
    <div class="crumb-left">
      <router-link to="/" class="crumb-link">
        <el-icon class="home-icon"><HomeFilled /></el-icon>
        <span>{{ $t('nav.home') }}</span>
      </router-link>
      <template v-for="(it, i) in items" :key="i">
        <span class="sep">/</span>
        <router-link v-if="it.to || it.path" :to="it.to || it.path" class="crumb-link">{{ it.label }}</router-link>
        <span v-else class="crumb-current">{{ it.label }}</span>
      </template>
    </div>
    <div v-if="$slots.right" class="crumb-right">
      <slot name="right" />
    </div>
  </nav>
</template>

<script setup>
import { HomeFilled } from '@element-plus/icons-vue'

defineProps({
  items: { type: Array, default: () => [] },
})
</script>

<style scoped>
.breadcrumb {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 13px;
  margin: -16px -16px 12px;
  color: var(--color-text-secondary);
  flex-wrap: wrap;
  position: sticky;
  top: 0;
  z-index: 20;
  padding: 10px 16px;
  background: var(--color-card);
  backdrop-filter: blur(12px) saturate(1.1);
  -webkit-backdrop-filter: blur(12px) saturate(1.1);
  border-bottom: 1px solid var(--color-border);
  -webkit-mask-image: linear-gradient(to right, transparent 0%, black 24px, black calc(100% - 24px), transparent 100%);
  mask-image: linear-gradient(to right, transparent 0%, black 24px, black calc(100% - 24px), transparent 100%);
}
.crumb-left { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.crumb-right { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }
.crumb-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--color-text-secondary);
  transition: color 0.15s;
}
.crumb-link:hover { color: var(--color-accent); }
.home-icon { font-size: 14px; }
.sep { color: #cdd5df; }
.crumb-current { color: var(--color-text); font-weight: 500; }
</style>