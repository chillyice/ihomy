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

      <el-form-item>
        <el-button @click="onPreview">{{ $t('morning.preview') }}</el-button>
        <div class="share-tip">{{ $t('morning.previewHint') }}</div>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup>
import { computed, reactive } from 'vue'
import { loadPrefs, MORNING_CARDS, requestPreview, savePrefs } from '@/utils/morning'

const CARD_LABELS = {
  greeting: 'morning.cardGreeting',
  date: 'morning.cardDate',
  todo: 'morning.cardTodo',
  weather: 'morning.cardWeather',
}

const prefs = reactive(loadPrefs())

const persist = () => {
  savePrefs({
    enabled: prefs.enabled,
    greeting: prefs.greeting,
    animation: prefs.animation,
    cards: { ...prefs.cards },
  })
}

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
  requestPreview()
}
</script>

<style scoped>
.mo-lead {
  margin: 0 0 12px;
}
</style>
