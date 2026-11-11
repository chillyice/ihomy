<!-- 根组件:移动端 → MobileLayout;桌面端 → 光影层 + 左侧导航 + 页面内容 -->
<template>
  <el-config-provider :locale="elLocale">
    <!-- 移动端布局:特效开启时同样挂载光影层(全部 pointer-events:none 不挡触摸) -->
    <template v-if="isMobile">
      <router-view v-if="standalone" v-slot="{ Component, route }">
        <component :is="Component" :key="route.path" />
      </router-view>
      <template v-else-if="routeReady">
        <SunLightLayer v-if="anyEffectEnabled && !immersive" />
        <MobileLayout />
      </template>
    </template>

    <!-- 桌面端布局 -->
    <template v-else>
      <!-- 独立站点页(咔哒软件首页等):完全独立全屏,不套 ihomy 外壳/光影/侧栏/页脚 -->
      <router-view v-if="standalone" v-slot="{ Component, route }">
        <component :is="Component" :key="route.path" />
      </router-view>
      <template v-else-if="routeReady">
        <!-- 光影层:暖居/光尘共用同一套太阳驱动的丁达尔体积光+窗影+尘+台灯;沉浸式页面(如光影实验台)时隐藏 -->
        <SunLightLayer v-if="anyEffectEnabled && !immersive" />
        <!-- 暖居主题:独立外壳(顶栏 + studio 外框 + 侧栏),光影沿用 SunLightLayer -->
        <WarmLayout v-if="isWarm" />
        <template v-else>
          <AppSidebar v-if="!userStore.isPureOps && !immersive" />
          <main class="app-main" :class="{ 'with-sidebar': !userStore.isPureOps && !immersive }">
            <router-view v-slot="{ Component, route }">
              <transition :name="route.meta.transition || 'fade'" mode="out-in">
                <component :is="Component" :key="route.path" />
              </transition>
            </router-view>
          </main>
        </template>
        <BackToTop v-if="!immersive" />
        <InstallPrompt />
        <MusicPlayer v-if="!immersive && floatsStore.music" />
        <LightTestConsole />
        <SiteFooter v-if="!immersive" />
      </template>
    </template>
    <!-- 首登强制改密(种子账号):全局弹窗,由 store 标记驱动 -->
    <ChangePasswordDialog />
    <!-- 全局聊天浮窗(懒加载,内部自守卫:登录且非独立页);儿童模式不显示 -->
    <ChatFloat v-if="!isChild" />
    <!-- 1:1 通话覆盖层(懒加载,内部自守卫登录态) -->
    <CallOverlay />
    <!-- 应用内更新弹窗(仅原生壳):懒加载,Web 端永不加载 Capacitor 运行时 -->
    <AppUpdateDialog v-if="isNative" />
    <!-- 命令面板(Ctrl/⌘+K):独立页与沉浸页不渲染,触屏由组件内 hasPointer 兜底;儿童模式关闭 -->
    <CommandPalette v-if="!standalone && !immersive && !isChild" />
    <!-- 儿童模式:右下角家长退出入口(需本机密码) -->
    <ChildLock v-if="isChild && !standalone && routeReady" />
  </el-config-provider>
</template>

<script setup>
import { ref, computed, watch, onMounted, provide, defineAsyncComponent } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import en from 'element-plus/es/locale/lang/en'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { useThemeStore } from '@/stores/theme'
import { useFloatsStore } from '@/stores/floats'
import { useSunLight, SUN_LIGHT_KEY } from '@/utils/useSunLight'
import { isNativeApp } from '@/utils/platform'
import { useDevice } from '@/composables/useDevice'
import { useHotkeys } from '@/composables/useHotkeys'
import SunLightLayer from '@/components/SunLightLayer.vue'
import AppSidebar from '@/components/AppSidebar.vue'
import WarmLayout from '@/components/warm/WarmLayout.vue'
import BackToTop from '@/components/BackToTop.vue'
import InstallPrompt from '@/components/InstallPrompt.vue'
import MusicPlayer from '@/components/MusicPlayer.vue'
import SiteFooter from '@/components/SiteFooter.vue'
import LightTestConsole from '@/components/LightTestConsole.vue'
import ChangePasswordDialog from '@/components/ChangePasswordDialog.vue'
import ChildLock from '@/components/ChildLock.vue'
import { initDisplayMode, useDisplayMode } from '@/composables/useDisplayMode'
const ChatFloat = defineAsyncComponent(() => import('@/components/chat/ChatFloat.vue'))
const CallOverlay = defineAsyncComponent(() => import('@/components/chat/CallOverlay.vue'))
// 命令面板(Ctrl/⌘+K):懒加载,内部只对精细指针显示,独立页/沉浸页不挂载
const CommandPalette = defineAsyncComponent(() => import('@/components/CommandPalette.vue'))
import MobileLayout from '@/layouts/MobileLayout.vue'
// 应用内更新弹窗:内部 import Capacitor 插件,必须懒加载且只在壳内渲染
const AppUpdateDialog = defineAsyncComponent(() => import('@/components/AppUpdateDialog.vue'))
// 壳内判定(静态读一次,不随运行期变化)
const isNative = isNativeApp()

const { isMobile } = useDevice()
const appStore = useAppStore()
const userStore = useUserStore()
const themeStore = useThemeStore()
// 浮窗开关(音乐页/聊天室可关,拖到底部删除条关的也是这个键)
const floatsStore = useFloatsStore()
const route = useRoute()
const router = useRouter()
// 显示模式(老人/儿童):启动即把本机保存的模式贴回 html 根类
const { isChild } = useDisplayMode()
initDisplayMode()
// 沉浸式页面(如 3D 光影实验台)：隐藏侧边栏 / 页脚 / 回顶 / 播放器，内容区占满全屏
const immersive = computed(() => !!route.meta.immersive)
// 路由首次解析完成前 currentRoute 是 START_LOCATION(meta 为空),此时按「非独立页」渲染会先挂载整个
// 外壳(侧栏/播放器/暖居/首页)再被卸载——独立页(Kada/壁纸)因此会打出一批与它无关的 ihomy 接口
// (含 OPS 权限 403 弹错、appStore 重复拉首页)。故外壳一律等解析完成后再渲染。
const routeReady = ref(false)
// 独立站点页(咔哒软件首页等)：完全独立全屏，不套 ihomy 外壳/光影/侧栏/页脚
const standalone = computed(() => routeReady.value && !!route.meta.standalone)
// 全局快捷键:模块级单例,只绑一次;面板状态由这里与 CommandPalette 共享
const { closePalette, openPalette } = useHotkeys()
const { locale } = useI18n()

// 暖居主题:桌面端 + 非纯 OPS 时启用独立外壳
const isWarm = computed(() => themeStore.theme === 'warm' && !isMobile.value && !userStore.isPureOps)

// 全局光影状态:在 App.vue 创建实例,provide 给 SunLightLayer(渲染)和 AppSidebar(控制台灯)
const sunLight = useSunLight()
provide(SUN_LIGHT_KEY, sunLight)
const { anyEffectEnabled } = sunLight

// 移动端首次使用(本设备无保存偏好)默认关闭所有光影特效(GPU/内存敏感);
// 用户开启过则写入 ihomy:effects,刷新后尊重用户选择不再强制关闭
watch(
  isMobile,
  (mobile) => {
    if (mobile && !localStorage.getItem('ihomy:effects')) {
      sunLight.shadowEnabled.value = false
      sunLight.weatherEffectEnabled.value = false
      sunLight.blobsEnabled.value = false
      sunLight.lampMode.value = 'off'
      sunLight.glassEnabled.value = false
    }
  },
  { immediate: true },
)

const elLocale = computed(() => (locale.value === 'en' ? en : zhCn))

onMounted(async () => {
  // 原生壳能力(返回键/状态栏/下载/外链/断网提示):懒加载,Web 端 bootNative 直接返回
  import('@/native').then((m) => m.bootNative()).catch((e) => console.warn('[native] 初始化失败', e))
  // 等首次导航解析完再判定独立页,否则会把独立页当普通页初始化一次 ihomy 家庭数据
  await router.isReady()
  routeReady.value = true
  // 独立站点页(咔哒软件首页等)不初始化 ihomy 家庭数据,避免调用 ihomy 后端接口
  if (standalone.value) return
  // 首登强制改密:改密前后端只放行改密/登出,不拉家庭聚合数据(否则一连串 403)
  if (userStore.mustChangePassword) return
  appStore.init()
  userStore.ensureUserInfo()
})

watch(
  () => userStore.isLoggedIn,
  () => {
    // 独立页自带登录(壁纸页):登录态变化不触发 ihomy 首页聚合,由该页自己按需取数
    if (standalone.value) return
    appStore.reset()
    // 首登强制改密:不初始化家庭聚合,等改密完成后再加载
    if (userStore.mustChangePassword) return
    appStore.init()
  },
)

// 直接落在独立页(如 /chillyice、/kada)后点站内链接回来:上面的 onMounted 已因 standalone 提前返回,
// 这里补一次初始化;init 自带 loaded/loading 守卫,从站内页进出独立页时不会重复拉取。
watch(standalone, (isStandalone) => {
  if (isStandalone) return
  if (userStore.mustChangePassword) return
  appStore.init()
  userStore.ensureUserInfo()
})

// 命令面板只在站内普通页可用:独立页 / 沉浸页即使收到 Ctrl/⌘+K 也不弹,离开时顺手关闭
watch([standalone, immersive], ([isStandalone, isImmersive]) => {
  if (isStandalone || isImmersive) closePalette()
})

// ?search=1 兜底:原生壳由 deeplink 直接开面板并清参;Web 直接输入 URL / 刷新会走到这里。
// 打开后立刻把 search 从地址栏摘掉,避免刷新或前进后退时再次弹。
watch(
  () => route.query.search,
  (v) => {
    if (v !== '1') return
    if (!standalone.value && !immersive.value) openPalette()
    const query = { ...route.query }
    delete query.search
    router.replace({ path: route.path, query }).catch(() => {})
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

/* 户型图编辑专注模式(Item 页编辑态时在 <html> 上挂 fp-edit-focus):隐藏左侧导航,内容区占满整屏,画板优先 */
html.fp-edit-focus .app-sidebar {
  display: none;
}
html.fp-edit-focus .app-main.with-sidebar {
  margin-left: 0;
}

/* 页面滑动过渡:当前页面整体向下滑出,新页面从上方滑入 */
/* 仿佛平板手机滑动屏幕切换,配合全局光影层看不出页面在切换 */
.slide-down-enter-active,
.slide-down-leave-active {
  transition:
    transform 0.5s cubic-bezier(0.4, 0, 0.2, 1),
    opacity 0.4s ease;
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
  .slide-down-enter-active,
  .slide-down-leave-active {
    transition: opacity 0.3s ease;
  }
  .slide-down-enter-from,
  .slide-down-leave-to {
    opacity: 0;
    transform: none;
  }
}

/* 全局页面切换过渡(默认):淡入淡出,无 transform 避免 fixed 定位偏移 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.34s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
