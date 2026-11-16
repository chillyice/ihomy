<!-- 早安页天气场景:按当前天气渲染整屏背景。
     晴 = 蓝天渐变 + 太阳 + 旋转光柱;多云 = 云层飘动;雨/雪/雾/雷各有粒子与压暗;
     风力 ≥3 级时加吹过的落叶;夜间换深色天空与星月。
     粒子数量固定、只跑 transform/opacity 的 CSS 动画;关闭动效或系统「减少动态效果」时静止。 -->
<template>
  <div class="ms" :class="[`ms-${scene}`, { 'ms-night': night, 'ms-static': !animated }]" aria-hidden="true">
    <div class="ms-sky"></div>

    <!-- 晴/夜:太阳或月亮 + 光柱 + 星星 -->
    <template v-if="scene === 'clear'">
      <div class="ms-sun"></div>
      <div class="ms-rays"></div>
      <div v-if="night" class="ms-stars">
        <span v-for="s in stars" :key="s.id" class="ms-star" :style="s.style"></span>
      </div>
    </template>

    <!-- 云/雨/雷:云层 -->
    <div v-if="hasClouds" class="ms-clouds">
      <span v-for="c in clouds" :key="c.id" class="ms-cloud" :style="c.style"></span>
    </div>

    <!-- 雨 -->
    <div v-if="scene === 'rain' || scene === 'thunder'" class="ms-rain">
      <span v-for="r in rain" :key="r.id" class="ms-drop" :style="r.style"></span>
    </div>

    <!-- 雪 -->
    <div v-if="scene === 'snow'" class="ms-snow">
      <span v-for="f in snow" :key="f.id" class="ms-flake" :style="f.style"></span>
    </div>

    <!-- 雾 -->
    <div v-if="scene === 'fog'" class="ms-fog">
      <span v-for="f in fogBands" :key="f.id" class="ms-fog-band" :style="f.style"></span>
    </div>

    <!-- 雷闪 -->
    <div v-if="scene === 'thunder'" class="ms-flash"></div>

    <!-- 风:吹过的落叶 -->
    <div v-if="windy" class="ms-leaves">
      <span v-for="l in leaves" :key="l.id" class="ms-leaf" :style="l.style"></span>
    </div>

    <div class="ms-scrim"></div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  // 后端归一化天气码:clear / cloud / rain / snow / fog / thunder
  condition: { type: String, default: 'clear' },
  night: { type: Boolean, default: false },
  // 风力(级),字符串 "3-4" 也吃,取第一段数字
  windScale: { type: [Number, String], default: 0 },
  // 降水强度档 1–6,决定雨雪粒子密度
  precipLevel: { type: [Number, String], default: 2 },
  // 关闭后保留配色,不播放动画(设置里可关,系统减少动效时也走这里)
  animated: { type: Boolean, default: true },
})

const SCENES = ['clear', 'cloud', 'rain', 'snow', 'fog', 'thunder']

const scene = computed(() => {
  const c = String(props.condition || 'clear').toLowerCase()
  return SCENES.includes(c) ? c : 'clear'
})

const level = computed(() => {
  const n = Number.parseFloat(props.precipLevel)
  return Number.isFinite(n) ? Math.min(6, Math.max(0, n)) : 2
})

const wind = computed(() => {
  const n = Number.parseFloat(props.windScale)
  return Number.isFinite(n) ? Math.min(12, Math.max(0, n)) : 0
})

const hasClouds = computed(() => ['cloud', 'rain', 'snow', 'thunder'].includes(scene.value))
// 雨雪雷本身已有粒子,不再叠落叶;晴/多云/雾的大风天才有落叶
const windy = computed(() => wind.value >= 3 && ['clear', 'cloud', 'fog'].includes(scene.value))

const rand = (min, max) => min + Math.random() * (max - min)
const pct = (n) => n.toFixed(2) + '%'
const px = (n) => n.toFixed(1) + 'px'
const sec = (n) => n.toFixed(2) + 's'

// 粒子在 setup 里一次生成:数量固定,运行期只靠 CSS 动画,不触发响应式更新
const stars = Array.from({ length: 38 }, (_, i) => ({
  id: 's' + i,
  style: {
    left: pct(rand(0, 100)),
    top: pct(rand(0, 62)),
    width: px(rand(1.5, 3.4)),
    height: px(rand(1.5, 3.4)),
    opacity: rand(0.35, 0.95).toFixed(2),
    animationDuration: sec(rand(2.4, 6.5)),
    animationDelay: sec(-rand(0, 6)),
  },
}))

const clouds = Array.from({ length: 5 }, (_, i) => ({
  id: 'c' + i,
  style: {
    left: pct(rand(-18, 82)),
    top: pct(rand(2, 38)),
    width: px(rand(280, 520)),
    height: px(rand(120, 210)),
    opacity: rand(0.35, 0.8).toFixed(2),
    animationDuration: sec(rand(55, 120)),
    animationDelay: sec(-rand(0, 90)),
  },
}))

const rain = Array.from({ length: Math.round(22 + level.value * 8) }, (_, i) => ({
  id: 'r' + i,
  style: {
    left: pct(rand(0, 100)),
    height: px(rand(16, 36)),
    opacity: rand(0.22, 0.6).toFixed(2),
    animationDuration: sec(rand(0.55, 1.15)),
    animationDelay: sec(-rand(0, 2)),
  },
}))

const snow = Array.from({ length: Math.round(18 + level.value * 8) }, (_, i) => ({
  id: 'f' + i,
  style: {
    left: pct(rand(0, 100)),
    width: px(rand(4, 10)),
    height: px(rand(4, 10)),
    opacity: rand(0.45, 0.95).toFixed(2),
    animationDuration: sec(rand(7, 15)),
    animationDelay: sec(-rand(0, 15)),
  },
}))

const fogBands = Array.from({ length: 4 }, (_, i) => ({
  id: 'g' + i,
  style: {
    top: pct(rand(8, 74)),
    height: px(rand(90, 200)),
    opacity: rand(0.25, 0.6).toFixed(2),
    animationDuration: sec(rand(26, 52)),
    animationDelay: sec(-rand(0, 30)),
  },
}))

const leaves = Array.from({ length: 14 }, (_, i) => ({
  id: 'l' + i,
  style: {
    top: pct(rand(6, 82)),
    width: px(rand(9, 16)),
    height: px(rand(5, 9)),
    opacity: rand(0.55, 0.95).toFixed(2),
    animationDuration: sec(rand(6, 13)),
    animationDelay: sec(-rand(0, 13)),
  },
}))
</script>

<style scoped>
.ms {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  overflow: hidden;
  pointer-events: none;
  contain: layout style paint;
}

/* ===== 天空底色 ===== */
.ms-sky {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  transition: background 1.2s ease;
}
.ms-clear .ms-sky {
  background: linear-gradient(180deg, #4a9fe0 0%, #8ec9f2 42%, #d9f0ff 100%);
}
.ms-cloud .ms-sky {
  background: linear-gradient(180deg, #7d91a6 0%, #a9bac9 55%, #d8e2e9 100%);
}
.ms-rain .ms-sky {
  background: linear-gradient(180deg, #3d4d60 0%, #5f7288 52%, #8b9caa 100%);
}
.ms-snow .ms-sky {
  background: linear-gradient(180deg, #7b90a5 0%, #b6c7d7 52%, #edf5fb 100%);
}
.ms-fog .ms-sky {
  background: linear-gradient(180deg, #8a98a4 0%, #bdc8d0 58%, #e4eaee 100%);
}
.ms-thunder .ms-sky {
  background: linear-gradient(180deg, #1d2531 0%, #374657 55%, #586879 100%);
}

/* 夜间:统一压成深色天空,晴天偏蓝紫 */
.ms-night .ms-sky {
  background: linear-gradient(180deg, #0a1226 0%, #162642 55%, #2b3d5c 100%);
}
.ms-night.ms-clear .ms-sky {
  background: linear-gradient(180deg, #081124 0%, #10203c 48%, #24395c 100%);
}
.ms-night.ms-rain .ms-sky,
.ms-night.ms-thunder .ms-sky {
  background: linear-gradient(180deg, #0d1420 0%, #1d2a3a 55%, #33414f 100%);
}
.ms-night.ms-snow .ms-sky,
.ms-night.ms-fog .ms-sky {
  background: linear-gradient(180deg, #18222f 0%, #2c3b4d 55%, #46566a 100%);
}

/* ===== 太阳 / 月亮 / 光柱 ===== */
.ms-sun {
  position: absolute;
  right: 11%;
  top: 7%;
  width: clamp(150px, 21vw, 250px);
  aspect-ratio: 1;
  border-radius: 50%;
  background: radial-gradient(circle, #fffdf2 0%, #ffeda8 34%, rgba(255, 222, 140, 0.55) 56%, transparent 74%);
  box-shadow: 0 0 110px 55px rgba(255, 234, 170, 0.5);
  animation: ms-sun-breathe 9s ease-in-out infinite alternate;
}
.ms-night .ms-sun {
  right: 14%;
  top: 8%;
  width: clamp(100px, 14vw, 170px);
  background: radial-gradient(circle at 36% 34%, #f7faff 0%, #d6e0f2 42%, rgba(190, 205, 232, 0.35) 62%, transparent 76%);
  box-shadow: 0 0 90px 28px rgba(190, 210, 255, 0.32);
  animation-duration: 14s;
}

.ms-rays {
  position: absolute;
  top: -60vmax;
  right: -60vmax;
  width: 160vmax;
  height: 160vmax;
  background: repeating-conic-gradient(from 0deg, rgba(255, 246, 220, 0.16) 0deg 2.5deg, transparent 2.5deg 15deg);
  -webkit-mask-image: radial-gradient(circle, #000 0%, rgba(0, 0, 0, 0.72) 30%, transparent 68%);
  mask-image: radial-gradient(circle, #000 0%, rgba(0, 0, 0, 0.72) 30%, transparent 68%);
  mix-blend-mode: screen;
  animation: ms-ray-spin 150s linear infinite;
}
.ms-cloud .ms-rays {
  opacity: 0.5;
}
.ms-rain .ms-rays,
.ms-snow .ms-rays,
.ms-fog .ms-rays,
.ms-thunder .ms-rays {
  display: none;
}
.ms-night .ms-rays {
  opacity: 0.38;
  animation-duration: 220s;
}

/* ===== 星星 ===== */
.ms-stars {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
}
.ms-star {
  position: absolute;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 0 6px 1px rgba(210, 226, 255, 0.7);
  animation-name: ms-twinkle;
  animation-timing-function: ease-in-out;
  animation-iteration-count: infinite;
  animation-direction: alternate;
}

/* ===== 云 ===== */
.ms-clouds {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
}
.ms-cloud {
  position: absolute;
  border-radius: 50%;
  background: radial-gradient(ellipse at center, rgba(255, 255, 255, 0.92) 0%, rgba(255, 255, 255, 0.45) 46%, transparent 72%);
  filter: blur(10px);
  animation-name: ms-cloud-drift;
  animation-timing-function: linear;
  animation-iteration-count: infinite;
  animation-direction: alternate;
}
.ms-night .ms-cloud {
  background: radial-gradient(ellipse at center, rgba(190, 205, 226, 0.5) 0%, rgba(160, 180, 210, 0.24) 48%, transparent 72%);
}
.ms-rain .ms-cloud,
.ms-thunder .ms-cloud {
  background: radial-gradient(ellipse at center, rgba(206, 218, 230, 0.5) 0%, rgba(150, 168, 186, 0.28) 48%, transparent 72%);
}
.ms-thunder .ms-cloud {
  filter: blur(14px);
  opacity: 0.75;
}

/* ===== 雨 ===== */
.ms-rain {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
}
.ms-drop {
  position: absolute;
  top: -8vh;
  width: 1.5px;
  border-radius: 2px;
  background: linear-gradient(to bottom, rgba(220, 238, 255, 0), rgba(220, 238, 255, 0.9));
  transform: rotate(11deg);
  animation-name: ms-rain-fall;
  animation-timing-function: linear;
  animation-iteration-count: infinite;
}

/* ===== 雪 ===== */
.ms-snow {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
}
.ms-flake {
  position: absolute;
  top: -6vh;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 0 5px 1px rgba(255, 255, 255, 0.55);
  animation-name: ms-snow-fall;
  animation-timing-function: linear;
  animation-iteration-count: infinite;
}

/* ===== 雾 ===== */
.ms-fog {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
}
.ms-fog-band {
  position: absolute;
  left: -30%;
  width: 160%;
  background: linear-gradient(90deg, transparent 0%, rgba(255, 255, 255, 0.75) 32%, rgba(255, 255, 255, 0.5) 58%, transparent 100%);
  filter: blur(22px);
  animation-name: ms-fog-drift;
  animation-timing-function: ease-in-out;
  animation-iteration-count: infinite;
  animation-direction: alternate;
}

/* ===== 雷闪 ===== */
.ms-flash {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  background: #eaf4ff;
  opacity: 0;
  animation: ms-lightning 9s linear infinite;
}

/* ===== 落叶 ===== */
.ms-leaves {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
}
.ms-leaf {
  position: absolute;
  left: -12vw;
  border-radius: 0 70% 0 70%;
  background: linear-gradient(135deg, #e0b055 0%, #b97a2d 100%);
  box-shadow: 0 1px 2px rgba(70, 40, 0, 0.18);
  animation-name: ms-leaf-fly;
  animation-timing-function: linear;
  animation-iteration-count: infinite;
}

/* ===== 可读性压暗 ===== */
.ms-scrim {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  background: linear-gradient(180deg, rgba(8, 14, 28, 0.16) 0%, rgba(8, 14, 28, 0.04) 38%, rgba(8, 14, 28, 0.46) 100%);
}

/* ===== 动画 ===== */
@keyframes ms-sun-breathe {
  0% {
    transform: scale(1);
    opacity: 0.9;
  }
  100% {
    transform: scale(1.06);
    opacity: 1;
  }
}
@keyframes ms-ray-spin {
  0% {
    transform: rotate(0deg);
  }
  100% {
    transform: rotate(360deg);
  }
}
@keyframes ms-twinkle {
  0% {
    opacity: 0.2;
  }
  100% {
    opacity: 1;
  }
}
@keyframes ms-cloud-drift {
  0% {
    transform: translate3d(-6vw, 0, 0);
  }
  100% {
    transform: translate3d(10vw, 1.5vh, 0);
  }
}
@keyframes ms-rain-fall {
  0% {
    transform: translate3d(0, -10vh, 0) rotate(11deg);
  }
  100% {
    transform: translate3d(-8vw, 112vh, 0) rotate(11deg);
  }
}
@keyframes ms-snow-fall {
  0% {
    transform: translate3d(0, -8vh, 0);
  }
  45% {
    transform: translate3d(28px, 46vh, 0);
  }
  100% {
    transform: translate3d(-22px, 110vh, 0);
  }
}
@keyframes ms-fog-drift {
  0% {
    transform: translate3d(-8vw, 0, 0);
  }
  100% {
    transform: translate3d(9vw, 0, 0);
  }
}
@keyframes ms-lightning {
  0%,
  86%,
  100% {
    opacity: 0;
  }
  87% {
    opacity: 0.85;
  }
  88.5% {
    opacity: 0.12;
  }
  90% {
    opacity: 0.65;
  }
  92% {
    opacity: 0;
  }
}
@keyframes ms-leaf-fly {
  0% {
    transform: translate3d(0, 0, 0) rotate(0deg);
    opacity: 0;
  }
  8% {
    opacity: 1;
  }
  50% {
    transform: translate3d(58vw, -9vh, 0) rotate(260deg);
  }
  100% {
    transform: translate3d(124vw, 7vh, 0) rotate(720deg);
    opacity: 0;
  }
}

/* 关闭动效:保留配色与静态粒子,全部停在第一帧 */
.ms-static *,
.ms-static *::before,
.ms-static *::after {
  animation: none !important;
}
.ms-static .ms-drop,
.ms-static .ms-flake,
.ms-static .ms-leaf {
  opacity: 0.35;
}

@media (prefers-reduced-motion: reduce) {
  .ms *,
  .ms *::before,
  .ms *::after {
    animation: none !important;
  }
}
</style>
