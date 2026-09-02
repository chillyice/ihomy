<template>
  <div class="mobile-me">
    <!-- 用户信息 -->
    <div class="me-header">
      <el-avatar :size="56" :src="userInfo?.avatar">{{ (userInfo?.nickname || 'U').charAt(0) }}</el-avatar>
      <div class="me-user-info">
        <div class="me-nickname">{{ userInfo?.nickname || $t('home.loginToView') }}</div>
        <div class="me-family">{{ appStore.familyName || 'ihomy' }}</div>
      </div>
      <span v-if="!userStore.isLoggedIn" class="me-login-btn" @click="$router.push('/login')">{{ $t('home.loginToView') }}</span>
    </div>

    <!-- 家庭切换 -->
    <div v-if="userStore.isLoggedIn && families.length > 1" class="me-section">
      <div class="me-row" @click="showFamilySwitch = !showFamilySwitch">
        <span class="me-row-icon">🔄</span>
        <span class="me-row-text">{{ $t('nav.switchFamily') }}</span>
        <el-icon class="me-row-arrow" :class="{ open: showFamilySwitch }"><ArrowRight /></el-icon>
      </div>
      <transition name="expand">
        <div v-show="showFamilySwitch" class="family-list">
          <div
            v-for="f in families"
            :key="f.familyId"
            class="family-item"
            :class="{ active: f.isCurrent }"
            @click="switchFamily(f.familyId)"
          >
            <span>{{ f.name }}</span>
            <el-icon v-if="f.isCurrent"><Check /></el-icon>
          </div>
        </div>
      </transition>
    </div>

    <!-- 设置区 -->
    <div class="me-section">
      <div class="me-row" @click="toggleTheme">
        <span class="me-row-icon">{{ theme.dark ? '🌙' : '☀️' }}</span>
        <span class="me-row-text">{{ theme.dark ? $t('theme.dark') : $t('theme.light') }}</span>
        <el-switch :model-value="theme.dark" size="small" />
      </div>
      <div class="me-row" @click="toggleLightEffect">
        <span class="me-row-icon">✨</span>
        <span class="me-row-text">光影特效</span>
        <el-switch :model-value="lightEffectOn" size="small" />
      </div>
      <div class="me-row" @click="toggleLang">
        <span class="me-row-icon">🌐</span>
        <span class="me-row-text">{{ $t('mobile.language') }}</span>
        <span class="me-row-value">{{ locale === 'en' ? 'EN' : '中' }}</span>
      </div>
    </div>

    <!-- 功能入口 -->
    <div class="me-section">
      <div v-if="userStore.isLoggedIn" class="me-row" @click="$router.push('/settings')">
        <span class="me-row-icon">⚙️</span>
        <span class="me-row-text">{{ $t('nav.settings') }}</span>
        <el-icon class="me-row-arrow"><ArrowRight /></el-icon>
      </div>
      <div v-if="userStore.isLoggedIn" class="me-row" @click="$router.push('/member')">
        <span class="me-row-icon">👥</span>
        <span class="me-row-text">{{ $t('mobile.members') }}</span>
        <el-icon class="me-row-arrow"><ArrowRight /></el-icon>
      </div>
      <div v-if="userStore.isLoggedIn" class="me-row" @click="$router.push({ path: '/settings', query: { tab: 'profile' } })">
        <span class="me-row-icon">👤</span>
        <span class="me-row-text">{{ $t('settings.profile') }}</span>
        <el-icon class="me-row-arrow"><ArrowRight /></el-icon>
      </div>
    </div>

    <!-- 退出 -->
    <div v-if="userStore.isLoggedIn" class="me-section">
      <div class="me-row logout" @click="doLogout">
        <span class="me-row-text">{{ $t('nav.logout') }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, inject, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { ArrowRight, Check } from '@element-plus/icons-vue'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { authApi } from '@/api'
import { applyTheme, loadTheme } from '@/theme'
import { applyLocale } from '@/i18n'
import { SUN_LIGHT_KEY } from '@/utils/useSunLight'

const { locale, t } = useI18n()
const appStore = useAppStore()
const userStore = useUserStore()
const sunLight = inject(SUN_LIGHT_KEY)

const userInfo = computed(() => userStore.userInfo)
const theme = ref(loadTheme())
const families = ref([])
const showFamilySwitch = ref(false)

const lightEffectOn = computed(() => sunLight?.shadowEnabled?.value ?? false)

const toggleTheme = () => {
  theme.value = applyTheme({ ...theme.value, dark: !theme.value.dark, autoMode: false })
}
const toggleLightEffect = () => {
  if (sunLight?.shadowEnabled) {
    sunLight.shadowEnabled.value = !sunLight.shadowEnabled.value
    if (sunLight.shadowEnabled.value) {
      sunLight.blobsEnabled.value = true
      sunLight.weatherEffectEnabled.value = true
      sunLight.lampMode.value = 'auto'
    } else {
      sunLight.blobsEnabled.value = false
      sunLight.weatherEffectEnabled.value = false
      sunLight.lampMode.value = 'off'
      sunLight.glassEnabled.value = false
    }
  }
}
const toggleLang = () => {
  const next = locale.value === 'en' ? 'zh-CN' : 'en'
  applyLocale(next)
}

const doLogout = () => {
  userStore.logout()
  location.reload()
}

const switchFamily = async (familyId) => {
  try {
    await userStore.switchFamily(familyId, true)
    ElMessage.success(t('nav.switchFamily') + ' ✓')
    location.reload()
  } catch (e) { ElMessage.error(e.message || 'Failed') }
}

onMounted(async () => {
  if (userStore.isLoggedIn) {
    try { families.value = await authApi.families() } catch (e) { families.value = [] }
  }
})
</script>

<style scoped>
.mobile-me { padding: 16px 16px 80px; }

.me-header {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 20px 16px;
  margin-bottom: 16px;
  background: rgba(255,255,255,0.5);
  border-radius: 16px;
  border: 1px solid rgba(0,0,0,0.04);
}
html.dark .me-header { background: rgba(255,255,255,0.06); border-color: rgba(255,255,255,0.06); }
.me-user-info { flex: 1; }
.me-nickname { font-size: 18px; font-weight: 600; color: var(--color-text-primary, #333); }
html.dark .me-nickname { color: #E8DCC8; }
.me-family { font-size: 13px; color: var(--color-text-secondary, #888); margin-top: 2px; }
html.dark .me-family { color: rgba(232,220,200,0.5); }
.me-login-btn {
  padding: 6px 16px;
  border-radius: 8px;
  background: var(--color-primary, #b88c6e);
  color: #fff;
  font-size: 13px;
  cursor: pointer;
}
html.dark .me-login-btn { background: #d4b298; color: #1a1a1a; }

.me-section {
  margin-bottom: 12px;
  background: rgba(255,255,255,0.5);
  border-radius: 14px;
  border: 1px solid rgba(0,0,0,0.04);
  overflow: hidden;
}
html.dark .me-section { background: rgba(255,255,255,0.06); border-color: rgba(255,255,255,0.06); }

.me-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  cursor: pointer;
  border-bottom: 1px solid rgba(0,0,0,0.03);
  -webkit-tap-highlight-color: transparent;
}
html.dark .me-row { border-bottom-color: rgba(255,255,255,0.04); }
.me-row:last-child { border-bottom: none; }
.me-row:active { background: rgba(0,0,0,0.03); }
html.dark .me-row:active { background: rgba(255,255,255,0.04); }

.me-row-icon { font-size: 18px; width: 24px; text-align: center; }
.me-row-text { flex: 1; font-size: 15px; color: var(--color-text-primary, #333); }
html.dark .me-row-text { color: #E8DCC8; }
.me-row-value { font-size: 14px; color: var(--color-text-secondary, #888); }
.me-row-arrow { color: var(--color-text-secondary, #aaa); transition: transform 0.2s; font-size: 14px; }
.me-row-arrow.open { transform: rotate(90deg); }

.me-row.logout .me-row-text { text-align: center; color: #b04a3a; }
html.dark .me-row.logout .me-row-text { color: #c97474; }

.family-list { overflow: hidden; }
.family-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px 12px 52px;
  font-size: 14px;
  color: var(--color-text-primary, #333);
  cursor: pointer;
  border-bottom: 1px solid rgba(0,0,0,0.03);
}
html.dark .family-item { color: #E8DCC8; border-bottom-color: rgba(255,255,255,0.04); }
.family-item:last-child { border-bottom: none; }
.family-item.active { color: var(--color-primary, #b88c6e); font-weight: 600; }
html.dark .family-item.active { color: #d4b298; }
.family-item:active { background: rgba(0,0,0,0.03); }

.expand-enter-active, .expand-leave-active { transition: max-height 0.25s ease; overflow: hidden; }
.expand-enter-from, .expand-leave-to { max-height: 0; }
.expand-enter-to, .expand-leave-from { max-height: 300px; }
</style>
