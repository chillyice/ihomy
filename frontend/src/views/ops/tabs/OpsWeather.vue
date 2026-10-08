<template>
  <div v-loading="weatherLoading">
    <!-- 1) 调用趋势(最关注):时间段 + API 类型多选筛选 -->
    <div class="filter-row">
      <el-radio-group v-model="timelineRange" size="small" @change="loadTimeline">
        <el-radio-button value="24h">{{ $t('ops.weather24h') }}</el-radio-button>
        <el-radio-button value="month">{{ $t('ops.weatherMonth') }}</el-radio-button>
        <el-radio-button value="30d">{{ $t('ops.weather30d') }}</el-radio-button>
        <el-radio-button value="year">{{ $t('ops.weatherYear') }}</el-radio-button>
      </el-radio-group>
      <el-select
        v-model="timelineTypes"
        multiple
        filterable
        clearable
        collapse-tags
        collapse-tags-tooltip
        :placeholder="$t('ops.weatherApiType')"
        style="width: 240px"
        @change="loadTimeline"
      >
        <el-option v-for="at in WEATHER_API_TYPES" :key="at" :value="at" :label="$t('ops.apiType.' + at)" />
      </el-select>
    </div>
    <div v-if="timelineData.length || pieSlices.length" class="charts-row">
      <div class="chart-wrap chart-hover-wrap" style="flex: 1.6">
        <div class="chart-summary">
          <span
            >{{ $t('ops.weatherTotalCalls') }} <b>{{ timelineTotal }}</b></span
          >
          <span
            >{{ $t('ops.trafficFailed') }} <b :class="{ 'fail-num': timelineFailed > 0 }">{{ timelineFailed }}</b></span
          >
          <span
            >{{ $t('ops.weatherFailRate') }} <b>{{ timelineFailRate }}%</b></span
          >
        </div>
        <OpsLineChart
          :data="timelineData"
          :y-ticks="yTicks"
          :x-ticks="xLabels"
          :total-points="linePoints(timelineData.map((d) => d.total))"
          :failed-points="linePoints(timelineData.map((d) => d.failed))"
          :x-pos="xPos"
          :y-val="yVal"
          :hover-col-w="hoverColW"
          v-model:hoverIdx="hoverIdx"
        />
        <div class="chart-legend">
          <span class="legend-item"><span class="legend-dot" style="background: #b88c6e"></span>{{ $t('ops.weatherTotalCalls') }}</span>
          <span class="legend-item"><span class="legend-dot" style="background: #b04a3a"></span>{{ $t('ops.trafficFailed') }}</span>
        </div>
        <!-- 数据点悬浮提示 -->
        <div v-if="hoverIdx >= 0 && timelineData[hoverIdx]" class="chart-tooltip" :style="tooltipStyle">
          <div class="ct-time">{{ timelineData[hoverIdx].time_bucket }}</div>
          <div class="ct-row">
            <span class="ct-dot" style="background: #b88c6e"></span>{{ $t('ops.weatherTotalCalls') }}
            <b>{{ timelineData[hoverIdx].total }}</b>
          </div>
          <div class="ct-row">
            <span class="ct-dot" style="background: #b04a3a"></span>{{ $t('ops.trafficFailed') }}
            <b>{{ timelineData[hoverIdx].failed }}</b>
          </div>
        </div>
      </div>
      <div class="chart-wrap" style="flex: 1">
        <div class="ops-sub-title">{{ $t('ops.weatherTypeShare') }}</div>
        <svg v-if="pieSlices.length" viewBox="0 0 480 260" class="pie-chart">
          <path v-for="(s, i) in pieSlices" :key="'ps' + i" :d="s.path" :fill="s.color" stroke="var(--color-card-2)" stroke-width="2" />
          <path v-for="(s, i) in pieSlices" :key="'pl' + i" :d="s.line" fill="none" stroke="var(--color-text-secondary)" stroke-width="1" />
          <text
            v-for="(s, i) in pieSlices"
            :key="'pt' + i"
            :x="s.labelX"
            :y="s.labelY"
            :text-anchor="s.anchor"
            font-size="12"
            fill="var(--color-text)"
          >
            {{ s.label }}
          </text>
        </svg>
        <el-empty v-else :description="$t('ops.weatherNoData')" :image-size="40" />
      </div>
    </div>
    <el-empty v-else-if="!timelineData.length" :description="$t('ops.weatherNoData')" :image-size="40" />

    <!-- 2) 本月配额(本地统计):4 卡片一行 + 横向进度条 -->
    <h4 class="ops-section-title section-gap">{{ $t('ops.weatherQuota') }}</h4>
    <div class="quota-grid">
      <div class="finance-card">
        <div class="finance-label">{{ $t('ops.weatherQuotaUsed') }}</div>
        <div class="finance-value">{{ weatherQuota?.used ?? '-' }}</div>
      </div>
      <div class="finance-card">
        <div class="finance-label">{{ $t('ops.weatherQuotaLimit') }}</div>
        <div class="finance-value">{{ weatherQuota?.quota ?? '-' }}</div>
      </div>
      <div class="finance-card">
        <div class="finance-label">{{ $t('ops.weatherQuotaRemaining') }}</div>
        <div class="finance-value">{{ weatherQuota?.remaining ?? '-' }}</div>
      </div>
      <div class="finance-card">
        <div class="finance-label">{{ $t('ops.weatherQuotaPercent') }}</div>
        <div class="finance-value">{{ weatherQuota ? weatherQuota.usagePercent + '%' : '-' }}</div>
      </div>
    </div>
    <div class="quota-progress">
      <div class="qp-track">
        <div class="qp-fill" :class="quotaBarClass" :style="{ width: Math.min(weatherQuota?.usagePercent || 0, 100) + '%' }"></div>
      </div>
      <span class="qp-text">{{ (weatherQuota?.used ?? 0).toLocaleString() }} / {{ (weatherQuota?.quota ?? 50000).toLocaleString() }}</span>
    </div>

    <!-- 3) 24h 请求量(按 API,成功/错误/失败率合并一表;控制台数据仅 OPS 可见) -->
    <template v-if="isOps">
      <h4 class="ops-section-title section-gap">{{ $t('ops.weather24hStats') }}</h4>
      <el-table v-if="weatherStatRows.length" :data="weatherStatRows" size="small" stripe>
        <el-table-column prop="api" label="API" min-width="140" />
        <el-table-column prop="ok" :label="$t('ops.weatherSuccess')" width="110" />
        <el-table-column prop="err" :label="$t('ops.weatherError')" width="110" />
        <el-table-column :label="$t('ops.weatherFailRate')" width="110">
          <template #default="{ row }">{{ row.failRate }}%</template>
        </el-table-column>
      </el-table>
      <el-alert v-else-if="!weatherLoading" type="warning" :closable="false" show-icon :title="$t('ops.weatherStatsFail')" />

      <!-- 4) 财务汇总(最不关注,放最底) -->
      <h4 class="ops-section-title section-gap">{{ $t('ops.weatherFinance') }}</h4>
      <div v-if="weatherFinance" class="finance-grid">
        <div class="finance-card">
          <div class="finance-label">{{ $t('ops.weatherBalance') }}</div>
          <div class="finance-value">{{ weatherFinance.currency || 'CNY' }} {{ weatherFinance.balance ?? '-' }}</div>
        </div>
        <div class="finance-card">
          <div class="finance-label">{{ $t('ops.weatherThisMonth') }}</div>
          <div class="finance-value">{{ weatherFinance.currency || 'CNY' }} {{ weatherFinance.thisMonth ?? '0' }}</div>
        </div>
        <div class="finance-card">
          <div class="finance-label">{{ $t('ops.weatherYesterday') }}</div>
          <div class="finance-value">{{ weatherFinance.currency || 'CNY' }} {{ weatherFinance.previousDay ?? '0' }}</div>
        </div>
      </div>
      <el-alert v-else-if="!weatherLoading" type="warning" :closable="false" show-icon :title="$t('ops.weatherFinanceFail')" />
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { opsApi } from '@/api'
import { useUserStore } from '@/stores/user'
import OpsLineChart from '@/components/OpsLineChart.vue'
import {
  buildPieSlices,
  chartH,
  chartW,
  linePointsFor,
  padB,
  padL,
  padR,
  padT,
  tooltipStyleFor,
  xLabelsFor,
  xPosFor,
  yMaxFor,
  yTicksFor,
} from '../opsChart'

const { t } = useI18n()
const userStore = useUserStore()
const isOps = computed(() => userStore.isOps)

const weatherLoading = ref(false)
const weatherQuota = ref(null)
const weatherFinance = ref(null)
const weatherStats = ref(null)

const timelineRange = ref('24h')
const timelineData = ref([])

// 天气 API 类型(与后端 parseApiType 对齐)
const WEATHER_API_TYPES = [
  'now',
  'forecast',
  'hourly',
  'warning',
  'air',
  'indices',
  'minutely',
  'location',
  'quota',
  'finance',
  'metrics',
  'other',
]
const timelineTypes = ref([])

const loadTimeline = async () => {
  loadTypeDist() // 饼图与折线图共用时间范围,并行加载
  try {
    timelineData.value = await opsApi.weatherTimeline(timelineRange.value, timelineTypes.value)
  } catch (e) {
    timelineData.value = []
  }
}

// 天气折线图(绑定天气数据)
const yMax = computed(() => yMaxFor(timelineData.value))
const yTicks = computed(() => yTicksFor(yMax.value))
const xLabels = computed(() => xLabelsFor(timelineData.value))
const xPos = (i) => xPosFor(timelineData.value, i)
const linePoints = (vals) => linePointsFor(timelineData.value, yMax.value, vals)

// ---------- 折线图悬浮提示 ----------
const hoverIdx = ref(-1)
const hoverColW = computed(() => (chartW - padL - padR) / Math.max(timelineData.value.length, 1))
const yVal = (v) => chartH - padB - ((chartH - padB - padT) * v) / yMax.value
const tooltipStyle = computed(() => tooltipStyleFor(timelineData.value, hoverIdx.value))

// ---------- API 类型占比饼图(与折线图共用时间范围) ----------
const typeDist = ref([])
const loadTypeDist = async () => {
  try {
    typeDist.value = await opsApi.weatherTypeDistribution(timelineRange.value)
  } catch (e) {
    typeDist.value = []
  }
}
const pieSlices = computed(() => buildPieSlices(typeDist.value || [], (d) => t('ops.apiType.' + (d.apiType || 'other'))))

// ---------- 配额进度条 ----------
const quotaBarClass = computed(() => {
  const pct = weatherQuota.value?.usagePercent || 0
  return pct >= 90 ? 'danger' : pct >= 70 ? 'warn' : ''
})

// 趋势摘要(所选范围合计)
const timelineTotal = computed(() => timelineData.value.reduce((a, d) => a + d.total, 0))
const timelineFailed = computed(() => timelineData.value.reduce((a, d) => a + d.failed, 0))
const timelineFailRate = computed(() =>
  timelineTotal.value === 0 ? 0 : Math.round((timelineFailed.value * 1000) / timelineTotal.value) / 10,
)

// 24h 请求量:成功/错误两表合一,补失败率
const weatherStatRows = computed(() => {
  const s = weatherStats.value
  if (!s) return []
  const map = new Map()
  for (const r of s.success || []) {
    map.set(r.api, { api: r.api, ok: (r.hours || []).reduce((a, b) => a + b, 0), err: 0 })
  }
  for (const r of s.errors || []) {
    const row = map.get(r.api) || { api: r.api, ok: 0, err: 0 }
    row.err = (r.hours || []).reduce((a, b) => a + b, 0)
    map.set(r.api, row)
  }
  return [...map.values()].map((r) => ({
    ...r,
    failRate: r.ok + r.err === 0 ? 0 : Math.round((r.err * 1000) / (r.ok + r.err)) / 10,
  }))
})

const loadWeatherQuota = async () => {
  weatherLoading.value = true
  loadTimeline() // 与下面并行,不串行等待
  try {
    if (isOps.value) {
      const [quota, finance, stats] = await Promise.allSettled([opsApi.weatherQuota(), opsApi.weatherFinance(), opsApi.weatherStats()])
      weatherQuota.value = quota.status === 'fulfilled' ? quota.value : null
      weatherFinance.value = finance.status === 'fulfilled' ? finance.value : null
      weatherStats.value = stats.status === 'fulfilled' ? stats.value : null
    } else {
      // 家长:仅本地统计(趋势/类型占比/本月配额),控制台财务/请求量需 ops:view 故跳过
      weatherQuota.value = await opsApi.weatherQuota()
    }
  } catch (e) {
    weatherQuota.value = null
    weatherFinance.value = null
    weatherStats.value = null
  } finally {
    weatherLoading.value = false
  }
}

onMounted(loadWeatherQuota)
</script>

<style scoped>
.filter-row {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 16px;
  align-items: center;
}
.ops-section-title {
  font-size: 14px;
  font-weight: 600;
  margin: 0 0 10px;
  color: var(--color-text);
}
.ops-sub-title {
  font-size: 13px;
  font-weight: 500;
  margin: 0 0 6px;
  color: var(--color-text-secondary);
}
.finance-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}
.finance-card {
  padding: 14px 16px;
  text-align: center;
  background: var(--color-card-2);
  border-radius: 10px;
}
.finance-label {
  font-size: 12px;
  color: var(--color-text-secondary);
  margin-bottom: 6px;
}
.finance-value {
  font-size: 18px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}
.charts-row {
  display: flex;
  gap: 14px;
  align-items: stretch;
}
.chart-wrap {
  background: var(--color-card-2);
  border-radius: 10px;
  padding: 16px;
  position: relative;
}
.line-chart {
  width: 100%;
  height: auto;
  display: block;
}
.pie-chart {
  width: 100%;
  height: auto;
  display: block;
  max-width: 420px;
  margin: 0 auto;
}
.chart-tooltip {
  position: absolute;
  top: 26px;
  transform: translateX(-50%);
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 12px;
  pointer-events: none;
  z-index: 5;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  white-space: nowrap;
}
.ct-time {
  color: var(--color-text-secondary);
  margin-bottom: 4px;
}
.ct-row {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--color-text);
}
.ct-row b {
  font-variant-numeric: tabular-nums;
}
.ct-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex: none;
}
.quota-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}
.quota-progress {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
}
.qp-track {
  flex: 1;
  height: 10px;
  background: var(--color-card-2);
  border-radius: 5px;
  overflow: hidden;
}
.qp-fill {
  height: 100%;
  border-radius: 5px;
  background: var(--color-brand);
  transition: width 0.4s ease;
}
.qp-fill.warn {
  background: #d4a13f;
}
.qp-fill.danger {
  background: #b04a3a;
}
.qp-text {
  font-size: 13px;
  color: var(--color-text-secondary);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.chart-summary {
  display: flex;
  gap: 24px;
  font-size: 13px;
  color: var(--color-text-secondary);
  margin-bottom: 10px;
}
.chart-summary b {
  color: var(--color-text);
  font-variant-numeric: tabular-nums;
}
.chart-summary .fail-num {
  color: #b04a3a;
}
.section-gap {
  margin-top: 24px;
}
.chart-legend {
  display: flex;
  gap: 20px;
  justify-content: center;
  margin-top: 8px;
}
.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--color-text-secondary);
}
.legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}
@media (max-width: 768px) {
  .charts-row {
    flex-direction: column;
  }
  .quota-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
