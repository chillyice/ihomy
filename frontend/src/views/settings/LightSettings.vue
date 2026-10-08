<template>
  <!-- 个性化设置:主题 + 台灯/色温/亮度/阴影/天气效果/夜间超时关灯/光照测试入口 -->
  <div class="card settings-card">
    <div class="section-label">{{ $t('settings.personalize') }}</div>
    <el-form label-position="top" class="settings-form">
      <el-form-item :label="$t('settings.theme')">
        <div class="theme-row">
          <ThemeSwatch />
          <el-switch v-model="themeStore.autoMode" @change="themeStore.setAutoMode" :active-text="$t('theme.autoMode')" />
          <el-radio-group v-if="!themeStore.autoMode" :model-value="themeStore.mode" @change="themeStore.setMode">
            <el-radio value="dawn">{{ $t('theme.dawn') }}</el-radio>
            <el-radio value="dusk">{{ $t('theme.dusk') }}</el-radio>
          </el-radio-group>
        </div>
      </el-form-item>
      <el-divider />
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
      <el-form-item>
        <div class="setting-row">
          <el-switch v-model="shadowEnabled" />
          <span class="setting-label">{{ $t('settings.shadowEffect') }}</span>
        </div>
        <div class="share-tip">{{ $t('settings.shadowHint') }}</div>
      </el-form-item>
      <el-form-item v-if="shadowEnabled" :label="$t('settings.shadowDepth')">
        <el-slider v-model.number="shadowDepth" :min="0" :max="100" show-input />
        <div class="share-tip">{{ $t('settings.shadowDepthHint') }}</div>
      </el-form-item>
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
      <el-divider />
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
      <el-divider />
      <el-form-item :label="$t('settings.panelLayout')">
        <el-button type="warning" plain @click="resetPanelLayout">{{ $t('settings.resetPanelLayout') }}</el-button>
        <div class="share-tip">{{ $t('settings.resetPanelHint') }}</div>
      </el-form-item>
    </el-form>
  </div>

  <!-- 壁纸氛围屏:桌面壁纸环境填不了账号密码,登录改走「壁纸令牌」(在普通浏览器里复制,粘到 WE 属性面板) -->
  <div class="card settings-card">
    <div class="section-label">{{ $t('wallpaper.settingsTitle') }}</div>
    <p class="share-tip">{{ $t('wallpaper.settingsHint') }}</p>
    <!-- 令牌宽度占满整行(EP 全局 el-input 宽度 100%),两个动作用页面通用 .form-footer 右对齐 -->
    <el-input :model-value="wallpaperToken" readonly :placeholder="$t('wallpaper.tokenEmpty')" :aria-label="$t('wallpaper.tokenLabel')" />
    <div class="form-footer">
      <el-button type="primary" @click="copyWallpaperToken">{{ $t('wallpaper.copyToken') }}</el-button>
      <el-button class="ghost-btn" @click="openWallpaper">{{ $t('wallpaper.openPage') }}</el-button>
    </div>
    <div class="share-tip">{{ $t('wallpaper.tokenWarn') }}</div>
  </div>
</template>

<script setup>
import { inject, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { authApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useThemeStore } from '@/stores/theme'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'
import ThemeSwatch from '@/components/ThemeSwatch.vue'

const { t } = useI18n()
const router = useRouter()

// 光照设置:从全局 useSunLight 实例注入(与 SunLightLayer/AppSidebar 共享)
const sunLight = inject(SUN_LIGHT_KEY)
const {
  lampMode,
  lampTemp,
  lampBrightness,
  shadowEnabled,
  shadowDepth,
  weatherEffectEnabled,
  blobsEnabled,
  glassEnabled,
  idleMinutes,
  isIdle,
} = sunLight || {}

// 主题:theme(暖居/光尘)× mode(晨/暮)+ 日出日落自动,统一由 Pinia store 管理
const themeStore = useThemeStore()

// 进入光照测试:跳转首页并启动测试模式
const enterLightTest = () => {
  if (sunLight) sunLight.startLightTest()
  router.push('/home')
}

// 恢复默认面板布局:清除 localStorage 中所有面板持久化记录,刷新页面生效。
// 当前确实在写的键:ihomy:music:pos(MusicPlayer 拖拽位置);ihomy:panel:* 与 ihomy:vinyl:pos
// 是历史键(现已无写入方),保留清理是为了清掉老浏览器里的残留。
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
      ElMessage.success(t('settings.resetPanelDone'))
      setTimeout(() => location.reload(), 800)
    })
    .catch(() => {})
}

// 壁纸令牌:桌面壁纸(WE)环境填不了账号密码,登录改由壁纸页用令牌调 /auth/refresh 换会话。
// 令牌由后端单独签发(与浏览器会话的 refresh token 区分:后者轮换后即作废,壁纸令牌可长期滑动续期),
// 故这里复制的令牌与浏览器自身会话互不干扰;令牌失效(过期)后回来重新复制一次即可。
const wallpaperToken = ref('')
const loadWallpaperToken = async () => {
  try {
    wallpaperToken.value = (await authApi.wallpaperToken()).refreshToken || ''
  } catch (e) {
    wallpaperToken.value = ''
  }
}
const copyWallpaperToken = async () => {
  const tk = wallpaperToken.value
  if (!tk) return ElMessage.warning(t('wallpaper.tokenEmpty'))
  try {
    await navigator.clipboard.writeText(tk)
    ElMessage.success(t('wallpaper.copied'))
  } catch (e) {
    // 剪贴板不可用(非安全上下文/无权限):令牌就在输入框里,提示用户手动选中复制
    ElMessage.error(t('settings.copyFailed'))
  }
}

const openWallpaper = () => {
  window.open(router.resolve('/wallpaper').href, '_blank', 'noopener')
}

onMounted(() => {
  loadWallpaperToken()
})
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
.theme-row {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

/* 个性化设置:控件行 + 标签水平排列,垂直居中 */
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

/* 表单提交按钮:右下角对齐 */
.form-footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 4px;
}

/* 个性化设置:表单项间距统一 */
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
  .settings-card .el-input,
  .settings-card .el-select {
    width: 100%;
  }
}
</style>
