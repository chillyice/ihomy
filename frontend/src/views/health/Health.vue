<!-- 健康数据页(/health):报表 / 睡眠 / 体征 / 活动 / 身体。
     健康数据本人独占(服务端只认登录者本人,传别人的 userId 一律 404),页面没有成员切换;
     要给别人看只能「分享报表」:生成一份冻结快照链接,7 天后自动失效。
     报表卡片与告警口径由后端 HealthReportService / HealthAlerts 决定,这里只渲染。 -->
<template>
  <div class="page health-page">
    <div class="page-toolbar card">
      <div class="tb-left">
        <span class="tb-note">{{ $t('health.selfOnly') }}</span>
      </div>
      <div class="tb-right">
        <el-button :disabled="!reportData" @click="shareVisible = true">
          <el-icon><Share /></el-icon>
          <span class="btn-text">{{ $t('health.share.entry') }}</span>
        </el-button>
        <el-button :loading="loading" @click="reloadAll">
          <el-icon><Refresh /></el-icon>
          <span class="btn-text">{{ $t('health.refresh') }}</span>
        </el-button>
        <router-link to="/settings?tab=health" class="tb-link">{{ $t('health.settings') }}</router-link>
      </div>
    </div>

    <el-tabs v-model="tab" class="health-tabs" @tab-change="onTabChange">
      <!-- 报表:默认显示最新一天的数据,卡片可自定义;分享走工具栏「分享」 -->
      <el-tab-pane :label="$t('health.tabs.report')" name="report">
        <div v-if="!reportHasData" class="card empty-card">
          <el-empty :image-size="88" :description="$t('health.noData')">
            <router-link to="/settings?tab=health">
              <el-button type="primary">{{ $t('health.setup') }}</el-button>
            </router-link>
          </el-empty>
        </div>
        <div v-else class="tab-body">
          <div class="report-actions">
            <el-button text @click="openCardDialog">
              <el-icon><Setting /></el-icon>
              <span class="btn-text">{{ $t('health.report.customize') }}</span>
            </el-button>
          </div>
          <HealthReport :report="reportData" />
        </div>
      </el-tab-pane>

      <!-- 睡眠:范围 tab(日/周/月/半年/一年)→ 顶部主卡(日=进度轴,其余=睡眠时间统计)+ 原来那四张卡 -->
      <el-tab-pane :label="$t('health.tabs.sleep')" name="sleep">
        <div class="tab-body">
          <div class="chip-row">
            <button
              v-for="r in SLEEP_RANGES"
              :key="r"
              type="button"
              class="metric-chip"
              :class="{ active: sleepRange === r }"
              @click="selectSleepRange(r)"
            >
              {{ $t(`health.sleep.range.${r.toLowerCase()}`) }}
            </button>
          </div>

          <div class="card chart-card">
            <div class="chart-head">
              <span class="section-label">{{ sleepRange === 'DAY' ? $t('health.sleep.lane') : $t('health.sleep.stats') }}</span>
              <span class="range-note">{{ sleepSpanText }}</span>
            </div>

            <!-- 日:某一晚的睡眠进度轴,横轴钟点、纵轴四行分期 -->
            <template v-if="sleepRange === 'DAY'">
              <template v-if="dayNight">
                <div class="detail-head">
                  <span>{{ $t('health.sleep.start') }} {{ clockText(dayNight.startTime) }}</span>
                  <span>{{ $t('health.sleep.end') }} {{ clockText(dayNight.endTime) }}</span>
                  <span>{{ sleepText($t, dayNight.totalMin) }}</span>
                </div>
                <HealthSleepNight
                  v-if="dayStages.length"
                  :stages="dayStages"
                  :start-time="dayNight.startTime"
                  :end-time="dayNight.endTime"
                  :label="$t('health.sleep.lane')"
                />
                <div v-else class="empty-hint">{{ detailLoading ? $t('health.loading') : $t('health.sleep.noStages') }}</div>
                <div class="stage-legend">
                  <span v-for="s in legend" :key="s.code" class="legend-item">
                    <i class="legend-dot" :style="{ background: s.color }" />{{ $t(s.labelKey) }}
                  </span>
                </div>
              </template>
              <div v-else class="empty-hint">{{ $t('health.sleep.noData') }}</div>
            </template>

            <!-- 周/月=每天睡了多久;半年/年=每月平均每晚 -->
            <template v-else>
              <div v-if="statsSummary.avg != null" class="stat-row">
                <span v-if="statsSummary.total != null" class="stat-inline">
                  {{ $t('health.trend.total') }} <b>{{ sleepText($t, statsSummary.total) }}</b>
                </span>
                <span class="stat-inline">
                  {{ $t('health.sleep.avgNight') }} <b>{{ sleepText($t, statsSummary.avg) }}</b>
                </span>
                <span class="stat-inline">{{ $t('health.sleep.nights', { n: statsSummary.nights }) }}</span>
              </div>
              <HealthTrendChart
                v-if="statsSeries.values.length"
                mode="bar"
                :values="statsSeries.values"
                :labels="statsSeries.labels"
                :label="$t('health.sleep.stats')"
              />
              <div v-else class="empty-hint">{{ statsLoading ? $t('health.loading') : $t('health.sleep.noData') }}</div>
            </template>
          </div>

          <div class="card chart-card">
            <div class="section-label">{{ $t('health.sleep.duration') }}</div>
            <HealthTrendChart
              v-if="nights.length"
              mode="bar"
              :values="nightValues"
              :labels="nightLabels"
              :picked-index="pickedIndex"
              :label="metricName($t, 'SLEEP')"
              @pick="pickNight"
            />
            <div v-else class="empty-hint">{{ $t('health.sleep.noData') }}</div>
          </div>

          <div class="card chart-card">
            <div class="section-label">{{ $t('health.sleep.stages') }}</div>
            <template v-if="stageNights.length">
              <div v-for="n in stageNights" :key="n.id" class="stage-row">
                <span class="stage-date">{{ dayLabel(n.sleepDate) }}</span>
                <span class="stage-bar">
                  <i
                    v-for="seg in stackOf(n)"
                    :key="seg.code"
                    class="stage-seg"
                    :style="{ width: seg.pct + '%', background: colorOf(seg.code) }"
                    :title="segTitle(seg)"
                  />
                </span>
              </div>
              <div class="stage-legend">
                <span v-for="s in legend" :key="s.code" class="legend-item">
                  <i class="legend-dot" :style="{ background: s.color }" />{{ $t(s.labelKey) }}
                </span>
              </div>
            </template>
            <div v-else class="empty-hint">{{ $t('health.sleep.noStages') }}</div>
          </div>

          <div class="card chart-card">
            <div class="section-label">{{ $t('health.sleep.bedtime') }}</div>
            <HealthTrendChart
              v-if="nights.length"
              mode="scatter"
              :nights="nights"
              :labels="nightLabels"
              :label="$t('health.sleep.bedtime')"
            />
            <div v-else class="empty-hint">{{ $t('health.sleep.noData') }}</div>
          </div>

          <div class="card chart-card">
            <div class="section-label">{{ $t('health.sleep.detail') }}</div>
            <template v-if="pickedNight">
              <div class="detail-head">
                <span>{{ $t('health.sleep.start') }} {{ clockText(pickedNight.startTime) }}</span>
                <span>{{ $t('health.sleep.end') }} {{ clockText(pickedNight.endTime) }}</span>
                <span>{{ sleepText($t, pickedNight.totalMin) }}</span>
                <el-tag v-if="pickedNight.provider" size="small" type="info">{{ providerText(pickedNight.provider) }}</el-tag>
              </div>
              <div v-if="timeline.length" class="timeline">
                <i
                  v-for="(seg, i) in timeline"
                  :key="i"
                  class="timeline-seg"
                  :style="{ left: seg.left + '%', width: seg.width + '%', background: colorOf(seg.code) }"
                  :title="segTitle(seg)"
                />
              </div>
              <div v-else class="empty-hint">{{ detailLoading ? $t('health.loading') : $t('health.sleep.noStages') }}</div>
            </template>
            <div v-else class="empty-hint">{{ $t('health.sleep.pick') }}</div>
          </div>
        </div>
      </el-tab-pane>

      <!-- 体征 / 活动 / 身体:同一套版式,只换指标条 -->
      <el-tab-pane v-for="pane in metricPanes" :key="pane.name" :label="$t(pane.labelKey)" :name="pane.name">
        <div class="tab-body">
          <div class="chip-row">
            <button
              v-for="code in pane.codes"
              :key="code"
              type="button"
              class="metric-chip"
              :class="{ active: selected[pane.name] === code }"
              @click="selectMetric(pane.name, code)"
            >
              {{ metricName($t, code) }}
            </button>
          </div>

          <div class="card chart-card">
            <div class="chart-head">
              <span class="section-label">{{ metricName($t, selected[pane.name]) }}</span>
              <span class="range-note">{{ rangeText(selected[pane.name]) }}</span>
            </div>
            <div class="stat-row">
              <span class="stat-inline">
                {{ $t('health.trend.latest') }}
                <b>{{ withUnit(selected[pane.name], formatMetric(selected[pane.name], paneLatest(pane.name))) }}</b>
              </span>
              <span class="stat-inline">
                {{ $t('health.trend.avg') }}
                <b>{{ withUnit(selected[pane.name], formatMetric(selected[pane.name], paneAvg(pane.name))) }}</b>
              </span>
            </div>
            <HealthTrendChart
              v-if="paneSeries(pane.name).values.length"
              :mode="pane.name === 'activity' ? 'bar' : 'line'"
              :values="paneSeries(pane.name).values"
              :labels="paneSeries(pane.name).labels"
              :points="paneSeries(pane.name).points"
              :label="metricName($t, selected[pane.name])"
            />
            <div v-else class="empty-hint">{{ $t('health.trend.noData') }}</div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 报表卡片自定义:勾选即保存(本机 + 账号偏好);顺序按目录顺序,与分享弹窗同口径 -->
    <el-dialog v-model="cardDialog" :title="$t('health.report.customizeTitle')" class="dialog-sm" append-to-body>
      <el-checkbox-group v-model="cardDraft" class="card-picks">
        <el-checkbox
          v-for="c in pickableCards"
          :key="c.id"
          :value="c.id"
          :disabled="c.sensitive && !consentOn"
          :label="cardTitle($t, c.id)"
        />
      </el-checkbox-group>
      <p v-if="!consentOn" class="dialog-hint">{{ $t('health.share.sensitiveDisabled') }}</p>
      <template #footer>
        <el-button text @click="restoreCards">{{ $t('health.report.restore') }}</el-button>
        <el-button @click="cardDialog = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :disabled="!cardDraft.length" @click="saveCards">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <HealthShareDialog v-model="shareVisible" :report="reportData" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Refresh, Setting, Share } from '@element-plus/icons-vue'
import { healthApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { readPref, syncPref, writePref } from '@/utils/userPref'
import HealthTrendChart from './HealthTrendChart.vue'
import HealthSleepNight from './HealthSleepNight.vue'
import HealthReport from './HealthReport.vue'
import HealthShareDialog from './HealthShareDialog.vue'
import { DEFAULT_CARDS, HEALTH_REPORT_CARDS, cardTitle, parseCardsPref, sanitizeCards } from './reportCards'
import { stackBar, timelineSegments } from './healthChart'
import {
  SLEEP_STAGES,
  TAB_METRICS,
  clockText,
  dayLabel,
  formatMetric,
  metricName,
  periodOf,
  sleepText,
  stageMeta,
  withUnit,
} from '@/utils/healthMetrics'

const { t, locale } = useI18n()
const route = useRoute()
const userStore = useUserStore()

// 报表卡片偏好:本机 localStorage + sys_user_pref(键 health.report.v1),与首页布局同款同步规则
const CARD_PREF_LOCAL = 'ihomy:health:report:v1'
const CARD_PREF_SERVER = 'health.report.v1'

// 旧书签 /health?tab=overview 映射到「报表」
const LEGACY_TABS = { overview: 'report' }
const TABS = ['report', 'sleep', 'vitals', 'activity', 'body']
// 睡眠页范围:日=某一晚的进度轴,周/月=当期每天睡了多久,半年/年=每月平均每晚
const SLEEP_RANGES = ['DAY', 'WEEK', 'MONTH', 'HALF', 'YEAR']
// 一天多次采样的点指标只画今天,不然几百个点在 360px 上糊成一条带
const DENSE_POINT = ['HEART_RATE', 'SPO2', 'BODY_TEMP', 'BLOOD_GLUCOSE', 'BP_SYSTOLIC', 'BP_DIASTOLIC']
const RANGE_DAYS = 14
const SLEEP_DAYS = 14

const initialTab = LEGACY_TABS[String(route.query.tab || '')] || String(route.query.tab || '')
const tab = ref(TABS.includes(initialTab) ? initialTab : 'report')
const loading = ref(false)
const reportData = ref(null)
const reportCards = ref([...DEFAULT_CARDS])
const cardDraft = ref([...DEFAULT_CARDS])
const cardDialog = ref(false)
const shareVisible = ref(false)
const consentOn = ref(false)
const nights = ref([])
const series = ref({})
const pickedId = ref(null)
const sleepDetail = ref(null)
const detailLoading = ref(false)
const sleepRange = ref('DAY')
const statsBuckets = ref({})
const statsLoading = ref(false)
const selected = reactive({ vitals: 'HEART_RATE', activity: 'STEPS', body: 'WEIGHT' })

const metricPanes = [
  { name: 'vitals', labelKey: 'health.tabs.vitals', codes: TAB_METRICS.VITAL },
  { name: 'activity', labelKey: 'health.tabs.activity', codes: TAB_METRICS.ACTIVITY },
  { name: 'body', labelKey: 'health.tabs.body', codes: TAB_METRICS.BODY },
]

const legend = computed(() => SLEEP_STAGES.slice(0, 4))
// 未开采集同意时敏感卡片不可选(后端也会再丢一次,这里只负责别让用户白勾)
const pickableCards = computed(() => HEALTH_REPORT_CARDS.filter((c) => !c.sensitive || consentOn.value))
const reportHasData = computed(() => (reportData.value?.cards || []).some((c) => c.hasData))

const nightLabels = computed(() => nights.value.map((n) => dayLabel(n.sleepDate)))
const nightValues = computed(() => nights.value.map((n) => (n.totalMin == null ? null : Number(n.totalMin))))
const pickedIndex = computed(() => nights.value.findIndex((n) => n.id === pickedId.value))
const pickedNight = computed(() => nights.value.find((n) => n.id === pickedId.value) || null)
const stageNights = computed(() => nights.value.filter((n) => n.deepMin || n.lightMin || n.remMin))
const timeline = computed(() =>
  timelineSegments(
    sleepDetail.value?.stages,
    sleepDetail.value?.sleep?.startTime || pickedNight.value?.startTime,
    sleepDetail.value?.sleep?.endTime || pickedNight.value?.endTime,
  ),
)

// ===== 睡眠范围:日=选中那一晚;周/月按天合计;半年/年按月平均 =====
const dayNight = computed(() => pickedNight.value)
const dayStages = computed(() => sleepDetail.value?.stages || [])

/** 范围窗口(周从本周一起、月从 1 号起;半年/年按月需要 6/12 个自然月) */
const sleepWindow = computed(() => {
  const to = shiftDay(0)
  if (sleepRange.value === 'WEEK') return { from: weekStartDay(), to, bucket: 'DAY' }
  if (sleepRange.value === 'MONTH') return { from: monthStartDay(), to, bucket: 'DAY' }
  if (sleepRange.value === 'HALF') return { from: monthsAgoStart(5), to, bucket: 'MONTH' }
  if (sleepRange.value === 'YEAR') return { from: monthsAgoStart(11), to, bucket: 'MONTH' }
  return null
})
const statsRows = computed(() => statsBuckets.value[sleepRange.value] || [])

/** 补齐网格:没数据的那天/那个月留 null(不画柱子,不伪造 0) */
const statsSeries = computed(() => {
  const win = sleepWindow.value
  if (!win) return { labels: [], values: [] }
  const labels = []
  const values = []
  const cur = parseIsoDay(win.from)
  const end = parseIsoDay(win.to)
  const dayMap = win.bucket === 'DAY' ? new Map(statsRows.value.map((r) => [String(r.date), Number(r.totalMin)])) : null
  const monthMap =
    win.bucket === 'MONTH' ? new Map(statsRows.value.map((r) => [String(r.month), r.avgMin == null ? null : Number(r.avgMin)])) : null
  while (cur <= end) {
    if (win.bucket === 'DAY') {
      const key = isoDay(cur)
      labels.push(dayLabel(key))
      values.push(dayMap.has(key) ? dayMap.get(key) : null)
      cur.setDate(cur.getDate() + 1)
    } else {
      const key = `${cur.getFullYear()}-${String(cur.getMonth() + 1).padStart(2, '0')}`
      labels.push(monthLabel(key))
      values.push(monthMap.has(key) ? monthMap.get(key) : null)
      cur.setMonth(cur.getMonth() + 1)
    }
  }
  return { labels, values }
})

const statsSummary = computed(() => {
  const win = sleepWindow.value
  if (!win) return { avg: null, total: null, nights: 0 }
  const rows = statsRows.value
  const nights = rows.reduce((a, r) => a + Number(r.nights || 0), 0)
  if (win.bucket === 'DAY') {
    // 周/月:合计是这段时间的睡眠总量,平均按「晚」算而不是按天
    const total = rows.reduce((a, r) => a + Number(r.totalMin || 0), 0)
    return { avg: nights ? Math.round(total / nights) : null, total, nights }
  }
  // 半年/年:每月一个平均值,再在这些月之间取平均
  const list = rows.map((r) => Number(r.avgMin)).filter((v) => Number.isFinite(v))
  return {
    avg: list.length ? Math.round(list.reduce((a, b) => a + b, 0) / list.length) : null,
    total: null,
    nights,
  }
})

const sleepSpanText = computed(() => {
  if (sleepRange.value === 'DAY') return dayNight.value ? dayLabel(dayNight.value.sleepDate) : ''
  return t(`health.sleep.span.${sleepRange.value.toLowerCase()}`)
})

const EMPTY_SERIES = { labels: [], values: [], points: [] }
const seriesFor = (metric) => series.value[metric] || EMPTY_SERIES
const paneSeries = (name) => seriesFor(selected[name])

const paneLatest = (name) => {
  const list = paneSeries(name).values.filter((v) => v != null)
  return list.length ? list[list.length - 1] : null
}
const paneAvg = (name) => {
  const list = paneSeries(name)
    .values.filter((v) => v != null)
    .map(Number)
  return list.length ? list.reduce((a, b) => a + b, 0) / list.length : null
}

const rangeText = (code) => {
  const days = rangeDays(code)
  return days <= 1 ? t('health.trend.today') : t('health.trend.range', { n: days })
}

const colorOf = (code) => stageMeta(code)?.color || 'var(--health-stage-unknown)'
const segTitle = (seg) => `${t(stageMeta(seg.code)?.labelKey || 'health.stage.unknown')} ${sleepText(t, seg.minutes)}`
const providerText = (provider) => {
  const map = {
    SHORTCUT: 'settings.health.sourceShortcut',
    HEALTH_CONNECT: 'settings.health.sourceHealthConnect',
    IOS_NATIVE: 'settings.health.sourceNative',
  }
  return map[provider] ? t(map[provider]) : String(provider)
}

function isoDay(d) {
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}
// 用正午做基准,避免夏令时/边界把「今天」挪一天
function shiftDay(offset) {
  const d = new Date()
  d.setHours(12, 0, 0, 0)
  d.setDate(d.getDate() + offset)
  return isoDay(d)
}
function parseIsoDay(value) {
  const [y, m, d] = String(value || '')
    .split('-')
    .map(Number)
  return new Date(y, (m || 1) - 1, d || 1, 12)
}
/** 本周一(周日算上周第一天,ISO 周口径) */
function weekStartDay() {
  const d = new Date()
  d.setHours(12, 0, 0, 0)
  d.setDate(d.getDate() - ((d.getDay() + 6) % 7))
  return isoDay(d)
}
function monthStartDay() {
  return monthsAgoStart(0)
}
/** n 个自然月前那个月的 1 号(半年=5、一年=11,含本月正好 6/12 个月) */
function monthsAgoStart(n) {
  const d = new Date()
  d.setHours(12, 0, 0, 0)
  d.setDate(1)
  d.setMonth(d.getMonth() - n)
  return isoDay(d)
}
/** 月份短标签走 Intl:中文「11月」、英文「Nov」,不另外维护 12 个月名 */
function monthLabel(ym) {
  const [y, m] = String(ym).split('-').map(Number)
  return new Intl.DateTimeFormat(locale.value, { month: 'short' }).format(new Date(y, m - 1, 1))
}
const rangeDays = (code) => (DENSE_POINT.includes(code) ? 1 : RANGE_DAYS)

/** 按天指标:补齐日期网格,那天没数据就是 null(不画,不补 0) */
function daySeries(points, days) {
  const byDate = new Map()
  points.forEach((p) => {
    const date = String(p.startTime || p.endTime || '').slice(0, 10)
    const value = Number(p.value)
    if (date && Number.isFinite(value)) byDate.set(date, value)
  })
  const labels = []
  const values = []
  for (let i = days - 1; i >= 0; i--) {
    const key = shiftDay(-i)
    labels.push(dayLabel(key))
    values.push(byDate.has(key) ? byDate.get(key) : null)
  }
  return { labels, values, points: [] }
}

/** 入参是端点返回的 points;每个点带 min/max(心率小时桶用来画带) */
function pointSeries(points, days) {
  const list = points
    .map((p) => ({ at: p.startTime || p.endTime || '', value: Number(p.value), min: p.min, max: p.max }))
    .filter((p) => p.at && Number.isFinite(p.value))
    .sort((a, b) => String(a.at).localeCompare(String(b.at)))
  const dense = days <= 1
  return {
    labels: list.map((p) => (dense ? clockText(p.at) : dayLabel(p.at))),
    values: list.map((p) => p.value),
    points: list.map((p) => ({ min: p.min, max: p.max })),
  }
}

const stackOf = (n) =>
  stackBar([
    { code: 'DEEP', minutes: Number(n.deepMin) || 0 },
    { code: 'LIGHT', minutes: Number(n.lightMin) || 0 },
    { code: 'REM', minutes: Number(n.remMin) || 0 },
    { code: 'AWAKE', minutes: Number(n.awakeMin) || 0 },
  ])

/** 报表:锚定日与窗口后端算(默认最近一天),前端只传卡片清单 */
async function loadReport() {
  reportData.value = await healthApi.report(reportCards.value).catch(() => null)
}

async function loadConsent() {
  const data = await healthApi.consent().catch(() => null)
  consentOn.value = !!data?.reproductive
}

async function loadNights() {
  const data = await healthApi.sleep({ from: shiftDay(-(SLEEP_DAYS - 1)), to: shiftDay(0) }).catch(() => null)
  const items = Array.isArray(data?.items) ? data.items.slice() : []
  items.sort((a, b) => String(a.sleepDate).localeCompare(String(b.sleepDate)))
  nights.value = items
}

async function loadSeries(metric) {
  if (!metric) return EMPTY_SERIES
  if (series.value[metric]) return series.value[metric]
  const days = rangeDays(metric)
  const data = await healthApi.metric({ metric, from: shiftDay(-(days - 1)), to: shiftDay(0) }).catch(() => null)
  const points = Array.isArray(data?.points) ? data.points : []
  const built = periodOf(metric) === 'DAY' ? daySeries(points, days) : pointSeries(points, days)
  series.value = { ...series.value, [metric]: built }
  return built
}

function resetDetail() {
  pickedId.value = null
  sleepDetail.value = null
}

/** 周/月/半年/年:统计接口按 DAY / MONTH 分桶返回,前端只负责补网格 */
async function loadStats() {
  const win = sleepWindow.value
  if (!win || !userStore.isLoggedIn) return
  const key = sleepRange.value
  if (statsBuckets.value[key]) return
  statsLoading.value = true
  try {
    const data = await healthApi.sleepStats({ from: win.from, to: win.to, bucket: win.bucket }).catch(() => null)
    statsBuckets.value = { ...statsBuckets.value, [key]: Array.isArray(data?.buckets) ? data.buckets : [] }
  } finally {
    statsLoading.value = false
  }
}

async function loadStages(night) {
  if (!night) return
  pickedId.value = night.id
  detailLoading.value = true
  try {
    sleepDetail.value = await healthApi.stages(night.id).catch(() => null)
  } finally {
    detailLoading.value = false
  }
}

/** 日视图默认看最近一晚(不算用户「点选」,换成员/刷新都会重新定位) */
async function ensureDayDetail() {
  if (!nights.value.length) return
  const night = pickedNight.value || nights.value[nights.value.length - 1]
  if (sleepDetail.value && pickedId.value === night.id) return
  await loadStages(night)
}

async function ensureSleepView() {
  if (sleepRange.value === 'DAY') await ensureDayDetail()
  else await loadStats()
}

async function selectSleepRange(key) {
  sleepRange.value = key
  await ensureSleepView()
}

async function reloadAll() {
  if (!userStore.isLoggedIn) return
  loading.value = true
  try {
    series.value = {}
    statsBuckets.value = {}
    resetDetail()
    await Promise.all([loadReport(), loadNights(), loadSeries('STEPS')])
    if (['vitals', 'activity', 'body'].includes(tab.value)) await loadSeries(selected[tab.value])
    // 睡眠页才需要分期/统计,其它 tab 不必多发这一轮请求
    if (tab.value === 'sleep') await ensureSleepView()
  } finally {
    loading.value = false
  }
}

async function onTabChange(name) {
  if (name === 'sleep') await ensureSleepView()
  if (['vitals', 'activity', 'body'].includes(name)) await loadSeries(selected[name])
}

async function selectMetric(name, code) {
  selected[name] = code
  await loadSeries(code)
}

/** 点柱子:切到「日」看那一晚的进度轴(下方「这一晚」明细条也一起更新) */
async function pickNight(index) {
  const night = nights.value[index]
  if (!night) return
  sleepRange.value = 'DAY'
  await loadStages(night)
}

/** 卡片自定义:先与账号对齐一次偏好(服务端优先),再用本机兜底 */
async function loadCardPref() {
  const remote = await syncPref(CARD_PREF_LOCAL, CARD_PREF_SERVER)
  reportCards.value = parseCardsPref(remote ?? readPref(CARD_PREF_LOCAL))
}

function saveCards() {
  const picked = sanitizeCards(cardDraft.value)
  reportCards.value = picked
  writePref(CARD_PREF_LOCAL, CARD_PREF_SERVER, JSON.stringify(picked))
  cardDialog.value = false
  loadReport()
}

function restoreCards() {
  cardDraft.value = [...DEFAULT_CARDS]
}

/** 打开自定义弹窗时把当前清单带进去(取消不影响已保存的选择) */
function openCardDialog() {
  cardDraft.value = [...reportCards.value]
}

onMounted(async () => {
  cardDraft.value = [...reportCards.value]
  await loadCardPref()
  loadConsent()
  await reloadAll()
})
</script>

<style scoped>
.tb-note {
  font-size: 13px;
  color: var(--color-text-secondary);
}
.tb-link {
  font-size: 13px;
  color: var(--color-primary);
  text-decoration: none;
}
.tb-link:hover {
  text-decoration: underline;
}
.btn-text {
  margin-left: 4px;
}
.health-tabs :deep(.el-tabs__header) {
  margin-bottom: 12px;
}
.tab-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.empty-card {
  padding: 20px;
}
.report-actions {
  display: flex;
  justify-content: flex-end;
}
.chart-card {
  padding: 14px 16px;
}
.chart-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}
.range-note {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.stat-row {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin: 8px 0 4px;
  font-size: 13px;
  color: var(--color-text-secondary);
}
.stat-inline b {
  margin-left: 4px;
  font-size: 15px;
  color: var(--color-text-primary);
}
.chip-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.metric-chip {
  padding: 5px 12px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
  background: var(--color-card);
  color: var(--color-text-secondary);
  font-size: 13px;
  cursor: pointer;
  transition:
    color 0.15s ease,
    border-color 0.15s ease,
    background 0.15s ease;
}
.metric-chip:hover {
  color: var(--color-text-primary);
}
.metric-chip.active {
  border-color: var(--health-bar, var(--color-primary));
  color: var(--color-text-primary);
  background: var(--health-chip-bg);
}
.stage-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}
.stage-date {
  width: 44px;
  flex: none;
  font-size: 12px;
  color: var(--color-text-secondary);
}
.stage-bar {
  display: flex;
  flex: 1;
  height: 14px;
  border-radius: 7px;
  overflow: hidden;
  background: var(--health-track);
}
.stage-seg {
  display: block;
  height: 100%;
}
.stage-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 10px;
  font-size: 12px;
  color: var(--color-text-secondary);
}
.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.legend-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
.detail-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-top: 6px;
  font-size: 13px;
  color: var(--color-text-secondary);
}
.timeline {
  position: relative;
  height: 18px;
  margin-top: 8px;
  border-radius: 9px;
  overflow: hidden;
  background: var(--health-track);
}
.timeline-seg {
  position: absolute;
  top: 0;
  height: 100%;
}
.empty-hint {
  padding: 10px 0;
  font-size: 13px;
  color: var(--color-text-secondary);
}
.dialog-hint {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--color-text-secondary);
}
</style>
