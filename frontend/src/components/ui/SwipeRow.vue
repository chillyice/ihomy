<!-- 列表行手势外壳:左滑露右侧操作、右滑露左侧操作,长按弹上下文菜单。
     位移直接写内容层 style(useRowGestures),松手才提交展开状态;不 preventDefault,纵向滚动照常。
     行尾「更多」按钮通过 slot prop openMenu 打开同一份菜单(D4:手势必须有等价按钮)。 -->
<template>
  <div class="swipe-row" :class="{ 'is-open': open }" @contextmenu="onRowContextmenu">
    <div class="swipe-actions swipe-actions-left" :style="{ width: actionWidth + 'px' }">
      <button
        v-for="a in leftActions"
        :key="a.key"
        type="button"
        class="swipe-action"
        :class="a.type"
        :aria-label="a.label"
        @click.stop="run(a)"
      >
        {{ a.label }}
      </button>
    </div>
    <div class="swipe-actions swipe-actions-right" :style="{ width: actionWidth + 'px' }">
      <button
        v-for="a in rightActions"
        :key="a.key"
        type="button"
        class="swipe-action"
        :class="a.type"
        :aria-label="a.label"
        @click.stop="run(a)"
      >
        {{ a.label }}
      </button>
    </div>

    <div ref="contentEl" class="swipe-content" @pointerdown="onPointerDown" @click.capture="onClickCapture">
      <slot :open-menu="openMenu" :close="close" />
    </div>

    <LongPressMenu v-model:visible="menu.visible" :x="menu.x" :y="menu.y" :items="menuItems" @select="run" />
  </div>
</template>

<script setup>
import LongPressMenu from './LongPressMenu.vue'
import { useRowGestures } from '@/composables/useRowGestures'
import { useDevice } from '@/composables/useDevice'

const props = defineProps({
  leftActions: { type: Array, default: () => [] },
  rightActions: { type: Array, default: () => [] },
  menuItems: { type: Array, default: () => [] },
  disabled: { type: Boolean, default: false },
})
const emit = defineEmits(['action'])

const { contentEl, open, menu, actionWidth, close, openMenuAt, openContextMenu, onPointerDown, onClickCapture } = useRowGestures({
  hasLeft: () => props.leftActions.length > 0,
  hasRight: () => props.rightActions.length > 0,
  hasMenu: () => props.menuItems.length > 0,
  disabled: () => props.disabled,
})
const { hasPointer } = useDevice()

// 右键与长按共用同一份菜单;触屏放行,避免 Android 长按补发的 contextmenu 再弹一层
const onRowContextmenu = (e) => {
  if (!hasPointer.value) return
  openContextMenu(e)
}

/** 行尾「更多」按钮:以按钮位置为锚点打开菜单(长按则跟随手指) */
const openMenu = (e) => {
  const rect = e?.currentTarget?.getBoundingClientRect?.()
  const x = rect ? rect.left : (e?.clientX ?? 0)
  const y = rect ? rect.bottom + 6 : (e?.clientY ?? 0)
  openMenuAt(x, y)
}

const run = (item) => {
  close()
  emit('action', item)
}
</script>

<style scoped>
.swipe-row {
  position: relative;
  overflow: hidden;
  border-radius: inherit;
}
.swipe-actions {
  position: absolute;
  top: 0;
  bottom: 0;
  display: flex;
  align-items: stretch;
  pointer-events: none;
}
.swipe-actions-left {
  left: 0;
}
.swipe-actions-right {
  right: 0;
}
.swipe-row.is-open .swipe-actions {
  pointer-events: auto;
}
.swipe-action {
  flex: 1;
  min-width: 0;
  border: none;
  padding: 0 10px;
  font-size: 13px;
  font-weight: 600;
  color: #fff;
  background: var(--color-brand);
  cursor: pointer;
}
.swipe-action + .swipe-action {
  border-left: 1px solid rgba(255, 255, 255, 0.18);
}
.swipe-action.danger {
  background: var(--color-danger, #b96058);
}
.swipe-action.success {
  background: var(--color-income, #67a35f);
}
.swipe-content {
  position: relative;
  z-index: 1;
  background: var(--color-card);
  touch-action: pan-y;
  will-change: transform;
}
/* 悬停反馈只在精细指针下出现:触屏(Android 长按/点击后)不残留 :hover */
@media (hover: hover) and (pointer: fine) {
  .swipe-content::after {
    content: '';
    position: absolute;
    inset: 0;
    z-index: 2;
    pointer-events: none;
    background: rgba(var(--color-brand-rgb), 0.04);
    opacity: 0;
    transition: opacity var(--dur-fast) ease;
  }
  .swipe-content:hover::after {
    opacity: 1;
  }
}
</style>
