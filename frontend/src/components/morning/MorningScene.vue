<template>
  <div class="ms" :class="['ms-cond-' + scene, { 'ms-night': night, 'ms-still morning-cloud-still': !animated }]" aria-hidden="true">
    <img
      v-if="photo"
      :key="photo"
      class="ms-photo"
      :class="{ 'ms-loaded': loadedPhoto === photo }"
      :src="photo"
      alt=""
      decoding="async"
      fetchpriority="high"
      @load="loadedPhoto = $event.currentTarget.getAttribute('src')"
    />
    <div v-if="solar" class="ms-solar" :style="solarStyle">
      <div class="ms-solar-glow"></div>
      <div class="ms-source">
        <div v-for="(style, i) in rayStyles" :key="i" class="ms-ray" :style="style"></div>
      </div>
    </div>

    <RandomClouds
      v-if="cloudStyle === 'random'"
      :scene="scene"
      :night="night"
      :wind-scale="windScale"
      :wind-deg="windDeg"
      :wind-dir="windDir"
      :animated="animated"
    />
    <div v-else :key="sceneKey" class="ms-cloud-field">
      <img
        v-for="({ image, style }, i) in movingClouds"
        :key="i"
        class="ms-cloud morning-cloud-motion"
        :src="image"
        :style="style"
        alt=""
        decoding="async"
        @animationend="recycleCloud($event, i)"
      />
    </div>

    <div v-if="animated && scene === 'thunder'" :key="'storm-' + sceneKey" class="ms-lightning">
      <div v-for="(style, i) in strikes" :key="i" class="ms-strike" :style="style">
        <div class="ms-flash"></div>
        <img class="ms-bolt" :src="lightning" alt="" decoding="async" />
      </div>
    </div>

    <div v-if="particles.length" :key="'particles-' + sceneKey" class="ms-motion" :style="particleFlow">
      <span v-for="(style, i) in particles" :key="i" class="ms-particle" :class="'ms-' + motion" :style="style"></span>
    </div>
    <div class="ms-scrim"></div>
  </div>
</template>

<script setup>
import { computed, ref, shallowRef, watch } from 'vue'
import { MORNING_SCENES } from '@/utils/morning'
import { getSunScene } from '@/utils/windowLight'
import cloudPuff from '@/assets/morning/cloud-puff.webp'
import cloudVeil from '@/assets/morning/cloud-veil.webp'
import lightning from '@/assets/morning/lightning.webp'
import { pickMorningBackdrop } from './backdrops'
import { morningWindVector, sampleCloudFlight } from './cloudMotion'
import { useCloudFlights } from './useCloudFlights'
import RandomClouds from './RandomClouds.vue'
import './cloudMotion.css'

const props = defineProps({
  condition: { type: String, default: 'clear' },
  night: { type: Boolean, default: false },
  sunScene: { type: Object, default: null },
  windScale: { type: [Number, String], default: 0 },
  windDeg: { type: [Number, String], default: null },
  windDir: { type: String, default: '' },
  precipLevel: { type: [Number, String], default: 2 },
  animated: { type: Boolean, default: true },
  // 云层样式:photo = 照片底图 + 云朵贴图;random = 同一张照片底图上叠代码生成的随机云
  cloudStyle: { type: String, default: 'photo' },
})

const photo = ref('')
const loadedPhoto = ref('')
const scene = computed(() => {
  const condition = String(props.condition).toLowerCase()
  return MORNING_SCENES.includes(condition) ? condition : 'clear'
})
const rand = (min, max) => min + Math.random() * (max - min)
const sceneKey = computed(() => scene.value + (props.night ? '-night' : '-day'))

// 晴、多云与光尘共用太阳方位和色温,夜里预览白天时用代表光向。
const sampleDaylight = getSunScene({ sunrise: '06:00', sunset: '18:00', slots: [{ time: '14:30', azimuth: 219.6, altitude: 36 }] }, 0)
const solar = computed(() => {
  if (props.night || !['clear', 'cloud'].includes(scene.value)) return null
  const light = props.sunScene
  if (!light || light.isNight) return sampleDaylight
  if (light.hasDirectLight) return light
  if (light.altitude <= 0) return null
  // 光尘的窗面收不到直射时,户外仍能看到地平线上方的太阳。
  return { ...sampleDaylight, source: light.source, rotation: light.rotation }
})
const solarStyle = computed(() => {
  const { source, rotation, palette, lightOpacity } = solar.value
  return {
    '--sun-x': source.x,
    '--sun-angle': Math.max(-52, Math.min(52, rotation)) + 'deg',
    '--sun-bloom': palette.bloom,
    '--sun-mid': palette.mid,
    '--sun-strength': Math.max(0.72, lightOpacity),
  }
})
// 小屏按比例收拢光束之间的距离,太阳方位和角度保持一致。
const beamSpan = (size) => {
  const compact = size * 0.46
  return `clamp(${Math.min(size, compact)}px, ${size / 10}vw, ${Math.max(size, compact)}px)`
}
const rayStyles = computed(() => {
  const { rays, palette } = solar.value
  return rays.map(({ width, offset, opacity, blur }, i) => ({
    '--beam-width': beamSpan(width),
    '--beam-angle': -offset * 0.045 + 'deg',
    '--beam-alpha': Math.max(0.32, opacity) * (scene.value === 'cloud' ? 0.95 : 0.83),
    animationDuration: 6 + i * 1.55 + 's',
    animationDelay: -i * 1.25 + 's',
    filter: `blur(${blur * 0.38}px)`,
    background: `linear-gradient(to bottom, ${palette.core}, ${palette.mid} 23%, transparent 66%)`,
  }))
})

const bound = (value, max, fallback) => {
  const n = Number.parseFloat(value)
  return Number.isFinite(n) ? Math.min(max, Math.max(0, n)) : fallback
}
const wind = computed(() => bound(props.windScale, 12, 0))
const level = computed(() => bound(props.precipLevel, 6, 2))

const drift = computed(() => morningWindVector(props.windDeg, props.windDir))

// 每种天气规定云的密度和高度,具体大小、位置与形状每次重新抽取。
const SKY_LAYERS = {
  clear: [
    [cloudVeil, 68, 17, 42, 0.32],
    [cloudVeil, 27, 31, 32, 0.24],
  ],
  cloud: [
    [cloudPuff, 23, 16, 62, 0.84],
    [cloudPuff, 83, 32, 55, 0.74],
    [cloudVeil, 53, 13, 82, 0.52],
  ],
  overcast: [
    [cloudVeil, 20, 5, 106, 0.74],
    [cloudVeil, 80, 22, 115, 0.62],
    [cloudVeil, 45, 39, 93, 0.5],
  ],
  rain: [
    [cloudVeil, 24, 11, 104, 0.64],
    [cloudVeil, 72, 31, 92, 0.6],
  ],
  snow: [
    [cloudVeil, 43, 10, 114, 0.6],
    [cloudVeil, 66, 32, 83, 0.48],
  ],
  fog: [
    [cloudVeil, 29, 64, 120, 0.48],
    [cloudVeil, 72, 76, 132, 0.43],
  ],
  thunder: [
    [cloudVeil, 30, 8, 101, 0.66],
    [cloudVeil, 74, 26, 110, 0.62],
  ],
}
const { clouds, movingClouds, recycleCloud } = useCloudFlights(drift, wind)
const strikes = shallowRef([])

function createClouds() {
  const horizontalShift = rand(-14, 14)
  return SKY_LAYERS[scene.value].map(([image, left, top, width, opacity], index) => {
    const size = width * rand(0.76, 1.24)
    const heightBand = [Math.max(2, top - 16), top + 16]
    const speedBias = 1 + index * 0.18
    return {
      image,
      heightBand,
      speedBias,
      rest: { x: Math.max(2, Math.min(98, left + horizontalShift + rand(-16, 16))), y: Math.max(1, top + rand(-8, 8)) },
      flight: sampleCloudFlight(heightBand, speedBias, index),
      style: {
        width: `clamp(${size * 3.6}px, ${size}vw, ${size * 15}px)`,
        '--track-opacity': opacity * rand(0.8, 1.08),
        '--cloud-flip': Math.random() < 0.5 ? -1 : 1,
        '--cloud-height': rand(0.76, 1.18),
        '--cloud-tilt': rand(-6, 6) + 'deg',
      },
    }
  })
}

function createStrikes() {
  const period = rand(13.5, 17.5)
  return [rand(65, 80), rand(24, 40)].map((x, index) => ({
    '--strike-x': x + '%',
    '--strike-y': rand(16, 27) + '%',
    '--strike-height': rand(41, 55) + 'vh',
    '--strike-mirror': index === 0 ? 1 : -1,
    animationDuration: period + 's',
    animationDelay: index === 0 ? '0s' : period * rand(0.43, 0.55) + 's',
  }))
}

// 同一场景固定素材；实况风刷新时只更新运动参数。
watch(
  sceneKey,
  () => {
    photo.value = pickMorningBackdrop(scene.value, props.night)
    loadedPhoto.value = ''
    clouds.value = createClouds()
    strikes.value = scene.value === 'thunder' ? createStrikes() : []
  },
  { immediate: true },
)

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

const particles = shallowRef([])
const count = computed(() => {
  if (motion.value === 'rain') return Math.round(level.value * 8)
  if (motion.value === 'snow') return Math.round(level.value * 7)
  if (motion.value === 'leaf') return Math.round(wind.value * 1.2)
  return 0
})
watch(
  [sceneKey, motion, count],
  ([key, type, size], [lastKey, lastType] = []) => {
    const retained = key === lastKey && type === lastType ? particles.value.slice(0, size) : []
    const added = Array.from({ length: size - retained.length }, () => {
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
    particles.value = [...retained, ...added]
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
  background: linear-gradient(#456d8b, #536a69);
}
.ms-cond-overcast {
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
.ms-cloud-field {
  position: absolute;
  inset: 0;
}
.ms-cloud {
  --cloud-flip: 1;
  --cloud-height: 1;
  --cloud-tilt: 0deg;
  --track-shape: rotate(var(--cloud-tilt)) scale(var(--cloud-flip), var(--cloud-height));
  height: auto;
  filter: blur(clamp(1px, 0.15vw, 2px));
}
.ms-cond-overcast .ms-cloud,
.ms-cond-rain .ms-cloud,
.ms-cond-thunder .ms-cloud {
  filter: brightness(0.76) saturate(0.66) blur(1px);
}
.ms-night .ms-cloud {
  filter: brightness(0.39) saturate(0.75) blur(1px);
}
/* 上缘外同一点发出光晕和光束；水平位置与入射方向继续沿用光尘。 */
.ms-solar {
  --sun-x: 50%;
  --sun-y: min(-28px, -5vmin);
  --sun-angle: 0deg;
  --sun-bloom: transparent;
  --sun-mid: transparent;
  --sun-strength: 1;
  --solar-mask: linear-gradient(#000 17%, rgba(0, 0, 0, 0.77) 39%, transparent 84%);
  position: absolute;
  inset: 0;
  mix-blend-mode: screen;
  -webkit-mask-image: var(--solar-mask);
  mask-image: var(--solar-mask);
}
.ms-solar-glow,
.ms-source {
  position: absolute;
  left: var(--sun-x);
  top: var(--sun-y);
}
.ms-solar-glow {
  width: clamp(232px, 51vmin, 530px);
  aspect-ratio: 1;
  transform: translate(-50%, -50%) scale(0.94);
  background: radial-gradient(circle, var(--sun-bloom) 0%, var(--sun-mid) 30%, transparent 69%);
  filter: blur(19px);
  opacity: calc(var(--sun-strength) * 0.8);
  animation: ms-glow-drift 11s ease-in-out infinite alternate;
}
.ms-source {
  width: 0;
  height: 0;
  transform: rotate(var(--sun-angle));
  transition: transform 1.8s ease;
}
.ms-ray {
  --beam-width: 0px;
  --beam-angle: 0deg;
  --beam-alpha: 0;
  position: absolute;
  top: 0;
  left: calc(var(--beam-width) * -0.5);
  width: var(--beam-width);
  height: 116vh;
  opacity: var(--beam-alpha);
  transform: rotate(var(--beam-angle)) scaleX(0.83);
  transform-origin: top center;
  animation: ms-beam-drift ease-in-out infinite alternate;
}
.ms-cond-cloud .ms-solar-glow {
  animation-duration: 8s;
}
.ms-cond-cloud .ms-ray {
  animation-name: ms-beam-through-cloud;
}
@keyframes ms-glow-drift {
  from {
    opacity: calc(var(--sun-strength) * 0.64);
    transform: translate(-50%, -50%) scale(0.9);
  }
  to {
    opacity: var(--sun-strength);
    transform: translate(-50%, -50%) scale(1.12);
  }
}
@keyframes ms-beam-drift {
  from {
    opacity: calc(var(--beam-alpha) * 0.57);
    transform: rotate(calc(var(--beam-angle) - 1.5deg)) scaleX(0.7);
  }
  to {
    opacity: var(--beam-alpha);
    transform: rotate(calc(var(--beam-angle) + 1.5deg)) scaleX(1.08);
  }
}
@keyframes ms-beam-through-cloud {
  from {
    opacity: calc(var(--beam-alpha) * 0.28);
    transform: rotate(calc(var(--beam-angle) - 1.7deg)) scaleX(0.68);
  }
  to {
    opacity: var(--beam-alpha);
    transform: rotate(calc(var(--beam-angle) + 1.7deg)) scaleX(1.14);
  }
}

/* 闪电和云内亮斑一同出现,天空其余区域保持原亮度。 */
.ms-lightning,
.ms-strike,
.ms-flash {
  position: absolute;
  inset: 0;
}
.ms-lightning {
  mix-blend-mode: screen;
}
.ms-strike {
  --strike-x: 74%;
  --strike-y: 20%;
  --strike-height: 48vh;
  --strike-mirror: 1;
  opacity: 0;
  animation: ms-lightning-strike 15s linear infinite;
}
.ms-flash {
  background: radial-gradient(ellipse 46% 34% at var(--strike-x) var(--strike-y), rgba(185, 211, 247, 0.44), transparent 78%);
}
.ms-bolt {
  position: absolute;
  left: var(--strike-x);
  top: var(--strike-y);
  height: min(var(--strike-height), 500px);
  width: auto;
  transform: translate(-50%, -13%) scaleX(var(--strike-mirror));
}
@keyframes ms-lightning-strike {
  0%,
  7%,
  10%,
  100% {
    opacity: 0;
  }
  7.4% {
    opacity: 0.86;
  }
  8.1% {
    opacity: 0.05;
  }
  8.6% {
    opacity: 0.57;
  }
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
.ms-cond-clear:not(.ms-night) .ms-scrim,
.ms-cond-cloud:not(.ms-night) .ms-scrim {
  background: linear-gradient(180deg, rgba(11, 23, 36, 0.35), rgba(11, 23, 36, 0.38) 55%, rgba(8, 16, 28, 0.57));
}
.ms-night .ms-scrim {
  background: linear-gradient(180deg, rgba(5, 10, 22, 0.15), rgba(5, 10, 22, 0.13) 55%, rgba(5, 10, 22, 0.45));
}
.ms-still .ms-source,
.ms-still .ms-ray,
.ms-still .ms-solar-glow {
  animation: none !important;
  transition: none;
}
@media (prefers-reduced-motion: reduce) {
  .ms-motion,
  .ms-lightning {
    display: none;
  }
  .ms-source,
  .ms-ray,
  .ms-solar-glow {
    animation: none;
    transition: none;
  }
}
</style>
