<!-- 健康报表(本人看 + 分享快照共用):按后端返回的 cards 顺序渲染,每张卡片只认 card.id。
     数据窗口与「最近一天」都由后端算好(HealthReportService),这里不做任何取数判断,
     分享快照页直接把同一份 JSON 丢进来即可,不需要再登录。 -->
<template>
  <div v-if="report" class="report">
    <div class="report-meta">
      <span>{{ $t('health.report.asOf', { date: anchorText }) }}</span>
      <span v-if="readOnly" class="readonly-badge">{{ $t('health.report.readOnlySnapshot') }}</span>
    </div>

    <div class="report-grid">
      <section
        v-for="card in cards"
        :key="card.id"
        class="card report-card"
        :class="[`report-card--${card.id}`, { 'is-wide': WIDE_CARDS.includes(card.id) }]"
      >
        <div class="report-head">
          <span class="section-label">{{ cardTitle($t, card.id) }}</span>
          <span class="report-sub">{{ subText(card) }}</span>
        </div>

        <div v-if="!card.hasData" class="empty-hint">{{ $t('health.noDataShort') }}</div>

        <!-- 最近一晚睡眠:时长 + 入睡/起床 + 睡眠节奏轴 -->
        <template v-else-if="card.id === 'sleep_last'">
          <div class="hero-row">
            <span class="hero-val">{{ sleepText($t, card.sleep?.totalMin) }}</span>
            <span class="hero-sub"> {{ clockText(card.sleep?.startTime) }} – {{ clockText(card.sleep?.endTime) }} </span>
          </div>
          <HealthSleepNight
            v-if="card.stages?.length"
            :stages="card.stages"
            :start-time="card.sleep?.startTime"
            :end-time="card.sleep?.endTime"
            :label="cardTitle($t, card.id)"
          />
          <div v-else class="empty-hint">{{ $t('health.sleep.noStages') }}</div>
        </template>

        <!-- 异常提醒:参考区间 + 个人基线 -->
        <HealthAlertCard v-else-if="card.id === 'alerts'" :card="card" />

        <!-- 近 7 天序列:柱状 + 合计/日均 -->
        <template v-else-if="SERIES_CARDS.includes(card.id)">
          <div class="hero-row">
            <span class="hero-val">{{ heroText(card) }}</span>
            <span class="hero-sub">{{ subStatText(card) }}</span>
          </div>
          <HealthTrendChart
            :mode="card.id === 'resting_hr_7d' ? 'line' : 'bar'"
            :values="seriesValues(card)"
            :labels="seriesLabels(card)"
            :label="cardTitle($t, card.id)"
          />
        </template>

        <!-- 最近一天的点指标(心率/血氧):折线 + 最小-最大带 -->
        <template v-else-if="POINT_CARDS.includes(card.id)">
          <div class="hero-row">
            <span class="hero-val">{{ withUnit(card.metric, formatMetric(card.metric, card.avg)) }}</span>
            <span class="hero-sub">
              {{ withUnit(card.metric, formatMetric(card.metric, card.min)) }} –
              {{ withUnit(card.metric, formatMetric(card.metric, card.max)) }}
            </span>
          </div>
          <HealthTrendChart
            mode="line"
            :values="pointValues(card)"
            :labels="pointLabels(card)"
            :points="pointBands(card)"
            :label="cardTitle($t, card.id)"
          />
        </template>

        <!-- 体重趋势:近 30 天折线 + 区间变化 -->
        <template v-else-if="card.id === 'weight_trend'">
          <div class="hero-row">
            <span class="hero-val">{{ withUnit(card.metric, formatMetric(card.metric, card.latest)) }}</span>
            <span class="hero-sub">{{ changeText(card) }}</span>
          </div>
          <HealthTrendChart mode="line" :values="weightValues(card)" :labels="weightLabels(card)" :label="cardTitle($t, card.id)" />
        </template>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import HealthTrendChart from './HealthTrendChart.vue'
import HealthSleepNight from './HealthSleepNight.vue'
import HealthAlertCard from './report/HealthAlertCard.vue'
import { cardMeta, cardTitle } from './reportCards'
import { clockText, dayLabel, formatMetric, sleepText, withUnit } from '@/utils/healthMetrics'

const props = defineProps({
  /** HealthReportService.build 的返回(或分享快照里的同一份 JSON) */
  report: { type: Object, default: null },
  /** 分享快照页为 true:顶部标一行「只读快照」 */
  readOnly: { type: Boolean, default: false },
})

const { t } = useI18n()

const SERIES_CARDS = ['sleep_7d', 'steps_7d', 'energy_7d', 'exercise_7d', 'resting_hr_7d', 'menstrual_7d']
const POINT_CARDS = ['hr_day', 'spo2_day']
const WIDE_CARDS = ['sleep_last', 'alerts']
const isoText = (v) => (v ? String(v).slice(0, 10) : '—')
const anchorText = computed(() => isoText(props.report?.anchorDate))

const cards = computed(() => (Array.isArray(props.report?.cards) ? props.report.cards.filter((c) => cardMeta(c.id)) : []))

function subText(card) {
  if (card.date) return isoText(card.date)
  if (card.from && card.to) return `${dayLabel(card.from)} – ${dayLabel(card.to)}`
  return ''
}

const seriesLabels = (card) => (card.items || []).map((i) => dayLabel(i.date))
const seriesValues = (card) => (card.items || []).map((i) => (i.value == null ? null : Number(i.value)))
const pointLabels = (card) => (card.points || []).map((p) => clockText(p.startTime))
const pointValues = (card) => (card.points || []).map((p) => Number(p.value))
const pointBands = (card) => (card.points || []).map((p) => ({ min: p.min, max: p.max }))
const weightLabels = (card) => (card.points || []).map((p) => dayLabel(p.startTime))
const weightValues = (card) => (card.points || []).map((p) => Number(p.value))

function heroText(card) {
  if (card.id === 'sleep_7d') return sleepText(t, card.total)
  if (card.id === 'resting_hr_7d') return withUnit(card.metric, formatMetric(card.metric, card.avg))
  return withUnit(card.metric, formatMetric(card.metric, card.total))
}

function subStatText(card) {
  if (card.id === 'sleep_7d') return `${t('health.report.avgNight')} ${sleepText(t, card.avg)}`
  return `${t('health.report.avg')} ${withUnit(card.metric, formatMetric(card.metric, card.avg))}`
}

function changeText(card) {
  const change = Number(card.change)
  if (!Number.isFinite(change)) return ''
  const sign = change > 0 ? '+' : ''
  return `${t('health.report.change')} ${sign}${formatMetric(card.metric, change)} ${card.unit || ''}`.trim()
}
</script>

<style scoped>
.report-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  font-size: 12px;
  color: var(--color-text-secondary);
}
.readonly-badge {
  padding: 1px 8px;
  border: 1px solid var(--color-border);
  border-radius: 999px;
}
.report-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 12px;
}
.report-card {
  padding: 14px 16px;
}
.report-card.is-wide {
  grid-column: 1 / -1;
}
.report-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}
.report-sub {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.hero-row {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 10px;
  margin: 6px 0 2px;
}
.hero-val {
  font-size: 20px;
  font-weight: 600;
  color: var(--color-text-primary);
}
.hero-sub {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.empty-hint {
  padding: 10px 0;
  font-size: 13px;
  color: var(--color-text-secondary);
}
</style>
