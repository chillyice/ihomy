<template>
  <canvas ref="surface" class="window-projection" aria-hidden="true"></canvas>
</template>

<script setup>
import { onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { drawWindowProjection } from '@/utils/windowProjection'

const props = defineProps({
  scene: { type: Object, required: true },
  weather: { type: String, required: true },
  shadowAlpha: { type: Number, required: true },
  occluders: { type: Array, default: () => [] },
})
const surface = ref(null)
let observer
let frame = 0

// Coalesce a resize, time step and slider change into one paint. Cloud cover
// and the lamp mask are composited by CSS and never invalidate the aperture.
function schedulePaint() {
  if (frame) return
  frame = requestAnimationFrame(() => {
    frame = 0
    const canvas = surface.value
    drawWindowProjection(canvas, { ...props, width: canvas.clientWidth, height: canvas.clientHeight })
  })
}

onMounted(() => {
  observer = new ResizeObserver(schedulePaint)
  observer.observe(surface.value)
})
watch(() => [props.scene, props.weather, props.shadowAlpha, ...props.occluders], schedulePaint, { flush: 'post' })
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
