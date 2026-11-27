<template>
  <div class="moon-panel">
    <div class="moon-observation">
      <div class="moon-now">
        <MoonDisc class="moon-portrait" :code="astro.moonPhaseCode" :phase="astro.moonPhase" :illumination="astro.moonIllumination" />
        <div>
          <strong class="moon-name">{{ moonPhaseText(t, astro.moonPhaseCode) || '—' }}</strong>
          <div class="moon-readings">
            <span
              >{{ t('weatherPage.moonrise') }} <b>{{ astro.moonrise || '—' }}</b></span
            >
            <span
              >{{ t('weatherPage.moonset') }} <b>{{ astro.moonset || '—' }}</b></span
            >
            <span v-if="astro.moonIllumination != null"
              >{{ t('weatherPage.illumination') }} <b>{{ astro.moonIllumination }}%</b></span
            >
          </div>
        </div>
      </div>
      <ul v-if="days.length" class="moon-days" :aria-label="t('weatherPage.moonOutlook')">
        <li v-for="day in days" :key="day.fxDate" :title="moonPhaseText(t, day.moonPhaseCode)">
          <MoonDisc :code="day.moonPhaseCode" :phase="day.moonPhase" :illumination="day.moonIllumination" />
          <time :datetime="day.fxDate" :aria-label="`${day.fxDate} · ${moonPhaseText(t, day.moonPhaseCode)}`">{{ fmtMD(day.fxDate) }}</time>
        </li>
      </ul>
    </div>
    <div class="moon-atlas">
      <div class="atlas-title">{{ t('weatherPage.moonAtlas') }}</div>
      <ol class="atlas-phases">
        <li v-for="code in MOON_PHASE_CODES" :key="code" :aria-current="code === astro.moonPhaseCode ? 'true' : undefined">
          <MoonDisc :code="code" />
          <span>{{ moonPhaseText(t, code) }}</span>
        </li>
      </ol>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { MOON_PHASE_CODES, moonPhaseText } from '@/utils/dict'
import MoonDisc from './MoonDisc.vue'

const props = defineProps({ astro: { type: Object, required: true } })
const { t } = useI18n()
const days = computed(() => (props.astro.moonDays || []).slice(0, 5))
const fmtMD = (date) => {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(date || '')
  return m ? `${Number(m[2])}/${Number(m[3])}` : ''
}
</script>

<style scoped>
.moon-panel {
  container-type: inline-size;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  overflow: hidden;
  background: var(--color-card-2);
}
.moon-observation {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px 24px;
  padding: 14px 18px;
  background: radial-gradient(ellipse at 2% 55%, #3c4659, transparent 56%), #222e41;
  color: #faf2df;
}
.moon-now {
  display: flex;
  align-items: center;
  gap: 13px;
}
.moon-portrait {
  width: 64px;
  flex: none;
}
.moon-name {
  font-size: 19px;
  line-height: 1.3;
}
.moon-readings {
  display: flex;
  flex-wrap: wrap;
  gap: 3px 12px;
  margin-top: 5px;
  color: #d3dae2;
  font-variant-numeric: tabular-nums;
  font-size: 12px;
}
.moon-readings span {
  white-space: nowrap;
}
.moon-readings b {
  color: #faf2df;
  font-weight: 550;
}
.moon-days,
.atlas-phases {
  display: grid;
  list-style: none;
  margin: 0;
  padding: 0;
}
.moon-days {
  grid-auto-flow: column;
  grid-auto-columns: 1fr;
  gap: 10px;
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  color: #d3dae2;
}
.moon-days li,
.atlas-phases li {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  text-align: center;
}
.moon-days .moon-disc {
  width: 28px;
}
.moon-atlas {
  padding: 12px 16px;
}
.atlas-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-secondary);
}
.atlas-phases {
  margin-top: 8px;
  grid-template-columns: repeat(8, minmax(0, 1fr));
  gap: 4px;
}
.atlas-phases li {
  padding: 5px 2px;
  font-size: 11px;
  line-height: 1.3;
  color: var(--color-text-secondary);
}
.atlas-phases .moon-disc {
  width: 34px;
}
.atlas-phases [aria-current] {
  border-radius: 8px;
  background: var(--color-card);
  box-shadow: inset 0 0 0 1px var(--color-brand);
  color: var(--color-text);
  font-weight: 700;
}
@container (max-width: 550px) {
  .moon-observation {
    padding: 12px;
  }
  .moon-days {
    width: 100%;
    padding-top: 10px;
    border-top: 1px solid #ffffff25;
  }
  .atlas-phases {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}
</style>
