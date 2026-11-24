<!-- 画面布局:一键恢复首页及各类浮窗面板的默认位置与大小。
     属于「首页内容」大类(面板布局是首页体验的一部分,不再挂在外观与显示的主题卡下)。 -->
<template>
  <div class="card settings-card">
    <div class="section-label">{{ $t('settings.panelLayout') }}</div>
    <p class="share-tip layout-lead">{{ $t('settings.panelLayoutTip') }}</p>
    <el-button type="warning" plain @click="resetPanelLayout">{{ $t('settings.resetPanelLayout') }}</el-button>
  </div>
</template>

<script setup>
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'

const { t } = useI18n()

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
  margin-bottom: 24px;
  background: var(--color-card);
}
.layout-lead {
  margin: 0 0 12px;
}
.share-tip {
  color: #776e62;
  font-size: 12px;
  line-height: 1.6;
  width: 100%;
}
html.dark .share-tip {
  color: #9a9088;
}
</style>
