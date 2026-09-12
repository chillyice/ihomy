<!-- 首页:12列×9行栅格仪表盘,编辑模式可拖拽/缩放/增删组件 -->
<template>
  <div ref="root" class="home-page" :class="{ 'edit-mode': editMode }">
    <PhotoViewer v-model:visible="viewerVisible" :photos="sevenDayPhotos" :initial-index="viewerIdx" />

    <!-- 编辑模式工具栏(hover隐藏) -->
    <Transition name="fade">
      <div v-if="editMode" class="edit-toolbar edit-toolbar-hover">
        <span class="edit-label">编辑模式 · 拖拽移动 · 右下角调整大小</span>
        <el-button size="small" @click="resetLayout">恢复默认</el-button>
        <el-button size="small" type="primary" @click="finishEdit">完成</el-button>
      </div>
    </Transition>

    <!-- 栅格背景(编辑模式可见,出现动画) -->
    <Transition name="grid-fade" appear>
      <div v-if="editMode" class="grid-overlay" :style="gridOverlayStyle">
        <div v-for="i in 108" :key="i" class="grid-cell" :style="{ animationDelay: (i * 5) + 'ms' }"></div>
      </div>
    </Transition>

    <!-- 组件 -->
    <template v-for="w in visibleWidgets" :key="w.uid">
      <div
        :ref="el => setCardEl(w.uid, el)"
        class="dash-card"
        :class="[w.id, { 'edit-active': editMode, dragging: w._dragging, 'h-1': w.h === 1, 'is-hovered': hoverExpands && hoverUid === w.uid, 'is-neighbor': isFullNeighbor(w), 'wave-hint': !editMode && displayTier(w) === 'S' && !hoverExpands }]"
        :style="cardBoxStyle(w)"
        @mouseenter="onCardEnter(w)"
        @mouseleave="onCardLeave"
        @click="bringToFront(w)"
      >
        <div v-if="editMode" class="drag-bar" @mousedown="onDragStart($event, w)"><span class="grip"></span></div>
        <button v-if="editMode" class="del-btn" @click.stop="removeWidget(w)">✕</button>
        <div v-if="editMode" class="resize-corner" @mousedown.stop="onResizeStart($event, w)"></div>

        <!-- 邻居胶囊:仅「全盖」级邻居缩成图标+标题(部分盖→降档显示内容,擦边→轻微缩小) -->
        <div v-if="isFullNeighbor(w)" class="neighbor-capsule">
          <span class="nc-icon">{{ WIDGET_ICONS[w.id] || '' }}</span>
          <span class="nc-label">{{ WIDGET_LABELS[w.id] }}</span>
        </div>

        <div class="card-inner">
          <!-- 家人动态 -->
          <template v-if="w.id === 'feed'">
            <div class="card-head">家人动态</div>
            <div class="card-scroll">
              <div v-if="!feeds.length" class="empty-hint">暂无动态</div>
              <div v-for="(f, i) in feeds.slice(0, nFeed(w))" :key="i" class="feed-row" @click="!editMode && goFeed(f)">
                <el-avatar :size="36" :src="f.authorAvatar" class="feed-avatar">{{ (f.authorName || 'U').charAt(0) }}</el-avatar>
                <div class="feed-content">
                  <div class="feed-nick">{{ f.authorName || '家人' }}</div>
                  <div class="feed-bubble">
                    <div class="bubble-type">{{ feedTypeLabel(f.type) }}</div>
                    <div class="bubble-body">{{ feedSummary(f) }}</div>
                    <div class="bubble-time">{{ formatTime(f.createdAt) }}</div>
                  </div>
                </div>
              </div>
            </div>
          </template>

          <!-- 悬赏任务 -->
          <template v-else-if="w.id === 'task'">
            <div class="card-head">悬赏任务</div>
            <div class="card-scroll">
              <div v-if="!tasks.length" class="empty-hint">暂无任务</div>
              <div v-for="t in tasks.slice(0, nTask(w))" :key="t.id" class="task-row" @click="!editMode && $router.push('/task')">
                <span class="task-reward">{{ rewardIcon(t.rewardType) }}</span>
                <div class="task-info"><div class="task-title">{{ t.title }}</div><div class="task-meta"><span class="task-status-dot" :class="'s-' + t.status"></span>{{ taskStatusLabel(t.status) }}</div></div>
              </div>
            </div>
          </template>

          <!-- 今日 -->
          <template v-else-if="w.id === 'today'">
            <div class="card-head">今日</div>
            <div class="card-scroll">
              <div class="today-points">
                <div class="tp-item" @click="!editMode && $router.push('/points')"><span class="tp-num">{{ pointsStats.balance ?? 0 }}</span><span class="tp-label">积分</span></div>
                <div class="tp-item" @click="!editMode && $router.push('/points')"><span class="tp-num">{{ pointsStats.streak ?? 0 }}</span><span class="tp-label">连续天数</span></div>
                <el-button size="small" type="primary" round :disabled="pointsStats.checkedToday" @click="doCheckin">{{ pointsStats.checkedToday ? '已签到' : '签到 +' + (pointsStats.todayPoints ?? 5) }}</el-button>
              </div>
              <template v-if="nReminder(w) > 0">
                <div class="today-reminders">
                  <div v-if="!reminders.length" class="empty-hint">今日无待办</div>
                  <div v-for="r in reminders.slice(0, nReminder(w))" :key="r.id" class="today-reminder" @click="!editMode && $router.push('/reminder')"><span class="tr-dot"></span><span class="tr-title">{{ r.title }}</span><span class="tr-time">{{ (r.remindTime || '').slice(0, 5) }}</span></div>
                </div>
              </template>
            </div>
          </template>

          <!-- 天气:AI 生图底图(中档以上)+ 图标/温度/高低温/文字 + 预报;点击进入天气详情页 -->
          <template v-else-if="w.id === 'weather'">
            <div v-if="tierOf(w) === 'XL'" class="weather-bg">
              <div v-if="weatherBg" class="weather-bg-img" :style="{ backgroundImage: `url(${weatherBg})` }"></div>
              <div class="weather-bg-grad"></div>
            </div>
            <div class="card-head">天气</div>
            <div class="card-scroll weather-scroll weather-clickable" @click="!editMode && $router.push('/weather')">
              <div v-if="weather" class="weather-main">
                <div class="weather-city">{{ weather.city || '济南' }}</div>
                <div class="weather-current">
                  <i v-if="weather.iconCode" :class="'qi-' + weather.iconCode" class="weather-icon-float"></i>
                  <span class="weather-temp-large">{{ weather.temp }}<span class="temp-unit">°</span></span>
                  <span v-if="tierOf(w) !== 'S' && (todayHigh != null || todayLow != null)" class="weather-hilo">
                    <span class="wh-item wh-hi">↑ {{ todayHigh ?? '-' }}°</span>
                    <span class="wh-item wh-lo">↓ {{ todayLow ?? '-' }}°</span>
                  </span>
                </div>
                <div v-if="tierOf(w) !== 'S'" class="weather-condition">{{ weatherText }}</div>
                <div v-if="showForecast(w)" class="weather-forecast">
                  <div v-for="d in (weatherDetail?.daily || []).slice(0, nForecast(w))" :key="d.fxDate" class="wf-row">
                    <span class="wf-date">{{ fmtForecastDate(d.fxDate) }}</span>
                    <i :class="'qi-' + (d.iconDay || '')" class="wf-icon"></i>
                    <span class="wf-range">{{ d.tempMin }}°~{{ d.tempMax }}°</span>
                  </div>
                </div>
              </div>
              <div v-else class="weather-loading-text">天气加载中…</div>
            </div>
          </template>

          <!-- 纪念日 -->
          <template v-else-if="w.id === 'anni'">
            <div class="card-head">近期纪念日</div>
            <div class="card-scroll">
              <div v-for="(a, i) in anniversaries.slice(0, nAnni(w))" :key="i" class="anni-row" @click="!editMode && $router.push('/anniversary')">
                <div class="anni-info"><div class="anni-name">{{ a.label }}</div><div class="anni-date">{{ a.date }}</div></div>
                <div class="anni-days"><span class="days-num">{{ a.days }}</span><span class="days-unit">天</span></div>
              </div>
            </div>
          </template>

          <!-- 今日推荐 -->
          <template v-else-if="w.id === 'recipe'">
            <div class="card-head">今日推荐</div>
            <div class="card-scroll">
              <div v-if="todayRecipes.length" class="recipe-list">
                <router-link v-for="r in todayRecipes.slice(0, nRecipe(w))" :key="r.id" :to="`/kitchen/recipe/${r.id}`" class="recipe-item">
                  <img v-if="r.coverImage" :src="r.coverImage" class="recipe-cover" />
                  <div v-else class="recipe-cover placeholder">🍳</div>
                  <span class="recipe-name">{{ r.name }}</span>
                </router-link>
              </div>
              <div v-else class="widget-empty">暂无推荐<div class="widget-empty-hint">去厨房添加菜谱</div></div>
            </div>
            <router-link to="/kitchen" class="card-more">查看菜谱 →</router-link>
          </template>

          <!-- 寻物(item 页缩小版):搜索 + 只读户型图(自适应大小)· 命中放大居中 + 上/下一个 -->
          <template v-else-if="w.id === 'search'">
            <div class="card-head">寻物</div>
            <div class="card-scroll search-scroll">
              <div class="search-input-row">
                <el-input v-model="itemKeyword" placeholder="搜物品名/别名/位置/家具/房间" clearable @keyup.enter="searchItems" @clear="clearItemSearch">
                  <template #prefix><el-icon><Search /></el-icon></template>
                </el-input>
                <button v-if="tierOf(w) === 'L' || tierOf(w) === 'XL'" :class="['voice-btn', { on: voiceListening }]" type="button" :title="voiceListening ? '停止' : '语音找物'" @click="toggleVoice">
                  <el-icon><Microphone /></el-icon>
                </button>
              </div>
              <div class="fp-wrap">
                <FloorPlanCanvas
                  ref="fpCanvasRef"
                  v-if="hasFloorPlan"
                  mode="view"
                  :rooms="homeFloorPlan.rooms"
                  :furnitures="homeFloorPlan.furnitures"
                  :items="homeFloorPlan.items"
                  :image-url="homeFloorPlan.imageUrl"
                  :opacity="floorPlanOpacity"
                  :highlight-item-ids="fpHighlight"
                  :scale="homeFloorPlan.scale || 100"
                  :image-transform="floorPlanImg"
                  :fit-key="fpFitKey"
                  :show-thumb="false"
                />
                <div v-else class="fp-no-plan">暂无户型图 · 到「寻物」管理页绘制</div>
                <!-- 当前命中物品 -->
                <div v-if="currentMatch" class="fp-match-chip">
                  <span class="fm-name">{{ currentMatch.name }}<em v-if="searchSource === 'ai'" class="fm-src">✨ AI</em></span>
                  <span class="fm-loc">{{ [currentMatch.house_name, currentMatch.room_name, currentMatch.furniture_name].filter(Boolean).join(' · ') }}</span>
                </div>
                <!-- 搜索进行/无结果提示(与物品定位页语义一致) -->
                <div v-if="aiSearching && hasFloorPlan" class="fp-search-hint">✨ AI 找物中…</div>
                <div v-else-if="searched && !searchResults.length && hasFloorPlan" class="fp-search-hint">未找到相关物品,换个说法试试</div>
                <!-- 上/下一个 命中导航(左右箭头) -->
                <div v-if="searchResults.length > 1" class="fp-nav">
                  <button class="fp-nav-btn" :disabled="matchIdx <= 0" @click="prevMatch" aria-label="上一个">‹</button>
                  <span class="fp-nav-count">{{ matchIdx + 1 }} / {{ searchResults.length }}</span>
                  <button class="fp-nav-btn" :disabled="matchIdx >= searchResults.length - 1" @click="nextMatch" aria-label="下一个">›</button>
                </div>
              </div>
            </div>
            <router-link to="/item" class="card-more">物品管理 →</router-link>
          </template>

          <!-- 愿望单 -->
          <template v-else-if="w.id === 'wish'">
            <div class="card-head">愿望单</div>
            <div class="card-scroll">
              <div v-if="wishes.length" class="wish-list">
                <div v-for="w in wishes.slice(0, nWish(w))" :key="w.id" class="wish-item" :class="{ done: w.status === 'ACHIEVED' }"><span class="wish-dot" :class="w.status"></span><span class="wish-name">{{ w.title }}</span></div>
              </div>
              <div v-else class="widget-empty">暂无愿望<div class="widget-empty-hint">去愿望单记录家庭心愿</div></div>
            </div>
            <router-link to="/wish" class="card-more">查看全部 →</router-link>
          </template>

          <!-- 本月收支 -->
          <template v-else-if="w.id === 'finance'">
            <div class="card-head">本月收支</div>
            <div class="finance-body">
              <div class="fin-item"><span class="fin-label">收入</span><span class="fin-val income">+{{ bookSummary?.income || 0 }}</span></div>
              <div class="fin-item"><span class="fin-label">支出</span><span class="fin-val expense">-{{ bookSummary?.expense || 0 }}</span></div>
              <div class="fin-item"><span class="fin-label">结余</span><span class="fin-val" :class="(bookSummary?.balance || 0) >= 0 ? 'income' : 'expense'">{{ bookSummary?.balance || 0 }}</span></div>
            </div>
            <router-link to="/book" class="card-more">查看明细 →</router-link>
          </template>

          <!-- 拍立得 -->
          <template v-else-if="w.id === 'album'">
            <div class="album-container" :style="{ '--polaroid-w': polaroidW(w) + 'px' }">
              <div v-if="recentPhotos.length" class="polaroid-stack">
                <div v-for="(p, i) in recentPhotos" :key="p.id" class="polaroid-pos" :style="{ transform: `rotate(${polaroidLayout[i]?.rotate || 0}deg) translate(${polaroidLayout[i]?.dx || 0}px, ${polaroidLayout[i]?.dy || 0}px)`, zIndex: polaroidLayout[i]?.z || 1 }">
                  <div class="polaroid" @click="!editMode && openViewer(i)"><img :src="p.url" :alt="p.description || ''" /><div v-if="p.description" class="polaroid-caption">{{ p.description }}</div></div>
                </div>
              </div>
              <div v-else class="album-closed" :style="{ width: albumCoverW(w) + 'px' }" @click="!editMode && $router.push('/album')">
                <div class="album-book" :class="{ open: albumOpen }">
                  <div class="album-page">
                    <Transition name="album-page">
                      <div v-if="historyPhotos.length" :key="albumIdx" class="album-page-card">
                        <img :src="historyPhotos[albumIdx]?.url" :alt="historyPhotos[albumIdx]?.description || ''" class="album-page-img" />
                        <div class="album-page-back"></div>
                      </div>
                      <div v-else key="empty" class="album-page-img album-page-empty"><span>去添加家庭照片</span></div>
                    </Transition>
                  </div>
                  <div class="album-cover">
                    <div class="album-cover-front"><img :src="albumCoverUrl" alt="家庭相册" class="album-cover-img" /></div>
                    <div class="album-cover-back"></div>
                  </div>
                </div>
              </div>
            </div>
          </template>
        </div>
      </div>
    </template>

    <!-- 拖拽幽灵(从侧边栏拖出组件时) -->
    <div v-if="ghostActive" class="drag-ghost" :class="{ 'ghost-grown': ghostGrown }" :style="{ left: ghostX + 'px', top: ghostY + 'px' }">
      <span class="ghost-label">{{ ghostLabel }}</span>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, inject, nextTick, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import { useI18n } from 'vue-i18n'
import { publicApi, homeApi, taskApi, pointsApi, reminderApi, bookApi, wishApi, itemApi, kitchenApi, aiApi, albumApi, photoApi } from '@/api'
import { gsap } from 'gsap'
import { ElMessage } from 'element-plus'
import { Search, Microphone } from '@element-plus/icons-vue'
import PhotoViewer from '@/components/PhotoViewer.vue'
import albumCoverUrl from '@/assets/album-cover.jpg'
import FloorPlanCanvas from '@/views/item/FloorPlanCanvas.vue'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'
import { useWidgetDrag } from '@/utils/useWidgetDrag'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const appStore = useAppStore()
const { t } = useI18n()
const sunLight = inject(SUN_LIGHT_KEY)
const root = ref(null)
let ctx

const editMode = computed(() => appStore.homeEditMode)
const finishEdit = () => { appStore.toggleHomeEditMode() }
// 进入编辑模式清除入场动画残留:GSAP from 的中间态 transform 在 tween 被中断(DOM 重建等)后会永久
// 残留在组件上,使组件偏离栅格线;编辑态语义是精确对齐,进场先 kill 动画并清掉非 Vue 管理的 inline 样式
watch(editMode, (on) => {
  if (!on) return
  gsap.killTweensOf('.dash-card')
  gsap.set('.dash-card', { clearProps: 'transform,opacity,visibility' })
})

// ========== 栅格尺寸:自适应屏幕分辨率 ==========
const COLS = 12
const ROWS = 9
const GAP = 40
const SIDEBAR_W = 220
const MARGIN = { top: 32, right: 40, bottom: 40, left: SIDEBAR_W + 40 }

const winW = ref(window.innerWidth)
const winH = ref(window.innerHeight)
const onWinResize = () => { winW.value = window.innerWidth; winH.value = window.innerHeight }
window.addEventListener('resize', onWinResize)
onUnmounted(() => window.removeEventListener('resize', onWinResize))

const cellW = computed(() => {
  const gridW = winW.value - MARGIN.left - MARGIN.right
  return Math.max(0, (gridW - GAP * (COLS - 1)) / COLS)
})
const cellH = computed(() => {
  const gridH = winH.value - MARGIN.top - MARGIN.bottom
  return Math.max(0, (gridH - GAP * (ROWS - 1)) / ROWS)
})

const cardStyle = (w) => {
  const cw = cellW.value
  const ch = cellH.value
  const left = MARGIN.left + w.col * (cw + GAP)
  const top = MARGIN.top + w.row * (ch + GAP)
  const width = w.w * cw + (w.w - 1) * GAP
  const height = w.h * ch + (w.h - 1) * GAP
  const s = { left: left + 'px', top: top + 'px', width: width + 'px', height: height + 'px' }
  if (w._z) s.zIndex = w._z
  return s
}

// 相册内容尺寸:按组件实际像素的宽高双向预算取 min——宽页面(列宽远大于行高)时受高度约束,
// 不再随组件宽度撑大;cardStyle 同源的格子数学
const albumBox = (w) => ({
  W: w.w * cellW.value + (w.w - 1) * GAP,
  H: w.h * cellH.value + (w.h - 1) * GAP,
})
// 拍立得宽:全高≈0.75P+22(4:3 图+白框+手写条),高向不超组件高 55%,宽向不超组件宽 42%
const polaroidW = (w) => {
  const { W, H } = albumBox(w)
  return Math.round(Math.max(80, Math.min(W * 0.42, (H * 0.55 - 22) / 0.75, 170)))
}
// 相册封面宽:4:3 → 全高=宽×0.75,宽取组件宽 70% 与组件高 95% 的较小者
const albumCoverW = (w) => {
  const { W, H } = albumBox(w)
  return Math.round(Math.min(W * 0.7, H * 0.95))
}

const gridOverlayStyle = computed(() => ({
  left: MARGIN.left + 'px',
  top: MARGIN.top + 'px',
  right: MARGIN.right + 'px',
  bottom: MARGIN.bottom + 'px',
  '--cell-w': cellW.value + 'px',
  '--cell-h': cellH.value + 'px',
}))

// ========== 数据 ==========
const family = ref({})
const feeds = ref([])
const tasks = ref([])
const allPhotos = ref([])
const weather = computed(() => sunLight?.weather.value)
const weatherDetail = computed(() => sunLight?.weatherDetail?.value)
const todayHigh = computed(() => weatherDetail.value?.daily?.[0]?.tempMax ?? null)
const todayLow = computed(() => weatherDetail.value?.daily?.[0]?.tempMin ?? null)
const anniversaries = ref([])
const pointsStats = ref({})
const reminders = ref([])
const bookSummary = ref(null)
const wishes = ref([])
const itemKeyword = ref('')
const searchResults = ref([])
const searchSource = ref('') // 'keyword' | 'ai'
const searched = ref(false)
const aiSearching = ref(false)
const todayRecipes = ref([])

const loadPoints = async () => { if (userStore.isLoggedIn) { try { pointsStats.value = await pointsApi.stats() } catch (e) {} } }
const loadReminders = async () => { if (userStore.isLoggedIn) { try { const r = await reminderApi.list(); reminders.value = (Array.isArray(r) ? r : []).filter(x => x.done !== 1) } catch (e) {} } }
const loadBookSummary = async () => { if (userStore.isLoggedIn) { try { bookSummary.value = await bookApi.summary() } catch (e) {} } }
const loadWishes = async () => { if (userStore.isLoggedIn) { try { wishes.value = await wishApi.list() } catch (e) {} } }
const searchItems = async () => {
  const kw = itemKeyword.value.trim()
  if (!kw) { clearItemSearch(); return }
  searched.value = true
  searchSource.value = 'keyword'
  try { searchResults.value = await itemApi.list({ keyword: kw }) } catch (e) { searchResults.value = [] }
  if (searchResults.value.length) { await focusInitialMatch(); return }
  // 关键词无命中 → AI 找物兜底(俗称/别名/自然语言),与物品定位页 onSearch 口径一致;AI 不可用静默保持无结果
  aiSearching.value = true
  try {
    const r = await itemApi.aiFind({ query: kw })
    if (r.matches && r.matches.length) {
      searchResults.value = r.matches
      searchSource.value = 'ai'
    }
  } catch (e) {
    // AI 未配置/调用失败:保持关键词搜索的无结果状态,不弹错误
  } finally {
    aiSearching.value = false
  }
  if (searchResults.value.length) await focusInitialMatch()
  else matchIdx.value = 0
}
const clearItemSearch = () => { itemKeyword.value = ''; searchResults.value = []; matchIdx.value = 0; searched.value = false; searchSource.value = ''; aiSearching.value = false }
const loadTodayRecipes = async () => { if (userStore.isLoggedIn) { try { const data = await kitchenApi.menu(); todayRecipes.value = data?.todayRecommend || [] } catch (e) {} } }
const doCheckin = async () => { try { const r = await pointsApi.checkin(); ElMessage.success(`签到成功 +${r.points} 积分,连续 ${r.streak} 天`); await loadPoints() } catch (e) {} }

const SEVEN_DAYS = 7 * 86400000
// 近7天照片与拍立得抽样:ref + watch 一次性生成(computed 内禁 Date.now()/Math.random(),
// 否则任何重算都会重新洗牌导致拍立得随机跳动 —— 见 AGENTS.md computed 纯函数规范)
const sevenDayPhotos = ref([])
const viewerVisible = ref(false)
const viewerIdx = ref(0)
const recentPhotos = ref([])
// 历史照片(全部照片打乱):供相册封面翻开后随机轮播,computed 内禁 Math.random 故放 watch 一次性生成
const historyPhotos = ref([])
watch(allPhotos, (photos) => {
  const now = Date.now()
  const ps = photos.filter(p => p.createdAt && now - new Date(p.createdAt).getTime() < SEVEN_DAYS)
  sevenDayPhotos.value = ps
  const shuffled = ps.slice()
  for (let i = shuffled.length - 1; i > 0; i--) { const j = Math.floor(Math.random() * (i + 1)); [shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]] }
  recentPhotos.value = shuffled.slice(0, 7)
  const hist = photos.slice()
  for (let i = hist.length - 1; i > 0; i--) { const j = Math.floor(Math.random() * (i + 1)); [hist[i], hist[j]] = [hist[j], hist[i]] }
  historyPhotos.value = hist
}, { immediate: true })
const polaroidLayout = ref([])
watch(recentPhotos, (ps) => { polaroidLayout.value = ps.map((p, i) => ({ rotate: (Math.random() - 0.5) * 50, dx: (Math.random() - 0.5) * 340, dy: (Math.random() - 0.5) * 140, z: i + 1 })) }, { immediate: true })
// 点击拍立得 → 打开照片大图(重新设计以前的交互)
const openViewer = (idx) => {
  const all = sevenDayPhotos.value
  const clicked = recentPhotos.value[idx]
  const realIdx = clicked ? all.findIndex(p => p.id === clicked.id) : 0
  viewerIdx.value = realIdx < 0 ? 0 : realIdx
  viewerVisible.value = true
}

// ========== 相册封面翻开:随机历史照片轮播(静躺 2s → 翻开 1.5s) ==========
const albumOpen = ref(false)
const albumIdx = ref(0)
let albumTimer = null
// 每张照片节奏 = 静躺 2000ms + 翻阅 1500ms;1500ms 与 CSS .album-page-card transform 1.5s / .album-page-img,back opacity 1.5s 一致
const ALBUM_PAGE_MS = 3500
const onAlbumEnter = () => {
  if (!HOVER_CAPABLE) return
  if (recentPhotos.value.length > 0 || !historyPhotos.value.length) return // 有近期照片时走拍立得分支,无历史照片无轮播
  albumOpen.value = true
  albumIdx.value = 0
  if (albumTimer) clearInterval(albumTimer)
  if (historyPhotos.value.length > 1) albumTimer = setInterval(() => { albumIdx.value = (albumIdx.value + 1) % historyPhotos.value.length }, ALBUM_PAGE_MS)
}
const onAlbumLeave = () => {
  albumOpen.value = false
  if (albumTimer) { clearInterval(albumTimer); albumTimer = null }
  albumIdx.value = 0
}

const TYPE_LABELS = { blog: '博客', diary: '日记', photo: '照片', video: '放映厅', wish: '愿望', task: '任务', recipe: '菜谱', book: '书架' }
const feedTypeLabel = (type) => TYPE_LABELS[type] || ''
const feedSummary = (f) => { if (f.type === 'blog') return f.title || ''; if (f.type === 'diary') return (f.content || '').slice(0, 40); if (f.type === 'photo') return `${f.count || 0} 张照片`; if (f.type === 'video') return `上传了影片:${f.title || ''}`; if (f.type === 'wish') return f.status === 'ACHIEVED' ? `实现了愿望:${f.title || ''}` : `许下愿望:${f.title || ''}`; if (f.type === 'task') return `发布任务:${f.title || ''}`; if (f.type === 'recipe') return `分享菜谱:${f.title || ''}`; if (f.type === 'book') return `上架图书:《${f.title || ''}》`; return '' }
const formatTime = (d) => { if (!d) return ''; const date = new Date(d); const now = new Date(); const diff = (now - date) / 1000; if (diff < 3600) return Math.floor(diff / 60) + ' 分钟前'; if (diff < 86400) return Math.floor(diff / 3600) + ' 小时前'; return date.toLocaleDateString('zh-CN') }
const FEED_ROUTES = { diary: '/diary', photo: '/album', video: '/cinema', wish: '/wish', task: '/task', recipe: '/kitchen', book: '/library' }
const goFeed = (f) => { if (f.type === 'blog' && f.id) router.push(`/blog/${f.id}`); else if (FEED_ROUTES[f.type]) router.push(FEED_ROUTES[f.type]) }
const rewardIcon = (t) => t === 1 ? '🎁' : t === 2 ? '📦' : '⭕'
const taskStatusLabel = (s) => ({ 0: '待领取', 1: '进行中', 2: '待确认', 3: '已完成', 4: '已取消' }[s] || '')
const weatherText = computed(() => weather.value?.text || '')

const homeId = computed(() => route.query.home_id || '')
const hid = computed(() => route.query.hid || '')
const loadAll = async () => {
  const homePromise = publicApi.getHome(homeId.value || undefined, hid.value || undefined).then(pub => { family.value = pub.family || {}; anniversaries.value = (pub.stats || {}).upcomingEvents || []; return pub.photos || [] }).catch(() => [])
  const feedPromise = (hid.value ? publicApi.getFeed(20, undefined, hid.value) : homeId.value ? publicApi.getFeed(20, homeId.value) : publicApi.getFeed(20)).then(r => { feeds.value = r || [] }).catch(() => { feeds.value = [] })
  const taskPromise = userStore.isLoggedIn ? taskApi.list().then(r => { tasks.value = (Array.isArray(r) ? r : (r.records || [])).filter(t => t.status !== 'CANCELLED') }).catch(() => {}) : Promise.resolve()
  const [photos] = await Promise.all([homePromise, feedPromise, taskPromise])
  allPhotos.value = photos
}

// ========== 布局状态 ==========
// 默认布局(排版方案 §五):12 列 × 9 行,9 组件默认展示(task 由用户从侧边栏拖入)
const DEFAULT_LAYOUT = [
  { id: 'feed', col: 0, row: 0, w: 4, h: 5 },
  { id: 'weather', col: 4, row: 0, w: 3, h: 3 },
  { id: 'anni', col: 7, row: 0, w: 3, h: 3 },
  { id: 'today', col: 4, row: 3, w: 3, h: 2 },
  { id: 'album', col: 0, row: 5, w: 4, h: 4 },
  { id: 'search', col: 4, row: 5, w: 4, h: 5 },
  { id: 'recipe', col: 8, row: 3, w: 2, h: 2 },
  { id: 'wish', col: 10, row: 3, w: 2, h: 2 },
  { id: 'finance', col: 8, row: 5, w: 2, h: 2 },
]

// 布局键加版本号:v2 让新默认布局真正生效——旧 v1 持久化布局与四档语义不兼容,直接丢弃改用 §五
const STORAGE_KEY = 'ihomy:dashboard:layout:v2'

// 拖入/拖出默认尺寸(排版方案 §三「拖出默认尺寸」)
const WIDGET_DEFAULT_SIZE = {
  feed: { w: 4, h: 5 },
  album: { w: 5, h: 4 },
  search: { w: 4, h: 5 },
  weather: { w: 3, h: 3 },
  today: { w: 3, h: 3 },
  anni: { w: 3, h: 3 },
  recipe: { w: 2, h: 2 },
  wish: { w: 2, h: 2 },
  finance: { w: 2, h: 2 },
  task: { w: 3, h: 3 },
}

// 四档(排版方案 §二/§三):档位由面积推导——小=2×2(≤4)、中=3×3/3×2(≤9)、大=4×4/4×5/5×4(≤20)、巨大=5×5/6×4(>20)
function displayTier(w) {
  const area = w.w * w.h
  if (area <= 4) return 'S'
  if (area <= 9) return 'M'
  if (area <= 20) return 'L'
  return 'XL'
}

// snap 预设档(排版方案 §三):小=2×2 / 中=3×3 / 大=4×4,5×4 / 巨大=5×5,6×4
const SNAP_SIZES = [
  { w: 2, h: 2 },
  { w: 3, h: 3 },
  { w: 4, h: 4 },
  { w: 5, h: 4 },
  { w: 5, h: 5 },
  { w: 6, h: 4 },
]
// 取最接近的预设档(受网格边界约束)
const snapSize = (nw, nh, col, row) => {
  let best = null, bestDist = Infinity
  for (const s of SNAP_SIZES) {
    if (s.w > COLS - col || s.h > ROWS - row) continue
    const d = Math.abs(s.w - nw) + Math.abs(s.h - nh)
    if (d < bestDist) { bestDist = d; best = s }
  }
  if (!best) best = { w: Math.max(1, Math.min(COLS - col, nw)), h: Math.max(1, Math.min(ROWS - row, nh)) }
  return best
}

// 各组件四档内容条数(排版方案 §四):小/中/大/巨大 渐进展开;tierOf 使 hover 升档时内容随之增多
const nFeed = (w) => ({ S: 2, M: 4, L: 6, XL: 8 }[tierOf(w)] ?? 4)
const nTask = (w) => ({ S: 2, M: 3, L: 5, XL: 50 }[tierOf(w)] ?? 3)
const nReminder = (w) => ({ S: 0, M: 3, L: 6, XL: 50 }[tierOf(w)] ?? 3)
const nAnni = (w) => ({ S: 1, M: 3, L: 5, XL: 50 }[tierOf(w)] ?? 3)
const nRecipe = (w) => ({ S: 1, M: 2, L: 4, XL: 6 }[tierOf(w)] ?? 2)
const nWish = (w) => ({ S: 2, M: 5, L: 8, XL: 50 }[tierOf(w)] ?? 5)
// 热搜/今日待办/收支明细等大档才出现的功能
const showForecast = (w) => tierOf(w) === 'L' || tierOf(w) === 'XL'
const nForecast = (w) => (tierOf(w) === 'XL' ? 7 : 3)
const fmtForecastDate = (d) => { const dt = new Date(d); return `${dt.getMonth() + 1}月${dt.getDate()}日` }
const loadLayout = () => {
  try { const raw = localStorage.getItem(STORAGE_KEY); if (raw) return JSON.parse(raw) } catch (e) {}
  return null
}
const saveLayout = () => { try { localStorage.setItem(STORAGE_KEY, JSON.stringify(widgets.value.map(w => ({ id: w.id, col: w.col, row: w.row, w: w.w, h: w.h })))) } catch (e) {} }

const makeWidget = (cfg) => ({ ...cfg, uid: cfg.id + '_' + Date.now() + '_' + Math.random().toString(36).slice(2, 6), _z: 0, _dragging: false })

const widgets = ref((loadLayout() || DEFAULT_LAYOUT).map(makeWidget))

const visibleWidgets = computed(() => widgets.value.filter(w => {
  if (['today', 'task', 'recipe', 'search', 'wish', 'finance'].includes(w.id)) return userStore.isLoggedIn
  if (w.id === 'anni') return anniversaries.value.length > 0
  return true
}))

const resetLayout = () => {
  widgets.value = DEFAULT_LAYOUT.map(makeWidget)
  saveLayout()
  ElMessage.success('布局已重置')
}

const removeWidget = (w) => {
  widgets.value = widgets.value.filter(x => x.uid !== w.uid)
  saveLayout()
}

// ========== P2 hover 放大 + 推开邻居(排版方案 §二,三档邻居:全盖→胶囊 / 部分盖→降档缩小 / 擦边→轻微缩小) ==========
const HOVER_DELAY = 350
// 触摸/平板只览不展开(探询 §7):仅「支持 hover + 精确指针」设备参与放大/推挤/翻牌,避免 tap 误触
const HOVER_CAPABLE = typeof window !== 'undefined' && !!window.matchMedia && window.matchMedia('(hover: hover)').matches && window.matchMedia('(pointer: fine)').matches
const hoverUid = ref(null)
let hoverTimer = null

// 放大比例:普通组件 小档 2→3(1.5x)/中档 3→4(1.33x),大/巨大不放大;
// 天气为「主角」:hover 直接放大到 5×5 巨大档(scale = 5 / 较长边,≥1),已 XL 不放大
const WEATHER_HERO = 5
const hoverScale = (hw) => {
  if (hw.id === 'weather') return displayTier(hw) === 'XL' ? 1 : Math.max(1, WEATHER_HERO / Math.max(hw.w, hw.h))
  return ({ S: 1.5, M: 4 / 3, L: 1, XL: 1 }[displayTier(hw)] ?? 1)
}
// 放大后组件的视觉矩形(格坐标):中心缩放理想矩形 clamp 进栅格(边缘组件向内收),
// 并反推 transform-origin(相对原组件尺寸的 0-1 比例)使 scale 视觉与矩形一致
const hoverRect = (h) => {
  const sc = hoverScale(h)
  const w = h.w * sc
  const hgt = h.h * sc
  const idealLeft = h.col + h.w / 2 - w / 2
  const idealTop = h.row + h.h / 2 - hgt / 2
  const left = Math.min(Math.max(idealLeft, 0), Math.max(0, COLS - w))
  const top = Math.min(Math.max(idealTop, 0), Math.max(0, ROWS - hgt))
  const originX = sc > 1 ? (h.col - left) / (h.w * (sc - 1)) : 0.5
  const originY = sc > 1 ? (h.row - top) / (h.h * (sc - 1)) : 0.5
  return { left, top, w, hgt, originX, originY }
}
// 判断邻居 n 是否被放大后的组件 h 影响:与 clamp 后的放大矩形(格坐标)相交即受影响
const hoverAffects = (h, n) => {
  const sc = hoverScale(h)
  if (sc <= 1) return false
  const r = hoverRect(h)
  const nl = n.col, nt = n.row, nr = n.col + n.w, nb = n.row + n.h
  return nl < r.left + r.w && nr > r.left && nt < r.top + r.hgt && nb > r.top
}
// ---- 推开邻居三档(方案二:按覆盖比例)----
// 全盖 ≥70% → 胶囊(缩 0.55+位移);部分盖 ≥30% → 降档缩小(内容还在,少显示一些);擦边 → 轻微缩小 0.85、内容不变
const NEIGHBOR_FULL = 0.7
const NEIGHBOR_PARTIAL = 0.3
const NEIGHBOR_SCALE_FULL = 0.55
const NEIGHBOR_SCALE_GRAZE = 0.85
const NEIGHBOR_PUSH_BUFFER = 6 // 最小推挤位移后的缓冲(px),确保邻居有可见移动
// 放大矩形盖住邻居 n 的面积占比(0~1)
const hoverCoverage = (h, n) => {
  const r = hoverRect(h)
  const ox = Math.max(n.col, r.left)
  const oy = Math.max(n.row, r.top)
  const ox2 = Math.min(n.col + n.w, r.left + r.w)
  const oy2 = Math.min(n.row + n.h, r.top + r.hgt)
  if (ox >= ox2 || oy >= oy2) return 0
  return ((ox2 - ox) * (oy2 - oy)) / (n.w * n.h)
}
// 覆盖等级:full / partial / graze(S 档最小无法降档,部分盖也按全盖处理成胶囊)
const neighborLevel = (h, n) => {
  const c = hoverCoverage(h, n)
  if (c >= NEIGHBOR_FULL || (c >= NEIGHBOR_PARTIAL && displayTier(n) === 'S')) return 'full'
  if (c >= NEIGHBOR_PARTIAL) return 'partial'
  return 'graze'
}
// 模板/tierOf 用:某组件当前是哪个覆盖等级的邻居(非邻居返回 '')
const neighborLevelOf = (w) => {
  if (!HOVER_CAPABLE || editMode.value || !hoverExpands.value || hoverUid.value === w.uid) return ''
  const hovered = widgets.value.find(x => x.uid === hoverUid.value)
  if (!hovered || !hoverAffects(hovered, w)) return ''
  return neighborLevel(hovered, w)
}
const isFullNeighbor = (w) => neighborLevelOf(w) === 'full'
const isPartialNeighbor = (w) => neighborLevelOf(w) === 'partial'

// 生效档位:被 hover 的组件临时升一档;被部分盖的邻居临时降一档(内容随之少一些,全盖邻居缩成胶囊);其余保持基础档
const TIER_UP = { S: 'M', M: 'L' }
const TIER_DOWN = { XL: 'L', L: 'M', M: 'S' }
const DOWN_SCALE = { XL: 0.8, L: 0.75, M: 2 / 3 }
const downScaleOf = (w) => DOWN_SCALE[displayTier(w)] ?? NEIGHBOR_SCALE_FULL
const tierOf = (w) => {
  const base = displayTier(w)
  if (editMode.value || !HOVER_CAPABLE) return base
  if (hoverUid.value === w.uid) return w.id === 'weather' ? 'XL' : (TIER_UP[base] || base)
  if (isPartialNeighbor(w)) return TIER_DOWN[base] || base
  return base
}
// 当前悬停组件是否真的会放大:天气放大到巨大(XL 前都放大),其余仅中小档放大——只有放大时才推挤邻居
const hoverExpands = computed(() => {
  if (!HOVER_CAPABLE || editMode.value) return false
  const h = widgets.value.find(x => x.uid === hoverUid.value)
  if (!h) return false
  if (h.id === 'weather') return displayTier(h) !== 'XL'
  return displayTier(h) === 'S' || displayTier(h) === 'M'
})

const setHover = (w) => {
  if (editMode.value || !HOVER_CAPABLE) return
  if (hoverTimer) clearTimeout(hoverTimer)
  hoverTimer = setTimeout(() => { hoverUid.value = w.uid }, HOVER_DELAY)
}
const clearHover = () => {
  if (hoverTimer) { clearTimeout(hoverTimer); hoverTimer = null }
  hoverUid.value = null
}
const onCardEnter = (w) => { setHover(w); if (w.id === 'album') onAlbumEnter() }
const onCardLeave = () => { clearHover(); onAlbumLeave() }

// 卡片 px 矩形(cardStyle 同源格子数学)
const cardRectPx = (w) => {
  const cw = cellW.value, ch = cellH.value
  const left = MARGIN.left + w.col * (cw + GAP)
  const top = MARGIN.top + w.row * (ch + GAP)
  const width = w.w * cw + (w.w - 1) * GAP
  const height = w.h * ch + (w.h - 1) * GAP
  return { left, top, width, height, cx: left + width / 2, cy: top + height / 2 }
}
// 放大矩形 px
const hoverRectPx = (h) => {
  const r = hoverRect(h)
  const cw = cellW.value, ch = cellH.value
  const left = MARGIN.left + r.left * (cw + GAP)
  const top = MARGIN.top + r.top * (ch + GAP)
  const width = r.w * cw + (r.w - 1) * GAP
  const height = r.hgt * ch + (r.hgt - 1) * GAP
  return { left, top, width, height, cx: left + width / 2, cy: top + height / 2 }
}
// 最小推挤位移:沿「远离放大中心」方向,把缩放后的邻居推到与放大矩形刚好不相交 + 缓冲
const hoverPush = (hovered, n, scale) => {
  const R = hoverRectPx(hovered)
  const N = cardRectPx(n)
  const dx = N.cx - R.cx, dy = N.cy - R.cy
  const len = Math.hypot(dx, dy) || 1
  const ux = dx / len, uy = dy / len
  const rHalf = (R.width / 2) * Math.abs(ux) + (R.height / 2) * Math.abs(uy)
  const nHalf = (N.width / 2 * scale) * Math.abs(ux) + (N.height / 2 * scale) * Math.abs(uy)
  const push = Math.max(0, rHalf + nHalf - len) + NEIGHBOR_PUSH_BUFFER
  return { x: ux * push, y: uy * push }
}
// GSAP 动效目标(纯 transform,不真实重排):hover 卡片放大,邻居推挤/缩放
const hoverTarget = (w) => {
  if (editMode.value || !HOVER_CAPABLE || !hoverExpands.value) return null
  if (hoverUid.value === w.uid) {
    const sc = hoverScale(w)
    if (sc <= 1) return null
    const r = hoverRect(w)
    return { x: 0, y: 0, scale: sc, origin: `${(r.originX * 100).toFixed(1)}% ${(r.originY * 100).toFixed(1)}%`, z: 80 }
  }
  const hovered = widgets.value.find(x => x.uid === hoverUid.value)
  if (!hovered || !hoverAffects(hovered, w)) return null
  const level = neighborLevel(hovered, w)
  if (level === 'graze') return { x: 0, y: 0, scale: NEIGHBOR_SCALE_GRAZE, origin: '50% 50%', z: 10 }
  if (level === 'partial') { const p = hoverPush(hovered, w, downScaleOf(w)); return { x: p.x, y: p.y, scale: downScaleOf(w), origin: '50% 50%', z: 10 } }
  const p = hoverPush(hovered, w, NEIGHBOR_SCALE_FULL); return { x: p.x, y: p.y, scale: NEIGHBOR_SCALE_FULL, origin: '50% 50%', z: 10 }
}
// 仅 z-index(非动画,进 cardBoxStyle 响应式样式;transform 由 GSAP 接管)
const hoverZ = (w) => { const t = hoverTarget(w); return t ? t.z : null }
const cardBoxStyle = (w) => {
  const s = cardStyle(w)
  const hz = hoverZ(w)
  if (hz) s.zIndex = hz
  return s
}
// ---- GSAP 弹性动效接管 transform:进入 back.out(回弹)/ 邻居 elastic.out(水波);离开反向回弹复原 ----
const cardEls = {}
const setCardEl = (uid, el) => { if (el) cardEls[uid] = el; else delete cardEls[uid] }
const applyHoverTweens = () => {
  gsap.killTweensOf('.dash-card')
  for (const w of visibleWidgets.value) {
    const el = cardEls[w.uid]
    if (!el) continue
    const t = hoverTarget(w)
    const hero = w.id === 'weather'
    if (t) {
      gsap.to(el, { x: t.x, y: t.y, scale: t.scale, transformOrigin: t.origin, duration: 0.7, ease: hero ? 'back.out(1.4)' : 'elastic.out(1, 0.5)' })
    } else {
      gsap.to(el, { x: 0, y: 0, scale: 1, transformOrigin: '50% 50%', duration: 0.6, ease: hero ? 'back.out(1.2)' : 'elastic.out(1, 0.5)' })
    }
  }
}
watch([hoverUid, hoverExpands, editMode], () => { nextTick(applyHoverTweens) })

// ========== P3 天气 AI 生图背景(排版方案 §4.4) ==========
const WEATHER_IMAGE_FEATURE = 'WEATHER_IMAGE' // 设置-家庭AI配置「功能绑定」里独立绑定的天气生图功能
const weatherBg = ref('')
const weatherBgLoading = ref(false)
const WEATHER_BG_CACHE = 'ihomy:weather-bg:v1'
const WEATHER_CFG_KEY = 'ihomy:weather-bg-config:v1'
const WEATHER_CFG_DEFAULT = { enabled: true, style: '温柔插画风格', size: '2048x2048', refreshDays: 7, scene: '', watermark: false }
const readWeatherCfg = () => {
  try {
    const raw = localStorage.getItem(WEATHER_CFG_KEY)
    return raw ? { ...WEATHER_CFG_DEFAULT, ...JSON.parse(raw) } : { ...WEATHER_CFG_DEFAULT }
  } catch { return { ...WEATHER_CFG_DEFAULT } }
}
let aiStatusChecked = false
let aiImageAvail = false
const seasonLabel = () => { const m = new Date().getMonth() + 1; return (m >= 3 && m <= 5) ? '春' : (m >= 6 && m <= 8) ? '夏' : (m >= 9 && m <= 11) ? '秋' : '冬' }
const dayNightNow = () => { const h = new Date().getHours(); return (h >= 6 && h < 19) ? 'day' : 'night' }
// 日期与分时(进 prompt;缓存键仍用粗粒度 day/night,避免分时频繁触发重生成)
const dateLabel = () => { const d = new Date(); return `${d.getMonth() + 1}月${d.getDate()}日` }
const timeOfDayLabel = () => {
  const h = new Date().getHours()
  if (h < 5) return '凌晨'
  if (h < 8) return '清晨'
  if (h < 11) return '上午'
  if (h < 14) return '午后'
  if (h < 17) return '下午'
  if (h < 19) return '傍晚'
  return '夜晚'
}
// 风格/地点随机池:风格不固定(摄影/手绘/油画…),地点不固定(突出天气氛围,弱化地标)
const WEATHER_STYLES = ['电影感摄影', '水彩手绘', '油画', '极简插画', '复古胶片', '日系动漫', '水墨淡彩']
const WEATHER_SCENES = ['城市街道', '公园', '海边', '山间', '郊野', '湖边', '窗前']
const pickRandom = (arr) => arr[Math.floor(Math.random() * arr.length)]
const weatherBgKey = () => [weather.value?.city, weather.value?.text, weather.value?.iconCode, dayNightNow(), seasonLabel()].join('|')
const loadWeatherBg = () => {
  const key = weatherBgKey()
  if (!key || !weather.value) return
  // 读取「天气背景 AI 生成」配置(AiPlayground 面板);关闭则回落渐变
  const cfg = readWeatherCfg()
  if (cfg.enabled === false) return
  const ttl = Math.max(1, (cfg.refreshDays ?? 7)) * 86400000
  // 分组缓存 + 保鲜(排版方案:一周一换)
  try {
    const cache = JSON.parse(localStorage.getItem(WEATHER_BG_CACHE) || '{}')
    const hit = cache[key]
    if (hit && Date.now() - hit.ts < ttl) { weatherBg.value = hit.url; return }
  } catch {}
  if (!userStore.isLoggedIn) return // /ai/* 需登录,公开首页仅渐变兜底
  if (weatherBgLoading.value) return
  weatherBgLoading.value = true
  const attempt = async () => {
    try {
      if (!aiStatusChecked) {
        try { aiImageAvail = !!(await aiApi.status())?.weatherImage?.available } catch {}
        aiStatusChecked = true
      }
      if (!aiImageAvail) return
      const prompt = `${pickRandom(WEATHER_STYLES)},${dateLabel()}${timeOfDayLabel()},${seasonLabel()}季,${weather.value?.city || ''} ${weather.value?.text || ''},${pickRandom(WEATHER_SCENES)},突出天气氛围,弱化地点地标,柔和高级色调,高清#`
      const res = await aiApi.image({
        prompt,
        size: cfg.size || '2048x2048',
        watermark: cfg.watermark === true, // 背景图默认不带水印,配置里显式开才带
      }, WEATHER_IMAGE_FEATURE)
      const first = res?.[0] || {}
      const url = first.url || (first.b64_json ? 'data:image/png;base64,' + first.b64_json : '')
      if (url) {
        weatherBg.value = url
        try { const c = JSON.parse(localStorage.getItem(WEATHER_BG_CACHE) || '{}'); c[key] = { url, ts: Date.now() }; localStorage.setItem(WEATHER_BG_CACHE, JSON.stringify(c)) } catch {}
        saveWeatherBgToAlbum(url) // AI 生图落库到家庭相册,不阻塞背景显示
      }
    } catch (e) { /* 无模型/出错回落渐变+毛玻璃 */ }
    finally { weatherBgLoading.value = false }
  }
  attempt()
}

// AI 生图落库:找到或新建「AI 生图」相册(private 类型 → 照片 FAMILY 家庭内可见),再把图片保存为照片
const WEATHER_ALBUM_NAME = 'AI 生图'
let weatherAlbumPromise = null
const ensureWeatherAlbum = () => {
  if (weatherAlbumPromise) return weatherAlbumPromise
  weatherAlbumPromise = (async () => {
    try {
      const albums = await albumApi.list()
      const found = (albums || []).find((a) => a.name === WEATHER_ALBUM_NAME)
      if (found) return found.id
      const created = await albumApi.create({ name: WEATHER_ALBUM_NAME, type: 'private' })
      return created.id
    } catch (e) {
      weatherAlbumPromise = null // 失败允许下次重试
      return null
    }
  })()
  return weatherAlbumPromise
}
const saveWeatherBgToAlbum = async (imgUrl) => {
  try {
    const albumId = await ensureWeatherAlbum()
    if (!albumId || !imgUrl) return
    // 网络图走后端下载落库,避免浏览器跨域 fetch 拦截;data: 由后端解码
    if (/^https?:\/\//i.test(imgUrl) || /^data:/i.test(imgUrl)) {
      await photoApi.saveFromUrl(albumId, { url: imgUrl })
    }
  } catch (e) { /* 相册保存失败静默(仅影响 AI 图归档,不影响背景展示) */ }
}
watch([() => weather.value?.city, () => weather.value?.text, () => weather.value?.iconCode], () => { if (weather.value) loadWeatherBg() }, { immediate: true })

// ========== P3 找物语音输入(浏览器 Web Speech API,无需后端 ASR 模型) ==========
const voiceListening = ref(false)
let voiceRecog = null
const startVoice = () => {
  const SR = window.SpeechRecognition || window.webkitSpeechRecognition
  if (!SR) { ElMessage.info('当前浏览器不支持语音输入'); return }
  try {
    voiceRecog = new SR()
    voiceRecog.lang = 'zh-CN'
    voiceRecog.interimResults = false
    voiceRecog.maxAlternatives = 1
    voiceRecog.onresult = (e) => {
      const txt = (e.results?.[0]?.[0]?.transcript || '').trim()
      if (txt) { itemKeyword.value = txt; searchItems() }
    }
    voiceRecog.onend = () => { voiceListening.value = false }
    voiceRecog.onerror = () => { voiceListening.value = false; ElMessage.error('语音识别失败') }
    voiceRecog.start()
    voiceListening.value = true
  } catch (e) { voiceListening.value = false }
}
const stopVoice = () => { if (voiceRecog) { try { voiceRecog.stop() } catch {} } voiceListening.value = false }
const toggleVoice = () => { voiceListening.value ? stopVoice() : startVoice() }
onBeforeUnmount(() => { if (voiceRecog) { try { voiceRecog.abort() } catch {} } })

// ========== P3 找物组件户型图(排版方案 §4.3,只读) ==========
const itemHouses = ref([])
const itemHouseId = ref(null)
const itemFloor = ref(1)
const homeFloorPlan = ref({ rooms: [], furnitures: [], items: [], imageUrl: null, scale: 100 })
const fpFitKey = ref(0)
const floorPlanOpacity = ref(1)
const floorPlanImg = ref({ x: 0, y: 0, k: 1 })
// 底图不透明度/变换(平移缩放)存于楼层配置 houses[i].floorPlans[floor] 内,
// 与物品定位页 floorPlanOpacity/floorPlanImg 口径一致——首页预览必须同样应用,否则底图按 k=1 自然比例渲染,与户型图错位。
const applyFloorConfig = () => {
  const house = itemHouses.value.find((h) => Number(h.id) === Number(itemHouseId.value))
  let opacity = 1
  let img = { x: 0, y: 0, k: 1 }
  if (house && house.floorPlans) {
    try {
      const cfg = JSON.parse(house.floorPlans)[itemFloor.value]
      opacity = cfg?.opacity ?? 1
      if (cfg?.img) img = { ...cfg.img }
    } catch {}
  }
  floorPlanOpacity.value = opacity
  floorPlanImg.value = img
}
const fpHighlight = computed(() => searchResults.value.map(r => r.id))
const hasFloorPlan = computed(() => homeFloorPlan.value.rooms.length || homeFloorPlan.value.imageUrl || homeFloorPlan.value.items.length)
// 命中导航:上/下一个 + 放大居中(与 item 页 focusItem 同款:切到命中房子/楼层 → 重载户型图 → 聚焦)
const matchIdx = ref(0)
const fpCanvasRef = ref(null)
const currentMatch = computed(() => searchResults.value[matchIdx.value] || null)
const focusMatch = async (idx) => {
  const it = searchResults.value[idx]
  if (!it) return
  matchIdx.value = idx
  let switched = false
  if (it.house_id != null && Number(it.house_id) !== Number(itemHouseId.value)) { itemHouseId.value = Number(it.house_id); switched = true }
  if (it.floor != null && Number(it.floor) !== Number(itemFloor.value)) { itemFloor.value = Number(it.floor); switched = true }
  if (switched) await loadFloorPlanPreview()
  await nextTick()
  fpCanvasRef.value?.focusItem(it.id)
}
// 搜索命中后的初始定位:与物品定位页 autoSwitchToHits/focusFirstHit 口径一致——
// 优先当前房子当前楼层,其次当前房子的其他楼层(有 1 楼取 1 楼,否则最高层),最后才跨房子取第一个命中。
const pickInitialMatch = () => {
  const hits = searchResults.value
  if (!hits.length) return -1
  const inHouse = (it) => it.house_id != null && Number(it.house_id) === Number(itemHouseId.value)
  const onFloor = (it, f) => it.floor != null && !Number.isNaN(Number(it.floor)) && Number(it.floor) === Number(f)
  let i = hits.findIndex((it) => inHouse(it) && onFloor(it, itemFloor.value))
  if (i >= 0) return i
  const houseHits = hits.filter(inHouse)
  const floors = [...new Set(houseHits.filter((it) => it.floor != null && !Number.isNaN(Number(it.floor))).map((it) => Number(it.floor)))]
  if (floors.length) {
    const target = floors.includes(1) ? 1 : Math.max(...floors)
    i = hits.findIndex((it) => inHouse(it) && onFloor(it, target))
    if (i >= 0) return i
  }
  return 0
}
const focusInitialMatch = async () => {
  const idx = pickInitialMatch()
  if (idx >= 0) await focusMatch(idx)
  else matchIdx.value = 0
}
const prevMatch = () => { if (matchIdx.value > 0) focusMatch(matchIdx.value - 1) }
const nextMatch = () => { if (matchIdx.value < searchResults.value.length - 1) focusMatch(matchIdx.value + 1) }
// 与 item 页 defaultFloorOf 口径一致:默认楼层 = floorPlans 键 ∪ 该房房间楼层;
// 有 1 楼选 1 楼,无 1 楼选最高层。硬编码 floor=1 会在无 1 楼(如 -1/3/15)的房子上拉到空户型图。
const defaultFloorOfPreview = async (house) => {
  const set = new Set()
  if (house && house.floorPlans) {
    try {
      const fp = JSON.parse(house.floorPlans)
      Object.keys(fp).forEach((k) => { if (k !== 'floorOrder') set.add(Number(k)) })
    } catch {}
  }
  try {
    const houseRooms = await itemApi.rooms(house.id)
    ;(houseRooms || []).forEach((r) => set.add(r.floor))
  } catch {}
  if (set.has(1)) return 1
  if (set.size) return Math.max(...set)
  return 1
}
const loadItemHouses = async () => {
  if (!userStore.isLoggedIn) return
  try {
    itemHouses.value = (await itemApi.houses()) || []
    if (itemHouses.value.length && !itemHouseId.value) {
      const house = itemHouses.value[0]
      itemHouseId.value = house.id
      itemFloor.value = await defaultFloorOfPreview(house)
      await loadFloorPlanPreview()
    }
  } catch (e) {}
}
const loadFloorPlanPreview = async () => {
  if (!itemHouseId.value) return
  applyFloorConfig()
  try {
    const data = await itemApi.floorPlan(itemHouseId.value, itemFloor.value)
    homeFloorPlan.value = data
    fpFitKey.value++
  } catch (e) {}
}

// ========== 拖拽 + 缩放(栅格吸附) ==========
let zCounter = 20
let dragState = null

const bringToFront = (w) => {
  zCounter = Math.min(zCounter + 1, 59)
  w._z = zCounter
}

const onDragStart = (e, w) => {
  if (e.target.classList.contains('resize-corner') || e.target.classList.contains('del-btn')) return
  w._dragging = true
  zCounter = Math.min(zCounter + 1, 59)
  w._z = zCounter
  dragState = { w, startX: e.clientX, startY: e.clientY, startCol: w.col, startRow: w.row }
  e.preventDefault()
  window.addEventListener('mousemove', onMouseMove)
  window.addEventListener('mouseup', onMouseUp)
}

const onResizeStart = (e, w) => {
  w._dragging = true
  zCounter = Math.min(zCounter + 1, 59)
  w._z = zCounter
  dragState = { w, startX: e.clientX, startY: e.clientY, startW: w.w, startH: w.h, isResize: true }
  e.preventDefault()
  window.addEventListener('mousemove', onMouseMove)
  window.addEventListener('mouseup', onMouseUp)
}

const onMouseMove = (e) => {
  if (!dragState) return
  const { w } = dragState
  const cw = cellW.value
  const ch = cellH.value
  const dx = e.clientX - dragState.startX
  const dy = e.clientY - dragState.startY
  const dCol = Math.round(dx / (cw + GAP))
  const dRow = Math.round(dy / (ch + GAP))
  if (dragState.isResize) {
    const nw = Math.max(1, Math.min(COLS - w.col, dragState.startW + dCol))
    const nh = Math.max(1, Math.min(ROWS - w.row, dragState.startH + dRow))
    const snapped = snapSize(nw, nh, w.col, w.row)
    w.w = snapped.w
    w.h = snapped.h
  } else {
    w.col = Math.max(0, Math.min(COLS - w.w, dragState.startCol + dCol))
    w.row = Math.max(0, Math.min(ROWS - w.h, dragState.startRow + dRow))
  }
}

const onMouseUp = () => {
  if (dragState) { dragState.w._dragging = false; saveLayout() }
  dragState = null
  window.removeEventListener('mousemove', onMouseMove)
  window.removeEventListener('mouseup', onMouseUp)
}

onUnmounted(() => { window.removeEventListener('mousemove', onMouseMove); window.removeEventListener('mouseup', onMouseUp) })

// ========== 从侧边栏拖入组件 ==========
const WIDGET_LABELS = { feed: '家人动态', task: '悬赏任务', today: '今日', weather: '天气', anni: '纪念日', recipe: '今日推荐', search: '寻物', wish: '愿望单', finance: '本月收支', album: '相册' }
const WIDGET_ICONS = { feed: '👥', task: '🎯', today: '📅', weather: '🌤', anni: '🎂', recipe: '🍳', search: '🔍', wish: '⭐', finance: '💰', album: '📷' }
const ghostActive = ref(false)
const ghostGrown = ref(false)
const ghostX = ref(0)
const ghostY = ref(0)
const ghostLabel = ref('')

const { dragging: wdDragging, dragType: wdType, dragX: wdX, dragY: wdY, crossed: wdCrossed, onDrop } = useWidgetDrag()

watch(wdDragging, (active) => {
  if (active) {
    ghostActive.value = true
    ghostGrown.value = false
    ghostLabel.value = WIDGET_LABELS[wdType.value] || wdType.value
  } else {
    ghostActive.value = false
    ghostGrown.value = false
  }
})

watch(wdX, () => { ghostX.value = wdX.value; ghostY.value = wdY.value })
watch(wdCrossed, (crossed) => {
  if (crossed) { ghostGrown.value = true }
})

onDrop((type, x, y) => {
  const size = WIDGET_DEFAULT_SIZE[type] || { w: 3, h: 3 }
  const cw = cellW.value
  const ch = cellH.value
  const col = Math.max(0, Math.min(COLS - size.w, Math.round((x - MARGIN.left) / (cw + GAP))))
  const row = Math.max(0, Math.min(ROWS - size.h, Math.round((y - MARGIN.top) / (ch + GAP))))
  widgets.value.push(makeWidget({ id: type, col, row, w: size.w, h: size.h }))
  saveLayout()
  ElMessage.success(`已添加 ${WIDGET_LABELS[type] || type} 组件`)
})

onMounted(() => {
  loadAll(); loadPoints(); loadReminders(); loadBookSummary(); loadWishes(); loadTodayRecipes(); loadItemHouses()
  nextTick(() => { if (!root.value) return; ctx = gsap.context(() => { gsap.from('.dash-card', { y: 16, autoAlpha: 0, duration: 0.4, stagger: 0.04, ease: 'power2.out' }) }, root.value) })
})
onBeforeUnmount(() => { ctx?.revert(); gsap.killTweensOf('.dash-card') })
</script>

<style scoped>
.home-page { min-height: 100vh; }
.home-page.edit-mode { user-select: none; }

/* 编辑模式工具栏(hover时隐藏,不挡操作) */
.edit-toolbar {
  position: fixed; top: 32px; right: 40px; z-index: 70;
  display: flex; align-items: center; gap: 10px;
  padding: 8px 16px; border-radius: 12px;
  background: rgba(255,255,255,0.6); backdrop-filter: blur(20px) saturate(1.2);
  border: 1px solid rgba(255,255,255,0.5); box-shadow: 0 4px 16px rgba(58,46,34,0.1);
  transition: opacity 0.3s ease, transform 0.3s ease;
}
.edit-toolbar-hover:hover { opacity: 0; transform: translateY(-10px); pointer-events: none; }
html.dark .edit-toolbar { background: rgba(var(--color-card-rgb),0.6); border-color: rgba(255,255,255,0.1); }
.edit-label { font-size: 12px; opacity: 0.6; }

/* 栅格背景 */
.grid-overlay {
  position: fixed; z-index: 15; pointer-events: none;
  display: grid;
  grid-template-columns: repeat(12, var(--cell-w));
  grid-template-rows: repeat(9, var(--cell-h));
  gap: 40px;
}
.grid-cell { border: 1px dashed rgba(var(--color-brand-rgb),0.15); border-radius: 8px; opacity: 0; animation: cellAppear 0.4s ease forwards; }
html.dark .grid-cell { border-color: rgba(var(--color-brand-rgb),0.1); }
@keyframes cellAppear { from { opacity: 0; transform: scale(0.8); } to { opacity: 1; transform: scale(1); } }
.grid-fade-enter-active, .grid-fade-leave-active { transition: opacity 0.3s ease; }
.grid-fade-enter-from, .grid-fade-leave-to { opacity: 0; }

/* 卡片通用 */
.dash-card {
  position: fixed; z-index: 20;
  display: flex; flex-direction: column;
  background: rgba(255,255,255,0.42);
  backdrop-filter: blur(28px) saturate(1.4);
  -webkit-backdrop-filter: blur(28px) saturate(1.4);
  border: 1px solid rgba(255,255,255,0.5);
  border-radius: 20px;
  box-shadow: 0 8px 28px rgba(58,46,34,0.1), inset 0 1px 0 rgba(255,255,255,0.6);
  color: #3A2E22; overflow: hidden;
  transition: box-shadow 0.25s ease;
  contain: layout style;
}
.dash-card:not(.edit-active):hover { box-shadow: 0 12px 40px rgba(58,46,34,0.18); }
.dash-card.edit-active {
  cursor: default;
  border-color: rgba(var(--color-brand-rgb),0.3);
  box-shadow: 0 4px 16px rgba(var(--color-brand-rgb),0.15);
  transition: left 0.15s cubic-bezier(0.4,0,0.2,1), top 0.15s cubic-bezier(0.4,0,0.2,1), width 0.15s cubic-bezier(0.4,0,0.2,1), height 0.15s cubic-bezier(0.4,0,0.2,1), box-shadow 0.25s ease;
}
.dash-card.dragging { opacity: 0.9; }
html.dark .dash-card { background: rgba(var(--color-card-rgb),0.5); border-color: rgba(255,255,255,0.1); color: #E8DCC8; box-shadow: 0 8px 28px rgba(0,0,0,0.25), inset 0 1px 0 rgba(255,255,255,0.06); }

.card-inner { flex: 1; display: flex; flex-direction: column; overflow: hidden; min-height: 0; }
.dash-card.edit-active .card-inner { pointer-events: none; }
.card-head { padding: 10px 18px 6px; font-size: 13px; font-weight: 600; opacity: 0.7; flex-shrink: 0; transition: opacity 0.3s ease, max-height 0.3s ease, padding 0.3s ease, margin 0.3s ease; max-height: 30px; overflow: hidden; }
html.dark .card-head { opacity: 0.75; }
/* h=1时标题行消失,内容占满 */
.dash-card.h-1 .card-head { opacity: 0; max-height: 0; padding: 0; margin: 0; }
.dash-card.h-1 .card-scroll { padding-top: 10px; }
.card-scroll { flex: 1; overflow-y: auto; padding: 0 18px 14px; min-height: 0; transform: translateZ(0); }
.card-scroll::-webkit-scrollbar { width: 0; }
.card-more { font-size: 13px; color: var(--color-brand); text-decoration: none; padding: 0 18px 10px; display: block; font-weight: 500; transition: color 0.15s, transform 0.15s; }
.card-more:hover { color: #a06a4e; }
html.dark .card-more { color: var(--color-brand); }
.empty-hint { text-align: center; padding: 16px 12px; font-size: 13px; opacity: 0.4; font-style: italic; }
.widget-empty { font-size: 12px; color: #9a9088; padding: 14px 0; text-align: center; line-height: 1.6; }
.widget-empty-hint { font-size: 11px; color: #b0a89e; margin-top: 4px; }
html.dark .widget-empty { color: rgba(232,220,200,0.3); }

/* 编辑模式拖拽条/删除/缩放 */
.drag-bar { height: 18px; flex-shrink: 0; display: flex; align-items: center; justify-content: center; cursor: grab; }
.drag-bar:active { cursor: grabbing; }
.grip { width: 32px; height: 3px; border-radius: 2px; background: rgba(var(--color-brand-rgb),0.3); }
.del-btn { position: absolute; top: 4px; right: 4px; width: 20px; height: 20px; border: none; border-radius: 50%; background: rgba(201,116,116,0.15); color: #c97474; font-size: 11px; cursor: pointer; z-index: 10; display: flex; align-items: center; justify-content: center; }
.del-btn:hover { background: rgba(201,116,116,0.3); }
.resize-corner { position: absolute; bottom: 0; right: 0; width: 18px; height: 18px; cursor: nwse-resize; background: linear-gradient(135deg, transparent 50%, rgba(var(--color-brand-rgb),0.25) 50%); border-bottom-right-radius: 20px; z-index: 10; }

/* 家人动态 */
.feed-row { display: flex; align-items: flex-start; gap: 10px; margin-bottom: 10px; cursor: pointer; }
.feed-avatar { flex-shrink: 0; }
.feed-content { flex: 1; min-width: 0; margin-top: 2px; }
.feed-nick { font-size: 12px; font-weight: 600; color: rgba(58,46,34,0.8); margin-bottom: 2px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
html.dark .feed-nick { color: rgba(232,220,200,0.8); }
.feed-bubble { background: rgba(255,255,255,0.4); border-radius: 4px 14px 14px 14px; padding: 8px 12px; transition: background 0.2s, transform 0.2s; }
.feed-row:hover .feed-bubble { background: rgba(255,255,255,0.6); }
html.dark .feed-bubble { background: rgba(255,255,255,0.06); }
html.dark .feed-row:hover .feed-bubble { background: rgba(255,255,255,0.1); }
.bubble-type { font-size: 11px; opacity: 0.4; margin-bottom: 2px; }
.bubble-body { font-size: 13px; line-height: 1.5; display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; }
.bubble-time { font-size: 10px; opacity: 0.3; margin-top: 3px; text-align: right; }

/* 任务 */
.task-row { display: flex; align-items: center; gap: 10px; padding: 6px 8px; border-radius: 8px; cursor: pointer; transition: background 0.2s; }
.task-row:hover { background: rgba(58,46,34,0.05); }
html.dark .task-row:hover { background: rgba(255,255,255,0.04); }
.task-reward { font-size: 16px; }
.task-info { flex: 1; min-width: 0; }
.task-title { font-size: 13px; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.task-meta { font-size: 11px; opacity: 0.4; margin-top: 2px; display: flex; align-items: center; gap: 4px; }
.task-status-dot { width: 6px; height: 6px; border-radius: 50%; flex-shrink: 0; background: #C9A876; }
.task-status-dot.s-0 { background: #C9A876; } .task-status-dot.s-1 { background: #6b9b6b; } .task-status-dot.s-2 { background: #d4a843; } .task-status-dot.s-3 { background: #8a9a8a; } .task-status-dot.s-4 { background: #b06a58; opacity: 0.5; }
html.dark .task-status-dot.s-0 { background: #c4a884; } html.dark .task-status-dot.s-1 { background: #7dba7d; } html.dark .task-status-dot.s-2 { background: #d4b86a; } html.dark .task-status-dot.s-3 { background: #6a7a6a; } html.dark .task-status-dot.s-4 { background: #c97474; opacity: 0.5; }

/* 今日 */
.today-points { display: flex; align-items: center; gap: 14px; padding: 2px 0 10px; }
.tp-item { display: flex; flex-direction: column; align-items: center; cursor: pointer; padding: 4px 8px; border-radius: 10px; transition: background 0.2s; }
.tp-item:hover { background: rgba(58,46,34,0.05); }
html.dark .tp-item:hover { background: rgba(255,255,255,0.04); }
.tp-num { font-size: 20px; font-weight: 700; color: #A8483A; line-height: 1; }
.tp-label { font-size: 11px; opacity: 0.45; margin-top: 3px; }
html.dark .tp-num { color: var(--color-brand); }
.today-reminders { border-top: 1px solid rgba(58,46,34,0.06); padding-top: 4px; }
html.dark .today-reminders { border-color: rgba(232,220,200,0.05); }
.today-reminder { display: flex; align-items: center; gap: 8px; padding: 6px 4px; border-radius: 8px; cursor: pointer; transition: background 0.2s; }
.today-reminder:hover { background: rgba(58,46,34,0.04); }
html.dark .today-reminder:hover { background: rgba(255,255,255,0.04); }
.tr-dot { width: 6px; height: 6px; border-radius: 50%; background: #C9A876; flex-shrink: 0; }
.tr-title { flex: 1; min-width: 0; font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.tr-time { font-size: 11px; opacity: 0.35; font-variant-numeric: tabular-nums; }

/* 天气(精简:图标+当前温度+今日最低最高+文字;点击进详情页) */
.weather-scroll { text-align: center; }
.weather-clickable { cursor: pointer; }
.weather-main { padding: 10px 12px 12px; border-radius: 14px; background: rgba(255,255,255,0.52); backdrop-filter: blur(10px) saturate(1.2); -webkit-backdrop-filter: blur(10px) saturate(1.2); border: 1px solid rgba(255,255,255,0.45); box-shadow: 0 2px 12px rgba(58,46,34,0.08); }
html.dark .weather-main { background: rgba(var(--color-card-rgb),0.58); border-color: rgba(255,255,255,0.12); box-shadow: 0 2px 12px rgba(0,0,0,0.2); }
.weather-city { font-size: 13px; opacity: 0.5; }
.weather-current { display: flex; align-items: center; justify-content: center; gap: 8px; margin: 4px 0 2px; }
.weather-icon-float { font-size: 36px; animation: icon-float 3s ease-in-out infinite; display: inline-block; }
@keyframes icon-float { 0%,100% { transform: translateY(0); } 50% { transform: translateY(-4px); } }
.weather-temp-large { font-size: 42px; font-weight: 700; line-height: 1; }
.temp-unit { font-size: 24px; opacity: 0.6; }
.weather-hilo { display: flex; flex-direction: column; align-items: flex-start; gap: 2px; margin-left: 4px; }
.wh-item { font-size: 12px; font-weight: 600; line-height: 1.1; opacity: 0.75; font-variant-numeric: tabular-nums; }
.wh-hi { color: #c07a4a; }
.wh-lo { color: #6a8ab0; }
.weather-condition { font-size: 14px; opacity: 0.7; font-weight: 500; }
.weather-loading-text { font-size: 13px; opacity: 0.4; padding: 20px; }
.weather-forecast { margin-top: 10px; display: flex; flex-direction: column; gap: 4px; text-align: left; }
.wf-row { display: flex; align-items: center; gap: 8px; font-size: 12px; padding: 3px 6px; border-radius: 6px; }
.wf-row:hover { background: rgba(58,46,34,0.04); }
html.dark .wf-row:hover { background: rgba(255,255,255,0.04); }
.wf-date { width: 44px; opacity: 0.6; flex-shrink: 0; }
.wf-icon { font-size: 16px; flex-shrink: 0; }
.wf-range { flex: 1; text-align: right; font-variant-numeric: tabular-nums; opacity: 0.8; }

/* 纪念日 */
.anni-row { display: flex; align-items: center; justify-content: space-between; padding: 8px 6px; border-bottom: 1px solid rgba(58,46,34,0.05); cursor: pointer; transition: background 0.2s; }
.anni-row:last-child { border-bottom: none; }
.anni-row:hover { background: rgba(58,46,34,0.04); border-radius: 8px; }
html.dark .anni-row { border-color: rgba(232,220,200,0.05); }
html.dark .anni-row:hover { background: rgba(255,255,255,0.04); }
.anni-info { flex: 1; min-width: 0; }
.anni-name { font-size: 13px; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.anni-date { font-size: 11px; opacity: 0.4; margin-top: 2px; }
.anni-days { display: flex; align-items: baseline; gap: 2px; flex-shrink: 0; margin-left: 6px; }
.days-num { font-size: 20px; font-weight: 700; color: #A8483A; }
.days-unit { font-size: 11px; opacity: 0.5; }
html.dark .days-num { color: var(--color-brand); }

/* 愿望单 */
.wish-list { display: flex; flex-direction: column; gap: 5px; }
.wish-item { display: flex; align-items: center; gap: 8px; font-size: 12px; color: #3A2E22; padding: 4px 6px; border-radius: 8px; transition: background 0.2s; }
html.dark .wish-item { color: #E8DCC8; }
.wish-item.done { opacity: 0.4; text-decoration: line-through; }
.wish-item:hover { background: rgba(58,46,34,0.04); }
html.dark .wish-item:hover { background: rgba(255,255,255,0.04); }
.wish-dot { width: 6px; height: 6px; border-radius: 50%; flex-shrink: 0; background: #c4a884; }
.wish-dot.ACHIEVED { background: #6b9b6b; } .wish-dot.ABANDONED { background: #b06a58; }
.wish-name { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

/* 寻物(item 页缩小版):前端搜索改为上/下一个导航,不再用扁平列表 */

/* 菜谱 */
.recipe-list { display: flex; flex-direction: column; gap: 6px; }
.recipe-item { display: flex; align-items: center; gap: 10px; text-decoration: none; padding: 4px 6px; border-radius: 8px; transition: background 0.2s; }
.recipe-item:hover { background: rgba(58,46,34,0.04); }
html.dark .recipe-item:hover { background: rgba(255,255,255,0.04); }
.recipe-cover { width: 36px; height: 36px; border-radius: 8px; object-fit: cover; flex-shrink: 0; }
.recipe-cover.placeholder { display: flex; align-items: center; justify-content: center; background: #ede5d8; font-size: 16px; }
html.dark .recipe-cover.placeholder { background: rgba(232,220,200,0.06); }
.recipe-name { font-size: 12px; font-weight: 500; color: #3A2E22; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
html.dark .recipe-name { color: #E8DCC8; }

/* 收支 */
.finance-body { display: flex; gap: 20px; padding: 4px 18px 8px; }
.fin-item { display: flex; flex-direction: column; gap: 2px; }
.fin-label { font-size: 10px; opacity: 0.4; color: #9a9088; }
.fin-val { font-size: 18px; font-weight: 700; font-variant-numeric: tabular-nums; }
.fin-val.income { color: #6b9b6b; } .fin-val.expense { color: #b06a58; }
html.dark .fin-val.income { color: #7dba7d; } html.dark .fin-val.expense { color: #d9806a; }

/* 音乐 */
.music-list { display: flex; flex-direction: column; gap: 4px; }
.music-pl-name { font-size: 13px; font-weight: 600; color: var(--color-brand); margin-bottom: 4px; }
html.dark .music-pl-name { color: var(--color-brand); }
.music-item { display: flex; align-items: center; gap: 8px; padding: 4px 6px; border-radius: 8px; transition: background 0.2s; }
.music-item:hover { background: rgba(58,46,34,0.04); }
html.dark .music-item:hover { background: rgba(255,255,255,0.04); }
.music-title { font-size: 12px; font-weight: 500; flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: #3A2E22; }
html.dark .music-title { color: #E8DCC8; }
.music-artist { font-size: 10px; opacity: 0.4; flex-shrink: 0; }
.music-empty-icon { font-size: 28px; margin-bottom: 4px; }

/* 拍立得(照片可溢出组件边界,z-index高于其他卡片) */
.album-container { position: relative; width: 100%; flex: 1; overflow: visible; }
.polaroid-stack { position: relative; width: 100%; height: 100%; }
.polaroid-pos { position: absolute; top: 50%; left: 50%; width: var(--polaroid-w, 120px); margin-left: calc(var(--polaroid-w, 120px) / -2); margin-top: calc(var(--polaroid-w, 120px) * -0.567); }
.polaroid { background: #fff; padding: 6px 6px 22px; box-shadow: 0 6px 18px rgba(0,0,0,0.25); border-radius: 2px; cursor: pointer; transition: transform 0.3s ease; }
.polaroid:hover { transform: scale(1.15); z-index: 99 !important; }
.polaroid-pos:hover { z-index: 99 !important; }
.polaroid img { width: 100%; aspect-ratio: 4/3; object-fit: cover; display: block; }
.polaroid-caption { position: absolute; bottom: 4px; left: 4px; right: 4px; font-size: 9px; color: #5a4a3a; text-align: center; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.album-closed { position: absolute; top: 50%; left: 50%; aspect-ratio: 4/3; transform: translate(-50%, -50%); cursor: pointer; perspective: 1100px; }
.album-book { position: relative; width: 100%; height: 100%; transform-style: preserve-3d; }
/* 内页:封面翻开后露出的照片轮播(去 overflow:hidden 以免压平 3D 翻页) */
.album-page { position: absolute; inset: 0; perspective: 900px; border-radius: 4px; background: linear-gradient(160deg, #fbf6ec 0%, #f3ead7 100%); box-shadow: 0 6px 18px rgba(0,0,0,0.25); }
/* 翻页照片卡片:管 3D 翻转(不淡出);正面照片/背面纸感背景各自淡出,避免 opacity 压平 preserve-3d */
.album-page-card { position: absolute; inset: 0; transform-style: preserve-3d; transform-origin: left center; transition: transform 1.5s ease; }
.album-page-img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; display: block; border-radius: 4px; backface-visibility: hidden; transition: opacity 1.5s ease; }
.album-page-back { position: absolute; inset: 0; backface-visibility: hidden; transform: rotateY(180deg); background: linear-gradient(160deg, #fdfaf3 0%, #f5eedd 100%); border-radius: 4px; transition: opacity 1.5s ease; }
.album-page-empty { display: flex; align-items: center; justify-content: center; padding: 12px; font-size: 12px; color: #8B6F47; }
/* 封面:沿书脊翻到 145° 露出内衬;合上时对称地从 145° 回到 0°,与翻开同一节奏(1.5s) */
.album-cover { position: absolute; inset: 0; transform-style: preserve-3d; transform-origin: left center; transform: rotateY(0deg); transition: transform 1.5s ease-in-out; }
/* 封面正面:老式皮质相册封面图(asset 为 800×600 预压缩版,勿直接换回 1.4MB 原图) */
.album-cover-front { position: absolute; inset: 0; backface-visibility: hidden; border-radius: 4px; overflow: hidden; box-shadow: 0 6px 18px rgba(0,0,0,0.3); transition: opacity 1.5s ease-in-out; }
.album-cover-img { width: 100%; height: 100%; object-fit: cover; display: block; }
.album-cover-back { position: absolute; inset: 0; backface-visibility: hidden; transform: rotateY(180deg); background: linear-gradient(160deg, #f3ead7 0%, #e7d9be 100%); border-radius: 4px; box-shadow: 0 6px 18px rgba(0,0,0,0.25); transition: opacity 1.5s ease-in-out; }
.album-book.open .album-cover { transform: rotateY(-145deg); }
.album-book.open .album-cover-front { opacity: 0; }
.album-book.open .album-cover-back { opacity: 0; }
/* 旧照片翻页:卡片翻转 145°(1.5s),正/背面各自淡出(1.5s),置于上层;新照片静躺不动,置于下层等待被翻开 */
.album-page-leave-active { z-index: 2; }
.album-page-leave-to { transform: rotateY(-145deg); }
.album-page-leave-to .album-page-img { opacity: 0; }
.album-page-leave-to .album-page-back { opacity: 0; }
.album-page-enter-active { z-index: 1; }

/* album卡片本身允许溢出,拍立得z-index高于其他卡片 */
.dash-card.album { overflow: visible; }
.dash-card.album .card-inner { overflow: visible; }
.dash-card.album .polaroid-stack { z-index: 40; }

/* 拖拽幽灵 */
.drag-ghost {
  position: fixed; z-index: 200;
  width: 80px; height: 60px;
  margin-left: -40px; margin-top: -30px;
  display: flex; align-items: center; justify-content: center;
  background: rgba(var(--color-brand-rgb),0.3);
  backdrop-filter: blur(12px);
  border: 1px solid rgba(var(--color-brand-rgb),0.4);
  border-radius: 14px;
  pointer-events: none;
  transform: scale(0.3);
  transition: transform 0.3s cubic-bezier(0.34, 1.56, 0.64, 1), background 0.3s;
}
.drag-ghost.ghost-grown {
  transform: scale(1);
  background: rgba(var(--color-brand-rgb),0.15);
  width: 200px; height: 150px;
  margin-left: -100px; margin-top: -75px;
}
.ghost-label { font-size: 12px; font-weight: 600; color: #3A2E22; white-space: nowrap; }
html.dark .ghost-label { color: #E8DCC8; }

/* ===== P2 hover:放大的卡片 + 被推开的邻居(全盖→胶囊/部分盖→降档缩小/擦边→轻微缩小) + 波浪引导 ===== */
.dash-card.is-hovered { box-shadow: 0 20px 52px rgba(58,46,34,0.24); transition: box-shadow 0.3s ease; }
html.dark .dash-card.is-hovered { box-shadow: 0 20px 52px rgba(0,0,0,0.4); }
/* 邻居 transform 由 GSAP elastic.out 接管(水波弹性形变),此处只隐藏内容显示胶囊 */
.dash-card.is-neighbor .card-inner { opacity: 0; pointer-events: none; }
.neighbor-capsule { position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 4px; z-index: 5; }
.neighbor-capsule .nc-icon { font-size: 20px; line-height: 1; }
.neighbor-capsule .nc-label { font-size: 11px; font-weight: 600; opacity: 0.7; }
/* 小档未展开时的波浪提示:引导 hover 查看更多(方案 §2.2) */
.dash-card.wave-hint::after { content: 'hover 查看更多'; position: absolute; bottom: 6px; left: 50%; transform: translateX(-50%); font-size: 10px; opacity: 0; color: #a06a4e; animation: wavePulse 2.4s ease-in-out infinite; pointer-events: none; }
html.dark .dash-card.wave-hint::after { color: var(--color-brand); }
@keyframes wavePulse { 0%,100% { opacity: 0; transform: translateX(-50%) translateY(0); } 50% { opacity: 0.75; transform: translateX(-50%) translateY(-3px); } }

/* ===== P3 天气 AI 生图底图(渐变+毛玻璃兜底) ===== */
.dash-card.weather .card-inner { position: relative; z-index: 1; }
.dash-card.weather .card-inner > .card-head,
.dash-card.weather .card-inner > .card-scroll { position: relative; z-index: 1; }
.weather-bg { position: absolute; inset: 0; z-index: 0; overflow: hidden; border-radius: 20px; pointer-events: none; }
.weather-bg-img { position: absolute; inset: -8%; background-size: cover; background-position: center; filter: saturate(1.08) contrast(1.02); transition: opacity 0.4s ease; }
.weather-bg-grad { position: absolute; inset: 0; background: linear-gradient(115deg, rgba(255,255,255,0.82) 0%, rgba(255,255,255,0.5) 34%, rgba(255,255,255,0.14) 62%, rgba(255,255,255,0.05) 100%); }
html.dark .weather-bg-grad { background: linear-gradient(115deg, rgba(var(--color-card-rgb),0.8) 0%, rgba(var(--color-card-rgb),0.5) 34%, rgba(var(--color-card-rgb),0.16) 62%, rgba(var(--color-card-rgb),0.06) 100%); }
/* 巨大档(XL)悬停时:内容面板更透,让 AI 生图背景透出来,文字区靠局部描边/投影保证可读 */
.dash-card.weather.is-hovered .weather-main { background: rgba(255,255,255,0.34); backdrop-filter: blur(6px) saturate(1.15); -webkit-backdrop-filter: blur(6px) saturate(1.15); border-color: rgba(255,255,255,0.35); box-shadow: 0 2px 14px rgba(58,46,34,0.16), 0 0 0 1px rgba(255,255,255,0.12) inset; text-shadow: 0 1px 2px rgba(255,255,255,0.5); }
html.dark .dash-card.weather.is-hovered .weather-main { background: rgba(var(--color-card-rgb),0.4); border-color: rgba(255,255,255,0.16); text-shadow: 0 1px 3px rgba(0,0,0,0.5); }

/* ===== P3 寻物组件 = 缩小版 item 页:户型图自适应大小 + 命中放大居中 + 上/下一个导航 ===== */
.dash-card.search .card-scroll { display: flex; flex-direction: column; overflow: hidden; padding: 0 14px 8px; }
.fp-wrap { position: relative; flex: 1; min-height: 0; border-radius: 16px; overflow: hidden; border: 1px solid rgba(var(--color-brand-rgb),0.22); background: #f6efe4; }
html.dark .fp-wrap { border-color: rgba(var(--color-brand-rgb),0.15); background: var(--color-card-2); }
.fp-no-plan { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; font-size: 11px; color: #9a9088; padding: 0 12px; text-align: center; }
html.dark .fp-no-plan { color: rgba(232,220,200,0.4); }
/* 当前命中物品 */
.fp-match-chip { position: absolute; left: 8px; top: 8px; max-width: calc(100% - 16px); display: flex; flex-direction: column; gap: 2px; padding: 5px 9px; border-radius: 10px; background: rgba(255,255,255,0.66); backdrop-filter: blur(10px) saturate(1.2); border: 1px solid rgba(255,255,255,0.5); box-shadow: 0 4px 12px rgba(58,46,34,0.14); z-index: 2; pointer-events: none; }
html.dark .fp-match-chip { background: rgba(var(--color-card-rgb),0.72); border-color: rgba(255,255,255,0.1); }
.fm-name { font-size: 12px; font-weight: 600; color: #3A2E22; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.fm-src { font-style: normal; font-size: 9px; font-weight: 600; color: #b8860b; background: rgba(232,160,48,0.16); border-radius: 6px; padding: 1px 5px; margin-left: 6px; vertical-align: 1px; }
.fm-loc { font-size: 10px; color: #9a9088; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
html.dark .fm-name { color: #E8DCC8; }
html.dark .fm-src { color: #e8b04b; background: rgba(232,176,75,0.16); }
html.dark .fm-loc { color: rgba(232,220,200,0.5); }
/* 搜索进行/无结果提示(覆盖在户型图上,居中) */
.fp-search-hint { position: absolute; left: 50%; top: 50%; transform: translate(-50%, -50%); font-size: 11px; color: #9a9088; padding: 6px 12px; border-radius: 10px; background: rgba(255,255,255,0.7); backdrop-filter: blur(10px) saturate(1.2); border: 1px solid rgba(255,255,255,0.5); z-index: 2; pointer-events: none; white-space: nowrap; }
html.dark .fp-search-hint { color: rgba(232,220,200,0.55); background: rgba(var(--color-card-rgb),0.72); border-color: rgba(255,255,255,0.1); }
/* 上/下一个 命中导航(左右箭头) */
.fp-nav { position: absolute; left: 50%; bottom: 10px; transform: translateX(-50%); display: flex; align-items: center; gap: 10px; padding: 4px 8px; border-radius: 999px; background: rgba(255,255,255,0.7); backdrop-filter: blur(12px) saturate(1.2); border: 1px solid rgba(255,255,255,0.55); box-shadow: 0 4px 14px rgba(58,46,34,0.16); z-index: 2; }
html.dark .fp-nav { background: rgba(var(--color-card-rgb),0.78); border-color: rgba(255,255,255,0.1); }
.fp-nav-btn { width: 26px; height: 26px; display: flex; align-items: center; justify-content: center; border-radius: 50%; border: 1px solid rgba(var(--color-brand-rgb),0.4); background: rgba(var(--color-brand-rgb),0.12); color: var(--color-brand); font-size: 16px; line-height: 1; cursor: pointer; transition: background 0.2s, transform 0.2s, color 0.2s, opacity 0.2s; }
.fp-nav-btn:hover:not(:disabled) { background: rgba(var(--color-brand-rgb),0.28); color: #a06a4e; transform: scale(1.08); }
.fp-nav-btn:active:not(:disabled) { transform: scale(0.94); }
.fp-nav-btn:disabled { opacity: 0.32; cursor: default; }
html.dark .fp-nav-btn { border-color: rgba(var(--color-brand-rgb),0.35); background: rgba(var(--color-brand-rgb),0.12); color: var(--color-brand); }
.fp-nav-count { font-size: 11px; font-weight: 600; color: #3A2E22; font-variant-numeric: tabular-nums; letter-spacing: 0.3px; min-width: 30px; text-align: center; }
html.dark .fp-nav-count { color: #E8DCC8; }
.search-input-row { display: flex; align-items: center; gap: 6px; margin-bottom: 8px; }
.search-input-row .el-input { flex: 1; }
.voice-btn { flex-shrink: 0; width: 32px; height: 32px; display: inline-flex; align-items: center; justify-content: center; border-radius: 50%; border: 1px solid rgba(var(--color-brand-rgb),0.4); background: rgba(var(--color-brand-rgb),0.12); color: var(--color-brand); cursor: pointer; font-size: 15px; transition: background 0.2s, transform 0.2s; }
.voice-btn:hover { background: rgba(var(--color-brand-rgb),0.25); }
.voice-btn.on { background: rgba(201,116,116,0.28); color: #c97474; animation: voicePulse 1s ease-in-out infinite; }
@keyframes voicePulse { 0%,100% { transform: scale(1); } 50% { transform: scale(1.12); } }
html.dark .voice-btn { border-color: rgba(var(--color-brand-rgb),0.35); background: rgba(var(--color-brand-rgb),0.12); color: var(--color-brand); }
html.dark .voice-btn:hover { background: rgba(var(--color-brand-rgb),0.22); }

/* Transition */
.fade-enter-active, .fade-leave-active { transition: opacity 0.2s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }

@media (max-width: 960px) {
  .dash-card { display: none; }
  .home-page::before { content: '请使用电脑或平板访问首页仪表盘'; position: fixed; top: 50%; left: 50%; transform: translate(-50%,-50%); font-size: 14px; opacity: 0.4; }
}
</style>
