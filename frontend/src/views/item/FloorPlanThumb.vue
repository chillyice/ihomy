<template>
  <div class="fp-thumb" :class="{ 'is-collapsed': thumbCollapsed }" :style="thumbStyle">
    <div class="fp-thumb-drag" @pointerdown="onThumbDragStart">
      <svg v-if="!thumbCollapsed" viewBox="0 0 16 8" class="fp-thumb-grip">
        <circle cx="4" cy="4" r="1.2" />
        <circle cx="8" cy="4" r="1.2" />
        <circle cx="12" cy="4" r="1.2" />
      </svg>
      <svg v-else viewBox="0 0 16 8" class="fp-thumb-grip">
        <circle cx="4" cy="4" r="1.2" />
        <circle cx="8" cy="4" r="1.2" />
        <circle cx="12" cy="4" r="1.2" />
      </svg>
    </div>
    <template v-if="!thumbCollapsed">
      <svg
        class="fp-thumb-map"
        :viewBox="thumbViewBox"
        preserveAspectRatio="xMidYMid meet"
        @pointerdown="onThumbMapDown"
        @pointermove="onThumbMapMove"
        @pointerup="onThumbMapEnd"
        @pointerleave="onThumbMapEnd"
      >
        <polygon v-for="r in rooms" :key="'tr' + r.id" :points="pts(r.poly)" class="fp-thumb-room" />
        <rect v-for="f in furnitures" :key="'tf' + f.id" :x="f.x" :y="f.y" :width="f.w" :height="f.h" class="fp-thumb-furn" />
        <circle v-for="it in thumbHighlightItems" :key="'ti' + it.id" :cx="it.ax" :cy="it.ay" r="3" class="fp-thumb-item" />
        <rect
          v-if="thumbViewport"
          :x="thumbViewport.x"
          :y="thumbViewport.y"
          :width="thumbViewport.w"
          :height="thumbViewport.h"
          class="fp-thumb-viewport"
        />
        <rect
          v-if="thumbPreview"
          :x="thumbPreview.x"
          :y="thumbPreview.y"
          :width="thumbPreview.w"
          :height="thumbPreview.h"
          class="fp-thumb-preview"
        />
      </svg>
    </template>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { pts } from './floorPlanCanvasUtil'

// 缩略图(迷你地图):右上角悬浮,可折叠成横条;拖拽手柄单击切换折叠/展开,拖动移动位置。
// 自 FloorPlanCanvas.vue 逐字抽取,行为保持不变;平移画板经 pan 事件回传父组件(改 view.tx/ty)。
const props = defineProps({
  rooms: { type: Array, default: () => [] },
  furnitures: { type: Array, default: () => [] },
  items: { type: Array, default: () => [] },
  highlightItemIds: { type: Array, default: () => [] },
  wrapWidth: { type: Number, default: 0 },
  wrapHeight: { type: Number, default: 0 },
  viewTx: { type: Number, default: 0 },
  viewTy: { type: Number, default: 0 },
  viewK: { type: Number, default: 1 },
})
const emit = defineEmits(['pan'])

// ---- 缩略图(迷你地图):右上角悬浮,可折叠成横条、可拖动 ----
const THUMB_W = 168
const THUMB_H = 112
const thumbCollapsed = ref(false)
const thumbPos = ref({ x: 0, y: 0 })
const thumbMoved = ref(false)

const thumbBounds = computed(() => {
  let minX = Infinity
  let minY = Infinity
  let maxX = -Infinity
  let maxY = -Infinity
  props.rooms.forEach((r) =>
    r.poly.forEach((p) => {
      if (p.x < minX) minX = p.x
      if (p.x > maxX) maxX = p.x
      if (p.y < minY) minY = p.y
      if (p.y > maxY) maxY = p.y
    }),
  )
  props.furnitures.forEach((f) => {
    if (f.x < minX) minX = f.x
    if (f.x + f.w > maxX) maxX = f.x + f.w
    if (f.y < minY) minY = f.y
    if (f.y + f.h > maxY) maxY = f.y + f.h
  })
  thumbHighlightItems.value.forEach((it) => {
    if (it.ax < minX) minX = it.ax
    if (it.ax > maxX) maxX = it.ax
    if (it.ay < minY) minY = it.ay
    if (it.ay > maxY) maxY = it.ay
  })
  if (!isFinite(minX)) return null
  return { minX, minY, w: Math.max(1, maxX - minX), h: Math.max(1, maxY - minY) }
})
const thumbViewBox = computed(() => {
  const b = thumbBounds.value
  return b ? `${b.minX} ${b.minY} ${b.w} ${b.h}` : '0 0 1 1'
})
// 缩略图中高亮物品(搜索结果)的绝对坐标
const thumbHighlightItems = computed(() => {
  const hit = new Set(props.highlightItemIds)
  return props.items.filter((it) => hit.has(it.id) && it.ax !== 0 && it.ay !== 0)
})
// 当前可视区域(世界坐标),在缩略图上以红框标示
const thumbViewport = computed(() => {
  const w = props.wrapWidth
  const h = props.wrapHeight
  if (!w || !h) return null
  const k = props.viewK || 1
  return { x: -props.viewTx / k, y: -props.viewTy / k, w: w / k, h: h / k }
})
const thumbStyle = computed(() => ({ left: `${thumbPos.value.x}px`, top: `${thumbPos.value.y}px` }))
const clampThumbPos = (x, y) => {
  const w = props.wrapWidth || THUMB_W
  const h = props.wrapHeight || THUMB_H + 18
  const tw = thumbCollapsed.value ? 80 : THUMB_W
  const th = thumbCollapsed.value ? 24 : THUMB_H + 18
  return {
    x: Math.max(0, Math.min(w - tw, x)),
    y: Math.max(0, Math.min(h - th, y)),
  }
}
const resetThumbPos = () => {
  const w = props.wrapWidth
  if (w) thumbPos.value = { x: Math.max(0, w - THUMB_W - 12), y: 12 }
}
// 画布尺寸变化时刷新默认位置(用户拖动过则保留其位置)
watch(
  () => [props.wrapWidth, props.wrapHeight],
  () => {
    if (!thumbMoved.value) resetThumbPos()
  },
)
// 缩略图内部:pointerdown/pointermove 实时预览红框+平移画板;pointerup 确认
const thumbPreview = ref(null)
let thumbNavDragging = false
const thumbNavWorld = (e) => {
  const svg = e.currentTarget
  const rect = svg.getBoundingClientRect()
  const sx = (e.clientX - rect.left) / rect.width
  const sy = (e.clientY - rect.top) / rect.height
  const b = thumbBounds.value
  if (!b) return null
  return { x: b.minX + sx * b.w, y: b.minY + sy * b.h }
}
const onThumbMapDown = (e) => {
  if (e.button !== 0) return
  thumbNavDragging = true
  const p = thumbNavWorld(e)
  if (p) {
    const cw = props.wrapWidth || 800
    const ch = props.wrapHeight || 500
    const k = props.viewK || 1
    thumbPreview.value = { x: p.x - cw / k / 2, y: p.y - ch / k / 2, w: cw / k, h: ch / k }
    emit('pan', { tx: cw / 2 - p.x * k, ty: ch / 2 - p.y * k })
  }
}
const onThumbMapMove = (e) => {
  if (!thumbNavDragging) return
  const p = thumbNavWorld(e)
  if (p) {
    const cw = props.wrapWidth || 800
    const ch = props.wrapHeight || 500
    const k = props.viewK || 1
    thumbPreview.value = { x: p.x - cw / k / 2, y: p.y - ch / k / 2, w: cw / k, h: ch / k }
    emit('pan', { tx: cw / 2 - p.x * k, ty: ch / 2 - p.y * k })
  }
}
const onThumbMapEnd = () => {
  thumbNavDragging = false
  thumbPreview.value = null
}
let thumbDrag = null
let thumbDragMoved = false
const onThumbDragStart = (e) => {
  if (e.button !== 0) return
  e.preventDefault()
  thumbDragMoved = false
  thumbDrag = { startX: e.clientX, startY: e.clientY, origX: thumbPos.value.x, origY: thumbPos.value.y }
  document.addEventListener('pointermove', onThumbDragMove)
  document.addEventListener('pointerup', onThumbDragEnd)
}
const onThumbDragMove = (e) => {
  if (!thumbDrag) return
  thumbDragMoved = true
  thumbMoved.value = true
  const dx = e.clientX - thumbDrag.startX
  const dy = e.clientY - thumbDrag.startY
  thumbPos.value = clampThumbPos(thumbDrag.origX + dx, thumbDrag.origY + dy)
}
const onThumbDragEnd = () => {
  const wasDrag = thumbDragMoved
  thumbDrag = null
  document.removeEventListener('pointermove', onThumbDragMove)
  document.removeEventListener('pointerup', onThumbDragEnd)
  if (!wasDrag) thumbCollapsed.value = !thumbCollapsed.value
}
onBeforeUnmount(onThumbDragEnd)
</script>

<style scoped>
/* 缩略图(迷你地图):右上角悬浮,可折叠成横条 */
.fp-thumb {
  position: absolute;
  z-index: 6;
  width: 168px;
  background: rgba(255, 253, 248, 0.96);
  border: 1px solid rgba(184, 140, 110, 0.35);
  border-radius: 10px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.12);
  user-select: none;
  touch-action: none;
}
.fp-thumb.is-collapsed {
  width: 168px;
  padding: 0;
}
.fp-thumb-drag {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 18px;
  cursor: grab;
  border-radius: 10px 10px 0 0;
  background: rgba(184, 140, 110, 0.12);
}
.fp-thumb.is-collapsed .fp-thumb-drag {
  height: 24px;
  border-radius: 10px;
  cursor: pointer;
  background: rgba(184, 140, 110, 0.18);
}
.fp-thumb-drag:active {
  cursor: grabbing;
}
.fp-thumb-grip {
  width: 16px;
  height: 8px;
  fill: #a89a8a;
}
.fp-thumb-map {
  width: 100%;
  display: block;
  cursor: pointer;
}
.fp-thumb-room {
  fill: rgba(184, 140, 110, 0.15);
  stroke: rgba(139, 115, 85, 0.5);
  stroke-width: 1;
}
.fp-thumb-furn {
  fill: rgba(176, 74, 58, 0.2);
  stroke: rgba(176, 74, 58, 0.5);
  stroke-width: 0.8;
}
.fp-thumb-item {
  fill: #e0a030;
  stroke: #fff;
  stroke-width: 1;
}
.fp-thumb-viewport {
  fill: rgba(176, 74, 58, 0.08);
  stroke: #b04a3a;
  stroke-width: 2;
}
.fp-thumb-preview {
  fill: rgba(176, 74, 58, 0.12);
  stroke: #b04a3a;
  stroke-width: 1.5;
  stroke-dasharray: 4 3;
}
.fp-thumb-chevron {
  width: 14px;
  height: 14px;
}
</style>
