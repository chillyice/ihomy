<!-- 主题:暖居 / 光尘 + 晨暮自动切换 + 应用图标 + 首页组件布局。
     主题专属的设置(暖居背景、窗下桌面小物)紧跟在本卡下方,按当前主题显示。 -->
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

      <el-divider />

      <el-form-item :label="$t('settings.panelLayout')">
        <el-button type="warning" plain @click="resetPanelLayout">{{ $t('settings.resetPanelLayout') }}</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup>
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
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

// 恢复默认面板布局:清除本机所有面板持久化记录,刷新页面生效。
// 当前确实在写的键:ihomy:music:pos(MusicPlayer 拖拽位置)、ihomy:chat:float:v1(ChatFloat 悬浮球位置)、
// ihomy:chat:panel:v1(ChatFloat 面板位置/尺寸)、ihomy:chat:sheet:v1(手机抽屉高度)、
// ihomy:chat:rail:v1(聊天对象列收起)、ihomy:chat:rail:w:v1(聊天对象列宽度)、ihomy:chat:text:v1(聊天字号/自己气泡颜色)、
// ihomy:games:lastRoomGame(一起玩默认游戏)、ihomy:room:autoReady(自动准备)、
// ihomy:mahjong3d:view(立体麻将视角偏好)、
// ihomy:call:mini:v1(通话小窗位置)、ihomy:last-doing:float:v1(上次在做浮卡位置)、
// ihomy:last-doing:float:collapsed(上次在做浮卡收起态)、
// ihomy:haptics:v1(触觉反馈总开关);ihomy:float:chat / ihomy:float:music(浮窗显隐开关)
// 是功能偏好不是布局,这里不清,要重新打开去聊天室 / 音乐页。
// ihomy:panel:*、ihomy:vinyl:pos 与 ihomy:room:chat:v1(房间聊天已并入全局浮窗)是历史键(现已无写入方),
// 保留清理是为了清掉老浏览器里的残留。
// 注意:光尘首页仪表盘(ihomy:dashboard:layout:v3)与暖居首页(ihomy:guangchen:home:v)布局
// 各有页面内自带的「恢复默认」按钮,不在这里覆盖——新增可拖拽面板时记得回来补键。
const resetPanelLayout = () => {
  ElMessageBox.confirm(t('settings.resetPanelConfirm'), t('settings.resetPanelTitle'), {
    confirmButtonText: t('common.confirm'),
    cancelButtonText: t('common.cancel'),
    type: 'warning',
    closeOnClickModal: true,
  })
    .then(() => {
      const keys = Object.keys(localStorage).filter((k) => k.startsWith('ihomy:panel:'))
      keys.forEach((k) => localStorage.removeItem(k))
      localStorage.removeItem('ihomy:vinyl:pos')
      localStorage.removeItem('ihomy:music:pos')
      localStorage.removeItem('ihomy:chat:float:v1')
      localStorage.removeItem('ihomy:chat:panel:v1')
      localStorage.removeItem('ihomy:chat:sheet:v1')
      localStorage.removeItem('ihomy:chat:rail:v1')
      localStorage.removeItem('ihomy:chat:rail:w:v1')
      localStorage.removeItem('ihomy:chat:text:v1')
      localStorage.removeItem('ihomy:games:lastRoomGame')
      localStorage.removeItem('ihomy:room:autoReady')
      localStorage.removeItem('ihomy:room:chat:v1')
      localStorage.removeItem('ihomy:mahjong3d:view')
      localStorage.removeItem('ihomy:call:mini:v1')
      localStorage.removeItem('ihomy:last-doing:float:v1')
      localStorage.removeItem('ihomy:last-doing:float:collapsed')
      ElMessage.success(t('settings.resetPanelDone'))
      setTimeout(() => location.reload(), 800)
    })
    .catch(() => {})
}
</script>

<style scoped>
.settings-card {
  margin-bottom: 16px;
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
.settings-form .el-form-item .el-switch,
.settings-form .el-form-item .el-radio-group {
  margin-bottom: 0;
}
.settings-form .el-divider {
  margin: 8px 0 4px;
}
@media (max-width: 768px) {
  .settings-card .el-select {
    width: 100%;
  }
}
</style>
