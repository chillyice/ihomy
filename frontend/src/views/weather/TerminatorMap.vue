<!-- 晨昏地图(天气页 L3):世界轮廓 + 此刻全球晨昏色带,点击地点看该处此刻的晨昏阶段。
     天文计算的唯一来源是后端 SolarUtil(随 /public/sun-info 下发 subsolar 直射点),
     本组件只做几何渲染:逐像素高度角由「直射点 + 经纬度」球面公式推出,不移植 NOAA/Meeus。 -->
<template>
  <div class="card wt-card-quiet wt-map-card">
    <div class="section-label">{{ $t('weatherPage.map.title') }}</div>

    <div class="wt-map-grid" :class="{ 'is-degraded': !mapReady }">
      <div v-if="mapReady" class="wt-map-left">
        <div class="wt-map-wrap">
          <canvas ref="canvasEl" aria-hidden="true" @click="onCanvasClick"></canvas>
          <button
            class="wt-map-marker"
            :style="markerStyle"
            type="button"
            :aria-label="$t('weatherPage.map.hint')"
            @click="focusCity"
          ></button>
        </div>
        <div class="wt-map-legend">
          <span v-for="s in STAGE_ORDER" :key="s" class="wt-map-lg">
            <i :style="{ background: bandColor(s) }"></i>{{ $t('weatherPage.map.stage.' + s) }}
          </span>
        </div>
        <div class="wt-map-hint">{{ $t('weatherPage.map.hint') }}</div>
      </div>

      <div ref="panelEl" class="wt-map-panel">
        <div v-if="mapReady" class="wt-map-now">
          <span class="wtm-badge" :style="{ background: stageTint, color: stageColor }">
            {{ $t('weatherPage.map.stage.' + stage) }}
          </span>
          <span class="wtm-where">{{ $t('weatherPage.map.nowAt') }} · {{ focusLabel }}</span>
        </div>
        <div v-if="mapReady" class="wt-map-figs">
          <div class="wtm-fig">
            <span class="wtm-l">{{ $t('weatherPage.map.altitude') }}</span>
            <span class="wtm-v">{{ deg(focusPos.altitude) }}</span>
          </div>
          <div class="wtm-fig">
            <span class="wtm-l">{{ $t('weatherPage.map.azimuth') }}</span>
            <span class="wtm-v">{{ deg(focusPos.azimuth) }}</span>
          </div>
        </div>

        <!-- 当地今日:9 个时刻 / 下一阶段 / 高度曲线 / 月亮 —— 与晨昏地图同源的一份 sun-info -->
        <template v-if="twilightMarks.length">
          <div class="wtm-sub">
            {{ $t('weatherPage.map.todayAt') }}<span v-if="cityName"> · {{ cityName }}</span>
          </div>
          <div class="wtm-next">
            <span class="wtm-l">{{ $t('weatherPage.map.nextStage') }}</span>
            <span class="wtm-v">
              <template v-if="nextEvent">
                {{ $t('weatherPage.' + nextEvent.key) }} {{ nextEvent.time }}<em>{{ nextInText }}</em>
              </template>
              <template v-else>—</template>
            </span>
          </div>
          <div class="wtm-times">
            <div v-for="m in twilightMarks" :key="m.key" class="wtm-time" :class="{ 'is-sun': SUN_KEYS.includes(m.key) }">
              <span class="wtm-time-t">{{ m.time }}</span>
              <span class="wtm-time-l">{{ $t('weatherPage.' + m.key) }}</span>
            </div>
          </div>
        </template>

        <div v-if="curve" class="wtm-curve">
          <div class="wtm-sub">{{ $t('weatherPage.map.todayCurve') }}</div>
          <svg :viewBox="`0 0 ${CURVE_W} ${CURVE_H}`" class="wtm-curve-svg" role="img" :aria-label="$t('weatherPage.map.todayCurve')">
            <template v-for="b in curve.bands" :key="b.key">
              <rect x="0" :y="b.y" :width="CURVE_W" :height="CURVE_H - b.y" :fill="bandColor(b.key)" />
              <line x1="0" :y1="b.y" :x2="CURVE_W" :y2="b.y" class="wtm-curve-th" />
            </template>
            <path :d="curve.area" :fill="curve.stroke" opacity="0.12" />
            <path :d="curve.line" fill="none" :stroke="curve.stroke" stroke-width="1.8" stroke-linejoin="round" />
            <line :x1="curve.nowX" y1="0" :x2="curve.nowX" :y2="CURVE_H" class="wtm-curve-now" />
          </svg>
        </div>

        <div v-if="astro?.moonPhaseCode" class="wtm-moon">
          <span class="wtm-moon-icon">{{ moonIcon(astro.moonPhaseCode) }}</span>
          <div class="wtm-moon-main">
            <div class="wtm-moon-name">{{ moonPhaseText(t, astro.moonPhaseCode) }}</div>
            <div class="wtm-moon-sub">
              <span>{{ $t('weatherPage.moonrise') }} {{ astro.moonrise || '—' }}</span>
              <span>{{ $t('weatherPage.moonset') }} {{ astro.moonset || '—' }}</span>
              <span v-if="astro.moonIllumination != null"> {{ $t('weatherPage.illumination') }} {{ astro.moonIllumination }}% </span>
            </div>
          </div>
          <div v-if="moonDays.length" class="wtm-moon-days">
            <span v-for="d in moonDays" :key="d.fxDate">{{ fmtMD(d.fxDate) }} {{ moonIcon(d.moonPhaseCode) }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useThemeStore } from '@/stores/theme'
import { moonIcon, moonPhaseText } from '@/utils/dict'
import {
  MAP_H,
  MAP_LAT_MAX,
  MAP_W,
  STAGE_ORDER,
  driftSubsolar,
  nextTwilightEvent,
  nowLocalMinutes,
  projectToMap,
  solarPosition,
  twilightStage,
  unprojectFromMap,
} from '@/utils/terminator'
import { WORLD_LAND_PATH } from '@/assets/world-land-path'

const props = defineProps({
  astro: { type: Object, default: null },
  city: { type: String, default: '' },
  lat: { type: Number, default: null },
  lng: { type: Number, default: null },
  generatedAt: { type: String, default: '' },
  subsolar: { type: Object, default: null },
})

const { t } = useI18n()
const themeStore = useThemeStore()

const SUN_KEYS = ['sunrise', 'solarNoon', 'sunset']
const TWILIGHT_KEYS = [
  'astronomicalDawn',
  'nauticalDawn',
  'civilDawn',
  'sunrise',
  'solarNoon',
  'sunset',
  'civilDusk',
  'nauticalDusk',
  'astronomicalDusk',
]

// 阶段徽标配色:白天琥珀 → 民用橙 → 航海蓝 → 天文深蓝 → 夜间灰(两套主题底色下都可读)
const STAGE_COLOR = { day: '#c9a227', civil: '#e0862f', nautical: '#4a90d9', astronomical: '#3a5f9e', night: '#7a6b5a' }
// 晨昏色带:两套主题各一组(暖居偏暖褐、光尘偏冷蓝),alpha 递增 = 夜色加深;深色模式再抬一档
const BANDS = {
  warm: { civil: '120,105,85', nautical: '100,90,75', astronomical: '80,72,62', night: '60,54,48' },
  guangchen: { civil: '90,110,150', nautical: '65,85,130', astronomical: '40,55,100', night: '20,30,60' },
}
const ALPHA = { civil: 0.1, nautical: 0.2, astronomical: 0.32, night: 0.48 }
const ALPHA_DARK = { civil: 0.14, nautical: 0.26, astronomical: 0.4, night: 0.58 }

const canvasEl = ref(null)
const panelEl = ref(null)
const focus = ref(null) // {lat, lng}:地图上当前查看的地点,默认当地
const drawFailed = ref(false)
const mapReady = computed(() => !!props.subsolar && !drawFailed.value)

// 直射点随时间漂移(后端给的是请求时刻,页面开着不动晨昏线也得走起来)
const baseTs = ref(Date.now())
const elapsed = ref(0)
const subsolarNow = computed(() => driftSubsolar(props.subsolar, elapsed.value))

const cityFocus = computed(() => (props.lat != null ? { lat: props.lat, lng: props.lng } : null))
const isCityFocus = computed(
  () => !!focus.value && props.lat != null && Math.abs(focus.value.lat - props.lat) < 0.5 && Math.abs(focus.value.lng - props.lng) < 0.5,
)
const fmtCoord = (p) => `${Math.abs(p.lat).toFixed(1)}°${p.lat >= 0 ? 'N' : 'S'} ${Math.abs(p.lng).toFixed(1)}°${p.lng >= 0 ? 'E' : 'W'}`
const focusLabel = computed(() => {
  if (!focus.value) return props.city || '—'
  return isCityFocus.value ? props.city || fmtCoord(focus.value) : fmtCoord(focus.value)
})

const focusPos = computed(() => {
  const ss = subsolarNow.value
  const p = focus.value
  if (!ss || !p) return { altitude: 0, azimuth: 0 }
  return solarPosition(p.lat, p.lng, ss)
})
const stage = computed(() => twilightStage(focusPos.value.altitude))
const stageColor = computed(() => STAGE_COLOR[stage.value])
const stageTint = computed(() => stageColor.value + '22')

const deg = (v) => `${Number(v ?? 0).toFixed(1)}°`

const isDark = () => document.documentElement.classList.contains('dark')
const bandAlpha = (key) => (isDark() ? ALPHA_DARK[key] : ALPHA[key])
function bandColor(key) {
  if (key === 'day') return 'transparent'
  return `rgba(${BANDS[themeStore.theme]?.[key] || BANDS.warm[key]},${bandAlpha(key)})`
}
const cssVar = (name, fallback) => getComputedStyle(document.documentElement).getPropertyValue(name).trim() || fallback

const markerStyle = computed(() => {
  if (props.lat == null) return { display: 'none' }
  const p = projectToMap(props.lng, props.lat, MAP_W, MAP_H)
  return { left: (p.x / MAP_W) * 100 + '%', top: (p.y / MAP_H) * 100 + '%' }
})

// ── 当地今日(与晨昏地图同源的一份 sun-info) ──
const cityName = computed(() => props.city || '')
const twilightMarks = computed(() => TWILIGHT_KEYS.map((key) => ({ key, time: props.astro?.[key] || '' })).filter((m) => m.time))
const moonDays = computed(() => (props.astro?.moonDays || []).slice(0, 5))
const fmtMD = (s) => {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(s || '')
  return m ? `${Number(m[2])}/${Number(m[3])}` : ''
}
const nextEvent = computed(() => nextTwilightEvent(props.astro, nowLocalMinutes()))
const nextInText = computed(() => {
  const e = nextEvent.value
  if (!e) return ''
  const d = e.minutes - nowLocalMinutes()
  return d >= 60 ? t('weatherPage.map.afterHm', { h: Math.floor(d / 60), m: d % 60 }) : t('weatherPage.map.afterM', { m: d })
})

// ── 今日太阳高度曲线(地图之外的第二条晨昏读数) ──
const CURVE_W = 720,
  CURVE_H = 90,
  CURVE_LO = -30,
  CURVE_HI = 90
const curve = computed(() => {
  const list = props.astro?.slots || []
  if (list.length < 2) return null
  const yOf = (a) => CURVE_H - 6 - ((Math.max(CURVE_LO, Math.min(CURVE_HI, a)) - CURVE_LO) / (CURVE_HI - CURVE_LO)) * (CURVE_H - 12)
  const pts = list.map((s, i) => ({ x: (i / (list.length - 1)) * CURVE_W, y: yOf(s.altitude) }))
  const line = pts.map((p, i) => `${i ? 'L' : 'M'}${p.x.toFixed(1)} ${p.y.toFixed(1)}`).join('')
  return {
    line,
    area: `${line}L${CURVE_W} ${CURVE_H}L0 ${CURVE_H}Z`,
    bands: [-6, -12, -18].map((v, i) => ({ key: ['civil', 'nautical', 'astronomical'][i], y: yOf(v) })),
    nowX: (nowLocalMinutes() / 1440) * CURVE_W,
    stroke: cssVar('--color-brand', '#b88c6e'),
  }
})

// ── Canvas 绘制 ──
let landPath = null
let bandCanvas = null
let timer = null
let ro = null

/** 晨昏色带:逐像素只比对 sin 阈值,不做 asin(高度角在 ±90° 内与 sin 同序,比较等价) */
function paintBands(ctx, ss) {
  if (!bandCanvas) bandCanvas = document.createElement('canvas')
  bandCanvas.width = MAP_W
  bandCanvas.height = MAP_H
  const bctx = bandCanvas.getContext('2d')
  const img = bctx.createImageData(MAP_W, MAP_H)
  const d = img.data
  const decR = (ss.lat * Math.PI) / 180
  const sinDec = Math.sin(decR)
  const cosDec = Math.cos(decR)
  // sin(-0.833 / -6 / -12 / -18):分档阈值,与 twilightStage 的 > 语义一致
  const T = [-0.833, -6, -12, -18].map((v) => Math.sin((v * Math.PI) / 180))
  // 逐列 cosH、逐行 sinLat/cosLat 预计算,像素循环里不再调三角函数
  const cosH = new Float64Array(MAP_W)
  for (let x = 0; x < MAP_W; x++) {
    cosH[x] = Math.cos(((-180 + (x / (MAP_W - 1)) * 360 - ss.lng) * Math.PI) / 180)
  }
  const rgb = BANDS[themeStore.theme] || BANDS.warm
  const keys = ['civil', 'nautical', 'astronomical', 'night']
  const alphas = keys.map((k) => Math.round(bandAlpha(k) * 255))
  const cols = keys.map((k) => rgb[k].split(',').map(Number))
  for (let y = 0; y < MAP_H; y++) {
    const lat = MAP_LAT_MAX - (y / (MAP_H - 1)) * (MAP_LAT_MAX - -60)
    const latR = (lat * Math.PI) / 180
    const sinLat = Math.sin(latR) * sinDec
    const cosLat = Math.cos(latR) * cosDec
    let i = y * MAP_W * 4
    for (let x = 0; x < MAP_W; x++, i += 4) {
      const s = sinLat + cosLat * cosH[x]
      const lv = s > T[0] ? -1 : s > T[1] ? 0 : s > T[2] ? 1 : s > T[3] ? 2 : 3
      if (lv < 0) continue
      d[i] = cols[lv][0]
      d[i + 1] = cols[lv][1]
      d[i + 2] = cols[lv][2]
      d[i + 3] = alphas[lv]
    }
  }
  bctx.putImageData(img, 0, 0)
  ctx.drawImage(bandCanvas, 0, 0, MAP_W, MAP_H)
}

function draw() {
  const cv = canvasEl.value
  const ss = subsolarNow.value
  if (!cv || !ss) return
  try {
    const dpr = Math.min(window.devicePixelRatio || 1, 2)
    const cssW = cv.clientWidth || MAP_W
    const cssH = (cssW * MAP_H) / MAP_W
    cv.width = Math.round(cssW * dpr)
    cv.height = Math.round(cssH * dpr)
    cv.style.height = cssH + 'px'
    const ctx = cv.getContext('2d')
    ctx.setTransform(cssW / MAP_W, 0, 0, cssH / MAP_H, 0, 0)

    // 海洋底色 → 晨昏色带 → 陆地 → 陆地描边 → 经纬网 → 赤道/本初子午线 → 城市点
    ctx.fillStyle = cssVar('--color-card-2', '#e8dec8')
    ctx.fillRect(0, 0, MAP_W, MAP_H)
    paintBands(ctx, ss)

    if (!landPath) landPath = new Path2D(WORLD_LAND_PATH)
    ctx.fillStyle = cssVar('--color-card', '#faf6ec')
    ctx.fill(landPath)
    ctx.strokeStyle = cssVar('--color-border', 'rgba(0,0,0,.12)')
    ctx.lineWidth = 0.5
    ctx.stroke(landPath)

    ctx.strokeStyle = cssVar('--color-text-tertiary', '#b5a98c')
    ctx.lineWidth = 0.4
    ctx.globalAlpha = 0.35
    ctx.beginPath()
    for (let lng = -150; lng <= 150; lng += 30) {
      const x = projectToMap(lng, 0, MAP_W, MAP_H).x
      ctx.moveTo(x, 0)
      ctx.lineTo(x, MAP_H)
    }
    for (let lat = -30; lat <= 60; lat += 30) {
      const y = projectToMap(0, lat, MAP_W, MAP_H).y
      ctx.moveTo(0, y)
      ctx.lineTo(MAP_W, y)
    }
    ctx.stroke()
    // 赤道与本初子午线略重一点
    ctx.globalAlpha = 0.6
    ctx.beginPath()
    const eq = projectToMap(0, 0, MAP_W, MAP_H)
    ctx.moveTo(0, eq.y)
    ctx.lineTo(MAP_W, eq.y)
    ctx.moveTo(eq.x, 0)
    ctx.lineTo(eq.x, MAP_H)
    ctx.stroke()
    ctx.globalAlpha = 1

    if (props.lat != null) {
      const p = projectToMap(props.lng, props.lat, MAP_W, MAP_H)
      ctx.fillStyle = cssVar('--color-brand', '#b88c6e')
      ctx.beginPath()
      ctx.arc(p.x, p.y, 2.4, 0, Math.PI * 2)
      ctx.fill()
    }
    drawFailed.value = false
  } catch {
    // Canvas 不可用(老浏览器/内存不足)→ 降级为只有时刻与月亮的面板
    drawFailed.value = true
  }
}

// ── 交互:地图上点任意地点 → 面板切到该地点 ──
function onCanvasClick(e) {
  const cv = canvasEl.value
  if (!cv) return
  const r = cv.getBoundingClientRect()
  const p = unprojectFromMap(((e.clientX - r.left) / r.width) * MAP_W, ((e.clientY - r.top) / r.height) * MAP_H)
  focus.value = { lat: p.lat, lng: p.lng }
  scrollPanelIntoView()
}
function focusCity() {
  if (!cityFocus.value) return
  focus.value = { ...cityFocus.value }
  scrollPanelIntoView()
}
function scrollPanelIntoView() {
  // 手机单列布局点完要把面板滚进视野,否则看不到更新后的数字
  if (window.matchMedia('(max-width: 900px)').matches) {
    panelEl.value?.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
  }
}

onMounted(() => {
  focus.value = cityFocus.value ? { ...cityFocus.value } : null
  // 后端给了 generatedAt 才算得准已过分钟数;没有就按当前时刻起算(误差最多几小时)
  const ts = props.generatedAt ? Date.parse(props.generatedAt) : NaN
  baseTs.value = Number.isNaN(ts) ? Date.now() : ts
  elapsed.value = Math.max(0, (Date.now() - baseTs.value) / 60000)
  ro = new ResizeObserver(() => draw())
  if (canvasEl.value) ro.observe(canvasEl.value)
  timer = setInterval(() => {
    if (document.hidden) return
    elapsed.value = Math.max(0, (Date.now() - baseTs.value) / 60000)
    draw()
  }, 60000)
})

onBeforeUnmount(() => {
  clearInterval(timer)
  ro?.disconnect()
})

// canvas 是 v-if 的产物,subsolar 到位后它才挂上 → 挂上时补一次绘制与观察
watch(
  canvasEl,
  (el) => {
    if (!el) return
    draw()
    ro?.observe(el)
  },
  { flush: 'post' },
)
// 主题/晨暮切换 → CSS 变量变了,Canvas 必须重画(Canvas 拿不到 var())
watch(
  () => [themeStore.theme, themeStore.mode],
  () => draw(),
)
</script>

<style scoped>
.wt-map-card {
  padding: 16px 20px;
}
.wt-map-grid {
  display: grid;
  grid-template-columns: minmax(0, 640px) 1fr;
  gap: 18px;
  align-items: start;
}
.wt-map-grid.is-degraded {
  grid-template-columns: 1fr;
}
.wt-map-wrap {
  position: relative;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid var(--color-border);
}
.wt-map-wrap canvas {
  display: block;
  width: 100%;
  cursor: crosshair;
}
.wt-map-marker {
  position: absolute;
  transform: translate(-50%, -50%);
  width: 14px;
  height: 14px;
  padding: 0;
  border: 2px solid var(--color-card);
  border-radius: 50%;
  background: var(--color-brand);
  cursor: pointer;
}
.wt-map-marker::after {
  content: '';
  position: absolute;
  inset: -5px;
  border-radius: 50%;
  border: 1.5px solid var(--color-brand);
  opacity: 0.5;
}
.wt-map-marker:focus-visible {
  outline: 2px solid var(--color-brand);
  outline-offset: 3px;
}
.wt-map-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
  margin-top: 8px;
  font-size: 11px;
  color: var(--color-text-secondary);
}
.wt-map-lg {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.wt-map-lg i {
  width: 12px;
  height: 8px;
  border-radius: 2px;
  border: 1px solid var(--color-border);
}
.wt-map-hint {
  margin-top: 6px;
  font-size: 11px;
  color: var(--color-text-tertiary);
}
.wt-map-panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
}
.wt-map-now {
  display: flex;
  align-items: center;
  gap: 8px;
}
.wtm-badge {
  border-radius: 8px;
  padding: 3px 10px;
  font-size: 13px;
  font-weight: 700;
}
.wtm-where {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.wt-map-figs {
  display: flex;
  gap: 10px;
}
.wtm-fig {
  flex: 1 1 0;
  background: var(--color-card-2);
  border-radius: 10px;
  padding: 8px 12px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.wtm-l {
  font-size: 11px;
  color: var(--color-text-secondary);
}
.wtm-v {
  font-size: 15px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.wtm-sub {
  margin-top: 4px;
  font-size: 11px;
  color: var(--color-text-tertiary);
  letter-spacing: 0.5px;
}
.wtm-next {
  display: flex;
  align-items: baseline;
  gap: 10px;
}
.wtm-next .wtm-v em {
  font-style: normal;
  font-size: 11px;
  font-weight: 400;
  color: var(--color-text-secondary);
  margin-left: 4px;
}
.wtm-times {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 6px;
}
.wtm-time {
  background: var(--color-card-2);
  border-radius: 8px;
  padding: 6px 8px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.wtm-time.is-sun {
  background: rgba(var(--color-brand-rgb), 0.1);
}
.wtm-time-t {
  font-size: 13px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}
.wtm-time-l {
  font-size: 10px;
  color: var(--color-text-secondary);
}
.wtm-curve-svg {
  width: 100%;
  height: auto;
  display: block;
  border-radius: 8px;
}
.wtm-curve-th {
  stroke: var(--color-text-tertiary);
  stroke-width: 0.5;
  opacity: 0.5;
}
.wtm-curve-now {
  stroke: var(--color-text);
  stroke-width: 1;
  stroke-dasharray: 3 3;
  opacity: 0.7;
}
.wtm-moon {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  background: var(--color-card-2);
  border-radius: 10px;
  padding: 10px 12px;
}
.wtm-moon-icon {
  font-size: 28px;
  line-height: 1;
}
.wtm-moon-main {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.wtm-moon-name {
  font-size: 14px;
  font-weight: 700;
}
.wtm-moon-sub {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  font-size: 11px;
  color: var(--color-text-secondary);
  font-variant-numeric: tabular-nums;
}
.wtm-moon-days {
  margin-left: auto;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  font-size: 11px;
  color: var(--color-text-secondary);
  font-variant-numeric: tabular-nums;
}
@media (max-width: 900px) {
  .wt-map-grid {
    grid-template-columns: 1fr;
  }
  .wtm-times {
    grid-template-columns: repeat(2, 1fr);
  }
  .wtm-moon-days {
    margin-left: 0;
  }
}
</style>
