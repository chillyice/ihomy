<!-- 每日早安设置(所有客户端「外观与显示」里):开关、问候语、显示内容、天气动效、预览。
     偏好只存本机 localStorage(utils/morning.js),改动即落盘,没有保存按钮。 -->
<template>
  <div class="card settings-card">
    <div class="section-label">{{ $t('morning.settingsTitle') }}</div>
    <p class="share-tip mo-lead">{{ $t('morning.lead') }}</p>

    <el-form label-position="top" class="settings-form">
      <el-form-item>
        <div class="setting-row">
          <el-switch v-model="prefs.enabled" @change="persist" />
          <span class="setting-label">{{ $t('morning.enable') }}</span>
        </div>
        <div class="share-tip">{{ $t('morning.enableHint') }}</div>
      </el-form-item>

      <el-form-item :label="$t('morning.greetingLabel')">
        <el-input
          v-model="prefs.greeting"
          maxlength="60"
          show-word-limit
          clearable
          :placeholder="$t('morning.greetingPlaceholder')"
          @change="persist"
        />
        <div class="share-tip">{{ $t('morning.greetingHint') }}</div>
      </el-form-item>

      <el-form-item :label="$t('morning.cards')">
        <el-checkbox-group v-model="cardList">
          <el-checkbox v-for="c in MORNING_CARDS" :key="c" :value="c">{{ $t(CARD_LABELS[c]) }}</el-checkbox>
        </el-checkbox-group>
        <div class="share-tip">{{ $t('morning.cardsHint') }}</div>
      </el-form-item>

      <el-form-item>
        <div class="setting-row">
          <el-switch v-model="prefs.animation" @change="persist" />
          <span class="setting-label">{{ $t('morning.animation') }}</span>
        </div>
        <div class="share-tip">{{ $t('morning.animationHint') }}</div>
      </el-form-item>

      <!-- 云层样式二选一:两个开关永远只有一个开着,关掉当前这个就自动落到另一种 -->
      <el-form-item :label="$t('morning.cloudStyle')">
        <div class="setting-row">
          <el-switch v-model="photoCloud" />
          <span class="setting-label">{{ $t('morning.cloudStylePhoto') }}</span>
        </div>
        <div class="setting-row mo-cloud-row">
          <el-switch v-model="randomCloud" />
          <span class="setting-label">{{ $t('morning.cloudStyleRandom') }}</span>
        </div>
        <div class="share-tip">{{ $t('morning.cloudStyleHint') }}</div>
      </el-form-item>

      <el-form-item :label="$t('morning.previewScene')">
        <div class="setting-row mo-scene-row">
          <el-select v-model="sceneCondition" class="mo-scene-select">
            <el-option :label="$t('morning.sceneLive')" value="live" />
            <el-option v-for="c in MORNING_SCENES" :key="c" :label="$t(MORNING_SCENE_LABELS[c])" :value="c" />
          </el-select>
          <el-radio-group v-model="scenePeriod">
            <el-radio-button value="live">{{ $t('morning.periodLive') }}</el-radio-button>
            <el-radio-button value="day">{{ $t('morning.periodDay') }}</el-radio-button>
            <el-radio-button value="night">{{ $t('morning.periodNight') }}</el-radio-button>
          </el-radio-group>
        </div>
        <div class="share-tip">{{ $t('morning.previewSceneHint') }}</div>
      </el-form-item>

      <el-form-item>
        <el-button @click="onPreview">{{ $t('morning.preview') }}</el-button>
        <div class="share-tip">{{ $t('morning.previewHint') }}</div>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { loadPrefs, MORNING_CARDS, MORNING_SCENE_LABELS, MORNING_SCENES, requestPreview, savePrefs, setPreviewScene } from '@/utils/morning'

const CARD_LABELS = {
  greeting: 'morning.cardGreeting',
  date: 'morning.cardDate',
  todo: 'morning.cardTodo',
  weather: 'morning.cardWeather',
}

const prefs = reactive(loadPrefs())

// 预览场景只存在本页内存里:一是调视觉用的,二是不该悄悄改掉真实天气
const sceneCondition = ref('live')
const scenePeriod = ref('live')

const persist = () => {
  savePrefs({
    enabled: prefs.enabled,
    greeting: prefs.greeting,
    animation: prefs.animation,
    cloudStyle: prefs.cloudStyle,
    cards: { ...prefs.cards },
  })
}

// 云层样式:两个开关互斥,关掉当前这个就切到另一种(始终有一种生效)
const pickCloudStyle = (style, on) => {
  prefs.cloudStyle = on ? style : style === 'photo' ? 'random' : 'photo'
  persist()
}
const photoCloud = computed({
  get: () => prefs.cloudStyle === 'photo',
  set: (on) => pickCloudStyle('photo', on),
})
const randomCloud = computed({
  get: () => prefs.cloudStyle === 'random',
  set: (on) => pickCloudStyle('random', on),
})

// 勾选=显示;取消勾选写回 false(允许全关,只留背景与「开始今天」)
const cardList = computed({
  get: () => MORNING_CARDS.filter((k) => prefs.cards[k] !== false),
  set: (list) => {
    MORNING_CARDS.forEach((k) => {
      prefs.cards[k] = list.includes(k)
    })
    persist()
  },
})

const onPreview = () => {
  persist()
  // 选了「跟随实况」就不覆盖;时段选白天/夜间才覆盖昼夜
  const condition = sceneCondition.value === 'live' ? '' : sceneCondition.value
  const night = scenePeriod.value === 'live' ? null : scenePeriod.value === 'night'
  setPreviewScene(condition, night)
  requestPreview()
}
</script>

<style scoped>
.mo-lead {
  margin: 0 0 12px;
}

/* 控件行:开关/下拉/分段按钮统一水平排列,窄屏自动换行 */
.setting-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
/* 两个云层样式开关上下排开时留一点间距 */
.mo-cloud-row {
  margin-top: 6px;
}
.setting-label {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text);
}

/* 预览场景:天气下拉 + 时段分段,两者各自撑开但不挤成一行 */
.mo-scene-row {
  flex-wrap: wrap;
  row-gap: 8px;
}
.mo-scene-select {
  width: 168px;
}

.settings-form .el-form-item {
  margin-bottom: 16px;
  padding: 6px 0;
}

@media (max-width: 768px) {
  .mo-scene-select {
    width: 100%;
  }
}
</style>
