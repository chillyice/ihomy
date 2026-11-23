<!-- 暖居背景:来源切换(每日天气图 / 我的背景)+ 内置氛围 + 上传照片 + 浓度。
     只对桌面暖居外壳生效(手机端不挂本面板);偏好跟账号走,见 utils/warmBackground.js -->
<template>
  <section class="card settings-card">
    <div class="section-label">{{ $t('warmBg.title') }}</div>
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

        <el-form-item :label="$t('warmBg.presets')">
          <div class="warmbg-grid" role="group" :aria-label="$t('warmBg.presets')">
            <button
              v-for="p in BG_PRESETS"
              :key="p.id"
              type="button"
              class="warmbg-preset"
              :aria-pressed="activePreset(p.id)"
              @click="pickPreset(p.id)"
            >
              <span class="warmbg-swatch" :style="{ backgroundImage: p.css, backgroundSize: p.size }"></span>
              <span class="warmbg-preset-label">{{ $t(p.labelKey) }}</span>
            </button>
          </div>
        </el-form-item>

        <el-form-item :label="$t('warmBg.photo')">
          <div class="warmbg-photo">
            <div
              v-if="warmBgState.url"
              class="warmbg-thumb"
              :style="{ backgroundImage: `url(${warmBgState.url})` }"
              role="img"
              :aria-label="$t('warmBg.myPhoto')"
            ></div>
            <div class="warmbg-actions">
              <el-button type="primary" plain :loading="uploading" @click="pickFile">
                {{ warmBgState.url ? $t('warmBg.replace') : $t('warmBg.upload') }}
              </el-button>
              <el-button v-if="warmBgState.url" class="ghost-btn" @click="removePhoto">{{ $t('warmBg.remove') }}</el-button>
            </div>
          </div>
          <div class="share-tip">{{ $t(userStore.isLoggedIn ? 'warmBg.uploadHint' : 'warmBg.needLogin') }}</div>
          <input ref="fileInput" class="warmbg-file" type="file" accept="image/*" @change="onFile" />
        </el-form-item>

        <el-form-item :label="$t('warmBg.opacity')">
          <el-slider :model-value="warmBgState.opacity" :min="20" :max="100" show-input @update:model-value="onOpacity" />
          <div class="share-tip">{{ $t('warmBg.opacityHint') }}</div>
        </el-form-item>

        <el-form-item>
          <el-button class="ghost-btn" @click="onReset">{{ $t('warmBg.reset') }}</el-button>
        </el-form-item>
      </template>
    </el-form>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { fileApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { BG_PRESETS, initWarmBg, resetWarmBg, setWarmBg, warmBgLayer, warmBgState } from '@/utils/warmBackground'

const { t } = useI18n()
const userStore = useUserStore()

// 上传上限 8MB:背景只需要够铺满屏幕,更大的图没必要,也拖慢首屏
const MAX_UPLOAD = 8 * 1024 * 1024

const fileInput = ref(null)
const uploading = ref(false)

// 预览:与外壳同一套算法(温度图除外——设置页不触发生图)
const preview = computed(() => warmBgLayer(warmBgState, { isLoggedIn: userStore.isLoggedIn }))

const activePreset = (id) => warmBgState.pick !== 'upload' && warmBgState.preset === id

const onMode = (mode) => setWarmBg({ mode: mode === 'mine' ? 'mine' : 'weather' })

const pickPreset = (id) => setWarmBg({ mode: 'mine', pick: 'preset', preset: id })

const onOpacity = (value) => setWarmBg({ opacity: value })

const pickFile = () => {
  if (!userStore.isLoggedIn) {
    ElMessage.warning(t('warmBg.needLogin'))
    return
  }
  fileInput.value?.click()
}

const onFile = async (event) => {
  const file = event.target.files && event.target.files[0]
  // 允许再次选同一张图:清空 input,否则 change 不会触发
  event.target.value = ''
  if (!file) return
  if (!/^image\//.test(file.type)) {
    ElMessage.warning(t('warmBg.notImage'))
    return
  }
  if (file.size > MAX_UPLOAD) {
    ElMessage.warning(t('warmBg.tooLarge'))
    return
  }
  uploading.value = true
  try {
    const data = await fileApi.upload(file)
    if (!data || !data.url) throw new Error('missing url')
    setWarmBg({ mode: 'mine', pick: 'upload', url: data.url })
    ElMessage.success(t('warmBg.updated'))
  } catch (e) {
    ElMessage.error(t('warmBg.uploadFailed'))
  } finally {
    uploading.value = false
  }
}

const removePhoto = () => setWarmBg({ url: '', pick: 'preset' })

const onReset = () => resetWarmBg()

onMounted(initWarmBg)
</script>

<style scoped>
.settings-card {
  margin-bottom: 16px;
  background: var(--color-card);
}
.warmbg-lead {
  margin: 0 0 12px;
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
.warmbg-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(96px, 1fr));
  gap: 10px;
  width: 100%;
}
.warmbg-preset {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 0;
  background: transparent;
  border: 0;
  font: inherit;
  color: var(--color-text);
  cursor: pointer;
}
.warmbg-swatch {
  display: block;
  width: 100%;
  height: 58px;
  border-radius: 12px;
  border: 2px solid transparent;
  background-position: center;
  box-shadow: var(--shadow);
}
.warmbg-preset[aria-pressed='true'] .warmbg-swatch {
  border-color: var(--color-brand);
  box-shadow: var(--shadow-hover);
}
.warmbg-preset:focus-visible {
  outline: 2px solid var(--color-brand);
  outline-offset: 2px;
  border-radius: 12px;
}
.warmbg-preset-label {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.warmbg-preset[aria-pressed='true'] .warmbg-preset-label {
  color: var(--color-brand);
}
.warmbg-photo {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  width: 100%;
}
.warmbg-thumb {
  width: 132px;
  height: 74px;
  border-radius: 10px;
  border: 1px solid var(--color-border);
  background-size: cover;
  background-position: center;
}
.warmbg-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.warmbg-file {
  display: none;
}
.settings-form .el-form-item {
  margin-bottom: 16px;
  padding: 6px 0;
}
.settings-form .el-form-item .el-radio-group,
.settings-form .el-form-item .el-slider {
  margin-bottom: 0;
}
@media (max-width: 768px) {
  .warmbg-thumb {
    width: 100%;
    height: 96px;
  }
}
</style>
