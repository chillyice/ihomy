<!-- 日记编辑页:信纸+磨砂玻璃风格,顶部页眉融为横线纸一部分,弹窗紧贴按钮 -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('diary.title'), to: '/diary' }, { label: isEdit ? $t('diary.editDiary') : $t('diary.newDiary') }]" />

    <div class="diary-layout">
      <!-- 左侧:日记纸 -->
      <div class="notebook">
        <!-- 顶部抬头:日期(左) + 心情/天气(右) -->
        <div class="paper-header">
          <div class="header-row">
            <!-- 左:日期,底部对齐到天气行 -->
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
            </div>
            <!-- 右:心情+天气 上下排列,左对齐,固定宽度 -->
            <div class="header-right">
              <button type="button" ref="moodBtnRef" class="mw-display" @click="togglePicker('mood', $event)">
                <span class="mw-icon">{{ form.mood || '😊' }}</span>
                <span class="mw-label">{{ moodLabel }}</span>
              </button>
              <button type="button" ref="weatherBtnRef" class="mw-display" @click="togglePicker('weather', $event)">
                <span class="mw-icon">{{ form.weather || '☀️' }}</span>
                <span class="mw-label">{{ weatherLabel }}</span>
              </button>
            </div>
          </div>
        </div>

        <!-- 横格纸区域 -->
        <div class="paper-body">
          <div class="ruling"></div>
          <textarea
            ref="taRef"
            v-model="currentPageText"
            class="paper-textarea"
            :placeholder="$t('diary.inputContent')"
            @input="onInput"
            @keydown="onKeydown"
          ></textarea>
        </div>

        <!-- 底部:翻页 + 页码 + 权限+保存 -->
        <div class="paper-footer">
          <div class="footer-left">
            <button type="button" class="page-btn" :disabled="pageNum === 1" @click="prevPage">‹ 上一页</button>
            <button type="button" class="page-btn" :disabled="pageNum === totalPages && !currentPageText" @click="nextPage">下一页 ›</button>
            <span class="page-num">{{ pageNum }} / {{ totalPages }}</span>
          </div>
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

      <!-- 右侧:涂鸦占位区(宽度减半) -->
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

    <!-- 心情/天气选择弹窗:紧贴按钮定位 -->
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
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
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

const form = reactive({ content: '', mood: '', weather: '', date: new Date().toISOString().slice(0, 10), visibility: 0 })
const taRef = ref(null)
const pickerOpen = ref(null)
const pickerStyle = ref({})
const moodBtnRef = ref(null)
const weatherBtnRef = ref(null)

const moodLabel = computed(() => MOODS.find(m => m.icon === form.mood)?.label || '心情')
const weatherLabel = computed(() => WEATHERS.find(w => w.icon === form.weather)?.label || '天气')

const togglePicker = (type, e) => {
  if (pickerOpen.value === type) { pickerOpen.value = null; return }
  const btn = type === 'mood' ? moodBtnRef.value : weatherBtnRef.value
  if (btn) {
    const rect = btn.getBoundingClientRect()
    // 弹窗与按钮垂直居中:弹窗顶部 = 按钮中心 - 弹窗高度/2
    const popH = 220
    const top = rect.top + rect.height / 2 - popH / 2
    pickerStyle.value = {
      position: 'fixed',
      top: Math.max(8, top) + 'px',
      left: (rect.right + 8) + 'px',
      zIndex: 59,
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

const LINES_PER_PAGE = 20
const CHARS_PER_LINE = 38
const CHARS_PER_PAGE = LINES_PER_PAGE * CHARS_PER_LINE

const pages = ref([''])
const pageNum = ref(1)
const totalPages = computed(() => pages.value.length)

const currentPageText = computed({
  get: () => pages.value[pageNum.value - 1] || '',
  set: (val) => { pages.value[pageNum.value - 1] = val },
})

const onInput = () => {
  const text = pages.value[pageNum.value - 1]
  if (text.length > CHARS_PER_PAGE) {
    const overflow = text.slice(CHARS_PER_PAGE)
    pages.value[pageNum.value - 1] = text.slice(0, CHARS_PER_PAGE)
    if (pageNum.value >= pages.value.length) pages.value.push(overflow)
    else pages.value[pageNum.value] = (pages.value[pageNum.value] || '') + overflow
    nextTick(() => { pageNum.value++; taRef.value?.focus() })
  }
}

const onKeydown = (e) => {
  if (e.key === 'PageDown' && !e.shiftKey) { e.preventDefault(); nextPage() }
  if (e.key === 'PageUp' && !e.shiftKey) { e.preventDefault(); prevPage() }
}

const prevPage = () => { if (pageNum.value > 1) { pageNum.value--; nextTick(() => taRef.value?.focus()) } }
const nextPage = () => {
  if (pageNum.value < pages.value.length) { pageNum.value++; nextTick(() => taRef.value?.focus()) }
  else if (currentPageText.value.trim()) { pages.value.push(''); pageNum.value++; nextTick(() => taRef.value?.focus()) }
}

const loadCurrentWeather = async () => {
  try {
    const res = await fetch('/api/public/weather')
    if (res.ok) {
      const json = await res.json()
      if (json.code === 0 && json.data?.text) {
        const text = json.data.text
        const match = WEATHERS.find(w => text.includes(w.label) || w.label.includes(text))
        form.weather = match ? match.icon : ''
      }
    }
  } catch (e) {}
}

const onSave = async () => {
  const fullContent = pages.value.join('')
  if (!fullContent.trim()) return ElMessage.warning(t('diary.inputContent'))
  loading.value = true
  try {
    const payload = { content: fullContent, mood: form.mood, weather: form.weather, date: form.date, visibility: form.visibility }
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
    const content = d.content || ''
    const arr = []
    for (let i = 0; i < content.length; i += CHARS_PER_PAGE) arr.push(content.slice(i, i + CHARS_PER_PAGE))
    pages.value = arr.length ? arr : ['']
    pageNum.value = 1
    Object.assign(form, {
      mood: d.mood || '', weather: d.weather || '',
      date: d.createdAt ? d.createdAt.slice(0, 10) : new Date().toISOString().slice(0, 10),
      visibility: d.visibility === 'PRIVATE' ? 0 : d.visibility === 'PUBLIC' ? 4 : 3,
    })
  } else {
    loadCurrentWeather()
  }
})
</script>

<style scoped>
/* 布局:左纸 2fr + 右涂鸦 1fr */
.diary-layout { display: grid; grid-template-columns: 2fr 1fr; gap: 16px; }

/* ========== 日记纸 ========== */
.notebook {
  background: #fffdf8;
  border-radius: 14px;
  box-shadow: 0 4px 24px rgba(58,46,34,0.1);
  overflow: hidden;
  position: relative;
  display: flex;
  flex-direction: column;
}
html.dark .notebook { background: #1E2A48; box-shadow: 0 4px 24px rgba(0,0,0,0.3); }
.notebook { --ruling-color: rgba(100,130,180,0.15); }
html.dark .notebook { --ruling-color: rgba(232,220,200,0.06); }

/* 日期选择器:无边框 */
:deep(.el-date-editor.el-input) { --el-border-color: transparent; }
:deep(.el-date-editor.el-input .el-input__wrapper) {
  box-shadow: none !important;
  background: transparent !important;
  padding: 0 4px;
}
:deep(.el-date-editor.el-input .el-input__inner) { font-size: 13px; }

/* 顶部抬头 */
.paper-header { padding: 8px 20px 0; }
.header-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  min-height: 56px;
}
.header-left { display: flex; align-items: flex-end; padding-bottom: 3px; }
.date-wrap { display: flex; flex-direction: column; align-items: stretch; }
.date-underline {
  width: 130px;
  border-bottom: 1px solid var(--ruling-color);
  margin-top: 1px;
}

/* 右侧:心情+天气 上下排列,左对齐,固定宽度 */
.header-right { display: flex; flex-direction: column; gap: 0; align-items: flex-start; }
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
.mw-display:hover { opacity: 1; }
.mw-icon { font-size: 16px; line-height: 1; }
.mw-label { font-size: 13px; color: var(--color-text); }

/* 横格纸正文:下移 2px */
.paper-body { position: relative; flex: 1; min-height: 460px; padding: 2px 20px 0; }
.ruling {
  position: absolute;
  top: 0; left: 20px; right: 20px; bottom: 0;
  background-image: repeating-linear-gradient(to bottom, transparent, transparent 27px, var(--ruling-color) 27px, var(--ruling-color) 28px);
  pointer-events: none;
}
.paper-textarea {
  position: relative;
  width: 100%;
  min-height: 460px;
  border: none; outline: none; resize: none;
  background: transparent;
  font-family: inherit;
  font-size: 15px;
  line-height: 28px;
  color: var(--color-text);
  z-index: 1;
}
.paper-textarea::placeholder { color: var(--color-text-secondary); opacity: 0.4; }

/* 底部:翻页+页码(左) + 权限+保存(右) */
.paper-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 20px 14px;
  border-top: 1px solid var(--ruling-color);
}
.footer-left { display: flex; align-items: center; gap: 8px; }
.page-num { font-size: 12px; color: var(--color-text-secondary); font-variant-numeric: tabular-nums; margin-left: 4px; }
.vis-row { display: flex; align-items: center; gap: 12px; }
.page-btn {
  height: 30px; padding: 0 14px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: transparent;
  color: var(--color-text-secondary);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}
.page-btn:hover:not(:disabled) { background: rgba(184,140,110,0.08); color: var(--color-accent, #b88c6e); }
.page-btn:disabled { opacity: 0.35; cursor: not-allowed; }
html.dark .page-btn { border-color: rgba(255,255,255,0.12); color: rgba(232,220,200,0.5); }
html.dark .page-btn:hover:not(:disabled) { background: rgba(212,178,152,0.08); color: #d4b298; }

/* ========== 右侧涂鸦占位区 ========== */
.doodle-area {
  background: rgba(255,255,255,0.45);
  backdrop-filter: blur(24px) saturate(1.2);
  -webkit-backdrop-filter: blur(24px) saturate(1.2);
  border-radius: 14px;
  min-height: 600px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(255,255,255,0.4);
  box-shadow: 0 2px 12px rgba(58,46,34,0.06);
}
html.dark .doodle-area {
  background: rgba(30,42,72,0.45);
  border-color: rgba(255,255,255,0.08);
  box-shadow: 0 2px 12px rgba(0,0,0,0.15);
}
.doodle-placeholder { display: flex; flex-direction: column; align-items: center; gap: 6px; color: var(--color-text-secondary); }
.doodle-text { font-size: 14px; font-weight: 500; opacity: 0.4; }
.doodle-hint { font-size: 12px; opacity: 0.3; }

/* ========== 心情/天气弹窗 ========== */
.picker-overlay {
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  z-index: 59;
}
.picker-pop {
  background: rgba(255,253,248,0.85);
  backdrop-filter: blur(24px) saturate(1.2);
  -webkit-backdrop-filter: blur(24px) saturate(1.2);
  border-radius: 14px;
  box-shadow: 0 8px 32px rgba(58,46,34,0.12);
  padding: 14px;
  width: 240px;
  animation: pickerIn 0.2s ease;
}
html.dark .picker-pop {
  background: rgba(30,42,72,0.9);
  box-shadow: 0 8px 32px rgba(0,0,0,0.4);
}
@keyframes pickerIn { from { opacity: 0; transform: translateY(-6px); } to { opacity: 1; transform: translateY(0); } }
.picker-title { font-size: 12px; font-weight: 600; color: var(--color-text-secondary); margin-bottom: 10px; text-align: center; }
.picker-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 4px; }
.picker-cell {
  display: flex; flex-direction: column; align-items: center; gap: 3px;
  padding: 8px 4px; border: none; border-radius: 10px;
  background: transparent; cursor: pointer; transition: all 0.15s;
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
  .diary-layout { grid-template-columns: 1fr; }
  .doodle-area { min-height: 200px; }
}
</style>
