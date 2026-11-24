<template>
  <canvas ref="surface" class="window-projection" aria-hidden="true"></canvas>
</template>

<script setup>
import { onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { drawWindowProjection } from '@/utils/windowProjection'

const props = defineProps({
  scene: { type: Object, required: true },
  standard: { type: String, default: 'corrected' },
  weather: { type: String, required: true },
  shadowAlpha: { type: Number, required: true },
  occluders: { type: Array, default: () => [] },
})
const surface = ref(null)
let observer
let frame = 0
let displayed
let start
let target
let startedAt

// Coalesce a resize, time step and slider change into one paint. Cloud cover
// and the lamp mask are composited by CSS and never invalidate the aperture.
function schedulePaint() {
  if (frame) return
  frame = requestAnimationFrame((now) => {
    frame = 0
    const canvas = surface.value
    const scene = props.scene
    const realWeight = props.standard === 'real' ? 1 : 0
    if (!target || target.realWeight !== realWeight || target.altitude !== scene.altitude || target.azimuth !== scene.azimuth) {
      start = displayed ?? { ...scene, realWeight }
      target = { altitude: scene.altitude, azimuth: scene.azimuth, realWeight }
      startedAt = now
    }
    const t =
      start.altitude === target.altitude && start.azimuth === target.azimuth && start.realWeight === target.realWeight
        ? 1
        : Math.min(1, (now - startedAt) / 600)
    const blend = t * t * (3 - 2 * t)
    displayed = {
      realWeight: start.realWeight + (target.realWeight - start.realWeight) * blend,
      altitude: start.altitude + (target.altitude - start.altitude) * blend,
      azimuth: start.azimuth + (target.azimuth - start.azimuth) * blend,
    }
    drawWindowProjection(canvas, {
      ...props,
      realWeight: displayed.realWeight,
      scene: { ...scene, ...displayed },
      width: canvas.clientWidth,
      height: canvas.clientHeight,
    })
    if (t < 1) schedulePaint()
  })
}

onMounted(() => {
  observer = new ResizeObserver(schedulePaint)
  observer.observe(surface.value)
})
watch(() => [props.standard, props.scene, props.weather, props.shadowAlpha, ...props.occluders], schedulePaint, { flush: 'post' })
onBeforeUnmount(() => {
  observer?.disconnect()
  cancelAnimationFrame(frame)
})
</script>

<style scoped>
.window-projection {
  position: fixed;
  inset: 0;
  width: 100%;
  height: 100%;
  z-index: calc(var(--z-deco) + 2);
  pointer-events: none;
  -webkit-mask-image: var(--lamp-mask, none);
  mask-image: var(--lamp-mask, none);
}
</style>
