<!--
  平板 / 窄屏电脑的「上次在…」浮卡:贴右边缘、可拖走,收起后变成 40×40 小页签。
  只在 lastDoingMode()==='float' 时由 App.vue 挂载;位置/收起状态都持久化,
  LightSettings 的「恢复面板布局」会一并清掉。
-->
<template>
  <div
    v-if="hasItems"
    ref="box"
    class="last-doing-float"
    :class="{ 'is-collapsed': collapsed }"
    :style="boxStyle"
    @pointerdown="onWrapperDown"
  >
    <button
      v-if="collapsed"
      type="button"
      class="ld-tab"
      :title="$t('lastDoing.expand')"
      :aria-label="$t('lastDoing.expand')"
      @pointerdown="onDragStart"
      @pointerup="onTabUp"
      @click="onTabClick"
    >
      <el-icon><Reading /></el-icon>
    </button>
    <LastDoingCard v-else variant="float" @collapse="collapse" />
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { Reading } from '@element-plus/icons-vue'
import LastDoingCard from './LastDoingCard.vue'
import { useDragResize } from '@/utils/useDragResize'
import { useRecentActivities } from '@/composables/useRecentActivities'

const COLLAPSE_KEY = 'ihomy:last-doing:float:collapsed'
const TAB_SIZE = 40

const readCollapsed = () => {
  try {
    return localStorage.getItem(COLLAPSE_KEY) === '1'
  } catch (e) {
    return false
  }
}

const collapsed = ref(readCollapsed())
const viewportHeight = typeof window === 'undefined' ? 800 : window.innerHeight || 800

const box = ref(null)
const { pos, size, zIndex, dragging, onDragStart, clampToViewport } = useDragResize({
  x: 10,
  y: Math.max(12, Math.round(viewportHeight * 0.3)),
  w: TAB_SIZE,
  h: TAB_SIZE,
  storageKey: 'ihomy:last-doing:float:v1',
  anchorRight: true,
  minY: 8,
})

const boxStyle = computed(() => ({
  right: `${pos.value.x}px`,
  top: `${pos.value.y}px`,
  zIndex: zIndex.value,
}))

// 展开态从空白处/标题拖动;行与按钮由 useDragResize 自己跳过
const onWrapperDown = (e) => {
  if (!collapsed.value) onDragStart(e)
}

// 没有可展示内容时整块不占位
const recent = useRecentActivities()
const hasItems = computed(() => recent.value.length > 0)

// 卡片按内容排版，拖动边界跟随实际外框，覆盖旧版本缓存的固定高度。
watch(
  box,
  (el, _, onCleanup) => {
    if (!el) return
    const measure = () => {
      size.value = { w: el.offsetWidth, h: el.offsetHeight }
      clampToViewport()
    }
    measure()
    if (typeof ResizeObserver === 'undefined') {
      onCleanup(watch([collapsed, recent], measure, { flush: 'post' }))
      return
    }
    const observer = new ResizeObserver(measure)
    observer.observe(el)
    onCleanup(() => observer.disconnect())
  },
  { flush: 'post' },
)

const persistCollapsed = (on) => {
  collapsed.value = on
  try {
    localStorage.setItem(COLLAPSE_KEY, on ? '1' : '0')
  } catch (e) {
    /* 隐私模式写不进去:本次会话内仍然生效 */
  }
}

const collapse = () => persistCollapsed(true)

// 拖动产生的 click 不当作展开
let ignoreTabClick = false
const onTabUp = (e) => {
  if (e.isPrimary && e.button === 0 && dragging.value) ignoreTabClick = true
}
const onTabClick = (e) => {
  if (e.detail === 0 || !ignoreTabClick) persistCollapsed(false)
  ignoreTabClick = false
}
</script>

<style scoped>
.last-doing-float {
  position: fixed;
  box-sizing: border-box;
  width: 168px;
  max-width: 100vw;
  touch-action: none;
}
.last-doing-float.is-collapsed {
  width: 40px;
}
.ld-tab {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  padding: 0;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  background: var(--color-card);
  color: var(--color-brand);
  box-shadow: var(--shadow);
  cursor: grab;
}
.ld-tab:active {
  cursor: grabbing;
}
.ld-tab .el-icon {
  font-size: 18px;
}
</style>
