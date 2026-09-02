<!-- 根组件:移动端 → MobileLayout;桌面端 → 光影层 + 左侧导航 + 页面内容 -->
<template>
  <el-config-provider :locale="elLocale">
    <!-- 移动端布局:特效开启时同样挂载光影层(全部 pointer-events:none 不挡触摸) -->
    <template v-if="isMobile">
      <SunLightLayer v-if="anyEffectEnabled" />
      <MobileLayout />
    </template>

    <!-- 桌面端布局 -->
    <template v-else>
      <SunLightLayer v-if="anyEffectEnabled" />
      <AppSidebar v-if="!userStore.isPureOps" />
      <main class="app-main" :class="{ 'with-sidebar': !userStore.isPureOps }">
        <router-view v-slot="{ Component, route }">
          <transition :name="route.meta.transition || 'fade'" mode="out-in">
            <component :is="Component" :key="route.path" />
          </transition>
        </router-view>
      </main>
      <BackToTop />
      <InstallPrompt />
      <MusicPlayer />
      <LightTestConsole />
      <SiteFooter />
    </template>
  </el-config-provider>
</template>

<script setup>
import { ref, computed, watch, onMounted, provide } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import en from 'element-plus/es/locale/lang/en'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { useSunLight, SUN_LIGHT_KEY } from '@/utils/useSunLight'
import { useDevice } from '@/composables/useDevice'
import SunLightLayer from '@/components/SunLightLayer.vue'
import AppSidebar from '@/components/AppSidebar.vue'
import BackToTop from '@/components/BackToTop.vue'
import InstallPrompt from '@/components/InstallPrompt.vue'
import MusicPlayer from '@/components/MusicPlayer.vue'
import SiteFooter from '@/components/SiteFooter.vue'
import LightTestConsole from '@/components/LightTestConsole.vue'
import MobileLayout from '@/layouts/MobileLayout.vue'

const { isMobile } = useDevice()
const appStore = useAppStore()
const userStore = useUserStore()
const route = useRoute()
const { locale } = useI18n()

// 全局光影状态:在 App.vue 创建实例,provide 给 SunLightLayer(渲染)和 AppSidebar(控制台灯)
const sunLight = useSunLight()
provide(SUN_LIGHT_KEY, sunLight)
const { anyEffectEnabled } = sunLight

// 移动端首次使用(本设备无保存偏好)默认关闭所有光影特效(GPU/内存敏感);
// 用户开启过则写入 ihomy:effects,刷新后尊重用户选择不再强制关闭
watch(isMobile, (mobile) => {
  if (mobile && !localStorage.getItem('ihomy:effects')) {
    sunLight.shadowEnabled.value = false
    sunLight.weatherEffectEnabled.value = false
    sunLight.blobsEnabled.value = false
    sunLight.lampMode.value = 'off'
    sunLight.glassEnabled.value = false
  }
}, { immediate: true })

const elLocale = computed(() => (locale.value === 'en' ? en : zhCn))

onMounted(() => {
  appStore.init()
  userStore.ensureUserInfo()
})

watch(
  () => userStore.isLoggedIn,
  () => {
    appStore.reset()
    appStore.init()
  },
)
</script>

<style>
/* 主内容区:左侧导航占位,不设 z-index 避免创建 stacking context(光影层需与内容混合) */
.app-main {
  min-height: 100vh;
  position: relative;
  z-index: 10;
  transition: margin-left 0.3s ease;
  contain: style;
}
.app-main.with-sidebar {
  margin-left: 220px;
}
.app-main > * {
  position: relative;
}

/* 页面滑动过渡:当前页面整体向下滑出,新页面从上方滑入 */
/* 仿佛平板手机滑动屏幕切换,配合全局光影层看不出页面在切换 */
.slide-down-enter-active,
.slide-down-leave-active {
  transition: transform 0.5s cubic-bezier(0.4, 0, 0.2, 1), opacity 0.4s ease;
}
.slide-down-enter-from {
  transform: translateY(-100%);
  opacity: 0;
}
.slide-down-leave-to {
  transform: translateY(100%);
  opacity: 0;
}

/* 兼容性:若浏览器不支持 transform 过渡,fallback 到 fade */
@supports not (transform: translateY(-100%)) {
  .slide-down-enter-active, .slide-down-leave-active { transition: opacity 0.3s ease; }
  .slide-down-enter-from, .slide-down-leave-to { opacity: 0; transform: none; }
}

/* 全局页面切换过渡(默认):淡入淡出,无 transform 避免 fixed 定位偏移 */
.fade-enter-active, .fade-leave-active { transition: opacity 0.5s ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
