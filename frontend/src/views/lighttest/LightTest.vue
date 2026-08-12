<!-- 光照效果测试页:1 分钟循环 96 个时隙(每 15 分钟一个),展示全天光影变化 -->
<template>
  <div ref="root" class="light-test-page">
    <div class="bg-blobs" aria-hidden="true">
      <div class="blob blob-1"></div>
      <div class="blob blob-2"></div>
      <div class="blob blob-3"></div>
    </div>

    <div class="ambient-layer" :style="ambientStyle" aria-hidden="true"></div>

    <div v-if="scene.shadowVisible" class="window-frame-shadow" :style="frameShadowStyle" aria-hidden="true"></div>

    <div v-if="scene.shadowVisible" class="window-shadow" :style="{ opacity: scene.shadowOpacity }" aria-hidden="true">
      <div class="shadow-bar shadow-v" :style="shadowVStyle"></div>
      <div class="shadow-bar shadow-h" :style="shadowHStyle"></div>
    </div>

    <div class="vignette" aria-hidden="true"></div>

    <div class="light-layer" aria-hidden="true">
      <div class="light-bloom" :style="bloomStyle"></div>
      <div class="light-source" :style="sourceStyle">
        <div v-for="(rs, i) in rayStyles" :key="i" class="light-ray" :style="rs"></div>
      </div>
    </div>

    <div class="dust-layer" aria-hidden="true">
      <div v-for="d in dustParticles" :key="d.id" class="dust" :style="{
        left: d.left, top: d.top, width: d.size+'px', height: d.size+'px',
        animationDuration: d.duration+'s', animationDelay: d.delay+'s', '--drift': d.drift+'px',
      }"></div>
    </div>

    <!-- 信息面板 -->
    <div class="info-panel">
      <div class="info-row"><span class="info-label">时间</span><span class="info-value">{{ currentTime }}</span></div>
      <div class="info-row"><span class="info-label">太阳高度</span><span class="info-value">{{ scene.altitude?.toFixed(1) }}°</span></div>
      <div class="info-row"><span class="info-label">太阳方位</span><span class="info-value">{{ scene.azimuth?.toFixed(1) }}°</span></div>
      <div class="info-row"><span class="info-label">日出</span><span class="info-value">{{ sunInfo?.sunrise || '--' }}</span></div>
      <div class="info-row"><span class="info-label">日落</span><span class="info-value">{{ sunInfo?.sunset || '--' }}</span></div>
      <div class="info-row"><span class="info-label">正午</span><span class="info-value">{{ sunInfo?.solarNoon || '--' }}</span></div>
      <div class="info-row"><span class="info-label">月相</span><span class="info-value">{{ moonPhaseText }}</span></div>
      <div class="progress-bar"><div class="progress-fill" :style="{ width: progress + '%' }"></div></div>
      <div class="phase-label">{{ phaseLabel }}</div>
    </div>

    <router-link to="/" class="back-link">← 返回首页</router-link>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { getSunScene } from '@/utils/windowLight'

const root = ref(null)
const sunInfo = ref(null)
const slotIdx = ref(0)
const scene = ref({ source: { x: '50%', y: '-2%' }, rotation: 0, shadowSkew: 0, palette: { bloom: 'transparent', core: 'transparent', mid: 'transparent', ambient: 'transparent', shadow: 'rgba(0,0,0,0.3)' }, rays: [], altitude: 0, azimuth: 0 })
let timer = null

const dustParticles = ref(
  Array.from({ length: 40 }, (_, i) => ({
    id: i,
    left: Math.random() * 100 + '%',
    top: Math.random() * 100 + '%',
    size: 1.5 + Math.random() * 4,
    duration: 10 + Math.random() * 15,
    delay: Math.random() * 12,
    drift: 40 + Math.random() * 80,
  }))
)

const rayStyles = computed(() => {
  const s = scene.value
  return (s.rays || []).map((ray) => ({
    width: ray.width + 'px',
    transform: `translateX(${ray.offset}px) rotate(${s.rotation}deg)`,
    opacity: ray.opacity,
    filter: `blur(${ray.blur}px)`,
    background: `linear-gradient(to bottom, ${s.palette.core} 0%, ${s.palette.mid} 35%, transparent 75%)`,
  }))
})

const sourceStyle = computed(() => ({ left: scene.value.source.x, top: scene.value.source.y }))
const bloomStyle = computed(() => ({
  left: scene.value.source.x, top: scene.value.source.y,
  background: `radial-gradient(circle, ${scene.value.palette.bloom} 0%, ${scene.value.palette.mid} 35%, transparent 70%)`,
}))
const shadowVStyle = computed(() => ({ transform: `rotate(${scene.value.shadowVRotation || 0}deg)` }))
const shadowHStyle = computed(() => ({ top: (scene.value.shadowHTop || 50) + '%', transform: `rotate(${scene.value.shadowVRotation || 0}deg)` }))
const frameShadowStyle = computed(() => ({
  width: (scene.value.frameWidth || 55) + '%',
  height: (scene.value.frameHeight || 100) + 'px',
  marginLeft: '-' + (scene.value.frameWidth || 55) / 2 + '%',
  transform: `skewX(${scene.value.frameSkew || 0}deg)`,
  opacity: scene.value.frameOpacity ?? 0.35,
}))
const ambientStyle = computed(() => ({ background: scene.value.palette.ambient }))

const currentTime = computed(() => {
  const slots = sunInfo.value?.slots || []
  return slots[slotIdx.value]?.time || '--:--'
})

const progress = computed(() => (slotIdx.value / 95) * 100)

const moonPhaseText = computed(() => {
  const p = sunInfo.value?.moonPhase
  if (p == null) return '--'
  if (p < 0.03 || p > 0.97) return '新月'
  if (p < 0.22) return '蛾眉月'
  if (p < 0.28) return '上弦月'
  if (p < 0.47) return '盈凸月'
  if (p < 0.53) return '满月'
  if (p < 0.72) return '亏凸月'
  if (p < 0.78) return '下弦月'
  return '残月'
})

const phaseLabel = computed(() => {
  const alt = scene.value.altitude
  if (alt < -6) return '🌙 夜间'
  if (alt < 0) return '🌅 日出/日落'
  if (alt < 6) return '🌇 黄金时刻'
  if (alt < 15) return '🌤️ 晨昏'
  if (alt < 60) return '☀️ 日间'
  return '🌞 正午'
})

const loadSunInfo = async () => {
  try {
    const res = await fetch('/api/public/sun-info')
    if (res.ok) {
      const json = await res.json()
      if (json.code === 0 && json.data) {
        sunInfo.value = json.data
        scene.value = getSunScene(json.data, 0)
      }
    }
  } catch (e) {}
}

onMounted(() => {
  loadSunInfo()
  // 1 分钟循环 96 时隙:每 625ms 前进 15 分钟
  timer = setInterval(() => {
    slotIdx.value = (slotIdx.value + 1) % 96
    if (sunInfo.value) {
      scene.value = getSunScene(sunInfo.value, slotIdx.value)
    }
  }, 625)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.light-test-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #EDE4D3 0%, #E2D8C4 50%, #D6CBB4 100%);
  position: relative;
  overflow: hidden;
  font-family: Georgia, 'Times New Roman', serif;
}

.bg-blobs { position: absolute; inset: 0; z-index: 0; overflow: hidden; pointer-events: none; }
.blob { position: absolute; border-radius: 50%; filter: blur(60px); opacity: 0.4; }
.blob-1 { width: 480px; height: 480px; top: -120px; left: -100px; background: #9CD0B5; }
.blob-2 { width: 560px; height: 560px; top: 25%; right: -180px; background: #EDDB8C; }
.blob-3 { width: 420px; height: 420px; bottom: -120px; left: 18%; background: #ECC0AC; }

.ambient-layer { position: fixed; inset: 0; z-index: 2; pointer-events: none; mix-blend-mode: multiply; transition: background 0.6s ease; }

.window-frame-shadow { position: fixed; top: 0; left: 50%; z-index: 5; pointer-events: none; mix-blend-mode: multiply; background: rgba(35,18,5,0.4); filter: blur(25px); transform-origin: top center; transition: transform 0.6s ease, opacity 0.6s ease, height 0.6s ease; }
.window-shadow { position: fixed; inset: 0; z-index: 6; pointer-events: none; mix-blend-mode: multiply; transition: opacity 0.6s ease; }
.shadow-bar { position: absolute; background: rgba(35,18,5,0.65); filter: blur(16px); }
.shadow-v { top: -30%; left: 50%; width: 70px; margin-left: -35px; height: 160%; transform-origin: top center; transition: transform 0.6s ease; }
.shadow-h { left: -10%; right: -10%; height: 70px; transform-origin: center top; transition: top 0.6s ease, transform 0.6s ease; }

.vignette { position: fixed; inset: 0; z-index: 44; pointer-events: none; background: radial-gradient(ellipse 90% 75% at 50% 42%, transparent 0%, transparent 55%, rgba(60,38,12,0.08) 80%, rgba(45,25,8,0.18) 100%); }

.light-layer { position: fixed; inset: 0; z-index: 48; pointer-events: none; mix-blend-mode: screen; overflow: hidden; }
.light-bloom { position: absolute; width: 700px; height: 700px; margin-left: -350px; margin-top: -350px; border-radius: 50%; filter: blur(60px); transition: background 0.6s ease, left 0.6s ease, top 0.6s ease; }
.light-source { position: absolute; width: 0; height: 0; transition: left 0.6s ease, top 0.6s ease; }
.light-ray { position: absolute; top: 0; left: 50%; height: 160vh; transform-origin: top center; transition: opacity 0.6s ease, filter 0.6s ease, transform 0.6s ease, background 0.6s ease; }

.dust-layer { position: fixed; inset: 0; z-index: 46; pointer-events: none; overflow: hidden; mix-blend-mode: screen; }
.dust { position: absolute; border-radius: 50%; background: rgba(255,238,185,0.85); box-shadow: 0 0 8px rgba(255,225,150,0.7); animation: dust-float linear infinite; }
@keyframes dust-float { 0% { transform: translate(0,0); opacity: 0; } 15% { opacity: 0.9; } 85% { opacity: 0.9; } 100% { transform: translate(var(--drift,60px), calc(var(--drift,60px) * -1.5)); opacity: 0; } }

.info-panel {
  position: fixed;
  bottom: 24px;
  left: 24px;
  z-index: 100;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(20px);
  color: #fff;
  padding: 20px 28px;
  border-radius: 20px;
  min-width: 260px;
  font-size: 14px;
}
.info-row { display: flex; justify-content: space-between; padding: 4px 0; }
.info-label { opacity: 0.6; }
.info-value { font-weight: 600; font-variant-numeric: tabular-nums; }
.progress-bar { height: 4px; background: rgba(255,255,255,0.15); border-radius: 2px; margin-top: 12px; overflow: hidden; }
.progress-fill { height: 100%; background: linear-gradient(90deg, #FFD078, #FFE8B0); border-radius: 2px; transition: width 0.6s ease; }
.phase-label { text-align: center; margin-top: 8px; font-size: 16px; font-weight: 700; }

.back-link {
  position: fixed;
  top: 24px;
  left: 24px;
  z-index: 100;
  color: #fff;
  background: rgba(0,0,0,0.4);
  backdrop-filter: blur(10px);
  padding: 8px 16px;
  border-radius: 12px;
  text-decoration: none;
  font-size: 14px;
  transition: background 0.2s;
}
.back-link:hover { background: rgba(0,0,0,0.6); }
</style>
