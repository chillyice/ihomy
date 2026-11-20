<!-- 某一晚的睡眠进度轴(内联 SVG,不引图表库):
     横轴是钟点(入睡前 / 起床后各留半小时再扩到整点),纵轴四行自下而上「深睡 → 核心 → 快速眼动 → 清醒」。
     几何在 healthChart.js 的 sleepAxis / sleepLaneBars 里算好,模板只取值。
     交互同 Apple 健康:鼠标移动 / 触摸拖动 → 那一刻的时刻 + 分期(含区间与时长),对应分期条高亮。 -->
<template>
  <div class="lane">
    <div class="lane-readout" aria-live="polite">{{ readout }}</div>
    <svg ref="svgRef" class="lane-svg" :viewBox="`0 0 ${LANE.w} ${LANE.h}`" role="img" :aria-label="label" v-on="handlers">
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
        <rect
          v-for="(b, i) in bars"
          :key="i"
          :x="b.x"
          :y="b.y"
          :width="b.w"
          :height="b.h"
          rx="2"
          :fill="colorOf(b.code)"
          :class="{ probed: probeBar === b }"
        >
          <title>{{ tip(b) }}</title>
        </rect>
      </g>

      <!-- 读数指示:竖线 + 当前时刻小圆点 -->
      <g v-if="probeX != null" class="lane-guide">
        <line :x1="probeX" :x2="probeX" :y1="LANE.padT" :y2="LANE.padT + 4 * LANE.rowH + 3 * LANE.rowGap" />
        <circle :cx="probeX" :cy="probeY" r="2.6" />
      </g>

      <g class="lane-x">
        <text v-for="t in ticks" :key="`t${t.label}`" :x="t.x" :y="LANE.h - 8" text-anchor="middle">{{ t.label }}</text>
      </g>
    </svg>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { LANE, sleepAxis, sleepAxisTicks, sleepLaneBars, sleepLaneRows, stageAt, timeAtX, viewBoxX } from './healthChart'
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

// ===== 指针读数:悬停即读,触摸按下拖动才读 =====
const svgRef = ref(null)
const probeMs = ref(null)
const dragging = ref(false)

const msAt = (clientX) => {
  const rect = svgRef.value?.getBoundingClientRect?.()
  if (!rect?.width) return null
  return timeAtX(viewBoxX(clientX, rect, LANE.w), axis.value)
}

const read = (event) => {
  const ms = msAt(event?.clientX)
  if (ms != null) probeMs.value = ms
}

const handlers = {
  pointerdown(event) {
    if (event.pointerType === 'mouse') return
    dragging.value = true
    event.currentTarget?.setPointerCapture?.(event.pointerId)
    read(event)
  },
  pointermove(event) {
    if (event.pointerType === 'mouse' || dragging.value) read(event)
  },
  pointerup(event) {
    if (!dragging.value) return
    dragging.value = false
    event?.currentTarget?.releasePointerCapture?.(event.pointerId)
  },
  pointercancel(event) {
    if (!dragging.value) return
    dragging.value = false
    event?.currentTarget?.releasePointerCapture?.(event.pointerId)
  },
  pointerleave() {
    if (!dragging.value) probeMs.value = null
  },
}

const probeBar = computed(() => stageAt(bars.value, probeMs.value))
const probeX = computed(() => {
  if (probeMs.value == null || !axis.value?.span) return null
  const ratio = Math.max(0, Math.min(1, (probeMs.value - axis.value.from) / axis.value.span))
  return LANE.padL + (LANE.w - LANE.padL - LANE.padR) * ratio
})
const probeY = computed(() => {
  const bar = probeBar.value
  if (!bar) return LANE.padT + 2 * (LANE.rowH + LANE.rowGap) + LANE.rowH / 2
  return bar.y + bar.h / 2
})

const readout = computed(() => {
  if (probeMs.value == null || !axis.value?.span) return ''
  const time = clockOf(probeMs.value)
  const bar = probeBar.value
  if (!bar) return t('health.sleep.probeGap', { time })
  return t('health.sleep.probe', {
    time,
    stage: stageName(bar.code),
    from: clockOf(bar.from),
    to: clockOf(bar.to),
    duration: sleepText(t, bar.minutes),
  })
})
</script>

<style scoped>
.lane {
  width: 100%;
}
.lane-readout {
  min-height: 17px;
  margin-bottom: 2px;
  font-size: 12px;
  color: var(--color-text-secondary);
  font-variant-numeric: tabular-nums;
}
.lane-svg {
  display: block;
  width: 100%;
  max-width: 640px;
  height: auto;
  touch-action: pan-y;
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
.lane-bars rect.probed {
  opacity: 1;
}
.lane-guide line {
  stroke: var(--color-text-secondary);
  stroke-width: 0.7;
  opacity: 0.65;
}
.lane-guide circle {
  fill: var(--color-card);
  stroke: var(--color-text-secondary);
  stroke-width: 1.2;
}
</style>
