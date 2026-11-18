<!-- 某一晚的睡眠进度轴(内联 SVG,不引图表库):
     横轴是钟点(入睡前 / 起床后各留半小时再扩到整点),纵轴四行自下而上「深睡 → 核心 → 快速眼动 → 清醒」。
     几何在 healthChart.js 的 sleepAxis / sleepLaneBars 里算好,模板只取值。 -->
<template>
  <div class="lane">
    <svg class="lane-svg" :viewBox="`0 0 ${LANE.w} ${LANE.h}`" role="img" :aria-label="label">
      <g class="lane-grid">
        <line
          v-for="t in ticks"
          :key="`g${t.label}`"
          :x1="t.x"
          :x2="t.x"
          :y1="LANE.padT"
          :y2="LANE.padT + 4 * LANE.rowH + 3 * LANE.rowGap"
        />
      </g>

      <g class="lane-rows">
        <text
          v-for="r in rows"
          :key="`r${r.code}`"
          :x="LANE.padL - 6"
          :y="r.y + LANE.rowH / 2 + 3"
          :fill="colorOf(r.code)"
          text-anchor="end"
        >
          {{ stageName(r.code) }}
        </text>
      </g>

      <g class="lane-bars">
        <rect v-for="(b, i) in bars" :key="i" :x="b.x" :y="b.y" :width="b.w" :height="b.h" rx="2" :fill="colorOf(b.code)">
          <title>{{ tip(b) }}</title>
        </rect>
      </g>

      <g class="lane-x">
        <text v-for="t in ticks" :key="`t${t.label}`" :x="t.x" :y="LANE.h - 8" text-anchor="middle">{{ t.label }}</text>
      </g>
    </svg>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { LANE, sleepAxis, sleepAxisTicks, sleepLaneBars, sleepLaneRows } from './healthChart'
import { sleepText, stageMeta } from '@/utils/healthMetrics'

const props = defineProps({
  /** 分期区间:[{ stage, startTime, endTime, durationMin }] */
  stages: { type: Array, default: () => [] },
  startTime: { type: String, default: '' },
  endTime: { type: String, default: '' },
  label: { type: String, default: '' },
})

const { t } = useI18n()

const rows = computed(() => sleepLaneRows())
const axis = computed(() => sleepAxis(props.startTime, props.endTime, props.stages))
const bars = computed(() => sleepLaneBars(props.stages, axis.value))
const ticks = computed(() => sleepAxisTicks(axis.value))

const colorOf = (code) => stageMeta(code)?.color || 'var(--health-stage-unknown)'
const stageName = (code) => t(stageMeta(code)?.labelKey || 'health.stage.unknown')

const clockOf = (ms) => {
  const d = new Date(ms)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}
const tip = (b) => `${stageName(b.code)} ${clockOf(b.from)}–${clockOf(b.to)} ${sleepText(t, b.minutes)}`
</script>

<style scoped>
.lane {
  width: 100%;
}
.lane-svg {
  display: block;
  width: 100%;
  max-width: 640px;
  height: auto;
}
.lane-grid line {
  stroke: var(--color-border);
  stroke-width: 0.6;
  stroke-dasharray: 3 3;
}
.lane-rows text,
.lane-x text {
  font-size: 8px;
}
.lane-x text {
  fill: var(--color-text-secondary);
}
.lane-bars rect {
  opacity: 0.85;
}
</style>
