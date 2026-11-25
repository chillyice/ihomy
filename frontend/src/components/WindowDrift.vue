<template>
  <div class="window-drift" aria-hidden="true">
    <i :key="pass" :class="kind" :style="trajectory" @animationend="nextPass"></i>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { WINDOW_WIDTH_RATIO } from '@/utils/windowSash'

const props = defineProps({
  openSashes: { type: Array, required: true },
  kind: { type: String, required: true },
})
const pass = ref(0)
const between = (min, max) => min + Math.random() * (max - min)

function chooseTrajectory() {
  const { hinge, tipX, latch } = props.openSashes[Math.floor(Math.random() * props.openSashes.length)]
  const throughGap = tipX + (latch - tipX) * between(0.1, 0.9)
  const x = (1 - WINDOW_WIDTH_RATIO) / 2 + throughGap * WINDOW_WIDTH_RATIO
  const rain = props.kind === 'rain'
  const leaf = props.kind === 'leaf'
  const drift = (hinge < 0.5 ? 1 : -1) * (rain ? between(1, 3) : between(4, 10))
  const delay = pass.value === 0 ? between(1, 4) : leaf ? between(16, 36) : between(4, 10)
  return {
    left: x * 100 + '%',
    '--dx': drift + 'vw',
    '--dy': between(12, 26) + 'vh',
    '--turn': (leaf ? between(-100, 100) : rain ? (drift > 0 ? -12 : 12) : 0) + 'deg',
    animationDuration: (rain ? between(0.6, 1) : between(4, 7)) + 's',
    animationDelay: delay + 's',
  }
}
const trajectory = ref(chooseTrajectory())
function nextPass() {
  pass.value++
  trajectory.value = chooseTrajectory()
}
watch(() => props.openSashes, nextPass)
</script>

<style scoped>
.window-drift {
  position: fixed;
  inset: 0;
  pointer-events: none;
  overflow: hidden;
  z-index: calc(var(--z-deco) + 3);
}
.window-drift i {
  --dx: 0vw;
  --dy: 0vh;
  --turn: 0deg;
  position: absolute;
  top: 0;
  opacity: 0;
  animation: drift-in linear both;
}
.leaf {
  width: 18px;
  height: 18px;
  background: url('@/assets/windowdesk/leaf-autumn.webp') center / contain no-repeat;
}
.window-drift .leaf {
  animation-name: leaf-fall;
  animation-timing-function: ease-in-out;
}
.rain {
  width: 1.5px;
  height: 9px;
  border-radius: 50%;
  background: linear-gradient(transparent, #7099ba);
}
.snow {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: radial-gradient(circle, #fff 25%, #abc4d6 65%, transparent 75%);
}
@keyframes drift-in {
  0% {
    opacity: 0;
    transform: translate(0, -100%) rotate(var(--turn));
  }
  15%,
  65% {
    opacity: 0.72;
  }
  55% {
    transform: translate(calc(var(--dx) * 0.7), calc(var(--dy) * 0.5)) rotate(calc(var(--turn) * 0.8));
  }
  100% {
    opacity: 0;
    transform: translate(var(--dx), var(--dy)) rotate(calc(var(--turn) * 1.8));
  }
}
@keyframes leaf-fall {
  0% {
    opacity: 0;
    transform: translateY(-100%) rotate(-20deg);
  }
  24% {
    opacity: 0.75;
    transform: translate(calc(var(--dx) * 0.55), calc(var(--dy) * 0.22)) rotate(32deg) scaleX(0.65);
  }
  60% {
    opacity: 0.68;
    transform: translate(calc(var(--dx) * 0.3), calc(var(--dy) * 0.64)) rotate(-24deg);
  }
  100% {
    opacity: 0;
    transform: translate(var(--dx), var(--dy)) rotate(var(--turn)) scaleX(0.7);
  }
}
@media (prefers-reduced-motion: reduce) {
  .window-drift i {
    display: none;
    animation: none;
  }
}
</style>
