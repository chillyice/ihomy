<!-- chillyice 个人主页 —— 独立页(https://ihomy.top/chillyice),全公开、不套 ihomy 外壳。
     舞台上的太阳与月亮按济南此刻的真实高度角/方位角悬挂:高度角决定吊线长短,方位角决定左右,
     落到地平线以下就不出现。天文数据来自后端 SolarUtil(单一来源),本页只做几何投影与渲染。 -->
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

    <section class="cy-stage">
      <div class="cy-baton" aria-hidden="true"></div>
      <div class="cy-horizon" aria-hidden="true"></div>

      <div class="cy-puppet" :class="{ 'is-off': !sun.visible, 'is-ready': ready, 'is-low': sun.y > 78 }" :style="puppetStyle(sun)">
        <span class="cy-string" aria-hidden="true"></span>
        <span class="cy-anchor">
          <span class="cy-disc cy-disc--sun" role="img" :aria-label="$t('chillyice.sunAria', { alt: Math.round(sunAlt) })"></span>
          <span class="cy-label">{{ $t('chillyice.sun') }}</span>
        </span>
      </div>

      <div class="cy-puppet" :class="{ 'is-off': !moon.visible, 'is-ready': ready, 'is-low': moon.y > 78 }" :style="puppetStyle(moon)">
        <span class="cy-string" aria-hidden="true"></span>
        <span class="cy-anchor">
          <span
            class="cy-disc cy-disc--moon"
            role="img"
            :aria-label="$t('chillyice.moonAria', { alt: Math.round(moonAlt), phase: moonPhaseName })"
          >
            <svg viewBox="-50 -50 100 100" aria-hidden="true">
              <circle class="cy-moon-dark" r="50" />
              <path class="cy-moon-lit" :d="moonPath" />
              <circle class="cy-moon-ring" r="49.5" />
            </svg>
          </span>
          <span class="cy-label">{{ moonLabel }}</span>
        </span>
      </div>

      <p v-if="!sun.visible && !moon.visible" class="cy-empty">{{ $t('chillyice.empty') }}</p>
    </section>

    <section class="cy-copy">
      <h1 class="cy-name">chillyice</h1>
      <p class="cy-role">{{ $t('chillyice.role') }}</p>
      <p class="cy-note">{{ $t('chillyice.note') }}</p>
      <p class="cy-meta">
        <span>{{ $t('chillyice.coords') }}</span>
        <span v-if="sun.visible">{{ $t('chillyice.sunAltitude', { deg: Math.round(sunAlt) }) }}</span>
        <span v-if="moon.visible">{{ $t('chillyice.moonAltitude', { deg: Math.round(moonAlt) }) }}</span>
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
import { moonLitPath, projectBody, skyStyle } from '@/utils/celestialStage'
import { moonPhaseText } from '@/utils/dict'

// 固定济南:这页讲的是「从这里看天空」,不跟随访客 IP 或家庭设置
const JINAN = { lat: 36.6512, lng: 117.1201 }
const JINAN_OFFSET_MS = 8 * 3_600_000
const POLL_MS = 60_000

const githubUrl = 'https://github.com/chillyice'
const year = new Date().getFullYear()
const { t } = useI18n()

const astro = reactive({ sun: null, moon: null, moonPhase: 0, moonPhaseCode: '' })
const scrub = ref(null)
const nowMin = ref(0)
const ready = ref(false)
let timer = 0
let scrubTimer = 0
let controller = null

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

// 天空随太阳高度角变色:变量挂在 html 上,body 与 .cy 一起继承
const sky = computed(() => skyStyle(astro.sun?.altitude))
const SKY_KEYS = Object.keys(skyStyle(24))
watchEffect(() => {
  for (const [k, v] of Object.entries(sky.value)) document.documentElement.style.setProperty(k, v)
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
      if (!ready.value) {
        // 等首帧按真实位置落位后再开过渡,避免从舞台中央滑进来
        requestAnimationFrame(() => requestAnimationFrame(() => (ready.value = true)))
      }
    }
  } catch (err) {
    if (err.name !== 'AbortError') console.warn('[chillyice] astro unavailable', err)
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
  timer = window.setInterval(() => {
    if (scrub.value === null) load()
  }, POLL_MS)
})

onBeforeUnmount(() => {
  window.clearInterval(timer)
  window.clearTimeout(scrubTimer)
  controller?.abort()
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
  --cy-sun-core: #fff6d8;
  --cy-sun-edge: #e5a04a;
  --cy-sun-glow: rgba(233, 170, 90, 0.45);
  --cy-moon-lit: #fdf7ea;
  --cy-moon-dark: rgba(32, 29, 25, 0.16);
  --cy-moon-line: rgba(32, 29, 25, 0.22);
  --cy-moon-glow: rgba(196, 184, 160, 0.4);
  background: var(--cy-bg);
  color-scheme: light;
  transition: background-color 1.2s ease;
}
:global(html.chillyice-page body) {
  background: var(--cy-bg);
}
@media (prefers-color-scheme: dark) {
  :global(html.chillyice-page) {
    --cy-sun-core: #ffeab4;
    --cy-sun-edge: #d98a3c;
    --cy-sun-glow: rgba(224, 160, 106, 0.38);
    --cy-moon-lit: #f4ecda;
    --cy-moon-glow: rgba(176, 188, 210, 0.32);
  }
}

.cy {
  position: relative;
  min-height: 100vh;
  min-height: 100dvh;
  display: grid;
  grid-template-rows: auto minmax(180px, 1fr) auto auto;
  transition:
    background-color 1.2s ease,
    color 1.2s ease;
  grid-template-columns: minmax(0, 1fr);
  gap: 0;
  padding: 22px clamp(20px, 5vw, 48px) 18px;
  overflow: hidden;
  background: var(--cy-bg);
  color: var(--cy-ink);
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
  z-index: 1;
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

/* ── 舞台:顶线是木架,底线是地平线,中间只放日月 ── */
.cy-stage {
  position: relative;
  z-index: 1;
  min-height: 0;
  margin: 6px 0 14px;
}
.cy-baton {
  position: absolute;
  top: 0;
  left: 4%;
  right: 4%;
  height: 1px;
  background: var(--cy-line);
}
.cy-horizon {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 1px;
  background: var(--cy-line);
}

.cy-puppet {
  --orb-size: clamp(36px, 8vw, 88px);
  position: absolute;
  top: 0;
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
  pointer-events: none;
}
.cy-puppet.is-low .cy-label {
  top: auto;
  bottom: calc(var(--orb-size) / 2 + 16px);
  left: 12px;
  transform: none;
}
@media (prefers-reduced-motion: reduce) {
  :global(html.chillyice-page) {
    transition: none;
  }
  .cy-puppet,
  .cy-puppet.is-ready,
  .cy-disc,
  .cy {
    transition: none;
  }
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
.cy-disc--sun {
  border-radius: 50%;
  background: radial-gradient(circle at 34% 30%, var(--cy-sun-core) 0 26%, var(--cy-sun-edge) 100%);
  box-shadow:
    0 0 28px var(--cy-sun-glow),
    0 0 72px var(--cy-sun-glow);
}
.cy-disc--moon {
  filter: drop-shadow(0 0 14px var(--cy-moon-glow));
}
.cy-disc--moon svg {
  display: block;
  width: 100%;
  height: 100%;
  overflow: visible;
}
.cy-moon-dark {
  fill: var(--cy-moon-dark);
}
.cy-moon-lit {
  fill: var(--cy-moon-lit);
}
.cy-moon-ring {
  fill: none;
  stroke: var(--cy-moon-line);
  stroke-width: 1;
}

.cy-label {
  position: absolute;
  left: 0;
  top: calc(var(--orb-size) / 2 + 10px);
  transform: translateX(-50%);
  white-space: nowrap;
  font-size: 11px;
  letter-spacing: 0.08em;
  color: var(--cy-muted);
}

.cy-empty {
  position: absolute;
  left: 50%;
  top: 46%;
  transform: translate(-50%, -50%);
  margin: 0;
  max-width: 26em;
  text-align: center;
  font-size: 13px;
  line-height: 1.8;
  color: var(--cy-muted);
}

/* ── 台前:名字与说明 ── */
.cy-copy {
  position: relative;
  z-index: 1;
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
.cy-note {
  max-width: 38em;
  margin: 10px 0 0;
  font-size: 13.5px;
  line-height: 1.85;
  color: var(--cy-muted);
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
  z-index: 1;
  margin-top: 18px;
  font-size: 11px;
  letter-spacing: 0.12em;
  color: var(--cy-muted);
}

@media (max-width: 520px) {
  .cy {
    padding: 18px 18px 14px;
  }
  .cy-stage {
    margin-bottom: 18px;
  }
  .cy-copy {
    max-width: none;
  }
  .cy-note {
    font-size: 13px;
  }
}
</style>
