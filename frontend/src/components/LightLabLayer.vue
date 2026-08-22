<template>
  <div class="lightlab-layer" :style="{ '--bg-color': bgColor }">
    <!-- 0. Background surface (wall/desk) -->
    <div class="ll-bg-surface"></div>

    <!-- 1. Ambient blobs -->
    <div class="ll-blobs" v-if="s.blobsEnabled.value">
      <div class="ll-blob ll-blob-1" :style="blob1Style"></div>
      <div class="ll-blob ll-blob-2" :style="blob2Style"></div>
    </div>

    <!-- 2. Shadow base (multiply blend) -->
    <div class="ll-shadow" :style="shadowStyle"></div>

    <!-- 3. Light source: bloom + rays (screen blend) -->
    <div
      v-if="s.lightSourceEnabled.value"
      class="ll-light-source"
      :style="lightSourceStyle"
    >
      <div class="ll-bloom" :style="bloomStyle"></div>
      <div class="ll-rays">
        <div
          v-for="(ray, i) in scene.rays"
          :key="i"
          class="ll-ray"
          :style="rayStyle(ray)"
        ></div>
      </div>
    </div>

    <!-- 4. Parallelogram light spots (screen blend) -->
    <div
      v-if="scene.showSpots && s.effectiveSpotsOpacity.value > 0.01 && s.spotsEnabled.value"
      class="ll-spots"
      :style="spotsContainerStyle"
    >
      <div class="ll-spot ll-spot-tl" :style="spotStyle"></div>
      <div class="ll-spot ll-spot-tr" :style="spotStyle"></div>
      <div class="ll-spot ll-spot-bl" :style="spotStyle"></div>
      <div class="ll-spot ll-spot-br" :style="spotStyle"></div>
    </div>

    <!-- 5. Lamp (screen blend, pendulum swing) -->
    <div v-if="s.lampEnabled.value" class="ll-lamp-wrap" :style="lampWrapStyle">
      <div class="ll-lamp-pendulum">
        <div class="ll-lamp-glow" :style="lampGlowStyle"></div>
      </div>
    </div>

    <!-- 6. Dust particles (screen blend) -->
    <div v-if="s.dustEnabled.value" class="ll-dust">
      <div
        v-for="d in dustParticles"
        :key="d.id"
        class="ll-dust-p"
        :style="dustStyle(d)"
      ></div>
    </div>

    <!-- 7. Precipitation -->
    <div class="ll-precip">
      <div
        v-for="r in rainParticles"
        :key="r.id"
        class="ll-rain-drop"
        :style="rainStyle(r)"
      ></div>
      <div
        v-for="s in snowParticles"
        :key="s.id"
        class="ll-snow-flake"
        :style="snowStyle(s)"
      ></div>
    </div>

    <!-- 8. Lightning flash (screen blend) -->
    <div class="ll-lightning" :style="lightningStyle"></div>

    <!-- 9. Vignette -->
    <div v-if="s.vignetteEnabled.value" class="ll-vignette"></div>

    <!-- 10. Window frame shadow (top/bottom bars) -->
    <div v-if="s.frameEnabled.value" class="ll-window-frame">
      <div class="ll-frame-top"></div>
      <div class="ll-frame-bottom"></div>
      <div class="ll-frame-left"></div>
      <div class="ll-frame-right"></div>
      <div class="ll-mullion-h"></div>
      <div class="ll-mullion-v"></div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { LIGHT_LAB_KEY } from '@/composables/useLightLab'

const props = defineProps({
  state: { type: Object, required: true },
})

const s = props.state
const scene = s.sunScene

// --- Background color ---
const bgColor = computed(() => {
  const sc = scene.value
  if (sc.isNight) return '#12121f'
  if (sc.dayProgress < 0.08) {
    const t = sc.dayProgress / 0.08
    return `rgb(${18 + t * 60},${18 + t * 40},${30 + t * 20})`
  }
  if (sc.dayProgress < 0.18) {
    const t = (sc.dayProgress - 0.08) / 0.1
    return `rgb(${78 + t * 155},${58 + t * 142},${50 + t * 150})`
  }
  if (sc.dayProgress < 0.82) return '#d5d0c8'
  if (sc.dayProgress < 0.92) {
    const t = (sc.dayProgress - 0.82) / 0.1
    return `rgb(${213 - t * 155},${208 - t * 150},${200 - t * 150})`
  }
  const t = (sc.dayProgress - 0.92) / 0.08
  return `rgb(${Math.round(58 - t * 40)},${Math.round(58 - t * 40)},${Math.round(50 - t * 20)})`
})

// --- Ambient blobs ---
const blob1Style = computed(() => ({
  background: `radial-gradient(circle, ${scene.value.isNight ? 'rgba(30,35,60,0.4)' : 'rgba(255,220,170,0.25)'} 0%, transparent 70%)`,
}))
const blob2Style = computed(() => ({
  background: `radial-gradient(circle, ${scene.value.isNight ? 'rgba(20,25,45,0.3)' : 'rgba(200,210,230,0.2)'} 0%, transparent 70%)`,
}))

// --- Shadow base ---
const shadowStyle = computed(() => {
  if (!s.shadowEnabled.value) return { display: 'none' }
  const sc = scene.value
  return {
    background: sc.shadowColor,
    opacity: sc.shadowOpacity,
  }
})

// --- Light source ---
const lightSourceStyle = computed(() => ({
  left: s.effectiveSourceX.value,
  top: s.effectiveSourceY.value,
  opacity: s.effectiveLightOpacity.value > 0.01 ? 1 : 0,
}))

const bloomStyle = computed(() => {
  const sc = scene.value
  return {
    background: `radial-gradient(circle, ${sc.lightColor.core} 0%, ${sc.lightColor.bloom} 30%, ${sc.lightColor.mid} 60%, transparent 100%)`,
    width: '40vw',
    height: '40vw',
    opacity: s.effectiveLightOpacity.value,
  }
})

function rayStyle(ray) {
  const sc = scene.value
  return {
    width: ray.width + 'px',
    marginLeft: ray.offset + 'px',
    opacity: ray.opacity * s.weatherLightMul.value,
    filter: `blur(${ray.blur}px)`,
    background: `linear-gradient(to bottom, ${sc.lightColor.core}, transparent)`,
  }
}

// --- Light spots ---
const spotsContainerStyle = computed(() => {
  const sc = scene.value
  return {
    left: sc.spotX + 'vw',
    top: '55vh',
    transform: `translate(-50%, -50%) skewX(${sc.skewX}deg)`,
    '--spot-w': (36 * sc.scale) + 'vw',
    '--spot-h': (46 * sc.scale) + 'vh',
    '--spot-opacity': Math.min(1, s.effectiveSpotsOpacity.value * 1.3),
    '--spot-color': sc.lightColor.core,
    '--spot-bloom': sc.lightColor.bloom,
  }
})

const spotStyle = computed(() => ({
  background: `radial-gradient(ellipse 70% 80% at 50% 45%, ${scene.value.lightColor.core} 0%, ${scene.value.lightColor.bloom} 50%, transparent 100%)`,
  opacity: Math.min(1, s.effectiveSpotsOpacity.value * 1.3),
}))

// --- Lamp ---
const lampWrapStyle = computed(() => ({
  opacity: s.lampOpacity.value,
}))

const lampGlowStyle = computed(() => {
  const r = s.lampRadius.value
  return {
    background: `radial-gradient(circle at 50% 0%, rgba(${s.lampColor.value},0.8) 0%, rgba(${s.lampColor.value},0.4) 20%, rgba(${s.lampColor.value},0.15) 40%, transparent 70%)`,
    width: r * 2 + 'vw',
    height: r * 2 + 'vw',
  }
})

// --- Dust ---
function dustStyle(d) {
  return {
    left: d.left,
    top: d.top,
    width: d.size + 'px',
    height: d.size + 'px',
    animationDuration: d.duration + 's',
    animationDelay: d.delay + 's',
    '--drift': d.drift + 'px',
  }
}

// --- Rain ---
function rainStyle(r) {
  return {
    left: r.left,
    height: r.height + 'px',
    opacity: r.opacity,
    animationDuration: r.duration + 's',
    animationDelay: r.delay + 's',
  }
}

// --- Snow ---
function snowStyle(sn) {
  return {
    left: sn.left,
    width: sn.size + 'px',
    height: sn.size + 'px',
    opacity: sn.opacity,
    animationDuration: sn.duration + 's',
    animationDelay: sn.delay + 's',
    '--drift': sn.drift + 'px',
  }
}

// --- Lightning ---
const lightningStyle = computed(() => ({
  opacity: s.lightningFlash.value * 0.6,
}))
</script>

<style scoped>
.lightlab-layer {
  position: fixed;
  inset: 0;
  overflow: hidden;
  background: var(--bg-color);
  transition: background 2s ease;
  isolation: isolate;
}

/* 0. Background surface */
.ll-bg-surface {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, rgba(0,0,0,0.08) 0%, transparent 30%, transparent 70%, rgba(0,0,0,0.12) 100%);
  z-index: 0;
}

/* 1. Ambient blobs */
.ll-blobs {
  position: absolute;
  inset: 0;
  z-index: 1;
}
.ll-blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
}
.ll-blob-1 {
  width: 60vw;
  height: 60vw;
  top: -10%;
  right: -10%;
}
.ll-blob-2 {
  width: 50vw;
  height: 50vw;
  bottom: -15%;
  left: -10%;
}

/* 2. Shadow base */
.ll-shadow {
  position: absolute;
  inset: 0;
  z-index: 2;
  mix-blend-mode: multiply;
  pointer-events: none;
  transition: opacity 1.5s ease, background 1.5s ease;
}

/* 3. Light source */
.ll-light-source {
  position: absolute;
  z-index: 10;
  transform: translate(-50%, 0);
  mix-blend-mode: screen;
  pointer-events: none;
  transition: left 1s ease, top 1s ease, opacity 0.5s ease;
}
.ll-bloom {
  position: absolute;
  left: 50%;
  top: 0;
  transform: translateX(-50%);
  border-radius: 50%;
  filter: blur(20px);
  transition: opacity 0.5s ease, background 1s ease;
}
.ll-rays {
  position: relative;
  display: flex;
  align-items: flex-start;
  justify-content: center;
}
.ll-ray {
  height: 100vh;
  margin-top: 0;
  filter: blur(var(--ray-blur, 40px));
  background: linear-gradient(to bottom, currentColor, transparent);
  transform-origin: top center;
  flex-shrink: 0;
}

/* 4. Light spots */
.ll-spots {
  position: absolute;
  z-index: 15;
  display: grid;
  grid-template-columns: 1fr 1fr;
  grid-template-rows: 1fr 1fr;
  gap: 6px;
  width: var(--spot-w, 36vw);
  height: var(--spot-h, 46vh);
  mix-blend-mode: screen;
  pointer-events: none;
  transition: left 1s ease, top 1s ease, transform 1s ease, width 1s ease, height 1s ease;
}
.ll-spot {
  border-radius: 0;
  transition: opacity 0.5s ease, background 1s ease;
}

/* 5. Lamp */
.ll-lamp-wrap {
  position: absolute;
  z-index: 20;
  left: 38.2%;
  top: 0;
  mix-blend-mode: screen;
  pointer-events: none;
  transition: opacity 0.5s ease;
}
.ll-lamp-pendulum {
  transform-origin: 38.2% 0;
  animation: llSwing 8s ease-in-out infinite alternate;
}
.ll-lamp-glow {
  position: absolute;
  left: 50%;
  top: 0;
  transform: translateX(-50%);
  border-radius: 50%;
  filter: blur(15px);
  transition: background 0.3s ease, width 0.3s ease, height 0.3s ease;
}
@keyframes llSwing {
  0% { transform: rotate(-2deg); }
  100% { transform: rotate(2deg); }
}

/* 6. Dust */
.ll-dust {
  position: absolute;
  inset: 0;
  z-index: 25;
  mix-blend-mode: screen;
  pointer-events: none;
}
.ll-dust-p {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 240, 200, 0.4);
  animation: llDustFloat linear infinite;
}
@keyframes llDustFloat {
  0% { transform: translate(0, 0); opacity: 0; }
  10% { opacity: 0.6; }
  90% { opacity: 0.4; }
  100% { transform: translate(var(--drift, 60px), -100vh); opacity: 0; }
}

/* 7. Precipitation */
.ll-precip {
  position: absolute;
  inset: 0;
  z-index: 30;
  pointer-events: none;
  overflow: hidden;
}
.ll-rain-drop {
  position: absolute;
  top: -20px;
  width: 1.5px;
  background: linear-gradient(to bottom, transparent, rgba(180,200,230,0.7));
  animation: llRainFall linear infinite;
}
@keyframes llRainFall {
  0% { transform: translateY(0); }
  100% { transform: translateY(105vh); }
}
.ll-snow-flake {
  position: absolute;
  top: -20px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.85);
  filter: blur(0.5px);
  animation: llSnowFall linear infinite;
}
@keyframes llSnowFall {
  0% { transform: translate(0, 0); }
  100% { transform: translate(var(--drift, 50px), 105vh); }
}

/* 8. Lightning */
.ll-lightning {
  position: absolute;
  inset: 0;
  z-index: 35;
  background: rgba(255, 255, 255, 0.9);
  mix-blend-mode: screen;
  pointer-events: none;
}

/* 9. Vignette */
.ll-vignette {
  position: absolute;
  inset: 0;
  z-index: 40;
  background: radial-gradient(ellipse at center, transparent 50%, rgba(0,0,0,0.25) 100%);
  pointer-events: none;
}

/* 10. Window frame */
.ll-window-frame {
  position: absolute;
  inset: 0;
  z-index: 45;
  pointer-events: none;
}
.ll-frame-top {
  position: absolute;
  top: 0; left: 0; right: 0;
  height: 3vh;
  background: linear-gradient(to bottom, rgba(0,0,0,0.4), transparent);
}
.ll-frame-bottom {
  position: absolute;
  bottom: 0; left: 0; right: 0;
  height: 6vh;
  background: linear-gradient(to top, rgba(0,0,0,0.5), transparent);
}
.ll-frame-left {
  position: absolute;
  top: 0; bottom: 0; left: 0;
  width: 2vw;
  background: linear-gradient(to right, rgba(0,0,0,0.3), transparent);
}
.ll-frame-right {
  position: absolute;
  top: 0; bottom: 0; right: 0;
  width: 2vw;
  background: linear-gradient(to left, rgba(0,0,0,0.3), transparent);
}
.ll-mullion-h {
  position: absolute;
  top: 50%;
  left: 2vw; right: 2vw;
  height: 2px;
  background: rgba(0, 0, 0, 0.15);
  transform: translateY(-50%);
}
.ll-mullion-v {
  position: absolute;
  left: 50%;
  top: 3vh; bottom: 6vh;
  width: 2px;
  background: rgba(0, 0, 0, 0.12);
  transform: translateX(-50%);
}
</style>
