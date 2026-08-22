<!-- 光照测试控制台:全局组件,任何页面可用 -->
<template>
  <div v-if="light?.lightTestMode?.value" class="light-test-console">
    <div class="lt-header">
      <span class="lt-title">光照测试</span>
      <input type="time" v-model="timeInput" @change="onTimeChange" class="lt-time-input" />
      <button class="lt-btn lt-reset" @click="light.stopLightTest" title="重置到真实时间并关闭">⏹</button>
    </div>
    <div class="lt-info lt-location" v-if="light?.sunInfo?.value">
      <span>{{ light.sunInfo.value.city || '未知地区' }}</span>
      <span>{{ light.sunInfo.value.date || '--' }}</span>
    </div>
    <div class="lt-info">
      <span>高度 {{ light.sunScene.value.altitude?.toFixed(1) }}°</span>
      <span>方位 {{ light.sunScene.value.azimuth?.toFixed(1) }}°</span>
      <span>窗角 {{ light.sunScene.value.windowAngle?.toFixed(1) }}°</span>
      <span class="lt-phase">{{ testPhase }}</span>
    </div>
    <div class="lt-info" v-if="light?.sunInfo?.value">
      <span>日出 {{ light.sunInfo.value.sunrise || '--' }}</span>
      <span>日落 {{ light.sunInfo.value.sunset || '--' }}</span>
    </div>
    <div class="lt-controls">
      <button class="lt-btn" @click="light.stepLightTest(-1)" title="后退 5 分钟">⏮</button>
      <button class="lt-btn lt-main" @click="light.pauseLightTest">{{ light.lightTestPaused.value ? '▶' : '⏸' }}</button>
      <button class="lt-btn" @click="light.stepLightTest(1)" title="前进 5 分钟">⏭</button>
      <div class="lt-speed-group">
        <button v-for="sp in speeds" :key="sp"
          class="lt-btn lt-btn-xs" :class="{ active: light.testSpeed.value === sp }"
          @click="light.setTestSpeed(sp)">{{ sp }}x</button>
      </div>
    </div>
    <div class="lt-weather">
      <button class="lt-btn" :class="{ active: light.weatherMode.value === 'clear' }" @click="light.setWeather('clear', 0)" title="晴天">☀️</button>
      <button class="lt-btn" :class="{ active: light.weatherMode.value === 'cloud' }" @click="light.setWeather('cloud', 0)" title="多云">☁️</button>
      <button class="lt-btn" :class="{ active: light.weatherMode.value === 'rain' }" @click="light.setWeather('rain', light.precipLevel.value || 3)" title="下雨">🌧️</button>
      <button class="lt-btn" :class="{ active: light.weatherMode.value === 'snow' }" @click="light.setWeather('snow', light.precipLevel.value || 3)" title="下雪">❄️</button>
      <button class="lt-btn" :class="{ active: light.weatherMode.value === 'thunder' }" @click="light.setWeather('thunder', light.precipLevel.value || 3)" title="雷雨">⛈️</button>
    </div>
    <div v-if="light.weatherMode.value === 'rain' || light.weatherMode.value === 'snow' || light.weatherMode.value === 'thunder'" class="lt-sliders">
      <label class="lt-slider-row"><span>{{ light.weatherMode.value === 'snow' ? '雪量' : '雨量' }}</span><input type="range" min="1" max="6" v-model.number="light.precipLevel.value" class="lt-slider" @input="light.setWeather(light.weatherMode.value, light.precipLevel.value)" /></label>
    </div>
    <div class="lt-divider"></div>
    <div class="lt-section-label">图层</div>
    <div class="lt-toggles">
      <label class="lt-toggle"><input type="checkbox" v-model="light.shadowEnabled.value" /><span>阴影</span></label>
      <label class="lt-toggle"><input type="checkbox" v-model="light.blobsEnabled.value" /><span>环境光</span></label>
    </div>
    <div class="lt-divider"></div>
    <div class="lt-section-label">台灯</div>
    <div class="lt-lamp-mode">
      <button v-for="m in lampModes" :key="m.value"
        class="lt-btn lt-btn-sm" :class="{ active: light.lampMode.value === m.value }"
        @click="light.lampMode.value = m.value">{{ m.label }}</button>
    </div>
    <div class="lt-sliders">
      <label class="lt-slider-row"><span>色温</span><input type="range" min="0" max="100" v-model.number="light.lampTemp.value" class="lt-slider" /></label>
      <label class="lt-slider-row"><span>亮度</span><input type="range" min="0" max="100" v-model.number="light.lampBrightness.value" class="lt-slider" /></label>
    </div>
    <div class="lt-progress"><div class="lt-progress-fill" :style="{ width: ((light.slotIdx.value / 288) * 100) + '%' }"></div></div>
  </div>
</template>

<script setup>
import { inject, computed, ref, watch } from 'vue'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const light = inject(SUN_LIGHT_KEY)

const speeds = [0.5, 1, 2, 4, 8]

const lampModes = [
  { value: 'auto', label: '自动' },
  { value: 'on', label: '开' },
  { value: 'off', label: '关' },
]

const testPhase = computed(() => {
  if (!light) return ''
  const s = light.sunScene.value
  if (s.isNight) {
    return s.dayProgress > 0.5 ? t('home.testPhase.midnight') : t('home.testPhase.dawn')
  }
  const p = s.dayProgress ?? 0
  if (p < 0.08) return t('home.testPhase.sunrise')
  if (p < 0.2) return t('home.testPhase.morning')
  if (p < 0.4) return t('home.testPhase.forenoon')
  if (p < 0.6) return t('home.testPhase.noon')
  if (p < 0.8) return t('home.testPhase.afternoon')
  if (p < 0.92) return t('home.testPhase.sunset')
  return t('home.testPhase.dusk')
})

const timeInput = ref('')
watch(() => light?.slotIdx?.value, (idx) => {
  if (idx == null) return
  const totalMin = idx * 5
  const h = Math.floor(totalMin / 60)
  const m = totalMin % 60
  timeInput.value = `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`
}, { immediate: true })

const onTimeChange = () => {
  const [h, m] = timeInput.value.split(':').map(Number)
  if (isNaN(h) || isNaN(m)) return
  const idx = Math.floor((h * 60 + m) / 5)
  light.setSlot?.(idx)
}
</script>

<style scoped>
.light-test-console {
  position: fixed; left: 240px; bottom: 24px; z-index: 80;
  background: rgba(20, 28, 45, 0.72);
  backdrop-filter: blur(20px) saturate(1.3);
  -webkit-backdrop-filter: blur(20px) saturate(1.3);
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 14px; padding: 14px 18px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
  color: #fff; min-width: 280px;
}
.lt-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; gap: 8px; }
.lt-title { font-size: 13px; font-weight: 600; opacity: 0.8; }
.lt-time-input {
  flex: 1; text-align: center;
  background: rgba(255,255,255,0.1); border: 1px solid rgba(255,255,255,0.2);
  border-radius: 8px; color: #fff; font-size: 18px; font-weight: 700;
  font-variant-numeric: tabular-nums; padding: 4px 8px; cursor: pointer;
  color-scheme: dark;
}
.lt-info { display: flex; gap: 12px; font-size: 11px; opacity: 0.7; margin-bottom: 6px; flex-wrap: wrap; }
.lt-location { justify-content: center; font-size: 12px; opacity: 0.85; font-weight: 600; }
.lt-phase { color: #C9A876; font-weight: 600; }
.lt-controls { display: flex; gap: 6px; justify-content: center; align-items: center; margin-bottom: 8px; }
.lt-speed-group { display: flex; gap: 3px; margin-left: 8px; padding-left: 8px; border-left: 1px solid rgba(255,255,255,0.15); }
.lt-weather { display: flex; gap: 6px; justify-content: center; margin-bottom: 8px; }
.lt-divider { height: 1px; background: rgba(255,255,255,0.1); margin: 8px 0; }
.lt-section-label { font-size: 10px; opacity: 0.5; text-transform: uppercase; letter-spacing: 1px; margin-bottom: 6px; text-align: center; }
.lt-lamp-mode { display: flex; gap: 6px; justify-content: center; margin-bottom: 8px; }
.lt-sliders { display: flex; flex-direction: column; gap: 6px; margin-bottom: 8px; }
.lt-slider-row { display: flex; align-items: center; gap: 8px; font-size: 11px; opacity: 0.8; }
.lt-slider { flex: 1; cursor: pointer; }
.lt-toggles { display: flex; gap: 10px; flex-wrap: wrap; justify-content: center; margin-bottom: 4px; }
.lt-toggle { display: flex; align-items: center; gap: 4px; font-size: 11px; opacity: 0.8; cursor: pointer; }
.lt-toggle input { cursor: pointer; width: 13px; height: 13px; }
.lt-btn {
  background: rgba(255,255,255,0.1);
  border: 1px solid rgba(255,255,255,0.2);
  border-radius: 8px; color: #fff;
  padding: 5px 12px; font-size: 15px; cursor: pointer;
  transition: background 0.2s;
}
.lt-btn:hover { background: rgba(255,255,255,0.2); }
.lt-btn.active { background: rgba(255,200,100,0.3); border-color: rgba(255,200,100,0.5); }
.lt-btn.lt-main { font-size: 17px; }
.lt-btn.lt-reset { background: rgba(244,67,54,0.3); border-color: rgba(244,67,54,0.5); }
.lt-btn.lt-reset:hover { background: rgba(244,67,54,0.5); }
.lt-btn-sm { padding: 4px 10px; font-size: 12px; }
.lt-btn-xs { padding: 3px 7px; font-size: 10px; border-radius: 6px; }
.lt-progress { height: 3px; background: rgba(255,255,255,0.1); border-radius: 2px; overflow: hidden; }
.lt-progress-fill { height: 100%; background: linear-gradient(90deg, #C9A876, #A8483A); transition: width 0.2s; }
@media (max-width: 900px) {
  .light-test-console { left: 12px; right: 12px; min-width: auto; }
}
</style>
