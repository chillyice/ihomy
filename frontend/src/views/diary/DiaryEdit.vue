<!-- 日记编辑页:单张信纸按整页(18行)自适应高度,涂鸦区 sticky 固定右侧 -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('diary.title'), to: '/diary' }, { label: isEdit ? $t('diary.editDiary') : $t('diary.newDiary') }]" />

    <div class="diary-layout">
      <!-- 左侧:纸张 -->
      <div class="paper-stack">
        <div class="paper-sheet">
          <!-- 页眉 -->
          <div class="paper-header">
            <div class="header-row">
              <div class="header-left">
                <div class="date-wrap">
                  <el-date-picker
                    ref="dateRef"
                    v-model="form.date"
                    type="date"
                    value-format="YYYY-MM-DD"
                    :clearable="false"
                    size="small"
                    class="date-picker-line"
                  />
                  <div class="date-underline"></div>
                </div>
                <div class="date-wrap">
                  <el-time-picker
                    v-model="form.time"
                    value-format="HH:mm"
                    :clearable="false"
                    size="small"
                    class="time-picker-line"
                  />
                  <div class="date-underline time-underline"></div>
                </div>
              </div>
              <div class="header-right">
                <button type="button" ref="moodBtnRef" class="mw-display" @click="togglePicker('mood')">
                  <span class="mw-icon">{{ form.mood || '😊' }}</span>
                  <span class="mw-label">{{ moodLabel }}</span>
                </button>
                <button type="button" ref="weatherBtnRef" class="mw-display" @click="togglePicker('weather')">
                  <span class="mw-icon">{{ form.weather || '☀️' }}</span>
                  <span class="mw-label">{{ weatherLabel }}</span>
                </button>
              </div>
            </div>
          </div>

          <!-- 正文:按整页自适应高度,横线背景+分页线+涂鸦画布层 -->
          <div class="paper-body" :style="{ height: bodyHeight + 'px' }">
            <div class="ruling-bg" :style="{ height: bodyHeight + 'px' }"></div>
            <div class="page-break-bg" :style="{ height: bodyHeight + 'px' }"></div>
            <textarea
              ref="textareaRef"
              v-model="content"
              class="paper-textarea"
              :style="{ height: bodyHeight + 'px' }"
              :placeholder="$t('diary.inputContent')"
              spellcheck="false"
              @input="autoResize"
            ></textarea>
            <!-- 涂鸦:荧光画布(multiply 叠文字)→ 墨迹画布 → 实时画布(接收指针) -->
            <canvas ref="markBaseRef" class="doodle-canvas doodle-mark" :style="{ height: bodyHeight + 'px' }"></canvas>
            <canvas ref="inkBaseRef" class="doodle-canvas" :style="{ height: bodyHeight + 'px' }"></canvas>
            <canvas
              ref="liveRef"
              class="doodle-canvas doodle-live"
              :class="{ 'doodle-mark': activeBrush === 'marker' }"
              :style="{ height: bodyHeight + 'px', pointerEvents: activeBrush ? 'auto' : 'none', cursor: drawCursor, touchAction: activeBrush ? 'none' : 'auto' }"
              @pointerdown="onPointerDown"
              @pointermove="onPointerMove"
              @pointerup="onPointerUp"
              @pointercancel="onPointerUp"
            ></canvas>
          </div>

          <!-- 页脚 -->
          <div class="paper-footer">
            <span class="page-num">{{ pageCount }} 页 · {{ wordCount }} 字</span>
            <div class="vis-row">
              <el-radio-group v-model="form.visibility" size="small">
                <el-radio-button :value="0">{{ $t('diary.onlySelf') }}</el-radio-button>
                <el-radio-button :value="3">{{ $t('diary.familyVisible') }}</el-radio-button>
                <el-radio-button :value="4">{{ $t('diary.publicVisible') }}</el-radio-button>
              </el-radio-group>
              <el-button type="primary" :loading="loading" @click="onSave">{{ $t('common.save') }}</el-button>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧:涂鸦笔盘 sticky -->
      <DoodleTray
        v-model:brush="activeBrush"
        v-model:size="brushSize"
        v-model:alpha="brushAlpha"
        v-model:brush-color="brushColor"
        :can-undo="canUndo"
        :can-redo="canRedo"
        @undo="undo"
        @redo="redo"
      />
    </div>

    <!-- 心情/天气选择弹窗 -->
    <teleport to="body">
      <div v-if="pickerOpen" class="picker-overlay" @click="pickerOpen = null">
        <div class="picker-pop" :style="pickerStyle" @click.stop>
          <div class="picker-title">{{ pickerOpen === 'mood' ? $t('diary.mood') : $t('diary.weather') }}</div>
          <div class="picker-grid">
            <button
              v-for="item in (pickerOpen === 'mood' ? MOODS : WEATHERS)"
              :key="item.icon"
              type="button"
              class="picker-cell"
              :class="{ active: (pickerOpen === 'mood' ? form.mood : form.weather) === item.icon }"
              @click="pickItem(item)"
            >
              <span class="cell-icon">{{ item.icon }}</span>
              <span class="cell-label">{{ item.label }}</span>
            </button>
          </div>
          <button v-if="(pickerOpen === 'mood' ? form.mood : form.weather)" type="button" class="picker-clear" @click="clearPick">清除选择</button>
        </div>
      </div>
    </teleport>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { diaryApi } from '@/api'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import Breadcrumb from '@/components/Breadcrumb.vue'
import DoodleTray from './DoodleTray.vue'
import { INK_COLORS, parseDoodle, renderStroke, renderStrokes, erasePixel, eraseObject, setupCanvas, clearCanvas } from '@/utils/doodle'
import { PAGE_H, MOODS, WEATHERS, moodLabel as moodLabelOf, weatherLabel as weatherLabelOf } from '@/utils/diary'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const isEdit = computed(() => !!route.params.id)
const loading = ref(false)
const authorId = ref(null)

const form = reactive({ mood: '', weather: '', date: new Date().toISOString().slice(0, 10), time: new Date().toTimeString().slice(0, 5), visibility: 0 })
const content = ref('')
const pickerOpen = ref(null)
const pickerStyle = ref({})
const moodBtnRef = ref(null)
const weatherBtnRef = ref(null)
const textareaRef = ref(null)
const bodyHeight = ref(PAGE_H)

const moodLabel = computed(() => moodLabelOf(form.mood))
const weatherLabel = computed(() => weatherLabelOf(form.weather))
const wordCount = computed(() => content.value.length)
const pageCount = computed(() => Math.max(1, Math.ceil(bodyHeight.value / PAGE_H)))

const autoResize = () => {
  nextTick(() => {
    const ta = textareaRef.value
    if (!ta) return
    ta.style.height = 'auto'
    const scrollH = ta.scrollHeight
    const pages = Math.ceil(scrollH / PAGE_H)
    const newH = Math.max(1, pages) * PAGE_H
    bodyHeight.value = newH
    ta.style.height = newH + 'px'
  })
}

/* ---------- 信纸涂鸦 ---------- */
const activeBrush = ref(null) // null = 写字模式
const brushSize = ref(4)
const brushAlpha = ref(100) // 百分比 10-100
const brushColor = ref(INK_COLORS[0])
const strokes = ref([])
const inkBaseRef = ref(null)
const markBaseRef = ref(null)
const liveRef = ref(null)
let drawing = false
let curStroke = null
let lastErase = null
let redrawQueued = false
let dragStartStrokes = null // 本次橡皮拖动前的笔画引用(判断是否有变化)

/* 撤销/重做:快照为 strokes 数组引用(笔画提交/擦除均不可变更新,引用即可作快照) */
const history = ref([])
const hIndex = ref(-1)
const canUndo = computed(() => hIndex.value > 0)
const canRedo = computed(() => hIndex.value < history.value.length - 1)
const pushHistory = () => {
  history.value.splice(hIndex.value + 1)
  history.value.push(strokes.value)
  hIndex.value = history.value.length - 1
}
const undo = () => {
  if (!canUndo.value) return
  hIndex.value--
  strokes.value = history.value[hIndex.value]
  redrawBase()
}
const redo = () => {
  if (!canRedo.value) return
  hIndex.value++
  strokes.value = history.value[hIndex.value]
  redrawBase()
}

const isEraser = computed(() => activeBrush.value === 'eraserP' || activeBrush.value === 'eraserO')
const eraserRadius = computed(() => Math.max(10, brushSize.value * 3))
const drawCursor = computed(() => {
  if (!activeBrush.value) return 'text'
  if (!isEraser.value) return 'crosshair'
  const r = eraserRadius.value
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${r * 2}" height="${r * 2}"><circle cx="${r}" cy="${r}" r="${r - 1}" fill="rgba(255,255,255,0.45)" stroke="rgba(58,46,34,0.7)" stroke-dasharray="3 2"/></svg>`
  return `url("data:image/svg+xml,${encodeURIComponent(svg)}") ${r} ${r}, crosshair`
})

const redrawBase = () => {
  const ink = inkBaseRef.value
  const mark = markBaseRef.value
  if (!ink || !mark) return
  const cInk = setupCanvas(ink)
  const cMark = setupCanvas(mark)
  clearCanvas(cInk, ink)
  clearCanvas(cMark, mark)
  renderStrokes(cInk, cMark, strokes.value)
}

const queueRedraw = () => {
  if (redrawQueued) return
  redrawQueued = true
  requestAnimationFrame(() => { redrawQueued = false; redrawBase() })
}

const drawLive = () => {
  const cv = liveRef.value
  if (!cv || !curStroke) return
  const ctx = setupCanvas(cv)
  clearCanvas(ctx, cv)
  renderStroke(ctx, curStroke)
}

const canvasPos = (e) => {
  const rect = liveRef.value.getBoundingClientRect()
  return [e.clientX - rect.left, e.clientY - rect.top]
}

const applyErase = (x, y) => {
  const r = eraserRadius.value
  strokes.value = activeBrush.value === 'eraserO'
    ? eraseObject(strokes.value, x, y, r)
    : erasePixel(strokes.value, x, y, r)
  queueRedraw()
}

const onPointerDown = (e) => {
  if (!activeBrush.value) return
  e.preventDefault()
  drawing = true
  liveRef.value.setPointerCapture(e.pointerId)
  const [x, y] = canvasPos(e)
  if (isEraser.value) {
    dragStartStrokes = strokes.value
    lastErase = [x, y]
    applyErase(x, y)
  } else {
    curStroke = { t: activeBrush.value, c: brushColor.value, w: brushSize.value, a: Math.round(brushAlpha.value) / 100, s: (Math.random() * 1e9) | 0, pts: [[Math.round(x), Math.round(y)]] }
    drawLive()
  }
}

const onPointerMove = (e) => {
  if (!drawing) return
  const [x, y] = canvasPos(e)
  if (isEraser.value) {
    const [lx, ly] = lastErase
    const d = Math.hypot(x - lx, y - ly)
    const steps = Math.max(1, Math.floor(d / Math.max(2, eraserRadius.value * 0.4)))
    for (let i = 1; i <= steps; i++) applyErase(lx + (x - lx) * i / steps, ly + (y - ly) * i / steps)
    lastErase = [x, y]
  } else if (curStroke) {
    const last = curStroke.pts[curStroke.pts.length - 1]
    if (Math.hypot(x - last[0], y - last[1]) >= 2) {
      curStroke.pts.push([Math.round(x), Math.round(y)])
      drawLive()
    }
  }
}

const onPointerUp = () => {
  if (!drawing) return
  drawing = false
  if (curStroke && curStroke.pts.length) {
    strokes.value = [...strokes.value, curStroke]
    pushHistory()
    redrawBase()
  }
  if (isEraser.value && dragStartStrokes !== null && strokes.value !== dragStartStrokes) {
    pushHistory()
  }
  dragStartStrokes = null
  curStroke = null
  const cv = liveRef.value
  if (cv) clearCanvas(setupCanvas(cv), cv)
}

const onKeydown = (e) => {
  if (e.key === 'Escape' && activeBrush.value) { activeBrush.value = null; return }
  // 撤销/重做仅在非文字输入焦点时生效(不劫持 textarea 原生撤销)
  const tag = e.target && e.target.tagName
  if (tag === 'TEXTAREA' || tag === 'INPUT') return
  if ((e.ctrlKey || e.metaKey) && !e.altKey) {
    const k = e.key.toLowerCase()
    if (k === 'z' || k === 'y') {
      e.preventDefault()
      if (k === 'y' || e.shiftKey) redo()
      else undo()
    }
  }
}

watch(bodyHeight, () => nextTick(redrawBase))

const togglePicker = (type) => {
  if (pickerOpen.value === type) { pickerOpen.value = null; return }
  const btn = type === 'mood' ? moodBtnRef.value : weatherBtnRef.value
  if (btn) {
    const rect = btn.getBoundingClientRect()
    const popH = 220
    const top = rect.top + rect.height / 2 - popH / 2
    pickerStyle.value = { position: 'fixed', top: Math.max(8, top) + 'px', left: (rect.right + 8) + 'px', zIndex: 61 }
  }
  pickerOpen.value = type
}
const pickItem = (item) => {
  if (pickerOpen.value === 'mood') form.mood = form.mood === item.icon ? '' : item.icon
  else form.weather = form.weather === item.icon ? '' : item.icon
  pickerOpen.value = null
}
const clearPick = () => {
  if (pickerOpen.value === 'mood') form.mood = ''
  else form.weather = ''
  pickerOpen.value = null
}

const loadCurrentWeather = async () => {
  try {
    const res = await fetch('/api/public/weather')
    if (res.ok) {
      const json = await res.json()
      if (json.code === 0 && json.data?.text) {
        const match = WEATHERS.find(w => json.data.text.includes(w.label) || w.label.includes(json.data.text))
        form.weather = match ? match.icon : ''
      }
    }
  } catch (e) {}
}

const onSave = async () => {
  if (!content.value.trim()) return ElMessage.warning(t('diary.inputContent'))
  loading.value = true
  try {
    const payload = { content: content.value, mood: form.mood, weather: form.weather, date: form.date + ' ' + form.time, visibility: form.visibility, doodle: strokes.value.length ? JSON.stringify({ v: 1, strokes: strokes.value }) : null }
    if (isEdit.value) await diaryApi.update(route.params.id, payload)
    else await diaryApi.create(payload)
    ElMessage.success(t('common.saveSuccess'))
    // 编辑完成回到该作者的日记本翻书视图;新建回到书架
    router.push(isEdit.value && authorId.value != null ? `/diary/book/${authorId.value}` : '/diary')
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  if (isEdit.value) {
    const d = await diaryApi.detail(route.params.id)
    content.value = d.content || ''
    authorId.value = d.authorId
    strokes.value = parseDoodle(d.doodle)
    const raw = String(d.createdAt || '')
    Object.assign(form, {
      mood: d.mood || '', weather: d.weather || '',
      date: raw.slice(0, 10) || form.date,
      time: raw.slice(11, 16) || form.time,
      visibility: d.visibility === 'PRIVATE' ? 0 : d.visibility === 'PUBLIC' ? 4 : 3,
    })
  } else {
    loadCurrentWeather()
  }
  history.value = [strokes.value]
  hIndex.value = 0
  nextTick(() => { autoResize(); redrawBase(); textareaRef.value?.focus() })
  window.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))

watch(content, () => autoResize())
</script>

<style scoped>
.diary-layout { display: flex; gap: 8px; justify-content: center; align-items: flex-start; }

.paper-stack { display: flex; flex-direction: column; flex-shrink: 0; width: calc(28 * 16px + 24px * 2); }

.paper-sheet {
  background: #fffdf8; border-radius: 14px; box-shadow: 0 4px 24px rgba(58,46,34,0.1);
  overflow: hidden; position: relative;
  --ruling-color: rgba(100,130,180,0.15);
  --break-color: rgba(100,130,180,0.35);
}
html.dark .paper-sheet {
  background: #1E2A48; box-shadow: 0 4px 24px rgba(0,0,0,0.3);
  --ruling-color: rgba(232,220,200,0.06);
  --break-color: rgba(232,220,200,0.15);
}

:deep(.el-date-editor.el-input), :deep(.el-time-editor.el-input) { --el-border-color: transparent; width: 100px !important; }
:deep(.el-date-editor.el-input .el-input__wrapper),
:deep(.el-time-editor.el-input .el-input__wrapper) {
  box-shadow: none !important; background: transparent !important; padding: 0 4px;
}
:deep(.el-date-editor.el-input .el-input__inner),
:deep(.el-time-editor.el-input .el-input__inner) { font-size: 13px; }

.paper-header { padding: 8px 24px 0; }
/* header-row: left 和 right 都是纵向排列,底端对齐 */
.header-row { display: flex; justify-content: space-between; align-items: flex-end; min-height: 56px; }
/* header-left: 日期和时间上下排列 */
.header-left { display: flex; flex-direction: column; gap: 2px; padding-bottom: 3px; }
.date-wrap { display: flex; flex-direction: column; align-items: stretch; }
.date-underline { width: 100px; border-bottom: 1px solid var(--ruling-color); margin-top: 1px; }
/* header-right: 心情和天气上下排列 */
.header-right { display: flex; flex-direction: column; gap: 0; align-items: flex-start; }
.mw-display {
  display: flex; align-items: center; gap: 4px;
  border: none; border-bottom: 1px solid var(--ruling-color); border-radius: 0;
  padding: 0 0 3px; background: transparent; cursor: pointer;
  transition: opacity 0.15s; opacity: 0.7;
  line-height: 28px; height: 28px; width: 80px;
}
.mw-display:hover { opacity: 1; }
.mw-icon { font-size: 16px; line-height: 1; }
.mw-label { font-size: 13px; color: var(--color-text); }

/* 正文区:正文从 y=0 起(与查看页 DiaryPage 坐标系一致,涂鸦/分页两页对齐) */
.paper-body { position: relative; padding: 0 24px; overflow: hidden; }
/* 横线背景:每28px一条浅线 */
.ruling-bg {
  position: absolute; top: 0; left: 24px; right: 24px;
  background-image: repeating-linear-gradient(to bottom,
    transparent, transparent 27px,
    var(--ruling-color) 27px, var(--ruling-color) 28px);
  pointer-events: none; z-index: 0;
}
/* 分页线:每18行(504px)一条深色实线 */
.page-break-bg {
  position: absolute; top: 0; left: 12px; right: 12px;
  background-image: repeating-linear-gradient(to bottom,
    transparent, transparent calc(504px - 2px),
    var(--break-color) calc(504px - 2px), var(--break-color) 504px);
  pointer-events: none; z-index: 0;
}
.paper-textarea {
  position: relative; width: 100%;
  border: none; outline: none; resize: none; background: transparent;
  font-family: 'Cascadia Mono', 'Consolas', 'Courier New', monospace;
  font-size: 16px; line-height: 28px; color: var(--color-text);
  z-index: 1; overflow: hidden; word-break: break-all; padding: 0;
}
.paper-textarea::placeholder { color: var(--color-text-secondary); opacity: 0.4; }

.paper-footer {
  display: flex; justify-content: space-between; align-items: center;
  padding: 10px 24px 14px; border-top: 1px solid var(--ruling-color);
}
.page-num { font-size: 12px; color: var(--color-text-secondary); font-variant-numeric: tabular-nums; }
.vis-row { display: flex; align-items: center; gap: 12px; }

/* 涂鸦画布层:荧光笔专用画布 multiply 叠在文字上(深色模式 screen),墨迹/实时画布普通叠加 */
.doodle-canvas {
  position: absolute; top: 0; left: 0; width: 100%;
  pointer-events: none; z-index: 3;
}
.doodle-mark { z-index: 2; mix-blend-mode: multiply; opacity: 0.55; }
html.dark .doodle-mark { mix-blend-mode: screen; }
.doodle-live { z-index: 4; }

/* 心情/天气弹窗:z-index 61(高于导航栏60,低于光影层65+) */
.picker-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; z-index: 61; }
.picker-pop {
  background: rgba(255,253,248,0.85); backdrop-filter: blur(24px) saturate(1.2);
  -webkit-backdrop-filter: blur(24px) saturate(1.2); border-radius: 14px;
  box-shadow: 0 8px 32px rgba(58,46,34,0.12); padding: 14px; width: 240px;
  animation: pickerIn 0.2s ease;
}
html.dark .picker-pop { background: rgba(30,42,72,0.9); box-shadow: 0 8px 32px rgba(0,0,0,0.4); }
@keyframes pickerIn { from { opacity: 0; transform: translateY(-6px); } to { opacity: 1; transform: translateY(0); } }
.picker-title { font-size: 12px; font-weight: 600; color: var(--color-text-secondary); margin-bottom: 10px; text-align: center; }
.picker-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 4px; }
.picker-cell {
  display: flex; flex-direction: column; align-items: center; gap: 3px;
  padding: 8px 4px; border: none; border-radius: 10px; background: transparent;
  cursor: pointer; transition: all 0.15s;
}
.picker-cell:hover { background: rgba(184,140,110,0.08); }
.picker-cell.active { background: rgba(184,140,110,0.12); }
html.dark .picker-cell:hover { background: rgba(212,178,152,0.08); }
html.dark .picker-cell.active { background: rgba(212,178,152,0.12); }
.cell-icon { font-size: 22px; line-height: 1; }
.cell-label { font-size: 11px; color: var(--color-text-secondary); }
.picker-clear {
  width: 100%; margin-top: 8px; border: none; border-radius: 8px; padding: 6px;
  background: rgba(185,96,88,0.06); color: #b04a3a; font-size: 12px; cursor: pointer;
}
html.dark .picker-clear { background: rgba(201,116,116,0.08); color: #c97474; }

@media (max-width: 768px) {
  .diary-layout { flex-direction: column; align-items: center; }
}
</style>
