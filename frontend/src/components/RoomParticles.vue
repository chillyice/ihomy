<!-- 房间粒子层:tsparticles 驱动光尘/雪/雨,按时辰+天气切换 -->
<template>
  <div class="particles-layer" aria-hidden="true">
    <vue-particles id="room-particles" :particles-init="particlesInit" :options="options" />
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { loadSlim } from '@tsparticles/slim'
import { getWindowLight } from '@/utils/windowLight'

const props = defineProps({
  weather: { type: String, default: null }, // 'snow' | 'rain' | 'clear' | null
})

const particlesInit = loadSlim

const scene = ref(getWindowLight().scene)
setInterval(() => { scene.value = getWindowLight().scene }, 60000)

// 粒子配置:夜间+晴 → 光尘;雪 → 雪花;雨 → 雨滴;其他 → 极弱光尘
const options = computed(() => {
  const isNight = scene.value === 'night' || scene.value === 'evening'
  const w = props.weather

  if (w === 'snow') {
    return {
      fpsLimit: 60,
      background: { color: 'transparent' },
      particles: {
        number: { value: 60, density: { enable: true, area: 800 } },
        color: { value: '#F5EFE0' },
        opacity: { value: { min: 0.3, max: 0.8 } },
        size: { value: { min: 1, max: 4 } },
        move: {
          enable: true,
          direction: 'bottom',
          speed: { min: 0.5, max: 1.5 },
          straight: false,
          outModes: { default: 'out' },
        },
        wobble: { enable: true, distance: 10, speed: 5 },
      },
      detectRetina: true,
    }
  }

  if (w === 'rain') {
    return {
      fpsLimit: 60,
      background: { color: 'transparent' },
      particles: {
        number: { value: 80, density: { enable: true, area: 800 } },
        color: { value: 'rgba(180,200,220,0.6)' },
        opacity: { value: 0.5 },
        size: { value: { min: 0.5, max: 1.5 } },
        move: {
          enable: true,
          direction: 'bottom',
          speed: { min: 8, max: 14 },
          straight: true,
          outModes: { default: 'out' },
        },
        shadow: { enable: true, color: 'rgba(180,200,220,0.3)', blur: 2 },
      },
      detectRetina: true,
    }
  }

  // 光尘(白天弱,夜间强)
  const dustValue = isNight ? 'rgba(255,235,180,0.6)' : 'rgba(255,250,230,0.4)'
  return {
    fpsLimit: 60,
    background: { color: 'transparent' },
    particles: {
      number: { value: isNight ? 40 : 25, density: { enable: true, area: 800 } },
      color: { value: dustValue },
      opacity: { value: { min: 0.2, max: isNight ? 0.7 : 0.4 } },
      size: { value: { min: 0.5, max: 2.5 } },
      move: {
        enable: true,
        direction: 'none',
        speed: { min: 0.1, max: 0.4 },
        random: true,
        straight: false,
        outModes: { default: 'out' },
      },
      shadow: isNight ? { enable: true, color: 'rgba(255,235,180,0.5)', blur: 4 } : { enable: false },
      twinkle: { particles: { enable: true, opacity: 0.8, frequency: 0.3 } },
    },
    detectRetina: true,
  }
})
</script>

<style scoped>
.particles-layer {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 6;
}
</style>
