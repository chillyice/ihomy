<!-- 健康页通用趋势图(内联 SVG,不引图表库):柱状 / 折线 / 折线+最小-最大带。
     数据由调用方按「与 labels 等长」的数组传入,null = 那天没数据(不画,不伪造 0)。
     mode=bar 时点柱子触发 pick(下标),睡眠页用它选某一晚看分期明细。 -->
<template>
  <div class="trend">
    <svg class="trend-svg" :viewBox="`0 0 ${CHART.w} ${CHART.h}`" role="img" :aria-label="label">
      <g class="trend-grid">
        <line v-for="g in grid" :key="`gl${g.label}`" :x1="CHART.padL" :x2="CHART.w - CHART.padR" :y1="g.y" :y2="g.y" />
        <text v-for="g in grid" :key="`gt${g.label}`" :x="CHART.padL - 5" :y="g.y + 3" text-anchor="end">{{ g.label }}</text>
      </g>

      <g v-if="bandList.length" class="trend-band">
        <path v-for="(d, i) in bandList" :key="`bd${i}`" :d="d" />
      </g>

      <g v-if="mode === 'bar'" class="trend-bars">
        <rect
          v-for="b in barList"
          :key="b.i"
          :x="b.x"
          :y="b.y"
          :width="b.w"
          :height="b.h"
          rx="2"
          :class="{ picked: b.i === pickedIndex }"
          @click="emit('pick', b.i)"
        />
      </g>
      <g v-else class="trend-line">
        <path v-for="(d, i) in lineList" :key="`ln${i}`" :d="d" />
        <circle v-for="p in dotList" :key="`dt${p.i}`" :cx="p.x" :cy="p.y" r="2.2" />
      </g>

      <g v-if="mode === 'scatter'" class="trend-scatter">
        <circle v-for="p in scatterList" :key="`sc${p.i}`" :cx="p.x" :cy="p.y" r="3" />
      </g>

      <g class="trend-x">
        <text v-for="t in ticks" :key="`xt${t.label}-${t.x}`" :x="t.x" :y="CHART.h - 8" text-anchor="middle">{{ t.label }}</text>
      </g>
    </svg>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { CHART, axisMax, bars, bandPaths, bedtimeTicks, dots, gridLines, linePaths, xTicks, yAt, BED_MAX, bedtimeDots } from './healthChart'

const props = defineProps({
  /** 数值数组(与 labels 等长);null = 无数据 */
  values: { type: Array, default: () => [] },
  labels: { type: Array, default: () => [] },
  /** 逐点 min/max(band 模式用,取 points 原样) */
  points: { type: Array, default: () => [] },
  /** 睡眠散点:传 sleep 会话列表(含 startTime) */
  nights: { type: Array, default: () => [] },
  mode: { type: String, default: 'line' }, // line | bar | scatter
  max: { type: Number, default: 0 },
  pickedIndex: { type: Number, default: -1 },
  label: { type: String, default: '' },
})

const emit = defineEmits(['pick'])

const values = computed(() => (props.mode === 'scatter' ? [] : props.values))

const axisTop = computed(() => {
  if (props.mode === 'scatter') return BED_MAX
  if (props.max > 0) return props.max
  const pool = props.values.map((v) => Number(v))
  if (props.points.length) props.points.forEach((p) => Number.isFinite(Number(p?.max)) && pool.push(Number(p.max)))
  return axisMax(
    pool.filter((v) => Number.isFinite(v)),
    1,
  )
})

// 散点(入睡时刻)的纵轴是钟点,用固定刻度而不是数值刻度
const grid = computed(() =>
  props.mode === 'scatter' ? bedtimeTicks().map((t) => ({ y: yAt(t.v, BED_MAX), label: t.label })) : gridLines(axisTop.value),
)
const ticks = computed(() => xTicks(props.labels, 7))
const barList = computed(() => (props.mode === 'bar' ? bars(values.value, axisTop.value) : []))
const lineList = computed(() => (props.mode === 'bar' || props.mode === 'scatter' ? [] : linePaths(values.value, axisTop.value)))
const dotList = computed(() => (props.mode === 'bar' || props.mode === 'scatter' ? [] : dots(values.value, axisTop.value)))
const bandList = computed(() => (props.mode === 'scatter' || !props.points.length ? [] : bandPaths(props.points, axisTop.value)))
const scatterList = computed(() => (props.mode === 'scatter' ? bedtimeDots(props.nights) : []))
</script>

<style scoped>
.trend {
  width: 100%;
}
.trend-svg {
  display: block;
  width: 100%;
  max-width: 560px;
  height: auto;
}
.trend-grid line {
  stroke: var(--color-border);
  stroke-width: 0.6;
  stroke-dasharray: 3 3;
}
.trend-grid text,
.trend-x text {
  font-size: 8px;
  fill: var(--color-text-secondary);
}
.trend-band path {
  fill: var(--health-band, var(--color-primary));
  opacity: 0.16;
  stroke: none;
}
.trend-bars rect {
  fill: var(--health-bar, var(--color-primary));
  opacity: 0.55;
  cursor: pointer;
  transition: opacity 0.15s ease;
}
.trend-bars rect:hover,
.trend-bars rect.picked {
  opacity: 0.95;
}
.trend-line path {
  fill: none;
  stroke: var(--health-bar, var(--color-primary));
  stroke-width: 1.6;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.trend-line circle,
.trend-scatter circle {
  fill: var(--health-bar, var(--color-primary));
}
.trend-scatter circle {
  opacity: 0.8;
}
</style>
