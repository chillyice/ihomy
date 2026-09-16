<!-- 植物养殖小游戏:全家共养一棵,实时养成;营养/天气/积分/家庭交互/成长日志。 -->
<template>
  <div class="page">
    <Breadcrumb :items="[{ label: $t('tools.title') }, { label: $t('plant.title') }]" />

    <!-- 已种植:植物 + 天气/营养 + 成长进度 + 照料按钮 -->
    <div v-if="state.planted" class="plant-card card">
      <div class="plant-visual">
        <PlantSprite :species="state.species" :stage="state.stage" :wilted="state.wilted" />
      </div>

      <div class="plant-info">
        <div class="plant-head">
          <span class="species-name">{{ $t('plant.species.' + state.species) }}</span>
          <span class="stage-name">{{ $t('plant.stageName.' + state.stage) }}</span>
          <el-tag v-if="state.wilted" type="danger" effect="plain">{{ $t('plant.wilted') }}</el-tag>
          <el-tag v-else-if="state.thirsty" type="warning" effect="plain">{{ $t('plant.thirsty') }}</el-tag>
          <el-tag v-if="state.harvestable" type="success" effect="plain">{{ $t('plant.matured') }}</el-tag>
        </div>

        <!-- 天气 + 营养 + 积分 概览条 -->
        <div class="env-strip">
          <div class="env-item" :title="weatherEffect">
            <span class="env-emoji">{{ weatherEmoji }}</span>
            <span class="env-text">
              <template v-if="state.weather && state.weather.condition">
                <b>{{ state.weather.temp }}°</b> {{ state.weather.text }}
              </template>
              <template v-else>{{ $t('plant.weather.none') }}</template>
            </span>
          </div>
          <div class="env-item">
            <span class="env-emoji">🧪</span>
            <span class="env-text">{{ $t('plant.nutrient') }} · {{ $t('plant.nutrientLevel.' + state.nutrientLevel) }}</span>
          </div>
          <div class="env-item">
            <span class="env-emoji">⭐</span>
            <span class="env-text">{{ $t('plant.pointsBalance', { n: pointsBalance }) }}</span>
          </div>
        </div>

        <div v-if="state.rainWatering" class="rain-note">💧 {{ $t('plant.rainWatering') }}</div>
        <div v-else-if="weatherEffect" class="weather-note">{{ weatherEffect }}</div>

        <!-- 营养进度条 -->
        <div class="nutrient-row">
          <div class="nutrient-bar">
            <div class="nutrient-fill" :style="{ width: state.nutrient + '%', background: nutrientColor }" />
          </div>
          <span class="nutrient-val">{{ state.nutrient }}</span>
        </div>

        <!-- 阶段圆点(6 段) -->
        <div class="stage-dots">
          <span
            v-for="(s, i) in stages"
            :key="s"
            class="stage-dot"
            :class="{ done: i <= state.stageIndex, cur: i === state.stageIndex }"
            :title="$t('plant.stageName.' + s)"
          />
        </div>

        <!-- 当前阶段成长进度条 -->
        <div class="growth-bar">
          <div class="growth-fill" :style="{ width: state.progressPct + '%' }" />
        </div>

        <div v-if="!state.harvestable && state.minutesToNextStage > 0" class="next-hint">
          {{ $t('plant.nextStage', { t: formatDuration(state.minutesToNextStage) }) }}
        </div>

        <div class="stats">
          <span class="stat">{{ $t('plant.daysGrown', { n: state.daysGrown }) }}</span>
          <span class="stat">{{ $t('plant.waterCount', { n: state.waterCount }) }}</span>
          <span class="stat">{{ $t('plant.sunCount', { n: state.sunCount }) }}</span>
          <span class="stat">{{ $t('plant.fertilizeCount', { n: state.fertilizeCount }) }}</span>
          <span class="stat">{{ $t('plant.harvestCount', { n: state.harvestCount }) }}</span>
        </div>

        <!-- 寄语输入 -->
        <el-input
          v-model="careMessage"
          class="care-message"
          size="small"
          :placeholder="$t('plant.messagePlaceholder')"
          maxlength="200"
          clearable
        />

        <div class="care-actions">
          <el-button
            type="primary"
            size="large"
            round
            :disabled="!state.waterReady"
            :loading="acting === 'water'"
            @click="onWater"
          >
            {{ state.waterReady ? $t('plant.water') : $t('plant.waterCooldown', { min: state.waterCooldownMin }) }}
          </el-button>
          <el-button
            type="warning"
            size="large"
            round
            :disabled="!state.sunReady"
            :loading="acting === 'sun'"
            @click="onSun"
          >
            {{ state.sunReady ? $t('plant.sun') : $t('plant.sunCooldown', { min: state.sunCooldownMin }) }}
          </el-button>
          <el-button
            type="success"
            size="large"
            round
            :disabled="!state.fertilizeReady"
            :loading="acting === 'fertilize'"
            @click="onFertilize"
          >
            <template v-if="state.fertilizeReady">
              {{ $t('plant.fertilize') }} · {{ $t('plant.fertilizeCost', { n: fertilizeCost }) }}
            </template>
            <template v-else>{{ $t('plant.fertilizeCooldown', { min: state.fertilizeCooldownMin }) }}</template>
          </el-button>

          <template v-if="state.harvestable">
            <div class="harvest-next">
              <span class="next-label">{{ $t('plant.nextSpecies') }}</span>
              <span
                v-for="s in species"
                :key="s"
                class="next-chip"
                :class="{ active: harvestSpecies === s }"
                @click="harvestSpecies = s"
              >{{ $t('plant.species.' + s) }}</span>
            </div>
            <el-button
              type="danger"
              size="large"
              round
              :loading="acting === 'harvest'"
              @click="onHarvest"
            >
              {{ $t('plant.harvest') }}
            </el-button>
          </template>
        </div>
      </div>
    </div>

    <!-- 未种植:品种选择 + 种下 -->
    <div v-else class="plant-empty card">
      <div class="empty-art"><PlantSprite species="SUNFLOWER" stage="FLOWER" /></div>
      <h2 class="empty-title">{{ $t('plant.emptyTitle') }}</h2>
      <p class="empty-desc">{{ $t('plant.emptyDesc') }}</p>

      <div class="species-picker">
        <div
          v-for="s in species"
          :key="s"
          class="species-card"
          :class="{ active: selected === s }"
          @click="selected = s"
        >
          <div class="species-sprite"><PlantSprite :species="s" stage="FLOWER" /></div>
          <span>{{ $t('plant.species.' + s) }}</span>
        </div>
      </div>

      <el-button type="primary" size="large" round :loading="acting === 'plant'" @click="onPlant">
        {{ $t('plant.plantNow') }}
      </el-button>
    </div>

    <!-- 成长日志(家庭内容 + 交互时间线) -->
    <div v-if="state.planted" class="log-card card">
      <div class="section-label">{{ $t('plant.log') }}</div>
      <div v-if="!state.logs || !state.logs.length" class="log-empty">{{ $t('plant.logEmpty') }}</div>
      <ul v-else class="log-list">
        <li v-for="log in state.logs" :key="log.id" class="log-item">
          <span class="log-actor">{{ log.nickname || fallbackName }}</span>
          <span class="log-action">{{ $t('plant.logAction.' + log.action) }}</span>
          <span v-if="log.message" class="log-msg">「{{ log.message }}」</span>
          <span class="log-time">{{ formatTime(log.createdAt) }}</span>
        </li>
      </ul>
    </div>
  </div>
</template>

<script setup>
// 植物养殖:读取状态 + 照料动作;30s 轮询对齐成长与冷却(家庭成员互见最新进度)
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { plantApi, pointsApi } from '@/api'
import Breadcrumb from '@/components/Breadcrumb.vue'
import PlantSprite from './PlantSprite.vue'

const { t } = useI18n()

const species = ['SUNFLOWER', 'ROSE', 'SUCCULENT']
const stages = ['SEED', 'SPROUT', 'SEEDLING', 'BUD', 'FLOWER', 'FRUIT']
const fertilizeCost = 15

const state = ref({
  planted: false,
  totalStages: 6,
  stageIndex: 0,
  progressPct: 0,
  nutrient: 50,
  nutrientLevel: 'MEDIUM',
  weather: null,
  rainWatering: false,
  waterReady: true,
  sunReady: true,
  fertilizeReady: true,
  waterCooldownMin: 0,
  sunCooldownMin: 0,
  fertilizeCooldownMin: 0,
  logs: [],
})
const selected = ref('SUNFLOWER')
const harvestSpecies = ref('SUNFLOWER')
const acting = ref('')
const careMessage = ref('')
const pointsBalance = ref(0)

let timer = null

const fallbackName = computed(() => t('feed.authorFallback'))

const weatherEmoji = computed(() => {
  const c = state.value.weather?.condition
  return { clear: '☀️', cloud: '⛅', rain: '🌧️', snow: '❄️', fog: '🌫️', thunder: '⛈️' }[c] || '🏠'
})

const weatherEffect = computed(() => {
  const c = state.value.weather?.condition
  return c ? t('plant.weather.' + c) : t('plant.weather.none')
})

const nutrientColor = computed(() => {
  return state.value.nutrientLevel === 'HIGH' ? '#7fb069'
    : state.value.nutrientLevel === 'MEDIUM' ? '#e6a23c' : '#f56c6c'
})

const formatDuration = (min) => {
  if (!min || min <= 0) return ''
  const h = Math.floor(min / 60)
  const m = min % 60
  if (h > 0) return m > 0 ? `${h}${t('plant.hour')}${m}${t('plant.minute')}` : `${h}${t('plant.hour')}`
  return `${m}${t('plant.minute')}`
}

const formatTime = (s) => {
  if (!s) return ''
  const d = String(s).replace('T', ' ')
  return d.length >= 16 ? d.slice(5, 16) : d
}

// 植物就绪后,「下一轮品种」默认沿用当前品种
watch(() => state.value.species, (s) => { if (s) harvestSpecies.value = s })

const loadState = async () => {
  try {
    state.value = await plantApi.state()
  } catch (e) {
    // 未登录或接口异常时保持空状态,不打断页面
  }
  try {
    const pts = await pointsApi.stats()
    if (pts && typeof pts.balance === 'number') pointsBalance.value = pts.balance
  } catch (e) {
    // 未登录时积分接口不可用,忽略
  }
}

const doAction = async (key, fn, successKey) => {
  acting.value = key
  try {
    state.value = await fn()
    careMessage.value = ''
    ElMessage.success(t(successKey))
    await loadState()
  } catch (e) {
    // 冷却/未成熟/积分不足等冲突已由 request.js 弹提示;刷新状态对齐
    await loadState()
  } finally {
    acting.value = ''
  }
}

const onPlant = () => doAction('plant', () => plantApi.plant(selected.value), 'plant.plantSuccess')
const onWater = () => doAction('water', () => plantApi.water(careMessage.value), 'plant.waterSuccess')
const onSun = () => doAction('sun', () => plantApi.sun(careMessage.value), 'plant.sunSuccess')
const onFertilize = () => doAction('fertilize', () => plantApi.fertilize(careMessage.value), 'plant.fertilizeSuccess')
const onHarvest = () => doAction('harvest', () => plantApi.harvest(harvestSpecies.value, careMessage.value), 'plant.harvestSuccess')

onMounted(() => {
  loadState()
  timer = setInterval(loadState, 30000)
})
onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.plant-card {
  display: flex;
  gap: 32px;
  align-items: center;
  padding: 32px;
}
.plant-visual {
  flex: 0 0 220px;
  height: 260px;
}
.plant-visual svg {
  width: 100%;
  height: 100%;
}
.plant-info {
  flex: 1;
  min-width: 0;
}
.plant-head {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.species-name {
  font-size: 18px;
  font-weight: 700;
  color: var(--color-text-primary, #333);
}
.stage-name {
  font-size: 14px;
  color: var(--color-text-secondary, #888);
}

/* 天气/营养/积分概览条 */
.env-strip {
  display: flex;
  gap: 18px;
  flex-wrap: wrap;
  margin: 14px 0 4px;
}
.env-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.04);
  font-size: 13px;
  color: var(--color-text-primary, #333);
}
html.dark .env-item {
  background: rgba(255, 255, 255, 0.08);
}
.env-emoji {
  font-size: 15px;
}
.env-text b {
  font-weight: 700;
}
.rain-note {
  margin: 8px 0 0;
  font-size: 13px;
  color: #4a9fd8;
}
.weather-note {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--color-text-secondary, #888);
}

/* 营养条 */
.nutrient-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 12px 0 4px;
}
.nutrient-bar {
  flex: 1;
  height: 8px;
  border-radius: 5px;
  background: rgba(0, 0, 0, 0.08);
  overflow: hidden;
}
.nutrient-fill {
  height: 100%;
  border-radius: 5px;
  transition: width 0.5s ease, background 0.3s ease;
}
html.dark .nutrient-bar {
  background: rgba(255, 255, 255, 0.1);
}
.nutrient-val {
  font-size: 12px;
  min-width: 28px;
  text-align: right;
  color: var(--color-text-secondary, #888);
}

.stage-dots {
  display: flex;
  gap: 10px;
  margin: 14px 0 10px;
}
.stage-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.12);
  transition: all 0.3s ease;
}
.stage-dot.done {
  background: #7fb069;
}
.stage-dot.cur {
  transform: scale(1.4);
  box-shadow: 0 0 0 3px rgba(127, 176, 105, 0.25);
}
html.dark .stage-dot {
  background: rgba(255, 255, 255, 0.16);
}
html.dark .stage-dot.done {
  background: #8fb573;
}
.growth-bar {
  height: 10px;
  border-radius: 6px;
  background: rgba(0, 0, 0, 0.08);
  overflow: hidden;
}
.growth-fill {
  height: 100%;
  border-radius: 6px;
  background: linear-gradient(90deg, #a8cf7f, #7fb069);
  transition: width 0.5s ease;
}
html.dark .growth-bar {
  background: rgba(255, 255, 255, 0.1);
}
.stats {
  display: flex;
  gap: 20px;
  flex-wrap: wrap;
  margin: 14px 0 16px;
}
.stat {
  font-size: 13px;
  color: var(--color-text-secondary, #888);
}
.care-message {
  max-width: 320px;
  margin-bottom: 14px;
}
.care-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}
.next-hint {
  margin-top: 8px;
  font-size: 12px;
  color: var(--color-text-secondary, #888);
}
.harvest-next {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  flex-basis: 100%;
  margin-top: 4px;
}
.next-label {
  font-size: 13px;
  color: var(--color-text-secondary, #888);
}
.next-chip {
  padding: 4px 14px;
  border-radius: 999px;
  font-size: 13px;
  cursor: pointer;
  border: 1px solid rgba(0, 0, 0, 0.12);
  color: var(--color-text-primary, #333);
  transition: all 0.2s ease;
}
.next-chip.active {
  border-color: #7fb069;
  background: rgba(127, 176, 105, 0.16);
  color: #5d8a44;
}
html.dark .next-chip {
  border-color: rgba(255, 255, 255, 0.16);
}

/* 成长日志 */
.log-card {
  margin-top: 16px;
  padding: 20px 24px;
}
.log-empty {
  font-size: 13px;
  color: var(--color-text-secondary, #888);
  padding: 8px 0;
}
.log-list {
  list-style: none;
  margin: 12px 0 0;
  padding: 0;
}
.log-item {
  display: flex;
  align-items: baseline;
  gap: 8px;
  flex-wrap: wrap;
  padding: 8px 0;
  border-bottom: 1px dashed rgba(0, 0, 0, 0.08);
  font-size: 13px;
}
.log-item:last-child {
  border-bottom: none;
}
html.dark .log-item {
  border-bottom-color: rgba(255, 255, 255, 0.08);
}
.log-actor {
  font-weight: 600;
  color: var(--color-text-primary, #333);
}
.log-action {
  color: var(--color-text-secondary, #888);
}
.log-msg {
  color: var(--color-accent, var(--color-brand));
}
.log-time {
  margin-left: auto;
  font-size: 12px;
  color: var(--color-text-secondary, #888);
}

/* 未种植空态 */
.plant-empty {
  text-align: center;
  padding: 40px 24px;
}
.empty-art {
  width: 140px;
  height: 180px;
  margin: 0 auto 8px;
}
.empty-art svg {
  width: 100%;
  height: 100%;
}
.empty-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--color-text-primary, #333);
  margin: 4px 0;
}
.empty-desc {
  color: var(--color-text-secondary, #888);
  margin: 0 0 24px;
}
.species-picker {
  display: flex;
  gap: 16px;
  justify-content: center;
  flex-wrap: wrap;
  margin-bottom: 24px;
}
.species-card {
  width: 120px;
  padding: 12px 8px;
  border-radius: 12px;
  border: 2px solid transparent;
  cursor: pointer;
  transition: all 0.2s ease;
  background: rgba(0, 0, 0, 0.03);
}
.species-card.active {
  border-color: #7fb069;
  background: rgba(127, 176, 105, 0.12);
}
.species-sprite {
  width: 72px;
  height: 92px;
  margin: 0 auto;
}
.species-sprite svg {
  width: 100%;
  height: 100%;
}
.species-card span {
  display: block;
  margin-top: 6px;
  font-size: 13px;
  color: var(--color-text-primary, #333);
}

@media (max-width: 768px) {
  .plant-card {
    flex-direction: column;
    gap: 8px;
    padding: 24px 16px;
  }
  .plant-visual {
    flex: none;
    width: 180px;
    height: 210px;
  }
  .stats {
    gap: 14px;
  }
}
</style>
