<!-- 根组件:移动端 → MobileLayout;桌面端 → 光影层 + 左侧导航 + 页面内容 -->
<template>
  <el-config-provider :locale="elLocale">
    <router-view v-if="standalone" v-slot="{ Component, route }">
      <component :is="Component" :key="route.path" />
    </router-view>
    <template v-else-if="routeReady">
      <SunLightLayer v-if="anyEffectEnabled && !immersive" />
      <WindowShell v-if="windowShell" />
      <MobileLayout v-else-if="isMobile" />
      <WarmLayout v-else-if="isWarm" />
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
      <template v-if="!isMobile || windowShell">
        <BackToTop v-if="!immersive" />
        <InstallPrompt />
      </template>
      <WindowMusicPlayer v-if="windowShell && userStore.isLoggedIn" />
      <MusicPlayer v-if="!isWindow && (isMobile || !immersive) && floatsStore.music" />
      <LightTestConsole v-if="!isMobile && lightTestMode" />
      <SiteFooter v-if="!isMobile && !immersive" />
      <ScreensaverOverlay v-if="userStore.isLoggedIn && (isMobile || windowShell)" />
    </template>
    <div v-if="route.name === 'Home' && appStore.loadFailed && !userStore.mustChangePassword" class="home-load-error" role="status">
      <span>{{ $t('home.familyLoadFailed') }}</span>
      <el-button text :loading="appStore.loading" @click="appStore.init()">{{ $t('common.retry') }}</el-button>
    </div>
    <!-- 每日首屏早安页(手机/桌面同一份):每天第一次进站时盖一层全屏,内部自守卫登录态/
         纯 OPS/首登改密/儿童模式/通话/独立页/沉浸页/弹窗,并自带「今天看过」记账 -->
    <MorningOverlay v-if="routeReady && !standalone && userStore.isLoggedIn" />
    <!-- 首登强制改密(种子账号):全局弹窗,由 store 标记驱动 -->
    <ChangePasswordDialog v-if="userStore.mustChangePassword" />
    <!-- 网页端新版本提示(仅 Web):线上 dist 已更新就提示刷新,别让 PWA 预缓存拖着跑旧 bundle -->
    <WebUpdateBanner v-if="!isNative && routeReady" />
    <!-- 全局聊天浮窗(懒加载,内部自守卫:登录且非独立页);儿童模式不显示 -->
    <ChatFloat v-if="userStore.isLoggedIn && !isChild" />
    <!-- 1:1 通话覆盖层(懒加载,内部自守卫登录态) -->
    <CallOverlay v-if="userStore.isLoggedIn" />
    <!-- 应用内更新弹窗(仅原生壳):懒加载,Web 端永不加载 Capacitor 运行时 -->
    <AppUpdateDialog v-if="isNative" />
    <!-- 命令面板(Ctrl/⌘+K):独立页与沉浸页不渲染,触屏由组件内 hasPointer 兜底;儿童模式关闭 -->
    <CommandPalette v-if="paletteUsed && !standalone && !immersive && !isChild" />
    <!-- 儿童模式:右下角家长退出入口(需本机密码) -->
    <ChildLock v-if="isChild && !standalone && routeReady" />
  </el-config-provider>
</template>

<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount, provide, defineAsyncComponent } from 'vue'
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
import { initPrefSync } from '@/utils/userPref'
import { useDevice } from '@/composables/useDevice'
import { useHotkeys } from '@/composables/useHotkeys'
import { usePenTap } from '@/composables/usePenTap'
const SunLightLayer = defineAsyncComponent(() => import('@/components/SunLightLayer.vue'))
const AppSidebar = defineAsyncComponent(() => import('@/components/AppSidebar.vue'))
const WarmLayout = defineAsyncComponent(() => import('@/components/warm/WarmLayout.vue'))
const WindowShell = defineAsyncComponent(() => import('@/components/window/WindowShell.vue'))
const WindowMusicPlayer = defineAsyncComponent(() => import('@/components/WindowMusicPlayer.vue'))
import BackToTop from '@/components/BackToTop.vue'
import InstallPrompt from '@/components/InstallPrompt.vue'
import MusicPlayer from '@/components/MusicPlayer.vue'
import SiteFooter from '@/components/SiteFooter.vue'
const LightTestConsole = defineAsyncComponent(() => import('@/components/LightTestConsole.vue'))
const ChangePasswordDialog = defineAsyncComponent(() => import('@/components/ChangePasswordDialog.vue'))
import ChildLock from '@/components/ChildLock.vue'
import WebUpdateBanner from '@/components/WebUpdateBanner.vue'
import { initDisplayMode, useDisplayMode } from '@/composables/useDisplayMode'
const ChatFloat = defineAsyncComponent(() => import('@/components/chat/ChatFloat.vue'))
const CallOverlay = defineAsyncComponent(() => import('@/components/chat/CallOverlay.vue'))
// 命令面板(Ctrl/⌘+K):懒加载,内部只对精细指针显示,独立页/沉浸页不挂载
const CommandPalette = defineAsyncComponent(() => import('@/components/CommandPalette.vue'))
const MobileLayout = defineAsyncComponent(() => import('@/layouts/MobileLayout.vue'))
// 手机与窗边的大屏待机共用覆盖层，是否自动进入由本机偏好决定。
const ScreensaverOverlay = defineAsyncComponent(() => import('@/components/screensaver/ScreensaverOverlay.vue'))
// 每日首屏早安页:全端共用,懒加载(天气动效与看板取数只在真正展示时才需要)
const MorningOverlay = defineAsyncComponent(() => import('@/components/morning/MorningOverlay.vue'))
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
const { closePalette, openPalette, paletteOpen } = useHotkeys()
const paletteUsed = ref(false)
watch(paletteOpen, (open) => {
  if (open) paletteUsed.value = true
})
// 手写笔点按兜底(iPad + Apple Pencil):全站唯一一处在 document 捕获阶段补发 click,
// 站内所有只吃 @click 的交互(含纸牌双击自动移动)跟着一起恢复
usePenTap()
const { locale } = useI18n()

// 暖居主题:桌面端 + 非纯 OPS 时启用独立外壳
const isWarm = computed(() => themeStore.theme === 'warm' && !isMobile.value && !userStore.isPureOps)
const isWindow = computed(() => themeStore.theme === 'window')
const windowShell = computed(() => isWindow.value && !immersive.value && !userStore.isPureOps)

// 站内窗边页面允许低视力成员双指放大，独立产品及旧主题沿用原来的视口约定。
const viewport = document.querySelector('meta[name="viewport"]')
const originalViewport = viewport?.content
watch(
  () => routeReady.value && !standalone.value && windowShell.value,
  (active) => {
    if (viewport)
      viewport.content = active
        ? 'width=device-width, initial-scale=1, viewport-fit=cover, interactive-widget=resizes-content'
        : originalViewport
  },
  { immediate: true },
)
onBeforeUnmount(() => {
  if (viewport) viewport.content = originalViewport
})

// 全局光影状态:在 App.vue 创建实例,provide 给 SunLightLayer(渲染)和 AppSidebar(控制台灯)
const sunLight = useSunLight()
provide(SUN_LIGHT_KEY, sunLight)
const { anyEffectEnabled, lightTestMode } = sunLight

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

function loadFamily() {
  if (!routeReady.value || standalone.value || userStore.mustChangePassword) return
  appStore.init()
}

onMounted(async () => {
  // 原生壳能力(返回键/状态栏/下载/外链/断网提示):懒加载,Web 端 bootNative 直接返回
  if (isNative) import('@/native').then((m) => m.bootNative()).catch((e) => console.warn('[native] 初始化失败', e))
  // 界面偏好同步:页面隐藏/关闭前把未发的那次上传发出去(纯事件绑定,不发请求)
  initPrefSync()
  window.addEventListener('online', loadFamily)
  // 等首次导航解析完再判定独立页,否则会把独立页当普通页初始化一次 ihomy 家庭数据
  await router.isReady()
  routeReady.value = true
})
onBeforeUnmount(() => window.removeEventListener('online', loadFamily))

watch(
  [
    () => userStore.isLoggedIn,
    () => userStore.userInfo?.id,
    () => userStore.userInfo?.familyId,
    () => route.query.hid,
    () => route.query.home_id,
  ],
  () => {
    appStore.reset()
    loadFamily()
  },
)

// 首次进站、从独立页返回、完成强制改密共用同一个入口。
watch(
  () => routeReady.value && !standalone.value && !userStore.mustChangePassword,
  (ready) => {
    if (ready) {
      loadFamily()
      userStore.ensureUserInfo()
    }
  },
)

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
.home-load-error {
  position: fixed;
  left: 50%;
  bottom: calc(76px + env(safe-area-inset-bottom, 0px));
  transform: translateX(-50%);
  z-index: var(--z-float-top);
  display: flex;
  align-items: center;
  gap: 8px;
  width: max-content;
  max-width: calc(100% - 32px);
  padding: 4px 12px;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  background: var(--color-card);
  color: var(--color-text);
}
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
