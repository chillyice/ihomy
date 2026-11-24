<!-- 光影:一个总开关管全部氛围光影;打开后才露出氛围色块、台灯、阴影、天气效果等细节。 -->
<template>
  <div class="card settings-card">
    <div class="section-label">{{ $t('settings.lightEffects') }}</div>
    <el-form label-position="top" class="settings-form">
      <el-form-item>
        <div class="setting-row">
          <el-switch v-model="effectsEnabled" />
          <span class="setting-label">{{ $t('settings.lightEffectsOn') }}</span>
        </div>
        <div class="share-tip">{{ $t('settings.lightEffectsHint') }}</div>
      </el-form-item>

      <template v-if="effectsEnabled">
        <el-form-item v-if="themeStore.theme !== 'warm'">
          <div class="setting-row">
            <el-switch v-model="blobsEnabled" />
            <span class="setting-label">{{ $t('settings.blobs') }}</span>
          </div>
          <div class="share-tip">{{ $t('settings.blobsHint') }}</div>
        </el-form-item>
        <el-form-item v-if="themeStore.theme !== 'warm'">
          <div class="setting-row">
            <el-switch v-model="glassEnabled" />
            <span class="setting-label">{{ $t('settings.glass') }}</span>
          </div>
          <div class="share-tip">{{ $t('settings.glassHint') }}</div>
        </el-form-item>

        <el-form-item :label="$t('settings.lampMode')">
          <el-radio-group :model-value="lampMode" @change="(v) => (lampMode = v)">
            <el-radio value="auto">{{ $t('settings.lampAuto') }}</el-radio>
            <el-radio value="on">{{ $t('settings.lampOn') }}</el-radio>
            <el-radio value="off">{{ $t('settings.lampOff') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="$t('settings.colorTemp')">
          <el-slider v-model.number="lampTemp" :min="0" :max="100" show-input />
          <div class="share-tip">{{ $t('settings.colorTempHint') }}</div>
        </el-form-item>
        <el-form-item :label="$t('settings.brightness')">
          <el-slider v-model.number="lampBrightness" :min="0" :max="100" show-input />
        </el-form-item>

        <el-divider />
        <el-form-item>
          <div class="setting-row">
            <el-switch v-model="shadowEnabled" />
            <span class="setting-label">{{ $t('settings.shadowEffect') }}</span>
          </div>
        </el-form-item>
        <el-form-item v-if="shadowEnabled" :label="$t('settings.projectionStandard')">
          <el-radio-group v-model="projectionStandard">
            <el-radio-button value="corrected">{{ $t('settings.correctedStandard') }}</el-radio-button>
            <el-radio-button value="real">{{ $t('settings.realStandard') }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="shadowEnabled" :label="$t('settings.shadowDepth')">
          <el-slider v-model.number="shadowDepth" :min="0" :max="100" show-input />
        </el-form-item>

        <el-form-item>
          <div class="setting-row">
            <el-switch v-model="weatherEffectEnabled" />
            <span class="setting-label">{{ $t('settings.weatherEffect') }}</span>
          </div>
          <div class="share-tip">{{ $t('settings.weatherEffectHint') }}</div>
        </el-form-item>

        <el-divider />
        <el-form-item :label="$t('settings.idleLabel')">
          <div class="setting-row">
            <el-input-number v-model.number="idleMinutes" :min="1" :max="120" :step="1" />
          </div>
          <div class="share-tip">{{ $t('settings.idleHint') }}</div>
          <div v-if="isIdle" class="share-tip" style="color: var(--color-accent)">{{ $t('settings.idleState') }}</div>
        </el-form-item>
        <el-form-item>
          <el-button @click="enterLightTest">{{ $t('settings.enterLightTest') }}</el-button>
        </el-form-item>
      </template>
    </el-form>
  </div>
</template>

<script setup>
import { inject } from 'vue'
import { useRouter } from 'vue-router'
import { useThemeStore } from '@/stores/theme'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'

const router = useRouter()
const themeStore = useThemeStore()

// 光照设置:从全局 useSunLight 实例注入(与 SunLightLayer/AppSidebar 共享)
const sunLight = inject(SUN_LIGHT_KEY)
const {
  effectsEnabled,
  lampMode,
  lampTemp,
  lampBrightness,
  shadowEnabled,
  projectionStandard,
  shadowDepth,
  weatherEffectEnabled,
  blobsEnabled,
  glassEnabled,
  idleMinutes,
  isIdle,
} = sunLight || {}

// 进入光照测试:跳转首页并启动测试模式
const enterLightTest = () => {
  if (sunLight) sunLight.startLightTest()
  router.push('/home')
}
</script>

<style scoped>
.settings-card {
  margin-bottom: 16px;
  background: var(--color-card);
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
/* 控件行 + 标签水平排列,垂直居中 */
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
.settings-form .el-form-item {
  margin-bottom: 16px;
  padding: 6px 0;
}
.settings-form .el-form-item .el-switch,
.settings-form .el-form-item .el-input-number,
.settings-form .el-form-item .el-radio-group,
.settings-form .el-form-item .el-slider {
  margin-bottom: 0;
}
.settings-form .el-divider {
  margin: 8px 0 4px;
}
@media (max-width: 768px) {
  .setting-row {
    flex-wrap: wrap;
  }
}
</style>
