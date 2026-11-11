<!-- 日程提醒页:近期条 + 月历 + 当日详情 + 待安排 + 日历/清单切换。
     地址栏兼容:?date=YYYY-MM-DD 选中该日;?tab=today 选中今天;?tab=plan 清单(全部);
     ?tab=anniversary 清单(纪念日);?tab=plan&focus=<planId> 清单定位高亮。 -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('schedule.title') }]" />

    <PageToolbar>
      <div class="tb-left">
        <el-radio-group v-model="view" size="small">
          <el-radio-button value="calendar">{{ $t('schedule.view.calendar') }}</el-radio-button>
          <el-radio-button value="list">{{ $t('schedule.view.list') }}</el-radio-button>
        </el-radio-group>
        <el-select v-if="view === 'list'" v-model="filter" size="small" class="filter-select" @change="onFilterChange">
          <el-option v-for="f in TYPE_FILTERS" :key="f" :label="filterLabel(f)" :value="f" />
        </el-select>
      </div>
      <div class="tb-right">
        <el-popover v-if="userStore.isLoggedIn" placement="bottom-end" trigger="click" :width="200">
          <template #reference>
            <el-button size="small" plain :aria-label="$t('schedule.sources')">
              <el-icon><Filter /></el-icon>
            </el-button>
          </template>
          <div class="source-panel">
            <div class="section-label">{{ $t('schedule.sources') }}</div>
            <el-checkbox
              v-for="s in SOURCE_OPTIONS"
              :key="s"
              class="source-item"
              :model-value="sourcePrefs[s]"
              @change="setSource(s, $event)"
            >
              {{ $t('schedule.source.' + s) }}
            </el-checkbox>
          </div>
        </el-popover>
        <el-button v-if="userStore.isLoggedIn" type="primary" @click="openCreate(selectedDate)">
          {{ $t('schedule.dialog.create') }}
        </el-button>
      </div>
    </PageToolbar>

    <div v-loading="loading">
      <ScheduleUpcoming v-if="view === 'calendar' && userStore.isLoggedIn" :items="visibleUpcoming" @pick="onPickUpcoming" />

      <!-- 宽屏双栏(≥900px 且屏幕短边 ≥600dp,判定唯一来源 useDevice.isWide):
           左月历、右当日 + 待安排,两列各自滚,页面不再被当日列表撑长。
           窄屏不加 `.wide-cols`,同一份 DOM 保持「月历 → 当日 → 待安排」的纵向顺序,
           不用 CSS order 重排(读屏顺序会错)。 -->
      <div v-if="view === 'calendar'" ref="wideEl" class="sched-wide" :class="{ 'wide-cols': viewWide }" :style="colStyle">
        <div class="wide-col sched-col-cal">
          <ScheduleCalendar
            v-model:selected="selectedDate"
            :grid="grid"
            :calendar-days="calendarDays"
            :events="visibleEventsDays"
            :year="cursor.year"
            :month="cursor.month"
            @change-month="changeMonth"
          />
        </div>
        <div class="wide-col sched-col-side">
          <ScheduleDayPanel
            :date="selectedDate"
            :items="dayItems"
            :info="dayInfo"
            :record-of="recordOf"
            @open="openDetail"
            @edit="openEdit"
            @delete="onDelete"
            @toggle="onToggle"
            @toggle-task="onToggleTask"
          />
          <!-- 宽屏把「待安排」收进右列底部(当日面板之后);窄屏同一位置,顺序与旧版一致 -->
          <ScheduleUndated v-if="showUndated" :items="undated" @arrange="openPlanEditor" @edit="openPlanEditor" @delete="onDeleteUndated" />
        </div>
      </div>

      <template v-else>
        <!-- 清单与月历共用同一份区间数据,所以翻月放在清单头上,否则进清单后无法换月 -->
        <div class="list-month card">
          <el-button size="small" text :aria-label="$t('schedule.prevMonth')" @click="changeMonth(-1)">
            <el-icon><ArrowLeft /></el-icon>
          </el-button>
          <span class="list-month-title">{{ $t('schedule.monthTitle', { year: cursor.year, month: cursor.month }) }}</span>
          <el-button size="small" text :aria-label="$t('schedule.nextMonth')" @click="changeMonth(1)">
            <el-icon><ArrowRight /></el-icon>
          </el-button>
        </div>
        <ScheduleList
          :groups="listGroups"
          :focus="focusId"
          :record-of="recordOf"
          :can-edit="userStore.isLoggedIn"
          @open="openDetail"
          @edit="openEdit"
          @delete="onDelete"
          @toggle="onToggle"
          @toggle-task="onToggleTask"
        />
      </template>

      <!-- 清单视图的待安排仍在清单之后(清单不是双栏) -->
      <ScheduleUndated
        v-if="view !== 'calendar' && showUndated"
        :items="undated"
        @arrange="openPlanEditor"
        @edit="openPlanEditor"
        @delete="onDeleteUndated"
      />
    </div>

    <ScheduleDialog v-model="editorVisible" :item="editing" :default-date="defaultDate" :members="members" @saved="onSaved" />
    <ScheduleDetail
      v-model="detailVisible"
      :kind="detailKind"
      :record="detailRecord"
      @edit="onDetailEdit"
      @delete="onDelete"
      @saved="onSaved"
    />
  </div>
</template>

<script setup>
// 数据分三路:月历基础数据(公共)+ 区间聚合(游客可读)+ 家庭明细(登录后,用于编辑与子任务)
// 切月只重拉前两路,家庭明细与近期提醒只在挂载/保存后刷新
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, ArrowRight, Filter } from '@element-plus/icons-vue'
import { anniversaryApi, memberApi, planApi, publicApi, reminderApi, scheduleApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { useDevice } from '@/composables/useDevice'
import { useWideCols } from '@/composables/useWideCols'
import { isNativeApp } from '@/utils/platform'
import { buildMonthGrid, isDateString, monthKey, monthRange, shiftMonth, todayString } from '@/utils/scheduleCalendar'
import { buildList, sortEvents } from '@/utils/scheduleList'
import { TYPE_FILTERS } from '@/utils/scheduleType'
import {
  SOURCE_OPTIONS,
  filterEventDays,
  filterSourceItems,
  isReadOnlySource,
  loadSourcePrefs,
  saveSourcePrefs,
} from '@/utils/scheduleSource'
import Breadcrumb from '@/components/Breadcrumb.vue'
import PageToolbar from '@/components/PageToolbar.vue'
import ScheduleCalendar from './ScheduleCalendar.vue'
import ScheduleUpcoming from './ScheduleUpcoming.vue'
import ScheduleDayPanel from './ScheduleDayPanel.vue'
import ScheduleList from './ScheduleList.vue'
import ScheduleUndated from './ScheduleUndated.vue'
import ScheduleDialog from './dialogs/ScheduleDialog.vue'
import ScheduleDetail from './dialogs/ScheduleDetail.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
// 宽屏判定只在这里取一次(页面/模板禁止自己读 window.innerWidth);
// 别名 viewWide:useGameView 里同名的是「游戏宽屏开关」,语义无关
const { isWide: viewWide } = useDevice()

const today = todayString()
const view = ref('calendar')
const filter = ref('ALL')
const selectedDate = ref(today)
const cursor = reactive({ year: Number(today.slice(0, 4)), month: Number(today.slice(5, 7)) })
const focusId = ref(null)

const calendarDays = ref({})
const eventsDays = ref({})
const undated = ref([])
const upcoming = ref([])
const sourcePrefs = ref(loadSourcePrefs())
const anniversaries = ref([])
const plans = ref([])
const reminders = ref([])
const members = ref([])
const loading = ref(false)

const editorVisible = ref(false)
const editing = ref(null)
const defaultDate = ref('')
const detailVisible = ref(false)
const detailRef = ref(null)

const grid = computed(() => buildMonthGrid(cursor.year, cursor.month))
// 来源开关只影响前端展示:后端仍返回全量,关掉某来源后当月圆点、当日列表、清单与近期条一起消失
const visibleEventsDays = computed(() => filterEventDays(eventsDays.value, sourcePrefs.value))
const visibleUpcoming = computed(() => filterSourceItems(upcoming.value, sourcePrefs.value))
const dayItems = computed(() => sortEvents(visibleEventsDays.value[selectedDate.value] || []))
const dayInfo = computed(() => calendarDays.value[selectedDate.value] || null)
const listGroups = computed(() => buildList(visibleEventsDays.value, filter.value))
// 待安排只对登录用户有意义(游客拿到的是空数组,也从未请求过)
const showUndated = computed(() => userStore.isLoggedIn && undated.value.length > 0)

// ---- 宽屏双栏:列内滚动净高 ----
// 量法抽在 composables/useWideCols(相册 / 文件 / 图书馆共用同一份,这里只负责在
// 「进出宽屏、切视图、近期条/待安排出现」时重新量)。
const { wideEl, colStyle, remeasure } = useWideCols(viewWide)

const onViewportChange = () => remeasure()
const detailKind = computed(() => detailRef.value?.kind || 'PLAN')
const detailRecord = computed(() => {
  if (!detailRef.value) return null
  const pool = detailRef.value.kind === 'ANNIVERSARY' ? anniversaries.value : plans.value
  return pool.find((x) => x.id === detailRef.value.id) || null
})

const filterLabel = (f) => t('schedule.filter.' + (f === 'ALL' ? 'all' : f))

// 事件 → 完整记录:纪念日看提前提醒,计划看子任务,提醒看重复与时刻
const recordOf = (it) => {
  if (it.kind === 'ANNIVERSARY') return anniversaries.value.find((a) => a.id === it.id) || null
  if (it.kind === 'REMINDER' || it.kind === 'TASK') return reminders.value.find((r) => r.id === it.id) || null
  if (it.kind === 'PLAN') return plans.value.find((p) => p.id === it.id) || null
  if (it.kind === 'PLAN_TASK') return plans.value.find((p) => p.id === it.planId) || null
  return null
}

// ---- 系统日历(仅安卓壳,用户主动开启后才读) ----
// 只做展示:不进近期条、不可勾选/编辑,按「标题+日期+时间」与站内事项去重;
// 未开启/未授权/网页端一律返回 null,loadMonth 原样用服务端数据。
const dayStart = (s) => {
  const [y, m, d] = String(s).split('-').map(Number)
  return new Date(y, (m || 1) - 1, d || 1)
}
const dayEnd = (s) => {
  const d = dayStart(s)
  d.setDate(d.getDate() + 1)
  return d
}
const loadDeviceEvents = async (from, to) => {
  try {
    const m = await import('@/native/calendarExport')
    return await m.readDeviceCalendar(dayStart(from), dayEnd(to))
  } catch (e) {
    return null
  }
}
const mergeDeviceEvents = (days, deviceItems) => {
  if (!deviceItems || !deviceItems.length) return days
  const seen = new Set()
  Object.values(days).forEach((list) =>
    (list || []).forEach((it) => seen.add(`${it.title}|${it.date}|${String(it.time || '').slice(0, 5)}`)),
  )
  const out = { ...days }
  for (const d of deviceItems) {
    const key = `${d.title}|${d.date}|${d.time || ''}`
    if (seen.has(key)) continue
    seen.add(key)
    if (!out[d.date]) out[d.date] = []
    out[d.date] = [
      ...out[d.date],
      {
        kind: 'DEVICE_CALENDAR',
        id: d.id,
        title: d.title,
        date: d.date,
        time: d.time || null,
        source: 'DEVICE_CALENDAR',
        readOnly: true,
      },
    ]
  }
  return out
}

// 月历基础数据 + 区间聚合:两次请求并行;切月只重拉这两个。
// pending 与 seq 只做两件事:同一月份的并发重复请求直接跳过、旧月份的慢响应丢弃,
// 否则快速连点翻月会被先发后到的响应覆盖成上个月。
let monthSeq = 0
let pendingKey = ''
const loadMonth = async () => {
  const key = monthKey(cursor.year, cursor.month)
  if (pendingKey === key) return
  pendingKey = key
  const seq = ++monthSeq
  loading.value = true
  try {
    const { from, to } = monthRange(cursor.year, cursor.month)
    const [cal, ev, dev] = await Promise.all([publicApi.calendar(key), scheduleApi.events(from, to), loadDeviceEvents(from, to)])
    if (seq !== monthSeq) return
    calendarDays.value = Object.fromEntries((cal?.days || []).map((d) => [d.date, d]))
    eventsDays.value = mergeDeviceEvents(ev?.days || {}, dev)
    undated.value = userStore.isLoggedIn && Array.isArray(ev?.undated) ? ev.undated : []
  } catch (e) {
    // 失败保留上一份数据,错误提示由 request 拦截器统一给出
  } finally {
    if (seq === monthSeq) {
      loading.value = false
      pendingKey = ''
    }
  }
}

// 近期提醒单拉一份:勾完成后它要跟着掉下去,不必为刷新顶部条重拉五个列表
const loadUpcoming = async () => {
  if (!userStore.isLoggedIn) return
  upcoming.value = await scheduleApi.upcoming(7).catch(() => [])
}

// 家庭明细:纪念日对游客也公开(只读);其余四类登录后才请求,游客不发注定 401 的请求
const loadLists = async () => {
  const loggedIn = userStore.isLoggedIn
  const [anni, mem, pl, rem, up] = await Promise.all([
    anniversaryApi.list().catch(() => []),
    loggedIn ? memberApi.list().catch(() => []) : Promise.resolve([]),
    loggedIn ? planApi.list().catch(() => []) : Promise.resolve([]),
    loggedIn ? reminderApi.list().catch(() => []) : Promise.resolve([]),
    loggedIn ? scheduleApi.upcoming(7).catch(() => []) : Promise.resolve([]),
  ])
  anniversaries.value = Array.isArray(anni) ? anni : []
  members.value = Array.isArray(mem) ? mem : []
  plans.value = Array.isArray(pl) ? pl : []
  reminders.value = Array.isArray(rem) ? rem : []
  upcoming.value = Array.isArray(up) ? up : []
}

const reloadAll = async () => {
  await Promise.all([loadMonth(), loadLists()])
  if (view.value === 'list') await scrollToFocus()
}

// ---- 地址栏:旧链接兼容 + 当前状态回写 ----
const applyQuery = () => {
  const q = route.query
  const qd = typeof q.date === 'string' ? q.date : ''
  // 一次性「新建提醒」(快捷方式):弹一次就清参,刷新不再弹;q 里其它参数(tab/type/focus/date)保留
  if (q.new === '1') {
    if (isDateString(qd)) {
      selectedDate.value = qd
      cursor.year = Number(qd.slice(0, 4))
      cursor.month = Number(qd.slice(5, 7))
    }
    if (userStore.isLoggedIn) openCreate(isDateString(qd) ? qd : selectedDate.value)
    const rest = { ...q }
    delete rest.new
    router.replace({ path: '/plan', query: rest }).catch(() => {})
    return
  }
  if (isDateString(qd)) {
    view.value = 'calendar'
    selectedDate.value = qd
    cursor.year = Number(qd.slice(0, 4))
    cursor.month = Number(qd.slice(5, 7))
    return
  }
  if (q.tab === 'today') {
    view.value = 'calendar'
    selectedDate.value = today
    cursor.year = Number(today.slice(0, 4))
    cursor.month = Number(today.slice(5, 7))
    return
  }
  if (q.tab === 'anniversary') {
    view.value = 'list'
    filter.value = 'ANNIVERSARY'
    focusId.value = null
    return
  }
  if (q.tab === 'plan') {
    view.value = 'list'
    filter.value = typeof q.type === 'string' && TYPE_FILTERS.includes(q.type) ? q.type : 'ALL'
    const nextFocus = q.focus != null ? String(q.focus) : null
    // 换了一条深链才重新允许抢定位;同一条深链被用户打断后保持不动
    if (nextFocus !== focusId.value) focusAborted = false
    focusId.value = nextFocus
    return
  }
  view.value = 'calendar'
  selectedDate.value = today
  cursor.year = Number(today.slice(0, 4))
  cursor.month = Number(today.slice(5, 7))
}

const syncQuery = () => {
  let query
  if (view.value === 'calendar') query = { date: selectedDate.value }
  else if (filter.value === 'ANNIVERSARY') query = { tab: 'anniversary' }
  else
    query = {
      tab: 'plan',
      ...(filter.value !== 'ALL' ? { type: filter.value } : {}),
      ...(focusId.value ? { focus: focusId.value } : {}),
    }
  const same = ['date', 'tab', 'type', 'focus'].every((k) => String(route.query[k] ?? '') === String(query[k] ?? ''))
  if (!same) router.replace({ path: '/plan', query })
}

// ---- 交互 ----
const changeMonth = (delta) => {
  const next = shiftMonth(cursor.year, cursor.month, delta)
  cursor.year = next.year
  cursor.month = next.month
  // 选中日不在新月份里时落到 1 号,避免详情停在上个月的某天
  const sel = selectedDate.value
  if (Number(sel.slice(0, 4)) !== next.year || Number(sel.slice(5, 7)) !== next.month) {
    selectedDate.value = `${monthKey(next.year, next.month)}-01`
  }
}

const onPickUpcoming = (it) => {
  view.value = 'calendar'
  selectedDate.value = it.date
  cursor.year = Number(it.date.slice(0, 4))
  cursor.month = Number(it.date.slice(5, 7))
  nextTick(() => document.getElementById('schedule-day')?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
}

const onFilterChange = () => {
  focusId.value = null
}

const openCreate = (date) => {
  editing.value = null
  defaultDate.value = date && isDateString(date) ? date : selectedDate.value
  editorVisible.value = true
}

const openEdit = (it) => {
  const record = recordOf(it)
  if (!record) return ElMessage.warning(t('schedule.detail.noData'))
  editing.value = { kind: it.kind, record }
  editorVisible.value = true
}

// 无日期计划:与编辑同一个弹窗,选日期即完成「安排日期」。
// 待安排列表来自聚合接口的精简项(没有说明/子任务),必须回查完整计划再填表,
// 否则保存会把说明清空;回查不到时宁可提示也不拿精简项硬填。
const openPlanEditor = (p) => {
  const record = plans.value.find((x) => x.id === p.id)
  if (!record) return ElMessage.warning(t('schedule.detail.noData'))
  editing.value = { kind: 'PLAN', record }
  editorVisible.value = true
}

const openDetail = (it) => {
  // 只读来源(贷款/理财/食材)不在日程页展开详情,直接跳原页面修改
  if (isReadOnlySource(it)) {
    if (it.link) router.push(it.link)
    return
  }
  if (it.kind === 'REMINDER' || it.kind === 'TASK') return openEdit(it)
  if (it.kind === 'PLAN_TASK') detailRef.value = { kind: 'PLAN', id: it.planId }
  else detailRef.value = { kind: it.kind, id: it.id }
  detailVisible.value = true
}

// 来源开关:只存本机偏好,不发请求、不改后端返回
const setSource = (source, on) => {
  sourcePrefs.value = { ...sourcePrefs.value, [source]: !!on }
  saveSourcePrefs(sourcePrefs.value)
}

const onDetailEdit = ({ kind, record }) => {
  detailVisible.value = false
  editing.value = { kind, record }
  editorVisible.value = true
}

const onToggle = async (it) => {
  // 循环提醒按当日勾选;一次性提醒忽略日期
  await reminderApi.toggleDone(it.id, it.kind === 'TASK' ? it.date : undefined)
  await Promise.all([loadMonth(), loadUpcoming()])
  // 原生壳内立即重排闹钟(勾完成的那期不再响);Web 端动态 import 直接跳过
  if (isNativeApp()) {
    import('@/native/reminders').then((m) => m.syncNotifications()).catch(() => {})
  }
}

const onToggleTask = async (it) => {
  await planApi.updateTask(it.id, { done: it.done ? 0 : 1 })
  await reloadAll()
}

// 删除有两条入口:日程行给的是 events 项({kind,id}),详情弹窗给的是 {kind,record},两种都要吃
const onDelete = async (payload) => {
  const record = payload.record || recordOf(payload)
  const id = record?.id ?? payload.id
  if (!id) return
  const title = payload.kind === 'ANNIVERSARY' ? record?.name : record?.title
  await ElMessageBox.confirm(t('schedule.dialog.deleteMessage', { title: title || '' }), t('common.deleteConfirm'), {
    type: 'warning',
    closeOnClickModal: true,
  })
  if (payload.kind === 'ANNIVERSARY') await anniversaryApi.remove(id)
  else if (payload.kind === 'REMINDER' || payload.kind === 'TASK') await reminderApi.remove(id)
  else if (payload.kind === 'PLAN') await planApi.remove(id)
  else return
  ElMessage.success(t('common.deleted'))
  detailVisible.value = false
  await reloadAll()
}

const onDeleteUndated = async (p) => {
  await ElMessageBox.confirm(t('schedule.dialog.deleteMessage', { title: p.title }), t('common.deleteConfirm'), {
    type: 'warning',
    closeOnClickModal: true,
  })
  await planApi.remove(p.id)
  ElMessage.success(t('common.deleted'))
  await reloadAll()
}

const onSaved = async (payload) => {
  // 新建了无日期计划或把计划改了日期时,待安排与月历都要跟着刷新
  detailVisible.value = false
  await reloadAll()
  if (payload?.kind === 'ITEM' || payload?.kind === 'PLAN') focusId.value = null
}

// 清单数据分批到、外壳挂载后还会把内容区滚回顶部、路由切换期布局仍在变高变矮:
// 单次滚动(含 scrollIntoView)会被后到的重排覆盖,实测 2.5s 后还会被推离目标位置。
// 做法:4 秒窗口内每 250ms 按「内容坐标绝对值」校正一次(幂等,已就位就跳过),
// 布局稳定后自然停在同一位置;离开页面立即停表。
// 用户主动滚动(wheel / touchmove / 翻页键)视为「我不要你定位了」,立刻停表且本次深链不再抢。
let focusTimer = null
let focusAborted = false
let focusEvtTarget = null
const FOCUS_KEYS = new Set(['ArrowUp', 'ArrowDown', 'PageUp', 'PageDown', 'Home', 'End', ' '])

const onFocusInterrupt = (e) => {
  // 不用 scroll 事件判断:自己的校正也会触发 scroll,会立刻自我中断
  if (e.type === 'keydown' && !FOCUS_KEYS.has(e.key)) return
  focusAborted = true
  stopFocusScroll()
}

const unbindFocusInterrupt = () => {
  if (!focusEvtTarget) return
  focusEvtTarget.removeEventListener('wheel', onFocusInterrupt)
  focusEvtTarget.removeEventListener('touchmove', onFocusInterrupt)
  focusEvtTarget.removeEventListener('keydown', onFocusInterrupt)
  focusEvtTarget = null
}

const stopFocusScroll = () => {
  if (focusTimer) clearInterval(focusTimer)
  focusTimer = null
  unbindFocusInterrupt()
}

// 监听挂在实际滚动容器上(暖居 .gc-main / 光尘 window);容器第一 tick 之后才稳定,允许换绑
const bindFocusInterrupt = () => {
  if (!focusId.value) return
  const node = document.getElementById('schedule-row-PLAN-' + focusId.value)
  const box = node?.closest('.gc-main')
  const target = box && box.scrollHeight > box.clientHeight + 10 ? box : window
  if (target === focusEvtTarget) return
  unbindFocusInterrupt()
  focusEvtTarget = target
  target.addEventListener('wheel', onFocusInterrupt, { passive: true })
  target.addEventListener('touchmove', onFocusInterrupt, { passive: true })
  target.addEventListener('keydown', onFocusInterrupt)
}

const scrollToFocus = () => {
  // 本次深链已被用户打断:后续数据变化重新起表也不许抢
  if (focusAborted) return
  // 返回 true 表示已经就位(不需要再动),连续 3 次就位就停表
  const scroll = () => {
    if (!focusId.value || view.value !== 'list') return true
    const node = document.getElementById('schedule-row-PLAN-' + focusId.value)
    if (!node) return false
    const centered = (viewportHeight, nodeHeight) => (viewportHeight - nodeHeight) / 2
    // 以节点真正所在的滚动容器为准:暖居主题主区是 .gc-main,光尘走 window。
    // 挂载早期页面还可能不在外壳里,closest 为 null 时按 window 处理,后续 tick 再校正。
    const box = node.closest('.gc-main')
    if (box && box.scrollHeight > box.clientHeight + 10) {
      const nodeTop = node.getBoundingClientRect().top - box.getBoundingClientRect().top + box.scrollTop
      const max = box.scrollHeight - box.clientHeight
      const target = Math.max(0, Math.min(max, nodeTop - centered(box.clientHeight, node.offsetHeight)))
      if (Math.abs(box.scrollTop - target) > 4) {
        box.scrollTo({ top: target, behavior: 'instant' })
        return false
      }
      return true
    }
    const nodeTop = node.getBoundingClientRect().top + window.scrollY
    const target = Math.max(0, nodeTop - centered(window.innerHeight, node.offsetHeight))
    if (Math.abs(window.scrollY - target) > 4) {
      window.scrollTo({ top: target, behavior: 'instant' })
      return false
    }
    return true
  }
  let okStreak = 0
  let ticks = 0
  const tick = () => {
    bindFocusInterrupt()
    okStreak = scroll() ? okStreak + 1 : 0
    if (++ticks >= 24 || okStreak >= 3) stopFocusScroll()
  }
  stopFocusScroll()
  scroll()
  bindFocusInterrupt()
  focusTimer = setInterval(tick, 250)
}

watch(() => [cursor.year, cursor.month], loadMonth)
// 选中日落到别的月份(点「今天」、点补格日期、点近期条)时跟着换月,
// 否则详情显示的日子不在当前网格里,看不到它在月历上的圆点
watch(selectedDate, (d) => {
  if (!isDateString(d)) return
  const y = Number(d.slice(0, 4))
  const m = Number(d.slice(5, 7))
  if (y !== cursor.year || m !== cursor.month) {
    cursor.year = y
    cursor.month = m
  }
})
watch([view, filter, selectedDate, focusId], syncQuery)
watch(
  () => route.fullPath,
  () => applyQuery(),
)
watch(
  [() => view.value, () => listGroups.value, () => focusId.value],
  () => {
    if (view.value === 'list') scrollToFocus()
  },
  { flush: 'post' },
)
// 双栏净高会随这几件事变:进出宽屏、切回月历视图、近期条/待安排出现把顶边下推
watch([viewWide, () => view.value], onViewportChange)
watch([() => showUndated.value, () => visibleUpcoming.value.length, () => loading.value], onViewportChange)

onMounted(async () => {
  applyQuery()
  await reloadAll()
  scrollToFocus()
  await nextTick()
  remeasure()
})

onBeforeUnmount(() => {
  stopFocusScroll()
})
</script>

<style scoped>
.filter-select {
  width: 132px;
}
/* 宽屏双栏(≥900px 且短边 ≥600):62/38 在 .page 的 1100px 内容宽内分,不改全局宽度。
   列内滚动、不把页面撑高的通用规则在 main.css 的 .wide-col 段;窄屏没有 .wide-cols 类,
   同一份 DOM 退回纵向堆叠,顺序与旧版一致。 */
.wide-cols {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 38%;
  gap: 16px;
}
.sched-col-cal,
.sched-col-side {
  min-width: 0;
}
/* 右列顶部与左列齐平:单栏时当日面板靠 12px 上边距与前一块分开,双栏里不需要 */
.wide-cols .sched-col-side :deep(.day) {
  margin-top: 0;
}
.source-panel {
  display: flex;
  flex-direction: column;
}
.source-panel .section-label {
  margin: 0 0 4px;
}
.source-item {
  height: 28px;
  margin-right: 0;
}
.list-month {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-bottom: 12px;
  padding: 8px 12px;
}
.list-month-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text);
  min-width: 120px;
  text-align: center;
}
@media (max-width: 480px) {
  .list-month-title {
    min-width: 0;
  }
}
</style>
