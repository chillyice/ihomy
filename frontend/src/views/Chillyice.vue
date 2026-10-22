<!-- chillyice 个人主页 —— 独立页(https://ihomy.top/chillyice),全公开、不套 ihomy 外壳。
     舞台是一只木板搭的立体画框:远景、树林、小屋、草地、前景草丛分层叠放,小动物定时经过。
     太阳与月亮按济南此刻的真实高度角/方位角挂在顶梁下:高度角决定吊线长短,方位角决定左右,
     落到地平线以下就收起来;小屋、动物和草地的投影随之改变方向与长度。
     天文数据来自后端 SolarUtil(单一来源),本页只做几何投影与渲染;位图美术由
     scripts/art/chillyice_paint.py 生成,换成 AI 出图只需覆盖同名 webp。 -->
<template>
  <main class="cy">
    <div class="cy-glow" aria-hidden="true"></div>

    <header class="cy-head">
      <p class="cy-kicker">
        <span class="cy-dot" aria-hidden="true"></span>
        {{ $t('chillyice.kicker') }}
      </p>
      <p class="cy-place">{{ $t('chillyice.place') }}</p>
    </header>

    <section class="cy-stage" :style="lightStyle">
      <div class="cy-scene">
        <div class="cy-air" aria-hidden="true"></div>

        <svg class="cy-orbit" viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true">
          <path
            v-for="(d, i) in sunPaths"
            :key="`sun-${i}`"
            :d="d"
            class="cy-arc cy-arc--sun"
            vector-effect="non-scaling-stroke"
          />
          <path
            v-for="(d, i) in moonPaths"
            :key="`moon-${i}`"
            :d="d"
            class="cy-arc cy-arc--moon"
            vector-effect="non-scaling-stroke"
          />
        </svg>

        <img class="cy-layer cy-far" :src="layerFar" alt="" />
        <img class="cy-layer cy-trees" :src="layerTrees" alt="" />

        <div class="cy-puppets">
          <div class="cy-puppet" :class="{ 'is-off': !sun.visible, 'is-ready': ready }" :style="puppetStyle(sun)">
            <span class="cy-string" aria-hidden="true"></span>
            <span class="cy-anchor">
              <span
                class="cy-disc cy-disc--sun"
                role="img"
                :aria-label="$t('chillyice.sunAria', { alt: Math.round(sunAlt) })"
              >
                <img :src="discSun" alt="" />
              </span>
            </span>
          </div>

          <div class="cy-puppet" :class="{ 'is-off': !moon.visible, 'is-ready': ready }" :style="puppetStyle(moon)">
            <span class="cy-string" aria-hidden="true"></span>
            <span class="cy-anchor">
              <span
                class="cy-disc cy-disc--moon"
                role="img"
                :aria-label="$t('chillyice.moonAria', { alt: Math.round(moonAlt), phase: moonPhaseName })"
              >
                <svg viewBox="-50 -50 100 100" aria-hidden="true">
                  <defs>
                    <clipPath id="cy-moon-lit">
                      <path :d="moonPath" />
                    </clipPath>
                  </defs>
                  <circle class="cy-moon-dark" r="50" />
                  <image :href="discMoon" x="-50" y="-50" width="100" height="100" clip-path="url(#cy-moon-lit)" />
                  <circle class="cy-moon-ring" r="49.5" />
                </svg>
              </span>
            </span>
          </div>
        </div>

        <img class="cy-layer cy-cabin cy-shadowed" :src="layerCabin" alt="" />
        <img class="cy-layer cy-mid" :src="layerMid" alt="" />

        <img class="cy-animal cy-animal--deer cy-shadowed" :src="animalDeer" alt="" />
        <img class="cy-animal cy-animal--rabbit cy-shadowed" :src="animalRabbit" alt="" />
        <img class="cy-animal cy-animal--fox cy-shadowed" :src="animalFox" alt="" />
        <span class="cy-animal cy-animal--bird">
          <img class="cy-bird" :src="animalBird" alt="" />
        </span>

        <img class="cy-layer cy-near" :src="layerNear" alt="" />
        <div class="cy-night" aria-hidden="true"></div>
        <img class="cy-layer cy-cabin-glow" :src="layerCabinGlow" alt="" />
        <div class="cy-light" aria-hidden="true"></div>
        <img class="cy-rail" :src="rail" alt="" />
      </div>

      <div class="cy-tags" aria-hidden="true">
        <span v-if="sun.visible" class="cy-tag" :style="tagStyle(sun)">
          {{ $t('chillyice.sunAltitude', { deg: Math.round(sunAlt) }) }}
        </span>
        <span v-if="moon.visible" class="cy-tag" :style="tagStyle(moon)">{{ moonLabel }}</span>
      </div>
    </section>

    <section class="cy-copy">
      <h1 class="cy-name">chillyice</h1>
      <p class="cy-role">{{ $t('chillyice.role') }}</p>
      <p class="cy-meta">
        <span>{{ $t('chillyice.coords') }}</span>
        <span v-if="sunWindow">{{ sunWindow }}</span>
        <span v-if="moonWindow">{{ moonWindow }}</span>
      </p>
      <div class="cy-scrub">
        <label class="cy-scrub-field">
          <span>{{ $t('chillyice.timeLabel') }}</span>
          <input
            class="cy-scrub-range"
            type="range"
            min="0"
            max="1439"
            step="1"
            :value="scrub ?? nowMin"
            :aria-valuetext="scrubText"
            @input="onScrub"
          />
          <span class="cy-scrub-time">{{ scrubText }}</span>
        </label>
        <button v-if="scrub !== null" type="button" class="cy-scrub-reset" @click="resetTime">
          {{ $t('chillyice.now') }}
        </button>
      </div>
      <nav class="cy-links" :aria-label="$t('chillyice.linksAria')">
        <router-link class="cy-link cy-link--primary" to="/home">{{ $t('chillyice.backHome') }}</router-link>
        <a class="cy-link" :href="githubUrl" target="_blank" rel="noopener noreferrer">{{ $t('chillyice.github') }}</a>
      </nav>
    </section>

    <footer class="cy-foot">© {{ year }} chillyice</footer>
  </main>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watchEffect } from 'vue'
import { useI18n } from 'vue-i18n'
import { moonLitPath, projectBody, projectTrack, skyStyle, stageLight } from '@/utils/celestialStage'
import { moonPhaseText } from '@/utils/dict'
import animalBird from '@/assets/chillyice/animal-bird.webp'
import animalDeer from '@/assets/chillyice/animal-deer.webp'
import animalFox from '@/assets/chillyice/animal-fox.webp'
import animalRabbit from '@/assets/chillyice/animal-rabbit.webp'
import discMoon from '@/assets/chillyice/disc-moon.webp'
import discSun from '@/assets/chillyice/disc-sun.webp'
import layerCabin from '@/assets/chillyice/layer-cabin.webp'
import layerCabinGlow from '@/assets/chillyice/layer-cabin-glow.webp'
import layerFar from '@/assets/chillyice/layer-far.webp'
import layerMid from '@/assets/chillyice/layer-mid.webp'
import layerNear from '@/assets/chillyice/layer-near.webp'
import layerTrees from '@/assets/chillyice/layer-trees.webp'
import rail from '@/assets/chillyice/rail.webp'

// 固定济南:这页讲的是「从这里看天空」,不跟随访客 IP 或家庭设置
const JINAN = { lat: 36.6512, lng: 117.1201 }
const JINAN_OFFSET_MS = 8 * 3_600_000
const POLL_MS = 60_000

const githubUrl = 'https://github.com/chillyice'
const year = new Date().getFullYear()
const { t } = useI18n()

const astro = reactive({ sun: null, moon: null, moonPhase: 0, moonPhaseCode: '' })
const day = reactive({ sun: [], moon: [], times: {} })
const scrub = ref(null)
const nowMin = ref(0)
const ready = ref(false)
let timer = 0
let scrubTimer = 0
let controller = null
let dayController = null
let dayDate = ''

// 济南墙钟(UTC+8 无夏令时),用于时间手柄与回显
const jinanParts = () => {
  const d = new Date(Date.now() + JINAN_OFFSET_MS)
  return { y: d.getUTCFullYear(), m: d.getUTCMonth(), d: d.getUTCDate(), min: d.getUTCHours() * 60 + d.getUTCMinutes() }
}
const instantAt = (min) => {
  const p = jinanParts()
  return new Date(Date.UTC(p.y, p.m, p.d, Math.floor(min / 60), min % 60) - JINAN_OFFSET_MS).toISOString()
}
const clockText = (min) => `${String(Math.floor(min / 60)).padStart(2, '0')}:${String(min % 60).padStart(2, '0')}`
const scrubText = computed(() => clockText(scrub.value ?? nowMin.value))
nowMin.value = jinanParts().min

// 济南当地日历日(YYYY-MM-DD),用来判断跨零点后要不要重取当天轨迹
const jinanDate = () => {
  const p = jinanParts()
  return `${p.y}-${String(p.m + 1).padStart(2, '0')}-${String(p.d).padStart(2, '0')}`
}

const sun = computed(() => projectBody(astro.sun?.altitude, astro.sun?.azimuth))
const moon = computed(() => projectBody(astro.moon?.altitude, astro.moon?.azimuth))
const sunAlt = computed(() => astro.sun?.altitude ?? 0)
const moonAlt = computed(() => astro.moon?.altitude ?? 0)
const moonPath = computed(() => moonLitPath(astro.moonPhase, 50))
const moonPhaseName = computed(() => moonPhaseText(t, astro.moonPhaseCode) || t('chillyice.moon'))
const moonLabel = computed(() =>
  moonPhaseName.value === t('chillyice.moon') ? moonPhaseName.value : `${t('chillyice.moon')} · ${moonPhaseName.value}`,
)
const puppetStyle = (p) => ({ left: p.x + '%', height: p.y + '%', '--orb-scale': p.scale })
// 标签跟着日月走,但贴着画框边时要收进来,不然读数被裁掉一半
const tagStyle = (p) => ({ left: `${Math.min(92, Math.max(8, p.x))}%`, top: `${Math.min(84, p.y)}%` })

// 全天轨迹:后端按 10 分钟采样,本页只把点投到舞台坐标并连成弧线
const sunPaths = computed(() => projectTrack(day.sun))
const moonPaths = computed(() => projectTrack(day.moon))
const windowText = (key, rise, set) => (rise && set ? t(key, { rise, set }) : '')
const sunWindow = computed(() => windowText('chillyice.sunWindow', day.times.sunrise, day.times.sunset))
// 月亮一天里可能先落再升:按时间先后显示,避免读成同一轮
const moonWindow = computed(() => {
  const rise = day.times.moonrise
  const set = day.times.moonset
  if (!rise || !set) return ''
  return rise <= set ? t('chillyice.moonWindow', { rise, set }) : t('chillyice.moonWindowReverse', { rise, set })
})

// 天空随太阳高度角变色:变量挂在 html 上,body 与 .cy 一起继承
const sky = computed(() => skyStyle(astro.sun?.altitude))
const SKY_KEYS = Object.keys(skyStyle(24))
watchEffect(() => {
  for (const [k, v] of Object.entries(sky.value)) document.documentElement.style.setProperty(k, v)
})

// 舞台光影:太阳在就按太阳,太阳落下换月光(更软、更冷),夜色始终按太阳高度角算
const lightStyle = computed(() => {
  const base = stageLight(astro.sun?.altitude, astro.sun?.azimuth)
  if (sun.value.visible || !moon.value.visible) return base
  return { ...stageLight(astro.moon?.altitude, astro.moon?.azimuth, { moon: true }), '--cy-night': base['--cy-night'] }
})

const load = async () => {
  controller?.abort()
  controller = new AbortController()
  try {
    const q = new URLSearchParams({ lat: JINAN.lat, lng: JINAN.lng })
    if (scrub.value !== null) q.set('at', instantAt(scrub.value))
    const res = await fetch(`/api/public/astro?${q}`, {
      signal: controller.signal,
      headers: { Accept: 'application/json' },
    })
    const body = await res.json()
    if (body?.code === 0 && body.data) {
      Object.assign(astro, body.data)
      if (scrub.value === null) nowMin.value = jinanParts().min
      if (dayDate !== jinanDate()) loadDay()
      if (!ready.value) {
        // 等首帧按真实位置落位后再开过渡,避免从舞台中央滑进来
        requestAnimationFrame(() => requestAnimationFrame(() => (ready.value = true)))
      }
    }
  } catch (err) {
    if (err.name !== 'AbortError') console.warn('[chillyice] astro unavailable', err)
  }
}

// 当天轨迹一天取一次;跨零点由 load() 的日期比较触发重取
const loadDay = async () => {
  dayController?.abort()
  dayController = new AbortController()
  try {
    const q = new URLSearchParams({ lat: JINAN.lat, lng: JINAN.lng })
    const res = await fetch(`/api/public/astro/day?${q}`, {
      signal: dayController.signal,
      headers: { Accept: 'application/json' },
    })
    const body = await res.json()
    if (body?.code === 0 && body.data) {
      day.sun = Array.isArray(body.data.sun) ? body.data.sun : []
      day.moon = Array.isArray(body.data.moon) ? body.data.moon : []
      day.times = body.data.times || {}
      dayDate = body.data.date || jinanDate()
    }
  } catch (err) {
    if (err.name !== 'AbortError') console.warn('[chillyice] astro track unavailable', err)
  }
}

const onScrub = (e) => {
  scrub.value = Number(e.target.value)
  window.clearTimeout(scrubTimer)
  scrubTimer = window.setTimeout(load, 250)
}

const resetTime = () => {
  window.clearTimeout(scrubTimer)
  scrub.value = null
  nowMin.value = jinanParts().min
  load()
}

onMounted(() => {
  document.title = t('chillyice.docTitle')
  // 独立页整屏渲染,根元素一起接管配色(滚动条/橡皮筋区);卸载时移除,规则不外漏到 ihomy 其他页面
  document.documentElement.classList.add('chillyice-page')
  load()
  loadDay()
  timer = window.setInterval(() => {
    if (scrub.value === null) load()
  }, POLL_MS)
})

onBeforeUnmount(() => {
  window.clearInterval(timer)
  window.clearTimeout(scrubTimer)
  controller?.abort()
  dayController?.abort()
  document.title = 'ihomy'
  document.documentElement.classList.remove('chillyice-page')
  for (const k of SKY_KEYS) document.documentElement.style.removeProperty(k)
})
</script>

<style scoped>
/* 底色/文字/光晕由太阳高度角实时驱动(见 skyStyle),以下只是首帧兜底;日/月盘配色跟随浏览器深浅偏好 */
:global(html.chillyice-page) {
  --cy-bg: #f6f2ea;
  --cy-ink: #201d19;
  --cy-muted: rgba(32, 29, 25, 0.62);
  --cy-line: rgba(32, 29, 25, 0.14);
  --cy-string: rgba(32, 29, 25, 0.26);
  --cy-accent: #b96a3d;
  --cy-spot-a: rgba(233, 190, 132, 0.55);
  --cy-spot-b: rgba(142, 174, 204, 0.42);
  --cy-moon-dark: rgba(32, 29, 25, 0.16);
  --cy-moon-line: rgba(32, 29, 25, 0.22);
  background: var(--cy-bg);
  color-scheme: light;
  transition: background-color 1.2s ease;
}
:global(html.chillyice-page body) {
  background: var(--cy-bg);
}

.cy {
  position: relative;
  min-height: 100vh;
  min-height: 100dvh;
  display: grid;
  grid-template-rows: auto minmax(200px, 1fr) auto auto;
  grid-template-columns: minmax(0, 1fr);
  gap: 0;
  padding: 22px clamp(20px, 5vw, 48px) 18px;
  overflow: hidden;
  background: var(--cy-bg);
  color: var(--cy-ink);
  transition:
    background-color 1.2s ease,
    color 1.2s ease;
}

/* 静态氛围光:两层径向渐变,不参与交互 */
.cy-glow {
  position: absolute;
  inset: -20%;
  background:
    radial-gradient(closest-side at 22% 24%, var(--cy-spot-a), transparent 72%),
    radial-gradient(closest-side at 82% 72%, var(--cy-spot-b), transparent 74%);
  filter: blur(18px);
  pointer-events: none;
}

.cy-head {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.cy-kicker,
.cy-place {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  font-size: 11px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--cy-muted);
}
.cy-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--cy-accent);
}

/* ── 舞台:一只木板画框,日月挂在顶梁下,动物从草地上走过 ── */
.cy-stage {
  --orb-size: clamp(34px, 7vw, 76px);
  position: relative;
  z-index: 1;
  min-height: 0;
  margin: 8px 0 18px;
}
.cy-scene {
  position: absolute;
  inset: 0;
  overflow: hidden;
  border-radius: 16px;
  background: var(--cy-bg);
  box-shadow:
    0 24px 60px -34px rgba(20, 16, 12, 0.55),
    inset 0 0 0 1px var(--cy-line);
  isolation: isolate;
}

/* 天光:上冷下暖,和页面底色同源,换昼夜时一起过渡 */
.cy-air {
  position: absolute;
  inset: 0;
  z-index: 0;
  background: linear-gradient(
    to bottom,
    color-mix(in srgb, var(--cy-bg) 86%, #7d94b4 14%),
    var(--cy-bg) 58%,
    color-mix(in srgb, var(--cy-bg) 82%, var(--cy-spot-a))
  );
  transition: background 1.2s ease;
}

.cy-layer {
  position: absolute;
  inset: 0;
  z-index: 2;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: 50% 100%;
  pointer-events: none;
  user-select: none;
}
.cy-far {
  z-index: 1;
}
.cy-trees {
  z-index: 3;
}
.cy-cabin {
  z-index: 4;
}
.cy-mid {
  z-index: 5;
}
.cy-near {
  z-index: 8;
}

/* 天空盒:日月与星光只在这块里走,alt 0 落在草地上沿,不会沉到画面底下 */
.cy-orbit {
  position: absolute;
  inset: 0 0 11% 0;
  z-index: 0;
  display: block;
  width: 100%;
  height: 100%;
  overflow: visible;
  pointer-events: none;
}
.cy-arc {
  fill: none;
  stroke-width: 1;
  stroke-dasharray: 4 6;
  opacity: 0.3;
}
.cy-arc--sun {
  stroke: var(--cy-accent);
}
.cy-arc--moon {
  stroke: var(--cy-string);
}

/* 吊偶:一根线从顶梁垂到日月中心,高度角越矮线越短。
   日月是这场戏的提线木偶,挂在整幅布景前面,不会被树梢吃掉;
   但顶梁要盖在吊线之上,才有"从梁上垂下来"的实感。 */
.cy-puppets {
  position: absolute;
  inset: 0 0 11% 0;
  z-index: 11;
  pointer-events: none;
}
.cy-puppet {
  position: absolute;
  top: 0;
  z-index: 1;
  width: 0;
  opacity: 1;
}
.cy-puppet.is-ready {
  transition:
    left 1.6s ease,
    height 1.6s ease,
    opacity 0.6s ease;
}
.cy-puppet.is-off {
  opacity: 0;
}
.cy-string {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 1px;
  background: var(--cy-string);
}
.cy-string::before {
  content: '';
  position: absolute;
  left: 50%;
  top: -2px;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--cy-accent);
  transform: translateX(-50%);
}
.cy-anchor {
  position: absolute;
  left: 0;
  bottom: 0;
  width: 0;
  height: 0;
}

.cy-disc {
  position: absolute;
  left: 0;
  bottom: 0;
  display: block;
  width: var(--orb-size);
  aspect-ratio: 1;
  transform: translate(-50%, 50%) scale(var(--orb-scale, 1));
  transition: transform 1.6s ease;
}
.cy-disc img,
.cy-disc svg {
  display: block;
  width: 100%;
  height: 100%;
}
.cy-disc--sun img {
  border-radius: 50%;
  filter: drop-shadow(0 0 10px rgba(255, 198, 118, 0.55)) drop-shadow(0 0 26px rgba(232, 154, 72, 0.32));
}
.cy-disc--moon {
  filter: drop-shadow(0 0 12px rgba(196, 210, 232, 0.35));
}
.cy-moon-dark {
  fill: color-mix(in srgb, var(--cy-bg) 26%, #1a1c24);
}
.cy-moon-ring {
  fill: none;
  stroke: var(--cy-moon-line);
  stroke-width: 1;
}

/* 读数标签单独一层:舞台之上一整层,和日月共用同一套天空坐标 */
.cy-tags {
  position: absolute;
  inset: 0 0 11% 0;
  z-index: 6;
  pointer-events: none;
}
.cy-tag {
  position: absolute;
  transform: translate(-50%, calc(var(--orb-size) * 0.5 + 18px));
  padding: 2px 8px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--cy-bg) 68%, transparent);
  font-size: 10.5px;
  letter-spacing: 0.06em;
  white-space: nowrap;
  color: var(--cy-muted);
  transition:
    left 1.6s ease,
    top 1.6s ease;
}

/* 动物:一段长周期横穿,走到画外后长时间待着,负延迟错开出场 */
@keyframes cy-cross {
  0% {
    transform: translate3d(-45vw, 0, 0);
  }
  62% {
    transform: translate3d(112vw, 0, 0);
  }
  100% {
    transform: translate3d(112vw, 0, 0);
  }
}
@keyframes cy-bob {
  from {
    transform: translateY(-3px);
  }
  to {
    transform: translateY(3px);
  }
}
.cy-animal {
  position: absolute;
  left: 0;
  z-index: 6;
  pointer-events: none;
  user-select: none;
  transform: translate3d(-45vw, 0, 0);
  animation: cy-cross 78s linear infinite;
  will-change: transform;
}
.cy-animal--deer {
  bottom: 8%;
  width: 17%;
  animation-duration: 84s;
  animation-delay: -21s;
}
.cy-animal--rabbit {
  bottom: 7%;
  width: 7%;
  animation-duration: 63s;
  animation-delay: -44s;
}
.cy-animal--fox {
  bottom: 5%;
  width: 11%;
  animation-duration: 97s;
  animation-delay: -68s;
  animation-direction: reverse;
}
.cy-animal--bird {
  bottom: 56%;
  width: 6%;
  z-index: 4;
  animation-duration: 52s;
  animation-delay: -13s;
}
.cy-bird {
  display: block;
  width: 100%;
  animation: cy-bob 2.6s ease-in-out infinite alternate;
}
.cy-shadowed {
  filter: drop-shadow(var(--cy-sh-x) var(--cy-sh-y) var(--cy-sh-blur) rgba(26, 20, 14, var(--cy-sh-a)));
}

/* 夜色与暖光都只在上层压一层,不改底图 */
.cy-night {
  position: absolute;
  inset: 0;
  z-index: 9;
  background: #0a111d;
  opacity: var(--cy-night, 0);
  pointer-events: none;
  transition: opacity 1.2s ease;
}
/* 窗光压在夜色之上、和 cabin 同一套 object-fit,天黑了自己亮起来 */
.cy-cabin-glow {
  z-index: 10;
  mix-blend-mode: screen;
  opacity: calc(var(--cy-night, 0) * 1.4);
  transition: opacity 1.2s ease;
}
.cy-light {
  position: absolute;
  inset: 0 0 11% 0;
  z-index: 9;
  background: radial-gradient(
    closest-side at var(--cy-light-x, 70%) var(--cy-light-y, 12%),
    rgba(255, 205, 138, 0.95),
    rgba(255, 174, 96, 0.35) 46%,
    transparent 72%
  );
  opacity: var(--cy-warm-a, 0);
  mix-blend-mode: screen;
  pointer-events: none;
  transition: opacity 1.2s ease;
}
.cy-rail {
  position: absolute;
  left: 0;
  top: -6%;
  z-index: 12;
  width: 100%;
  height: auto;
  pointer-events: none;
  filter: drop-shadow(0 4px 6px rgba(30, 22, 14, 0.28));
}

@media (prefers-reduced-motion: reduce) {
  :global(html.chillyice-page) {
    transition: none;
  }
  .cy-puppet,
  .cy-puppet.is-ready,
  .cy-tag,
  .cy-disc,
  .cy-night,
  .cy-light,
  .cy {
    transition: none;
  }
  .cy-animal {
    display: none;
  }
}

/* ── 台前:名字与读数 ── */
.cy-copy {
  position: relative;
  z-index: 2;
  max-width: 680px;
}
.cy-name {
  margin: 0;
  font-size: clamp(40px, 7.4vw, 76px);
  font-weight: 600;
  line-height: 0.96;
  letter-spacing: -0.045em;
}
.cy-role {
  margin: 12px 0 0;
  font-size: clamp(14px, 1.7vw, 17px);
  letter-spacing: 0.02em;
}
.cy-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 16px;
  margin: 12px 0 0;
  font-size: 11px;
  letter-spacing: 0.06em;
  color: var(--cy-muted);
  font-variant-numeric: tabular-nums;
}

.cy-scrub {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 14px;
  margin-top: 14px;
  font-size: 11px;
  letter-spacing: 0.06em;
  color: var(--cy-muted);
}
.cy-scrub-field {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
}
.cy-scrub-range {
  appearance: none;
  -webkit-appearance: none;
  width: min(200px, 46vw);
  height: 2px;
  margin: 0;
  border-radius: 2px;
  background: var(--cy-line);
  cursor: pointer;
}
.cy-scrub-range::-webkit-slider-thumb {
  -webkit-appearance: none;
  width: 14px;
  height: 14px;
  border: 0;
  border-radius: 50%;
  background: var(--cy-accent);
}
.cy-scrub-range::-moz-range-thumb {
  width: 14px;
  height: 14px;
  border: 0;
  border-radius: 50%;
  background: var(--cy-accent);
}
.cy-scrub-range:focus-visible {
  outline: 2px solid var(--cy-accent);
  outline-offset: 6px;
}
.cy-scrub-time {
  min-width: 4.2em;
  color: var(--cy-ink);
  font-variant-numeric: tabular-nums;
}
.cy-scrub-reset {
  padding: 0;
  border: 0;
  border-bottom: 1px solid currentColor;
  background: none;
  color: var(--cy-accent);
  font: inherit;
  letter-spacing: inherit;
  cursor: pointer;
}

.cy-links {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 18px;
}
.cy-link {
  display: inline-flex;
  align-items: center;
  height: 40px;
  padding: 0 20px;
  border: 1px solid var(--cy-line);
  border-radius: 999px;
  font-size: 13px;
  color: inherit;
  text-decoration: none;
  transition:
    background-color 0.18s ease,
    border-color 0.18s ease,
    color 0.18s ease;
}
.cy-link:hover {
  border-color: var(--cy-ink);
}
.cy-link:focus-visible {
  outline: 2px solid var(--cy-accent);
  outline-offset: 3px;
}
.cy-link--primary {
  background: var(--cy-ink);
  border-color: var(--cy-ink);
  color: var(--cy-bg);
}
.cy-link--primary:hover {
  background: var(--cy-accent);
  border-color: var(--cy-accent);
  color: #fff;
}

.cy-foot {
  position: relative;
  z-index: 2;
  margin-top: 18px;
  font-size: 11px;
  letter-spacing: 0.12em;
  color: var(--cy-muted);
}

@media (max-width: 520px) {
  .cy {
    padding: 18px 18px 14px;
    grid-template-rows: auto minmax(160px, 1fr) auto auto;
  }
  .cy-stage {
    --orb-size: clamp(28px, 9vw, 44px);
    margin-bottom: 14px;
  }
  .cy-copy {
    max-width: none;
  }
  .cy-animal--deer {
    width: 26%;
  }
  .cy-animal--rabbit {
    width: 11%;
  }
  .cy-animal--fox {
    width: 17%;
  }
  .cy-animal--bird {
    width: 9%;
  }
}
</style>
