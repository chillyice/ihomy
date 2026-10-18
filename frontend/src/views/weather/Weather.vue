<!-- 天气详情页:展示当天当地全部天气数据。
     数据源 GET /api/public/weather/detail(实况/24h/72h/10d/预警/空气/生活指数/分钟降水,后端缓存 30 分钟)
     + GET /api/public/sun-info(日月与晨昏,含晨昏地图用的太阳直射点,纯天文计算不依赖天气 API)。
     信息层级:实况(含预警) → 未来(逐小时 + 10 天)→ 空气与天空(空气 / 晨昏地图 / 生活指数)。 -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('weatherPage.title') }]" />
    <div v-loading="loading" class="weather-page-body">
      <!-- ── L1 实况(预警右起排在实况右侧) ── -->
      <div class="wt-zone">
        <div class="wt-zone-label">{{ $t('weatherPage.zone.now') }}</div>
        <div class="card wt-hero">
          <div class="wt-hero-top">
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
            <div v-if="sortedWarnings.length" ref="warnRowEl" class="wt-warnings">
              <button
                v-for="w in sortedWarnings"
                :key="w.id"
                class="wt-warn-chip"
                type="button"
                :style="{ borderLeftColor: warnLevelColor(w.level), background: warnTint(w.level) }"
                @click="openWarning(w)"
              >
                <span class="wt-warn-title" :style="{ color: warnLevelColor(w.level) }">
                  {{ w.typeName }} {{ w.level }}{{ $t('weatherPage.warningSuffix') }}
                </span>
                <span class="wt-warn-time">{{ fmtDateTime(w.startTime) }} ~ {{ fmtDateTime(w.endTime) }}</span>
                <span class="wt-warn-text">{{ w.text }}</span>
              </button>
            </div>
          </div>
          <div class="wt-metrics">
            <div class="wt-metric">
              <span class="wm-label">{{ $t('weatherPage.feelsLike') }}</span
              ><span class="wm-val">{{ nf('feelsLike') }}°</span>
            </div>
            <div class="wt-metric">
              <span class="wm-label">{{ $t('weatherPage.humidity') }}</span
              ><span class="wm-val">{{ nf('humidity') }}%</span>
            </div>
            <div class="wt-metric">
              <span class="wm-label">{{ $t('weatherPage.wind') }}</span
              ><span class="wm-val">{{ nf('windDir') }} {{ nf('windScale') }}{{ $t('weatherPage.level') }}</span>
            </div>
            <div class="wt-metric">
              <span class="wm-label">{{ $t('weatherPage.windSpeed') }}</span
              ><span class="wm-val">{{ nf('windSpeed') }} km/h</span>
            </div>
            <div class="wt-metric">
              <span class="wm-label">{{ $t('weatherPage.precip') }}</span
              ><span class="wm-val">{{ nf('precip') }} mm</span>
            </div>
            <div class="wt-metric">
              <span class="wm-label">{{ $t('weatherPage.pressure') }}</span
              ><span class="wm-val">{{ nf('pressure') }} hPa</span>
            </div>
            <div class="wt-metric">
              <span class="wm-label">{{ $t('weatherPage.vis') }}</span
              ><span class="wm-val">{{ nf('vis') }} km</span>
            </div>
            <div class="wt-metric">
              <span class="wm-label">{{ $t('weatherPage.cloud') }}</span
              ><span class="wm-val">{{ nf('cloud') }}%</span>
            </div>
            <div class="wt-metric">
              <span class="wm-label">{{ $t('weatherPage.dew') }}</span
              ><span class="wm-val">{{ nf('dew') }}°</span>
            </div>
          </div>
        </div>
      </div>

      <!-- ── L2 未来 ── -->
      <div class="wt-zone">
        <div class="wt-zone-label">{{ $t('weatherPage.zone.next') }}</div>

        <!-- 逐小时预报(24h/72h × 折线/卡片) -->
        <div v-if="hourPoints.length" class="card">
          <div class="section-label wt-section-head">
            <span>{{ $t('weatherPage.hourly') }}</span>
            <div class="wt-toggles">
              <div class="view-toggle">
                <button class="vt-btn wt-vt-text" :class="{ active: effRange === '24h' }" type="button" @click="setHourlyRange('24h')">
                  {{ $t('weatherPage.range24') }}
                </button>
                <button class="vt-btn wt-vt-text" :class="{ active: effRange === '72h' }" type="button" @click="setHourlyRange('72h')">
                  {{ $t('weatherPage.range72') }}
                </button>
              </div>
              <div class="view-toggle">
                <button
                  class="vt-btn"
                  :class="{ active: hourlyView === 'chart' }"
                  type="button"
                  :title="$t('weatherPage.chartView')"
                  :aria-label="$t('weatherPage.chartView')"
                  @click="setHourlyView('chart')"
                >
                  <svg viewBox="0 0 20 20" width="15" height="15" aria-hidden="true">
                    <path
                      d="M2 14.5l4.2-6.5 3.6 3L14.4 4 18 8.5"
                      fill="none"
                      stroke="currentColor"
                      stroke-width="1.8"
                      stroke-linecap="round"
                      stroke-linejoin="round"
                    />
                  </svg>
                </button>
                <button
                  class="vt-btn"
                  :class="{ active: hourlyView === 'cards' }"
                  type="button"
                  :title="$t('weatherPage.cardsView')"
                  :aria-label="$t('weatherPage.cardsView')"
                  @click="setHourlyView('cards')"
                >
                  <svg viewBox="0 0 20 20" width="15" height="15" aria-hidden="true">
                    <rect x="2.6" y="2.6" width="6.2" height="5.6" rx="1.3" fill="none" stroke="currentColor" stroke-width="1.6" />
                    <rect x="11.2" y="2.6" width="6.2" height="5.6" rx="1.3" fill="none" stroke="currentColor" stroke-width="1.6" />
                    <rect x="2.6" y="11.8" width="6.2" height="5.6" rx="1.3" fill="none" stroke="currentColor" stroke-width="1.6" />
                    <rect x="11.2" y="11.8" width="6.2" height="5.6" rx="1.3" fill="none" stroke="currentColor" stroke-width="1.6" />
                  </svg>
                </button>
              </div>
            </div>
          </div>

          <!-- 分钟降水一句话:最高频的决策信息,并入逐小时卡顶部 -->
          <div v-if="minutelySummary" class="wt-minutely-banner">💧 {{ minutelySummary }}</div>

          <!-- 卡片模式(72 小时按天插入日期分隔) -->
          <div v-if="hourlyView === 'cards'" class="wt-hourly">
            <template v-for="(h, i) in hourPoints" :key="i">
              <div v-if="effRange === '72h' && isDayStart(i)" class="wt-hour-sep">{{ fmtMD(h.fxTime) }}</div>
              <div class="wt-hour">
                <span class="wh-time">{{ fmtHour(h.fxTime) }}</span>
                <i :class="'qi-' + h.icon" class="wh-icon"></i>
                <span class="wh-temp">{{ h.temp }}°</span>
                <span class="wh-text">{{ h.text }}</span>
                <span v-if="h.pop && Number(h.pop) > 0" class="wh-pop">💧 {{ h.pop }}%</span>
                <span class="wh-wind">{{ h.windDir }} {{ h.windScale }}{{ $t('weatherPage.level') }}</span>
              </div>
            </template>
          </div>
          <!-- 折线图模式(72 小时点数多,固定点位宽度后横向滚动) -->
          <div v-else class="wt-chart-scroll" @mouseleave="hoverIdx = -1">
            <div class="wt-chart-inner" :style="{ width: chartW + 'px' }">
              <svg v-if="chartData" :viewBox="`0 0 ${chartW} ${CHART_H}`" class="wt-chart">
                <defs>
                  <linearGradient id="wtLineGrad" x1="0" y1="0" x2="1" y2="0">
                    <stop offset="0%" :stop-color="tempColor(chartData.tMin + 1)" />
                    <stop offset="50%" :stop-color="tempColor((chartData.tMin + chartData.tMax) / 2)" />
                    <stop offset="100%" :stop-color="tempColor(chartData.tMax - 1)" />
                  </linearGradient>
                </defs>
                <path :d="areaPath" fill="url(#wtLineGrad)" opacity="0.10" />
                <path
                  :d="linePath"
                  fill="none"
                  stroke="url(#wtLineGrad)"
                  stroke-width="2.5"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
                <template v-for="(d, i) in dayMarks" :key="'dm' + i">
                  <line :x1="d.x" :y1="PT - 8" :x2="d.x" :y2="CHART_H - PB" class="wc-day-sep" />
                  <text :x="d.x + 5" :y="PT - 14" class="wc-daylabel">{{ d.label }}</text>
                </template>
                <template v-for="(lb, i) in extremeLabels" :key="'el' + i">
                  <text :x="lb.x" :y="lb.y" class="wc-extreme" :fill="lb.fill">{{ lb.label }}</text>
                </template>
                <template v-for="(lb, i) in timeLabels" :key="'xl' + i">
                  <text :x="lb.x" :y="CHART_H - 10" class="wc-xlabel">{{ lb.label }}</text>
                </template>
                <g v-if="hoverIdx >= 0 && chartData">
                  <line :x1="chartData.pts[hoverIdx].x" y1="32" :x2="chartData.pts[hoverIdx].x" :y2="CHART_H - 36" class="wc-hover-line" />
                  <circle :cx="chartData.pts[hoverIdx].x" :cy="chartData.pts[hoverIdx].y" r="5" class="wc-hover-dot" />
                </g>
                <rect
                  v-for="(p, i) in chartData?.pts || []"
                  :key="'hr' + i"
                  :x="p.x - hoverStep / 2"
                  y="0"
                  :width="hoverStep"
                  :height="CHART_H"
                  fill="transparent"
                  @mouseenter="hoverIdx = i"
                />
              </svg>
              <div v-if="hoverIdx >= 0 && chartData" class="wc-tooltip" :style="tooltipStyle">
                <div class="wct-time">{{ fmtHour(chartData.pts[hoverIdx].h.fxTime) }}</div>
                <div class="wct-main">
                  <i :class="'qi-' + chartData.pts[hoverIdx].h.icon" style="font-size: 16px"></i>
                  {{ chartData.pts[hoverIdx].t }}° {{ chartData.pts[hoverIdx].h.text }}
                </div>
                <div v-if="Number(chartData.pts[hoverIdx].h.pop) > 0" class="wct-sub">💧 {{ chartData.pts[hoverIdx].h.pop }}%</div>
                <div class="wct-sub">
                  {{ chartData.pts[hoverIdx].h.windDir }} {{ chartData.pts[hoverIdx].h.windScale }}{{ $t('weatherPage.level') }}
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 10 天预报:前 3 天 7 列细列,后 7 天 3 列精简列(近细远粗) -->
        <div v-if="detail?.daily?.length" class="card">
          <div class="section-label">{{ $t('weatherPage.daily') }}</div>
          <el-table v-if="dailyMain.length" :data="dailyMain" size="small" stripe>
            <el-table-column :label="$t('weatherPage.date')" width="110">
              <template #default="{ row }">{{ fmtDate(row.fxDate) }}</template>
            </el-table-column>
            <el-table-column :label="$t('weatherPage.dayWeather')" min-width="140">
              <template #default="{ row }"> <i :class="'qi-' + row.iconDay" class="wt-td-icon"></i> {{ row.textDay }} </template>
            </el-table-column>
            <el-table-column :label="$t('weatherPage.nightWeather')" min-width="140">
              <template #default="{ row }"> <i :class="'qi-' + row.iconNight" class="wt-td-icon"></i> {{ row.textNight }} </template>
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
              <template #default="{ row }">
                ☀ {{ (row.sunrise || '').slice(11, 16) }} / 🌙 {{ (row.sunset || '').slice(11, 16) }}
              </template>
            </el-table-column>
          </el-table>
          <el-table v-if="dailyRest.length" :data="dailyRest" size="small" stripe class="wt-daily-rest">
            <el-table-column :label="$t('weatherPage.date')" width="110">
              <template #default="{ row }">{{ fmtDate(row.fxDate) }}</template>
            </el-table-column>
            <el-table-column :label="$t('weatherPage.dayWeather')" min-width="140">
              <template #default="{ row }"> <i :class="'qi-' + row.iconDay" class="wt-td-icon"></i> {{ row.textDay }} </template>
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
          </el-table>
        </div>
      </div>

      <!-- ── L3 空气与天空(安静块) ── -->
      <div class="wt-zone">
        <div class="wt-zone-label">{{ $t('weatherPage.zone.env') }}</div>

        <!-- 空气质量:常驻主指标,明细默认收起 -->
        <div v-if="detail?.air" class="card wt-card-quiet">
          <div class="section-label wt-section-head">
            <span>{{ $t('weatherPage.air') }}</span>
            <button class="wt-more" type="button" @click="airDetailOpen = !airDetailOpen">
              {{ airDetailOpen ? $t('weatherPage.airDetail.collapse') : $t('weatherPage.airDetail.expand') }}
              <span class="wt-more-arrow" :class="{ open: airDetailOpen }">▾</span>
            </button>
          </div>
          <div class="wt-air">
            <div class="wt-air-main">
              <span class="wa-aqi">{{ detail.air.aqi }}</span>
              <span class="wa-cat">{{ detail.air.category }}</span>
              <span v-if="detail.air.primary" class="wa-primary">{{ $t('weatherPage.primaryPollutant') }}: {{ detail.air.primary }}</span>
              <span class="wa-item"
                >PM2.5 <b>{{ detail.air.pm2p5 }}</b></span
              >
            </div>
            <div v-if="detail.air.adviceGeneral" class="wt-air-health">
              <span class="wah-label">{{ $t('weatherPage.healthAdvice') }}</span
              >{{ detail.air.adviceGeneral }}
            </div>
          </div>

          <div v-if="airDetailOpen" class="wt-air">
            <div class="wt-air-items">
              <span class="wa-item"
                >PM10 <b>{{ detail.air.pm10 }}</b></span
              >
              <span class="wa-item"
                >NO₂ <b>{{ detail.air.no2 }}</b></span
              >
              <span class="wa-item"
                >SO₂ <b>{{ detail.air.so2 }}</b></span
              >
              <span class="wa-item"
                >O₃ <b>{{ detail.air.o3 }}</b></span
              >
              <span class="wa-item"
                >CO <b>{{ detail.air.co }}</b></span
              >
            </div>
            <div v-if="detail.air.adviceSensitive" class="wt-air-health">
              <span class="wah-label">{{ $t('weatherPage.sensitiveAdvice') }}</span
              >{{ detail.air.adviceSensitive }}
            </div>

            <!-- 24 小时空气质量走势 -->
            <div v-if="airTrend" class="wt-air-trend">
              <div class="wat-head">
                <span class="wat-title">{{ $t('weatherPage.airHourly') }}</span>
                <span class="wat-range">AQI {{ airTrend.min }} ~ {{ airTrend.max }}</span>
              </div>
              <div class="wat-chart-wrap" @mouseleave="airHover = -1">
                <svg :viewBox="`0 0 ${AIR_W} ${AIR_H}`" class="wat-chart">
                  <path :d="airArea" :fill="airPeakColor" opacity="0.12" />
                  <path :d="airLine" fill="none" :stroke="airPeakColor" stroke-width="2" stroke-linejoin="round" stroke-linecap="round" />
                  <template v-for="(lb, i) in airTrend.labels" :key="'al' + i">
                    <text :x="lb.x" :y="AIR_H - 8" class="wc-xlabel">{{ lb.label }}</text>
                  </template>
                  <g v-if="airHover >= 0">
                    <line
                      :x1="airTrend.pts[airHover].x"
                      :y1="AIR_PT - 8"
                      :x2="airTrend.pts[airHover].x"
                      :y2="AIR_H - AIR_PB"
                      class="wc-hover-line"
                    />
                    <circle
                      :cx="airTrend.pts[airHover].x"
                      :cy="airTrend.pts[airHover].y"
                      r="4"
                      :fill="aqiColor(airTrend.pts[airHover].h.level)"
                      class="wat-dot"
                    />
                  </g>
                  <rect
                    v-for="(p, i) in airTrend.pts"
                    :key="'ar' + i"
                    :x="p.x - 15"
                    y="0"
                    width="30"
                    :height="AIR_H"
                    fill="transparent"
                    @mouseenter="airHover = i"
                  />
                </svg>
                <div v-if="airHover >= 0" class="wc-tooltip" :style="airTooltipStyle">
                  <div class="wct-time">{{ fmtHour(airTrend.pts[airHover].h.fxTime) }}</div>
                  <div class="wct-main" :style="{ color: aqiColor(airTrend.pts[airHover].h.level) }">
                    AQI {{ airTrend.pts[airHover].v }} · {{ airTrend.pts[airHover].h.category }}
                  </div>
                  <div v-if="airTrend.pts[airHover].h.primary" class="wct-sub">
                    {{ $t('weatherPage.primaryPollutant') }}: {{ airTrend.pts[airHover].h.primary }}
                  </div>
                </div>
              </div>
            </div>

            <!-- 未来 3 天空气质量 -->
            <div v-if="detail.airDaily?.length" class="wt-air-days">
              <div class="wat-head">
                <span class="wat-title">{{ $t('weatherPage.airDaily') }}</span>
              </div>
              <div class="wad-list">
                <div v-for="d in detail.airDaily" :key="d.fxDate" class="wad-item" :style="{ borderLeftColor: aqiColor(d.level) }">
                  <span class="wad-date">{{ fmtMD(d.fxDate) }}</span>
                  <span class="wad-aqi" :style="{ color: aqiColor(d.level) }">{{ d.aqi }}</span>
                  <span class="wad-cat" :style="{ background: aqiColor(d.level) + '22', color: aqiColor(d.level) }">{{ d.category }}</span>
                  <span v-if="d.primary" class="wad-primary">{{ d.primary }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 日月与晨昏:世界地图 + 晨昏线,点击地点看此刻阶段(sun-info 拿不到时整块不渲染) -->
        <TerminatorMap
          v-if="astro"
          :astro="astro"
          :city="astro.city || ''"
          :lat="astro.lat"
          :lng="astro.lng"
          :subsolar="astro.subsolar"
          :generated-at="astro.generatedAt"
        />

        <!-- 生活指数:紧凑网格,点开看全文 -->
        <div v-if="detail?.indices?.length" class="card wt-card-quiet">
          <div class="section-label">{{ $t('weatherPage.indices') }}</div>
          <div class="wt-indices">
            <button v-for="(ix, i) in detail.indices" :key="i" class="wt-index" type="button" @click="openIndex(ix)">
              <span class="wi-head">
                <span class="wi-name">{{ ix.name }}</span
                ><span class="wi-cat">{{ ix.category }}</span>
              </span>
              <span class="wi-text">{{ ix.text }}</span>
            </button>
          </div>
        </div>
      </div>

      <el-empty v-if="!loading && !detail" :description="$t('weatherPage.empty')" />
    </div>

    <!-- 预警详情:append-to-body 免得被左侧导航盖住;限宽避免长正文撑满屏 -->
    <el-dialog v-model="warnDialog" :title="activeWarn?.typeName" append-to-body width="min(640px, 92vw)">
      <div v-if="activeWarn" class="wt-warn-dialog">
        <div class="wt-warn-head">
          <span class="wt-warn-title" :style="{ color: warnLevelColor(activeWarn.level) }">
            {{ activeWarn.level }}{{ $t('weatherPage.warningSuffix') }}
          </span>
          <span v-if="activeWarn.senderName" class="wt-warn-sender">{{ activeWarn.senderName }}</span>
          <span class="wt-warn-time">{{ fmtDateTime(activeWarn.startTime) }} ~ {{ fmtDateTime(activeWarn.endTime) }}</span>
        </div>
        <div class="wt-warn-text">{{ activeWarn.text }}</div>
        <div v-if="activeWarn.instruction" class="wt-warn-instruction">
          {{ $t('weatherPage.defenseGuide') }}：{{ activeWarn.instruction }}
        </div>
      </div>
    </el-dialog>

    <!-- 生活指数全文 -->
    <el-dialog v-model="indexDialog" :title="activeIndex?.name" append-to-body width="min(560px, 92vw)">
      <div v-if="activeIndex" class="wt-index-dialog">
        <div class="wi-cat">{{ activeIndex.category }}</div>
        <div class="wi-text">{{ activeIndex.text }}</div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, inject, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import Breadcrumb from '@/components/Breadcrumb.vue'
import TerminatorMap from '@/views/weather/TerminatorMap.vue'
import { aqiColor, warnLevelColor, WARN_LEVEL_ORDER } from '@/utils/dict'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'
import { publicApi } from '@/api'

const { t } = useI18n()
const sunLight = inject(SUN_LIGHT_KEY, null)

const loading = ref(false)
const detail = ref(null)
const warnings = computed(() => detail.value?.warning || [])
// 预警按级别降序:最严重的排在最右,其余向左延伸
const sortedWarnings = computed(() =>
  [...warnings.value].sort((a, b) => WARN_LEVEL_ORDER.indexOf(b.level) - WARN_LEVEL_ORDER.indexOf(a.level)),
)
const warnTint = (level) => warnLevelColor(level) + '14'
const todayHigh = computed(() => detail.value?.daily?.[0]?.tempMax ?? '—')
const todayLow = computed(() => detail.value?.daily?.[0]?.tempMin ?? '—')
const minutelySummary = computed(() => detail.value?.minutely?.summary || '')
// 今日日期标签:跨零点自动刷新(每分钟检查一次)
const fmtToday = () => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
const todayLabel = ref(fmtToday())
const todayTimer = setInterval(() => {
  todayLabel.value = fmtToday()
}, 60000)
onBeforeUnmount(() => clearInterval(todayTimer))
const nf = (key) => detail.value?.nowFull?.[key] ?? '—'
const fmtHour = (s) => (s || '').slice(11, 16)
const fmtDate = (s) => {
  if (!s) return ''
  const d = new Date(s)
  const weekdays = [
    t('weatherPage.w0'),
    t('weatherPage.w1'),
    t('weatherPage.w2'),
    t('weatherPage.w3'),
    t('weatherPage.w4'),
    t('weatherPage.w5'),
    t('weatherPage.w6'),
  ]
  return `${d.getMonth() + 1}/${d.getDate()} ${weekdays[d.getDay()]}`
}
const fmtDateTime = (s) => (s || '').replace('T', ' ').slice(5, 16)
// "2026-09-25" / ISO 串 → "9/25"(按字符串取,避免 new Date 在非 +08:00 环境偏移一天)
const fmtMD = (s) => {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(s || '')
  return m ? `${Number(m[2])}/${Number(m[3])}` : ''
}

// ── 气象预警(实况右侧,右起排列 + 详情弹窗) ──
const warnRowEl = ref(null)
const warnDialog = ref(false)
const activeWarn = ref(null)
const openWarning = (w) => {
  activeWarn.value = w
  warnDialog.value = true
}
// row-reverse 下初始滚动位置各浏览器不一致:按第一条(最右)chip 的实际位置纠正
function scrollWarnRow() {
  const el = warnRowEl.value
  const first = el?.firstElementChild
  if (!el || !first || el.scrollWidth <= el.clientWidth) return
  el.scrollLeft += first.getBoundingClientRect().right - el.getBoundingClientRect().right
}
watch(sortedWarnings, () => nextTick(scrollWarnRow))
onMounted(() => nextTick(scrollWarnRow))

// ── 生活指数(紧凑网格 + 全文弹窗) ──
const indexDialog = ref(false)
const activeIndex = ref(null)
const openIndex = (ix) => {
  activeIndex.value = ix
  indexDialog.value = true
}

// ── 空气质量明细折叠 ──
const airDetailOpen = ref(false)

// ── 视图切换(折线/卡片 + 24h/72h,localStorage 记忆) ──
const hourlyView = ref(localStorage.getItem('ihomy:hourly-view') || 'chart')
const setHourlyView = (v) => {
  hourlyView.value = v
  localStorage.setItem('ihomy:hourly-view', v)
}
// 72h 为逐 2 小时(后端每小时采样一点);数据缺失(凭证不支持 hours 参数)时回落 24h,避免空图
const hourlyRange = ref(localStorage.getItem('ihomy:hourly-range') === '72h' ? '72h' : '24h')
const setHourlyRange = (r) => {
  hourlyRange.value = r
  localStorage.setItem('ihomy:hourly-range', r)
}
const hourly72 = computed(() => detail.value?.hourly72 || [])
const effRange = computed(() => (hourlyRange.value === '72h' && hourly72.value.length ? '72h' : '24h'))
const hourPoints = computed(() => (effRange.value === '72h' ? hourly72.value : detail.value?.hourly || []))
const isDayStart = (i) =>
  i === 0 || (hourPoints.value[i]?.fxTime || '').slice(0, 10) !== (hourPoints.value[i - 1]?.fxTime || '').slice(0, 10)

// ── 温度 → 颜色(7 段渐变,-20°冷蓝→42°暖红) ──
const TEMP_STOPS = [
  [-20, 74, 111, 212],
  [-5, 95, 159, 224],
  [5, 98, 184, 201],
  [15, 143, 201, 143],
  [22, 224, 201, 95],
  [28, 224, 155, 95],
  [34, 224, 120, 69],
  [42, 216, 74, 58],
]
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

// ── 折线图几何(宽度随点数变化:72h 点位固定 46px 后横向滚动) ──
const CHART_W = 720,
  CHART_H = 230,
  PT = 34,
  PB = 40,
  PL = 14,
  PR = 14
const hoverIdx = ref(-1)
const chartW = computed(() => (effRange.value === '72h' ? Math.max(CHART_W, hourPoints.value.length * 46) : CHART_W))

const chartData = computed(() => {
  const hs = hourPoints.value
  if (!hs.length) return null
  const temps = hs.map((h) => Number(h.temp) || 0)
  const tMin = Math.min(...temps) - 1,
    tMax = Math.max(...temps) + 1
  const n = hs.length
  const xOf = (i) => PL + (i / (n - 1)) * (chartW.value - PL - PR)
  const yOf = (t) => PB + (1 - (t - tMin) / (tMax - tMin || 1)) * (CHART_H - PB - PT)
  const pts = hs.map((h, i) => ({ x: xOf(i), y: yOf(Number(h.temp) || 0), t: Number(h.temp) || 0, h }))
  return { pts, tMin, tMax, temps }
})

// 天数分隔线(72h 跨三天,标出每天的起点)
const dayMarks = computed(() => {
  if (effRange.value !== '72h' || !chartData.value) return []
  return chartData.value.pts
    .map((p, i) => ({ p, i }))
    .filter(({ i }) => i > 0 && isDayStart(i))
    .map(({ p, i }) => ({ x: p.x, label: fmtMD(hourPoints.value[i].fxTime) }))
})

const hoverStep = computed(() => {
  const n = chartData.value?.pts.length || 1
  return (chartW.value - PL - PR) / (n - 1)
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
    const cp1y = p1.y + (p2.y - p1.y) * tension
    const cp2x = p2.x - (p3.x - p1.x) * tension
    const cp2y = p2.y - (p3.y - p2.y) * tension
    d += `C${cp1x},${cp1y} ${cp2x},${cp2y} ${p2.x},${p2.y}`
  }
  return d
}
const linePath = computed(() => (chartData.value ? catmullRom(chartData.value.pts) : ''))
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
  return chartData.value.pts.filter((_, i) => i % 3 === 0).map((p) => ({ x: p.x, label: fmtHour(p.h.fxTime) }))
})

// 悬浮 tooltip 定位(百分比相对 .wt-chart-inner,内层宽度即 chartW,横向滚动时仍跟随)
const tooltipStyle = computed(() => {
  if (hoverIdx.value < 0 || !chartData.value) return { display: 'none' }
  const p = chartData.value.pts[hoverIdx.value]
  const left = Math.min(Math.max(p.x / chartW.value, 0.08), 0.92) * 100
  const top = (p.y / CHART_H) * 100
  return { left: left + '%', top: top + '%' }
})

// ── 日月天文(纯天文计算:取自 /public/sun-info,后端 SolarUtil 用 NOAA/Meeus 算法算,不依赖天气 API;
//    故天气凭证未配置时晨昏地图照常显示。渲染交给 <TerminatorMap>) ──
const astro = ref(null)
const astroLoading = ref(false)
// 晨昏地图依赖后端新增的 subsolar 直射点;老后端/应用启动时取的旧数据没有该字段 → 当日则重取一次
const astroStale = () => !!astro.value && astro.value.date === todayLabel.value && !astro.value.subsolar && !astroLoading.value
// ── 空气质量 24 小时走势(和风 airquality/v1/hourly) ──
const AIR_W = 720,
  AIR_H = 150,
  AIR_PT = 20,
  AIR_PB = 26,
  AIR_PL = 16,
  AIR_PR = 16
const airHover = ref(-1)
const airTrend = computed(() => {
  const hs = detail.value?.airHourly || []
  if (hs.length < 2) return null
  const vals = hs.map((h) => Number(h.aqi) || 0)
  const min = Math.min(...vals),
    max = Math.max(...vals)
  const lo = Math.max(0, min - 10),
    hi = max + 10
  const n = hs.length
  const xOf = (i) => AIR_PL + (i / (n - 1)) * (AIR_W - AIR_PL - AIR_PR)
  const yOf = (v) => AIR_PB + (1 - (v - lo) / (hi - lo || 1)) * (AIR_H - AIR_PB - AIR_PT)
  const pts = hs.map((h, i) => ({ x: xOf(i), y: yOf(Number(h.aqi) || 0), v: Number(h.aqi) || 0, h }))
  return {
    pts,
    min,
    max,
    peakLevel: Math.max(...hs.map((h) => Number(h.level) || 1)),
    labels: pts.filter((_, i) => i % 6 === 0).map((p) => ({ x: p.x, label: fmtHour(p.h.fxTime) })),
  }
})
const airPeakColor = computed(() => aqiColor(airTrend.value?.peakLevel || 1))
const airLine = computed(() => (airTrend.value ? airTrend.value.pts.map((p, i) => `${i ? 'L' : 'M'}${p.x},${p.y}`).join('') : ''))
const airArea = computed(() => {
  const tr = airTrend.value
  if (!tr) return ''
  const pts = tr.pts
  return `${airLine.value}L${pts[pts.length - 1].x},${AIR_H - AIR_PB}L${pts[0].x},${AIR_H - AIR_PB}Z`
})
const airTooltipStyle = computed(() => {
  if (airHover.value < 0 || !airTrend.value) return { display: 'none' }
  const p = airTrend.value.pts[airHover.value]
  return {
    left: Math.min(Math.max(p.x / AIR_W, 0.08), 0.92) * 100 + '%',
    top: (p.y / AIR_H) * 100 + '%',
  }
})

// ── N 天温度区间渐变横条 ──
const dailyRange = computed(() => {
  const d = detail.value?.daily || []
  if (!d.length) return null
  const min = Math.min(...d.map((x) => Number(x.tempMin)))
  const max = Math.max(...d.map((x) => Number(x.tempMax)))
  return { min, max, range: max - min || 1 }
})
const tbarStyle = (row) => {
  const r = dailyRange.value
  if (!r) return { background: '#ccc' }
  const lo = Number(row.tempMin),
    hi = Number(row.tempMax)
  return {
    left: ((lo - r.min) / r.range) * 100 + '%',
    width: (Math.max(hi - lo, 2) / r.range) * 100 + '%',
    background: `linear-gradient(90deg, ${tempColor(lo)}, ${tempColor(hi)})`,
  }
}
const tbarDotLeft = (row) => {
  const r = dailyRange.value
  if (!r) return '50%'
  const avg = (Number(row.tempMin) + Number(row.tempMax)) / 2
  return ((avg - r.min) / r.range) * 100 + '%'
}

// 近细远粗:前 3 天 7 列细列,后 7 天 3 列精简列(拆两张表,EP 列是全局的没法按行隐藏)
const dailyMain = computed(() => (detail.value?.daily || []).slice(0, 3))
const dailyRest = computed(() => (detail.value?.daily || []).slice(3))

const load = async () => {
  if (sunLight?.weatherDetail?.value) detail.value = sunLight.weatherDetail.value
  // 光影层应用启动时已取过当日 sun-info(同一份数据),先拿来立即渲染省一次请求;
  // 但光线测试台可能按别的日期取过,日期不符就自己再取一次,免得卡片显示的是另一天的日月时刻
  const shared = sunLight?.sunInfo?.value
  if (shared?.date === todayLabel.value) astro.value = shared
  loading.value = true
  // 天气详情与日月天文并行:后者纯天文计算(不依赖天气凭证),故天气凭证未配/接口失败也不影响晨昏区块
  const [data, sun] = await Promise.all([
    publicApi.getWeatherDetail().catch(() => null),
    astroStale() || astro.value ? Promise.resolve(null) : publicApi.getSunInfo().catch(() => null),
  ])
  if (data) {
    detail.value = data
    if (sunLight?.weatherDetail) sunLight.weatherDetail.value = data
  }
  if (sun) astro.value = sun
  loading.value = false
  // 共享的那份缺 subsolar(后端未升级/启动时取于旧版本)→ 补一次取当日完整数据
  if (astroStale()) {
    astroLoading.value = true
    const fresh = await publicApi.getSunInfo().catch(() => null)
    astroLoading.value = false
    if (fresh) astro.value = fresh
  }
}
onMounted(load)
</script>

<style scoped>
.weather-page-body {
  display: flex;
  flex-direction: column;
}
/* 三个信息组:现在 / 未来 / 空气与天空,组内紧凑、组间留白 */
.wt-zone {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.wt-zone + .wt-zone {
  margin-top: 14px;
}
.wt-zone-label {
  font-size: 12px;
  letter-spacing: 0.5px;
  color: var(--color-text-tertiary);
}
/* L3 安静块:去掉阴影与悬浮位移,标题收小,靠内层 --color-card-2 分层 */
.wt-card-quiet {
  box-shadow: none;
  padding: 16px 20px;
}
.wt-card-quiet:hover {
  box-shadow: none;
  transform: none;
}
.wt-card-quiet .section-label {
  font-size: 12.5px;
  color: var(--color-text-secondary);
  margin-bottom: 12px;
}
.wt-card-quiet .section-label:hover {
  color: var(--color-text-secondary);
}
/* ── L1 实况 ── */
.wt-hero {
  padding: 20px;
}
.wt-hero-top {
  display: flex;
  align-items: flex-start;
  gap: 16px;
}
.wt-now {
  flex: 1 1 auto;
  min-width: 0;
}
.wt-city {
  font-size: 14px;
  color: var(--color-text-secondary);
}
.wt-main {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 8px 0 4px;
  flex-wrap: wrap;
}
.wt-big-icon {
  font-size: 56px;
  line-height: 1;
}
.wt-temp {
  font-size: 56px;
  font-weight: 700;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}
.wt-degree {
  font-size: 28px;
  opacity: 0.5;
}
.wt-hilo {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.wt-hi {
  color: #c07a4a;
  font-weight: 600;
  font-size: 14px;
}
.wt-lo {
  color: #6a8ab0;
  font-weight: 600;
  font-size: 14px;
}
.wt-text {
  font-size: 16px;
  color: var(--color-text-secondary);
}
.wt-metrics {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 10px;
  margin-top: 16px;
}
.wt-metric {
  background: var(--color-card-2);
  border-radius: 10px;
  padding: 10px 14px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.wm-label {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.wm-val {
  font-size: 15px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
/* 预警列:右起排列,最严重在最右;溢出时横向滚动并初始停在最右 */
.wt-warnings {
  flex: 0 1 46%;
  display: flex;
  flex-direction: row-reverse;
  justify-content: flex-start;
  gap: 10px;
  overflow-x: auto;
  padding-bottom: 2px;
}
.wt-warn-chip {
  flex: 0 0 280px;
  border: 0;
  border-left: 3px solid;
  border-radius: 10px;
  padding: 10px 12px;
  text-align: left;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}
.wt-warn-chip:focus-visible {
  outline: 2px solid var(--color-brand);
  outline-offset: 2px;
}
.wt-warn-title {
  font-weight: 700;
  font-size: 13px;
}
.wt-warn-time {
  font-size: 11px;
  color: var(--color-text-secondary);
}
.wt-warn-text {
  font-size: 12px;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.wt-warn-dialog {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.wt-warn-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  flex-wrap: wrap;
}
.wt-warn-sender {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.wt-warn-instruction {
  font-size: 12px;
  line-height: 1.6;
  padding: 8px 10px;
  background: rgba(var(--color-brand-rgb), 0.08);
  border-radius: 8px;
  color: var(--color-text-secondary);
  white-space: pre-line;
}
/* ── 控件 ── */
.wt-section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.wt-toggles {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.wt-vt-text {
  width: auto;
  padding: 0 10px;
  font-size: 12px;
}
.wt-more {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: 0;
  background: transparent;
  cursor: pointer;
  font-size: 12px;
  color: var(--color-text-secondary);
  padding: 0;
}
.wt-more:hover {
  color: var(--color-text);
}
.wt-more-arrow {
  display: inline-block;
  transition: transform 0.2s;
}
.wt-more-arrow.open {
  transform: rotate(180deg);
}
/* 分钟降水一句话(并入逐小时卡顶部) */
.wt-minutely-banner {
  font-size: 12px;
  line-height: 1.6;
  background: var(--color-card-2);
  color: var(--color-text-secondary);
  border-radius: 8px;
  padding: 8px 12px;
  margin-bottom: 10px;
}
/* ── 逐小时 ── */
.wt-hourly {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding-bottom: 6px;
}
.wt-hour {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  background: var(--color-card-2);
  border-radius: 10px;
  padding: 10px 8px;
  min-width: 76px;
  flex: none;
}
.wh-time {
  font-size: 12px;
  color: var(--color-text-secondary);
  font-variant-numeric: tabular-nums;
}
.wh-icon {
  font-size: 26px;
}
.wh-temp {
  font-size: 15px;
  font-weight: 700;
}
.wh-text {
  font-size: 11px;
  color: var(--color-text-secondary);
}
.wh-pop {
  font-size: 11px;
  color: #6a8ab0;
}
.wh-wind {
  font-size: 11px;
  color: var(--color-text-secondary);
}
.wt-chart-scroll {
  overflow-x: auto;
  overflow-y: hidden;
  padding-bottom: 4px;
}
.wt-chart-inner {
  position: relative;
}
.wt-chart-inner .wt-chart {
  max-width: none;
}
.wc-xlabel {
  /* 10px 次要色在暖色纸底上几乎看不清,轴标签是读数不是装饰,提到正文色并放大一号 */
  font-size: 11px;
  fill: var(--color-text);
  text-anchor: middle;
  font-variant-numeric: tabular-nums;
}
.wc-extreme {
  font-size: 11px;
  font-weight: 700;
  text-anchor: middle;
}
.wc-hover-line {
  stroke: var(--color-text-secondary);
  stroke-dasharray: 3 3;
  opacity: 0.4;
}
.wc-hover-dot {
  fill: var(--color-brand);
  stroke: #fffdf8;
  stroke-width: 2;
}
.wc-tooltip {
  position: absolute;
  transform: translate(-50%, -120%);
  background: var(--color-card-2);
  border: 1px solid var(--color-border);
  border-radius: 10px;
  padding: 8px 12px;
  font-size: 12px;
  pointer-events: none;
  box-shadow: 0 3px 12px rgba(0, 0, 0, 0.08);
  white-space: nowrap;
  z-index: 5;
}
.wct-time {
  color: var(--color-text-secondary);
  font-size: 11px;
}
.wct-main {
  font-weight: 700;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 4px;
}
.wct-sub {
  color: var(--color-text-secondary);
  font-size: 11px;
}
/* 72h 卡片模式按天插分隔 */
.wt-hour-sep {
  flex: none;
  align-self: stretch;
  display: flex;
  align-items: center;
  padding: 0 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-secondary);
  border-left: 1px dashed var(--color-border);
}
.wc-day-sep {
  stroke: var(--color-border);
  stroke-dasharray: 4 4;
}
.wc-daylabel {
  font-size: 10px;
  font-weight: 600;
  fill: var(--color-text-secondary);
}
/* ── N 天温度横条 ── */
.wt-daily-rest {
  margin-top: 8px;
}
.wt-trange {
  display: flex;
  align-items: center;
  gap: 8px;
}
.wt-tbar-track {
  flex: 1;
  height: 8px;
  background: rgba(58, 46, 34, 0.1);
  border-radius: 4px;
  position: relative;
  min-width: 90px;
}
.wt-tbar-fill {
  position: absolute;
  top: 0;
  height: 100%;
  border-radius: 4px;
}
.wt-tbar-dot {
  position: absolute;
  top: 50%;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: rgba(58, 46, 34, 0.55);
  transform: translate(-50%, -50%);
}
.wt-td-icon {
  font-size: 18px;
}
/* ── 空气质量 ── */
.wt-air {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.wt-air-main {
  display: flex;
  align-items: baseline;
  gap: 12px;
  flex-wrap: wrap;
}
.wa-aqi {
  font-size: 34px;
  font-weight: 700;
  line-height: 1;
}
.wa-cat {
  font-size: 15px;
  font-weight: 600;
}
.wa-primary {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.wt-air-items {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}
.wa-item {
  background: var(--color-card-2);
  border-radius: 8px;
  padding: 6px 12px;
  font-size: 12px;
  color: var(--color-text-secondary);
}
.wa-item b {
  color: var(--color-text);
  margin-left: 4px;
  font-variant-numeric: tabular-nums;
}
.wt-air-health {
  font-size: 12px;
  line-height: 1.7;
  color: var(--color-text-secondary);
  background: rgba(107, 155, 107, 0.08);
  border-radius: 8px;
  padding: 10px 12px;
}
.wah-label {
  font-weight: 600;
  color: var(--color-text);
  margin-right: 6px;
}
.wt-air-trend,
.wt-air-days {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.wat-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
}
.wat-title {
  font-size: 13px;
  font-weight: 600;
}
.wat-range {
  font-size: 12px;
  color: var(--color-text-secondary);
  font-variant-numeric: tabular-nums;
}
.wat-chart-wrap {
  position: relative;
}
.wat-chart {
  width: 100%;
  height: auto;
  display: block;
}
.wat-dot {
  stroke: var(--color-card);
  stroke-width: 2;
}
.wad-list {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}
.wad-item {
  flex: 1 1 150px;
  display: flex;
  align-items: center;
  gap: 8px;
  background: var(--color-card-2);
  border-left: 3px solid;
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 12px;
}
.wad-date {
  color: var(--color-text-secondary);
  font-variant-numeric: tabular-nums;
}
.wad-aqi {
  font-size: 17px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}
.wad-cat {
  border-radius: 6px;
  padding: 2px 8px;
  font-weight: 600;
}
.wad-primary {
  margin-left: auto;
  color: var(--color-text-secondary);
}
/* ── 生活指数(紧凑网格) ── */
.wt-indices {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 10px;
}
.wt-index {
  background: var(--color-card-2);
  border: 0;
  border-radius: 10px;
  padding: 12px 14px;
  text-align: left;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}
.wt-index:hover {
  background: var(--color-bg-2);
}
.wt-index:focus-visible {
  outline: 2px solid var(--color-brand);
  outline-offset: 2px;
}
.wi-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}
.wi-name {
  font-size: 13px;
  font-weight: 600;
}
.wi-cat {
  font-size: 12px;
  color: var(--color-brand);
  font-weight: 600;
}
.wi-text {
  font-size: 12px;
  line-height: 1.6;
  color: var(--color-text-secondary);
  display: -webkit-box;
  -webkit-line-clamp: 3;
  line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.wt-index-dialog {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.wt-index-dialog .wi-text {
  -webkit-line-clamp: unset;
  line-clamp: unset;
  display: block;
  overflow: visible;
}
/* 深色模式横条轨道 */
:global(html.dark) .wt-tbar-track {
  background: rgba(232, 220, 200, 0.12);
}
:global(html.dark) .wt-tbar-dot {
  background: rgba(232, 220, 200, 0.55);
}
:global(html.dark) .wc-hover-dot {
  stroke: var(--color-card);
}
@media (max-width: 900px) {
  .wt-hero-top {
    flex-direction: column;
  }
  .wt-warnings {
    width: 100%;
    flex: none;
    flex-direction: row;
  }
  .wt-warn-chip {
    flex: 0 0 78vw;
  }
}
@media (max-width: 768px) {
  .wt-metrics {
    grid-template-columns: repeat(2, 1fr);
  }
  .wt-big-icon {
    font-size: 44px;
  }
  .wt-temp {
    font-size: 44px;
  }
  .wt-toggles {
    gap: 8px;
  }
  .wad-item {
    flex: 1 1 100%;
  }
}
</style>
