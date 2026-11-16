<template>
  <div class="ms" :class="['ms-cond-' + scene, { 'ms-night': night }]" aria-hidden="true">
    <img
      :key="photo"
      class="ms-photo"
      :class="{ 'ms-loaded': loadedPhoto === photo }"
      :src="photo"
      alt=""
      decoding="async"
      fetchpriority="high"
      @load="loadedPhoto = $event.currentTarget.getAttribute('src')"
    />
    <img
      v-if="animated && scene === 'cloud' && loadedPhoto === photo"
      class="ms-photo ms-cloud-drift"
      :src="photo"
      :style="cloudFlow"
      alt=""
      decoding="async"
    />

    <div v-if="solar" class="ms-solar" :style="solarStyle">
      <div class="ms-bloom"></div>
      <div class="ms-source">
        <div v-for="(style, i) in rayStyles" :key="i" class="ms-ray" :style="style"></div>
      </div>
    </div>

    <div v-if="particles.length" class="ms-motion" :style="particleFlow">
      <span v-for="(style, i) in particles" :key="i" class="ms-particle" :class="'ms-' + motion" :style="style"></span>
    </div>
    <div class="ms-scrim"></div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { MORNING_SCENES } from '@/utils/morning'
import { getSunScene } from '@/utils/windowLight'
import clearDay from '@/assets/morning/clear-day.webp'
import clearNight from '@/assets/morning/clear-night.webp'
import cloudDay from '@/assets/morning/cloud-day.webp'
import cloudNight from '@/assets/morning/cloud-night.webp'
import rainDay from '@/assets/morning/rain-day.webp'
import rainNight from '@/assets/morning/rain-night.webp'
import snowDay from '@/assets/morning/snow-day.webp'
import snowNight from '@/assets/morning/snow-night.webp'
import fogDay from '@/assets/morning/fog-day.webp'
import fogNight from '@/assets/morning/fog-night.webp'
import thunderDay from '@/assets/morning/thunder-day.webp'
import thunderNight from '@/assets/morning/thunder-night.webp'

const props = defineProps({
  condition: { type: String, default: 'clear' },
  night: { type: Boolean, default: false },
  sunScene: { type: Object, default: null },
  windScale: { type: [Number, String], default: 0 },
  windDeg: { type: [Number, String], default: null },
  windDir: { type: String, default: '' },
  precipLevel: { type: [Number, String], default: 2 },
  animated: { type: Boolean, default: true },
})

const PHOTOS = {
  clear: [clearDay, clearNight],
  cloud: [cloudDay, cloudNight],
  rain: [rainDay, rainNight],
  snow: [snowDay, snowNight],
  fog: [fogDay, fogNight],
  thunder: [thunderDay, thunderNight],
}

const loadedPhoto = ref('')
const scene = computed(() => {
  const condition = String(props.condition).toLowerCase()
  return MORNING_SCENES.includes(condition) ? condition : 'clear'
})
const photo = computed(() => PHOTOS[scene.value][props.night ? 1 : 0])
watch(
  photo,
  () => {
    loadedPhoto.value = ''
  },
  { flush: 'sync' },
)

// 无天文数据或夜里预览白天时,用光尘色板的代表角度;有实况就跟随同一份光源/光束数据。
const sampleDaylight = getSunScene({ sunrise: '06:00', sunset: '18:00', slots: [{ time: '14:30', azimuth: 219.6, altitude: 36 }] }, 0)
const solar = computed(() => {
  if (scene.value !== 'clear' || props.night) return null
  const light = props.sunScene
  if (!light || light.isNight) return sampleDaylight
  return light.hasDirectLight ? light : null
})
const solarStyle = computed(() => {
  const { source, palette, lightOpacity } = solar.value
  return {
    '--sun-x': source.x,
    '--sun-y': source.y,
    '--sun-bloom': palette.bloom,
    '--sun-mid': palette.mid,
    opacity: lightOpacity,
  }
})
// 小屏按比例收拢光束之间的距离,太阳方位和角度保持一致。
const beamSpan = (size) => {
  const compact = size * 0.46
  return `clamp(${Math.min(size, compact)}px, ${size / 10}vw, ${Math.max(size, compact)}px)`
}
const rayStyles = computed(() => {
  const { rays, rotation, palette } = solar.value
  return rays.map(({ width, offset, opacity, blur }) => ({
    width: beamSpan(width),
    transform: `translateX(${beamSpan(offset)}) rotate(${rotation}deg)`,
    opacity: opacity * 0.64,
    filter: `blur(${blur}px)`,
    background: `linear-gradient(to bottom, ${palette.core}, ${palette.mid} 28%, transparent 72%)`,
  }))
})

const bound = (value, max, fallback) => {
  const n = Number.parseFloat(value)
  return Number.isFinite(n) ? Math.min(max, Math.max(0, n)) : fallback
}
const wind = computed(() => bound(props.windScale, 12, 0))
const level = computed(() => bound(props.precipLevel, 6, 2))

const COMPASS = [
  '北',
  '北东北',
  '东北',
  '东东北',
  '东',
  '东东南',
  '东南',
  '南东南',
  '南',
  '南西南',
  '西南',
  '西西南',
  '西',
  '西西北',
  '西北',
  '北西北',
]
const drift = computed(() => {
  const numeric = Number.parseFloat(props.windDeg)
  const from = COMPASS.indexOf(props.windDir.replace(/\s+|风$/g, ''))
  const angle = Number.isFinite(numeric) ? numeric : from >= 0 ? from * 22.5 : 270
  // 气象风向是来向;屏幕上向东为右、向南为下。
  const toward = ((angle + 180) * Math.PI) / 180
  return { x: Math.sin(toward), y: -Math.cos(toward) }
})
const cloudFlow = computed(() => ({
  '--cloud-x': drift.value.x * 2.4 + '%',
  '--cloud-y': drift.value.y * 0.9 + '%',
  animationDuration: Math.max(24, 180 * 0.8 ** wind.value) + 's',
}))
const motion = computed(() => {
  if (!props.animated) return ''
  if (scene.value === 'rain' || scene.value === 'thunder') return 'rain'
  if (scene.value === 'snow') return 'snow'
  return wind.value >= 4 && !props.night ? 'leaf' : ''
})
const particleFlow = computed(() => {
  const push = drift.value.x * Math.min(16, wind.value * 1.6)
  return {
    '--fall-x': push + 'vw',
    '--tilt': -Math.atan2(push, 110) * (180 / Math.PI) + 'deg',
    '--leaf-from': drift.value.x < 0 ? '105%' : '-5%',
    '--leaf-to': drift.value.x < 0 ? '-112vw' : '112vw',
  }
})

const rand = (min, max) => min + Math.random() * (max - min)
const particles = ref([])
const count = computed(() => {
  if (motion.value === 'rain') return Math.round(level.value * 8)
  if (motion.value === 'snow') return Math.round(level.value * 7)
  if (motion.value === 'leaf') return Math.round(wind.value * 1.2)
  return 0
})
watch(
  [motion, count],
  ([type, size]) => {
    particles.value = Array.from({ length: size }, () => {
      const duration = type === 'rain' ? rand(0.65, 1.35) : type === 'snow' ? rand(8, 17) : rand(8, 14) * (4 / wind.value)
      return {
        left: type === 'leaf' ? 'var(--leaf-from)' : rand(-8, 108) + '%',
        top: type === 'leaf' ? rand(55, 97) + '%' : '-5vh',
        width: (type === 'rain' ? rand(0.7, 1.4) : type === 'snow' ? rand(1.4, 4) : rand(4, 8)) + 'px',
        height: type === 'rain' ? rand(12, 34) + 'px' : undefined,
        opacity: type === 'leaf' ? rand(0.4, 0.72) : rand(0.2, 0.62),
        animationDuration: duration + 's',
        animationDelay: -rand(0, duration) + 's',
        '--sway': rand(-24, 24) + 'px',
      }
    })
  },
  { immediate: true },
)
</script>

<style scoped>
.ms {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
  contain: layout style paint;
  background: linear-gradient(#3e7395, #536a69);
}
.ms-cond-cloud {
  background: linear-gradient(#687886, #515a5a);
}
.ms-cond-rain {
  background: linear-gradient(#435464, #354347);
}
.ms-cond-snow {
  background: linear-gradient(#8799a7, #566776);
}
.ms-cond-fog {
  background: linear-gradient(#88989f, #4d6669);
}
.ms-cond-thunder {
  background: linear-gradient(#273644, #293639);
}
.ms-night {
  background: linear-gradient(#101b2d, #172433);
}

.ms-photo {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: 50% 56%;
  opacity: 0;
}
.ms-loaded {
  opacity: 1;
}
.ms-cloud-drift {
  --cloud-x: 0%;
  --cloud-y: 0%;
  --cloud-mask: linear-gradient(#000 20%, transparent 57%);
  opacity: 1;
  -webkit-mask-image: var(--cloud-mask);
  mask-image: var(--cloud-mask);
  animation: ms-cloud-drift linear infinite;
}
@keyframes ms-cloud-drift {
  0%,
  100% {
    opacity: 0;
    transform: scale(1.06);
  }
  22%,
  78% {
    opacity: 1;
  }
  99% {
    opacity: 0;
    transform: translate(var(--cloud-x), var(--cloud-y)) scale(1.06);
  }
}

/* 画外光源、辉光与柔边光束共用光尘主题的坐标,不把太阳画死在照片上。 */
.ms-solar {
  --sun-x: 50%;
  --sun-y: -15%;
  --sun-bloom: transparent;
  --sun-mid: transparent;
  --solar-mask: linear-gradient(#000 5%, rgba(0, 0, 0, 0.7) 30%, transparent 78%);
  position: absolute;
  inset: 0;
  mix-blend-mode: screen;
  -webkit-mask-image: var(--solar-mask);
  mask-image: var(--solar-mask);
}
.ms-bloom,
.ms-source {
  position: absolute;
  left: var(--sun-x);
  top: var(--sun-y);
}
.ms-bloom {
  width: clamp(350px, 68vmin, 700px);
  aspect-ratio: 1;
  transform: translate(-50%, -50%);
  background: radial-gradient(circle, var(--sun-bloom) 0%, var(--sun-mid) 30%, transparent 69%);
  filter: blur(26px);
}
.ms-source {
  width: 0;
  height: 0;
}
.ms-ray {
  position: absolute;
  height: 132vh;
  transform-origin: top center;
}

.ms-motion,
.ms-scrim {
  position: absolute;
  inset: 0;
}
.ms-motion {
  --fall-x: 0vw;
  --tilt: 0deg;
  --leaf-from: -5%;
  --leaf-to: 112vw;
}
.ms-particle {
  --sway: 0px;
  position: absolute;
  animation-timing-function: linear;
  animation-iteration-count: infinite;
}
.ms-rain {
  background: linear-gradient(transparent, #dceaff 85%, transparent);
  animation-name: ms-rain-fall;
}
.ms-snow {
  aspect-ratio: 1;
  border-radius: 50%;
  background: #f4f8fb;
  animation-name: ms-snow-fall;
}
.ms-leaf {
  aspect-ratio: 1.85;
  border-radius: 0 75%;
  background: linear-gradient(140deg, #ba9a68, #6d693d);
  animation-name: ms-leaf-fly;
}
@keyframes ms-rain-fall {
  0% {
    transform: rotate(var(--tilt));
  }
  100% {
    transform: translate(var(--fall-x), 111vh) rotate(var(--tilt));
  }
}
@keyframes ms-snow-fall {
  45% {
    transform: translate(calc(var(--fall-x) * 0.45 + var(--sway)), 49vh);
  }
  100% {
    transform: translate(var(--fall-x), 110vh);
  }
}
@keyframes ms-leaf-fly {
  45% {
    transform: translate(calc(var(--leaf-to) * 0.45), var(--sway)) rotate(190deg);
  }
  100% {
    transform: translate(var(--leaf-to), -12vh) rotate(390deg);
  }
}

.ms-scrim {
  background: linear-gradient(180deg, rgba(11, 23, 36, 0.51), rgba(11, 23, 36, 0.47) 55%, rgba(8, 16, 28, 0.67));
}
.ms-cond-clear:not(.ms-night) .ms-scrim {
  background: linear-gradient(180deg, rgba(11, 23, 36, 0.35), rgba(11, 23, 36, 0.38) 55%, rgba(8, 16, 28, 0.57));
}
.ms-night .ms-scrim {
  background: linear-gradient(180deg, rgba(5, 10, 22, 0.15), rgba(5, 10, 22, 0.13) 55%, rgba(5, 10, 22, 0.45));
}
/* 竖屏裁切时保留雷暴主体,两张照片的闪电位于不同位置。 */
@media (max-aspect-ratio: 4/5) {
  .ms-cond-thunder .ms-photo {
    object-position: 95% 56%;
  }
  .ms-cond-thunder.ms-night .ms-photo {
    object-position: 70% 56%;
  }
}
@media (prefers-reduced-motion: reduce) {
  .ms-motion,
  .ms-cloud-drift {
    display: none;
    animation: none;
  }
}
</style>
