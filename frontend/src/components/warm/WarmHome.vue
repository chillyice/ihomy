<!-- 暖居首页:12 列网格,组件化 + 可编辑(OWNER 增删/拖拽排序),布局持久化 localStorage(与光尘仪表盘同款思路) -->
<template>
  <div class="gc-home" :class="{ 'gc-editing': editMode }">
    <div class="gc-main-head">
      <div>
        <h2 class="gc-h2">{{ greeting }}</h2>
        <div class="gc-subline">{{ familyName }} · {{ memberCount }} 位家人</div>
      </div>
      <div class="gc-head-btns">
        <template v-if="!editMode">
          <button v-if="canEdit" class="gc-btn ghost sm" @click="startEdit">编辑首页</button>
          <button class="gc-btn primary sm" @click="$router.push('/blog')">+ 发点什么</button>
          <button class="gc-btn ghost sm" @click="$router.push('/member')">家人</button>
        </template>
        <template v-else>
          <button class="gc-btn ghost sm" @click="resetLayout">恢复默认</button>
          <button class="gc-btn primary sm" @click="finishEdit">完成</button>
        </template>
      </div>
    </div>

    <!-- 编辑提示条 -->
    <Transition name="gc-fade">
      <div v-if="editMode" class="gc-edit-hint">编辑模式 · 拖拽卡片排序 · 右下角调整大小 · ✕ 移除组件 · 下方托盘添加组件</div>
    </Transition>

    <div class="gc-grid" @dragover.prevent @drop="onSidebarDrop">
      <div
        v-for="w in widgets"
        :key="w.id"
        class="gc-card"
        :class="[`gc-c${w.span}`, `gc-r${w.row}`, { 'gc-weather': w.id === 'weather', 'gc-overflow': w.id === 'weather', 'gc-card-edit': editMode, 'gc-card-dragover': dragOverId === w.id, 'gc-card-link': isLinkCard(w) }]"
        :draggable="editMode"
        @dragstart="onDragStart($event, w)"
        @dragover="onDragOver($event, w)"
        @drop="onDrop($event, w)"
        @dragend="onDragEnd"
        @click="onCardClick(w)"
      >
        <!-- 编辑手柄 -->
        <template v-if="editMode">
          <button class="gc-del" title="移除组件" @click.stop="removeWidget(w)">✕</button>
          <div class="gc-grip" title="拖拽排序">⋮⋮</div>
          <div class="gc-resize" title="拖拽调整大小" @mousedown.stop.prevent="onResizeStart($event, w)" @click.stop></div>
        </template>

        <!-- 天气活窗(暖居签名) -->
        <template v-if="w.id === 'weather'">
          <h3 class="gc-card-h3"><span>天气窗 · {{ weatherCity }}</span></h3>
          <div class="gc-glass" @click="!editMode && $router.push('/weather')">
            <div v-if="weatherBg" class="gc-win-bg" :style="{ backgroundImage: `url(${weatherBg})` }"></div>
            <div class="gc-win-glow"></div>
            <span class="gc-win-dust" style="animation-delay:-1s"></span>
            <span class="gc-win-dust" style="animation-delay:-4s;left:30%;top:12%"></span>
            <span class="gc-win-dust" style="animation-delay:-7s;left:12%;top:50%"></span>
            <span class="gc-win-dust" style="animation-delay:-10s;left:44%;top:30%"></span>
            <div class="gc-win-info">
              <div class="gc-temp">{{ weatherTemp }}°<span class="gc-c">C</span>
                <span v-if="todayHigh != null" class="gc-hilo">↑{{ todayHigh }}° ↓{{ todayLow ?? '—' }}°</span>
              </div>
              <div class="gc-we">{{ weatherText }}</div>
              <div class="gc-st">{{ weatherSub }}</div>
            </div>
            <div v-if="showWeatherMetrics(w)" class="gc-win-metrics">
              <span class="gc-wm">💧 {{ humidityText }}</span>
              <span class="gc-wm">🌬 {{ windText }}</span>
              <span class="gc-wm">🌫 {{ pm25Text }}</span>
            </div>
            <div v-if="showWeatherForecast(w)" class="gc-forecast">
              <div v-for="d in forecast" :key="d.fxDate" class="gc-fc-item">
                <span class="gc-fc-date">{{ fcDate(d.fxDate) }}</span>
                <span class="gc-fc-range">{{ d.tempMin }}~{{ d.tempMax }}°</span>
              </div>
            </div>
          </div>
        </template>

        <!-- 家人动态 -->
        <template v-else-if="w.id === 'feed'">
          <h3 class="gc-card-h3">家人动态</h3>
          <div v-if="feeds.length" class="gc-list">
            <div v-for="(f, i) in feeds.slice(0, nFeed(w))" :key="i" class="gc-row" @click="!editMode && goFeed(f)">
              <span class="gc-ic">{{ feedIcon(f.type) }}</span>
              <span class="gc-val">{{ f.authorName || '家人' }}</span>
              <span class="gc-muted">{{ feedSummary(f) }}</span>
            </div>
          </div>
          <div v-else class="gc-empty">暂无动态</div>
        </template>

        <!-- 照片(暖居磨砂缩略拼贴) -->
        <template v-else-if="w.id === 'photos'">
          <h3 class="gc-card-h3">照片<span class="gc-muted">最近</span></h3>
          <div v-if="photos.length" class="gc-photos" :style="{ gridTemplateColumns: `repeat(${photosCols(w)}, 1fr)` }" @click="!editMode && $router.push('/album')">
            <img v-for="p in photos.slice(0, nPhotos(w))" :key="p.id" :src="p.url" class="gc-photo" :alt="p.description || ''" loading="lazy" />
          </div>
          <div v-else class="gc-empty gc-empty-link" @click="!editMode && $router.push('/album')">去相册添加家庭照片</div>
        </template>

        <!-- 纪念日 -->
        <template v-else-if="w.id === 'anni'">
          <h3 class="gc-card-h3">纪念日</h3>
          <template v-if="anniversaries.length">
            <div class="gc-val-lg">{{ anniversaries[0].label }}</div>
            <div class="gc-muted">{{ anniversaries[0].date }} · 还有 {{ anniversaries[0].days }} 天</div>
            <div class="gc-meter"><i :style="{ width: Math.min(100, anniversaries[0].days) + '%' }"></i></div>
            <div v-if="nAnni(w) > 1" class="gc-list gc-anni-more">
              <div v-for="(a, i) in anniversaries.slice(1, nAnni(w))" :key="i" class="gc-row">
                <span class="gc-ic">🎂</span><span class="gc-val">{{ a.label }}</span><span class="gc-muted">{{ a.days }} 天</span>
              </div>
            </div>
          </template>
          <div v-else class="gc-empty">暂无纪念日</div>
        </template>

        <!-- 本月收支 -->
        <template v-else-if="w.id === 'finance'">
          <h3 class="gc-card-h3">本月收支</h3>
          <div class="gc-flex-baseline">
            <div class="gc-val-lg">¥ {{ fmt(balance) }}</div>
            <div class="gc-muted">结余</div>
          </div>
          <div v-if="showFinanceDetail(w)" class="gc-fin-row">
            <span class="gc-fin in">+{{ fmt(bookSummary.income) }}</span>
            <span class="gc-fin out">-{{ fmt(bookSummary.expense) }}</span>
          </div>
        </template>

        <!-- 寻物 -->
        <template v-else-if="w.id === 'item'">
          <h3 class="gc-card-h3">寻物</h3>
          <div class="gc-ctrl-row">
            <button class="gc-btn primary sm" @click="!editMode && $router.push('/item')">找东西 →</button>
          </div>
          <div v-if="items.length" class="gc-list">
            <div v-for="it in items.slice(0, nItem(w))" :key="it.id" class="gc-row">
              <span class="gc-ic">📦</span><span>{{ it.name }}</span><span class="gc-tag">{{ it.room_name || it.house_name || '—' }}</span>
            </div>
          </div>
          <div v-else class="gc-empty">尚未登记物品</div>
        </template>

        <!-- 悬赏任务 -->
        <template v-else-if="w.id === 'task'">
          <h3 class="gc-card-h3">悬赏任务</h3>
          <div v-if="tasks.length" class="gc-list">
            <div v-for="t in tasks.slice(0, nTask(w))" :key="t.id" class="gc-row" @click="!editMode && $router.push('/task')">
              <span class="gc-ic">{{ taskIcon(t.rewardType) }}</span>
              <span class="gc-val">{{ t.title }}</span>
              <span class="gc-tag">{{ taskStatusLabel(t.status) }}</span>
            </div>
          </div>
          <div v-else class="gc-empty">暂无任务</div>
        </template>

        <!-- 愿望单 -->
        <template v-else-if="w.id === 'wish'">
          <h3 class="gc-card-h3">愿望单</h3>
          <div v-if="wishes.length" class="gc-list">
            <div v-for="wi in wishes.slice(0, nWish(w))" :key="wi.id" class="gc-row" @click="!editMode && $router.push('/wish')">
              <span class="gc-ic">⭐</span>
              <span class="gc-val" :class="{ done: wi.status === 'ACHIEVED' }">{{ wi.title }}</span>
              <span class="gc-muted">{{ wi.status === 'ACHIEVED' ? '已实现' : '' }}</span>
            </div>
          </div>
          <div v-else class="gc-empty">暂无愿望</div>
        </template>

        <!-- 今日提醒 -->
        <template v-else-if="w.id === 'reminder'">
          <h3 class="gc-card-h3">今日提醒</h3>
          <div v-if="reminders.length" class="gc-list">
            <div v-for="r in reminders.slice(0, nReminder(w))" :key="r.id" class="gc-row" @click="!editMode && $router.push('/reminder')">
              <span class="gc-ic">🔔</span>
              <span class="gc-val">{{ r.title }}</span>
              <span class="gc-muted">{{ (r.remindTime || '').slice(0, 5) }}</span>
            </div>
          </div>
          <div v-else class="gc-empty">今日无待办</div>
        </template>

        <!-- 快捷入口(无专属内容的模块拖入后生成) -->
        <template v-else-if="w.kind === 'link'">
          <h3 class="gc-card-h3">{{ w.label }}</h3>
          <div class="gc-link-body" @click="!editMode && $router.push(w.path)">
            <span class="gc-link-icon">{{ w.icon }}</span>
            <span class="gc-link-text">打开{{ w.label }}</span>
            <span class="gc-link-arrow">→</span>
          </div>
        </template>
      </div>
    </div>

    <!-- 添加组件托盘(编辑态) -->
    <Transition name="gc-fade">
      <div v-if="editMode && availableWidgets.length" class="gc-add-tray">
        <div class="gc-add-label">添加组件</div>
        <button v-for="w in availableWidgets" :key="w.id" class="gc-add-item" @click="addWidget(w.id)">
          <span class="gc-add-icon">{{ w.icon }}</span>{{ w.label }}
        </button>
      </div>
    </Transition>
  </div>
</template>

<script setup>
import { computed, inject, onMounted, onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useThemeStore } from '@/stores/theme'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'
import { publicApi, bookApi, itemApi, taskApi, wishApi, reminderApi } from '@/api'

const router = useRouter()
const themeStore = useThemeStore()
const appStore = useAppStore()
const userStore = useUserStore()
const sunLight = inject(SUN_LIGHT_KEY)

defineProps({ weatherBg: { type: String, default: '' } })

const familyName = computed(() => appStore.familyName)
const memberCount = computed(() => appStore.stats.memberCount || 0)
const anniversaries = computed(() => appStore.stats.upcomingEvents || [])

// ========== 组件注册表 + 布局(localStorage 持久化) ==========
// 9 个富组件(有专属内容模板);span=8/4 对应 12 列栅格分栏
const WIDGETS = [
  { id: 'weather', label: '天气活窗', icon: '🌤', span: 8 },
  { id: 'feed', label: '家人动态', icon: '👥', span: 4 },
  { id: 'photos', label: '照片', icon: '📷', span: 4 },
  { id: 'anni', label: '纪念日', icon: '🎂', span: 4 },
  { id: 'finance', label: '本月收支', icon: '💰', span: 4 },
  { id: 'item', label: '寻物', icon: '📦', span: 4 },
  { id: 'task', label: '悬赏任务', icon: '🎯', span: 4 },
  { id: 'wish', label: '愿望单', icon: '⭐', span: 4 },
  { id: 'reminder', label: '今日提醒', icon: '🔔', span: 4 },
]
const WIDGET_BY_ID = Object.fromEntries(WIDGETS.map((w) => [w.id, w]))

// 侧栏模块 → 富组件映射;未映射的模块拖入后落为「快捷入口」卡片
const MODULE_TO_WIDGET = {
  blog: 'feed', album: 'photos', anniversary: 'anni', book: 'finance',
  item: 'item', task: 'task', wish: 'wish', reminder: 'reminder',
}
// 全部模块的入口元信息(标题/路径/图标),快捷入口卡片据此渲染
const MODULE_META = {
  blog: { title: '博客', path: '/blog', icon: '📝' },
  diary: { title: '日记本', path: '/diary', icon: '📖' },
  album: { title: '相册', path: '/album', icon: '📷' },
  cinema: { title: '放映厅', path: '/cinema', icon: '🎬' },
  music: { title: '音乐', path: '/music', icon: '🎵' },
  library: { title: '书架', path: '/library', icon: '📚' },
  item: { title: '物品定位', path: '/item', icon: '📦' },
  kitchen: { title: '厨房', path: '/kitchen', icon: '🍳' },
  anniversary: { title: '纪念日', path: '/anniversary', icon: '🎂' },
  points: { title: '积分商城', path: '/points', icon: '🏆' },
  task: { title: '任务悬赏', path: '/task', icon: '🎯' },
  reminder: { title: '今日提醒', path: '/reminder', icon: '🔔' },
  plan: { title: '家庭计划', path: '/plan', icon: '📋' },
  wish: { title: '愿望单', path: '/wish', icon: '⭐' },
  book: { title: '记账本', path: '/book', icon: '💰' },
  cascade: { title: '照片瀑布', path: '/cascade', icon: '🖼' },
  tree: { title: '家谱', path: '/tree', icon: '🌳' },
  tools: { title: '工具箱', path: '/tools', icon: '🧰' },
  member: { title: '家庭成员', path: '/member', icon: '👨‍👩‍👧' },
  storage: { title: '文件浏览', path: '/storage/files', icon: '🗂' },
}

// 布局键:富组件用其 id(如 feed),快捷入口用 'link:<code>'(如 link:diary);每项携带 span(列宽)+ row(行高),可调整
const defaultSpan = (id) => WIDGET_BY_ID[id]?.span || 4
// 行高基准:天气 8 列 × 4 行为基准,家人动态同高 4 行,其余 2 行
const defaultRow = (id) => (id === 'weather' || id === 'feed' ? 4 : 2)
const resolveWidget = (entry) => {
  const id = entry?.id
  if (!id) return null
  const row = entry.row || defaultRow(id)
  if (id.startsWith('link:')) {
    const meta = MODULE_META[id.slice(5)]
    if (!meta) return null
    return { id, kind: 'link', code: id.slice(5), label: meta.title, icon: meta.icon, path: meta.path, span: entry.span || 4, row }
  }
  const base = WIDGET_BY_ID[id]
  if (!base) return null
  return { ...base, span: entry.span || base.span, row }
}

// 默认展示 6 张;其余富组件(task/wish/reminder)由托盘加入,其余模块经侧栏拖入为入口卡片
const DEFAULT_LAYOUT = [
  { id: 'weather', span: 8, row: 4 },
  { id: 'feed', span: 4, row: 4 },
  { id: 'photos', span: 4, row: 2 },
  { id: 'anni', span: 4, row: 2 },
  { id: 'finance', span: 4, row: 2 },
  { id: 'item', span: 4, row: 2 },
]
const STORAGE_KEY = 'ihomy:guangchen:home:v2'
const LEGACY_KEY = 'ihomy:guangchen:home:v1'

const loadLayout = () => {
  const parse = (raw) => {
    try {
      if (!raw) return null
      const arr = JSON.parse(raw)
      if (!Array.isArray(arr) || !arr.length) return null
      return arr
        .map((it) => (typeof it === 'string'
          ? { id: it, span: defaultSpan(it), row: defaultRow(it) }
          : { ...it, row: it.row || defaultRow(it.id) }))
        .filter((it) => resolveWidget(it))
    } catch (e) { return null }
  }
  return parse(localStorage.getItem(STORAGE_KEY)) || parse(localStorage.getItem(LEGACY_KEY))
}
const saveLayout = () => { try { localStorage.setItem(STORAGE_KEY, JSON.stringify(layout.value)) } catch (e) {} }

const layout = ref((loadLayout() || DEFAULT_LAYOUT).map((e) => ({ ...e })))
const widgets = computed(() => layout.value.map(resolveWidget).filter(Boolean))
// 托盘仅列富组件(含无侧栏入口的天气);入口卡片经侧栏拖入
const availableWidgets = computed(() => WIDGETS.filter((w) => !layout.value.some((e) => e.id === w.id)))

const applyLayout = (entries) => { layout.value = entries.filter((e) => resolveWidget(e)); saveLayout() }
const addWidget = (id) => {
  if (layout.value.some((e) => e.id === id)) { ElMessage.info('该组件已在首页'); return }
  applyLayout([...layout.value, { id, span: defaultSpan(id), row: defaultRow(id) }])
  ElMessage.success(`已添加 ${resolveWidget({ id, span: defaultSpan(id), row: defaultRow(id) })?.label || ''} 组件`)
}
const addWidgetFromModule = (code) => addWidget(MODULE_TO_WIDGET[code] || `link:${code}`)
const removeWidget = (w) => { applyLayout(layout.value.filter((e) => e.id !== w.id)) }
const resetLayout = () => { layout.value = DEFAULT_LAYOUT.map((e) => ({ ...e })); saveLayout(); ElMessage.success('布局已重置') }

// ========== 编辑模式(复用 appStore.homeEditMode,与光尘同一开关) ==========
const editMode = computed(() => appStore.homeEditMode)
const canEdit = computed(() => userStore.isOwner)
const startEdit = () => appStore.toggleHomeEditMode()
const finishEdit = () => appStore.toggleHomeEditMode()

// 卡片点击:仅整卡可跳转的组件(weather/anni/finance/photos);feed/task/wish/reminder/item 由内部行/按钮跳转
const LINK_IDS = ['weather', 'anni', 'finance', 'photos']
const isLinkCard = (w) => LINK_IDS.includes(w.id)
const onCardClick = (w) => {
  if (editMode.value) return
  const dest = { weather: '/weather', anni: '/anniversary', finance: '/book', photos: '/album' }[w.id]
  if (dest) router.push(dest)
}

// ========== 拖拽排序 + 调整大小(原生 HTML5 DnD 排序 + 右下角手柄拖拽改列宽) ==========
const dragId = ref(null)
const dragOverId = ref(null)
const resizing = ref(false)
const resizeState = ref(null)
const ROW_H = 80
const GAP = 16
const MAX_COLS = 12
const MAX_ROWS = 20

const onDragStart = (e, w) => {
  if (resizing.value) { e.preventDefault(); return } // 调整大小时取消卡片原生拖拽
  dragId.value = w.id
  e.dataTransfer.effectAllowed = 'move'
}
const onDragOver = (e, w) => { e.preventDefault(); e.dataTransfer.dropEffect = dragId.value ? 'move' : 'copy'; if (dragId.value && dragOverId.value !== w.id) dragOverId.value = w.id }
// 侧栏拖入:dataTransfer 带模块 code 标记;卡片重排不 setData(为空),以此区分两类拖拽
const onSidebarDrop = (e) => { const code = e.dataTransfer?.getData('application/x-ihomy-widget'); if (code) addWidgetFromModule(code) }
const onDrop = (e, target) => {
  e.preventDefault()
  const from = dragId.value
  dragOverId.value = null
  dragId.value = null
  if (!from || from === target.id) return
  const list = [...layout.value]
  const fromIdx = list.findIndex((x) => x.id === from)
  const toIdx = list.findIndex((x) => x.id === target.id)
  if (fromIdx < 0 || toIdx < 0) return
  const [moved] = list.splice(fromIdx, 1)
  list.splice(toIdx, 0, moved)
  applyLayout(list)
}
const onDragEnd = () => { dragId.value = null; dragOverId.value = null }

// 调整大小:按下右下角手柄后横向拖拽,把卡片列宽吸附到 4/6/8/12 档
const onResizeStart = (e, w) => {
  const card = e.currentTarget.closest('.gc-card')
  if (!card) return
  const grid = card.parentElement
  const gridW = grid.getBoundingClientRect().width
  const colW = (gridW - 11 * GAP) / 12
  resizeState.value = {
    id: w.id,
    startX: e.clientX,
    startY: e.clientY,
    startWidth: card.getBoundingClientRect().width,
    startHeight: card.getBoundingClientRect().height,
    colW,
    currentSpan: w.span,
    currentRow: w.row,
  }
  resizing.value = true
  window.addEventListener('mousemove', onResizeMove)
  window.addEventListener('mouseup', onResizeUp)
}
const onResizeMove = (e) => {
  const s = resizeState.value
  if (!s) return
  const targetW = s.startWidth + (e.clientX - s.startX)
  const targetH = s.startHeight + (e.clientY - s.startY)
  let bestSpan = s.currentSpan
  let bestRow = s.currentRow
  let bestDist = Infinity
  for (const span of ALLOWED_SPANS) {
    const wpx = span * s.colW + (span - 1) * GAP
    const d = Math.abs(wpx - targetW)
    if (d < bestDist) { bestDist = d; bestSpan = span }
  }
  bestDist = Infinity
  for (const row of ALLOWED_ROWS) {
    const hpx = row * ROW_H + (row - 1) * GAP
    const d = Math.abs(hpx - targetH)
    if (d < bestDist) { bestDist = d; bestRow = row }
  }
  if (bestSpan !== s.currentSpan || bestRow !== s.currentRow) {
    s.currentSpan = bestSpan
    s.currentRow = bestRow
    const i = layout.value.findIndex((x) => x.id === s.id)
    if (i >= 0) { layout.value[i] = { ...layout.value[i], span: bestSpan, row: bestRow }; saveLayout() }
  }
}
const onResizeUp = () => {
  resizing.value = false
  resizeState.value = null
  window.removeEventListener('mousemove', onResizeMove)
  window.removeEventListener('mouseup', onResizeUp)
}
onBeforeUnmount(() => { window.removeEventListener('mousemove', onResizeMove); window.removeEventListener('mouseup', onResizeUp) })

// ========== 内容丰富度(分两维,面积相关) ==========
// 纵向条数看「行数 row」:行越多数据条数越多(窄而高 → 纵向数据多)
// 横向丰富度看「列宽 span」:越宽横向信息越丰富(宽而矮 → 宽幅数据)
// 两维同时放大 → 横纵都丰富;同时缩小 → 横纵都缩紧
const vTier = (w) => ({ 2: 'S', 3: 'M', 4: 'L', 6: 'XL' }[w.row] || 'M')
const hTier = (w) => ({ 4: 'S', 6: 'M', 8: 'L', 12: 'XL' }[w.span] || 'M')

const nFeed = (w) => ({ S: 2, M: 4, L: 6, XL: 9 }[vTier(w)] ?? 3)
const nTask = (w) => ({ S: 2, M: 3, L: 5, XL: 8 }[vTier(w)] ?? 3)
const nWish = (w) => ({ S: 2, M: 4, L: 6, XL: 10 }[vTier(w)] ?? 3)
const nReminder = (w) => ({ S: 2, M: 4, L: 6, XL: 10 }[vTier(w)] ?? 3)
const nItem = (w) => ({ S: 1, M: 2, L: 3, XL: 5 }[vTier(w)] ?? 2)
const nAnni = (w) => ({ S: 1, M: 2, L: 3, XL: 5 }[vTier(w)] ?? 1)

// 照片:横向列数看 span,纵向行数看 row(总数 = 列 × 行)
const photosCols = (w) => (hTier(w) === 'XL' ? 6 : hTier(w) === 'L' ? 4 : 3)
const photosRows = (w) => ({ S: 1, M: 2, L: 2, XL: 3 }[vTier(w)] ?? 1)
const nPhotos = (w) => photosCols(w) * photosRows(w)

// 横向丰富度:天气预报/指标、收支明细看 span
const showWeatherForecast = (w) => hTier(w) !== 'S' && forecast.value.length > 0
const showWeatherMetrics = (w) => hTier(w) === 'XL'
const showFinanceDetail = (w) => hTier(w) !== 'S'

// ========== 数据 ==========
const feeds = ref([])
const bookSummary = ref({ income: 0, expense: 0, balance: 0 })
const items = ref([])
const tasks = ref([])
const wishes = ref([])
const reminders = ref([])
const photos = computed(() => appStore.photos || [])
const balance = computed(() => bookSummary.value?.balance ?? 0)

onMounted(async () => {
  const [feed, book, it, task, wish, rem] = await Promise.all([
    publicApi.getFeed(20).catch(() => []),
    bookApi.summary().catch(() => null),
    itemApi.list({}).catch(() => []),
    taskApi.list().catch(() => []),
    wishApi.list().catch(() => []),
    reminderApi.list().catch(() => []),
  ])
  feeds.value = feed || []
  if (book) bookSummary.value = book
  items.value = (it || []).slice(0, 4)
  tasks.value = (Array.isArray(task) ? task : (task?.records || [])).filter((t) => t.status !== 'CANCELLED')
  wishes.value = Array.isArray(wish) ? wish : []
  reminders.value = (Array.isArray(rem) ? rem : []).filter((r) => r.done !== 1)
})

const weatherCity = computed(() => sunLight?.weather?.value?.city || appStore.familyName || '杭州')
const weatherTemp = computed(() => sunLight?.weather?.value?.temp ?? 24)
const weatherText = computed(() => sunLight?.weather?.value?.text || (sunLight?.weather?.value?.condition === 'cloud' ? '多云' : '晴 · 白云缓移'))
const weatherSub = computed(() => {
  const t = sunLight?.weather?.value?.temp
  if (t != null) return t >= 28 ? '仿佛窗外就是午后阳光' : t >= 15 ? '不冷不热，正好在家' : '屋里有灯，心里就暖'
  return ''
})
const weatherDetail = computed(() => sunLight?.weatherDetail?.value)
const todayHigh = computed(() => weatherDetail.value?.daily?.[0]?.tempMax ?? null)
const todayLow = computed(() => weatherDetail.value?.daily?.[0]?.tempMin ?? null)
const forecast = computed(() => (weatherDetail.value?.daily || []).slice(1, 4))
const fcDate = (d) => { const dt = new Date(d); return `${dt.getMonth() + 1}/${dt.getDate()}` }
// 巨大档关键指标(湿度/风力/PM2.5;缺数据显示 —)
const humidityText = computed(() => { const h = weatherDetail.value?.nowFull?.humidity; return h != null ? `${h}%` : '—' })
const windText = computed(() => { const f = weatherDetail.value?.nowFull; if (!f) return '—'; const dir = f.windDir || ''; const scale = f.windScale != null ? `${f.windScale} 级` : ''; return (dir || scale) ? `${dir} ${scale}`.trim() : '—' })
const pm25Text = computed(() => { const p = weatherDetail.value?.air?.pm2p5; return p != null ? `${p}` : '—' })

const FEED_ICON = { blog: '📝', diary: '📖', photo: '📷', video: '🎬', wish: '🎁', task: '🎯', recipe: '🍳', book: '📚' }
const FEED_ROUTES = { blog: '/blog', diary: '/diary', photo: '/album', video: '/cinema', wish: '/wish', task: '/task', recipe: '/kitchen', book: '/library' }
const feedIcon = (type) => FEED_ICON[type] || '✨'
const feedSummary = (f) => {
  if (f.type === 'blog') return f.title || ''
  if (f.type === 'diary') return (f.content || '').slice(0, 40)
  if (f.type === 'photo') return `${f.count || 0} 张照片`
  if (f.type === 'video') return `上传了影片:${f.title || ''}`
  if (f.type === 'wish') return f.status === 'ACHIEVED' ? `实现了愿望:${f.title || ''}` : `许下愿望:${f.title || ''}`
  if (f.type === 'task') return `发布任务:${f.title || ''}`
  if (f.type === 'recipe') return `分享菜谱:${f.title || ''}`
  if (f.type === 'book') return `上架图书:《${f.title || ''}》`
  return ''
}
const goFeed = (f) => { if (f.type === 'blog' && f.id) router.push(`/blog/${f.id}`); else if (FEED_ROUTES[f.type]) router.push(FEED_ROUTES[f.type]) }
const fmt = (n) => (Number(n) || 0).toFixed(2)

// 任务枚举映射(与 sys_dict_item 一致:OPEN/IN_PROGRESS/REVIEW/DONE/CANCELLED;奖励 NONE/POINTS/ITEM)
const TASK_STATUS_LABEL = { OPEN: '待领取', IN_PROGRESS: '进行中', REVIEW: '待确认', DONE: '已完成', CANCELLED: '已取消' }
const TASK_REWARD_ICON = { NONE: '⭕', POINTS: '🎁', ITEM: '📦' }
const taskStatusLabel = (s) => TASK_STATUS_LABEL[s] || ''
const taskIcon = (t) => TASK_REWARD_ICON[t] || '⭕'

const greeting = computed(() => {
  const h = new Date().getHours()
  const dawn = themeStore.mode === 'dawn'
  if (dawn) {
    if (h < 6) return '凌晨 · 好梦'
    if (h < 12) return '早安'
    if (h < 18) return '午后'
    return '傍晚'
  }
  return '晚上好 · 家人都在'
})
</script>

<style>
.gc-home { color: var(--color-text); }

.gc-main-head { display: flex; align-items: flex-end; justify-content: space-between; margin-bottom: 20px; }
.gc-h2 { font-size: 20px; margin: 0; font-weight: 650; color: var(--color-text); }
.gc-subline { font-size: 12.5px; color: var(--color-text-tertiary); margin-top: 3px; }
.gc-head-btns { display: flex; gap: 8px; }

.gc-grid { display: grid; grid-template-columns: repeat(12, 1fr); grid-auto-rows: 80px; gap: 16px; }
.gc-c12 { grid-column: span 12; }
.gc-c8 { grid-column: span 8; }
.gc-c6 { grid-column: span 6; }
.gc-c4 { grid-column: span 4; }
.gc-r2 { grid-row: span 2; }
.gc-r3 { grid-row: span 3; }
.gc-r4 { grid-row: span 4; }
.gc-r6 { grid-row: span 6; }

/* 卡片 = 家具块 */
.gc-card { background: var(--color-card); border: 1px solid var(--color-border); border-radius: 16px;
  box-shadow: var(--shadow); padding: 18px; position: relative; overflow: hidden; transition: box-shadow .35s ease, transform .35s ease; }
.gc-card:hover { box-shadow: var(--shadow-hover); }
.gc-card-h3 { font-size: 13px; font-weight: 650; margin: 0 0 12px; color: var(--color-text); display: flex; align-items: center; justify-content: space-between; }
.gc-muted { font-size: 11.5px; color: var(--color-text-tertiary); }
.gc-val-lg { font-size: 16px; font-weight: 650; color: var(--color-brand); margin-bottom: 4px; }
.gc-flex-baseline { display: flex; justify-content: space-between; align-items: baseline; }
.gc-ctrl-row { display: flex; gap: 8px; margin: 4px 0 12px; flex-wrap: wrap; }
.gc-empty { font-size: 12px; color: var(--color-text-tertiary); padding: 20px 0; text-align: center; }
.gc-empty-link { cursor: pointer; }

/* 编辑模式 */
.gc-card-edit { border-style: dashed; border-color: var(--color-accent, var(--color-brand)); }
.gc-card-dragover { border-color: var(--color-brand); box-shadow: 0 0 0 3px rgba(var(--color-brand-rgb), .18), var(--shadow-hover); }
.gc-card-link { cursor: pointer; }
.gc-overflow { overflow: hidden; }
.gc-del { position: absolute; top: 10px; right: 10px; z-index: 2; width: 24px; height: 24px; border-radius: 8px; border: none;
  display: grid; place-items: center; cursor: pointer; font-size: 12px; line-height: 1;
  background: var(--color-card-2); color: var(--color-text-secondary); box-shadow: var(--shadow); }
.gc-del:hover { background: var(--color-accent, var(--color-brand)); color: var(--color-card); }
.gc-grip { position: absolute; top: 10px; right: 42px; z-index: 2; width: 22px; height: 24px; border-radius: 8px; display: grid; place-items: center;
  cursor: grab; font-size: 13px; letter-spacing: -2px; color: var(--color-text-tertiary); user-select: none; }
.gc-grip:active { cursor: grabbing; }
.gc-resize { position: absolute; right: 0; bottom: 0; z-index: 2; width: 20px; height: 20px; cursor: nwse-resize; }
.gc-resize::after { content: ''; position: absolute; right: 5px; bottom: 5px; width: 8px; height: 8px; border-right: 2px solid var(--color-text-tertiary); border-bottom: 2px solid var(--color-text-tertiary); border-bottom-right-radius: 2px; }
.gc-edit-hint { font-size: 12px; color: var(--color-text-secondary); background: var(--color-card-2); border: 1px dashed var(--color-border);
  border-radius: 10px; padding: 8px 14px; margin: -8px 0 16px; }

/* 照片拼贴 */
.gc-photos { display: grid; grid-template-columns: repeat(3, 1fr); gap: 6px; cursor: pointer; }
.gc-photo { width: 100%; height: 64px; object-fit: cover; border-radius: 8px; background: var(--color-line); transition: transform .25s ease, opacity .25s ease; }
.gc-photos:hover .gc-photo { opacity: .92; }
.gc-photo:hover { transform: scale(1.04); opacity: 1; }

/* 添加组件托盘 */
.gc-add-tray { margin-top: 16px; padding: 14px 16px; border-radius: 16px; background: var(--color-card); border: 1px dashed var(--color-border);
  display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.gc-add-label { font-size: 12px; font-weight: 650; color: var(--color-text-tertiary); margin-right: 4px; }
.gc-add-item { display: inline-flex; align-items: center; gap: 6px; padding: 7px 13px; border-radius: 10px; cursor: pointer;
  font-size: 12.5px; border: 1px solid var(--color-border); background: var(--color-line); color: var(--color-text-secondary); transition: .2s; }
.gc-add-item:hover { background: var(--color-card-2); color: var(--color-text); border-color: var(--color-brand); }
.gc-add-icon { font-size: 14px; }

/* 快捷入口卡片 */
.gc-link-body { display: flex; align-items: center; gap: 10px; padding: 10px 4px 4px; cursor: pointer; }
.gc-link-icon { width: 42px; height: 42px; border-radius: 12px; display: grid; place-items: center; font-size: 20px; background: var(--color-line); flex-shrink: 0; }
.gc-link-text { font-size: 13px; font-weight: 600; color: var(--color-text); flex: 1; }
.gc-link-arrow { font-size: 16px; color: var(--color-text-tertiary); transition: transform .2s ease, color .2s ease; }
.gc-link-body:hover .gc-link-arrow { transform: translateX(3px); color: var(--color-brand); }

/* 天气活窗 */
.gc-glass { position: relative; height: 230px; border-radius: 12px; overflow: hidden; cursor: pointer;
  background: linear-gradient(160deg, var(--color-bg-2), var(--color-card-2));
  box-shadow: inset 0 0 0 1px var(--color-line), inset 0 14px 40px rgba(122, 90, 60, .08); }
/* 天气卡在固定行高网格内占满剩余高度(8 列 × 4 行基准) */
.gc-weather { display: flex; flex-direction: column; }
.gc-weather .gc-glass { flex: 1; height: auto; min-height: 120px; }
.gc-glass::before { content: ""; position: absolute; inset: 0;
  background: radial-gradient(360px 200px at 74% 18%, var(--glow-warm), transparent 62%); }
.gc-glass::after { content: ""; position: absolute; left: 14%; top: -4%; width: 46%; height: 110%;
  background: url("data:image/svg+xml,%3Csvg width='340' height='760' xmlns='http://www.w3.org/2000/svg'%3E%3Cdefs%3E%3ClinearGradient id='b' x1='0' y1='0' x2='1' y2='1'%3E%3Cstop offset='0' stop-color='%23FFE9C4' stop-opacity='.38'/%3E%3Cstop offset='.55' stop-color='%23FFDDA6' stop-opacity='.13'/%3E%3Cstop offset='1' stop-color='%23FFDDA6' stop-opacity='0'/%3E%3C/linearGradient%3E%3C/defs%3E%3Crect width='340' height='760' fill='url(%23b)' transform='rotate(14 170 380)'/%3E%3C/svg%3E");
  background-size: 100% 100%; mix-blend-mode: screen; opacity: .8; transform: rotate(10deg); }
html.dark .gc-glass::after { opacity: .5; }
.gc-win-glow { position: absolute; top: 16px; right: 20px; width: 130px; height: 130px; border-radius: 50%;
  background: radial-gradient(circle, var(--glow-warm), transparent 66%); filter: blur(20px); }
.gc-win-dust { width: 4px; height: 4px; border-radius: 50%; background: #FFF0D0; position: absolute; left: 6%; top: 6%;
  filter: blur(.3px); opacity: 0; animation: gcWd 13s ease-in-out infinite; box-shadow: 0 0 6px 1px rgba(255, 236, 196, .8); }
@keyframes gcWd {
  0% { transform: translate(0, 0) rotate(0); opacity: 0; }
  14% { opacity: .9; }
  36% { transform: translate(120px, 70px) rotate(160deg); opacity: 1; }
  58% { transform: translate(40px, 150px) rotate(280deg); opacity: .7; }
  80% { transform: translate(150px, 210px) rotate(420deg); opacity: .3; }
  100% { transform: translate(90px, 250px); opacity: 0; }
}
.gc-win-info { position: absolute; left: 22px; bottom: 22px; color: var(--color-text); }
.gc-temp { font-size: 40px; font-weight: 650; line-height: 1; letter-spacing: -1px; }
.gc-c { font-size: 18px; vertical-align: top; }
.gc-we { font-size: 14px; font-weight: 600; margin-top: 6px; }
.gc-st { font-size: 12px; color: var(--color-text-secondary); margin-top: 3px; }
.gc-win-bg { position: absolute; inset: 0; background-size: cover; background-position: center; opacity: .5; }
.gc-hilo { font-size: 13px; font-weight: 600; color: var(--color-text-secondary); margin-left: 12px; }
.gc-forecast { position: absolute; right: 16px; bottom: 16px; display: flex; gap: 14px; }
.gc-fc-item { display: flex; flex-direction: column; align-items: center; gap: 3px; font-size: 11.5px; color: var(--color-text-secondary); }
.gc-fc-range { font-variant-numeric: tabular-nums; font-weight: 600; }
.gc-win-metrics { position: absolute; top: 14px; left: 18px; display: flex; gap: 12px; font-size: 11.5px; color: var(--color-text-secondary); }

/* 按钮(扩展 primary/accent) */
.gc-btn.primary { background: var(--color-brand); border-color: var(--color-brand); color: var(--color-card); box-shadow: var(--shadow); }
.gc-btn.primary:hover { background: var(--color-brand-hover); }
.gc-btn.accent { background: var(--color-accent); border-color: var(--color-accent); color: #FFF7F0; box-shadow: var(--shadow); }

/* 标签 */
.gc-tag { display: inline-flex; align-items: center; gap: 4px; padding: 4px 11px; border-radius: 9px; font-size: 12px; background: var(--color-line); color: var(--color-text-secondary); }
.gc-tag.acc { background: var(--color-accent); color: #FFF7F0; }
.gc-tag.grn { background: var(--color-green); color: var(--color-card); }
.gc-tag.pri { background: var(--color-brand); color: var(--color-card); }

/* 进度条 */
.gc-meter { height: 8px; border-radius: 6px; background: var(--color-line); overflow: hidden; margin-top: 8px; }
.gc-meter > i { display: block; height: 100%; background: linear-gradient(90deg, var(--color-brand), var(--color-accent)); border-radius: 6px; }
.gc-anni-more { margin-top: 8px; }

/* 收支 */
.gc-fin-row { display: flex; gap: 14px; margin-top: 10px; font-size: 12.5px; }
.gc-fin { font-variant-numeric: tabular-nums; }
.gc-fin.in { color: var(--color-green); }
.gc-fin.out { color: var(--color-accent); }

/* 列表 */
.gc-list .gc-row { display: flex; align-items: center; gap: 8px; padding: 9px 0; border-bottom: 1px solid var(--color-line); font-size: 13px; cursor: pointer; }
.gc-list .gc-row:last-child { border-bottom: none; }
.gc-list .gc-row .gc-muted { margin-left: auto; text-align: right; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 45%; }
.gc-ic { width: 26px; height: 26px; border-radius: 8px; display: grid; place-items: center; font-size: 13px; background: var(--color-line); flex-shrink: 0; }
.gc-val { font-weight: 650; color: var(--color-brand); }
.gc-val.done { color: var(--color-text-tertiary); text-decoration: line-through; }

/* 编辑态下卡片内部禁止点击/文本选择 */
.gc-editing .gc-card { user-select: none; }

/* 过渡 */
.gc-fade-enter-active, .gc-fade-leave-active { transition: opacity .25s ease; }
.gc-fade-enter-from, .gc-fade-leave-to { opacity: 0; }

@media (max-width: 880px) {
  .gc-grid { grid-auto-rows: auto; }
  .gc-c4, .gc-c6, .gc-c8, .gc-c12 { grid-column: span 12; }
  .gc-r2, .gc-r3, .gc-r4, .gc-r6 { grid-row: auto; }
}
</style>
