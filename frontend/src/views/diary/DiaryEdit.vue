<!-- 日记编辑页:单张信纸按整页(18行)自适应高度,涂鸦区 sticky 固定右侧 -->
<template>
  <div class="page diary-editor">
    <Breadcrumb :items="[{ label: $t('diary.title'), to: '/diary' }, { label: isEdit ? $t('diary.editDiary') : $t('diary.newDiary') }]" />

    <div ref="layoutRef" class="diary-layout" :class="{ compact: compactTray }">
      <DoodleTray
        v-model:brush="activeBrush"
        v-model:size="brushSize"
        v-model:alpha="brushAlpha"
        v-model:brush-color="brushColor"
        :compact="compactTray"
        :can-undo="canUndo"
        :can-redo="canRedo"
        @undo="undo"
        @redo="redo"
      />
      <!-- 左侧:纸张(移动端等比缩放适配屏幕宽度) -->
      <div class="paper-column">
        <div class="paper-scaler" :class="{ scaled: paperScale < 1 }" :style="scalerStyle">
          <div ref="paperRef" class="paper-sheet diary-paper" :style="paperTransform">
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
                    <el-time-picker v-model="form.time" value-format="HH:mm" :clearable="false" size="small" class="time-picker-line" />
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
                :style="{
                  height: bodyHeight + 'px',
                  pointerEvents: activeBrush ? 'auto' : 'none',
                  cursor: drawCursor,
                  touchAction: activeBrush ? 'none' : 'auto',
                }"
                @pointerdown="onPointerDown"
                @pointermove="onPointerMove"
                @pointerup="onPointerUp"
                @pointercancel="onPointerUp"
              ></canvas>
            </div>
          </div>
        </div>
        <!-- 页脚控件保留实际触控尺寸，只有纸张参与缩放。 -->
        <div class="paper-footer">
          <span class="page-num">{{ pageCount }} {{ $t('diary.pagesUnit') }} · {{ wordCount }} {{ $t('diary.words') }}</span>
          <div class="vis-row">
            <VisibilityPicker v-model="form.visibility" :options="visibilityOptions" button size="small" />
            <el-button v-if="form.visibility === 'CIRCLE'" size="small" @click="openShare">{{ $t('audience.label') }}</el-button>
            <el-button type="primary" :loading="loading" @click="onSave">{{ $t('common.save') }}</el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 心情/天气选择弹窗 -->
    <teleport to="body">
      <div v-if="pickerOpen" class="picker-overlay" @click="pickerOpen = null">
        <div class="picker-pop" :class="{ centered: pickerCentered }" :style="pickerStyle" @click.stop>
          <div class="picker-title">{{ pickerOpen === 'mood' ? $t('diary.mood') : $t('diary.weather') }}</div>
          <div class="picker-grid">
            <button
              v-for="item in pickerOpen === 'mood' ? MOODS : WEATHERS"
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
          <button v-if="pickerOpen === 'mood' ? form.mood : form.weather" type="button" class="picker-clear" @click="clearPick">
            {{ $t('diary.clearPick') }}
          </button>
        </div>
      </div>
    </teleport>

    <!-- 分享范围:仅非私密日记可分享;不选圈 = 只给自家人 -->
    <el-dialog v-model="shareDialog" append-to-body :title="t('audience.label')" width="420px">
      <div v-loading="shareLoading">
        <AudiencePicker v-if="audience.canShare" v-model="audience.selected" :families="audience.families" />
        <p v-else class="audience-tip">{{ t('audience.canShareFalse') }}</p>
      </div>
      <template #footer>
        <el-button @click="shareDialog = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :disabled="!audience.canShare" @click="confirmShare">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { diaryApi, audienceApi, publicApi } from '@/api'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import Breadcrumb from '@/components/Breadcrumb.vue'
import VisibilityPicker from '@/components/common/VisibilityPicker.vue'
import AudiencePicker from '@/components/common/AudiencePicker.vue'
import { useAudience } from '@/composables/useAudience'
import { visibilityOptions as buildVisibilityOptions, needsAudience } from '@/utils/visibility'
import DoodleTray from './DoodleTray.vue'
import { useDiaryWidth } from './useDiaryWidth'
import './diaryPaper.css'
import { INK_COLORS, parseDoodle, renderStroke, renderStrokes, erasePixel, eraseObject, setupCanvas, clearCanvas } from '@/utils/doodle'
import { PAGE_H, PAPER_W, MOODS, WEATHERS, moodLabel as moodLabelOf, weatherLabel as weatherLabelOf } from '@/utils/diary'
import { recordDoing } from '@/utils/lastDoing'
import { useDevice } from '@/composables/useDevice'
import { useUserStore } from '@/stores/user'
import { useThemeStore } from '@/stores/theme'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const { isMobile } = useDevice()
const isEdit = computed(() => !!route.params.id)
const loading = ref(false)
const authorId = ref(null)

const form = reactive({
  mood: '',
  weather: '',
  date: new Date().toISOString().slice(0, 10),
  time: new Date().toTimeString().slice(0, 5),
  visibility: 'PRIVATE',
})
// 分享范围:仅非私密日记可分享;打开面板才载入,没打开就不动已有设置
const shareDialog = ref(false)
const shareLoading = ref(false)
const { state: audience, load: loadAudience } = useAudience('diary')
const openShare = async () => {
  shareDialog.value = true
  shareLoading.value = true
  try {
    await loadAudience(route.params.id || null, 'diary', { applyDefaults: !route.params.id })
  } finally {
    shareLoading.value = false
  }
}
const confirmShare = async () => {
  shareDialog.value = false
  if (!isEdit.value || !route.params.id) return
  try {
    const res = await audienceApi.save('diary', route.params.id, [...audience.selected])
    if (audience.selected.length && res?.targetCount === 0) ElMessage.info(t('audience.noTarget'))
    else ElMessage.success(t('common.saved'))
  } catch {
    /* 请求层已提示 */
  }
}
// 可见范围四档(共用选择器与文案,顺序:公开 > 亲友圈 > 家庭 > 私密)
const visibilityOptions = computed(() => buildVisibilityOptions(t))
const content = ref('')
const pickerOpen = ref(null)
const pickerStyle = ref({})
const pickerCentered = ref(false)
const moodBtnRef = ref(null)
const weatherBtnRef = ref(null)
const textareaRef = ref(null)
const bodyHeight = ref(PAGE_H)

/* ---------- 移动端信纸等比缩放:纸张固定 496px,小屏按视口宽度缩放,保持涂鸦坐标系与查看页一致 ---------- */
const paperRef = ref(null)
const layoutRef = ref(null)
const layoutWidth = useDiaryWidth(layoutRef)
// 496px 信纸 + 272px 笔盘 + 16px 间距。
const compactTray = computed(() => isMobile.value || layoutWidth.value < PAPER_W + 272 + 16)
const paperScale = computed(() => Math.min(1, (layoutWidth.value || PAPER_W) / PAPER_W))
const scalerH = ref(PAGE_H)
const paperTransform = computed(() =>
  paperScale.value < 1 ? { transform: `scale(${paperScale.value})`, transformOrigin: 'top left' } : {},
)
const scalerStyle = computed(() => (paperScale.value < 1 ? { width: `${PAPER_W * paperScale.value}px`, height: `${scalerH.value}px` } : {}))
const syncHeight = () => {
  if (paperRef.value) scalerH.value = Math.ceil(paperRef.value.offsetHeight * paperScale.value)
}
let scaleObserver = null

const moodLabel = computed(() => moodLabelOf(form.mood))
const weatherLabel = computed(() => weatherLabelOf(form.weather))
const wordCount = computed(() => content.value.length)
const pageCount = computed(() => Math.max(1, Math.ceil(bodyHeight.value / PAGE_H)))

/* ---------- 草稿持久化(sessionStorage):iPad 旋转会切换移动/桌面布局导致本组件重挂载、iOS PWA 旋转会整页 reload,未保存内容全丢;同会话内恢复 ---------- */
const draftKey = computed(() => `ihomy:diary-draft:${route.params.id || 'new'}:${userStore.userInfo?.id ?? 0}`)
let draftReady = false // 初始装载(服务端数据/草稿回填)完成前不写草稿,避免只打开不编辑也生成草稿
let draftDirty = false
let draftTimer = null
const saveDraft = () => {
  try {
    sessionStorage.setItem(
      draftKey.value,
      JSON.stringify({ c: content.value, m: form.mood, w: form.weather, d: form.date, t: form.time, v: form.visibility, s: strokes.value }),
    )
  } catch (e) {} // 隐私模式/存储满:静默降级,不阻断书写
}
const queueSaveDraft = () => {
  clearTimeout(draftTimer)
  draftTimer = setTimeout(saveDraft, 400)
}
const clearDraft = () => {
  clearTimeout(draftTimer)
  draftDirty = false
  try {
    sessionStorage.removeItem(draftKey.value)
  } catch (e) {}
}
const readDraft = () => {
  try {
    const raw = sessionStorage.getItem(draftKey.value)
    if (!raw) return null
    const o = JSON.parse(raw)
    return o && typeof o === 'object' ? o : null
  } catch {
    return null
  }
}
const applyDraft = (o) => {
  if (typeof o.c === 'string' && o.c) content.value = o.c
  if (typeof o.m === 'string') form.mood = o.m
  if (typeof o.w === 'string') form.weather = o.w
  if (typeof o.d === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(o.d)) form.date = o.d
  if (typeof o.t === 'string' && /^\d{2}:\d{2}$/.test(o.t)) form.time = o.t
  if (['PRIVATE', 'FAMILY', 'CIRCLE', 'PUBLIC'].includes(o.v)) form.visibility = o.v
  if (Array.isArray(o.s)) strokes.value = o.s.filter((s) => s && typeof s.t === 'string' && Array.isArray(s.pts) && s.pts.length)
}
const flushDraftIfDirty = () => {
  if (draftDirty) saveDraft()
} // pagehide/切后台立即落盘(旋转 reload 前的最后时机)

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
const themeStore = useThemeStore()
const pickedColor = ref(null)
const brushColor = computed({
  get: () => pickedColor.value ?? (themeStore.isDusk ? '#F5F2EA' : INK_COLORS[0]),
  set: (color) => {
    pickedColor.value = color
  },
})
const strokes = ref([])
const inkBaseRef = ref(null)
const markBaseRef = ref(null)
const liveRef = ref(null)
let drawing = false
let curStroke = null
let lastErase = null
let redrawQueued = false
let dragStartStrokes = null // 本次橡皮拖动前的笔画引用(判断是否有变化)
let drawingPointerId = null // 当前笔画的指针:多指/手掌的 pointermove 不再串进同一笔画
let lastPenAt = 0 // 手写笔最近一次活动(按下/移动/悬停)时间戳,用于手掌抑制
const PEN_REJECT_MS = 1500

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
  requestAnimationFrame(() => {
    redrawQueued = false
    redrawBase()
  })
}

const drawLive = () => {
  const cv = liveRef.value
  if (!cv || !curStroke) return
  const ctx = setupCanvas(cv)
  clearCanvas(ctx, cv)
  renderStroke(ctx, curStroke)
}

const canvasPos = (e) => {
  const cv = liveRef.value
  const rect = cv.getBoundingClientRect()
  // CSS transform 缩放后 rect 为视觉尺寸,须换算回画布布局坐标(clientWidth 为未缩放布局尺寸)
  const sx = (cv.clientWidth || 1) / (rect.width || 1)
  const sy = (cv.clientHeight || 1) / (rect.height || 1)
  return [(e.clientX - rect.left) * sx, (e.clientY - rect.top) * sy]
}

const applyErase = (x, y) => {
  const r = eraserRadius.value
  strokes.value = activeBrush.value === 'eraserO' ? eraseObject(strokes.value, x, y, r) : erasePixel(strokes.value, x, y, r)
  queueRedraw()
}

const onPointerDown = (e) => {
  if (!activeBrush.value) return
  if (drawing) return // 单一笔画:并发的第二指针(多指/手掌)一律忽略
  if (e.pointerType === 'pen') {
    lastPenAt = Date.now()
  } else if (e.pointerType === 'touch' && Date.now() - lastPenAt < PEN_REJECT_MS) {
    return // 手写笔刚用过(含悬停):触摸大概率是手掌,拒绝起笔(平板 palm rejection)
  }
  e.preventDefault()
  drawing = true
  drawingPointerId = e.pointerId
  liveRef.value.setPointerCapture(e.pointerId)
  const [x, y] = canvasPos(e)
  if (isEraser.value) {
    dragStartStrokes = strokes.value
    lastErase = [x, y]
    applyErase(x, y)
  } else {
    curStroke = {
      t: activeBrush.value,
      c: brushColor.value,
      w: brushSize.value,
      a: Math.round(brushAlpha.value) / 100,
      s: (Math.random() * 1e9) | 0,
      pts: [[Math.round(x), Math.round(y)]],
    }
    drawLive()
  }
}

const onPointerMove = (e) => {
  // 笔悬停(hover,无按键)也刷新时间戳:笔在板面上方时手掌持续被抑制
  if (e.pointerType === 'pen') lastPenAt = Date.now()
  if (!drawing || e.pointerId !== drawingPointerId) return
  const [x, y] = canvasPos(e)
  if (isEraser.value) {
    const [lx, ly] = lastErase
    const d = Math.hypot(x - lx, y - ly)
    const steps = Math.max(1, Math.floor(d / Math.max(2, eraserRadius.value * 0.4)))
    for (let i = 1; i <= steps; i++) applyErase(lx + ((x - lx) * i) / steps, ly + ((y - ly) * i) / steps)
    lastErase = [x, y]
  } else if (curStroke) {
    const last = curStroke.pts[curStroke.pts.length - 1]
    if (Math.hypot(x - last[0], y - last[1]) >= 2) {
      curStroke.pts.push([Math.round(x), Math.round(y)])
      drawLive()
    }
  }
}

const onPointerUp = (e) => {
  if (!drawing || e.pointerId !== drawingPointerId) return
  drawing = false
  drawingPointerId = null
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
  if (e.key === 'Escape' && activeBrush.value) {
    activeBrush.value = null
    return
  }
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
  if (pickerOpen.value === type) {
    pickerOpen.value = null
    return
  }
  if (isMobile.value) {
    // 手机:信纸缩放铺满屏宽,按钮贴右缘,锚点定位会把弹窗推出屏幕外 → 固定居中展示
    pickerCentered.value = true
    pickerStyle.value = {}
  } else {
    pickerCentered.value = false
    const btn = type === 'mood' ? moodBtnRef.value : weatherBtnRef.value
    if (btn) {
      const rect = btn.getBoundingClientRect()
      const popW = 240
      const popH = 220
      const top = Math.min(Math.max(8, rect.top + rect.height / 2 - popH / 2), window.innerHeight - popH - 8)
      const left = Math.min(rect.right + 8, window.innerWidth - popW - 8)
      pickerStyle.value = { position: 'fixed', top: top + 'px', left: left + 'px', zIndex: 61 }
    }
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
    const data = await publicApi.getWeather()
    if (data?.text) {
      const match = WEATHERS.find((w) => data.text.includes(w.label) || w.label.includes(data.text))
      form.weather = match ? match.icon : ''
    }
  } catch (e) {}
}

const onSave = async () => {
  if (!content.value.trim()) return ElMessage.warning(t('diary.inputContent'))
  loading.value = true
  try {
    const payload = {
      content: content.value,
      mood: form.mood,
      weather: form.weather,
      date: form.date + ' ' + form.time,
      visibility: form.visibility,
      doodle: strokes.value.length ? JSON.stringify({ v: 1, strokes: strokes.value }) : null,
    }
    const picked = audience.loaded && audience.canShare ? (needsAudience(form.visibility) ? [...audience.selected] : []) : null
    let diaryId = isEdit.value ? route.params.id : null
    if (isEdit.value) await diaryApi.update(route.params.id, payload)
    else {
      const created = await diaryApi.create(payload)
      diaryId = created?.id
    }
    if (diaryId && picked !== null) {
      try {
        const res = await audienceApi.save('diary', diaryId, picked)
        if (picked.length && res?.targetCount === 0) ElMessage.info(t('audience.noTarget'))
      } catch {
        ElMessage.warning(t('audience.saveFailed'))
      }
    }
    clearDraft() // 保存成功,草稿使命完成
    ElMessage.success(t('common.saveSuccess'))
    // 「上次在做」:日记没有标题,用正文首行/前 24 字做卡片标题
    if (diaryId) {
      const title = content.value.trim().split('\n')[0].slice(0, 24)
      recordDoing({ kind: 'DIARY', id: diaryId, title, authorId: authorId.value ?? userStore.userInfo?.id })
    }
    // 编辑完成回到该作者的日记本翻书视图;新建回到书架
    router.push(isEdit.value && authorId.value != null ? `/diary/book/${authorId.value}` : '/diary')
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  const draft = readDraft()
  if (isEdit.value) {
    const d = await diaryApi.detail(route.params.id)
    content.value = d.content || ''
    authorId.value = d.authorId
    strokes.value = parseDoodle(d.doodle)
    const raw = String(d.createdAt || '')
    Object.assign(form, {
      mood: d.mood || '',
      weather: d.weather || '',
      date: raw.slice(0, 10) || form.date,
      time: raw.slice(11, 16) || form.time,
      visibility: d.visibility || 'FAMILY',
    })
    if (draft) applyDraft(draft) // 旋转/刷新丢内容后回来:草稿是本设备最近一次编辑,覆盖服务端数据
    if (form.visibility === 'CIRCLE') loadAudience(route.params.id)
  } else {
    if (draft) applyDraft(draft)
    else loadCurrentWeather()
  }
  history.value = [strokes.value]
  hIndex.value = 0
  nextTick(() => {
    autoResize()
    redrawBase()
    if (!compactTray.value) textareaRef.value?.focus({ preventScroll: true })
  })
  window.addEventListener('keydown', onKeydown)
  syncHeight()
  window.addEventListener('pagehide', flushDraftIfDirty)
  document.addEventListener('visibilitychange', flushDraftIfDirty)
  if (typeof ResizeObserver !== 'undefined' && paperRef.value) {
    scaleObserver = new ResizeObserver(() => syncHeight())
    scaleObserver.observe(paperRef.value)
  }
  draftReady = true
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  window.removeEventListener('pagehide', flushDraftIfDirty)
  document.removeEventListener('visibilitychange', flushDraftIfDirty)
  scaleObserver?.disconnect()
})

watch(content, () => autoResize())
watch(paperScale, () => syncHeight())
// 草稿自动保存:任何内容/心情天气/涂鸦变更(初始装载完成后)防抖落盘
watch([content, () => form.mood, () => form.weather, () => form.date, () => form.time, () => form.visibility, strokes], () => {
  if (!draftReady) return
  draftDirty = true
  queueSaveDraft()
})
</script>

<style scoped>
.page.diary-editor {
  overflow-x: clip;
}
.diary-layout {
  display: flex;
  margin-inline: min(2vw, 8px);
  gap: 16px;
  justify-content: center;
  align-items: flex-start;
}
.diary-layout.compact {
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.paper-column {
  width: min(100%, 496px);
  order: -1;
  background-color: var(--color-card);
  border-radius: 14px;
  box-shadow: var(--shadow);
  overflow: clip;
}
.compact .paper-column {
  order: 0;
}
.mobile-layout.has-tabbar .diary-layout {
  padding-bottom: 64px;
}
.paper-scaler.scaled {
  overflow: clip;
}
.paper-sheet {
  width: calc(28 * 16px + 24px * 2);
  box-shadow: none;
  overflow: hidden;
  position: relative;
}

:deep(.el-date-editor.el-input),
:deep(.el-time-editor.el-input) {
  --el-border-color: transparent;
  width: 100px !important;
}
:deep(.el-date-editor.el-input .el-input__wrapper),
:deep(.el-time-editor.el-input .el-input__wrapper) {
  box-shadow: none !important;
  background: transparent !important;
  padding: 0 4px;
}
:deep(.el-date-editor.el-input .el-input__inner),
:deep(.el-time-editor.el-input .el-input__inner) {
  font-size: 13px;
}

.paper-header {
  padding: 8px 24px 0;
}
/* header-row: left 和 right 都是纵向排列,底端对齐 */
.header-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  min-height: 56px;
}
/* header-left: 日期和时间上下排列 */
.header-left {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding-bottom: 3px;
}
.date-wrap {
  display: flex;
  flex-direction: column;
  align-items: stretch;
}
.date-underline {
  width: 100px;
  border-bottom: 1px solid var(--ruling-color);
  margin-top: 1px;
}
/* header-right: 心情和天气上下排列 */
.header-right {
  display: flex;
  flex-direction: column;
  gap: 0;
  align-items: flex-start;
}
.mw-display {
  display: flex;
  align-items: center;
  gap: 4px;
  border: none;
  border-bottom: 1px solid var(--ruling-color);
  border-radius: 0;
  padding: 0 0 3px;
  background: transparent;
  cursor: pointer;
  transition: opacity 0.15s;
  opacity: 0.7;
  line-height: 28px;
  height: 28px;
  width: 80px;
}
.mw-display:hover {
  opacity: 1;
}
.mw-icon {
  font-size: 16px;
  line-height: 1;
}
.mw-label {
  font-size: 13px;
  color: var(--color-text);
}

/* 正文区:正文从 y=0 起(与查看页 DiaryPage 坐标系一致,涂鸦/分页两页对齐) */
.paper-body {
  position: relative;
  padding: 0 24px;
  overflow: hidden;
}
/* 横线背景:每28px一条浅线 */
.ruling-bg {
  position: absolute;
  top: 0;
  left: 24px;
  right: 24px;
  background-image: repeating-linear-gradient(to bottom, transparent, transparent 27px, var(--ruling-color) 27px, var(--ruling-color) 28px);
  pointer-events: none;
  z-index: 0;
}
/* 分页线:每18行(504px)一条深色实线 */
.page-break-bg {
  position: absolute;
  top: 0;
  left: 12px;
  right: 12px;
  background-image: repeating-linear-gradient(
    to bottom,
    transparent,
    transparent calc(504px - 2px),
    var(--break-color) calc(504px - 2px),
    var(--break-color) 504px
  );
  pointer-events: none;
  z-index: 0;
}
.paper-textarea {
  position: relative;
  width: 100%;
  border: none;
  outline: none;
  resize: none;
  background: transparent;
  font-family: 'Cascadia Mono', 'Consolas', 'Courier New', monospace;
  font-size: 16px;
  line-height: 28px;
  color: var(--color-text);
  z-index: 1;
  overflow: hidden;
  word-break: break-all;
  padding: 0;
}
.paper-textarea::placeholder {
  color: var(--color-text-secondary);
  opacity: 0.4;
}

.paper-footer {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  border-top: 1px solid var(--color-border);
}
.page-num {
  font-size: 12px;
  color: var(--color-text-secondary);
  font-variant-numeric: tabular-nums;
}
.vis-row {
  display: flex;
  flex-wrap: wrap;
  margin-left: auto;
  align-items: center;
  gap: 8px;
}
.audience-tip {
  margin: 0;
  font-size: 12px;
  color: var(--color-text-secondary);
}

/* 涂鸦画布层:荧光笔专用画布 multiply 叠在文字上(深色模式 screen),墨迹/实时画布普通叠加 */
.doodle-canvas {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  pointer-events: none;
  z-index: 3;
}
.doodle-mark {
  z-index: 2;
  mix-blend-mode: multiply;
  opacity: 0.55;
}
html.dark .doodle-mark {
  mix-blend-mode: screen;
}
.doodle-live {
  z-index: 4;
}

/* 心情/天气弹窗:取全局遮罩档,与 EP 弹窗同一层级语义(页内渲染,实际仍受内容栏层叠限制) */
.picker-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: var(--z-overlay);
}
.picker-pop {
  background: rgba(255, 253, 248, 0.85);
  backdrop-filter: blur(24px) saturate(1.2);
  -webkit-backdrop-filter: blur(24px) saturate(1.2);
  border-radius: 14px;
  box-shadow: 0 8px 32px rgba(58, 46, 34, 0.12);
  padding: 14px;
  width: 240px;
  animation: pickerIn 0.2s ease;
}
html.dark .picker-pop {
  background: rgba(var(--color-card-rgb), 0.9);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.4);
}
@keyframes pickerIn {
  from {
    opacity: 0;
    transform: translateY(-6px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
/* 手机居中模式:transform 已用于居中定位,入场动画只走透明度避免覆盖位移 */
.picker-pop.centered {
  position: fixed;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  animation: pickerInCenter 0.2s ease;
}
@keyframes pickerInCenter {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}
.picker-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-secondary);
  margin-bottom: 10px;
  text-align: center;
}
.picker-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 4px;
}
.picker-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  padding: 8px 4px;
  border: none;
  border-radius: 10px;
  background: transparent;
  cursor: pointer;
  transition: all 0.15s;
}
.picker-cell:hover {
  background: rgba(var(--color-brand-rgb), 0.08);
}
.picker-cell.active {
  background: rgba(var(--color-brand-rgb), 0.12);
}
html.dark .picker-cell:hover {
  background: rgba(var(--color-brand-rgb), 0.08);
}
html.dark .picker-cell.active {
  background: rgba(var(--color-brand-rgb), 0.12);
}
.cell-icon {
  font-size: 22px;
  line-height: 1;
}
.cell-label {
  font-size: 11px;
  color: var(--color-text-secondary);
}
.picker-clear {
  width: 100%;
  margin-top: 8px;
  border: none;
  border-radius: 8px;
  padding: 6px;
  background: rgba(185, 96, 88, 0.06);
  color: #b04a3a;
  font-size: 12px;
  cursor: pointer;
}
html.dark .picker-clear {
  background: rgba(201, 116, 116, 0.08);
  color: var(--color-danger);
}
</style>
