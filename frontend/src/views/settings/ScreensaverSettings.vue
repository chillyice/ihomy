<!-- 屏保设置(仅手机端在「外观与显示」里出现):开关、模式(背景/看板)、空闲时长、看板卡片。
     偏好只存本机 localStorage(utils/screensaver.js),改动即落盘,没有保存按钮。 -->
<template>
  <div class="card settings-card">
    <div class="section-label">{{ $t('screensaver.settingsTitle') }}</div>
    <p class="share-tip ss-lead">{{ $t('screensaver.lead') }}</p>

    <el-form label-position="top" class="settings-form">
      <el-form-item>
        <div class="setting-row">
          <el-switch v-model="prefs.enabled" @change="persist" />
          <span class="setting-label">{{ $t('screensaver.enable') }}</span>
        </div>
        <div class="share-tip">{{ $t('screensaver.enableHint') }}</div>
      </el-form-item>

      <el-form-item :label="$t('screensaver.mode')">
        <el-radio-group v-model="prefs.mode" @change="persist">
          <el-radio :value="MODES.PHOTO">{{ $t('screensaver.modePhoto') }}</el-radio>
          <el-radio :value="MODES.BOARD">{{ $t('screensaver.modeBoard') }}</el-radio>
        </el-radio-group>
        <div class="share-tip">
          {{ prefs.mode === MODES.BOARD ? $t('screensaver.modeBoardHint') : $t('screensaver.modePhotoHint') }}
        </div>
      </el-form-item>

      <el-form-item :label="$t('screensaver.idle')">
        <div class="setting-row">
          <el-input-number v-model.number="prefs.idleMinutes" :min="IDLE_MIN" :max="IDLE_MAX" :step="1" @change="persist" />
          <span class="ss-unit">{{ $t('screensaver.minutes') }}</span>
        </div>
        <div class="share-tip">{{ $t('screensaver.idleHint') }}</div>
      </el-form-item>

      <el-form-item v-if="prefs.mode === MODES.BOARD" :label="$t('screensaver.cards')">
        <el-checkbox-group v-model="cardList">
          <el-checkbox v-for="c in BOARD_CARDS" :key="c" :value="c">{{ $t(CARD_LABELS[c]) }}</el-checkbox>
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
import { BOARD_CARDS, IDLE_MAX, IDLE_MIN, loadPrefs, MODES, requestPreview, savePrefs } from '@/utils/screensaver'

const CARD_LABELS = {
  clock: 'screensaver.cardClock',
  todo: 'screensaver.cardTodo',
  schedule: 'screensaver.cardSchedule',
  anni: 'screensaver.cardAnni',
  photos: 'screensaver.cardPhotos',
}

const prefs = reactive(loadPrefs())

const persist = () => {
  savePrefs({ enabled: prefs.enabled, mode: prefs.mode, idleMinutes: prefs.idleMinutes, board: { ...prefs.board } })
}

// 看板卡片是「勾选=显示」;取消勾选写回 false(空数组即全关,由用户自己决定)
const cardList = computed({
  get: () => BOARD_CARDS.filter((k) => prefs.board[k] !== false),
  set: (list) => {
    BOARD_CARDS.forEach((k) => {
      prefs.board[k] = list.includes(k)
    })
    persist()
  },
})

const onPreview = () => {
  persist()
  requestPreview()
}
</script>

<style scoped>
.ss-lead {
  margin: 0 0 12px;
}
.ss-unit {
  font-size: 13px;
  color: var(--color-text-secondary);
}
</style>
