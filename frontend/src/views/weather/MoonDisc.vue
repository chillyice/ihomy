<template>
  <svg v-if="fraction != null" class="moon-disc" viewBox="0 0 64 64" aria-hidden="true">
    <defs>
      <radialGradient :id="surfaceId" cx="38%" cy="35%" r="70%">
        <stop stop-color="#fff5db" />
        <stop offset=".65" stop-color="#e6d6b3" />
        <stop offset="1" stop-color="#ae9e84" />
      </radialGradient>
    </defs>
    <circle cx="32" cy="32" r="29" :fill="`url(#${surfaceId})`" />
    <!-- 同一幅月海与环形山在各相位间保持方位，阴影遮住背光面。 -->
    <g fill="#857e72" opacity=".34">
      <path d="M12 23q1-8 9-9 5-2 7 3 2 3 0 5 7 3 3 8-2 4-9 1-3 6-7 3-5-3-3-11Z" />
      <path d="M35 14q4-3 7 1l2 5q5-1 7 4 2 5-2 7-5 1-7-3-5 3-7-1-2-4 1-6-4-4-1-7Z" />
      <circle cx="51" cy="19" r="2.4" />
      <circle cx="38" cy="41" r="3.3" />
    </g>
    <g fill="#bcaf96" stroke="#f8ebd1" stroke-width=".85">
      <circle cx="21" cy="43" r="3.4" />
      <circle cx="29" cy="51" r="2" />
      <circle cx="46" cy="37" r="1.5" />
    </g>
    <g fill="#232e41" opacity=".92" :transform="waning ? 'translate(64 0) scale(-1 1)' : undefined">
      <circle v-if="fraction === 0" cx="32" cy="32" r="29" />
      <path v-else-if="fraction < 1" :d="shadowPath" />
    </g>
    <circle cx="32" cy="32" r="29" fill="none" stroke="#aab1b9" stroke-opacity=".48" />
  </svg>
  <span v-else class="moon-disc" aria-hidden="true">—</span>
</template>

<script setup>
import { computed, useId } from 'vue'
import { MOON_PHASE_CODES } from '@/utils/dict'

const props = defineProps({
  code: { type: String, default: '' },
  phase: { type: Number, default: null },
  illumination: { type: Number, default: null },
})
const surfaceId = useId()
// 实况与逐日图优先画接口返回的照明比例，图谱和旧数据使用八档代表值。
const index = computed(() => MOON_PHASE_CODES.indexOf(props.code))
const waning = computed(() => (Number.isFinite(props.phase) ? props.phase > 0.5 : index.value > 4))
const fraction = computed(() => {
  if (Number.isFinite(props.illumination)) return Math.max(0, Math.min(1, props.illumination / 100))
  return [0, 0.15, 0.5, 0.85, 1, 0.85, 0.5, 0.15][index.value] ?? null
})
const shadowPath = computed(() => {
  const radius = 29 * (1 - 2 * fraction.value)
  const terminator = radius === 0 ? 'L32 3' : `A${Math.abs(radius)} 29 0 0 ${radius < 0 ? 1 : 0} 32 3`
  return `M32 3A29 29 0 0 0 32 61${terminator}Z`
})
</script>

<style scoped>
.moon-disc {
  display: block;
  width: 100%;
  height: auto;
  aspect-ratio: 1;
}
</style>
