<template>
  <canvas
    ref="surface"
    class="window-projection"
    :class="{ 'lightning-projection': scene.kind === 'lightning' }"
    aria-hidden="true"
  ></canvas>
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
  deskCasters: { type: Array, default: () => [] },
  sashes: { type: Array, required: true },
})
const surface = ref(null)
let observer
let frame = 0
let breezeTimer
let reducedMotion
let strike
let foliageTime = 0
let targetWeight = props.standard === 'real' ? 1 : 0
let realWeight = targetWeight
let startWeight = realWeight
let startedAt = 0

// Coalesce a resize, time step and slider change into one paint. Cloud cover
// and the lamp mask are composited by CSS and never invalidate the aperture.
function schedulePaint() {
  clearTimeout(breezeTimer)
  if (document.hidden || frame) return
  frame = requestAnimationFrame((now) => {
    frame = 0
    const canvas = surface.value
    const scene = props.scene
    const nextWeight = props.standard === 'real' ? 1 : 0
    if (targetWeight !== nextWeight) {
      startWeight = realWeight
      targetWeight = nextWeight
      startedAt = now
    }
    if (realWeight !== targetWeight) {
      const t = reducedMotion.matches ? 1 : Math.min(1, (now - startedAt) / 600)
      const blend = t * t * (3 - 2 * t)
      realWeight = t === 1 ? targetWeight : startWeight + (targetWeight - startWeight) * blend
    }
    if (!reducedMotion.matches) foliageTime = now / 1000
    const painted = drawWindowProjection(canvas, {
      ...props,
      foliageTime,
      realWeight,
      width: canvas.clientWidth,
      height: canvas.clientHeight,
    })
    if (realWeight !== targetWeight) schedulePaint()
    // Slow movement only needs about twelve samples per second on a stationary window.
    else if (painted && scene.kind !== 'lightning' && !reducedMotion.matches) breezeTimer = setTimeout(schedulePaint, 80)
  })
}

function scheduleStrike() {
  strike?.cancel()
  if (!surface.value || props.scene.kind !== 'lightning' || document.hidden || reducedMotion.matches) return
  strike = surface.value.animate(
    [
      { opacity: 0 },
      { opacity: 1, offset: 0.08 },
      { opacity: 0.3, offset: 0.25 },
      { opacity: 0.85, offset: 0.33, easing: 'ease-out' },
      { opacity: 0 },
    ],
    { duration: 600, delay: 3000 + Math.random() * 7000 },
  )
  strike.onfinish = scheduleStrike
}

function refreshProjection() {
  schedulePaint()
  scheduleStrike()
}

onMounted(() => {
  reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)')
  reducedMotion.addEventListener('change', refreshProjection)
  document.addEventListener('visibilitychange', refreshProjection)
  scheduleStrike()
  observer = new ResizeObserver(schedulePaint)
  observer.observe(surface.value)
})
watch(
  () => [props.standard, props.scene, props.weather, props.shadowAlpha, props.sashes, props.deskCasters, ...props.occluders],
  schedulePaint,
  {
    flush: 'post',
  },
)
onBeforeUnmount(() => {
  document.removeEventListener('visibilitychange', refreshProjection)
  reducedMotion?.removeEventListener('change', refreshProjection)
  strike?.cancel()
  clearTimeout(breezeTimer)
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
.lightning-projection {
  opacity: 0;
  z-index: calc(var(--z-atmos) + 6);
  mix-blend-mode: screen;
}
</style>
