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

    <div class="gc-grid" ref="gridEl" @dragover="onReorderDragOver" @drop="onReorderDrop">
      <div
        v-for="w in displayWidgets"
        :key="w.id"
        class="gc-card"
        :data-wid="w.id"
        :class="[`gc-c${w.span}`, `gc-r${w.row}`, { 'gc-weather': w.id === 'weather', 'gc-overflow': w.id === 'weather', 'gc-item-card': w.id === 'item', 'gc-photos': w.id === 'photos', 'gc-card-edit': editMode, 'gc-card-link': isLinkCard(w), 'gc-card-preview': w.kind === 'preview', 'gc-card-dragging': reorderPreview && reorderPreview.id === w.id }]"
        :style="cardStyle(w)"
        :draggable="editMode && w.kind !== 'preview'"
        @dragstart="onDragStart($event, w)"
        @dragover.prevent
        @dragend="onDragEnd"
        @click="onCardClick(w)"
      >
        <!-- 编辑手柄 -->
        <template v-if="editMode && w.kind !== 'preview'">
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
              <span class="gc-ic" :style="feedChipStyle(f.type)">{{ feedIcon(f.type) }}</span>
              <span class="gc-val">{{ f.authorName || '家人' }}</span>
              <span class="gc-muted">{{ feedSummary(f) }}</span>
            </div>
          </div>
          <div v-else class="gc-empty">暂无动态</div>
        </template>

        <!-- 照片(暖居卡牌堆轮播) -->
        <template v-else-if="w.id === 'photos'">
          <h3 class="gc-card-h3">照片<button class="gc-more" @click.stop="!editMode && $router.push('/album')">相册 →</button></h3>
          <div v-if="photos.length" class="gc-photo-stack" @mouseenter="photoHover = true" @mouseleave="photoHover = false" @click="!editMode && advancePhotos()">
            <div v-for="(p, i) in stackCards" :key="p.id" class="gc-photo-pcard" :class="{ 'gc-photo-hovered': hoveredCard === i }" :style="pcardStyle(i)" @mouseenter="onCardEnter(i)" @mouseleave="onCardLeave()">
              <img :src="p.url" :alt="p.description || ''" loading="lazy" />
            </div>
            <div class="gc-photo-meta">
              <span class="gc-photo-cap">{{ topPhoto?.description || '家庭照片' }}</span>
              <span class="gc-photo-count">{{ photoIndex + 1 }} / {{ photos.length }}</span>
            </div>
            <button v-if="photos.length > 1 && !editMode" class="gc-photo-nav gc-photo-prev" @click.stop="prevPhotos()" aria-label="上一张">
              <svg viewBox="0 0 24 24"><polyline points="15 18 9 12 15 6" /></svg>
            </button>
            <button v-if="photos.length > 1 && !editMode" class="gc-photo-nav gc-photo-next" @click.stop="advancePhotos()" aria-label="下一张">
              <svg viewBox="0 0 24 24"><polyline points="9 18 15 12 9 6" /></svg>
            </button>
          </div>
          <div v-else class="gc-empty gc-empty-link" @click="!editMode && $router.push('/album')">去相册添加家庭照片</div>
        </template>

        <!-- 纪念日 -->
        <template v-else-if="w.id === 'anni'">
          <h3 class="gc-card-h3">纪念日</h3>
          <template v-if="anniversaries.length">
            <div class="gc-val-lg">{{ anniversaries[0].label }}</div>
            <div class="gc-muted">{{ anniversaries[0].date }} · 还有 <span class="gc-days">{{ anniversaries[0].days }}</span> 天</div>
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
          <h3 class="gc-card-h3">本月收支<span class="gc-muted">{{ bookSummary.month || '' }}</span></h3>
          <div class="gc-flex-baseline">
            <div class="gc-val-lg">¥ {{ fmt(balance) }}</div>
            <div class="gc-muted">{{ bookSummary.count ?? 0 }} 笔</div>
          </div>
          <div class="gc-fin-row">
            <span class="gc-fin in">+{{ fmt(bookSummary.income) }}</span>
            <span class="gc-fin out">-{{ fmt(bookSummary.expense) }}</span>
          </div>
          <div v-if="financeTotal > 0" class="gc-fin-bar">
            <i class="in" :style="{ width: incomePct + '%' }"></i>
            <i class="out" :style="{ width: (100 - incomePct) + '%' }"></i>
          </div>
          <div v-else class="gc-empty" style="padding:8px 0">本月还没有记账</div>
        </template>

        <!-- 寻物(搜索 + 语音 + 户型图预览 / 列表) -->
        <template v-else-if="w.id === 'item'">
          <h3 class="gc-card-h3">寻物<span class="gc-muted">{{ itemHouse?.name || '' }}</span><button class="gc-more" @click.stop="!editMode && $router.push('/item')">管理 →</button></h3>
          <div class="gc-item-search">
            <input v-model="itemKeyword" class="gc-item-input" :placeholder="itemSearching ? '✨ AI 语义找物中…' : '搜索物品 / 位置 / 俗称…'" @keyup.enter="onItemSearch" @input="!itemKeyword && clearItemSearch()" />
            <button class="gc-btn ghost sm gc-item-voice" :class="{ recording: itemVoiceRecording }" :disabled="itemVoiceProcessing" title="语音找物" @click="toggleItemVoice">🎤</button>
          </div>

          <!-- 高行(≥3 行):户型图预览 + 命中高亮 -->
          <template v-if="itemShowFloorPlan(w)">
            <div class="gc-item-plan" @click="!editMode && $router.push('/item')">
              <svg v-if="itemFloorPlanView" :viewBox="itemFloorPlanView.viewBox" preserveAspectRatio="xMidYMid meet" class="gc-item-svg">
                <image v-if="itemFloorPlanView.imageUrl" :href="itemFloorPlanView.imageUrl" :transform="itemFloorPlanView.imgTransform" class="gc-item-bg" />
                <g v-for="r in itemFloorPlanView.rooms" :key="r.id">
                  <polygon :points="r.points" class="gc-item-room" :class="{ hit: itemHitRoomIds.includes(r.id) }" />
                  <text :x="r.cx" :y="r.cy" class="gc-item-room-label">{{ r.name }}</text>
                </g>
                <g v-for="f in itemFloorPlanView.furnitures" :key="f.id">
                  <rect :x="f.x" :y="f.y" :width="f.w" :height="f.h" rx="2" class="gc-item-furn" />
                  <text v-if="f.w > 26 && f.h > 16" :x="f.x + f.w / 2" :y="f.y + f.h / 2" class="gc-item-furn-label">{{ f.name }}</text>
                </g>
                <g v-for="it in itemFloorPlanView.items" :key="it.id">
                  <circle :cx="it.ax" :cy="it.ay" r="5" class="gc-item-dot" :class="{ hit: itemHitIds.includes(it.id) }" />
                  <text :x="it.ax" :y="it.ay - 9" class="gc-item-dot-label">{{ it.name }}</text>
                </g>
              </svg>
              <div v-else class="gc-empty gc-empty-link">去登记户型图与物品</div>
              <div v-if="itemResults.length" class="gc-item-results" @click.stop>
                <div v-for="it in itemResults.slice(0, 5)" :key="it.id" class="gc-row" @click="!editMode && $router.push('/item')">
                  <span class="gc-ic">📦</span><span class="gc-val">{{ it.name }}</span><span class="gc-muted">{{ itemPathOf(it) }}</span>
                </div>
              </div>
              <div v-else-if="itemSearched && !itemSearching" class="gc-item-nohit">没找到，换个说法试试</div>
            </div>
          </template>

          <!-- 矮行(<3 行):列表式搜索结果 -->
          <template v-else>
            <div v-if="itemResults.length" class="gc-list">
              <div v-for="it in itemResults.slice(0, nItem(w))" :key="it.id" class="gc-row" @click="!editMode && $router.push('/item')">
                <span class="gc-ic">📦</span><span class="gc-val">{{ it.name }}</span><span class="gc-muted">{{ itemPathOf(it) }}</span>
              </div>
            </div>
            <div v-else-if="itemSearched && !itemSearching" class="gc-empty">未找到相关物品</div>
            <div v-else-if="items.length" class="gc-list">
              <div v-for="it in items.slice(0, nItem(w))" :key="it.id" class="gc-row" @click="!editMode && $router.push('/item')">
                <span class="gc-ic">📦</span><span class="gc-val">{{ it.name }}</span><span class="gc-tag">{{ it.room_name || it.house_name || '—' }}</span>
              </div>
            </div>
            <div v-else class="gc-empty gc-empty-link" @click="!editMode && $router.push('/item')">尚未登记物品 · 去添加</div>
          </template>
        </template>

        <!-- 悬赏任务 -->
        <template v-else-if="w.id === 'task'">
          <h3 class="gc-card-h3">悬赏任务</h3>
          <div v-if="tasks.length" class="gc-list">
            <div v-for="t in tasks.slice(0, nTask(w))" :key="t.id" class="gc-row" @click="!editMode && $router.push('/task')">
              <span class="gc-ic">{{ taskIcon(t.rewardType) }}</span>
              <span class="gc-val">{{ t.title }}</span>
              <span class="gc-tag" :class="TASK_STATUS_TAG[t.status]">{{ taskStatusLabel(t.status) }}</span>
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
              <span v-if="wi.status === 'ACHIEVED'" class="gc-tag grn">已实现</span>
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

        <!-- 拖入预览占位(4 列 × 2 行,作为真实网格项参与推挤) -->
        <template v-else-if="w.kind === 'preview'">
          <div class="gc-preview-body">
            <span class="gc-preview-icon">{{ w.icon }}</span>
            <span class="gc-preview-label">{{ w.label }}</span>
            <span class="gc-preview-hint">{{ w.hint || '松手放置' }}</span>
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
import { computed, inject, onMounted, onBeforeUnmount, ref, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'
import { useWarmWidgetDrag } from '@/utils/widgetDragData'
import { addedCodes } from '@/utils/warmHomeShared'
import { publicApi, bookApi, itemApi, taskApi, wishApi, reminderApi, aiApi } from '@/api'
import { useVoiceRecorder } from '@/composables/useVoiceRecorder'

const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()
const sunLight = inject(SUN_LIGHT_KEY)

defineProps({ weatherBg: { type: String, default: '' } })

const familyName = computed(() => appStore.familyName)
const memberCount = computed(() => appStore.stats.memberCount || 0)
const anniversaries = computed(() => appStore.stats.upcomingEvents || [])

// ========== 组件注册表 + 布局(localStorage 持久化) ==========
// 9 个富组件(有专属内容模板);span=8/4 对应 12 列栅格分栏
// chip = 该卡的色相锚点(--blob-N:1 陶土 / 2 暖沙 / 3 鼠尾草 / 4 暖米 / 5 暖木),标题竖条与列表图标芯片都取此色
const WIDGETS = [
  { id: 'weather', label: '天气活窗', icon: '🌤', span: 8, chip: 1 },
  { id: 'feed', label: '家人动态', icon: '👥', span: 4, chip: 3 },
  { id: 'photos', label: '照片', icon: '📷', span: 4, chip: 2 },
  { id: 'anni', label: '纪念日', icon: '🎂', span: 4, chip: 1 },
  { id: 'finance', label: '本月收支', icon: '💰', span: 4, chip: 3 },
  { id: 'item', label: '寻物', icon: '📦', span: 4, chip: 5 },
  { id: 'task', label: '悬赏任务', icon: '🎯', span: 4, chip: 5 },
  { id: 'wish', label: '愿望单', icon: '⭐', span: 4, chip: 3 },
  { id: 'reminder', label: '今日提醒', icon: '🔔', span: 4, chip: 1 },
]
const WIDGET_BY_ID = Object.fromEntries(WIDGETS.map((w) => [w.id, w]))
// 卡片色相锚点 → 内联 --chip,供 .gc-card-h3::before 与 .gc-ic 取色(后代继承)
const cardStyle = (w) => ({ '--chip': `var(--blob-${w.chip || 1})` })

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
  plant: { title: '花园', path: '/plant', icon: '🌱' },
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
    return { id, kind: 'link', code: id.slice(5), label: meta.title, icon: meta.icon, path: meta.path, span: entry.span || 4, row, chip: 3 }
  }
  const base = WIDGET_BY_ID[id]
  if (!base) return null
  return { ...base, span: entry.span || base.span, row }
}

// 默认展示 6 张(照片/纪念日/收支 3 行、寻物整行户型图);其余富组件(task/wish/reminder)由托盘加入,其余模块经侧栏拖入为入口卡片
const DEFAULT_LAYOUT = [
  { id: 'weather', span: 8, row: 4 },
  { id: 'feed', span: 4, row: 4 },
  { id: 'photos', span: 4, row: 3 },
  { id: 'anni', span: 4, row: 3 },
  { id: 'finance', span: 4, row: 3 },
  { id: 'item', span: 12, row: 4 },
]
const STORAGE_KEY = 'ihomy:guangchen:home:v3'
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

// ========== 已添加模块集合(供侧栏在编辑模式标记「已拖入冲突」) ==========
const WIDGET_TO_MODULE = Object.fromEntries(Object.entries(MODULE_TO_WIDGET).map(([code, wid]) => [wid, code]))
const computeAddedCodes = () => {
  const codes = new Set()
  for (const e of layout.value) {
    const code = e.id.startsWith('link:') ? e.id.slice(5) : WIDGET_TO_MODULE[e.id]
    if (code) codes.add(code)
  }
  return codes
}
watch(layout, () => { addedCodes.value = computeAddedCodes() }, { deep: true, immediate: true })

// ========== 布局动画(FLIP:先量后放,统一驱动「变大缩小 / 邻居移动 / 回弹」) ==========
const gridEl = ref(null)
const flipAnims = new WeakMap()
const captureRects = () => {
  const m = new Map()
  gridEl.value?.querySelectorAll('.gc-card').forEach((c) => { if (c.dataset.wid) m.set(c.dataset.wid, c.getBoundingClientRect()) })
  return m
}
// 回弹缓动:末段轻微越过目标再回正(overshoot)
const FLIP_EASE = 'cubic-bezier(.34, 1.56, .64, 1)'
const flipLayout = (prev) => {
  const grid = gridEl.value
  if (!grid) return
  const moves = []
  grid.querySelectorAll('.gc-card').forEach((c) => {
    const id = c.dataset.wid
    if (!id) return
    const first = prev.get(id)
    if (!first) return
    const last = c.getBoundingClientRect()
    const dx = first.left - last.left
    const dy = first.top - last.top
    const dw = last.width ? first.width / last.width : 1
    const dh = last.height ? first.height / last.height : 1
    if (Math.abs(dx) < .5 && Math.abs(dy) < .5 && Math.abs(dw - 1) < .01 && Math.abs(dh - 1) < .01) return
    moves.push({ el: c, dx, dy, dw, dh })
  })
  if (!moves.length) return
  moves.forEach(({ el, dx, dy, dw, dh }) => {
    flipAnims.get(el)?.cancel()
    flipAnims.set(el, el.animate(
      [
        { transform: `translate(${dx}px, ${dy}px) scale(${dw}, ${dh})`, transformOrigin: 'top left' },
        { transform: 'translate(0, 0) scale(1, 1)', transformOrigin: 'top left' },
      ],
      { duration: 300, easing: FLIP_EASE }
    ))
  })
}

const applyLayout = (entries) => {
  const prev = captureRects()
  layout.value = entries.filter((e) => resolveWidget(e))
  saveLayout()
  nextTick(() => flipLayout(prev))
}
const addWidget = (id) => {
  if (layout.value.some((e) => e.id === id)) { ElMessage.info('该组件已在首页'); return }
  applyLayout([...layout.value, { id, span: defaultSpan(id), row: defaultRow(id) }])
  ElMessage.success(`已添加 ${resolveWidget({ id, span: defaultSpan(id), row: defaultRow(id) })?.label || ''} 组件`)
}
const removeWidget = (w) => { applyLayout(layout.value.filter((e) => e.id !== w.id)) }
const resetLayout = () => { layout.value = DEFAULT_LAYOUT.map((e) => ({ ...e })); saveLayout(); ElMessage.success('布局已重置') }

// ========== 编辑模式(复用 appStore.homeEditMode,与光尘同一开关) ==========
const editMode = computed(() => appStore.homeEditMode)
const canEdit = computed(() => userStore.isOwner)
const startEdit = () => appStore.toggleHomeEditMode()
const finishEdit = () => appStore.toggleHomeEditMode()

// 卡片点击:仅整卡可跳转的组件(weather/anni/finance);photos 点击翻牌、feed/task/wish/reminder/item 由内部行/按钮跳转
const LINK_IDS = ['weather', 'anni', 'finance']
const isLinkCard = (w) => LINK_IDS.includes(w.id)
const onCardClick = (w) => {
  if (editMode.value) return
  const dest = { weather: '/weather', anni: '/anniversary', finance: '/book' }[w.id]
  if (dest) router.push(dest)
}

// ========== 拖拽排序 + 调整大小(原生 HTML5 DnD 排序 + 右下角手柄拖拽改列宽) ==========
const dragId = ref(null)
const reorderPreview = ref(null) // { id, index, label, icon, span, row } —— 重排时在目标下标插入同尺寸占位
const resizing = ref(false)
const resizeState = ref(null)
const ROW_H = 80
const GAP = 16
const MAX_COLS = 12
const MAX_ROWS = 20
// 可吸附的列宽档(4/6/8/12)与行高档(2/3/4/6),与 .gc-c*/.gc-r* 样式类一一对应
const ALLOWED_SPANS = [4, 6, 8, 12]
const ALLOWED_ROWS = [2, 3, 4, 6]

const onDragStart = (e, w) => {
  if (resizing.value) { e.preventDefault(); return } // 调整大小时取消卡片原生拖拽
  dropPreview.value = null
  reorderPreview.value = null
  dragId.value = w.id
  e.dataTransfer.effectAllowed = 'move'
}

// ========== 侧栏拖入预览(4 列 × 2 行幽灵占位,作为真实网格项参与推挤) ==========
const { code: dragCode, label: dragLabel, onMove, onDrop: onWidgetDrop, setOverGrid } = useWarmWidgetDrag()
const dropPreview = ref(null) // { code, label, icon, index }
const displayWidgets = computed(() => {
  const list = [...widgets.value]
  const rp = reorderPreview.value
  const dp = dropPreview.value
  if (rp) {
    // 卡片重排:源卡片保留原位(降透明度表示"被拿起"),在目标下标插入同尺寸占位
    list.splice(Math.max(0, Math.min(rp.index, list.length)), 0, { id: '__reorder__', kind: 'preview', label: rp.label, icon: rp.icon, span: rp.span, row: rp.row, hint: '移到此处' })
  } else if (dp) {
    list.splice(Math.max(0, Math.min(dp.index, list.length)), 0, { id: '__preview__', kind: 'preview', label: dp.label, icon: dp.icon, span: 4, row: 2, hint: '松手放置' })
  }
  return list
})
const previewMeta = (code) => {
  const wid = MODULE_TO_WIDGET[code]
  if (wid) { const base = WIDGET_BY_ID[wid]; if (base) return { label: base.label, icon: base.icon } }
  const meta = MODULE_META[code]
  return meta ? { label: meta.title, icon: meta.icon } : null
}
// 由鼠标坐标推导插入下标(网格自动流,按视觉顺序逐项判断)
const insertionIndexAt = (x, y) => {
  const grid = gridEl.value
  if (!grid) return layout.value.length
  const cards = Array.from(grid.querySelectorAll('.gc-card')).filter((c) => {
    const wid = c.dataset.wid
    return wid && !wid.startsWith('__') && wid !== dragId.value // 排除占位幽灵与正在拖拽的源卡片
  })
  for (let i = 0; i < cards.length; i++) {
    const r = cards[i].getBoundingClientRect()
    const cx = r.left + r.width / 2
    const cy = r.top + r.height / 2
    if (y < cy) return i
    if (y >= r.top && y < r.bottom && x < cx) return i
  }
  return cards.length
}
const insertWidgetAt = (code, idx) => {
  const id = MODULE_TO_WIDGET[code] || `link:${code}`
  const resolved = resolveWidget({ id, span: defaultSpan(id), row: defaultRow(id) })
  if (!resolved) { dropPreview.value = null; return }
  if (layout.value.some((e) => e.id === id)) { ElMessage.info('该组件已在首页'); dropPreview.value = null; return }
  dropPreview.value = null
  const list = [...layout.value]
  list.splice(Math.max(0, Math.min(idx, list.length)), 0, { id, span: defaultSpan(id), row: defaultRow(id) })
  applyLayout(list)
  ElMessage.success(`已添加 ${resolved.label} 组件`)
}
// 拖拽移动(鼠标事件驱动,同步更新):进入网格 → 幽灵变形为卡片 + 占位推挤;离开 → 还原
const updateDragPreview = (nx, ny) => {
  if (!dragCode.value) return
  const r = gridEl.value?.getBoundingClientRect()
  const inside = !!(r && nx >= r.left && nx <= r.right && ny >= r.top && ny <= r.bottom)
  if (inside) {
    setOverGrid(true)
    const idx = insertionIndexAt(nx, ny)
    const cur = dropPreview.value
    if (!cur || cur.code !== dragCode.value || cur.index !== idx) {
      const prev = captureRects()
      const meta = previewMeta(dragCode.value)
      dropPreview.value = { code: dragCode.value, label: meta?.label || dragLabel.value || '新组件', icon: meta?.icon || '✨', index: idx }
      nextTick(() => flipLayout(prev))
    }
  } else {
    setOverGrid(false)
    if (dropPreview.value) { const prev = captureRects(); dropPreview.value = null; nextTick(() => flipLayout(prev)) }
  }
}
onMove((nx, ny) => updateDragPreview(nx, ny))
onWidgetDrop((c) => { if (dropPreview.value) insertWidgetAt(c, dropPreview.value.index) })

// 卡片重排预览:拖拽中在目标下标插入同尺寸占位(复用 insertIndex 计算 + FLIP 推挤)
const onReorderDragOver = (e) => {
  e.preventDefault()
  if (!dragId.value) return
  e.dataTransfer.dropEffect = 'move'
  const idx = insertionIndexAt(e.clientX, e.clientY)
  const cur = reorderPreview.value
  if (!cur || cur.index !== idx) {
    const prev = captureRects()
    const dragged = widgets.value.find((w) => w.id === dragId.value)
    reorderPreview.value = { id: dragId.value, index: idx, label: dragged?.label || '', icon: dragged?.icon || '✨', span: dragged?.span || 4, row: dragged?.row || 2 }
    nextTick(() => flipLayout(prev))
  }
}
const onReorderDrop = (e) => {
  e.preventDefault()
  const from = dragId.value
  if (!from) return
  const k = reorderPreview.value?.index
  dragId.value = null
  reorderPreview.value = null
  if (k == null) return
  const dragged = layout.value.find((x) => x.id === from)
  if (!dragged) return
  const others = layout.value.filter((x) => x.id !== from)
  others.splice(Math.max(0, Math.min(k, others.length)), 0, dragged)
  applyLayout(others)
}
const onDragEnd = () => { dragId.value = null; reorderPreview.value = null }

// 调整大小:按下右下角手柄拖拽,把卡片列宽吸附 4/6/8/12、行高吸附 2/3/4/6;
// 拖动中实时改 span/row,邻居经 CSS 栅格自动重排(实时预览),松手才 saveLayout 提交,拖回原位等于还原
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
    const prev = captureRects()
    s.currentSpan = bestSpan
    s.currentRow = bestRow
    const i = layout.value.findIndex((x) => x.id === s.id)
    if (i >= 0) { layout.value[i] = { ...layout.value[i], span: bestSpan, row: bestRow } } // 拖动中仅实时预览,不落库
    nextTick(() => flipLayout(prev)) // 吸附档变化时动画过渡(放大缩小 + 邻居让位 + 回弹)
  }
}
const onResizeUp = () => {
  resizing.value = false
  resizeState.value = null
  window.removeEventListener('mousemove', onResizeMove)
  window.removeEventListener('mouseup', onResizeUp)
  saveLayout() // 松手才提交最终布局(拖回原位则等于原布局,无副作用)
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
const nAnni = (w) => ({ S: 1, M: 3, L: 4, XL: 6 }[vTier(w)] ?? 1)

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
// 收支比例条
const financeTotal = computed(() => (Number(bookSummary.value?.income) || 0) + (Number(bookSummary.value?.expense) || 0))
const incomePct = computed(() => (financeTotal.value > 0 ? ((Number(bookSummary.value?.income) || 0) / financeTotal.value) * 100 : 0))

// ========== 照片卡牌堆(点击翻动 + 5s 自动轮播 + hover 摆正放大/暂停) ==========
const photoIndex = ref(0)
const photoHover = ref(false)
const hoveredCard = ref(-1) // 当前 hover 的照片卡索引(-1 = 无),hover 时该卡摆正放大(JS 追踪,正确处理扇形重叠)
const PHOTO_STACK_N = 4
const topPhoto = computed(() => photos.value[photoIndex.value] || null)
const stackCards = computed(() => {
  const arr = photos.value
  const n = arr.length
  if (!n) return []
  const out = []
  for (let i = 0; i < Math.min(PHOTO_STACK_N, n); i++) out.push(arr[(photoIndex.value + i) % n])
  return out
})
const pcardStyle = (i) => {
  const n = Math.min(PHOTO_STACK_N, photos.value.length)
  const mid = (n - 1) / 2
  return {
    '--dx': `${((i - mid) * 12).toFixed(1)}px`,
    '--dy': `${(Math.abs(i - mid) * 8).toFixed(1)}px`,
    '--rot': `${((i - mid) * 5).toFixed(1)}deg`,
    '--z': 10 - i,
  }
}
const onCardEnter = (i) => { hoveredCard.value = i }
const onCardLeave = () => { hoveredCard.value = -1 }
const advancePhotos = () => { if (photos.value.length > 1) photoIndex.value = (photoIndex.value + 1) % photos.value.length }
const prevPhotos = () => { if (photos.value.length > 1) photoIndex.value = (photoIndex.value - 1 + photos.value.length) % photos.value.length }
let photoTimer = null
const startPhotoTimer = () => { if (!photoTimer) photoTimer = setInterval(() => { if (!photoHover.value) advancePhotos() }, 5000) }
const stopPhotoTimer = () => { if (photoTimer) { clearInterval(photoTimer); photoTimer = null } }

// ========== 寻物(搜索 + 语音 + 户型图预览) ==========
const itemHouse = ref(null)
const itemFloorPlan = ref({ rooms: [], furnitures: [], items: [], imageUrl: null, scale: 100 })
const itemImgTransform = ref(null) // 底图变换 {x,y,k}
const itemImgSize = ref({ w: 0, h: 0 })
const itemKeyword = ref('')
const itemResults = ref([])
const itemSearched = ref(false)
const itemSearching = ref(false)
const { recording: itemVoiceRecording, start: itemVoiceStart, stop: itemVoiceStop } = useVoiceRecorder()
const itemVoiceProcessing = ref(false)
let itemVoiceTimer = null

const defaultFloorOfHouse = (house) => {
  const set = new Set()
  if (house?.floorPlans) {
    try { const fp = JSON.parse(house.floorPlans); Object.keys(fp).forEach((k) => { if (k !== 'floorOrder') set.add(Number(k)) }) } catch {}
  }
  if (set.has(1)) return 1
  if (set.size) return Math.max(...set)
  return 1
}
const loadItemFloorPlan = async (house) => {
  itemHouse.value = house
  itemImgTransform.value = null
  itemImgSize.value = { w: 0, h: 0 }
  if (!house) { itemFloorPlan.value = { rooms: [], furnitures: [], items: [], imageUrl: null, scale: 100 }; return }
  const floor = defaultFloorOfHouse(house)
  const data = await itemApi.floorPlan(house.id, floor).catch(() => null)
  itemFloorPlan.value = data || { rooms: [], furnitures: [], items: [], imageUrl: null, scale: 100 }
  if (house.floorPlans) {
    try { const cfg = JSON.parse(house.floorPlans)[floor]; if (cfg?.img) itemImgTransform.value = { ...cfg.img } } catch {}
  }
  if (data?.imageUrl) {
    const img = new Image()
    img.onload = () => { itemImgSize.value = { w: img.naturalWidth, h: img.naturalHeight } }
    img.src = data.imageUrl
  }
}
const itemShowFloorPlan = (w) => w.row >= 3
const itemPathOf = (it) => [it.house_name, it.room_name, it.furniture_name || it.position].filter(Boolean).join(' / ')
const itemHitIds = computed(() => itemResults.value.map((r) => r.id))
const itemHitRoomIds = computed(() => {
  const hit = new Set(itemHitIds.value)
  const ids = new Set()
  ;(itemFloorPlan.value.items || []).forEach((it) => { if (hit.has(it.id) && it.roomId != null) ids.add(Number(it.roomId)) })
  return [...ids]
})
const onItemSearch = async () => {
  const kw = (itemKeyword.value || '').trim()
  if (!kw) { clearItemSearch(); return }
  itemSearched.value = true
  itemResults.value = await itemApi.list({ keyword: kw }).catch(() => [])
  if (itemResults.value.length) return
  itemSearching.value = true
  try {
    const r = await itemApi.aiFind({ query: kw }).catch(() => null)
    if (r?.matches?.length) itemResults.value = r.matches
  } finally { itemSearching.value = false }
}
const clearItemSearch = () => { itemKeyword.value = ''; itemResults.value = []; itemSearched.value = false }
const toggleItemVoice = async () => {
  if (itemVoiceRecording.value) { await finishItemVoice(); return }
  if (itemVoiceProcessing.value) return
  try {
    await itemVoiceStart()
    clearTimeout(itemVoiceTimer)
    itemVoiceTimer = setTimeout(() => { if (itemVoiceRecording.value) finishItemVoice() }, 10000)
  } catch (e) { ElMessage.warning('麦克风不可用') }
}
const finishItemVoice = async () => {
  clearTimeout(itemVoiceTimer)
  if (!itemVoiceRecording.value) return
  itemVoiceProcessing.value = true
  try {
    const blob = await itemVoiceStop()
    const file = new File([blob], 'voice.wav', { type: 'audio/wav' })
    const r = await aiApi.transcribe(file, null)
    const text = (r.text || '').trim()
    if (!text) { ElMessage.warning('未识别到语音'); return }
    itemKeyword.value = text
    await onItemSearch()
  } catch (e) {} finally { itemVoiceProcessing.value = false }
}
// 户型图 SVG 视图模型:解析房间多边形/家具/物品绝对坐标,自适应包围盒
const itemFloorPlanView = computed(() => {
  const fp = itemFloorPlan.value
  const rooms = (fp.rooms || []).map((r) => {
    let poly = []
    try { poly = JSON.parse(r.geometry || '[]') } catch {}
    if (!Array.isArray(poly)) poly = []
    return { id: r.id, name: r.name, poly }
  }).filter((r) => r.poly.length >= 3)
  const hasFurn = (fp.furnitures || []).some((f) => f.x != null)
  if (!rooms.length && !hasFurn && !fp.imageUrl) return null

  const roomBox = {}
  const roomPts = []
  for (const r of rooms) {
    const xs = r.poly.map((p) => p.x), ys = r.poly.map((p) => p.y)
    const box = { minX: Math.min(...xs), minY: Math.min(...ys), maxX: Math.max(...xs), maxY: Math.max(...ys) }
    roomBox[r.id] = box
    roomPts.push({ id: r.id, name: r.name, points: r.poly.map((p) => `${p.x},${p.y}`).join(' '), cx: (box.minX + box.maxX) / 2, cy: (box.minY + box.maxY) / 2 })
  }
  const furnitures = (fp.furnitures || []).filter((f) => f.x != null).map((f) => ({ id: f.id, name: f.name, x: f.x, y: f.y, w: f.w, h: f.h }))
  const furnitureById = Object.fromEntries(furnitures.map((f) => [f.id, f]))
  const items = (fp.items || []).map((it) => {
    const fid = it.furnitureId, rid = it.roomId
    const relX = Number(it.relX ?? 0.5), relY = Number(it.relY ?? 0.5)
    let ax = null, ay = null
    if (fid != null && furnitureById[fid]) { const f = furnitureById[fid]; ax = f.x + relX * f.w; ay = f.y + relY * f.h }
    else if (rid != null && roomBox[rid]) { const b = roomBox[rid]; ax = b.minX + relX * (b.maxX - b.minX); ay = b.minY + relY * (b.maxY - b.minY) }
    return { id: it.id, name: it.name, ax, ay }
  }).filter((it) => it.ax != null)

  let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity
  const collect = (x, y) => { if (x < minX) minX = x; if (x > maxX) maxX = x; if (y < minY) minY = y; if (y > maxY) maxY = y }
  rooms.forEach((r) => r.poly.forEach((p) => collect(p.x, p.y)))
  furnitures.forEach((f) => { collect(f.x, f.y); collect(f.x + f.w, f.y + f.h) })
  items.forEach((it) => collect(it.ax, it.ay))
  if (fp.imageUrl && itemImgTransform.value && itemImgSize.value.w) {
    const t = itemImgTransform.value
    collect(t.x, t.y); collect(t.x + itemImgSize.value.w * t.k, t.y + itemImgSize.value.h * t.k)
  }
  if (!isFinite(minX)) { minX = 0; minY = 0; maxX = 100; maxY = 100 }
  const pad = 26
  return {
    rooms: roomPts, furnitures, items,
    imageUrl: fp.imageUrl || null,
    imgTransform: itemImgTransform.value ? `translate(${itemImgTransform.value.x},${itemImgTransform.value.y}) scale(${itemImgTransform.value.k})` : '',
    viewBox: `${minX - pad} ${minY - pad} ${(maxX - minX) + pad * 2} ${(maxY - minY) + pad * 2}`,
  }
})

onMounted(async () => {
  // 未登录(公开首页)只拉公开动态流:其余卡片数据都要登录态,直接跳过,
  // 别打一批注定 401 的请求(与 Home.vue 的 loadXxx 登录态守卫一致)
  const loggedIn = userStore.isLoggedIn
  const [feed, book, it, task, wish, rem, houses] = await Promise.all([
    publicApi.getFeed(20).catch(() => []),
    loggedIn ? bookApi.summary().catch(() => null) : Promise.resolve(null),
    loggedIn ? itemApi.list({}).catch(() => []) : Promise.resolve([]),
    loggedIn ? taskApi.list().catch(() => []) : Promise.resolve([]),
    loggedIn ? wishApi.list().catch(() => []) : Promise.resolve([]),
    loggedIn ? reminderApi.list().catch(() => []) : Promise.resolve([]),
    loggedIn ? itemApi.houses().catch(() => []) : Promise.resolve([]),
  ])
  feeds.value = feed || []
  if (book) bookSummary.value = book
  items.value = (it || []).slice(0, 8)
  tasks.value = (Array.isArray(task) ? task : (task?.records || [])).filter((t) => t.status !== 'CANCELLED')
  wishes.value = Array.isArray(wish) ? wish : []
  reminders.value = (Array.isArray(rem) ? rem : []).filter((r) => r.done !== 1)
  const h = Array.isArray(houses) ? houses : []
  if (h.length) await loadItemFloorPlan(h[0])
  startPhotoTimer()
})
onBeforeUnmount(() => { stopPhotoTimer(); if (itemVoiceRecording.value) itemVoiceStop() })

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
// 动态类型 → 图标芯片色相(博客陶土 / 影像暖沙 / 愿望鼠尾草 / 日记·任务暖木)
const FEED_CHIP = { blog: 1, diary: 5, photo: 2, video: 2, wish: 3, task: 5, recipe: 1, book: 3 }
const feedChipStyle = (type) => ({ '--chip': `var(--blob-${FEED_CHIP[type] || 1})` })
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
// 状态语义着色:进行中=主色 / 待确认=陶土 / 已完成=鼠尾草绿(设计稿 §3.4「已找到 绿标签」)
const TASK_STATUS_TAG = { OPEN: '', IN_PROGRESS: 'pri', REVIEW: 'acc', DONE: 'grn', CANCELLED: '' }
const TASK_REWARD_ICON = { NONE: '⭕', POINTS: '🎁', ITEM: '📦' }
const taskStatusLabel = (s) => TASK_STATUS_LABEL[s] || ''
const taskIcon = (t) => TASK_REWARD_ICON[t] || '⭕'

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '凌晨 · 好梦'
  if (h < 12) return '早安'
  if (h < 18) return '午后'
  return '晚上好 · 家人都在'
})
</script>

<style>
/* 补色强度档:陶土粉(--blob-1)与鼠尾草绿(--blob-3)原先只以 .12~.14 的雾状渐变存在,
 * 叠在米色卡面上与卡面只差个位数 RGB,读不出色相 —— 统一提到可读区间,并且立一条「标题色相锚点」 */
.gc-home { color: var(--color-text); --chip-a: .20; --bar-a: .8; --sill-a: .5; --sage-a: .30; }
/* 暮色是 #33241A 深咖底,同一 alpha 在暗底上更向灰收敛,补色统一再提一档 */
html.theme-warm.dark .gc-home { --chip-a: .26; --bar-a: 1; --sill-a: .58; --sage-a: .36; }

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
.gc-card-h3 { font-size: 13px; font-weight: 650; margin: 0 0 12px; color: var(--color-text); display: flex; align-items: center; gap: 8px; }
/* 色相锚点:每张卡标题前一条 3px 竖条,取该卡的 --chip(陶土/鼠尾草/暖沙/暖木),
 * 让 6 张卡不再共用同一个「米色面 + 棕字」 */
.gc-card-h3::before { content: ''; width: 3px; height: 12px; border-radius: 2px; flex-shrink: 0;
  background: rgba(var(--chip, var(--color-brand-rgb)), var(--bar-a)); }
/* 右侧附属文字(收支月份/寻物户型名)推到行尾;末位是「更多」按钮时由 .gc-more 自己的 margin-left:auto 接管 */
.gc-card-h3 > .gc-muted:last-child { margin-left: auto; }
.gc-muted { font-size: 11.5px; color: var(--color-text-tertiary); }
.gc-val-lg { font-size: 16px; font-weight: 650; color: var(--color-brand); margin-bottom: 4px; }
.gc-flex-baseline { display: flex; justify-content: space-between; align-items: baseline; }
.gc-ctrl-row { display: flex; gap: 8px; margin: 4px 0 12px; flex-wrap: wrap; }
.gc-empty { font-size: 12px; color: var(--color-text-tertiary); padding: 20px 0; text-align: center; }
.gc-empty-link { cursor: pointer; }

/* 编辑模式 */
.gc-card-edit { border-style: dashed; border-color: var(--color-accent, var(--color-brand)); }
.gc-card-link { cursor: pointer; }
.gc-overflow { overflow: hidden; }
/* 卡片重排中被"拿起"的源卡片:保留原位降透明度,目标下标另有同尺寸占位 */
.gc-card-dragging { opacity: .35; }
/* 拖入预览占位(4 列 × 2 行幽灵卡片,作为真实网格项参与推挤) */
.gc-card-preview { border-style: dashed; border-color: var(--color-accent, var(--color-brand)); background: rgba(var(--color-brand-rgb), .05); box-shadow: none; cursor: copy; }
.gc-preview-body { height: 100%; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8px; color: var(--color-text-secondary); }
.gc-preview-icon { font-size: 30px; line-height: 1; }
.gc-preview-label { font-size: 14px; font-weight: 650; color: var(--color-text); }
.gc-preview-hint { font-size: 11.5px; color: var(--color-text-tertiary); }
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

/* 照片卡牌堆:卡片须为 flex 纵向容器,stack 的 flex:1 才能撑满剩余高度(否则子项全 absolute → 高度塌缩为 0 → 照片不可见) */
.gc-photos { display: flex; flex-direction: column; }
.gc-photo-stack { position: relative; flex: 1; min-height: 0; cursor: pointer; display: grid; place-items: center; overflow: hidden; }
.gc-photo-pcard { position: absolute; width: 72%; height: 76%; border-radius: 14px; overflow: hidden; background: var(--color-line);
  box-shadow: 0 14px 30px rgba(0, 0, 0, .16); z-index: var(--z, 10);
  transform: translate(var(--dx, 0), var(--dy, 0)) rotate(var(--rot, 0deg));
  transition: transform .45s cubic-bezier(.22, 1, .36, 1), box-shadow .3s ease, opacity .5s ease; }
.gc-photo-pcard.gc-photo-hovered { z-index: 40; transform: translate(0, 0) rotate(0deg) scale(1.1); box-shadow: 0 22px 44px rgba(0, 0, 0, .28); }
.gc-photo-pcard img { width: 100%; height: 100%; object-fit: cover; display: block; }
.gc-photo-meta { position: absolute; left: 10px; right: 10px; bottom: 8px; display: flex; align-items: center; justify-content: space-between; gap: 8px; z-index: 45; opacity: 0; transform: translateY(6px); transition: opacity .3s ease, transform .3s ease; pointer-events: none; }
.gc-photo-stack:hover .gc-photo-meta { opacity: 1; transform: translateY(0); }
.gc-photo-cap { font-size: 12px; font-weight: 600; color: #fff; padding: 4px 10px; border-radius: 999px; background: rgba(0, 0, 0, .4); backdrop-filter: blur(6px); max-width: 72%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.gc-photo-count { font-size: 11px; color: #fff; padding: 3px 9px; border-radius: 999px; background: rgba(0, 0, 0, .4); backdrop-filter: blur(6px); flex-shrink: 0; font-variant-numeric: tabular-nums; }
.gc-photo-nav { position: absolute; top: 50%; transform: translateY(-50%); z-index: 50; display: flex; align-items: center; justify-content: center; width: 28px; height: 28px; border: none; border-radius: 50%; cursor: pointer; color: #fff; background: rgba(0, 0, 0, .35); backdrop-filter: blur(6px); transition: background .2s ease, transform .2s ease; }
.gc-photo-nav:hover { background: rgba(0, 0, 0, .6); transform: translateY(-50%) scale(1.06); }
.gc-photo-nav svg { width: 16px; height: 16px; fill: none; stroke: currentColor; stroke-width: 2.4; stroke-linecap: round; stroke-linejoin: round; }
.gc-photo-prev { left: 2px; }
.gc-photo-next { right: 2px; }

/* 头部「更多」小链接(照片→相册 / 寻物→管理) */
.gc-more { all: unset; margin-left: auto; cursor: pointer; font-size: 11.5px; font-weight: 600; color: var(--color-text-tertiary); transition: color .2s; }
.gc-more:hover { color: var(--color-brand); }

/* 收支比例条 */
.gc-fin-bar { display: flex; height: 8px; border-radius: 6px; overflow: hidden; margin-top: 12px; background: var(--color-line); }
.gc-fin-bar i { display: block; height: 100%; }
.gc-fin-bar i.in { background: var(--color-green); }
.gc-fin-bar i.out { background: var(--color-accent); }

/* 寻物搜索框 + 语音 */
.gc-item-card { display: flex; flex-direction: column; }
.gc-item-search { display: flex; gap: 8px; margin-bottom: 12px; }
.gc-item-input { flex: 1; min-width: 0; border: 1px solid var(--color-border); background: var(--color-line); color: var(--color-text);
  border-radius: 10px; padding: 8px 12px; font-size: 13px; outline: none; transition: border-color .2s, background .2s; }
.gc-item-input:focus { border-color: var(--color-brand); background: var(--color-card); }
.gc-item-input::placeholder { color: var(--color-text-tertiary); }
.gc-item-voice { flex-shrink: 0; padding: 6px 12px; }
.gc-item-voice.recording { background: var(--color-accent); color: #fff7f0; border-color: var(--color-accent); animation: gcVoicePulse 1.2s ease-in-out infinite; }
@keyframes gcVoicePulse { 0%, 100% { box-shadow: 0 0 0 0 rgba(168, 72, 58, .4); } 50% { box-shadow: 0 0 0 6px rgba(168, 72, 58, 0); } }

/* 户型图预览 */
.gc-item-plan { position: relative; flex: 1; min-height: 0; border-radius: 12px; overflow: hidden; cursor: pointer;
  background: linear-gradient(160deg, var(--color-bg-2), var(--color-card-2)); box-shadow: inset 0 0 0 1px var(--color-line); }
.gc-item-svg { position: absolute; inset: 0; width: 100%; height: 100%; display: block; }
.gc-item-bg { opacity: .5; }
/* 户型图:房间/家具原先把「光尘」主色 rgb(184,140,110) 写死,暖居下不跟随主题 —— 改走 --color-brand-rgb */
.gc-item-room { fill: rgba(var(--color-brand-rgb), .1); stroke: rgba(var(--color-brand-rgb), .5); stroke-width: 1.6; stroke-linejoin: round; }
.gc-item-room.hit { fill: rgba(var(--color-brand-rgb), .22); stroke: var(--color-brand); }
.gc-item-room-label { fill: var(--color-text-secondary); font-size: 12px; text-anchor: middle; pointer-events: none; }
.gc-item-furn { fill: rgba(var(--color-brand-rgb), .2); stroke: rgba(var(--color-brand-rgb), .45); stroke-width: 1.2; }
.gc-item-furn-label { fill: var(--color-text-tertiary); font-size: 10px; text-anchor: middle; pointer-events: none; }
.gc-item-dot { fill: var(--color-brand); stroke: var(--color-card); stroke-width: 1.5; }
.gc-item-dot.hit { fill: var(--color-accent); }
.gc-item-dot-label { fill: var(--color-text-secondary); font-size: 10px; text-anchor: middle; pointer-events: none; }
.gc-item-results { position: absolute; top: 10px; right: 10px; width: 46%; max-height: 60%; overflow-y: auto;
  background: var(--color-card); border: 1px solid var(--color-border); border-radius: 12px; box-shadow: var(--shadow); padding: 6px 10px; z-index: 5; }
.gc-item-results .gc-row { font-size: 12px; padding: 7px 0; }
.gc-item-nohit { position: absolute; left: 50%; bottom: 14px; transform: translateX(-50%); font-size: 12px; color: var(--color-text-tertiary);
  padding: 6px 12px; border-radius: 999px; background: var(--color-card); box-shadow: var(--shadow); white-space: nowrap; }

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
.gc-link-icon { width: 42px; height: 42px; border-radius: 12px; display: grid; place-items: center; font-size: 20px; background: rgba(var(--blob-3), var(--chip-a)); flex-shrink: 0; }
.gc-link-text { font-size: 13px; font-weight: 600; color: var(--color-text); flex: 1; }
.gc-link-arrow { font-size: 16px; color: var(--color-text-tertiary); transition: transform .2s ease, color .2s ease; }
.gc-link-body:hover .gc-link-arrow { transform: translateX(3px); color: var(--color-brand); }

/* 天气活窗 */
/* 底部 3px 陶土「窗台线」:暖居的隐喻是屋里的窗,一条实色窗台比一层模糊光晕更能说明这是陶土粉 */
.gc-glass { position: relative; height: 230px; border-radius: 12px; overflow: hidden; cursor: pointer;
  background: linear-gradient(160deg, var(--color-bg-2), var(--color-card-2));
  box-shadow: inset 0 0 0 1px var(--color-line), inset 0 14px 40px rgba(122, 90, 60, .08),
    inset 0 -3px 0 rgba(var(--blob-1), var(--sill-a)); }
/* 天气卡在固定行高网格内占满剩余高度(8 列 × 4 行基准) */
.gc-weather { display: flex; flex-direction: column; }
.gc-weather .gc-glass { flex: 1; height: auto; min-height: 120px; }
/* 右上陶土暖光 + 左下鼠尾草绿光斑:半径收小(原 360/280px 铺太开被拉平成「泛黄」)并把绿的 alpha 提到可读 */
.gc-glass::before { content: ""; position: absolute; inset: 0;
  background:
    radial-gradient(240px 150px at 76% 16%, var(--glow-warm), transparent 60%),
    radial-gradient(200px 140px at 8% 92%, rgba(var(--blob-3), var(--sage-a)), transparent 56%); }
.gc-glass::after { content: ""; position: absolute; left: 14%; top: -4%; width: 46%; height: 110%;
  background: url("data:image/svg+xml,%3Csvg width='340' height='760' xmlns='http://www.w3.org/2000/svg'%3E%3Cdefs%3E%3ClinearGradient id='b' x1='0' y1='0' x2='1' y2='1'%3E%3Cstop offset='0' stop-color='%23FFE9C4' stop-opacity='.38'/%3E%3Cstop offset='.55' stop-color='%23FFDDA6' stop-opacity='.13'/%3E%3Cstop offset='1' stop-color='%23FFDDA6' stop-opacity='0'/%3E%3C/linearGradient%3E%3C/defs%3E%3Crect width='340' height='760' fill='url(%23b)' transform='rotate(14 170 380)'/%3E%3C/svg%3E");
  background-size: 100% 100%; mix-blend-mode: screen; opacity: .8; transform: rotate(10deg); }
/* 暮色:screen 混合叠在深咖窗面上会把 SVG 矩形的直边暴露成一块「硬边米色板」,降透明 + 模糊化回光 */
html.dark .gc-glass::after { opacity: .3; filter: blur(7px); }
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
/* 进度条:填充改纯陶土实色 —— 原先 brand→accent 渐变在窄幅里大半还是棕色,是页面唯一能读出陶土粉的地方却读不出来 */
.gc-meter { height: 10px; border-radius: 6px; background: var(--color-line); overflow: hidden; margin-top: 8px; }
.gc-meter > i { display: block; height: 100%; background: var(--color-accent); border-radius: 6px; }
.gc-days { color: var(--color-accent); font-weight: 650; }
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
.gc-list .gc-row > .gc-tag { margin-left: auto; }
.gc-ic { width: 26px; height: 26px; border-radius: 8px; display: grid; place-items: center; font-size: 13px; background: rgba(var(--chip, var(--blob-1)), var(--chip-a)); flex-shrink: 0; }
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
