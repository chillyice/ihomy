<!-- Wallpaper Engine:复制壁纸令牌,粘到桌面壁纸部件的属性面板里即可显示站内内容。
     只留令牌与两个动作,说明文字收掉。 -->
<template>
  <div class="card settings-card">
    <div class="section-label">{{ $t('wallpaper.weTitle') }}</div>
    <el-input :model-value="wallpaperToken" readonly :placeholder="$t('wallpaper.tokenEmpty')" :aria-label="$t('wallpaper.tokenLabel')" />
    <div class="form-footer">
      <el-button type="primary" @click="copyWallpaperToken">{{ $t('wallpaper.copyToken') }}</el-button>
      <el-button class="ghost-btn" @click="openWallpaper">{{ $t('wallpaper.openPage') }}</el-button>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '@/api'

const { t } = useI18n()
const router = useRouter()

// 壁纸令牌:桌面壁纸(WE)环境填不了账号密码,登录改由壁纸页用令牌调 /auth/refresh 换会话。
// 与浏览器会话的 refresh token 区分:后者轮换后即作废,壁纸令牌可长期滑动续期。
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
  margin-bottom: 24px;
  background: var(--color-card);
}
.form-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 12px;
}
</style>
