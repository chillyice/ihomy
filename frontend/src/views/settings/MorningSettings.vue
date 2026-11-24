<!-- 每日早安设置(所有客户端「外观与显示」里):只留总开关 + 编辑入口。
     开关之外的配置(问候语/显示组件/组件大小/动效/预览)都收进编辑弹窗,
     偏好只存本机 localStorage(utils/morning.js),改动即落盘,没有保存按钮。 -->
<template>
  <div class="card settings-card mo-settings">
    <div class="section-label">{{ $t('morning.settingsTitle') }}</div>

    <el-form label-position="top" class="settings-form">
      <el-form-item>
        <div class="setting-row">
          <el-switch v-model="prefs.enabled" @change="persist" />
          <span class="setting-label">{{ $t('morning.enable') }}</span>
        </div>
        <div class="share-tip">{{ $t(themeStore.theme === 'window' ? 'windowDisplay.welcomeHint' : 'morning.enableHint') }}</div>
      </el-form-item>

      <el-form-item v-if="prefs.enabled">
        <el-button @click="editorVisible = true">{{ $t('morning.editEntry') }}</el-button>
      </el-form-item>
    </el-form>
  </div>

  <!-- 编辑早安页:显示什么组件 / 显示什么内容 / 组件大小,附场景预览 -->
  <el-dialog v-model="editorVisible" :title="$t('morning.editorTitle')" width="dialog-md" append-to-body @closed="afterEditorClose">
    <el-form label-position="top" class="settings-form">
      <el-form-item :label="$t('morning.greetingLabel')">
        <el-input
          v-model="prefs.greeting"
          maxlength="60"
          show-word-limit
          clearable
          :placeholder="$t('morning.greetingPlaceholder')"
          @change="persist"
        />
      </el-form-item>

      <el-form-item :label="$t('morning.cards')">
        <el-checkbox-group v-model="cardList">
          <el-checkbox v-for="c in MORNING_CARDS" :key="c" :value="c">{{ $t(CARD_LABELS[c]) }}</el-checkbox>
        </el-checkbox-group>
      </el-form-item>

      <el-form-item :label="$t('morning.cardSize')">
        <div v-for="k in MORNING_SIZED_CARDS" :key="k" class="mo-size-row">
          <span class="mo-size-label">{{ $t(CARD_LABELS[k]) }}</span>
          <el-radio-group v-model="prefs.sizes[k]" size="small" @change="persist">
            <el-radio-button v-for="s in MORNING_CARD_SIZES" :key="s" :value="s">
              {{ $t(`morning.size${s.charAt(0).toUpperCase() + s.slice(1)}`) }}
            </el-radio-button>
          </el-radio-group>
        </div>
      </el-form-item>

      <el-form-item>
        <div class="setting-row">
          <el-switch v-model="prefs.animation" @change="persist" />
          <span class="setting-label">{{ $t('morning.animation') }}</span>
        </div>
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
        <div class="mo-preview-row">
          <el-button type="primary" plain @click="onPreview">{{ $t('morning.preview') }}</el-button>
          <span class="share-tip">{{ $t('morning.previewHint') }}</span>
        </div>
      </el-form-item>
    </el-form>
  </el-dialog>
</template>

<script setup>
import { useThemeStore } from '@/stores/theme'
import { computed, reactive, ref } from 'vue'
import {
  loadPrefs,
  MORNING_CARDS,
  MORNING_CARD_SIZES,
  MORNING_SCENES,
  MORNING_SCENE_LABELS,
  MORNING_SIZED_CARDS,
  requestPreview,
  savePrefs,
  setPreviewScene,
} from '@/utils/morning'

const CARD_LABELS = {
  greeting: 'morning.cardGreeting',
  date: 'morning.cardDate',
  todo: 'morning.cardTodo',
  weather: 'morning.cardWeather',
}

const prefs = reactive(loadPrefs())
const editorVisible = ref(false)

// 预览场景只存在本页内存里:一是调视觉用的,二是不该悄悄改掉真实天气
const sceneCondition = ref('live')
const scenePeriod = ref('live')

const persist = () => {
  savePrefs({
    enabled: prefs.enabled,
    greeting: prefs.greeting,
    animation: prefs.animation,
    cards: { ...prefs.cards },
    sizes: { ...prefs.sizes },
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

const themeStore = useThemeStore()
let previewAfterClose = false
const afterEditorClose = () => {
  if (!previewAfterClose) return
  previewAfterClose = false
  requestPreview()
}
const onPreview = () => {
  persist()
  // 选了「跟随实况」就不覆盖;时段选白天/夜间才覆盖昼夜
  const condition = sceneCondition.value === 'live' ? '' : sceneCondition.value
  const night = scenePeriod.value === 'live' ? null : scenePeriod.value === 'night'
  setPreviewScene(condition, night)
  previewAfterClose = true
  editorVisible.value = false
}
</script>

<style scoped>
/* 与上面的「显示模式」框拉开一档距离(反馈:贴太近) */
.mo-settings {
  margin-top: 16px;
}

/* 控件行:开关/下拉/分段按钮统一水平排列,窄屏自动换行 */
.setting-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.setting-label {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text);
}

/* 组件大小:每张卡一行(卡名 + 三档单选) */
.mo-size-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 2px 0;
}
.mo-size-label {
  width: 72px;
  font-size: 13px;
  color: var(--color-text-secondary);
}

/* 预览场景:天气下拉 + 时段分段 + 预览按钮 */
.mo-scene-row {
  flex-wrap: wrap;
  row-gap: 8px;
}
.mo-scene-select {
  width: 168px;
}
.mo-preview-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 8px;
}

.settings-form .el-form-item {
  margin-bottom: 16px;
  padding: 6px 0;
}

@media (max-width: 768px) {
  .mo-scene-select {
    width: 100%;
  }
  .mo-size-row {
    flex-wrap: wrap;
  }
}
</style>
