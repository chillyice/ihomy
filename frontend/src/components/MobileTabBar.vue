<template>
  <div class="mobile-tabbar" :class="{ 'has-capture': captureIndex >= 0 }">
    <!-- 滑动指示条:跟着当前 Tab 平移;没命中任何入口(拍摄/详情页)时隐藏 -->
    <span
      v-if="activeIndex >= 0"
      class="tab-indicator"
      :style="{ width: `${100 / items.length}%`, transform: `translateX(${activeIndex * 100}%)` }"
      aria-hidden="true"
    />
    <div
      v-for="item in items"
      :key="item.code"
      v-a11y-click
      class="tab-item"
      :class="{ active: item.code === activeCode, 'is-capture': item.capture }"
      :aria-label="labelOf(item)"
      @pointerdown="startHold"
      @pointerup="endHold"
      @pointercancel="endHold"
      @pointerleave="endHold"
      @pointermove="cancelHold"
      @click="onPick(item.code)"
    >
      <span v-if="item.capture" class="capture-btn" :class="{ busy: capturing }">
        <span v-if="capturing" class="capture-spinner" aria-hidden="true" />
        <el-icon v-else><Plus /></el-icon>
      </span>
      <el-icon v-else class="tab-icon"><component :is="iconOf(item)" /></el-icon>
      <span v-if="!item.capture" class="tab-label">{{ labelOf(item) }}</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { HomeFilled, Grid, User, Plus } from '@element-plus/icons-vue'
import { iconComp } from '@/utils/navModules'

const props = defineProps({
  items: { type: Array, default: () => [] },
  activeCode: { type: String, default: '' },
  capturing: { type: Boolean, default: false },
})
const emit = defineEmits(['pick', 'edit'])
const { t } = useI18n()

const captureIndex = computed(() => props.items.findIndex((i) => i.capture))
const activeIndex = computed(() => props.items.findIndex((i) => i.code === props.activeCode))

const SHELL_ICON = { home: HomeFilled, more: Grid, me: User }
const iconOf = (item) => SHELL_ICON[item.code] || iconComp(item.code)
const labelOf = (item) => (item.titleKey ? t(item.titleKey) : item.title || '')

// 长按底栏任意格 → 进入编辑(点击仍然正常跳转;长按后抬手不再触发一次跳转)
const HOLD_MS = 550
let holdTimer = null
let longPressed = false

const cancelHold = () => {
  if (holdTimer) {
    clearTimeout(holdTimer)
    holdTimer = null
  }
}
const startHold = () => {
  longPressed = false
  cancelHold()
  holdTimer = setTimeout(() => {
    holdTimer = null
    longPressed = true
    emit('edit')
  }, HOLD_MS)
}
const endHold = () => cancelHold()
const onPick = (code) => {
  cancelHold()
  if (longPressed) {
    longPressed = false
    return
  }
  emit('pick', code)
}
</script>

<style scoped>
/* 固定底栏:高度 56px 与 MobileLayout 的 padding-bottom 是同一契约,须同步改。
   这里用 backdrop-filter 是刻意保留的例外——只在固定的窄条上生效、不随长页面滚动。 */
.mobile-tabbar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  height: calc(56px + env(safe-area-inset-bottom, 0px));
  padding-bottom: env(safe-area-inset-bottom, 0px);
  display: flex;
  align-items: stretch;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(20px) saturate(1.1);
  -webkit-backdrop-filter: blur(20px) saturate(1.1);
  border-top: 1px solid rgba(0, 0, 0, 0.06);
  z-index: var(--z-nav);
  /* 中间加号要凸出栏外,不能被裁掉 */
  overflow: visible;
}
/* 顶部 3px 指示条跟着 Tab 滑动;本身不吃指针事件 */
.tab-indicator {
  position: absolute;
  top: 0;
  left: 0;
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
  min-width: 0;
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
  -webkit-touch-callout: none;
  user-select: none;
}
.tab-item .tab-icon {
  font-size: 22px;
  transition:
    transform 0.28s var(--ease-spring),
    color 0.2s ease;
}
.tab-item.active .tab-icon {
  transform: translateY(-2px) scale(1.1);
}
.tab-item:active .tab-icon {
  transform: scale(0.9);
}
.tab-item.active {
  color: var(--color-primary, var(--color-brand));
}
html.dark .tab-item.active {
  color: var(--color-brand);
}
.tab-label {
  max-width: 100%;
  padding: 0 2px;
  line-height: 1.1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 中间「拍摄」:凸出档条半截的圆形加号,视觉重心落在栏外 */
.tab-item.is-capture {
  position: relative;
  justify-content: center;
}
.capture-btn {
  width: 50px;
  height: 50px;
  margin-top: -34px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 26px;
  background: linear-gradient(160deg, var(--color-brand), var(--color-primary, var(--color-brand)));
  box-shadow:
    0 6px 16px rgba(var(--color-brand-rgb, 184, 140, 110), 0.42),
    0 2px 6px rgba(0, 0, 0, 0.12);
  /* 与底栏同色描边,让凸出的圆和栏体之间有干净的缝 */
  border: 3px solid rgba(255, 255, 255, 0.92);
  transition:
    transform 0.24s var(--ease-spring),
    box-shadow 0.24s ease;
}
html.dark .capture-btn {
  border-color: rgba(var(--color-card-rgb), 0.92);
}
.tab-item.is-capture:active .capture-btn {
  transform: scale(0.92);
  box-shadow: 0 3px 10px rgba(var(--color-brand-rgb, 184, 140, 110), 0.34);
}
.capture-btn .el-icon {
  font-size: 26px;
}
.capture-spinner {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: 2px solid rgba(255, 255, 255, 0.45);
  border-top-color: #fff;
  animation: capture-spin 0.8s linear infinite;
}
@keyframes capture-spin {
  to {
    transform: rotate(360deg);
  }
}
@media (prefers-reduced-motion: reduce) {
  .capture-spinner {
    animation-duration: 2.4s;
  }
  .tab-indicator,
  .tab-item .tab-icon,
  .capture-btn {
    transition: none;
  }
}
</style>
