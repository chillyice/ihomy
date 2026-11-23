<template>
  <div class="weather-window" :class="{ short: row <= 2, strip: row === 1 }">
    <header class="window-head">
      <h3>{{ current?.city ? $t('warm.weatherWindow', { city: current.city }) : $t('warm.widget.weather') }}</h3>
      <span aria-hidden="true">↗</span>
    </header>
    <div class="observation">
      <div class="reading">
        <div v-if="current?.text" class="condition">
          <i v-if="current.iconCode" :class="'qi-' + current.iconCode" aria-hidden="true"></i>
          {{ current.text }}
        </div>
        <div v-if="current?.temp != null" class="temperature">{{ current.temp }}<small>°C</small></div>
        <span v-else class="unavailable">{{ $t('morning.weatherUnavailable') }}</span>
        <div v-if="today?.tempMax != null || today?.tempMin != null" class="range">
          <span v-if="today.tempMax != null">↑ {{ today.tempMax }}°</span>
          <span v-if="today.tempMin != null">↓ {{ today.tempMin }}°</span>
        </div>
      </div>
      <div aria-hidden="true" class="casement">
        <div class="glass">
          <img
            v-if="scene"
            :key="scene"
            :src="scene"
            alt=""
            draggable="false"
            @error="failedScenes.add($event.target.getAttribute('src'))"
          />
        </div>
      </div>
    </div>
    <dl v-if="span === 12 && metrics.length" class="metrics">
      <div v-for="metric in metrics" :key="metric.label">
        <dt>{{ metric.label }}</dt>
        <dd>{{ metric.value }}</dd>
      </div>
    </dl>
    <ol v-if="span >= 6 && forecast.length" class="forecast">
      <li v-for="day in forecast" :key="day.fxDate">
        <time :datetime="day.fxDate">{{ formatDate(day.fxDate) }}</time>
        <span class="outlook">
          <i v-if="day.iconDay" :class="'qi-' + day.iconDay" :title="day.textDay" :aria-label="day.textDay" role="img"></i>
          {{ day.tempMin ?? '—' }}° / {{ day.tempMax ?? '—' }}°
        </span>
      </li>
    </ol>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { resolveMorningWeather } from '@/utils/morningWeather'
import { weatherBackdrop } from '@/components/morning/backdrops'

const props = defineProps({
  weather: Object,
  detail: Object,
  background: String,
  night: Boolean,
  span: Number,
  row: Number,
})
const { t, locale } = useI18n()
const current = computed(() => props.weather || props.detail?.now)
const today = computed(() => props.detail?.daily?.[0])
const forecast = computed(() => (props.detail?.daily || []).slice(1, 4))
const dateFormat = computed(() => new Intl.DateTimeFormat(locale.value, { month: 'numeric', day: 'numeric', timeZone: 'UTC' }))
function formatDate(value) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '—' : dateFormat.value.format(date)
}

const fallback = computed(() => (current.value ? weatherBackdrop(resolveMorningWeather(current.value), props.night) : ''))
const failedScenes = ref(new Set())
watch([() => props.background, fallback], () => failedScenes.value.clear())
const scene = computed(() => [props.background, fallback.value].find((url) => url && !failedScenes.value.has(url)))

const metrics = computed(() => {
  const now = props.detail?.nowFull
  const pm = props.detail?.air?.pm2p5
  const wind = [now?.windDir, now?.windScale != null ? t('home.dashboard.windScale', { n: now.windScale }) : ''].filter(Boolean).join(' ')
  return [
    { label: t('weatherPage.humidity'), value: now?.humidity != null ? now.humidity + '%' : '' },
    { label: t('weatherPage.wind'), value: wind },
    { label: 'PM2.5', value: pm != null ? String(pm) : '' },
  ].filter((metric) => metric.value)
})
</script>

<style scoped>
.weather-window {
  --wood: color-mix(in srgb, rgb(var(--blob-5)) 66%, var(--color-card));
  --frame-edge: color-mix(in srgb, var(--color-text) 26%, var(--wood));
  --ink-soft: color-mix(in srgb, var(--color-text) 82%, var(--color-card));
  display: flex;
  flex-direction: column;
  height: 100%;
  min-width: 0;
  gap: clamp(8px, 2cqi, 16px);
  color: var(--color-text);
  font-variant-numeric: tabular-nums;
}
.window-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  color: var(--ink-soft);
}
.window-head h3 {
  margin: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 13px;
  font-weight: 650;
}
.window-head > span {
  font-size: 17px;
  line-height: 1;
}
.observation {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.65fr);
  grid-template-rows: minmax(0, 1fr);
  align-items: stretch;
  gap: clamp(8px, 3cqi, 24px);
}
.reading {
  align-self: center;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: clamp(5px, 1.6cqi, 14px);
}
.condition,
.range {
  color: var(--ink-soft);
  font-size: clamp(12px, 2.8cqi, 17px);
}
.condition {
  font-weight: 600;
  overflow-wrap: anywhere;
}
.condition i {
  margin-inline-end: 2px;
  color: var(--color-brand);
}
.temperature {
  font-size: clamp(34px, 12cqi, 86px);
  line-height: 1;
  font-weight: 550;
  letter-spacing: -0.07em;
  white-space: nowrap;
}
.temperature small {
  display: inline-block;
  vertical-align: top;
  margin: 0.2em 0 0 0.15em;
  font-size: 0.32em;
  letter-spacing: 0;
}
.range {
  display: flex;
  flex-wrap: wrap;
  gap: 0 10px;
  font-size: 13px;
}
.unavailable {
  font-size: 13px;
  color: var(--ink-soft);
}
.casement {
  position: relative;
  min-height: 0;
  margin: 2px 3px 5px;
  padding: clamp(4px, 0.95cqi, 8px);
  border: 1px solid var(--frame-edge);
  border-radius: 48% 48% 2px 2px / 18% 18% 2px 2px;
  background:
    linear-gradient(color-mix(in srgb, var(--wood) 88%, transparent), color-mix(in srgb, var(--wood) 84%, transparent)),
    var(--album-cloth-texture);
  background-size: auto, 110px;
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.38),
    0 2px 4px rgba(58, 36, 19, 0.14);
}
.casement::after {
  content: '';
  position: absolute;
  inset: auto -4px -4px;
  height: 5px;
  border-radius: 2px;
  background: var(--wood);
  border-bottom: 2px solid var(--frame-edge);
}
.glass {
  position: relative;
  height: 100%;
  overflow: hidden;
  border-radius: inherit;
  background: var(--home-paper-ground);
  background-size: auto, var(--home-grain-size);
}
.glass img {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
}
.glass::before,
.glass::after {
  content: '';
  position: absolute;
  z-index: 1;
  background: var(--wood);
  box-shadow: 1px 1px 1px var(--frame-edge);
}
.glass::before {
  inset: 0 auto 0 50%;
  width: 3px;
}
.glass::after {
  inset: 32% 0 auto;
  height: 3px;
}
.forecast,
.metrics {
  display: flex;
  margin: 0;
  padding: 8px 0 0;
  list-style: none;
  border-top: 1px solid var(--mat-edge, var(--color-border));
}
.forecast li,
.metrics > div {
  flex: 1;
  min-width: 0;
  padding-inline: clamp(5px, 2cqi, 16px);
}
.forecast li:first-child,
.metrics > div:first-child {
  padding-left: 0;
}
.forecast li + li,
.metrics > div + div {
  border-left: 1px solid var(--mat-edge, var(--color-border));
}
.forecast time,
.metrics dt {
  display: block;
  font-size: 11px;
  color: var(--ink-soft);
}
.outlook,
.metrics dd {
  margin: 2px 0 0;
  font-size: 13px;
  font-weight: 650;
}
.outlook {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  white-space: nowrap;
}
.outlook i {
  color: var(--ink-soft);
  font-weight: 400;
}
.weather-window.short {
  gap: 4px;
}
.short .reading {
  gap: 3px;
}
.short .temperature {
  font-size: clamp(26px, 6cqi, 42px);
}
.short .condition,
.short .range {
  font-size: 12px;
}
.short .forecast,
.short .metrics {
  padding-top: 3px;
}
.short :is(.forecast li, .metrics > div) {
  display: flex;
  flex-direction: column;
  align-items: baseline;
}
@container (min-width: 470px) {
  .short :is(.forecast li, .metrics > div) {
    flex-direction: row;
    gap: 6px;
  }
  .short .reading {
    display: grid;
    grid-template-columns: auto 1fr;
    align-items: center;
    column-gap: 12px;
  }
  .short .temperature {
    grid-row: 1 / span 2;
    grid-column: 1;
  }
}
.strip {
  flex-direction: row;
  align-items: center;
}
.strip .window-head {
  max-width: 24%;
}
.strip .window-head > span,
.strip .range,
.strip .forecast,
.strip .metrics {
  display: none;
}
.strip .observation {
  height: 100%;
}
.strip .temperature {
  font-size: 24px;
}
@container (max-width: 310px) {
  .observation {
    grid-template-columns: minmax(0, 1fr) minmax(0, 1.1fr);
    gap: 6px;
  }
  .outlook {
    font-size: 11px;
    gap: 2px;
  }
}
@media (max-width: 880px) {
  .weather-window:not(.strip) .observation {
    min-height: 135px;
  }
}
</style>
