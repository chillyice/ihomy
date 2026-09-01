<!-- 天气详情页(V9.12):展示当天当地全部天气数据。
     数据源 GET /api/public/weather/detail(now 全量实况/24h/10d/预警/空气/生活指数/分钟降水),
     后端 Redis 缓存 30 分钟;从首页天气组件与侧边栏迷你天气点击进入。 -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('weatherPage.title') }]" />
    <div v-loading="loading" class="weather-page-body">
      <!-- 顶部实况 -->
      <div class="card wt-hero">
        <div class="wt-now">
          <div class="wt-city">{{ detail?.now?.city || '—' }} · {{ todayLabel }}</div>
          <div class="wt-main">
            <i :class="'qi-' + (detail?.now?.iconCode || '100')" class="wt-big-icon"></i>
            <span class="wt-temp">{{ detail?.now?.temp ?? '—' }}<span class="wt-degree">°</span></span>
            <span class="wt-hilo">
              <span class="wt-hi">↑ {{ todayHigh }}°</span>
              <span class="wt-lo">↓ {{ todayLow }}°</span>
            </span>
            <span class="wt-text">{{ detail?.now?.text || '' }}</span>
          </div>
        </div>
        <div class="wt-metrics">
          <div class="wt-metric"><span class="wm-label">{{ $t('weatherPage.feelsLike') }}</span><span class="wm-val">{{ nf('feelsLike') }}°</span></div>
          <div class="wt-metric"><span class="wm-label">{{ $t('weatherPage.humidity') }}</span><span class="wm-val">{{ nf('humidity') }}%</span></div>
          <div class="wt-metric"><span class="wm-label">{{ $t('weatherPage.wind') }}</span><span class="wm-val">{{ nf('windDir') }} {{ nf('windScale') }}{{ $t('weatherPage.level') }}</span></div>
          <div class="wt-metric"><span class="wm-label">{{ $t('weatherPage.windSpeed') }}</span><span class="wm-val">{{ nf('windSpeed') }} km/h</span></div>
          <div class="wt-metric"><span class="wm-label">{{ $t('weatherPage.precip') }}</span><span class="wm-val">{{ nf('precip') }} mm</span></div>
          <div class="wt-metric"><span class="wm-label">{{ $t('weatherPage.pressure') }}</span><span class="wm-val">{{ nf('pressure') }} hPa</span></div>
          <div class="wt-metric"><span class="wm-label">{{ $t('weatherPage.vis') }}</span><span class="wm-val">{{ nf('vis') }} km</span></div>
          <div class="wt-metric"><span class="wm-label">{{ $t('weatherPage.cloud') }}</span><span class="wm-val">{{ nf('cloud') }}%</span></div>
          <div class="wt-metric"><span class="wm-label">{{ $t('weatherPage.dew') }}</span><span class="wm-val">{{ nf('dew') }}°</span></div>
        </div>
      </div>

      <!-- 气象预警 -->
      <div v-if="warnings.length" class="card wt-warnings-card">
        <div class="section-label">{{ $t('weatherPage.warnings') }}</div>
        <div v-for="w in warnings" :key="w.id" class="wt-warning" :style="{ borderLeftColor: warnLevelColor(w.level) }">
          <div class="wt-warn-head">
            <span class="wt-warn-title" :style="{ color: warnLevelColor(w.level) }">{{ w.typeName }} {{ w.level }}{{ $t('weatherPage.warningSuffix') }}</span>
            <span v-if="w.senderName" class="wt-warn-sender">{{ w.senderName }}</span>
            <span class="wt-warn-time">{{ fmtDateTime(w.startTime) }} ~ {{ fmtDateTime(w.endTime) }}</span>
          </div>
          <div class="wt-warn-text">{{ w.text }}</div>
          <div v-if="w.instruction" class="wt-warn-instruction">{{ $t('weatherPage.defenseGuide') }}：{{ w.instruction }}</div>
        </div>
      </div>

      <!-- 24 小时预报(卡片 ↔ 折线图) -->
      <div v-if="detail?.hourly?.length" class="card">
        <div class="section-label wt-section-head">
          <span>{{ $t('weatherPage.hourly') }}</span>
          <div class="view-toggle">
            <button class="vt-btn" :class="{ on: hourlyView === 'chart' }" @click="setHourlyView('chart')">{{ $t('weatherPage.chartView') }}</button>
            <button class="vt-btn" :class="{ on: hourlyView === 'cards' }" @click="setHourlyView('cards')">{{ $t('weatherPage.cardsView') }}</button>
          </div>
        </div>
        <!-- 卡片模式 -->
        <div v-if="hourlyView === 'cards'" class="wt-hourly">
          <div v-for="(h, i) in detail.hourly" :key="i" class="wt-hour">
            <span class="wh-time">{{ fmtHour(h.fxTime) }}</span>
            <i :class="'qi-' + h.icon" class="wh-icon"></i>
            <span class="wh-temp">{{ h.temp }}°</span>
            <span class="wh-text">{{ h.text }}</span>
            <span v-if="h.pop && Number(h.pop) > 0" class="wh-pop">💧 {{ h.pop }}%</span>
            <span class="wh-wind">{{ h.windDir }} {{ h.windScale }}{{ $t('weatherPage.level') }}</span>
          </div>
        </div>
        <!-- 折线图模式 -->
        <div v-else class="wt-chart-wrap" @mouseleave="hoverIdx = -1">
          <svg v-if="chartData" :viewBox="`0 0 ${CHART_W} ${CHART_H}`" class="wt-chart">
            <defs>
              <linearGradient id="wtLineGrad" x1="0" y1="0" x2="1" y2="0">
                <stop offset="0%" :stop-color="tempColor(chartData.tMin + 1)" />
                <stop offset="50%" :stop-color="tempColor((chartData.tMin + chartData.tMax) / 2)" />
                <stop offset="100%" :stop-color="tempColor(chartData.tMax - 1)" />
              </linearGradient>
            </defs>
            <path :d="areaPath" fill="url(#wtLineGrad)" opacity="0.10" />
            <path :d="linePath" fill="none" stroke="url(#wtLineGrad)" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" />
            <template v-for="(lb, i) in extremeLabels" :key="'el' + i">
              <text :x="lb.x" :y="lb.y" class="wc-extreme" :fill="lb.fill">{{ lb.label }}</text>
            </template>
            <template v-for="(lb, i) in timeLabels" :key="'xl' + i">
              <text :x="lb.x" :y="CHART_H - 10" class="wc-xlabel">{{ lb.label }}</text>
            </template>
            <g v-if="hoverIdx >= 0 && chartData">
              <line :x1="chartData.pts[hoverIdx].x" :y1="32" :x2="chartData.pts[hoverIdx].x" :y2="CHART_H - 36" class="wc-hover-line" />
              <circle :cx="chartData.pts[hoverIdx].x" :cy="chartData.pts[hoverIdx].y" r="5" class="wc-hover-dot" />
            </g>
            <rect v-for="(p, i) in (chartData?.pts || [])" :key="'hr' + i"
              :x="p.x - hoverStep / 2" y="0" :width="hoverStep" :height="CHART_H"
              fill="transparent" @mouseenter="hoverIdx = i" />
          </svg>
          <div v-if="hoverIdx >= 0 && chartData" class="wc-tooltip" :style="tooltipStyle">
            <div class="wct-time">{{ fmtHour(chartData.pts[hoverIdx].h.fxTime) }}</div>
            <div class="wct-main"><i :class="'qi-' + chartData.pts[hoverIdx].h.icon" style="font-size:16px"></i> {{ chartData.pts[hoverIdx].t }}° {{ chartData.pts[hoverIdx].h.text }}</div>
            <div v-if="Number(chartData.pts[hoverIdx].h.pop) > 0" class="wct-sub">💧 {{ chartData.pts[hoverIdx].h.pop }}%</div>
            <div class="wct-sub">{{ chartData.pts[hoverIdx].h.windDir }} {{ chartData.pts[hoverIdx].h.windScale }}{{ $t('weatherPage.level') }}</div>
          </div>
        </div>
      </div>

      <!-- N 天预报(温度区间渐变横条) -->
      <div v-if="detail?.daily?.length" class="card">
        <div class="section-label">{{ $t('weatherPage.daily') }}</div>
        <el-table :data="detail.daily" size="small" stripe>
          <el-table-column :label="$t('weatherPage.date')" width="110">
            <template #default="{ row }">{{ fmtDate(row.fxDate) }}</template>
          </el-table-column>
          <el-table-column :label="$t('weatherPage.dayWeather')" min-width="140">
            <template #default="{ row }"><i :class="'qi-' + row.iconDay" class="wt-td-icon"></i> {{ row.textDay }}</template>
          </el-table-column>
          <el-table-column :label="$t('weatherPage.nightWeather')" min-width="140">
            <template #default="{ row }"><i :class="'qi-' + row.iconNight" class="wt-td-icon"></i> {{ row.textNight }}</template>
          </el-table-column>
          <el-table-column :label="$t('weatherPage.tempRange')" min-width="220">
            <template #default="{ row }">
              <div class="wt-trange">
                <span class="wt-lo">{{ row.tempMin }}°</span>
                <div class="wt-tbar-track">
                  <div class="wt-tbar-fill" :style="tbarStyle(row)"></div>
                  <div class="wt-tbar-dot" :style="{ left: tbarDotLeft(row) }"></div>
                </div>
                <span class="wt-hi">{{ row.tempMax }}°</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column :label="$t('weatherPage.uv')" width="70">
            <template #default="{ row }">{{ row.uvIndex }}</template>
          </el-table-column>
          <el-table-column :label="$t('weatherPage.wind')" width="120">
            <template #default="{ row }">{{ row.windDirDay }} {{ row.windScaleDay }}{{ $t('weatherPage.level') }}</template>
          </el-table-column>
          <el-table-column :label="$t('weatherPage.sunSunset')" width="150">
            <template #default="{ row }">☀ {{ (row.sunrise || '').slice(11, 16) }} / 🌙 {{ (row.sunset || '').slice(11, 16) }}</template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 空气质量 -->
      <div v-if="detail?.air" class="card">
        <div class="section-label">{{ $t('weatherPage.air') }}</div>
        <div class="wt-air">
          <div class="wt-air-main">
            <span class="wa-aqi">{{ detail.air.aqi }}</span>
            <span class="wa-cat">{{ detail.air.category }}</span>
            <span v-if="detail.air.primary" class="wa-primary">{{ $t('weatherPage.primaryPollutant') }}: {{ detail.air.primary }}</span>
          </div>
          <div class="wt-air-items">
            <span class="wa-item">PM2.5 <b>{{ detail.air.pm2p5 }}</b></span>
            <span class="wa-item">PM10 <b>{{ detail.air.pm10 }}</b></span>
            <span class="wa-item">NO₂ <b>{{ detail.air.no2 }}</b></span>
            <span class="wa-item">SO₂ <b>{{ detail.air.so2 }}</b></span>
            <span class="wa-item">O₃ <b>{{ detail.air.o3 }}</b></span>
            <span class="wa-item">CO <b>{{ detail.air.co }}</b></span>
          </div>
          <div v-if="detail.air.adviceGeneral || detail.air.adviceSensitive" class="wt-air-health">
            <div v-if="detail.air.adviceGeneral"><span class="wah-label">{{ $t('weatherPage.healthAdvice') }}</span>{{ detail.air.adviceGeneral }}</div>
            <div v-if="detail.air.adviceSensitive"><span class="wah-label">{{ $t('weatherPage.sensitiveAdvice') }}</span>{{ detail.air.adviceSensitive }}</div>
          </div>
        </div>
      </div>

      <!-- 生活指数 -->
      <div v-if="detail?.indices?.length" class="card">
        <div class="section-label">{{ $t('weatherPage.indices') }}</div>
        <div class="wt-indices">
          <div v-for="(ix, i) in detail.indices" :key="i" class="wt-index">
            <div class="wi-head"><span class="wi-name">{{ ix.name }}</span><span class="wi-cat">{{ ix.category }}</span></div>
            <div class="wi-text">{{ ix.text }}</div>
          </div>
        </div>
      </div>

      <!-- 分钟降水 -->
      <div v-if="minutelySummary" class="card">
        <div class="section-label">{{ $t('weatherPage.minutely') }}</div>
        <div class="wt-minutely">{{ minutelySummary }}</div>
      </div>

      <el-empty v-if="!loading && !detail" :description="$t('weatherPage.empty')" />
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, inject } from 'vue'
import { useI18n } from 'vue-i18n'
import Breadcrumb from '@/components/Breadcrumb.vue'
import { warnLevelColor } from '@/utils/dict'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'

const { t } = useI18n()
const sunLight = inject(SUN_LIGHT_KEY, null)

const loading = ref(false)
const detail = ref(null)
const warnings = computed(() => detail.value?.warning || [])
const todayHigh = computed(() => detail.value?.daily?.[0]?.tempMax ?? '—')
const todayLow = computed(() => detail.value?.daily?.[0]?.tempMin ?? '—')
const minutelySummary = computed(() => detail.value?.minutely?.summary || '')
const todayLabel = computed(() => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
})
const nf = (key) => detail.value?.nowFull?.[key] ?? '—'
const fmtHour = (s) => (s || '').slice(11, 16)
const fmtDate = (s) => {
  if (!s) return ''
  const d = new Date(s)
  const weekdays = [t('weatherPage.w0'), t('weatherPage.w1'), t('weatherPage.w2'), t('weatherPage.w3'), t('weatherPage.w4'), t('weatherPage.w5'), t('weatherPage.w6')]
  return `${d.getMonth() + 1}/${d.getDate()} ${weekdays[d.getDay()]}`
}
const fmtDateTime = (s) => (s || '').replace('T', ' ').slice(5, 16)

// ── 24h 视图切换(卡片/折线,localStorage 记忆,默认折线) ──
const hourlyView = ref(localStorage.getItem('ihomy:hourly-view') || 'chart')
const setHourlyView = (v) => { hourlyView.value = v; localStorage.setItem('ihomy:hourly-view', v) }

// ── 温度 → 颜色(7 段渐变,-20°冷蓝→42°暖红) ──
const TEMP_STOPS = [[-20, 74, 111, 212], [-5, 95, 159, 224], [5, 98, 184, 201], [15, 143, 201, 143], [22, 224, 201, 95], [28, 224, 155, 95], [34, 224, 120, 69], [42, 216, 74, 58]]
const tempColor = (t) => {
  const s = TEMP_STOPS
  if (t <= s[0][0]) return `rgb(${s[0][1]},${s[0][2]},${s[0][3]})`
  for (let i = 0; i < s.length - 1; i++) {
    if (t <= s[i + 1][0]) {
      const f = (t - s[i][0]) / (s[i + 1][0] - s[i][0])
      return `rgb(${Math.round(s[i][1] + (s[i + 1][1] - s[i][1]) * f)},${Math.round(s[i][2] + (s[i + 1][2] - s[i][2]) * f)},${Math.round(s[i][3] + (s[i + 1][3] - s[i][3]) * f)})`
    }
  }
  const l = s[s.length - 1]
  return `rgb(${l[1]},${l[2]},${l[3]})`
}

// ── 折线图几何 ──
const CHART_W = 720, CHART_H = 230, PT = 34, PB = 40, PL = 14, PR = 14
const hoverIdx = ref(-1)

const chartData = computed(() => {
  const hs = detail.value?.hourly || []
  if (!hs.length) return null
  const temps = hs.map(h => Number(h.temp) || 0)
  const tMin = Math.min(...temps) - 1, tMax = Math.max(...temps) + 1
  const n = hs.length
  const xOf = (i) => PL + (i / (n - 1)) * (CHART_W - PL - PR)
  const yOf = (t) => PB + (1 - (t - tMin) / (tMax - tMin || 1)) * (CHART_H - PB - PT)
  const pts = hs.map((h, i) => ({ x: xOf(i), y: yOf(Number(h.temp) || 0), t: Number(h.temp) || 0, h }))
  return { pts, tMin, tMax, temps }
})

const hoverStep = computed(() => {
  const n = chartData.value?.pts.length || 1
  return (CHART_W - PL - PR) / (n - 1)
})

// Catmull-Rom → 平滑 Bezier
const catmullRom = (pts) => {
  if (pts.length < 2) return ''
  const tension = 0.35
  let d = `M${pts[0].x},${pts[0].y}`
  for (let i = 0; i < pts.length - 1; i++) {
    const p0 = pts[Math.max(i - 1, 0)]
    const p1 = pts[i]
    const p2 = pts[i + 1]
    const p3 = pts[Math.min(i + 2, pts.length - 1)]
    const cp1x = p1.x + (p2.x - p0.x) * tension
    const cp1y = p1.y + (p2.y - p0.y) * tension
    const cp2x = p2.x - (p3.x - p1.x) * tension
    const cp2y = p2.y - (p3.y - p1.y) * tension
    d += `C${cp1x},${cp1y} ${cp2x},${cp2y} ${p2.x},${p2.y}`
  }
  return d
}
const linePath = computed(() => chartData.value ? catmullRom(chartData.value.pts) : '')
const areaPath = computed(() => {
  if (!chartData.value) return ''
  const { pts } = chartData.value
  return catmullRom(pts) + `L${pts[pts.length - 1].x},${CHART_H - PB}L${pts[0].x},${CHART_H - PB}Z`
})

// 最高/最低标注
const extremeLabels = computed(() => {
  if (!chartData.value) return []
  const { pts } = chartData.value
  const maxI = pts.reduce((m, p, i) => (p.t > pts[m].t ? i : m), 0)
  const minI = pts.reduce((m, p, i) => (p.t < pts[m].t ? i : m), 0)
  const out = [{ x: pts[maxI].x, y: pts[maxI].y - 10, label: pts[maxI].t + '°', fill: '#c07a4a' }]
  if (minI !== maxI) out.push({ x: pts[minI].x, y: pts[minI].y + 16, label: pts[minI].t + '°', fill: '#6a8ab0' })
  return out
})

// 底部时间标签(每 3 小时)
const timeLabels = computed(() => {
  if (!chartData.value) return []
  return chartData.value.pts.filter((_, i) => i % 3 === 0).map(p => ({ x: p.x, label: fmtHour(p.h.fxTime) }))
})

// 悬浮 tooltip 定位
const tooltipStyle = computed(() => {
  if (hoverIdx.value < 0 || !chartData.value) return { display: 'none' }
  const p = chartData.value.pts[hoverIdx.value]
  const left = Math.min(Math.max(p.x / CHART_W, 0.08), 0.92) * 100
  const top = p.y / CHART_H * 100
  return { left: left + '%', top: top + '%' }
})

// ── N 天温度区间渐变横条 ──
const dailyRange = computed(() => {
  const d = detail.value?.daily || []
  if (!d.length) return null
  const min = Math.min(...d.map(x => Number(x.tempMin)))
  const max = Math.max(...d.map(x => Number(x.tempMax)))
  return { min, max, range: max - min || 1 }
})
const tbarStyle = (row) => {
  const r = dailyRange.value
  if (!r) return { background: '#ccc' }
  const lo = Number(row.tempMin), hi = Number(row.tempMax)
  return {
    left: ((lo - r.min) / r.range * 100) + '%',
    width: (Math.max(hi - lo, 2) / r.range * 100) + '%',
    background: `linear-gradient(90deg, ${tempColor(lo)}, ${tempColor(hi)})`
  }
}
const tbarDotLeft = (row) => {
  const r = dailyRange.value
  if (!r) return '50%'
  const avg = (Number(row.tempMin) + Number(row.tempMax)) / 2
  return ((avg - r.min) / r.range * 100) + '%'
}

const load = async () => {
  if (sunLight?.weatherDetail?.value) detail.value = sunLight.weatherDetail.value
  loading.value = true
  try {
    const res = await fetch('/api/public/weather/detail')
    if (res.ok) {
      const json = await res.json()
      if (json.code === 0 && json.data) {
        detail.value = json.data
        if (sunLight?.weatherDetail) sunLight.weatherDetail.value = json.data
      }
    }
  } catch (e) {} finally {
    loading.value = false
  }
}
onMounted(load)
</script>

<style scoped>
.weather-page-body { display: flex; flex-direction: column; gap: 14px; }
.wt-hero { padding: 20px; }
.wt-city { font-size: 14px; color: var(--color-text-secondary); }
.wt-main { display: flex; align-items: center; gap: 10px; margin: 8px 0 4px; flex-wrap: wrap; }
.wt-big-icon { font-size: 56px; line-height: 1; }
.wt-temp { font-size: 56px; font-weight: 700; line-height: 1; font-variant-numeric: tabular-nums; }
.wt-degree { font-size: 28px; opacity: 0.5; }
.wt-hilo { display: flex; flex-direction: column; gap: 2px; }
.wt-hi { color: #c07a4a; font-weight: 600; font-size: 14px; }
.wt-lo { color: #6a8ab0; font-weight: 600; font-size: 14px; }
.wt-text { font-size: 16px; color: var(--color-text-secondary); }
.wt-metrics { display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: 10px; margin-top: 16px; }
.wt-metric { background: var(--color-card-2); border-radius: 10px; padding: 10px 14px; display: flex; flex-direction: column; gap: 4px; }
.wm-label { font-size: 12px; color: var(--color-text-secondary); }
.wm-val { font-size: 15px; font-weight: 600; font-variant-numeric: tabular-nums; }
.wt-warnings-card { padding: 16px 20px; }
.wt-warning { border-left: 3px solid; border-radius: 8px; background: var(--color-card-2); padding: 10px 14px; margin-top: 10px; }
.wt-warn-head { display: flex; align-items: baseline; gap: 10px; flex-wrap: wrap; }
.wt-warn-title { font-weight: 700; font-size: 14px; }
.wt-warn-sender { font-size: 12px; color: var(--color-text-secondary); }
.wt-warn-time { font-size: 12px; color: var(--color-text-secondary); }
.wt-warn-text { font-size: 13px; line-height: 1.6; margin-top: 6px; color: var(--color-text); }
.wt-warn-instruction { font-size: 12px; line-height: 1.6; margin-top: 6px; padding: 8px 10px; background: rgba(184,140,110,0.08); border-radius: 8px; color: var(--color-text-secondary); white-space: pre-line; }
.wt-section-head { display: flex; align-items: center; justify-content: space-between; }
/* 24h 卡片 */
.wt-hourly { display: flex; gap: 8px; overflow-x: auto; padding-bottom: 6px; }
.wt-hour { display: flex; flex-direction: column; align-items: center; gap: 4px; background: var(--color-card-2); border-radius: 10px; padding: 10px 8px; min-width: 76px; flex: none; }
.wh-time { font-size: 12px; color: var(--color-text-secondary); font-variant-numeric: tabular-nums; }
.wh-icon { font-size: 26px; }
.wh-temp { font-size: 15px; font-weight: 700; }
.wh-text { font-size: 11px; color: var(--color-text-secondary); }
.wh-pop { font-size: 11px; color: #6a8ab0; }
.wh-wind { font-size: 11px; color: var(--color-text-secondary); }
/* 24h 折线图 */
.wt-chart-wrap { position: relative; padding: 4px 0 0; }
.wt-chart { width: 100%; height: auto; display: block; }
.wc-xlabel { font-size: 10px; fill: var(--color-text-secondary); text-anchor: middle; font-variant-numeric: tabular-nums; }
.wc-extreme { font-size: 11px; font-weight: 700; text-anchor: middle; }
.wc-hover-line { stroke: var(--color-text-secondary); stroke-dasharray: 3 3; opacity: 0.4; }
.wc-hover-dot { fill: #b88c6e; stroke: #fffdf8; stroke-width: 2; }
.wc-tooltip { position: absolute; transform: translate(-50%, -120%); background: var(--color-card-2); border: 1px solid var(--color-border); border-radius: 10px; padding: 8px 12px; font-size: 12px; pointer-events: none; box-shadow: 0 3px 12px rgba(0,0,0,0.08); white-space: nowrap; z-index: 5; }
.wct-time { color: var(--color-text-secondary); font-size: 11px; }
.wct-main { font-weight: 700; font-size: 13px; display: flex; align-items: center; gap: 4px; }
.wct-sub { color: var(--color-text-secondary); font-size: 11px; }
/* N 天温度横条 */
.wt-trange { display: flex; align-items: center; gap: 8px; }
.wt-tbar-track { flex: 1; height: 8px; background: rgba(58,46,34,0.10); border-radius: 4px; position: relative; min-width: 90px; }
.wt-tbar-fill { position: absolute; top: 0; height: 100%; border-radius: 4px; }
.wt-tbar-dot { position: absolute; top: 50%; width: 5px; height: 5px; border-radius: 50%; background: rgba(58,46,34,0.55); transform: translate(-50%, -50%); }
.wt-td-icon { font-size: 18px; }
/* 空气健康建议 */
.wt-air { display: flex; flex-direction: column; gap: 12px; }
.wt-air-main { display: flex; align-items: baseline; gap: 12px; }
.wa-aqi { font-size: 34px; font-weight: 700; line-height: 1; }
.wa-cat { font-size: 15px; font-weight: 600; }
.wa-primary { font-size: 12px; color: var(--color-text-secondary); }
.wt-air-items { display: flex; gap: 10px; flex-wrap: wrap; }
.wa-item { background: var(--color-card-2); border-radius: 8px; padding: 6px 12px; font-size: 12px; color: var(--color-text-secondary); }
.wa-item b { color: var(--color-text); margin-left: 4px; font-variant-numeric: tabular-nums; }
.wt-air-health { font-size: 12px; line-height: 1.7; color: var(--color-text-secondary); background: rgba(107,155,107,0.08); border-radius: 8px; padding: 10px 12px; }
.wah-label { font-weight: 600; color: var(--color-text); margin-right: 6px; }
.wt-indices { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 10px; }
.wt-index { background: var(--color-card-2); border-radius: 10px; padding: 12px 14px; }
.wi-head { display: flex; align-items: baseline; justify-content: space-between; margin-bottom: 6px; }
.wi-name { font-size: 13px; font-weight: 600; }
.wi-cat { font-size: 12px; color: #b88c6e; font-weight: 600; }
.wi-text { font-size: 12px; line-height: 1.6; color: var(--color-text-secondary); }
.wt-minutely { font-size: 13px; line-height: 1.6; color: var(--color-text); }
/* 深色模式横条轨道 */
:global(html.dark) .wt-tbar-track { background: rgba(232,220,200,0.12); }
:global(html.dark) .wt-tbar-dot { background: rgba(232,220,200,0.55); }
:global(html.dark) .wc-hover-dot { stroke: #1E2A48; }
@media (max-width: 768px) {
  .wt-metrics { grid-template-columns: repeat(2, 1fr); }
  .wt-big-icon { font-size: 44px; }
  .wt-temp { font-size: 44px; }
}
</style>
