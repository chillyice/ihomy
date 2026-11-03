<template>
  <div class="mobile-tabbar">
    <!-- 滑动指示条:跟着当前 Tab 平移,让切换有连续方向感 -->
    <span class="tab-indicator" :style="{ transform: `translateX(${activeIndex * 100}%)` }" aria-hidden="true" />
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
import { computed } from 'vue'
import { HomeFilled, Grid, User } from '@element-plus/icons-vue'

const props = defineProps({ modelValue: String })
defineEmits(['update:modelValue'])
const activeIndex = computed(() => ({ home: 0, more: 1, me: 2 })[props.modelValue] ?? 0)
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
  z-index: var(--z-nav);
}
/* 顶部 3px 指示条跟着 Tab 滑动;本身不吃指针事件 */
.tab-indicator {
  position: absolute;
  top: 0;
  left: 0;
  width: 33.3333%;
  height: 3px;
  display: flex;
  justify-content: center;
  pointer-events: none;
  transition: transform 0.32s var(--ease-soft);
}
.tab-indicator::after {
  content: '';
  width: 28px;
  height: 3px;
  border-radius: 0 0 3px 3px;
  background: var(--color-brand);
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
  transition:
    transform 0.28s var(--ease-spring),
    color 0.2s ease;
}
.tab-item.active .el-icon {
  transform: translateY(-2px) scale(1.1);
}
.tab-item:active .el-icon {
  transform: scale(0.9);
}
.tab-item.active {
  color: var(--color-primary, var(--color-brand));
}
html.dark .tab-item.active {
  color: var(--color-brand);
}
</style>
