<!-- 光照效果测试页:1 分钟循环 96 个时隙(每 15 分钟一个),展示全天光影变化 -->
<template>
  <div ref="root" class="light-test-page">
    <div class="bg-blobs" aria-hidden="true">
      <div class="blob blob-1"></div>
      <div class="blob blob-2"></div>
      <div class="blob blob-3"></div>
    </div>

    <div class="ambient-layer" :style="ambientStyle" aria-hidden="true"></div>

    <!-- 亮斑图层:模拟阳光照耀强度,在内容之上、阴影之下 -->
    <div class="bright-spot" :style="brightSpotStyle" aria-hidden="true"></div>

    <!-- 下层阴影:内框竖+内框横+顶框+底框(z=35,在光柱之下) -->
    <div class="window-shadow-lower"
         :style="{ '--rot': (scene.shadowVRotation || 0) + 'deg', '--htop': (scene.shadowHTop || 50) + '%', '--shadow-gray': (scene.shadowGray ?? 0) }"
         aria-hidden="true">
      <div class="shadow-bar frame-h-top"></div>
      <div class="shadow-bar frame-h-bottom"></div>
      <div class="shadow-bar shadow-v"></div>
      <div class="shadow-bar shadow-h"></div>
    </div>

    <!-- 中央相册舞台(演示内容组件) -->
    <main class="album-stage">
      <div class="album-frame">
        <div class="album-base" aria-hidden="true"></div>
        <div class="album-photo">
          <div class="album-placeholder">相册照片</div>
          <div class="album-caption">
            <div class="caption-title">演示照片</div>
            <div class="caption-desc">光照测试 — 观察反光效果</div>
          </div>
        </div>
        <div class="album-spine" aria-hidden="true"></div>
      </div>
    </main>

    <!-- 左侧面板:家人动态 + 任务(毛玻璃) -->
    <aside class="left-panel">
      <div class="glass-panel feed-panel">
        <div class="panel-title">家人动态</div>
        <div class="feed-row">
          <div class="feed-avatar">M</div>
          <div class="feed-content">
            <div class="feed-nick">妈妈</div>
            <div class="feed-bubble">发布了一篇博客《周末郊游》</div>
          </div>
        </div>
        <div class="feed-row">
          <div class="feed-avatar">D</div>
          <div class="feed-content">
            <div class="feed-nick">爸爸</div>
            <div class="feed-bubble">上传了 3 张照片到相册</div>
          </div>
        </div>
      </div>
      <div class="glass-panel task-panel">
        <div class="panel-title">任务悬赏</div>
        <div class="task-row"><span class="task-reward">🎁</span><div class="task-info"><div class="task-title">打扫书房</div><div class="task-meta">待领取</div></div></div>
        <div class="task-row"><span class="task-reward">⭐</span><div class="task-info"><div class="task-title">倒垃圾</div><div class="task-meta">进行中</div></div></div>
      </div>
    </aside>

    <!-- 右侧:时间天气 + 纪念日(毛玻璃) -->
    <div class="glass-panel weather-panel">
      <div class="clock">{{ currentTime }}</div>
      <div class="weather"><span class="weather-icon">☀️</span><span class="weather-temp">26°</span><span class="weather-text">晴</span></div>
    </div>
    <div class="glass-panel anniversary-panel">
      <div class="panel-title">近期纪念日</div>
      <div class="anni-row"><div class="anni-info"><div class="anni-name">结婚纪念日</div><div class="anni-date">10-01</div></div><div class="anni-days"><span class="days-num">50</span><span class="days-unit">天</span></div></div>
      <div class="anni-row"><div class="anni-info"><div class="anni-name">小宝生日</div><div class="anni-date">11-15</div></div><div class="anni-days"><span class="days-num">95</span><span class="days-unit">天</span></div></div>
    </div>

    <!-- 反光层:内容组件被阳光照亮的轻微高光(soft-light,在内容之上、阴影之下) -->
    <div class="reflection-layer" :style="reflectionStyle" aria-hidden="true"></div>

    <div class="vignette" aria-hidden="true"></div>

    <div class="light-layer" aria-hidden="true">
      <div class="light-bloom" :style="bloomStyle"></div>
      <div class="light-source" :style="sourceStyle">
        <div v-for="(rs, i) in rayStyles" :key="i" class="light-ray" :style="rs"></div>
      </div>
    </div>

    <!-- 上层阴影:左框+右框(z=49,在光柱之上,最顶层) -->
    <div class="window-shadow-upper"
         :style="{ '--rot': (scene.shadowVRotation || 0) + 'deg', '--shadow-gray': (scene.shadowGray ?? 0) }"
         aria-hidden="true">
      <div class="shadow-bar frame-v-left"></div>
      <div class="shadow-bar frame-v-right"></div>
    </div>

    <div class="dust-layer" aria-hidden="true">
      <div v-for="d in dustParticles" :key="d.id" class="dust" :style="{
        left: d.left, top: d.top, width: d.size+'px', height: d.size+'px',
        animationDuration: d.duration+'s', animationDelay: d.delay+'s', '--drift': d.drift+'px',
      }"></div>
    </div>

    <!-- 信息面板 -->
    <div class="info-panel">
      <div class="info-row"><span class="info-label">日期</span>
        <input type="date" v-model="testDate" @change="reloadSunInfo" class="date-input" />
      </div>
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
const testDate = ref('2026-06-21')
const scene = ref({ source: { x: '50%', y: '-2%' }, rotation: 0, palette: { bloom: 'transparent', core: 'transparent', mid: 'transparent', ambient: 'transparent' }, rays: [], altitude: 0, azimuth: 0, shadowVRotation: 0, shadowHTop: 50, shadowIntensity: 1, shadowGray: 0, brightSpotColor: 'rgba(0,0,0,1)', brightSpotOpacity: 0.7, reflectionOpacity: 0, isNight: true, dayProgress: 0 })
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
const ambientStyle = computed(() => ({ background: scene.value.palette.ambient }))
const brightSpotStyle = computed(() => ({
  background: scene.value.brightSpotColor || 'transparent',
  opacity: scene.value.brightSpotOpacity ?? 0,
}))
const reflectionStyle = computed(() => ({
  background: `radial-gradient(ellipse 60% 50% at ${scene.value.source.x} ${scene.value.source.y}, rgba(255,245,220,1) 0%, rgba(255,235,200,0.6) 30%, transparent 70%)`,
  opacity: scene.value.reflectionOpacity ?? 0,
}))

const currentTime = computed(() => {
  const slots = sunInfo.value?.slots || []
  return slots[slotIdx.value]?.time || '--:--'
})

const progress = computed(() => (slotIdx.value / 287) * 100)

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
  const s = scene.value
  if (s.isNight) return s.dayProgress === 0 ? '🌙 日出待命' : '🌙 日落待命'
  const dp = s.dayProgress
  if (dp < 0.1) return '🌅 日出'
  if (dp < 0.3) return '🌤️ 清晨'
  if (dp < 0.7) return '☀️ 日间'
  if (dp < 0.9) return '🌇 傍晚'
  return '🌅 日落'
})

const loadSunInfo = async () => {
  try {
    const url = testDate.value ? `/api/public/sun-info?date=${testDate.value}` : '/api/public/sun-info'
    const res = await fetch(url)
    if (res.ok) {
      const json = await res.json()
      if (json.code === 0 && json.data) {
        sunInfo.value = json.data
        scene.value = getSunScene(json.data, 0)
      }
    }
  } catch (e) {}
}

const reloadSunInfo = () => {
  slotIdx.value = 0
  loadSunInfo()
}

onMounted(() => {
  loadSunInfo()
  // 1 分钟循环 288 时隙:每 208ms 前进 5 分钟
  timer = setInterval(() => {
    slotIdx.value = (slotIdx.value + 1) % 288
    if (sunInfo.value) {
      scene.value = getSunScene(sunInfo.value, slotIdx.value)
    }
  }, 208)
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

/* 亮斑图层:在内容之上(z>30),阴影之下(z<35),multiply 让白色=透明、黑色=压暗、彩色=染色 */
.bright-spot { position: fixed; inset: 0; z-index: 32; pointer-events: none; mix-blend-mode: multiply; transition: background 0.6s ease, opacity 0.6s ease; }

/* 下层阴影:内框+顶框+底框(z=35,在光柱之下) */
.window-shadow-lower { position: fixed; inset: 0; z-index: 35; pointer-events: none; }
/* 上层阴影:左框+右框(z=49,在光柱之上,最顶层) */
.window-shadow-upper { position: fixed; inset: 0; z-index: 49; pointer-events: none; }
/* opaque gray + darken:min(backdrop, G) 幂等,跨层重叠不叠加 */
.shadow-bar { position: absolute; filter: blur(16px); mix-blend-mode: darken; background: rgb(var(--shadow-gray, 0), var(--shadow-gray, 0), var(--shadow-gray, 0)); transition: background 0.2s ease; }

/* === 三条竖直 bar:原点全部对齐到 (页面 50% X, 页面 -10vh Y) === */
/* shadow-v: 宽度 112px(减20%),origin X = bar 中心(50%)= 页面 50% */
.shadow-v { top: -50vh; left: 50%; width: 112px; margin-left: -56px; height: 337.5vh; transform-origin: 50% 40vh; transition: transform 0.2s ease, background 0.2s ease; transform: rotate(var(--rot,0deg)); }
/* frame-v-left: origin X = bar 右边缘(100%)= 页面 50% */
.frame-v-left { top: -50vh; left: 50%; width: 1400px; margin-left: -1400px; height: 337.5vh; transform-origin: 100% 40vh; transition: transform 0.2s ease, background 0.2s ease; transform: translateX(-42.5vw) rotate(var(--rot,0deg)); }
/* frame-v-right: origin X = bar 左边缘(0%)= 页面 50% */
.frame-v-right { top: -50vh; left: 50%; width: 1400px; height: 337.5vh; transform-origin: 0% 40vh; transition: transform 0.2s ease, background 0.2s ease; transform: translateX(42.5vw) rotate(var(--rot,0deg)); }

.shadow-h { left: -75%; right: -75%; height: 70px; top: var(--htop, 50%); transition: top 0.2s ease, background 0.2s ease; }
.frame-h-top { left: -75%; right: -75%; height: 140px; top: calc(-10vh - 140px); }
.frame-h-bottom { left: -75%; right: -75%; height: 1400px; top: calc(var(--htop, 50%) * 2 + 70px); transition: top 0.2s ease, background 0.2s ease; }

.vignette { position: fixed; inset: 0; z-index: 44; pointer-events: none; background: radial-gradient(ellipse 90% 75% at 50% 42%, transparent 0%, transparent 55%, rgba(60,38,12,0.08) 80%, rgba(45,25,8,0.18) 100%); }

.light-layer { position: fixed; inset: 0; z-index: 48; pointer-events: none; mix-blend-mode: screen; overflow: hidden; }
.light-bloom { position: absolute; width: 700px; height: 700px; margin-left: -350px; margin-top: -350px; border-radius: 50%; filter: blur(60px); transition: background 0.6s ease, left 0.6s ease, top 0.6s ease; }
.light-source { position: absolute; width: 0; height: 0; transition: left 0.6s ease, top 0.6s ease; }
.light-ray { position: absolute; top: 0; left: 50%; height: 160vh; transform-origin: top center; transition: opacity 0.6s ease, filter 0.6s ease, transform 0.6s ease, background 0.6s ease; }

.dust-layer { position: fixed; inset: 0; z-index: 46; pointer-events: none; overflow: hidden; mix-blend-mode: screen; }
.dust { position: absolute; border-radius: 50%; background: rgba(255,238,185,0.85); box-shadow: 0 0 8px rgba(255,225,150,0.7); animation: dust-float linear infinite; }
@keyframes dust-float { 0% { transform: translate(0,0); opacity: 0; } 15% { opacity: 0.9; } 85% { opacity: 0.9; } 100% { transform: translate(var(--drift,60px), calc(var(--drift,60px) * -1.5)); opacity: 0; } }

/* === 演示内容组件(模拟首页) === */
/* 中央相册舞台 */
.album-stage { position: fixed; top: 50%; left: 50%; transform: translate(-50%, -50%); z-index: 30; width: 520px; max-width: 50vw; }
.album-frame { position: relative; }
.album-base { position: absolute; inset: -30px; background: linear-gradient(135deg, #F5E6D3, #E8D5BC); border-radius: 12px; box-shadow: 0 8px 30px rgba(60,40,20,0.15); }
.album-photo { position: relative; width: 100%; aspect-ratio: 4/3; border-radius: 4px; overflow: hidden; box-shadow: 0 4px 20px rgba(0,0,0,0.2); }
.album-placeholder { position: absolute; inset: 0; background: linear-gradient(135deg, #A8C9DE, #C0D8A8); display: flex; align-items: center; justify-content: center; color: rgba(255,255,255,0.8); font-size: 20px; font-weight: 600; }
.album-caption { position: absolute; bottom: 0; left: 0; right: 0; padding: 16px 20px; background: linear-gradient(transparent, rgba(0,0,0,0.6)); color: #fff; }
.caption-title { font-size: 18px; font-weight: 700; }
.caption-desc { font-size: 13px; opacity: 0.8; margin-top: 4px; }
.album-spine { position: absolute; left: -8px; top: 0; bottom: 0; width: 8px; background: linear-gradient(90deg, rgba(0,0,0,0.2), transparent); }

/* 毛玻璃面板通用 */
.glass-panel { background: rgba(255,255,255,0.25); backdrop-filter: blur(30px); border: 1px solid rgba(255,255,255,0.5); border-radius: 28px; padding: 20px 24px; box-shadow: 0 4px 20px rgba(0,0,0,0.08); }
.panel-title { font-size: 14px; font-weight: 600; color: #5a4a3a; margin-bottom: 12px; opacity: 0.8; }

/* 左侧面板 */
.left-panel { position: fixed; left: 24px; top: 50%; transform: translateY(-50%); z-index: 40; width: 320px; display: flex; flex-direction: column; gap: 16px; }
.feed-panel { }
.feed-row { display: flex; gap: 10px; padding: 8px 0; }
.feed-avatar { width: 36px; height: 36px; border-radius: 50%; background: #A8C9DE; display: flex; align-items: center; justify-content: center; color: #fff; font-weight: 600; font-size: 14px; flex-shrink: 0; }
.feed-content { flex: 1; }
.feed-nick { font-size: 13px; font-weight: 600; color: #4a3a2a; }
.feed-bubble { background: rgba(255,255,255,0.4); border-radius: 12px; padding: 8px 12px; margin-top: 4px; font-size: 13px; color: #3a2a1a; }
.task-row { display: flex; align-items: center; gap: 10px; padding: 8px 0; }
.task-reward { font-size: 18px; }
.task-info { flex: 1; }
.task-title { font-size: 13px; font-weight: 600; color: #4a3a2a; }
.task-meta { font-size: 11px; opacity: 0.6; color: #5a4a3a; }

/* 右侧面板 */
.weather-panel { position: fixed; right: 24px; top: 90px; z-index: 40; width: 280px; text-align: center; }
.clock { font-size: 36px; font-weight: 300; color: #3a2a1a; font-variant-numeric: tabular-nums; }
.weather { display: flex; align-items: center; justify-content: center; gap: 8px; margin-top: 8px; color: #5a4a3a; }
.weather-icon { font-size: 24px; }
.weather-temp { font-size: 20px; font-weight: 600; }
.weather-text { font-size: 14px; }
.anniversary-panel { position: fixed; right: 24px; top: 230px; z-index: 40; width: 280px; }
.anni-row { display: flex; justify-content: space-between; align-items: center; padding: 8px 0; }
.anni-name { font-size: 13px; font-weight: 600; color: #4a3a2a; }
.anni-date { font-size: 11px; opacity: 0.6; color: #5a4a3a; }
.anni-days { display: flex; align-items: baseline; gap: 2px; }
.days-num { font-size: 20px; font-weight: 700; color: #C08850; }
.days-unit { font-size: 12px; color: #8a6a4a; }

/* 反光层:内容被阳光照亮的轻微高光(soft-light,夜间 0) */
.reflection-layer { position: fixed; inset: 0; z-index: 42; pointer-events: none; mix-blend-mode: soft-light; transition: opacity 0.6s ease; }

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
.info-row { display: flex; justify-content: space-between; align-items: center; padding: 4px 0; }
.info-label { opacity: 0.6; }
.info-value { font-weight: 600; font-variant-numeric: tabular-nums; }
.date-input { background: rgba(255,255,255,0.1); border: 1px solid rgba(255,255,255,0.2); border-radius: 4px; color: #fff; padding: 2px 6px; font-size: 13px; font-family: inherit; }
.date-input::-webkit-calendar-picker-indicator { filter: invert(1); }
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
