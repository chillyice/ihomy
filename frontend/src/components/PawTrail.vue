<template>
  <Teleport to="body">
    <div v-if="steps.length" class="paw-trail" :class="{ 'dog-trail': dog }" data-desk-layer aria-hidden="true">
      <span
        v-for="(step, index) in steps"
        :key="index"
        class="desk-paw"
        :style="{ left: step.x + 'px', top: step.y + 'px', rotate: step.angle + 'deg', animationDelay: step.delay + 's' }"
      >
        <i></i><i></i><i></i><i></i>
      </span>
    </div>
  </Teleport>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, shallowRef } from 'vue'
import { DESK_FLOATS, deskLanes, pawTrailDelay, pawTrailFits, placePawTrail } from '@/utils/deskSpace'

const PLAYBACK = '.photo-viewer,.el-image-viewer__wrapper,.pc-overlay,.ss,.call-overlay,.morning-overlay'
const OBSTACLES = [DESK_FLOATS, '.app-sidebar', '.window-rail', '.desk-object', '[role="dialog"]'].join(',')
const steps = shallowRef([])
const dog = ref(false)
let timer, fadeTimer, observer, reduced, frame

function inPlayback() {
  return (
    document.fullscreenElement ||
    document.webkitFullscreenElement ||
    [...document.querySelectorAll('video')].some((video) => video.webkitDisplayingFullscreen) ||
    [...document.querySelectorAll(PLAYBACK)].some((el) => el.getClientRects().length)
  )
}

function scene() {
  const width = document.documentElement.clientWidth
  const lanes = deskLanes(width, window.innerHeight, 0, null, Math.min(180, Math.max(84, width * 0.1)))
  const obstacles = [...document.querySelectorAll(OBSTACLES)]
    .map((el) => el.getBoundingClientRect())
    .filter((rect) => rect.width && rect.height)
  return { lanes, obstacles }
}

function clearTrail() {
  observer?.disconnect()
  cancelAnimationFrame(frame)
  frame = 0
  clearTimeout(fadeTimer)
  if (steps.value.length) steps.value = []
}

function recheck() {
  if (!steps.value.length || frame) return
  frame = requestAnimationFrame(() => {
    frame = 0
    const { lanes, obstacles } = scene()
    if (inPlayback() || !pawTrailFits(steps.value, lanes, obstacles)) clearTrail()
  })
}

function showTrail() {
  if (document.hidden || reduced.matches || inPlayback()) return
  const { lanes, obstacles } = scene()
  const trail = placePawTrail(lanes, obstacles)
  if (!trail.length) return
  dog.value = Math.random() < 0.5
  steps.value = trail
  // Only watch for opening players and moving panels while the prints are visible.
  observer.observe(document.body, {
    childList: true,
    subtree: true,
    attributes: true,
    attributeFilter: ['class', 'style', 'hidden', 'open'],
  })
  // 末尾一步要跑完 3.8s 的落爪动画再收,步数越多等得越久(最多 12 步)
  const lastDelay = trail[trail.length - 1]?.delay ?? 0
  fadeTimer = setTimeout(clearTrail, lastDelay * 1000 + 4000)
}

function schedule() {
  clearTimeout(timer)
  // 每次出现后重新掷一次间隔(≥5 分钟),不做固定节拍
  timer = setTimeout(() => {
    showTrail()
    schedule()
  }, pawTrailDelay())
}

function restart() {
  clearTimeout(timer)
  clearTrail()
  if (!document.hidden && !reduced.matches) schedule()
}

onMounted(() => {
  reduced = matchMedia('(prefers-reduced-motion: reduce)')
  observer = new MutationObserver(recheck)
  reduced.addEventListener('change', restart)
  document.addEventListener('visibilitychange', restart)
  document.addEventListener('fullscreenchange', clearTrail)
  document.addEventListener('webkitbeginfullscreen', clearTrail, true)
  window.addEventListener('resize', recheck)
  document.addEventListener('scroll', recheck, { capture: true, passive: true })
  restart()
})
onBeforeUnmount(() => {
  clearTimeout(timer)
  clearTrail()
  reduced.removeEventListener('change', restart)
  document.removeEventListener('visibilitychange', restart)
  document.removeEventListener('fullscreenchange', clearTrail)
  document.removeEventListener('webkitbeginfullscreen', clearTrail, true)
  window.removeEventListener('resize', recheck)
  document.removeEventListener('scroll', recheck, true)
})
</script>

<style scoped>
.paw-trail {
  position: fixed;
  inset: 0;
  z-index: var(--z-desk);
  pointer-events: none;
  overflow: clip;
  contain: layout style;
}
.desk-paw {
  position: absolute;
  width: 16px;
  height: 17px;
  translate: -50% -50%;
  color: #a3a3a3;
  opacity: 0;
  animation: paw-step 3.8s ease-in-out both;
}
.desk-paw::after,
.desk-paw i {
  position: absolute;
  background: currentColor;
}
.desk-paw::after {
  content: '';
  left: 21%;
  bottom: 2%;
  width: 61%;
  height: 48%;
  border-radius: 58% 58% 41% 41%;
}
.desk-paw i {
  width: 21%;
  height: 31%;
  border-radius: 50%;
}
.desk-paw i:nth-child(1) {
  left: 0;
  top: 25%;
  rotate: -26deg;
}
.desk-paw i:nth-child(2) {
  left: 24%;
  top: 3%;
  rotate: -10deg;
}
.desk-paw i:nth-child(3) {
  right: 24%;
  top: 3%;
  rotate: 10deg;
}
.desk-paw i:nth-child(4) {
  right: 0;
  top: 25%;
  rotate: 26deg;
}
@keyframes paw-step {
  0%,
  100% {
    opacity: 0;
  }
  16%,
  51% {
    opacity: 0.48;
  }
}
.dog-trail .desk-paw {
  width: 18px;
  height: 20px;
}
.dog-trail .desk-paw::after {
  border-radius: 65% 65% 32% 32%;
}
@media (prefers-reduced-motion: reduce) {
  .paw-trail {
    display: none;
  }
}
</style>
