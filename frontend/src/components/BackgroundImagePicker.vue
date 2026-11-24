<!-- 背景图片来源:内置氛围 + 上传照片 + 浓度。暖居背景与屏保共用一套(预设清单 / 上传校验 / 文案同源)。
     只负责选择,不改外部状态:选中后把增量交给父组件(update:model)。 -->
<template>
  <div class="bg-picker">
    <el-form-item :label="$t('warmBg.presets')">
      <div class="bgp-grid" role="group" :aria-label="$t('warmBg.presets')">
        <button
          v-for="p in BG_PRESETS"
          :key="p.id"
          type="button"
          class="bgp-preset"
          :aria-pressed="active(p.id)"
          @click="emitPatch({ pick: 'preset', preset: p.id })"
        >
          <span class="bgp-swatch" :style="{ backgroundImage: p.css, backgroundSize: p.size }"></span>
          <span class="bgp-label">{{ $t(p.labelKey) }}</span>
        </button>
      </div>
    </el-form-item>

    <el-form-item :label="$t('warmBg.photo')">
      <div class="bgp-photo">
        <div
          v-if="model.url"
          class="bgp-thumb"
          :style="{ backgroundImage: `url(${model.url})` }"
          role="img"
          :aria-label="$t('warmBg.myPhoto')"
        ></div>
        <div class="bgp-actions">
          <el-button type="primary" plain :loading="uploading" @click="pickFile">
            {{ model.url ? $t('warmBg.replace') : $t('warmBg.upload') }}
          </el-button>
          <el-button v-if="model.url" class="ghost-btn" @click="removePhoto">{{ $t('warmBg.remove') }}</el-button>
        </div>
      </div>
      <div class="share-tip">{{ $t(userStore.isLoggedIn ? 'warmBg.uploadHint' : 'warmBg.needLogin') }}</div>
      <input ref="fileInput" class="bgp-file" type="file" accept="image/*" @change="onFile" />
    </el-form-item>

    <el-form-item v-if="showOpacity" :label="$t('warmBg.opacity')">
      <el-slider :model-value="model.opacity" :min="20" :max="100" show-input @update:model-value="onOpacity" />
      <div class="share-tip">{{ $t('warmBg.opacityHint') }}</div>
    </el-form-item>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { fileApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { BG_PRESETS } from '@/utils/warmBackground'

const props = defineProps({
  // { pick: 'preset'|'upload', preset, url, opacity }
  model: { type: Object, required: true },
  showOpacity: { type: Boolean, default: true },
})
const emit = defineEmits(['update:model'])

const { t } = useI18n()
const userStore = useUserStore()

// 上传上限 8MB:背景只需够铺满屏幕,更大的图没必要,也拖慢首屏
const MAX_UPLOAD = 8 * 1024 * 1024

const fileInput = ref(null)
const uploading = ref(false)

const emitPatch = (patch) => emit('update:model', patch)

const active = (id) => props.model.pick !== 'upload' && props.model.preset === id

const onOpacity = (value) => emitPatch({ opacity: value })

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
    emitPatch({ pick: 'upload', url: data.url })
    ElMessage.success(t('warmBg.updated'))
  } catch (e) {
    ElMessage.error(t('warmBg.uploadFailed'))
  } finally {
    uploading.value = false
  }
}

const removePhoto = () => emitPatch({ url: '', pick: 'preset' })
</script>

<style scoped>
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
.bg-picker .el-form-item {
  margin-bottom: 16px;
  padding: 6px 0;
}
/* 组内分隔:相邻设置行之间一条淡虚线(暖居 / 屏保共用) */
.bg-picker .el-form-item + .el-form-item {
  border-top: 1px dashed color-mix(in srgb, var(--color-border) 55%, var(--color-text-secondary));
  padding-top: 14px;
}
.bg-picker .el-form-item .el-slider {
  margin-bottom: 0;
}
.bgp-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(96px, 1fr));
  gap: 10px;
  width: 100%;
}
.bgp-preset {
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
.bgp-swatch {
  display: block;
  width: 100%;
  height: 58px;
  border-radius: 12px;
  border: 2px solid transparent;
  background-position: center;
  box-shadow: var(--shadow);
}
.bgp-preset[aria-pressed='true'] .bgp-swatch {
  border-color: var(--color-brand);
  box-shadow: var(--shadow-hover);
}
.bgp-preset:focus-visible {
  outline: 2px solid var(--color-brand);
  outline-offset: 2px;
  border-radius: 12px;
}
.bgp-label {
  font-size: 12px;
  color: var(--color-text-secondary);
}
.bgp-preset[aria-pressed='true'] .bgp-label {
  color: var(--color-brand);
}
.bgp-photo {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  width: 100%;
}
.bgp-thumb {
  width: 132px;
  height: 74px;
  border-radius: 10px;
  border: 1px solid var(--color-border);
  background-size: cover;
  background-position: center;
}
.bgp-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.bgp-file {
  display: none;
}
@media (max-width: 768px) {
  .bgp-thumb {
    width: 100%;
    height: 96px;
  }
}
</style>
