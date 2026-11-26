<!-- 光照测试控制台:全局组件,可拖动,任何页面可用 -->
<template>
  <div v-if="light?.lightTestMode?.value" class="light-test-console" :style="{ left: pos.x + 'px', top: pos.y + 'px' }">
    <div class="lt-header">
      <span class="lt-title lt-drag" @pointerdown="onDragStart" :title="$t('lightTest.drag')">≡ {{ $t('lightTest.title') }}</span>
      <input
        type="date"
        :value="light.previewDate.value"
        @focus="pauseForEdit"
        @change="onDateChange"
        class="lt-date-input"
        :title="$t('lightTest.dateHint')"
      />
      <input type="time" :value="slotTime" @focus="pauseForEdit" @change="onTimeChange" class="lt-time-input" />
      <button class="lt-btn lt-reset" @click="light.stopLightTest" :title="$t('lightTest.resetTitle')">⏹</button>
    </div>
    <div v-if="activeSunInfo" class="lt-info">
      <span>{{ $t('lightTest.altitude') }} {{ light.sunScene.value.altitude?.toFixed(1) }}°</span>
      <span>{{ $t('lightTest.azimuth') }} {{ light.sunScene.value.azimuth?.toFixed(1) }}°</span>
      <span>{{ $t('lightTest.windowAngle') }} {{ (light.sunScene.value.windowAngle ?? 0).toFixed(1) }}°</span>
      <span class="lt-phase">{{ testPhase }}</span>
    </div>
    <div v-else class="lt-info" role="status">
      <template v-if="light.previewLoadFailed.value">
        {{ $t('common.loadFailed') }}
        <button class="lt-btn" @click="light.loadSunInfoForDate(light.previewDate.value)">{{ $t('common.retry') }}</button>
      </template>
      <template v-else>{{ $t('common.loading') }}</template>
    </div>
    <div class="lt-info" v-if="activeSunInfo">
      <span>{{ $t('lightTest.sunrise') }} {{ activeSunInfo.sunrise || '--' }}</span>
      <span>{{ $t('lightTest.sunset') }} {{ activeSunInfo.sunset || '--' }}</span>
      <span v-if="light.sunScene.value.altitude > 0">{{
        $t('lightTest.groundShadow', { length: $n(groundShadowPerHeight(light.sunScene.value.altitude), { maximumFractionDigits: 2 }) })
      }}</span>
    </div>
    <div class="lt-controls">
      <button class="lt-btn" @click="light.stepLightTest(-1)" :title="$t('lightTest.back5')">⏮</button>
      <button class="lt-btn lt-main" :disabled="light.previewLoadFailed.value" @click="light.pauseLightTest">
        {{ light.lightTestPaused.value ? '▶' : '⏸' }}
      </button>
      <button class="lt-btn" @click="light.stepLightTest(1)" :title="$t('lightTest.forward5')">⏭</button>
    </div>
    <div class="lt-speed">
      <button
        v-for="sp in speeds"
        :key="sp"
        class="lt-btn lt-speed-btn"
        :class="{ active: light.testSpeed.value === sp }"
        @click="light.setTestSpeed(sp)"
      >
        {{ sp }}x
      </button>
    </div>
    <div class="lt-weather">
      <button
        class="lt-btn"
        :class="{ active: light.weatherMode.value === 'clear' }"
        @click="light.setWeather('clear', 0)"
        :title="$t('lightTest.weatherClear')"
      >
        ☀️
      </button>
      <button
        class="lt-btn"
        :class="{ active: light.weatherMode.value === 'cloud' }"
        @click="light.setWeather('cloud', 0)"
        :title="$t('lightTest.weatherCloud')"
      >
        ⛅
      </button>
      <button
        class="lt-btn"
        :class="{ active: light.weatherMode.value === 'overcast' }"
        @click="light.setWeather('overcast', 0)"
        :title="$t('lightTest.weatherOvercast')"
      >
        ☁️
      </button>
      <button
        class="lt-btn"
        :class="{ active: light.weatherMode.value === 'fog' }"
        @click="light.setWeather('fog', 0)"
        :title="$t('lightTest.weatherFog')"
      >
        🌫️
      </button>
      <button
        class="lt-btn"
        :class="{ active: light.weatherMode.value === 'rain' }"
        @click="light.setWeather('rain', light.precipLevel.value || 3)"
        :title="$t('lightTest.weatherRain')"
      >
        🌧️
      </button>
      <button
        class="lt-btn"
        :class="{ active: light.weatherMode.value === 'snow' }"
        @click="light.setWeather('snow', light.precipLevel.value || 3)"
        :title="$t('lightTest.weatherSnow')"
      >
        ❄️
      </button>
      <button
        class="lt-btn"
        :class="{ active: light.weatherMode.value === 'thunder' }"
        @click="light.setWeather('thunder', light.precipLevel.value || 3)"
        :title="$t('lightTest.weatherThunder')"
      >
        ⛈️
      </button>
    </div>
    <div
      v-if="light.weatherMode.value === 'rain' || light.weatherMode.value === 'snow' || light.weatherMode.value === 'thunder'"
      class="lt-sliders"
    >
      <label class="lt-slider-row"
        ><span>{{ light.weatherMode.value === 'snow' ? $t('lightTest.snowAmount') : $t('lightTest.rainAmount') }}</span
        ><input
          type="range"
          min="1"
          max="6"
          v-model.number="light.precipLevel.value"
          class="lt-slider"
          @input="light.setWeather(light.weatherMode.value, light.precipLevel.value)"
      /></label>
    </div>
    <div class="lt-sliders">
      <label class="lt-slider-row"
        ><span>{{ $t('lightTest.colorTemp') }}</span
        ><input type="range" min="0" max="100" v-model.number="light.lampTemp.value" class="lt-slider"
      /></label>
      <label class="lt-slider-row"
        ><span>{{ $t('lightTest.brightness') }}</span
        ><input type="range" min="0" max="100" v-model.number="light.lampBrightness.value" class="lt-slider"
      /></label>
      <label class="lt-slider-row"
        ><span>{{ $t('lightTest.shadowDepth') }}</span
        ><input type="range" min="0" max="100" v-model.number="light.shadowDepth.value" class="lt-slider"
      /></label>
      <label class="lt-slider-row">
        <span>{{ $t('settings.featherStrength') }}</span>
        <input type="range" min="0" max="100" v-model.number="light.featherStrength.value" class="lt-slider" />
        <output>{{ light.featherStrength.value }}%</output>
      </label>
    </div>

    <!-- 光影控制:设置页里能调的开关/按钮/滑条都在这里,测试时不用来回跳页 -->
    <div class="lt-section">
      <div class="lt-section-title">{{ $t('lightTest.controls') }}</div>
      <div class="lt-toggles">
        <label class="lt-toggle"
          ><input type="checkbox" v-model="light.effectsEnabled.value" /><span>{{ $t('settings.lightEffectsOn') }}</span></label
        >
        <label v-if="themeStore.theme !== 'warm'" class="lt-toggle"
          ><input type="checkbox" v-model="light.blobsEnabled.value" /><span>{{ $t('settings.blobs') }}</span></label
        >
        <label v-if="themeStore.theme !== 'warm'" class="lt-toggle"
          ><input type="checkbox" v-model="light.glassEnabled.value" /><span>{{ $t('settings.glass') }}</span></label
        >
        <label class="lt-toggle"
          ><input type="checkbox" v-model="light.shadowEnabled.value" /><span>{{ $t('settings.shadowEffect') }}</span></label
        >
        <label class="lt-toggle"
          ><input type="checkbox" v-model="light.weatherEffectEnabled.value" /><span>{{ $t('settings.weatherEffect') }}</span></label
        >
      </div>

      <div class="lt-mode-row">
        <span class="lt-label">{{ $t('settings.lampMode') }}</span>
        <button
          v-for="m in LAMP_MODES"
          :key="m.value"
          class="lt-btn lt-mode"
          :class="{ active: light.lampMode.value === m.value }"
          @click="light.lampMode.value = m.value"
        >
          {{ $t(m.labelKey) }}
        </button>
      </div>

      <div class="lt-mode-row">
        <span class="lt-label">{{ $t('settings.projectionStandard') }}</span>
        <button
          v-for="p in PROJECTION_STANDARDS"
          :key="p.value"
          class="lt-btn lt-mode"
          :class="{ active: light.projectionStandard.value === p.value }"
          @click="light.projectionStandard.value = p.value"
        >
          {{ $t(p.labelKey) }}
        </button>
      </div>

      <label class="lt-slider-row"
        ><span>{{ $t('settings.idleLabel') }}</span
        ><input type="number" min="1" max="120" step="1" v-model.number="light.idleMinutes.value" class="lt-num"
      /></label>
    </div>

    <div class="lt-progress">
      <div class="lt-progress-fill" :style="{ width: (light.slotIdx.value / (activeSunInfo?.slots.length || 288)) * 100 + '%' }"></div>
    </div>
  </div>
</template>

<script setup>
import { inject, computed } from 'vue'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'
import { solarSlotForTime } from '@/utils/solarSamples'
import { groundShadowPerHeight } from '@/utils/windowLight'
import { useI18n } from 'vue-i18n'
import { useDragResize } from '@/utils/useDragResize'
import { useThemeStore } from '@/stores/theme'

const { t } = useI18n()
const light = inject(SUN_LIGHT_KEY)
const themeStore = useThemeStore()

const speeds = [0.5, 1, 2, 4, 8]
const LAMP_MODES = [
  { value: 'auto', labelKey: 'lightTest.lampAuto' },
  { value: 'on', labelKey: 'lightTest.lampOn' },
  { value: 'off', labelKey: 'lightTest.lampOff' },
]
const PROJECTION_STANDARDS = [
  { value: 'corrected', labelKey: 'lightTest.corrected' },
  { value: 'real', labelKey: 'lightTest.real' },
]

const initY = typeof window !== 'undefined' ? Math.max(42, window.innerHeight - 360) : 100
const { pos, onDragStart } = useDragResize({ x: 240, y: initY, minY: 42 })

const activeSunInfo = light.activeSunInfo
const testPhase = computed(() => {
  if (!light) return ''
  const s = light.sunScene.value
  if (s.altitude <= -12) return t('home.testPhase.night')
  if (s.altitude < 0) {
    const info = activeSunInfo.value
    const index = light.slotIdx.value
    const next = info?.slots?.[index + 1] ?? info?.nextMidnight
    const previous = info?.slots?.[Math.max(0, index - 1)]
    const rising = next ? next.altitude >= s.altitude : s.altitude >= (previous?.altitude ?? s.altitude)
    return t(rising ? 'home.testPhase.dawn' : 'home.testPhase.dusk')
  }
  if (!activeSunInfo.value?.sunrise || !activeSunInfo.value?.sunset) return t('home.testPhase.daytime')
  const p = s.dayProgress ?? 0
  if (p < 0.1) return t('home.testPhase.sunrise')
  if (p < 0.25) return t('home.testPhase.morning')
  if (p < 0.75) return t('home.testPhase.daytime')
  if (p < 0.9) return t('home.testPhase.evening')
  return t('home.testPhase.sunset')
})

const slotTime = computed(() => {
  const sample = activeSunInfo.value?.slots?.[light.slotIdx.value]
  if (sample) return sample.time
  const minutes = light.slotIdx.value * 5
  return [Math.floor(minutes / 60), minutes % 60].map((part) => String(part).padStart(2, '0')).join(':')
})

const pauseForEdit = () => {
  if (!light.lightTestPaused.value) light.pauseLightTest()
}

const onTimeChange = ({ currentTarget: input }) => {
  if (input.value) {
    const [hour, minute] = input.value.split(':').map(Number)
    light.setSlot(solarSlotForTime(activeSunInfo.value?.slots, hour * 60 + minute, light.slotIdx.value))
  }
  input.value = slotTime.value
}

const onDateChange = ({ currentTarget: input }) => {
  if (input.value) light.loadSunInfoForDate(input.value)
  else input.value = light.previewDate.value
}
</script>

<style scoped>
.light-test-console {
  position: fixed;
  z-index: var(--z-action);
  background: rgba(20, 28, 45, 0.72);
  backdrop-filter: blur(20px) saturate(1.3);
  -webkit-backdrop-filter: blur(20px) saturate(1.3);
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 14px;
  padding: 14px 18px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
  color: #fff;
  min-width: 280px;
  max-height: calc(100vh - 60px);
  overflow-y: auto;
}
/* Keep the live controls visible while the old page snapshot sweeps across the screen. */
:global(body:has(> .theme-sweep-old) > .light-test-console) {
  z-index: calc(var(--z-topmost) + 1);
}
.lt-header {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
  gap: 6px;
}
.lt-title {
  font-size: 13px;
  font-weight: 600;
  opacity: 0.8;
  cursor: grab;
  user-select: none;
  white-space: nowrap;
  touch-action: none; /* Pointer Events 拖拽把手:触屏不被页面滚动抢走 */
}
.lt-title:active {
  cursor: grabbing;
}
.lt-date-input {
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 8px;
  color: #fff;
  font-size: 12px;
  padding: 3px 6px;
  cursor: pointer;
  color-scheme: dark;
}
.lt-time-input {
  flex: 1;
  text-align: center;
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 8px;
  color: #fff;
  font-size: 18px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  padding: 4px 8px;
  cursor: pointer;
  color-scheme: dark;
}
.lt-info {
  display: flex;
  gap: 12px;
  font-size: 11px;
  opacity: 0.7;
  margin-bottom: 6px;
  flex-wrap: wrap;
}
.lt-phase {
  color: var(--color-brand);
  font-weight: 600;
}
.lt-controls {
  display: flex;
  gap: 6px;
  justify-content: center;
  margin-bottom: 8px;
}
.lt-speed {
  display: flex;
  gap: 4px;
  justify-content: center;
  margin-bottom: 8px;
}
.lt-speed .lt-btn {
  padding: 3px 8px;
  font-size: 11px;
}
.lt-weather {
  display: flex;
  gap: 4px;
  justify-content: center;
  margin-bottom: 8px;
  flex-wrap: wrap;
}
.lt-sliders {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 8px;
}
.lt-slider-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
  opacity: 0.8;
}
.lt-slider-row > span {
  min-width: 52px;
}
.lt-slider-row output {
  min-width: 4ch;
  text-align: end;
  font-variant-numeric: tabular-nums;
}
.lt-slider {
  flex: 1;
  cursor: pointer;
}
.lt-btn {
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 8px;
  color: #fff;
  padding: 5px 12px;
  font-size: 15px;
  cursor: pointer;
  transition: background 0.2s;
}
.lt-btn:hover {
  background: rgba(255, 255, 255, 0.2);
}
.lt-btn.active {
  background: rgba(255, 200, 100, 0.3);
  border-color: rgba(255, 200, 100, 0.5);
}
.lt-btn.lt-main {
  font-size: 17px;
}
.lt-btn.lt-reset {
  background: rgba(244, 67, 54, 0.3);
  border-color: rgba(244, 67, 54, 0.5);
}
.lt-btn.lt-reset:hover {
  background: rgba(244, 67, 54, 0.5);
}

/* 光影控制区:开关(复选框)+ 台灯模式/窗影标准(按钮)+ 夜间关灯(数字框) */
.lt-section {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid rgba(255, 255, 255, 0.16);
}
.lt-section-title {
  margin-bottom: 6px;
  font-size: 11px;
  letter-spacing: 0.04em;
  opacity: 0.7;
}
.lt-toggles {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 12px;
  margin-bottom: 8px;
}
.lt-toggle {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
  opacity: 0.85;
  cursor: pointer;
}
.lt-toggle input {
  accent-color: var(--color-brand);
  cursor: pointer;
}
.lt-mode-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  margin-bottom: 8px;
  font-size: 11px;
}
.lt-label {
  margin-right: 2px;
  opacity: 0.75;
}
.lt-btn.lt-mode {
  padding: 3px 8px;
  font-size: 11px;
}
.lt-num {
  width: 64px;
  padding: 2px 6px;
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 8px;
  color: #fff;
  font-size: 12px;
  color-scheme: dark;
}
.lt-progress {
  height: 3px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 2px;
  overflow: hidden;
}
.lt-progress-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--color-brand), var(--color-accent));
  transition: width 0.2s;
}
</style>
