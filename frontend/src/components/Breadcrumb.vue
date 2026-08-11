<!-- 面包屑导航:首页 + 传入的分级项,带 path 的项可点击跳转 -->
<template>
  <nav class="breadcrumb">
    <router-link to="/" class="crumb-link">
      <el-icon class="home-icon"><HomeFilled /></el-icon>
      <span>{{ $t('nav.home') }}</span>
    </router-link>
    <template v-for="(it, i) in items" :key="i">
      <span class="sep">/</span>
      <router-link v-if="it.path" :to="it.path" class="crumb-link">{{ it.label }}</router-link>
      <span v-else class="crumb-current">{{ it.label }}</span>
    </template>
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
  gap: 8px;
  font-size: 13px;
  margin-bottom: 16px;
  color: var(--color-text-secondary);
  flex-wrap: wrap;
}
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