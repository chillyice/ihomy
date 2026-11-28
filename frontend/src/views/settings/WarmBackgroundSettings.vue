<!-- 暖居:背景来源(每日天气图 / 我的背景)+ 浓度 + 窗下桌面小物,合并成一张卡。
     背景只对桌面端暖居外壳生效;窗下桌面(爪印/桌边小物)在其它主题与手机端也保留开关。
     偏好跟账号走,见 utils/warmBackground.js;图片来源与屏保共用 BackgroundImagePicker。 -->
<template>
  <section class="card settings-card">
    <div class="section-label">{{ $t('warmBg.title') }}</div>

    <template v-if="backgroundAvailable">
      <p class="share-tip warmbg-lead">{{ $t('warmBg.lead') }}</p>

      <el-form label-position="top" class="settings-form">
        <el-form-item :label="$t('warmBg.source')">
          <el-radio-group :model-value="warmBgState.mode" @change="onMode">
            <el-radio-button value="weather">{{ $t('warmBg.weather') }}</el-radio-button>
            <el-radio-button value="mine">{{ $t('warmBg.mine') }}</el-radio-button>
          </el-radio-group>
          <div class="share-tip">{{ $t(warmBgState.mode === 'weather' ? 'warmBg.weatherHint' : 'warmBg.mineHint') }}</div>
        </el-form-item>

        <template v-if="warmBgState.mode === 'mine'">
          <el-form-item :label="$t('warmBg.preview')">
            <div
              v-if="preview"
              class="warmbg-preview"
              :style="{ backgroundImage: preview.image, backgroundSize: preview.size, opacity: preview.opacity }"
              role="img"
              :aria-label="$t('warmBg.preview')"
            ></div>
          </el-form-item>

          <!-- 浓度紧跟预览图:调滑动条时眼睛不用在卡片里来回找 -->
          <el-form-item :label="$t('warmBg.opacity')">
            <el-slider :model-value="warmBgState.opacity" :min="20" :max="100" show-input @update:model-value="onOpacity" />
            <div class="share-tip">{{ $t('warmBg.opacityHint') }}</div>
          </el-form-item>

          <BackgroundImagePicker :model="warmBgState" :show-opacity="false" @update:model="setWarmBg" />

          <el-form-item>
            <el-button class="ghost-btn" @click="onReset">{{ $t('warmBg.reset') }}</el-button>
          </el-form-item>
        </template>
      </el-form>
    </template>
    <p v-else class="share-tip warmbg-lead">{{ $t(backgroundHint) }}</p>

    <el-divider class="warmbg-divider" />
    <WindowDeskSettings embedded />
  </section>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import { useThemeStore } from '@/stores/theme'
import { useDevice } from '@/composables/useDevice'
import BackgroundImagePicker from '@/components/BackgroundImagePicker.vue'
import WindowDeskSettings from './WindowDeskSettings.vue'
import { initWarmBg, resetWarmBg, setWarmBg, warmBgLayer, warmBgState } from '@/utils/warmBackground'

const userStore = useUserStore()
const themeStore = useThemeStore()
const { isMobile } = useDevice()

// 预览:与外壳同一套算法(天气图除外——设置页不触发生图)
const preview = computed(() => warmBgLayer(warmBgState, { isLoggedIn: userStore.isLoggedIn }))

// 背景只作用于桌面端暖居外壳(WarmLayout),其它主题/手机端只保留窗下桌面开关
const backgroundAvailable = computed(() => themeStore.theme === 'warm' && !isMobile.value)
const backgroundHint = computed(() => (themeStore.theme === 'warm' ? 'warmBg.deviceHint' : 'warmBg.themeHint'))

const onMode = (mode) => setWarmBg({ mode: mode === 'mine' ? 'mine' : 'weather' })
const onOpacity = (value) => setWarmBg({ opacity: value })
const onReset = () => resetWarmBg()

onMounted(initWarmBg)
</script>

<style scoped>
.settings-card {
  margin-bottom: 24px;
  background: var(--color-card);
}
.warmbg-lead {
  margin: 0 0 12px;
}
/* 卡内子分组分隔:比默认细灰线更明显(暖居卡里是背景 ↔ 窗下桌面两段) */
.warmbg-divider {
  margin: 4px 0 16px;
  border-top: 1px dashed color-mix(in srgb, var(--color-border) 45%, var(--color-text-secondary));
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
/* 预览条:按外壳实际浓度叠在卡片底上,所见即所得(平铺口径与外壳一致) */
.warmbg-preview {
  width: 100%;
  height: 96px;
  border-radius: 12px;
  border: 1px solid var(--color-border);
  background-position: center;
}
.settings-form .el-form-item {
  margin-bottom: 16px;
  padding: 6px 0;
}
.settings-form .el-form-item .el-slider {
  margin-bottom: 0;
}
</style>
