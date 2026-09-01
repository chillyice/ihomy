<!-- 天气详情页(V9.10):展示当天当地全部可查询的天气数据。
     数据源 GET /api/public/weather/detail(now 全量实况/24h/7d/预警/空气/生活指数/分钟降水),
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
            <span class="wt-warn-time">{{ fmtDateTime(w.startTime) }} ~ {{ fmtDateTime(w.endTime) }}</span>
          </div>
          <div class="wt-warn-text">{{ w.text }}</div>
        </div>
      </div>

      <!-- 24 小时预报 -->
      <div v-if="detail?.hourly?.length" class="card">
        <div class="section-label">{{ $t('weatherPage.hourly') }}</div>
        <div class="wt-hourly">
          <div v-for="(h, i) in detail.hourly" :key="i" class="wt-hour">
            <span class="wh-time">{{ fmtHour(h.fxTime) }}</span>
            <i :class="'qi-' + h.icon" class="wh-icon"></i>
            <span class="wh-temp">{{ h.temp }}°</span>
            <span class="wh-text">{{ h.text }}</span>
            <span v-if="h.pop && Number(h.pop) > 0" class="wh-pop">💧 {{ h.pop }}%</span>
            <span class="wh-wind">{{ h.windDir }} {{ h.windScale }}{{ $t('weatherPage.level') }}</span>
          </div>
        </div>
      </div>

      <!-- 7 天预报 -->
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
          <el-table-column :label="$t('weatherPage.tempRange')" width="110">
            <template #default="{ row }"><span class="wt-lo">{{ row.tempMin }}°</span> ~ <span class="wt-hi">{{ row.tempMax }}°</span></template>
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
// 光影层已拉过详情则直接复用,避免重复请求
const warnings = computed(() => detail.value?.warning || [])
const todayHigh = computed(() => detail.value?.daily?.[0]?.tempMax ?? '—')
const todayLow = computed(() => detail.value?.daily?.[0]?.tempMin ?? '—')
const minutelySummary = computed(() => detail.value?.minutely?.summary || '')
const todayLabel = computed(() => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
})
/** 实况字段(缺省回退 —) */
const nf = (key) => detail.value?.nowFull?.[key] ?? '—'

const fmtHour = (s) => (s || '').slice(11, 16)
const fmtDate = (s) => {
  if (!s) return ''
  const d = new Date(s)
  const weekdays = [t('weatherPage.w0'), t('weatherPage.w1'), t('weatherPage.w2'), t('weatherPage.w3'), t('weatherPage.w4'), t('weatherPage.w5'), t('weatherPage.w6')]
  return `${d.getMonth() + 1}/${d.getDate()} ${weekdays[d.getDay()]}`
}
const fmtDateTime = (s) => (s || '').replace('T', ' ').slice(5, 16)

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
.wt-warn-head { display: flex; align-items: baseline; gap: 12px; flex-wrap: wrap; }
.wt-warn-title { font-weight: 700; font-size: 14px; }
.wt-warn-time { font-size: 12px; color: var(--color-text-secondary); }
.wt-warn-text { font-size: 13px; line-height: 1.6; margin-top: 6px; color: var(--color-text); }
.wt-hourly { display: flex; gap: 8px; overflow-x: auto; padding-bottom: 6px; }
.wt-hour { display: flex; flex-direction: column; align-items: center; gap: 4px; background: var(--color-card-2); border-radius: 10px; padding: 10px 8px; min-width: 76px; flex: none; }
.wh-time { font-size: 12px; color: var(--color-text-secondary); font-variant-numeric: tabular-nums; }
.wh-icon { font-size: 26px; }
.wh-temp { font-size: 15px; font-weight: 700; }
.wh-text { font-size: 11px; color: var(--color-text-secondary); }
.wh-pop { font-size: 11px; color: #6a8ab0; }
.wh-wind { font-size: 11px; color: var(--color-text-secondary); }
.wt-td-icon { font-size: 18px; }
.wt-air { display: flex; flex-direction: column; gap: 12px; }
.wt-air-main { display: flex; align-items: baseline; gap: 12px; }
.wa-aqi { font-size: 34px; font-weight: 700; line-height: 1; }
.wa-cat { font-size: 15px; font-weight: 600; }
.wa-primary { font-size: 12px; color: var(--color-text-secondary); }
.wt-air-items { display: flex; gap: 10px; flex-wrap: wrap; }
.wa-item { background: var(--color-card-2); border-radius: 8px; padding: 6px 12px; font-size: 12px; color: var(--color-text-secondary); }
.wa-item b { color: var(--color-text); margin-left: 4px; font-variant-numeric: tabular-nums; }
.wt-indices { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 10px; }
.wt-index { background: var(--color-card-2); border-radius: 10px; padding: 12px 14px; }
.wi-head { display: flex; align-items: baseline; justify-content: space-between; margin-bottom: 6px; }
.wi-name { font-size: 13px; font-weight: 600; }
.wi-cat { font-size: 12px; color: #b88c6e; font-weight: 600; }
.wi-text { font-size: 12px; line-height: 1.6; color: var(--color-text-secondary); }
.wt-minutely { font-size: 13px; line-height: 1.6; color: var(--color-text); }
@media (max-width: 768px) {
  .wt-metrics { grid-template-columns: repeat(2, 1fr); }
  .wt-big-icon { font-size: 44px; }
  .wt-temp { font-size: 44px; }
}
</style>
