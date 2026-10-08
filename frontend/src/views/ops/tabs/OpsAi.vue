<template>
  <div v-loading="aiLoading">
    <!-- 汇总卡:总调用/失败/成功率 -->
    <div class="quota-grid">
      <div class="finance-card">
        <div class="finance-label">{{ $t('ops.aiTotalCalls') }}</div>
        <div class="finance-value">{{ aiSummary?.total ?? '-' }}</div>
      </div>
      <div class="finance-card">
        <div class="finance-label">{{ $t('ops.aiFailed') }}</div>
        <div class="finance-value">{{ aiSummary?.failed ?? '-' }}</div>
      </div>
      <div class="finance-card">
        <div class="finance-label">{{ $t('ops.aiSuccessRate') }}</div>
        <div class="finance-value">{{ aiSummary ? aiSummary.successRate + '%' : '-' }}</div>
      </div>
    </div>
    <!-- 调用趋势 + 功能占比 -->
    <div class="filter-row section-gap">
      <el-radio-group v-model="aiRange" size="small" @change="loadAiTimeline">
        <el-radio-button value="24h">{{ $t('ops.weather24h') }}</el-radio-button>
        <el-radio-button value="month">{{ $t('ops.weatherMonth') }}</el-radio-button>
        <el-radio-button value="30d">{{ $t('ops.weather30d') }}</el-radio-button>
        <el-radio-button value="year">{{ $t('ops.weatherYear') }}</el-radio-button>
      </el-radio-group>
      <el-select
        v-model="aiFeatures"
        multiple
        filterable
        clearable
        collapse-tags
        collapse-tags-tooltip
        :placeholder="$t('ops.aiFeatureFilter')"
        style="width: 240px"
        @change="loadAiTimeline"
      >
        <el-option v-for="f in AI_FEATURES" :key="f" :value="f" :label="$t('ops.aiFeature.' + f)" />
      </el-select>
    </div>
    <div v-if="aiTimelineData.length || aiPieSlices.length" class="charts-row">
      <div class="chart-wrap chart-hover-wrap" style="flex: 1.6">
        <div class="chart-summary">
          <span
            >{{ $t('ops.aiTotalCalls') }} <b>{{ aiTimelineTotal }}</b></span
          >
          <span
            >{{ $t('ops.aiFailed') }} <b :class="{ 'fail-num': aiTimelineFailed > 0 }">{{ aiTimelineFailed }}</b></span
          >
          <span
            >{{ $t('ops.weatherFailRate') }} <b>{{ aiTimelineFailRate }}%</b></span
          >
        </div>
        <OpsLineChart
          :data="aiTimelineData"
          :y-ticks="aiYTicks"
          :x-ticks="aiXLabels"
          :total-points="aiLinePoints(aiTimelineData.map((d) => d.total))"
          :failed-points="aiLinePoints(aiTimelineData.map((d) => d.failed))"
          :x-pos="aiXPos"
          :y-val="aiYVal"
          :hover-col-w="aiHoverColW"
          v-model:hoverIdx="aiHoverIdx"
        />
        <div class="chart-legend">
          <span class="legend-item"><span class="legend-dot" style="background: #b88c6e"></span>{{ $t('ops.aiTotalCalls') }}</span>
          <span class="legend-item"><span class="legend-dot" style="background: #b04a3a"></span>{{ $t('ops.aiFailed') }}</span>
        </div>
        <div v-if="aiHoverIdx >= 0 && aiTimelineData[aiHoverIdx]" class="chart-tooltip" :style="aiTooltipStyle">
          <div class="ct-time">{{ aiTimelineData[aiHoverIdx].time_bucket }}</div>
          <div class="ct-row">
            <span class="ct-dot" style="background: #b88c6e"></span>{{ $t('ops.aiTotalCalls') }}
            <b>{{ aiTimelineData[aiHoverIdx].total }}</b>
          </div>
          <div class="ct-row">
            <span class="ct-dot" style="background: #b04a3a"></span>{{ $t('ops.aiFailed') }}
            <b>{{ aiTimelineData[aiHoverIdx].failed }}</b>
          </div>
        </div>
      </div>
      <div class="chart-wrap" style="flex: 1">
        <div class="ops-sub-title">{{ $t('ops.aiFeatureShare') }}</div>
        <svg v-if="aiPieSlices.length" viewBox="0 0 480 260" class="pie-chart">
          <path v-for="(s, i) in aiPieSlices" :key="'aips' + i" :d="s.path" :fill="s.color" stroke="var(--color-card-2)" stroke-width="2" />
          <path
            v-for="(s, i) in aiPieSlices"
            :key="'aipl' + i"
            :d="s.line"
            fill="none"
            stroke="var(--color-text-secondary)"
            stroke-width="1"
          />
          <text
            v-for="(s, i) in aiPieSlices"
            :key="'aipt' + i"
            :x="s.labelX"
            :y="s.labelY"
            :text-anchor="s.anchor"
            font-size="12"
            fill="var(--color-text)"
          >
            {{ s.label }}
          </text>
        </svg>
        <el-empty v-else :description="$t('ops.aiNoData')" :image-size="40" />
      </div>
    </div>
    <el-empty v-else-if="!aiTimelineData.length" :description="$t('ops.aiNoData')" :image-size="40" />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { opsApi } from '@/api'
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

const AI_FEATURES = ['ITEM_FIND', 'ITEM_PUT', 'CHAT', 'IMAGE', 'WEATHER_IMAGE', 'ASR', 'OSS_UPGRADE_EVAL']
const aiLoading = ref(false)
const aiSummary = ref(null)
const aiRange = ref('24h')
const aiFeatures = ref([])
const aiTimelineData = ref([])
const aiTypeDist = ref([])
const aiHoverIdx = ref(-1)

const aiYMax = computed(() => yMaxFor(aiTimelineData.value))
const aiYTicks = computed(() => yTicksFor(aiYMax.value))
const aiXLabels = computed(() => xLabelsFor(aiTimelineData.value))
const aiXPos = (i) => xPosFor(aiTimelineData.value, i)
const aiYVal = (v) => chartH - padB - ((chartH - padB - padT) * v) / aiYMax.value
const aiLinePoints = (vals) => linePointsFor(aiTimelineData.value, aiYMax.value, vals)
const aiHoverColW = computed(() => (chartW - padL - padR) / Math.max(aiTimelineData.value.length, 1))
const aiTooltipStyle = computed(() => tooltipStyleFor(aiTimelineData.value, aiHoverIdx.value))
const aiPieSlices = computed(() => buildPieSlices(aiTypeDist.value || [], (d) => t('ops.aiFeature.' + (d.featureCode || 'OTHER'))))

const aiTimelineTotal = computed(() => aiTimelineData.value.reduce((a, d) => a + d.total, 0))
const aiTimelineFailed = computed(() => aiTimelineData.value.reduce((a, d) => a + d.failed, 0))
const aiTimelineFailRate = computed(() =>
  aiTimelineTotal.value === 0 ? 0 : Math.round((aiTimelineFailed.value * 1000) / aiTimelineTotal.value) / 10,
)

const loadAiSummary = async () => {
  try {
    aiSummary.value = await opsApi.aiSummary()
  } catch (e) {
    aiSummary.value = null
  }
}
const loadAiTypeDist = async () => {
  try {
    aiTypeDist.value = await opsApi.aiTypeDistribution(aiRange.value)
  } catch (e) {
    aiTypeDist.value = []
  }
}
const loadAiTimeline = async () => {
  loadAiTypeDist() // 饼图与折线图共用时间范围,并行加载
  try {
    aiTimelineData.value = await opsApi.aiTimeline(aiRange.value, aiFeatures.value)
  } catch (e) {
    aiTimelineData.value = []
  }
}
const loadAi = async () => {
  aiLoading.value = true
  try {
    await Promise.all([loadAiSummary(), loadAiTimeline()])
  } finally {
    aiLoading.value = false
  }
}

onMounted(loadAi)
</script>

<style scoped>
.quota-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
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
.filter-row {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 16px;
  align-items: center;
}
.section-gap {
  margin-top: 24px;
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
.ops-sub-title {
  font-size: 13px;
  font-weight: 500;
  margin: 0 0 6px;
  color: var(--color-text-secondary);
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
