<!-- 光影:一个总开关管全部氛围光影;打开后才露出氛围色块、台灯、阴影、天气效果等细节。 -->
<template>
  <div class="card settings-card">
    <div class="section-label">{{ $t('settings.lightEffects') }}</div>
    <el-form label-position="top" class="settings-form">
      <el-form-item>
        <div class="setting-row">
          <el-switch v-model="effectsEnabled" :aria-label="$t('settings.lightEffectsOn')" />
          <span class="setting-label">{{ $t('settings.lightEffectsOn') }}</span>
        </div>
        <div class="share-tip">{{ $t('settings.lightEffectsHint') }}</div>
      </el-form-item>

      <template v-if="effectsEnabled">
        <fieldset v-if="themeStore.theme !== 'warm'" class="light-group">
          <legend>{{ $t('settings.lightBackground') }}</legend>
          <el-form-item>
            <div class="setting-row">
              <el-switch v-model="blobsEnabled" :aria-label="$t('settings.blobs')" />
              <span class="setting-label">{{ $t('settings.blobs') }}</span>
            </div>
            <div class="share-tip">{{ $t('settings.blobsHint') }}</div>
          </el-form-item>
          <el-form-item>
            <div class="setting-row">
              <el-switch v-model="glassEnabled" :aria-label="$t('settings.glass')" />
              <span class="setting-label">{{ $t('settings.glass') }}</span>
            </div>
            <div class="share-tip">{{ $t('settings.glassHint') }}</div>
          </el-form-item>
        </fieldset>
        <fieldset class="light-group">
          <legend>{{ $t('settings.lightLamp') }}</legend>
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

          <!-- 夜间超时关灯也是台灯行为,跟台灯模式/色温/亮度放在一档 -->
          <el-form-item :label="$t('settings.idleLabel')">
            <div class="setting-row">
              <el-input-number v-model.number="idleMinutes" :min="1" :max="120" :step="1" />
            </div>
            <div class="share-tip">{{ $t('settings.idleHint') }}</div>
            <div v-if="isIdle" class="share-tip" style="color: var(--color-accent)">{{ $t('settings.idleState') }}</div>
          </el-form-item>
        </fieldset>
        <fieldset class="light-group">
          <legend>{{ $t('settings.lightWindow') }}</legend>
          <el-form-item>
            <div class="setting-row">
              <el-switch v-model="shadowEnabled" :aria-label="$t('settings.shadowEffect')" />
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

          <el-form-item v-if="shadowEnabled" :label="$t('settings.featherStrength')">
            <el-slider v-model.number="featherStrength" :min="0" :max="100" :aria-label="$t('settings.featherStrength')" show-input />
            <div class="share-tip">{{ $t('settings.featherStrengthHint') }}</div>
          </el-form-item>
        </fieldset>

        <div class="light-group">
          <el-form-item>
            <div class="setting-row">
              <el-switch v-model="weatherEffectEnabled" :aria-label="$t('settings.weatherEffect')" />
              <span class="setting-label">{{ $t('settings.weatherEffect') }}</span>
            </div>
            <div class="share-tip">{{ $t('settings.weatherEffectHint') }}</div>
          </el-form-item>
        </div>
        <fieldset class="light-group">
          <legend>{{ $t('settings.lightPreview') }}</legend>
          <!-- 光影测试:首页拉起全量光影控制台(开关/按钮/滑条);光影实验台:3D 太阳与真实阴影 -->
          <el-form-item>
            <div class="light-launch">
              <el-button @click="enterLightTest">{{ $t('settings.enterLightTest') }}</el-button>
              <el-button @click="enterLightLab">{{ $t('settings.enterLightLab') }}</el-button>
            </div>
            <div class="share-tip">{{ $t('settings.lightTestHint') }}</div>
          </el-form-item>
        </fieldset>
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
  featherStrength,
  weatherEffectEnabled,
  blobsEnabled,
  glassEnabled,
  idleMinutes,
  isIdle,
} = sunLight || {}

// 进入光影测试:跳转首页并启动测试模式(控制台带全部光影开关/按钮/滑条)
const enterLightTest = () => {
  if (sunLight) sunLight.startLightTest()
  router.push('/home')
}

// 3D 光影实验台:Three.js 太阳模拟 + 真实阴影(工具箱卡片已撤,只从这里进)
const enterLightLab = () => router.push('/tools/light-lab')
</script>

<style scoped>
.settings-card {
  margin-bottom: 24px;
  background: var(--color-card);
}
.share-tip {
  color: var(--color-text-secondary);
  font-size: 12px;
  margin-top: 8px;
  line-height: 1.4;
  width: 100%;
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
.light-group {
  min-inline-size: 0;
  margin: 20px 0 0;
  padding: 14px 0 0;
  border: 0;
  border-block-start: 1px solid var(--color-border);
}
.light-group legend {
  padding: 0 10px 0 0;
  color: var(--color-text);
  font-size: 14px;
  font-weight: 650;
}
.light-group .el-form-item:last-child {
  margin-bottom: 0;
}
.light-launch {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.settings-form .el-form-item .el-switch,
.settings-form .el-form-item .el-input-number,
.settings-form .el-form-item .el-radio-group,
.settings-form .el-form-item .el-slider {
  margin-bottom: 0;
}
@media (max-width: 768px) {
  .setting-row {
    flex-wrap: wrap;
  }
}
</style>
