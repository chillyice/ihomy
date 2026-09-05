<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('tools.title') }]" />

    <div class="page-toolbar card">
      <div class="tb-left">
        <span class="section-label">{{ $t('tools.title') }}</span>
      </div>
    </div>

    <div class="tool-grid">
      <div class="tool-card card" @click="$router.push('/tools/mindmap')">
        <div class="tool-icon"><el-icon :size="30"><Tools /></el-icon></div>
        <div class="tool-name">{{ $t('tools.mindmap.title') }}</div>
        <div class="tool-desc">{{ $t('tools.mindmap.desc') }}</div>
        <span class="tool-enter">{{ $t('tools.enter') }}<el-icon><ArrowRight /></el-icon></span>
      </div>
      <div class="tool-card card tool-disabled">
        <div class="tool-icon"><el-icon :size="30"><MagicStick /></el-icon></div>
        <div class="tool-name">{{ $t('tools.more') }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { Tools, ArrowRight, MagicStick } from '@element-plus/icons-vue'
import Breadcrumb from '@/components/Breadcrumb.vue'
</script>

<style scoped>
.tool-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 16px;
}
.tool-card {
  position: relative;
  cursor: pointer;
  transition: transform 0.2s ease;
  contain: layout style;
}
.tool-card:hover { transform: translateY(-3px); }
.tool-disabled { cursor: default; opacity: 0.55; border-style: dashed; }
.tool-icon {
  width: 56px;
  height: 56px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-accent-weak, rgba(184, 140, 110, 0.14));
  color: var(--color-accent, #b88c6e);
  margin-bottom: 14px;
}
.tool-name { font-size: 16px; font-weight: 600; margin-bottom: 6px; }
.tool-desc { font-size: 13px; color: var(--color-text-secondary, #8a8a8a); line-height: 1.5; }
.tool-enter {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: 12px;
  font-size: 13px;
  color: var(--color-accent, #b88c6e);
}
</style>
