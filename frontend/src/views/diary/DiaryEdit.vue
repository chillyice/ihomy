<!-- 日记编辑页:多张纸垂直排列,连续文本流自动分页,涂鸦区 sticky 固定右侧 -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('diary.title'), to: '/diary' }, { label: isEdit ? $t('diary.editDiary') : $t('diary.newDiary') }]" />

    <div class="diary-layout">
      <!-- 左侧:纸张栈(多张纸垂直排列) -->
      <div class="paper-stack">
        <div v-for="(pageText, idx) in pages" :key="idx" class="paper-sheet">
          <!-- 页眉:仅第一页 -->
          <div v-if="idx === 0" class="paper-header">
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

          <!-- 正文:每页固定 18 行 × 28px -->
          <div class="paper-body">
            <div class="ruling-bg"></div>
            <textarea
              :ref="el => { if (el) pageRefs[idx] = el }"
              :value="pageText"
              class="paper-textarea"
              :placeholder="idx === 0 ? $t('diary.inputContent') : ''"
              spellcheck="false"
              @input="onInput(idx, $event)"
              @keydown="onKeydown(idx, $event)"
            ></textarea>
          </div>

          <!-- 页脚:仅最后一页 -->
          <div v-if="idx === pages.length - 1" class="paper-footer">
            <span class="page-num">{{ pages.length }} 页</span>
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

const LINE_H = 28
const LINES_PER_PAGE = 18
const CHARS_PER_LINE = 28
const CHARS_PER_PAGE = LINES_PER_PAGE * CHARS_PER_LINE

const form = reactive({ mood: '', weather: '', date: new Date().toISOString().slice(0, 10), visibility: 0 })
const pages = ref([''])
const pageRefs = ref([])
const pickerOpen = ref(null)
const pickerStyle = ref({})
const moodBtnRef = ref(null)
const weatherBtnRef = ref(null)

const moodLabel = computed(() => MOODS.find(m => m.icon === form.mood)?.label || '心情')
const weatherLabel = computed(() => WEATHERS.find(w => w.icon === form.weather)?.label || '天气')
const fullContent = computed(() => pages.value.join(''))

// 核心分页逻辑:从 startIdx 开始向下重新分配文本
const redistribute = (startIdx) => {
  let i = startIdx
  while (i < pages.value.length) {
    const text = pages.value[i]
    if (text.length > CHARS_PER_PAGE) {
      const overflow = text.slice(CHARS_PER_PAGE)
      pages.value[i] = text.slice(0, CHARS_PER_PAGE)
      if (i + 1 >= pages.value.length) pages.value.push('')
      pages.value[i + 1] = overflow + pages.value[i + 1]
      i++
    } else if (text.length < CHARS_PER_PAGE && i + 1 < pages.value.length) {
      const needed = CHARS_PER_PAGE - text.length
      const pulled = pages.value[i + 1].slice(0, needed)
      pages.value[i] = text + pulled
      pages.value[i + 1] = pages.value[i + 1].slice(needed)
      if (pages.value[i + 1] === '' && i + 1 === pages.value.length - 1) { pages.value.splice(i + 1, 1); break }
      i++
    } else break
  }
  while (pages.value.length > 1 && pages.value[pages.value.length - 1] === '') pages.value.pop()
}

const onInput = (idx, e) => {
  const ta = e.target
  const newText = ta.value
  const cursorPos = ta.selectionStart
  pages.value[idx] = newText
  const oldLen = newText.length
  redistribute(idx)
  const newLen = pages.value[idx].length
  if (newLen < oldLen && cursorPos > newLen) {
    nextTick(() => {
      const nextTa = pageRefs.value[idx + 1]
      if (nextTa) { nextTa.focus(); const p = cursorPos - newLen; nextTa.setSelectionRange(p, p) }
    })
  } else if (newLen !== oldLen) {
    nextTick(() => { ta.setSelectionRange(cursorPos, cursorPos) })
  }
}

const onKeydown = (idx, e) => {
  const ta = e.target
  const pos = ta.selectionStart
  const len = ta.value.length
  const collapsed = ta.selectionStart === ta.selectionEnd

  // 左键在页首 → 跳到上一页末尾
  if (e.key === 'ArrowLeft' && idx > 0 && collapsed && pos === 0) {
    e.preventDefault()
    nextTick(() => { const prev = pageRefs.value[idx - 1]; if (prev) { prev.focus(); const p = prev.value.length; prev.setSelectionRange(p, p) } })
    return
  }
  // 右键在页尾 → 跳到下一页开头
  if (e.key === 'ArrowRight' && idx < pages.value.length - 1 && collapsed && pos === len) {
    e.preventDefault()
    nextTick(() => { const next = pageRefs.value[idx + 1]; if (next) { next.focus(); next.setSelectionRange(0, 0) } })
    return
  }
  // 退格在页首 → 删除上一页最后一个字符,文本回流
  if (e.key === 'Backspace' && idx > 0 && collapsed && pos === 0) {
    e.preventDefault()
    const prevText = pages.value[idx - 1]
    if (prevText.length > 0) {
      const deletedPos = prevText.length - 1
      pages.value[idx - 1] = prevText.slice(0, -1)
      redistribute(idx - 1)
      nextTick(() => { const prev = pageRefs.value[idx - 1]; if (prev) { prev.focus(); prev.setSelectionRange(deletedPos, deletedPos) } })
    }
    return
  }
}

const togglePicker = (type) => {
  if (pickerOpen.value === type) { pickerOpen.value = null; return }
  const btn = type === 'mood' ? moodBtnRef.value : weatherBtnRef.value
  if (btn) {
    const rect = btn.getBoundingClientRect()
    const popH = 220
    const top = rect.top + rect.height / 2 - popH / 2
    pickerStyle.value = { position: 'fixed', top: Math.max(8, top) + 'px', left: (rect.right + 8) + 'px', zIndex: 59 }
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
  if (!fullContent.value.trim()) return ElMessage.warning(t('diary.inputContent'))
  loading.value = true
  try {
    const payload = { content: fullContent.value, mood: form.mood, weather: form.weather, date: form.date, visibility: form.visibility }
    if (isEdit.value) await diaryApi.update(route.params.id, payload)
    else await diaryApi.create(payload)
    ElMessage.success(t('common.saveSuccess'))
    router.push('/diary')
  } finally {
    loading.value = false
  }
}

const splitIntoPages = (content) => {
  if (!content) return ['']
  const arr = []
  for (let i = 0; i < content.length; i += CHARS_PER_PAGE) arr.push(content.slice(i, i + CHARS_PER_PAGE))
  return arr.length ? arr : ['']
}

onMounted(async () => {
  if (isEdit.value) {
    const d = await diaryApi.detail(route.params.id)
    pages.value = splitIntoPages(d.content || '')
    Object.assign(form, {
      mood: d.mood || '', weather: d.weather || '',
      date: d.createdAt ? d.createdAt.slice(0, 10) : new Date().toISOString().slice(0, 10),
      visibility: d.visibility === 'PRIVATE' ? 0 : d.visibility === 'PUBLIC' ? 4 : 3,
    })
  } else {
    loadCurrentWeather()
  }
  nextTick(() => pageRefs.value[0]?.focus())
})
</script>

<style scoped>
/* 布局:左纸张栈 + 右涂鸦 sticky */
.diary-layout {
  display: flex;
  gap: 8px;
  justify-content: center;
  align-items: flex-start;
}

/* ========== 纸张栈 ========== */
.paper-stack {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex-shrink: 0;
  width: calc(28 * 16px + 24px * 2);
}

/* 单张纸 */
.paper-sheet {
  background: #fffdf8;
  border-radius: 14px;
  box-shadow: 0 4px 24px rgba(58,46,34,0.1);
  overflow: hidden;
  position: relative;
  --ruling-color: rgba(100,130,180,0.15);
}
html.dark .paper-sheet {
  background: #1E2A48;
  box-shadow: 0 4px 24px rgba(0,0,0,0.3);
  --ruling-color: rgba(232,220,200,0.06);
}

/* 日期选择器 */
:deep(.el-date-editor.el-input) { --el-border-color: transparent; }
:deep(.el-date-editor.el-input .el-input__wrapper) {
  box-shadow: none !important; background: transparent !important; padding: 0 4px;
}
:deep(.el-date-editor.el-input .el-input__inner) { font-size: 13px; }

/* 页眉(仅第一页) */
.paper-header { padding: 8px 24px 0; }
.header-row { display: flex; justify-content: space-between; align-items: flex-end; min-height: 56px; }
.header-left { display: flex; align-items: flex-end; padding-bottom: 3px; }
.date-wrap { display: flex; flex-direction: column; align-items: stretch; }
.date-underline { width: 130px; border-bottom: 1px solid var(--ruling-color); margin-top: 1px; }
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

/* 正文区:固定高度 = 18行 × 28px,无滚动 */
.paper-body {
  position: relative;
  height: calc(18 * 28px);
  padding: 2px 24px 0;
  overflow: hidden;
}
.ruling-bg {
  position: absolute;
  top: 2px; left: 24px; right: 24px; bottom: 0;
  background-image: repeating-linear-gradient(to bottom,
    transparent, transparent 27px,
    var(--ruling-color) 27px, var(--ruling-color) 28px);
  pointer-events: none;
  z-index: 0;
}
.paper-textarea {
  position: relative;
  width: 100%;
  height: 100%;
  border: none; outline: none; resize: none;
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
.paper-textarea::placeholder { color: var(--color-text-secondary); opacity: 0.4; }

/* 页脚(仅最后一页) */
.paper-footer {
  display: flex; justify-content: space-between; align-items: center;
  padding: 10px 24px 14px;
  border-top: 1px solid var(--ruling-color);
}
.page-num { font-size: 12px; color: var(--color-text-secondary); font-variant-numeric: tabular-nums; }
.vis-row { display: flex; align-items: center; gap: 12px; }

/* ========== 涂鸦区:sticky 固定右侧 ========== */
.doodle-area {
  background: rgba(255,255,255,0.45);
  backdrop-filter: blur(24px) saturate(1.2);
  -webkit-backdrop-filter: blur(24px) saturate(1.2);
  border-radius: 14px;
  width: calc(14 * 16px + 24px * 2);
  height: calc(18 * 28px);
  display: flex; align-items: center; justify-content: center;
  border: 1px solid rgba(255,255,255,0.4);
  box-shadow: 0 2px 12px rgba(58,46,34,0.06);
  flex-shrink: 0;
  position: sticky;
  top: 16px;
  align-self: flex-start;
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
.picker-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; z-index: 59; }
.picker-pop {
  background: rgba(255,253,248,0.85);
  backdrop-filter: blur(24px) saturate(1.2);
  -webkit-backdrop-filter: blur(24px) saturate(1.2);
  border-radius: 14px;
  box-shadow: 0 8px 32px rgba(58,46,34,0.12);
  padding: 14px; width: 240px;
  animation: pickerIn 0.2s ease;
}
html.dark .picker-pop { background: rgba(30,42,72,0.9); box-shadow: 0 8px 32px rgba(0,0,0,0.4); }
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
  .diary-layout { flex-direction: column; align-items: center; }
  .doodle-area { width: 100%; height: 200px; position: static; }
}
</style>
