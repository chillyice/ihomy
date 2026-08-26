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

          <!-- 正文:按整页自适应高度,横线背景+分页线 -->
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

      <!-- 右侧:涂鸦占位区 sticky -->
      <div class="doodle-area">
        <div class="doodle-placeholder">
          <svg viewBox="0 0 24 24" width="36" height="36" fill="none" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round" opacity="0.25">
            <path d="M12 19l7-7 3 3-7 7-3-3z"/><path d="M18 13l-1.5-7.5L2 2l3.5 14.5L13 18l5-5z"/><path d="M2 2l7.586 7.586"/><circle cx="11" cy="11" r="2"/>
          </svg>
          <span class="doodle-text">画笔涂鸦</span>
          <span class="doodle-hint">功能开发中,敬请期待</span>
        </div>
      </div>
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
import { ref, reactive, computed, onMounted, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { diaryApi } from '@/api'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import Breadcrumb from '@/components/Breadcrumb.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const isEdit = computed(() => !!route.params.id)
const loading = ref(false)

const MOODS = [
  { icon: '😊', label: '开心' }, { icon: '😌', label: '平静' }, { icon: '😢', label: '难过' }, { icon: '😡', label: '生气' },
  { icon: '😰', label: '焦虑' }, { icon: '🥰', label: '温馨' }, { icon: '😴', label: '疲倦' }, { icon: '🤔', label: '思考' },
  { icon: '🥳', label: '兴奋' }, { icon: '😎', label: '得意' }, { icon: '🥺', label: '感动' }, { icon: '😔', label: '失落' },
]
const WEATHERS = [
  { icon: '☀️', label: '晴' }, { icon: '⛅', label: '多云' }, { icon: '☁️', label: '阴' }, { icon: '🌧️', label: '雨' },
  { icon: '⛈️', label: '雷雨' }, { icon: '❄️', label: '雪' }, { icon: '🌫️', label: '雾' }, { icon: '🌪️', label: '大风' },
]

const LINE_H = 28
const LINES_PER_PAGE = 18
const PAGE_H = LINE_H * LINES_PER_PAGE

const form = reactive({ mood: '', weather: '', date: new Date().toISOString().slice(0, 10), time: new Date().toTimeString().slice(0, 5), visibility: 0 })
const content = ref('')
const pickerOpen = ref(null)
const pickerStyle = ref({})
const moodBtnRef = ref(null)
const weatherBtnRef = ref(null)
const textareaRef = ref(null)
const bodyHeight = ref(PAGE_H)

const moodLabel = computed(() => MOODS.find(m => m.icon === form.mood)?.label || '心情')
const weatherLabel = computed(() => WEATHERS.find(w => w.icon === form.weather)?.label || '天气')
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
    const payload = { content: content.value, mood: form.mood, weather: form.weather, date: form.date + ' ' + form.time, visibility: form.visibility }
    if (isEdit.value) await diaryApi.update(route.params.id, payload)
    else await diaryApi.create(payload)
    ElMessage.success(t('common.saveSuccess'))
    router.push('/diary')
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  if (isEdit.value) {
    const d = await diaryApi.detail(route.params.id)
    content.value = d.content || ''
    const dt = d.createdAt ? new Date(d.createdAt) : new Date()
    Object.assign(form, {
      mood: d.mood || '', weather: d.weather || '',
      date: dt.toISOString().slice(0, 10),
      time: dt.toTimeString().slice(0, 5),
      visibility: d.visibility === 'PRIVATE' ? 0 : d.visibility === 'PUBLIC' ? 4 : 3,
    })
  } else {
    loadCurrentWeather()
  }
  nextTick(() => { autoResize(); textareaRef.value?.focus() })
})

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

/* 正文区 */
.paper-body { position: relative; padding: 2px 24px 0; overflow: hidden; }
/* 横线背景:每28px一条浅线 */
.ruling-bg {
  position: absolute; top: 2px; left: 24px; right: 24px;
  background-image: repeating-linear-gradient(to bottom,
    transparent, transparent 27px,
    var(--ruling-color) 27px, var(--ruling-color) 28px);
  pointer-events: none; z-index: 0;
}
/* 分页线:每18行(504px)一条深色实线 */
.page-break-bg {
  position: absolute; top: 2px; left: 12px; right: 12px;
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

.doodle-area {
  background: rgba(255,255,255,0.45); backdrop-filter: blur(24px) saturate(1.2);
  -webkit-backdrop-filter: blur(24px) saturate(1.2); border-radius: 14px;
  width: calc(14 * 16px + 24px * 2); height: calc(18 * 28px);
  display: flex; align-items: center; justify-content: center;
  border: 1px solid rgba(255,255,255,0.4); box-shadow: 0 2px 12px rgba(58,46,34,0.06);
  flex-shrink: 0; position: sticky; top: 16px; align-self: flex-start;
}
html.dark .doodle-area {
  background: rgba(30,42,72,0.45); border-color: rgba(255,255,255,0.08);
  box-shadow: 0 2px 12px rgba(0,0,0,0.15);
}
.doodle-placeholder { display: flex; flex-direction: column; align-items: center; gap: 6px; color: var(--color-text-secondary); }
.doodle-text { font-size: 14px; font-weight: 500; opacity: 0.4; }
.doodle-hint { font-size: 12px; opacity: 0.3; }

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
  .doodle-area { width: 100%; height: 200px; position: static; }
}
</style>
