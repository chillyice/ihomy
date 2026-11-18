<!-- 健康数据页(/health):概览 / 睡眠 / 体征 / 活动 / 身体。
     读侧接口:overview(单日)、sleep(会话)、sleep/{id}/stages(某晚分期)、metric(单指标趋势)。
     图表几何在 healthChart.js(纯函数 + 内联 SVG,不引图表库);指标名与单位只从 utils/healthMetrics.js 取。 -->
<template>
  <div class="page health-page">
    <div class="page-toolbar card">
      <div class="tb-left">
        <el-select v-if="canViewOthers && memberOptions.length > 1" v-model="targetUserId" class="member-select" @change="onMemberChange">
          <el-option v-for="m in memberOptions" :key="m.value" :label="m.label" :value="m.value" />
        </el-select>
        <span v-else class="tb-note">{{ $t('health.selfOnly') }}</span>
      </div>
      <div class="tb-right">
        <el-button :loading="exporting" @click="exportCsv">
          <el-icon><Download /></el-icon>
          <span class="btn-text">{{ $t('health.exportCsv') }}</span>
        </el-button>
        <el-button :loading="loading" @click="reloadAll">
          <el-icon><Refresh /></el-icon>
          <span class="btn-text">{{ $t('health.refresh') }}</span>
        </el-button>
        <router-link to="/settings?tab=health" class="tb-link">{{ $t('health.settings') }}</router-link>
        <el-button v-if="viewingSelf" text type="danger" @click="clearVisible = true">{{ $t('health.clearEntry') }}</el-button>
      </div>
    </div>

    <el-tabs v-model="tab" class="health-tabs" @tab-change="onTabChange">
      <!-- 概览:今日四卡 + 近 7 天迷你趋势 -->
      <el-tab-pane :label="$t('health.tabs.overview')" name="overview">
        <div v-if="!hasAny" class="card empty-card">
          <el-empty :image-size="88" :description="$t('health.noData')">
            <router-link to="/settings?tab=health">
              <el-button type="primary">{{ $t('health.setup') }}</el-button>
            </router-link>
          </el-empty>
        </div>
        <div v-else class="tab-body">
          <div class="stat-grid">
            <div class="card stat-card">
              <div class="stat-label">{{ metricName($t, 'SLEEP') }}</div>
              <div class="stat-val">{{ sleepText($t, daySleep ? daySleep.totalMin : null) }}</div>
              <div class="stat-sub">{{ overviewDate }}</div>
            </div>
            <div class="card stat-card">
              <div class="stat-label">{{ metricName($t, 'STEPS') }}</div>
              <div class="stat-val">{{ withUnit('STEPS', formatMetric('STEPS', dayValue('STEPS'))) }}</div>
              <div class="stat-sub">{{ overviewDate }}</div>
            </div>
            <div class="card stat-card">
              <div class="stat-label">{{ metricName($t, 'RESTING_HR') }}</div>
              <div class="stat-val">{{ withUnit('RESTING_HR', formatMetric('RESTING_HR', dayValue('RESTING_HR'))) }}</div>
              <div class="stat-sub">{{ overviewDate }}</div>
            </div>
            <div class="card stat-card">
              <div class="stat-label">{{ metricName($t, 'SPO2') }}</div>
              <div class="stat-val">{{ withUnit('SPO2', formatMetric('SPO2', dayValue('SPO2'))) }}</div>
              <div class="stat-sub">{{ overviewDate }}</div>
            </div>
          </div>

          <div class="card chart-card">
            <div class="section-label">{{ $t('health.overview.sleep7') }}</div>
            <HealthTrendChart
              v-if="sleep7.values.length"
              mode="bar"
              :values="sleep7.values"
              :labels="sleep7.labels"
              :label="metricName($t, 'SLEEP')"
            />
            <div v-else class="empty-hint">{{ $t('health.noDataShort') }}</div>
          </div>

          <div class="card chart-card">
            <div class="section-label">{{ $t('health.overview.steps7') }}</div>
            <HealthTrendChart
              v-if="steps7.values.length"
              mode="bar"
              :values="steps7.values"
              :labels="steps7.labels"
              :label="metricName($t, 'STEPS')"
            />
            <div v-else class="empty-hint">{{ $t('health.noDataShort') }}</div>
          </div>
        </div>
      </el-tab-pane>

      <!-- 睡眠:时长周柱(点柱子选某一晚) + 分期堆叠条 + 入睡散点 + 选中那晚的时间轴 -->
      <el-tab-pane :label="$t('health.tabs.sleep')" name="sleep">
        <div class="tab-body">
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

    <el-dialog v-model="clearVisible" :title="$t('settings.health.clearTitle')" class="dialog-sm" append-to-body>
      <p class="dialog-hint">{{ $t('settings.health.clearHint') }}</p>
      <el-select v-model="clearSource" class="clear-select">
        <el-option :label="$t('settings.health.sourceShortcut')" value="SHORTCUT" />
        <el-option :label="$t('settings.health.sourceHealthConnect')" value="HEALTH_CONNECT" />
        <el-option :label="$t('settings.health.sourceNative')" value="IOS_NATIVE" />
      </el-select>
      <template #footer>
        <el-button @click="clearVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="danger" :loading="clearing" @click="doClear">{{ $t('settings.health.clear') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Refresh, Download } from '@element-plus/icons-vue'
import { healthApi, memberApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { saveBlobResponse } from '@/utils/download'
import HealthTrendChart from './HealthTrendChart.vue'
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

const { t } = useI18n()
const route = useRoute()
const userStore = useUserStore()

const TABS = ['overview', 'sleep', 'vitals', 'activity', 'body']
// 一天多次采样的点指标只画今天,不然几百个点在 360px 上糊成一条带
const DENSE_POINT = ['HEART_RATE', 'SPO2', 'BODY_TEMP', 'BLOOD_GLUCOSE', 'BP_SYSTOLIC', 'BP_DIASTOLIC']
const RANGE_DAYS = 14
const SLEEP_DAYS = 14
// 导出范围默认近 90 天(与服务端默认一致);更长的区间不在界面上选,直接用接口参数
const EXPORT_DAYS = 90

const tab = ref(TABS.includes(String(route.query.tab || '')) ? String(route.query.tab) : 'overview')
const loading = ref(false)
const exporting = ref(false)
const members = ref([])
const targetUserId = ref(userStore.userInfo?.id ?? null)
const overviewDay = ref(null)
const nights = ref([])
const series = ref({})
const pickedId = ref(null)
const sleepDetail = ref(null)
const detailLoading = ref(false)
const clearVisible = ref(false)
const clearSource = ref('SHORTCUT')
const clearing = ref(false)
const selected = reactive({ vitals: 'HEART_RATE', activity: 'STEPS', body: 'WEIGHT' })

const metricPanes = [
  { name: 'vitals', labelKey: 'health.tabs.vitals', codes: TAB_METRICS.VITAL },
  { name: 'activity', labelKey: 'health.tabs.activity', codes: TAB_METRICS.ACTIVITY },
  { name: 'body', labelKey: 'health.tabs.body', codes: TAB_METRICS.BODY },
]

const legend = computed(() => SLEEP_STAGES.slice(0, 4))
const selfId = computed(() => userStore.userInfo?.id ?? null)
const viewingId = computed(() => targetUserId.value || selfId.value)
const viewingSelf = computed(() => !selfId.value || viewingId.value === selfId.value)
// 家长恒可看全家;其他成员要 health:view 才给切人(服务端也会再校验一次)
const canViewOthers = computed(() => userStore.isOwner || userStore.hasPerm('health:view'))

const memberOptions = computed(() => {
  const list = []
  const me = userStore.userInfo
  if (me?.id) list.push({ value: me.id, label: me.nickname || me.username || `#${me.id}` })
  members.value.forEach((m) => {
    if (m.id && m.id !== me?.id) list.push({ value: m.id, label: m.nickname || m.username || `#${m.id}` })
  })
  return list
})

const hasAny = computed(() => !!(daySleep.value || (overviewDay.value?.metrics || []).length || nights.value.length))
const overviewDate = computed(() => overviewDay.value?.date || isoDay(new Date()))
const daySleep = computed(() => overviewDay.value?.sleep || null)
const dayValue = (code) => {
  const row = (overviewDay.value?.metrics || []).find((m) => m.metric === code)
  return row ? row.value : null
}

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

const sleep7 = computed(() => ({
  labels: nightLabels.value.slice(-7),
  values: nightValues.value.slice(-7),
}))
const steps7 = computed(() => tail(seriesFor('STEPS'), 7))

const EMPTY_SERIES = { labels: [], values: [], points: [] }
const seriesKey = (metric) => `${viewingId.value}:${metric}`
const seriesFor = (metric) => series.value[seriesKey(metric)] || EMPTY_SERIES
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
const rangeDays = (code) => (DENSE_POINT.includes(code) ? 1 : RANGE_DAYS)
const tail = (obj, n) => ({ labels: obj.labels.slice(-n), values: obj.values.slice(-n), points: obj.points.slice(-n) })

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

async function loadOverview() {
  overviewDay.value = (await healthApi.overview({ userId: viewingId.value }).catch(() => null)) || null
}

async function loadNights() {
  const data = await healthApi.sleep({ userId: viewingId.value, from: shiftDay(-(SLEEP_DAYS - 1)), to: shiftDay(0) }).catch(() => null)
  const items = Array.isArray(data?.items) ? data.items.slice() : []
  items.sort((a, b) => String(a.sleepDate).localeCompare(String(b.sleepDate)))
  nights.value = items
}

async function loadSeries(metric) {
  if (!metric) return EMPTY_SERIES
  const key = seriesKey(metric)
  if (series.value[key]) return series.value[key]
  const days = rangeDays(metric)
  const data = await healthApi.metric({ userId: viewingId.value, metric, from: shiftDay(-(days - 1)), to: shiftDay(0) }).catch(() => null)
  const points = Array.isArray(data?.points) ? data.points : []
  const built = periodOf(metric) === 'DAY' ? daySeries(points, days) : pointSeries(points, days)
  series.value = { ...series.value, [key]: built }
  return built
}

function resetDetail() {
  pickedId.value = null
  sleepDetail.value = null
}

async function reloadAll() {
  if (!userStore.isLoggedIn || !viewingId.value) return
  loading.value = true
  try {
    series.value = {}
    resetDetail()
    await Promise.all([loadOverview(), loadNights(), loadSeries('STEPS')])
    if (['vitals', 'activity', 'body'].includes(tab.value)) await loadSeries(selected[tab.value])
  } finally {
    loading.value = false
  }
}

async function onMemberChange() {
  resetDetail()
  await reloadAll()
}

async function onTabChange(name) {
  if (['vitals', 'activity', 'body'].includes(name)) await loadSeries(selected[name])
}

async function selectMetric(name, code) {
  selected[name] = code
  await loadSeries(code)
}

async function pickNight(index) {
  const night = nights.value[index]
  if (!night) return
  pickedId.value = night.id
  sleepDetail.value = null
  detailLoading.value = true
  try {
    sleepDetail.value = await healthApi.stages(night.id).catch(() => null)
  } finally {
    detailLoading.value = false
  }
}

async function doClear() {
  clearing.value = true
  try {
    const data = await healthApi.clearData(clearSource.value)
    ElMessage.success(t('settings.health.cleared', { count: (data?.samples || 0) + (data?.sleeps || 0) }))
    clearVisible.value = false
    await reloadAll()
  } catch (e) {
    ElMessage.error(e?.message || t('common.failed'))
  } finally {
    clearing.value = false
  }
}

/** 导出当前成员近 90 天的长表 CSV:成功直接落盘;失败时服务端仍回 JSON,由 saveBlobResponse 抛出可读文案 */
async function exportCsv() {
  if (!userStore.isLoggedIn || !viewingId.value) return
  const from = shiftDay(-(EXPORT_DAYS - 1))
  const to = shiftDay(0)
  exporting.value = true
  try {
    const blob = await healthApi.exportCsv({ userId: viewingId.value, from, to })
    await saveBlobResponse(blob, `health-${from}_${to}.csv`)
    ElMessage.success(t('health.exportDone', { from, to }))
  } catch (e) {
    ElMessage.error(e?.message || t('common.failed'))
  } finally {
    exporting.value = false
  }
}

onMounted(async () => {
  if (canViewOthers.value) {
    const list = await memberApi.list().catch(() => [])
    members.value = Array.isArray(list) ? list : []
  }
  await reloadAll()
})
</script>

<style scoped>
.member-select {
  width: 170px;
}
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
.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: 12px;
}
.stat-card {
  padding: 14px 16px;
}
.stat-label {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.stat-val {
  margin: 6px 0 2px;
  font-size: 20px;
  font-weight: 600;
  color: var(--color-text-primary);
}
.stat-sub {
  font-size: 11px;
  color: var(--color-text-secondary);
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
.clear-select {
  width: 100%;
}
</style>
