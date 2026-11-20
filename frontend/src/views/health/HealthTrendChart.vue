<!-- 健康页通用趋势图(内联 SVG,不引图表库):柱状 / 折线 / 折线+最小-最大带。
     数据由调用方按「与 labels 等长」的数组传入,null = 那天没数据(不画,不伪造 0)。
     mode=bar 时点柱子触发 pick(下标),睡眠页用它选某一晚看分期明细。
     交互同 Apple 健康:鼠标移动 / 触摸拖动出竖线 + 读数(读数行在上方,避免盖住图形);
     纵向滚动交给浏览器(touch-action: pan-y),只有按下拖动才接管指针。 -->
<template>
  <div class="trend">
    <div class="trend-readout" aria-live="polite">{{ readout }}</div>
    <svg ref="svgRef" class="trend-svg" :viewBox="`0 0 ${CHART.w} ${CHART.h}`" role="img" :aria-label="label" v-on="probe.handlers">
      <g class="trend-grid">
        <line v-for="g in grid" :key="`gl${g.label}`" :x1="CHART.padL" :x2="CHART.w - CHART.padR" :y1="g.y" :y2="g.y" />
        <text v-for="g in grid" :key="`gt${g.label}`" :x="CHART.padL - 5" :y="g.y + 3" text-anchor="end">{{ g.label }}</text>
      </g>

      <g v-if="bandList.length" class="trend-band">
        <path v-for="(d, i) in bandList" :key="`bd${i}`" :d="d" />
      </g>

      <!-- 日均线:与读数同一轴,虚线 + 右端小标 -->
      <g v-if="avgLine" class="trend-avg">
        <line :x1="CHART.padL" :x2="CHART.w - CHART.padR" :y1="avgLine.y" :y2="avgLine.y" />
        <text :x="CHART.w - CHART.padR" :y="avgLine.y - 3" text-anchor="end">{{ averageLabel || '—' }}</text>
      </g>

      <g v-if="mode === 'bar'" class="trend-bars">
        <rect
          v-for="b in barList"
          :key="b.i"
          :x="b.x"
          :y="b.y"
          :width="b.w"
          :height="b.h"
          :rx="Math.min(6, b.w / 2)"
          :class="{ picked: b.i === pickedIndex, probed: b.i === probeIndex }"
          @click="emit('pick', b.i)"
        />
      </g>
      <g v-else class="trend-line">
        <path v-for="(d, i) in lineList" :key="`ln${i}`" :d="d" />
        <circle v-for="p in dotList" :key="`dt${p.i}`" :cx="p.x" :cy="p.y" r="2.2" />
      </g>

      <g v-if="mode === 'scatter'" class="trend-scatter">
        <circle
          v-for="p in scatterList"
          :key="`sc${p.i}`"
          :cx="p.x"
          :cy="p.y"
          :r="p.i === probeIndex ? 4 : 3"
          :class="{ probed: p.i === probeIndex }"
        />
      </g>

      <!-- 读数指示:竖线 + 高亮点(缺数据的那天只有竖线) -->
      <g v-if="guideX != null" class="trend-guide">
        <line :x1="guideX" :x2="guideX" :y1="CHART.padT" :y2="CHART.h - CHART.padB" />
        <circle v-if="probePoint" :cx="probePoint.x" :cy="probePoint.y" r="3.4" />
      </g>

      <g class="trend-x">
        <text v-for="t in ticks" :key="`xt${t.label}-${t.x}`" :x="t.x" :y="CHART.h - 8" text-anchor="middle">{{ t.label }}</text>
      </g>
    </svg>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import {
  BED_MAX,
  CHART,
  axisMax,
  bandPaths,
  bars,
  bedtimeDots,
  bedtimeTicks,
  dots,
  gridLines,
  indexAtX,
  linePaths,
  slotCenterX,
  viewBoxX,
  xAt,
  xTicks,
  yAt,
} from './healthChart'
import { useChartProbe } from './useChartProbe'

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
  /** 读数格式化(value, index) → 文本(含单位);不传就按数值/区间兜底 */
  formatValue: { type: Function, default: null },
  /** 日均线数值;null = 不画 */
  average: { type: Number, default: null },
  averageLabel: { type: String, default: '' },
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

// ===== 指针读数 =====
const svgRef = ref(null)
const count = computed(() => (props.mode === 'scatter' ? props.nights.length : props.values.length))

const probe = useChartProbe((clientX) => {
  if (!count.value) return -1
  const rect = svgRef.value?.getBoundingClientRect?.()
  return indexAtX(viewBoxX(clientX, rect, CHART.w), count.value, props.mode === 'bar' ? 'bar' : 'line')
})
const probeIndex = probe.index

const valueAt = (i) => (props.mode === 'scatter' ? props.nights[i] : props.values[i])

const readout = computed(() => {
  const i = probeIndex.value
  if (i < 0 || i >= count.value) return ''
  const label = props.labels[i] ?? ''
  const raw = valueAt(i)
  let text = ''
  if (props.formatValue) text = props.formatValue(raw, i) || ''
  if (!text) {
    const band = props.points?.[i]
    const hasBand = band && Number.isFinite(Number(band.min)) && Number.isFinite(Number(band.max)) && Number(band.min) !== Number(band.max)
    if (hasBand) text = `${band.min} – ${band.max}`
    else if (raw != null && Number.isFinite(Number(raw))) text = String(raw)
    else text = '—'
  }
  return label ? `${label} · ${text}` : text
})

const guideX = computed(() => {
  const i = probeIndex.value
  if (i < 0 || i >= count.value) return null
  return props.mode === 'bar' ? slotCenterX(i, count.value) : xAt(i, count.value)
})

const probePoint = computed(() => {
  if (props.mode !== 'line') return null
  const i = probeIndex.value
  if (i < 0 || i >= count.value) return null
  const v = props.values[i]
  if (v == null || !Number.isFinite(Number(v))) return null
  return { x: xAt(i, count.value), y: yAt(Number(v), axisTop.value) }
})

const avgLine = computed(() => {
  const avg = Number(props.average)
  if (props.mode === 'scatter' || !Number.isFinite(avg)) return null
  return { y: yAt(avg, axisTop.value) }
})
</script>

<style scoped>
.trend {
  width: 100%;
}
.trend-readout {
  min-height: 17px;
  margin-bottom: 2px;
  font-size: 12px;
  color: var(--color-text-secondary);
  font-variant-numeric: tabular-nums;
}
.trend-svg {
  display: block;
  width: 100%;
  max-width: 560px;
  height: auto;
  touch-action: pan-y;
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
.trend-avg line {
  stroke: var(--color-text-secondary);
  stroke-width: 0.7;
  stroke-dasharray: 4 3;
  opacity: 0.7;
}
.trend-avg text {
  font-size: 8px;
  fill: var(--color-text-secondary);
}
.trend-bars rect {
  fill: var(--health-bar, var(--color-primary));
  opacity: 0.55;
  cursor: pointer;
  transition: opacity 0.15s ease;
}
.trend-bars rect:hover,
.trend-bars rect.picked,
.trend-bars rect.probed {
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
.trend-scatter circle.probed {
  opacity: 1;
}
.trend-guide line {
  stroke: var(--color-text-secondary);
  stroke-width: 0.7;
  opacity: 0.6;
}
.trend-guide circle {
  fill: var(--color-card);
  stroke: var(--health-bar, var(--color-primary));
  stroke-width: 1.6;
}
</style>
