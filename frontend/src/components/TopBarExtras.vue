<!-- 顶部条幅右侧:时钟 + 天气 -->
<template>
  <div class="top-extras">
    <div class="clock">{{ time }}</div>
    <div class="date">{{ dateStr }}</div>
    <div v-if="weather" class="weather">
      <span class="weather-icon">{{ weatherIcon }}</span>
      <span class="weather-temp">{{ weather.temp }}°</span>
      <span class="weather-text">{{ weatherText }}</span>
    </div>
    <div v-else class="weather loading">--</div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'

const props = defineProps()
const emit = defineEmits(['weather'])

const time = ref('')
const dateStr = ref('')
const weather = ref(null)
let timer = null

const weatherIcon = computed(() => {
  if (!weather.value) return ''
  const map = { clear: '☀️', cloud: '☁️', overcast: '☁️', rain: '🌧️', snow: '❄️', fog: '🌫️', thunder: '⛈️' }
  return map[weather.value.condition] || '☀️'
})
const weatherText = computed(() => {
  if (!weather.value) return ''
  const map = { clear: '晴', cloud: '多云', overcast: '阴', rain: '雨', snow: '雪', fog: '雾', thunder: '雷' }
  return map[weather.value.condition] || ''
})

const updateClock = () => {
  const d = new Date()
  const h = String(d.getHours()).padStart(2, '0')
  const m = String(d.getMinutes()).padStart(2, '0')
  time.value = `${h}:${m}`
  const weekdays = ['日', '一', '二', '三', '四', '五', '六']
  dateStr.value = `${d.getMonth() + 1}月${d.getDate()}日 周${weekdays[d.getDay()]}`
}

const loadWeather = async () => {
  try {
    const res = await fetch('/api/public/weather')
    if (!res.ok) return
    const json = await res.json()
    if (json.code === 0 && json.data) {
      weather.value = json.data
      emit('weather', json.data.condition)
    }
  } catch (e) {
    // 后端未实现天气接口时静默失败,不影响首页
  }
}

onMounted(() => {
  updateClock()
  timer = setInterval(updateClock, 1000)
  loadWeather()
})
onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.top-extras {
  display: flex;
  align-items: center;
  gap: 14px;
  color: #F5EFE0;
  font-family: Georgia, serif;
}
.clock {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 1px;
}
.date {
  font-size: 11px;
  opacity: 0.7;
  margin-right: 8px;
}
.weather {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
}
.weather.loading { opacity: 0.5; }
.weather-icon { font-size: 18px; }
.weather-temp { font-weight: 700; }
.weather-text { opacity: 0.8; font-size: 12px; }
@media (max-width: 768px) {
  .date, .weather-text { display: none; }
  .clock { font-size: 18px; }
}
</style>
