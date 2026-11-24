<!-- 屏保设置:开关、多久不动进入、背景三选(纯黑 / 天气图 / 我的图片)、卡片勾选、预览。
     手机、暖居桌面、窗边大屏共用这一套(原先的屏保 / 暖居待机浮现 / 看板模式已合并)。
     偏好只存本机 localStorage(utils/screensaver.js),改动即落盘,没有保存按钮。 -->
<template>
  <div class="card settings-card">
    <div class="section-label">{{ $t('screensaver.settingsTitle') }}</div>
    <p class="share-tip ss-lead">{{ $t('screensaver.lead') }}</p>

    <el-form label-position="top" class="settings-form">
      <el-form-item>
        <div class="setting-row">
          <el-switch v-model="prefs.enabled" :aria-label="$t('screensaver.enable')" @change="persist" />
          <span class="setting-label">{{ $t('screensaver.enable') }}</span>
        </div>
        <div class="share-tip">{{ $t('screensaver.enableHint') }}</div>
      </el-form-item>

      <el-form-item :label="$t('screensaver.idle')">
        <div class="setting-row">
          <el-input-number
            v-model.number="prefs.idleMinutes"
            :min="IDLE_MIN"
            :max="IDLE_MAX"
            :step="1"
            :disabled="!prefs.enabled"
            @change="persist"
          />
          <span class="ss-unit">{{ $t('screensaver.minutes') }}</span>
        </div>
        <div class="share-tip">{{ $t('screensaver.idleHint') }}</div>
      </el-form-item>

      <el-form-item :label="$t('screensaver.background')">
        <el-radio-group v-model="prefs.bg" :disabled="!prefs.enabled" @change="persist">
          <el-radio-button :value="BG.BLACK">{{ $t('screensaver.bgBlack') }}</el-radio-button>
          <el-radio-button :value="BG.WEATHER">{{ $t('screensaver.bgWeather') }}</el-radio-button>
          <el-radio-button :value="BG.IMAGE">{{ $t('screensaver.bgImage') }}</el-radio-button>
        </el-radio-group>
        <div class="share-tip">{{ $t(BG_HINTS[prefs.bg]) }}</div>
      </el-form-item>

      <template v-if="prefs.bg === BG.IMAGE">
        <BackgroundImagePicker :model="prefs.image" @update:model="onImage" />
      </template>

      <el-form-item :label="$t('screensaver.cards')">
        <el-checkbox-group :model-value="cardList" :disabled="!prefs.enabled" @update:model-value="setCards">
          <el-checkbox v-for="c in CARDS" :key="c" :value="c">{{ $t(CARD_LABELS[c]) }}</el-checkbox>
        </el-checkbox-group>
        <div class="share-tip">{{ $t('screensaver.cardsHint') }}</div>
      </el-form-item>

      <el-form-item>
        <el-button @click="onPreview">{{ $t('screensaver.preview') }}</el-button>
        <div class="share-tip">{{ $t('screensaver.previewHint') }}</div>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup>
import { computed, reactive } from 'vue'
import BackgroundImagePicker from '@/components/BackgroundImagePicker.vue'
import { BG, CARDS, IDLE_MAX, IDLE_MIN, loadPrefs, requestPreview, savePrefs } from '@/utils/screensaver'

const CARD_LABELS = {
  clock: 'screensaver.cardClock',
  todo: 'screensaver.cardTodo',
  schedule: 'screensaver.cardSchedule',
  anni: 'screensaver.cardAnni',
  photos: 'screensaver.cardPhotos',
}

const BG_HINTS = {
  [BG.BLACK]: 'screensaver.bgBlackHint',
  [BG.WEATHER]: 'screensaver.bgWeatherHint',
  [BG.IMAGE]: 'screensaver.bgImageHint',
}

const prefs = reactive(loadPrefs())
const cardList = computed(() => CARDS.filter((k) => prefs.cards[k] !== false))

const persist = () => {
  const next = savePrefs({ enabled: prefs.enabled, idleMinutes: prefs.idleMinutes, bg: prefs.bg })
  Object.assign(prefs, next)
}

const onImage = (patch) => {
  const next = savePrefs({ image: patch })
  Object.assign(prefs, next)
}

// 卡片是「勾选=显示」;空数组即全关,由用户自己决定(屏保只剩背景)
const setCards = (list) => {
  const cards = {}
  CARDS.forEach((k) => {
    cards[k] = list.includes(k)
  })
  const next = savePrefs({ cards })
  Object.assign(prefs, next)
}

const onPreview = () => {
  persist()
  requestPreview()
}
</script>

<style scoped>
.settings-card {
  margin-bottom: 24px;
  background: var(--color-card);
}
.ss-lead {
  margin: 0 0 12px;
}
.ss-unit {
  font-size: 13px;
  color: var(--color-text-secondary);
}
.share-tip {
  color: #776e62;
  font-size: 12px;
  margin-top: 8px;
  line-height: 1.4;
  width: 100%;
}
html.dark .share-tip {
  color: #9a9088;
}
.settings-form .el-form-item {
  margin-bottom: 16px;
  padding: 6px 0;
}
/* 组内分隔:相邻设置行之间一条淡虚线 */
.settings-form .el-form-item + .el-form-item {
  border-top: 1px dashed color-mix(in srgb, var(--color-border) 55%, var(--color-text-secondary));
  padding-top: 14px;
}
</style>
