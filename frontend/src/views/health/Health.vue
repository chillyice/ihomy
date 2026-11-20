<!-- 健康数据页(/health):报表 / 睡眠 / 体征 / 活动 / 身体。
     健康数据本人独占(服务端只认登录者本人,传别人的 userId 一律 404),页面没有成员切换;
     要给别人看只能「分享报表」:生成一份冻结快照链接,7 天后自动失效。
     报表卡片与告警口径由后端 HealthReportService / HealthAlerts 决定,这里只渲染。
     时间范围:各指标页可选今天/7/14/30/90/365 天或自定义(≤366 天);点指标跨度过长时按天聚合,
     睡眠明细(会话/分期)只有 ≤62 天的窗口,更长范围只出统计图。 -->
<template>
  <div class="page health-page">
    <!-- 顶部工具栏(暖居滚动后胞吐进顶栏):只有操作,不放说明文字 -->
    <PageToolbar>
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
    </PageToolbar>

    <el-tabs v-model="tab" class="health-tabs" @tab-change="onTabChange">
      <!-- 报表:默认看最新一天,可前后翻日期 / 选任意历史日;卡片可自定义,分组渲染 -->
      <el-tab-pane :label="$t('health.tabs.report')" name="report">
        <div v-if="!reportHasData && !reportLoading" class="card empty-card">
          <el-empty :image-size="88" :description="$t('health.noData')">
            <router-link to="/settings?tab=health">
              <el-button type="primary">{{ $t('health.setup') }}</el-button>
            </router-link>
          </el-empty>
        </div>
        <div v-else class="tab-body">
          <div class="report-bar" v-loading="reportLoading">
            <div class="date-nav">
              <el-button text circle :disabled="!reportAnchor" :title="$t('health.report.prevDay')" @click="reportStep(-1)">
                <el-icon><ArrowLeft /></el-icon>
              </el-button>
              <el-date-picker
                v-model="reportPick"
                type="date"
                value-format="YYYY-MM-DD"
                size="small"
                :clearable="false"
                :disabled="!reportAnchor"
                class="report-date"
                @update:model-value="onReportDate"
              />
              <el-button text circle :disabled="!canNextDay" :title="$t('health.report.nextDay')" @click="reportStep(1)">
                <el-icon><ArrowRight /></el-icon>
              </el-button>
              <el-button text size="small" :disabled="!reportAnchor || reportPick === today" @click="loadReport()">
                {{ $t('health.report.latest') }}
              </el-button>
            </div>
            <el-button text @click="openCardDialog">
              <el-icon><Setting /></el-icon>
              <span class="btn-text">{{ $t('health.report.customize') }}</span>
            </el-button>
          </div>
          <HealthReport :report="reportData" @detail="goDetail" />
        </div>
      </el-tab-pane>

      <!-- 睡眠:最近一晚 / 7 / 30 / 90 / 365 天 / 自定义;点柱子看那一晚的进度轴 -->
      <el-tab-pane :label="$t('health.tabs.sleep')" name="sleep">
        <div class="tab-body">
          <div class="chip-row">
            <button
              v-for="r in SLEEP_PRESETS"
              :key="r.key"
              type="button"
              class="metric-chip range-chip"
              :class="{ active: sleepRange.key === r.key }"
              @click="selectSleepKey(r.key)"
            >
              {{ r.days ? $t('health.trend.range', { n: r.days }) : $t(r.labelKey) }}
            </button>
            <el-date-picker
              class="range-picker"
              type="daterange"
              size="small"
              value-format="YYYY-MM-DD"
              :clearable="false"
              :model-value="[sleepRange.from, sleepRange.to]"
              @update:model-value="onSleepDates"
            />
          </div>

          <div class="card chart-card">
            <div class="chart-head">
              <span class="section-label">{{ sleepDayMode ? $t('health.sleep.lane') : $t('health.sleep.stats') }}</span>
              <span class="range-note">{{ sleepSpanText }}</span>
            </div>

            <!-- 最近一晚:某一晚的睡眠进度轴,横轴钟点、纵轴四行分期 -->
            <template v-if="sleepDayMode">
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

            <!-- 长范围:每天睡了多久(≤62 天按天,更长按月平均) -->
            <template v-else>
              <div v-if="statsSummary.avg != null" class="stat-row">
                <span v-if="statsSummary.total != null" class="stat-inline">
                  {{ $t('health.trend.total') }} <b>{{ sleepText($t, statsSummary.total) }}</b>
                </span>
                <span class="stat-inline">
                  {{ $t('health.sleep.avgNight') }} <b>{{ sleepText($t, statsSummary.avg) }}</b>
                </span>
                <span class="stat-inline">{{ $t('health.sleep.nights', { n: statsSummary.nights }) }}</span>
                <span v-if="sleepAvgClock.bed" class="stat-inline">
                  {{ $t('health.sleep.avgBed') }} <b>{{ sleepAvgClock.bed }}</b>
                </span>
                <span v-if="sleepAvgClock.wake" class="stat-inline">
                  {{ $t('health.sleep.avgWake') }} <b>{{ sleepAvgClock.wake }}</b>
                </span>
              </div>
              <HealthTrendChart
                v-if="statsSeries.values.length"
                mode="bar"
                :values="statsSeries.values"
                :labels="statsSeries.labels"
                :average="statsAvg"
                :average-label="$t('health.trend.avg')"
                :format-value="sleepFormat"
                :label="$t('health.sleep.stats')"
              />
              <div v-else class="empty-hint">{{ statsLoading ? $t('health.loading') : $t('health.sleep.noData') }}</div>
              <div v-if="!sleepHasSessions" class="range-note sessions-limit">
                {{ $t('health.sleep.sessionsLimit', { n: SESSION_MAX_DAYS }) }}
              </div>
            </template>
          </div>

          <template v-if="sleepHasSessions">
            <div class="card chart-card">
              <div class="section-label">{{ $t('health.sleep.duration') }}</div>
              <HealthTrendChart
                v-if="nights.length"
                mode="bar"
                :values="nightValues"
                :labels="nightLabels"
                :picked-index="pickedIndex"
                :average="nightAvg"
                :average-label="$t('health.trend.avg')"
                :format-value="sleepFormat"
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
                :format-value="bedtimeFormat"
                :label="$t('health.sleep.bedtime')"
              />
              <div v-else class="empty-hint">{{ $t('health.sleep.noData') }}</div>
            </div>

            <!-- 点某一晚:进度轴 + 起止时长(长范围下点柱子就到这里) -->
            <div v-if="!sleepDayMode" class="card chart-card">
              <div class="section-label">{{ $t('health.sleep.detail') }}</div>
              <template v-if="pickedNight">
                <div class="detail-head">
                  <span>{{ $t('health.sleep.start') }} {{ clockText(pickedNight.startTime) }}</span>
                  <span>{{ $t('health.sleep.end') }} {{ clockText(pickedNight.endTime) }}</span>
                  <span>{{ sleepText($t, pickedNight.totalMin) }}</span>
                  <el-tag v-if="pickedNight.provider" size="small" type="info">{{ providerText(pickedNight.provider) }}</el-tag>
                </div>
                <HealthSleepNight
                  v-if="dayStages.length"
                  :stages="dayStages"
                  :start-time="pickedNight.startTime"
                  :end-time="pickedNight.endTime"
                  :label="$t('health.sleep.detail')"
                />
                <div v-else class="empty-hint">{{ detailLoading ? $t('health.loading') : $t('health.sleep.noStages') }}</div>
              </template>
              <div v-else class="empty-hint">{{ $t('health.sleep.pick') }}</div>
            </div>
          </template>
        </div>
      </el-tab-pane>

      <!-- 体征 / 活动 / 身体:同一套版式,只换指标条;指标条下是时间范围 -->
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

          <div class="chip-row">
            <button
              v-for="p in RANGE_PRESETS"
              :key="p.key"
              type="button"
              class="metric-chip range-chip"
              :class="{ active: metricRange[pane.name].key === p.key }"
              @click="selectMetricRange(pane.name, p.key)"
            >
              {{ p.days === 1 ? $t('health.trend.today') : $t('health.trend.range', { n: p.days }) }}
            </button>
            <el-date-picker
              class="range-picker"
              type="daterange"
              size="small"
              value-format="YYYY-MM-DD"
              :clearable="false"
              :model-value="[metricRange[pane.name].from, metricRange[pane.name].to]"
              @update:model-value="onMetricDates(pane.name, $event)"
            />
          </div>

          <div class="card chart-card">
            <div class="chart-head">
              <span class="section-label">{{ metricName($t, selected[pane.name]) }}</span>
              <span class="range-note">{{ rangeText(pane.name) }}</span>
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
              :average="paneAvg(pane.name)"
              :average-label="$t('health.trend.avg')"
              :format-value="valueFormat(selected[pane.name])"
              :label="metricName($t, selected[pane.name])"
            />
            <div v-else class="empty-hint">{{ $t('health.trend.noData') }}</div>
          </div>

          <!-- 活动页补一段训练记录:步态/活动指标是「累加量」,一次训练是什么项目只有表里能看 -->
          <div v-if="pane.name === 'activity'" class="card chart-card">
            <div class="chart-head">
              <span class="section-label">{{ $t('health.workout.title') }}</span>
              <span class="range-note">{{ rangeText('activity') }}</span>
            </div>
            <ul v-if="workouts.length" class="workout-list">
              <li v-for="w in workouts" :key="w.id" class="workout-row">
                <span class="workout-type">{{ workoutLabel($t, w.type) }}</span>
                <span class="workout-meta">
                  <span>{{ dayLabel(w.startTime) }} {{ clockText(w.startTime) }}</span>
                  <span>{{ $t('health.workout.duration', { n: w.durationMin == null ? 0 : w.durationMin }) }}</span>
                  <span v-if="w.distanceKm != null">{{ $t('health.workout.distance', { n: Number(w.distanceKm).toFixed(2) }) }}</span>
                  <span v-if="w.energyKcal != null">{{ $t('health.workout.energy', { n: Math.round(Number(w.energyKcal)) }) }}</span>
                </span>
              </li>
            </ul>
            <div v-else class="empty-hint">{{ workoutLoading ? $t('health.loading') : $t('health.workout.empty') }}</div>
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
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { ArrowLeft, ArrowRight, Refresh, Setting, Share } from '@element-plus/icons-vue'
import { healthApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { readPref, syncPref, writePref } from '@/utils/userPref'
import PageToolbar from '@/components/PageToolbar.vue'
import HealthTrendChart from './HealthTrendChart.vue'
import HealthSleepNight from './HealthSleepNight.vue'
import HealthReport from './HealthReport.vue'
import HealthShareDialog from './HealthShareDialog.vue'
import { DEFAULT_CARDS, HEALTH_REPORT_CARDS, cardTitle, parseCardsPref, sanitizeCards } from './reportCards'
import {
  MAX_RANGE_DAYS,
  RANGE_PRESETS,
  SESSION_MAX_DAYS,
  bucketFor,
  clampRange,
  dateGrid,
  isoDay,
  presetRange,
  shiftDay,
  spanDays,
} from './healthRange'
import { avgClock, avgOf, stackBar } from './healthChart'
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
  workoutLabel,
} from '@/utils/healthMetrics'

const { t, locale } = useI18n()
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// 报表卡片偏好:本机 localStorage + sys_user_pref(键 health.report.v1),与首页布局同款同步规则
const CARD_PREF_LOCAL = 'ihomy:health:report:v1'
const CARD_PREF_SERVER = 'health.report.v1'

// 旧书签 /health?tab=overview 映射到「报表」
const LEGACY_TABS = { overview: 'report' }
const TABS = ['report', 'sleep', 'vitals', 'activity', 'body']
// 睡眠范围:最近一晚看进度轴,其余范围看统计;明细(会话/分期)只有 ≤62 天
const SLEEP_PRESETS = [
  { key: 'night', labelKey: 'health.sleep.rangeNight', days: 0 },
  { key: 'd7', days: 7 },
  { key: 'd30', days: 30 },
  { key: 'd90', days: 90 },
  { key: 'd365', days: 365 },
]
const PANES = ['vitals', 'activity', 'body']
const PANE_GROUP = { vitals: 'VITAL', activity: 'ACTIVITY', body: 'BODY' }

const today = isoDay(new Date())

const initialTab = LEGACY_TABS[String(route.query.tab || '')] || String(route.query.tab || '')
const tab = ref(TABS.includes(initialTab) ? initialTab : 'report')
const loading = ref(false)
const reportData = ref(null)
const reportLoading = ref(false)
const reportPick = ref('')
const reportCards = ref([...DEFAULT_CARDS])
const cardDraft = ref([...DEFAULT_CARDS])
const cardDialog = ref(false)
const shareVisible = ref(false)
const consentOn = ref(false)
const nights = ref([])
const nightsKey = ref('')
const series = ref({})
const pickedId = ref(null)
const sleepDetail = ref(null)
const detailLoading = ref(false)
const statsRows = ref([])
const statsKey = ref('')
const statsLoading = ref(false)
const workouts = ref([])
const workoutKey = ref('')
const workoutLoading = ref(false)
const selected = reactive({ vitals: 'HEART_RATE', activity: 'STEPS', body: 'WEIGHT' })

const metricPanes = [
  { name: 'vitals', labelKey: 'health.tabs.vitals', codes: TAB_METRICS.VITAL },
  { name: 'activity', labelKey: 'health.tabs.activity', codes: TAB_METRICS.ACTIVITY },
  { name: 'body', labelKey: 'health.tabs.body', codes: TAB_METRICS.BODY },
]

// days 要一起存:rangeText 和自定义区间的文案都读它,只存 from/to 会渲染成「近 天」
const makeRange = (key, days) => ({ key, days, ...presetRange(days, today) })
const metricRange = reactive({
  vitals: makeRange('d14', 14),
  activity: makeRange('d14', 14),
  body: makeRange('d14', 14),
})

/** 睡眠默认近 30 天(能看每晚明细,也不至于一屏几百根柱子) */
const makeSleepRange = (key) => {
  const days = key === 'night' ? 3 : SLEEP_PRESETS.find((p) => p.key === key)?.days || 30
  return { key, days, ...presetRange(days, today) }
}
const sleepRange = ref(makeSleepRange('d30'))

const legend = computed(() => SLEEP_STAGES.slice(0, 4))
// 未开采集同意时敏感卡片不可选(后端也会再丢一次,这里只负责别让用户白勾)
const pickableCards = computed(() => HEALTH_REPORT_CARDS.filter((c) => !c.sensitive || consentOn.value))
const reportHasData = computed(() => (reportData.value?.cards || []).some((c) => c.hasData))
const reportAnchor = computed(() => String(reportData.value?.anchorDate || '').slice(0, 10))
const canNextDay = computed(() => !!reportAnchor.value && reportAnchor.value < today)

const nightLabels = computed(() => nights.value.map((n) => dayLabel(n.sleepDate)))
const nightValues = computed(() => nights.value.map((n) => (n.totalMin == null ? null : Number(n.totalMin))))
const nightAvg = computed(() => avgOf(nightValues.value))
const pickedIndex = computed(() => nights.value.findIndex((n) => n.id === pickedId.value))
const pickedNight = computed(() => nights.value.find((n) => n.id === pickedId.value) || null)
const stageNights = computed(() => nights.value.filter((n) => n.deepMin || n.lightMin || n.remMin))
const dayNight = computed(() => pickedNight.value)
const dayStages = computed(() => sleepDetail.value?.stages || [])

// ===== 睡眠范围 =====
const sleepSpan = computed(() => spanDays(sleepRange.value.from, sleepRange.value.to))
const sleepDayMode = computed(() => sleepRange.value.key === 'night' || sleepSpan.value === 1)
const sleepHasSessions = computed(() => sleepSpan.value <= SESSION_MAX_DAYS)
const sleepBucket = computed(() => (sleepSpan.value > SESSION_MAX_DAYS ? 'MONTH' : 'DAY'))
const sleepSpanText = computed(() => `${dayLabel(sleepRange.value.from)} – ${dayLabel(sleepRange.value.to)}`)
const sleepAvgClock = computed(() => avgClock(nights.value))

const statsSummary = computed(() => {
  const rows = statsRows.value
  const nightsCount = rows.reduce((a, r) => a + Number(r.nights || 0), 0)
  if (sleepBucket.value === 'DAY') {
    const total = rows.reduce((a, r) => a + Number(r.totalMin || 0), 0)
    return { avg: nightsCount ? Math.round(total / nightsCount) : null, total, nights: nightsCount }
  }
  // 按月:每月一个平均值,再在这些月之间取平均
  const list = rows.map((r) => Number(r.avgMin)).filter((v) => Number.isFinite(v))
  return {
    avg: list.length ? Math.round(list.reduce((a, b) => a + b, 0) / list.length) : null,
    total: null,
    nights: nightsCount,
  }
})

/** 统计图按范围补网格(按天/按月),缺数据留 null */
const statsSeries = computed(() => {
  const { from, to } = sleepRange.value
  if (sleepBucket.value === 'DAY') {
    const map = new Map(statsRows.value.map((r) => [String(r.date), r.totalMin == null ? null : Number(r.totalMin)]))
    const labels = []
    const values = []
    dateGrid(from, to).forEach((key) => {
      labels.push(dayLabel(key))
      values.push(map.has(key) ? map.get(key) : null)
    })
    return { labels, values }
  }
  const map = new Map(statsRows.value.map((r) => [String(r.month), r.avgMin == null ? null : Number(r.avgMin)]))
  const labels = []
  const values = []
  let cur = parseIsoDay(from)
  const end = parseIsoDay(to)
  cur.setDate(1)
  while (cur <= end) {
    const key = `${cur.getFullYear()}-${String(cur.getMonth() + 1).padStart(2, '0')}`
    labels.push(monthLabel(key))
    values.push(map.has(key) ? map.get(key) : null)
    cur.setMonth(cur.getMonth() + 1)
  }
  return { labels, values }
})
const statsAvg = computed(() => avgOf(statsSeries.value.values))

const EMPTY_SERIES = { labels: [], values: [], points: [] }
const cacheKey = (metric, range) => `${metric}|${range.from}|${range.to}|${bucketFor(periodOf(metric), range.from, range.to)}`
const paneSeries = (name) => series.value[cacheKey(selected[name], metricRange[name])] || EMPTY_SERIES

const paneLatest = (name) => {
  const list = paneSeries(name).values.filter((v) => v != null)
  return list.length ? list[list.length - 1] : null
}
const paneAvg = (name) => avgOf(paneSeries(name).values)

const valueFormat = (code) => (v) => (v == null || v === '' ? '' : withUnit(code, formatMetric(code, v)))
const sleepFormat = (v) => (v == null ? '' : sleepText(t, v))
const bedtimeFormat = (night) => (night ? `${clockText(night.startTime)} · ${sleepText(t, night.totalMin)}` : '')

const rangeText = (name) => {
  const range = metricRange[name]
  if (range.key === 'custom') return `${dayLabel(range.from)} – ${dayLabel(range.to)}`
  return range.days === 1 ? t('health.trend.today') : t('health.trend.range', { n: range.days })
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

function parseIsoDay(value) {
  const [y, m, d] = String(value || '')
    .split('-')
    .map(Number)
  return new Date(y, (m || 1) - 1, d || 1, 12)
}
/** 月份短标签走 Intl:中文「11月」、英文「Nov」,不另外维护 12 个月名 */
function monthLabel(ym) {
  const [y, m] = String(ym).split('-').map(Number)
  return new Intl.DateTimeFormat(locale.value, { month: 'short' }).format(new Date(y, m - 1, 1))
}

/** 按天指标:补齐日期网格,那天没数据就是 null(不画,不补 0);带 min/max 的走区间带 */
function daySeries(points, from, to) {
  const byDate = new Map()
  points.forEach((p) => {
    const date = String(p.date || p.startTime || p.endTime || '').slice(0, 10)
    const value = Number(p.value)
    if (date && Number.isFinite(value)) byDate.set(date, { value, min: p.min, max: p.max })
  })
  const labels = []
  const values = []
  const bands = []
  dateGrid(from, to).forEach((key) => {
    labels.push(dayLabel(key))
    const hit = byDate.get(key)
    values.push(hit ? hit.value : null)
    bands.push(hit ? { min: hit.min, max: hit.max } : { min: null, max: null })
  })
  return { labels, values, points: bands }
}

/** 逐点指标(短区间):按时间点画;跨度 >1 天时标签带日期区分 */
function pointSeries(points, range) {
  const dense = spanDays(range.from, range.to) <= 1
  const list = points
    .map((p) => ({ at: p.startTime || p.endTime || '', value: Number(p.value), min: p.min, max: p.max }))
    .filter((p) => p.at && Number.isFinite(p.value))
    .sort((a, b) => String(a.at).localeCompare(String(b.at)))
  return {
    labels: list.map((p) => (dense ? clockText(p.at) : `${dayLabel(p.at)} ${clockText(p.at)}`)),
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

// ===== 报表 =====
/** 报表:锚定日与窗口后端算;date 为空 = 最近一天,可翻任意历史日 */
async function loadReport(date) {
  if (!userStore.isLoggedIn) return
  reportLoading.value = true
  try {
    reportData.value = await healthApi.report(reportCards.value, date || null).catch(() => null)
    reportPick.value = reportAnchor.value || ''
  } finally {
    reportLoading.value = false
  }
}

function reportStep(delta) {
  if (!reportAnchor.value) return
  const next = shiftDay(delta, reportAnchor.value)
  if (next > today) return
  loadReport(next)
}

function onReportDate(value) {
  if (!value) return
  loadReport(String(value).slice(0, 10))
}

async function loadConsent() {
  const data = await healthApi.consent().catch(() => null)
  consentOn.value = !!data?.reproductive
}

// ===== 睡眠 =====
async function loadNights(from, to) {
  const key = `${from}|${to}`
  if (nightsKey.value === key) return
  const data = await healthApi.sleep({ from, to }).catch(() => null)
  const items = Array.isArray(data?.items) ? data.items.slice() : []
  items.sort((a, b) => String(a.sleepDate).localeCompare(String(b.sleepDate)))
  nights.value = items
  nightsKey.value = key
  // 选中那晚如果不在新窗口里就清掉,避免详情停在上一个范围
  if (pickedId.value && !items.some((n) => n.id === pickedId.value)) resetDetail()
}

/** 统计:≤62 天按天合计,更长按月平均(接口上限 366 天) */
async function loadStats() {
  const { from, to } = sleepRange.value
  const key = `${from}|${to}|${sleepBucket.value}`
  if (statsKey.value === key) return
  statsLoading.value = true
  try {
    const data = await healthApi.sleepStats({ from, to, bucket: sleepBucket.value }).catch(() => null)
    statsRows.value = Array.isArray(data?.buckets) ? data.buckets : []
    statsKey.value = key
  } finally {
    statsLoading.value = false
  }
}

async function loadSleep({ force = false } = {}) {
  if (!userStore.isLoggedIn) return
  if (force) {
    nightsKey.value = ''
    statsKey.value = ''
  }
  const { from, to } = sleepRange.value
  if (sleepDayMode.value) {
    await loadNights(from, to)
    await ensurePickedDetail()
    return
  }
  await Promise.all([loadStats(), sleepHasSessions.value ? loadNights(from, to) : Promise.resolve()])
  if (!sleepHasSessions.value) {
    nights.value = []
    resetDetail()
    return
  }
  await ensurePickedDetail()
}

/** 睡眠默认选最近一晚;用户点过别的晚就保持 */
async function ensurePickedDetail() {
  if (!nights.value.length) {
    resetDetail()
    return
  }
  const night = pickedNight.value || nights.value[nights.value.length - 1]
  if (!pickedNight.value) pickedId.value = night.id
  if (sleepDetail.value && pickedId.value === night.id) return
  await loadStages(night)
}

function resetDetail() {
  pickedId.value = null
  sleepDetail.value = null
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

async function selectSleepKey(key) {
  sleepRange.value = makeSleepRange(key)
  resetDetail()
  await loadSleep()
}

async function onSleepDates(value) {
  if (!Array.isArray(value) || value.length !== 2) return
  const out = clampRange(value[0], value[1], today)
  if (out.clamped) ElMessage.warning(t('health.trend.clamped', { n: MAX_RANGE_DAYS }))
  sleepRange.value = { key: 'custom', from: out.from, to: out.to, days: spanDays(out.from, out.to) }
  resetDetail()
  await loadSleep()
}

/** 点时长柱子:选中那一晚,下方进度轴跟着换(不切范围) */
async function pickNight(index) {
  const night = nights.value[index]
  if (!night) return
  await loadStages(night)
}

// ===== 指标趋势 =====
async function loadSeries(metric, name, force = false) {
  if (!metric || !userStore.isLoggedIn) return EMPTY_SERIES
  const range = metricRange[name]
  if (!range) return EMPTY_SERIES
  const key = cacheKey(metric, range)
  if (!force && series.value[key]) return series.value[key]
  const bucket = bucketFor(periodOf(metric), range.from, range.to)
  const params = { metric, from: range.from, to: range.to }
  // 逐点指标的日聚合:长区间不再被 2000 行上限静默截断
  if (periodOf(metric) === 'POINT' && bucket === 'DAY') params.bucket = 'DAY'
  const data = await healthApi.metric(params).catch(() => null)
  const points = Array.isArray(data?.points) ? data.points : []
  const built = periodOf(metric) === 'POINT' && bucket === 'POINT' ? pointSeries(points, range) : daySeries(points, range.from, range.to)
  series.value = { ...series.value, [key]: built }
  return built
}

// ===== 训练记录 =====
async function loadWorkouts(force = false) {
  if (!userStore.isLoggedIn) return
  const { from, to } = metricRange.activity
  const key = `${from}|${to}`
  if (!force && workoutKey.value === key) return
  workoutLoading.value = true
  try {
    const data = await healthApi.workout({ from, to }).catch(() => null)
    workouts.value = Array.isArray(data?.items) ? data.items : []
    workoutKey.value = key
  } finally {
    workoutLoading.value = false
  }
}

// ===== 范围切换 =====
async function selectMetricRange(name, key) {
  const preset = RANGE_PRESETS.find((p) => p.key === key)
  if (!preset) return
  metricRange[name] = makeRange(preset.key, preset.days)
  await loadSeries(selected[name], name, true)
  if (name === 'activity') await loadWorkouts(true)
}

async function onMetricDates(name, value) {
  if (!Array.isArray(value) || value.length !== 2) return
  const out = clampRange(value[0], value[1], today)
  if (out.clamped) ElMessage.warning(t('health.trend.clamped', { n: MAX_RANGE_DAYS }))
  metricRange[name] = { key: 'custom', from: out.from, to: out.to, days: spanDays(out.from, out.to) }
  await loadSeries(selected[name], name, true)
  if (name === 'activity') await loadWorkouts(true)
}

// ===== 刷新 / 切页 =====
async function reloadAll() {
  if (!userStore.isLoggedIn) return
  loading.value = true
  try {
    series.value = {}
    statsKey.value = ''
    nightsKey.value = ''
    workoutKey.value = ''
    resetDetail()
    const jobs = [loadReport(reportPick.value || null), loadSeries('STEPS', 'activity', true), loadWorkouts(true)]
    if (tab.value === 'sleep') jobs.push(loadSleep({ force: true }))
    else if (PANES.includes(tab.value)) jobs.push(loadSeries(selected[tab.value], tab.value, true))
    await Promise.all(jobs)
  } finally {
    loading.value = false
  }
}

async function onTabChange(name) {
  syncQuery()
  if (name === 'sleep') await loadSleep()
  if (PANES.includes(name)) await loadSeries(selected[name], name)
}

async function selectMetric(name, code) {
  selected[name] = code
  syncQuery()
  await loadSeries(code, name)
}

/** 报表「查看详情」:跳到对应 tab 并预选指标(?tab=activity&metric=STEPS 可直达) */
async function goDetail(detail) {
  const nextTab = detail?.tab
  if (!TABS.includes(nextTab) || nextTab === 'report') return
  tab.value = nextTab
  if (nextTab === 'sleep') {
    syncQuery()
    await loadSleep()
    return
  }
  const codes = TAB_METRICS[PANE_GROUP[nextTab]] || []
  if (detail.metric && codes.includes(detail.metric)) selected[nextTab] = detail.metric
  syncQuery()
  await loadSeries(selected[nextTab], nextTab)
}

/** 当前 tab / 指标写回地址栏,便于直接分享「活动 · 步数」这一页 */
function syncQuery() {
  const query = { ...route.query, tab: tab.value }
  if (PANES.includes(tab.value)) query.metric = selected[tab.value]
  else delete query.metric
  router.replace({ query }).catch(() => {})
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
  loadReport(reportPick.value || null)
}

function restoreCards() {
  cardDraft.value = [...DEFAULT_CARDS]
}

/** 打开自定义弹窗时把当前清单带进去(取消不影响已保存的选择) */
function openCardDialog() {
  cardDraft.value = [...reportCards.value]
}

onMounted(async () => {
  // 直达链接先定位指标,再按当前 tab 取数
  const initialMetric = String(route.query.metric || '').toUpperCase()
  if (PANES.includes(tab.value) && (TAB_METRICS[PANE_GROUP[tab.value]] || []).includes(initialMetric)) {
    selected[tab.value] = initialMetric
  }
  cardDraft.value = [...reportCards.value]
  await loadCardPref()
  loadConsent()
  await reloadAll()
})
</script>

<style scoped>
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
.report-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
}
.date-nav {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
}
.report-date {
  width: 140px;
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
.sessions-limit {
  margin-top: 8px;
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
  align-items: center;
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
.range-chip {
  padding: 3px 10px;
  font-size: 12px;
}
.range-picker {
  width: 230px;
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
.empty-hint {
  padding: 10px 0;
  font-size: 13px;
  color: var(--color-text-secondary);
}
.workout-list {
  margin: 10px 0 0;
  padding: 0;
  list-style: none;
}
.workout-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 0;
  border-top: 1px solid var(--color-border);
  font-size: 13px;
}
.workout-row:first-child {
  border-top: none;
}
.workout-type {
  flex: none;
  font-weight: 600;
  color: var(--color-text-primary);
}
.workout-meta {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
  color: var(--color-text-secondary);
}
.dialog-hint {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--color-text-secondary);
}
@media (max-width: 640px) {
  .range-picker {
    width: 100%;
  }
  .report-date {
    width: 120px;
  }
}
</style>
