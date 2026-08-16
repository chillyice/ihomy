<!-- 根组件:全局光影层 + 左侧导航 + 页面内容(带滑动过渡) -->
<template>
  <el-config-provider :locale="elLocale">
    <!-- 全局光影层:所有页面共享(体积光+窗框阴影+台灯+灰尘) -->
    <SunLightLayer />

    <!-- 左侧导航:纯 OPS 账号(无家庭角色)不显示,OWNER+OPS 等复合角色显示 -->
    <AppSidebar v-if="!userStore.isPureOps" />

    <!-- 主内容区:带滑动过渡,仿佛平板滑动屏幕切换 -->
    <main class="app-main" :class="{ 'with-sidebar': !userStore.isPureOps }">
      <router-view v-slot="{ Component, route }">
        <transition :name="route.meta.transition || 'slide-down'" mode="out-in">
          <component :is="Component" :key="route.path" />
        </transition>
      </router-view>
    </main>

    <BackToTop />
    <InstallPrompt />
    <MusicPlayer />
    <SiteFooter />
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
import SunLightLayer from '@/components/SunLightLayer.vue'
import AppSidebar from '@/components/AppSidebar.vue'
import BackToTop from '@/components/BackToTop.vue'
import InstallPrompt from '@/components/InstallPrompt.vue'
import MusicPlayer from '@/components/MusicPlayer.vue'
import SiteFooter from '@/components/SiteFooter.vue'

const appStore = useAppStore()
const userStore = useUserStore()
const route = useRoute()
const { locale } = useI18n()

// 全局光影状态:在 App.vue 创建实例,provide 给 SunLightLayer(渲染)和 AppSidebar(控制台灯)
const sunLight = useSunLight()
provide(SUN_LIGHT_KEY, sunLight)

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
  transition: margin-left 0.3s ease;
}
.app-main.with-sidebar {
  margin-left: 220px;
}
/* 页面根元素:z-index 10 确保在背景色块(z=1)之上、窗框阴影(z=35)之下 */
/* 光影层的 multiply/screen 混合模式可正确作用于内容(同一 stacking context) */
.app-main > * {
  position: relative;
  z-index: 10;
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
</style>
