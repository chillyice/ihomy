<!-- 主题:暖居 / 光尘 + 晨暮自动切换 + 应用图标。
     主题专属的设置(暖居)紧跟在本卡下方;画面布局属首页内容,见首页内容大类。 -->
<template>
  <div class="card settings-card">
    <div class="section-label">{{ $t('settings.theme') }}</div>
    <el-form label-position="top" class="settings-form">
      <el-form-item>
        <div class="theme-row">
          <ThemeSwatch />
          <el-switch v-model="themeStore.autoMode" @change="themeStore.setAutoMode" :active-text="$t('theme.autoMode')" />
          <el-radio-group v-if="!themeStore.autoMode" :model-value="themeStore.mode" @change="themeStore.setMode">
            <el-radio value="dawn">{{ $t('theme.dawn') }}</el-radio>
            <el-radio value="dusk">{{ $t('theme.dusk') }}</el-radio>
          </el-radio-group>
        </div>
      </el-form-item>

      <el-form-item :label="$t('settings.appIcon.title')">
        <div class="icon-options" role="group" :aria-label="$t('settings.appIcon.title')">
          <button
            v-for="id in APP_ICON_IDS"
            :key="id"
            type="button"
            class="icon-option"
            :aria-pressed="appIcon.id === id"
            :disabled="appIcon.busy"
            @click="selectIcon(id)"
          >
            <img :src="appIconImage(id)" alt="" width="44" height="44" />
            <span>{{ $t(`settings.appIcon.${id}`) }}</span>
          </button>
        </div>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup>
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { useThemeStore } from '@/stores/theme'
import { APP_ICON_IDS, appIconImage, useAppIconStore } from '@/stores/appIcon'
import ThemeSwatch from '@/components/ThemeSwatch.vue'

const { t } = useI18n()
const themeStore = useThemeStore()
const appIcon = useAppIconStore()

async function selectIcon(id) {
  try {
    await appIcon.select(id)
  } catch (error) {
    const key = error.code === 'UNIMPLEMENTED' ? 'updateAndroid' : 'changeFailed'
    ElMessage.error(t(`settings.appIcon.${key}`))
  }
}
</script>

<style scoped>
.settings-card {
  margin-bottom: 24px;
  background: var(--color-card);
}
.theme-row {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.icon-options {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.icon-option {
  display: grid;
  justify-items: center;
  gap: 6px;
  padding: 8px 10px;
  background: transparent;
  color: var(--color-text);
  border: 1px solid transparent;
  border-radius: 12px;
  font: inherit;
  cursor: pointer;
}
.icon-option img {
  border-radius: 9px;
}
.icon-option[aria-pressed='true'] {
  border-color: var(--color-brand);
  color: var(--color-brand);
}
.icon-option:focus-visible {
  outline: 2px solid var(--color-brand);
  outline-offset: 2px;
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
.settings-form .el-form-item .el-switch,
.settings-form .el-form-item .el-radio-group {
  margin-bottom: 0;
}
@media (max-width: 768px) {
  .settings-card .el-select {
    width: 100%;
  }
}
</style>
