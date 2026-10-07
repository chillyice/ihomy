<template>
  <svg :viewBox="`0 0 ${chartW} ${chartH}`" class="line-chart">
    <line v-for="(t, i) in yTicks" :key="'g' + i" :x1="padL" :x2="chartW - padR" :y1="t.y" :y2="t.y" stroke="var(--color-border)" stroke-width="1" stroke-dasharray="3 3" />
    <text v-for="(t, i) in yTicks" :key="'yl' + i" :x="padL - 8" :y="t.y + 4" text-anchor="end" fill="var(--color-text-secondary)" font-size="11">{{ t.label }}</text>
    <text v-for="(lb, i) in xTicks" :key="'xl' + i" :x="lb.x" :y="chartH - padB + 16" text-anchor="middle" fill="var(--color-text-secondary)" font-size="11">{{ lb.label }}</text>
    <polyline :points="totalPoints" fill="none" :stroke="totalColor" stroke-width="2" stroke-linejoin="round" stroke-linecap="round" />
    <polyline :points="failedPoints" fill="none" :stroke="failedColor" stroke-width="2" stroke-linejoin="round" stroke-linecap="round" />
    <rect v-for="(d, i) in data" :key="'hv' + i" :x="xPos(i) - hoverColW / 2" :y="padT"
      :width="hoverColW" :height="chartH - padB - padT" fill="transparent"
      @mouseenter="$emit('update:hoverIdx', i)" @mouseleave="$emit('update:hoverIdx', -1)" />
    <line v-if="hoverIdx >= 0 && data[hoverIdx]" :x1="xPos(hoverIdx)" :x2="xPos(hoverIdx)"
      :y1="padT" :y2="chartH - padB" :stroke="totalColor" stroke-width="1" stroke-dasharray="4 2" />
    <circle v-if="hoverIdx >= 0 && data[hoverIdx]" :cx="xPos(hoverIdx)" :cy="yVal(data[hoverIdx].total)" r="4" :fill="totalColor" stroke="#fff" stroke-width="1.5" />
    <circle v-if="hoverIdx >= 0 && data[hoverIdx]" :cx="xPos(hoverIdx)" :cy="yVal(data[hoverIdx].failed)" r="4" :fill="failedColor" stroke="#fff" stroke-width="1.5" />
  </svg>
</template>

<script setup>
defineProps({
  data: { type: Array, required: true },
  yTicks: { type: Array, required: true },
  xTicks: { type: Array, required: true },
  totalPoints: { type: String, default: '' },
  failedPoints: { type: String, default: '' },
  xPos: { type: Function, required: true },
  yVal: { type: Function, required: true },
  hoverColW: { type: Number, required: true },
  hoverIdx: { type: Number, default: -1 },
  totalColor: { type: String, default: '#b88c6e' },
  failedColor: { type: String, default: '#b04a3a' },
  chartW: { type: Number, default: 800 },
  chartH: { type: Number, default: 280 },
  padL: { type: Number, default: 40 },
  padR: { type: Number, default: 20 },
  padB: { type: Number, default: 30 },
  padT: { type: Number, default: 20 },
})
defineEmits(['update:hoverIdx'])
</script>

<style scoped>
.line-chart { width: 100%; height: auto; display: block; }
</style>
