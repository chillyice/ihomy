<!-- 日记书单页:首页带页眉(日期/时间/心情/天气)+ 正文裁剪窗口(504px/页)+ 页脚页码 -->
<template>
  <div class="sheet" :class="sideClass">
    <div v-if="page.blank" class="sheet-head"></div>
    <div v-else class="sheet-head">
      <template v-if="page.first">
        <div class="head-row">
          <div class="head-left">
            <div class="hl-line">{{ dateStr }}</div>
            <div class="hl-line">{{ timeStr }}</div>
          </div>
          <div class="head-right">
            <div class="mw-line"><span class="mw-icon">{{ page.e.mood || '😊' }}</span>{{ moodLabel(page.e.mood) }}</div>
            <div class="mw-line"><span class="mw-icon">{{ page.e.weather || '☀️' }}</span>{{ weatherLabel(page.e.weather) }}</div>
          </div>
        </div>
        <div v-if="canEdit" class="head-actions">
          <button class="act-btn" :title="$t('common.edit')" @click.stop="$emit('action', 'edit')">
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 20h9"/><path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"/></svg>
          </button>
          <button class="act-btn danger" :title="$t('common.delete')" @click.stop="$emit('action', 'delete')">
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M3 6h18"/><path d="M8 6V4a1 1 0 0 1 1-1h6a1 1 0 0 1 1 1v2"/><path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/></svg>
          </button>
        </div>
      </template>
    </div>

    <div class="sheet-body">
      <div class="ruling-bg"></div>
      <div v-if="!page.blank" class="page-text" :style="{ transform: `translateY(${-(page.p || 0) * PAGE_H}px)` }">{{ page.e.content }}</div>
      <template v-if="!page.blank && strokes.length">
        <canvas ref="markRef" class="doodle-canvas doodle-mark" :style="canvasStyle"></canvas>
        <canvas ref="inkRef" class="doodle-canvas" :style="canvasStyle"></canvas>
      </template>
    </div>

    <div class="sheet-foot">
      <span v-if="!page.blank && page.first && page.e.visibility === 'PRIVATE'" class="vis-tag private">{{ $t('diary.onlySelf') }}</span>
      <span v-else-if="!page.blank && page.first && page.e.visibility === 'PUBLIC'" class="vis-tag public">{{ $t('diary.publicVisible') }}</span>
      <span v-else></span>
      <span class="page-no">· {{ no }} ·</span>
      <span v-if="!page.blank && page.first" class="word-count">{{ page.e.content.length }} {{ $t('diary.words') }}</span>
      <span v-else></span>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch, onMounted, nextTick } from 'vue'
import { PAGE_H, moodLabel, weatherLabel } from '@/utils/diary'
import { parseDoodle, doodleExtentY, renderStrokes, setupCanvas, clearCanvas } from '@/utils/doodle'

const props = defineProps({
  page: { type: Object, required: true },
  no: { type: Number, default: 0 },
  side: { type: String, default: 'single' }, // left | right | single
  canEdit: { type: Boolean, default: false },
})
defineEmits(['action'])

const sideClass = computed(() => `sheet-${props.side}`)

const raw = computed(() => String(props.page.e?.createdAt || ''))
const dateStr = computed(() => raw.value.slice(0, 10))
const timeStr = computed(() => raw.value.slice(11, 16))

/* 涂鸦渲染:与正文同坐标(画布 top:0 + translateY 翻页偏移),由 sheet-body 裁剪窗口裁切 */
const strokes = computed(() => (props.page.blank ? [] : parseDoodle(props.page.e.doodle)))
const doodleH = computed(() => doodleExtentY(strokes.value))
const inkRef = ref(null)
const markRef = ref(null)
const canvasStyle = computed(() => ({
  height: doodleH.value + 'px',
  transform: `translateY(${-(props.page.p || 0) * PAGE_H}px)`,
}))

const renderDoodle = () => {
  nextTick(() => {
    const ink = inkRef.value
    const mark = markRef.value
    if (!ink || !mark || !strokes.value.length) return
    const cInk = setupCanvas(ink)
    const cMark = setupCanvas(mark)
    clearCanvas(cInk, ink)
    clearCanvas(cMark, mark)
    renderStrokes(cInk, cMark, strokes.value)
  })
}
onMounted(renderDoodle)
watch([strokes, () => props.page.p], renderDoodle)
</script>

<!-- 字体/行高/宽度必须与 utils/diary.js 的离屏测量样式一致,否则分页错位 -->
<style scoped>
.sheet {
  width: calc(28 * 16px + 24px * 2);
  flex-shrink: 0;
  background: #fffdf8;
  box-shadow: 0 4px 24px rgba(58,46,34,0.1);
  overflow: hidden;
  position: relative;
  display: flex;
  flex-direction: column;
  --ruling-color: rgba(100,130,180,0.15);
}
html.dark .sheet {
  background: #1E2A48;
  box-shadow: 0 4px 24px rgba(0,0,0,0.3);
  --ruling-color: rgba(232,220,200,0.06);
}
.sheet-left { border-radius: 14px 3px 3px 14px; }
.sheet-right { border-radius: 3px 14px 14px 3px; }
.sheet-single { border-radius: 14px; }

/* 书脊阴影:左页右缘/右页左缘压暗 */
.sheet-left::after, .sheet-right::before {
  content: '';
  position: absolute; top: 0; bottom: 0; width: 42px;
  pointer-events: none; z-index: 2;
}
.sheet-left::after { right: 0; background: linear-gradient(to left, rgba(58,46,34,0.08), transparent); }
.sheet-right::before { left: 0; background: linear-gradient(to right, rgba(58,46,34,0.08), transparent); }
html.dark .sheet-left::after { background: linear-gradient(to left, rgba(0,0,0,0.2), transparent); }
html.dark .sheet-right::before { background: linear-gradient(to right, rgba(0,0,0,0.2), transparent); }

.sheet-head { min-height: 56px; padding: 8px 24px 0; position: relative; }
.head-row { display: flex; justify-content: space-between; align-items: flex-end; min-height: 56px; }
.head-left { display: flex; flex-direction: column; gap: 2px; padding-bottom: 3px; }
.hl-line {
  font-size: 13px; color: var(--color-text); font-variant-numeric: tabular-nums;
  border-bottom: 1px solid var(--ruling-color); line-height: 24px; min-width: 100px;
}
.head-right { display: flex; flex-direction: column; gap: 0; align-items: flex-start; }
.mw-line {
  display: flex; align-items: center; gap: 5px;
  font-size: 13px; color: var(--color-text); line-height: 28px; height: 28px;
  border-bottom: 1px solid var(--ruling-color); min-width: 80px; padding: 0 2px 3px;
}
.mw-icon { font-size: 16px; line-height: 1; }

.head-actions {
  position: absolute; top: 6px; right: 10px;
  display: flex; gap: 4px; opacity: 0; transition: opacity 0.2s;
}
.sheet:hover .head-actions { opacity: 1; }
.act-btn {
  width: 26px; height: 26px; border: none; border-radius: 8px; cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  background: rgba(255,253,248,0.9); color: var(--color-text-secondary);
  box-shadow: 0 1px 4px rgba(58,46,34,0.12);
  transition: background 0.2s, color 0.2s;
}
.act-btn:hover { background: rgba(184,140,110,0.14); color: #b88c6e; }
.act-btn.danger:hover { background: rgba(176,74,58,0.1); color: #b04a3a; }
html.dark .act-btn { background: rgba(30,42,72,0.9); color: rgba(232,220,200,0.7); }
html.dark .act-btn:hover { background: rgba(212,178,152,0.15); color: #d4b298; }
html.dark .act-btn.danger:hover { background: rgba(201,116,116,0.15); color: #c97474; }

.sheet-body { position: relative; height: 504px; overflow: hidden; }
.ruling-bg {
  position: absolute; top: 0; left: 24px; right: 24px; bottom: 0;
  background-image: repeating-linear-gradient(to bottom,
    transparent, transparent 27px,
    var(--ruling-color) 27px, var(--ruling-color) 28px);
  pointer-events: none; z-index: 0;
}
.page-text {
  position: absolute; top: 0; left: 24px; right: 24px;
  font-family: 'Cascadia Mono', 'Consolas', 'Courier New', monospace;
  font-size: 16px; line-height: 28px;
  word-break: break-all; white-space: pre-wrap;
  color: var(--color-text); z-index: 1;
}

/* 涂鸦画布:荧光笔 multiply 叠文字(深色模式 screen),墨迹普通叠加;随正文一起被窗口裁剪 */
.doodle-canvas {
  position: absolute; top: 0; left: 0; width: 100%;
  pointer-events: none; z-index: 3;
}
.doodle-mark { z-index: 2; mix-blend-mode: multiply; opacity: 0.55; }
html.dark .doodle-mark { mix-blend-mode: screen; }

.sheet-foot {
  display: flex; justify-content: space-between; align-items: center;
  padding: 6px 16px 8px; border-top: 1px solid var(--ruling-color);
  min-height: 30px;
}
.page-no { font-size: 12px; color: var(--color-text-secondary); opacity: 0.6; font-variant-numeric: tabular-nums; }
.word-count { font-size: 11px; color: var(--color-text-secondary); opacity: 0.5; }
.vis-tag {
  padding: 1px 8px; border-radius: 8px; font-size: 11px; line-height: 1.5;
  border: 1px solid transparent;
}
.vis-tag.private { background: rgba(58,46,34,0.06); border-color: rgba(58,46,34,0.1); color: var(--color-text-secondary); }
.vis-tag.public { background: rgba(107,155,107,0.1); border-color: rgba(107,155,107,0.15); color: #6b9b6b; }
html.dark .vis-tag.private { background: rgba(255,255,255,0.06); color: rgba(232,220,200,0.5); }
html.dark .vis-tag.public { background: rgba(125,186,125,0.12); color: #7dba7d; }
</style>
